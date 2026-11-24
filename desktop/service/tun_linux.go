package main

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"net/netip"
	"os"
	"os/user"
	"time"
	"unsafe"

	"github.com/godbus/dbus/v5"
	"golang.org/x/sys/unix"
)

const (
	networkManager          = "org.freedesktop.NetworkManager"
	networkManagerPath      = "/org/freedesktop/NetworkManager"
	dnsManagerPath          = "/org/freedesktop/NetworkManager/DnsManager"
	dnsConfiguration        = "org.freedesktop.NetworkManager.DnsManager.Configuration"
	systemResolver          = "system://"
	activeConnectionState   = "org.freedesktop.NetworkManager.Connection.Active.State"
	activeConnectionReady   = 2
	activeConnectionGone    = 4
	tunModeTun              = 1
	tunReadyTimeout         = 10 * time.Second
	tunPollInterval         = 50 * time.Millisecond
	exclusiveDnsPriority    = -10
	wholeAddressSpaceHalves = 1
)

type userTunnel struct {
	bus    *dbus.Conn
	active dbus.ObjectPath
}

func openTunnel(config string) (string, tunnelSession, error) {
	tun := tunDeviceOf(config)
	if os.Geteuid() == 0 || tun.name == "" {
		return config, nil, nil
	}
	tunnel, resolvers, err := activateUserTunnel(tun)
	if err != nil {
		return "", nil, fmt.Errorf("NetworkManager could not create the tunnel: %w", err)
	}
	fd, err := attachTun(tun.name)
	if err != nil {
		tunnel.close()
		return "", nil, err
	}
	adopted, err := adoptTun(config, fd, resolvers)
	if err != nil {
		_ = unix.Close(fd)
		tunnel.close()
		return "", nil, err
	}
	return adopted, tunnel, nil
}

func activateUserTunnel(tun tunDevice) (*userTunnel, []string, error) {
	owner, err := user.Current()
	if err != nil {
		return nil, nil, err
	}
	bus, err := dbus.ConnectSystemBus()
	if err != nil {
		return nil, nil, err
	}
	resolvers := networkResolvers(bus, tun.name)
	settings := tunnelSettings(tun, owner.Username, os.Getuid())
	options := map[string]dbus.Variant{
		"persist":         dbus.MakeVariant("volatile"),
		"bind-activation": dbus.MakeVariant("dbus-client"),
	}
	var connection, active dbus.ObjectPath
	var result map[string]dbus.Variant
	call := bus.Object(networkManager, networkManagerPath).Call(
		networkManager+".AddAndActivateConnection2", 0, settings, dbus.ObjectPath("/"), dbus.ObjectPath("/"), options)
	if err = call.Store(&connection, &active, &result); err != nil {
		bus.Close()
		return nil, nil, err
	}
	tunnel := &userTunnel{bus: bus, active: active}
	if err = tunnel.waitUntilReady(); err != nil {
		tunnel.close()
		return nil, nil, err
	}
	return tunnel, resolvers, nil
}

func networkResolvers(bus *dbus.Conn, tunName string) []string {
	value, err := bus.Object(networkManager, dnsManagerPath).GetProperty(dnsConfiguration)
	if err != nil {
		return nil
	}
	entries, _ := value.Value().([]map[string]dbus.Variant)
	return resolversOf(entries, tunName)
}

func resolversOf(entries []map[string]dbus.Variant, tunName string) []string {
	var resolvers []string
	seen := map[string]bool{}
	for _, entry := range entries {
		if name, _ := entry["interface"].Value().(string); name == tunName {
			continue
		}
		servers, _ := entry["nameservers"].Value().([]string)
		for _, server := range servers {
			address, err := netip.ParseAddr(server)
			if err != nil || seen[server] {
				continue
			}
			seen[server] = true
			if address.Is6() {
				server = "[" + server + "]"
			}
			resolvers = append(resolvers, server)
		}
	}
	return resolvers
}

func tunnelSettings(tun tunDevice, username string, uid int) map[string]map[string]dbus.Variant {
	settings := map[string]map[string]dbus.Variant{
		"connection": {
			"id":             dbus.MakeVariant(serviceName),
			"type":           dbus.MakeVariant("tun"),
			"interface-name": dbus.MakeVariant(tun.name),
			"autoconnect":    dbus.MakeVariant(false),
			"permissions":    dbus.MakeVariant([]string{"user:" + username}),
		},
		"tun": {
			"mode":        dbus.MakeVariant(uint32(tunModeTun)),
			"owner":       dbus.MakeVariant(int64(uid)),
			"pi":          dbus.MakeVariant(false),
			"vnet-hdr":    dbus.MakeVariant(false),
			"multi-queue": dbus.MakeVariant(false),
		},
		"ipv4": {"method": dbus.MakeVariant("disabled")},
		"ipv6": {"method": dbus.MakeVariant("disabled")},
	}
	for _, prefix := range tun.addresses {
		family := "ipv4"
		if prefix.Addr().Is6() {
			family = "ipv6"
		}
		settings[family] = familySettings(prefix)
	}
	return settings
}

func familySettings(prefix netip.Prefix) map[string]dbus.Variant {
	halves := []string{"0.0.0.0", "128.0.0.0"}
	dns := dbus.MakeVariant([]uint32{binary.LittleEndian.Uint32(prefix.Addr().Next().AsSlice())})
	if prefix.Addr().Is6() {
		halves = []string{"::", "8000::"}
		dns = dbus.MakeVariant([][]byte{prefix.Addr().Next().AsSlice()})
	}
	routes := make([]map[string]dbus.Variant, 0, len(halves))
	for _, half := range halves {
		routes = append(routes, map[string]dbus.Variant{
			"dest":   dbus.MakeVariant(half),
			"prefix": dbus.MakeVariant(uint32(wholeAddressSpaceHalves)),
		})
	}
	return map[string]dbus.Variant{
		"method": dbus.MakeVariant("manual"),
		"address-data": dbus.MakeVariant([]map[string]dbus.Variant{{
			"address": dbus.MakeVariant(prefix.Addr().String()),
			"prefix":  dbus.MakeVariant(uint32(prefix.Bits())),
		}}),
		"route-data":      dbus.MakeVariant(routes),
		"never-default":   dbus.MakeVariant(true),
		"dns":             dns,
		"dns-priority":    dbus.MakeVariant(int32(exclusiveDnsPriority)),
		"ignore-auto-dns": dbus.MakeVariant(true),
	}
}

func (t *userTunnel) waitUntilReady() error {
	deadline := time.Now().Add(tunReadyTimeout)
	for time.Now().Before(deadline) {
		value, err := t.bus.Object(networkManager, t.active).GetProperty(activeConnectionState)
		if err != nil {
			return err
		}
		switch state, _ := value.Value().(uint32); state {
		case activeConnectionReady:
			return nil
		case activeConnectionGone:
			return errors.New("the connection was deactivated")
		}
		time.Sleep(tunPollInterval)
	}
	return errors.New("the connection did not come up in time")
}

func (t *userTunnel) close() {
	_ = t.bus.Object(networkManager, networkManagerPath).Call(networkManager+".DeactivateConnection", 0, t.active).Err
	_ = t.bus.Close()
}

func attachTun(name string) (int, error) {
	deadline := time.Now().Add(tunReadyTimeout)
	for {
		fd, err := unix.Open("/dev/net/tun", unix.O_RDWR|unix.O_CLOEXEC, 0)
		if err != nil {
			return 0, err
		}
		request, err := unix.NewIfreq(name)
		if err != nil {
			_ = unix.Close(fd)
			return 0, err
		}
		request.SetUint16(unix.IFF_TUN | unix.IFF_NO_PI)
		_, _, errno := unix.Syscall(unix.SYS_IOCTL, uintptr(fd), uintptr(unix.TUNSETIFF), uintptr(unsafe.Pointer(request)))
		if errno == 0 {
			return fd, nil
		}
		_ = unix.Close(fd)
		if time.Now().After(deadline) {
			return 0, fmt.Errorf("could not attach to %s: %w", name, errno)
		}
		time.Sleep(tunPollInterval)
	}
}

func adoptTun(config string, fd int, resolvers []string) (string, error) {
	var parsed map[string]any
	if err := json.Unmarshal([]byte(config), &parsed); err != nil {
		return "", err
	}
	tun, ok := parsed["tun"].(map[string]any)
	if !ok {
		return "", errors.New("the config has no tun section")
	}
	tun["file-descriptor"] = fd
	tun["auto-route"] = false
	tun["strict-route"] = false
	if dns, ok := parsed["dns"]; ok && len(resolvers) > 0 {
		parsed["dns"] = withResolvers(dns, resolvers)
	}
	adopted, err := json.Marshal(parsed)
	return string(adopted), err
}

func withResolvers(value any, resolvers []string) any {
	switch typed := value.(type) {
	case string:
		if typed == systemResolver {
			return resolvers
		}
		return typed
	case []any:
		replaced := make([]any, 0, len(typed))
		for _, element := range typed {
			if element == systemResolver {
				for _, resolver := range resolvers {
					replaced = append(replaced, resolver)
				}
				continue
			}
			replaced = append(replaced, withResolvers(element, resolvers))
		}
		return replaced
	case map[string]any:
		for key, element := range typed {
			typed[key] = withResolvers(element, resolvers)
		}
		return typed
	}
	return value
}

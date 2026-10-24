package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"sync"
	"time"

	"github.com/yulbax/erkin/mihomocore"
)

type tunDevice struct {
	name      string
	addresses []netip.Prefix
}

type engine struct {
	mutex    sync.Mutex
	running  bool
	tun      tunDevice
	stateDir string
}

func check(config string) error {
	return mihomocore.Validate(config, true)
}

func (e *engine) start(config string, resolvedProxy string, resolver string) error {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	if e.running {
		return errors.New("mihomo is already running")
	}
	if err := checkProcessLookup(); err != nil {
		return err
	}
	tun := tunDeviceOf(config)
	if tun.name != "" {
		if err := releaseAdapter(tun.name, tun.addresses); err != nil {
			return err
		}
	}
	if err := mihomocore.Apply(config, true, resolvedProxy, resolver); err != nil {
		mihomocore.Shutdown()
		return err
	}
	e.running = true
	e.tun = tun
	if tun.name != "" {
		if err := pinSystemDNS(e.stateDir); err != nil {
			fmt.Fprintln(os.Stderr, "dns:", err)
		}
	}
	return nil
}

func (e *engine) stop() error {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	if !e.running {
		return nil
	}
	e.running = false
	mihomocore.Shutdown()
	if err := restoreSystemDNS(e.stateDir); err != nil {
		fmt.Fprintln(os.Stderr, "dns:", err)
	}
	if e.tun.name != "" {
		return releaseAdapter(e.tun.name, e.tun.addresses)
	}
	return nil
}

func (e *engine) isRunning() bool {
	e.mutex.Lock()
	defer e.mutex.Unlock()
	return e.running
}

func (e *engine) delay(proxy string, link string, timeout time.Duration) int {
	if !e.isRunning() {
		return -1
	}
	return int(mihomocore.URLTest(proxy, link, int32(timeout/time.Millisecond)))
}

func (e *engine) traffic() (up int64, down int64, ok bool) {
	if !e.isRunning() {
		return 0, 0, false
	}
	total := mihomocore.TotalTraffic()
	return total.Up, total.Down, true
}

func (e *engine) defaultInterfaceIndex() (int, error) {
	if !e.isRunning() {
		return 0, errors.New("mihomo is not running")
	}
	name := mihomocore.DefaultInterface()
	if name == "" {
		return 0, errors.New("no default interface")
	}
	defaultInterface, err := net.InterfaceByName(name)
	if err != nil {
		return 0, err
	}
	return defaultInterface.Index, nil
}

func tunDeviceOf(config string) tunDevice {
	var parsed struct {
		Tun struct {
			Enable       bool     `json:"enable"`
			Device       string   `json:"device"`
			Inet4Address []string `json:"inet4-address"`
			Inet6Address []string `json:"inet6-address"`
		} `json:"tun"`
	}
	if err := json.Unmarshal([]byte(config), &parsed); err != nil || !parsed.Tun.Enable {
		return tunDevice{}
	}
	device := tunDevice{name: parsed.Tun.Device}
	for _, address := range append(parsed.Tun.Inet4Address, parsed.Tun.Inet6Address...) {
		if prefix, err := netip.ParsePrefix(address); err == nil {
			device.addresses = append(device.addresses, prefix)
		}
	}
	return device
}

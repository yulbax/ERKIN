//go:build android

package mihomocore

import (
	"context"
	"errors"
	"io"
	"net"
	"net/netip"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/process"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/dns"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/tunnel"
	"golang.org/x/sync/semaphore"
)

const callbackConcurrency = 4

type Platform interface {
	Protect(fd int32) bool
	PackageName(protocol int32, source string, target string) string
}

type session struct {
	platform Platform
	limit    *semaphore.Weighted
	closed   bool
	tun      io.Closer
}

var (
	access  sync.Mutex
	current atomic.Pointer[session]
)

func Setup(homeDir string, logPath string, timezone string) {
	Init(homeDir, logPath, timezone)
	dialer.DefaultSocketHook = func(network, address string, conn syscall.RawConn) error {
		active := current.Load()
		if active == nil {
			return nil
		}
		protected := true
		if err := conn.Control(func(fd uintptr) { protected = active.protect(int32(fd)) }); err != nil {
			return err
		}
		if !protected {
			return errors.New("protect socket failed")
		}
		return nil
	}
	process.DefaultPackageNameResolver = func(metadata *C.Metadata) (string, error) {
		active := current.Load()
		source, target := metadata.RawSrcAddr, metadata.RawDstAddr
		if active == nil || source == nil || target == nil {
			return "", process.ErrInvalidNetwork
		}
		name := active.packageName(source, target)
		if name == "" {
			return "", process.ErrNotFound
		}
		return name, nil
	}
}

func Start(content string, tunFd int32, mtu int32, gateway string, dnsHijack string, resolvedProxy string, resolverAddress string, platform Platform) error {
	access.Lock()
	defer access.Unlock()
	stopLocked()
	if err := Validate(content, false); err != nil {
		return err
	}
	fd, err := syscall.Dup(int(tunFd))
	if err != nil {
		return err
	}
	options, err := tunOptions(fd, uint32(mtu), gateway, dnsHijack)
	if err != nil {
		syscall.Close(fd)
		return err
	}
	next := &session{platform: platform, limit: semaphore.NewWeighted(callbackConcurrency)}
	current.Store(next)
	if err = Apply(content, false, resolvedProxy, resolverAddress); err != nil {
		syscall.Close(fd)
		stopLocked()
		return err
	}
	listener, err := sing_tun.New(options, tunnel.Tunnel)
	if err != nil {
		syscall.Close(fd)
		stopLocked()
		return err
	}
	next.tun = listener
	return nil
}

func Stop() {
	access.Lock()
	defer access.Unlock()
	stopLocked()
}

func NotifyDnsChanged(servers string) {
	var addresses []string
	if servers != "" {
		addresses = strings.Split(servers, ",")
	}
	dns.UpdateSystemDNS(addresses)
	dns.FlushCacheWithDefaultResolver()
}

func stopLocked() {
	stopping := current.Swap(nil)
	if stopping == nil {
		return
	}
	stopping.close()
	Shutdown()
}

func (s *session) protect(fd int32) bool {
	_ = s.limit.Acquire(context.Background(), 1)
	defer s.limit.Release(1)
	if s.closed {
		return true
	}
	return s.platform.Protect(fd)
}

func (s *session) packageName(source, target net.Addr) string {
	_ = s.limit.Acquire(context.Background(), 1)
	defer s.limit.Release(1)
	if s.closed {
		return ""
	}
	var protocol int32
	switch source.Network() {
	case "tcp", "tcp4", "tcp6":
		protocol = syscall.IPPROTO_TCP
	case "udp", "udp4", "udp6":
		protocol = syscall.IPPROTO_UDP
	default:
		return ""
	}
	return s.platform.PackageName(protocol, source.String(), target.String())
}

func (s *session) close() {
	_ = s.limit.Acquire(context.Background(), callbackConcurrency)
	defer s.limit.Release(callbackConcurrency)
	s.closed = true
	if s.tun != nil {
		_ = s.tun.Close()
	}
}

func tunOptions(fd int, mtu uint32, gateway string, dnsHijack string) (LC.Tun, error) {
	var inet4, inet6 []netip.Prefix
	for _, item := range strings.Split(gateway, ",") {
		item = strings.TrimSpace(item)
		if item == "" {
			continue
		}
		prefix, err := netip.ParsePrefix(item)
		if err != nil {
			return LC.Tun{}, err
		}
		if prefix.Addr().Is4() {
			inet4 = append(inet4, prefix)
		} else {
			inet6 = append(inet6, prefix)
		}
	}
	var hijack []string
	for _, item := range strings.Split(dnsHijack, ",") {
		item = strings.TrimSpace(item)
		if item != "" {
			hijack = append(hijack, net.JoinHostPort(item, "53"))
		}
	}
	return LC.Tun{
		Enable:              true,
		Device:              sing_tun.InterfaceName,
		Stack:               C.TunGvisor,
		DNSHijack:           hijack,
		AutoRoute:           false,
		AutoDetectInterface: false,
		Inet4Address:        inet4,
		Inet6Address:        inet6,
		MTU:                 mtu,
		FileDescriptor:      fd,
	}, nil
}

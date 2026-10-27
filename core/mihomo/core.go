package mihomocore

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	_ "time/tzdata"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/dns"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

const (
	siteConcurrency    = 12
	siteDeadlineFactor = 4
	siteBodyLimit      = 1 << 20
	testConcurrency    = 10
	invalidInterface   = "<invalid>"
	logFileMode        = 0o644
)

type Traffic struct {
	Up   int64
	Down int64
}

type LogSink interface {
	Write(level string, message string)
}

var (
	logOnce   sync.Once
	sinkMutex sync.RWMutex
	logSink   LogSink
	applied   atomic.Bool
)

func SetLogSink(sink LogSink) {
	sinkMutex.Lock()
	defer sinkMutex.Unlock()
	logSink = sink
}

func Init(homeDir string, logPath string, timezone string) {
	if timezone != "" {
		if location, err := time.LoadLocation(timezone); err == nil {
			time.Local = location
		}
	}
	C.SetHomeDir(homeDir)
	adapter.UnifiedDelay.Store(true)
	logOnce.Do(func() { go writeLog(logPath) })
}

func Validate(content string, keepTun bool) error {
	_, err := parseConfig(content, keepTun)
	return err
}

func Apply(content string, keepTun bool, resolvedProxy string, resolverAddress string) error {
	parsed, err := parseConfig(content, keepTun)
	if err != nil {
		return err
	}
	var server *dns.NameServer
	if resolverAddress != "" {
		parsedServer, err := resolverNameServer(resolverAddress, resolvedProxy)
		if err != nil {
			return err
		}
		server = &parsedServer
	}
	executor.ApplyConfig(parsed, true)
	applied.Store(true)
	if server != nil {
		resolveBeforeDial(resolvedProxy, *server, parsed.DNS, parsed.General.IPv6)
	}
	return nil
}

func Shutdown() {
	applied.Store(false)
	closeConnections()
	executor.Shutdown()
	listener.ReCreateTun(LC.Tun{}, tunnel.Tunnel)
}

func URLTest(proxyName string, url string, timeoutMs int32) int32 {
	proxy, loaded := tunnel.Proxies()[proxyName]
	if !loaded {
		return -1
	}
	return measure(proxy, url, timeoutMs)
}

func TestProxies(proxies string, url string, timeoutMs int32, ipv6 bool) (string, error) {
	if !applied.Load() {
		resolver.DisableIPv6 = !ipv6
	}
	var mappings []map[string]any
	if err := json.Unmarshal([]byte(proxies), &mappings); err != nil {
		return "", err
	}
	results := make(map[string]int32, len(mappings))
	var resultsAccess sync.Mutex
	var group sync.WaitGroup
	slots := make(chan struct{}, testConcurrency)
	for _, mapping := range mappings {
		name, _ := mapping["name"].(string)
		proxy, err := adapter.ParseProxy(mapping)
		if err != nil {
			resultsAccess.Lock()
			results[name] = -1
			resultsAccess.Unlock()
			continue
		}
		group.Add(1)
		go func() {
			defer group.Done()
			slots <- struct{}{}
			defer func() { <-slots }()
			delay := measure(proxy, url, timeoutMs)
			_ = proxy.Close()
			resultsAccess.Lock()
			results[name] = delay
			resultsAccess.Unlock()
		}()
	}
	group.Wait()
	encoded, err := json.Marshal(results)
	return string(encoded), err
}

func Version() string {
	return C.Version
}

func CurrentTraffic() *Traffic {
	up, down := statistic.DefaultManager.Now()
	return &Traffic{Up: up, Down: down}
}

func TotalTraffic() *Traffic {
	up, down := statistic.DefaultManager.Total()
	return &Traffic{Up: up, Down: down}
}

func DefaultInterface() string {
	if name := dialer.DefaultInterface.Load(); name != "" {
		return name
	}
	finder := dialer.DefaultInterfaceFinder.Load()
	if finder == nil {
		return ""
	}
	if name := finder.FindInterfaceName(netip.IPv4Unspecified()); name != invalidInterface {
		return name
	}
	return ""
}

type resolvingProxy struct {
	C.Proxy
	resolver resolver.Resolver
	ipv6     bool
}

func (p *resolvingProxy) DialContext(ctx context.Context, metadata *C.Metadata) (C.Conn, error) {
	resolved, err := p.resolve(ctx, metadata)
	if err != nil {
		return nil, err
	}
	return p.Proxy.DialContext(ctx, resolved)
}

func (p *resolvingProxy) ListenPacketContext(ctx context.Context, metadata *C.Metadata) (C.PacketConn, error) {
	resolved, err := p.resolve(ctx, metadata)
	if err != nil {
		return nil, err
	}
	conn, err := p.Proxy.ListenPacketContext(ctx, resolved)
	if err != nil {
		return nil, err
	}
	return &resolvingPacketConn{PacketConn: conn, proxy: p}, nil
}

func (p *resolvingProxy) resolve(ctx context.Context, metadata *C.Metadata) (*C.Metadata, error) {
	if metadata.Host == "" {
		return metadata, nil
	}
	ip, err := p.lookup(ctx, metadata.Host)
	if err != nil {
		return nil, err
	}
	resolved := metadata.Clone()
	resolved.DstIP = ip
	resolved.Host = ""
	return resolved, nil
}

func (p *resolvingProxy) lookup(ctx context.Context, host string) (netip.Addr, error) {
	if p.ipv6 {
		return resolver.ResolveIPWithResolver(ctx, host, p.resolver)
	}
	return resolver.ResolveIPv4WithResolver(ctx, host, p.resolver)
}

type resolvingPacketConn struct {
	C.PacketConn
	proxy *resolvingProxy
}

func (c *resolvingPacketConn) ResolveUDP(ctx context.Context, metadata *C.Metadata) error {
	if metadata.Resolved() {
		return nil
	}
	ip, err := c.proxy.lookup(ctx, metadata.Host)
	if err != nil {
		return fmt.Errorf("can't resolve ip: %w", err)
	}
	metadata.DstIP = ip
	return nil
}

var resolverTransports = map[string]struct{ network, port string }{
	"udp": {"", "53"},
	"tls": {"tls", "853"},
}

func resolverNameServer(address string, proxy string) (dns.NameServer, error) {
	parsed, err := url.Parse(address)
	if err != nil {
		return dns.NameServer{}, err
	}
	transport, known := resolverTransports[parsed.Scheme]
	if !known {
		return dns.NameServer{}, fmt.Errorf("unsupported resolver %q", address)
	}
	ip, err := netip.ParseAddr(parsed.Hostname())
	if err != nil {
		return dns.NameServer{}, fmt.Errorf("resolver %q is not an IP address", address)
	}
	port := parsed.Port()
	if port == "" {
		port = transport.port
	}
	return dns.NameServer{Net: transport.network, Addr: net.JoinHostPort(ip.String(), port), ProxyName: proxy}, nil
}

func resolveBeforeDial(name string, server dns.NameServer, servers *config.DNS, ipv6 bool) {
	current := tunnel.Proxies()
	proxy, loaded := current[name]
	if !loaded {
		return
	}
	var defaults []dns.NameServer
	if servers != nil {
		defaults = servers.DefaultNameserver
	}
	resolvers := dns.NewResolver(dns.Config{
		Main:    []dns.NameServer{server},
		Default: defaults,
		IPv6:    ipv6,
	})
	updated := make(map[string]C.Proxy, len(current))
	for key, value := range current {
		updated[key] = value
	}
	updated[name] = &resolvingProxy{Proxy: proxy, resolver: resolvers.Resolver, ipv6: ipv6}
	tunnel.UpdateProxies(updated, tunnel.Providers())
}

func parseConfig(content string, keepTun bool) (*config.Config, error) {
	raw, err := config.UnmarshalRawConfig([]byte(content))
	if err != nil {
		return nil, err
	}
	raw.ExternalController = ""
	raw.ExternalControllerTLS = ""
	raw.ExternalControllerUnix = ""
	raw.ExternalControllerPipe = ""
	raw.Profile.StoreSelected = false
	raw.Profile.StoreFakeIP = true
	if !keepTun {
		raw.Interface = ""
		raw.RoutingMark = 0
		raw.Tun.Enable = false
		raw.Tun.AutoRoute = false
		raw.Tun.AutoDetectInterface = false
	}
	return config.ParseRawConfig(raw)
}

func TestSites(port int32, resolverAddress string, sites string, timeoutMs int32, ipv6 bool) (string, error) {
	proxy, err := adapter.ParseProxy(map[string]any{
		"name":   "byedpi-test",
		"type":   "socks5",
		"server": "127.0.0.1",
		"port":   int(port),
		"udp":    true,
	})
	if err != nil {
		return "", err
	}
	defer proxy.Close()
	server, err := resolverNameServer(resolverAddress, "")
	if err != nil {
		return "", err
	}
	server.ProxyAdapter = proxy
	resolvers := dns.NewResolver(dns.Config{Main: []dns.NameServer{server}, IPv6: ipv6})
	defer resolvers.Resolver.ResetConnection()
	tested := &resolvingProxy{Proxy: proxy, resolver: resolvers.Resolver, ipv6: ipv6}
	hosts := strings.Fields(sites)
	results := make(map[string]bool, len(hosts))
	var resultsAccess sync.Mutex
	var group sync.WaitGroup
	slots := make(chan struct{}, siteConcurrency)
	for _, host := range hosts {
		group.Add(1)
		go func() {
			defer group.Done()
			slots <- struct{}{}
			defer func() { <-slots }()
			target := host
			if !strings.Contains(host, "://") {
				target = "https://" + host
			}
			reachable := fetchThrough(tested, target, timeoutMs)
			resultsAccess.Lock()
			results[host] = reachable
			resultsAccess.Unlock()
		}()
	}
	group.Wait()
	encoded, err := json.Marshal(results)
	return string(encoded), err
}

func fetchThrough(proxy *resolvingProxy, url string, timeoutMs int32) bool {
	idle := time.Duration(timeoutMs) * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), idle*siteDeadlineFactor)
	defer cancel()
	transport := &http.Transport{
		DisableKeepAlives:  true,
		DisableCompression: true,
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			metadata := &C.Metadata{NetWork: C.TCP}
			if err := metadata.SetRemoteAddress(address); err != nil {
				return nil, err
			}
			dialCtx, cancelDial := context.WithTimeout(ctx, idle)
			defer cancelDial()
			conn, err := proxy.DialContext(dialCtx, metadata)
			if err != nil {
				return nil, err
			}
			return &idleConn{Conn: conn, idle: idle}, nil
		},
	}
	defer transport.CloseIdleConnections()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return false
	}
	response, err := (&http.Client{Transport: transport}).Do(request)
	if err != nil {
		return false
	}
	defer response.Body.Close()
	declared := response.ContentLength
	limit := declared
	if limit <= 0 {
		limit = siteBodyLimit
	}
	read, _ := io.Copy(io.Discard, io.LimitReader(response.Body, limit))
	return declared <= 0 || read >= declared
}

type idleConn struct {
	net.Conn
	idle time.Duration
}

func (c *idleConn) Read(b []byte) (int, error) {
	if err := c.Conn.SetReadDeadline(time.Now().Add(c.idle)); err != nil {
		return 0, err
	}
	return c.Conn.Read(b)
}

func measure(proxy C.Proxy, url string, timeoutMs int32) int32 {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()
	delay, err := proxy.URLTest(ctx, url, nil)
	if err != nil || delay == 0 {
		return -1
	}
	return int32(delay)
}

func writeLog(path string) {
	file := openLogFile(path)
	for event := range log.Subscribe() {
		if event.LogLevel < log.Level() {
			continue
		}
		if file != nil {
			_, _ = file.WriteString(time.Now().Format("2006-01-02 15:04:05.000") + " " + event.LogLevel.String() + " " + event.Payload + "\n")
		}
		sinkMutex.RLock()
		sink := logSink
		sinkMutex.RUnlock()
		if sink != nil {
			sink.Write(event.LogLevel.String(), event.Payload)
		}
	}
}

func openLogFile(path string) *os.File {
	if path == "" {
		return nil
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, logFileMode)
	if err != nil {
		return nil
	}
	_ = file.Chmod(logFileMode)
	return file
}

func closeConnections() {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		_ = c.Close()
		return true
	})
}

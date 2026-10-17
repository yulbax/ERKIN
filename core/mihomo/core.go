package mihomocore

import (
	"context"
	"encoding/json"
	"net/netip"
	"os"
	"sync"
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
	testConcurrency  = 10
	invalidInterface = "<invalid>"
	logFileMode      = 0o644
)

type Traffic struct {
	Up   int64
	Down int64
}

var logOnce sync.Once

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
	executor.ApplyConfig(parsed, true)
	if resolverAddress != "" {
		resolveBeforeDial(resolvedProxy, parsed.DNS, parsed.General.IPv6)
	}
	return nil
}

func Shutdown() {
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

func TestProxies(proxies string, url string, timeoutMs int32) (string, error) {
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
	return p.Proxy.ListenPacketContext(ctx, resolved)
}

func (p *resolvingProxy) resolve(ctx context.Context, metadata *C.Metadata) (*C.Metadata, error) {
	if metadata.Host == "" {
		return metadata, nil
	}
	resolve := resolver.ResolveIPv4WithResolver
	if p.ipv6 {
		resolve = resolver.ResolveIPWithResolver
	}
	ip, err := resolve(ctx, metadata.Host, p.resolver)
	if err != nil {
		return nil, err
	}
	resolved := metadata.Clone()
	resolved.DstIP = ip
	resolved.Host = ""
	return resolved, nil
}

func resolveBeforeDial(name string, servers *config.DNS, ipv6 bool) {
	if name == "" || servers == nil || len(servers.NameServer) == 0 {
		return
	}
	current := tunnel.Proxies()
	proxy, loaded := current[name]
	if !loaded {
		return
	}
	main := make([]dns.NameServer, 0, len(servers.NameServer))
	for _, server := range servers.NameServer {
		server.ProxyAdapter = nil
		server.ProxyName = name
		main = append(main, server)
	}
	resolvers := dns.NewResolver(dns.Config{
		Main:    main,
		Default: servers.DefaultNameserver,
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
	if path == "" {
		return
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, logFileMode)
	if err != nil {
		return
	}
	_ = file.Chmod(logFileMode)
	for event := range log.Subscribe() {
		if event.LogLevel < log.Level() {
			continue
		}
		_, _ = file.WriteString(time.Now().Format("2006-01-02 15:04:05") + " " + event.LogLevel.String() + " " + event.Payload + "\n")
	}
}

func closeConnections() {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		_ = c.Close()
		return true
	})
}

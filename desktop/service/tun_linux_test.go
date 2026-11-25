package main

import (
	"encoding/json"
	"net/netip"
	"reflect"
	"testing"

	"github.com/godbus/dbus/v5"
)

func TestTheNetworkResolversSkipTheTunnelAndBracketIPv6(t *testing.T) {
	entry := func(name string, servers ...string) map[string]dbus.Variant {
		return map[string]dbus.Variant{"interface": dbus.MakeVariant(name), "nameservers": dbus.MakeVariant(servers)}
	}
	got := resolversOf([]map[string]dbus.Variant{
		entry("ERKIN", "172.19.0.2"),
		entry("wlan0", "192.168.1.1"),
		entry("wlan0", "fd33:9d52:d96d::1", "192.168.1.1", "not-an-address"),
	}, "ERKIN")
	if want := []string{"192.168.1.1", "[fd33:9d52:d96d::1]"}; !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v", got)
	}
}

func TestTheAdoptedConfigUsesTheDescriptorAndTheNetworkResolvers(t *testing.T) {
	config := `{"tun":{"enable":true,"auto-route":true,"strict-route":true},` +
		`"dns":{"nameserver":["https://dns.example/dns-query"],"direct-nameserver":["system://"],` +
		`"nameserver-policy":{"+.lan":"system://","+.corp":["system://","10.0.0.1"]}}}`
	adopted, err := adoptTun(config, 7, []string{"192.168.1.1", "[fd33::1]"})
	if err != nil {
		t.Fatal(err)
	}
	var parsed struct {
		Tun map[string]any `json:"tun"`
		DNS struct {
			Nameserver []string            `json:"nameserver"`
			Direct     []string            `json:"direct-nameserver"`
			Policy     map[string][]string `json:"nameserver-policy"`
		} `json:"dns"`
	}
	if err = json.Unmarshal([]byte(adopted), &parsed); err != nil {
		t.Fatal(err)
	}
	if parsed.Tun["file-descriptor"] != float64(7) || parsed.Tun["auto-route"] != false || parsed.Tun["strict-route"] != false {
		t.Fatalf("tun %v", parsed.Tun)
	}
	resolvers := []string{"192.168.1.1", "[fd33::1]"}
	if !reflect.DeepEqual(parsed.DNS.Direct, resolvers) || !reflect.DeepEqual(parsed.DNS.Policy["+.lan"], resolvers) ||
		!reflect.DeepEqual(parsed.DNS.Policy["+.corp"], append(resolvers, "10.0.0.1")) ||
		!reflect.DeepEqual(parsed.DNS.Nameserver, []string{"https://dns.example/dns-query"}) {
		t.Fatalf("dns %+v", parsed.DNS)
	}
}

func TestTheTunnelAdoptsOnlyNewNonEmptyResolvers(t *testing.T) {
	tunnel := &userTunnel{
		config:    `{"tun":{"enable":true},"dns":{"direct-nameserver":["system://"]}}`,
		fd:        7,
		resolvers: []string{"192.168.1.1"},
	}
	if _, changed := tunnel.adoptResolvers(nil); changed {
		t.Fatal("a network without resolvers replaced the last known ones")
	}
	if _, changed := tunnel.adoptResolvers([]string{"192.168.1.1"}); changed {
		t.Fatal("the same resolvers were applied again")
	}
	tunnel.resolvers = []string{"192.168.1.1", "[fd33::1]"}
	if _, changed := tunnel.adoptResolvers([]string{"[fd33::1]", "192.168.1.1"}); changed {
		t.Fatal("reordered resolvers were applied again")
	}
	adopted, changed := tunnel.adoptResolvers([]string{"10.0.0.1"})
	if !changed {
		t.Fatal("new resolvers were not applied")
	}
	var parsed struct {
		Tun struct {
			FileDescriptor int `json:"file-descriptor"`
		} `json:"tun"`
		DNS struct {
			Direct []string `json:"direct-nameserver"`
		} `json:"dns"`
	}
	if err := json.Unmarshal([]byte(adopted), &parsed); err != nil {
		t.Fatal(err)
	}
	if parsed.Tun.FileDescriptor != 7 || !reflect.DeepEqual(parsed.DNS.Direct, []string{"10.0.0.1"}) {
		t.Fatalf("got %s", adopted)
	}
	if !reflect.DeepEqual(tunnel.resolvers, []string{"10.0.0.1"}) {
		t.Fatalf("remembered %v", tunnel.resolvers)
	}
}

func TestOnlyOurTunnelProfileIsTakenForStale(t *testing.T) {
	profile := func(id, kind, name string) map[string]map[string]dbus.Variant {
		return map[string]map[string]dbus.Variant{"connection": {
			"id": dbus.MakeVariant(id), "type": dbus.MakeVariant(kind), "interface-name": dbus.MakeVariant(name),
		}}
	}
	if !isOwnTunnel(profile(serviceName, tunType, "ERKIN"), "ERKIN") {
		t.Fatal("our leftover tunnel profile was not recognised")
	}
	for _, other := range []map[string]map[string]dbus.Variant{
		profile("Wired connection 1", "802-3-ethernet", "eth0"),
		profile(serviceName, "wireguard", "ERKIN"),
		profile(serviceName, tunType, "tun0"),
		{},
	} {
		if isOwnTunnel(other, "ERKIN") {
			t.Fatalf("took %v for our tunnel", other)
		}
	}
}

func TestTheMissingVirtualDeviceErrorIsRecognisedByName(t *testing.T) {
	if name := dbusErrorName(dbus.Error{Name: unknownDevice}); name != unknownDevice {
		t.Fatalf("got %q", name)
	}
	if name := dbusErrorName(&dbus.Error{Name: unknownDevice}); name != unknownDevice {
		t.Fatalf("got %q from a pointer", name)
	}
	if name := dbusErrorName(nil); name != "" {
		t.Fatalf("got %q for no error", name)
	}
}

func TestTheTunnelTakesEveryDomainForItsDns(t *testing.T) {
	prefix := netip.MustParsePrefix("172.19.0.1/30")
	for _, family := range []netip.Prefix{prefix, netip.MustParsePrefix("fdfe:dcba:9876::1/126")} {
		settings := familySettings(family)
		domains, _ := settings["dns-search"].Value().([]string)
		if !reflect.DeepEqual(domains, []string{"~."}) {
			t.Fatalf("%s: dns-search %v", family, domains)
		}
		if exclusive, _ := settings["dns-priority"].Value().(int32); exclusive >= 0 {
			t.Fatalf("%s: dns-priority %d", family, exclusive)
		}
	}
}

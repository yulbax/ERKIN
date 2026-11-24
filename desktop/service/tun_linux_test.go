package main

import (
	"encoding/json"
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

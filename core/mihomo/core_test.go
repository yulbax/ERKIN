package mihomocore

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"

	D "github.com/miekg/dns"
)

func TestResolverNameServerMapsTransportsAndPorts(t *testing.T) {
	cases := map[string]string{
		"tls://1.1.1.1:853":                  "tls 1.1.1.1:853",
		"udp://1.1.1.1:53":                   "udp 1.1.1.1:53",
		"tls://1.1.1.1":                      "tls 1.1.1.1:853",
		"udp://9.9.9.9":                      "udp 9.9.9.9:53",
		"tls://[2606:4700:4700::1111]":       "tls [2606:4700:4700::1111]:853",
		"udp://[2606:4700:4700::1111]:53":    "udp [2606:4700:4700::1111]:53",
		"tls://[2606:4700:4700::1111]:10853": "tls [2606:4700:4700::1111]:10853",
	}
	for address, want := range cases {
		server, err := resolverNameServer(address, "byedpi")
		if err != nil {
			t.Fatalf("%s: %v", address, err)
		}
		network := server.Net
		if network == "" {
			network = "udp"
		}
		if got := network + " " + server.Addr; got != want || server.ProxyName != "byedpi" {
			t.Fatalf("%s: got %q via %q, want %q", address, got, server.ProxyName, want)
		}
	}
}

func TestResolverNameServerRejectsHostNamesAndOtherProtocols(t *testing.T) {
	for _, address := range []string{"tls://dns.google", "https://1.1.1.1/dns-query", "quic://1.1.1.1", "1.1.1.1"} {
		if _, err := resolverNameServer(address, "byedpi"); err == nil {
			t.Fatalf("%s: expected an error", address)
		}
	}
}

func TestByeDpiTrafficResolvesThroughItsOwnResolver(t *testing.T) {
	site := listenTCP(t)
	go http.Serve(site, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	resolverPort := serveDNS(t, "probe.test.", net.ParseIP("127.0.0.1"))
	byeDpiPort, probePort := freePort(t), freePort(t)

	config := fmt.Sprintf(`
mode: rule
log-level: silent
ipv6: false
dns:
  enable: true
  ipv6: false
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  default-nameserver: ["udp://127.0.0.1:1"]
  nameserver: ["udp://127.0.0.1:1#DIRECT"]
listeners:
  - {name: fake-byedpi, type: socks, listen: 127.0.0.1, port: %d, udp: true}
  - {name: probe-in, type: socks, listen: 127.0.0.1, port: %d, udp: true}
proxies:
  - {name: byedpi, type: socks5, server: 127.0.0.1, port: %d, udp: true}
rules:
  - IN-NAME,fake-byedpi,DIRECT
  - IN-NAME,probe-in,byedpi
  - MATCH,REJECT
`, byeDpiPort, probePort, byeDpiPort)
	if err := Apply(config, false, "byedpi", fmt.Sprintf("udp://127.0.0.1:%d", resolverPort)); err != nil {
		t.Fatal(err)
	}
	defer Shutdown()

	client := http.Client{
		Timeout:   10 * time.Second,
		Transport: &http.Transport{Proxy: http.ProxyURL(&url.URL{Scheme: "socks5", Host: fmt.Sprintf("127.0.0.1:%d", probePort)})},
	}
	response, err := client.Get(fmt.Sprintf("http://probe.test:%d/", site.Addr().(*net.TCPAddr).Port))
	if err != nil {
		t.Fatal(err)
	}
	_, _ = io.Copy(io.Discard, response.Body)
	_ = response.Body.Close()
	if response.StatusCode != http.StatusNoContent {
		t.Fatalf("status %d", response.StatusCode)
	}
}

func TestSitesAreCheckedThroughTheGivenPortAndResolver(t *testing.T) {
	const declared = 64 << 10
	site := listenTCP(t)
	go http.Serve(site, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/chunked" {
			w.Write(make([]byte, 1024))
			return
		}
		w.Header().Set("Content-Length", strconv.Itoa(declared))
		if r.URL.Path == "/full" {
			w.Write(make([]byte, declared))
			return
		}
		w.Write(make([]byte, 16<<10))
		w.(http.Flusher).Flush()
		<-r.Context().Done()
	}))
	resolverPort := serveDNS(t, "probe.test.", net.ParseIP("127.0.0.1"))
	socksPort := freePort(t)
	config := fmt.Sprintf(`
mode: rule
log-level: silent
ipv6: false
dns:
  enable: true
  ipv6: false
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  default-nameserver: ["udp://127.0.0.1:1"]
  nameserver: ["udp://127.0.0.1:1#DIRECT"]
listeners:
  - {name: fake-byedpi, type: socks, listen: 127.0.0.1, port: %d, udp: true}
rules:
  - IN-NAME,fake-byedpi,DIRECT
  - MATCH,REJECT
`, socksPort)
	if err := Apply(config, false, "", ""); err != nil {
		t.Fatal(err)
	}
	defer Shutdown()

	port := site.Addr().(*net.TCPAddr).Port
	full := fmt.Sprintf("http://probe.test:%d/full", port)
	chunked := fmt.Sprintf("http://probe.test:%d/chunked", port)
	frozen := fmt.Sprintf("http://probe.test:%d/frozen", port)
	unknown := fmt.Sprintf("http://missing.test:%d/", port)
	sites := strings.Join([]string{full, chunked, frozen, unknown}, " ")
	encoded, err := TestSites(int32(socksPort), fmt.Sprintf("udp://127.0.0.1:%d", resolverPort), sites, 1000, false)
	if err != nil {
		t.Fatal(err)
	}
	var results map[string]bool
	if err := json.Unmarshal([]byte(encoded), &results); err != nil {
		t.Fatal(err)
	}
	if !results[full] || !results[chunked] || results[frozen] || results[unknown] || len(results) != 4 {
		t.Fatalf("unexpected results %v", results)
	}
}

func listenTCP(t *testing.T) net.Listener {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	return listener
}

func freePort(t *testing.T) int {
	listener := listenTCP(t)
	port := listener.Addr().(*net.TCPAddr).Port
	_ = listener.Close()
	return port
}

func serveDNS(t *testing.T, name string, ip net.IP) int {
	conn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	server := &D.Server{PacketConn: conn, Handler: D.HandlerFunc(func(w D.ResponseWriter, request *D.Msg) {
		reply := new(D.Msg)
		reply.SetReply(request)
		for _, question := range request.Question {
			if question.Name == name && question.Qtype == D.TypeA {
				reply.Answer = append(reply.Answer, &D.A{
					Hdr: D.RR_Header{Name: name, Rrtype: D.TypeA, Class: D.ClassINET, Ttl: 60},
					A:   ip,
				})
			}
		}
		_ = w.WriteMsg(reply)
	})}
	go func() { _ = server.ActivateAndServe() }()
	t.Cleanup(func() { _ = server.Shutdown() })
	return conn.LocalAddr().(*net.UDPAddr).Port
}

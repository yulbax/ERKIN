package mihomocore

import (
	"fmt"
	"net"
	"net/http"
	"testing"
	"time"
)

func TestAGroupReportsHowManyOfItsServersAnswerOnceTheCoreTestedThem(t *testing.T) {
	site := listenTCP(t)
	go http.Serve(site, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	link := fmt.Sprintf("http://127.0.0.1:%d/", site.Addr().(*net.TCPAddr).Port)
	config := fmt.Sprintf(`
mode: rule
log-level: silent
ipv6: false
proxies:
  - {name: p1, type: direct}
  - {name: p2, type: socks5, server: 127.0.0.1, port: %d}
proxy-groups:
  - {name: g9, type: load-balance, strategy: consistent-hashing, proxies: [p1, p2], url: "%s", interval: 60, timeout: 2000, lazy: false}
rules:
  - MATCH,g9
`, freePort(t), link)
	if err := Apply(config, false, "", ""); err != nil {
		t.Fatal(err)
	}
	defer Shutdown()

	if health := GroupHealthOf("p1", link); health != nil {
		t.Fatalf("a single server is not a group, got %+v", health)
	}
	deadline := time.Now().Add(5 * time.Second)
	health := GroupHealthOf("g9", link)
	for health == nil && time.Now().Before(deadline) {
		time.Sleep(50 * time.Millisecond)
		health = GroupHealthOf("g9", link)
	}
	if health == nil || *health != (GroupHealth{Alive: 1, Total: 2}) {
		t.Fatalf("expected one of two servers to answer, got %+v", health)
	}
}

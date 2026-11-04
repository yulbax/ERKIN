package mihomocore

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	C "github.com/metacubex/mihomo/constant"
)

func TestTrafficIsKeptPerAppSiteAndRouteAfterConnectionsClose(t *testing.T) {
	ledger := newTrafficLedger(10)
	metadata := &C.Metadata{NetWork: C.TCP, Type: C.TUN, Host: "speed.cloudflare.com", DstPort: 443, Process: "com.android.chrome"}
	first := ledger.open(metadata, "p1", time.UnixMilli(10))
	second := ledger.open(metadata, "p1", time.UnixMilli(20))
	first.down.Add(25_000_000)
	second.up.Add(1_000)
	ledger.close(first, time.UnixMilli(30))

	rows := ledger.snapshot(time.UnixMilli(40)).Rows
	if len(rows) != 1 {
		t.Fatalf("expected one row, got %+v", rows)
	}
	row := rows[0]
	if row.Down != 25_000_000 || row.Up != 1_000 || row.Connections != 2 || row.Active != 1 || row.Route != "p1" || row.Inbound != "Tun" || row.LastSeen != 40 {
		t.Fatalf("unexpected row %+v", row)
	}
	ledger.close(second, time.UnixMilli(50))
	if closed := ledger.snapshot(time.UnixMilli(60)).Rows[0]; closed.Active != 0 || closed.LastSeen != 50 {
		t.Fatalf("closed row should keep its totals and last time, got %+v", closed)
	}
}

func TestRejectedQuicIsNotCountedAndAddressesWithoutAHostAreKeyedByIp(t *testing.T) {
	ledger := newTrafficLedger(10)
	if row := ledger.open(&C.Metadata{NetWork: C.UDP, DstIP: netip.MustParseAddr("1.2.3.4"), DstPort: 443}, rejectProxyName, time.Now()); row != nil {
		t.Fatal("QUIC rejects must not be counted")
	}
	ledger.open(&C.Metadata{NetWork: C.TCP, Type: C.INNER, DstIP: netip.MustParseAddr("1.1.1.1"), DstPort: 853}, "byedpi", time.Now())
	rows := ledger.snapshot(time.Now()).Rows
	if len(rows) != 1 || rows[0].IP != "1.1.1.1" || rows[0].Inbound != "Inner" || rows[0].Route != "byedpi" {
		t.Fatalf("unexpected rows %+v", rows)
	}
}

func TestTheOldestFinishedRowMakesRoomWhenTheLedgerIsFull(t *testing.T) {
	ledger := newTrafficLedger(2)
	old := ledger.open(&C.Metadata{Host: "old.example"}, "DIRECT", time.UnixMilli(1))
	ledger.close(old, time.UnixMilli(2))
	ledger.open(&C.Metadata{Host: "busy.example"}, "DIRECT", time.UnixMilli(3))
	ledger.open(&C.Metadata{Host: "new.example"}, "DIRECT", time.UnixMilli(4))

	hosts := map[string]bool{}
	for _, row := range ledger.snapshot(time.UnixMilli(5)).Rows {
		hosts[row.Host] = true
	}
	if len(hosts) != 2 || hosts["old.example"] || !hosts["busy.example"] || !hosts["new.example"] {
		t.Fatalf("expected the finished old row to be evicted, got %v", hosts)
	}
}

func TestConnectionTableIsValidJsonWithoutTraffic(t *testing.T) {
	encoded, err := Connections()
	if err != nil {
		t.Fatal(err)
	}
	var table trafficTable
	if err = json.Unmarshal([]byte(encoded), &table); err != nil || table.Rows == nil {
		t.Fatalf("bad table %q: %v", encoded, err)
	}
}

func TestACountedProxyReportsTheBytesOfARealConnection(t *testing.T) {
	server, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()
	go func() {
		conn, acceptErr := server.Accept()
		if acceptErr != nil {
			return
		}
		defer conn.Close()
		request := make([]byte, 5)
		_, _ = io.ReadFull(conn, request)
		_, _ = conn.Write(make([]byte, 64_000))
	}()
	saved := traffic
	traffic = newTrafficLedger(10)
	defer func() { traffic = saved }()

	port := server.Addr().(*net.TCPAddr).Port
	metadata := &C.Metadata{NetWork: C.TCP, Type: C.TUN, DstIP: netip.MustParseAddr("127.0.0.1"), DstPort: uint16(port), Process: "org.example"}
	proxy := countingProxy{adapter.NewProxy(outbound.NewDirect())}
	conn, err := proxy.DialContext(context.Background(), metadata)
	if err != nil {
		t.Fatal(err)
	}
	if _, err = conn.Write([]byte("hello")); err != nil {
		t.Fatal(err)
	}
	if _, err = io.Copy(io.Discard, conn); err != nil {
		t.Fatal(err)
	}
	_ = conn.Close()

	rows := traffic.snapshot(time.Now()).Rows
	if len(rows) != 1 || rows[0].Up != 5 || rows[0].Down != 64_000 || rows[0].Active != 0 || rows[0].Route != "DIRECT" {
		t.Fatalf("unexpected rows %+v", rows)
	}
}

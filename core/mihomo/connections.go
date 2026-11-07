package mihomocore

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/metacubex/mihomo/common/buf"
	N "github.com/metacubex/mihomo/common/net"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

const (
	trafficRowLimit = 2000
	evictionShare   = 10
	quicPort        = 443
)

type trafficKey struct {
	process string
	path    string
	host    string
	ip      string
	inbound string
	inName  string
	route   string
}

type trafficRow struct {
	key         trafficKey
	up          atomic.Int64
	down        atomic.Int64
	active      atomic.Int32
	connections atomic.Int64
	lastSeen    atomic.Int64
	rule        string
	payload     string
}

type trafficInfo struct {
	Process     string `json:"process,omitempty"`
	Path        string `json:"path,omitempty"`
	Host        string `json:"host,omitempty"`
	IP          string `json:"ip,omitempty"`
	Inbound     string `json:"inbound,omitempty"`
	InName      string `json:"inName,omitempty"`
	Route       string `json:"route"`
	Rule        string `json:"rule,omitempty"`
	Payload     string `json:"payload,omitempty"`
	Up          int64  `json:"up"`
	Down        int64  `json:"down"`
	Active      int32  `json:"active"`
	Connections int64  `json:"connections"`
	LastSeen    int64  `json:"lastSeen"`
}

type trafficTable struct {
	Rows []trafficInfo `json:"rows"`
}

type trafficLedger struct {
	access sync.Mutex
	rows   map[trafficKey]*trafficRow
	limit  int
}

var traffic = newTrafficLedger(trafficRowLimit)

func newTrafficLedger(limit int) *trafficLedger {
	return &trafficLedger{rows: map[trafficKey]*trafficRow{}, limit: limit}
}

func (l *trafficLedger) open(metadata *C.Metadata, route string, now time.Time) *trafficRow {
	if route == rejectProxyName && metadata.NetWork == C.UDP && metadata.DstPort == quicPort {
		return nil
	}
	key := trafficKey{
		process: metadata.Process,
		path:    metadata.ProcessPath,
		host:    hostOf(metadata),
		inbound: metadata.Type.String(),
		inName:  metadata.InName,
		route:   route,
	}
	if key.host == "" {
		key.ip = ipOf(metadata)
	}
	l.access.Lock()
	defer l.access.Unlock()
	row, found := l.rows[key]
	if !found {
		l.evictLocked()
		row = &trafficRow{key: key}
		l.rows[key] = row
	}
	row.active.Add(1)
	row.connections.Add(1)
	row.lastSeen.Store(now.UnixMilli())
	return row
}

func (l *trafficLedger) evictLocked() {
	if len(l.rows) < l.limit {
		return
	}
	idle := make([]*trafficRow, 0, len(l.rows))
	for _, row := range l.rows {
		if row.active.Load() == 0 {
			idle = append(idle, row)
		}
	}
	sort.Slice(idle, func(i, j int) bool { return idle[i].lastSeen.Load() < idle[j].lastSeen.Load() })
	for _, row := range idle[:min(len(idle), max(1, l.limit/evictionShare))] {
		delete(l.rows, row.key)
	}
}

func (l *trafficLedger) close(row *trafficRow, now time.Time) {
	row.active.Add(-1)
	row.lastSeen.Store(now.UnixMilli())
}

func (l *trafficLedger) learnRule(row *trafficRow, rule string, payload string) {
	l.access.Lock()
	defer l.access.Unlock()
	row.rule, row.payload = rule, payload
}

func (l *trafficLedger) snapshot(now time.Time) trafficTable {
	l.access.Lock()
	defer l.access.Unlock()
	table := trafficTable{Rows: make([]trafficInfo, 0, len(l.rows))}
	for _, row := range l.rows {
		active := row.active.Load()
		lastSeen := row.lastSeen.Load()
		if active > 0 {
			lastSeen = now.UnixMilli()
		}
		table.Rows = append(table.Rows, trafficInfo{
			Process:     row.key.process,
			Path:        row.key.path,
			Host:        row.key.host,
			IP:          row.key.ip,
			Inbound:     row.key.inbound,
			InName:      row.key.inName,
			Route:       row.key.route,
			Rule:        row.rule,
			Payload:     row.payload,
			Up:          row.up.Load(),
			Down:        row.down.Load(),
			Active:      active,
			Connections: row.connections.Load(),
			LastSeen:    lastSeen,
		})
	}
	return table
}

func (l *trafficLedger) clear() {
	l.access.Lock()
	defer l.access.Unlock()
	l.rows = map[trafficKey]*trafficRow{}
}

type countingProxy struct {
	C.Proxy
}

func (p countingProxy) DialContext(ctx context.Context, metadata *C.Metadata) (C.Conn, error) {
	conn, err := p.Proxy.DialContext(ctx, metadata)
	if err != nil {
		return nil, err
	}
	row := traffic.open(metadata, p.Proxy.Name(), time.Now())
	if row == nil {
		return conn, nil
	}
	return &countingConn{Conn: conn, row: row}, nil
}

func (p countingProxy) ListenPacketContext(ctx context.Context, metadata *C.Metadata) (C.PacketConn, error) {
	conn, err := p.Proxy.ListenPacketContext(ctx, metadata)
	if err != nil {
		return nil, err
	}
	row := traffic.open(metadata, p.Proxy.Name(), time.Now())
	if row == nil {
		return conn, nil
	}
	return &countingPacketConn{PacketConn: conn, row: row}, nil
}

type ruleWatch struct {
	learned atomic.Bool
}

func (w *ruleWatch) learnFrom(row *trafficRow, tracker statistic.Tracker) {
	info := tracker.Info()
	traffic.learnRule(row, info.Rule, info.RulePayload)
	w.learned.Store(true)
}

func (w *ruleWatch) learnOnClose(row *trafficRow, self any) {
	if w.learned.Load() {
		return
	}
	statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
		if upstreamOf(tracker) != self {
			return true
		}
		w.learnFrom(row, tracker)
		return false
	})
}

func upstreamOf(tracker statistic.Tracker) any {
	upstream, ok := tracker.(interface{ Upstream() any })
	if !ok {
		return nil
	}
	return upstream.Upstream()
}

func learnActiveRules() {
	statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
		switch counted := upstreamOf(tracker).(type) {
		case *countingConn:
			counted.rules.learnFrom(counted.row, tracker)
		case *countingPacketConn:
			counted.rules.learnFrom(counted.row, tracker)
		}
		return true
	})
}

type countingConn struct {
	C.Conn
	row    *trafficRow
	rules  ruleWatch
	closed atomic.Bool
}

func (c *countingConn) counted(download int64) {
	c.row.down.Add(download)
}

func (c *countingConn) Read(b []byte) (int, error) {
	n, err := c.Conn.Read(b)
	c.counted(int64(n))
	return n, err
}

func (c *countingConn) ReadBuffer(buffer *buf.Buffer) error {
	err := c.Conn.ReadBuffer(buffer)
	c.counted(int64(buffer.Len()))
	return err
}

func (c *countingConn) UnwrapReader() (io.Reader, []N.CountFunc) {
	return c.Conn, []N.CountFunc{c.counted}
}

func (c *countingConn) Write(b []byte) (int, error) {
	n, err := c.Conn.Write(b)
	c.row.up.Add(int64(n))
	return n, err
}

func (c *countingConn) WriteBuffer(buffer *buf.Buffer) error {
	upload := int64(buffer.Len())
	err := c.Conn.WriteBuffer(buffer)
	c.row.up.Add(upload)
	return err
}

func (c *countingConn) UnwrapWriter() (io.Writer, []N.CountFunc) {
	return c.Conn, []N.CountFunc{func(upload int64) { c.row.up.Add(upload) }}
}

func (c *countingConn) Close() error {
	if c.closed.CompareAndSwap(false, true) {
		c.rules.learnOnClose(c.row, c)
		traffic.close(c.row, time.Now())
	}
	return c.Conn.Close()
}

type countingPacketConn struct {
	C.PacketConn
	row    *trafficRow
	rules  ruleWatch
	closed atomic.Bool
}

func (c *countingPacketConn) ReadFrom(b []byte) (int, net.Addr, error) {
	n, addr, err := c.PacketConn.ReadFrom(b)
	c.row.down.Add(int64(n))
	return n, addr, err
}

func (c *countingPacketConn) WaitReadFrom() ([]byte, func(), net.Addr, error) {
	data, put, addr, err := c.PacketConn.WaitReadFrom()
	c.row.down.Add(int64(len(data)))
	return data, put, addr, err
}

func (c *countingPacketConn) WriteTo(b []byte, addr net.Addr) (int, error) {
	n, err := c.PacketConn.WriteTo(b, addr)
	c.row.up.Add(int64(n))
	return n, err
}

func (c *countingPacketConn) Close() error {
	if c.closed.CompareAndSwap(false, true) {
		c.rules.learnOnClose(c.row, c)
		traffic.close(c.row, time.Now())
	}
	return c.PacketConn.Close()
}

const rejectProxyName = "REJECT"

func countTraffic() {
	replaceProxies(func(_ string, proxy C.Proxy) C.Proxy {
		if _, counted := proxy.(countingProxy); counted {
			return proxy
		}
		return countingProxy{proxy}
	})
}

func Connections() (string, error) {
	learnActiveRules()
	encoded, err := json.Marshal(traffic.snapshot(time.Now()))
	return string(encoded), err
}

func hostOf(metadata *C.Metadata) string {
	if metadata.Host != "" {
		return metadata.Host
	}
	return metadata.SniffHost
}

func ipOf(metadata *C.Metadata) string {
	if !metadata.DstIP.IsValid() {
		return ""
	}
	return metadata.DstIP.String()
}

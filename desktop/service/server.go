package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/yulbax/erkin/mihomocore"
)

type request struct {
	ID       int64  `json:"id"`
	Op       string `json:"op"`
	Config   string `json:"config,omitempty"`
	Proxy    string `json:"proxy,omitempty"`
	Proxies  string `json:"proxies,omitempty"`
	Resolved string `json:"resolved,omitempty"`
	Resolver string `json:"resolver,omitempty"`
	URL      string `json:"url,omitempty"`
	Path     string `json:"path,omitempty"`
	Name     string `json:"name,omitempty"`
	Key      string `json:"key,omitempty"`
	Format   string `json:"format,omitempty"`
	Behavior string `json:"behavior,omitempty"`
	Timeout  int    `json:"timeout,omitempty"`
	IPv6     bool   `json:"ipv6,omitempty"`
	Port     int    `json:"port,omitempty"`
	Sites    string `json:"sites,omitempty"`
}

type response struct {
	ID      int64           `json:"id"`
	Error   string          `json:"error,omitempty"`
	OK      bool            `json:"ok,omitempty"`
	Version string          `json:"version,omitempty"`
	Core    string          `json:"core,omitempty"`
	WorkDir string          `json:"workDir,omitempty"`
	Delay   int             `json:"delay,omitempty"`
	Delays  json.RawMessage `json:"delays,omitempty"`
	Sites   json.RawMessage `json:"sites,omitempty"`
	Up      int64           `json:"up,omitempty"`
	Down    int64           `json:"down,omitempty"`
	Kind    string          `json:"kind,omitempty"`
	Count   int64           `json:"count,omitempty"`
	Log     *logEvent       `json:"log,omitempty"`
}

type logEvent struct {
	Level   string `json:"level"`
	Message string `json:"message"`
}

const maxMessageSize = 16 << 20

type server struct {
	engine    engine
	workDir   string
	listener  net.Listener
	protector net.Listener

	ownerMutex sync.Mutex
	owner      net.Conn

	logMutex       sync.Mutex
	logSubscribers map[net.Conn]func(response)
}

func listen(socketPath string, workDir string) (*server, error) {
	socketPath, err := filepath.Abs(socketPath)
	if err != nil {
		return nil, err
	}
	workDir, err = filepath.Abs(workDir)
	if err != nil {
		return nil, err
	}
	if err = os.MkdirAll(workDir, 0o755); err != nil {
		return nil, err
	}
	if err = os.Chdir(workDir); err != nil {
		return nil, err
	}
	removeLegacyGeoIP(workDir)
	_ = os.Remove(socketPath)
	listener, err := net.Listen("unix", relativeTo(workDir, socketPath))
	if err != nil {
		return nil, err
	}
	if err = allowClients(socketPath); err != nil {
		_ = listener.Close()
		return nil, err
	}
	protectPath := protectSocketPath(socketPath)
	_ = os.Remove(protectPath)
	protector, err := net.Listen("unix", relativeTo(workDir, protectPath))
	if err != nil {
		_ = listener.Close()
		return nil, err
	}
	if err = allowClients(protectPath); err != nil {
		_ = listener.Close()
		_ = protector.Close()
		return nil, err
	}
	s := &server{workDir: workDir, listener: listener, protector: protector, logSubscribers: map[net.Conn]func(response){}}
	mihomocore.SetLogSink(s)
	mihomocore.Init(workDir, filepath.Join(workDir, coreLogName), "")
	return s, nil
}

func (s *server) serve() error {
	go s.serveProtect()
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return nil
			}
			return err
		}
		go s.handle(conn)
	}
}

func (s *server) shutdown() {
	_ = s.listener.Close()
	_ = s.protector.Close()
	_ = s.engine.stop()
}

func (s *server) handle(conn net.Conn) {
	defer s.release(conn)
	defer s.unsubscribeLog(conn)
	defer conn.Close()
	var writeMutex sync.Mutex
	reply := func(message response) {
		encoded, err := json.Marshal(message)
		if err != nil {
			return
		}
		writeMutex.Lock()
		defer writeMutex.Unlock()
		_, _ = conn.Write(append(encoded, '\n'))
	}
	scanner := bufio.NewScanner(conn)
	scanner.Buffer(make([]byte, 64<<10), maxMessageSize)
	for scanner.Scan() {
		var message request
		if err := json.Unmarshal(scanner.Bytes(), &message); err != nil {
			reply(response{Error: "bad request: " + err.Error()})
			continue
		}
		if message.Op == "logs" {
			s.subscribeLog(conn, reply)
			reply(response{ID: message.ID, OK: true})
			continue
		}
		go func() { reply(s.dispatch(conn, message)) }()
	}
}

func (s *server) dispatch(conn net.Conn, message request) response {
	result := response{ID: message.ID}
	fail := func(err error) response {
		if err != nil {
			result.Error = err.Error()
		} else {
			result.OK = true
		}
		return result
	}
	switch message.Op {
	case "hello":
		result.Version = version
		result.Core = mihomocore.Version()
		result.WorkDir = s.workDir
		result.OK = true
	case "check":
		return fail(check(message.Config))
	case "start":
		err := s.engine.start(message.Config, message.Resolved, message.Resolver)
		if err == nil {
			s.claim(conn)
		}
		return fail(err)
	case "stop":
		s.claim(nil)
		return fail(s.engine.stop())
	case "clearLog":
		err := os.Truncate(filepath.Join(s.workDir, coreLogName), 0)
		if errors.Is(err, os.ErrNotExist) {
			err = nil
		}
		return fail(err)
	case "delay":
		result.Delay = s.engine.delay(message.Proxy, message.URL, time.Duration(message.Timeout)*time.Millisecond)
		result.OK = result.Delay > 0
	case "urltest":
		delays, err := mihomocore.TestProxies(message.Proxies, message.URL, int32(message.Timeout), message.IPv6)
		if err != nil {
			return fail(err)
		}
		result.Delays = json.RawMessage(delays)
		result.OK = true
	case "sites":
		sites, err := mihomocore.TestSites(int32(message.Port), message.Resolver, message.Sites, int32(message.Timeout), message.IPv6)
		if err != nil {
			return fail(err)
		}
		result.Sites = json.RawMessage(sites)
		result.OK = true
	case "rulelist":
		set, err := importRuleList(s.workDir, message.Key, message.Path, message.Format, message.Behavior)
		if err != nil {
			return fail(err)
		}
		result.Kind, result.Count, result.OK = set.Behavior, set.Count, true
	case "rulelistRemove":
		return fail(removeRuleList(s.workDir, message.Name))
	case "traffic":
		result.Up, result.Down, result.OK = s.engine.traffic()
	default:
		result.Error = "unknown op: " + message.Op
	}
	return result
}

func (s *server) Write(level string, message string) {
	s.logMutex.Lock()
	defer s.logMutex.Unlock()
	for _, reply := range s.logSubscribers {
		reply(response{Log: &logEvent{Level: level, Message: message}})
	}
}

func (s *server) subscribeLog(conn net.Conn, reply func(response)) {
	s.logMutex.Lock()
	defer s.logMutex.Unlock()
	s.logSubscribers[conn] = reply
}

func (s *server) unsubscribeLog(conn net.Conn) {
	s.logMutex.Lock()
	defer s.logMutex.Unlock()
	delete(s.logSubscribers, conn)
}

func (s *server) claim(conn net.Conn) {
	s.ownerMutex.Lock()
	defer s.ownerMutex.Unlock()
	s.owner = conn
}

func (s *server) release(conn net.Conn) {
	s.ownerMutex.Lock()
	owned := s.owner == conn
	if owned {
		s.owner = nil
	}
	s.ownerMutex.Unlock()
	if owned {
		_ = s.engine.stop()
	}
}

func relativeTo(base string, path string) string {
	if relative, err := filepath.Rel(base, path); err == nil && len(relative) < len(path) {
		return relative
	}
	return path
}

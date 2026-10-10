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

	C "github.com/sagernet/sing-box/constant"
)

type request struct {
	ID       int64  `json:"id"`
	Op       string `json:"op"`
	Config   string `json:"config,omitempty"`
	Group    string `json:"group,omitempty"`
	Outbound string `json:"outbound,omitempty"`
	URL      string `json:"url,omitempty"`
	Timeout  int    `json:"timeout,omitempty"`
}

type response struct {
	ID      int64  `json:"id"`
	Error   string `json:"error,omitempty"`
	OK      bool   `json:"ok,omitempty"`
	Version string `json:"version,omitempty"`
	Core    string `json:"core,omitempty"`
	WorkDir string `json:"workDir,omitempty"`
	Delay   int    `json:"delay,omitempty"`
	Up      int64  `json:"up,omitempty"`
	Down    int64  `json:"down,omitempty"`
}

const maxMessageSize = 16 << 20

type server struct {
	engine   engine
	workDir  string
	listener net.Listener

	ownerMutex sync.Mutex
	owner      net.Conn
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
	_ = os.Remove(socketPath)
	listener, err := net.Listen("unix", relativeTo(workDir, socketPath))
	if err != nil {
		return nil, err
	}
	if err = allowClients(socketPath); err != nil {
		_ = listener.Close()
		return nil, err
	}
	return &server{workDir: workDir, listener: listener}, nil
}

func (s *server) serve() error {
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
	_ = s.engine.stop()
}

func (s *server) handle(conn net.Conn) {
	defer s.release(conn)
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
		result.Core = C.Version
		result.WorkDir = s.workDir
		result.OK = true
	case "check":
		return fail(check(message.Config))
	case "start":
		err := s.engine.start(message.Config)
		if err == nil {
			s.claim(conn)
		}
		return fail(err)
	case "stop":
		s.claim(nil)
		return fail(s.engine.stop())
	case "clearLog":
		err := os.Truncate(filepath.Join(s.workDir, "box.log"), 0)
		if errors.Is(err, os.ErrNotExist) {
			err = nil
		}
		return fail(err)
	case "select":
		result.OK = s.engine.selectOutbound(message.Group, message.Outbound)
	case "delay":
		result.Delay = s.engine.delay(message.Outbound, message.URL, time.Duration(message.Timeout)*time.Millisecond)
		result.OK = result.Delay > 0
	case "traffic":
		result.Up, result.Down, result.OK = s.engine.traffic()
	default:
		result.Error = "unknown op: " + message.Op
	}
	return result
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

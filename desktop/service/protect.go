package main

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
	"time"
)

const (
	protectSocketName = "protect.sock"
	protectTimeout    = 2 * time.Second
)

func protectSocketPath(socketPath string) string {
	return filepath.Join(filepath.Dir(socketPath), protectSocketName)
}

func (s *server) serveProtect() {
	for {
		conn, err := s.protector.Accept()
		if err != nil {
			return
		}
		go func() {
			defer conn.Close()
			_ = conn.SetDeadline(time.Now().Add(protectTimeout))
			if err := protect(conn.(*net.UnixConn), s.engine.defaultInterfaceIndex); err != nil {
				fmt.Fprintln(os.Stderr, "protect:", err)
			}
		}()
	}
}

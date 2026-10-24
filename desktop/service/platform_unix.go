//go:build !windows

package main

import (
	"os"
	"path/filepath"
)

func allowClients(socketPath string) error { return os.Chmod(socketPath, 0o666) }

func run(socket string, workDir string) error {
	if err := os.MkdirAll(filepath.Dir(socket), 0o755); err != nil {
		return err
	}
	return runForeground(socket, workDir)
}

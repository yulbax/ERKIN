package main

import (
	"net"
	"os"
	"path/filepath"
	"strconv"
)

const systemdFirstFd = 3

var serviceCommands = map[string]func() error{}

const serviceUsage = ""

func defaultSocketPath() string { return "/run/erkin/erkin.sock" }

func defaultWorkDir() string { return "/var/lib/erkin" }

func allowClients(socketPath string) error { return os.Chmod(socketPath, 0o666) }

func run(socket string, workDir string) error {
	if err := os.MkdirAll(filepath.Dir(socket), 0o755); err != nil {
		return err
	}
	return runForeground(socket, workDir)
}

func inheritedListener() (net.Listener, error) {
	if os.Getenv("LISTEN_PID") != strconv.Itoa(os.Getpid()) || os.Getenv("LISTEN_FDS") != "1" {
		return nil, nil
	}
	file := os.NewFile(systemdFirstFd, "erkin.sock")
	defer file.Close()
	return net.FileListener(file)
}

package main

import (
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

var version = "dev"

const (
	serviceName    = "ERKIN"
	appDirFileName = "app-dir"
	coreLogName    = "core.log"
	appCheckPeriod = 10 * time.Minute
)

func main() {
	if len(os.Args) < 2 {
		usage()
	}
	var err error
	switch os.Args[1] {
	case "run":
		flags := flag.NewFlagSet("run", flag.ExitOnError)
		socket := flags.String("socket", defaultSocketPath(), "unix socket to listen on")
		workDir := flags.String("work-dir", defaultWorkDir(), "directory for logs and state")
		_ = flags.Parse(os.Args[2:])
		err = run(*socket, *workDir)
	case "install":
		flags := flag.NewFlagSet("install", flag.ExitOnError)
		appDir := flags.String("app-dir", "", "ERKIN installation directory; the service removes itself once it is gone")
		_ = flags.Parse(os.Args[2:])
		err = install(*appDir)
	case "uninstall":
		err = uninstall()
	case "version":
		fmt.Println(version)
	default:
		usage()
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: erkin-service run [--socket path] [--work-dir dir] | install [--app-dir dir] | uninstall | version")
	os.Exit(2)
}

func runForeground(socket string, workDir string) error {
	s, err := listen(socket, workDir)
	if err != nil {
		return err
	}
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-signals
		s.shutdown()
	}()
	stopWatch := watchApp(workDir, func() {
		s.shutdown()
		removeSelf()
	})
	defer stopWatch()
	return s.serve()
}

func watchApp(workDir string, onGone func()) func() {
	content, err := os.ReadFile(filepath.Join(workDir, appDirFileName))
	appDir := strings.TrimSpace(string(content))
	if err != nil || appDir == "" {
		return func() {}
	}
	done := make(chan struct{})
	go func() {
		ticker := time.NewTicker(appCheckPeriod)
		defer ticker.Stop()
		missing := 0
		for {
			if _, statErr := os.Stat(appDir); os.IsNotExist(statErr) {
				missing++
			} else {
				missing = 0
			}
			if missing >= 2 {
				onGone()
				return
			}
			select {
			case <-done:
				return
			case <-ticker.C:
			}
		}
	}()
	return func() { close(done) }
}

func writeAppDir(workDir string, appDir string) error {
	if appDir == "" {
		return nil
	}
	return os.WriteFile(filepath.Join(workDir, appDirFileName), []byte(appDir), 0o644)
}

func copyExecutable(source string, target string) error {
	if same, _ := sameFile(source, target); same {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	input, err := os.Open(source)
	if err != nil {
		return err
	}
	defer input.Close()
	temporary := target + ".new"
	output, err := os.OpenFile(temporary, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	if _, err = io.Copy(output, input); err != nil {
		_ = output.Close()
		return err
	}
	if err = output.Close(); err != nil {
		return err
	}
	return os.Rename(temporary, target)
}

func sameFile(a string, b string) (bool, error) {
	first, err := os.Stat(a)
	if err != nil {
		return false, err
	}
	second, err := os.Stat(b)
	if err != nil {
		return false, err
	}
	return os.SameFile(first, second), nil
}

package main

import (
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"

	"github.com/sirupsen/logrus"
)

var version = "dev"

const (
	serviceName = "ERKIN"
	coreLogName = "core.log"
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
		stdio := flags.Bool("stdio", false, "serve the app that started this process over stdin and stdout")
		protect := flags.String("protect", "", "unix socket ciadpi asks for the outbound interface (with --stdio)")
		_ = flags.Parse(os.Args[2:])
		if *stdio {
			err = runStdio(*workDir, *protect)
		} else {
			err = run(*socket, *workDir)
		}
	case "version":
		fmt.Println(version)
	default:
		command, found := serviceCommands[os.Args[1]]
		if !found {
			usage()
		}
		err = command()
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: erkin-service run [--socket path | --stdio --protect path] [--work-dir dir] | version"+serviceUsage)
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
	return s.serve()
}

func runStdio(workDir string, protectPath string) error {
	if protectPath == "" {
		return fmt.Errorf("--stdio needs --protect")
	}
	logrus.SetOutput(io.Discard)
	s, err := newServer(workDir, protectPath)
	if err != nil {
		return err
	}
	s.serveOne(stdio{})
	return nil
}

type stdio struct{}

func (stdio) Read(p []byte) (int, error) { return os.Stdin.Read(p) }

func (stdio) Write(p []byte) (int, error) { return os.Stdout.Write(p) }

func (stdio) Close() error { return os.Stdin.Close() }

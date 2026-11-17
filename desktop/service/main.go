package main

import (
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"
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
		_ = flags.Parse(os.Args[2:])
		err = run(*socket, *workDir)
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
	fmt.Fprintln(os.Stderr, "usage: erkin-service run [--socket path] [--work-dir dir] | version"+serviceUsage)
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

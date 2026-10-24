package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/netip"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

const dnsBackupName = "dns-backup.json"

var tunnelResolver = netip.MustParseAddr("198.18.0.2")

func pinSystemDNS(stateDir string) error {
	backupPath := filepath.Join(stateDir, dnsBackupName)
	backup, err := readDNSBackup(backupPath)
	if err != nil {
		return err
	}
	if backup == nil {
		services, err := networkServices()
		if err != nil {
			return err
		}
		backup = map[string][]string{}
		for _, service := range services {
			if backup[service], err = dnsServers(service); err != nil {
				return err
			}
		}
		content, err := json.Marshal(backup)
		if err != nil {
			return err
		}
		if err = os.WriteFile(backupPath, content, 0o600); err != nil {
			return err
		}
	}
	for service := range backup {
		if err = networksetup("-setdnsservers", service, tunnelResolver.String()); err != nil {
			return err
		}
	}
	flushDNSCache()
	return nil
}

func restoreSystemDNS(stateDir string) error {
	backupPath := filepath.Join(stateDir, dnsBackupName)
	backup, err := readDNSBackup(backupPath)
	if err != nil || backup == nil {
		return err
	}
	var failures []error
	for service, servers := range backup {
		if len(servers) == 0 {
			servers = []string{"empty"}
		}
		if err = networksetup(append([]string{"-setdnsservers", service}, servers...)...); err != nil {
			failures = append(failures, err)
		}
	}
	flushDNSCache()
	if len(failures) > 0 {
		return errors.Join(failures...)
	}
	return os.Remove(backupPath)
}

func readDNSBackup(path string) (map[string][]string, error) {
	content, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var backup map[string][]string
	return backup, json.Unmarshal(content, &backup)
}

func networkServices() ([]string, error) {
	output, err := exec.Command("networksetup", "-listallnetworkservices").Output()
	if err != nil {
		return nil, fmt.Errorf("networksetup -listallnetworkservices: %w", err)
	}
	lines := strings.Split(strings.TrimSpace(string(output)), "\n")
	var services []string
	for _, line := range lines[1:] {
		if line = strings.TrimSpace(line); line != "" && !strings.HasPrefix(line, "*") {
			services = append(services, line)
		}
	}
	return services, nil
}

func dnsServers(service string) ([]string, error) {
	output, err := exec.Command("networksetup", "-getdnsservers", service).Output()
	if err != nil {
		return nil, fmt.Errorf("networksetup -getdnsservers %s: %w", service, err)
	}
	servers := []string{}
	for _, line := range strings.Split(string(output), "\n") {
		if address, parseErr := netip.ParseAddr(strings.TrimSpace(line)); parseErr == nil {
			servers = append(servers, address.String())
		}
	}
	return servers, nil
}

func networksetup(args ...string) error {
	output, err := exec.Command("networksetup", args...).CombinedOutput()
	if err != nil {
		return fmt.Errorf("networksetup %v: %w: %s", args, err, output)
	}
	return nil
}

func flushDNSCache() {
	_ = exec.Command("dscacheutil", "-flushcache").Run()
	_ = exec.Command("killall", "-HUP", "mDNSResponder").Run()
}

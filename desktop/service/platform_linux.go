package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

const (
	linuxInstallDir = "/usr/local/lib/erkin-service"
	linuxUnitPath   = "/etc/systemd/system/erkin.service"
	linuxWorkDir    = "/var/lib/erkin"

	legacyInstallDir = "/usr/local/lib/frkn-service"
	legacyUnitPath   = "/etc/systemd/system/frkn.service"
	legacyWorkDir    = "/var/lib/frkn"
)

const linuxUnit = `[Unit]
Description=ERKIN VPN service
After=network-online.target
Wants=network-online.target

[Service]
ExecStartPre=-modprobe -a inet_diag tcp_diag udp_diag
ExecStart=%s run
Restart=on-failure
RestartSec=5
RuntimeDirectory=erkin
RuntimeDirectoryMode=0755
StateDirectory=erkin
StateDirectoryMode=0755

[Install]
WantedBy=multi-user.target
`

func defaultSocketPath() string { return "/run/erkin/erkin.sock" }

func defaultWorkDir() string { return linuxWorkDir }

func install(appDir string) error {
	self, err := os.Executable()
	if err != nil {
		return err
	}
	target := filepath.Join(linuxInstallDir, "erkin-service")
	removeLegacy()
	_ = systemctl("stop", "erkin.service")
	if err = copyExecutable(self, target); err != nil {
		return err
	}
	if err = os.WriteFile(linuxUnitPath, []byte(fmt.Sprintf(linuxUnit, target)), 0o644); err != nil {
		return err
	}
	if err = os.MkdirAll(linuxWorkDir, 0o755); err != nil {
		return err
	}
	if err = writeAppDir(linuxWorkDir, appDir); err != nil {
		return err
	}
	for _, args := range [][]string{{"daemon-reload"}, {"enable", "erkin.service"}, {"restart", "erkin.service"}} {
		if err = systemctl(args...); err != nil {
			return err
		}
	}
	return nil
}

func uninstall() error {
	_ = systemctl("disable", "--now", "erkin.service")
	removeFiles()
	return systemctl("daemon-reload")
}

func removeSelf() {
	removeFiles()
	_ = systemctl("daemon-reload")
	_ = systemctl("disable", "--now", "erkin.service")
}

func removeLegacy() {
	_ = systemctl("disable", "--now", "frkn.service")
	_ = os.Remove(legacyUnitPath)
	_ = os.RemoveAll(legacyInstallDir)
	_ = os.RemoveAll(legacyWorkDir)
}

func removeFiles() {
	_ = os.Remove(linuxUnitPath)
	_ = os.RemoveAll(linuxInstallDir)
	_ = os.RemoveAll(linuxWorkDir)
}

func systemctl(args ...string) error {
	output, err := exec.Command("systemctl", args...).CombinedOutput()
	if err != nil {
		return fmt.Errorf("systemctl %v: %w: %s", args, err, output)
	}
	return nil
}

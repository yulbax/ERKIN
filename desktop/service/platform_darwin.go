package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

const (
	darwinLabel      = "io.github.yulbax.erkin.service"
	darwinInstallDir = "/Library/PrivilegedHelperTools/erkin-service"
	darwinPlistPath  = "/Library/LaunchDaemons/" + darwinLabel + ".plist"
	darwinWorkDir    = "/Library/Application Support/ERKIN Service"
	darwinTarget     = "system/" + darwinLabel
)

const darwinPlist = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>%s</string>
	<key>ProgramArguments</key>
	<array>
		<string>%s</string>
		<string>run</string>
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>KeepAlive</key>
	<dict>
		<key>SuccessfulExit</key>
		<false/>
	</dict>
	<key>ThrottleInterval</key>
	<integer>5</integer>
	<key>StandardErrorPath</key>
	<string>%s</string>
</dict>
</plist>
`

func defaultSocketPath() string { return "/var/run/erkin/erkin.sock" }

func defaultWorkDir() string { return darwinWorkDir }

func install(appDir string) error {
	self, err := os.Executable()
	if err != nil {
		return err
	}
	target := filepath.Join(darwinInstallDir, "erkin-service")
	_ = launchctl("bootout", darwinTarget)
	if err = copyExecutable(self, target); err != nil {
		return err
	}
	if err = os.MkdirAll(darwinWorkDir, 0o755); err != nil {
		return err
	}
	plist := fmt.Sprintf(darwinPlist, darwinLabel, target, filepath.Join(darwinWorkDir, "service.log"))
	if err = os.WriteFile(darwinPlistPath, []byte(plist), 0o644); err != nil {
		return err
	}
	if err = writeAppDir(darwinWorkDir, appDir); err != nil {
		return err
	}
	_ = launchctl("enable", darwinTarget)
	return launchctl("bootstrap", "system", darwinPlistPath)
}

func uninstall() error {
	_ = launchctl("bootout", darwinTarget)
	removeFiles()
	return nil
}

func removeSelf() {
	removeFiles()
	_ = launchctl("bootout", darwinTarget)
}

func removeFiles() {
	_ = os.Remove(darwinPlistPath)
	_ = os.RemoveAll(darwinInstallDir)
	_ = os.RemoveAll(darwinWorkDir)
}

func launchctl(args ...string) error {
	output, err := exec.Command("launchctl", args...).CombinedOutput()
	if err != nil {
		return fmt.Errorf("launchctl %v: %w: %s", args, err, output)
	}
	return nil
}

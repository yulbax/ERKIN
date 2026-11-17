package main

import (
	"errors"
	"net"
	"os"
	"path/filepath"
	"time"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/svc"
	"golang.org/x/sys/windows/svc/mgr"
)

const (
	dataDirSDDL = "D:P(A;OICI;GA;;;SY)(A;OICI;GA;;;BA)(A;OICI;GRGX;;;AU)"
	socketSDDL  = "D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGW;;;AU)"
	serviceSDDL = "D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWRPLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)"
	stopTimeout = 30 * time.Second

	legacyServiceName  = "FRKN"
	previousAppDirFile = "app-dir"
)

var serviceCommands = map[string]func() error{
	"install":   install,
	"stop":      stop,
	"uninstall": uninstall,
}

const serviceUsage = " | install | stop | uninstall"

func defaultWorkDir() string {
	return filepath.Join(os.Getenv("ProgramData"), "ERKIN")
}

func previousInstallDir() string {
	return filepath.Join(os.Getenv("ProgramFiles"), "ERKIN Service")
}

func previousWorkDir() string {
	return filepath.Join(os.Getenv("ProgramData"), "ERKIN Service")
}

func legacyInstallDir() string {
	return filepath.Join(os.Getenv("ProgramFiles"), "FRKN Service")
}

func legacyWorkDir() string {
	return filepath.Join(os.Getenv("ProgramData"), "FRKN Service")
}

func defaultSocketPath() string { return filepath.Join(defaultWorkDir(), "erkin.sock") }

func allowClients(socketPath string) error { return applySDDL(socketPath, socketSDDL) }

func inheritedListener() (net.Listener, error) { return nil, nil }

func daclOf(sddl string) (*windows.ACL, error) {
	descriptor, err := windows.SecurityDescriptorFromString(sddl)
	if err != nil {
		return nil, err
	}
	dacl, _, err := descriptor.DACL()
	return dacl, err
}

func allowUsersToStart(service *mgr.Service) error {
	dacl, err := daclOf(serviceSDDL)
	if err != nil {
		return err
	}
	return windows.SetSecurityInfo(service.Handle, windows.SE_SERVICE, windows.DACL_SECURITY_INFORMATION, nil, nil, dacl, nil)
}

func applySDDL(path string, sddl string) error {
	dacl, err := daclOf(sddl)
	if err != nil {
		return err
	}
	return windows.SetNamedSecurityInfo(
		path,
		windows.SE_FILE_OBJECT,
		windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION,
		nil, nil, dacl, nil,
	)
}

func run(socket string, workDir string) error {
	isService, err := svc.IsWindowsService()
	if err != nil {
		return err
	}
	if !isService {
		return runForeground(socket, workDir)
	}
	return svc.Run(serviceName, &handler{socket: socket, workDir: workDir})
}

type handler struct {
	socket  string
	workDir string
}

func (h *handler) Execute(_ []string, requests <-chan svc.ChangeRequest, status chan<- svc.Status) (bool, uint32) {
	status <- svc.Status{State: svc.StartPending}
	s, err := listen(h.socket, h.workDir)
	if err != nil {
		return true, 1
	}
	served := make(chan error, 1)
	go func() { served <- s.serve() }()
	status <- svc.Status{State: svc.Running, Accepts: svc.AcceptStop | svc.AcceptShutdown}
	for {
		select {
		case request := <-requests:
			switch request.Cmd {
			case svc.Interrogate:
				status <- request.CurrentStatus
			case svc.Stop, svc.Shutdown:
				status <- svc.Status{State: svc.StopPending}
				s.shutdown()
				return false, 0
			}
		case <-served:
			return false, 0
		}
	}
}

func install() error {
	self, err := os.Executable()
	if err != nil {
		return err
	}
	manager, err := mgr.Connect()
	if err != nil {
		return err
	}
	defer manager.Disconnect()
	removeLegacy(manager)

	existing, err := manager.OpenService(serviceName)
	if err == nil {
		defer existing.Close()
		if err = stopService(existing); err != nil {
			return err
		}
	}
	removeDir(previousInstallDir(), "erkin-service.exe")
	workDir := defaultWorkDir()
	adoptPreviousWorkDir(workDir)
	if err = os.MkdirAll(workDir, 0o755); err != nil {
		return err
	}
	if err = applySDDL(workDir, dataDirSDDL); err != nil {
		return err
	}

	service := existing
	if service == nil {
		service, err = manager.CreateService(serviceName, self, mgr.Config{
			DisplayName: "ERKIN VPN",
			Description: "Runs the ERKIN tunnel while the ERKIN app is open, so the app does not need administrator rights.",
			StartType:   mgr.StartManual,
		}, "run")
		if err != nil {
			return err
		}
		defer service.Close()
	} else {
		config, configErr := service.Config()
		if configErr != nil {
			return configErr
		}
		config.BinaryPathName = windows.EscapeArg(self) + " run"
		config.StartType = mgr.StartManual
		if err = service.UpdateConfig(config); err != nil {
			return err
		}
	}
	_ = service.SetRecoveryActions([]mgr.RecoveryAction{{Type: mgr.ServiceRestart, Delay: 5 * time.Second}}, 86400)
	return allowUsersToStart(service)
}

func stop() error {
	manager, err := mgr.Connect()
	if err != nil {
		return err
	}
	defer manager.Disconnect()
	service, err := manager.OpenService(serviceName)
	if err != nil {
		return nil
	}
	defer service.Close()
	return stopService(service)
}

func uninstall() error {
	manager, err := mgr.Connect()
	if err != nil {
		return err
	}
	defer manager.Disconnect()
	service, err := manager.OpenService(serviceName)
	if err == nil {
		_ = stopService(service)
		err = service.Delete()
		service.Close()
		if err != nil {
			return err
		}
	}
	removeDir(previousInstallDir(), "erkin-service.exe")
	_ = os.RemoveAll(previousWorkDir())
	_ = os.RemoveAll(defaultWorkDir())
	return nil
}

func adoptPreviousWorkDir(workDir string) {
	if _, err := os.Stat(workDir); os.IsNotExist(err) {
		_ = os.Rename(previousWorkDir(), workDir)
	}
	_ = os.RemoveAll(previousWorkDir())
	_ = os.Remove(filepath.Join(workDir, previousAppDirFile))
}

func removeLegacy(manager *mgr.Mgr) {
	if service, err := manager.OpenService(legacyServiceName); err == nil {
		_ = stopService(service)
		_ = service.Delete()
		service.Close()
	}
	removeDir(legacyInstallDir(), "frkn-service.exe")
	_ = os.RemoveAll(legacyWorkDir())
}

func removeDir(dir string, executable string) {
	if os.RemoveAll(dir) == nil {
		return
	}
	if path, err := windows.UTF16PtrFromString(filepath.Join(dir, executable)); err == nil {
		_ = windows.MoveFileEx(path, nil, windows.MOVEFILE_DELAY_UNTIL_REBOOT)
	}
}

func stopService(service *mgr.Service) error {
	status, err := service.Query()
	if err != nil {
		return err
	}
	if status.State == svc.Stopped {
		return nil
	}
	if status.State != svc.StopPending {
		if _, err = service.Control(svc.Stop); err != nil {
			return err
		}
	}
	deadline := time.Now().Add(stopTimeout)
	for time.Now().Before(deadline) {
		status, err = service.Query()
		if err != nil {
			return err
		}
		if status.State == svc.Stopped {
			return nil
		}
		time.Sleep(300 * time.Millisecond)
	}
	return errors.New("the ERKIN service did not stop in time")
}

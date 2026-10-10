package main

import (
	"errors"
	"fmt"
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
	stopTimeout = 30 * time.Second

	legacyServiceName = "FRKN"
)

func installDir() string {
	return filepath.Join(os.Getenv("ProgramFiles"), "ERKIN Service")
}

func defaultWorkDir() string {
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

func applySDDL(path string, sddl string) error {
	descriptor, err := windows.SecurityDescriptorFromString(sddl)
	if err != nil {
		return err
	}
	dacl, _, err := descriptor.DACL()
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
	gone := make(chan struct{})
	stopWatch := watchApp(h.workDir, func() { close(gone) })
	defer stopWatch()
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
		case <-gone:
			status <- svc.Status{State: svc.StopPending}
			s.shutdown()
			removeSelf()
			return false, 0
		case <-served:
			return false, 0
		}
	}
}

func install(appDir string) error {
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

	target := filepath.Join(installDir(), "erkin-service.exe")
	existing, err := manager.OpenService(serviceName)
	if err == nil {
		defer existing.Close()
		if err = stopService(existing); err != nil {
			return err
		}
	}
	if err = copyExecutable(self, target); err != nil {
		return err
	}
	workDir := defaultWorkDir()
	if err = os.MkdirAll(workDir, 0o755); err != nil {
		return err
	}
	if err = applySDDL(workDir, dataDirSDDL); err != nil {
		return err
	}
	if err = writeAppDir(workDir, appDir); err != nil {
		return err
	}

	service := existing
	if service == nil {
		service, err = manager.CreateService(serviceName, target, mgr.Config{
			DisplayName: "ERKIN VPN",
			Description: "Runs the ERKIN tunnel so the ERKIN app does not need administrator rights.",
			StartType:   mgr.StartAutomatic,
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
		config.BinaryPathName = fmt.Sprintf("%q run", target)
		config.StartType = mgr.StartAutomatic
		if err = service.UpdateConfig(config); err != nil {
			return err
		}
	}
	_ = service.SetRecoveryActions([]mgr.RecoveryAction{{Type: mgr.ServiceRestart, Delay: 5 * time.Second}}, 86400)
	return service.Start()
}

func removeLegacy(manager *mgr.Mgr) {
	if service, err := manager.OpenService(legacyServiceName); err == nil {
		_ = stopService(service)
		_ = service.Delete()
		service.Close()
	}
	if os.RemoveAll(legacyInstallDir()) != nil {
		if path, err := windows.UTF16PtrFromString(filepath.Join(legacyInstallDir(), "frkn-service.exe")); err == nil {
			_ = windows.MoveFileEx(path, nil, windows.MOVEFILE_DELAY_UNTIL_REBOOT)
		}
	}
	_ = os.RemoveAll(legacyWorkDir())
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
	_ = os.RemoveAll(installDir())
	_ = os.RemoveAll(defaultWorkDir())
	return nil
}

func removeSelf() {
	if manager, err := mgr.Connect(); err == nil {
		if service, openErr := manager.OpenService(serviceName); openErr == nil {
			_ = service.Delete()
			service.Close()
		}
		manager.Disconnect()
	}
	target := filepath.Join(installDir(), "erkin-service.exe")
	if path, err := windows.UTF16PtrFromString(target); err == nil {
		_ = windows.MoveFileEx(path, nil, windows.MOVEFILE_DELAY_UNTIL_REBOOT)
	}
	_ = os.RemoveAll(defaultWorkDir())
}

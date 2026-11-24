package main

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

func hostProcesses() []hostProcess {
	entries, err := os.ReadDir("/proc")
	if err != nil {
		return nil
	}
	processes := make([]hostProcess, 0, len(entries))
	for _, entry := range entries {
		pid, err := strconv.Atoi(entry.Name())
		if err != nil {
			continue
		}
		if process, ok := hostProcessOf(pid); ok {
			processes = append(processes, process)
		}
	}
	return processes
}

func hostProcessOf(pid int) (hostProcess, bool) {
	dir := "/proc/" + strconv.Itoa(pid)
	stat, err := os.ReadFile(dir + "/stat")
	if err != nil {
		return hostProcess{}, false
	}
	parent, ok := parentPid(string(stat))
	if !ok {
		return hostProcess{}, false
	}
	exe, _ := os.Readlink(dir + "/exe")
	name := strings.TrimSuffix(filepath.Base(exe), " (deleted)")
	if exe == "" {
		comm, _ := os.ReadFile(dir + "/comm")
		name = strings.TrimSpace(string(comm))
	}
	cgroup, _ := os.ReadFile(dir + "/cgroup")
	return hostProcess{
		PID:     pid,
		Parent:  parent,
		Exe:     exe,
		Name:    name,
		Unit:    cgroupUnit(string(cgroup)),
		Flatpak: flatpakApp(pid),
	}, true
}

func cgroupUnit(cgroup string) string {
	line := strings.TrimSpace(cgroup)
	if index := strings.LastIndexByte(line, '\n'); index >= 0 {
		line = line[index+1:]
	}
	return line[strings.LastIndexByte(line, '/')+1:]
}

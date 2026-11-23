package main

import (
	"os"
	"regexp"
	"strconv"
	"strings"
)

const flatpakApplicationGroup = "[Application]"

var flatpakScope = regexp.MustCompile(`(?m)app-flatpak-(.+)-\d+\.scope$`)

func flatpakApp(pid int) string {
	dir := "/proc/" + strconv.Itoa(pid)
	info, _ := os.ReadFile(dir + "/root/.flatpak-info")
	cgroup, _ := os.ReadFile(dir + "/cgroup")
	return flatpakAppOf(string(info), string(cgroup))
}

func flatpakAppOf(info, cgroup string) string {
	inApplication := false
	for _, line := range strings.Split(info, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "[") {
			inApplication = line == flatpakApplicationGroup
			continue
		}
		if name, ok := strings.CutPrefix(line, "name="); ok && inApplication {
			return strings.TrimSpace(name)
		}
	}
	if match := flatpakScope.FindStringSubmatch(cgroup); match != nil {
		return match[1]
	}
	return ""
}

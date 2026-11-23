package main

import (
	"encoding/binary"
	"encoding/json"
	"net/netip"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync/atomic"
	"syscall"

	"github.com/metacubex/mihomo/component/process"
	C "github.com/metacubex/mihomo/constant"
)

const (
	maxAncestry          = 64
	inetDiagMessageSize  = 72
	inetDiagInodeOffset  = 68
	inetDiagSourceOffset = 8
	inetDiagPortOffset   = 4
)

var (
	ruledProcessPattern = regexp.MustCompile(`PROCESS-NAME,([^,()]+)`)
	ruledProcesses      atomic.Pointer[map[string]struct{}]
	sessionRoots        = map[string]struct{}{
		"systemd": {}, "init": {}, "erkin-service": {}, "login": {},
		"Hyprland": {}, "sway": {}, "river": {}, "niri": {}, "labwc": {}, "wayfire": {}, "hyprland": {},
		"kwin_wayland": {}, "kwin_x11": {}, "plasmashell": {}, "ksmserver": {}, "startplasma-wayland": {}, "startplasma-x11": {},
		"gnome-shell": {}, "gnome-session-binary": {}, "xfce4-session": {}, "cinnamon": {}, "mate-session": {}, "lxqt-session": {},
		"sddm-helper": {}, "gdm-wayland-session": {}, "gdm-x-session": {}, "lightdm": {},
	}
)

type processNode struct {
	pid int
	exe string
	app string
}

func init() {
	process.DefaultPackageNameResolver = inheritedOwnerOf
}

func setRuledProcesses(config string) {
	var parsed struct {
		Rules []string `json:"rules"`
	}
	ids := map[string]struct{}{}
	if json.Unmarshal([]byte(config), &parsed) == nil {
		for _, rule := range parsed.Rules {
			for _, match := range ruledProcessPattern.FindAllStringSubmatch(rule, -1) {
				ids[match[1]] = struct{}{}
			}
		}
	}
	ruledProcesses.Store(&ids)
}

func inheritedOwnerOf(metadata *C.Metadata) (string, error) {
	ruled := ruledProcesses.Load()
	if ruled == nil || len(*ruled) == 0 || metadata.ProcessPath == "" {
		return "", process.ErrNotFound
	}
	pid, ok := ownerPid(metadata)
	if !ok {
		return "", process.ErrNotFound
	}
	if owner, ok := inheritedOwner(ancestry(pid), *ruled); ok {
		return owner, nil
	}
	return "", process.ErrNotFound
}

func inheritedOwner(chain []processNode, ruled map[string]struct{}) (string, bool) {
	for index, node := range chain {
		if index > 0 && isSessionRoot(node) {
			return "", false
		}
		for _, id := range node.ids() {
			if _, ok := ruled[id]; ok {
				return id, true
			}
		}
	}
	return "", false
}

func (node processNode) ids() []string {
	var ids []string
	if node.app != "" {
		ids = append(ids, node.app)
	}
	if node.exe != "" {
		ids = append(ids, node.exe, filepath.Base(node.exe))
	}
	return ids
}

func isSessionRoot(node processNode) bool {
	if node.pid == 1 {
		return true
	}
	_, ok := sessionRoots[strings.TrimSuffix(filepath.Base(node.exe), " (deleted)")]
	return ok
}

func ancestry(pid int) []processNode {
	var chain []processNode
	for pid > 0 && len(chain) < maxAncestry {
		exe, _ := os.Readlink("/proc/" + strconv.Itoa(pid) + "/exe")
		chain = append(chain, processNode{pid: pid, exe: exe, app: flatpakApp(pid)})
		stat, err := os.ReadFile("/proc/" + strconv.Itoa(pid) + "/stat")
		if err != nil {
			break
		}
		parent, ok := parentPid(string(stat))
		if !ok || parent == pid {
			break
		}
		pid = parent
	}
	return chain
}

func parentPid(stat string) (int, bool) {
	end := strings.LastIndexByte(stat, ')')
	if end < 0 {
		return 0, false
	}
	fields := strings.Fields(stat[end+1:])
	if len(fields) < 2 {
		return 0, false
	}
	parent, err := strconv.Atoi(fields[1])
	return parent, err == nil
}

func ownerPid(metadata *C.Metadata) (int, bool) {
	candidates := processesRunning(metadata.ProcessPath, metadata.Uid)
	switch len(candidates) {
	case 0:
		return 0, false
	case 1:
		return candidates[0], true
	}
	inode, ok := socketInode(metadata.NetWork.String(), metadata.SrcIP, uint16(metadata.SrcPort))
	if !ok {
		return 0, false
	}
	target := "socket:[" + strconv.FormatUint(uint64(inode), 10) + "]"
	for _, pid := range candidates {
		if holdsSocket(pid, target) {
			return pid, true
		}
	}
	return 0, false
}

func processesRunning(exe string, uid uint32) []int {
	entries, err := os.ReadDir("/proc")
	if err != nil {
		return nil
	}
	var pids []int
	for _, entry := range entries {
		pid, err := strconv.Atoi(entry.Name())
		if err != nil {
			continue
		}
		dir := "/proc/" + entry.Name()
		if link, err := os.Readlink(dir + "/exe"); err != nil || link != exe {
			continue
		}
		var info syscall.Stat_t
		if syscall.Stat(dir, &info) != nil || info.Uid != uid {
			continue
		}
		pids = append(pids, pid)
	}
	return pids
}

func holdsSocket(pid int, target string) bool {
	dir := "/proc/" + strconv.Itoa(pid) + "/fd/"
	entries, err := os.ReadDir(dir)
	if err != nil {
		return false
	}
	for _, entry := range entries {
		if link, err := os.Readlink(dir + entry.Name()); err == nil && link == target {
			return true
		}
	}
	return false
}

func socketInode(network string, source netip.Addr, port uint16) (uint32, bool) {
	protocol := uint8(syscall.IPPROTO_UDP)
	if strings.HasPrefix(network, "tcp") {
		protocol = syscall.IPPROTO_TCP
	}
	family := uint8(syscall.AF_INET6)
	if source.Is4() {
		family = syscall.AF_INET
	}
	messages, ok := sockDiagDump(family, protocol, port, source.AsSlice())
	if !ok {
		return 0, false
	}
	native := nativeEndian()
	for _, message := range messages {
		data := message.Data
		if len(data) < inetDiagMessageSize || binary.BigEndian.Uint16(data[inetDiagPortOffset:]) != port {
			continue
		}
		var address netip.Addr
		if family == syscall.AF_INET {
			address = netip.AddrFrom4([4]byte(data[inetDiagSourceOffset : inetDiagSourceOffset+4]))
		} else {
			address = netip.AddrFrom16([16]byte(data[inetDiagSourceOffset : inetDiagSourceOffset+16])).Unmap()
		}
		if address == source.Unmap() {
			return native.Uint32(data[inetDiagInodeOffset:]), true
		}
	}
	return 0, false
}

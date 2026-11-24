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
	inetDiagUidOffset    = 64
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
	pid, uid, ok := connectionOwner(metadata.NetWork.String(), metadata.SrcIP, uint16(metadata.SrcPort))
	if !ok {
		return "", process.ErrNotFound
	}
	chain := ancestry(pid)
	if len(chain) == 0 || chain[0].exe == "" {
		return "", process.ErrNotFound
	}
	metadata.ProcessPath = chain[0].exe
	metadata.Uid = uid
	ruled := map[string]struct{}{}
	if loaded := ruledProcesses.Load(); loaded != nil {
		ruled = *loaded
	}
	if owner, ok := ownerOf(chain, ruled); ok {
		return owner, nil
	}
	return filepath.Base(chain[0].exe), nil
}

func ownerOf(chain []processNode, ruled map[string]struct{}) (string, bool) {
	if owner, ok := inheritedOwner(chain, ruled); ok {
		return owner, true
	}
	if len(chain) > 0 && chain[0].app != "" {
		return chain[0].app, true
	}
	return "", false
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

func connectionOwner(network string, source netip.Addr, port uint16) (int, uint32, bool) {
	inode, uid, ok := socketOf(network, source, port)
	if !ok {
		return 0, 0, false
	}
	pid, ok := pidHolding(inode, uid)
	return pid, uid, ok
}

func pidHolding(inode uint32, uid uint32) (int, bool) {
	entries, err := os.ReadDir("/proc")
	if err != nil {
		return 0, false
	}
	target := "socket:[" + strconv.FormatUint(uint64(inode), 10) + "]"
	for _, entry := range entries {
		pid, err := strconv.Atoi(entry.Name())
		if err != nil {
			continue
		}
		var info syscall.Stat_t
		if syscall.Stat("/proc/"+entry.Name(), &info) != nil || info.Uid != uid {
			continue
		}
		if holdsSocket(pid, target) {
			return pid, true
		}
	}
	return 0, false
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

func socketOf(network string, source netip.Addr, port uint16) (uint32, uint32, bool) {
	if inode, uid, ok := socketIn(network, source, port); ok {
		return inode, uid, true
	}
	if source.Is4() {
		return socketIn(network, netip.AddrFrom16(source.As16()), port)
	}
	return 0, 0, false
}

func socketIn(network string, source netip.Addr, port uint16) (uint32, uint32, bool) {
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
		return 0, 0, false
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
			return native.Uint32(data[inetDiagInodeOffset:]), native.Uint32(data[inetDiagUidOffset:]), true
		}
	}
	return 0, 0, false
}

package main

import (
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"syscall"
	"testing"
)

func ruled(ids ...string) map[string]struct{} {
	set := map[string]struct{}{}
	for _, id := range ids {
		set[id] = struct{}{}
	}
	return set
}

func TestAChildTakesTheChannelOfItsNearestRuledAncestor(t *testing.T) {
	chain := []processNode{{40, "/usr/bin/curl", ""}, {30, "/usr/bin/fish", ""}, {20, "/usr/bin/ghostty", ""}, {10, "/usr/bin/Hyprland", ""}, {1, "/usr/lib/systemd/systemd", ""}}
	owner, ok := inheritedOwner(chain, ruled("/usr/bin/ghostty"))
	if !ok || owner != "/usr/bin/ghostty" {
		t.Fatalf("got %q %v", owner, ok)
	}
}

func TestAProcessWithItsOwnRuleKeepsIt(t *testing.T) {
	chain := []processNode{{40, "/usr/bin/curl", ""}, {20, "/usr/bin/ghostty", ""}}
	owner, ok := inheritedOwner(chain, ruled("/usr/bin/ghostty", "/usr/bin/curl"))
	if !ok || owner != "/usr/bin/curl" {
		t.Fatalf("got %q %v", owner, ok)
	}
}

func TestTheDesktopSessionPassesNothingDown(t *testing.T) {
	chain := []processNode{{40, "/usr/bin/zen-bin", ""}, {10, "/usr/bin/Hyprland", ""}, {1, "/usr/lib/systemd/systemd", ""}}
	if owner, ok := inheritedOwner(chain, ruled("/usr/bin/Hyprland", "/usr/lib/systemd/systemd")); ok {
		t.Fatalf("inherited %q from the session", owner)
	}
	if owner, ok := inheritedOwner(chain[1:], ruled("/usr/bin/Hyprland")); !ok || owner != "/usr/bin/Hyprland" {
		t.Fatalf("the compositor lost its own rule: %q %v", owner, ok)
	}
}

func TestAnOrphanFallsBackToTheDefault(t *testing.T) {
	chain := []processNode{{40, "/usr/bin/bash", ""}, {1, "/usr/lib/systemd/systemd", ""}}
	if owner, ok := inheritedOwner(chain, ruled("/usr/bin/ghostty")); ok {
		t.Fatalf("an orphan inherited %q", owner)
	}
}

func TestNameRulesAndReplacedBinariesMatch(t *testing.T) {
	chain := []processNode{{40, "/usr/bin/curl", ""}, {20, "/opt/zen/zen-bin (deleted)", ""}}
	if owner, ok := inheritedOwner(chain, ruled("/opt/zen/zen-bin (deleted)")); !ok || owner != "/opt/zen/zen-bin (deleted)" {
		t.Fatalf("got %q %v", owner, ok)
	}
	if owner, ok := inheritedOwner(chain, ruled("zen-bin (deleted)")); !ok || owner != "zen-bin (deleted)" {
		t.Fatalf("got %q %v", owner, ok)
	}
}

func TestTheParentIsReadPastAProcessNameWithBrackets(t *testing.T) {
	parent, ok := parentPid("4242 (weird ) name) S 17 4242 4242 0 -1")
	if !ok || parent != 17 {
		t.Fatalf("got %d %v", parent, ok)
	}
}

func TestRuledProcessesComeFromTheConfigRules(t *testing.T) {
	setRuledProcesses(`{"rules":["PROCESS-NAME,ciadpi,DIRECT","PROCESS-PATH,/usr/bin/a,p1","PROCESS-NAME,/usr/bin/a,p1",` +
		`"AND,((OR,((PROCESS-PATH,/usr/bin/b),(PROCESS-NAME,/usr/bin/b))),(DOMAIN,x.org)),DIRECT","MATCH,p1"]}`)
	got := *ruledProcesses.Load()
	want := ruled("ciadpi", "/usr/bin/a", "/usr/bin/b")
	if len(got) != len(want) {
		t.Fatalf("got %v", got)
	}
	for id := range want {
		if _, ok := got[id]; !ok {
			t.Fatalf("missing %q in %v", id, got)
		}
	}
}

func TestTheOwnerOfALiveSocketIsFound(t *testing.T) {
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Skip(err)
	}
	defer listener.Close()
	port := uint16(listener.Addr().(*net.TCPAddr).Port)
	pid, uid, ok := connectionOwner("tcp", netip.MustParseAddr("127.0.0.1"), port)
	if !ok {
		t.Skip("sock_diag is unavailable")
	}
	if pid != os.Getpid() || uid != uint32(os.Getuid()) {
		t.Fatalf("owner %d/%d, want %d/%d", pid, uid, os.Getpid(), os.Getuid())
	}
}

func TestADualStackSocketTalkingToIPv4IsFound(t *testing.T) {
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Skip(err)
	}
	defer listener.Close()
	fd, err := syscall.Socket(syscall.AF_INET6, syscall.SOCK_STREAM, 0)
	if err != nil {
		t.Skip(err)
	}
	defer syscall.Close(fd)
	_ = syscall.SetsockoptInt(fd, syscall.IPPROTO_IPV6, syscall.IPV6_V6ONLY, 0)
	mapped := netip.AddrFrom16(netip.MustParseAddr("127.0.0.1").As16()).As16()
	if err = syscall.Connect(fd, &syscall.SockaddrInet6{Port: listener.Addr().(*net.TCPAddr).Port, Addr: mapped}); err != nil {
		t.Skip(err)
	}
	local, _ := syscall.Getsockname(fd)
	port := uint16(local.(*syscall.SockaddrInet6).Port)
	pid, _, ok := connectionOwner("tcp", netip.MustParseAddr("127.0.0.1"), port)
	if !ok || pid != os.Getpid() {
		t.Fatalf("a dual-stack socket was not found (%d, %v)", pid, ok)
	}
}

func TestAFlatpakAppIsKnownByItsIdInsideAndAroundItsSandbox(t *testing.T) {
	scope := "0::/user.slice/user-1000.slice/user@1000.service/app.slice/app-flatpak-org.telegram.desktop-1443230209.scope\n"
	info := "[Application]\nname=org.telegram.desktop\nruntime=runtime/org.kde.Platform/x86_64/6.9\n\n[Instance]\nname=other\n"
	if got := flatpakAppOf(info, ""); got != "org.telegram.desktop" {
		t.Fatalf("info: %q", got)
	}
	if got := flatpakAppOf("", scope); got != "org.telegram.desktop" {
		t.Fatalf("scope: %q", got)
	}
	if got := flatpakAppOf("", "0::/user.slice/user-1000.slice/session-3.scope\n"); got != "" {
		t.Fatalf("host: %q", got)
	}
	chain := []processNode{{40, "/app/bin/Telegram", "org.telegram.desktop"}, {30, "/usr/bin/bwrap", ""}, {10, "/usr/bin/Hyprland", ""}}
	owner, ok := inheritedOwner(chain, map[string]struct{}{"org.telegram.desktop": {}})
	if !ok || owner != "org.telegram.desktop" {
		t.Fatalf("owner %q %v", owner, ok)
	}
}

func TestTheHostProcessTableDescribesTheTestItself(t *testing.T) {
	own := os.Getpid()
	for _, process := range hostProcesses() {
		if process.PID != own {
			continue
		}
		if process.Parent != os.Getppid() || process.Exe == "" || process.Name != filepath.Base(process.Exe) || process.Unit == "" {
			t.Fatalf("unexpected %+v", process)
		}
		return
	}
	t.Fatal("own process missing")
}

func TestTheUnitIsTheLastCgroupComponent(t *testing.T) {
	if got := cgroupUnit("0::/user.slice/user-1000.slice/user@1000.service/app.slice/app-flatpak-org.telegram.desktop-1.scope\n"); got != "app-flatpak-org.telegram.desktop-1.scope" {
		t.Fatalf("%q", got)
	}
}

func TestAFlatpakAppWithoutARuleIsStillNamedByItsId(t *testing.T) {
	chain := []processNode{{50, "/app/bin/Telegram", "org.telegram.desktop"}, {45, "/usr/bin/bwrap", "org.telegram.desktop"}, {10, "/usr/bin/Hyprland", ""}}
	if owner, ok := ownerOf(chain, map[string]struct{}{}); !ok || owner != "org.telegram.desktop" {
		t.Fatalf("owner %q %v", owner, ok)
	}
	sandboxed := []processNode{{60, "/app/lib/erkin/ciadpi", "io.github.yulbax.erkin"}}
	if owner, _ := ownerOf(sandboxed, map[string]struct{}{"ciadpi": {}}); owner != "ciadpi" {
		t.Fatalf("a ruled name must win over the Flatpak id, got %q", owner)
	}
	host := []processNode{{70, "/usr/bin/curl", ""}, {1, "/usr/lib/systemd/systemd", ""}}
	if owner, ok := ownerOf(host, map[string]struct{}{}); ok {
		t.Fatalf("an unruled host process keeps its own name, got %q", owner)
	}
}

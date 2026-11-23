package main

import (
	"net"
	"net/netip"
	"os"
	"strconv"
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
	inode, ok := socketInode("tcp", netip.MustParseAddr("127.0.0.1"), port)
	if !ok {
		t.Skip("sock_diag is unavailable")
	}
	target := "socket:[" + strconv.FormatUint(uint64(inode), 10) + "]"
	if !holdsSocket(os.Getpid(), target) {
		t.Fatalf("this process does not hold %s", target)
	}
	self, _ := os.Readlink("/proc/self/exe")
	found := false
	for _, pid := range processesRunning(self, uint32(os.Getuid())) {
		found = found || pid == os.Getpid()
	}
	if !found {
		t.Fatal("this test process was not among the running copies of its executable")
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

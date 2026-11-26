<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" alt="ERKIN icon" />

# ERKIN

**A per-app split VPN for Android, Windows and Linux.**

</div>

ERKIN tunnels each app independently. Instead of an all-or-nothing VPN switch,
you decide — per app — whether traffic should go through a proxy, through a built-in
DPI-bypass engine, or straight out untouched. All of them can run at the same time.

## Download

Get the latest APKs, the Windows installer and the Linux packages from
[Releases](https://github.com/yulbax/ERKIN/releases/latest).

- **Android** — `ERKIN-<version>-android-arm64-v8a.apk` fits almost every phone; `universal`
  works everywhere. Android 10 or newer.
- **Windows** — `ERKIN-<version>-windows-x86_64.msi`, Windows 10/11. To update, install the newer
  MSI over the old one; your servers and settings are kept.
  `ERKIN-<version>-windows-x86_64-portable.zip` is the portable build: it runs from any folder
  without installation and shares its data with the installed ERKIN.
- **Linux** — `ERKIN-<version>-linux-x86_64.deb` (Debian, Ubuntu), `.rpm` (Fedora, openSUSE) or
  `.pkg.tar.zst` (Arch).

The Android app and the installed Windows app check GitHub for new versions and update
themselves.

## Features

### Routing

- **Per-app routing.** Assign every app one of five modes:
  - **Bypass** — leaves the device around ERKIN, with its real IP; the app does not see
    the VPN. Android only.
  - **Direct** — goes through ERKIN straight out, so rules apply and it shows up in
    Connections.
  - **VPN** — tunnelled through your proxy server, the main one or a server or chain of its own.
  - **ByeDPI** — routed through a built-in engine that defeats DPI-based censorship
    locally, without any remote server.
  - **Block** — no network at all while ERKIN is connected.
- **Routing rules.** Send domains, keywords, IP ranges, countries and rule lists to Direct,
  ByeDPI, VPN or Block, for every app or for a single one, each VPN rule through the main
  server or a server or chain of its own. Rules apply from top to bottom. Ready rule sets
  cover ads, telemetry, country IP ranges and services such as YouTube, Telegram and Discord.
- **Chains.** Route through several servers in a row, optionally starting with ByeDPI, and
  measure the whole chain.
- **Connections.** See where every app goes while connected — traffic per channel, the
  addresses each app opened and the channel or rule that routed them — and turn an address
  into a rule for that app.
- **Desktop.** On Windows and Linux apps are matched by their program, and traffic of
  programs without a channel goes straight out.

### Servers

- **Protocols.** VLESS, VMess, Trojan, Shadowsocks (with obfs and v2ray-plugin,
  UDP over TCP), ShadowsocksR, Hysteria, Hysteria2 (with port hopping), TUIC, AnyTLS, mieru,
  SSH, SOCKS5, HTTP and HTTPS proxies, WireGuard, AmneziaWG and OpenVPN. Snell, sudoku,
  MASQUE, TrustTunnel, ShadowQUIC and GOST relay servers come in through Clash/mihomo
  subscriptions.
- **Transports.** TCP, WebSocket, gRPC, HTTP/2, HTTPUpgrade and XHTTP, with TLS /
  Reality and configurable uTLS fingerprints.
- **Import formats.** Share links, including ones with spaces in names or HTML-escaped
  parameters, pasted lists of links, base64 subscriptions, Clash/mihomo YAML, sing-box and
  Xray JSON configs, SIP008, WireGuard and AmneziaWG config files, Amnezia `vpn://` and
  `amneziawg://` links, and OpenVPN `.ovpn` files or `ovpn://` links. On Android a `.ovpn`
  file opens straight in ERKIN, and an OpenVPN server that needs a login asks for it.
- **Subscriptions and groups.** Servers added together form a group; a subscription keeps
  its group up to date and shows its traffic, expiry date, last update and the provider's
  announcement and links. Each group remembers its server, and servers can move between groups.
- **Subscription rules.** A Clash/mihomo subscription can bring its rules. They are off by
  default and, when on, apply after your own rules while the main server comes from that
  subscription.
- **Latency.** Test the servers on any page of the server picker and sort a group by ping;
  while connected, the servers bound to apps and rules are checked in the background.
- **Self-healing.** A health check watches the VPN and ByeDPI. When the server stops
  answering, ERKIN switches at once to another server of its group or subscription that
  answers; if none does, it reloads the core and refreshes the subscription.

### ByeDPI

- **Strategies.** Test strategies against the site lists you pick, find a working one from
  a ready list, and see per-network results. Tested strategies are kept in a history you
  can pin and reuse.

### Privacy

- **No leak to other apps.** On Android an app reaches your server only through the VPN
  tunnel, and only if you route it there. ERKIN exposes no password-free SOCKS or HTTP port
  into the core: its only local listener, used for health checks and latency tests, takes a
  random password generated on every connect, so another app cannot send traffic through
  your server or learn its exit IP. Bypass apps never enter the tunnel at all.
- **No system proxy, control API or telemetry.** ERKIN does not change the system proxy
  settings, opens no control API and sends no telemetry.

### Everyday use

- **Quick Settings tile.** On Android, turn ERKIN on or off from the notification shade
  without opening the app.
- **Auto-connect.** Optionally connect when ERKIN starts and, on Android, after a reboot.
- **Backups.** Export servers, apps, rules and settings to a file and import them on another
  device.
- **Desktop window.** Closing the window while connected asks first, and minimizing hides
  ERKIN to the system tray.
- **Languages.** English, Russian, Ukrainian, Uzbek, Turkish, Persian and Chinese, switchable
  inside the app, with built-in help in every language.

## How it works

ERKIN combines two engines behind a single tunnel interface:

- **[mihomo](https://github.com/MetaCubeX/mihomo)** owns the tunnel and dispatches traffic
  per app — *VPN* apps to the upstream server, *ByeDPI* apps to the local bypass proxy,
  *Direct* apps straight out; on Android *Bypass* apps never enter the tunnel. ERKIN uses
  the official mihomo release behind a thin wrapper (`core/mihomo`). The same core runs
  everywhere: as a Go Mobile library inside the app on Android, and inside the `erkin-service`
  background service on the desktop — a Windows service or a systemd unit that starts with the app and stops shortly
  after it closes. The portable Windows build runs the same core as a child process. The
  desktop app talks to it over a local connection; if the app goes away, the tunnel is torn
  down.
- **[ByeDPI](https://github.com/hufrea/byedpi)** runs as a local proxy that
  desynchronizes packets to slip past Deep Packet Inspection.

## Building

ERKIN is fully open-source — no Google services, no proprietary dependencies.

The mihomo core is built from source by `scripts/build-mihomo.sh` (Android) and
`scripts/build-service.sh` (desktop service) rather than committed as a binary. It needs
Go 1.24 or newer.

### Android

Install the `gomobile` tools and point `ANDROID_NDK_HOME` at NDK 28:

```bash
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20250808145247-395d808d53cd
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20250808145247-395d808d53cd
export PATH="$(go env GOPATH)/bin:$PATH"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
bash scripts/build-mihomo.sh
./gradlew assembleGithubRelease
```

Building requires Android SDK 37 and CMake 3.22.1. A physical device is recommended;
Android's VPN APIs are unreliable on emulators.

### Windows

The tunnel runs in a small background service, `erkin-service.exe`, built from the same core
(no C toolchain needed):

```bash
TARGETS=windows bash scripts/build-service.sh   # → desktop/libs/windows/erkin-service.exe
```

The installer itself must be built on Windows with JDK 21:

```bash
./gradlew -Perkin.desktopOnly :desktop:packageServiceMsi
```

The portable zip is built the same way and also needs `mt.exe` from the Windows SDK:

```bash
./gradlew -Perkin.desktopOnly :desktop:packagePortableZip
```

`-Perkin.desktopOnly` skips the Android modules, so no Android SDK is needed there.

### Linux

One script builds the service, the app and the `.deb`, `.rpm` and Arch packages with
[nFPM](https://nfpm.goreleaser.com) 2.47 ([prebuilt binaries](https://github.com/goreleaser/nfpm/releases/tag/v2.47.0)):

```bash
bash scripts/build-linux-packages.sh          # FORMATS="archlinux" for just one
sudo pacman -U desktop/build/linux-packages/ERKIN-<version>-linux-x86_64.pkg.tar.zst
```

The packages install to `/opt/erkin` and ship `erkin.service`; removing the package stops
and removes the service again.

## Built with

- [mihomo](https://github.com/MetaCubeX/mihomo) — the VPN core
- [ByeDPI](https://github.com/hufrea/byedpi) — the DPI-bypass engine
- Kotlin Multiplatform, Compose Multiplatform, Room, Koin, JNA,
  [ComposeNativeTray](https://github.com/kdroidfilter/ComposeNativeTray)

## License

ERKIN is licensed under the **GNU General Public License v3.0** — see [`LICENSE`](LICENSE).

ByeDPI retains its own license; see `app/src/main/cpp/byedpi/LICENSE`.

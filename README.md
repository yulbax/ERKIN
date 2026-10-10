<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" alt="ERKIN icon" />

# ERKIN

**A per-app split VPN for Android, Windows and Linux.**

</div>

ERKIN tunnels each app independently. Instead of an all-or-nothing VPN switch,
you decide — per app — whether traffic should go through a proxy, through a built-in
DPI-bypass engine, or straight out untouched. All three can run at the same time.

## Download

Get the latest APKs, the Windows installer and the Linux packages from
[Releases](https://github.com/yulbax/ERKIN/releases/latest).

- **Android** — `ERKIN-<version>-android-arm64-v8a.apk` fits almost every phone; `universal`
  works everywhere. Android 10 or newer.
- **Windows** — `ERKIN-<version>-windows-x86_64.msi`, Windows 10/11. To update, install the newer
  MSI over the old one; your servers and settings are kept.
- **Linux** — `ERKIN-<version>-linux-x86_64.deb` (Debian, Ubuntu), `.rpm` (Fedora, openSUSE) or
  `.pkg.tar.zst` (Arch).

## Features

- **Per-app routing.** Assign every app one of three modes:
  - **Direct** — leaves the device normally, with its real IP and no overhead.
  - **VPN** — tunnelled through your proxy server.
  - **ByeDPI** — routed through a built-in engine that defeats DPI-based censorship
    locally, without any remote server.
- **Wide protocol support.** Import servers from `vless`, `vmess`, `trojan`,
  `shadowsocks`, `hysteria2` and `wireguard` share links, AmneziaWG `vpn://` links,
  WireGuard / AmneziaWG `.conf` files, or a subscription URL.
- **Modern transports.** TCP, WebSocket, gRPC, HTTP and HTTPUpgrade, with TLS / Reality
  and configurable uTLS fingerprints.
- **Desktop extras.** On Windows and Linux apps are matched by process name; an optional
  mode sends all traffic that has no explicit rule through the VPN. Closing the window while
  connected asks first, and minimizing hides ERKIN to the system tray.
- **Stays out of the way.** No system-wide proxy, no exposed control API, optional
  auto-connect, and a built-in connection health check.

## How it works

ERKIN combines two engines behind a single tunnel interface:

- **sing-box** owns the tunnel and dispatches traffic per app — *VPN* apps to the
  upstream server, *ByeDPI* apps to the local bypass proxy, *Direct* apps straight out.
  ERKIN uses [amnezia-box](https://github.com/amnezia-vpn/amnezia-box), the Amnezia fork
  of [sing-box](https://github.com/SagerNet/sing-box) that adds AmneziaWG. The same core
  runs everywhere: as a Go Mobile library inside the app on Android, and as the
  `erkin-service` background service (a Windows service or a systemd unit) on the desktop.
  The desktop app talks to it over a local socket; if the app goes away, the service tears
  the tunnel down.
- **[ByeDPI](https://github.com/hufrea/byedpi)** runs as a local proxy that
  desynchronizes packets to slip past Deep Packet Inspection.

## Building

ERKIN is fully open-source — no Google services, no proprietary dependencies.

The sing-box core is built from source by `scripts/build-libbox.sh` rather than committed
as a binary. It needs Go 1.25.

### Android

Install the SagerNet `gomobile` tools and point `ANDROID_NDK_HOME` at NDK 28:

```bash
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.13
export PATH="$(go env GOPATH)/bin:$PATH"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
CORE_TARGETS=android bash scripts/build-libbox.sh
./gradlew assembleRelease
```

Building requires Android SDK 37 and CMake 3.22.1. A physical device is recommended;
Android's VPN APIs are unreliable on emulators.

### Windows

The tunnel runs in a small background service, `erkin-service.exe`, built from the same core
(no C toolchain needed):

```bash
CORE_TARGETS=windows bash scripts/build-libbox.sh   # → desktop/libs/windows/erkin-service.exe
```

The installer itself must be built on Windows with JDK 21:

```bash
./gradlew -Perkin.desktopOnly :desktop:packageServiceMsi
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

- [amnezia-box](https://github.com/amnezia-vpn/amnezia-box) /
  [sing-box](https://github.com/SagerNet/sing-box) — the VPN core
- [ByeDPI](https://github.com/hufrea/byedpi) — the DPI-bypass engine
- Kotlin Multiplatform, Compose Multiplatform, Room, Koin, JNA,
  [ComposeNativeTray](https://github.com/kdroidfilter/ComposeNativeTray)

## License

ERKIN is licensed under the **GNU General Public License v3.0** — see [`LICENSE`](LICENSE).

ByeDPI retains its own license; see `app/src/main/cpp/byedpi/LICENSE`.

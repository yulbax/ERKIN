#!/usr/bin/env bash
# Builds the desktop erkin-service (desktop/service) for Windows, Linux and macOS. The service
# embeds the official mihomo core (version pinned in core/mihomo/go.mod) and runs it
# with the privileges a TUN needs, driven by the ERKIN app over a local socket.
#
# Knobs: TARGETS (default "windows linux"; an entry is os or os/arch, e.g. darwin/arm64 — the
# arch defaults to amd64, macOS binaries land in desktop/libs/macos-<arch>), FORCE_REBUILD=1 to
# rebuild existing binaries.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_VERSION="$(sed -n 's/^erkin\.versionName=//p' "${REPO_ROOT}/gradle.properties" | head -n1)"
TARGETS="${TARGETS:-windows linux}"

say() { printf '\033[1;36m>>> %s\033[0m\n' "$*"; }

cd "${REPO_ROOT}/desktop/service"
go mod download github.com/metacubex/mihomo
MIHOMO_VERSION="$(go list -m -f '{{.Version}}' github.com/metacubex/mihomo)"
MIHOMO_DIR="$(go list -m -f '{{.Dir}}' github.com/metacubex/mihomo)"
for target in $TARGETS; do
  os="${target%%/*}"
  arch="amd64"
  [ "$target" != "$os" ] && arch="${target#*/}"
  suffix=""
  dir="$os"
  [ "$os" = "windows" ] && suffix=".exe"
  [ "$os" = "darwin" ] && dir="macos-${arch}"
  out="${REPO_ROOT}/desktop/libs/${dir}/erkin-service${suffix}"
  if [ "${FORCE_REBUILD:-0}" != "1" ] && [ -f "$out" ]; then
    say "$(basename "$out") for $os already built — skipping (FORCE_REBUILD=1 to override)."
    continue
  fi
  say "Building erkin-service ${APP_VERSION} for ${os}/${arch} with mihomo ${MIHOMO_VERSION}"
  mkdir -p "$(dirname "$out")"
  CGO_ENABLED=0 GOOS="$os" GOARCH="$arch" go build -trimpath -buildvcs=false -tags "with_gvisor,no_tailscale,no_easytier,no_zerotier,no_fake_tcp" \
    -ldflags "-X github.com/metacubex/mihomo/constant.Version=${MIHOMO_VERSION} -X main.version=${APP_VERSION} -s -w -buildid=" \
    -o "$out" .
  install -m644 "${MIHOMO_DIR}/LICENSE" "$(dirname "$out")/mihomo-LICENSE.txt"
  ls -la "$out"
done

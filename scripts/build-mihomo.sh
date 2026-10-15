#!/usr/bin/env bash
# Builds app/libs/mihomo.aar: the official mihomo core (version pinned in
# core/mihomo/go.mod) behind the thin gomobile wrapper in core/mihomo, which starts
# the TUN from the VpnService fd and resolves app packages and socket protection
# through Kotlin callbacks, the same way ClashMetaForAndroid does.
#
# Needs go, gomobile + gobind (golang.org/x/mobile, the version pinned in core/mihomo/go.mod),
# ANDROID_HOME and ANDROID_NDK_HOME. Knobs: FORCE_REBUILD=1 to rebuild an existing aar.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_AAR="${REPO_ROOT}/app/libs/mihomo.aar"
TAGS="cmfa,with_gvisor,no_tailscale,no_easytier,no_zerotier,no_fake_tcp"

say() { printf '\033[1;36m>>> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m!!! %s\033[0m\n' "$*" >&2; exit 1; }

if [ "${FORCE_REBUILD:-0}" != "1" ] && [ -f "$OUT_AAR" ]; then
  say "mihomo.aar already built — skipping (FORCE_REBUILD=1 to override)."
  exit 0
fi
command -v go >/dev/null || die "go not in PATH"
command -v gomobile >/dev/null || die "gomobile not in PATH (go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20250808145247-395d808d53cd)"
command -v gobind >/dev/null || die "gobind not in PATH (go install golang.org/x/mobile/cmd/gobind@v0.0.0-20250808145247-395d808d53cd)"
[ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ] || die "ANDROID_NDK_HOME unset or missing"

cd "${REPO_ROOT}/core/mihomo"
MIHOMO_VERSION="$(go list -m -f '{{.Version}}' github.com/metacubex/mihomo)"
say "Building mihomo ${MIHOMO_VERSION} for android/arm64, android/amd64, android/arm"
mkdir -p "$(dirname "$OUT_AAR")"
GOFLAGS="${GOFLAGS:-} -buildvcs=false" gomobile bind -v \
  -o "$OUT_AAR" \
  -target=android/arm64,android/amd64,android/arm \
  -androidapi 29 \
  -trimpath \
  -ldflags "-X github.com/metacubex/mihomo/constant.Version=${MIHOMO_VERSION} -s -w -buildid=" \
  -tags "$TAGS" \
  .
say "Built $(du -h "$OUT_AAR" | cut -f1) $OUT_AAR"

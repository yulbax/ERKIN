#!/usr/bin/env bash
# Builds ciadpi.exe for the Windows desktop app from the pinned byedpi tag with
# byedpi-windows-protect.patch applied: it enables --protect-path on Windows, where
# ciadpi asks erkin-service for the physical interface index and pins its outgoing
# sockets to it (IP_UNICAST_IF), so desync packets bypass the TUN untouched.
#
# Knobs (env vars):
#   CC              Windows C compiler (default x86_64-w64-mingw32-gcc)
#   FORCE_REBUILD=1 build even if desktop/libs/windows/ciadpi.exe already exists
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BYEDPI_TAG="v0.17.3"
BYEDPI_SHA256="0a9cb8585554c68c3e2be88c33c9bf6f99f8e8c7f54b362285adab99e262566c"
CC="${CC:-x86_64-w64-mingw32-gcc}"
OUT="${REPO_ROOT}/desktop/libs/windows/ciadpi.exe"

if [ "${FORCE_REBUILD:-0}" != "1" ] && [ -f "$OUT" ]; then
  echo "ciadpi.exe already built — skipping (FORCE_REBUILD=1 to override)."
  exit 0
fi
command -v "${CC%% *}" >/dev/null || { echo "$CC not found (install the mingw-w64 gcc cross compiler)" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
curl -fsSL -o "$WORK/byedpi.tar.gz" "https://github.com/hufrea/byedpi/archive/refs/tags/${BYEDPI_TAG}.tar.gz"
echo "${BYEDPI_SHA256}  $WORK/byedpi.tar.gz" | sha256sum -c -
tar -xzf "$WORK/byedpi.tar.gz" -C "$WORK"
SRC="$WORK/byedpi-${BYEDPI_TAG#v}"
patch -d "$SRC" -p1 < "$SCRIPT_DIR/byedpi-windows-protect.patch"
make -C "$SRC" windows CC="$CC" WIN_LDFLAGS="-lws2_32 -lmswsock -s"
mkdir -p "$(dirname "$OUT")"
cp "$SRC/ciadpi.exe" "$OUT"
echo "Built $OUT"

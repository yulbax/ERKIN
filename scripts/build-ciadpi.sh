#!/usr/bin/env bash
# Builds ciadpi for the desktop app from the pinned byedpi tag with a protect patch applied:
# - windows: byedpi-windows-protect.patch enables --protect-path on Windows, where ciadpi asks
#   erkin-service for the physical interface index and pins its outgoing sockets to it
#   (IP_UNICAST_IF), so desync packets bypass the TUN untouched.
# - macos-arm64 / macos-amd64: byedpi-macos-protect.patch enables the upstream Linux
#   --protect-path (the socket is handed to erkin-service, which binds it with IP_BOUND_IF).
#
# Knobs (env vars):
#   TARGET          windows (default), macos-arm64 or macos-amd64
#   CC              C compiler (default x86_64-w64-mingw32-gcc for windows, zig cc for macOS)
#   FORCE_REBUILD=1 build even if the binary already exists
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BYEDPI_TAG="v0.17.3"
BYEDPI_SHA256="0a9cb8585554c68c3e2be88c33c9bf6f99f8e8c7f54b362285adab99e262566c"
TARGET="${TARGET:-windows}"

case "$TARGET" in
  windows)
    CC="${CC:-x86_64-w64-mingw32-gcc}"
    PATCH="byedpi-windows-protect.patch"
    BUILT="ciadpi.exe"
    MAKE_ARGS=(windows WIN_LDFLAGS="-lws2_32 -lmswsock -s")
    ;;
  macos-arm64 | macos-amd64)
    arch="${TARGET#macos-}"
    [ "$arch" = "amd64" ] && arch="x86_64" || arch="aarch64"
    CC="${CC:-zig cc -target ${arch}-macos.12.0}"
    PATCH="byedpi-macos-protect.patch"
    BUILT="ciadpi"
    MAKE_ARGS=()
    ;;
  *)
    echo "unknown TARGET $TARGET (windows, macos-arm64, macos-amd64)" >&2
    exit 1
    ;;
esac
OUT="${REPO_ROOT}/desktop/libs/${TARGET}/${BUILT}"

if [ "${FORCE_REBUILD:-0}" != "1" ] && [ -f "$OUT" ]; then
  echo "$BUILT for $TARGET already built — skipping (FORCE_REBUILD=1 to override)."
  exit 0
fi
command -v "${CC%% *}" >/dev/null || { echo "$CC not found (install the C cross compiler for $TARGET)" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
curl -fsSL -o "$WORK/byedpi.tar.gz" "https://github.com/hufrea/byedpi/archive/refs/tags/${BYEDPI_TAG}.tar.gz"
if command -v sha256sum >/dev/null; then sha256=(sha256sum); else sha256=(shasum -a 256); fi
echo "${BYEDPI_SHA256}  $WORK/byedpi.tar.gz" | "${sha256[@]}" -c -
tar -xzf "$WORK/byedpi.tar.gz" -C "$WORK"
SRC="$WORK/byedpi-${BYEDPI_TAG#v}"
patch -d "$SRC" -p1 < "$SCRIPT_DIR/$PATCH"
make -C "$SRC" ${MAKE_ARGS[@]+"${MAKE_ARGS[@]}"} CC="$CC"
mkdir -p "$(dirname "$OUT")"
cp "$SRC/$BUILT" "$OUT"
echo "Built $OUT"

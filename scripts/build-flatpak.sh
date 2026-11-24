#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGE="$REPO_ROOT/desktop/build/flatpak-stage"
BUILD="$REPO_ROOT/desktop/build/flatpak"
MANIFEST="$REPO_ROOT/desktop/packaging/flatpak/io.github.yulbax.erkin.yml"
BUILDER="${FLATPAK_BUILDER:-$(command -v flatpak-builder || echo "flatpak run org.flatpak.Builder")}"

if [ "${SKIP_CORE:-0}" != "1" ]; then
  TARGETS=linux bash "$REPO_ROOT/scripts/build-service.sh"
fi
(cd "$REPO_ROOT" && ./gradlew -Perkin.desktopOnly :desktop:prepareLinuxBinaries :desktop:createReleaseDistributable)

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -a "$REPO_ROOT/desktop/build/compose/binaries/main-release/app/ERKIN" "$STAGE/ERKIN"
mkdir -p "$STAGE/ERKIN/lib/app/resources"
cp "$REPO_ROOT/desktop/build/bundled-resources/linux/"* "$STAGE/ERKIN/lib/app/resources/"
chmod 755 "$STAGE/ERKIN/lib/app/resources/erkin-service" "$STAGE/ERKIN/lib/app/resources/ciadpi"
cp "$REPO_ROOT/desktop/packaging/flatpak/io.github.yulbax.erkin.desktop" "$STAGE/"
cp "$REPO_ROOT/desktop/src/main/resources/erkin-icon.png" "$STAGE/erkin.png"
cp "$REPO_ROOT/LICENSE" "$STAGE/LICENSE"

$BUILDER --user --install --force-clean --state-dir="$BUILD/state" "$BUILD/app" "$MANIFEST"

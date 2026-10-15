#!/usr/bin/env bash
# Builds the Linux packages (.deb, .rpm, Arch .pkg.tar.zst) with nFPM into desktop/build/linux-packages.
#   SKIP_CORE=1   reuse desktop/libs/linux/erkin-service instead of building it (CI downloads it)
#   FORMATS       subset of "deb rpm archlinux"
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(sed -n 's/^erkin\.versionName=//p' "$REPO_ROOT/gradle.properties" | head -n1)"
FORMATS="${FORMATS:-deb rpm archlinux}"
STAGE="$REPO_ROOT/desktop/build/linux-stage"
OUT="$REPO_ROOT/desktop/build/linux-packages"
NFPM="${NFPM:-$(command -v nfpm || echo "$(go env GOPATH 2>/dev/null)/bin/nfpm")}"
[ -x "$NFPM" ] || { echo "nfpm not found: install it from https://github.com/goreleaser/nfpm/releases/tag/v2.47.0" >&2; exit 1; }

if [ "${SKIP_CORE:-0}" != "1" ]; then
  TARGETS=linux bash "$REPO_ROOT/scripts/build-service.sh"
fi
(cd "$REPO_ROOT" && ./gradlew -Perkin.desktopOnly :desktop:prepareLinuxBinaries :desktop:createReleaseDistributable)

rm -rf "$STAGE" "$OUT"
mkdir -p "$STAGE" "$OUT"
cp -a "$REPO_ROOT/desktop/build/compose/binaries/main-release/app/ERKIN" "$STAGE/ERKIN"
mkdir -p "$STAGE/ERKIN/lib/app/resources"
cp "$REPO_ROOT/desktop/build/bundled-resources/linux/"* "$STAGE/ERKIN/lib/app/resources/"
chmod 755 "$STAGE/ERKIN/lib/app/resources/erkin-service" "$STAGE/ERKIN/lib/app/resources/ciadpi"

cp -r "$REPO_ROOT/desktop/packaging/linux/." "$STAGE/"
cp "$REPO_ROOT/desktop/src/main/resources/erkin-icon.png" "$STAGE/erkin.png"
cp "$REPO_ROOT/LICENSE" "$STAGE/LICENSE"
sed -e "s|\${ERKIN_VERSION}|$VERSION|" \
    -e "s|\${ERKIN_ICON}|./erkin.png|" -e "s|\${ERKIN_LICENSE}|./LICENSE|" \
    "$REPO_ROOT/desktop/packaging/linux/nfpm.yaml" > "$STAGE/nfpm.yaml"
(
  cd "$STAGE"
  find ERKIN -type d | sort | while read -r dir; do
    printf '  - dst: /opt/erkin%s\n    type: dir\n    file_info:\n      mode: 0755\n' "${dir#ERKIN}"
  done
  find ERKIN -type f | sort | while read -r file; do
    printf '  - src: ./%s\n    dst: /opt/erkin%s\n    file_info:\n      mode: 0%s\n' "$file" "${file#ERKIN}" "$(stat -c %a "$file")"
  done
) > "$STAGE/app-contents.yaml"
awk -v contents="$STAGE/app-contents.yaml" '{ print } /^contents:$/ { while ((getline line < contents) > 0) print line }' \
    "$STAGE/nfpm.yaml" > "$STAGE/nfpm.full.yaml"
mv "$STAGE/nfpm.full.yaml" "$STAGE/nfpm.yaml"
cd "$STAGE"
for format in $FORMATS; do
  case "$format" in
    deb) file="ERKIN-$VERSION-linux-x86_64.deb" ;;
    rpm) file="ERKIN-$VERSION-linux-x86_64.rpm" ;;
    archlinux) file="ERKIN-$VERSION-linux-x86_64.pkg.tar.zst" ;;
    *) echo "unknown format: $format" >&2; exit 1 ;;
  esac
  "$NFPM" package --config nfpm.yaml --packager "$format" --target "$OUT/$file"
done
ls -la "$OUT"

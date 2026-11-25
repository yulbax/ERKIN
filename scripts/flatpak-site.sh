#!/usr/bin/env bash
set -euo pipefail

: "${FLATPAK_REPO:?}" "${FLATPAK_GPG_KEY:?}" "${GNUPGHOME:?}"
SITE="${1:?site directory}"
BASE_URL="${2:?site address}"
BUNDLE="${3:?bundle file}"
APP_ID=io.github.yulbax.erkin
BRANCH=master
RUNTIME_REPO=https://dl.flathub.org/repo/flathub.flatpakrepo
REPO_URL="${BASE_URL%/}/flatpak/"
PUBLIC_KEY="$(mktemp)"
trap 'rm -f "$PUBLIC_KEY"' EXIT

flatpak build-update-repo --gpg-sign="$FLATPAK_GPG_KEY" --gpg-homedir="$GNUPGHOME" \
  --generate-static-deltas --title=ERKIN "$FLATPAK_REPO"
gpg --export "$FLATPAK_GPG_KEY" > "$PUBLIC_KEY"
GPG_KEY="$(base64 -w0 "$PUBLIC_KEY")"

flatpak build-bundle --runtime-repo="$RUNTIME_REPO" --repo-url="$REPO_URL" --gpg-keys="$PUBLIC_KEY" \
  "$FLATPAK_REPO" "$BUNDLE" "$APP_ID" "$BRANCH"

rm -rf "$SITE"
mkdir -p "$SITE"
cp -a "$FLATPAK_REPO" "$SITE/flatpak"

cat > "$SITE/erkin.flatpakrepo" <<REPO
[Flatpak Repo]
Title=ERKIN
Url=$REPO_URL
Homepage=https://github.com/yulbax/ERKIN
Comment=VPN with per-app routing and ByeDPI
GPGKey=$GPG_KEY
REPO

cat > "$SITE/erkin.flatpakref" <<REF
[Flatpak Ref]
Name=$APP_ID
Branch=$BRANCH
Title=ERKIN
Url=$REPO_URL
SuggestRemoteName=erkin
RuntimeRepo=$RUNTIME_REPO
IsRuntime=false
GPGKey=$GPG_KEY
REF

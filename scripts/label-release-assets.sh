#!/usr/bin/env bash
# Labels release assets with the commit they were built from, as "<name> (<short sha>)"; the app compares it with its own build.
#   label-release-assets.sh <tag> <commit> <file>...    needs GH_TOKEN and GH_REPO
set -euo pipefail
tag="$1"
build="${2:0:7}"
shift 2
assets="$(gh api "repos/$GH_REPO/releases/tags/$tag" --jq '.assets[] | "\(.id) \(.name)"')"
for file in "$@"; do
  name="$(basename "$file")"
  id="$(awk -v name="$name" '$2 == name { print $1 }' <<<"$assets")"
  [ -n "$id" ] || { echo "No asset $name in $tag"; exit 1; }
  gh api -X PATCH "repos/$GH_REPO/releases/assets/$id" -f name="$name" -f label="$name ($build)" >/dev/null
  echo "$name ($build)"
done

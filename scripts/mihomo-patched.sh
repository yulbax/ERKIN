mihomo_patched() {
  local repo_root="$1" mihomo_dir="$2"
  local out="${repo_root}/build/mihomo-patched/$(basename "$mihomo_dir")"
  rm -rf "$out"
  mkdir -p "$(dirname "$out")"
  cp -r "$mihomo_dir" "$out"
  chmod -R u+w "$out"
  for patch_file in "${repo_root}"/core/mihomo/patches/*.patch; do
    [ -e "$patch_file" ] || continue
    if ! patch -d "$out" -p1 --batch --forward --no-backup-if-mismatch --quiet < "$patch_file" >&2; then
      echo "!!! $(basename "$patch_file") does not apply to $(basename "$mihomo_dir"); update or drop it" >&2
      return 1
    fi
  done
  echo "$out"
}

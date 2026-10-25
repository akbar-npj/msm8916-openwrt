#!/usr/bin/env bash
# sync-version.sh - single source of truth for the project release version.
#
# Usage:
#   ./sync-version.sh 25.12.6-r1        # set version + propagate to all diffconfigs
#   ./sync-version.sh                   # re-propagate current version (no change)
#
# The repo `version` file is the ONLY place you bump the number. This script
# rewrites the CONFIG_VERSION_* block in every diffconfigs/* so a live system
# can report it via `ubus call system board | jsonfilter -e @.release.version`
# (also present in /etc/os-release).
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
VER_FILE="$REPO_DIR/version"

if [ $# -ge 1 ]; then
  echo "$1" > "$VER_FILE"
fi

VER="$(cat "$VER_FILE")"
if [ -z "$VER" ]; then
  echo "version file is empty" >&2
  exit 1
fi

BASE="${VER%-r*}"
REL="${VER##*-}"

BLOCK=$(printf 'CONFIG_VERSIONOPT=y\nCONFIG_VERSION_FILENAMES=n\nCONFIG_VERSION_DIST="msm8916-openwrt"\nCONFIG_VERSION_NUMBER="%s"\nCONFIG_VERSION_CODE=""\nCONFIG_VERSION_REPO="https://akbar-npj.github.io/msm8916-openwrt/releases/%s"\nCONFIG_VERSION_HOME_URL="https://github.com/akbar-npj/msm8916-openwrt"\nCONFIG_VERSION_BUG_URL="https://github.com/akbar-npj/msm8916-openwrt/issues"' "$VER" "$BASE")

for f in "$REPO_DIR"/diffconfigs/*; do
  [ -f "$f" ] || continue
  grep -vE '^CONFIG_VERSION(_|OPT)' "$f" > "$f.tmp"
  mv "$f.tmp" "$f"
  printf '\n%s\n' "$BLOCK" >> "$f"
done

echo "version set to $VER (base=$BASE rel=$REL) across all diffconfigs"

#!/bin/bash
#
# apply_openwrt_patches.sh
#
# Apply all project OpenWrt patches.
#
# Usage:
#
#     ./apply_openwrt_patches.sh <openwrt-directory>
#

set -euo pipefail

###############################################################################
# Arguments
###############################################################################

OPENWRT_DIR="${1:-}"

[ -n "$OPENWRT_DIR" ] || {
    echo "Usage: $0 <openwrt-directory>" >&2
    exit 1
}

###############################################################################
# Validate OpenWrt tree
###############################################################################

[ -f "$OPENWRT_DIR/rules.mk" ] || {
    echo "Error: '$OPENWRT_DIR' is not an OpenWrt source tree." >&2
    exit 1
}

###############################################################################
# Directories
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PATCH_DIR="$REPO_DIR/patches/openwrt"

###############################################################################
# Nothing to do
###############################################################################

[ -d "$PATCH_DIR" ] || exit 0

###############################################################################
# Apply patches
###############################################################################

echo "Applying OpenWrt patches..."

for patch in "$PATCH_DIR"/*.patch; do

    [ -f "$patch" ] || continue

    printf "  %-45s" "$(basename "$patch")"

    #
    # Skip if already applied.
    #
    if git -C "$OPENWRT_DIR" apply --reverse --check "$patch" >/dev/null 2>&1; then
        echo "already applied"
        continue
    fi

    #
    # Apply patch.
    #
    if git -C "$OPENWRT_DIR" apply "$patch"; then
        echo "applied"
    else
        echo "FAILED"
        echo
        echo "Error: failed to apply:"
        echo "  $(basename "$patch")"
        exit 1
    fi

done

echo
echo "OpenWrt patches applied successfully."

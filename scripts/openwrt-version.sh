#!/bin/bash
#
# openwrt-version.sh
#
# OpenWrt Version Manager
#
# Responsibilities:
#   • Show current OpenWrt version
#   • Change OpenWrt version
#   • List available releases
#
# This script NEVER clones repositories, prepares OpenWrt,
# or invokes Docker.
#

set -euo pipefail

###############################################################################
# Directories
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ENV_FILE="$REPO_DIR/devenv/.env"

OPENWRT_REPO="https://git.openwrt.org/openwrt/openwrt.git"

###############################################################################
# Colours
###############################################################################

BLUE="\033[1;34m"
GREEN="\033[1;32m"
YELLOW="\033[1;33m"
RED="\033[1;31m"
NC="\033[0m"

###############################################################################
# Console helpers
###############################################################################

msg() {
    printf "${BLUE}==>${NC} %s\n" "$*"
}

ok() {
    printf "${GREEN}==>${NC} %s\n" "$*"
}

warn() {
    printf "${YELLOW}==>${NC} %s\n" "$*"
}

die() {
    printf "${RED}Error:${NC} %s\n" "$*" >&2
    exit 1
}

###############################################################################
# Validation
###############################################################################

check_requirements() {

    command -v git >/dev/null ||
        die "git is not installed."

    [ -f "$ENV_FILE" ] ||
        die "Missing: $ENV_FILE"
}

###############################################################################
# Version helpers
###############################################################################
read_version() {

    local version

    version="$(
        grep '^OPENWRT_VERSION=' "$ENV_FILE" 2>/dev/null |
        cut -d= -f2
    )"

    echo "${version:-main}"
}

write_version() {

    local version="$1"

    grep -q '^OPENWRT_VERSION=' "$ENV_FILE" ||
        die "OPENWRT_VERSION not found in $ENV_FILE"

    sed -i \
        "s/^OPENWRT_VERSION=.*/OPENWRT_VERSION=${version}/" \
        "$ENV_FILE"
}

###############################################################################
# Usage
###############################################################################

usage() {

cat <<EOF

OpenWrt Version Manager

Usage:

    ./scripts/openwrt-version.sh

        Show current version and available releases.

    ./scripts/openwrt-version.sh --current

        Print only the configured OpenWrt version.

    ./scripts/openwrt-version.sh <version>

        Update OPENWRT_VERSION in devenv/.env.

Examples

    ./scripts/openwrt-version.sh

    ./scripts/openwrt-version.sh --current

    ./scripts/openwrt-version.sh main

    ./scripts/openwrt-version.sh v25.12.5

EOF
}

###############################################################################
# Git helpers
###############################################################################

fetch_versions() {

    git ls-remote \
        --tags \
        "$OPENWRT_REPO"
}

###############################################################################
# Status
###############################################################################

show_status() {

    local current

    current="$(read_version)"

    echo
    echo "Current OpenWrt version"
    echo "-----------------------"
    echo "  $current"
    echo

    msg "Fetching available releases..."
    echo

    echo "Development"
    echo "-----------"
    echo "  main"
    echo

    echo "Latest stable releases"
    echo "----------------------"

    fetch_versions |
        awk '{print $2}' |
        sed 's#refs/tags/##' |
        grep '^v' |
        grep -v '{}' |
        sort -Vr |
        head -20 |
        sed 's/^/  /'
}

###############################################################################
# Update version
###############################################################################

update_version() {

    local version="$1"
    local current

    current="$(read_version)"

    if [ "$version" = "$current" ]; then
        ok "Already using $version."
        return
    fi

    write_version "$version"

    ok "OpenWrt version updated."

    echo
    echo "Old : $current"
    echo "New : $version"
}

###############################################################################
# Main
###############################################################################

check_requirements

case "${1:-}" in

    --current)

        read_version
        ;;

    "")

        show_status
        ;;

    main|v*)

        update_version "$1"
        ;;

    -h|--help)

        usage
        ;;

    *)

        die "Unknown argument '$1'."
        ;;

esac

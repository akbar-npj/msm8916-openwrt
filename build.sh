#!/bin/bash
#
# build.sh
#
# OpenWrt Build Manager
#
# Repository:
#   msm8916-openwrt
#
# This script orchestrates the complete build environment.
#

set -euo pipefail

###############################################################################
# Variables
###############################################################################

TMP_DIFFCONFIG=""

###############################################################################
# Directories
###############################################################################
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

REPO_DIR="$SCRIPT_DIR"

OPENWRT_DIR="$REPO_DIR/openwrt"

SCRIPTS_DIR="$REPO_DIR/scripts"

DIFFCONFIG_DIR="$REPO_DIR/diffconfigs"

OPENWRT_VERSION_SCRIPT="$SCRIPTS_DIR/openwrt-version.sh"
OPENWRT_PREPARE_SCRIPT="$SCRIPTS_DIR/openwrt-prepare.sh"

COMPOSE_FILE="$REPO_DIR/devenv/docker-compose.yml"

###############################################################################
# Container paths
###############################################################################

CONTAINER_REPO_DIR="/repo"
CONTAINER_OPENWRT_DIR="/repo/openwrt"

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

    command -v docker >/dev/null ||
        die "Docker is not installed."

    command -v git >/dev/null ||
        die "Git is not installed."

    docker info >/dev/null 2>&1 ||
        die "Docker daemon is not running."

    [ -f "$COMPOSE_FILE" ] ||
        die "Missing: $COMPOSE_FILE"

    [ -x "$OPENWRT_VERSION_SCRIPT" ] ||
        die "Missing executable: $OPENWRT_VERSION_SCRIPT"

    [ -x "$OPENWRT_PREPARE_SCRIPT" ] ||
        die "Missing executable: $OPENWRT_PREPARE_SCRIPT"
}

###############################################################################
# Docker helpers
###############################################################################

docker_compose() {

    docker compose \
        -f "$COMPOSE_FILE" \
        "$@"
}

docker_exec() {
    ensure_builder
    docker_compose exec builder "$@"
}

run_openwrt_make() {

    local target="$*"

    msg "Running: $target"

    if ! docker_exec bash -lc "
        cd $CONTAINER_OPENWRT_DIR
        $target
    "; then

        echo
        echo "======================================================"
        echo "                OpenWrt build failed"
        echo "======================================================"
        echo
        echo "Failed command:"
        echo
        echo "    $target"
        echo
        echo "Working directory:"
        echo
        echo "    $CONTAINER_OPENWRT_DIR"
        echo

        case "$target" in
            *defconfig*)
                echo "Possible causes:"
                echo
                echo "  • New kernel Kconfig options were introduced."
                echo "  • target/linux/msm89xx/config-6.12 needs updating."
                echo "  • A package Kconfig file now requires additional options."
                echo
                echo "If you were prompted with (NEW) configuration options:"
                echo
                echo "  1. Review and answer the new options."
                echo "  2. Save the updated kernel configuration."
                echo "  3. Copy the generated .config back to:"
                echo
                echo "       target/linux/msm89xx/config-6.12"
                echo
                ;;
        esac

        echo "The first compiler error shown above is usually the"
        echo "actual cause of the failure."
        echo
        echo "Tip:"
        echo "  Search upward for the FIRST occurrence of:"
        echo
        echo "      error:"
        echo
        echo "Subsequent errors are often consequences of the first."
        echo

        exit 1
    fi
}

builder_image_exists() {

    docker image inspect devenv-builder >/dev/null 2>&1
}

build_image() {

    msg "Building Docker image..."

    docker_compose build

    rm -f "$OPENWRT_DIR/.builder-state"

    ok "Docker image is ready."
}

ensure_builder() {
    if ! builder_image_exists; then
        build_image
    fi

    docker_compose up -d builder >/dev/null

    if ! docker_compose ps --status running --services | grep -qx builder; then
        die "Docker builder service failed to start. Check: docker compose logs builder"
    fi
}

###############################################################################
# Common helpers
###############################################################################

timed() {

    local start end

    start=$(date +%s)

    "$@"

    end=$(date +%s)

    ok "Finished in $((end-start)) seconds."
}
###############################################################################
# OpenWrt tree helpers
###############################################################################

clone_openwrt() {

    [ -d "$OPENWRT_DIR/.git" ] && return

    msg "Cloning OpenWrt..."

    git clone \
        https://git.openwrt.org/openwrt/openwrt.git \
        "$OPENWRT_DIR"
}

check_openwrt_clean() {

    [ -d "$OPENWRT_DIR/.git" ] || return

    if ! git -C "$OPENWRT_DIR" diff --quiet ||
       ! git -C "$OPENWRT_DIR" diff --cached --quiet; then

        die "OpenWrt tree contains local modifications.
Commit, stash, or discard them before switching versions."
    fi
}

checkout_openwrt() {

    local version="$1"

    local current

    current="$(
    git -C "$OPENWRT_DIR" describe \
        --tags \
        --exact-match \
        2>/dev/null ||
    echo main
)"

    if [ "$current" = "$version" ]; then
        return
    fi

    check_openwrt_clean

    msg "Checking out $version..."

    git -C "$OPENWRT_DIR" fetch --tags origin

    git -C "$OPENWRT_DIR" checkout "$version"
}
###############################################################################
# OpenWrt helpers
###############################################################################

ensure_openwrt() {

    local version

    version="$("$OPENWRT_VERSION_SCRIPT" --current)"

    clone_openwrt

    checkout_openwrt "$version"
}


prepared_version() {

    [ -f "$OPENWRT_DIR/.builder-state" ] || return

    awk -F= '
        /^OPENWRT_VERSION=/ {
            print $2
        }
    ' "$OPENWRT_DIR/.builder-state"
}

prepare_tree() {

    local version="$1"

    ensure_builder

    msg "Preparing OpenWrt tree..."

    docker_exec sh -c "
        cd $CONTAINER_REPO_DIR && \
        ./scripts/openwrt-prepare.sh \
            --version '$version' \
            openwrt
    "

    ok "OpenWrt tree prepared."
}

ensure_prepared() {

    local version
    local prepared

    version="$("$OPENWRT_VERSION_SCRIPT" --current)"

    ensure_openwrt

    prepared="$(prepared_version || true)"

    if [ "$prepared" = "$version" ]; then

        ok "OpenWrt tree already prepared. Syncing BSP..."

        docker_exec sh -c "
            rm -rf $CONTAINER_OPENWRT_DIR/target/linux/msm89xx && \
            cp -a $CONTAINER_REPO_DIR/msm89xx $CONTAINER_OPENWRT_DIR/target/linux/ && \
            rm -f $CONTAINER_OPENWRT_DIR/target/linux/msm89xx/patches/809-mac80211-enable-wcn36xx.patch && \
            rm -rf $CONTAINER_OPENWRT_DIR/package/msm8916 && \
            cp -a $CONTAINER_REPO_DIR/packages $CONTAINER_OPENWRT_DIR/package/msm8916 && \
            for pkg in $CONTAINER_REPO_DIR/packages/*; do \
                if [ -d \"\$pkg/patches\" ]; then \
                    pname=\$(basename \"\$pkg\"); \
                    if [ -d \"$CONTAINER_OPENWRT_DIR/package/system/\$pname\" ]; then \
                        mkdir -p \"$CONTAINER_OPENWRT_DIR/package/system/\$pname/patches\" && \
                        cp -a \"\$pkg/patches/.\" \"$CONTAINER_OPENWRT_DIR/package/system/\$pname/patches/\"; \
                    fi; \
                fi; \
            done && \
            if [ -f $CONTAINER_OPENWRT_DIR/feeds/packages/net/modemmanager/files/lib/netifd/proto/modemmanager.sh ]; then \
                sed -i 's/proto_notify_error \"\${interface}\" MM_INIT_EPS_BEARER_SET_FAILED/return 0/' $CONTAINER_OPENWRT_DIR/feeds/packages/net/modemmanager/files/lib/netifd/proto/modemmanager.sh 2>/dev/null || true; \
            fi
        "

        return
    fi

    prepare_tree "$version"
}

###############################################################################
# Feed helpers
###############################################################################

update_feeds() {

    msg "Updating OpenWrt feeds..."

    docker_exec sh -c "
        cd $CONTAINER_OPENWRT_DIR &&
        ./scripts/feeds update -a &&
        ./scripts/feeds install -a
    "

    ok "OpenWrt feeds updated and installed."
}

###############################################################################
# Board helpers
###############################################################################

list_boards() {

    local cfg

    for cfg in "$DIFFCONFIG_DIR"/*; do
        [ -f "$cfg" ] || continue
        echo "  ${cfg##*/}"
    done
}

check_board() {

    BOARD="${BOARD:-}"

    [ -n "$BOARD" ] ||
        die "No board specified."

    if [ ! -f "$DIFFCONFIG_DIR/$BOARD" ]; then

        echo
        echo "Available boards:"
        echo

        list_boards

        echo

        die "Unknown board '$BOARD'"
    fi
}

###############################################################################
# Build helpers
###############################################################################

prepare_config() {

    local board="$1"

    msg "Preparing configuration for $board..."

    docker_exec sh -c "
        cd $CONTAINER_OPENWRT_DIR &&
        cp $CONTAINER_REPO_DIR/diffconfigs/$board .config
    "

    run_openwrt_make "make defconfig V=sc"
}




save_diffconfig() {

    ensure_prepared

    [ -n "$TMP_DIFFCONFIG" ] || {
        TMP_DIFFCONFIG="$(mktemp)"
    }

    if ! docker_exec bash -lc "
        cd $CONTAINER_OPENWRT_DIR &&
        ./scripts/diffconfig.sh
    " > "$TMP_DIFFCONFIG"; then

        rm -f "$TMP_DIFFCONFIG"
        TMP_DIFFCONFIG=""

        die "Failed to generate diffconfig."
    fi

    if cmp -s "$TMP_DIFFCONFIG" "$DIFFCONFIG_DIR/$BOARD"; then

        ok "$BOARD is already up to date."

        rm -f "$TMP_DIFFCONFIG"
        TMP_DIFFCONFIG=""

        return
    fi

    echo
    warn "Configuration changes detected."
    echo

    read -rp "Overwrite diffconfigs/$BOARD? [y/N] " reply

    case "${reply,,}" in

        y|yes)

            mv "$TMP_DIFFCONFIG" \
               "$DIFFCONFIG_DIR/$BOARD"

            TMP_DIFFCONFIG=""

            ok "Updated diffconfigs/$BOARD."
            ;;

        *)

            warn "Configuration was not saved."
            ;;

    esac

    [ -n "$TMP_DIFFCONFIG" ] && rm -f "$TMP_DIFFCONFIG"

    TMP_DIFFCONFIG=""
}



build_target() {

    local board="$1"
    local clean="${2:-0}"

###############################################################################
# Always synchronize repository sources before building.
#
# This refreshes:
#
#   msm89xx/
#   packages/
#   package patches
#   openwrt-overlay/
#   package feeds
#
###############################################################################

    local version

    version="$("$OPENWRT_VERSION_SCRIPT" --current)"

    ensure_openwrt

    prepare_tree "$version"

    prepare_config "$board"

    if [ "$clean" -eq 1 ]; then

        run_openwrt_make "make clean V=sc"

    fi

    run_openwrt_make "make -j\$((\$(nproc)+1)) V=sc"
}

run_menuconfig() {

    ensure_prepared

    prepare_config

    docker_exec sh -c "
        cd $CONTAINER_OPENWRT_DIR && \
        make menuconfig
    "

    echo
    warn "Save the updated diffconfig with:"
    echo
    echo
    echo "To save this configuration permanently, run:"
    echo
    echo "    ./build.sh saveconfig $BOARD"
    echo
    echo
}


force_prepare() {

    rm -f "$OPENWRT_DIR/.builder-state"

    local version

    version="$("$OPENWRT_VERSION_SCRIPT" --current)"

    ensure_openwrt

    prepare_tree "$version"
}
###############################################################################
# Usage
###############################################################################

usage() {

cat <<EOF

OpenWrt Build Manager

Usage:

    ./build.sh <command> [arguments]

Commands

    help
        Show this help.

    list
        List supported boards.

    version
        Show or change the configured OpenWrt version.

    image
        Build or update the Docker image.

    prepare [--force]
        Clone and prepare the OpenWrt tree.

    shell
        Open a shell inside the builder container.

    build <board> [board ...]
        Build firmware for one or more boards.
        Use "all" to build firmware for all supported boards.

    saveconfig <board>
        Save the current OpenWrt .config back to the board diffconfig.

    rebuild <board> [board ...]
        Clean and rebuild firmware for one or more boards.
        Use "all" to rebuild firmware for all supported boards.

    menuconfig <board>
        Run menuconfig.

    clean
    dirclean
    distclean
        Run the corresponding OpenWrt make target.

EOF
}

###############################################################################
# Main
###############################################################################

COMMAND="${1:-help}"

check_requirements

case "$COMMAND" in

###############################################################################
# Help
###############################################################################

help|-h|--help)

    usage
    ;;

###############################################################################
# Boards
###############################################################################

list)

    echo
    echo "Supported boards:"
    echo

    list_boards
    ;;

###############################################################################
# OpenWrt version
###############################################################################

version)

    shift

    exec "$OPENWRT_VERSION_SCRIPT" "$@"
    ;;

###############################################################################
# Docker image
###############################################################################

image)

    timed build_image
    ;;

###############################################################################
# Prepare OpenWrt
###############################################################################

prepare)

    if [ "${2:-}" = "--force" ]; then

        timed force_prepare

    else

        timed ensure_prepared

    fi
    ;;
###############################################################################
# Shell
###############################################################################

shell)

    ensure_builder

    docker_exec bash
    ;;

###############################################################################
# Build
###############################################################################

build)

    shift

    [ "$#" -gt 0 ] ||
        die "No boards specified."

    # Expand "all" to every board in diffconfigs/
    if [ "$1" = "all" ]; then

        [ "$#" -eq 1 ] ||
            die "'all' cannot be combined with individual boards."

        set --

        for cfg in "$DIFFCONFIG_DIR"/*; do
            [ -f "$cfg" ] || continue
            set -- "$@" "${cfg##*/}"
        done

        [ "$#" -gt 0 ] ||
            die "No board configurations found in $DIFFCONFIG_DIR"

    fi

    # Validate ALL boards before starting any build.
    for BOARD in "$@"; do
        check_board
    done

    # All boards are valid, so start building.
    for BOARD in "$@"; do

        echo
        echo "======================================================"
        echo " Building: $BOARD"
        echo "======================================================"
        echo

        timed build_target "$BOARD"

    done

    ;;

###############################################################################
# Rebuild
###############################################################################

rebuild)

    shift

    [ "$#" -gt 0 ] ||
        die "No boards specified."

    # Expand "all" to every board in diffconfigs/
    if [ "$1" = "all" ]; then

        [ "$#" -eq 1 ] ||
            die "'all' cannot be combined with individual boards."

        set --

        for cfg in "$DIFFCONFIG_DIR"/*; do
            [ -f "$cfg" ] || continue
            set -- "$@" "${cfg##*/}"
        done

        [ "$#" -gt 0 ] ||
            die "No board configurations found in $DIFFCONFIG_DIR"

    fi

    # Validate ALL boards before starting any rebuild.
    for BOARD in "$@"; do
        check_board
    done

    # All boards are valid, so start rebuilding.
    for BOARD in "$@"; do

        echo
        echo "======================================================"
        echo " Rebuilding: $BOARD"
        echo "======================================================"
        echo

        timed build_target "$BOARD" 1

    done

    ;;

###############################################################################
# Menuconfig
###############################################################################

menuconfig)

    BOARD="${2:-}"

    check_board

    timed run_menuconfig
    ;;


###############################################################################
# Save diffconfig
###############################################################################

saveconfig)

    BOARD="${2:-}"

    check_board

    timed save_diffconfig
    ;;


###############################################################################
# Cleaning
###############################################################################

clean|dirclean|distclean)

    ensure_prepared

    timed run_openwrt_make "make $COMMAND V=sc"
    ;;

###############################################################################
# Unknown command
###############################################################################

*)

    die "Unknown command '$COMMAND'. Try './build.sh help'."
    ;;

esac

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

    HOST_UID="${HOST_UID:-$(id -u)}" \
    HOST_GID="${HOST_GID:-$(id -g)}" \
    docker compose \
        -f "$COMPOSE_FILE" \
        "$@"
}

docker_exec() {
    ensure_builder
    docker_compose exec builder sh -c 'umask 022 && exec "$@"' sh "$@"
}

run_openwrt_make() {

    local target="$*"

    msg "Running: $target"

    if [ "${DRY_RUN:-0}" = "1" ]; then
        warn "DRY_RUN=1 -- not executing. Would run in the builder container:"
        echo "    cd $CONTAINER_OPENWRT_DIR && $target"
        return 0
    fi

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

###############################################################################
# BSP drift detection
###############################################################################

# Trees that sync_bsp() rewrites from a tracked repository source into the
# prepared OpenWrt tree, as "tracked-source:live-destination".
#
# This matters because the kernel build reads the LIVE tree, not the tracked
# one: PATCH_DIR resolves to openwrt/target/linux/msm89xx/patches
# (include/kernel.mk:43), and that directory is part of the prepare stamp's md5
# (include/kernel-build.mk:12-13). A live tree that is missing patches therefore
# produces a kernel missing those patches -- silently. See Doc 160.
BSP_MIRRORS=(
    "msm89xx:target/linux/msm89xx"
    "packages:package/msm8916"
)

# Compare every mirror. Returns 0 when all match, 1 on drift.
# On drift, prints the differing paths.
bsp_drift() {

    local entry src rel out
    local rc=0

    for entry in "${BSP_MIRRORS[@]}"; do

        src="${entry%%:*}"
        rel="${entry##*:}"

        if [ ! -d "$OPENWRT_DIR/$rel" ]; then

            warn "Live tree is missing: openwrt/$rel"
            rc=1
            continue
        fi

        # Fails only when the two trees differ.
        if out="$(diff -rq "$REPO_DIR/$src" "$OPENWRT_DIR/$rel" 2>&1)"; then
            continue
        fi

        rc=1

        echo "  drift: $src/  ->  openwrt/$rel"

        # awk rather than head: head closes the pipe early, and `set -o pipefail`
        # would turn the resulting SIGPIPE into a script failure.
        printf '%s\n' "$out" | awk 'NR<=20 { print "    " $0 }'
    done

    return "$rc"
}

# The invariant every install path must leave behind: the live tree is exactly
# what the tracked sources describe. Checked on both paths -- sync_bsp() (fast)
# and scripts/openwrt-prepare.sh (fresh prepare).
assert_bsp_synced() {

    if ! bsp_drift; then
        die "BSP verification FAILED: the live tree differs from the tracked sources."
    fi
}

###############################################################################
# BSP sync helpers
###############################################################################

sync_bsp() {

    # Report drift BEFORE healing it, so a live tree that diverged is never
    # repaired silently. This is the state that produces a wrong kernel.
    if ! bsp_drift; then
        warn "The live OpenWrt tree differed from the tracked sources (listed above)."
        warn "This normally means a build ran outside ./build.sh. Syncing now."
    fi

    ok "Syncing BSP into prepared OpenWrt tree..."

    # Reinstall the msm89xx target.
    docker_exec sh -c "
        rm -rf $CONTAINER_OPENWRT_DIR/target/linux/msm89xx && \
        cp -a $CONTAINER_REPO_DIR/msm89xx $CONTAINER_OPENWRT_DIR/target/linux/
    "

    # Reinstall project packages.
    docker_exec sh -c "
        rm -rf $CONTAINER_OPENWRT_DIR/package/msm8916 && \
        cp -a $CONTAINER_REPO_DIR/packages $CONTAINER_OPENWRT_DIR/package/msm8916
    "

    # Install package patches into the upstream OpenWrt package tree.
    docker_exec sh -c "
        for pkg in $CONTAINER_REPO_DIR/packages/*; do \
            [ -d \"\$pkg/patches\" ] || continue; \
            pname=\$(basename \"\$pkg\"); \
            if [ -d \"$CONTAINER_OPENWRT_DIR/package/system/\$pname\" ]; then \
                mkdir -p \"$CONTAINER_OPENWRT_DIR/package/system/\$pname/patches\" && \
                cp -a \"\$pkg/patches/.\" \
                      \"$CONTAINER_OPENWRT_DIR/package/system/\$pname/patches/\"; \
            fi; \
        done
    "

    # Sync openwrt-overlay into the OpenWrt tree if present.
    docker_exec sh -c "
        if [ -d \"$CONTAINER_REPO_DIR/openwrt-overlay\" ]; then
            cp -a \"$CONTAINER_REPO_DIR/openwrt-overlay/.\" \"$CONTAINER_OPENWRT_DIR/\"
        fi
    "

    # Apply OpenWrt source-tree patches if present.
    docker_exec sh -c "
        if [ -d \"$CONTAINER_REPO_DIR/openwrt-patches\" ]; then
            for p in \"$CONTAINER_REPO_DIR/openwrt-patches\"/*.patch; do
                [ -f \"\$p\" ] || continue
                if patch -p1 -d \"$CONTAINER_OPENWRT_DIR\" --dry-run < \"\$p\" >/dev/null 2>&1; then
                    patch -p1 -d \"$CONTAINER_OPENWRT_DIR\" < \"\$p\"
                fi
            done
        fi
    "

    # Apply ModemManager compatibility fix if the file is present.
    docker_exec sh -c "
        mm_proto=$CONTAINER_OPENWRT_DIR/feeds/packages/net/modemmanager/files/lib/netifd/proto/modemmanager.sh
        if [ -f \"\$mm_proto\" ]; then
            sed -i \
                's/proto_notify_error \"\${interface}\" MM_INIT_EPS_BEARER_SET_FAILED/return 0/' \
                \"\$mm_proto\" 2>/dev/null || true
        fi
    "

    # Assert the sync actually landed. A partial or failed copy would otherwise
    # build a kernel from the wrong patch set without any error.
    assert_bsp_synced

    ok "BSP sync verified: live tree matches tracked sources."
}

ensure_prepared() {

    local version
    local prepared

    version="$("$OPENWRT_VERSION_SCRIPT" --current)"

    ensure_openwrt

    prepared="$(prepared_version || true)"

    if [ "$prepared" = "$version" ]; then

        ok "OpenWrt tree already prepared for $version."

        sync_bsp

        return
    fi

    prepare_tree "$version"

    # openwrt-prepare.sh installs the BSP from the same tracked sources; verify
    # it, so both install paths are held to the same invariant.
    assert_bsp_synced

    ok "BSP install verified: live tree matches tracked sources."
}

###############################################################################
# Guard
###############################################################################

# Ask make for its own STAMP_PREPARED: the stamp file that will exist once
# build_dir is consistent with the current patch set. If it does not exist yet,
# the next kernel build will wipe build_dir and re-prepare.
#
# Deliberately NOT a re-implementation of OpenWrt's find_md5. That hash covers
# absolute paths, which differ between the host and the container, so a host
# copy would compute a different value. make already knows the answer.
#
# TOPDIR is set by the top-level make and exported to sub-makes, so invoking the
# target Makefile directly requires passing it. TARGET_BUILD must be exactly 1:
# include/target.mk gates kernel-build.mk on `ifeq ($(TARGET_BUILD),1)`.
#
# Prints the stamp path (container-absolute), or nothing if undeterminable.
deep_prepare_stamp() {

    docker_exec bash -lc "
        cd $CONTAINER_OPENWRT_DIR/target/linux/msm89xx || exit 1
        make TOPDIR=$CONTAINER_OPENWRT_DIR TARGET_BUILD=1 -r -s --no-print-directory --eval='print-stamp: ; @printf \"%s\\n\" \"\$(STAMP_PREPARED)\"' print-stamp
    " 2>/dev/null || true
}

guard_check() {

    local deep=0
    local stamp

    if [ "${1:-}" = "--deep" ]; then
        deep=1
    fi

    msg "Checking the live OpenWrt tree against the tracked sources..."

    if ! bsp_drift; then
        warn "DRIFT: the live OpenWrt tree does not match the tracked sources."
        warn "Any ./build.sh build command re-syncs it; run './build.sh guard' again after."
        return 1
    fi

    ok "In sync: msm89xx/ and packages/ match the live OpenWrt tree."

    [ "$deep" -eq 1 ] || return 0

    msg "Asking make whether a kernel re-prepare is pending..."

    stamp="$(deep_prepare_stamp)"

    if [ -z "$stamp" ]; then
        warn "Could not determine STAMP_PREPARED. Verdict: UNKNOWN."
        return 0
    fi

    echo "  expected stamp: ${stamp#"$CONTAINER_REPO_DIR"/}"

    if [ -e "$REPO_DIR/${stamp#"$CONTAINER_REPO_DIR"/}" ]; then

        ok "Kernel build_dir is consistent with the current patch set."
        ok "No re-prepare pending -- the next kernel build is incremental."
    else

        warn "A re-prepare is PENDING."
        warn "The next kernel build will wipe build_dir, re-extract linux-6.12.94 and"
        warn "re-apply every patch. Expect a full kernel rebuild, not an incremental one."
    fi

    return 0
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

    # Guarded so DRY_RUN leaves the tree untouched: without it, a dry run would
    # still replace .config with the bare diffconfig and, because the following
    # `make defconfig` is skipped, leave the tree with an unexpanded config.
    if [ "${DRY_RUN:-0}" = "1" ]; then
        warn "DRY_RUN=1 -- not copying diffconfigs/$board to openwrt/.config"
    else
        docker_exec sh -c "
            cd $CONTAINER_OPENWRT_DIR &&
            cp $CONTAINER_REPO_DIR/diffconfigs/$board .config
        "
    fi

    run_openwrt_make "make defconfig V=sc"
}

# Ensure a usable .config before a selective build.
#
# With a board, rewrite .config from that board's diffconfig -- the same thing
# `build` does. Without one, reuse the .config already in the tree, which is what
# makes single-target iteration fast (no diffconfig copy, no `make defconfig`).
ensure_config() {

    local board="${1:-}"

    if [ -n "$board" ]; then
        prepare_config "$board"
        return
    fi

    if [ -f "$OPENWRT_DIR/.config" ]; then
        ok "Using the existing .config (no board given)."
        return
    fi

    die "No board given and openwrt/.config does not exist.
Usage: ./build.sh <command> [board]   -- run './build.sh list' for boards."
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
# ensure_prepared handles the fast path: if the tree is already prepared for
# the current version, it runs a quick BSP sync (msm89xx/, packages/,
# package patches, compatibility fixes) without a full re-prepare.
#
###############################################################################

    ensure_prepared

    prepare_config "$board"

    if [ "$clean" -eq 1 ]; then

        run_openwrt_make "make clean V=sc"

    fi

    run_openwrt_make "make -j\$((\$(nproc)+1)) V=sc"
}

###############################################################################
# Selective builds (kernel / single package / single kernel module)
###############################################################################

# Resolve a package name -- or an explicit package/... or feeds/... path -- to a
# make goal prefix, e.g. "package/msm8916/qrtr".
resolve_package() {

    local name="$1"
    local hit

    case "$name" in

        package/*|feeds/*)
            [ -d "$OPENWRT_DIR/$name" ] || return 1
            echo "$name"
            return 0
            ;;

    esac

    # Project packages are installed under package/msm8916/<name>.
    for hit in "package/$name" "package/msm8916/$name"; do
        if [ -d "$OPENWRT_DIR/$hit" ]; then
            echo "$hit"
            return 0
        fi
    done

    # Feed-installed symlinks, then the feed trees themselves.
    # `|| true` because an unmatched glob makes ls exit non-zero, and
    # `set -o pipefail` would otherwise abort the script. sed -n '1p' rather
    # than head -1 for the same reason (head closes the pipe early).
    hit="$( cd "$OPENWRT_DIR" && ls -d package/feeds/*/"$name" 2>/dev/null | sed -n '1p' || true )"
    if [ -n "$hit" ]; then
        echo "$hit"
        return 0
    fi

    hit="$( cd "$OPENWRT_DIR" && find feeds -maxdepth 4 -type d -name "$name" 2>/dev/null | sed -n '1p' || true )"
    if [ -n "$hit" ]; then
        echo "$hit"
        return 0
    fi

    # Base-tree packages nested below package/<name>, e.g.
    # package/network/services/dnsmasq or package/kernel/mac80211.
    hit="$( cd "$OPENWRT_DIR" && find package -maxdepth 4 -type d -name "$name" 2>/dev/null | sed -n '1p' || true )"
    if [ -n "$hit" ]; then
        echo "$hit"
        return 0
    fi

    return 1
}

# Read file paths on stdin, print "  <md5>  <path>" for each, keeping only the
# first path per distinct md5. A single build leaves identical copies in several
# places (build_dir, .pkgdir, ipkg-*, root-*); one line each is enough.
print_hashed_paths() {

    while IFS= read -r f; do

        [ -n "$f" ] || continue

        printf '%s  %s\n' \
            "$(md5sum "$f" | awk '{ print $1 }')" \
            "${f#"$OPENWRT_DIR"/}"

    done | awk '!seen[$1]++ { print "  " $0 }'
}

# Report what a selective build just produced, with md5 so the result can be
# compared against the device immediately.
report_new_artifacts() {

    local since="$1"
    local what="$2"
    local pattern="$3"
    local found

    found="$(find "$OPENWRT_DIR/build_dir" "$OPENWRT_DIR/bin" \
                  -name "$pattern" -newermt "@$since" 2>/dev/null || true)"

    [ -n "$found" ] || return 0

    echo
    msg "Built $what:"

    printf '%s\n' "$found" | print_hashed_paths
}

# Rebuild the kernel and its in-tree modules (target/linux/compile).
# This is the workhorse for driver iteration: qcom_bam_dmux is in-tree, so
# there is no narrower goal that rebuilds just it.
build_kernel() {

    local board="${1:-}"
    local start

    ensure_prepared
    ensure_config "$board"

    start="$(date +%s)"

    run_openwrt_make "make target/linux/compile V=s"

    report_new_artifacts "$start" "kernel modules" "*.ko"
}

# Build an already-resolved package path. Assumes ensure_prepared has run.
build_package_path() {

    local path="$1"
    local board="$2"
    local start

    ensure_config "$board"

    start="$(date +%s)"

    run_openwrt_make "make $path/compile V=s"

    report_new_artifacts "$start" "packages" "*.ipk"
}

# Rebuild one package: ./build.sh package <name|path> [board]
build_package() {

    local name="${1:-}"
    local board="${2:-}"
    local path
    local cands

    [ -n "$name" ] ||
        die "No package given. Usage: ./build.sh package <name|path> [board]"

    ensure_prepared

    if ! path="$(resolve_package "$name")"; then

        warn "Cannot resolve package '$name'."

        cands="$( cd "$OPENWRT_DIR" &&
                  find package -maxdepth 3 -type d -name "*${name}*" 2>/dev/null |
                      sed -n '1,20p' || true )"

        if [ -n "$cands" ]; then
            echo
            echo "Candidates under openwrt/package:"
            printf '%s\n' "$cands" | sed 's/^/  /'
        fi

        die "Unknown package '$name'."
    fi

    ok "Resolved '$name' -> openwrt/$path"

    build_package_path "$path" "$board"
}

# Rebuild one kernel module: ./build.sh kmod <name> [board]
#
# Two cases, and the difference matters:
#   * an out-of-tree kmod package (package/kernel/<name> or a feed) -> build it
#   * an in-tree module, generated from the kernel config (there is no package
#     directory for it) -> only `target/linux/compile` can rebuild it, which
#     rebuilds the kernel and every in-tree module.
build_kmod() {

    local name="${1:-}"
    local board="${2:-}"
    local path
    local norm hit

    [ -n "$name" ] ||
        die "No module given. Usage: ./build.sh kmod <name> [board]"

    ensure_prepared

    if path="$(resolve_package "kmod-$name" 2>/dev/null)" ||
       path="$(resolve_package "$name" 2>/dev/null)"; then

        ok "Resolved kmod '$name' -> openwrt/$path"

        build_package_path "$path" "$board"
        return
    fi

    warn "'$name' has no package directory -- treating it as an in-tree kernel module."
    warn "The narrowest goal OpenWrt offers for one in-tree module is the kernel"
    warn "target, so this rebuilds the kernel and all in-tree modules."

    build_kernel "$board"

    # The file name rarely matches the module name (bam-dmux -> qcom_bam_dmux.ko).
    norm="${name//-/_}"

    hit="$(find "$OPENWRT_DIR/build_dir" -name "*${norm}*.ko" 2>/dev/null | sed -n '1,10p' || true)"

    if [ -n "$hit" ]; then
        echo
        msg "Module(s) matching '$name':"
        printf '%s\n' "$hit" | print_hashed_paths
    fi
}

run_menuconfig() {

    ensure_prepared

    prepare_config "$BOARD"

    docker_exec sh -c "
        cd $CONTAINER_OPENWRT_DIR && \
        make menuconfig
    "

    echo
    warn "To save this configuration permanently, run:"
    echo
    echo "    ./build.sh saveconfig $BOARD"
    echo
}


force_prepare() {

    rm -f "$OPENWRT_DIR/.builder-state"

    local version

    version="$("$OPENWRT_VERSION_SCRIPT" --current)"

    ensure_openwrt

    prepare_tree "$version"

    assert_bsp_synced

    ok "BSP install verified: live tree matches tracked sources."
}

###############################################################################
# Build helpers (shared by build / rebuild)
###############################################################################

run_builds() {

    # $1 = clean flag (0 = incremental, 1 = clean rebuild)
    # $2... = board names (or "all")
    local clean="$1"
    local verb
    shift

    verb="$([ "$clean" -eq 1 ] && echo "Rebuilding" || echo "Building")"

    [ "$#" -gt 0 ] ||
        die "No boards specified."

    # Expand "all" to every board in diffconfigs/.
    if [ "$1" = "all" ]; then

        [ "$#" -eq 1 ] ||
            die "'all' cannot be combined with individual boards."

        local boards=()

        for cfg in "$DIFFCONFIG_DIR"/*; do
            [ -f "$cfg" ] || continue
            boards+=("${cfg##*/}")
        done

        [ "${#boards[@]}" -gt 0 ] ||
            die "No board configurations found in $DIFFCONFIG_DIR"

        set -- "${boards[@]}"
    fi

    # Validate ALL boards before starting any build.
    # BOARD is a global used by check_board and build_target.
    for BOARD in "$@"; do
        check_board
    done

    # All boards are valid — start building.
    for BOARD in "$@"; do

        echo
        echo "======================================================"
        echo " $verb: $BOARD"
        echo "======================================================"
        echo

        timed build_target "$BOARD" "$clean"

    done
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

    guard [--deep]
        Verify the live OpenWrt tree matches the tracked sources
        (msm89xx/, packages/). The kernel build reads the live tree,
        so a mismatch means a build from the wrong patch set.
        --deep also asks make whether the next kernel build will
        re-prepare (wiping build_dir) or be incremental.

    kernel [board]
        Rebuild only the kernel and its in-tree modules.
        Without a board, the existing .config is reused.

    package <name|path> [board]
        Rebuild only one package, e.g. "qrtr" or "package/msm8916/qrtr".

    kmod <name> [board]
        Rebuild only one kernel module. An out-of-tree kmod package is
        built directly; an in-tree module (e.g. bam-dmux) has no
        narrower goal than the kernel target.

    clean
    dirclean
    distclean
        Run the corresponding OpenWrt make target.

Environment

    DRY_RUN=1
        Print the OpenWrt make command instead of executing it.
        Works with every build command.

EOF
}

###############################################################################
# Main
###############################################################################

COMMAND="${1:-help}"

# `guard` is a host-side tree check, and is most useful precisely when the build
# environment is not running, so it does not require Docker. `--deep` asks make
# inside the container, so that form does.
if [ "$COMMAND" != "guard" ] || [ "${2:-}" = "--deep" ]; then
    check_requirements
fi

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
# Guard
###############################################################################

guard)

    shift
    guard_check "$@"
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
    run_builds 0 "$@"
    ;;

###############################################################################
# Rebuild
###############################################################################

rebuild)

    shift
    run_builds 1 "$@"
    ;;

###############################################################################
# Selective builds
###############################################################################

kernel)

    timed build_kernel "${2:-}"
    ;;

package)

    timed build_package "${2:-}" "${3:-}"
    ;;

kmod)

    timed build_kmod "${2:-}" "${3:-}"
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

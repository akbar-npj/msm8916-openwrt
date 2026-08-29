#!/bin/bash
#
# openwrt-prepare.sh
#
# Prepare an existing OpenWrt source tree.
#
# Responsibilities:
#   • Install msm89xx target
#   • Install project packages
#   • Apply repository overlay
#   • Apply project patches
#   • Update/install package feeds
#   • Apply compatibility fixes
#   • Record preparation state
#
# This script NEVER:
#   • Clones OpenWrt
#   • Checks out branches/tags
#   • Invokes Docker
#   • Reads devenv/.env
#

set -euo pipefail

###############################################################################
# Directories
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TARGET_DIR="$REPO_DIR/msm89xx"
PACKAGES_DIR="$REPO_DIR/packages"
OVERLAY_DIR="$REPO_DIR/openwrt-overlay"

###############################################################################
# Special OpenWrt-tree patch
#
# 809 modifies:
#
#   package/kernel/mac80211/ath.mk
#
# It is stored together with the BSP patches for repository organization,
# but it must NOT be left in:
#
#   target/linux/msm89xx/patches/
#
# because that directory is used for patches against the Linux kernel source.
###############################################################################

OPENWRT_MAC80211_PATCH="$TARGET_DIR/patches/809-mac80211-enable-wcn36xx.patch"

###############################################################################
# Arguments
###############################################################################

OPENWRT_VERSION="unknown"
REFRESH_FEEDS=0

while [ $# -gt 0 ]; do
    case "$1" in

    --version)

        shift

        [ $# -gt 0 ] || die "--version requires an argument."

        OPENWRT_VERSION="$1"
        ;;

    --refresh-feeds)

        REFRESH_FEEDS=1
        ;;

    -h|--help)

        usage
        exit 0
        ;;

    *)

        OPENWRT_DIR="$(realpath "$1")"
        ;;

    esac

    shift
done

: "${OPENWRT_DIR:=$REPO_DIR/openwrt}"

STATE_FILE="$OPENWRT_DIR/.builder-state"

###############################################################################
# Console helpers
###############################################################################

info() {

    echo "==> $*"
}

die() {

    echo "Error: $*" >&2
    exit 1
}

###############################################################################
# Usage
###############################################################################

usage() {

cat <<EOF

OpenWrt Preparation Tool

Usage:

    ./scripts/openwrt-prepare.sh [options] [openwrt-directory]

Options

    --version <version>

        Record the prepared OpenWrt version in .builder-state.

    --refresh-feeds

        Force update/install of all package feeds.

Examples

    ./scripts/openwrt-prepare.sh

    ./scripts/openwrt-prepare.sh openwrt

    ./scripts/openwrt-prepare.sh --version v25.12.5 openwrt

    ./scripts/openwrt-prepare.sh --refresh-feeds

If no directory is specified:

    $REPO_DIR/openwrt

is used.

EOF
}

###############################################################################
# Validation
###############################################################################

check_requirements() {

    [ -d "$OPENWRT_DIR" ] ||
        die "OpenWrt tree not found: $OPENWRT_DIR"

    [ -f "$OPENWRT_DIR/scripts/feeds" ] ||
        die "'$OPENWRT_DIR' is not an OpenWrt source tree."

    [ -d "$TARGET_DIR" ] ||
        die "Missing: msm89xx/"

    [ -d "$PACKAGES_DIR" ] ||
        die "Missing: packages/"

    if [ ! -f "$OPENWRT_MAC80211_PATCH" ]; then

        die "Missing OpenWrt mac80211 patch:
$OPENWRT_MAC80211_PATCH"

    fi
}

###############################################################################
# Installation helpers
###############################################################################

install_target() {

    info "Installing msm89xx target..."

    mkdir -p "$OPENWRT_DIR/target/linux"

    rm -rf "$OPENWRT_DIR/target/linux/msm89xx"

    cp -a \
        "$TARGET_DIR" \
        "$OPENWRT_DIR/target/linux/"

    ###########################################################################
    # 809 is NOT a Linux kernel patch.
    #
    # It modifies OpenWrt's package/kernel/mac80211/ath.mk, so it must not
    # remain in target/linux/msm89xx/patches/.
    ###########################################################################

    rm -f \
        "$OPENWRT_DIR/target/linux/msm89xx/patches/809-mac80211-enable-wcn36xx.patch"
}

install_packages() {

    info "Installing project packages..."

    mkdir -p "$OPENWRT_DIR/package"

    rm -rf "$OPENWRT_DIR/package/msm8916"

    cp -a \
        "$PACKAGES_DIR" \
        "$OPENWRT_DIR/package/msm8916"
}

install_overlay() {

    [ -d "$OVERLAY_DIR" ] || return

    info "Applying repository overlay..."

    cp -a \
        "$OVERLAY_DIR"/. \
        "$OPENWRT_DIR"/
}

###############################################################################
# Preparation helpers
###############################################################################

install_package_patches() {

    #
    # Install patches for OpenWrt packages.
    #
    # Repository layout:
    #
    #   packages/<pkg>/patches/*.patch
    #
    # becomes:
    #
    #   openwrt/package/system/<pkg>/patches/
    #

    local pkg

    for pkg in "$PACKAGES_DIR"/*; do

        [ -d "$pkg/patches" ] || continue

        pkg="$(basename "$pkg")"

        [ -d "$OPENWRT_DIR/package/system/$pkg" ] || {
            die "OpenWrt package not found: package/system/$pkg"
        }

        info "Installing $pkg patches..."

        rm -rf "$OPENWRT_DIR/package/system/$pkg/patches"

        mkdir -p "$OPENWRT_DIR/package/system/$pkg/patches"

        cp -a \
            "$PACKAGES_DIR/$pkg/patches/." \
            "$OPENWRT_DIR/package/system/$pkg/patches/"

    done
}

###############################################################################
# Apply patches to OpenWrt source itself
###############################################################################

apply_openwrt_patches() {

    local patch_file

    patch_file="$OPENWRT_MAC80211_PATCH"

    [ -f "$patch_file" ] || {
        die "Missing OpenWrt patch: $patch_file"
    }

    info "Applying OpenWrt patch: $(basename "$patch_file")..."

    (
        cd "$OPENWRT_DIR"

        #######################################################################
        # First check whether the patch can be applied normally.
        #######################################################################

        if patch -p1 --dry-run < "$patch_file" >/dev/null 2>&1; then

            patch -p1 < "$patch_file"

            return

        fi

        #######################################################################
        # If normal application fails, check whether it is already applied.
        #######################################################################

        if patch -p1 -R --dry-run < "$patch_file" >/dev/null 2>&1; then

            info "OpenWrt patch already applied: $(basename "$patch_file")"

            return

        fi

        #######################################################################
        # Neither forward nor reverse application works.
        #######################################################################

        die "Cannot apply OpenWrt patch:
$patch_file

The patch is neither applicable nor already applied."

    )
}

###############################################################################
# Package feeds
###############################################################################

update_feeds() {

    (
        cd "$OPENWRT_DIR"

        #######################################################################
        # User requested a full refresh
        #######################################################################

        if [ "$REFRESH_FEEDS" -eq 1 ]; then

            info "Refreshing package feeds..."

            rm -rf tmp

            ./scripts/feeds update -a
            ./scripts/feeds install -a

            return

        fi

        #######################################################################
        # Normal mode
        #######################################################################

        needs_install=0

        for feed in packages luci routing telephony video; do

            if [ ! -d "feeds/$feed" ]; then

                info "Updating feed: $feed"

                ./scripts/feeds update "$feed"

                needs_install=1

            else

                info "Feed '$feed' already present."

            fi

        done

        if [ "$needs_install" -eq 1 ]; then

            info "Installing package feeds..."

            ./scripts/feeds install -a

        else

            info "Package feeds already installed."

        fi
    )
}

###############################################################################
# Compatibility fixes
###############################################################################

apply_compatibility_fixes() {

    info "Applying compatibility fixes..."

    local luci_file

    luci_file="$OPENWRT_DIR/feeds/luci/modules/luci-base/root/usr/share/rpcd/ucode/luci"

    if [ -f "$luci_file" ] &&
       grep -q 'result\.odhcpd = false;' "$luci_file"; then

        sed -i \
            's/result\.odhcpd = false;/result.odhcpd = {};/' \
            "$luci_file"

    fi
}

###############################################################################
# Builder state
###############################################################################

write_builder_state() {

    info "Recording preparation state..."

    cat > "$STATE_FILE" <<EOF
OPENWRT_VERSION=$OPENWRT_VERSION
PREPARED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF
}

###############################################################################
# Main
###############################################################################

main() {

    check_requirements

    install_target

    install_packages

    install_package_patches

    install_overlay

    apply_openwrt_patches

    update_feeds

    apply_compatibility_fixes

    write_builder_state

    echo
    echo "=========================================="
    echo " OpenWrt source tree is ready."
    echo "=========================================="
}

###############################################################################
# Entry
###############################################################################

main

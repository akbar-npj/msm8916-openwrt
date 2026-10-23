#!/usr/bin/env bash
#
# build-recovery.sh - build the per-board MSM8916 recovery (rescue) boot image.
#
#   ./build-recovery.sh <board>      hmuf02v5 | mf800b | mifib-v12 | ufi003_mb_v02 | ufi001b
#   ./build-recovery.sh all
#   DRY_RUN=1 ./build-recovery.sh <board>   (print the container script, do not run)
#
# Output: msm89xx/recovery/<board>.img
#
# Image recipe (matches the hardware-verified recovery image chain):
#
#   kernel : Linux built from the patched OpenWrt source with
#            msm89xx/config-6.12-recovery (devtmpfs, configfs, libcomposite,
#            usb_f_ncm, usb_f_acm built in - the initramfs has no modules)
#            with the frozen initramfs msm89xx/recovery/irfs embedded via
#            CONFIG_INITRAMFS_SOURCE
#   pack   : gzip -9 Image | append board DTB | mkbootimg --ramdisk /dev/null
#   cmdline: empty (the /init does everything)
#
# Boards with GPT_RECOVERY=1 in msm89xx/image/msm8916.mk (mf800b, mifib-v12)
# flash this image to the dedicated 16 MiB 'recovery' GPT partition; other
# boards boot it one-shot over fastboot (fastboot boot <img>).
#
# Auth model: root shell on the USB ACM console and passwordless
# ssh root@192.168.10.1 (blank root password patch). The dropbear host key is
# generated at boot - nothing secret is baked into the image.
#
# Rebuilding the frozen binaries (only needed after an upstream bump):
#   irfs/bin/busybox            - busybox source tree configured with
#                                 msm89xx/recovery/busybox.config, CONFIG_STATIC=y
#   irfs/usr/sbin/dropbear      - bit-identical to the OpenWrt dropbear package
#                                 build (build_dir/.../root-msm89xx/usr/sbin/dropbear)
#   irfs/lib/ld-musl*, libgcc   - bit-identical to the toolchain rootfs copies
#                                 (build_dir/.../root-msm89xx/lib/)
#   irfs/sbin/reboot-mode-raw   - sources in msm89xx/package/ (reboot-edl)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
REPO_DIR="$SCRIPT_DIR"
RECOVERY_DIR="$REPO_DIR/msm89xx/recovery"
COMPOSE_FILE="$REPO_DIR/devenv/docker-compose.yml"
CONTAINER_REPO_DIR="/repo"
CONTAINER_RECOVERY_DIR="$CONTAINER_REPO_DIR/msm89xx/recovery"

BLUE="\033[1;34m"
GREEN="\033[1;32m"
RED="\033[1;31m"
NC="\033[0m"

msg() { printf "${BLUE}==>${NC} %s\n" "$*"; }
ok()  { printf "${GREEN}==>${NC} %s\n" "$*"; }
die() { printf "${RED}Error:${NC} %s\n" "$*" >&2; exit 1; }

docker_compose() {
    HOST_UID="${HOST_UID:-$(id -u)}" \
    HOST_GID="${HOST_GID:-$(id -g)}" \
    docker compose -f "$COMPOSE_FILE" "$@"
}

ensure_builder() {
    command -v docker >/dev/null || die "Docker is not installed."
    docker info >/dev/null 2>&1 || die "Docker daemon is not running."
    if ! docker image inspect devenv-builder >/dev/null 2>&1; then
        msg "Building the devenv-builder image (first run only)"
        docker_compose build
    fi
    docker_compose up -d builder >/dev/null
    docker_compose ps --status running --services | grep -qx builder \
        || die "Docker builder service failed to start. Check: docker compose logs builder"
}

docker_exec() {
    ensure_builder
    docker_compose exec builder sh -c 'umask 022 && exec "$@"' sh "$@"
}

# Board list - derived from the device definitions, stays in sync automatically.
list_boards() {
    grep -o 'DEVICE_DTS := msm8916-generic-[a-z0-9_-]*' \
        "$REPO_DIR/msm89xx/image/msm8916.mk" |
        sed 's/.*msm8916-generic-//'
}

check_board() {
    list_boards | grep -qx "$BOARD" \
        || die "Unknown board '$BOARD'. Known boards: $(list_boards | tr '\n' ' ')"
}

# Install this board's WiFi blobs into the initramfs tree.
#
# wcnss.mdt / the calibrated wlan NV / hostapd are per-board and are NOT
# interchangeable, so each board keeps its own blobs/<board>/ directory and
# only that directory is copied in.  Anything a previous board left behind is
# removed first - a mixed tree would silently ship the wrong firmware.
install_blobs() {
    local b="$1" src="$RECOVERY_DIR/blobs/$b"
    [ -d "$src" ] || die "No WiFi blobs for '$b'. Run:
       ./msm89xx/recovery/fetch-device-blobs.sh $b root@<device>"
    msg "Installing '$b' blobs (WCNSS firmware, hostapd, libs) into the initramfs"
    rm -rf "$RECOVERY_DIR/irfs/lib/firmware"
    find "$RECOVERY_DIR/irfs/lib" -maxdepth 1 -name 'lib*.so*' \
        ! -name 'libc.so*' ! -name 'libgcc_s.so*' -delete
    cp -a "$src/." "$RECOVERY_DIR/irfs/"
}

# The container-side build script (single-quoted heredoc: taken literally).
# Reuses the patched kernel source and the OpenWrt toolchain from the prepared
# tree, and swaps the recovery .config in for the build (restored afterwards).
container_script() {
    cat <<'EOF'
set -e

BOARD="${BOARD:?BOARD not set}"
RECOVERY_DIR="${RECOVERY_DIR:?RECOVERY_DIR not set}"
IRFS="$RECOVERY_DIR/irfs"
OUT="$RECOVERY_DIR/$BOARD.img"

# --- locate the patched kernel source (needs ./build.sh prepare + one build) ---
KSRC=$(ls -d /repo/openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.* 2>/dev/null | head -1)
[ -n "$KSRC" ] && [ -d "$KSRC" ] || {
    echo "ERROR: patched kernel source not found under openwrt/build_dir." >&2
    echo "       Run './build.sh prepare' and './build.sh kernel' first." >&2
    exit 1
}

TOOLCHAIN=$(ls -d /repo/openwrt/staging_dir/toolchain-aarch64_generic_gcc-*_musl | head -1)
export PATH="/repo/openwrt/staging_dir/host/bin:$TOOLCHAIN/bin:$PATH"
export STAGING_DIR=/repo/openwrt/staging_dir/target-aarch64_generic_musl


# --- build in-tree: swap .config, build, restore ------------------------------
# (O= builds are refused: the OpenWrt-prepared source tree carries in-tree
# artifacts. Swapping the config in-place reuses the existing object cache.)
cp "$KSRC/.config" "$KSRC/.config.openwrt.saved"
# NOTE: mv preserves the saved file's (older) mtime.  Without the touch below,
# .config ends up OLDER than include/generated/autoconf.h, so kbuild decides
# nothing changed and keeps the RECOVERY config compiled in - every later
# ./build.sh then ships a production kernel with the recovery initramfs
# embedded and CONFIG_LOCALVERSION="-msm8916".
restore_config() {
    mv "$KSRC/.config.openwrt.saved" "$KSRC/.config"
    touch "$KSRC/.config"
}
trap restore_config EXIT

cp /repo/msm89xx/config-6.12-recovery "$KSRC/.config"
"$KSRC/scripts/config" --file "$KSRC/.config" \
    --set-str CONFIG_INITRAMFS_SOURCE "$IRFS"
make -C "$KSRC" ARCH=arm64 CROSS_COMPILE=aarch64-openwrt-linux-musl- olddefconfig
grep -q "CONFIG_INITRAMFS_SOURCE=\"$IRFS\"" "$KSRC/.config" || {
    echo "ERROR: CONFIG_INITRAMFS_SOURCE did not survive olddefconfig" >&2
    exit 1
}
grep -q "^CONFIG_WCN36XX=y" "$KSRC/.config" || {
    echo "ERROR: CONFIG_WCN36XX did not survive olddefconfig (check WLAN_VENDOR_ATH)" >&2
    exit 1
}

# The in-tree wcn36xx lacks MFP_CAPABLE (needed for WPA3/SAE PMF).  Carry the
# same OpenWrt mac80211 patch used for the backports driver so the recovery AP
# can do WPA3.  Fatal if it does not apply: hostapd.conf requires PMF.
# The kernel tree is reused between runs, so the patch is usually already in
# place - a reverse dry-run detects that and keeps the step idempotent.
WPA3_PATCH=/repo/openwrt/package/kernel/mac80211/patches/ath/451-wcn36xx-mfp-wpa3.patch
if patch -d "$KSRC" -p1 -R --dry-run -f < "$WPA3_PATCH" >/dev/null 2>&1; then
    echo "wcn36xx MFP/WPA3 patch already applied"
else
    patch -d "$KSRC" -p1 --forward < "$WPA3_PATCH" || {
        echo "ERROR: wcn36xx MFP/WPA3 patch did not apply" >&2
        exit 1
    }
fi

# --- build the kernel + board DTBs -------------------------------------------
make -C "$KSRC" ARCH=arm64 CROSS_COMPILE=aarch64-openwrt-linux-musl- -j$(nproc) Image dtbs

# production .config back in place; the next ./build.sh kernel re-merges it
restore_config
trap - EXIT

BOOT="$KSRC/arch/arm64/boot"
DTB="$BOOT/dts/qcom/msm8916-generic-$BOARD.dtb"
[ -f "$DTB" ] || { echo "ERROR: $DTB not built" >&2; exit 1; }
IRFS_SIZE=$(stat -c %s "$KSRC/usr/initramfs_data.o")
[ "$IRFS_SIZE" -gt 4096 ] || {
    echo "ERROR: initramfs looks empty (usr/initramfs_data.o = $IRFS_SIZE bytes)" >&2
    exit 1
}

# --- assemble: gzip | append-dtb | aboot-img ---------------------------------
gzip -9c "$BOOT/Image" > "$BOOT/Image.recovery.gz"
cat "$BOOT/Image.recovery.gz" "$DTB" > "$BOOT/kernel-recovery-$BOARD.img.gz-dtb"

mkbootimg \
    --kernel "$BOOT/kernel-recovery-$BOARD.img.gz-dtb" \
    --ramdisk /dev/null \
    -o "$OUT.new"

# --- sanity -------------------------------------------------------------------
head -c 8 "$OUT.new" | grep -q "ANDROID!" \
    || { echo "ERROR: mkbootimg output lacks ANDROID! magic" >&2; exit 1; }
mv "$OUT.new" "$OUT"

echo
echo "Built $OUT"
ls -la "$OUT"
sha256sum "$OUT"
EOF
}

COMMAND="${1:-}"

case "$COMMAND" in
    ""|help|-h|--help)
        echo "Usage: $0 <board|all>"
        echo "Boards: $(list_boards | tr '
' ' ')"
        echo
        echo "Output: msm89xx/recovery/<board>.img"
        echo "Requires a prepared tree: ./build.sh prepare && ./build.sh kernel <board>"
        exit 0
        ;;
esac

if [ "$COMMAND" = "all" ]; then
    BOARDS="$(list_boards)"
else
    BOARD="$COMMAND"
    check_board
    BOARDS="$BOARD"
fi

if [ "${DRY_RUN:-0}" = "1" ]; then
    msg "DRY_RUN: container script that would run:"
    container_script
    exit 0
fi

for b in $BOARDS; do
    install_blobs "$b"
    msg "Building recovery image for '$b' in the builder container"
    if ! docker_exec env BOARD="$b" RECOVERY_DIR="$CONTAINER_RECOVERY_DIR" \
            bash -c "$(container_script)"; then
        die "Recovery build failed for board '$b'"
    fi
    ok "Recovery image ready: msm89xx/recovery/$b.img"
done

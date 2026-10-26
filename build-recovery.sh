#!/usr/bin/env bash
#
# build-recovery.sh - build the per-board MSM8916 recovery (rescue) boot image.
#
#   ./build-recovery.sh <board>      hmuf02v5 | mf800b | mifib-v12 | ufi003_mb_v02 | ufi001b
#   ./build-recovery.sh all
#   DRY_RUN=1 ./build-recovery.sh <board>   (show what would be packed, write nothing)
#
# Output: msm89xx/recovery/<board>.img
#
# Image recipe - the PRODUCTION kernel, a recovery initramfs:
#
#   kernel : copied verbatim out of the board's production boot image
#              openwrt/bin/targets/msm89xx/msm8916/
#                msm8916-openwrt-msm89xx-msm8916-generic-<board>-squashfs-boot.img
#            (gzipped Image with the board DTB already appended).  Reusing the
#            shipped kernel is the whole point: the rescue image can then never
#            drift from the kernel the device actually runs.  The previous
#            recipe built a SECOND kernel from msm89xx/config-6.12-recovery and
#            that drift is what left the first AP+STA image without USB.
#   ramdisk: gzip -9 of a newc cpio of msm89xx/recovery/irfs, plus the kernel
#            modules the initramfs needs (RECOVERY_MODULES below)
#   cmdline: earlycon console=ttyMSM0,115200 panic=5
#            (the production "root=/dev/mmcblk0p14 rootfstype=squashfs" is
#            dropped: /init runs from the initramfs and never switch_roots, so
#            root= is dead weight that only invites a confusing mount attempt)
#
# Reusing the production kernel has two consequences, both handled here:
#
#   * CONFIG_DEVTMPFS is NOT set (OpenWrt mounts a tmpfs and runs mdev), so
#     /init has to build /dev itself - see the mount block at the top of it.
#   * the wireless stack is built as MODULES, so the initramfs must carry
#     wcn36xx/mac80211/cfg80211/compat plus the WCNSS remoteproc set, and /init
#     has to insmod them.  Without them the image boots perfectly and simply
#     has no wlan0.
#
# Boards with GPT_RECOVERY=1 in msm89xx/image/msm8916.mk (mf800b, mifib-v12)
# flash this image to the dedicated 16 MiB 'recovery' GPT partition; the other
# boards boot it one-shot from the running system with 'reboot-recovery'.
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
IRFS="$RECOVERY_DIR/irfs"

BLUE="\033[1;34m"
GREEN="\033[1;32m"
RED="\033[1;31m"
NC="\033[0m"

msg() { printf "${BLUE}==>${NC} %s\n" "$*"; }
ok()  { printf "${GREEN}==>${NC} %s\n" "$*"; }
die() { printf "${RED}Error:${NC} %s\n" "$*" >&2; exit 1; }

# cmdline baked into the recovery image.  panic=5 so a panic reboots into
# production instead of parking the box in a dead rescue shell - the recovery
# boot is a one-shot PON reason, so a plain reboot always lands back in
# production.
RECOVERY_CMDLINE="earlycon console=ttyMSM0,115200 panic=5"

# Modules the initramfs has to carry, in insmod order.  Must match the list in
# msm89xx/recovery/irfs/init.
RECOVERY_MODULES="mdt_loader qcom_common qcom_pil_info qcom_wcnss_pil compat cfg80211 mac80211 wcn36xx"

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

production_image() {
    echo "$REPO_DIR/openwrt/bin/targets/msm89xx/msm8916/msm8916-openwrt-msm89xx-msm8916-generic-$1-squashfs-boot.img"
}

# Newest root-msm89xx/lib/modules/<ver> that actually contains wcn36xx.ko.
# The tree can hold several target dirs after a board switch; pick the built one.
module_dir() {
    local d
    for d in $(ls -dt "$REPO_DIR"/openwrt/build_dir/target-*/root-msm89xx/lib/modules/*/ 2>/dev/null); do
        if [ -f "$d/wcn36xx.ko" ]; then
            echo "$d"
            return 0
        fi
    done
    return 1
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
    # A blobs directory fetched before the station mode was removed still
    # carries the wpa_supplicant symlink; drop it so a stale tree cannot put it
    # back into the image.
    rm -f "$RECOVERY_DIR/irfs/usr/sbin/wpa_supplicant"
}

# Copy the wireless + WCNSS modules out of the production root into the
# initramfs.  They are built by the normal kernel build, so this fails loudly
# rather than shipping an image whose AP silently cannot exist.
install_modules() {
    local src ver m
    src="$(module_dir)" || die "No kernel modules found under openwrt/build_dir/*/root-msm89xx/lib/modules.
       The recovery image reuses the production kernel, so its modules must
       exist first: ./build.sh kernel <board>"
    ver="$(basename "$src")"
    msg "Installing kernel modules ($ver) into the initramfs"
    rm -rf "$RECOVERY_DIR/irfs/lib/modules"
    mkdir -p "$RECOVERY_DIR/irfs/lib/modules/$ver"
    for m in $RECOVERY_MODULES; do
        [ -f "$src/$m.ko" ] || die "module $m.ko is missing from $src"
        cp "$src/$m.ko" "$RECOVERY_DIR/irfs/lib/modules/$ver/$m.ko"
    done
}

# Pack: initramfs -> gzip -> ANDROID! header of the production boot image.
#
# Done with python rather than mkbootimg because mkbootimg wants a kernel and a
# ramdisk as separate files; here the kernel is lifted straight out of the
# production image, which is the only copy guaranteed to match the shipped DTB.
pack_image() {
    local b="$1" prod="$2" out="$RECOVERY_DIR/$b.img"
    msg "Packing $b: production kernel + recovery initramfs"
    python3 - "$prod" "$IRFS" "$out" "$RECOVERY_CMDLINE" <<'PY'
import gzip, struct, subprocess, sys

prod, irfs, out, cmdline = sys.argv[1:5]

# --- initramfs: newc cpio, root-owned, reproducible ---------------------------
names = subprocess.run(["find", ".", "-print0"], cwd=irfs,
                       capture_output=True, check=True).stdout
p = subprocess.run(["cpio", "--null", "-o", "-H", "newc", "--owner", "root:root",
                    "--reproducible", "--quiet"],
                   cwd=irfs, input=names, capture_output=True)
if p.returncode != 0:
    sys.exit("cpio failed: " + p.stderr.decode(errors="replace"))
ram = gzip.compress(p.stdout, 9)

# --- kernel: lift it out of the production boot image -------------------------
d = open(prod, "rb").read()
magic, ksize, kaddr, rsize, raddr, ssize, saddr, tags, page, hver, over = \
    struct.unpack("<8s10I", d[:48])
if magic != b"ANDROID!":
    sys.exit(prod + ": not an ANDROID! boot image")
if page < 2048 or page & (page - 1):
    sys.exit(prod + ": bad page size %d" % page)
kern = d[page:page + ksize]
if len(kern) != ksize:
    sys.exit("%s: truncated kernel (%d of %d bytes)" % (prod, len(kern), ksize))
if kern[:2] != b"\x1f\x8b":
    sys.exit(prod + ": kernel is not gzip-compressed")

# --- rebuild the header ------------------------------------------------------
hdr = bytearray(d[:page])
struct.pack_into("<I", hdr, 16, len(ram))        # ramdisk_size
hdr[64:64 + 512] = b"\0" * 512                   # cmdline (production root= goes)
cm = cmdline.encode()
hdr[64:64 + len(cm)] = cm

def pad(x):
    return x + b"\0" * (-len(x) % page)

img = bytes(hdr) + pad(kern) + pad(ram)
if img[:8] != b"ANDROID!":
    sys.exit("header magic lost during packing")
open(out, "wb").write(img)

print("    kernel   %d bytes (from the production boot image)" % ksize)
print("    ramdisk  %d bytes gzip (%d bytes cpio)" % (len(ram), len(p.stdout)))
print("    image    %d bytes (%.2f MiB)" % (len(img), len(img) / 1048576))
PY
}

COMMAND="${1:-}"

case "$COMMAND" in
    ""|help|-h|--help)
        echo "Usage: $0 <board|all>"
        echo "Boards: $(list_boards | tr '\n' ' ')"
        echo
        echo "Output: msm89xx/recovery/<board>.img"
        echo "Requires the board's production image: ./build.sh <board>"
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

for b in $BOARDS; do
    prod="$(production_image "$b")"
    [ -f "$prod" ] || die "No production boot image for '$b':
       $prod
       Build it first: ./build.sh $b"

    if [ "${DRY_RUN:-0}" = "1" ]; then
        msg "[dry-run] $b"
        msg "[dry-run]   kernel  <- $prod"
        msg "[dry-run]   modules <- $(module_dir || echo MISSING)"
        msg "[dry-run]   ramdisk <- $IRFS"
        msg "[dry-run]   output  -> $RECOVERY_DIR/$b.img"
        continue
    fi

    install_blobs "$b"
    install_modules "$b"
    pack_image "$b" "$prod"
    ok "Recovery image ready: msm89xx/recovery/$b.img"
    sha256sum "$RECOVERY_DIR/$b.img"
done

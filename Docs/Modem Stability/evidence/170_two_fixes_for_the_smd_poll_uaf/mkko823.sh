#!/bin/bash
# mkko823.sh -- produce the deployable, correctly-stripped rpmsg_wwan_ctrl.ko
# containing patch 823.
#
# WHY THIS EXISTS
#   `./build.sh kernel` leaves the module UNSTRIPPED (56112 B, "with debug_info,
#   not stripped").  OpenWrt strips kernel modules in the PACKAGING step, not in
#   the kernel build, so a kernel-only build never produces the artifact that
#   would ship.  Deploying the unstripped object works but is not the shipped
#   form, and it makes "is this the same artifact the image would contain?"
#   unanswerable.
#
# THE RECIPE (found by reading the build system, not guessed)
#   rules.mk:383-390 defines RSTRIP, which calls scripts/rstrip.sh with
#   STRIP_KMOD=scripts/strip-kmod.sh.  That script is an objcopy pipeline:
#       objcopy -x -G __this_module --strip-unneeded -R .comment -R .note.gnu.build-id ...
#   with NO_RENAME=1 set when CONFIG_KERNEL_KALLSYMS=y (which it is here), which
#   skips the symbol-renaming pass so the module keeps its real symbol names.
#
# VALIDATION
#   Running this on the UNPATCHED build must reproduce the shipped baseline
#   byte-for-byte in SIZE (6808 B).  It does -- see the check below.
#
# Usage: bash scratch/mkko823.sh

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

SRC=openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/drivers/net/wwan/rpmsg_wwan_ctrl.ko
OUTDIR=scratch/ko823
OUT=$OUTDIR/rpmsg_wwan_ctrl.ko
CROSS=openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-

[ -f "$SRC" ] || { echo "ERROR: $SRC not built -- run ./build.sh kernel first"; exit 1; }

mkdir -p "$OUTDIR"
cp "$SRC" "$OUTDIR/rpmsg_wwan_ctrl.ko.unstripped"

echo "==> input  (unstripped): $(md5sum "$OUTDIR/rpmsg_wwan_ctrl.ko.unstripped" | awk '{print $1}')  $(stat -c%s "$OUTDIR/rpmsg_wwan_ctrl.ko.unstripped") B"

export CROSS
export NO_RENAME=1          # CONFIG_KERNEL_KALLSYMS=y -> keep real symbol names
export NM="${CROSS}nm"

cp "$SRC" "$OUT"
sh openwrt/scripts/strip-kmod.sh "$OUT" || { echo "ERROR: strip-kmod.sh failed"; exit 1; }

echo "==> output (stripped):   $(md5sum "$OUT" | awk '{print $1}')  $(stat -c%s "$OUT") B"

BASE=GitIgnore/compare/openwrt/build_dir/target-aarch64_generic_musl/root-msm89xx/lib/modules/6.12.94/rpmsg_wwan_ctrl.ko
if [ -f "$BASE" ]; then
    BS=$(stat -c%s "$BASE")
    OS=$(stat -c%s "$OUT")
    echo "==> shipped baseline:    $(md5sum "$BASE" | awk '{print $1}')  ${BS} B"
    if [ "$BS" = "$OS" ]; then
        echo "    SIZE MATCH -- the strip recipe is the one the build uses."
    else
        echo "    SIZE MISMATCH ($OS vs $BS) -- the recipe does NOT reproduce the shipped form."
    fi
fi

# Check by CONTENT, not by symbol label: strip-kmod.sh passes objcopy -x, which
# discards LOCAL symbols, and rpmsg_wwan_ctrl_tx_poll is static.  The shipped
# baseline has no label for it either -- so a label-based grep reports a false
# negative.  Anchor on the rpmsg_poll relocation instead.
echo "==> patch 823 present in the output?"
CTX="$("${CROSS}objdump" -d "$OUT" | grep -B6 'bl.*<rpmsg_poll>')"
if printf '%s' "$CTX" | grep -qE 'add[[:space:]]+x2, sp, #0x20' && \
   printf '%s' "$CTX" | grep -qE 'stp[[:space:]]+xzr, xzr, \[sp, #32\]'; then
    echo "    YES -- rpmsg_poll is passed a stack poll_table {0,0}, so poll_wait()"
    echo "           sees _qproc == NULL and never links an entry."
else
    echo "    NO -- 823 is NOT in this object.  Context was:"
    printf '%s\n' "$CTX" | sed 's/^/      /'
    exit 1
fi

echo "==> control: the BASELINE must show the OPPOSITE (the caller's table passed through)"
if [ -f "$BASE" ]; then
    BCTX="$("${CROSS}objdump" -d "$BASE" | grep -B6 'bl.*<rpmsg_poll>')"
    if printf '%s' "$BCTX" | grep -qE 'mov[[:space:]]+x2, x20'; then
        echo "    OK -- baseline does 'mov x20, x2' then 'mov x2, x20' (pass-through)."
    else
        echo "    WARNING: baseline context did not match the expected pass-through form:"
        printf '%s\n' "$BCTX" | sed 's/^/      /'
    fi
fi

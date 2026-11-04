#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Prints the hardware evidence a board pull request has to paste back.
# A computer can check that the DTB is registered; only the bench can check
# that the reset pinhole is the GPIO the DTS claims, that the slot switch
# actually selects the right SIM, and that the modem survives 20 minutes.
#
#   tests/board-profile/signoff.sh <board>       # board = a diffconfigs/ name
#
# Nothing here touches a device: it reads the tree and prints commands.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
BOARD=$1
MK=$ROOT/msm89xx/image/msm8916.mk

[ -n "$BOARD" ] || { echo "usage: $0 <board>   (one of: $(ls "$ROOT/diffconfigs" | tr '\n' ' '))" >&2; exit 2; }
[ -f "$ROOT/diffconfigs/$BOARD" ] || { echo "no diffconfigs/$BOARD" >&2; exit 2; }

DEV=$(grep -oE '^CONFIG_TARGET_msm89xx_msm8916_DEVICE_[A-Za-z0-9_-]+=y$' "$ROOT/diffconfigs/$BOARD" |
      sed -e 's/^CONFIG_TARGET_msm89xx_msm8916_DEVICE_//' -e 's/=y$//')
profile_field() {
    awk -v want="$1" -v var="$2" '
        /^define Device\// { dev = $2; sub(/^Device\//, "", dev); inblk = (dev == want); next }
        /^endef/           { inblk = 0; next }
        inblk && $0 ~ "^[ \t]*" var "[ \t]*:?=" {
            line = $0
            sub(/^[^=]*=[ \t]*/, "", line)
            # DEVICE_PACKAGES is written over several lines with trailing "\".
            while (line ~ /\\$/) {
                sub(/\\$/, "", line)
                if ((getline nxt) <= 0) break
                sub(/^[ \t]*/, "", nxt)
                line = line " " nxt
            }
            print line
            exit
        }
    ' "$MK"
}
DTS=$(profile_field "$DEV" DEVICE_DTS)
[ -n "$DTS" ] || DTS="msm8916-$DEV"
SUPPORTED=$(profile_field "$DEV" SUPPORTED_DEVICES)
PKGS=$(profile_field "$DEV" DEVICE_PACKAGES | tr -s ' \t' ' ')

# The DTS text, taken from the patch that adds it, for the LED and battery hints.
PATCH=$(awk -v want="arch/arm64/boot/dts/qcom/$DTS.dts" '
    /^--- \/dev\/null[ \t]*$/ { n = 1; next }
    n && $0 == "+++ b/" want { print FILENAME; exit }
    { n = 0 }' "$ROOT"/msm89xx/patches/*.patch 2>/dev/null | head -1)
DTS_TEXT=""
[ -n "$PATCH" ] && DTS_TEXT=$(grep '^+' "$PATCH" | sed 's/^+//')

# LED names as /sys/class/leds shows them: "label" where the DTS sets one,
# otherwise "<color>:<function>" from the pair.
leds=$(printf '%s\n' "$DTS_TEXT" | awk '
    /function = .*LED_FUNCTION_/ {
        f = $0; sub(/.*LED_FUNCTION_/, "", f); sub(/[^A-Z0-9_].*/, "", f)
        if (c != "") { print tolower(c) ":" tolower(f); c = ""; f = "" }
        next
    }
    /color = .*LED_COLOR_ID_/ {
        c = $0; sub(/.*LED_COLOR_ID_/, "", c); sub(/[^A-Z0-9_].*/, "", c)
        if (f != "") { print tolower(c) ":" tolower(f); c = ""; f = "" }
        next
    }
    /label = "/ { l = $0; sub(/.*label = "/, "", l); sub(/".*/, "", l); print l }
' | sort -u | tr '\n' ' ')
[ -n "$leds" ] || leds="(inherited from msm8916-ufi.dtsi; check /sys/class/leds on the device)"
battery=no
case "$PKGS" in *luci-app-battery*) battery=yes ;; esac

cat <<EOF
Board        : $BOARD  (Device/$DEV)
Device tree  : $DTS
Accepted as  : $SUPPORTED
Battery      : $battery
LEDs in DTS  : $leds

Flash the image, then paste the output of every block below into the pull
request. "It works" is not evidence; the lines are.

--- 1. identity -------------------------------------------------------------
. /etc/openwrt_release
echo "release=\$DISTRIB_RELEASE board=\$(cat /tmp/sysinfo/board_name)"
echo "expect board=$SUPPORTED"

--- 2. modem and data -------------------------------------------------------
mmcli -L
ip -4 addr show wwan0 | grep inet
ip route show default
ping -c3 1.1.1.1
nslookup example.com

--- 3. failsafe -------------------------------------------------------------
# Power on, wait for the red LED to fast-blink, then hold the reset button for
# the whole 7 s window and release. The box must answer on 192.168.10.1.
# Also report which GPIO the pinhole really is:
evtest            # or: cat /sys/kernel/debug/gpio, then press the button

--- 4. LEDs -----------------------------------------------------------------
for l in /sys/class/leds/*; do
    echo 255 > "\$l/brightness"; sleep 1; echo 0 > "\$l/brightness"
done
# Say which physical lamp lit for each name above, and which lamps stayed dark.

--- 5. cold boots -----------------------------------------------------------
# Three power-cycles (not reboots). Each must reach the LAN page on 192.168.8.1
# and bring up the modem without a manual intervention.

--- 6. soak -----------------------------------------------------------------
# Leave it running with a bearer for at least 20 minutes, then:
awk '{print int(\$1)}' /proc/uptime
dmesg | grep -c 'handling crash'
dmesg | grep -E 'restarting|is now up' | tail -5
# The failure this project cares about lands at ~900 s of modem uptime.
# A five-minute ping test cannot see it.

--- 7. image ---------------------------------------------------------------
sha256sum <the image you flashed>
EOF

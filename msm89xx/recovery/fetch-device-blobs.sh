#!/usr/bin/env bash
# fetch-device-blobs.sh <board> <user@host>
#
# Pull the per-board WiFi firmware (wcnss.mdt + segments, wlan/prima NV) and a
# SAE-capable hostapd + its shared libraries from a LIVE device into
# msm89xx/recovery/blobs/<board>/.
#
# build-recovery.sh installs that directory into the initramfs for the SAME
# board only.  The blobs are not interchangeable - wcnss.mdt and the calibrated
# wlan NV are board-specific - so one directory per board is what stops a
# mifib-v12 build from silently carrying hmuf02v5 firmware.
#
# The blobs are git-ignored; run this once per board before building it.
set -euo pipefail

BOARD="${1:?usage: $0 <board> <user@host>}"
HOST="${2:?usage: $0 <board> <user@host>}"

RECOVERY_DIR="$(cd "$(dirname "$0")" && pwd -P)"
DEST="$RECOVERY_DIR/blobs/$BOARD"
FW="$DEST/lib/firmware"

echo "[+] $BOARD <- $HOST"
rm -rf "$DEST"
mkdir -p "$FW/wlan/prima" "$DEST/usr/sbin" "$DEST/lib"

echo "[+] fetching WCNSS processor firmware (wcnss.mdt + segments)"
ssh "$HOST" 'ls /lib/firmware/wcnss.mdt /lib/firmware/wcnss.b* 2>/dev/null' | while read -r f; do
    [ -n "$f" ] && scp -p "$HOST:$f" "$FW/"
done

echo "[+] fetching wlan/prima NV (WCNSS_qcom_wlan_nv.bin)"
scp -p "$HOST:/lib/firmware/wlan/prima/WCNSS_qcom_wlan_nv.bin" "$FW/wlan/prima/" \
    || echo "!! WCNSS_qcom_wlan_nv.bin not found on device; the AP may fail without it"

echo "[+] fetching hostapd"
scp -p "$HOST:/usr/sbin/hostapd" "$DEST/usr/sbin/hostapd"
chmod +x "$DEST/usr/sbin/hostapd"

echo "[+] fetching iw"
scp -p "$HOST:/usr/sbin/iw" "$DEST/usr/sbin/iw"
chmod +x "$DEST/usr/sbin/iw"

# Library closure of BOTH binaries, resolved ON THE DEVICE (the libs are
# aarch64 and this host cannot ldd them).  The walk is transitive:
# libblobmsg_json pulls in libjson-c, for example, and a missing indirect
# library makes the daemon exit with no useful message.
echo "[+] resolving hostapd + iw shared libraries (transitive)"
LIBS=$(ssh "$HOST" 'set -- /usr/sbin/hostapd /usr/sbin/iw; seen=""; while [ $# -gt 0 ]; do f="$1"; shift; for l in $(ldd "$f" 2>/dev/null | grep -oE "/[^ ]+\.so[^ ]*"); do case " $seen " in *" $l "*) continue ;; esac; seen="$seen $l"; set -- "$@" "$l"; done; done; echo $seen')
for lib in $LIBS; do
    case "$lib" in
        */ld-musl*|*/libc.so*|*/libgcc_s*) continue ;;
    esac
    scp -p "$HOST:$lib" "$DEST/lib/" 2>/dev/null || echo "!! could not fetch $lib"
done

echo "[+] stored under blobs/$BOARD:"
find "$DEST" -type f | sed "s|^$RECOVERY_DIR/|    |"
echo "[+] now run: ./build-recovery.sh $BOARD"

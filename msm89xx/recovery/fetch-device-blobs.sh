#!/usr/bin/env bash
# fetch-device-blobs.sh <board> <user@host>
#
# Pull the per-board WiFi firmware and a SAE-capable hostapd (plus its shared
# libs) from a LIVE device into the recovery irfs, so the recovery image can
# bring up a WiFi AP without a USB cable. The blobs are device-specific
# (calibrated NV) and must come from the real hardware, not a generic dump.
#
# These fetched files are git-ignored (see msm89xx/recovery/.gitignore); run
# this before build-recovery.sh <board>.
set -euo pipefail

BOARD="${1:?usage: $0 <board> <user@host>}"
HOST="${2:?usage: $0 <board> <user@host>}"

RECOVERY_DIR="$(cd "$(dirname "$0")" && pwd -P)"
IRFS="$RECOVERY_DIR/irfs"
FW="$IRFS/lib/firmware"
mkdir -p "$FW/wlan/prima" "$IRFS/usr/sbin" "$IRFS/lib"

echo "[+] fetching WCNSS processor firmware (wcnss.mdt + segments)"
ssh "$HOST" 'ls /lib/firmware/wcnss.mdt /lib/firmware/wcnss.b* 2>/dev/null' | while read -r f; do
    [ -n "$f" ] && scp -p "$HOST:$f" "$FW/"
done

echo "[+] fetching wlan/prima NV (WCNSS_qcom_wlan_nv.bin)"
scp -p "$HOST:/lib/firmware/wlan/prima/WCNSS_qcom_wlan_nv.bin" "$FW/wlan/prima/" \
    || echo "!! WCNSS_qcom_wlan_nv.bin not found on device; the AP may fail without it"

echo "[+] fetching hostapd"
scp -p "$HOST:/usr/sbin/hostapd" "$IRFS/usr/sbin/hostapd"
chmod +x "$IRFS/usr/sbin/hostapd"

echo "[+] copying hostapd shared libraries (skipping libc/ld-musl already in irfs)"
ssh "$HOST" 'ldd /usr/sbin/hostapd' | grep -oE '/[^ ]+\.so[^ ]*' | sort -u | while read -r lib; do
    case "$lib" in
        */ld-musl*|*/libc.so*|*/libgcc_s.so*) continue ;;
    esac
    scp -p "$HOST:$lib" "$IRFS/lib/" 2>/dev/null || true
done

echo "[+] done. Firmware and hostapd libs are git-ignored; now run:"
echo "    ./build-recovery.sh $BOARD"

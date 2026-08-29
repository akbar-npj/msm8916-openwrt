#!/bin/sh
# One-shot Qualcomm firmware dumper: mount partitions read-only,
# copy relevant blobs into /lib/firmware, set a marker to avoid
# re-running, and trigger a reboot. Accepts optional 'MCFG_PATH'
# env var to override the MCFG relative path within modem/persist.

set -e
DEFAULT_FLAG="/lib/firmware/DUMPED"
DEFAULT_MCFG_PATH="image/modem_pr/mcfg/configs/mcfg_sw/generic/common/row/gen_3gpp"

MARKER="${FLAG:-$DEFAULT_FLAG}"
[ -f "$MARKER" ] && exit 0

log() { logger -t msm-fw-dumper "$*"; }

MNT="/tmp/mnt/msmfw"
FW="/lib/firmware"
MCFG_REL="${MCFG_PATH:-$DEFAULT_MCFG_PATH}"

log "start (marker not present)"

# Prepare mount points and target
mkdir -p "$MNT/modem" "$MNT/persist" "$FW/wlan/prima"

find_part() {
  local name="$1"
  if [ -e "/dev/disk/by-partlabel/$name" ]; then
    echo "/dev/disk/by-partlabel/$name"
    return
  fi
  for dev in /dev/mmcblk[0-9]p[0-9]*; do
    [ -b "$dev" ] || continue
    local pname="$(cat "/sys/class/block/$(basename "$dev")/uevent" 2>/dev/null | grep PARTNAME | cut -d= -f2)"
    if [ "$pname" = "$name" ]; then
      echo "$dev"
      return
    fi
  done
  # Fallback mappings for known MSM8916 partition layouts
  case "$name" in
    modem)
      [ -b /dev/mmcblk0p1 ] && echo "/dev/mmcblk0p1" && return
      [ -b /dev/mmcblk0p3 ] && echo "/dev/mmcblk0p3" && return
      ;;
    persist)
      [ -b /dev/mmcblk0p24 ] && echo "/dev/mmcblk0p24" && return
      [ -b /dev/mmcblk0p6 ]  && echo "/dev/mmcblk0p6"  && return
      ;;
  esac
}

MODEM_DEV="$(find_part modem)"
PERSIST_DEV="$(find_part persist)"

# Mount partitions read-only
if [ -n "$MODEM_DEV" ]; then
  mount -t vfat -o ro,nosuid,nodev,noexec,iocharset=iso8859-1,codepage=437 "$MODEM_DEV" "$MNT/modem" 2>/dev/null || \
  mount -t vfat -o ro "$MODEM_DEV" "$MNT/modem" 2>/dev/null || log "WARN: modem mount failed on $MODEM_DEV"
else
  log "WARN: modem partition not found"
fi

if [ -n "$PERSIST_DEV" ]; then
  mount -t ext4 -o ro,nosuid,nodev,noexec "$PERSIST_DEV" "$MNT/persist" 2>/dev/null || \
  mount -t vfat -o ro,nosuid,nodev,noexec "$PERSIST_DEV" "$MNT/persist" 2>/dev/null || \
  mount -o ro "$PERSIST_DEV" "$MNT/persist" 2>/dev/null || log "WARN: persist mount failed on $PERSIST_DEV"
else
  log "WARN: persist partition not found"
fi

# Copy if exists!
copy_if() {
  src="$1"; dst="$2"
  if [ -f "$src" ]; then
    cp -af "$src" "$dst" && log "copied $(basename "$src")" || return $?
  fi
  return 0
}

# Modem/Wi-Fi core blobs (search case-insensitively across common paths)
for img_dir in "$MNT/modem/image" "$MNT/modem/IMAGE" "$MNT/modem"; do
  [ -d "$img_dir" ] || continue
  for f in "$img_dir"/*; do
    [ -f "$f" ] || continue
    fname="$(basename "$f" | tr '[:upper:]' '[:lower:]')"
    case "$fname" in
      wcnss.*|modem.*|mba.mbn|cmnlib.*|keymaste.*)
        cp -af "$f" "$FW/$fname" && log "copied $fname"
        ;;
    esac
  done
done

# Wi‑Fi NV/configs required by wcn36xx (place under wlan/prima)
mkdir -p "$FW/wlan/prima"

for p in "$MNT/persist/WCNSS_qcom_wlan_nv.bin" "$MNT/persist/wlan/prima/WCNSS_qcom_wlan_nv.bin" \
         "$MNT/modem/image/wlan/prima/WCNSS_qcom_wlan_nv.bin" "$MNT/modem/IMAGE/WLAN/PRIMA/WCNSS_QCOM_WLAN_NV.BIN" \
         "$MNT/modem/wlan/prima/WCNSS_qcom_wlan_nv.bin"; do
  if [ -f "$p" ]; then
    cp -af "$p" "$FW/wlan/prima/WCNSS_qcom_wlan_nv.bin" && log "copied WCNSS_qcom_wlan_nv.bin"
    break
  fi
done

for p in "$MNT/modem/image/wlan/prima/WCNSS_cfg.dat" "$MNT/modem/IMAGE/WLAN/PRIMA/WCNSS_CFG.DAT" \
         "$MNT/modem/wlan/prima/WCNSS_cfg.dat"; do
  if [ -f "$p" ]; then
    cp -af "$p" "$FW/wlan/prima/WCNSS_cfg.dat" && log "copied WCNSS_cfg.dat"
    break
  fi
done

for p in "$MNT/modem/image/wlan/prima/WCNSS_qcom_cfg.ini" "$MNT/modem/IMAGE/WLAN/PRIMA/WCNSS_QCOM_CFG.INI" \
         "$MNT/modem/wlan/prima/WCNSS_qcom_cfg.ini"; do
  if [ -f "$p" ]; then
    cp -af "$p" "$FW/wlan/prima/WCNSS_qcom_cfg.ini" && log "copied WCNSS_qcom_cfg.ini"
    break
  fi
done

# MCFG handling:
for mcfg_try in "$MNT/modem/$MCFG_REL/mcfg_sw.mbn" "$MNT/modem/image/modem_pr/mcfg/configs/mcfg_sw/generic/common/row/gen_3gpp/mcfg_sw.mbn"; do
  if [ -f "$mcfg_try" ]; then
    cp -af "$mcfg_try" "$FW/MCFG_SW.MBN" && log "MCFG from modem"
    break
  fi
done

# Honoring any user copied MCFG to /lib/firmware
[ -f "$FW/mcfg_sw.mbn" ] && ln -sf "$FW/mcfg_sw.mbn" "$FW/MCFG_SW.MBN" 2>/dev/null || true

sync

# Unmount and cleanup
umount "$MNT/modem" 2>/dev/null || true
umount "$MNT/persist" 2>/dev/null || true
rmdir "$MNT/persist" "$MNT/modem" 2>/dev/null || true

# Set marker and reboot once
touch "$MARKER"
log "Dumped!"

exit 0

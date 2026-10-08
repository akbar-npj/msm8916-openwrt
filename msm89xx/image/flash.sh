#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Flash OpenWrt to MSM8916 devices entirely via EDL.
# Two modes:
#   migrate - stock Android -> OpenWrt (repartition + bootloader + boot + rootfs)
#   update  - existing OpenWrt -> OpenWrt (boot + rootfs only)
# Prerequisites: edl
# Usage: run from the build output directory (bin/targets/...)

set -euo pipefail

# Temp files - cleaned up on exit
firmware_tmp=""
gpt_tmp=""
trap 'rm -rf "$firmware_tmp" "$gpt_tmp"' EXIT

# rootfs_data occupies the tail of the eMMC (several GiB). For a "factory clean"
# overlay we only destroy the primary ext4 superblock: the boot hook
# lib/preinit/79-check-rootfs-data probes ONLY the primary superblock and
# reformats with `mkfs.ext4 -q -F -L rootfs_data` when it is gone. The old
# filesystem beyond this window is logically overwritten by that mkfs.
# 4096 sectors = 2 MiB (superblock at byte 1024, wide margin).
#
# The wipe is done with a raw zero-write ("edl ws"), NOT "edl e"/"edl ep":
# see wipe_rootfs_data() for why the erase op is unusable on this device.
ROOTFS_DATA_WIPE_SECTORS="${ROOTFS_DATA_WIPE_SECTORS:-4096}"

MODE=""
ERASE_CONFIG=""      # update only: "keep" or "erase"
ASSUME_YES=false
GPT_DUMP=""
TOT_SECTORS=""
boot_path=""
rootfs_path=""
gpt_path=""
firmware_dir=""

die() {
    echo "[-] Error: $*" >&2
    exit 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

usage() {
    cat <<'EOF'
Flash OpenWrt to MSM8916 devices via EDL.

Usage: flash.sh [options]

Options:
  --mode migrate     Stock Android -> OpenWrt (repartition + bootloader).
  --mode update      Existing OpenWrt -> OpenWrt (boot + rootfs only).
  --erase-config     Update mode: wipe the rootfs_data overlay (factory clean).
  --keep-config      Update mode: keep /etc, packages and dumped firmware.
  --yes              Skip the interactive confirmation. Requires --mode.
  --help             Show this help.

With no options an interactive menu is shown.
EOF
}

# Read the total sector count out of the GPT itself (device eMMC sizes differ):
# the primary header at LBA 1 stores the backup header LBA at byte offset 32.
read_tot_sectors() {
    local alt_lba
    alt_lba=$(od -An -tu8 -j 544 -N 8 "$gpt_path" | tr -d '[:space:]')
    if ! [[ "${alt_lba:-0}" =~ ^[0-9]+$ ]] || [[ "$alt_lba" -lt 67 ]]; then
        die "cannot read backup header LBA from $(basename "$gpt_path")"
    fi
    TOT_SECTORS=$((alt_lba + 1))
}

parse_args() {
    local saw_keep=false saw_erase=false
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --mode)
                [[ $# -ge 2 ]] || die "--mode requires an argument"
                MODE="$2"
                [[ "$MODE" == "migrate" || "$MODE" == "update" ]] \
                    || die "--mode must be 'migrate' or 'update'"
                shift 2
                ;;
            --erase-config)
                saw_erase=true; ERASE_CONFIG="erase"; shift ;;
            --keep-config)
                saw_keep=true; ERASE_CONFIG="keep"; shift ;;
            --yes)
                ASSUME_YES=true; shift ;;
            --help|-h)
                usage; exit 0 ;;
            *)
                die "unknown option: $1 (try --help)" ;;
        esac
    done

    if [[ "$saw_keep" == true && "$saw_erase" == true ]]; then
        die "--keep-config and --erase-config are mutually exclusive"
    fi
    if [[ "$ERASE_CONFIG" == "erase" && "$MODE" != "update" ]]; then
        die "--erase-config is only valid with --mode update"
    fi
    if [[ "$ERASE_CONFIG" == "keep" && "$MODE" != "update" ]]; then
        die "--keep-config is only valid with --mode update"
    fi
    if [[ "$ASSUME_YES" == true && -z "$MODE" ]]; then
        die "--yes requires --mode (refusing to guess in unattended mode)"
    fi
}

show_menu() {
    echo "=== OpenWrt MSM8916 EDL Flash Script ==="
    echo
    echo "  1) Migrate from stock Android to OpenWrt"
    echo "     (DESTRUCTIVE: repartitions the eMMC, flashes the bootloader,"
    echo "      wipes Android)"
    echo "  2) Update an existing OpenWrt install"
    echo "     (flashes boot + rootfs only; partition table left untouched)"
    echo
    local choice
    while true; do
        read -r -p "Choice [1/2]: " choice
        case "$choice" in
            1) MODE="migrate"; return ;;
            2) MODE="update";  return ;;
            *) echo "[!] Please enter 1 or 2." ;;
        esac
    done
}

find_image() {
    local dir="$1" pattern="$2" file
    file=$(find "$dir" -maxdepth 1 -type f -name "$pattern" 2>/dev/null | head -n 1 || true)
    if [[ -z "${file:-}" ]]; then
        echo "[-] Error: Image not found with pattern: $pattern" >&2
        return 1
    fi
    echo "$file"
}

# Verify the device is reachable in EDL and, for update mode, that the OpenWrt
# GPT (boot + rootfs) is already present. edl resolves partitions via the GPT,
# so update mode must never be used on a non-OpenWrt device.
check_edl() {
    require_cmd edl

    if lsusb 2>/dev/null | grep -q '05c6:9008'; then
        echo "[+] EDL device detected (05c6:9008)"
    else
        echo "[!] Warning: no 05c6:9008 device on USB; relying on edl handshake"
    fi

    echo "[*] Talking to device (edl printgpt)..."
    if ! GPT_DUMP="$(edl printgpt 2>&1)"; then
        die "edl printgpt failed - is the device in EDL mode?"
    fi

    if [[ "$MODE" == "update" ]]; then
        if ! grep -qw boot <<<"$GPT_DUMP" || ! grep -qw rootfs <<<"$GPT_DUMP"; then
            die "the device does not have an OpenWrt GPT (boot/rootfs not found).
    This looks like a stock/Android device. Use 'Migrate' (mode 1) instead."
        fi
        echo "[+] OpenWrt GPT found (boot + rootfs present)"
    fi
}

detect_images_migrate() {
    echo "[*] Detecting OpenWrt images..."
    gpt_path=$(find_image "." "*-squashfs-gpt_both0.bin") || exit 1
    boot_path=$(find_image "." "*-squashfs-boot.img")     || exit 1
    rootfs_path=$(find_image "." "*-squashfs-system.img") || exit 1

    read_tot_sectors

    echo "[+] GPT:    $(basename "$gpt_path") (${TOT_SECTORS} sectors)"
    echo "[+] Boot:   $(basename "$boot_path")"
    echo "[+] Rootfs: $(basename "$rootfs_path")"
}

detect_images_update() {
    echo "[*] Detecting OpenWrt images..."
    boot_path=$(find_image "." "*-squashfs-boot.img")     || exit 1
    rootfs_path=$(find_image "." "*-squashfs-system.img") || exit 1

    echo "[+] Boot:   $(basename "$boot_path")"
    echo "[+] Rootfs: $(basename "$rootfs_path")"
}

# Extract the Qualcomm bootloader blobs (migrate only).
extract_firmware() {
    echo
    echo "=== Firmware bundle (.zip) ==="
    local zip_path
    zip_path="$(find_image "." "*-firmware.zip" || true)"

    if [[ -n "${zip_path:-}" ]]; then
        echo "[*] Found firmware ZIP: $(basename "$zip_path")"
        firmware_tmp="$(mktemp -d)"
        echo "[*] Extracting .mbn files..."
        unzip -q -j -d "$firmware_tmp" "$zip_path" "*.mbn" || \
            die "failed to extract .mbn files from ZIP"
        firmware_dir="$firmware_tmp"
    else
        echo "[!] No firmware ZIP found in the current directory"
        echo "=== Qualcomm Firmware Directory (fallback) ==="
        read -e -r -p "Drag the folder with .mbn files (aboot, hyp, rpm, sbl1, tz): " firmware_dir
        firmware_dir="${firmware_dir//\"/}"
        firmware_dir="${firmware_dir//\'/}"
        firmware_dir="${firmware_dir// /}"
    fi

    [[ -n "$firmware_dir" && -d "$firmware_dir" ]] \
        || die "invalid firmware directory: $firmware_dir"

    echo "[*] Using firmware directory: $firmware_dir"
    echo
}

verify_mbn() {
    echo "[*] Verifying firmware partitions..."
    local part missing=false
    for part in aboot hyp rpm sbl1 tz; do
        if [[ ! -f "$firmware_dir/${part}.mbn" ]]; then
            echo "[-] ${part}.mbn not found"
            missing=true
        else
            echo "[+] ${part}.mbn"
        fi
    done
    [[ "$missing" == false ]] || die "missing required .mbn files"
}

prompt_config() {
    if [[ -n "$ERASE_CONFIG" ]]; then
        return
    fi
    echo
    echo "Config overlay (rootfs_data):"
    echo "  y) KEEP  - preserve /etc, packages and the dumped modem/Wi-Fi firmware"
    echo "  n) ERASE - wipe the overlay for a factory-clean first boot"
    echo "             (fast: 2 MiB superblock wipe, reformatted on boot;"
    echo "              modem firmware is re-dumped automatically)"
    local reply
    read -r -p "Keep configuration? [Y/n]: " reply
    case "$reply" in
        [Nn]*) ERASE_CONFIG="erase" ;;
        *)     ERASE_CONFIG="keep" ;;
    esac
}

confirm_migrate() {
    if [[ "$ASSUME_YES" == true ]]; then
        return
    fi
    echo
    echo "!!! WARNING: MIGRATE is DESTRUCTIVE !!!"
    echo "    This will repartition the eMMC, flash the bootloader and ERASE"
    echo "    stock Android (including its userdata) from this device."
    local confirm
    read -r -p "Type 'yes' to continue: " confirm
    [[ "$confirm" == "yes" ]] || { echo "[!] Cancelled"; exit 0; }
}

confirm_update() {
    if [[ "$ASSUME_YES" == true ]]; then
        return
    fi
    local confirm
    echo
    read -r -p "Continue with the update? (y/N): " confirm
    [[ "$confirm" =~ ^[Yy]$ ]] || { echo "[!] Cancelled"; exit 0; }
}

# Back up the device-unique radio/calibration partitions. This runs BEFORE any
# write, so if any single backup fails we abort with the device completely
# untouched. Each read is validated (exit code AND a non-empty file) so a
# "successful" but empty/truncated read can never slip through to the restore
# step after the eMMC has been repartitioned.
backup_radio() {
    mkdir -p saved
    echo
    echo "=== Partition Backup (EDL) ==="
    echo "[*] Saving radio/calibration partitions to ./saved/"
    echo "    (runs before anything is written; any failure aborts safely)"
    local n tmp
    for n in fsc fsg modemst1 modemst2 modem persist sec; do
        echo "[*] Backing up $n..."
        tmp="saved/$n.bin.new"
        rm -f "$tmp"
        if ! edl r "$n" "$tmp"; then
            rm -f "$tmp"
            die "BACKUP FAILED for '$n' (edl read returned an error).
    No changes have been made to the device - nothing was repartitioned or flashed.
    Fix the EDL connection and run the script again."
        fi
        if [[ ! -s "$tmp" ]]; then
            rm -f "$tmp"
            die "BACKUP FAILED for '$n' (the backup file is empty or was not created).
    Refusing to repartition without a valid backup.
    No changes have been made to the device - nothing was repartitioned or flashed.
    Fix the EDL connection and run the script again."
        fi
        mv -f "$tmp" "saved/$n.bin"
        echo "    -> saved/$n.bin ($(wc -c < "saved/$n.bin") bytes)"
    done
    echo "[+] Backup complete: all 7 partitions saved to ./saved/"
}

restore_radio() {
    echo
    echo "=== Partition Restoration (EDL) ==="
    local n
    # Pre-flight: never start restoring unless every backup is present, so we
    # cannot half-restore and leave the radio partitions inconsistent.
    for n in fsc fsg modemst1 modemst2 modem persist sec; do
        [[ -s "saved/$n.bin" ]] || die "missing backup file saved/$n.bin - refusing to restore"
    done
    for n in fsc fsg modemst1 modemst2 modem persist sec; do
        echo "[*] Restoring $n..."
        edl w "$n" "saved/$n.bin" || die "restoring $n"
    done
}

# Flash the new GPT via raw sector writes first, so all subsequent flashes use
# the correct partition offsets from the OpenWrt GPT.
# gpt_both0.bin layout: [34 sectors primary] [32 sectors backup entries] [1 sector backup header]
flash_gpt() {
    echo
    echo "=== Flashing GPT (EDL) ==="
    gpt_tmp="$(mktemp -d)"
    dd if="$gpt_path" bs=512 count=34         of="${gpt_tmp}/primary.bin"        2>/dev/null
    dd if="$gpt_path" bs=512 skip=34 count=32 of="${gpt_tmp}/backup_entries.bin" 2>/dev/null
    dd if="$gpt_path" bs=512 skip=66 count=1  of="${gpt_tmp}/backup_header.bin"  2>/dev/null
    edl ws 0                      "${gpt_tmp}/primary.bin"        || die "flashing primary GPT"
    edl ws $((TOT_SECTORS - 33)) "${gpt_tmp}/backup_entries.bin" || die "flashing GPT backup entries"
    edl ws $((TOT_SECTORS - 1))  "${gpt_tmp}/backup_header.bin"  || die "flashing GPT backup header"
}

flash_bootloader() {
    echo
    echo "=== Flashing Bootloader (EDL) ==="
    edl w aboot "$firmware_dir/aboot.mbn" || die "flashing aboot"
    edl w hyp   "$firmware_dir/hyp.mbn"   || die "flashing hyp"
    edl w rpm   "$firmware_dir/rpm.mbn"   || die "flashing rpm"
    edl w sbl1  "$firmware_dir/sbl1.mbn"  || die "flashing sbl1"
    edl w tz    "$firmware_dir/tz.mbn"    || die "flashing tz"
}

flash_boot_rootfs() {
    echo
    echo "=== Flashing OpenWrt images (EDL) ==="
    edl w boot   "$boot_path"   || die "flashing boot"
    edl w rootfs "$rootfs_path" || die "flashing rootfs"
}

# The firehose loader this device hands out (fhprg_peek.bin) does NOT implement
# the erase op: "edl e" / "edl ep" make the target reply "No storage drive
# number", perform NO write, and yet edl still prints "Erased ..." and exits 0.
# So the overlay is wiped with a raw zero-write ("edl ws") instead, which this
# loader does support, and the result is verified by reading the superblock back.
gpt_field() {  # $1=partition-name  $2=Offset|Length  -> hex value (no 0x)
    sed -n "s/^$1:[[:space:]]*.*$2 0x\([0-9a-fA-F]*\),.*/\1/p" <<<"$GPT_DUMP" | head -n 1
}

wipe_rootfs_data() {
    local off_hex start count
    # The GPT may have just been rewritten by flash_gpt (migrate mode), so
    # re-read it instead of trusting the pre-flash dump in $GPT_DUMP: on an
    # Android -> OpenWrt migration that dump is the *Android* GPT, which has
    # no rootfs_data at all.  If rootfs_data is genuinely absent there is
    # nothing to wipe - a fresh one is created and formatted on first boot.
    GPT_DUMP="$(edl printgpt 2>&1)" || true
    off_hex="$(gpt_field rootfs_data Offset)"
    if [[ -z "$off_hex" ]]; then
        echo "[!] rootfs_data not present in the device GPT; skipping overlay wipe"
        return 0
    fi
    start=$(( 0x$off_hex / 512 ))
    count="$ROOTFS_DATA_WIPE_SECTORS"
    echo "[*] Wiping rootfs_data superblock (${count} sectors)..."

    local zeros
    zeros="$(mktemp)"
    dd if=/dev/zero of="$zeros" bs=512 count="$count" status=none \
        || die "creating zero image for the wipe"
    edl ws "$start" "$zeros" || die "wiping rootfs_data"
    rm -f "$zeros"

    # Verify: the ext superblock magic lives at byte 1080 (sector 2, byte 56).
    # If it is still there the wipe silently did nothing - refuse to continue.
    local sb
    sb="$(mktemp)"
    if edl rs $(( start + 2 )) 1 "$sb" >/dev/null 2>&1 && [[ -s "$sb" ]]; then
        if od -An -tx1 -j 56 -N 2 "$sb" | grep -q "53 ef"; then
            rm -f "$sb"
            die "rootfs_data superblock is still present after the wipe - refusing to continue"
        fi
    fi
    rm -f "$sb"
    echo "[+] rootfs_data wiped and verified"
}

do_migrate() {
    detect_images_migrate
    extract_firmware
    verify_mbn
    confirm_migrate

    backup_radio
    flash_gpt
    flash_bootloader
    flash_boot_rootfs
    # Restore the radio partitions BEFORE the overlay wipe: restore_radio is the
    # step that puts the modem/EFS blobs back at the new GPT offsets, so it must
    # never be skipped because an unrelated later step aborted the script.
    restore_radio
    wipe_rootfs_data

    echo
    echo "[+] Flash completed successfully"
    echo "[*] Rebooting..."
    edl reset || die "resetting device"
}

do_update() {
    detect_images_update
    prompt_config
    confirm_update

    flash_boot_rootfs
    if [[ "$ERASE_CONFIG" == "erase" ]]; then
        wipe_rootfs_data
    fi

    echo
    echo "[+] Update completed successfully"
    echo "[*] Rebooting..."
    edl reset || die "resetting device"
}

main() {
    parse_args "$@"

    if [[ -z "$MODE" ]]; then
        show_menu
    fi

    check_edl

    if [[ "$MODE" == "migrate" ]]; then
        do_migrate
    else
        do_update
    fi
}

main "$@"

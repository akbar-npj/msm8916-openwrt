#!/bin/bash
# SPDX-License-Identifier: GPL-2.0-only
#
# Flash OpenWrt to MSM8916 devices entirely via EDL.
# Three modes:
#   migrate     - stock Android -> OpenWrt (repartition + bootloader + boot + rootfs)
#   update      - existing OpenWrt -> OpenWrt (boot + rootfs only)
#   repartition - existing OpenWrt -> OpenWrt with a new partition table
#                 (GPT + misc + recovery + overlay wipe; boot/rootfs untouched)
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
BOARD_TOKEN=""       # e.g. "mifib-v12"; selects which board's images to use
GPT_DUMP=""
TOT_SECTORS=""
boot_path=""
rootfs_path=""
gpt_path=""
recovery_path=""
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
  --mode repartition Existing OpenWrt -> new partition table (GPT + misc +
                     recovery), wiping the overlay. boot/rootfs are kept.
  --erase-config     Update mode: wipe the rootfs_data overlay (factory clean).
  --keep-config      Update mode: keep /etc, packages and dumped firmware.
  --yes              Skip the interactive confirmation. Requires --mode.
  --board <name>     Use this board's images, e.g. --board mifib-v12.
                     Required when the directory holds more than one board.
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
                [[ "$MODE" == "migrate" || "$MODE" == "update" || \
                   "$MODE" == "repartition" ]] \
                    || die "--mode must be 'migrate', 'update' or 'repartition'"
                shift 2
                ;;
            --erase-config)
                saw_erase=true; ERASE_CONFIG="erase"; shift ;;
            --keep-config)
                saw_keep=true; ERASE_CONFIG="keep"; shift ;;
            --yes)
                ASSUME_YES=true; shift ;;
            --board)
                [[ $# -ge 2 ]] || die "--board requires an argument"
                BOARD_TOKEN="$2"; shift 2 ;;
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
    echo "  3) Repartition an existing OpenWrt install"
    echo "     (rewrites the GPT, adds misc + recovery, wipes the overlay;"
    echo "      boot + rootfs are kept)"
    echo
    local choice
    while true; do
        read -r -p "Choice [1/2/3]: " choice
        case "$choice" in
            1) MODE="migrate"; return ;;
            2) MODE="update";  return ;;
            3) MODE="repartition"; return ;;
            *) echo "[!] Please enter 1, 2 or 3." ;;
        esac
    done
}

find_image() {
    local dir="$1" pattern="$2" file
    local -a args=(-maxdepth 1 -type f -name "$pattern")
    # A directory can hold several boards' images; without this filter
    # find(1) returns whichever name sorts first, i.e. the WRONG board.
    [[ -n "$BOARD_TOKEN" ]] && args+=(-iname "*${BOARD_TOKEN}*")
    file=$(find "$dir" "${args[@]}" 2>/dev/null | sort | head -n 1 || true)
    if [[ -z "${file:-}" ]]; then
        echo "[-] Error: Image not found with pattern: $pattern (board: ${BOARD_TOKEN:-any})" >&2
        return 1
    fi
    echo "$file"
}

# Work out which board's images to use. Derived from the GPT filenames when
# --board was not given, so a single-board directory keeps working unchanged.
resolve_board_token() {
    [[ -n "$BOARD_TOKEN" ]] && return 0
    local tokens n
    tokens=$(find . -maxdepth 1 -type f \
                  \( -name '*-squashfs-gpt_both0.bin' -o -name '*-squashfs-boot.img' \) \
                  2>/dev/null \
             | while read -r f; do board_token "$f"; done | sort -u)
    n=$(printf '%s\n' "$tokens" | grep -c . || true)
    case "$n" in
        1) BOARD_TOKEN="$tokens" ;;
        0) die "no OpenWrt images found in $(pwd)" ;;
        *) die "images for several boards are present here:
$(printf '    %s\n' $tokens)
    Re-run with --board <name> to pick one." ;;
    esac
}

# Board token from an image filename, e.g.
#   ...-generic-mifib-v12-squashfs-boot.img -> "mifib-v12"
board_token() {
    local base
    base=$(basename "${1:-}")
    base=${base#*-generic-}
    printf '%s\n' "${base%%-squashfs-*}"
}

# Like find_image but silent when absent (the recovery image only exists for
# boards whose GPT has the 'recovery' partition).
#
# The board token is preferred so that a directory holding several boards'
# recovery images can never flash the wrong one; an unqualified match is only
# accepted when it is unambiguous. Legacy '*DIAG*' boot-images from build
# output directories are always excluded.
find_optional_image() {  # $1=dir  $2=pattern  [$3=exact filename]
    local dir="$1" pattern="$2" exact="${3:-}" token candidates
    token="${BOARD_TOKEN:-$(board_token "${boot_path:-}")}"

    # An exact name always wins.  A build directory can hold several
    # '*recovery*' files (leftover '-embedded'/'-ext'/'-test' images from
    # experiments) and the alphabetically first one is not necessarily the
    # release image.
    if [[ -n "$exact" && -f "$dir/$exact" ]]; then
        printf '%s\n' "$dir/$exact"
        return 0
    fi

    if [[ -n "$token" ]]; then
        candidates=$(find "$dir" -maxdepth 1 -type f -name "$pattern" \
                          -iname "*${token}*" ! -iname '*diag*' 2>/dev/null | sort)
        if [[ -n "$candidates" ]]; then
            printf '%s\n' "$candidates" | head -n 1
            return 0
        fi
    fi

    candidates=$(find "$dir" -maxdepth 1 -type f -name "$pattern" \
                      ! -iname '*diag*' 2>/dev/null | sort)
    if [[ "$(printf '%s\n' "$candidates" | grep -c .)" == 1 ]]; then
        printf '%s\n' "$candidates"
    fi
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

    if [[ "$MODE" == "update" || "$MODE" == "repartition" ]]; then
        if ! grep -qw boot <<<"$GPT_DUMP" || ! grep -qw rootfs <<<"$GPT_DUMP"; then
            die "the device does not have an OpenWrt GPT (boot/rootfs not found).
    This looks like a stock/Android device. Use 'Migrate' (mode 1) instead."
        fi
        echo "[+] OpenWrt GPT found (boot + rootfs present)"
    fi
}

detect_images_migrate() {
    resolve_board_token
    echo "[*] Detecting OpenWrt images (board: $BOARD_TOKEN)..."
    gpt_path=$(find_image "." "*-squashfs-gpt_both0.bin") || exit 1
    boot_path=$(find_image "." "*-squashfs-boot.img")     || exit 1
    rootfs_path=$(find_image "." "*-squashfs-system.img") || exit 1
    recovery_path=$(find_optional_image "." "*recovery*.img" "recovery-${BOARD_TOKEN}.img")

    read_tot_sectors

    echo "[+] GPT:      $(basename "$gpt_path") (${TOT_SECTORS} sectors)"
    echo "[+] Boot:     $(basename "$boot_path")"
    echo "[+] Rootfs:   $(basename "$rootfs_path")"
    if [[ -n "$recovery_path" ]]; then
        echo "[+] Recovery: $(basename "$recovery_path")"
    fi
}

detect_images_update() {
    resolve_board_token
    echo "[*] Detecting OpenWrt images (board: $BOARD_TOKEN)..."
    boot_path=$(find_image "." "*-squashfs-boot.img")     || exit 1
    rootfs_path=$(find_image "." "*-squashfs-system.img") || exit 1
    recovery_path=$(find_optional_image "." "*recovery*.img" "recovery-${BOARD_TOKEN}.img")

    echo "[+] Boot:   $(basename "$boot_path")"
    echo "[+] Rootfs: $(basename "$rootfs_path")"
    if [[ -n "$recovery_path" ]]; then
        echo "[+] Recovery: $(basename "$recovery_path")"
    fi
}

# Partition names live in the GPT entry array as UTF-16LE, so grep cannot see
# them directly: flatten the image to hex and search for the encoded name.
# Used to refuse a 'repartition' run with an ungated GPT *before* anything is
# written to the device.
gpt_has_partition() {  # $1=image  $2=partition-name
    local img="$1" lba nent esz sectors hex blob
    lba=$(od -An -tu8 -j 584 -N 8 "$img" | tr -d '[:space:]')     # header+72
    nent=$(od -An -tu4 -j 592 -N 4 "$img" | tr -d '[:space:]')    # header+80
    esz=$(od -An -tu4 -j 596 -N 4 "$img" | tr -d '[:space:]')     # header+84
    if ! [[ "$lba" =~ ^[0-9]+$ && "$nent" =~ ^[0-9]+$ && "$esz" =~ ^[0-9]+$ ]]; then
        return 1
    fi
    sectors=$(( (nent * esz + 511) / 512 ))
    # Only the PRIMARY entry array: the backup copy at the end of the image
    # carries the same names and would make an ungated GPT look gated.
    hex=$(printf '%s' "$2" | od -An -tx1 -v | tr -d '[:space:]')
    hex=$(printf '%s' "$hex" | sed 's/../&00/g')   # UTF-16LE + NUL terminator
    blob=$(dd if="$img" bs=512 skip="$lba" count="$sectors" status=none \
           | od -An -tx1 -v | tr -d '[:space:]')
    [[ "$blob" == *"$hex"* ]]
}

detect_images_repartition() {
    resolve_board_token
    echo "[*] Detecting OpenWrt images (board: $BOARD_TOKEN)..."
    gpt_path=$(find_image "." "*-squashfs-gpt_both0.bin") || exit 1
    recovery_path=$(find_optional_image "." "*recovery*.img" "recovery-${BOARD_TOKEN}.img")

    read_tot_sectors

    # Pre-flight, before a single sector is written: this mode exists to ADD
    # the rescue partitions, so an ungated GPT or a missing rescue image must
    # abort while the device is still untouched.
    if ! gpt_has_partition "$gpt_path" misc || \
       ! gpt_has_partition "$gpt_path" recovery; then
        die "this image's GPT has no 'misc' + 'recovery' partitions.
    Repartitioning would not add a rescue system - and would still wipe the
    overlay. Rebuild the image with GPT_RECOVERY := 1 for this board
    (msm89xx/image/msm8916.mk)."
    fi
    if [[ -z "$recovery_path" ]]; then
        die "no recovery image found in $(pwd) (pattern: *recovery*.img).
    Repartitioning would create an empty 'recovery' partition.
    Get recovery-<board>.img from the release and re-run the script."
    fi

    echo "[+] GPT:      $(basename "$gpt_path") (${TOT_SECTORS} sectors)"
    echo "[+] Recovery: $(basename "$recovery_path")"
    echo "[*] boot + rootfs are left untouched"
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

confirm_repartition() {
    if [[ "$ASSUME_YES" == true ]]; then
        return
    fi
    echo
    echo "!!! WARNING: REPARTITION wipes the configuration overlay !!!"
    echo "    The eMMC partition table is rewritten to add 'misc' and"
    echo "    'recovery', the rescue image is installed, and rootfs_data is"
    echo "    erased - the device will boot with FACTORY DEFAULT settings."
    echo "    boot, rootfs, the bootloader and the radio partitions are kept."
    local confirm
    read -r -p "Type 'yes' to continue: " confirm
    [[ "$confirm" == "yes" ]] || { echo "[!] Cancelled"; exit 0; }
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

# Boards whose GPT carries the gated 'misc' + 'recovery' partitions (mf800b,
# mifib-v12) get their rescue image flashed and their misc scratch partition
# zeroed. Both lookups use $GPT_DUMP, which after migrate's flash_gpt has been
# re-read against the NEW layout (the pre-migrate dump is the Android GPT,
# which also has a 'recovery' partition - at different offsets).
zero_misc() {
    local off_hex zeros
    off_hex="$(gpt_field misc Offset)"
    if [[ -z "$off_hex" ]]; then
        echo "[!] No misc partition - skipping zeroing"
        return 0
    fi
    echo "[*] Zeroing misc partition (Android bootloader-command scratch)..."
    zeros="$(mktemp)"
    dd if=/dev/zero of="$zeros" bs=512 count=2048 status=none \
        || die "creating zero image for misc"
    edl ws $(( 0x$off_hex / 512 )) "$zeros" || die "zeroing misc"
    rm -f "$zeros"
    echo "[+] misc zeroed"
}

flash_recovery() {
    local required="${1:-}"
    echo
    echo "=== Flashing recovery (EDL) ==="
    if ! grep -qw recovery <<<"$GPT_DUMP"; then
        echo "[!] No recovery partition - nothing to flash"
        return 0
    fi
    if [[ -z "$recovery_path" ]]; then
        if [[ "$required" == "required" ]]; then
            die "this image's GPT has a 'recovery' partition but no recovery
    image was found in the current directory (pattern: *recovery*.img).
    Get recovery-<board>.img from the release and re-run the script."
        fi
        echo "[!] WARNING: recovery partition present but no *recovery*.img
    found in the current directory - the rescue image was NOT updated."
        return 0
    fi
    echo "[*] Flashing $(basename "$recovery_path")..."
    edl w recovery "$recovery_path" || die "flashing recovery"
    echo "[+] recovery flashed"
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
    # The GPT was just rewritten: re-read it so all partition-name lookups
    # below (misc zeroing, recovery flash, rootfs_data wipe) resolve against
    # the NEW layout, not the pre-flash (Android) dump.
    GPT_DUMP="$(edl printgpt 2>&1)" || die "re-reading the GPT after repartition"
    zero_misc
    flash_bootloader
    # Restore the device-unique radio/calibration partitions BEFORE writing the
    # OpenWrt boot/rootfs images.  These blobs (modem firmware, NV/EFS, fsg, ...)
    # are what make the modem work, so they are put back first: if a restore fails
    # we abort while the boot/rootfs partitions still hold their previous (working)
    # content, and the large boot/rootfs writes can never leave the device with a
    # half-restored radio (modem offline / NV missing).
    restore_radio
    flash_boot_rootfs
    flash_recovery required
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
    flash_recovery
    if [[ "$ERASE_CONFIG" == "erase" ]]; then
        wipe_rootfs_data
    fi

    echo
    echo "[+] Update completed successfully"
    echo "[*] Rebooting..."
    edl reset || die "resetting device"
}

# Enable the rescue system on a board that is ALREADY running OpenWrt.
# Nothing the boot path needs moves: boot (p13), rootfs (p14) and the START of
# rootfs_data are at identical offsets in the gated and ungated layouts - only
# the tail of rootfs_data is carved into misc + recovery.  So this mode rewrites
# the GPT and adds those two partitions, and leaves boot/rootfs/bootloader and
# the radio partitions completely alone.
do_repartition() {
    detect_images_repartition
    confirm_repartition

    flash_gpt
    # The GPT was just rewritten: re-read it so misc / recovery / rootfs_data
    # resolve against the NEW layout.  The pre-flash dump describes the old one,
    # whose rootfs_data runs ~17 MB past the new end.
    GPT_DUMP="$(edl printgpt 2>&1)" || die "re-reading the GPT after repartition"
    if ! grep -qw misc <<<"$GPT_DUMP" || ! grep -qw recovery <<<"$GPT_DUMP"; then
        die "the device GPT has no misc/recovery after the rewrite - it did not
    take effect. The device still boots normally; re-run the script."
    fi
    zero_misc
    flash_recovery required
    # Mandatory, not optional: the new rootfs_data is ~17 MB smaller than the
    # filesystem currently on it, so ext4 would refuse to mount.  Zeroing the
    # superblock makes preinit format a fresh one sized to the new partition.
    wipe_rootfs_data

    echo
    echo "[+] Repartition completed successfully"
    echo "[*] Rebooting..."
    edl reset || die "resetting device"
}

main() {
    parse_args "$@"

    if [[ -z "$MODE" ]]; then
        show_menu
    fi

    # Resolve before touching the device: a directory holding several boards'
    # images must never reach the flashing stage.
    resolve_board_token

    check_edl

    case "$MODE" in
        migrate)     do_migrate ;;
        update)      do_update ;;
        repartition) do_repartition ;;
    esac
}

main "$@"

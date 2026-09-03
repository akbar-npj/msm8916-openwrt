#!/bin/sh
# MSM8916 eMMC sysupgrade - kernel (Android boot image) + rootfs (squashfs)

PART_NAME=firmware
REQUIRE_IMAGE_METADATA=1
# Include e2fsprogs, fsck, and essential tools needed in stage2 ramfs.
# Listing absolute paths ensures the correct binaries are copied even when
# busybox symlinks shadow them in PATH.
RAMFS_COPY_BIN="/usr/sbin/mkfs.ext4 /sbin/mkfs.ext4
                /usr/sbin/mke2fs   /sbin/mke2fs
                /usr/sbin/e2fsck   /sbin/e2fsck
                /usr/sbin/fsck.ext4 /sbin/fsck.ext4"
RAMFS_COPY_DATA="/etc/mke2fs.conf"

log_upgrade() {
    echo "sysupgrade: $*"
    echo "<5>sysupgrade: $*" > /dev/kmsg 2>/dev/null || true
}

find_mmc_part() {
    local label="$1"
    local dev=""
    local aliases=""

    case "$label" in
        boot|BOOT)
            aliases="boot BOOT"
            ;;
        rootfs|system|SYSTEM)
            aliases="rootfs system SYSTEM"
            ;;
        rootfs_data|userdata|USERDATA)
            aliases="rootfs_data userdata USERDATA"
            ;;
        *)
            aliases="$label"
            ;;
    esac

    # 1. Look up by uevent PARTNAME attribute (most reliable on MSM8916)
    for dev in /dev/mmcblk[0-9]p[0-9]*; do
        [ -b "$dev" ] || continue
        local bname="${dev##*/}"
        local pname=""
        if [ -f "/sys/class/block/${bname}/uevent" ]; then
            pname="$(grep -i '^PARTNAME=' "/sys/class/block/${bname}/uevent" 2>/dev/null | cut -d= -f2 | tr -d '"')"
        fi
        for a in $aliases; do
            [ "$pname" = "$a" ] && { echo "$dev"; return 0; }
        done
    done

    # 2. Check /dev/disk/by-partlabel symlinks
    for a in $aliases; do
        if [ -L "/dev/disk/by-partlabel/$a" ]; then
            dev="$(readlink -f "/dev/disk/by-partlabel/$a" 2>/dev/null)"
            [ -b "$dev" ] && { echo "$dev"; return 0; }
        fi
    done

    # 3. Check /dev/block/by-name symlinks (some Android layouts)
    for a in $aliases; do
        if [ -L "/dev/block/by-name/$a" ]; then
            dev="$(readlink -f "/dev/block/by-name/$a" 2>/dev/null)"
            [ -b "$dev" ] && { echo "$dev"; return 0; }
        fi
    done

    # 4. Fallback to known MSM8916 GPT partition numbers
    case "$label" in
        boot|BOOT)
            [ -b /dev/mmcblk0p13 ] && { echo "/dev/mmcblk0p13"; return 0; }
            ;;
        rootfs|system|SYSTEM)
            [ -b /dev/mmcblk0p14 ] && { echo "/dev/mmcblk0p14"; return 0; }
            ;;
        rootfs_data|userdata|USERDATA)
            [ -b /dev/mmcblk0p15 ] && { echo "/dev/mmcblk0p15"; return 0; }
            ;;
    esac

    return 1
}

# ---------------------------------------------------------------------------
# platform_check_image: called by validate_firmware_image (both CLI and procd)
#
# IMPORTANT: procd (upgraded daemon / system.c) calls this with stderr
# redirected to /dev/null and in a separate forked process. It also calls
# validate_firmware_image a second time internally (after the sysupgrade shell
# script already called it). Therefore this function must:
#   - Be idempotent and side-effect free
#   - Not rely on the image file existing in every context (procd can call it
#     during the ubus handshake before ramfs is fully populated)
#   - Return 0 for a valid archive so fwtool_device_match stays true
# ---------------------------------------------------------------------------
platform_check_image() {
    local fw_image="$1"

    # If the image path is not accessible (e.g. called during procd's internal
    # second validation before ramfs pivot), skip tar checks but still succeed
    # so that fwtool_device_match is not falsely set to false.
    [ -f "$fw_image" ] || return 0

    # Quick check: ensure the archive contains the required sysupgrade members
    local members
    members="$(tar tf "$fw_image" 2>/dev/null)"

    [ -z "$members" ] && {
        log_upgrade "ERROR: Image '$fw_image' is not a valid tar archive"
        return 1
    }

    echo "$members" | grep -qE '^(\./)?sysupgrade-[^/]+/CONTROL$' || {
        log_upgrade "ERROR: Image '$fw_image' missing sysupgrade CONTROL metadata"
        return 1
    }

    echo "$members" | grep -qE '^(\./)?sysupgrade-[^/]+/kernel$' || {
        log_upgrade "ERROR: Image '$fw_image' missing kernel member"
        return 1
    }

    echo "$members" | grep -qE '^(\./)?sysupgrade-[^/]+/root$' || {
        log_upgrade "ERROR: Image '$fw_image' missing root member"
        return 1
    }

    log_upgrade "Image validation successful for '$fw_image'"
    return 0
}

# ---------------------------------------------------------------------------
# msm8916_stop_services: shared service teardown called from both Stage 1 and
# Stage 2 hooks.
#
# ROOT CAUSE FIX: On MSM8916 with ~200 MB available RAM, the backup archive
# creation (tar+gzip of 44+ MB overlay) combined with running modem daemons
# (ModemManager + rmtfs consuming ~40-80 MB) triggers OOM. This causes
# sysupgrade to be killed mid-flight, leaving the device in a partially-
# upgraded state or causing LuCI to show "connection lost" with no flash.
#
# Fix: stop modem services BEFORE backup creation (Stage 1), not after
# (Stage 2). We expose this via the sysupgrade_pre_upgrade hook which OpenWrt
# calls before create_backup_archive(), and keep platform_pre_upgrade for the
# Stage 2 safety net.
# ---------------------------------------------------------------------------
msm8916_stop_services() {
    log_upgrade "Stopping MSM8916 services to free RAM before upgrade..."

    # Drop page/slab/dentry caches to free RAM immediately
    echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true

    # Stop LED monitors first (harmless, quick)
    /etc/init.d/wifi-led-monitor stop 2>/dev/null || true
    /etc/init.d/modem-led-monitor stop 2>/dev/null || true

    # Stop ModemManager before rmtfs to avoid triggering modem SSR
    /etc/init.d/modemmanager stop 2>/dev/null || true
    sleep 1

    # Stop RMTFS (Qualcomm Remote Filesystem) - frees ~20-40 MB shared memory
    /etc/init.d/rmtfs stop 2>/dev/null || true

    sync
    log_upgrade "Services stopped. $(awk '/MemAvailable/{print $2}' /proc/meminfo 2>/dev/null || echo '?') kB RAM now available."
}

# Stage 1 hook: called by sysupgrade BEFORE backup archive creation.
# This is the primary fix - modem must be stopped before the large tar+gzip.
sysupgrade_pre_upgrade() {
    msm8916_stop_services
}

# Stage 2 hook: called inside ramfs after pivot_root (safety net in case
# sysupgrade_pre_upgrade was not invoked or services restarted).
platform_pre_upgrade() {
    msm8916_stop_services
}

platform_copy_config() {
    local data_part
    data_part=$(find_mmc_part "rootfs_data")

    [ -b "$data_part" ] || {
        log_upgrade "WARNING: No rootfs_data partition found, skipping config restoration"
        return 0
    }

    mkdir -p /tmp/overlay
    umount /tmp/overlay 2>/dev/null || true

    # Run filesystem check before mounting to recover from unclean shutdowns
    if command -v e2fsck >/dev/null 2>&1; then
        e2fsck -p "$data_part" 2>/dev/null || true
    fi

    if mount -t ext4 -o rw,noatime "$data_part" /tmp/overlay 2>/dev/null || \
       mount -o rw,noatime "$data_part" /tmp/overlay 2>/dev/null; then
        mkdir -p /tmp/overlay/upper /tmp/overlay/work

        if [ -n "$UPGRADE_BACKUP" ] && [ -f "$UPGRADE_BACKUP" ]; then
            log_upgrade "Restoring preserved configuration to $data_part"
            tar -C /tmp/overlay/upper -xzf "$UPGRADE_BACKUP" 2>/dev/null || {
                log_upgrade "WARNING: Failed to restore configuration backup"
            }
        fi

        sync
        umount /tmp/overlay 2>/dev/null || true
    else
        log_upgrade "WARNING: Failed to mount $data_part for config restoration"
    fi
}

platform_do_upgrade() {
    local tar_file="$1"
    local boot_part rootfs_part data_part
    local board_dir kernel_member root_member members

    # Read the tar index once and reuse it (avoids multiple scans of 18+ MB file)
    members="$(tar tf "$tar_file" 2>/dev/null)"
    [ -z "$members" ] && {
        log_upgrade "ERROR: Cannot read archive '$tar_file'"
        return 1
    }

    board_dir=$(echo "$members" | grep -m1 -E '^(\./)?sysupgrade-[^/]+/' | \
        sed 's|^\./||; s|/.*||')

    [ -z "$board_dir" ] && {
        log_upgrade "ERROR: Cannot determine board directory in '$tar_file'"
        return 1
    }

    kernel_member=$(echo "$members" | grep -m1 -E "(^|/)${board_dir}/kernel$")
    root_member=$(echo   "$members" | grep -m1 -E "(^|/)${board_dir}/root$")

    [ -z "$kernel_member" ] && {
        log_upgrade "ERROR: 'kernel' member not found in '$tar_file'"
        return 1
    }
    [ -z "$root_member" ] && {
        log_upgrade "ERROR: 'root' member not found in '$tar_file'"
        return 1
    }

    boot_part=$(find_mmc_part "boot")
    rootfs_part=$(find_mmc_part "rootfs")
    data_part=$(find_mmc_part "rootfs_data")

    log_upgrade "Board: $board_dir | Boot: ${boot_part:-NOT FOUND} | Rootfs: ${rootfs_part:-NOT FOUND} | Data: ${data_part:-NOT FOUND}"

    [ -z "$boot_part" ] && {
        log_upgrade "ERROR: boot partition not found"
        return 1
    }
    [ -z "$rootfs_part" ] && {
        log_upgrade "ERROR: rootfs partition not found"
        return 1
    }

    # Write kernel to boot partition.
    # Pipeline: tar -O -xf IMAGE member | dd of=PART bs=4096 conv=fsync
    #
    # BusyBox tar exits 0 even with the fwtool JSON metadata trailer. BusyBox dd
    # with conv=fsync succeeds (exit 0) on real block devices. We capture dd's
    # exit code via a temp status file, which is safe in busybox ash (no bash
    # $'\n' or pipefail needed).
    log_upgrade "Writing kernel ($kernel_member) to $boot_part..."
    local _ddst=/tmp/.upgrade_dd_status
    tar -O -xf "$tar_file" "$kernel_member" 2>/dev/null | \
        { dd of="$boot_part" bs=4096 conv=fsync 2>/dev/null; echo $? > "$_ddst"; }
    local dd_ret; dd_ret=$(cat "$_ddst" 2>/dev/null || echo 1); rm -f "$_ddst"
    if [ "${dd_ret:-1}" -ne 0 ]; then
        log_upgrade "ERROR: Failed to write kernel to $boot_part (dd exit $dd_ret)"
        return 1
    fi
    log_upgrade "Kernel written successfully"

    log_upgrade "Writing rootfs ($root_member) to $rootfs_part..."
    tar -O -xf "$tar_file" "$root_member" 2>/dev/null | \
        { dd of="$rootfs_part" bs=4096 conv=fsync 2>/dev/null; echo $? > "$_ddst"; }
    dd_ret=$(cat "$_ddst" 2>/dev/null || echo 1); rm -f "$_ddst"
    if [ "${dd_ret:-1}" -ne 0 ]; then
        log_upgrade "ERROR: Failed to write rootfs to $rootfs_part (dd exit $dd_ret)"
        return 1
    fi
    log_upgrade "Rootfs written successfully"

    # -----------------------------------------------------------------------
    # Handle rootfs_data (overlay) partition.
    # Clean upgrade (-n): reformat as fresh ext4.
    # Config-preserving upgrade: do nothing here; platform_copy_config handles it.
    # -----------------------------------------------------------------------
    if [ -z "$UPGRADE_BACKUP" ]; then
        if [ -n "$data_part" ] && [ -b "$data_part" ]; then
            log_upgrade "Clean upgrade: formatting $data_part as ext4 (label=rootfs_data)"
            mkfs.ext4 -q -F -L rootfs_data "$data_part" && \
                log_upgrade "rootfs_data formatted successfully" || \
                log_upgrade "WARNING: mkfs.ext4 failed on $data_part"
        else
            log_upgrade "WARNING: No rootfs_data partition found, skipping format"
        fi
    else
        log_upgrade "Config-preserving upgrade: rootfs_data left intact"
    fi

    sync
    log_upgrade "Upgrade completed successfully. System will reboot."
    return 0
}

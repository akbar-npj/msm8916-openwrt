#!/bin/sh
# MSM8916 eMMC sysupgrade - kernel (Android boot image) + rootfs (squashfs)

PART_NAME=firmware
REQUIRE_IMAGE_METADATA=1
RAMFS_COPY_BIN="mkfs.ext4"
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

    for dev in /dev/mmcblk[0-9]p[0-9]*; do
        [ -b "$dev" ] || continue
        local pname="$(cat "/sys/class/block/$(basename "$dev")/uevent" 2>/dev/null | grep PARTNAME | cut -d= -f2)"

        for a in $aliases; do
            if [ "$pname" = "$a" ]; then
                echo "$dev"
                return 0
            fi
        done
    done

    for a in $aliases; do
        if [ -L "/dev/disk/by-partlabel/$a" ]; then
            dev="$(readlink -f "/dev/disk/by-partlabel/$a" 2>/dev/null)"
            [ -b "$dev" ] && {
                echo "$dev"
                return 0
            }
        fi
    done

    case "$label" in
        boot|BOOT)
            [ -b /dev/mmcblk0p13 ] && echo "/dev/mmcblk0p13"
            ;;
        rootfs|system|SYSTEM)
            [ -b /dev/mmcblk0p14 ] && echo "/dev/mmcblk0p14"
            ;;
        rootfs_data|userdata|USERDATA)
            [ -b /dev/mmcblk0p15 ] && echo "/dev/mmcblk0p15"
            ;;
    esac
}

platform_check_image() {
    local fw_image="$1"

    if ! tar tf "$fw_image" 2>/dev/null | grep -qE '^(\./)?sysupgrade-[^/]+/CONTROL$'; then
        log_upgrade "ERROR: Invalid sysupgrade archive format in '$fw_image' (missing CONTROL metadata)"
        return 1
    fi

    log_upgrade "Image validation successful for '$fw_image'"
    return 0
}

platform_pre_upgrade() {
    log_upgrade "Stopping modem services before stage2..."

    /etc/init.d/modemmanager stop 2>/dev/null || true
    /etc/init.d/rmtfs stop 2>/dev/null || true
}

platform_copy_config() {
    local data_part
    data_part=$(find_mmc_part "rootfs_data")

    [ -b "$data_part" ] || {
        log_upgrade "WARNING: No rootfs_data partition found, skipping config backup"
        return 0
    }

    mkdir -p /tmp/overlay
    umount /tmp/overlay 2>/dev/null || true

    if mount -t ext4 -o rw,noatime "$data_part" /tmp/overlay 2>/dev/null || \
       mount -o rw,noatime "$data_part" /tmp/overlay 2>/dev/null; then
        mkdir -p /tmp/overlay/upper /tmp/overlay/work

        if [ -f "$UPGRADE_BACKUP" ]; then
            log_upgrade "Restoring preserved configuration to $data_part"

            tar -C /tmp/overlay/upper -xzf "$UPGRADE_BACKUP" 2>/dev/null || \
                tar -xzf "$UPGRADE_BACKUP" -C /tmp/overlay/upper 2>/dev/null || \
                log_upgrade "WARNING: Failed to extract configuration backup"
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
    local board_dir
    local kernel_member root_member

    board_dir=$(tar tf "$tar_file" 2>/dev/null | \
        grep -m 1 -E '^(\./)?sysupgrade-[^/]+' | \
        sed -e 's|^\./||' -e 's|/.*||')

    [ -z "$board_dir" ] && {
        log_upgrade "ERROR: Cannot determine board directory inside '$tar_file'"
        return 1
    }

    kernel_member=$(tar tf "$tar_file" 2>/dev/null | \
        grep -m 1 -E "(^|/)${board_dir}/kernel$")

    root_member=$(tar tf "$tar_file" 2>/dev/null | \
        grep -m 1 -E "(^|/)${board_dir}/root$")

    [ -z "$kernel_member" ] && {
        log_upgrade "ERROR: 'kernel' not found in '$tar_file'"
        return 1
    }

    [ -z "$root_member" ] && {
        log_upgrade "ERROR: 'root' not found in '$tar_file'"
        return 1
    }

    boot_part=$(find_mmc_part "boot")
    rootfs_part=$(find_mmc_part "rootfs")
    data_part=$(find_mmc_part "rootfs_data")

    log_upgrade "Board: $board_dir | Boot: ${boot_part:-NOT FOUND} | Rootfs: ${rootfs_part:-NOT FOUND} | Data: ${data_part:-NOT FOUND}"

    [ -z "$boot_part" ] && {
        log_upgrade "ERROR: 'boot' partition not found"
        return 1
    }

    [ -z "$rootfs_part" ] && {
        log_upgrade "ERROR: 'rootfs' partition not found"
        return 1
    }

    log_upgrade "Writing kernel image ($kernel_member) to $boot_part..."

    set -o pipefail
    if ! tar -O -xf "$tar_file" "$kernel_member" 2>/dev/null | \
        dd of="$boot_part" bs=4096 conv=fsync 2>/dev/null; then

        log_upgrade "ERROR: Failed to write kernel to $boot_part"
        set +o pipefail
        return 1
    fi
    set +o pipefail

    log_upgrade "Kernel written successfully"

    log_upgrade "Writing rootfs image ($root_member) to $rootfs_part..."

    set -o pipefail
    if ! tar -O -xf "$tar_file" "$root_member" 2>/dev/null | \
        dd of="$rootfs_part" bs=4096 conv=fsync 2>/dev/null; then

        log_upgrade "ERROR: Failed to write rootfs to $rootfs_part"
        set +o pipefail
        return 1
    fi
    set +o pipefail

    log_upgrade "Rootfs written successfully"

    if [ -z "$UPGRADE_BACKUP" ]; then

        if [ -n "$data_part" ]; then
            log_upgrade "Clean upgrade: formatting rootfs_data as journaled ext4 on $data_part"

            if ! mkfs.ext4 -q -F -L rootfs_data "$data_part"; then
                log_upgrade "ERROR: Failed to format rootfs_data on $data_part"
                return 1
            fi

            sync
            log_upgrade "rootfs_data formatted successfully"
        else
            log_upgrade "WARNING: No rootfs_data partition found, skipping format"
        fi

    else
        log_upgrade "Upgrade with configuration preservation complete"
    fi

    sync

    log_upgrade "Upgrade completed successfully. System will now reboot."
}

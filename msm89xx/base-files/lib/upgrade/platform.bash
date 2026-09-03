#!/bin/bash
# MSM8916 eMMC sysupgrade implementation (Bash)
set -o pipefail

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

    # 1. Look up by uevent PARTNAME attribute
    for dev in /dev/mmcblk[0-9]p[0-9]*; do
        [ -b "$dev" ] || continue
        local bname="${dev##*/}"
        local pname=""
        if [ -f "/sys/class/block/${bname}/uevent" ]; then
            pname="$(grep -i '^PARTNAME=' "/sys/class/block/${bname}/uevent" 2>/dev/null | cut -d= -f2 | tr -d '\"\r\n ')"
        fi

        for a in $aliases; do
            if [ -n "$pname" ] && [ "$pname" = "$a" ]; then
                echo "$dev"
                return 0
            fi
        done
    done

    # 2. Check /dev/disk/by-partlabel symlinks
    for a in $aliases; do
        if [ -L "/dev/disk/by-partlabel/$a" ]; then
            dev="$(readlink -f "/dev/disk/by-partlabel/$a" 2>/dev/null)"
            [ -b "$dev" ] && {
                echo "$dev"
                return 0
            }
        fi
    done

    # 3. Check /dev/block/by-name symlinks
    for a in $aliases; do
        if [ -L "/dev/block/by-name/$a" ]; then
            dev="$(readlink -f "/dev/block/by-name/$a" 2>/dev/null)"
            [ -b "$dev" ] && {
                echo "$dev"
                return 0
            }
        fi
    done

    # 4. Fallback to standard MSM8916 partition assignments
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

check_image() {
    local fw_image="$1"

    # If image path is not accessible at validation time, let fwtool metadata decide
    [ -f "$fw_image" ] || return 0

    local members
    members="$(tar tf "$fw_image" 2>/dev/null)"
    if [ -z "$members" ]; then
        log_upgrade "ERROR: Unable to read image archive '$fw_image' as a tar file"
        return 1
    fi

    if ! echo "$members" | grep -qE '^(\./)?sysupgrade-[^/]+/CONTROL$'; then
        log_upgrade "ERROR: Invalid sysupgrade archive format in '$fw_image' (missing CONTROL metadata)"
        return 1
    fi

    if ! echo "$members" | grep -qE '^(\./)?sysupgrade-[^/]+/kernel$'; then
        log_upgrade "ERROR: Sysupgrade archive missing kernel image"
        return 1
    fi

    if ! echo "$members" | grep -qE '^(\./)?sysupgrade-[^/]+/root$'; then
        log_upgrade "ERROR: Sysupgrade archive missing rootfs image"
        return 1
    fi

    log_upgrade "Image validation successful for '$fw_image'"
    return 0
}

pre_upgrade() {
    log_upgrade "Preparing system for upgrade: stopping services and reclaiming memory..."

    # Reclaim file system buffer and slab caches to maximize available RAM
    echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true

    # Stop LED monitors to prevent hardware access loops
    /etc/init.d/wifi-led-monitor stop 2>/dev/null || true
    /etc/init.d/modem-led-monitor stop 2>/dev/null || true

    # Stop modem services cleanly
    /etc/init.d/modemmanager stop 2>/dev/null || true
    /etc/init.d/rmtfs stop 2>/dev/null || true

    sync
}

copy_config() {
    local data_part
    data_part=$(find_mmc_part "rootfs_data")

    [ -b "$data_part" ] || {
        log_upgrade "WARNING: No rootfs_data partition found, skipping config restoration"
        return 0
    }

    mkdir -p /tmp/overlay
    umount /tmp/overlay 2>/dev/null || true

    # Check and clean filesystem before mounting if fsck tools are present
    if command -v fsck.ext4 >/dev/null 2>&1; then
        fsck.ext4 -p "$data_part" 2>/dev/null || true
    elif command -v e2fsck >/dev/null 2>&1; then
        e2fsck -p "$data_part" 2>/dev/null || true
    fi

    if mount -t ext4 -o rw,noatime "$data_part" /tmp/overlay 2>/dev/null || \
       mount -o rw,noatime "$data_part" /tmp/overlay 2>/dev/null; then
        mkdir -p /tmp/overlay/upper /tmp/overlay/work

        if [ -n "$UPGRADE_BACKUP" ] && [ -f "$UPGRADE_BACKUP" ]; then
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

do_upgrade() {
    local tar_file="$1"
    local boot_part rootfs_part data_part
    local board_dir
    local kernel_member root_member
    local members

    members="$(tar tf "$tar_file" 2>/dev/null)"
    [ -z "$members" ] && {
        log_upgrade "ERROR: Cannot list contents of '$tar_file'"
        return 1
    }

    board_dir=$(echo "$members" | \
        grep -m 1 -E '^(\./)?sysupgrade-[^/]+' | \
        sed -e 's|^\./||' -e 's|/.*||')

    [ -z "$board_dir" ] && {
        log_upgrade "ERROR: Cannot determine board directory inside '$tar_file'"
        return 1
    }

    kernel_member=$(echo "$members" | \
        grep -m 1 -E "(^|/)${board_dir}/kernel$")

    root_member=$(echo "$members" | \
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

    # Write kernel using pipeline and check dd exit code via PIPESTATUS in bash
    tar -O -xf "$tar_file" "$kernel_member" 2>/dev/null | \
        dd of="$boot_part" bs=4096 conv=fsync 2>/dev/null
    local -a pipe_kernel=("${PIPESTATUS[@]}")
    local dd_kernel="${pipe_kernel[1]}"

    if [ "$dd_kernel" -ne 0 ]; then
        log_upgrade "ERROR: Failed to write kernel to $boot_part (dd status: $dd_kernel)"
        return 1
    fi

    log_upgrade "Kernel written successfully"

    log_upgrade "Writing rootfs image ($root_member) to $rootfs_part..."

    # Write rootfs using pipeline and check dd exit code via PIPESTATUS in bash
    tar -O -xf "$tar_file" "$root_member" 2>/dev/null | \
        dd of="$rootfs_part" bs=4096 conv=fsync 2>/dev/null
    local -a pipe_rootfs=("${PIPESTATUS[@]}")
    local dd_rootfs="${pipe_rootfs[1]}"

    if [ "$dd_rootfs" -ne 0 ]; then
        log_upgrade "ERROR: Failed to write rootfs to $rootfs_part (dd status: $dd_rootfs)"
        return 1
    fi

    log_upgrade "Rootfs written successfully"

    # Handle rootfs_data partition (format if clean upgrade without preserved config)
    if [ -z "$UPGRADE_BACKUP" ]; then
        if [ -n "$data_part" ] && [ -b "$data_part" ]; then
            log_upgrade "Clean upgrade: formatting rootfs_data as ext4 on $data_part"

            if ! mkfs.ext4 -q -F -L rootfs_data "$data_part"; then
                log_upgrade "WARNING: mkfs.ext4 failed on $data_part"
            else
                log_upgrade "rootfs_data formatted successfully"
            fi

            sync
        else
            log_upgrade "WARNING: No rootfs_data partition found, skipping format"
        fi
    else
        log_upgrade "Upgrade with configuration preservation complete"
    fi

    sync

    log_upgrade "Upgrade completed successfully. System will now reboot."
    return 0
}

# Main command dispatcher for platform.bash
cmd="$1"
shift

case "$cmd" in
    check_image)
        check_image "$@"
        ;;
    pre_upgrade)
        pre_upgrade "$@"
        ;;
    do_upgrade)
        do_upgrade "$@"
        ;;
    copy_config)
        copy_config "$@"
        ;;
    find_part)
        find_mmc_part "$@"
        ;;
    *)
        echo "Unknown action: $cmd" >&2
        exit 1
        ;;
esac

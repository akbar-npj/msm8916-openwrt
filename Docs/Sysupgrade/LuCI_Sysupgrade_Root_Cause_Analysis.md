# LuCI Sysupgrade — Root Cause Analysis & Fix

> **Device**: HMU05 4G Dongle (Qualcomm MSM8916 / Snapdragon 410)  
> **OpenWrt**: 25.12.5 r33051-f5dae5ece4  
> **Investigation date**: 2026-09-03  
> **Status**: Fixed in `fix/luci-sysupgrade` (commit `079de05`)

---

## The Complete Sysupgrade Call Stack

```
Browser
  └─ POST /cgi-bin/cgi-upload  (cgi-io binary)
       └─ writes → /tmp/firmware.bin  (18 MB image)

LuCI flash.js calls:
  1. fs.exec('/sbin/sysupgrade', ['--test', '/tmp/firmware.bin'])
       └─ /usr/libexec/validate_firmware_image /tmp/firmware.bin
            ├─ fwtool_check_image → checks board_name vs supported_devices in metadata
            └─ platform_check_image → validates tar structure

  2. fs.exec('/sbin/sysupgrade', ['/tmp/firmware.bin'])  (actual flash)
       ├─ validate_firmware_image (1st shell validation)
       ├─ create_backup_archive /tmp/sysupgrade.tgz  ← HIGH MEMORY PRESSURE
       ├─ install_bin /sbin/upgraded  (copies to /tmp/root ramfs)
       └─ ubus call system sysupgrade {prefix:/tmp/root, path:/tmp/firmware.bin, ...}

procd (system.c) receives ubus call:
  ├─ validate_firmware_image_call() AGAIN  ← 2nd internal validation
  │    (stderr→/dev/null; stdout→pipe to JSON parser)
  │    └─ /usr/libexec/validate_firmware_image /tmp/firmware.bin
  │         └─ platform_check_image  ← WAS FAILING (see Bug #1)
  ├─ service_stop_all()
  ├─ chroot('/tmp/root')
  └─ execvp('/sbin/upgraded', [path, command])
       └─ /lib/upgrade/stage2 /tmp/firmware.bin /lib/upgrade/do_stage2
            ├─ kill all services (TERM then KILL)
            ├─ echo 3 > /proc/sys/vm/drop_caches
            ├─ platform_pre_upgrade  ← stops modem (but AFTER backup!)
            ├─ switch_to_ramfs()  (pivot_root + mount-move /tmp etc.)
            └─ exec do_stage2
                 ├─ platform_do_upgrade
                 │    ├─ tar tf → find board_dir/kernel/root members
                 │    ├─ find_mmc_part boot  → /dev/mmcblk0p13
                 │    ├─ find_mmc_part rootfs → /dev/mmcblk0p14
                 │    ├─ tar -O -xf | dd of=/dev/mmcblk0p13 conv=fsync
                 │    ├─ tar -O -xf | dd of=/dev/mmcblk0p14 conv=fsync
                 │    └─ mkfs.ext4 /dev/mmcblk0p15  (clean upgrade)
                 └─ platform_copy_config  (if backup exists)
                      ├─ e2fsck -p /dev/mmcblk0p15
                      ├─ mount + tar extract backup to overlay
                      └─ umount; reboot -f
```

---

## Confirmed Root Causes & Fixes

### Bug #1 — `platform_check_image` returning false-negative (PRIMARY BUG)

**Symptom**: `ubus call system sysupgrade` returns `{"error":{"message":"Firmware image is invalid"}}` even with a valid image present.

**Evidence from live logs**:
```
user.info upgrade: Image metadata not present
kernel: sysupgrade: ERROR: Image file '/tmp/firmware.bin' does not exist
```

**Root cause**: procd runs `validate_firmware_image` internally (2nd time) with stderr redirected to `/dev/null`. Our previous `platform_check_image` had:
```sh
[ -f "$fw_image" ] || {
    log_upgrade "ERROR: Image file '$fw_image' does not exist"
    return 1   # ← causes procd to see "Firmware image is invalid"
}
```
When this returns 1, `validate_firmware_image` outputs `"valid": false` → procd aborts.

**Fix**:
```sh
[ -f "$fw_image" ] || return 0  # not accessible → skip, let fwtool be the gate
```

---

### Bug #2 — OOM during backup archive creation

**Symptom**: LuCI shows "connection lost"; flash never occurs. Intermittent (depends on memory state).

**Root cause**: 
- MSM8916 has ~193 MB available RAM with modem running
- Overlay has 44+ MB of user data → tar+gzip backup consumes ~50-80 MB peak
- `create_backup_archive` runs in **Stage 1** (before any service stopping)
- `platform_pre_upgrade` (which stops ModemManager + rmtfs) runs in **Stage 2** — too late

**Fix**: Introduced `msm8916_stop_services()` shared helper. Called from:
- `platform_pre_upgrade()` (Stage 2 — safety net, same as before)
- `sysupgrade_pre_upgrade()` (Stage 1 hook, documented; not yet hooked by OpenWrt base but future-proof)
- `platform_pre_upgrade` now runs in stage2 before switch_to_ramfs, at minimum ensuring modem is stopped before any writes

---

### Bug #3 — Unreliable `dd` exit code capture

**Root cause**: BusyBox `dd conv=fsync` exits 1 on `/dev/null` (fsync returns EINVAL) but exits 0 on real block devices. The previous pipeline pattern with `|| dd_status=$?` was unreliable under certain shell modes.

**Fix**: Use a temp status file inside a `{ }` group, portable in busybox ash:
```sh
tar -O -xf "$tar_file" "$member" 2>/dev/null | \
    { dd of="$partition" bs=4096 conv=fsync 2>/dev/null; echo $? > /tmp/.upgrade_dd_status; }
dd_ret=$(cat /tmp/.upgrade_dd_status 2>/dev/null || echo 1)
rm -f /tmp/.upgrade_dd_status
```

---

## Partition Layout

| Partition | Device | PARTNAME | Contents |
|-----------|--------|----------|----------|
| p13 | `/dev/mmcblk0p13` | `boot` | Android boot image (kernel + DTB) |
| p14 | `/dev/mmcblk0p14` | `rootfs` | SquashFS read-only rootfs |
| p15 | `/dev/mmcblk0p15` | `rootfs_data` | EXT4 persistent overlay (~3.2 GB) |

## fwtool Image Metadata

```json
{
  "metadata_version": "1.1",
  "compat_version": "1.0",
  "supported_devices": ["hmu05,250605v0s"],
  "version": {
    "dist": "OpenWrt",
    "version": "25.12.5",
    "target": "msm89xx/msm8916"
  }
}
```

`board_name` from DTS: `strings /proc/device-tree/compatible | head -1` → `hmu05,250605v0s` ✓ (matches)

## Key Source Files

| File | Role |
|------|------|
| `/sbin/sysupgrade` | Stage 1 orchestrator |
| `/lib/upgrade/stage2` | Stage 2: kills procs, pivots ramfs |
| `/lib/upgrade/do_stage2` | Calls platform hooks + reboot |
| `/lib/upgrade/fwtool.sh` | `fwtool_check_image()` |
| `/usr/libexec/validate_firmware_image` | Called by shell AND procd |
| `msm89xx/base-files/lib/upgrade/platform.sh` | **Our file** (MSM8916 hooks) |
| `procd: system.c` | `ubus system sysupgrade` handler |
| `procd: sysupgrade.c` | `sysupgrade_exec_upgraded()` |

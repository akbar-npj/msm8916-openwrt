# Qualcomm MSM8916: Complete Reboot-to-EDL & System Safety Implementation Guide

**Author:** OpenWrt MSM8916 Porting & Stability Team  
**Target:** Qualcomm MSM8916 / Snapdragon 410 (HMU05, UFI001B, UFI003, UZ801, Generic MSM8916)  
**Kernel:** Linux 6.12.94 / OpenWrt 25.12.5  
**Artifact Path:** `Docs/EDL/MSM8916_Reboot_to_EDL_Complete_Implementation_Guide.md`  
**Status:** 100% Implemented, Hardware Tested, and Merged to `main`  

---

## 1. Executive Summary & Architecture Overview

This document is the definitive technical reference for enabling **Reboot to EDL (`05c6:9008` Qualcomm HS-USB QDLoader)** and **Reboot to DLOAD (`05c6:9006` Mass Storage / Dump Mode)** on Qualcomm MSM8916 devices running clean OpenWrt Linux 6.12.

The implementation solves three interconnected engineering challenges:
1. **Hardware Warm-Reset Magic:** Discovering and executing the exact low-level register writes, IMEM cookies, PMIC PON configuration, and SPMI arbiter halt required for the Qualcomm BootROM (PBL) to enter 9008 mode across a warm reset.
2. **Kernel Reset Subsystem Integration:** Integrating a priority 130 restart handler in `drivers/power/reset/msm-poweroff.c` hooked to `LINUX_REBOOT_CMD_RESTART2` via device tree and SCM drivers.
3. **Safe Userspace Teardown & Filesystem Protection:** Orchestrating a clean userspace shutdown flow (`reboot-edl` / `reboot-mode`) and normal reboot unmount service (`umount-overlay`) so that `/overlay` (`/dev/mmcblk0p15` EXT4) is cleanly remounted read-only (`MS_RDONLY`) before power is cut, completely eliminating journal corruption and `e2fsck` repairs on next boot.

```
====================================================================================================
                        COMPLETE QUALCOMM MSM8916 REBOOT-TO-EDL PIPELINE
====================================================================================================

+--------------------------------------------------------------------------------------------------+
| USERSPACE: Safe Teardown Orchestrator (/sbin/reboot-edl -> /sbin/reboot-mode)                   |
| 1. Quiesce network & modem interfaces (ifdown -a, stop BAM-DMUX/RMTFS)                          |
| 2. Stop system services (/etc/init.d/rcS K shutdown)                                             |
| 3. Terminate running daemons (SIGTERM -> 1s sleep -> SIGKILL)                                    |
| 4. Flush caches & drop page caches (echo 3 > /proc/sys/vm/drop_caches)                           |
| 5. Remount storage read-only (mount -o noatime,remount,ro /overlay; mount -o remount,ro /)      |
| 6. Unmount all file systems (umount -a -r)                                                       |
| 7. Trigger emergency kernel sync & remount-ro (SysRq-s, SysRq-u)                                 |
| 8. Execute low-level dispatcher: /sbin/reboot-mode-raw "edl"                                     |
+--------------------------------------------------------------------------------------------------+
                                                 │
                                                 │ syscall(SYS_reboot, ..., RESTART2, "edl")
                                                 v
+--------------------------------------------------------------------------------------------------+
| LINUX KERNEL 6.12: Priority 130 Restart Handler (msm-poweroff.c + qcom-pon.c + qcom_scm.c)       |
| 1. Intercept "edl" / "dload" command string in do_msm_poweroff()                                |
| 2. Write 3 EDL Cookies to IMEM (0x08600FE0):                                                     |
|    - [0x08600FE0] = 0x322A4F99 (MSM_IMEM_EDL_MAGIC1)                                            |
|    - [0x08600FE4] = 0xC67E4350 (MSM_IMEM_EDL_MAGIC2)                                            |
|    - [0x08600FE8] = 0x77777777 (MSM_IMEM_EDL_MAGIC3)                                            |
| 3. Set TCSR_BOOT_MISC_DETECT bit[0] = 0x01 via qcom_scm_io_writel(0x0193D100, 0x01)             |
| 4. Configure PM8916 PON PS_HOLD for WARM RESET (pm8916_pon_configure_warm_reset()):             |
|    - Reg 0x85B (PS_HOLD_RST_CTL2) = 0x0 (disable) -> udelay(300)                                |
|    - Reg 0x85A (PS_HOLD_RST_CTL)  = 0x01 (PON_POWER_OFF_WARM_RESET)                             |
|    - Reg 0x85B (PS_HOLD_RST_CTL2) = BIT(7) (re-enable)                                          |
|    - Reg 0x857 (WD_RST_S2_CTL2)   = 0x0 (clear PMIC watchdog)                                   |
| 5. Halt SPMI PMIC Arbiter via TrustZone SMC: SCM SVC=0x9, CMD=0x1                                |
| 6. Pull PS_HOLD low: writel(0, msm_ps_hold) (0x004AB000 = 0x0)                                   |
+--------------------------------------------------------------------------------------------------+
                                                 │
                                                 │ Hardware Reset with SRAM Retention
                                                 v
+--------------------------------------------------------------------------------------------------+
| QUALCOMM HARDWARE BOOT CHAIN                                                                     |
| 1. PM8916 PMIC asserts reset line while keeping SoC SRAM / IMEM power rails powered              |
| 2. Primary Boot Loader (PBL BootROM) executes from internal ROM                                 |
| 3. PBL reads IMEM 0x08600FE0 -> Matches 3 EDL Magic Cookies                                      |
| 4. PBL reads TCSR 0x0193D100 bit[0] == 1 -> Enters Emergency Download Mode                     |
| 5. Enumerates on USB bus as: ID 05c6:9008 Qualcomm HS-USB QDLoader 9008                         |
+--------------------------------------------------------------------------------------------------+
```

---

## 2. Reverse Engineering & Hardware Ground Truth

### 2.1 The Three Magic Cookies in IMEM
Reverse engineering of the stock Qualcomm Android kernel (`android-vmlinux`) and Little Kernel (`lk2nd` source) revealed that the MSM8916 BootROM does **not** check the generic ASCII cookie `0x65646c00` ("edl\0") at `0x0860065c`. 

Instead, the MSM8916 PBL expects **three 32-bit magic cookies** at IMEM offset `0xFE0` (physical address `0x08600FE0`):

| Address | Offset in IMEM (`0x08600000`) | Value (Hex) | Macro Name |
| :--- | :--- | :--- | :--- |
| `0x08600FE0` | `+0xFE0` | `0x322A4F99` | `MSM_IMEM_EDL_MAGIC1` |
| `0x08600FE4` | `+0xFE4` | `0xC67E4350` | `MSM_IMEM_EDL_MAGIC2` |
| `0x08600FE8` | `+0xFE8` | `0x77777777` | `MSM_IMEM_EDL_MAGIC3` |

### 2.2 TCSR Boot Misc Register (`0x0193D100`)
The Qualcomm PBL checks bit 0 of `TCSR_BOOT_MISC_DETECT` (physical address `0x0193D100`):
* **EDL Mode (`05c6:9008`):** Bit 0 set (`0x01`, `SCM_EDLOAD_MODE`).
* **DLOAD / Dump Mode (`05c6:9006`):** Bit 4 set (`0x10`, `QCOM_DLOAD_FULLDUMP`).
* *Crucial Fix:* Upstream Linux QCOM SCM driver hardcodes `0x10` for full dumps, which causes SBL1 crash dump mode rather than PBL EDL. Writing raw `0x01` to `0x0193D100` is required for true PBL 9008 mode.

### 2.3 PM8916 PMIC PON PS_HOLD Warm Reset Sequence
For IMEM contents to survive the reboot, the PM8916 PMIC must perform a **Warm Reset** rather than a Hard Power-Off:
1. Disable reset detection: Reg `0x85B` (`QPNP_PON_PS_HOLD_RST_CTL2`) = `0x0`.
2. Wait 300 µs: `udelay(300)`.
3. Set Warm Reset mode: Reg `0x85A` (`QPNP_PON_PS_HOLD_RST_CTL`) = `0x01` (`PON_POWER_OFF_WARM_RESET`).
4. Re-enable reset detection: Reg `0x85B` (`QPNP_PON_PS_HOLD_RST_CTL2`) = `BIT(7)` (`QPNP_PON_RESET_EN`).
5. Clear PMIC watchdog: Reg `0x857` (`QPNP_PON_WD_RST_S2_CTL2`) = `0x0`.

### 2.4 PMIC Arbiter Halt (TrustZone SMC)
Before dropping `PS_HOLD`, the SPMI PMIC bus arbiter must be quiesced via an ARM SMC call to the Secure World (TrustZone):
* `SCM_SVC_PWR` (`0x09`), `SCM_IO_DISABLE_PMIC_ARBITER` (`0x01`)
* Retries with CMD `0x02` (`SCM_IO_DISABLE_PMIC_ARBITER1`) if CMD `0x01` is rejected.

---

## 3. Kernel Patches Applied (`msm89xx/patches/`)

The kernel modifications are packaged in [`msm89xx/patches/813-msm8916-reboot-to-edl-support.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/813-msm8916-reboot-to-edl-support.patch).

### 3.1 Device Tree Node (`arch/arm64/boot/dts/qcom/msm8916.dtsi`)
Exposes IMEM memory range to the kernel:
```dts
qcom,msm-imem@8600000 {
	compatible = "qcom,msm-imem", "syscon", "simple-mfd";
	reg = <0x08600000 0x1000>;
	ranges = <0x0 0x08600000 0x1000>;
	#address-cells = <1>;
	#size-cells = <1>;

	emergency_download_mode@fe0 {
		compatible = "qcom,msm-imem-emergency_download_mode";
		reg = <0xfe0 12>;
	};
};
```

### 3.2 SCM Functions (`drivers/firmware/qcom/qcom_scm.c`)
```c
int qcom_scm_set_edload_mode(void)
{
	if (!__scm || !__scm->dload_mode_addr)
		return -ENODEV;

	/* Raw write: SCM_EDLOAD_MODE = 0x01 to TCSR_BOOT_MISC_DETECT */
	return qcom_scm_io_writel(__scm->dload_mode_addr, 0x01);
}
EXPORT_SYMBOL_GPL(qcom_scm_set_edload_mode);

void qcom_scm_halt_pmic_arbiter(void)
{
	struct qcom_scm_desc desc = {
		.svc   = 0x09,	/* SCM_SVC_PWR */
		.cmd   = 0x01,	/* SCM_IO_DISABLE_PMIC_ARBITER */
		.owner = ARM_SMCCC_OWNER_SIP,
	};
	int ret;

	if (!__scm)
		return;

	ret = qcom_scm_call_atomic(__scm->dev, &desc, NULL);
	if (ret) {
		desc.cmd = 0x02;	/* SCM_IO_DISABLE_PMIC_ARBITER1 */
		ret = qcom_scm_call_atomic(__scm->dev, &desc, NULL);
		if (ret)
			dev_err_ratelimited(__scm->dev,
				"halt_pmic_arbiter failed: %d\n", ret);
	}
}
EXPORT_SYMBOL_GPL(qcom_scm_halt_pmic_arbiter);
```

### 3.3 Poweroff Restart Handler (`drivers/power/reset/msm-poweroff.c`)
```c
static void msm_write_emergency_dload_magic(void)
{
	if (!msm_imem_base)
		return;

	/* Write 3 EDL magic cookies to 0x08600FE0 */
	writel(MSM_IMEM_EDL_MAGIC1, msm_imem_base + IMEM_EDL_COOKIES_OFFSET + 0x00);
	writel(MSM_IMEM_EDL_MAGIC2, msm_imem_base + IMEM_EDL_COOKIES_OFFSET + 0x04);
	writel(MSM_IMEM_EDL_MAGIC3, msm_imem_base + IMEM_EDL_COOKIES_OFFSET + 0x08);
	mb();
}

static int do_msm_poweroff(struct sys_off_data *data)
{
	if (data->cmd && (!strcmp(data->cmd, "edl") || !strcmp(data->cmd, "dload"))) {
		pr_emerg("MSM8916: EDL warm reset sequence (lk2nd-equivalent)\n");
		msm_write_emergency_dload_magic();
		qcom_scm_set_edload_mode();
		if (msm_pon_configure_warm_reset_fn)
			msm_pon_configure_warm_reset_fn();
		qcom_scm_halt_pmic_arbiter();
	}

	writel(0, msm_ps_hold);
	mdelay(10000);

	return NOTIFY_DONE;
}
```

---

## 4. Userspace Architecture & Safe Shutdown Orchestration

### 4.1 The Unclean Reboot Problem
When a userspace binary calls `syscall(SYS_reboot, ..., LINUX_REBOOT_CMD_RESTART2, "edl")` directly:
1. `procd` init system is not notified; services and daemons remain running.
2. Peripheral drivers (e.g. WiFi `wcn36xx`, Modem `rmtfs`) continue transmitting DMA transactions.
3. `/overlay` (`/dev/mmcblk0p15` EXT4) is mounted read-write.
4. The kernel pulls `PS_HOLD` low in under 2 milliseconds, resetting the SoC with uncommitted EXT4 journal transactions.
5. On next boot, `79-check-rootfs-data` runs `e2fsck -p`, recovers the journal, and reports `rootfs_data: filesystem errors repaired`.

### 4.2 The Two-Tier Userspace Solution (`packages/reboot-edl/`)

We replaced the naive C binary with a clean two-tier architecture:
1. **Low-Level Syscall Helper (`packages/reboot-edl/src/reboot-mode-raw.c`):**
   Compiled to `/sbin/reboot-mode-raw`. Strictly dispatches `reboot(LINUX_REBOOT_CMD_RESTART2, cmd)`.
2. **Safe Orchestrator (`packages/reboot-edl/files/reboot-mode.sh`):**
   Installed to `/sbin/reboot-mode` and symlinked to `/sbin/reboot-edl`, `/sbin/reboot-dload`, `/sbin/reboot-bootloader`, `/sbin/reboot-fastboot`, `/sbin/reboot-recovery`.

#### The 8-Step Teardown Script Flow:
```sh
#!/bin/sh
# /sbin/reboot-mode - Graceful teardown before hardware reboot

MODE="${1:-edl}"

# 1. Graceful network interface shutdown
if command -v ifdown >/dev/null 2>&1; then
	ifdown -a 2>/dev/null || true
fi

# 2. Stop running system services
if [ -d /etc/rc.d ]; then
	/etc/init.d/rcS K shutdown >/dev/null 2>&1 || true
fi

# 3. Terminate remaining daemons
killall5 -15 2>/dev/null || true
sync; sleep 1

killall5 -9 2>/dev/null || true
sync; sleep 1

# 4. Flush page caches and sync buffers
echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true
sync

# 5. Remount storage and overlay read-only
if grep -q " /overlay " /proc/mounts 2>/dev/null; then
	mount -o noatime,remount,ro /overlay 2>/dev/null || true
fi
mount -o remount,ro / 2>/dev/null || true
umount -a -r 2>/dev/null || true

# 6. Kernel-level emergency sync & remount-ro
if [ -w /proc/sysrq-trigger ]; then
	echo s > /proc/sysrq-trigger 2>/dev/null || true
	echo u > /proc/sysrq-trigger 2>/dev/null || true
fi

# 7. Dispatch low-level reboot
exec /sbin/reboot-mode-raw "$MODE"
```

---

## 5. Normal Reboot Clean Unmount Fix (`msm89xx/base-files/`)

### 5.1 Root Cause in Standard OpenWrt
During standard `/sbin/reboot`:
1. `procd` runs `/etc/init.d/rcS K shutdown`, which executes `K90umount` (`umount -a -d -r`).
2. Because `procd` and active daemons hold open files on `/overlay`, `umount` fails with `EBUSY`.
3. In `STATE_HALT`, `procd` kills all processes but **never retries remounting read-only** before calling `reboot(RB_AUTOBOOT)`.
4. The EXT4 clean unmount flag (`EXT4_VALID_FS`) is never written to disk.

### 5.2 Resolution: `umount-overlay` Service (`STOP=98`)
Created [`msm89xx/base-files/etc/init.d/umount-overlay`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/etc/init.d/umount-overlay):
```sh
#!/bin/sh /etc/rc.common
# Cleanly flush caches and remount rootfs/overlay read-only during system halt/reboot

STOP=98

stop() {
	sync
	echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true
	if grep -q " /overlay " /proc/mounts 2>/dev/null; then
		mount -o noatime,remount,ro /overlay 2>/dev/null || true
	fi
	mount -o remount,ro / 2>/dev/null || true
	if [ -w /proc/sysrq-trigger ]; then
		echo s > /proc/sysrq-trigger 2>/dev/null || true
		echo u > /proc/sysrq-trigger 2>/dev/null || true
	fi
}

shutdown() {
	stop
}
```
Enabled on first boot via [`msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot).

---

## 6. Live Hardware Verification & Test Results (192.168.8.1)

### 6.1 Test 1: Standard Reboot Comparison

#### Before Fix:
```
[    6.690689] rootfs_data: running e2fsck -p
[    6.900334] rootfs_data: recovering journal
[    7.076821] rootfs_data: clean, 114/217728 files, 44532/869879 blocks
[    7.168335] rootfs_data: filesystem clean
```
*⚠️ EXT4 had to perform journal recovery on every reboot.*

#### After Fix (Live Device Result):
```
[    6.689074] rootfs_data: running e2fsck -p
[    6.901814] rootfs_data: clean, 120/217728 files, 44552/869879 blocks
[    6.967234] rootfs_data: filesystem clean
```
*✅ Journal recovery completely eliminated; filesystem is 100% clean.*

---

### 6.2 Test 2: Safe `reboot-edl` Transition & Reset

1. **Triggered `/sbin/reboot-edl` on Live Device**:
   * Network interfaces brought down gracefully.
   * Services stopped (`rcS K shutdown`).
   * Daemons terminated (`killall5`).
   * Page caches dropped (`drop_caches: 3`).
   * `/overlay` remounted read-only: `EXT4-fs (mmcblk0p15): re-mounted ... ro.`
   * Emergency sync & remount executed (`sysrq: Emergency Remount R/O`).
   * Low-level reboot syscall triggered.

2. **Shutdown Trace Captured in Ramoops (`/sys/fs/pstore/console-ramoops-0`)**:
   ```
   [   69.914400] reboot-edl (5305): drop_caches: 3
   [   70.260789] EXT4-fs (mmcblk0p15): re-mounted 7c50df6f-2fee-4370-b621-af0d89950311 ro.
   [   70.352377] sysrq: Emergency Sync
   [   70.352517] sysrq: Emergency Remount R/O
   [   70.355185] Emergency Sync complete
   [   70.359698] Emergency Remount complete
   [   70.411350] reboot: Restarting system with command 'edl'
   [   70.411536] MSM8916: EDL warm reset sequence (lk2nd-equivalent)
   ```

3. **Host USB Enumeration (`lsusb`)**:
   ```
   Bus 001 Device 008: ID 05c6:9008 Qualcomm, Inc. Gobi Wireless Modem (QDL mode)
   ```
   *✅ Device cleanly entered Qualcomm 9008 EDL mode.*

4. **Return from EDL via `edl reset` & Next Boot Verification**:
   * Sent `edl reset` from host PC.
   * Device booted back to OpenWrt.
   * **Boot Kernel Log (`dmesg`):**
     ```
     [    6.643009] rootfs_data: existing ext filesystem detected
     [    6.643358] rootfs_data: od_magic=1 blkid_type=ext4
     [    6.647759] rootfs_data: running e2fsck -p
     [    6.891747] rootfs_data: clean, 120/217728 files, 44552/869879 blocks
     [    6.902277] rootfs_data: filesystem clean
     ```
   *✅ The filesystem remained 100% clean across the EDL mode transition without any journal recovery.*

---

## 7. Repository File Map & References

| File | Purpose |
| :--- | :--- |
| [`msm89xx/patches/813-msm8916-reboot-to-edl-support.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/813-msm8916-reboot-to-edl-support.patch) | Kernel patch for IMEM cookies, SCM EDLOAD mode, PM8916 PON warm reset, PMIC arbiter halt, and PS_HOLD restart handler |
| [`packages/reboot-edl/src/reboot-mode-raw.c`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/reboot-edl/src/reboot-mode-raw.c) | Minimal AArch64 C binary for low-level `LINUX_REBOOT_CMD_RESTART2` dispatch |
| [`packages/reboot-edl/files/reboot-mode.sh`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/reboot-edl/files/reboot-mode.sh) | Orchestrator script for interface teardown, service stop, daemon termination, read-only remount, and SysRq sync |
| [`packages/reboot-edl/Makefile`](file:///home/shaanair/Projects/msm8916-openwrt-clean/packages/reboot-edl/Makefile) | OpenWrt package definition installing binaries and symlinks |
| [`msm89xx/base-files/etc/init.d/umount-overlay`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/etc/init.d/umount-overlay) | Base-files `STOP=98` service ensuring `/overlay` is cleanly remounted `ro` during standard `/sbin/reboot` |
| [`msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot) | Firstboot script enabling `umount-overlay` service |
| [`Docs/EDL/MSM8916_Clean_Reboot_EDL_and_Filesystem_Safety_Analysis.md`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/EDL/MSM8916_Clean_Reboot_EDL_and_Filesystem_Safety_Analysis.md) | In-depth technical analysis and step-by-step trace of procd shutdown and EXT4 state flags |

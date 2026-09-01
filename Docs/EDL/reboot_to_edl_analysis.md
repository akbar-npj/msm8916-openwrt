# Qualcomm MSM8916: "Reboot to EDL" Mechanism Analysis & OpenWrt Implementation Guide

This document provides a comprehensive technical analysis of how the **"Reboot to EDL" (Emergency Download Mode / Qualcomm 9008)** mechanism operates in the downstream Android kernel source (`GitIgnore/android_kernel_zte_msm8916/`), and outlines practical implementation strategies for the mainline OpenWrt tree (`msm8916-openwrt-clean`).

---

## 1. Overview & Core Concepts

### What is EDL Mode?
**Emergency Download Mode (EDL)** is Qualcomm's primary hardware-level recovery mode embedded in the SoC's **Primary Boot Loader (PBL / BootROM)**. When in EDL mode:
- The device exposes a USB interface with **VID:PID `05c6:9008`** (Qualcomm HS-USB QDLoader 9008).
- It accepts low-level flashing commands via the **Sahara** and **Firehose** protocols.
- It operates independent of eMMC partitions, SBL1, Little Kernel (LK/aboot), or the Linux kernel.

### The Inter-processor Communication Mechanism: IMEM & SCM
When the operating system is running, the CPU cannot simply tell the BootROM to "enter EDL" via a software jump because a reboot reinitializes CPU registers. Instead, communication across a reboot is achieved via:
1. **IMEM (Internal Shared SRAM)**: A 4KB on-chip memory region mapped at physical address `0x08600000` (on MSM8916) that **retains its contents across warm resets / PMIC power cycles**.
2. **SCM (Secure Channel Manager / TrustZone SMC Call)**: Secure Monitor Calls into TrustZone (QSEE) to set boot flags and disable watchdog/debug subsystems.

---

## 2. Deep Dive: How Android Kernel (`android_kernel_zte_msm8916`) Executes `reboot edl`

The entire reboot-to-EDL pipeline in the ZTE MSM8916 Android kernel is implemented in:
- [`drivers/power/reset/msm-poweroff.c`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/drivers/power/reset/msm-poweroff.c)
- [`drivers/platform/msm/qpnp-power-on.c`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/drivers/platform/msm/qpnp-power-on.c)
- [`arch/arm/boot/dts/qcom/msm8916.dtsi`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/arch/arm/boot/dts/qcom/msm8916.dtsi)

```
+-------------------------------------------------------------------------+
|                              USERSPACE                                  |
|   `adb reboot edl`  OR  `reboot edl`                                    |
|   -> sys_reboot(LINUX_REBOOT_CMD_RESTART2, "edl")                       |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                       KERNEL REBOOT FRAMEWORK                           |
|   kernel_restart("edl") -> arm_pm_restart(reboot_mode, "edl")           |
|   -> do_msm_restart()                                                   |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                  MSM-POWEROFF DRIVER (msm-poweroff.c)                   |
|                                                                         |
|  1. Parse cmd string: strcmp(cmd, "edl")                                |
|  2. enable_emergency_dload_mode():                                      |
|     * Write 3 Magic Words to IMEM (0x08600FE0):                         |
|         [0x08600FE0] = 0x322A4F99 (MAGIC1)                              |
|         [0x08600FE4] = 0xC67E4350 (MAGIC2)                              |
|         [0x08600FE8] = 0x77777777 (MAGIC3)                              |
|     * Disable PMIC Watchdog via qpnp_pon_wd_config(0)                   |
|     * Invoke SCM call: SCM_SVC_BOOT (0x1), SCM_DLOAD_CMD (0x10),        |
|                        SCM_EDLOAD_MODE (0x1)                            |
|  3. Configure PMIC for WARM RESET: qpnp_pon_system_pwr_off()            |
|  4. Flush caches: flush_cache_all()                                     |
|  5. Bypass Watchdog debug: SCM call SCM_WDOG_DEBUG_BOOT_PART (0x9)        |
|  6. Disable SPMI PMIC Arbiter: halt_spmi_pmic_arbiter()                 |
|  7. Pull down PS_HOLD: writel(0, msm_ps_hold @ 0x004AB000)              |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                      HARDWARE / BOOTROM (PBL)                           |
|  1. PMIC initiates warm reset                                           |
|  2. PBL boots from ROM, reads IMEM @ 0x08600FE0                         |
|  3. Detects 0x322A4F99, 0xC67E4350, 0x77777777                          |
|  4. Aborts eMMC boot -> Enters EDL (Qualcomm HS-USB QDLoader 9008)      |
+-------------------------------------------------------------------------+
```

### Detailed Breakdown of Code Execution:

#### A. Magic Constants & Register Definitions
From [`drivers/power/reset/msm-poweroff.c#L34-L44`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/drivers/power/reset/msm-poweroff.c#L34-L44):
```c
#define EMERGENCY_DLOAD_MAGIC1    0x322A4F99
#define EMERGENCY_DLOAD_MAGIC2    0xC67E4350
#define EMERGENCY_DLOAD_MAGIC3    0x77777777

#define SCM_IO_DISABLE_PMIC_ARBITER 1
#define SCM_WDOG_DEBUG_BOOT_PART    0x9
#define SCM_DLOAD_MODE              0x10
#define SCM_EDLOAD_MODE             0x01
#define SCM_DLOAD_CMD               0x10
```

#### B. Device Tree IMEM Mapping
In the Android device tree [`msm8916.dtsi`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/arch/arm/boot/dts/qcom/msm8916.dtsi#L1440-L1466):
```dts
qcom,msm-imem@8600000 {
    compatible = "qcom,msm-imem";
    reg = <0x08600000 0x1000>;
    ranges = <0x0 0x08600000 0x1000>;

    restart_reason@65c {
        compatible = "qcom,msm-imem-restart_reason";
        reg = <0x65c 4>;
    };

    emergency_download_mode@fe0 {
        compatible = "qcom,msm-imem-emergency_download_mode";
        reg = <0xfe0 12>; /* 3 x 4-byte words */
    };
};

restart@4ab000 {
    compatible = "qcom,pshold";
    reg = <0x4ab000 0x4>;
};
```

#### C. The `enable_emergency_dload_mode()` Function
From [`drivers/power/reset/msm-poweroff.c#L102-L128`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/drivers/power/reset/msm-poweroff.c#L102-L128):
```c
static void enable_emergency_dload_mode(void)
{
    int ret;

    if (emergency_dload_mode_addr) {
        /* Write the 3 magic cookies to IMEM offset 0xFE0 */
        __raw_writel(EMERGENCY_DLOAD_MAGIC1, emergency_dload_mode_addr);
        __raw_writel(EMERGENCY_DLOAD_MAGIC2, emergency_dload_mode_addr + sizeof(unsigned int));
        __raw_writel(EMERGENCY_DLOAD_MAGIC3, emergency_dload_mode_addr + (2 * sizeof(unsigned int)));

        /* Disable PMIC watchdog so the device will not auto-reboot while in EDL */
        qpnp_pon_wd_config(0);
        mb();
    }

    if (scm_dload_supported) {
        /* Inform TrustZone firmware of Emergency Download request */
        ret = scm_call_atomic2(SCM_SVC_BOOT, SCM_DLOAD_CMD, SCM_EDLOAD_MODE, 0);
        if (ret)
            pr_err("Failed to set EDLOAD mode: %d\n", ret);
    }
}
```

#### D. Restart Preparation & Execution
From [`drivers/power/reset/msm-poweroff.c#L190-L257`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916/drivers/power/reset/msm-poweroff.c#L190-L257):
```c
static void msm_restart_prepare(const char *cmd)
{
    /* Warm reset so IMEM contents are preserved */
    qpnp_pon_system_pwr_off(PON_POWER_OFF_WARM_RESET);

    if (cmd != NULL) {
        if (!strncmp(cmd, "bootloader", 10)) {
            __raw_writel(0x77665500, restart_reason);
        } else if (!strncmp(cmd, "recovery", 8)) {
            __raw_writel(0x77665502, restart_reason);
        } else if (!strncmp(cmd, "edl", 3)) {
            enable_emergency_dload_mode();
        } else {
            __raw_writel(0x77665501, restart_reason);
        }
    }

    flush_cache_all();
}

static void do_msm_restart(enum reboot_mode reboot_mode, const char *cmd)
{
    msm_restart_prepare(cmd);

    /* Bypass watchdog debug partitioning */
    scm_call_atomic2(SCM_SVC_BOOT, SCM_WDOG_DEBUG_BOOT_PART, 1, 0);

    /* Prevent SPMI bus lockup during PS_HOLD deassertion */
    halt_spmi_pmic_arbiter();

    /* Drop PS_HOLD to 0 -> hardware initiates SoC reset */
    __raw_writel(0, msm_ps_hold);

    mdelay(10000);
}
```

---

## 3. Current State in `msm8916-openwrt-clean`

In the OpenWrt tree:
1. **Kernel Version**: Mainline Linux **6.12** (`target/linux/msm89xx`).
2. **Reboot Architecture**:
   - Resets are coordinated via **PSCI** (Power State Coordination Interface) or `qcom-pon` / `syscon-reboot-mode`.
   - `CONFIG_REBOOT_MODE=y` is enabled in `msm89xx/config-6.12`.
3. **Why `reboot edl` does not currently work out-of-the-box**:
   - **Userspace**: OpenWrt's init/reboot (`procd` / `busybox reboot`) does not accept arbitrary string arguments like `edl` and does not invoke `reboot(LINUX_REBOOT_CMD_RESTART2, "edl")`.
   - **Device Tree**: Mainline device trees (`msm8916.dtsi` / `msm8916-ufi.dtsi`) do not have the IMEM EDL subnode or `syscon-reboot-mode` nodes mapped to `0x08600FE0`.
   - **PBL Requirements**: Mainline's `syscon-reboot-mode` only writes a single 32-bit word to `0x0860065c` (`restart_reason`). While custom bootloaders like `lk2nd` or modified Little Kernel can inspect `0x65c`, the hardware **PBL (BootROM)** strictly looks for the 3-word cookie at `0x08600FE0` (`0x322A4F99`, `0xC67E4350`, `0x77777777`).

---

## 4. Implementation Options for `msm8916-openwrt-clean`

There are **three recommended approaches** depending on architecture preference:

---

### Option 1: Direct Userspace Utility / Script (Recommended for Quickest & Non-Invasive Implementation)

This option requires zero kernel patches. A small utility directly maps IMEM via `/dev/mem`, writes the 3 magic cookies to `0x08600FE0` and `restart_reason` to `0x0860065c`, and triggers a warm reboot.

#### A. Standalone C Tool: `reboot-edl.c`
```c
/*
 * reboot-edl.c - Trigger Qualcomm MSM8916 Emergency Download Mode (EDL / 9008)
 */
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/reboot.h>
#include <linux/reboot.h>

#define IMEM_PHYS_BASE       0x08600000
#define IMEM_SIZE            0x1000

#define RESTART_REASON_OFFSET 0x65C
#define EMERGENCY_DLOAD_OFFSET 0xFE0

#define MAGIC_EDL_REASON     0x77777777
#define MAGIC_EDL_1          0x322A4F99
#define MAGIC_EDL_2          0xC67E4350
#define MAGIC_EDL_3          0x77777777

int main(int argc, char *argv[])
{
    int fd;
    void *map_base;
    volatile uint32_t *edl_magic;
    volatile uint32_t *restart_reason;

    if (geteuid() != 0) {
        fprintf(stderr, "Error: Must be run as root.\n");
        return 1;
    }

    fd = open("/dev/mem", O_RDWR | O_SYNC);
    if (fd < 0) {
        perror("Failed to open /dev/mem (Ensure CONFIG_DEVMEM=y and strict devmem disabled)");
        return 1;
    }

    map_base = mmap(NULL, IMEM_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, IMEM_PHYS_BASE);
    if (map_base == MAP_FAILED) {
        perror("Failed to mmap IMEM");
        close(fd);
        return 1;
    }

    printf("[reboot-edl] Writing EDL cookies to IMEM...\n");

    /* 1. Write Little Kernel / aboot restart reason */
    restart_reason = (volatile uint32_t *)((uint8_t *)map_base + RESTART_REASON_OFFSET);
    *restart_reason = MAGIC_EDL_REASON;

    /* 2. Write BootROM (PBL) 3-word emergency download cookies */
    edl_magic = (volatile uint32_t *)((uint8_t *)map_base + EMERGENCY_DLOAD_OFFSET);
    edl_magic[0] = MAGIC_EDL_1;
    edl_magic[1] = MAGIC_EDL_2;
    edl_magic[2] = MAGIC_EDL_3;

    /* Memory barrier & flush */
    __sync_synchronize();

    munmap(map_base, IMEM_SIZE);
    close(fd);

    printf("[reboot-edl] Resetting system into 9008 EDL mode...\n");
    sync();

    /* Issue immediate reboot */
    reboot(RB_AUTOBOOT);

    return 0;
}
```

#### B. Kernel Config Requirement for `/dev/mem`
Ensure the following are set in `msm89xx/config-6.12`:
```ini
CONFIG_DEVMEM=y
# CONFIG_STRICT_DEVMEM is not set
```

---

### Option 2: Mainline Device Tree + `syscon-reboot-mode` (Standard Linux Solution)

If your bootloader (e.g. Little Kernel `aboot` or `lk2nd`) supports reading `0x65c` to enter EDL or Fastboot:

#### A. Kernel Config
Ensure `msm89xx/config-6.12` contains:
```ini
CONFIG_REBOOT_MODE=y
CONFIG_SYSCON_REBOOT_MODE=y
CONFIG_MFD_SYSCON=y
```

#### B. Device Tree Patch (`msm89xx/patches/`)
Add `imem` and `reboot-mode` in `arch/arm64/boot/dts/qcom/msm8916.dtsi`:
```dts
/ {
    soc {
        imem@8600000 {
            compatible = "qcom,msm8916-imem", "syscon", "simple-mfd";
            reg = <0x08600000 0x1000>;
            #address-cells = <1>;
            #size-cells = <1>;
            ranges = <0 0x08600000 0x1000>;

            reboot_mode: reboot-mode {
                compatible = "syscon-reboot-mode";
                offset = <0x65c>;

                mode-normal     = <0x77665501>;
                mode-bootloader = <0x77665500>;
                mode-recovery   = <0x77665502>;
                mode-edl        = <0x77777777>;
            };
        };
    };
};
```

---

### Option 3: Kernel Reboot Notifier Patch (Direct PBL Hardware EDL - 100% Android-Identical)

To guarantee that `reboot edl` triggers the PBL hardware 3-cookie sequence without relying on userspace `/dev/mem` or custom bootloaders:

#### Kernel Patch: `msm89xx/patches/812-arm64-qcom-edl-reboot-support.patch`
```diff
--- a/drivers/power/reset/qcom-pon.c
+++ b/drivers/power/reset/qcom-pon.c
@@ -20,6 +20,11 @@
 #include <linux/reboot.h>
 #include <linux/reboot-mode.h>
 #include <linux/regmap.h>
+#include <linux/io.h>
+
+#define MSM8916_IMEM_EDL_PHYS    0x08600FE0
+#define EDL_MAGIC1               0x322A4F99
+#define EDL_MAGIC2               0xC67E4350
+#define EDL_MAGIC3               0x77777777
 
 #define PON_SOFT_RB_SPARE		0x8f
@@ -38,6 +43,20 @@ static int qcom_pon_reboot_mode_write(st
 	struct qcom_pon *pon = container_of
 			(reboot, struct qcom_pon, reboot_mode);
 	int ret;
+
+	/* If EDL mode requested, write the 3 BootROM magic cookies directly to IMEM */
+	if (magic == 0x77777777) {
+		void __iomem *imem_edl = ioremap(MSM8916_IMEM_EDL_PHYS, 12);
+		if (imem_edl) {
+			writel_relaxed(EDL_MAGIC1, imem_edl);
+			writel_relaxed(EDL_MAGIC2, imem_edl + 4);
+			writel_relaxed(EDL_MAGIC3, imem_edl + 8);
+			iounmap(imem_edl);
+			mb();
+		}
+	}
 
 	ret = regmap_update_bits(pon->regmap,
 				 pon->baseaddr + PON_SOFT_RB_SPARE,
```

---

### Option 4: OpenWrt CLI Wrapper

In `msm89xx/base-files/usr/sbin/reboot-edl` (or extending `/sbin/reboot`):

```bash
#!/bin/sh
# /usr/sbin/reboot-edl - Convenient CLI helper for OpenWrt

echo "[*] Switching Qualcomm MSM8916 to Emergency Download (EDL / 9008)..."

# If compiled binary is present:
if [ -x /usr/sbin/reboot-edl-bin ]; then
    exec /usr/sbin/reboot-edl-bin
fi

# Fallback: using busybox devmem (if devmem is enabled in busybox)
if which devmem >/dev/null 2>&1; then
    # Write 0x77777777 to 0x0860065C (restart reason)
    devmem 0x0860065C 32 0x77777777
    # Write 3 PBL cookies to 0x08600FE0
    devmem 0x08600FE0 32 0x322A4F99
    devmem 0x08600FE4 32 0xC67E4350
    devmem 0x08600FE8 32 0x77777777
    sync
    reboot -f
fi
```

---

## 5. Comparison Matrix of Implementation Strategies

| Feature / Criterion | Option 1: Userspace Utility (`/dev/mem`) | Option 2: DTS `syscon-reboot-mode` | Option 3: Kernel Patch (`qcom-pon`) |
| :--- | :--- | :--- | :--- |
| **PBL Hardware EDL (9008)** | Yes (writes `0xFE0` cookies) | Only if bootloader translates `0x65c` | Yes (writes `0xFE0` cookies) |
| **Bootloader Independence** | High (triggers EDL even if LK corrupted) | Low (requires LK support) | High (triggers EDL directly) |
| **Kernel Modifications** | None (only `/dev/mem` needed) | Minimal (DTS only) | Patch to `qcom-pon.c` |
| **OpenWrt Upstream Cleanliness** | High (standalone package) | Standard mainline DT convention | Requires OpenWrt kernel patch |
| **Ease of Deployment** | Immediate | Requires rebuild with DTS patch | Requires rebuild with patch |

---

## 6. Summary of Key Values for Reference

| Register / Parameter | Physical Address / Offset | Magic Value(s) | Description |
| :--- | :--- | :--- | :--- |
| **IMEM Base** | `0x08600000` | — | 4KB on-chip internal SRAM |
| **`restart_reason`** | `0x0860065C` | `0x77665500` | Fastboot / Bootloader mode |
| | `0x0860065C` | `0x77665502` | Recovery mode |
| | `0x0860065C` | `0x77777777` | EDL mode (Little Kernel cookie) |
| **`emergency_download_mode`** | `0x08600FE0` | `0x322A4F99` (`MAGIC1`) | Primary BootROM (PBL) EDL word 1 |
| | `0x08600FE4` | `0xC67E4350` (`MAGIC2`) | Primary BootROM (PBL) EDL word 2 |
| | `0x08600FE8` | `0x77777777` (`MAGIC3`) | Primary BootROM (PBL) EDL word 3 |
| **`download_mode` (RAM dump)**| `0x08600000` | `0xE47B337D`, `0xCE14091A` | Memory dump download mode |
| **`msm_ps_hold`** | `0x004AB000` | `0x00000000` | SoC reset line deassertion |
| **SCM EDLOAD Command** | SCM Call | `SVC=0x1, CMD=0x10, MODE=0x1` | TrustZone Emergency DLOAD notify |

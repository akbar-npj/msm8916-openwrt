# MSM8916 OpenWrt Kernel and Source Patches Reference Guide

This document provides a comprehensive technical catalog and architectural explanation for all patches maintained in [`msm89xx/patches/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/).

---

## 1. Patches Overview

The patches are categorized by their functional domain:

| Patch Number & Name | Category | Targets Modified | Description |
| :--- | :--- | :--- | :--- |
| [`801-arm64-dts-qcom-add-devices-makefile.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch) | Device Tree Build | `arch/arm64/boot/dts/qcom/Makefile` | Registers generic MSM8916 modem stick DTBs (`generic-hmu05`, `generic-uf02`, `generic-ufi001b`) in the kernel build system. |
| [`802-arm64-dts-qcom-msm8916-label-reserved-memory.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/802-arm64-dts-qcom-msm8916-label-reserved-memory.patch) | Device Tree Core | `arch/arm64/boot/dts/qcom/msm8916.dtsi` | Exposes the `reserved_memory:` label on the top-level `reserved-memory` node, allowing board DTS files to append `ramoops` regions. |
| [`803-arm64-dts-qcom-swap-leds-uz801.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/803-arm64-dts-qcom-swap-leds-uz801.patch) | Device Tree Board | `arch/arm64/boot/dts/qcom/msm8916-yiming-uz801v3.dts` | Defines explicit `LED_FUNCTION_WLAN` and `LED_FUNCTION_WAN` attributes for YiMing UZ801V3 LEDs. |
| [`804-arm64-dts-qcom-add-msm8916-generic-uf02.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/804-arm64-dts-qcom-add-msm8916-generic-uf02.patch) | Device Tree Board | `arch/arm64/boot/dts/qcom/msm8916-generic-uf02.dts` | Adds complete board DTS for the UF02 4G modem stick (GPIO buttons, LEDs). |
| [`805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch) | Device Tree Board | `arch/arm64/boot/dts/qcom/msm8916-generic-hmu05.dts` | Adds complete board DTS for the HMU05 4G modem stick (SIM/eSIM GPIO hogs, ramoops logging at `0x8db00000`, LEDs, reset button). |
| [`806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch) | Device Tree Board | `arch/arm64/boot/dts/qcom/msm8916-generic-ufi001b.dts` | Adds complete board DTS for the UFI001B 4G modem stick (SIM enable/selector GPIO hogs, ramoops logging at `0x8db00000`, LEDs, reset button). |
| [`808-bam-dmux-stats.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/808-bam-dmux-stats.patch) | Network Driver | `drivers/net/bam_dmux.c` | Adds accurate TX/RX packet & byte statistics to BAM-DMUX network interfaces and frees TX skbs and clears channel bitmaps during power-off. |
| [`809-mac80211-enable-wcn36xx.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/809-mac80211-enable-wcn36xx.patch) | OpenWrt Source | `package/kernel/mac80211/ath.mk` | OpenWrt package recipe patch enabling Qualcomm Atheros `wcn36xx` and `ath10k-sdio` kernel modules on `msm89xx`. |
| [`813-msm8916-reboot-to-edl-support.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/813-msm8916-reboot-to-edl-support.patch) | Power & Reset | `drivers/firmware/qcom/qcom_scm.c`<br>`drivers/power/reset/msm-poweroff.c`<br>`drivers/power/reset/msm-poweroff.h`<br>`drivers/power/reset/qcom-pon.c`<br>`include/linux/firmware/qcom/qcom_scm.h` | Kernel driver support for Emergency Download (EDL / 9008) mode warm-reset matching lk2nd bootloader sequence. |
| [`815-qcom-sysmon-ignore-wcnss-modem-ssr.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/815-qcom-sysmon-ignore-wcnss-modem-ssr.patch) | Remoteproc / Modem | `drivers/remoteproc/qcom_sysmon.c` | Skips forwarding Modem Subsystem Restart (SSR) notifications to WCNSS, preventing WCNSS firmware faults on modem stop/restart. |
| [`999-tsens-propagate-eprobe-defer.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/999-tsens-propagate-eprobe-defer.patch) | Thermal Driver | `drivers/thermal/qcom/tsens-v0_1.c` | Properly propagates `-EPROBE_DEFER` from `tsens_calibrate_nvmem` so TSENS probes successfully when QFPROM arrives asynchronously. |

---

## 2. Detailed Technical Breakdown

### 801: Device Tree Makefile Registration
- **File**: [`801-arm64-dts-qcom-add-devices-makefile.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch)
- **Target**: `arch/arm64/boot/dts/qcom/Makefile`
- **Purpose**:
  The upstream Linux kernel Makefile for Qualcomm 64-bit Device Trees does not include our generic 4G USB dongle targets. This patch adds the following device tree blobs to the `dtb-$(CONFIG_ARCH_QCOM)` build target list:
  ```makefile
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-hmu05.dtb
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-uf02.dtb
  dtb-$(CONFIG_ARCH_QCOM) += msm8916-generic-ufi001b.dtb
  ```
- **Consolidation Note**: Formerly split across `801` (UF02) and `806` (HMU05 + UFI001B). Consolidated into a single cleanly sorted patch.

---

### 802: MSM8916 Reserved Memory Node Label
- **File**: [`802-arm64-dts-qcom-msm8916-label-reserved-memory.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/802-arm64-dts-qcom-msm8916-label-reserved-memory.patch)
- **Target**: `arch/arm64/boot/dts/qcom/msm8916.dtsi`
- **Purpose**:
  In standard upstream `msm8916.dtsi`, the `reserved-memory` node has no label reference (it is defined anonymously as `reserved-memory { ... };`). Downstream board DTS files (`msm8916-generic-hmu05.dts`, `msm8916-generic-ufi001b.dts`) need to inject board-specific persistent log buffers (`ramoops`).
  This patch adds the `reserved_memory:` phandle label:
  ```dts
  -	reserved-memory {
  +	reserved_memory: reserved-memory {
  		#address-cells = <2>;
  		#size-cells = <2>;
  		ranges;
  ```
- **Consolidation Note**: Renumbered from `810` to `802` so that the base `.dtsi` label is guaranteed to be in place prior to board DTS definitions.

---

### 803: UZ801V3 LED Functions
- **File**: [`803-arm64-dts-qcom-swap-leds-uz801.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/803-arm64-dts-qcom-swap-leds-uz801.patch)
- **Target**: `arch/arm64/boot/dts/qcom/msm8916-yiming-uz801v3.dts`
- **Purpose**:
  Configures the standard OpenWrt LED function bindings (`LED_FUNCTION_WLAN` on Blue LED / GPIO 6, `LED_FUNCTION_WAN` on Green LED / GPIO 8). This allows OpenWrt's user-space LED monitor scripts and `uci` triggers to consistently identify network indicator LEDs across different board variants.

---

### 804: Generic UF02 Board Device Tree
- **File**: [`804-arm64-dts-qcom-add-msm8916-generic-uf02.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/804-arm64-dts-qcom-add-msm8916-generic-uf02.patch)
- **Target**: `arch/arm64/boot/dts/qcom/msm8916-generic-uf02.dts`
- **Purpose**:
  Provides complete device tree definition for the UF02 4G modem stick:
  - Compatible string: `"uf02,250605v0s", "qcom,msm8916"`
  - Reset button on GPIO 23 (Active Low with pull-up)
  - Tri-color status LEDs: Red (GPIO 72), Green (GPIO 71, `LED_FUNCTION_WAN`), Blue (GPIO 73, `LED_FUNCTION_WLAN`)
  - Default pin-control bias configurations.

---

### 805: Generic HMU05 Board Device Tree (with Ramoops)
- **File**: [`805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/805-arm64-dts-qcom-add-msm8916-generic-hmu05.patch)
- **Target**: `arch/arm64/boot/dts/qcom/msm8916-generic-hmu05.dts`
- **Purpose**:
  Provides full board device tree support for the HMU05 4G LTE USB stick:
  1. **Ramoops Logging Node**: Allocates 1MB at `0x8db00000` (`record-size = 256KB`, `console-size = 256KB`, `pmsg-size = 256KB`) for persistent kernel crash dumps and panic analysis.
  2. **Reset Key**: Configured on GPIO 37 (`key_freset`, active-low).
  3. **LED Indicators**:
     - Green LED (GPIO 71) -> Wi-Fi status (`LED_FUNCTION_WLAN`)
     - Red LED (GPIO 72) -> Power/Modem status (`LED_FUNCTION_POWER`, initial boot state ON)
     - Blue LED (GPIO 73) -> LTE WAN status (`LED_FUNCTION_WAN`)
  4. **Hardware GPIO Hogs**:
     Sets critical board power and SIM routing lines directly during early boot via TLMM:
     - `GPIO 119` (`sim1_en`): Output HIGH (primary physical SIM slot power enable)
     - `GPIO 14` (`sim2_en`): Output LOW (secondary SIM/eSIM disable)
     - `GPIO 12` (`sim3_en`): Output LOW (third eSIM disable)
     - `GPIO 114` (`sim_hotplug`): Output LOW
     - `GPIO 36` (`bat1`): Output LOW
     - `GPIO 106` (`ftest`): Output LOW
- **Consolidation Note**: Merged previously separate incremental patch `812-arm64-dts-qcom-msm8916-hmu05-add-ramoops.patch` directly into the base DTS file.

---

### 806: Generic UFI001B Board Device Tree (with Ramoops)
- **File**: [`806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/806-arm64-dts-qcom-add-msm8916-generic-ufi001b.patch)
- **Target**: `arch/arm64/boot/dts/qcom/msm8916-generic-ufi001b.dts`
- **Purpose**:
  Provides full board device tree support for the UFI001B 4G LTE USB stick:
  1. **Ramoops Logging Node**: Allocates 1MB at `0x8db00000` under `&reserved_memory` for persistent kernel logging.
  2. **Reset Key**: Configured on GPIO 37 (`key_freset`, active-low).
  3. **LED Indicators**:
     - Red LED (GPIO 22) -> Power/Modem indicator (`LED_FUNCTION_POWER`)
     - Green LED (GPIO 21) -> LTE WAN indicator (`LED_FUNCTION_WAN`)
     - Blue LED (GPIO 20) -> Wi-Fi indicator (`LED_FUNCTION_WLAN`)
  4. **Hardware GPIO Hogs**:
     - `GPIO 1` (`sim_en`): Output LOW (physical SIM enable)
     - `GPIO 2` (`sim_sel`): Output LOW (SIM selector: physical SIM vs internal eSIM)
     - `GPIO 68` (`4G_L3`): Output LOW (auxiliary LTE indicator line)
- **Consolidation Note**: Merged previously separate incremental patch `811-arm64-dts-qcom-msm8916-ufi001b-add-ramoops.patch` into `806`.

---

### 808: BAM-DMUX Network Driver Statistics & Clean Shutdown
- **File**: [`808-bam-dmux-stats.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/808-bam-dmux-stats.patch)
- **Target**: `drivers/net/bam_dmux.c`
- **Purpose**:
  The Linux kernel `bam_dmux` network driver bridges Qualcomm's BAM DMA channels to Linux network interfaces (e.g. `rmnet_data0`, `bam_dmux0`).
  1. **Network Interface Statistics**:
     Upstream `bam_dmux` omitted packet/byte accounting. This caused LuCI, `ifconfig`, and `ip -s link` to report 0 bytes / 0 packets transferred. This patch adds:
     ```c
     DEV_STATS_INC(netdev, tx_packets);
     DEV_STATS_ADD(netdev, tx_bytes, skb->len);
     DEV_STATS_INC(netdev, rx_packets);
     DEV_STATS_ADD(netdev, rx_bytes, skb->len);
     ```
  2. **Power-Off Cleanup**:
     During modem shutdown or remoteproc crash recovery, pending TX skbs were not freed, leading to DMA buffer leaks and corrupted channel bitmaps. This patch adds:
     ```c
     bam_dmux_free_skbs(dmux->tx_skbs, DMA_TO_DEVICE);
     dmux->tx_next_skb = 0;
     atomic_long_set(&dmux->tx_deferred_skb, 0);
     bitmap_zero(dmux->remote_channels, BAM_DMUX_NUM_CH);
     ```

---

### 809: OpenWrt mac80211 Package Driver Support
- **File**: [`809-mac80211-enable-wcn36xx.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/809-mac80211-enable-wcn36xx.patch)
- **Target**: `package/kernel/mac80211/ath.mk` (OpenWrt source-tree patch)
- **Purpose**:
  Enables compilation and packaging of `kmod-wcn36xx` (the Qualcomm WCN3660/3680 integrated Wi-Fi driver) and `kmod-ath10k-sdio` within OpenWrt's mac80211 package infrastructure.
  - Adds `@TARGET_msm89xx` dependency to `kmod-ath`.
  - Defines `KernelPackage/wcn36xx` with autoload and probe support.
  - Adds `WCN36XX_DEBUGFS` support.
- **Handling Note**: This patch modifies OpenWrt package source (`ath.mk`) rather than the Linux kernel. The preparation scripts (`scripts/openwrt-prepare.sh` and `build.sh`) apply this patch to OpenWrt and prune it from the kernel patch queue so the kernel build tool does not attempt to apply it to `linux-6.12/`.

---

### 813: Kernel Emergency Download (EDL / 9008) Mode Support
- **File**: [`813-msm8916-reboot-to-edl-support.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/813-msm8916-reboot-to-edl-support.patch)
- **Targets**:
  - `drivers/firmware/qcom/qcom_scm.c`
  - `drivers/power/reset/msm-poweroff.c`
  - `drivers/power/reset/msm-poweroff.h`
  - `drivers/power/reset/qcom-pon.c`
  - `include/linux/firmware/qcom/qcom_scm.h`
- **Purpose**:
  Enables warm-reboot directly into Qualcomm Emergency Download mode (EDL / 9008) from userspace commands (`reboot edl` or `reboot dload`).
  Replicates the proven bootloader reset sequence from `lk2nd`:
  1. **IMEM Cookies**: Maps IMEM (`0x08600000`) and writes magic cookies at `0x08600FE0` (`0x444C4F57`, `0x12345678`, `0x56781234`).
  2. **TCSR Boot Detect**: Invokes SCM call `qcom_scm_set_edload_mode()` setting `TCSR_BOOT_MISC_DETECT` bit 0.
  3. **PM8916 PON Configuration**: Configures PM8916 PMIC PS_HOLD for warm reset (`PON_POWER_OFF_WARM_RESET`) and clears PMIC watchdog via `qcom-pon` callback.
  4. **PMIC Arbiter**: Halts PMIC arbiter via SCM service `0x9` cmd `0x1` (`qcom_scm_halt_pmic_arbiter()`).
  5. **PS_HOLD Assert**: Writes 0 to PS_HOLD register with system-off priority 130.

---

### 815: Sysmon Ignore WCNSS Modem SSR Events
- **File**: [`815-qcom-sysmon-ignore-wcnss-modem-ssr.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/815-qcom-sysmon-ignore-wcnss-modem-ssr.patch)
- **Target**: `drivers/remoteproc/qcom_sysmon.c`
- **Purpose**:
  On Qualcomm MSM8916 devices, the WCNSS (Wi-Fi/Bluetooth) coprocessor firmware does not implement subsystem restart (SSR) event notifications from the Hexagon modem DSP. When the modem is stopped or crashes, `qcom_sysmon` attempts to send an SSR notification message to WCNSS, which causes the WCNSS firmware to crash and take down the Wi-Fi subsystem.
  This patch skips sending modem SSR notifications to WCNSS:
  ```c
  if (!strcmp(sysmon->name, "wcnss") && !strcmp(sysmon_event->subsys_name, "modem"))
      return NOTIFY_DONE;
  ```

---

### 999: TSENS Propagate EPROBE_DEFER
- **File**: [`999-tsens-propagate-eprobe-defer.patch`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/999-tsens-propagate-eprobe-defer.patch)
- **Target**: `drivers/thermal/qcom/tsens-v0_1.c`
- **Purpose**:
  During early boot, the TSENS thermal sensor driver reads calibration data from QFPROM NVMEM cells. If the `nvmem` provider has not yet probed, `tsens_calibrate_nvmem` returns `-EPROBE_DEFER`.
  Without this patch, the driver treated `-EPROBE_DEFER` as a fatal initialization error rather than deferring the probe, causing the SoC thermal monitoring and throttling subsystem to fail to load.
  This patch ensures `-EPROBE_DEFER` is propagated back up to the platform driver probe mechanism:
  ```c
  ret = tsens_calibrate_nvmem(priv, 3);
  if (ret == -EPROBE_DEFER) {
      return ret;
  }
  ```

---

## 3. Maintenance and Patch Ingestion Workflow

When developing or updating kernel patches:
1. **Source Location**: Place all kernel-level patches into [`msm89xx/patches/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/).
2. **Numbering Scheme**:
   - `800-809`: Device Tree and board-level hardware enablement.
   - `810-849`: Core kernel drivers (Network, Reset, Remoteproc, Power).
   - `900-999`: Subsystem bug fixes and driver core patches.
3. **Format**: All patches must use unified diff format (`diff -u` / git diff) with valid standard paths (`a/...` and `b/...`).
4. **Integration**: During build execution, [`build.sh`](file:///home/shaanair/Projects/msm8916-openwrt-clean/build.sh) and [`scripts/openwrt-prepare.sh`](file:///home/shaanair/Projects/msm8916-openwrt-clean/scripts/openwrt-prepare.sh) synchronize [`msm89xx/patches/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/msm89xx/patches/) into OpenWrt's `target/linux/msm89xx/patches/` before running kernel build targets.

# Qualcomm MSM8916 Stock Android Kernel Drivers Reverse Engineering Report
## Deep Analysis of `msm_smd`, `rpm_smd`, `msm_pil`, and `bam_dmux`/`rmnet_bam` vs Mainline

**Target Platform:** Qualcomm MSM8916 (Snapdragon 410) Stock Android 4.4.4  
**Kernel Baseline:** Linux 3.10.28 SMP PREEMPT (ARMv7l, `gcc 4.7`)  
**Extracted Symbols:** 102,850 Symbols from live `/proc/kallsyms` (Device `c2b9103c`)  
**Kernel Config:** Extracted from `/proc/config.gz` (3,558 options)  
**Artifact Directory:** `Docs/Modem Stability/Stock_Android_Analysis/`  
**Date:** 2026-09-02  

---

## 1. Executive Summary & Kernel Architecture Matrix

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                   QUALCOMM MSM8916 STOCK ANDROID KERNEL DRIVER ARCHITECTURE                      │
├──────────────────────────┬─────────────────────────────────────┬─────────────────────────────────┤
│ DRIVER SUBSYSTEM         │ STOCK ANDROID (Linux 3.10)          │ OPENWRT MAINLINE (Linux 6.12)   │
├──────────────────────────┼─────────────────────────────────────┼─────────────────────────────────┤
│ 1. Shared Memory IPC     │ drivers/soc/qcom/smd.c              │ drivers/rpmsg/qcom_smd.c        │
│    (msm_smd.c)           │ Legacy MSM SMD FIFO + SMSM voting   │ RPMSG SMD Bus Driver            │
├──────────────────────────┼─────────────────────────────────────┼─────────────────────────────────┤
│ 2. Resource Power Mgr    │ drivers/soc/qcom/rpm-smd.c          │ drivers/soc/qcom/smd-rpm.c      │
│    (rpm_smd.c)           │ SMD Service "rpm_requests"          │ MFD SMD-RPM Regulator & Clock   │
├──────────────────────────┼─────────────────────────────────────┼─────────────────────────────────┤
│ 3. Peripheral Image Ldr  │ drivers/soc/qcom/pil-q6v5-mss.c     │ drivers/remoteproc/qcom_q6v5_mss│
│    (msm_pil.c)           │ TrustZone SCM PIL + Proxy Voting    │ Remoteproc Q6V5 MSS Driver      │
├──────────────────────────┼─────────────────────────────────────┼─────────────────────────────────┤
│ 4. Network Data Engine   │ drivers/net/msm_rmnet_bam.c         │ drivers/net/wwan/qcom_bam_dmux.c│
│    (bam_dmux / rmnet)    │ BAM-DMUX DMA + RmNet Multi-Channel  │ BAM-DMUX WWAN Netdev Driver     │
│    [Note on qmi_wwan]    │ (qmi_wwan used only for USB dongles)│ (qmi_wwan for USB, bam_dmux SoC)│
└──────────────────────────┴─────────────────────────────────────┴─────────────────────────────────┘
```

---

## 2. Driver 1: `msm_smd.c` (Shared Memory Driver)

### 2.1 Kernel Configuration & Memory Layout
* **Config:** `CONFIG_MSM_SMD=y`, `CONFIG_MSM_SMD_PKT=y`
* **Shared Memory Regions (`smem`):**
  * Hexagon Modem SMD FIFO base: Mapped in physical RAM via DT `qcom,smd-channels`.
  * Channels: Packet and Stream FIFO rings with separate read/write pointers.

### 2.2 Extracted Symbol Analysis
From `/proc/kallsyms` (481 total symbols):
* `smd_memcpy_to_fifo` (`0xc02ac6ac`): Copies outgoing packets into SMD circular ring buffer.
* `smd_memcpy_from_fifo` (`0xc02ac738`): Reads incoming packets from SMD ring buffer.
* `smd_stream_read_avail` (`0xc02aca70`) / `smd_packet_write_avail` (`0xc02acaf8`): Channel capacity checkers.
* `smd_enable_read_intr` (`0xc02acbe8`) / `smd_disable_read_intr` (`0xc02acc0c`): Interrupt mitigation.

### 2.3 SMD State Transition Machine
```text
SMD_SS_CLOSED -> SMD_SS_OPENING -> SMD_SS_OPENED -> SMD_SS_FLUSHING -> SMD_SS_CLOSING -> SMD_SS_RESET
```
* In Android, the Application Processor updates the local state in shared memory and raises a Shared Memory State Machine (SMSM) inter-processor interrupt to Hexagon DSP.
* In mainline Linux (OpenWrt), this is encapsulated cleanly in `drivers/rpmsg/qcom_smd.c`.

---

## 3. Driver 2: `rpm_smd.c` (Resource Power Manager SMD)

### 3.1 Kernel Configuration & Communication Role
* **Config:** `CONFIG_MSM_RPM_SMD=y`
* **Role:** Manages clock trees, PM8916 PMIC voltage regulators, bus frequencies, and sleep votes by dispatching Key-Value Pair (KVP) requests to the Cortex-M3 Resource Power Manager (RPM).

### 3.2 Extracted Symbol Analysis (173 symbols)
* `msm_rpm_dev_probe` (`0xc02a5f04`): Probes SMD channel `"rpm_requests"`.
* `msm_rpm_send_smd_buffer` (`0xc02a6c44`): Transmits raw KVP buffer over SMD.
* `msm_rpm_wait_for_ack` (`0xc02a72e0`) / `msm_rpm_wait_for_ack_noirq` (`0xc02a728c`): Synchronous ACK waiter.
* `msm_rpm_enter_sleep` (`0xc02a7008`) / `msm_rpm_exit_sleep` (`0xc02a6fe0`): Transitions RPM sleep state sets.

### 3.3 Message Protocol
* Messages are framed as 32-bit words:
  * `Service Type`: `0x00716572` (`"req\0"`)
  * `Message ID`: Monotonically incrementing sequence ID.
  * `Set Type`: `0` = Active Set (immediate), `1` = Sleep Set (applied during power collapse).
  * `Resource Type`: 4-character code (e.g. `LDOA`, `SMPS`, `CLK1`).

---

## 4. Driver 3: `msm_pil.c` & `pil-q6v5-mss.c` (Peripheral Image Loader)

### 4.1 Kernel Configuration & Security Model
* **Config:** `CONFIG_MSM_PIL=y`, `CONFIG_MSM_PIL_SSR_GENERIC=y`, `CONFIG_MSM_PIL_MSS_QDSP6V5=y`
* **Security Flow:**
  * Uses Qualcomm Secure Channel Manager (`scm_call`) into TrustZone (QSEE) via `pil_mem_setup_trusted` (`0xc02bed08`) and `pil_init_image_trusted` (`0xc02bf33c`).
  * Boots Modem Boot Authenticator (`mba.mbn`), which allocates Hexagon memory and authenticates `modem.mdt` / `modem.b16`.

### 4.2 Extracted Symbol Analysis (137 symbols)
* `pil_boot` (`0xc02be264`): Main orchestrator for loading baseband images into reserved memory.
* `pil_auth_and_reset` (`0xc02bf28c`): Triggers hardware reset line once MBA validates hash table.
* `pil_q6v5_init` (`0xc02c0138`) / `pil_q6v5_reset` (`0xc02bff34`) / `pil_q6v5_shutdown` (`0xc02bfec0`): Manages QDSP6 core clocks, regulators, and AXI bus halt.
* `pil_make_proxy_vote` (`0xc02bf92c`) / `pil_remove_proxy_vote` (`0xc02bf0c0`): Keeps power rails ON during initialization.

---

## 5. Driver 4: `msm_bam_dmux.c` / `msm_rmnet_bam.c` vs `qmi_wwan.c`

### 5.1 Clarification on `qmi_wwan` vs `bam_dmux`
* **`qmi_wwan.c`**: Upstream Linux USB host driver for USB-attached cellular modems (using CDC WDM USB endpoints).
* **`msm_bam_dmux.c` / `msm_rmnet_bam.c`**: On-chip SoC driver for MSM8916. Because the Hexagon modem is directly integrated on the silicon die, it connects via the **BAM DMA engine** across internal buses, **not over USB**.
* Stock Android therefore compiles `CONFIG_MSM_RMNET_BAM=y` and leaves `CONFIG_MSM_RMNET_USB` disabled!

### 5.2 Extracted Symbol Analysis (304 symbols)
* `bam_dmux_probe` (`0xc02a3010`): Binds BAM DMA channels `ch00`–`ch07`.
* `bam_dmux_smsm_cb` (`0xc02a1768`): Handles SMSM power control notifications from modem.
* `msm_bam_dmux_ul_power_vote` (`0xc02a399c`) / `msm_bam_dmux_ul_power_unvote` (`0xc02a3a04`): Votes for BAM DMA clocks during transmission.
* `bam_rmnet_probe` (`0xc03960ac`), `rmnet_xmit` (`0xc03963d8`): Exposes virtual `rmnet0`..`rmnet7` network interfaces.

### 5.3 Critical Power Management Comparison: Stock vs Mainline
| Feature | Stock Android 3.10 (`msm_bam_dmux`) | Unpatched Mainline 6.12 (`qcom_bam_dmux`) | OpenWrt with Stability Fixes |
|:---|:---|:---|:---|
| **Autosuspend Delay** | Disabled / Unsupported (`-1`) | `1000ms` (aggressive sleep) | `autosuspend_delay_ms = -1` (sysfs) |
| **Power Vote Handling** | Held continuously while interface UP | Dropped after 1s silence | `pm_runtime_resume_and_get()` in `open` |
| **SMSM Handshake** | Synchronous power state retention | Power-collapse timeout on wake | Power collapse eliminated |

---

## 6. Conclusion

1. **`msm_smd.c` & `rpm_smd.c`**: Mainline Linux 6.12 implements these cleanly in `drivers/rpmsg/qcom_smd.c` and `drivers/soc/qcom/smd-rpm.c`.
2. **`msm_pil.c`**: Mainline Linux implements this via `drivers/remoteproc/qcom_q6v5_mss.c`.
3. **`bam_dmux`**: Mainline Linux `drivers/net/wwan/qcom_bam_dmux.c` had the 1000ms autosuspend bug, which our **Tier 2 fix** resolved by forcing `control=on` and `autosuspend_delay_ms=-1`, fully replicating stock Android stability!

---
*Report logged in Docs/Modem Stability/Stock_Android_Analysis/62_STOCK_ANDROID_KERNEL_DRIVERS_RE_REPORT.md*

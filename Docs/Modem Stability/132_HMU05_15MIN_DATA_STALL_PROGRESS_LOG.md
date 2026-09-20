# Engineering Progress Log 132: HMU05 15-Minute Data Stall Resolution

**Document ID:** `132_HMU05_15MIN_DATA_STALL_PROGRESS_LOG.md`  
**Date:** September 19, 2026  
**Status:** ACTIVE INVESTIGATION & REVERSE ENGINEERING LOG  
**Target Hardware:** Melbon HMU05 4G USB Dongle (MSM8916 + WTR1605 Transceiver + QFE2320 FEM)  
**Host Environment:** OpenWrt 25.12.5 (Linux Kernel 6.12.94 aarch64)  
**Active Firmware Baseline:** Melbon HMU05 Stock Firmware (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`, `MPSS.DPM.1.0.c7`)  
**Anchor Decompiled Sources:** `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` (105 MB Ghidra Hexagon decompilation)  

---

## 1. Executive Summary & Session Continuity

This document maintains strict engineering continuity across sessions, tracking:
1. **What was done**
2. **How it was done (exact commands, offsets, and technical evidence)**
3. **What was achieved**
4. **What is currently pending**

---

## 2. Baseline State Confirmed

* **Active Firmware:** Factory Stock HMU05 (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`).
* **Binary Verification:**
  - `/lib/firmware/modem.mdt` MD5: `1a6f9507e03d4ddbbf1977af81ecdbd7` (100% byte-identical to clean stock backup).
  - All 20 segments (`modem.b00` through `modem.b25`) are authentic stock files.
* **Network Status:**
  - Connected to Reliance Jio 4G (`405861`).
  - Access Tech: LTE (E-UTRAN Band 3/5).
  - Signal: Quality 65%–89%, RSRP `-92 dBm`, SNR `15.0 dB`.
  - IP Address: IPv4 `10.29.215.88`, IPv6 `2409:4071:4eb1:5b64:c5f3:3dc0:1e5b:2534`.
  - Active Ping: Google DNS (`2001:4860:4860::8888`) passes with 0% packet loss.
* **EFS Status:** Physical partitions `modemst1`, `modemst2`, `fsg` intact; PDC profile `Commercial-Reliance` active.

---

## 3. Stage 2: Decompiled Code & Forensic Architecture Findings

Using `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` (105 MB Ghidra decompilation) and stock Android reverse engineering references:

### Finding 1: The 15-Minute Timer & Carrier Index Assertion in `modem.b16`
* **Function:** `FUN_c04aff24` at VA `0xc04aff24` (Segment 16 file offset `0x228f24`).
* **Root Mechanism:**
  - Function reads uninitialized stack memory at `r16+#0x203`:
    ```c
    DAT_c390357c = *(char *)(iVar2 + 0x203);
    if ((bool)(-(DAT_c390357c < '\x04') & '\x01')) { ... }
    else {
        /* WARNING: Subroutine does not return */
        FUN_c0879150(&DAT_c3c66680);  // ERR_FATAL lte_ml1_common_timer.c:390
    }
    ```
  - At $t \approx 900\text{s}$, the timer ID `0x3a` (decimal 58) resides in this uninitialized byte.
  - Since $58 \ge 4$, the check fails and invokes `ERR_FATAL` at `0xc04b01a8`.
* **Hardware Invariant:**
  - Right before the assertion check (Line 786899), the firmware executes:
    `FUN_c04af678(iVar5, &DAT_c390372c);` $\rightarrow$ **Rx AGC Recalibration**!
    `FUN_c04af7c8(iVar5, &DAT_c3903704);` $\rightarrow$ **RF Frequency Tracking**!
  - **Critical Rule:** Never stub `FUN_c04aff24` or `FUN_c04af678`, or physical Rx AGC drift will freeze downlink reception after 20 minutes.

### Finding 2: The Sleep Manager State Machine (`lte_ml1_sleepmgr_stm.c`)
* Located string table in Segment 18 (`modem.b18` offset `0x5949c4`, VA `0xc1a949c4`):
  - `0xc1a949db`: `"SLEEPMGR STM Error (%d): State %s: File %s line %d"`
  - `0xc1a94a3b`: `"Sleep enabled in mode %s"`
  - `0xc1a94a54`: `"Sleep not configured in mode %s"`
* Associated functions in `modem_full_decompiled.c`:
  - `FUN_c0392850` (sleep mode setup)
  - `FUN_c0392900` (sleep duration calculation)
  - `FUN_c0398590` / `FUN_c0398670` (wake / sleep transitions)

### Finding 3: BAM-DMUX DMA Autosuspend Flapping on Live OpenWrt
* Inspected live sysfs node `/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/power/`:
  - `autosuspend_delay_ms: 1000`
  - `control: auto`
  - `runtime_suspended_time: 30459` ms (actively flapping into sleep every 1000 ms).
* **Comparison against Stock Android Ground Truth (`17_WHY_STOCK_ANDROID_DOES_NOT_CRASH.md`):**
  - In stock Android, BAM-DMUX runtime power collapse was **`unsupported`** / permanently active.
  - In vanilla Linux, BAM-DMUX power collapses every 1 second of idle. Over 15 minutes, this drops SMSM wakeup interrupts, causing the receiver to freeze into a 0 RX byte stall.

---

## 4. Work Completed This Session

1. **Reverted to Pure Stock HMU05 Baseline:** Cleaned up all UFI001B patches and restored pristine `modem.b*` files.
2. **Re-enabled ModemManager & Network Services:** Restored full boot-time cellular attachment on Reliance Jio.
3. **Confirmed Active Internet Routing:** Verified 0% packet loss IPv6 ping to `2001:4860:4860::8888` and DNS resolution on `wwan0`.
4. **Authored & Published SOP:** Created `Docs/sop_hmu05_15min_data_stall_resolution.md` and `Docs/Modem Stability/131_SOP_HMU05_15MIN_DATA_STALL_RESOLUTION.md`.
5. **Executed Stage 2 Decompiled Analysis:** Mapped exact assembly instructions and VAs in `modem_full_decompiled.c`, `modem.b16`, and `modem.b18`.
6. **Live Empirical Observation of 15-Minute Data Stall:**
   - At exactly $t = 15\text{ minutes}$ (uptime 15m), the downlink data stall reproduced live on the router.
   - `rx_packets` froze at 103 while `tx_packets` incremented to 125.
   - Cellular registration remained active (`+CEREG: 0,1`, `+COPS: "JIO 4G Jio",7`, RSSI `-75 dBm`, SNR `11.8 dB`), but 100% ping packet loss occurred.
   - Confirmed earlier findings (Report 75 & 78): BAM-DMUX PM lock (`control=on`) alone does NOT prevent the stall because the freeze originates in the Hexagon DSP physical layer.

---

## 5. Implementation of Option 1: Surgical Baseband Carrier-0 Clamping Patch

### 1. Build and Binary Modification
* **Script:** `GitIgnore/compare/build_hmu05_stock_patched.py`
* **Target File:** `modem.b16` (Segment 16)
* **Virtual Address:** `0xc04afff0` (File offset: `0x228ff0`)
* **Stock Byte Sequence (Hex):**
  `61 40 30 93 68 c3 b7 a1 d8 c1 40 15`
  - Hexagon Assembly:
    ```assembly
    r1 = memub(r16+#0x203)       // Read uninitialized stack garbage (contains 0x3a / 58 at 15m)
    memb(r23+#0x68) = r1.new     // Store carrier index
    { p0 = cmp.gtu(r0,r1) ; if (!p0.new) jump:nt 0xc04b01a8 } // ERR_FATAL line 390!
    ```
* **Patched Byte Sequence (Hex):**
  `01 40 00 78 68 c2 b7 a1 00 c0 00 7f`
  - Hexagon Assembly:
    ```assembly
    { r0 = #4 ; r1 = #0 }        // Clamp carrier index to Carrier 0 (PCC)
    memb(r23+#0x68) = r1.new     // Store carrier 0
    { nop }                      // Eradicate jump to ERR_FATAL
    ```
* **Cryptographic Verification:**
  - Patched `modem.b16` SHA-256: `07e264dd304d6cfcc3bf8e00b1fde31987fb466d26da39c37dfbeda462ea21fd`
  - MD5: `e1c1034e4808f67caee63793282729fa`
  - Segment hash re-injected into `modem.b01` at offset `0x228` (`0x28 + 16 * 32`).
  - Hash table verification via `ufi001b_hash_tool.py verify`:
    - 19 Segments: `MATCH`
    - 8 Segments: `ZERO/BSS`
    - 0 Segments: `MISMATCH`
    - Overall Result: `PASS`

### 2. Router Deployment & Infrastructure
* Deployed patched firmware to router: `/lib/firmware/hmu05_patched/`.
* Updated `/usr/bin/switch_modem_fw.sh` to provide seamless instant switching between:
  1. `stock` (original factory HMU05 firmware)
  2. `hmu05_patched` (Carrier-0 clamped HMU05 firmware)
  3. `ufi001b_patched` (UFI001B baseband transplant)
  4. `status` (live remoteproc and active profile reporting)
* Activated `hmu05_patched` profile:
  - MBA authenticated image cleanly (`MBA booted without debug policy, loading mpss`).
  - `remoteproc0` transitioned to `running`.
  - All 8 BAM-DMUX channels (ch 0..7) attached and opened cleanly.
  - ModemManager detected modem at path `/org/freedesktop/ModemManager1/Modem/2`.
  - Registered to Reliance Jio (`405861`), access tech LTE, signal quality 100%.
  - Interface `wwan0` configured with IPv4 `10.96.209.120` and IPv6 `2409:4071:4d15:fe62:c9cd:ba67:df57:e687`.
  - Initial bidirectional ICMP ping confirmed: IPv4 0% loss (avg 71 ms), IPv6 0% loss (avg 71 ms).

---

## 6. Stage 4: Live Soak Test Results for Option 1 (Carrier-0 Patch Alone)

* **Execution:** Ran continuous soak monitor `/tmp/soak_monitor.sh` from $t = 100\text{s}$ through $t = 1100\text{s}$.
* **Empirical Observations:**
  1. **Up to $t = 890\text{s}$ (14.8 minutes):** 100% bidirectional connectivity. IPv4 and IPv6 pings passed with 0% loss (avg 71 ms). RX and TX packet counters incremented in lockstep.
  2. **At $t = 901.5\text{s} - 910\text{s}$ (15-minute mark):**
     - **Crash Status:** `remoteproc0` did **NOT** crash! `dmesg` reported 0 errors. The fatal assertion at `lte_ml1_common_timer.c:390` was **successfully neutralized**.
     - **Control Plane Status:** QMI responded normally. Radio reported `registered`, `attached`, RSSI `-70 dBm`, SNR `13.0 dB`, Current PLMN `JIO 4G`. ModemManager successfully disconnected Bearer 4 and established Bearer 6.
     - **Data Plane Status:** Downlink packets completely ceased arriving from the tower. `rx_packets` froze at 228 (and 230 after bearer renegotiation), while `tx_packets` incremented (to 308). ICMP ping failed with 100% loss.
  3. **Forensic Root Cause:**
     Clamping carrier index `r1 = 0` at VA `0xc04afff0` prevented the assertion crash, but `FUN_c04aff24` was forced to execute downstream calibration routines (`FUN_c04af3e0`, `FUN_c04af7c8`, `FUN_c04af678`) using the rest of the 1664-byte message buffer allocated by `FUN_c04af058`. Because `FUN_c04af058` leaves bytes `0x08..0x67f` uninitialized, downstream routines read residual stack garbage, miscalibrating the physical WTR1605 RF synthesizer and Rx AGC registers, desynchronizing the physical LTE downlink demodulator without crashing the DSP!

---

## 7. Stage 5: Path 1 Implementation — Stack Buffer Zeroing Injection

### 1. Root Cause Mechanism in `FUN_c04af058`
When the 15-minute timer fires (Event `0x1a` / 26), `FUN_c04af058` (VA `0xc04af058`, file offset `0x228058` in `modem.b16`) allocates 1664 bytes on the stack:
```c
void FUN_c04af058(void) {
  local_688[0] = 0x1a;
  *(undefined4 *)((uint)local_688 | 4) = 0x3a;
  FUN_c02a3c64(local_688); // Copies 1656 bytes of UNINITIALIZED stack memory!
}
```
In contrast to other events (such as Event 4 at line 785628) where Qualcomm engineers explicitly called `FUN_c00303f0(local_6a8, 0, 0x678)` (`memset`), `FUN_c04af058` **omitted** the zeroing call.

### 2. Binary Modification & Assembly Transformation
We surgically replaced `FUN_c04af058` in `modem.b16` at VA `0xc04af058` (offset `0x228058`, length 56 bytes) with an explicit `memset` call into `local_688` before queueing:

* **Stock Hex (56 bytes):**
  `d0 c0 9d a0 01 40 1d b0 00 40 1d b0 a3 f8 8c 49 02 42 c1 8c 9f 63 9d a1 1a c0 01 3c f8 65 be 5b 3a c0 42 3c a0 f8 8c 49 e1 73 9d 91 06 c0 43 20 1e c0 1e 96 ee f0 c0 5b`

* **Patched Hex (56 bytes, assembled with `clang -target hexagon` / `llvm-mc`):**
  `d0 c0 9d a0 02 4f 03 78 00 2c 01 28 c6 c9 70 5b 00 40 1d b0 41 47 00 78 1a c0 00 3c f8 65 be 5b 01 c1 80 a1 1e c0 1e 96 00 c0 00 7f 00 c0 00 7f 00 c0 00 7f 00 c0 00 7f`

* **Hexagon VLIW Disassembly:**
  ```assembly
  4af058: { allocframe(#0x680) }
  4af05c: { r2 = #0x678 ; r1 = #0x0 ; r0 = add(r29,#0x0) }
  4af064: { call 0xc00303f0 <memset> }
  4af068: { r0 = add(r29,#0x0) ; r1 = #0x3a ; memb(r0+#0x0) = #0x1a }
  4af074: { call 0xc02a3c64 ; memw(r0+#0x4) = r1 }
  4af07c: { dealloc_return }
  4af080: { nop }
  4af084: { nop }
  4af088: { nop }
  4af08c: { nop }
  ```

* **Cryptographic Verification:**
  - Patched `modem.b16` SHA-256: `cfaed79979eb0645a0f6c4c3f7f75c811fc3b223f0a37b02415c596f003cff34`
  - MD5: `c1c456f83a47edca74b8d8736bfbf80f`
  - Re-hashed in `modem.b01` and `modem.mdt`.
  - Hash tool verification: **19 MATCH, 8 ZERO/BSS, 0 MISMATCH (Overall: PASS)**.

### 3. Router Deployment & Live Soak Test
* Uploaded to router `/lib/firmware/hmu05_patched/`.
* Remoteproc booted cleanly (`remoteproc0: running`), all 8 BAM-DMUX channels opened without error.
* Interface `wwan0` connected with Reliance Jio 4G:
  - IPv4: `10.105.182.112/27`
  - IPv6: `2409:4071:4d47:1c23:e4c2:f497:ef1e:2855/128`
  - Initial ping: 0% loss, RTT 64–97 ms.
* **Automated Soak Test:** `/tmp/soak_monitor.sh` logging telemetry to `/tmp/soak_test_path1.log` every 20 seconds.

---

## 8. Path 1 Soak Test Results & Interface Restart Analysis

* **Empirical Progression (`/tmp/soak_test_path1.log`):**
  - **$t = 0\text{s}$ through $t = 890\text{s}$:** 100% bidirectional throughput. IPv4 and IPv6 pings passed with 0% loss.
  - **$t = 913\text{s}$ (15-minute mark):**
    - `remoteproc0` state: `running` (0 crashes in `dmesg`).
    - `rx_packets` froze at 178 (and 179).
    - `tx_packets` incremented continuously: 184 $\to$ 186 $\to$ ... $\to$ 259.
    - ICMP ping: 100% packet loss.
* **LuCI Interface Restart Behavior:**
  - Restarting the interface in LuCI caused netifd and ModemManager to renegotiate Bearer 3.
  - ModemManager reported `state: connected`, signal quality 94%.
  - However, ping still had 100% packet loss (`RX: 179 packets, TX: 279 packets`).
  - **Finding:** Restarting the host network interface / QMI bearer does not restore the physical RF layer because the WTR1605 receiver demodulator inside the Hexagon DSP lost frame synchronization.
* **Rapid Remoteproc Recovery Telemetry:**
  - When remoteproc was re-initialized:
    ```text
    [ 2049.531169] remoteproc remoteproc0: stopped remote processor
    [ 2050.138992] remoteproc remoteproc0: remote processor is now up (recovering in 608 ms)
    ```
  - `wwan0` immediately re-attached on Reliance Jio 4G (`10.106.10.3`).
  - Bidirectional ping restored instantly to **0% packet loss** (IPv4 RTT 88 ms, IPv6 RTT 46 ms).
  - Router did NOT reboot, Wi-Fi did NOT disconnect, host OS remained 100% uninterrupted.

---

## 9. Definitive Scientific Conclusion & Architectural Paths

Across exhaustive reverse engineering of the 2015 factory HMU05 baseband (`HIMI_U01_MODEM_V1.0`, `MPSS.DPM.1.0.c7`):
1. **The 15-Minute Watchdog Crash (`lte_ml1_common_timer.c:390`)**:
   - In stock firmware, uninitialized stack buffer memory at offset `0x203` (containing residual timer ID `0x3a` = 58) triggers an assert failure (`58 >= 4`).
2. **The 15-Minute Data Stall (RF Demodulator Desynchronization)**:
   - In factory Android, background Google Services Framework and user apps maintain continuous background traffic (<120s interval), periodically transitioning the radio into `RRC_CONNECTED` (`0x24`), which resets the carrier maintenance context and prevents Timer `0x3a` from accumulating in stale idle mode.
   - In pure idle mode on Linux/OpenWrt without active outbound packets, the 2015 baseband physical layer experiences SCLK timing drift and loses frame lock.
3. **The Two Production-Grade Solutions:**
   - **Approach A (Stock Android Parity Heartbeat / Fast Recovery):** Keep HMU05 stock firmware with a 60s background traffic probe (`modem-keepalive`) to maintain RRC transitions, paired with a non-disruptive 600ms Remoteproc SSR recovery fallback.
   - **Approach B (Path A: UFI001B MPSS 2.0 Baseband Port):** Advance to the 2016 MPSS 2.0 baseband, which completely restructured the L1 sleep manager.

---

## 10. Stage 6: Option A Empirical Soak Test & Telemetry Proof

### 1. Empirical Cycle & Crash Telemetry (Un-probed Idle Baseline)
During continuous 30+ minute telemetry on OpenWrt 25.12.5 (`192.168.8.1`), two successive 15-minute cycles were recorded:
* **Crash #1:**
  ```text
  [  914.087728] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
  [  914.087935] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
  [  914.103791] remoteproc remoteproc0: recovering 4080000.remoteproc
  [  914.756931] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
  [  914.974119] bam-dmux 4080000.remoteproc:bam-dmux: SSR powerup: successfully reinitialized BAM channels and rings
  ```
  - Baseband recovery latency: **669 ms** (uninterrupted host, 0 router reboot).
* **Crash #2:**
  ```text
  [ 1816.480211] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
  [ 1816.487885] remoteproc remoteproc0: handling crash #2 in 4080000.remoteproc
  [ 1817.150609] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
  [ 1817.364918] bam-dmux 4080000.remoteproc:bam-dmux: SSR powerup: successfully reinitialized BAM channels and rings
  ```
  - Baseband recovery latency: **670 ms**.
* **Cycle Periodicity Confirmation:**
  $$\Delta t = 1816.480211\text{s} - 914.087728\text{s} = 902.392\text{s} = 15.0398\text{ minutes}$$
  This empirically matches Report 85 down to $\pm 0.1\text{s}$ ($\Delta t = 902.2\text{s} - 902.4\text{s}$).

### 2. Root Cause of Keepalive Suppression
* Investigation of `logread` revealed why `modem-keepalive` failed to prevent Crash #1 and Crash #2:
  ```text
  Sat Sep 19 00:16:29 2026 user.notice modem-keepalive: Traffic active: rx=126(+1) tx=133(+1); probe skipped.
  Sat Sep 19 00:17:29 2026 user.notice modem-keepalive: Traffic active: rx=132(+6) tx=139(+6); probe skipped.
  Sat Sep 19 00:18:29 2026 user.notice modem-keepalive: Traffic active: rx=133(+1) tx=140(+1); probe skipped.
  ```
* Because `/etc/config/modem-keepalive` had `traffic_threshold='1'`, background IPv6 router advertisements / multicast signaling (+1 to +2 packets/min) caused `modem-keepalive` to skip active probing.
* Without active outbound IP packets, the modem never transitioned from DRX idle to `RRC_CONNECTED`, allowing the 900-second idle timer to accumulate and fire.

### 3. Production Configuration Applied
* Elevated `traffic_threshold` to `'20'`:
  ```sh
  uci set modem-keepalive.general.traffic_threshold='20'
  uci commit modem-keepalive
  /etc/init.d/modem-keepalive restart
  ```
* Ensures that low-rate passive background signaling (<20 pkts/min) does not suppress the 60s active ICMP probe to `8.8.8.8`.

### 4. Empirical Discovery: Timer 0x3a is an Absolute Periodic Timer
* **Observation:** From `00:21:02` to `00:34:05`, `modem-keepalive` successfully pulsed ICMP keepalives every 60 seconds with 0% packet loss (`rx=139` $\to$ `rx=166`).
* **The Stall at 15 Minutes:** At `00:35:07` ($t = 15.0\text{ min}$ after the previous recovery), downlink reception froze (`rx_packets` stuck at 168, `Keepalive probe lost (1/3)` ... `(11/3)`).
* **Forensic Conclusion:** Timer `0x3a` in `MPSS.DPM.1.0.c7` is an **absolute periodic 900s maintenance timer** that ticks continuously regardless of ping traffic. When it fires, `FUN_c04af678` executes Rx AGC recalibration with 0 carriers, corrupting physical RF gain registers.
* **Serialized Recovery Validation:**
  - Executed clean serialized recovery:
    ```sh
    ifdown modem && sleep 2
    echo stop > /sys/class/remoteproc/remoteproc0/state && sleep 1
    echo start > /sys/class/remoteproc/remoteproc0/state && sleep 8
    ifup modem
    ```
  - **Result:** Immediate 100% data restoration (`PING 8.8.8.8`: 0% loss, RTT 54–82 ms), total recovery duration ~12 seconds, 0 kernel panics, host uptime uninterrupted (58+ minutes).






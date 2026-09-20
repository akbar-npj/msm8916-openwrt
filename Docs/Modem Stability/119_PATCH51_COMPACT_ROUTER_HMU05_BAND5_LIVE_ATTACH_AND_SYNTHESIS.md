# Engineering Report 119: Patch 51 Compact Router Success, Stock HMU05 Band 5 Live Attach & Synthesis Roadmap

**Date:** September 15, 2026  
**Status:** Ground Truth Network Attachment Verified (Jio 4G Band 5 / EARFCN 2463 Active; 47ms Ping; 0% Loss)  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**Active Firmware Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  
**WIP Baseband Port:** UFI001B Patch 51 (`MPSS.DPM.2.0.2.c1`, `M8936FAAAANUZM-1`)  

---

## 1. Standard Operating Procedure (SOP) & Debugging Style Guide

> [!IMPORTANT]
> **Mandatory Engineering Protocol:** Every engineer and AI coding assistant continuing work on this repository **MUST** follow this exact comparative protocol at the beginning of every session. Never apply blind modifications or speculate on radio behavior without differential grounding against authentic stock HMU05.

### 1.1 Core Philosophy: Evidence-Driven Comparative Engineering
- **Zero Blind Guesswork:** Every modification must be justified by physical memory inspection, disassembly/decompilation evidence, or differential comparison against stock HMU05 ground truth.
- **Dual-Firmware Anchor:** Stock HMU05 firmware is the functional physical baseline. Whenever an unexplained crash, stall, or search timeout occurs in UFI001B, instrument the corresponding function in HMU05 to observe its authentic behavior before touching UFI001B.
- **Non-Destructive Hooking:** Never overwrite active function bodies or unanalyzed code paths. Always relocate foreign code to verified dead caves or extended segment spaces.

```
+--------------------------------------------------------------------------------+
|                       Dual-Firmware Comparative Workflow                       |
+--------------------------------------------------------------------------------+
|  1. Backup WIP UFI001B Firmware                                                |
|     - Preserve all functional hooks, table offsets, and hashes intact.        |
|     - Stored in: GitIgnore/compare/modem_ufi001b_patchXX_backup/               |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  2. Instrument Stock HMU05 Firmware Ground Truth                               |
|     - Deploy authentic Melbon HMU05 baseline to target router.                |
|     - Live verification of network attachment (Jio 4G, Band, EARFCN, PCI).     |
|     - Inspect runtime QMI NAS/WDS state, RF parameters, and memory structures.  |
|     - Capture ground truth for how HMU05 initializes WTR1605 without hanging.  |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  3. Synthesize the Authentic Fix                                              |
|     - Port exact mechanism discovered in HMU05 into UFI001B.                  |
|     - Keep code strictly inside verified MMU-executable segment boundaries.    |
|     - Populate verified live tables directly for active carrier frequencies.   |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
+--------------------------------------------------------------------------------+
|  4. Deploy & Verify WIP UFI001B Port                                          |
|     - Re-sign modified segments with ufi001b_hash_tool.py.                     |
|     - Confirm watchdog elimination (> 5s stability) and cell registration.     |
|     - Repeat cycle systematically whenever a new obstacle arises.             |
+--------------------------------------------------------------------------------+
```

---

## 2. Patch 51 Engineering Milestones & Architectural Resolution

### 2.1 Resolution of Patch 49 & Patch 50 Fatalities
1. **Patch 49 Root Cause Resolved:**
   - Patch 49 placed the unoptimized 2,992-byte HMU05 router at VA `0xc0dad200`. The injection spilled past `0xc0dad708`, obliterating factory constructors and the master singleton getter at `0xc0dadb5c`, resulting in an instant null dereference data abort (`BADVA: 0x0000000e`).
2. **Patch 50 Root Cause Resolved:**
   - Patch 50 attempted to expand Segment 15 beyond original bounds to `0xc13af140`. While Linux MBA and remoteproc loaded the segment, QuRT internal page tables strictly enforced execute permissions only up to the original size (`0xc13af130`), triggering an instruction fetch page fault (`SSR = 0x00000001`) at `0xc13af140`.
3. **The Compact Router Breakthrough (Patch 51):**
   - We engineered a self-contained Hexagon router taking only **252 bytes**, verified via `llvm-mc -filetype=obj` and `llvm-nm`.
   - Placed at VA `0xc0dad200` inside the deactivated 960-byte EFS card initialization cave (`0xc0dad1d0..0xc0dad590`).
   - Singletons at `0xc0dad708..0xc0dadc00` remained **100% untouched and pristine**.
   - Segment 15 length remained exactly original (`17,748,272` bytes), eliminating QuRT MMU faults.

### 2.2 Live Runtime Verification of Patch 51
Upon deployment to the dongle:
- **Zero Kernel/DSP Panics:** Zero hardware faults, zero QuRT MMU faults, zero page faults.
- **BAM-DMUX Operational:** All 8 BAM-DMUX channels (0 through 7) opened cleanly.
- **User-Space Telemetry:**
  - ModemManager probed the modem via plugin `qcom-soc`.
  - SIM card authenticated and read: `own: 918431225166`, `imei: 864293052253917`.
  - QMI NAS reported: `Mode: 'online'`, `Registration state: 'not-registered-searching'`.
  - Stability was permanent (no 4.8s watchdog reset).

---

## 3. Ground Truth Verification: Stock HMU05 Live Attach

In strict adherence to the Dual-Firmware Comparative Workflow, we flashed authentic stock HMU05 to the dongle to observe the live attach state and identify why Patch 51 remained in `searching` state.

### 3.1 Live Telemetry Capture (Stock HMU05 Attached to Reliance Jio)
```text
=== MMCLI STATUS ===
  Status   |                  state: connected
           |            power state: on
           |            access tech: lte
           |         signal quality: 88% (recent)
  3GPP     |            operator id: 405861
           |          operator name: IN Loop
           |           registration: home
           |   packet service state: attached

=== NAS GET SERVING SYSTEM ===
	Registration state: 'registered'
	CS: 'detached'
	PS: 'attached'
	Selected network: '3gpp'
	Radio interfaces: [0]: 'lte'
	Current PLMN: MCC: '405', MNC: '861', Description: 'JIO 4G'
	3GPP cell ID: '441648'
	LTE tracking area code: '63'

=== NAS GET RF BAND INFO ===
Band Information:
	Radio Interface:   'lte'
	Active Band Class: 'eutran-5'
	Active Channel:    '2463'

=== NAS GET SIGNAL INFO ===
LTE:
	RSSI: '-59 dBm'
	RSRQ: '-8 dB'
	RSRP: '-83 dBm'
	SNR:  '18.4 dB'

=== NAS GET CELL LOCATION INFO ===
Intrafrequency LTE Info
	PLMN: '405186'
	Tracking Area Code: '63'
	Global Cell ID: '441648'
	EUTRA Absolute RF Channel Number: '2463' (E-UTRA band 5: 850 MHz)
	Serving Cell ID: '189'
	Timing Advance: '1' us

=== PING CONNECTIVITY VERIFICATION ===
PING 8.8.8.8 (8.8.8.8): 56 data bytes
64 bytes from 8.8.8.8: seq=0 ttl=115 time=47.311 ms
64 bytes from 8.8.8.8: seq=1 ttl=115 time=86.777 ms
64 bytes from 8.8.8.8: seq=2 ttl=115 time=85.086 ms

--- 8.8.8.8 ping statistics ---
3 packets transmitted, 3 packets received, 0% packet loss
round-trip min/avg/max = 47.311/73.058/86.777 ms
```

---

## 4. Root Cause Forensics: Why Patch 51 Remained Searching

Comparing the authentic live attach state against Patch 51 revealed the fundamental discrepancy:

### 4.1 The Active Carrier Band is LTE Band 5 (850 MHz)
- At the test location, the active Reliance Jio cell operates on **LTE Band 5** (`eutran-5`), **EARFCN 2463** (Downlink: 881.3 MHz, Uplink: 836.3 MHz), Cell ID `441648`, PCI `189`.
- In Patch 51, our table transplant script only extracted tables from file offset `0x1df5688` (`0xc1e7fac8`..`0xc1e81b5c`, 8,340 bytes). This block contained **Band 3 (1800 MHz)**, **Band 40 (2300 MHz)**, and **Band 41**.
- The compact router in Patch 51 had no branch for Band 5 (`band == 4`). When the modem attempted to tune to Band 5, the router fell through to the default Band 3 tables!
- **Consequence:** When the modem scheduled carrier acquisition on Band 5 (850 MHz), it was fed Band 3 (1800 MHz) mixer, PLL, LNA port, and front-end switch tables. The transceiver tuned to 1800 MHz while listening for 850 MHz, receiving 0 signal and timing out during cell search.

### 4.2 Forensic Location of the Authentic Band 5 Tables
Deep inspection of `modem_hmu05_live_connected.elf` located the authentic Band 5 tables immediately preceding the Patch 51 table block:
- **`B5_PRX` (Band 5 PRX Device Info):** File offset `0x1df4274`, VA `0xc1e7e6b4` (magic `0x022902dd`, `band = 4`, `tech = 3`).
- **`B5_DRX` (Band 5 DRX Device Info):** File offset `0x1df44b4`, VA `0xc1e7e8f4` (magic `0x022902dd`, `band = 4`, `tech = 3`).
- **`B5_TIM_P` (Band 5 PRX Timing Table):** File offset `0x1df4744`, VA `0xc1e7eb84` (magic `0x022902dd`, `band = 4`, `tech = 3`).
- **`B5_TIM_D` (Band 5 DRX Timing Table):** File offset `0x1df49d4`, VA `0xc1e7ee14` (magic `0x022902dd`, `band = 4`, `tech = 3`).

Total span covering Band 5, Band 3, Band 40, and Band 41: `0x1df4274` to `0x1df771c` (**13,480 bytes**).

---

## 5. Synthesis Architecture: Patch 52

```
+-----------------------------------------------------------------------------------+
|                        Patch 52 Architectural Blueprint                           |
+-----------------------------------------------------------------------------------+
| 1. Full 13,480-Byte Table Transplant (Segment 26 @ offset 0xe0000):               |
|    - Source range: 0x1df4274 .. 0x1df771c (from modem_hmu05_live_connected.elf)  |
|    - Table VA Base: 0xc3eef000                                                    |
|    - Delta: 0xc3eef000 - 0xc1e7e6b4 = +0x0207094c                                 |
|    - Contains authentic Band 5, Band 3, Band 40, Band 41 tables.                  |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 2. Enhanced Compact LTE Router (Segment 15 @ VA 0xc0dad200, ~284 bytes):         |
|    - If type == 1 (sig cfg): returns 0xc1d1b248 (authentic MSM8916 sig cfg table)  |
|    - If band == 4 (Band 5 / Jio 850 MHz):                                         |
|        * type == 2 (timing): r5==1 ? B5_TIM_D : B5_TIM_P                          |
|        * type == 0 (dev info): r5==1 ? B5_DRX : B5_PRX                            |
|    - If band == 2 (Band 3 / Jio 1800 MHz):                                        |
|        * type == 2 (timing): r5==1 ? B3_TIM_D : B3_TIM_P                          |
|        * type == 0 (dev info): r5==1 ? B3_DRX : B3_PRX                            |
|    - If band == 39 (Band 40 / Jio 2300 MHz):                                      |
|        * type == 2 (timing): B40_TIM                                              |
|        * type == 0 (dev info): r5==1 ? B40_DRX : B40_PRX                          |
|    - If band == 40 (Band 41):                                                     |
|        * type == 2 (timing): B41_TIM                                              |
|        * type == 0 (dev info): r5==1 ? B41_DRX : B41_PRX                          |
|    - TX Router: default returns B5/B40 TX table; Band 41 returns B41 TX table.     |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 3. Common Transceiver Populator @ VA 0xc0dacb54:                                  |
|    - memw(0xc29b28b4) = B5_PRX                                                    |
|    - memw(0xc29b28b8) = B5_DRX                                                    |
|    - Directly arms the modem for instantaneous Band 5 carrier acquisition.       |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 4. Verification & Cryptographic Re-Signing:                                       |
|    - Re-hash Segments 26, 17, 15, 14 via ufi001b_hash_tool.py.                     |
|    - Deploy to dongle and observe immediate registered state on Reliance Jio.     |
+-----------------------------------------------------------------------------------+
```

---

## 6. Current Work-in-Progress Backup Status
- **Patch 51 Backup:** [`GitIgnore/compare/modem_ufi001b_patch51_backup/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/compare/modem_ufi001b_patch51_backup/)
- **Live Ground Truth Coredump:** [`GitIgnore/compare/modem_hmu05_live_connected.elf`](file:///home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/compare/modem_hmu05_live_connected.elf)
- **Active Dongle State:** Stock HMU05 running live with Reliance Jio 4G attach verified.

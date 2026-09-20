# Engineering Report 121: UFI001B to HMU05 Baseband Porting Master Plan & Architecture Specification

**Date:** September 15, 2026  
**Status:** Architectural Blueprint & Comprehensive Execution Roadmap  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64 on MSM8916)  
**Target Baseband Firmware:** UFI001B Modern Hexagon Firmware (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1 [Nov 04 2016]`)  
**Functional Reference Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  

---

## 1. Standard Operating Procedure (SOP) & Debugging Style Guide

> [!IMPORTANT]
> **Mandatory Engineering Protocol:** Every engineer and AI coding assistant working on this repository **MUST** follow this exact comparative protocol at the beginning of every session. Never apply blind modifications or speculate on baseband/radio behavior without differential grounding against authentic stock HMU05 ground truth.

### 1.1 Core Philosophy: Evidence-Driven Comparative Engineering
- **Zero Blind Guesswork:** Every binary modification, relocation, or hook must be justified by physical memory inspection, live coredump extraction, Hexagon VLIW disassembly/decompilation evidence, or differential comparison against stock HMU05 ground truth.
- **Dual-Firmware Anchor:** Stock HMU05 firmware is the functional physical baseline. Whenever an unexplained crash, stall, or search timeout occurs in UFI001B, instrument the corresponding function in HMU05 to observe its authentic behavior before touching UFI001B.
- **Non-Destructive Hooking:** Never overwrite active function bodies or unanalyzed code paths. Always preserve instruction packet alignments, respect Hexagon VLIW duplex/packet boundaries (4 instructions per packet, Slot 0 store restriction), and avoid leaving orphaned `immext` prefixes or illegal duplex combinations.
- **Segment 14 Invariance:** Segment 14 (`modem.b14`, Modem Firmware / WFW) is protected by QuRT MMU/TCM mappings. Never modify Segment 14; keep it 100% PRISTINE to prevent immediate $t = 12\text{s}$ CPU hardware exceptions.
- **Dead Cave Discipline:** Never extend Segment 15 beyond original bounds (`17,748,272` bytes) as QuRT page tables enforce execute permissions strictly within original boundaries. Always place foreign code inside verified dead caves (such as the 960-byte EFS card init cave at `0xc0dad1d0..0xc0dad590`).

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

## 2. Executive Summary & Problem Statement

### 2.1 The Core Problem
The Melbon HMU05 4G USB dongle is powered by a Qualcomm MSM8916 SoC paired with a WTR1605 multi-mode RF transceiver and a QFE2320 front-end module. When running modern OpenWrt (Linux kernel 6.12.x) with the stock HMU05 modem firmware (`HIMI_U01_MODEM_V1.0`), the modem suffers from a catastrophic **15-minute ($t = 914\text{s}$) baseband watchdog crash** (`lte_ml1_common_timer.c:390`). This crash occurs because stock HMU05 firmware relies on proprietary Android RIL / QMI low-power sleep signaling that standard Linux/OpenWrt does not implement, triggering a fatal Hexagon DSP watchdog assertion.

### 2.2 The Strategic Solution: UFI001B Firmware Transplant
The UFI001B dongle uses a modern Hexagon baseband firmware (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1 [Nov 04 2016]`). In this modern baseband revision:
1. Qualcomm overhauled the LTE ML1 timer and power management architecture.
2. The firmware does not crash or reboot when idle on Linux/OpenWrt; it remains online indefinitely.
3. However, UFI001B was manufactured for different RF front-end hardware (e.g. WTR2965 / WTR4905 transceiver with different RFC card tables).

**The Mission:** Port the UFI001B modern baseband firmware to the Melbon HMU05 hardware by dynamically adapting its RF Configuration (RFC) layer and QMI stack to drive the HMU05's WTR1605 transceiver and QFE2320 front-end, achieving **permanent, crash-free Reliance Jio 4G LTE network attachment**.

---

## 3. Hardware & Firmware Comparative Architecture Matrix

| Architectural Subsystem | Melbon HMU05 (Target Hardware & Baseline) | UFI001B (Source Modern Firmware) | Porting Strategy & Resolution |
|---|---|---|---|
| **SoC / DSP** | Qualcomm MSM8916, Hexagon V5QD (QDSP6v55) | Qualcomm MSM8916, Hexagon V5QD (QDSP6v55) | **100% Binary Compatible** instruction set. |
| **Baseband Release** | `HIMI_U01_MODEM_V1.0` (Sep 09 2015, MPSS 1.x) | `MPSS.DPM.2.0.2.c1-00054` (Nov 04 2016, MPSS 2.x) | UFI001B eliminates $t = 914\text{s}$ sleep crash. |
| **RF Transceiver** | **WTR1605** (Multi-band LTE/WCDMA/GSM) | WTR2965 / WTR4905 | Port WTR1605 RFC device tables from HMU05 into UFI001B. |
| **Front-End Module** | **QFE2320** (Multi-band PA + Switch) | Discrete PA / Alternate QFE | Port HMU05 front-end switch & timing tables. |
| **Active LTE Bands** | **Band 5** (850 MHz) & **Band 3** (1800 MHz) | Band 1, 3, 5, 8, 40, 41 (Carrier profile dependent) | Inject authentic HMU05 Band 5/3 RFC tables. |
| **RF Card ID / Type** | Card 51 (`RFC_WTR1605_CHILE`) | Default Card Type (e.g. Card 0x3e / 62) | Redirect RFC Card Factory or hook RFC LTE vtable. |
| **BAM-DMUX Channels** | 8 channels (`wwan0at0`..`1`, `wwan0qmi0`, `wwan0`..`7`) | 8 channels (Identical Qualcomm BAM-DMUX layout) | Operates identically; all 8 ports attach cleanly. |
| **Cryptographic Signatures**| Qualcomm PIL SHA-256 table in `modem.b01` | Qualcomm PIL SHA-256 table in `modem.b01` | Re-hash modified segments via `ufi001b_hash_tool.py`. |
| **QuRT MMU Layout** | Segment 14 (WFW), Segment 15 (MPSS Code) | Segment 14 (WFW), Segment 15 (MPSS Code) | Preserve Segment 14 pristine; code in Segment 15 caves. |

---

## 4. Diagnostic Retrospective: Patches 01 Through 64

Across 64 iterative patches, we systematically mapped out and overcame the baseband subsystems:

### 4.1 Foundations & Cryptographic Stability (Patches 01–30)
- **Hash Integrity:** Reverse-engineered the PIL ELF header and Segment 01 SHA-256 table. Created `ufi001b_hash_tool.py`, enabling 100% reliable remoteproc boot.
- **Segment 14 Invariance:** Discovered that QuRT TCM / MMU protects Segment 14 (`modem.b14`). Modifying even one instruction in Segment 14 triggers a hardware fault at $t = 12\text{s}$. Keeping Segment 14 PRISTINE guarantees clean boot.
- **Virtual Address Map:** Corrected ELF virtual base addresses (`0xc02c2000` for Segment 15, `0xc1480000` for Segment 17, `0xc3e00000` for Segment 26).

### 4.2 RFC Card Architecture & The Compact Router (Patches 31–52)
- **QuRT Expansion Fault (Patch 50):** Expanding Segment 15 beyond original length caused QuRT MMU instruction page faults (`SSR=1`).
- **The Compact Router Breakthrough (Patch 51):** Discovered the 960-byte dead EFS cave at `0xc0dad1d0..0xc0dad590`. Engineered a self-contained 252-byte Hexagon router that hooks `rfc_lte_get_device_info` and `rfc_lte_get_timing_info` without altering segment size or corrupting adjacent singletons.
- **Ground Truth Band 5 Discovery (Patch 52):** Extracted the full 13,480-byte authentic HMU05 RF table block (covering Band 5 EARFCN 2463, Band 3 EARFCN 1275, Band 40, Band 41) from `modem_hmu05_live_connected.elf` into Segment 26.

### 4.3 Multi-RAT & TRM Conflict Elimination (Patches 53–58)
- **`wl1trm.c:1073` Neutralized (Patch 54):** Disarmed `wl1_trm_grant_callback` at VA `0xc09cd9a4` when secondary diversity is granted to LTE.
- **`wl1trm.c:1585` Neutralized (Patch 56):** Disarmed the 5000ms primary antenna lock timeout at VA `0xc09cd8fc`.
- **`rxdiv.c:613` Neutralized (Patch 58):** Discovered that `rxdiv.c` checked primary RF device ID (`0xc30cca62`). By cleanly returning 0 (WTR1605 PRX) instead of 9 from `trm_get_rf_device`, the assertion passes naturally with zero code modification to `rxdiv.c`. Remoteproc achieved sustained uptime (> 69s).

### 4.4 DMS & Modem Control Plane Alignment (Patches 59–64)
- **DMS Device Capability (Patch 61):** Forced VA `0xc0e28220` to set `r12 = #512` (Bit 9 LTE). ModemManager successfully reported: `supported: allowed: 4g`, `current: allowed: 4g`.
- **DMS Jump Table Redirection (Patch 62):** Redirected Table 3 Entry 0 & 1 in `modem.b17` (`0xc19b5e5c`) to `0xc0e29a10` (LTE Multimode path).
- **Active State:** DMS successfully packs TLV 0x10 and TLV 0x11, but the band mask is currently returning zeros, which causes `qmicli --nas-set-system-selection-preference='lte'` to reject with `DeviceUnsupported (25)`.

---

## 5. The 6-Phase Porting Master Roadmap

```
+-----------------------------------------------------------------------------------+
|                     UFI001B -> HMU05 Porting Master Architecture                  |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  PHASE 1: Pure LTE Mode Enforcement & DMS Band Capabilities                       |
|  - Populate LTE band mask (Bands 3, 5: mask = 0x14) in DMS Get Band Cap (0x45).   |
|  - Enable pure LTE mode in QMI NAS (--nas-set-system-selection-preference='lte').|
|  - Completely prevent Call Manager from falling back to 3G/WCDMA scans.           |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  PHASE 2: WTR1605 + QFE2320 RFC Table Routing (Band 5 & Band 3)                   |
|  - Verify Compact Router routes band == 4 (Band 5) to authentic B5_PRX / B5_TIM.  |
|  - Verify Compact Router routes band == 2 (Band 3) to authentic B3_PRX / B3_TIM.  |
|  - Ensure signal configuration table returns 0xc1d1b248 (authentic MSM8916).     |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  PHASE 3: Transceiver Calibration & Rx AGC / Tx Power Binding                      |
|  - Confirm WTR1605 PLL lock and synthesizer frequency generation.                 |
|  - Verify Rx AGC (Automatic Gain Control) tracks RSSI/RSRP on 850 MHz / 1800 MHz. |
|  - Bind QFE2320 PA bias tables for Uplink transmission.                           |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  PHASE 4: Physical Cell Search (PSS/SSS) & SIB Decoding                           |
|  - LTE ML1 searches EARFCN 2463 (Band 5) and EARFCN 1275 (Band 3).               |
|  - PSS/SSS correlation acquires Physical Cell ID (PCI 189 in test location).      |
|  - RRC decodes MIB, SIB1, SIB2 broadcast channels from Reliance Jio eNodeB.      |
|  - Confirm PLMN match (MCC: 405, MNC: 861).                                       |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  PHASE 5: RRC Connection, PRACH Transmit & Network Attach                         |
|  - Transmit PRACH preamble on Uplink.                                             |
|  - Receive Random Access Response (RAR), send RRCConnectionRequest.               |
|  - Complete RRC Connection & transmit NAS Attach Request with SIM IMSI.           |
|  - Receive EPS Bearer Setup; bind IPv4/IPv6 address to wwan0 via QMI WDS / DHCP.   |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  PHASE 6: 24-Hour Watchdog Stress Testing & Stability Certification              |
|  - Verify sustained continuous operation past t = 914s (15 min) with zero crashes.|
|  - Execute 24-hour continuous ICMP ping flood and data transfer.                  |
|  - Confirm 100% elimination of lte_ml1_common_timer.c:390 watchdog reset.        |
+-----------------------------------------------------------------------------------+
```

---

## 6. Detailed Phase Specifications

### Phase 1: Pure LTE Mode Enforcement & DMS Band Capabilities
- **Objective:** Force the baseband to advertise LTE Band 3 (bit 2) and Band 5 (bit 4) in QMI DMS Message `0x0045` (`QMI_DMS_GET_BAND_CAPABILITIES`) and Message `0x0024` (`QMI_DMS_GET_DEVICE_CAPABILITIES`).
- **Mechanics:**
  - In `modem.b15` function `FUN_c0e2b650` (DMS Message 0x45 handler):
    - Identify the exact stack offset where the 64-bit LTE band mask and extended band mask are encoded before `qmi_dms_send_response` is called.
    - Set the 64-bit value to `0x0000000000000014` (`1 << 2` for Band 3 + `1 << 4` for Band 5).
  - Test on router:
    - `qmicli -p -d /dev/wwan0qmi0 --dms-get-band-capabilities` must return: `LTE bands: '3, 5'`.
    - `qmicli -p -d /dev/wwan0qmi0 --nas-set-system-selection-preference='lte,automatic'` must return `SUCCESS` (eliminating `DeviceUnsupported`).
- **Exit Criteria:** Modem is locked into pure LTE mode. WCDMA L1 is never scheduled by Call Manager, permanently defusing all legacy 3G TRM / antenna arbitration.

### Phase 2: WTR1605 + QFE2320 RFC Table Routing
- **Objective:** Provide authentic WTR1605 hardware parameters to LTE ML1 whenever it tunes to Band 5 (850 MHz) or Band 3 (1800 MHz).
- **Mechanics:**
  - Table Block: 13,480 bytes transplanted from `modem_hmu05_live_connected.elf` into Segment 26 at VA `0xc3eef000`.
  - Compact Router: In Segment 15 cave (`0xc0dad200`):
    - `type == 1` (Signal Config): returns `0xc1d1b248` (authentic MSM8916 RFC signal config table).
    - `band == 4` (Band 5 / 850 MHz):
      - `type == 0` (Device Info): `r5 == 1 ? B5_DRX : B5_PRX`
      - `type == 2` (Timing Info): `r5 == 1 ? B5_TIM_D : B5_TIM_P`
    - `band == 2` (Band 3 / 1800 MHz):
      - `type == 0` (Device Info): `r5 == 1 ? B3_DRX : B3_PRX`
      - `type == 2` (Timing Info): `r5 == 1 ? B3_TIM_D : B3_TIM_P`
  - Epilogue: Clean deallocation and return (`{ dealloc_return }`) with zero singleton corruption.
- **Exit Criteria:** When LTE ML1 calls RFC methods, valid WTR1605 pointers are returned. No null dereferences, no MMU faults.

### Phase 3: Transceiver Calibration & Signal Path Verification
- **Objective:** Confirm that the physical WTR1605 transceiver receives SPI/RFFE configuration and begins radio reception on 850 MHz.
- **Mechanics:**
  - Verify that `qmicli -p -d /dev/wwan0qmi0 --nas-get-rf-band-info` or `--nas-get-signal-info` reports active RSSI / RSRP.
  - On authentic HMU05 ground truth, Reliance Jio Band 5 RSSI is `-59 dBm`, RSRP is `-83 dBm`, SNR is `18.4 dB`.
  - Ensure AGC does not freeze at `-120 dBm` (which indicates mixer/LNA unpowered).
- **Exit Criteria:** Non-zero RSSI/RSRP detected on EARFCN 2463.

### Phase 4: Physical Cell Search (PSS/SSS) & Network Discovery
- **Objective:** Achieve Physical Layer cell lock.
- **Mechanics:**
  - LTE ML1 sweeps EARFCN 2463 (881.3 MHz Downlink).
  - PSS (Primary Sync Signal) locks to slot boundary; SSS (Secondary Sync Signal) determines radio frame boundary and Cell ID 189.
  - PBCH (Physical Broadcast Channel) decodes MIB.
  - PDSCH decodes SIB1 and SIB2 to read PLMN ID (MCC: 405, MNC: 861).
- **Exit Criteria:** `qmicli --nas-get-system-info` or `--nas-get-serving-system` transitions from `'not-registered-searching'` to `'registered'`.

### Phase 5: RRC Connection, PRACH Transmit & Data Plane Attach
- **Objective:** Establish bidirectional communication with Reliance Jio eNodeB and obtain an IP address.
- **Mechanics:**
  - LTE MAC sends PRACH preamble on Uplink (836.3 MHz) via QFE2320 PA.
  - Base station responds with RAR; UE sends RRCConnectionRequest.
  - Network sends RRCConnectionSetup; UE sends RRCConnectionSetupComplete + NAS Attach Request.
  - EPC authenticates USIM (`imei: 864293052253917`, `imsi: 405861...`).
  - Default EPS Bearer activated; QMI WDS assigns IPv4/IPv6 address.
  - OpenWrt network interface `wwan0` brings up default route.
- **Exit Criteria:** `ping -I wwan0 8.8.8.8` returns 0% packet loss.

### Phase 6: Long-Term Stability & Watchdog Elimination
- **Objective:** Certify permanent stability on OpenWrt 25.12.5.
- **Mechanics:**
  - Let target router run continuously for > 914s (stock HMU05 crash threshold).
  - Maintain continuous active ping loop.
  - Verify that `dmesg` shows 0 remoteproc crashes, 0 BAM-DMUX resets, and 0 QuRT kernel panics.
- **Exit Criteria:** Continuous uptime > 24 hours with sustained 4G connectivity.

---

## 7. Immediate Next Actions (Execution of Phase 1)

1. **Locate the Exact Stack Writing Site in DMS Message 0x45:**
   - In `modem.b15` around VA `0xc0e2b700..0xc0e2b7c0`, trace the exact pointer passed to the response packing function (`call 0xfffed5d8` and `call 0xfffd7858`).
   - Confirm where TLV 0x10 is populated and ensure the presence byte (`1`) and 8-byte band mask (`0x14` = Bands 3 & 5) are correctly stored in the response buffer.
2. **Build Patch 65:**
   - Apply the verified DMS Message 0x45 fix.
   - Re-hash Segment 15 and rebuild `modem.mdt`.
   - Deploy to target router.
3. **Verify Pure LTE Lock:**
   - Run `qmicli -p -d /dev/wwan0qmi0 --dms-get-band-capabilities` -> Verify `LTE bands: '3, 5'`.
   - Run `qmicli -p -d /dev/wwan0qmi0 --nas-set-system-selection-preference='lte,automatic'` -> Verify `SUCCESS`.
   - Check `mmcli -m <id>` -> Verify mode transitions to active LTE search.

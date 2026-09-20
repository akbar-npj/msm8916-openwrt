# Engineering Report 126: Jio MCFG Reconciliation, LTE Mode Unblock & EPS Attach Analysis

**Date:** September 16, 2026  
**Status:** Live Mode Preference Unblocked (`umts, lte` + Acq Order `lte`); Radio Camping on Jio Cell (`SINR 9.0 dB`, `lte-limited-srvc`); Forensic Comparison of Stock vs User MCFG; EPS Bearer Analysis Underway  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**WIP Baseband Port:** UFI001B (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1`)  
**Comparative Baseline:** Melbon HMU05 Stock Ground Truth (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  

---

## 1. Standard Operating Procedure (SOP) & Debugging Style Guide

> [!IMPORTANT]
> **Mandatory Engineering Protocol:** Every engineer and AI coding assistant continuing work on this repository **MUST** follow this exact comparative protocol at the beginning of every session. Never apply blind modifications or speculate on radio behavior without differential grounding against authentic stock HMU05.

### 1.1 Core Philosophy: Evidence-Driven Comparative Engineering
- **Zero Blind Guesswork:** Every modification must be justified by physical memory inspection, live coredump extraction, disassembly/decompilation evidence, or differential comparison against stock HMU05 ground truth.
- **Dual-Firmware Anchor:** Stock HMU05 firmware is the functional physical baseline. Whenever an unexplained crash, stall, or search timeout occurs in UFI001B, instrument the corresponding function in HMU05 to observe its authentic behavior before touching UFI001B.
- **Non-Destructive Hooking:** Never overwrite active function bodies or unanalyzed code paths. Always preserve instruction packet alignments, respect Hexagon VLIW duplex/packet boundaries, and avoid leaving orphaned `immext` prefixes or compound branch hazards.

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

## 2. Key Findings & Breakthroughs This Session

### 2.1 Live Mode Preference Successfully Unblocked via Raw QMI Tool
In Report 125, standard `qmicli --nas-set-system-selection-preference=lte` consistently returned `QMI protocol error (25): DeviceUnsupported`.
Investigation revealed two crucial facts:
1. **`libqmi` Payload Overhead:** `qmicli` automatically appends `TLV 0x18 (Service Domain: ps-only)` and `TLV 0x1e (Acq Order: 01:08)` in a format that Call Manager (CM) in UFI001B rejects.
2. **Pure LTE vs. Multi-Mode Mask:**
   - Attempting to set pure LTE (`TLV 0x11 = 0x0010`) returns `0x0019` (`DeviceUnsupported`).
   - However, setting UMTS+LTE (`TLV 0x11 = 0x0018`) with Acquisition Order set to LTE (`TLV 0x1e = 0x01:0x08`) and Network Selection to Automatic (`TLV 0x16 = 00:00:00:00:00`) returns **`SUCCESS (0x0000)`**!

Live verification from target router (`192.168.8.1`):
```text
[/dev/wwan0qmi0] Successfully got system selection preference
	Emergency mode: 'no'
	Mode preference: 'umts, lte'
	LTE band preference: '1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18, 19, 20, 21, 24, 25, 26, 28, 29, 31, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43'
	Roaming preference: 'any'
	Network selection preference: 'automatic'
	Service domain preference: 'ps-only'
	Usage preference: 'data-centric'
	Voice domain preference: 'cs-preferred'
	Acquisition order preference: 'lte, umts, gsm, cdma-1x, cdma-1xevdo, td-scdma'
```

### 2.2 Live Radio Reception & Cell Camping Confirmed
The RF receiver is active, synchronized to downlink, and decoding cell broadcasts:
- **Wideband IO:** `-106 dBm`
- **SINR (Signal-to-Interference-plus-Noise Ratio):** `9.0 dB` (clean, solid signal)
- **DSD State:** `Network type: '3gpp'`, `Service option: '3gpp-so-mask-lte-limited-srvc'`
- **Serving System State:** `Registration state: 'not-registered-searching'`
- **SIM Card Identity:**
  - IMSI: `405861183291040` (Reliance Jio India)
  - Applications: USIM (Application 1, ready) + ISIM (Application 2, detected)
  - EF_PNN / EF_SPN: PLMNs `405-854` through `405-874` (Jio)

### 2.3 ModemManager State (Modem/4)
```text
Hardware | manufacturer: 1, model: 0
         | firmware revision: UFI001BC 20211121 1 [Nov 04 2016 02:00:00]
Modes    | supported: allowed: 4g; preferred: none
         | current:   allowed: 4g; preferred: none
Bands    | current:   eutran-3, eutran-5, eutran-40, eutran-41, eutran-42, eutran-43
3GPP EPS | ue mode of operation: ps-2
         | initial bearer apn:   jionet
         | initial bearer ip:    ipv4v6
```

---

## 3. Forensic Analysis: The Problem

### 3.1 Symptom Forensics: `3gpp-so-mask-lte-limited-srvc` & `+CEREG: 2, 6`
When `mmcli -b 0 --connect` or `ubus call network.interface.modem up` is called, the connection fails with:
```text
Call failed: cm error: no-service
```
At the cellular protocol level:
1. AT command `AT+CEREG?` returned `+CEREG: 2, 6`:
   - `stat = 6`: **"Registered for 'SMS only', home network (E-UTRAN)"** (per 3GPP TS 27.007).
2. The modem has successfully performed cell acquisition and camped on the Jio cell, but full EPS packet service registration has not completed.
3. Querying LTE attach parameters (`qmicli --wds-get-lte-attach-parameters`) returns `QMI protocol error (74): InformationUnavailable`.

### 3.2 Root Cause Decomposition

There are three converging factors causing the modem to remain in "SMS only" / "limited service":

#### Factor 1: ESM Initial PDN Connectivity Not Triggered during EMM Attach
- In LTE (3GPP TS 24.301), unlike 2G/3G where GPRS attach can occur without activating a PDP context, **LTE EPS Attach MUST be combined with an initial default PDN Connectivity Request**.
- If the UE does not include ESM information or if the initial PDN context parameters are unpopulated in baseband memory, the EPC (MME) either accepts the attach for SMS only (if CSFB or SMS over SGs/NAS is supported) or denies packet data bearer creation.
- Because the WDS layer reports `InformationUnavailable` for LTE attach parameters, the baseband's internal Data Services (DS) EPS context is not binding the default APN (`jionet`) to the initial attach request.

#### Factor 2: PDC Fallback Configuration Persistence
- The modem has an internal EFS fallback profile:
  ```text
  Configuration 1:
      Description: ROW_Generic_3GPP
      Type:        software
      Size:        8984
      Status:      Active
  ```
- When `qmicli --pdc-deactivate-config` and `qmicli --pdc-delete-config` were executed, the profile was temporarily removed, but upon remoteproc modem reboot, the baseband DSP repopulated `ROW_Generic_3GPP` from internal non-volatile storage.
- Attempting to push a new carrier MBN via `qmicli --pdc-load-config` timed out (`Uploaded 0 of 29816; Transaction timed out`), indicating that the PDC QMI service in this firmware build requires specific timing or is not accepting file chunking over the standard QMI channel while the modem stack is active.

#### Factor 3: Jio MCFG Discrepancy (User Modified vs. Stock)
- The user noted: *`jio mcfg.mbn was modified by me`*.
- The original stock file that came with UFI001B was identified at:
  `/home/shaanair/Projects/msm8916-openwrt-clean/GitIgnore/compare/modem_ufi001b_extracted/image/modem_pr/mcfg/configs/mcfg_sw/generic/apac/reliance/commerci/mcfg_sw.mbn` (size: 29816 bytes, MD5: `41918b9d5f96f4a253900fe169992e20`).
- Inspection of the stock UFI001B Reliance MCFG revealed:
  - NV item 10 (`NV_MODE_PREF_I`) is set to `0x04` (`CM_MODE_PREF_WCDMA_ONLY`) with `WRITE_ONCE` flag (`0x07`).
  - This explains why stock UFI001B was locked into WCDMA mode and why user modification was attempted.

---

## 4. Plan to Fix and Achieve Pure LTE Attachment

### Phase 1: Reconcile User MCFG & Verify Parameter Alignment
1. Align with the user on specific modifications made to `jio mcfg.mbn` (e.g. APN, band priorities, VoLTE / voice domain, or NV item 10).
2. Ensure the active carrier profile on the router reflects:
   - `NV_MODE_PREF_I`: `31` (`CM_MODE_PREF_WCDMA_LTE`) or `21` (`CM_MODE_PREF_LTE_ONLY`)
   - `ue_usage_setting`: `1` (`DATA_CENTRIC`)
   - `voice_domain_pref`: `3` (`PS_ONLY`)
   - Initial attach APN: `jionet`

### Phase 2: Explicit LTE Initial Attach PDN Configuration
1. Use QMI WDS to explicitly bind Profile 1 (`jionet`, IPv4v6) as the LTE Attach PDN:
   ```bash
   qmicli -p -d /dev/wwan0qmi0 --wds-set-lte-attach-pdn-list=1
   ```
2. Set 3GPP EPS initial bearer settings in ModemManager:
   ```bash
   mmcli -m 4 --3gpp-set-initial-eps-bearer-settings="apn=jionet,ip-type=ipv4v6,allowed-auth=none"
   mmcli -m 4 --3gpp-set-eps-ue-mode-operation=ps-2
   ```

### Phase 3: Force Network Registration & Packet Service Attach
1. Trigger packet service attach explicitly:
   ```bash
   mmcli -m 4 --3gpp-set-packet-service-state=attached
   ```
2. Verify transition of NAS serving system from `not-registered-searching` to `registered-home`:
   ```bash
   qmicli -p -d /dev/wwan0qmi0 --nas-get-serving-system
   ```
3. Connect Bearer 0 and bring up `wwan0`:
   ```bash
   mmcli -m 4 -b 0 --connect
   ifup modem
   ```

### Phase 4: Hexagon Firmware Verification via `llvm-objdump`
- Now that `llvm-objdump` with Hexagon target support is confirmed available on the host system:
  `llvm-objdump -d --arch=hexagon GitIgnore/compare/modem_ufi001b_extracted/image/modem.b15`
- If runtime QMI configuration ever reverts, disassemble Call Manager's `cmph_init` directly to install a permanent, non-destructive hook that guarantees LTE mode preference initialization on every boot.

---

*End of Engineering Report 126*

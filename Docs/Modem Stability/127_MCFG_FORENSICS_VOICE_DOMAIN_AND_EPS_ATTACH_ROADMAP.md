# Engineering Report 127: Jio MCFG Forensics, QMI Voice/Service Domain Alignment & Pure LTE Data Attach Plan

**Date:** September 16, 2026  
**Status:** User MCFG Modification Forensics Complete; QMI Voice Domain & Service Domain Successfully Configured to Pure PS; Attach PDN Updated to IPv4v6; Plan to Finalize Pure LTE EPS Bearer Activation  
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

## 2. Executive Summary & Root Cause Forensics

### 2.1 What Was Discovered in the User-Modified MCFG
The user correctly targeted the exact cellular parameters required to break the WCDMA lock and force data-centric LTE operation:
1. **NV Item 10 (`CM_MODE_PREF`)**: Changed from `0x04` (`CM_MODE_PREF_WCDMA_ONLY`) to `0x1f` (31: `CM_MODE_PREF_ANY` / `CM_MODE_PREF_WCDMA_LTE`).
2. **EFS Item `/nv/item_files/modem/mmode/voice_domain_pref`**: Changed from `0x00` (`CS_ONLY`) to `0x03` (`PS_PREFERRED`).
3. **EFS Item `/nv/item_files/modem/mmode/ue_usage_setting`**: Changed from `0x00` (`VOICE_CENTRIC`) to `0x01` (`DATA_CENTRIC`).

### 2.2 Why the User-Modified MCFG Failed to Load
Qualcomm `mcfg_sw.mbn` is an **ELF32 LSB executable binary container** structured into three specific segments:
- **Segment 0 (0x0000 - 0x0094)**: ELF program headers and segment descriptors.
- **Segment 1 (0x1000 - 0x1088, 136 bytes)**: Qualcomm Segment Hash Table. It contains a 40-byte header followed by three 32-byte SHA256 hashes:
  - Hash 0: SHA256 of Segment 0 (`762b292a3ca586407a3288a4fe8a6a3bf9a9844ac5d29f6d411ad5e468a597ec`)
  - Hash 1: Null padding (`0x00` * 32)
  - Hash 2: SHA256 of Segment 2 (`5c088809e9ac3cb8a8329dfdfb1dc3e9c7b457ecb690d22c8a0a4b02557e0f5b`)
- **Segment 2 (0x2000 - 0x7478, 21624 bytes)**: The raw MCFG v3.1 NV/EFS item payload.

When the user manually hex-edited `mcfg_sw.mbn`:
1. When inserting the new SHA256 hash into Segment 1 at offset `0x1068`, extra bytes were accidentally introduced, shifting all downstream offsets.
2. At the end of the file, 3 bytes were truncated (`filesz = 0x5475` instead of `0x5478`).
3. The total file size became **29813 bytes** instead of the required **29816 bytes**.
4. The ELF Program Header at offset `0x84` was altered (`0x78` became `0x75`), mismatching the in-memory segment size.
5. Consequently, when Qualcomm PIL / PDC attempted to parse or authenticate the MBN, the ELF parser or hash verification failed immediately, causing transaction timeouts or refusal to apply the configuration.

> [!NOTE]
> There are **NO RSA certificates or public key signatures** on carrier `mcfg_sw.mbn` files in this architecture. Segment 1 is purely a standard SHA256 hash table. A bit-accurate repack tool can synthesize a 100% valid carrier MBN.

---

## 3. Real-Time Telemetry & Parameter Breakthroughs

### 3.1 Live Setting of Voice & Service Domain Preferences via QMI NAS 0x0033
Using our custom native aarch64 direct-syscall tool (`set_voice_pref`), we transmitted a single combined QMI NAS `Set System Selection Preference` message (`0x0033`) to `/dev/wwan0qmi0` containing:
- **TLV 0x17 (Change Duration)**: `0x01` (`PERMANENT`)
- **TLV 0x11 (Mode Preference)**: `0x0018` (`UMTS + LTE`)
- **TLV 0x1e (Acquisition Order)**: `0x01 0x08` (`LTE` first)
- **TLV 0x16 (Network Selection)**: `0x00 0x00 0x00 0x00 0x00` (`AUTOMATIC`)
- **TLV 0x18 (Service Domain Preference)**: `0x00000001` (`PS_ONLY`)
- **TLV 0x21 (Usage Preference)**: `0x00000002` (`DATA_CENTRIC`)
- **TLV 0x23 (Voice Domain Preference)**: `0x00000001` (`PS_ONLY`)

**Result:** The baseband accepted the entire request with **`SUCCESS (0x0000)`**!
Querying `qmicli --nas-get-system-selection-preference` confirms the live state:
```text
[/dev/wwan0qmi0] Successfully got system selection preference
	Emergency mode: 'no'
	Mode preference: 'umts, lte'
	Band preference: 'bc-0-a-system, bc-0-b-system, ...'
	LTE band preference: '1, 2, 3, 4, 5, ...'
	Roaming preference: 'any'
	Network selection preference: 'automatic'
	Service domain preference: 'ps-only'
	Usage preference: 'data-centric'
	Voice domain preference: 'ps-only'
	Acquisition order preference: 'lte, umts, gsm, cdma-1x, cdma-1xevdo, td-scdma'
```
Furthermore, because `Change Duration = permanent (0x01)` was set, these settings **persisted across baseband resets**.

### 3.2 Discovery of the Attach PDN Profile Mismatch
Querying WDS for the LTE attach profile list revealed a critical misconfiguration:
```text
[/dev/wwan0qmi0] --wds-get-lte-attach-pdn-list
Attach PDN list retrieved:
	Current list: '2'
```
Looking at the 3GPP profile definitions:
```text
[1] 3gpp - profile1
	APN: 'jionet'
	PDP type: 'ipv4-or-ipv6'
	PDP context number: '1'

[2] 3gpp - jionet_attach
	APN: 'jionet'
	PDP type: 'ipv4'
	PDP context number: '2'
```
- The active LTE attach profile was **Profile 2**, which had **`PDP type: 'ipv4'`**.
- Reliance Jio is a pure IPv6/IPv4v6 network that utilizes 464XLAT. An IPv4-only request during EMM Attach is rejected by Jio's MME for packet data, causing the MME to grant **SMS-only registration (`+CEREG: 0, 6`)** while withholding the default EPS bearer.
- We updated Profile 2 to `IPV4V6` and updated the LTE attach PDN list to Profile 1 (`--wds-set-lte-attach-pdn-list=1`), which was accepted and written to NVRAM.

### 3.3 Discovery of the `init_epsbearer` Suppression in OpenWrt
In `openwrt/package/msm8916/qcom-carrier-autocfg/files/qcom-carrier-autocfg.sh`:
```sh
case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
	*hmu05*) target_eps="none" ;;
esac
...
if [ "$target_eps" = "none" ]; then
	uci -q delete network.modem.init_epsbearer
else
	uci set network.modem.init_epsbearer="$target_eps"
fi
```
For HMU05, `init_epsbearer` was explicitly **deleted** from `/etc/config/network`. When ModemManager initializes without initial EPS bearer settings, it does not instruct the modem to negotiate the default EPS bearer with the APN `jionet` upon initial attach.

---

## 4. Why `+CEREG: 0, 6` ("SMS Only") Occurs & How It Clears

In 3GPP TS 24.301 Section 5.5.1.2:
1. When the UE transmits `ATTACH REQUEST` to the eNodeB/MME, it specifies:
   - `EPS attach type`: Combined EPS/IMSI attach (when voice-centric / CS-preferred).
   - `ESM Container`: PDN Connectivity Request (APN, IP Type, Bearer Resource Allocation).
2. Because Reliance Jio has no legacy 2G/3G CS domain, if the UE requests CS voice services or uses an incompatible IP family (IPv4-only), the MME responds with `ATTACH ACCEPT` containing:
   - `EPS attach result`: **`0x02` ("SMS only")** or EMM Cause #18 ("CS domain not available").
   - Default EPS Bearer Context Activation Request is either missing or unacknowledged.
3. This manifests in the AT coprocessor as **`+CEREG: 0, 6`** ("Registered for 'SMS only', home network").
4. To transition to **`+CEREG: 0, 1`** ("Registered, home network"):
   - UE Usage Setting must be `DATA_CENTRIC` (`1`).
   - Voice Domain Preference must be `PS_ONLY` (`1`) or `PS_PREFERRED` (`3`).
   - Attach APN must be `jionet` with `PDP type = IPV4V6`.
   - The modem must perform a fresh initial attach procedure (triggered cleanly via QMI DMS low-power/online toggle, avoiding manual AT CFUN/COPS).

---

## 5. Comprehensive Action Plan to Fix It

```
+-----------------------------------------------------------------------------------+
|                        Pure LTE Data Attach Execution Plan                        |
+-----------------------------------------------------------------------------------+
|  Step 1: Bit-Perfect Reliance Jio MCFG Repack                                    |
|  - Write repack_mcfg.py to cleanly patch orig mcfg_sw.mbn (29816 bytes)           |
|  - Apply: CM_MODE_PREF = 0x1f, voice_domain = 0x03, ue_usage = 0x01              |
|  - Compute SHA256 of Seg 2, insert at 0x1068, keep exact 29816 byte size         |
|  - Deploy to /lib/firmware/MCFG_SW.MBN and /usr/share/qcom-carrier-autocfg/       |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  Step 2: Re-enable Initial EPS Bearer Settings in OpenWrt                        |
|  - Remove *hmu05*) target_eps="none" override in qcom-carrier-autocfg.sh          |
|  - Configure /etc/config/network: init_epsbearer='default', iptype='ipv4v6'       |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  Step 3: Trigger Clean Initial Attach Procedure via QMI DMS                      |
|  - Cycle operating mode: low-power (DMS 0x002e: 0x02) -> online (0x01)            |
|  - Eliminates dangerous AT+COPS=2 / AT+CFUN=0 MMOC state deactivation asserts     |
|  - Baseband sends ATTACH REQUEST with DATA_CENTRIC + IPV4V6 + jionet              |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  Step 4: Verify Default EPS Bearer Establishment & Network Bringup               |
|  - Verify +CEREG: 0, 1 (Registered, home network)                                 |
|  - Verify qmicli --nas-get-serving-system: PS: 'attached', Radio: 'lte'           |
|  - Start ModemManager (/etc/init.d/modemmanager start)                            |
|  - Activate data bearer and bring up wwan0 interface (ifup modem)                 |
+-----------------------------------------------------------------------------------+
```

---

## 6. Verification Milestones

| Milestone | Expected Telemetry / Output | Verification Command | Status |
| :--- | :--- | :--- | :--- |
| **1. Mode Preference** | `Mode preference: 'umts, lte'` | `qmicli --nas-get-system-selection-preference` | **PASSED** |
| **2. Voice & Service Domain** | `Voice domain: 'ps-only'`, `Usage: 'data-centric'` | `qmicli --nas-get-system-selection-preference` | **PASSED** |
| **3. Attach PDN Profile** | `Current list: '1'`, `PDP type: 'ipv4-or-ipv6'` | `qmicli --wds-get-lte-attach-pdn-list` | **PASSED** |
| **4. Valid Carrier MBN** | Exactly 29816 bytes, valid SHA256 in Seg 1 | `repack_mcfg.py` + `ls -l /lib/firmware/MCFG_SW.MBN` | **NEXT** |
| **5. EMM Network Attach** | `+CEREG: 0, 1` (Eliminate SMS-only stat 6) | `/tmp/at_tool 'AT+CEREG?'` | **NEXT** |
| **6. NAS Serving System** | `Registration state: 'registered'`, `PS: 'attached'` | `qmicli --nas-get-serving-system` | **NEXT** |
| **7. EPS Data Bearer** | Connected (`wwan0` has IP and default gateway) | `mmcli -m 0 --bearer 0 --connect && ifup modem` | **NEXT** |

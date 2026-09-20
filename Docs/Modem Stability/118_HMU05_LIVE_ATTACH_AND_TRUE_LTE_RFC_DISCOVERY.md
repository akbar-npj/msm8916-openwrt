# Engineering Report 118: Stock HMU05 Live Attach & True LTE RFC Architecture Discovery

**Date:** September 15, 2026  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**Active Firmware for Ground-Truth:** Melbon HMU05 Stock (`HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]`)  
**Target Baseband for Porting:** UFI001B MPSS.DPM.2.0.2.c1-00054 (`M8936FAAAANUZM-1`)  
**Status:** **HISTORIC ARCHITECTURAL BREAKTHROUGH** — Live Ground-Truth 82 MB Coredump Captured Under Active Reliance Jio Connection; Previous TD-SCDMA Transplant Mistake Identified; Authentic WTR1605 LTE Vtable & 92 Active RF Tables Extracted  

---

## 0. Engineering Methodology & Dual-Firmware Comparative Workflow SOP

To guarantee technical rigor, consistency, and total reproducibility across all engineering sessions, every investigation follows the codified **Dual-Firmware Comparative Workflow**:

```mermaid
flowchart TD
    A["Encounter Block / Stagnation in WIP UFI001B"] --> B["Snapshot Atomic Backup to GitIgnore/compare/modem_ufi001b_patchXX_backup/"]
    B --> C["Deploy Ground-Truth Stock HMU05 to /lib/firmware/ and Reboot"]
    C --> D["Verify Live Network Operation on Reliance Jio 4G (qmicli / mmcli / ping)"]
    D --> E["Inspect Live Physical Memory, Registers & Disassembly (Coredump / Devmem)"]
    E --> F["Synthesize Precision Patch in Python with Exact Hexagon Bitfield Encoding"]
    F --> G["Cryptographic Re-Hash of Modified Segments via ufi001b_hash_tool.py"]
    G --> H["Deploy Synthesized Patch to Dongle & Verify Telemetry"]
    H --> I["Document Milestone & Update Architecture Reports"]
```

### 0.1 Standard Rules of Engagement
1. **Never Guess Hardware Semantics:** Hardware transceivers (WTR1605) and front-end ICs (QFE2320) cannot be configured by trial and error. Always observe how authentic stock HMU05 drives the transceiver under live Reliance Jio connectivity (Band 3 / EARFCN 1451, Band 5 / EARFCN 2463).
2. **Versioned Backups Before Major Changes:** Keep atomic backups of WIP modem segments in `GitIgnore/compare/modem_ufi001b_patchXX_backup/` before applying experimental modifications.
3. **Live Memory Capture via Devcoredump:** When physical access is restricted by TrustZone XPU firewalls (`SIGBUS` on `/dev/mem`), configure `remoteproc0/coredump` to `inline` and capture the live RAM state into `/sys/class/devcoredump/devcdX/data`.
4. **Cryptographic Integrity:** Every modified segment must be re-hashed into `modem.b01` and `modem.mdt` and verified across all 28 program headers with `ufi001b_hash_tool.py` before flashing.

---

## 1. Executive Summary: The True LTE vs. TD-SCDMA Revelation

In Patches 21 through 48, the modem was stabilized: the 4.8-second watchdog crash was neutralized, all BAM-DMUX channels opened, and the baseband remained online continuously for $>10$ minutes in `state: searching`. However, it never transitioned to `registered` on Reliance Jio, reporting `signal quality: 0%`, `InformationUnavailable`, and `NoNetworkFound`.

By flashing stock HMU05, confirming live 4G LTE bidirectional attachment, and capturing the running modem physical RAM via remoteproc devcoredump, **the foundational root cause was uncovered**:

1. **The TD-SCDMA Misidentification (Patches 21–48):**
   - In Patch 21, the 700-byte router at VA `0xc118a9dc` and its 26 static tables (`0xc1e81b68`..`0xc1e829cc`) were transplanted.
   - String and RTTI forensics proved that vtable `0xc1c12950` belongs to `34rfc_wtr1605_chile_rf360_tdscdma_ag` (**TD-SCDMA**)!
   - The 26 tables were `rx_tdscdma_*` and `tx_tdscdma_*`—intended for 3G TD-SCDMA time-division duplexing in China, completely devoid of LTE FDD Band 3 / Band 5 transceiver synthesizers and front-end timings!
2. **The True WTR1605 LTE Vtable (`0xc1c12180`):**
   - The authentic LTE vtable resides at VA `0xc1c12188` (RTTI `30rfc_wtr1605_chile_rf360_lte_ag`).
   - Its primary router is `0xc118794c` (2,996 bytes), which configures all LTE bands (Band 1, 2, 3, 4, 7, 8, 20, 38, 39, 40, 41).
   - Its TX device info router is `0xc1188528` (192 bytes).
   - Its table block spans `0xc1e7fac8` to `0xc1e81b40` (8,340 bytes, exactly 92 tables).
3. **Live RAM Extraction:**
   - In raw firmware files, these 92 tables reside in BSS and are dynamically populated at boot by `0xc11885e8` from EFS.
   - By triggering an inline devcoredump while connected to Reliance Jio, **we captured all 92 fully initialized, authentic LTE RF tables directly from live RAM** into `modem_hmu05_live_connected.elf`.

---

## 2. Live Stock HMU05 Attachment Telemetry

Deploying pristine HMU05 firmware to `/lib/firmware/` and rebooting yielded immediate full network registration:

### 2.1 Serving System & Cell Acquisition
```text
[/dev/wwan0qmi0] Successfully got serving system:
	Registration state: 'registered'
	CS: 'detached'
	PS: 'attached'
	Selected network: '3gpp'
	Radio interfaces: '1'
		[0]: 'lte'
	Current PLMN:
		MCC: '405'
		MNC: '861'
		Description: 'JIO 4G'
	3GPP cell ID: '441635'
	LTE tracking area code: '63'
```

### 2.2 RF Band Information & Signal Strength
```text
[/dev/wwan0qmi0] Successfully got RF band info
Band Information:
	Radio Interface:   'lte'
	Active Band Class: 'eutran-3'
	Active Channel:    '1451'

[/dev/wwan0qmi0] Successfully got signal info
LTE:
	RSSI: '-58 dBm'
	RSRQ: '-10 dB'
	RSRP: '-86 dBm'
	SNR: '20.6 dB'
```
*(On alternate boot cycles, the modem locked to Band Class `eutran-5`, Channel `2463`, Cell ID `441648`, RSRP `-83 dBm`, confirming both Jio Band 3 and Band 5 are active).*

### 2.3 Bi-directional Internet Routing
```text
root@OpenWrt:~# ping -c 3 8.8.8.8
PING 8.8.8.8 (8.8.8.8): 56 data bytes
64 bytes from 8.8.8.8: seq=0 ttl=115 time=81.358 ms
64 bytes from 8.8.8.8: seq=1 ttl=115 time=39.509 ms
64 bytes from 8.8.8.8: seq=2 ttl=115 time=49.535 ms

--- 8.8.8.8 ping statistics ---
3 packets transmitted, 3 packets received, 0% packet loss
round-trip min/avg/max = 39.509/56.800/81.358 ms
```

---

## 3. Live Physical Memory Capture via Remoteproc Devcoredump

Because MSM8916 TrustZone XPU fires `SIGBUS` on direct Linux CPU access to `0x86000000..0x8bcfffff`, we used remoteproc inline coredumping:

1. **Configured DebugFS:**
   ```bash
   echo inline > /sys/kernel/debug/remoteproc/remoteproc0/coredump
   ```
2. **Triggered Live Snapshot:**
   ```bash
   echo 1 > /sys/kernel/debug/remoteproc/remoteproc0/crash
   ```
3. **Extracted Devcoredump:**
   Streamed `/sys/class/devcoredump/devcd1/data` to host:
   $$\text{Artifact: } \texttt{GitIgnore/compare/modem\_hmu05\_live\_connected.elf} \quad (85,738,496 \text{ bytes})$$

### 3.1 Linear VA-to-PA Relationship in HMU05
From HMU05 `modem.mdt` program headers:
$$\text{PA} = \text{VA} - 0x39800000 \quad \iff \quad \text{VA} = \text{PA} + 0x39800000$$
Segment 13 (containing VA `0xc1c3c000..0xc1f2bac8`):
- Physical Base: `0x8843c000`
- Coredump File Offset: `0x1bb1bc0`
- Target LTE Tables VA: `0xc1e7fac8` $\rightarrow$ PA `0x8867fac8` $\rightarrow$ File Offset `0x1df5688`

---

## 4. Deep Forensic Comparison: True LTE vs. TD-SCDMA

### 4.1 HMU05 Class Hierarchy & Vtable Comparison (`modem.b18`)

| Property | TD-SCDMA (Wrong, Patches 21–48) | True LTE (Authentic, Ground-Truth) |
| :--- | :--- | :--- |
| **RTTI Class Name** | `34rfc_wtr1605_chile_rf360_tdscdma_ag` | `30rfc_wtr1605_chile_rf360_lte_ag` |
| **RTTI Descriptor VA** | `0xc1c12c48` | `0xc1c12944` |
| **Vtable VA** | `0xc1c12958` (offset `0x712958`) | `0xc1c12188` (offset `0x712188`) |
| **Virtual Method 0** | `0xc118b624` (destructor) | `0xc118a970` (destructor) |
| **Virtual Method 1** | `0xc118b628` (destructor) | `0xc118a974` (destructor) |
| **Virtual Method 2 (RX Dev Info)**| `0xc118a9d0` (calls `0xc118a9dc`, r3=0) | `0xc1187940` (calls `0xc118794c`, r3=0) |
| **Virtual Method 3 (Mode/Timing)**| `0xc118ac98` (calls `0xc118a9dc`, r3=1) | `0xc1188500` (calls `0xc118794c`, r3=2) |
| **Virtual Method 4 (RX Sig Cfg)** | `0xc118aca4` (stub) | `0xc118850c` (calls `0xc118794c`, r3=1) |
| **Virtual Method 5 (TX Dev Info)**| `0xc118acb4` (table populator) | `0xc1188528` (**TX device info router!**) |
| **Virtual Method 6 (TX Sig Cfg)** | None (out of bounds) | `0xc1188518` (TX sig cfg stub) |
| **Virtual Method 7 (Table Init)** | None | `0xc11885e8` (table loader) |

### 4.2 UFI001B Vtable Alignment (`modem.b17` @ `0xc199a4ac`)

UFI001B's `rfc_efs_card_lte_data` has the EXACT same 8-slot vtable structure:
- `vtable[2]` (offset `+0x8`): RX get_device_info $\longleftrightarrow$ HMU05 `0xc1187940` (`r3 = #0`)
- `vtable[3]` (offset `+0xc`): RX get_sig_cfg $\longleftrightarrow$ HMU05 `0xc118850c` (`r3 = #1`)
- `vtable[4]` (offset `+0x10`): Timing info $\longleftrightarrow$ HMU05 `0xc1188500` (`r3 = #2`)
- `vtable[5]` (offset `+0x14`): TX get_device_info $\longleftrightarrow$ HMU05 `0xc1188528`
- `vtable[6]` (offset `+0x18`): TX get_sig_cfg $\longleftrightarrow$ HMU05 `0xc1188518`

---

## 5. Verification of the 92 Captured LTE Tables

Inspecting `modem_hmu05_live_connected.elf` at file offset `0x1df5688` (VA `0xc1e7fac8`) confirms all 92 tables are fully populated with active Qualcomm RF device headers (`0x022902dd`):

```text
[00] VA=0xc1e7fac8: ['0x22902dd', '0x0', '0x0', '0x0'] (rx_lte_init_device_info)
[01] VA=0xc1e7fb0c: ['0x10', '0x0', '0xffff', '0x0']   (rx_lte_init_sig_cfg)
[02] VA=0xc1e7fb1c: ['0x22902dd', '0x0', '0x0', '0x0'] (tx_lte_init_device_info)
[04] VA=0xc1e7fb70: ['0x22902dd', '0x0', '0x0', '0x0'] (rx0_lte_00_device_info - B1 PRX)
...
[10] VA=0xc1e7fdbc: ['0x22902dd', '0x0', '0x0', '0x0'] (rx0_lte_02_device_info - B3 PRX)
[12] VA=0xc1e7fe48: ['0x22902dd', '0x1', '0x0', '0x1'] (rx1_lte_02_device_info - B3 DRX)
[14] VA=0xc1e7feec: ['0x22902dd', '0x0', '0x1', '0x0'] (tx0_lte_02_device_info - B3 TX)
...
[88] VA=0xc1e81a38: ['0x22902dd', '0x956a', '0x96f9', '0x26'] (tx0_lte_39 - B40 TX)
[89] VA=0xc1e81a84: ['0x22902dd', '0x9ae2', '0x9fba', '0x28'] (tx0_lte_40 - B41 TX)
[90] VA=0xc1e81ad0: ['0x22902dd', '0x9ae2', '0x9fba', '0x28']
[91] VA=0xc1e81b1c: ['0x22902dd', '0x9ae2', '0x9fba', '0x28']
```

---

## 6. Implementation Roadmap for Patch 49

1. **Table Block Transplant (8,340 bytes):**
   - Extract `0xc1e7fac8..0xc1e81b5c` directly from `modem_hmu05_live_connected.elf`.
   - Embed at Segment 26 (`modem.b26`) offset `0xe0000` (VA `0xc3eef000`).
2. **True LTE Router Relocation (`0xc118794c`, 2,996 bytes):**
   - Extract `0xc118794c..0xc11884fc` from HMU05 `modem.b16`.
   - Relocate all 92 `immext` table pointers to the new VA in Segment 26:
     $$\Delta = \text{VA}_{\text{new}} - 0xc1e7fac8$$
   - Place into executable code space in Segment 15 or 26.
3. **TX Router Relocation (`0xc1188528`, 192 bytes):**
   - Extract `0xc1188528..0xc11885e8` from HMU05 `modem.b16`.
   - Relocate table pointers and embed.
4. **Vtable Hooking in Segment 15:**
   - Hook `vtable[2]` (PRX/DRX dev info) $\rightarrow$ Relocated `0xc118794c` with `r3 = #0`.
   - Hook `vtable[3]` (PRX/DRX sig cfg) $\rightarrow$ Relocated `0xc118794c` with `r3 = #1`.
   - Hook `vtable[5]` (TX dev info) $\rightarrow$ Relocated `0xc1188528`.
   - Hook `vtable[6]` (TX sig cfg) $\rightarrow$ Relocated `0xc1188518` stub (`r0 = 1`).
5. **Cryptographic Re-Hash & Deployment:**
   - Verify all 28 ELF headers with `ufi001b_hash_tool.py` and deploy to `/lib/firmware/`.

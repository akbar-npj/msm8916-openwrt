# Engineering Report 108: HMU05 Live Telemetry Capture & Card ID 0x51 Breakthrough

**Date:** September 14, 2026  
**Status:** Live Baseline Captured & Root Cause Identified  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 + RF360 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**Network Operator:** Reliance Jio (MCC 405 MNC 861, LTE Band 3 / EARFCN 1451)  

---

## 1. Executive Summary

Executing the strategy proposed by the user, we temporarily booted the original stock HMU05 firmware on the live router for a brief, controlled 2-minute run to observe its authentic hardware initialization and network registration sequence.

### Key Accomplishments:
1. **Live Reliance Jio LTE Connection Achieved:**
   - Stock HMU05 attached to Reliance Jio LTE instantly, transitioned to `registered` and `connected`, established bearer `/org/freedesktop/ModemManager1/Bearer/2` on `wwan0`, and received a public/private IP lease (`10.103.250.184`).
2. **Comprehensive Golden RF Baseline Captured:**
   - Captured active band (`eutran-3`), exact carrier EARFCN (`1451`), serving cell PCI (`99`), TAC (`63`), and authentic signal metrics (RSSI: `-57 dBm`, RSRP: `-86.8 dBm`, SNR: `19.8 dB`).
3. **Safe Shutdown & UFI001B Restoration:**
   - The modem was safely halted at $t \approx 2\text{ min}$ (well before the 15-minute DRX timer bug), and UFI001B Patch 35 was cleanly restored to `/lib/firmware/` and re-enabled at `Modem/3`.
4. **Major Diagnostic Breakthrough (The Card ID 0x51 Bug):**
   - Cross-referencing the live telemetry and disassembly revealed why UFI001B failed to initialize the transceiver:
     - Melbon HMU05 hardware queries the board/NV and passes **RFC Card ID `0x51` (`81` decimal)**.
     - In stock HMU05, card ID `0x51` maps to the native `wtr1605_chile_rf360` constructor at `0xc1186154`.
     - In UFI001B, `rfc_card_factory` (`0xc123de54` in `b15`) checked `0x51 - 0x31 = 0x20 > 0x14`, concluded `0x51` was out of bounds, and jumped straight to the default NULL exit (`0xc123e028`)!
     - As a direct result, **the Chile card constructor was NEVER called during boot**, leaving `card_singleton` (`0xc34078ac`) and `wtr_singleton` (`0xc3407ad4`) uninstantiated (`0x00000000`).

---

## 2. Golden Telemetry Telemetry Log (Stock HMU05)

### 2.1 ModemManager State & Identification
```
Firmware Revision: HIMI_U01_MODEM_V1.0 1 [Sep 09 2015 10:00:00]
Equipment ID:      864293052253917
SIM Number:        918431225166
Power State:       on
State:             connected
Access Tech:       lte
Signal Quality:    91% (recent)
Operator ID:       405861 (Reliance Jio)
Operator Name:     IN Loop / JIO 4G
Registration:      home
Packet Service:    attached
Bearer Path:       /org/freedesktop/ModemManager1/Bearer/2
IP Address:        10.103.250.184 / 28
Gateway:           10.103.250.185
DNS:               49.45.0.1
MTU:               1500
```

### 2.2 QMI NAS RF Band & Channel Information
```
[qmicli --nas-get-rf-band-info]
Radio Interface:   'lte'
Active Band Class: 'eutran-3' (LTE Band 3)
Active Channel:    '1451' (EARFCN 1451)
```
- **Carrier Frequencies for EARFCN 1451:**
  $$F_{DL} = 1805.0\text{ MHz} + 0.1 \times (1451 - 1200) = 1830.1\text{ MHz}$$
  $$F_{UL} = 1710.0\text{ MHz} + 0.1 \times (1451 - 1200) = 1735.1\text{ MHz}$$

### 2.3 QMI NAS Signal Metrics
```
[qmicli --nas-get-signal-info]
LTE RSSI:  -57 dBm to -60 dBm
LTE RSRQ:  -12.0 dB to -13.3 dB
LTE RSRP:  -86.8 dBm to -88.0 dBm
LTE SNR:   19.6 dB to 19.8 dB
```

### 2.4 QMI NAS Cell Location & Serving Cell Info
```
[qmicli --nas-get-cell-location-info]
PLMN:                       405186 / 405861
Tracking Area Code (TAC):   63
Global Cell ID:             441635
Physical Cell ID (PCI):     99
Timing Advance:             1 us
```

### 2.5 QMI NAS Serving System & Network Settings
```
[qmicli --nas-get-serving-system]
Registration State:         'registered'
Circuit Switched (CS):      'detached'
Packet Switched (PS):       'attached'
Selected Network:           '3gpp'
Current PLMN:               MCC 405, MNC 861 ('JIO 4G')
Timezone Offset:            +330 minutes (UTC+05:30 IST)
Service Domain Preference:  'ps-only'
LTE Band Preference:        '1, 3, 5, 8, 40'
DMS LTE Bands Supported:    '1, 3, 5, 8'
```

---

## 3. Disassembly Analysis: The Card ID 0x51 Root Cause

### 3.1 What Stock HMU05 Executes
In stock HMU05 (`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`):
- The Chile RFC Card class `30rfc_wtr1605_chile_rf360_cmn_ag` is natively compiled into Segment 16.
- The card initialization routine at `0xc11861d0` explicitly loads:
  ```hexagon
  c11861d0: { r1 = #0x51 ; r2 = ##0xc1c11c68 ; r0 = add(r29, #0x10) }
  ```
- Melbon HMU05 hardware identifies itself to MPSS with **Card ID `0x51` (decimal 81)**.

### 3.2 What UFI001B Does in `rfc_card_factory`
In UFI001B (`modem.b15` at VA `0xc123de54`):
```hexagon
c123de5c: { r18 = r0 }                           // r18 = Card ID (0x51)
...
c123de78: { p0 = cmp.gtu(r18, #0x30) }           // 0x51 > 0x30 -> TRUE, jump c123de8c
...
c123dea8: { p0 = cmp.gtu(r18, #0x4b) }           // 0x51 > 0x4b -> TRUE
c123deac: { r1.l = #0xb47c ;
            r0 = add(r18, #-0x31) ;              // r0 = 0x51 - 0x31 = 0x20 (32)
            if (cmp.gtu(r0.new, #0x14)) jump:t 0xc123e028 } // 0x20 > 0x14 (32 > 20) -> JUMP TO NULL!
```
- At `0xc123e028`:
  ```hexagon
  c123e028: { r19 = #0x0 ; r1 = #0x0 ; memw(r17+#0x0) = #0 }
  c123e030: { r0 = r19 ; jump 0xc0060450 }       // RETURN NULL (0x00000000)
  ```

### 3.3 The Failure Sequence
1. MPSS queries the hardware/NV for Card ID $\rightarrow$ receives `0x51`.
2. `rfm_init()` calls `rfc_card_factory(0x51, ...)`.
3. `rfc_card_factory` tests if $0x51 - 0x31 \le 0x14$. Because $32 > 20$, it takes the default fail branch.
4. `rfc_card_factory` returns `NULL`.
5. The Chile RFC Card constructor (`0xc3e35000`) is **never executed**.
6. `card_singleton` (`0xc34078ac`) remains `0x00000000`.
7. `wtr_singleton` (`0xc3407ad4`) remains `0x00000000`.
8. The transceiver is never tuned to EARFCN 1451.
9. Any manual scan request triggers a NULL dereference exception at `0x8b99e000`.

---

## 4. The Fix (Patch 36)

To resolve this issue completely:
1. In `rfc_card_factory` (`modem.b15` at `0xc123de54`):
   - Rather than letting Card ID `0x51` fall into the default NULL branch, route Card ID `0x51` (or force-bind all card requests on Melbon HMU05) directly to:
     ```hexagon
     r20 = ##0xc3e35000     // Chile RFC Card constructor in Segment 26
     jump 0xc123e00c        // Call constructor and return instantiated card pointer
     ```
2. Re-hash Segment 15 in `modem.b01`, rebuild `modem.mdt`, and verify all 28 ELF segments.
3. Deploy to the router and verify that `card_singleton` (`0xc34078ac`) and `wtr_singleton` (`0xc3407ad4`) are instantiated.
4. Verify WTR1605 locks to EARFCN 1451 and attaches to Reliance Jio LTE.

---

## 5. Summary Table: Before vs After Live Capture

| Parameter | UFI001B (Pre-Capture) | Stock HMU05 Reference | UFI001B Target (Patch 36) |
|---|---|---|---|
| **RFC Card ID Passed** | `0x51` (81) | `0x51` (81) | `0x51` (81) |
| **`rfc_card_factory` Result** | NULL (`0x00000000`) | Native Card (`0xc1186154`) | Chile Card (`0xc3e35000`) |
| **Card Singleton** | `0x00000000` | Instantiated | Instantiated (`0xc34078ac`) |
| **WTR1605 Singleton** | `0x00000000` | Instantiated | Instantiated (`0xc3407ad4`) |
| **Active LTE Band** | Idle / None | `eutran-3` (Band 3) | `eutran-3` (Band 3) |
| **Active Channel** | None | EARFCN `1451` | EARFCN `1451` |
| **Network Registration** | `searching` | `registered` (`JIO 4G`) | `registered` (`JIO 4G`) |
| **Data Bearer** | Detached | `connected` (`10.103.250.184`) | `connected` (`jionet`) |
| **Crash Behavior** | Stable (No timer bug) | Crashes at 15 min | Stable (No timer bug) |

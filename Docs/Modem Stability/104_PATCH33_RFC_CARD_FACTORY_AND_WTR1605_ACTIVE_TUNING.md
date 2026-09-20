# Engineering Report 104: Patch 33 - True RFC Factory Virtual Address & Active WTR1605 Transceiver Binding

**Date:** 2026-09-14  
**Target Hardware:** Melbon HMU05 4G Dongle (MSM8916 + WTR1605 transceiver + QFE2320 RF360 front-end)  
**Base Firmware:** UFI001B MPSS.DPM.2.0.2.C1 (donor baseband)  
**Carrier:** Reliance Jio 4G (MCC 405 MNC 861, Bands 3/5)  
**Status:** Patch 33 Applied, MBA Hash Verified (20 MATCH, 8 ZERO/BSS, 0 MISMATCH), Deployed & Verified Live  

---

## 1. Executive Summary & Problem Formulation

While **Patch 30** established full virtual address consistency for the newly injected tables at `0xe8000`, and **Patch 32** aligned PMIC voltage regulator client routing to `rf1`, the modem remained in state `searching` with signal strength `-128 dBm`.

In-depth reverse engineering of Qualcomm's baseband execution flow revealed two critical architectural gaps:

### 1.1 The Lingering `0xd2338000` Virtual Address Bug in `modem.b15`
In Qualcomm's `rfc_card_factory` (`FUN_c123de54` in `modem.b15` at VA `0xc123de54`):
- Both the fallback branch at `0x00f7be84` (VA `0xc123de84`) and the case `0x35` (Chile Card) branch at `0x00f7bff4` (VA `0xc123dff4`) loaded:
  $$\mathbf{r20 = \#\#0xd2338000}$$
- At `0xc123e010`, execution called `callr r20` to construct the RFC card.
- However, as proven in Report 100, `0xd2312000` was a fictitious base address with zero MMU mapping in QDSP6. The genuine virtual address of Segment 26 (`modem.b26`) is **`0xc3e0f000`**.
- The authentic constructor of the Chile Card resides at offset `0x26000` in `modem.b26`:
  $$\text{VA}_{\text{card\_constructor}} = 0xc3e0f000 + 0x26000 = \mathbf{0xc3e35000}$$
- Because `modem.b15` was never updated to `0xc3e35000`, the RFC card factory was either jumping to unmapped space or aborting card initialization.

### 1.2 Transceiver Chip Descriptors & Vtables in `modem.b26`
In `modem.b26`, all internal constructors and vtables were still referencing `0xd23...`:
1. **Chile Card Vtable Constructor (`0x26044`)**:
   Wrote `memw(r16+#0) = ##0xd233816c` (unmapped vtable address) instead of `0xc3e3516c`.
2. **Chile Card Vtable Entries (`0x2616c..0x26188`)**:
   All 7 method pointers pointed to `0xd2338...` instead of `0xc3e35...`.
3. **WTR Device Constructor (`0x26214`)**:
   Wrote `memw(r16+#0) = ##0xd23382a8` (unmapped vtable address) instead of `0xc3e352a8`.
4. **WTR Device Vtable Entries (`0x262a8..0x262b8`)**:
   All 5 method pointers pointed to `0xd2338...` instead of `0xc3e35...`.
5. **Authentic Chip Table Getters (`0x26254` & `0x2625c`)**:
   - `0x26254`: Loaded `r1 = ##0xd23863cc` instead of genuine `0xc3e833cc` (DRX descriptor).
   - `0x2625c`: Loaded `r1 = ##0xd2386388` instead of genuine `0xc3e83388` (PRX descriptor).
   - These descriptors contain the genuine WTR1605 chip ID `0x022902dd` (`dd 02 29 02`). Without them, Layer 1 never detected that a physical WTR1605 transceiver was present.
6. **Injected Device Vtable Slot Alignment**:
   `static_wtr_vtable` (`0xe8040`) and `subdev_vtable` (`0xe8140`) were stubbed to `RET0_STUB` for transceiver queries. When Layer 1 queried the transceiver chip descriptor (Slot 2/3), it received 0 instead of the WTR1605 hardware descriptor.

---

## 2. Technical Modifications (Patch 33)

### 2.1 `modem.b15` Modifications
Both RFC Card Factory constructor targets were redirected to the genuine Segment 26 virtual address `0xc3e35000`:
- **Offset `0x00f7be84` (VA `0xc123de84`)**:
  Replaced `00 4e 23 0d 14 c0 00 78` (`r20 = ##0xd2338000`) with:
  $$\text{Hexagon: } \mathbf{40\ 4d\ 3e\ 0c\ 14\ c0\ 00\ 78} \quad (\mathbf{r20 = \#\#0xc3e35000})$$
- **Offset `0x00f7bff4` (VA `0xc123dff4`)**:
  Replaced `00 4e 23 0d 14 c0 00 78` (`r20 = ##0xd2338000`) with:
  $$\text{Hexagon: } \mathbf{40\ 4d\ 3e\ 0c\ 14\ c0\ 00\ 78} \quad (\mathbf{r20 = \#\#0xc3e35000})$$

### 2.2 `modem.b26` Modifications
1. **Chile Card Constructor (`0x26044`)**:
   Replaced `05 4e 23 0d 80 45 00 78` (`r0 = ##0xd233816c`) with:
   $$\text{Hexagon: } \mathbf{45\ 4d\ 3e\ 0c\ 80\ 45\ 00\ 78} \quad (\mathbf{r0 = \#\#0xc3e3516c})$$
2. **Chile Card Vtable (`0x2616c..0x26188`)**:
   - `vtable[0]` (`0x2616c`): `0xc3e35148`
   - `vtable[1]` (`0x26170`): `0xc3e3514c`
   - `vtable[2]` (`0x26174`): `0xc3e35050` (card_init)
   - `vtable[3]` (`0x26178`): `0xc3e350dc` (mode 0 table getter)
   - `vtable[4]` (`0x2617c`): `0xc3e350e4` (mode 1 table getter)
   - `vtable[6]` (`0x26184`): `0xc3e350ec` (card routing copy)
   - `vtable[7]` (`0x26188`): `0xc3e3512c` (mode 7 device table)
3. **WTR Device Constructor (`0x26214`)**:
   Replaced `0a 4e 23 0d 00 45 00 78` (`r0 = ##0xd23382a8`) with:
   $$\text{Hexagon: } \mathbf{4a\ 4d\ 3e\ 0c\ 00\ 45\ 00\ 78} \quad (\mathbf{r0 = \#\#0xc3e352a8})$$
4. **WTR Device Vtable (`0x262a8..0x262b8`)**:
   - `vtable[0]` (`0x262a8`): `0xc3e35284` (destructor)
   - `vtable[1]` (`0x262ac`): `0xc3e35288` (deleting destructor)
   - `vtable[2]` (`0x262b0`): `0xc3e35220` (PRX chip descriptor getter)
   - `vtable[3]` (`0x262b4`): `0xc3e3526c` (DRX chip descriptor getter)
   - `vtable[4]` (`0x262b8`): `0xc3e35274` (safe stub)
5. **Transceiver Chip Table Getters (`0x26254` & `0x2625c`)**:
   - `0x26254` (DRX): Replaced `8f 61 23 0d 81 c1 00 78` with `cf 60 3e 0c 81 c1 00 78` (`r1 = ##0xc3e833cc`).
   - `0x2625c` (PRX): Replaced `8e 61 23 0d 01 c1 00 78` with `ce 60 3e 0c 01 c1 00 78` (`r1 = ##0xc3e83388`).
6. **Injected Vtables (`static_wtr_vtable` @ `0xe8040` & `subdev_vtable` @ `0xe8140`)**:
   - Slot 2 (`+0x08`): `0xc3e35220` (Authentic WTR1605 PRX getter -> returns chip ID `0x022902dd`)
   - Slot 3 (`+0x0c`): `0xc3e3526c` (Authentic WTR1605 DRX getter -> returns chip ID `0x022902dd`)
   - Slot 15 (`+0x3c`): `0xc3ef7188` (`RET1_VA`: RF path query success)

---

## 3. Cryptographic Integrity & MBA Verification

All 28 ELF segments verified with `ufi001b_hash_tool.py`:
- **Segment 15 SHA256**: `ded69a7ccb73bf38a49db9df904853ab21c2f0aa1d93db0b0b785f1f6d919cd4`
- **Segment 26 SHA256**: `93e2555601eb7515c1dbae17a8f1466b73b2519ab529db9feb839b430d74ab25`
- **Result**: `20 MATCH, 8 ZERO/BSS, 0 MISSING, 0 MISMATCH` (`Overall: PASS ✓`)

---

## 4. Live Hardware Verification Results

1. **Clean Cold Hardware Boot**:
   - Target rebooted cleanly in 32 seconds.
   - Zero early boot exceptions in `dmesg`.
   - BAM-DMUX channels 0..7 initialized and opened cleanly.
2. **ModemManager Detection**:
   - Modem detected at `/org/freedesktop/ModemManager1/Modem/0`.
   - SIM phone number `918431225166` read immediately.
   - States: `state: searching`, `power state: on`, `registration: searching`.
3. **QMI Interface Commands**:
   - `qmicli --nas-force-network-search`: **SUCCESS** (0 crashes).
   - Zero kernel panics or SSR events recorded.

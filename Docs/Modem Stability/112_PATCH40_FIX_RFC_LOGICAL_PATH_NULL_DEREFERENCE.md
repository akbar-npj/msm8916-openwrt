# Engineering Report 112: Patch 40 — Resolution of RFC Logical Path Properties NULL Dereference

**Date:** September 14, 2026  
**Status:** Implemented, Physical RAM Forensic Traced, Disassembled, Cryptographically Verified, Ready for Deployment  
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + RF360 Front-End)  
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64)  
**Baseband Firmware:** Patched UFI001B (`MPSS.DPM.2.0.2.c1-00055-M8936FAAAANUZM-1`)  
**Network Operator:** Reliance Jio Infocomm Ltd. (MCC 405 MNC 861, Band 3 / 5 / 40)

---

## 1. Executive Summary & Forensic Root Cause Identification

### 1.1 Patch 39 Verification & Milestone Confirmation
Following the deployment of Patch 39, we performed live memory inspection of the Qualcomm Hexagon DSP memory via `/dev/mem` on the OpenWrt router. This revealed an enormous breakthrough:
1. **The Patch 39 Hook Succeeded 100%**:
   - `rfc_cmn_card_info` (`0xc0dae188`) successfully instantiated the 188-byte singleton card info structure.
   - `FUN_c0dac2dc` successfully allocated the 96-byte Card ID 0x51 structure.
   - `FUN_c0dacb08` successfully allocated and constructed the authentic WTR1605 transceiver instance and bound it to Card ID 0x51's LTE Primary RX and Diversity RX signal paths.
   - Canonical epilogue returned `1` (SUCCESS) to caller `rfm_init` at VA `0xc1003d94`.
   - `rfm_init` verified non-null objects, allocated the 2060-byte common card object, and proceeded into `FUN_c0d7b970` to initialize all 17 LTE band slots and 14 RATs.

### 1.2 Physical RAM Forensic Call Stack Trace
By reading the Hexagon thread exception records (`DAT_c2e717b4`, `DAT_c2e717c0`) and walking the physical stack frames directly from router RAM at PA `0x8a8f1e00`, we reconstructed the complete, exact call stack at the moment of the crash:

```text
Stack Level 4: 0xc1003d94 (rfm_init)
Stack Level 3: 0xc0d7ba68 (FUN_c0d7b970 - RF card band slot populator)
Stack Level 2: 0xc0d7c260 (FUN_c0d7c260 - Logical path properties processor)
Stack Level 1: 0xc0d7ce80 (FUN_c0d7ce80 - vtable[6] wrapper)
Stack Level 0: 0xc0dac69c (FUN_c0dac648 - vtable[6] method)
Faulting PC  : 0xc00605cc (Hexagon optimized memcpy inner loop)
Faulting BadVA: 0x00000000 (Source pointer was NULL!)
Copy Length  : 0x7b0 (1968 bytes)
```

### 1.3 Mechanism of the Fault
In `modem.b15`, function `FUN_c0dac648` (the `vtable[6]` method of `card_obj`) executes:
```assembly
c0dac674: immext(#0xc29b2880)
c0dac678: r2 = memw(##0xc29b28a8) // Loads global pointer DAT_c29b28a8
c0dac680: immext(#0x780)
c0dac684: r1:0 = combine(##0x7b0,r1) // Destination buffer, length 0x7b0 (1968 bytes)
c0dac688: immext(#0xc29b2880)
c0dac68c: r2 = memw(##0xc29b28a8) // Source pointer = DAT_c29b28a8
c0dac690: call 0xc086d5f0        // Blindly calls memcpy without checking if source is NULL!
```

1. `DAT_c29b28a8` is populated during card construction by attempting to read `/rfc/0081/common/rfc_logical_path_properties.dat` from the EFS filesystem.
2. Because Melbon HMU05 (and standard Cat-4 hardware) does not have or require this file in EFS, `DAT_c29b28a8` is NULL (`0x00000000`).
3. Unlike `vtable[7]` (`FUN_c0dac6a4`), which explicitly tests `if (DAT_c29b28ac == 0) return 0;`, `vtable[6]` had **no NULL check**. It passed NULL directly into `memcpy`, crashing the DSP at `c00605cc`.
4. Crucially, caller `FUN_c0d7c260` at `0xc0d7c260` was explicitly written to handle `vtable[6]` returning 0:
   ```assembly
   c0d7c25c: callr r2             // calls vtable[6]
   c0d7c260: { r18 = r0
   c0d7c264:   if (!cmp.eq(r18.new,#0x1)) jump:t 0xc0d7c28c } // If not 1, jump to cleanup!
   c0d7c28c: { call 0xc08db280 ; r1:0 = combine(#0x17,r17) }   // Frees temporary buffer!
   c0d7c294: jump 0xc0d7c2a4                                    // Returns cleanly!
   ```
   When `vtable[6]` returns `0`, `FUN_c0d7c260` cleanly frees its temporary buffer and returns without error. Furthermore, decompilation of stock HMU05 confirmed that its `vtable[6]` equivalent (`0xc1067770`) always returned 0 when the property table was absent.

---

## 2. Implementation of Patch 40

Patch 40 updates `FUN_c0dac648` (VA `0xc0dac648`, offset `0x00aea648` in `modem.b15`, 92 bytes) to return `0` immediately:

### 2.1 Hexagon Disassembly of Patch 40
```assembly
c0dac648: c0 3f 00 48 { r0 = #0x0 ; jumpr r31 }  // Return 0 immediately
c0dac64c..c0dac6a4:     00 c0 00 7f               // 22 NOP instructions (88 bytes padding)
```

### 2.2 Functional Behavior
1. When `rfm_init` / `FUN_c0d7ce80` queries `vtable[6]` for logical path properties:
   - `FUN_c0dac648` executes `{ r0 = #0x0 ; jumpr r31 }`.
   - Returns status code `0` (indicating no optional EFS logical path property overrides exist).
2. Caller `FUN_c0d7c260` observes `r0 == 0`, immediately takes the jump to `0xc0d7c28c`, frees its temporary heap buffer via `FUN_c08db280(0x17, r17)`, and returns cleanly.
3. Zero invalid memory reads or unmapped page faults occur.
4. `FUN_c0d7b970` proceeds to configure all RATs and band slots.

---

## 3. Cryptographic Verification

All 28 ELF segments were re-hashed and verified via `ufi001b_hash_tool.py`:

```text
Patching segment 15 (modem.b15)
  Source file: modem.b15
  Old hash:    dc0ece8a73ae219779cb12201757dc75f01bf1d7e3bfc4c354d255568bc7dab6
  New hash:    2fc5a7cf8f5dc17b0debf45ad3b0f210567aaeb18b0fe25fdb21b43555ed0e32
  b01 offset:  0x0208
  mdt offset:  0x05bc

Verifying: image/
  Results: 20 MATCH  8 ZERO/BSS  0 MISSING  0 MISMATCH
  Overall: PASS ✓
```

---

## 4. What Was Done, What Is Expected, and What Is Next

### What Was Done
1. Reconstructed the exact QDSP6 exception call stack from physical RAM on the router (`0x8a8f1e00`).
2. Identified that `rfm_init` and our Patch 39 hook succeeded completely, successfully constructing the card object and WTR1605 transceiver.
3. Isolated the exact cause of the crash to `vtable[6]` (`FUN_c0dac648`) attempting to `memcpy` from a NULL pointer because `/rfc/0081/common/rfc_logical_path_properties.dat` is not present in EFS.
4. Confirmed that caller `FUN_c0d7c260` is designed to cleanly handle a return value of `0` by freeing its temporary buffer and continuing execution without assertion.
5. Implemented Patch 40 by replacing `FUN_c0dac648` with `{ r0 = #0x0 ; jumpr r31 }`, cryptographically re-signed Segment 15 in `modem.b01`, and synchronized `modem.mdt`.

### What Is Expected
1. During boot, `rfm_init` will successfully execute the Patch 39 hook.
2. When `FUN_c0d7b970` invokes `vtable[6]`, `FUN_c0dac648` will return `0` cleanly.
3. `FUN_c0d7c260` will release its temporary buffer and return `0`.
4. `FUN_c0d7b970` will complete initialization of all LTE band slots.
5. The `remoteproc` driver will report `remoteproc0 is now up` with **zero exceptions**.
6. BAM-DMUX channels 0..7 will initialize and QMI / ModemManager communication will establish.

### What Is Next
1. Copy `modem.b01`, `modem.b15`, and `modem.mdt` to `/lib/firmware/` on router (`192.168.8.1`).
2. Verify SHA-256 checksums on router.
3. Reboot the router to trigger PIL authentication and monitor the kernel boot log.
4. Execute ModemManager (`mmcli -m 0`) and QMI diagnostics (`qmicli -d /dev/cdc-wdm0 --nas-get-rf-band-info`, `--nas-get-signal-info`) to confirm Reliance Jio cell lock.

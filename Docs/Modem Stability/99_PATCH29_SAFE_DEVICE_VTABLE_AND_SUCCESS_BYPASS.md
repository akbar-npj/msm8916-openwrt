# Engineering Report 99: Patch 29 - Safe Static WTR Device Vtables & Success Path Bypass

## 1. Summary of Changes in Patch 29

Patch 29 was deployed to resolve the two major issues identified in Reports 97 and 98:
1. **Restored `card_init` Success Path Bypass**:
   `0x260b8` in `modem.b26` is patched to `{ r0 = #1 }`. This guarantees that `card_init` takes the success branch at `0x260c0` and jumps to `0x260d4` (our hook) on HMU05 hardware, bypassing the failure of `0x261d4`.
2. **Replaced Invalid `STUB_VA` with Clean Duplex Return Stubs**:
   - `0xd2338dd8` was previously used as a catch-all stub in `static_wtr_vtable`. Coredump analysis revealed `0xd2338dd8` was actually a conditional branch to `0x26e50` (an error/exception handler).
   - In Patch 29, clean Hexagon duplex stubs were constructed:
     - `RET0_STUB` at `0xe8180` (VA `0xd23fa180`): `{ r0 = #0 ; jumpr r31 }` (`0x48003fc0`)
     - `RET1_STUB` at `0xe8188` (VA `0xd23fa188`): `{ r0 = #1 ; jumpr r31 }` (`0x48103fc0`)
3. **Implemented Non-Zero Return for Vtable Slot 7 (`+0x1c`)**:
   Decompilation analysis of `FUN_c0d593f8` (line 2486124) revealed that during RF configuration queries:
   ```c
   iVar4 = (**(code **)(*piVar5 + 0x1c))(piVar5, 8, 0, band);
   iVar6 = (**(code **)(*piVar5 + 0x1c))(piVar5, 8, 1, 0);
   if ((iVar4 == 0) || (iVar6 == 0)) {
       FUN_c0883b04(&DAT_c15da06c, ...); // FATAL CONFIG ERROR
   }
   ```
   Slot 7 (`+0x1c`) **must** return non-zero (`1`) indicating that the frequency/band path configuration is valid and supported.
   Slot 7 in both `static_wtr_vtable` and `subdev_vtable` was linked directly to `RET1_STUB`.
4. **Populated PRX and DRX Sub-Device Vtables**:
   `prx_dev` and `drx_dev` were assigned dedicated vtable pointers (`0xd23fa140`) where all unsupported calls safely return 0 without executing unmapped memory or jumping to crash routines.

---

## 2. Segment Hash Verification

All 28 ELF segments were validated with `ufi001b_hash_tool.py`:
- **Segment 26 SHA256**: `eac9f6d0e67ee3a428c33f911c5d80ccc0cd97281cb64c02e461542b867b1382`
- **Result**: `20 MATCH, 8 ZERO/BSS, 0 MISSING, 0 MISMATCH` (`Overall: PASS ✓`)

---

## 3. Test & Verification Plan

Following the target cold reboot:
1. Check dmesg and uptime to confirm clean boot without early SSR.
2. Verify ModemManager detection and transition to `searching`.
3. Execute `qmicli -p -d /dev/wwan0qmi0 --nas-force-network-search`.
4. Execute `qmicli -p -d /dev/wwan0qmi0 --nas-network-scan` to verify complete immunity to the previously observed TLB miss crash.
5. Monitor NAS serving system and signal strength metrics for LTE cell detection.

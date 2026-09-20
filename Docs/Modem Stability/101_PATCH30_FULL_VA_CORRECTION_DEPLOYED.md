# Engineering Report 101: Patch 30 - Full Virtual Address Base Correction to 0xc3e0f000

## 1. Description of Changes

Patch 30 systematically rectified the critical flaw detailed in Report 100, where all injected structures, tables, and pointers were referencing unmapped virtual space (`0xd23...`) instead of the true QDSP6 memory mapping (`0xc3e...`).

### Detailed Modification Summary
1. **Target 1 (Mode 0 Table Getter @ `0x260dc`)**:
   - Instruction: `{ immext(#0xc3eefb00) ; r0 = ##0xc3eefb3c ; jumpr r31 }`
   - Bytes: `ec 7b 3e 0c c0 3f c0 4b`
   - Maps to genuine antenna switch script table at `0xc3e0f000 + 0xe0b3c`.

2. **Target 2 (Mode 1 Table Getter @ `0x260e4`)**:
   - Instruction: `{ immext(#0xc3eefb80) ; r0 = ##0xc3eefb98 ; jumpr r31 }`
   - Bytes: `ee 7b 3e 0c c0 3f 80 49`
   - Maps to genuine WTR1605 device table at `0xc3e0f000 + 0xe0b98`.

3. **Target 3 (Card Routing Buffer Copy @ `0x26118`)**:
   - Size: 1688 bytes (HMU05 authentic).
   - Source: `0xc3eef500` (`0xc3e0f000 + 0xe0500`).
   - Instruction: `{ call memcpy ; immext(#0xc3eef500) ; r2 = ##0xc3eef500 ; r16 = #1 }`
   - Bytes: `22 50 fb 5b d4 7b 3e 0c 18 28 02 28`

4. **Target 4 (Method 7 Device Table Pointer @ `0x26130`)**:
   - Address: `0xc3eefb98`.
   - Bytes: `00 c0 00 78 ee 7b 3e 0c 02 43 00 78 20 40 00 78 00 d4 a1 a1 00 c0 9f 52`

5. **`card_init` Success Path Bypass & Hook**:
   - `0x260b8`: `20 c0 00 78` (`{ r0 = #1 }`).
   - `0x260d4`: `96 d0 18 58 00 c0 00 7f` (`{ jump 0xe8200 } ; { nop }`).

6. **Static WTR Device & Vtables @ `0xe8000` (VA Base `0xc3e0f000`)**:
   - `DEV_VA`: `0xc3ef7000`
   - `VTBL_VA`: `0xc3ef7040`
   - `METH_VA`: `0xc3ef7080`
   - `PRX_VA`: `0xc3ef70c0`
   - `DRX_VA`: `0xc3ef7100`
   - `DVT_VA`: `0xc3ef7140`
   - `RET0_VA`: `0xc3ef7180` (`0x48003fc0` = `{ r0 = #0 ; jumpr r31 }`)
   - `RET1_VA`: `0xc3ef7188` (`0x48103fc0` = `{ r0 = #1 ; jumpr r31 }`)
   - Helper (`0xe8200` / VA `0xc3ef7200`): loads `r1 = ##0xc3ef7000` and fills `card_ptr + 0x424` (17 LTE band slots) with genuine mapped device pointer.

---

## 2. Integrity Verification
- **Segment 26 SHA256**: `c636769dd08d4bec548eeece76a9c2b546711917cfafdcff59436b9460fd8ec5`
- **Tool Result**: `20 MATCH, 8 ZERO/BSS, 0 MISSING, 0 MISMATCH` (`Overall: PASS ✓`)
- **Deployment**: Copied to `/lib/firmware/` and synced. Target rebooted.

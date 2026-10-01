# 240 — Diagnostic firmware patch v5: export the `FUN_c02fda90` CALL BOUNDARY (which caller armed the state-20 watchdog)

Status: **built and verified offline** (image signed, hash tool PASS). **NOT deployed.**
Supersedes the *target* of Doc 239 (same technique, new aim). Ledger item 61.

---

## 1. Goal

`FUN_c02fda90` is the primitive that **sends an ML1 cell-measurement request and arms
the 50 ms "state 20" watchdog** — the watchdog whose expiry is fatal by construction
(Doc 239 / ledger item 60). It has **exactly two callers** (whole-image
`llvm-objdump -d`, authoritative — Ghidra showed only one):

| | call site | containing function | what it is |
|---|---|---|---|
| **A** | `0xc0326874` | `FUN_c032685c` | the ML1 measurement / mobility evaluator |
| **B** | `0xc033c0f4` | `0xc033c0a4` | `LTE_ML1_SM_ACQ_STM` **CELL_MEAS** activity |

We want to know, at the ~902 s fatal: **which caller armed the watchdog**, and
**which message it sent** (`r2==1 → 0x408020d` ; `r2==0 → 0x4070210`).

## 2. Why the CALL BOUNDARY and not the entry (the design decision)

Doc 239 replaced the **entry** instruction `call 0xc0030020` of the target with
`call <cave>`. That works for capturing the *arguments*, but it **cannot** capture
the caller: the entry instruction *is itself a `call`*, so by the time the cave runs
it has already overwritten `r31` (the caller's return address) with its own return
address. The Doc-239 export read `r31 = 0xc02d7bdc` — the callback's **own internal
continuation**, not its caller.

At a **call site** the situation is different: the cave is entered by
`call <cave>`, so `r31` at cave entry **is** the return address into the caller
(`0xc032687c` for A, `0xc033c0f8` for B). That is the direct, unambiguous answer.

The instrumentation is made **semantically transparent** by having the cave
**tail-jump** into `FUN_c02fda90` (`jumpr`, which does not touch `r31`) rather than
`call` it. `FUN_c02fda90` is therefore entered with *exactly* the register state a
direct call would have produced — same `r31`, same `r0..r4` — and returns directly
to the caller.

## 3. The discriminating registers (read off the two call sites)

**Caller A** — `FUN_c032685c`, entry `0xc032685c`:
```
c032685c: { r16 = r1
c0326860:   r1 = r2
c0326864:   memd(r29+#-0x10) = r17:16; allocframe(#0x8) }
c0326868: { r3:2 = combine(r4,r3)          ; r2 = arg4, r3 = arg5
c032686c:   r6 = memh(r16+#0x2) }
c0326870: { p0 = tstbit(r6,#0); if (p0.new) jump:t 0xc0326894 }
c0326874: { call 0xc02fda90                ; <-- site A
c0326878:   r4 = r5 }
```
⇒ `FUN_c02fda90` receives `r0=inst`, `r1=&local_34`, **`r2 = FUN_c032685c arg4`**,
`r3 = arg5`, `r4 = arg6`.

**Caller B** — `0xc033c0a4` (ACQ CELL_MEAS), just before `0xc033c0f4`:
```
c033c0ec: { r3:2 = combine(#0x1,#0x0)      ; r2 = 0x0, r3 = 0x1
c033c0f0:   r4 = #0x0; r0 = r16 }
c033c0f4: { call 0xc02fda90 }              ; <-- site B
```
⇒ `r2 = 0`.

### 3.1 `r2` selects the message
Inside `FUN_c02fda90`:
```
c02fdad4: { p0 = cmp.eq(r22,#0x1); if (!p0.new) jump:t 0xc02fdb88 }
c02fdae0: { r1 = #0x402
c02fdae4:   immext(#0x4080200)
c02fdae8:   r2 = ##0x408020d; r0 = add(r29,#0x98) }
```
where `r22 = r2` (saved at `c02fdaa0: r16 = r0; r22 = r2`). So **`r2==1 → msg 0x408020d`**,
else **`0x4070210`** (the `0x4070210` form is the one Caller B's `r2=0` produces).

### 3.2 ⚠ `r2` alone is NOT enough
`FUN_c032685c` is itself called from **three** sites, and forwards its own `arg4` as
`FUN_c02fda90`'s `r2`:

| site of `call FUN_c032685c` | `arg4` (= `r2` at `FUN_c02fda90`) |
|---|---|
| `0xc03264f8` | `1` |
| `0xc032681c` | `0` |
| `0xc03278f4` | `0` |

⇒ `r2==0` is **ambiguous** (Caller A via those two paths, *or* Caller B).
**`r31` (the return address) removes the ambiguity** — this is the whole reason for
instrumenting the call boundary rather than the entry.

## 4. The patch

**Patch 1 (modem.b16) — redirect both call sites to the cave**
```
0xc0326874 : 0e79fa5b  ->  764ea15b     (call 0xc02fda90 -> call 0xc0030560)
0xc033c0f4 : ceccf85b  ->  36e29e5b     (call 0xc02fda90 -> call 0xc0030560)
```
The `call` offset field is `((target-PC)/2)` as a 22-bit two's complement in
bits[23:16]+[13:0]; bits[31:24] and the packet-parse bits[15:14] are preserved. The
re-encoder was validated by reproducing **both** Doc-239 encodings exactly
(`b84aff5b` unchanged for the same target, `be44ab5b` for `0xc003054c`).

**Patch 2 (modem.b05) — write the 72-byte cave at VA 0xc0030560** (dead nop run
`0xc003054c..0xc0030600`, 180 B; no branch targets it). Assembled with
`llvm-mc -triple=hexagon -mcpu=hexagonv60`:
```
{ r5 = ##0xc14408f0 }              ; base = SAVE_AREA
{ memw(r5+#0x00) = r31 }           ; caller return address
{ memw(r5+#0x04) = r0 }            ; inst
{ memw(r5+#0x08) = r1 }
{ memw(r5+#0x0c) = r2 }            ; ** message selector **
{ memw(r5+#0x10) = r3 }
{ memw(r5+#0x14) = r4 }
{ memw(r5+#0x18) = r29 }           ; caller frame pointer
{ r7 = ##0xc0030560 }              ; magic = the cave's own VA
{ memw(r5+#0x20) = r7 }
{ r6 = memw(r5+#0x1c) }            ; counter
{ r6 = add(r6,#1) }
{ memw(r5+#0x1c) = r6 }
{ r6 = ##0xc02fda90 }              ; tail target
{ jumpr r6 }                       ; r31 untouched -> transparent tail call
```
bytes:
```
2350140c05c6007800df85a101c085a102c185a103c285a104c385a105c485a106dd85a1
154c000c07c4007808c785a1e6c0859126c006b007c685a16a7f020c06c2007800c08652
```

**Register safety.** The cave writes only **r5, r6, r7** — all *caller-saved*, and
none of `gp(r28)`, `fp(r29)`, `sp(r30)`, `lr(r31)`, nor any of the callee-saved set
`r16..r27` that the target's prolog (stub `0xc0030020`) saves. The target's arguments
`r0..r4` are read but never written.

**Save area `0xc14408f0`** — first page of phdr17 (LOADED writable, `filesz=0xa1fe0`;
`b17` size `0xa1fe0` confirms base `0xc1440000`). Chosen in Doc 239 because the modem
**writes it at runtime with cross-boot-varying content** while the image is all-zero
there — the only sound proof of mapped+writable (v1 `0xc51bf000` and v3 `0xc4546800`
both faulted; "free-looking in a coredump" ≠ mapped). Verified here: `b17[0x8f0:0x910]`
is all-zero and the tail `[0x1e6,0x1000)` is all-zero, so the cave's writes are the
only content.

**Export layout** (readback: `read_diag_export_v5.py`):
```
+0x00 r31  caller return address    0xc032687c ~ A  |  0xc033c0f8 ~ B
+0x04 r0   inst
+0x08 r1   arg2
+0x0c r2   arg3  ** message selector **
+0x10 r3   arg4
+0x14 r4   arg5
+0x18 r29  caller frame pointer
+0x1c counter   incremented once per call into FUN_c02fda90
+0x20 magic = 0xc0030560 (the cave's own VA)
```
The magic self-identifies the layout, because the save area is **shared with the
Doc-239 v4 patch**, whose layout differs (`r0,r1,r31,r29,counter`).

## 5. Verification (offline)

* `ufi001b_hash_tool.py verify scratch/diag_patch_v5/image_patched`
  → **19 MATCH, 8 ZERO/BSS, 0 MISMATCH — Overall: PASS ✓** (segments 16 & 5 re-hashed
  into `modem.b01`; `modem.mdt = modem.b00 + modem.b01`).
* Byte-diff vs stock: `modem.b16` **6** bytes (the two call words), `modem.b05` **62**
  bytes (the cave) — no other change.
* The patched call words disassemble to `call 0xc0030560` (both sites; site A still in
  its 2-instruction packet with `r4 = r5`).
* The cave disassembles to exactly the 15 instructions above; the two `##` immediates
  resolve to `0xc14408f0` and `0xc02fda90`.
* Readback smoke-test on `scratch/android_dump/diag_v4.elf` correctly reports
  **"NOT a v5 export"** (magic mismatch) — the guard against a v4-layout dump.

md5 (see `evidence/240_diag_v5/patched_image.md5`):
```
438292ca85b3b15a03e65eace065114d  modem.b16
78212d41339f42ec2fb7317a110ffe1a  modem.b05
70e904259b59d2f993649e96b84a8338  modem.b01
48d7910d02d0559a60be9517b5cf652a  modem.mdt
```

## 6. Pre-registered prediction (to score after a fatal under this image)

The Doc-239-era evidence (ledger item 60) predicted the fatal arm came from **Caller A**
(`FUN_c032685c`, `flags&1` path, `param_3=1`). This image tests that directly:

* **P-V5-1:** the last export before a fatal has **`r31` near `0xc032687c`** (Caller A).
* **P-V5-2:** that export has **`r2 == 1`** ⇒ message **`0x408020d`**.
* **P-V5-3:** `counter` is a *rate* — `FUN_c02fda90` is called repeatedly (the state-20
  watchdog is armed on a cadence), not once.

If P-V5-1 fails (r31 near `0xc033c0f8`), the fatal arm is Caller B (ACQ CELL_MEAS) and
ledger item 60's reachability argument needs revisiting.

## 7. Deployment (only if/when requested — this doc does not deploy)

1. Back up the device's current `modem.mdt` + `modem.b*` (hash them).
2. Push `scratch/diag_patch_v5/image_patched/*` to the modem partition set
   (same procedure as Doc 239 §10); `sync`.
3. Reboot the AP; confirm the modem boots (`ats-probe` responds).
4. Wait for a natural fatal, capture the coredump **device-local** (never to host
   stdout — it truncates silently), then
   `python3 evidence/240_diag_v5/read_diag_export_v5.py <dump.elf>`.
5. Restore stock (Doc 227) and score P-V5-1..3.

## 8. Achieved vs Expected

| item | expected | achieved |
|---|---|---|
| identify both callers | 2 sites (A `0xc0326874`, B `0xc033c0f4`) | ✓ confirmed (whole-image objdump) |
| capture the caller unambiguously | `r31` at the call boundary | ✓ (entry-detour provably cannot) |
| capture the message selector | `r2` | ✓ (plus r0,r1,r3,r4,r29) |
| `r2`-only sufficiency | assumed sufficient | **✗ falsified** — FUN_c032685c has 3 callers, 2 with arg4=0 (⇒ r31 required) |
| like-for-like call re-encode | byte-exact | ✓ (validated against both v4 encodings) |
| cave fits the dead nop run | ≤ 180 B | ✓ 72 B |
| image re-signed | hash tool PASS | ✓ 19 MATCH / 0 MISMATCH |
| semantically transparent | identical entry state | ✓ (tail `jumpr`, r31 preserved) |
| self-identifying export | guard vs v4 layout | ✓ magic `0xc0030560` |

## 9. SOP-compliance statement (Doc 197 living-ledger SOP)

* **Ground-truth first:** every claim above is read from the **stock HMU05 ELF**
  (`scratch/hmu05_stock_elf/modem_hmu05_stock.elf`, md5 `954f2be5…`) and the stock
  segment files — call sites from whole-image `llvm-objdump -d`, byte fields decoded
  and cross-checked, no Ghidra-only claims (Ghidra was in fact wrong about the caller
  count).
* **Pre-registered, scored predictions:** §6 (P-V5-1..3), scored only after a fatal.
* **Reversible / no irreversible step taken:** the patch is built and verified
  **offline**; nothing was flashed. Deployment is gated in §7.
* **Falsification recorded:** the `r2`-only sufficiency assumption was **falsified**
  during design (§3.2) and is recorded in §8, not hidden.
* **Ledger + memory updated in the same session** (ledger item 61; memory §54).

## 10. Evidence inventory (`evidence/240_diag_v5/`)

| file | what |
|---|---|
| `build_diag_patch_v5.py` | the builder (assembles the cave, splices both call sites, re-signs) |
| `build_output.txt` | build log (cave bytes, per-site old→new, hashes, byte-diff) |
| `read_diag_export_v5.py` | coredump readback + caller/message interpretation |
| `readback_smoketest.txt` | smoke test on `diag_v4.elf` (correctly "NOT a v5 export") |
| `patched_image.md5` | md5 of the four rebuilt artifacts |

# Doc 233 — Offline firmware analysis: the ~902.7 s deadline is NOT a constant in the image, and the fatal callback is identified and byte-verified

**Date:** 2026-09-29
**Device:** none — **offline only** (the item-43 Android cascade and the 24 h soak were left running untouched)
**Subject:** stock HMU05 `modem.mdt` + `modem.bNN` → `scratch/hmu05_stock_elf/modem_hmu05_stock.elf`
(`modem.b16` md5 `57fef19de7178fb732c8b2edc40bc9dc`)
**Cross-checks:** `scratch/hmu05_patch_test/patched/modem.b16` (md5 `b7b799693bba0373b6825418ae8af68b`),
`Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` (77 099 functions, 105 MB)
**Tools:** `scratch/doc232_const_scan.py`, `scratch/doc232_const_focus.py` (new)
**Ledger:** issued under `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — **item 44**
**Continues:** Doc 231 (the ML1-timer patch relays the fatal), Doc 230 §7.6, `project_900s_fatal_anatomy.md` §29/§32
**⚠ Ledger↔docs drift noted:** ledger item 38 and memory §29 both cite a **"Doc 232"** that **does not
exist** in `Docs/Modem Stability/`. That finding lives in `900S_CRASH_LPR_FRAMEWORK_RE.md` §11. This
document takes the next free number (233) rather than silently occupying the reserved one.

---

## 1. Why this was run

Item 43 route 3: *if the ~902.7 s / ~904.95 s deadline is a counter wrap or a literal timeout, the
constant should be findable in the image, and a firmware patch could move it.* That is the cheapest,
lowest-risk test available, and it is **decisive in one direction**: if the constant is absent, the
"patch the magic number" route is closed permanently.

Two questions were asked:

* **Q1** — does any 32/64-bit encoding of the observed period exist anywhere in the image?
* **Q2** — what function actually carries the 10 patched sites, and what is the condition there?

---

## 2. Q1 — the constant is ABSENT (a measured negative)

### 2.1 What was searched

Every observed period — `904.954823` (fatal→fatal), `902.712` (modem running time), `902.7067` (pmOS
mean), `903.6746` / `903.6752001` (the OpenWrt invariant), `902.798` (Android mean), `1529.4` (the first
fatal) — in **300+ encodings**:

| family | encodings |
| :-- | :-- |
| tick counts | `19.2 / 9.6 / 4.8 / 2.4 / 1.92 / 1.2 / 0.96 / 0.48 MHz`, `32768 Hz`, `8 263 680 Hz` (the a2 timetick), `1 MHz`, `13 MHz` — each as **u32 and u64** |
| decimal | µs, 100 µs, ms, centiseconds |
| float | `f32` and `f64` of the period **and of its reciprocal** |
| quanta | the MCPM `0.32 / 0.64 / 1.28 s` quanta |
| derived | `2.259187 s` (Doc 230 §7.6's unit) in all of the above |
| thresholds | `400`, and the round-number family (`900000`, `900`, `9e8`, `9e9`, `15294`, `152940`, `1529400`, `54000`, `540000`) |

### 2.2 Result

**Every distinctive value returns zero hits.** The only hits are on small ubiquitous integers that occur
thousands of times in data (`15` ×2017, `900` ×23, `400` ×73, `9000` ×7) — indistinguishable from noise.

```
  zero  f2f us=904954823 u32 / u64            zero  2.259187 f32 / f64
  zero  f2f ms=904955 u32 / u64               zero  2.259187 us=2259187 u32
  zero  run us=902712000 u32 / u64            zero  2.259187@19.2M=43376390 u32/u64
  zero  owrt us=903674600 u32 / u64           zero  f2f / run / pmos / owrt  f32 & f64
  zero  all 10 tick rates x u32 and u64       zero  all reciprocals f32 & f64
  zero  900000 u64 / 9e8 / 9e9 / 152940*      zero  0x35A4E900 (=900000000)
```

### 2.3 The scanner was validated before its zero was believed

A negative is worthless unless the instrument is proven to work. Two controls:

1. **Positive control** — a u32 read out of segment 16, 18 and 25 at a known VA is re-found at its exact
   file offset (`OK=True` for all three probes).
2. **Same-scanner hits** — the identical code finds `900000` ×6, `900` ×23, `15` ×2017, `400` ×73.

⇒ **The scanner works; the constants are not there.**

### 2.4 The counter-wrap hypothesis also fails

For a counter wrap at `2^k` ticks, the implied clock is `f = 2^k / P`. Sweeping `k = 16..40`:

| k | f (Hz) | nearest standard | error |
| --: | --: | :-- | --: |
| 32 | 4 746 057 | *(not a standard clock)* | — |
| 30 | 1 186 514 | 1.2 M | −1.1238 % |
| 31 | 2 373 029 | 2.4 M | −1.1238 % |
| 33 | 9 492 114 | 9.6 M | −1.1238 % |
| 34 | 18 984 229 | 19.2 M | −1.1238 % |
| 35 | 37 968 457 | 38.4 M | −1.1238 % |

The `k = 32` "fit" is a **tautology** — `f ≡ 2³²/P` by construction, and `4.746 MHz` is not a modem
clock. Every member of the `1.2 / 2.4 / 9.6 / 19.2 / 38.4 MHz` family is uniformly **1.12 % off**.

⇒ **No power-of-two wrap of any standard clock produces the period.**

### 2.5 Conclusion for Q1

**The period is EMERGENT, not a stored constant.** The "find the magic number and patch it" route is
**CLOSED**. This is consistent with two independent prior results: ledger item 38 (the per-tech
accounting layer that would *hold* such a watchdog does not execute) and Doc 231 §5 (the clock is a
*condition*, not a code path).

---

## 3. Q2 — the fatal callback, identified and byte-verified

### 3.1 `0xc02d7bd0` is not a function — it is a registered callback

Doc 231 §3 named the patch target `FUN_c02d7bd0`. **`grep FUN_c02d7bd0` in the 105 MB HMU05 decompile
returns 0 hits.** The reason:

* the enclosing function is **`FUN_c02d7b80`** (`0xc02d7b80`–`0xc02d7e00`, 640 B), and it is a
  **registrar**:

```c
void FUN_c02d7b80(int param_1)
{
  undefined *puVar1 = 0;
  if (*(sbyte *)(param_1 + 0x38) == 0x13) { puVar1 = &DAT_c312b9d4; FUN_c09141a0(&DAT_c312b9d4,0); }
  *(undefined2 *)(param_1 + 0x3a) = 0;          /* zeroes u16 counter A */
  *(undefined2 *)(param_1 + 0x3c) = 0;          /* zeroes u16 counter B */
  thunk_FUN_c0b61cc0(param_1, puVar1, &UNK_c02d7bd0, param_1);   /* <-- registers 0xc02d7bd0 */
  return;
}
```

* `FUN_c0b61cc0` → `FUN_c0914b20(param_1, param_2, 0, 0, param_3, param_4)`, i.e. a
  **register-callback API** with the callback at `0xc02d7bd0`.
* Because the address is taken as **data** (`&UNK_c02d7bd0`), Ghidra never disassembled it — hence
  `UNK_`, and hence the earlier analysis could only infer it.

⇒ **Doc 231's "registered ML1 timer callback" reading is CONFIRMED**, and the object layout is now
known: `obj+0x38` = a tech/state id (compared to `0x13`), `obj+0x3a` and `obj+0x3c` = two u16 counters
**zeroed at registration**.

### 3.2 The callback is a dispatcher with exactly 10 sites

`llvm-objdump -d --triple=hexagon` (llvm-objdump **does** carry the Hexagon target) over
`0xc02d7bd0`–`0xc02d7dfc`:

```
c02d7bd0: call 0xc02d1140
c02d7bd8: memd(r29+#-0x10) = r17:16; allocframe(#0xb0)
c02d7be4: r2 = memw(gp+#0xba64)
c02d7be8: r2 = memw(r2+r1<<#0x2)
c02d7bec: jumpr r2                      <-- DISPATCH on r1 through a table at gp+0xba64
```

followed by **exactly 10** repetitions of the same idiom (see §3.3).

### 3.3 The site idiom

```
r1 = #0x41b ; immext(#0x41b0400) ; r2 = ##0x41b041a ; r0 = add(r29,#0x80)
call 0xc02871ac          ; tail-call -> FUN_c0b63880(buf, r1, r2)   build the F3 message record
r0 = add(r29,#0x80) ; r1 = memb(r16+#0x38) ; r2 = add(r29,#0x80)
call 0xc0287198          ; tail-call -> FUN_c0b62f10(buf, 0x18)     the F3 MESSAGE EMITTER
memb(r2+#0x10) = r1      ; record the tech id
p0 = cmp.eq(r0,#0x0) ; if (p0.new) jump:t 0xc02d7df8    ; if not delivered -> epilogue
call 0xc0879150          ; *** the NON-RETURNING fatal handler ***
```

Verified by disassembling the two tail-call thunks directly:

```
c0287198: immext(#0x8dbd40)
c028719c: r1 = #0x18 ; jump 0xc0b62f10      <-- FUN_c0b62f10(buf, 0x18)
c02871ac: immext(#0x8dc6c0)
c02871b0: jump 0xc0b63880                   <-- FUN_c0b63880(buf, r1, r2)
```

### 3.4 What the helpers are

* **`FUN_c0879150` is the fatal handler, not a logger.** Ghidra annotates its callers with
  `/* WARNING: Subroutine does not return */`, and `FUN_c0b62f10`'s own prologue shows the canonical
  assert idiom:

```c
if (!(bool)(-(DAT_c2fe7220 == 0x31415926) & '\x01')) { FUN_c0879150(&DAT_c3c5bd60); }  /* fatal */
if (puVar2 == (undefined4 *)0x0)                     { FUN_c0879150(&DAT_c3c5bd70); }  /* fatal */
if (!(bool)(-(0xf < uVar6) & '\x01'))                { FUN_c0879150(&DAT_c3c5bd80); }  /* fatal */
```

  (`0x31415926` — π — is the "subsystem initialised" sentinel.) It has **12 286 call sites** in the
  decompile, i.e. QCOM's `ERR_FATAL`/`ASSERT` macro is used densely across the image.

* **`FUN_c0b63880(buf, param_2, param_3)` builds the message record**: `+0 = param_3 (u32)`,
  `+4 = param_2 (u16)`, `+6 = a per-call counter`, `+7 = 0`, `+8 = 0x7f`, `+9 = +10 = 0`.

* **`FUN_c0b62f10(msg, len)` is the F3 emitter** (`msg_send`): `if (msg==0) assert`, `if (len>0xf)
  assert`, atomic bump at `0xc3c0e83c`, then walk `FUN_c0b64aa0`/`FUN_c0b64c00` (group first/next),
  `FUN_c0b648a0`/`FUN_c0b657a0` (per-group mask lookup), `FUN_c0b65980(0xa0, len, msg, …, maskbit, …)`
  (the sink). It returns **0 / 1 / 0x6d** (`0x6d` = "no registered group").

* **The one true state test** in the callback is at `0xc02d7c60`:

```
c02d7c60: r0 = memuh(r16+#0x3c)
c02d7c64: if (!cmp.eq(r0.new,#0x1)) jump:t 0xc02d7df8
```

  i.e. the block at `0xc02d7c60` runs **only when the u16 at `obj+0x3c` == 1** — and `obj+0x3c` is one of
  the two counters `FUN_c02d7b80` zeroes at registration.

⇒ **The "fatal" is a FATAL-severity F3 message, and the callback is a message dispatcher.** This is the
structural reason **assert-site patching RELAYS** (Doc 231 §5.2): suppressing one report does not change
the state that the next report site observes.

### 3.5 The patch scope is independently confirmed

Byte-diff of stock `57fef19d` vs patched `b7b79969` `modem.b16`:

* **98 differing bytes in 27 runs**, all inside file `0x50c18`–`0x50def` → VA `0xc02d7c18`–`0xc02d7def`.
* Every run is the 4-byte packet `{ call 0xc0879150 }` (`9c4ab45a` …) replaced by
  `{ jump 0xc02d7df8 }` + `nop` (`f0c00058 00c0007f`).
* The 10 patched addresses are **`0xc02d7c18, c4c, c90, cb8, ce0, d14, d48, d80, dd8, de4`** — exactly
  the 10 call sites found by disassembly.

⇒ Doc 231 §3's "jump-to-epilogue at the 10 sites, one localized function" is **independently confirmed**.

---

## 4. ⚠ Scope — what this analysis CANNOT see

The image is **not uniformly scannable**. Per-segment entropy:

| segment | VA | filesz | entropy | printable | note |
| :-- | --: | --: | --: | --: | :-- |
| ph16 | `0xc0287000` | 18 341 392 | 6.90 | 38.8 % | **code**, plaintext |
| ph18 | `0xc1500000` | 7 580 896 | 6.74 | 41.3 % | **strings**, plaintext — the `.c` pool lives here |
| ph25 | `0xc449a000` | 309 120 | 5.23 | 95.3 % | **strings**, plaintext |
| ph23 | `0xc3c1c000` | 503 871 | **7.89** | 33.8 % | **compressed/encrypted — unscannable** |
| ph24 | `0xc3c98000` | 8 396 540 | **7.91** | 34.7 % | **compressed/encrypted — unscannable** |
| ph20 | `0xc1f2c000` | **0** | — | — | **`filesz = 0` — NO DATA IN THE IMAGE** |
| ph21 | `0xc2070000` | **0** | — | — | **`filesz = 0`** (28.9 MB mem — the runtime RAM segment) |
| ph26 | `0xc44e6000` | **0** | — | — | **`filesz = 0`** (13.7 MB mem) |

⇒ **The §2 negative is scoped to the plaintext, file-backed segments.** A constant living in ph23/ph24,
or written into ph20/21/26 at runtime, is invisible to *any* image scan. So the correct statement is:

> **No constant encoding the period is statically visible in the HMU05 image.**
> **Not:** "no such constant exists."

This is exactly the discipline `feedback_measurement_discipline.md` demands — a negative is only as wide
as its window, and the window must be stated **before** the negative is used.

---

## 5. ⚠ Open item — the `line 390` encoding is NOT decoded

Doc 231 calls these "the 10 `lte_ml1_common_timer.c:390` sites". Two facts are consistent with that; one
is not:

**Consistent ✅**

* `lte_ml1_common_timer.c` occurs in the image **exactly 10 times** — `modem.b18` file offset
  `0x25b2b8 + k·0x18`, VA `0xc175b2b8` … `0xc175b390`. **10 copies for 10 sites.**
* The pool holding them is **sorted and pointer-free**: 45 273 NUL-separated names, `sorted == True`, and
  `lte_ml1_common_timer.c` occupies the **10 consecutive slots 20601–20610**. There are **0** u32
  pointers to any of the 10 VAs anywhere in the image and **0** `immext` references from code — file
  names are reached **positionally**.
* The registrar (`FUN_c02d7b80`) and the 10 sites are confirmed and byte-verified (§3).

**Not consistent ❌**

The message constants in the callback are:

| site VA | `r1` | `r2` |
| :-- | --: | --: |
| `c02d7bf0` | `0x41b` | `0x041b041a` |
| `c02d7c24` | `0x403` | `0x04030442` |
| `c02d7c68` | `0x405` | `0x0405042f` |
| `c02d7c9c` | `0x401` | `0x0405043a` |
| `c02d7cc4` | `0x401` | `0x04200409` |
| `c02d7cec` | `0x401` | `0x043a0404` |
| `c02d7d20` | `0x405` | `0x04050451` |
| `c02d7d90` | `0x401` | `0x042a0405` |

i.e. **lines in the 1025–1105 range, not 390.** Three encodings were tested and **falsified**:

1. **file-id = the high 16 bits.** Indices `1025 / 1027 / 1029 / 1051 / 1056 / 1066 / 1082` resolve to
   `a2_dl_phy_hspa.c`, `a2_ul_phy.c`, `a2_ul_sec.c` — not `lte_ml1_common_timer.c`.
2. **the constant as a short-form VA with base `0xc0000000`** (`0x041b041a` → `0xc41b041a`). All eight
   land in **high-entropy ph24 data**, not on strings or descriptors.
3. **a literal `"lte_ml1_common_timer.c:390"` string** — absent (the 10 strings are bare `…timer.c`).

⇒ **The function identification stands; the line mapping does not.** "The 10 sites are the
`common_timer.c:390` sites" is currently supported by the **patch behaviour only**, not by a decoded
record. Flagged rather than papered over.

---

## 6. Achieved vs Expected

| # | expected | achieved | verdict |
| :-- | :-- | :-- | :-- |
| 1 | find a constant matching the period in the image | 300+ encodings, all zero; scanner validated by positive control and round-number hits | **NEGATIVE (measured)** |
| 2 | explain the period as a counter wrap | no standard clock at any `2^k`; the `k = 32` fit is a tautology | **NEGATIVE** |
| 3 | locate the function containing the fatal sites | `0xc02d7bd0` — a **registered callback** (Ghidra `UNK_`), registrar `FUN_c02d7b80` | **MET** |
| 4 | read the assert condition | 10× `{msg_hdr_init; msg_send; if(!delivered) return; FUN_c0879150}`; one real state test at `obj+0x3c == 1` | **MET (structure)** |
| 5 | confirm Doc 231's patch scope independently | 98 bytes / 27 runs, all 10 `call 0xc0879150` → `jump epilogue` | **MET** |
| 6 | map the sites to `lte_ml1_common_timer.c:390` | 10 string copies = 10 sites ✅, but the line field reads 1025–1105 ❌ | **NOT MET — OPEN** |
| 7 | state the scan's blind spots before using the negative | ph23/ph24 high-entropy; ph20/21/26 `filesz = 0` | **MET** |

---

## 7. SOP compliance

| SOP step | status |
| :-- | :-- |
| Ground truth first | ✅ both `modem.b16` images md5-verified before the diff; the ELF is a rejoin of the stock `mdt` + `bNN` |
| Read the definition, not the name | ✅ **this is the document's core finding** — `FUN_c02d7bd0` is *not a function*; it is `UNK_` data taken by address; the enclosing function is `FUN_c02d7b80` |
| Instrument validated before its negative is believed | ✅ positive control re-finds known u32s; the same scanner hits the round-number family |
| A negative is only as wide as its window | ✅ §4 states the blind segments **before** the negative is used |
| Honest about what is NOT established | ✅ §5 records the undecoded line encoding as **OPEN** rather than asserting Doc 231's mapping |
| Reversibility / no device risk | ✅ **offline only** — no device write, no firmware change; the item-43 cascade and the 24 h Android soak were left running |
| Ledger + memory updated in the same session | ✅ ledger **item 44**; `project_900s_fatal_anatomy.md` §33 |
| No blind baseband patch | ✅ nothing was patched; the constant search was explicitly chosen as the *safe* route |

---

## 8. Falsifiers (state before use)

* **"The period is not statically visible"** — refuted by finding any u32/u64/f32/f64 equal to a
  tick-count or decimal form of the period **inside ph23 or ph24 after decompression**, or by reading
  the deadline from a runtime dump of ph20/21/26.
* **"The 10 sites are the `common_timer.c:390` sites"** — refuted by decoding the `(0x41b, 0x041b041a)`
  record pair to a *different* file/line; **confirmed** by decoding it to `lte_ml1_common_timer.c:390`.
* **"`FUN_c0879150` is the fatal handler"** — refuted by showing it returns normally on the paths used by
  the 10 sites.

---

## 9. What this changes, and what it does not

**Changes**

* The **"patch the magic constant"** route is closed. The period is emergent.
* The patch target is now a **named, disassemblable object** with a known registrar and a known object
  layout (`+0x38` tech id, `+0x3a`/`+0x3c` counters) — no longer an inferred `FUN_` address.
* Doc 231's patch scope is independently byte-verified.
* A concrete, previously unstated **blind-spot** is on the record: ph23/ph24 are compressed, and
  ph20/21/26 are not in the image at all.

**Does not change**

* **Root cause remains UNIDENTIFIED.** This document adds no new mechanism and no fix.
* Item 38/§29 stands: the per-tech accounting layer does not execute, so `400 × 2.259187 s` is not the
  live watchdog.
* Item 43/§32 stands: the 902.7 s clock is **armed by an SSR**, and the Android arm reproduces it on
  demand.
* No firmware patch is warranted by anything here.

**The two remaining decisive routes are unchanged** (item 43): read the modem's own clock at the crash
(`ATS_RTC`), and the arm/disarm test.

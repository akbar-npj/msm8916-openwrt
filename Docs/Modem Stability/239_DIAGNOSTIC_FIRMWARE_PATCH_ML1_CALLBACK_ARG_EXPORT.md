# 239 — THE DIAGNOSTIC FIRMWARE PATCH: export the ML1 timer callback's arguments at every invocation

Date: 2026-09-30
Ledger: this doc is item **57** of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`
Device: HMU05 (UFI001B) on **Android** — image **v4 FLASHED**, **E1 CONFIRMED** (the modem boots and
does **not** crash-loop); the natural-fatal coredump capture is **armed / in progress**.
Question (user, verbatim): **"can we not instrument modem firmware in android or anything else
suspected by patching it so that it reveals what caused it to crash"** → then **"firmware patch worth
building is diagnostic , that is what i want to experiment now"** → **"Build the diagnostic firmware
patch"**.
Status: **★★★ THE EXPORT IS READ (v4 live on device).** E1 = CONFIRMED; the save area is MAPPED
(proven by the absence of the v1/v3 crash-loop); the natural ~902.4 s fatal fired and its coredump was
captured; the cave **RAN** (marker `0xc02d7bdc` matches) and exported the callback's arguments.
See **§6.2** for the numbers and **§6.3** for the decoded dispatch.

> **★★★ Revision history of the save area (read §3.3 before touching the builder).** Three addresses
> were tried; the first two **faulted and crash-looped the modem**:
> * **v1 `0xc51bf000`** — tail of phdr26 (declared BSS). First store faulted (`PC=0xc0030554`,
>   `BADVA=0xc51bf000`). **Unmapped.**
> * **v2 `0xc4d90000`** — rejected offline before flashing.
> * **v3 `0xc4546800`** — "looked free" (nonzero in every dump) but was **byte-identical in 6/6 dumps**:
>   it is **loader fill** (`0xf8f8f8f8`), never written by the modem ⇒ **unmapped**. Flashed; faulted
>   (`BADVA=0xc4546800`), crash-looped again.
> * **v4 `0xc14408f0`** — first page of phdr17 (**a LOADED RW data segment**). Chosen because the modem
>   itself **writes** it with **cross-boot-varying** data while the image is all-zero there ⇒ **mapped
>   and writable.** Flashed; **no fault, no crash-loop** ⇒ E1 CONFIRMED.
>
> **The criterion is NOT "nonzero/free-looking in a coredump" (that is FALSE — loader fill looks used).
> The criterion is: the modem WRITES the page with data that VARIES across boots.** See §3.3, §4.3.

---

## 1. Why this doc — the design constraint, stated before any code

Two prior results bound what a *useful* diagnostic patch can be.

* **Doc 231 — patching the ML1 assert RELAYS the fatal.** The 10 assert sites in the registered ML1
  timer callback are `call 0xc0879150` into a shared assert helper. Neutralising them does not remove
  the fault; it moves it. The clock is untouched. ⇒ **do not touch the assert.**
* **Doc 233 — the ~902.7 s period is NOT a constant.** A whole-image census for the period value
  (300+ encodings) returned **0** hits. There is no threshold to move, and no literal to find. ⇒
  **there is nothing to "fix" by moving a constant.**

So the only firmware experiment that can add information is one that **does not change control flow at
all** and instead **records the state the callback is handed**. That is what this patch does.

> **The instrument is deliberately NOT a fix and NOT a guard.** It branches on nothing, relays nothing,
> and skips nothing. It writes five words to a save area and then reproduces the exact semantics of the
> instruction it replaced. If it changes the crash rate, that is itself a finding (it would mean the
> crash depends on a side effect of the replaced instruction); it cannot *cause* the fatal by
> construction.

## 2. The site and the target, restated from ground truth

From **Doc 233** (re-verified here by disassembly, §3):

| item | value |
| :-- | :-- |
| the fatal callback | `FUN_c02d7bd0`, in `lte_ml1_common_uemob.c` (Doc 233 §42) |
| the assert sites | 10 × `call 0xc0879150` inside that callback, all reporting `lte_ml1_common_timer.c:390` |
| how it is registered | `FUN_c02d7b80(param_1)` → `thunk_FUN_c0b61cc0(param_1, puVar1, &UNK_c02d7bd0, param_1)` |

The **entry packet** of the callback (disassembled, §3):

```
c02d7bd0:  { call 0xc02d1140 ;  r16 = r0 ;  memd(r29+#-0x10) = r17:16 ;  allocframe(#0xb0) }
```

* `0xc02d1140` is a **`return 0` stub**: `{ r0 = #0x0 ; jumpr r31 }`.
* Because `r16 = r0` executes **in parallel** with the call, `r0` at that instant still holds the
  callback's **incoming first argument** — the **timer-context pointer**.
* So the callback's body receives `r0 = 0` in `r16`, and the **context pointer is destroyed** one slot
  after entry. **That is the value we want, and it is only available at this exact site.**

**Why the context pointer matters.** Doc 233 §42 records that the callback's context object holds
`0xc02d7bd0` at `+0x94` and the fatal case's entry (`0xc02d7d8c`) at `+0x9C`. The fatal is reached by a
31-state jump-table dispatch (`jumpr r2`). If we capture the context pointer **and** the dispatch index
at the last invocation before the crash, we can walk the timer object **in the coredump** and read the
state the fatal saw — without the AP ever needing to read modem memory live (which it cannot; Doc 172
§1).

## 3. The two patches

### 3.1 Patch A — `modem.b16`, the entry detour (3 bytes)

Redirect the callback's first `call` from the `return 0` stub to a code cave:

| | VA | bytes | meaning |
| :-- | :-- | :-- | :-- |
| old | `0xc02d7bd0` | `b8 4a ff 5b` | `call 0xc02d1140` |
| new | `0xc02d7bd0` | `be 44 ab 5b` | `call 0xc003054c` |

b16 file offset = `VA − 0xc0287000` ⇒ `0x50bd0`. Only **3 bytes** change (`0x50bd0/1/2`); the fourth
byte `0x5b` is common to both encodings.

### 3.2 Patch B — `modem.b05`, the cave (44 bytes at `0xc003054c`)

The cave sits in a **180-byte `nop` run** in phdr5 (VA `0xc0030000`, `RWE`), preceded by an
**unconditional `jump 0xc0030538`** (dead padding). Verified: **no branch anywhere targets this run**
(`find_cave.py`/`find_cave2.py`). b05 offset = `VA − 0xc0030000` ⇒ `0x54c`.

The cave is 11 instructions (disassembled, §3.4):

```asm
    immext(#0xc14408c0)
    r2 = ##0xc14408f0          ; SAVE_AREA  (modem VA)
    memw(r2+#0x00) = r0        ; the callback's first argument  (timer ctx)
    memw(r2+#0x04) = r1        ; the second argument            (dispatch index)
    memw(r2+#0x08) = r31       ; return address into the callback (fixed marker)
    memw(r2+#0x0c) = r29       ; frame pointer at entry
    r0 = memw(r2+#0x10)        ; counter
    r0 = add(r0,#1)
    memw(r2+#0x10) = r0        ; counter++
    r0 = #0x0                  ; <-- the replaced stub's own semantics
    jumpr r31
```

**Control-flow neutrality.** The cave ends with `r0 = #0x0 ; jumpr r31` — byte-identical in effect to
the stub it replaced (`0xc02d1140`). The caller resumes at **`0xc02d7bdc`** (the packet after the entry
packet; §3.4) with `r0 = 0`, so `r16 = r0` still yields `r16 = 0`, exactly as before. Nothing is
skipped, nothing is branched on.

**Register neutrality.** The cave clobbers **`r2`** (and `r0`, which it restores to the stub's value).
`r2` is **caller-saved** under the Hexagon ABI, and the instruction it replaced is a **`call`** — which
already licenses the callee to clobber `r2`. The callback therefore cannot hold a live value in `r2`
across it. (Empirically the callback body's first `r2` access is a **write**, `r2 = memw(gp+#0xba64)`
at `0xc02d7be4`.) `r31` is written only by the detour `call` itself, which is its defined behaviour.

### 3.3 The save area — `0xc14408f0`

| property | value |
| :-- | :-- |
| VA | `0xc14408f0` (modem) |
| phys | `0xc14408f0 − 0x39800000` = `0x87c408f0` |
| where it lives | first page (`0xc1440000`) of **phdr17** (VA `0xc1440000`, phys `0x87c40000`, **RW, filesz `0xa1fe0`**) — a **LOADED** writable data segment |
| why it is safe | the image is **all-zero** at that page, yet **every coredump holds 39..122 nonzero words** there ⇒ the modem **writes** it at runtime; the content **varies across boots** (0.066 bit-difference) ⇒ genuine live data, not fill. The store slot `[+0x8f0, +0x1000)` is a **stable zero run** in 8/8 dumps |

> **★★★ The criterion for a mapped+writable save area — and the two failures that taught it.**
> A modem coredump captures **physical RAM**, which includes **loader fill** (`0xf8f8f8f8` pages) and
> zero pages that persist across boots but are **not mapped**. So *"nonzero / free-looking in a
> coredump"* does **NOT** imply mapped. The sound criterion is:
> **a page the modem WRITES with data that VARIES across boots** (a write proves it is mapped *and*
> writable), **plus** a large run that is **zero in every dump** (so the export cannot collide with
> live state). A page that is pure `0xf8f8f8f8` / zero / **byte-identical in all dumps** is a hole.
>
> | version | VA | segment | dump evidence | live result |
> | :-- | :-- | :-- | :-- | :-- |
> | v1 | `0xc51bf000` | phdr26 (BSS, `filesz=0`) | zero in 6/6 | **FAULT** `BADVA=0xc51bf000` → crash-loop |
> | v2 | `0xc4d90000` | — | written in 49 dumps | **rejected offline before flashing** |
> | v3 | `0xc4546800` | phdr26 (BSS) | "nonzero" but **byte-identical 6/6** = **fill** | **FAULT** `BADVA=0xc4546800` → crash-loop |
> | **v4** | **`0xc14408f0`** | **phdr17 (LOADED RW)** | image=0, dumps=39..122 nonzero words, **varies across boots**, free tail | **E1 OK — no fault** |

> **★ The save area is ONLY readable from a coredump.** phdr17 is a loaded segment, but the **AP cannot
> read modem memory by any route** (Doc 172 §1, TrustZone), so the export is still read from a
> **modem ramdump** — which the project captures on demand (`reference_android_ramdump_instrument.md`;
> on OpenWrt the on-demand recipe is Doc 217 §6.2a: `rmmod qcom_bam_dmux` first, then
> `echo enabled > …/coredump`, `echo 1 > …/crash`, stream `/sys/class/devcoredump/devcd0/data`).

### 3.4 Disassembly of the patched image (the verification that the bytes decode as intended)

The two patches were applied to a copy of the stock ELF (`verify_elf.py`) and disassembled:

```
c02d7bd0:  be 44 ab 5b   { call 0xc003054c }          <-- ENTRY: detour in place
                                                          (stock: { call 0xc02d1140 ; r16 = r0 ;
                                                           memd(r29+#-0x10) = r17:16 ; allocframe(#0xb0) }
                                                           spans 0xc02d7bd0..0xc02d7bd8 => the return
                                                           address r31 = 0xc02d7bdc)

c003054c:  23 50 14 0c   { immext(#0xc14408c0)
c0030550:  02 c6 00 78      r2 = ##-0x3ebbf710 }        (= 0xc14408f0)
c0030554:  00 c0 82 a1   { memw(r2+#0x0) = r0 }
c0030558:  01 c1 82 a1   { memw(r2+#0x4) = r1 }
c003055c:  02 df 82 a1   { memw(r2+#0x8) = r31 }
c0030560:  03 dd 82 a1   { memw(r2+#0xc) = r29 }
c0030564:  80 c0 82 91   { r0 = memw(r2+#0x10) }
c0030568:  20 c0 00 b0   { r0 = add(r0,#0x1) }
c003056c:  04 c0 82 a1   { memw(r2+#0x10) = r0 }
c0030570:  00 c0 00 78   { r0 = #0x0 }
c0030574:  00 c0 9f 52   { jumpr r31 }
```

> **★ The return marker is `0xc02d7bdc`, NOT `0xc02d7bd4`.** Disassembling the stock entry (above)
> shows the packet spans **three** instructions (`0xc02d7bd0/4/8`), so the next packet — and therefore
> the `call`'s link value in `r31` — is **`0xc02d7bdc`**. An earlier revision of this doc (and of
> `read_diag_export.py`'s marker) said `0xc02d7bd4`; that was an off-by-one-packet error, corrected
> here and confirmed against the live exception-dump task context.

## 4. The build and the offline verification

### 4.1 The build

`scratch/diag_patch/build_diag_patch.py` copies the pristine HMU05 image, applies both patches with
**preconditions asserted** (the old bytes must match exactly), re-hashes segments 16 and 5 into
`modem.b01`, rebuilds `modem.mdt = b00 + b01`, and re-verifies. Reproduced output:

```
[+] pristine image copied -> scratch/diag_patch/image_patched
[+] b16 @0x50bd0 (VA 0xc02d7bd0): b84aff5b -> be44ab5b
[+] b05 @0x54c (VA 0xc003054c): wrote 44B cave
[+] b01 seg16 @0x228: ab795bf097be.. -> 36aa04829ca2..
[+] b01 seg5  @0xc8 : 6cada5c9c025.. -> 1b728f111060..
[+] rebuilt modem.mdt (8220 B)
[+] hash re-verify: PASS
[+] modem.b16: 3 differing bytes at ['0x50bd0', '0x50bd1', '0x50bd2']
[+] modem.b05: 32 differing bytes at [0x54c..0x577]
```

**Only the intended bytes differ from stock.** b16: 3 bytes. b05: 32 bytes, all inside `0x54c..0x578`.
Every other `modem.bNN` is byte-identical to stock.

### 4.2 ★ The project's own verifier PASSES

`python3 GitIgnore/compare/ufi001b_hash_tool.py verify scratch/diag_patch/image_patched`:

```
  Results: 19 MATCH  8 ZERO/BSS  0 MISSING  0 MISMATCH
  Overall: PASS ✓
```

This is the same verifier used for every deployed baseband patch (v1–v19). **The segment hash chain is
consistent**, i.e. the image satisfies the on-device loader's integrity model.

### 4.3 ★ How the save area was chosen — and why "zero in all dumps" was the WRONG test

The v1 page (`0xc51bf000`) **passed** the original test ("zero in 6/6 dumps, with nonzero controls") and
was still **unmapped** — it faulted on the first live store. The test was wrong because **an unmapped
hole in a modem coredump reads as zero** (there is nothing to dump there). "Zero everywhere" cannot
distinguish *free-and-mapped* from *not-mapped-at-all*.

The correct discriminator is a **modem write with cross-boot variation**. `page_map.py` /
`rank_pages.py` score every page of the coredumps on four signals:

| signal | meaning |
| :-- | :-- |
| `diff` (cross-boot bit-difference) | **> 0 ⇒ the modem wrote it ⇒ mapped + writable** |
| `fill_frac` (`0xf8f8f8f8` fraction) | loader fill ⇒ **not** a real page |
| `zero_frac` | all-zero pages are ambiguous (hole *or* free) — need `diff` to disambiguate |
| `codepat` (pointers into `[0xc0000000,0xc1c40000)`) | live data |

**The v4 page `0xc1440000` (phdr17, LOADED RW):**

| property | v1 `0xc51bf000` | v3 `0xc4546800` | **v4 `0xc1440000`** |
| :-- | :-- | :-- | :-- |
| segment | phdr26 BSS (`filesz=0`) | phdr26 BSS | **phdr17, LOADED RW** |
| nonzero words in dumps | 0 | (fill) | **39..122** |
| image content at page | — | `0xf8f8f8f8` fill | **all zero** |
| cross-boot `diff` | 0 (pure zero) | **0 (byte-identical 6/6)** | **0.066 (varies)** |
| fill fraction | 0 | ~1.0 | **0** |
| free tail run | (whole page) | — | `[+0x8f0, +0x1000)` zero in 8/8 |
| live verdict | **FAULT / crash-loop** | **FAULT / crash-loop** | **E1 OK** |

⇒ `0xc1440000` is a page the modem **actively writes** (so it is mapped) with a large **stable zero
tail** (so the export is collision-free). The store slot is `+0x8f0` into that page.

> **Lesson (recorded for the whole project):** *"nonzero / free-looking in a coredump" is NOT evidence
> of a mapped page.* Loader fill (`0xf8f8f8f8`) persists across boots and looks used. Only a **write
> that varies across boots** proves mapping. This supersedes the v1 control table that previously
> stood here.

## 5. The readback instrument

`scratch/diag_patch/read_diag_export.py <coredump.elf>` finds the PT_LOAD covering `0x87c408f0`
(`0xc14408f0 − 0x39800000`) and decodes the five words:

| offset | field | meaning |
| :-- | :-- | :-- |
| `+0x00` | `r0` | the ML1 timer-context pointer |
| `+0x04` | `r1` | the dispatch index |
| `+0x08` | `r31` | return address — must be **`0xc02d7bdc`** (a **cave-ran** marker; §3.4) |
| `+0x0c` | `r29` | frame pointer at entry |
| `+0x10` | counter | callback invocations since power-on |

### 5.1 Both controls pass

* **Negative** — on all six **stock-firmware** dumps (the cave does not exist there): the page is zero,
  the marker mismatches, and the tool reports **"CAVE NEVER RAN / counter = 0"**. No false positive.
* **Positive** — a synthetic injection into a copy of the fatal dump (`/tmp/fake_export.elf`) is read
  back exactly:

  ```
  +0x00 0xc3a1b2c0  +0x04 0x0000001d  +0x08 0xc02d7bdc  +0x0c 0xc1f23450  +0x10 0x0001a2b3
  marker r31 = 0xc02d7bdc == 0xc02d7bdc  -> the cave RAN (sanity OK)
  counter = 107187  -> the callback ran 107187x since power-on
  ```

## 6. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Build a firmware patch that **reveals** the crash cause, not masks it | an instrument, not a fix | detour records the callback's args + a counter; **branches on nothing** | **MET** |
| Preserve the replaced instruction's semantics | identical `r0 = 0` return | cave ends `r0 = #0x0 ; jumpr r31` — same as the stub | **MET** |
| Be control-flow neutral (cannot relay the fatal) | no branch/skip | no branch; the detour only writes then returns | **MET** |
| Be register-neutral | no live register corrupted | clobbers `r2` (caller-saved; the replaced instruction is a `call`) | **MET** |
| Change only the intended bytes | a handful | b16 **3 B**, b05 **32 B**, b01 64 B (two hashes), mdt rebuilt | **MET** |
| Pass the project's segment verifier | PASS | **19 MATCH / 0 MISMATCH / Overall PASS ✓** | **MET** |
| Find a **mapped** save area | a page the modem **writes** | v1/v3 (unmapped BSS holes) **FAULTED**; v4 `0xc14408f0` in **phdr17 (LOADED RW)**, written + cross-boot-varying, free tail | **MET (v4)** |
| Read the export back | a tool + controls | `read_diag_export.py`; negative **and** positive controls pass | **MET** |
| **E1: the patched image boots without `MPSS authentication failed`** | boots, LTE attaches | **v4 flashed on Android; `subsystem_powerup` → `Brought out of reset` → services up; no auth failure, no `:Excep`, no crash-loop** | **MET** |
| Deploy + capture a fatal + read the export | a live `ctx`/`idx`/`counter` | **v4 deployed; natural fatal captured; `ctx=0xc2150f38`, `r1=0xc02d7bd0`, counter=10836, marker OK** | **MET** |
| Reconstruct the timer object from `ctx` in the dump | the fatal's state | `ctx` = a 0x40-byte-record array; `+0x38` = the dispatch state byte = 20; the case path reaches the assert at `0xc02d7dd8` | **PARTIAL — dispatch decoded, assertion condition open** |

## 6.1 ★ E1 CONFIRMED on device (v4)

The v4 image was flashed on the Android dongle (`/firmware`, `mount -o remount,rw`, `cp`, `sync`,
md5-verified, remount `ro`), then the modem was restarted (`echo restart > /sys/kernel/debug/msm_subsys/modem`).
`dmesg`:

```
subsystem_powerup
modem: Brought out of reset
Subsystem error monitoring/handling services are up
```

**No `MPSS authentication failed`. No `:Excep`. No `Fatal error`. `crash_count` unchanged.**
Because v1 and v3 both faulted **within ~4 s of every powerup** (a crash-loop), the **absence** of a
crash-loop is itself the proof that **the cave's store to `0xc14408f0` succeeded** — i.e. the v4 save
area is **mapped**.

> **Device-recovery note (SOP honesty).** While v3 was flashed the modem crash-looped; the loop persisted
> for a few minutes even after restoring stock, because the in-flight restart chain was still draining.
> It stopped once the chain drained (`/proc/uptime` stable). The stock set was restored md5-verified
> (b16 `57fef19d…`, b05 `332f000b…`, b01 `b85b86ce…`, mdt `1a6f9507…`). No partition was damaged.

## 6.2 ★★★ THE EXPORT — the natural ~902.4 s fatal, captured and read

The v4 image ran to the natural fatal. Device dmesg:

```
[ 2993.328627] Fatal error on the modem.
[ 2993.328643] modem subsystem failure reason: lte_ml1_common_timer.c:390:.
[ 3004.762215] pil-q6v5-mss ...: modem: Brought out of reset
```
Modem up at `2090.951498` ⇒ **modem runtime at the fatal = 902.377 s** — the instrument **did not
perturb** the crash (F-57c: the timing is unchanged). The coredump (`diag_v4.elf`, md5
`0ab22ccb25409072520d2eecab35592b`, 85 443 284 B) was captured **device-local** (Doc 217's rule).

`read_diag_export.py diag_v4.elf 0xc14408f0`:

| offset | value | field |
| :-- | :-- | :-- |
| `+0x00` | `0xc2150f38` | `r0` — **the ML1 timer-context pointer** |
| `+0x04` | `0xc02d7bd0` | `r1` — arg1 = the **registered callback's own address** |
| `+0x08` | `0xc02d7bdc` | `r31` — the marker ⇒ **THE CAVE RAN** (F-57b **SCORED PASS**) |
| `+0x0c` | `0x8ad52180` | `r29` — the frame pointer (the `tmr_slave3` stack/TCB region) |
| `+0x10` | `0x00002a54` | counter = **10 836** callback invocations this power cycle |

**Rate:** 10 836 / 902.377 s = **12.008 /s** — the callback is entered ~12×/s.

## 6.3 ★★ The decoded dispatch — how `ctx` selects the case

The callback body is a jump-table dispatch:

```
c02d7bdc: { r1 = memub(r16+#0x38) }        ; r1 = the STATE BYTE read from ctx+0x38
c02d7be4: { r2 = memw(gp+#0xba64) }        ; r2 = the jump-table base (ELF VA 0xc1a861d4)
c02d7be8: { r2 = memw(r2+r1<<#0x2) }       ; r2 = table[r1]
c02d7bec: { jumpr r2 }                     ; dispatch
```

> **★ The `<unknown>` packet decodes — do not trust llvm-objdump here.** The 8-byte packet
> `01 47 30 91 04 de 13 25` is `{ r1 = memub(r16+#0x38) }` … **but only when the parse field is set
> correctly.** Brute-forcing the parse bits, `p1=3` (bits[15:14]=11) yields
> **`r1 = memub(r16+#56)` = `memub(r16+#0x38)`**; every other parse setting reports "invalid encoding".
> (The same trap family as `feedback_source_reading_traps.md` §14.)

So the **dispatch index = the unsigned byte at `ctx+0x38`**. For this dump `ctx+0x38 = 0x14 = 20`.

The jump table (31 entries at `0xc1a861d4`) maps indices to case entries:

| index | entry | index | entry |
| :-- | :-- | :-- | :-- |
| 0 | `0xc02d7bf0` | 18 | `0xc02d7d20` |
| 1 | `0xc02d7c24` | 19 | `0xc02d7df0` |
| 2 | `0xc02d7c60` | **20..28** | **`0xc02d7d54`** (shared) |
| 3 | `0xc02d7cc4` | **29** | **`0xc02d7d8c`** |
| 4,5 | `0xc02d7bf0` | 30 | `0xc02d7d90` |
| 6..11 | `0xc02d7cec` | | |
| 12 | `0xc02d7c9c` | | |
| 13..15 | `0xc02d7c24` | | |
| 16 | `0xc02d7c58` | | |
| 17 | `0xc02d7bf0` | | |

**Indices 20–28 share the block at `0xc02d7d54`, which FALLS THROUGH into `0xc02d7d8c` (the "index-29
entry") and reaches the fatal assert at `0xc02d7dd8`.** So index 20 and index 29 are the **same code
path** — the 20-vs-29 apparent conflict dissolves: `ctx+0x38 = 20` enters at `0xc02d7d54` and flows to
the same assert. (This also explains why `0xc02d7d8c` appears in the saved frame.)

The assert itself:

```
c02d7dcc: call 0xc028b140 ; r0 = add(r29,#0x18)     ; r0 = f(r29+0x18)
c02d7dd4: p0 = cmp.eq(r0,#0x0); if (p0.new) jump:t 0xc02d7df8   ; if result==0 -> exit
c02d7dd8: call 0xc0879150                            ; <-- THE FATAL ASSERT
```

i.e. **the fatal fires when `f(r29+0x18) != 0`**, where `f = 0xc028b140`. The assert helper
`FUN_c0879150` then reports a **fixed** descriptor (`0xc35b1384` = `{line 390, "Assert 0 failed: ",
"lte_ml1_common_timer.c"}`) — so the dmesg's `lte_ml1_common_timer.c:390` is the **helper's** location,
not the caller's (confirmed again here).

### 6.3.1 The `ctx` object shape (for the next step)

`ctx = 0xc2150f38` (in BSS, coredump phdr 15) is an **array of 0x40-byte records**; consecutive records
have consecutive `+0x38` values (this dump: `0x14,0x15,…,0x1b` = 20..27):

| off | meaning (observed) |
| :-- | :-- |
| `+0x00` | `0xc361a3f0` (a shared pointer) |
| `+0x0c` | `0xc02d7bd0` (the registered callback) |
| `+0x14` | a self-pointer (each node points to itself) |
| `+0x1c` | `0xdeaddead` (debug poison) |
| `+0x30` | `0x0fedcbab/c…` (debug pattern) |
| **`+0x38`** | **the dispatch state byte** (selects the jump-table case) |
| `+0x3c` | 0 |

**RESOLVED — the emitter's return semantics (2026-09-30, offline).** `f = 0xc028b140` is a **thin stub**:
`r1 = #0x10 ; jump 0xc0b62f10`, so `f(rec)` = **`FUN_c0b62f10(rec, level = 0x10)`**. The emitter computes
its result in `r18` and the epilogue moves it to the return register
(`0xc0b6305c: r0 = r18 ; jump 0xc0030084`), so the caller's `cmp.eq(r0,#0x0)` at `0xc02d7dd4` tests
**exactly** the emitter's return value. Reading the two `r18` writes (`0xc0b62f64/68/6c` and
`0xc0b62ff8/ffc`) against the decompilation gives the three-valued contract:

| return | meaning |
| :-- | :-- |
| **`0`** | **DELIVERED** — the message ID resolved to ≥1 descriptor and every item formatted + sent OK. **The only value the caller accepts.** |
| **`1`** | at least one item **failed to FORMAT** (`iVar3 != 0`). ⚠ **Not a transport failure** — the sink `FUN_c0b65980` is `void` and its result is never checked; the code comes from `FUN_c0b648a0(tech,…)` (no descriptor for the tech) or the formatter `FUN_c0b657a0` (returns `0x12` on a NULL descriptor). See **ledger §58.1**. |
| **`0x6d`** | `FUN_c0b64aa0(rec[0])` returned 0 ⇒ the **message ID has no registered descriptor** — nothing to deliver. (In this branch the emitter also re-sends a suppressed duplicate with argument `0x6d`, `0xc0b63038`.) |

**★ Guard correction.** The prologue guard at `0xc0b62f3c` is
`p0 = cmp.gtu(r17,#0xf); if (!p0.new) jump:nt 0xc0b63078` ⇒ **it fatals when `level ≤ 0xf`**; valid levels
are **> 0xf** (level 0x10 = `LOG_F` passes). Ledger §52.2's "level > 0xf" is the **inverted** phrasing.

⇒ **the caller asserts that the F3 message was DELIVERED** (`result == 0`). Any non-zero return —
`1` (send failed) **or** `0x6d` (message ID unregistered) — fires the assert, so the fatal is
**not** a logic assert and **not** necessarily a transport failure: it is "the F3 emit did not deliver".

**Still open:** which of `1` / `0x6d` the captured fatal produced. The assert's return address
(`0xc02d7ddc`) occurs **0×** in `diag_v4.elf`, so the value is not directly readable from that dump.
The msgids these case blocks use (`0x042a0405` at `0xc02d7d8c`; siblings `0x04200409` at `0xc02d7cc4`,
`0x405043a` at `0xc02d7c9c`, `0x43a0404` at `0xc02d7cec`) are all **registered** IDs — they appear in the
message-ID table at dump_va `0x886581ac` and in a `{msgid, 0x001b0000}` descriptor table at `0x897f00c0`
— which makes the `0x6d` "unregistered" branch unlikely; the most probable value is **`1`** (a send
failure). Flagged as a **hypothesis**, not established. (The thunk target is verified in §6.3.2.)

### 6.3.2 ★ Verification of the thunk target `0xc0b62f10` (llvm-objdump's render confirmed, not trusted)

Because §14 warns that llvm-objdump's render can be wrong, the call→thunk→target chain was verified from
first principles:

1. **Call target — decoded by hand.** For `call` the target = `instr_addr + 2·signed22((word[23:16]<<14)|word[13:0])`.
   The call at `0xc02d7dcc` (`word = 0x5bf659ba`) → **`0xc028b140`**. The decoder is validated by the
   *known* stub call at `0xc02d7bd0` (`word = 0x5bff4ab8` → `0xc02d1140` ✓).
2. **Boundary.** `0xc028b140` starts a packet (the previous packet ends at `0xc028b13f`), so it is a real
   instruction boundary.
3. **The jump — byte-for-byte round-trip.** The 8 thunk bytes are `f7 75 08 00 20 d0 01 16`. Assembling
   `{ r1 = #0x10 ; jump .Ltgt }` with `.Ltgt` at packet-offset `0x8d7dd0` produces **exactly those
   bytes** (`llvm-mc` → `llvm-objdump` round-trip, `BYTE-MATCH: True`). The jump target is
   **packet-relative**: `0xc028b140 + 0x8d7dd0 = 0xc0b62f10` ✓.
4. **Formula cross-checked on all 7 thunks** (`0xc028b140…0xc028b170`): `target = thunk_VA + (immext | (low_byte>>1))`
   reproduces llvm's reported target for **every** entry (e.g. `0xc028b150` → `0xc064d900`).
5. **Destination is a function entry.** `0xc0b62f10` begins a prologue and is preceded by a
   `{ r0 = and(r5,#255) ; jumpr r31 }` return + `nop` padding.

⇒ **`0xc0b62f10` is VERIFIED as the thunk target.** (One sibling thunk, `0xc028b148`, targets
`0xd099aefc` — **outside the loaded modem VA range** `[…,0xc5200000]`; that is a genuine decode, so that
entry is a deliberate trap/error stub, not a decode error.)

### 6.4 ★ Is the failed emit the trigger, or a symptom? — the STATE is the trigger (ledger §58)

Follow-on verification (offline). Three results:

1. **The emitter's non-zero code is a FORMAT/lookup failure, not a transport failure.** The real sink
   `FUN_c0b65980` is **`void`** — it appends to a 1024-entry ring (`index & 0x3ff`) and its result is never
   checked. `iVar10 = 1` comes from `FUN_c0b648a0(tech,…)` (no descriptor for the tech) or the formatter
   `FUN_c0b657a0` (returns `0x12` on a NULL descriptor).
2. **`ctx+0x38` is a real state id.** The registration routine `FUN_c02d7b80(ctx)` zeroes `ctx+0x3a/+0x3c`
   and registers the dispatcher; its callers **write the state first** — `DAT_c3984bd8 = 0x13` (19) in the
   acq init, `*(*piVar2+0x40) = 0x1e` (30), `*(*piVar2+0x350) = 0x1d` (29).
3. **States 20–28 are the switch's `default:`.** Nine consecutive states map to the single block
   `0xc02d7d54`, whose shape differs from every other case: no message is built; it is a conditional
   debug log (`debug_flag || (mask1&bit) && (mask2&bit)`) attached to one of the 10 fatal sites
   (`0xc02d7d80`).

The fatal context's state is **20** ⇒ the dispatcher took the default case. The crash context's recorded
handler `0xc02d7d8c` is the block that `0xc02d7d54` **falls through into** after its assert returns.

⇒ **The upstream trigger is the ML1 state machine entering an invalid state (20); the failed emit is
downstream.** The F3 transport is not implicated. **Still open:** *what writes 20*, and which assert in
the relay is "the" fatal. Full detail, including the four explicit non-establishments, in **ledger §58**.

## 7. What is VERIFIED and what is NOT

**VERIFIED (offline + on-device):**
* The entry packet and the `return 0` stub, by disassembly of the stock ELF (and the packet spans
  `0xc02d7bd0/4/8` ⇒ the return marker is `0xc02d7bdc`).
* The `call`/`jump` duplex encoding (empirically derived, §8 trap 1), and that the patched entry
  decodes to `call 0xc003054c`.
* The cave: 44 B in a dead `nop` run with **no inbound branch**; disassembles to the 11 intended
  instructions.
* The patched image: **only** the intended bytes differ; the project verifier reports **PASS**.
* **The v4 save area `0xc14408f0` is MAPPED** — proven live: the image boots with no auth failure and
  **no crash-loop**, whereas v1/v3 faulted within ~4 s of every powerup.
* The readback tool, by **both** a negative (6 stock dumps) and a positive (synthetic) control.
* **The instrument RAN on the natural fatal** and exported `ctx`/`r1`/`r29`/counter (§6.2); the crash
  timing is unchanged (§6.2); the dispatch is decoded (§6.3).

**NOT verified / open:**
* **The emitter's return semantics are RESOLVED** (§6.3.1): `0` = delivered, `1` = send failed,
  `0x6d` = message ID unregistered; the caller accepts only `0`. **Still open:** which of `1`/`0x6d`
  the captured fatal produced (the assert's return address `0xc02d7ddc` occurs 0× in the dump).
* **The r2 clobber** is argued from the ABI, not measured on hardware.
* Whether the **detour itself perturbs the crash** — measured once: it did **not** (902.377 s vs the
  stock ~902.7 s clock).
* **The r2 clobber** is argued from the ABI, not measured on hardware.
* Whether the **detour itself perturbs the crash** (it should not, by construction) is a live question —
  the natural fatal's timing under v4 is the first measurement of it.

## 8. Traps hit (and recorded)

1. **The `call`/`jump` encoding is duplex.** The 22-bit two's-complement offset/2 is split across
   bits[23:16] and bits[13:0]; **bits[15:14] are the packet-parse field** and must be preserved, as
   must bits[31:24]. Deriving it by assembling forward/backward labels is the only safe route.
2. **`llvm-objdump`'s register render is silently wrong** — it shows `r16 = r0` as `0x70604010` while the
   true bytes are `10 40 60 70`. Assemble whole packets and round-trip; never trust the render.
3. **The first "cave" found (`0xc016e9f8`) was a LIVE delay loop** — `0xc016ea20: if (!cmp.eq(...))
   jump:t 0xc016e9f8` branches back into it. Reject any run that has an inbound branch; require a run
   preceded by an unconditional terminal.
4. **A bytearray slice with a hard-coded `+32` GREW the file** when the cave became 44 B, shifting every
   later byte (42 529 spurious "differences"). Slice with `len(CAVE)` and assert the file size.
5. **`verify_elf.py` initially carried a stale 32-byte cave** (v1) — its disassembly silently showed the
   wrong (truncated) export. The ELF-verify copy and the builder **must share the cave bytes**; they now
   do, and `verify_elf.py` asserts the pre-patch bytes are the 11-nop run.
6. **`filesz = 0` ⇒ not in any `modem.bNN`.** The save area is only in a coredump. "Absent from the
   image" ≠ "absent from the modem" (the same trap as `feedback_source_reading_traps.md` §17).
7. **★★★ "Zero/nonzero/free-looking in a coredump" does NOT prove a page is mapped.** A coredump is
   physical RAM, which holds **loader fill** (`0xf8f8f8f8`) and zero pages that persist across boots
   but are **not mapped**. v1 (`0xc51bf000`, zero in 6/6) and v3 (`0xc4546800`, byte-identical 6/6 =
   fill) both **faulted**. The sound criterion is **a page the modem writes with data that VARIES
   across boots** (a write ⇒ mapped+writable) **plus a large run zero in every dump**. This cost two
   live crash-loops to learn; the criterion now lives in `read_diag_export.py`'s docstring and §3.3.
8. **Off-by-one-packet on the return address.** The entry packet spans `0xc02d7bd0/4/8` (three
   instructions), so the `call`'s link value in `r31` is `0xc02d7bdc`, **not** `0xc02d7bd4`. An early
   revision of this doc and the readback marker used `0xc02d7bd4`; disassembling the stock entry settles
   it. (A `call` links to the packet **after** its own packet, not to the next instruction word.)

## 9. SOP-compliance statement

* **Ground-truth first.** Every byte patched is anchored to a disassembly of the stock ELF, and every
  precondition is **asserted in code** (`b16[off:off+4] == b84aff5b`, `b05[off:off+44] == 11×nop`).
  No VA is patched from a name or a guess.
* **The instrument is surgical and reversible.** Two segments change (b16 3 B, b05 32 B); the rest of
  the image is byte-identical. On the OpenWrt arm the firmware lives on the overlay, so deploy/revert is
  `cp`; the stock set is preserved by `modem-guard` (`/overlay/fwbackup/hmu05_stock/`). On the Android
  arm the stock set was backed up to `/data/local/tmp/modem_backup_239/stock_files/` (md5-verified)
  before flashing, and restored during the v3 incident.
* **Re-signed correctly.** Segments 16 and 5 are re-hashed into `modem.b01` and `modem.mdt` is rebuilt —
  the same scheme as the byte-verified genuine patcher — and the project's verifier reports **PASS**.
* **Control-flow neutral by construction.** The patch branches on nothing and reproduces the replaced
  stub exactly; it therefore cannot relay the fatal (the failure mode Doc 231 documented).
* **Controls run.** The save area was re-selected on a **sound** criterion after two failures: the page
  is one the modem **writes** (cross-boot-varying content) with a **zero free tail** — proven live by
  the absence of the v1/v3 crash-loop. The readback tool is proven both ways (negative on stock,
  positive on a synthetic injection).
* **Negative/unknown results recorded honestly.** v1/v3 were **unmapped** and **faulted** (recorded, with
  the recovery); the export **is read** (§6.2) but the **assertion condition is not yet identified**
  (§6.3.1). E1 **is** confirmed.
* **Reversible flashing, with a warning stated first.** Flashing the Android `/firmware` partition is a
  write to a persistent partition; the stock files were backed up and md5-verified first, and the
  remount was returned to `ro`. The crash-loop incident and its recovery are recorded above.
* **Ledger + memory updated in the same session** (item 57).

## 10. The live experiment — the recipe (as run on Android, and for OpenWrt)

### 10.1 Android arm (the arm actually used)

```sh
# 0. root (the build is userdebug; adb root alone is a no-op)
adb shell 'setprop service.adb.root 1; busybox killall adbd'
# 1. back up stock (md5-verified) then flash v4
adb shell 'mount -o remount,rw /firmware'                       # vfat, ro at boot
adb push scratch/diag_patch/image_patched/modem.* /firmware/image/
adb shell 'sync; mount -o remount,ro /firmware'
# 2. confirm E1: restart the modem, then require no auth failure / no :Excep
adb shell 'echo restart > /sys/kernel/debug/msm_subsys/modem'
adb shell 'dmesg | grep -Ei "authentication|:Excep|Fatal error" || echo E1-OK'
# 3. arm the ramdump reader BEFORE the natural ~902.7 s fatal, then let it fire:
adb shell 'cat /dev/ramdump_modem > /data/local/tmp/diag_v4.elf'   # one crash per capture
# 4. pull + read the export
adb pull /data/local/tmp/diag_v4.elf scratch/android_dump/
python3 scratch/diag_patch/read_diag_export.py scratch/android_dump/diag_v4.elf 0xc14408f0
# 5. walk ctx (+0x94 = 0xc02d7bd0, +0x9C = the fatal case entry) in the dump
```

### 10.2 OpenWrt arm (the safe arm, when a device is free)

```sh
cp scratch/diag_patch/image_patched/modem.*  /lib/firmware/    # then sync + reboot
ssh root@192.168.8.1 "dmesg | grep -i 'authentication failed' || echo E1-OK"
# on-demand coredump — Doc 217 §6.2a — rmmod qcom_bam_dmux FIRST
python3 scratch/diag_patch/read_diag_export.py <captured_coredump.elf> 0xc14408f0
```

## 11. Artifacts

`Docs/Modem Stability/evidence/239_diag_patch/`
* `build_diag_patch.py` — the builder (copy → patch b16 → patch b05 → re-hash b01 → rebuild mdt → verify).
* `verify_elf.py` — applies the same two patches to a copy of the stock ELF for disassembly.
* `read_diag_export.py` — the AP-side coredump readback tool.
* `find_cave.py`, `find_cave2.py` — the nop-run scanners (found `0xc003054c`; rejected the live loop).
* `find_savearea.py`, `check_page.py`, `region_density.py` — the save-area finder and its controls.
* `build_output.txt`, `verify_output.txt` — the captured build + verifier runs.
* `readback_test_output.txt` — the negative (6 stock dumps) + positive (synthetic) readback controls.
* `patched_image.md5` — the four changed files' md5s.

The built image is at `scratch/diag_patch/image_patched/` (**v4**, the image on the device):

| file | md5 (v4 patched) | md5 (stock) |
| :-- | :-- | :-- |
| `modem.b16` | `2fddadca3274077fd466c09131a2bf73` | `57fef19de7178fb732c8b2edc40bc9dc` |
| `modem.b05` | `11c492fdb13c62a94dbdfdf105be418c` | `332f000baa2522e8bc01f3240115d316` |
| `modem.b01` | `bd89444bb9c8f5c0625883dfb57db8f5` | `b85b86cec95bc250d753fe3eca016dc9` |
| `modem.mdt` | `2792fd2e5a5032ba36450e801cbe0d01` | `1a6f9507e03d4ddbbf1977af81ecdbd7` |

(The stale md5s of the earlier save-area revisions — b05 `ea900354…` / b01 `624be58c…` / mdt `445052d2…`
— are superseded; `scratch/diag_patch/image_patched_v4.md5` is the current record.)

### 11.1 The save-area analysis tools (added with v4)

* `page_map.py` — per-page grid: `diff` (cross-boot bit-difference), `fill_frac`, `zero_frac`,
  `codepat`, longest stable zero run.
* `seg_layout.py` — the 27 phdrs; maps a target VA to its segment.
* `region_profile.py`, `page_detail.py`, `vadump.py` — chunk/block profiles and VA hexdumps across dumps.
* `rank_pages.py` — the final ranking, using an explicit coverage mask (the working variant).
* `img_vs_dump.py` — image-vs-dump comparison (image=0 but dump nonzero ⇒ a modem write).
* `pick_savearea.py`, `find_loaded_slot.py`, `final_pick.py`, `verify_slot.py` — earlier attempts
  (superseded; `find_loaded_slot.py`'s `0xFF` byte-sentinel was a bug — real data contains `0xFF`).

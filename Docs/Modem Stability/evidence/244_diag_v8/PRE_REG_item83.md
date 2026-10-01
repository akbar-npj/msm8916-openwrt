# PRE-REGISTRATION — item 83: the PAIRED ARM+CALLBACK ring (v8), amended for v9

Written **before** deploying v8 and arming the reader. Firmware to deploy:
`scratch/diag_patch_v8/image_patched/` (built and hash-verified offline; see
`build_output.txt`). Ground truth for the build: stock HMU05
`GitIgnore/compare/modem_hmu05_extracted/image/`.

> **AMENDMENT (v9) — written after the v8 validation capture, before the v9 event
> run.** See §A at the end. The v8 validation capture worked and **falsified a
> premise of the design**: `FUN_c02d7bd0` is a *generic* state dispatcher whose
> 30 Hz stream carries states 0–15, so the 128-slot ring held only ~4 s and no ARM
> entry survived. v9 adds a **state filter** (record the CALLBACK only when the
> dispatch index is 20..28) and two wrap-proof counters. The question, the ARM
> detour and the save area are unchanged; P-83c/P-83d are restated for v9 in §A.

## The question

`FUN_c02fda90` is the primitive that **SENDS** a 0x28-byte ML1 message (header
`0x408020d` for selector `r2==1`) and **ARMS** the 50 ms "state-20" watchdog
(ledger §45–52, item 60). `FUN_c02d7bd0` is the **shared ML1 state dispatcher**
registered at `ctx+0x0c` for all nine ctx objects (states 20…28). Item 82 showed
ctx0's `+0x20` expiry is **SET** at exactly the three
`lte_ml1_common_timer.c:390` fatals and clear at the nine other dumps.

**What is NOT known:** whether the ARM is normally **ANSWERED**. If the reply to
`0x408020d` arrives, it must cancel the 50 ms timer (⇒ no state-20 callback). If
it does not, the timer expires and the state-20 handler runs.

## New fact established while designing this (item 83 §1)

The 8 bytes at `0xc02d7bdc` that `llvm-objdump` renders as `<unknown>` decode to

```
r1 = memub(r16+#0x38)        ; r16 == ctx, so r1 == the STATE BYTE
r2 = memw(gp+#0xba64)
r2 = memw(r2 + r1<<2)
jumpr r2
```

so the dispatcher's jump-table **index is the state byte at `ctx+0x38`**
(20…28). Method: the same word `0x91304701` appears 9× in `modem.b16`; at
`0xc045f228` and `0xc0c8aea4` it is decoded (the packet's next word is a valid
ender), giving `r1 = memub(r16+#0x38)` directly. Consequence: a CALLBACK entry's
`ctx+0x38` identifies **which ctx fired**, and ctx0 ≡ state 20.

## Design (frozen before the run)

One **shared** ring, one monotonic counter, **two** sources — so the gap between
an ARM's `seq` and the next CALLBACK's `seq` is a coarse clock:

| | detour | recorded |
|---|---|---|
| **ARM** | both call sites of `FUN_c02fda90` (`0xc0326874` A, `0xc033c0f4` B) | caller `r31`, selector `r2`, index `r0` |
| **CALLBACK** | the entry `0xc02d7bd0` (its `call 0xc02d1140` → cave; the cave mimics the return-0 stub and resumes at `0xc02d7bdc`) | `ctx` (`r0`), `state` (`ctx+0x38`), `ctx+0x20` expiry low word |

Save area `0xc1455000` (the **proven** v7 page), header 64 B, ring 128 × 16 B,
slot = `(count-1) & 0x7f`, CALLBACK entries tagged by `seq` bit 31.
Full layout in `build_diag_patch_v8.py`'s docstring.

Offline verification already done: `ufi001b_hash_tool.py verify` → **19 MATCH /
0 MISMATCH / PASS**; both caves and all three patched call sites disassembled and
confirmed (`build_output.txt`, `/tmp/v8_b05.elf`, `/tmp/v8_b16.elf`).

## Predictions (scored only after a capture)

* **P-83a (capture).** A complete 85 443 284 B coredump is obtained at the event.
* **P-83b (instrument ran).** Ring `magic == 0xc1455000` and `count > 0`, and the
  ring holds **both** kinds.
* **P-83c (rates match the prior exports).** In the last 128 entries the CALLBACK
  share is dominant and the ARM count is small: **CB ≳ 100, ARM ≤ 15**, consistent
  with the v4 export (CALLBACK 12.0 Hz) and v7 (ARM 0.75 Hz idle / 0.2 Hz traffic).
  A CALLBACK rate ≪ 12 Hz ⇒ the detour perturbed the dispatcher ⇒ **VOID**.
* **P-83d (★ THE DISCRIMINATOR — mutually exclusive).**
  * **(D1) state-20 CALLBACKs are COMMON and track the ARMs** (a state-20 CB
    within ≤ 5 entries of most ARMs) ⇒ the reply does **not** normally cancel the
    timer ⇒ the state-20 expiry is a **routine** part of the loop ⇒ the fatal is
    downstream of it (the handler), not the expiry itself.
  * **(D2) state-20 CALLBACKs are RARE** (≤ 2 in the window; ARMs followed by
    non-20 callbacks) ⇒ the reply **does** normally cancel the timer ⇒ the fatal
    is the first (or an anomalous) state-20 expiry.
* **P-83e (the fatal's own expiry).** The **last ARM** in the ring is followed by
  ≥ 1 **state-20** CALLBACK. (Item 82 makes this the expected case; if it fails,
  the last ARM is not the fatal's arm and the ARM/CALLBACK link is broken.)

## Falsifier / void conditions

* **V-83a.** No capture / incomplete dump ⇒ VOID.
* **V-83b.** `magic != 0xc1455000` or `count == 0` ⇒ the cave never ran ⇒ the
  image did not deploy or the cave is broken ⇒ revert to v7 and re-check.
* **V-83c.** CALLBACK rate ≪ 12 Hz ⇒ the CB detour changed dispatcher behaviour
  ⇒ VOID (do not interpret D1/D2).
* **V-83d.** If the event is a **WEDGE** rather than a fatal (item 78's 2×2 says
  the branch is an `image × traffic` interaction), the ring is still read at the
  wedge (post-`restart`) and P-83a is relaxed to "a dump is obtained"; D1/D2 are
  scored on whatever the ring shows.

## Scope / honesty

* **n = 1**, one device/SIM, one regime (idle unless stated). Exploratory.
* **No in-cave clock.** The seq gap is converted to time **only** by assuming the
  CALLBACK rate measured on a *different* boot (v4, 12.0 Hz). All timing claims
  are therefore coarse and are stated as *entry counts*, not milliseconds.
* The ring is **128 entries ≈ 10 s at 12.75 Hz**. This is deliberately short: it
  is a *local* view of the fatal's last seconds, not a 900 s trace.
* The CB detour replaces a call to a **return-0 stub** (`FUN_c02d1140` =
  `{ r0 = #0x0; jumpr r31 }`); the cave reproduces `r0 = 0` and returns to
  `0xc02d7bdc`. This is the first boot with this detour — if the modem does not
  come up, V-83b applies.
* The ARM cave records the **index** `r0`, not the ctx: the armer's `r0` is an
  index into a table at `0xc312c764` (`ctx = table[r0]`; index 0 → `0xc2150c30`,
  an *instance* object, **not** the timer ctx). So ARM↔CALLBACK cannot be paired
  by a ctx pointer; the pairing is by **sequence only**.

---

# §A — AMENDMENT for v9 (written before the v9 event run)

## A.1 The v8 validation capture (the premise it falsified)

v8 deployed cleanly (no crash-loop, fatal 0→0, `DSP is ready`), and a
reader-armed `restart` produced a complete 85 443 284 B dump
(`scratch/android_dump/v8_cap1.elf`, md5 `84f6ea704d620a0c064eb7a1f44c8ba9`).
`read_diag_ring_v8.py` read:

* **`magic = 0xc1455000` ✓, `count = 2804`** — the instrument ran. **P-83a/83b PASS.**
* the ring held **0 ARM / 128 CALLBACK**;
* the callbacks' states were **0:36, 1:12, 3:55, 12:3, 13:6, 14:12, 15:4** —
  **no state in 20..28 at all**;
* the callback `ctx` values were `0xc211cc28`, `0xc210edd0`, `0xc2176878`,
  `0xc21768f8`, … — **not** the nine ML1 timer ctxs.

⇒ **`FUN_c02d7bd0` is a GENERIC state dispatcher**, not an ML1-timer-only
callback: the nine timer ctxs are merely nine of its many callers, and they did
**not** fire in this 93 s window. Consequently the CALLBACK rate is **~30 Hz**
(2804/93.4 s), not the 12 Hz the v4 entry-export implied, so the 128-slot ring
held only **~4.3 s** and no ARM entry survived. **P-83c as written FAILS**
(ARM ≤ 15 held, but for the wrong reason — the ring was too short).

The 0-ARM result is therefore **not** evidence about the ARM cave: 128 slots at
30 Hz = 4.3 s, and ~3 arms were expected. **The ARM cave is unconfirmed by v8.**

## A.2 v9 — the design (frozen before the v9 run)

`scratch/diag_patch_v9/image_patched/`, built by `build_diag_patch_v9.py`;
`ufi001b_hash_tool.py verify` → **19 MATCH / 0 MISMATCH / PASS**. Same patches,
same save area, same entry layout as v8, plus:

* the **CALLBACK cave records only when the dispatch index is 20..28**
  (`r1 = memub(ctx+0x38); r1 = add(r1,#-0x14); if (r1 > 8) skip`), storing
  `state-20` (0..8) in the `b` field;
* two wrap-proof counters: **`+0x08 arm_total`** (every ARM) and
  **`+0x0c cb_total`** (every CALLBACK, filtered or not).

The cave is 100 B (ARM 76 B), both inside the b05 nop run `0xc003054c..0xc0030600`.
⚠ **Build trap found and fixed in-session:** `llvm-mc --show-encoding` renders an
unresolved PC-relative fixup as `[A,0xc0'A',A,0x5c'A']`, and a hex-scrape silently
**drops the whole instruction** — v9's first build lost its `if (p0) jump`, making
the filter a no-op (the cave assembled to 96 B instead of 100 B). Fixed by
assembling through `-filetype=obj` + `llvm-objcopy -O binary`; the builder now
**asserts the cave sizes** so a dropped instruction cannot recur.

## A.3 v9 predictions

* **P-83f (instrument ran).** `magic == 0xc1455000`, `count > 0`, `arm_total > 0`.
  **This is the primary test of the ARM cave** that v8 could not deliver.
* **P-83g (filter works).** `cb_total ≫` the number of CB entries in the ring, and
  every recorded CB has state ∈ 20..28.
* **P-83h (★ THE DISCRIMINATOR, unchanged from P-83d).**
  * **(D1)** state-20 CALLBACKs are common and track the ARMs (a recorded CB
    within ≤ 5 entries of most ARMs) ⇒ the reply does **not** normally cancel the
    timer ⇒ the state-20 expiry is routine.
  * **(D2)** state-20 CALLBACKs are **rare/absent** in the ring ⇒ the reply
    **does** normally cancel the timer ⇒ the fatal is an anomalous expiry.
  * v8's window already leans **D2** (0 state-20..28 callbacks in the last 4.3 s
    of a 93 s idle boot), but that window is far too short to score.
* **P-83i (the fatal's own expiry, unchanged from P-83e).** The **last ARM** in
  the ring is followed by ≥ 1 **state-20** CALLBACK.

## A.4 Void conditions (v9)

* **V-83e.** `arm_total == 0` after a ≥ 300 s boot ⇒ the ARM cave did not run ⇒
  the v9 capture is VOID for the ARM/CALLBACK link (report it as such).
* **V-83f.** `cb_total` far below ~30 Hz × window ⇒ the CB detour perturbed the
  dispatcher ⇒ VOID.
* Everything else (V-83a..d) carries over.

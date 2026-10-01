# 242 — Diagnostic firmware patch v7: a RING-BUFFER HISTORY of `FUN_c02fda90`

Status: **built, verified offline, DEPLOYED, VALIDATED on-device, and the ~900 s EVENT
WINDOW IS CAPTURED** — the fatal fired at `boot+902.290 s` and the ring reads
**`count = 674`** with the **last 128 arms uniformly `A + r2==1`** (P-79d PASS).
Ledger items 79 (build/validate), 80 (the event), 81 (the regime control) and 82 (the
**offline state-20 test** — §5c: the fatal is **coupled** to the ctx0 watchdog, 3/3 vs 0/9,
p≈0.005, but "pending vs just-fired" is **not** separable from a post-assert dump; **§80.4's
"still pending" is WITHDRAWN**). Evidence: `evidence/242_diag_v7/` + `evidence/243_ctx_expiry_offline/`.

---

## 1. Goal — and why a RING, not another snapshot

`FUN_c02fda90` is the primitive that **sends an ML1 cell-measurement request and ARMS
the 50 ms "state 20" watchdog** — the watchdog whose expiry is fatal by construction
(Doc 239 / ledger item 60). §60.9a named instrumenting it as the decisive next
experiment, to obtain *"the full call trace and its period."*

The v4 (Doc 239), v5 (Doc 240) and v6 patches already instrumented it — but as a
**single slot** that keeps only the **last** call. Ledger §63.4 / §57.4 states the
**REDUNDANCY RULE**: *a coredump already contains every byte of modem RAM at the
fatal*, so a firmware instrument is justified **only** by

1. a **transient** the dump cannot attribute — e.g. a caller's `r31` at a call
   boundary (**this is exactly, and only, what v4/v5/v6 delivered — EXHAUSTED**);
2. **HISTORY** (a ring/trace over time, which a snapshot cannot show); or
3. an actual **fix**.

v6 also showed *why* the snapshot was not enough: its cave counter read
`0xdeadc102` ⇒ `FUN_c02fda90` ran only **4×** in the whole 900.6 s **traffic** window
(§63.5). The single slot watched a dormant path and could not show the **sequence**.
⚠ **Superseded in part:** the v7 event capture (§5a) measures **674** calls in an *idle*
902.29 s window — so "dormant" describes the *traffic* regime only, and the 168× gap is
**OPEN** (ledger §80.5).

**v7 is a (2)-class HISTORY instrument**: a 128-entry ring that appends
`{ seq, caller r31, r2 selector, r0 instance }` on **every** call.

## 2. Design (frozen before the run; `PRE_REG_item79.md`)

```
modem.b16 @ 0xc0326874 : `call 0xc02fda90` -> `call 0xc0030560`   (site A)
modem.b16 @ 0xc033c0f4 : `call 0xc02fda90` -> `call 0xc0030560`   (site B)
modem.b05 @ 0xc0030560 : 80-byte ring cave (dead nop run 0xc003054c..0xc0030600)
```

* **The two call sites are the same as v5/v6** — A `0xc0326874` inside `FUN_c032685c`
  (the ML1 measurement/mobility evaluator), B `0xc033c0f4` inside `0xc033c0a4`
  (`LTE_ML1_SM_ACQ_STM` CELL_MEAS). At a **call site** the cave is entered by
  `call <cave>`, so `r31` **is** the caller's return address (`0xc032687c` = A,
  `0xc033c0f8` = B) — the direct, unambiguous discriminator.
* The cave **tail-jumps** (`jumpr`, `r31` untouched) into `FUN_c02fda90`, so the
  target sees *exactly* the register state a direct call would have produced ⇒ the
  instrumentation is **semantically transparent**.
* **Scratch = `r5` (base), `r6` (entry ptr / target), `r7` (counter / magic)** — all
  **caller-saved**, none of `gp(r28)`, `fp(r29)`, `sp(r30)`, `lr(r31)`, and none of the
  callee-saved set `r16..r27`. `r0..r4` (the target's arguments) are **never written**.
  ⚠ At site A the call packet is `{ call … ; r4 = r5 }` — the parallel `r4 = r5`
  executes **before** the call transfers, so it reads the **pre-cave** `r5` (verified
  from the disassembly).

**Ring layout** (save VA `0xc1455000`):

| offset | field |
| :-- | :-- |
| `+0x00` | `u32 magic = 0xc0030560` (the cave's own VA — self-identifies v7) |
| `+0x04` | `u32 count` (total calls; the ring slot for call *N* is `N & 127`) |
| `+0x08` | `u32 idx = (count-1) & 127` |
| `+0x0c` | `u32 pad` |
| `+0x10 + i*16` | `{ u32 seq ; u32 caller_r31 ; u32 r2 ; u32 r0 }` |

⚠ **Slot 0 is the 128th slot, not the first**: call *N* lands in slot `N & 127`, so for
`count < 128` slot 0 is *unwritten* (`seq == 0`). The readback filters `seq == 0` and
sorts by `seq` — which is also wrap-proof. (Caught during validation: the first
readback printed a spurious `seq 0` row and an off-by-one histogram.)

## 3. The save area — the §57.5 SOUND criterion, applied to a multi-KB region

The v1/v3 crash-loops (ledger §57.5) established that **a coredump is physical RAM**:
it holds loader fill and zero pages that persist across boots but are **NOT mapped**,
so *"zero / free-looking" is not proof*. The sound proof is **(a)** the page is in a
**writable, LOADED (`filesz>0`)** segment, **(b)** the page holds words that **vary
across dumps** (a live writer ⇒ mapped), and **(c)** the target run is
**byte-identical in every dump** (no live writer touches it).

`find_ring_area.py` searched all writable loaded pages of the stock ELF against **10
complete coredumps** (idle / traffic / cold / warm / post-SSR). `0xc1455000`
(phys `0x87c55000`) satisfies all three:

* **phdr17** (VA `0xc1440000`, `filesz 0xa1fe0`, flags `0x8000006` = R+W, **LOADED**) —
  the **same segment** as the v4/v5/v6-proven save area, so physical memory exists.
* **546 words vary across dumps** ⇒ the modem writes the page ⇒ **mapped + writable**.
* the PIL **poison guard `0xdeadc0fe`** is present at `+0xd58..+0xd7c` ⇒ the loader
  touched the page.
* the run `[+0x000, +0xd58)` = **3416 B** is byte-identical (all zero) in **every**
  dump and all-zero in the stock image ⇒ no live writer touches it.

The 2064-byte ring therefore fits with **1352 B of margin** before the poison guard.

## 4. Build + verification

`python3 scratch/diag_patch_v7/build_diag_patch_v7.py` (preconditions asserted):

```
[+] cave assembled: 80 bytes
[+] ring: 128 entries, save 0xc1455000..0xc1455810 (poison guard at +0xd58 -> margin 0x548)
[+] b16 site A @0x9f874 (VA 0xc0326874): 0e79fa5b -> 764ea15b
[+] b16 site B @0xb50f4 (VA 0xc033c0f4): ceccf85b -> 36e29e5b
[+] b05 @0x560 (VA 0xc0030560): wrote 80B cave
[+] hash re-verify: PASS
[+] modem.b16: 6 differing bytes ; modem.b05: 68 differing bytes
```

* `ufi001b_hash_tool.py verify` → **19 MATCH / 8 ZERO-BSS / 0 MISSING / 0 MISMATCH /
  Overall PASS ✓**.
* `verify_elf.py` (patched ELF) disassembles **both** sites to `call 0xc0030560` and
  the cave to the **17 intended instructions** (`r5 = ##0xc1455000`; `r6 = ##0xc02fda90`).
* **Readback controls.** Negative: `diag_v4.elf` and `cw1_cold.elf` → `magic = 0` →
  *"NOT a v7 export"*. Positive: a synthetic injection reads back exactly; the wrap
  case (`count = 300`) reconstructs `seq 173..300` in order.

**Patched md5s (v7):** `b16 438292ca…`, `b05 1e627199…`, `b01 c1fa8d29…`,
`mdt 854479d9…`. (Stock: `b16 57fef19d…`, `b05 332f000b…`, `b01 b85b86ce…`,
`mdt 1a6f9507…`.) ⚠ **v7's `b16` is byte-identical to v6's** — expected, since both
re-encode the *same two call sites* to the *same cave VA*; v7 differs in `b05` (the
80-byte ring cave vs v6's 60-byte slot), `b01` and `mdt`.

## 5. Deployment + the validation capture (on-device)

Backed up the resident v6 set (`/data/local/tmp/fw_backup_v6_prev7/`), copied the four
v7 files to `/firmware/image/`, `sync`, md5-verified, then
`echo restart > /sys/kernel/debug/msm_subsys/modem`.

**P-79a (deployment) — PASS.** No crash-loop: no `:Excep`, no `MPSS authentication
failed`, `Brought out of reset` with **fatal count unchanged (45→45)**, the modem
reaches ONLINE and the data path comes up. (The v1/v3 lesson: a bad save area
crash-loops within ~4 s.)

**P-79b (instrument ran) — PASS.** Arming one device-local `/dev/ramdump_modem`
reader and issuing a `restart` (which also produces a ramdump) yielded a **complete
85 443 284 B** dump (`diag_v7a.elf`, md5 `ae78e4df59b24137a42ff5398fef6016`). The
readback found `magic = 0xc0030560` and `count = 30`.

**P-79c (caller identity) — PASS.** Of the 30 calls in the ring, **caller A
(mobility) = 30, caller B (ACQ) = 0**; selector `r2==1 (0x408020d)` = 22,
`r2==0 (0x4070210)` = 8. ⇒ the §60.4 reading (the mobility evaluator, flags `&1`) is
reproduced by a **direct** instrument, over a whole boot rather than at one instant.

⚠ **What this capture is NOT.** It is the **validation** capture (a deliberate restart
after ~90 s of the previous boot), not the ~900 s event window: 30 calls in ~90 s
≈ **0.33 Hz**. P-79d (the *last* arm before the event) is **not** scored here.

## 5a. ★★★ THE EVENT CAPTURE (ledger item 80) — a FATAL, `count = 674`, last 128 arms `A + r2==1`

A fresh v7 boot (`Brought out of reset` at device uptime `64471.502727`), **idle**, with
**one** device-local `/dev/ramdump_modem` reader armed. A host watcher caught the event:

```
[65373.792793] modem subsystem failure reason: lte_ml1_common_timer.c:390:.
[65386.658481] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset
```

⇒ **fatal at `boot+902.290066 s`** (fatal 45→46, reset 66→67). Dump **complete
85 443 284 B**, md5 **`9714fc3f815d0e72e9bfd909bf7396a2`** (`diag_v7_event.elf`; device
md5 == host md5). `read_diag_ring.py` and an independent raw `struct` scan agree:

| field | value |
| :-- | :-- |
| `magic` | `0xc0030560` ✔ |
| `count` | **674**  (= **0.747 Hz** over 902.29 s) |
| ring | 128 entries, seq **547 .. 674**, **consecutive (no gaps)** |
| caller | **A = 128, B = 0** |
| selector | **`r2==1` = 128, `r2==0` = 0** |
| last call | **caller A + `r2==1`** ⇒ `0x408020d` |

**P-79d — PASS**: the last arm before the fatal is caller A with message `0x408020d`.
**Cross-check (§80.4):** the state-20 ctx (`0xc2150f58`, `+0x38 = 0x14`) is **armed** with
the full timeout — arm `…400fb4e8`, expiry `…401e5b32`, **Δ = 960 074 ticks = 50.004 ms**
⇒ the last arm was < 50 ms before the fatal, and the fatal is **not** the state-20 expiry
(it is still pending), confirming ledger §60.6.2.

⚠ **OPEN (§80.5):** v6 (traffic, 900.606 s) counted **4**; v7 (idle, 902.29 s) counted
**674** — a **168×** discrepancy on the *same* `modem.b16`. Regime, instrument quality, and
a different fatal signature (`lte_LL1_gap_rf_tune.c:351` vs `lte_ml1_common_timer.c:390`)
are all candidates; none is established. ⇒ ledger §63.5's *"the path is dormant"* is **not**
representative of the idle regime. The **causal** direction (arm → fatal) is **withheld**.

## 5b. ★★★ THE REGIME CONTROL (ledger item 81) — traffic gives 225 (0.20 Hz), so the gap is ~3.8× regime + ~45× unexplained

The 168× gap of §80.5 was attacked by holding the **instrument fixed** (v7) and changing **only the
regime**: sustained ping traffic (`ping -c 1 -W 2 8.8.8.8` every 4 s). Fresh boot ref `67650.588566`,
data interface `rmnet6` (⚠ the index varies per boot). Result:

| run | regime | window | count | rate |
| :-- | :-- | --: | --: | --: |
| `diag_v6` | traffic | 900.606 s | 4 | 0.0044 Hz |
| `diag_v7_event` | **idle** | 902.29 s | **674** | **0.747 Hz** |
| `diag_v7_traffic` | **traffic** | 1139.03 s | **225** | **0.1975 Hz** |

* The event was a **WEDGE**, not a fatal (pings 1..132 OK, 133..170 fail; fatal count unchanged 48 through
  `boot+1099 s`) — the **3rd v6/v7-lineage traffic → WEDGE** (items 71, 78, 81), confirming item 78's 2×2.
  Dump via `restart`; complete 85 443 284 B, md5 **`f9e6de7e91b47ca8cc45b5bf20e8dd5e`**.
* Ring: `count = 225`, seq **98..225** (no gaps), **A = 128, B = 0**, `r2==1` = 126 / `r2==0` = 2.
* ⇒ **regime effect = 3.78×; residual 44.9× UNEXPLAINED.** ⚠ **P-81c MISSED in both directions** (the
  count neither collapsed to ≲50 nor stayed ≳300).
* ⇒ **§63.5's "dormant" is wrong in BOTH regimes**: `FUN_c02fda90` runs at **0.2–0.75 Hz**.

⚠ Setup traps (recorded): `adb shell` kills the process group on exit (arm over `ssh`); the data path was
dead and `svc data` did not fix it (a modem restart did); and the traffic-`ssh` **hangs** because the
backgrounded loop inherits the channel's stdout — the host watcher never polled.



## 5c. ★★★ THE OFFLINE STATE-20 TEST (ledger item 82) — the fatal is COUPLED to the ctx0 watchdog, but "pending vs just-fired" is not separable from a post-assert dump

Answers the user's step (3). **Offline/read-only** — no image built, flashed or run. Evidence:
`evidence/243_ctx_expiry_offline/` + `evidence/239_diag_patch/export_v4_output.txt`.

**Method.** In all 14 archived dumps, locate the **nine** ML1 ctx objects **by invariant** (`+0x0c ==
0xc02d7bd0`, `+0x14 == self`, `+0x38 ∈ [20,28]`) — never a fixed VA, since the array base moves `+0x20`
between the v4 and v7 builds — and read ctx0's `+0x20` (expiry), `+0x28` (arm), `+0x30`.

**Result — a clean partition.** ctx0's **expiry is non-zero at exactly the three `lte_ml1_common_timer.c:390`
fatals** (`diag_v4` `0x0d6356d049`, `diag_v7_event` `0x124401e5b32`, `modem_20260930T052551Z` `0xb6cc928964`)
and **zero at all nine other dumps** (3/3 vs 0/9; Fisher exact **p ≈ 0.0045**). The `+0x30` low byte co-varies
(`0xab` vs `0xac`/`0xa9`). ⚠ The discriminator is the **fatal signature**, not "fatal vs wedge": `diag_v6` is a
*fatal* with ctx0 **cleared** (signature `lte_LL1_gap_rf_tune.c:351`).

**Why not chance.** ctx0 is armed at **0.75 Hz idle / 0.20 Hz traffic** (§5a/§5b) with a **50.004 ms** timeout
(Δ = 960 074 ticks, export-verified), so a live deadline is non-zero only **1–4 %** of the time; hitting it
3/3 is p ≈ 5e-5.

**Corroboration — and a tension.** `diag_v4.elf` (md5 `0ab22ccb…`, re-verified) is the v4 entry-argument export
(`evidence/239_diag_patch/build_diag_patch.py`): it redirected the **entry** of the shared callback
`FUN_c02d7bd0` (whose stock first instruction is `call 0xc02d1140`, a `return 0` stub). It reads: the callback
ran **10 836× in 902.377 s = 12.0 Hz**, and the **last entry before the natural fatal carried `r0 = ctx0`**.
⚠ But 12 Hz is **16× the armer's 0.75 Hz**, so `FUN_c02d7bd0` is **not** "one call per ctx0 arm" — the
§45–52 "50 ms one-shot" model is **not the whole story**. **Flagged OPEN.**

**⚠⚠ §80.4 is WITHDRAWN.** Its inference *"Δ = 50.004 ms ⇒ the arm was < 50 ms before the fatal ⇒ still
pending"* is **invalid**: Δ = `expiry − arm` is the **timeout constant**, not an age. The offline data
**cannot** separate "pending at the assert" from "just fired at the assert" — the dump is taken **~13 s after**
the assert (`dmesg`: `65373.792793` → `Brought out of reset` `65386.658481`), so it shows the ctx frozen after
the fact, and whether the fire path clears `+0x20` is unobservable. ⇒ **step (3) is NARROWED, not settled;**
the decisive test is the **response-side instrument (step 2)**.

## 6. Readback

`python3 scratch/diag_patch_v7/read_diag_ring.py <coredump.elf> [-n N]` — finds the
PT_LOAD covering phys `0x87c55000`, checks the magic, reconstructs the ring in
chronological order (filter `seq==0`, sort by `seq`), and prints the last *N* calls,
the caller/selector histogram, and the seq span.

## 7. SOP compliance (this doc)

**Ground-truth first.** Every VA and byte was read from the **stock HMU05 ELF**
(`md5 954f2be5…`) with `llvm-objdump`; the call sites were re-encoded with a `call`
re-encoder already validated against the Doc-239/Doc-240 encodings; the save area was
chosen by a **cross-dump** search over 10 complete coredumps, not by inspection.
**Re-hash + require PASS:** `ufi001b_hash_tool.py verify` → **0 MISMATCH / PASS**, and
the patched ELF disassembles to the intended code. **Pre-registration:** `PRE_REG_item79.md`
was written **before** the deploy, with explicit predictions (P-79a..d) and falsifiers
(F-79a..c). **Reversible:** the resident v6 set is backed up on the device
(`fw_backup_v6_prev7/`), so a revert is a copy + restart. **Honesty:** the §2
off-by-one in the ring index was **caught and fixed in-session** (not hidden); the
validation capture is explicitly labelled *not* the event window; the **rejected**
`c15:14` cycle-counter timestamp is recorded with its reason (an unverified
instruction could trap — the v1/v3 lesson). **Live-write discipline:** `/firmware` was
`rw`, the four files were md5-verified after the copy, and `sync` was issued.

## 8. Achieved vs Expected

| planned | achieved | status |
| :-- | :-- | :-- |
| build a (2)-class HISTORY instrument on `FUN_c02fda90` | a 128-entry ring cave | **MET** |
| pass the project hash verifier | 19 MATCH / 0 MISMATCH / PASS | **MET** |
| pick a save area by the §57.5 sound criterion | `0xc1455000` (phdr17, 546 live words, poison, 3416 B free) | **MET** |
| deploy without a crash-loop (P-79a) | no `:Excep`, fatal 45→45, ONLINE | **MET** |
| read a populated ring (P-79b) | `magic` OK, `count = 30` | **MET** |
| caller A dominates (P-79c) | A 30 / B 0 | **MET** |
| the last arm before the ~900 s event (P-79d) | **scored in the EVENT capture**: caller A + `r2==1` (seq 674) | **MET** |
| bound the period | mean **0.747 Hz** (674 calls / 902.29 s); no in-cave clock ⇒ the spread is unknown | **PARTIAL** |
| explain v6's 4× count | **decomposed**: 3.78× regime + 44.9× unexplained (§5b / ledger item 81) | **PARTIAL** |
| replicate the branch under traffic | v7 + traffic → **WEDGE** (3rd of the lineage) | **MET** |
| (item 82) settle offline whether the fatal is the state-20 expiry | strong coupling established (3/3 vs 0/9, p≈0.005; last callback entry = ctx0), but **pending vs just-fired not separable** | **PARTIAL** |
| (item 82) re-audit §80.4 | its inference is invalid; **WITHDRAWN** | **CORRECTED** |

# PRE-REGISTRATION — v11 state-20 TIMEOUT SWEEP point (500 ms)

**Written 2026-10-01, BEFORE the build and BEFORE the run.** Ledger item 85 §85.5.
Builds on items 83/84 (the paired ARM+CALLBACK ring) and item 85 (the 50 ms → 5000 ms
extension, which showed the reply is **LATE, not LOST** and the state-20 watchdog is a
**symptom**).

## The question (item 85 §85.5, verbatim)

Is the new `lte_ml1_sm_idle_stm.c:2913` assert that appeared under the v10 5 s deadline

* **(a)** an **independent** consequence of the same ~900 s ML1 stall, or
* **(b)** **caused by the 5 s deadline being too long** (the ML1 waiting where it used to bail)?

A **timeout sweep** discriminates: a smaller extension that **survives past ~904 s** supports
(b) and is a **usable fix**; a **third relay** supports (a) and says the stall is robust.

## The lever

`modem.b16` file offset `0x74c40` (VA `0xc02fbc40`): the state-20 watchdog arm reads the
instance's 50 ms timeout.

> `81 d6 50 93` (`r1 = memh(r16+#0x568)`) → `81 fe 00 78` (`r1 = #0x1f4` = **500 ms**)

Slot 3 of the same 3-instruction packet; the `r0 = add(r16,#0x328)` word is **byte-identical**
and the parse bits stay **11** (end of packet). The timer pointer, the call, and the instance's
`+0x568` field are untouched.

## Predictions

| id | prediction | falsifier |
| :-- | :-- | :-- |
| **P-S1** | the patch is resident and executing (code bytes in the dump; a 500 ms timer-pool entry appears) | bytes absent / no 500-entry |
| **P-S2** | the state-20 watchdog **fires again** ⇒ fatal `lte_ml1_common_timer.c:390` at **≈902.1–902.7 s** ⇒ the reply's lateness at the event is **> 500 ms** | a `sm_idle_stm.c:2913` fatal ⇒ lateness < 500 ms |
| **P-S3** | the fatal window stays in **902.1 … 904.0 s** (the stall onset is unchanged; only the first *fatal* consequence moves) | a fatal outside that band |
| **P-S4** | **no deadline value survives** (the expected outcome, per the epoch-10 prior) — i.e. this is a **relay**, not a fix | survival past `boot+950 s` with the data path alive |

**Prior (stated, not a prediction):** ledger line 2487 (epoch 10, 2026-09-28) saw
`lte_ml1_sm_idle_stm.c:2913` fire at 902.69 s when the ten `FUN_c02d7bd0` assert sites were
patched **off** — a *different* intervention removing the `common_timer.c:390` manifestation.
That makes **(a)** the expected branch.

## Verification checks (before scoring)

* **V-S1** build hash re-verify PASS; `ufi001b_hash_tool.py verify` 19/19; b16 diff = 13 bytes
  (9 v9 + 4 here); b05 **identical to v9/v10**.
* **V-S2** deployed md5 == host build md5 for all four files (b01/b05/b16/mdt).
* **V-S3** deploy PASS: `Brought out of reset` + `rmnet2` UP/LOWER_UP + `ping` 0 % loss.
* **V-S4** the dump is **complete** (validate against its own ELF header total; `/data` free
  before capture).
* **V-S5** the patch is read back from the dump's own memory at `0xc02fbc40` == `81fe0078`.
* **V-S6** the timer pool (`0xc2cd4de0`, 256 × 0x90) holds a mode-2 entry with raw `0x1f4`.

## What counts as the answer

* If **P-S2** passes → the lateness is **> 500 ms**; the ML1 really is stalled for a long time.
  The next sweep point would be **2000 ms** (expect the `sm_idle_stm.c:2913` relay).
* If **P-S2** is falsified (`sm_idle_stm.c:2913` again) → the lateness is **< 500 ms**, and the
  idle-SM assert is **independent of the state-20 deadline** ⇒ **(a)**, and the sweep is done.
* Either way, if **P-S4** passes the state-20 watchdog is confirmed a **symptom**, and the work
  moves to the stall's **root**, not to any watchdog.

## Scope / honesty

Single boot, **n = 1**. The window is one boot (~910 s). The instrument is read-only (the ring is
written by the modem). The v10 image is backed up on-device before the v11 flash.

# 243 — Diagnostic firmware v24/v25: engine-entry ring, the prologue-packet crash, and the OFFLINE_WAKEUP state correction

**Date:** 2026-10-04. **Task:** #267. **SOP:** Doc 197 mandatory SOP. **Ledger:** §112.58.

**Goal (user request):** "Instrument the 2→3 sleep-entry completion path" — build a ring that captures
every call to the STM engine `FUN_c0fe1460` so we can see whether a `2→3` (ONLINE_SLEEP_WAIT → SLEEP)
REQUEST is still being made at the ~900 s fatal, or the driver stopped.

## Achieved vs Expected

| Objective | Status |
|---|---|
| Build a stable engine-call ring | **v25 STABLE to 916 s, captured the fatal** (v24 crash-looped) |
| Read the sleepmgr state at the fatal | **DONE — but CORRECTS the prior model: state = 9 (OFFLINE_WAKEUP), not 2** |
| Determine if `2→3` requests still flow at the fatal | **OPEN** — v25 ring unusable (wrong caller offset, no sleepmgr filter, wrapped) |
| Identify the sleepmgr's actual driver | **OPEN** — the `→2`/`→3` external sites pass `array0`, not sleepmgr |

## ★★★★★ §112.58 — STATE CORRECTION: slot[0] is OFFLINE_WAKEUP (9) at fatals, NOT 2

**⚠ This corrects §112.54, §112.56.5, §112.57.9, §112.57.14, §112.57.15 — all of which claimed
`*(0xc1e158d4) = 2` (ONLINE_SLEEP_WAIT) at the fatal.** A direct byte-read of the sleepmgr instance
`0xc1e158d0` slot[0], field `+0x4` (the state field, per `stm_get_state` at `0xc0fe1960`:
`r0 = memw(r0+0x4); return`), across **6 fatal dumps** gives **9 (OFFLINE_WAKEUP)** in every one:

| Dump | modem uptime | slot[0] `+0x4` | state |
|---|---|---|---|
| `dump_devcd1_916.bin` (v25 fatal) | 902 s | 9 | OFFLINE_WAKEUP |
| `modem_coredump_up915.44_devcd1.elf` | 915 s | 9 | OFFLINE_WAKEUP |
| `modem_coredump_up1818.92_devcd2.elf` | 1818 s | 9 | OFFLINE_WAKEUP |
| `modem_coredump_up2723.69.elf` | 2723 s | 9 | OFFLINE_WAKEUP |
| `modem_coredump_up3629.79.elf` | 3629 s | 9 | OFFLINE_WAKEUP |
| `dump_v13_wedge.bin` (wedge) | — | 4 | ONLINE_WAKEUP |
| `dump_v13_fixed_boot.bin` (healthy) | — | 0 | INACTIVE |
| `dump_devcd2_328.bin` (v24 crash, 1 s) | 1 s | 0 | INACTIVE |

Raw bytes at `0xc1e158d0` in every fatal dump: `70 4f a9 c1 | 09 00 00 00 | 1d 00 00 00 …`
(class ptr `0xc1a94f70`, state `9`, `+0x8 = 0x1d`).

**Reframed model:** the ~900 s event is a **stuck WAKEUP** — the sleepmgr is in `OFFLINE_WAKEUP (9)`
at the fatal (trying to wake from offline sleep, the `9 → … → 1` transition never lands), or in
`ONLINE_WAKEUP (4)` at the wedge (the `4 → 1` transition never lands). The assert
`ASSERT(stm_get_state(LTE_ML1_SLEEPMGR_STM) == SLEEP)` fires because a timer callback expected
`SLEEP (3)` but found `OFFLINE_WAKEUP (9)`. This is the **inverse** of the prior "stuck sleep-entry"
model — the sleepmgr is stuck **waking up**, not going to sleep.

**Why the prior claims were wrong:** unknown. The v13 wedge dump correctly reads state 4 (ONLINE_WAKEUP),
so the read method works. The "state = 2" claims for fatal dumps may have been a misread, a stale
intermediate dump, or a transcription error. The correction is verified across 6 independent fatal
dumps (4 from prior sessions, 2 from this session).

## v24 — engine prologue-packet hook: CRASH-LOOP (NEGATIVE)

**Technique:** replace word0 of the engine prologue packet `{ call 0xc08384e0; allocframe(#0x28) }` at
`0xc0fe1460` with `call 0xc0dedc08` (a tramp → cave), **keeping `allocframe` in its original packet**
(the v7/Doc-242 technique). Cave at `0xc003054c` (88 B) logs `{seq, caller=memw(r30-0x24),
new_state=r2, obj=r1}`, reproduces the helper's two `memd` saves, then `r31 = r7; jumpr r7` → continues
at `0xc0fe1468`. `modem.mdt = f81098986d7bb6c9ec89bf144e0a013d`.

**Result:** CRASH-LOOP. The modem crashed at variable AP uptimes (24, 22, 328 s) with **corrupted
control flow**: dump `dump_devcd2_328.bin` shows modem uptime 0:00:01, Task AMSS0, PC `0xd0a7eaf8`
(a stale return address — `0xd0xxxxxx` = `0xc0xxxxxx + 0x10000000`), LR=0, BADVA `0x4cd67a2c`,
SP `0x8af4fff0`, SSR=3 (bad store). The ring (marker `0xc1455040`) wrote seq=22 then crashed.

**Isolation (v22 control):** v22 = v21 image with the site reverted to stock (`40780a5b05c09da0`),
tramp+cave kept → **STABLE**. This isolated the **engine-entry prologue-packet rewrite** as the sole
crash cause. The bytes are byte-verified correct (site/tramp/cave decode exactly as intended), so the
fault is the **prologue packet semantics** — the `call` + `allocframe` pairing in a single packet must
not be disturbed, even when `allocframe` stays in place. **Do not hook the engine prologue packet.**

## v25 — engine first-BODY-packet hook: STABLE, captured the fatal, but ring UNUSABLE

**Technique:** leave the prologue packet untouched; hook the first BODY packet at `0xc0fe1468`
(`{ r17:16 = combine(r3,r1); r19:18 = combine(r2,r0) }` → `{ jump TRAMP; nop }`). Cave at `0xc003054c`
(180 B) logs `{seq, obj=r1, new_state=r2, caller=memw(r30+0x28)}`, reproduces the `combine` packet,
continues at `0xc0fe1470`. `modem.mdt = 36fbafd5fd4daceec9a4dff0a7413435`.

**Result:** **STABLE to 916 s.** The ~900 s fatal fired at AP uptime 915.649 s, signature
`lte_ml1_common_timer.c:390`, Task `tmr_slave3`, PC `0xc087a804` (assert logger), SSR=0, BADVA=0 — a
clean assert (not a data abort). Coredump `dump_devcd1_916.bin` captured.

**Ring unusable — three bugs:**
1. **Wrong caller offset.** The cave reads `caller = memw(r30+0x28)`, but `allocframe(#0x28)` makes a
   0x28-byte frame; the engine's saved r31 (the external caller's return address) is at `[r30+0]`
   (allocframe stores r31:r30 at `[r29] = [r30+0]`), NOT `[r30+0x28]` (which is outside the frame,
   reading the caller's stack). Fix: `memw(r30+0)`.
2. **No sleepmgr filter.** The engine drives **many** STMs (it's the generic STM transition driver).
   120/128 ring entries are `r1=0xc1e145f0` (a `TTL_WAIT` STM, state 5), drowning the rare sleepmgr
   calls. The ring wrapped (seq=738283 > 128) so sleepmgr entries were overwritten. Fix: filter on
   `r1` in the sleepmgr range `0xc1e158d0..+0x1c*64`.
3. **`objclass()` misclassification.** `0xc1e145f0` was labeled `array0[666]` but the slot math is
   non-integer (`(0xc1e145f0-0xc1e0fd08)/0x1c = 666.57`), so it's NOT array0-aligned — it's a different
   object in the `0xc1e1xxxx` range.

**★ The `→2`/`→3` external call sites pass `array0`, NOT the sleepmgr.** At `0xc0d28a68`:
`r20 = add(r6, mpyi(#0x1c, r16))` where `r6 = 0xc1e0fd08` (array0 base); then `r1:0 = combine(r20, #0)`
at `0xc0d28a94`. So the `→2` site (`0xc0d28aac`) and `→3` site (`0xc0d2885c`) pass `r1 = array0[n]`,
NOT the sleepmgr `0xc1e158d0`. The sleepmgr is driven through a DIFFERENT path — likely the internal
recursive call `0xc0fe1558` (`r1 = add(r1, mpyi(#0x1c, r2))` at `0xc0fe1554`, which computes a
state-offset base), or another sleepmgr-specific function. (This is why v23's filter on
`r1 == 0xc1e158d0` at the external sites blocked everything.)

**stm_get_state** at `0xc0fe1960`: `if(r0==0) assert; r0 = memw(r0+0x4); return` ⇒ the state field is
**+0x4**. The engine's state WRITE at `0xc0fe1630`: `memw(r16+0xc) = r18` writes to **+0xc** — a
DIFFERENT field (likely "pending state" or a sub-state), NOT the getter's state. The getter reads
`+0x4`; the writer writes `+0xc`. These are different fields.

## Next steps

1. **Find the sleepmgr's actual driver** — the caller that passes `r1 = 0xc1e158d0+0x1c*n` to the
   engine. Candidates: the internal recursive call `0xc0fe1558`, or a sleepmgr-specific function not
   yet identified. Search for raw LE occurrences of `0xc1e158d0` (and `0xc1e158c0` for the immext
   prefix) in the disassembly.
2. **Build v26** with: (a) the correct caller offset `memw(r30+0)`, (b) a sleepmgr-only filter
   (`r1` in `0xc1e158d0..+0x1c*64`), (c) a larger ring (256 or 512 entries) to avoid wrapping.
3. **Test the stuck-wakeup hypothesis:** if the sleepmgr is stuck in `OFFLINE_WAKEUP (9)`, the
   `9 → 1` (ONLINE) transition is what never lands. The `OFFLINE_WAKEUP` entry handler and its
   awaited completion event are the new targets.

## SOP compliance

* **Ground-truth-first:** all disassembly from the stock HMU05 ELF (`scratch/hmu05_stock_elf/`); all
  state reads from raw coredump bytes (not the format string); the crash report from the filled report
  block (via `read_crash_report.py`); the state field offset from reading `stm_get_state`'s code.
* **One change at a time:** v24 deployed alone → crash-looped → rolled back to stock → v25 deployed
  alone → stable. v22 control isolated the crash cause.
* **Reversible:** v24/v25 rolled back to stock (`/lib/firmware/modem.mdt 1a6f9507…`); the shipped
  mitigation (pre-emptive SSR) was re-enabled after the diagnostic.
* **Honest about negatives:** v24 NEGATIVE (crash-loop); v25 ring UNUSABLE (three bugs documented
  above); the prior "state=2" model CORRECTED to state=9.
* **Pre-registered criteria:** the v25 ring was pre-registered to answer "does a `2→3` request still
  flow at the fatal?" — it could not answer this (ring unusable), and the direct state read answered
  a different question (the state is 9, not 2, so the `2→3` question was based on a wrong premise).
* **Ledger + memory updated this session:** §112.58 added to `project_wedge_vs_fatal.md`; MEMORY.md
  index updated; this doc is Doc 243.

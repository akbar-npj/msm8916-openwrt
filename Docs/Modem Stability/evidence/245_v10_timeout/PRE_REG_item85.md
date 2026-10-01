# PRE-REGISTRATION — item 85: the v10 **state-20 watchdog timeout extension** (50 ms → 5000 ms)

Written **before** building, deploying or running v10. Ledger item 85. Follows items 83/84, which established:

* the ~900 s fatal **is** the ctx0 **state-20 watchdog expiry** (the state-20 callback is the last
  `FUN_c02d7bd0` entry of the boot and the only one; ctx0's `+0x20` is **SET and uncancelled**), i.e. the
  reply to the ML1 message **`0x408020d` was lost** — **n = 2 replicated** (`901.922537` / `902.289005 s`);
* the state-20 handler **is** the ERR_FATAL by construction (§51.5: `table[20] → 0xc02d7d54` →
  `0xc02d7d80: call 0xc0879150`).

## The question this settles: is the reply **LATE** or **LOST**?

Items 83/84 show the reply arrives within the 50 ms deadline **564 times** and then, once, does not. They
**cannot** distinguish:
* **(LOST)** the responder stopped answering — no reply will ever come; or
* **(LATE)** the responder answered, but later than 50 ms — the deadline is simply too tight.

**A single halfword-scale change discriminates them, and one branch is a fix.**

## The patch (verified offline)

`FUN_c02fbba4`'s `0x80` branch (the state-20 / ctx0 watchdog) is:

```
c02fbc34: p0 = cmp.eq(r1,#0x80)
c02fbc38: { call 0xc02a54b0
c02fbc3c:   r0 = add(r16,#0x328)
c02fbc40:   r1 = memh(r16+#0x568) }     ; 0x9350d681  -> the 50 ms timeout
```

⇒ **`modem.b16`, file offset `0x74c40` (VA `0xc02fbc40`): `81 d6 50 93` → `01 f1 09 78`**
(`r1 = memh(r16+#0x568)` → `r1 = #0x1388` = **5000 ms**). One 32-bit word; the timer object pointer
(`r0 = add(r16,#0x328)`) and the call are untouched, and the instance's own `+0x568` field is **not** modified.

Verified offline: `r1 = memh(r16+#0x568)` assembles **byte-identically** (`81 d6 50 93`) in slot 3 of a
3-instruction packet, and `r1 = #0x1388` assembles to `01 f1 09 78` in every slot ⇒ the parse bits (the two
LSBs) are `01` in both, so the packet structure is preserved.

⚠ **Scope:** the `0x80` event bit is the state-20 arm; the ring shows **every** ARM carries `r0 = 0` (index 0 →
ctx0), so in practice this changes only the ctx0 watchdog. If any other instance arms `0x80`, its watchdog is
loosened too — harmless (a looser deadline cannot create a fault that a tighter one would not).

## Predictions (scored only after the run)

* **P-T1 (the patch TOOK — mechanical).** At the next fatal, ctx0's Δ = `+0x20 − +0x28` is
  **≈ 96 000 000 ticks** (5000 ms at 19.2 MHz), not the ~960 073 of items 82/84. (Items 84 measured
  960 073 **to the tick** on both boots, so a stale value is unambiguous.) The ring's recorded `expiry_lo`
  should still equal ctx0's `+0x20` low word.
* **P-T2 (★ THE DISCRIMINATOR — mutually exclusive).**
  * **(LOST)** a fatal still occurs, at modem uptime **≈ 902.1 + 4.95 = `907.05 s`** (the +4.95 s shift is
    ~13× the 0.366 s boot-to-boot spread, so it is resolvable).
  * **(LATE)** **no fatal at all** in an idle boot run to ≥ **1200 s**, and the ring shows ARMs continuing
    with **0** state-20 callbacks (as in item 83's 305 s validation).
* **P-T3 (no collateral).** The ARM rate (~0.63 Hz) and the dispatcher rate (~11 Hz) are unchanged from
  items 84's two boots (0.626/0.661 Hz; 11.93/10.58 Hz) ⇒ the patch did not perturb the requester.

## Falsifiers / void conditions

* **V-T1.** The modem does not come up, or the next fatal's Δ is still ~960 073 ⇒ the patch did not take ⇒
  VOID (do not interpret P-T2).
* **V-T2.** The event is a **WEDGE** rather than a fatal (item 78's `image × traffic` 2×2) ⇒ the fatal branch
  of P-T2 is unscorable; re-run. (Items 80/84 show **idle ⇒ fatal** for this lineage, 3/3.)
* **V-T3.** `/data` must have room: **check `df` before arming and pull + delete after** (item 84 §84.2).

## Scope / honesty

* **n = 1** for v10 (items 84's result is n = 2; a v10 replication is a separate run).
* One device/SIM, **idle** (no generated traffic), one regime.
* The patch is a **probe, not yet a fix**: if the answer is LOST, the fatal will simply move ~5 s later and
  the real work is the responder. If the answer is LATE, it is a candidate fix and needs a soak.
* Reversible: the resident v9 set is backed up on-device before deploying v10.

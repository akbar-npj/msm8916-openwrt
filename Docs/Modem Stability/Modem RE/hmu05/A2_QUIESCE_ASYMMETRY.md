# A2 power — the modem-side quiesce asymmetry

**Crash it explains:** `a2_power.c:1189` — *"A2 Assertion Failed"* (ERR descriptor
`0xc3c1f3a0`, id 64, msg_ptr `0xc3cd68d8`).

**Status:** VERIFIED STRUCTURE (from the decompiled stock baseband) + LEADING
HYPOTHESIS for the crash. The structure below is read directly from the disassembly;
the causal step (that the collapse leaves a client un-quiesced and the next bring-up
then spins out) is the leading explanation, **not yet proven** — see §6.

**Source of truth:** `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c`
(stock HMU05 modem, 105 MB, generated 2025-09-20). All line numbers below are into
that file. Descriptor → `file:line` resolved with `scratch/a2_descr.py` against a live
coredump (`scratch/coredump_live/modem_coredump_up919.52.elf`).

**Related findings:**
- `project_a2_power_fatal_needs_control_on.md` — the fatal needs `control=auto`
  (A2 churn); `control=on` suppresses it. A/B: 16 fatals / 195 cycles under `auto`,
  **0** under `on`.
- `project_a2_driver_fix_investigation.md` — the AP-side handshake is **not** the
  cause (patch 834 zeroed the resume timeouts but left the fatal unchanged; in both
  arms the fatal *precedes* its down-ack timeout).

---

## 1. The A2 in one paragraph

The **A2** is a power/clock domain shared between the AP (the router CPU) and the
modem (MPSS). The modem owns it and collapses it (power-down) when idle and brings it
back up (power-up) when there is work. Both transitions are sequences of "spin until a
hardware status word reaches a required value". A spin that never reaches its value
eventually trips a shared watchdog and **asserts** — `a2_power.c:1189`.

---

## 2. The shared spin-and-assert helper — `FUN_c05042c8`

`FUN_c05042c8` (`:862146`) is called from **every** A2 wait loop. It is the single
place the `a2_power.c:1189` assert lives:

```c
void FUN_c05042c8(void)
{
  DAT_c28602f8 = DAT_c28602f8 + 1;              /* per-loop spin counter        */
  if (DAT_c28602f8 % 0x32 == 0) {               /* every 50 spins               */
      FUN_c0887450(1);
      DAT_c28602fc = DAT_c28602fc + 1;          /* SHARED budget counter        */
  } else {
      if (900 < DAT_c28602fc) {                 /* budget exhausted (> 900)     */
          FUN_c0506938();
          FUN_c0879150(&DAT_c3c1f3a0);          /* ASSERT a2_power.c:1189       */
      }
  }
}
```

* `DAT_c28602f8` — the **per-loop** spin counter; callers reset it to `0` before each
  `while` loop (`DAT_c28602f8 = 0;`).
* `DAT_c28602fc` — the **cumulative** budget; incremented once per 50 spins. The assert
  fires when it exceeds **900**, i.e. after **> 45 000 total spins** (~a few hundred ms).
* Because `DAT_c28602f8` is reset per loop but `DAT_c28602fc` is **not**, the budget is
  **shared across every wait loop in the surrounding sequence**.

`DAT_c285eb80` records a high-water mark of `DAT_c28602fc` (`:862905`, `:863202`) — a
diagnostic of how close the modem ever came to the assert.

---

## 3. The two directions wait on DISJOINT state sets

### Power-up / active-refresh path

`FUN_c0504ccc` (`:862482`) is the A2 bring-up/refresh sequence (it asserts
`DAT_c285ebc8 == 1`, i.e. "A2 is up"). It calls **`FUN_c0504fc8`** twice
(`:862512` and `:862574`).

`FUN_c0504fc8` (`:862621`) waits on **five per-client state words** — the quiesce:

```c
/* fast pre-check: if any client word is set, do a pre-pass */
if (((_DAT_ec320ba4 & 7) != 0) || ((_DAT_ec320ba8 & 7) != 0) ||
    ((_DAT_ec320bac & 7) != 0) || ((_DAT_ec320bd8 & 7) != 0) ||
    ((_DAT_ec320be0 & 7) != 0))
    FUN_c0508c30();

DAT_c28602f8 = 0;  while ((_DAT_ec320ba4 & 7) != 0) FUN_c05042c8();   /* :862637 */
DAT_c28602f8 = 0;  while ((_DAT_ec320ba8 & 7) != 0) FUN_c05042c8();   /* :862641 */
DAT_c28602f8 = 0;  while ((_DAT_ec320bac & 7) != 0) FUN_c05042c8();   /* :862645 */
DAT_c28602f8 = 0;  while ((_DAT_ec320bd8 & 7) != 0) FUN_c05042c8();   /* :862649 */
DAT_c28602f8 = 0;  while ((_DAT_ec320be0 & 7) != 0) FUN_c05042c8();   /* :862653 */
```

Each client word must reach **`& 7 == 0`** ("quiesced / released"). The five words are
referenced **only** here and in two diagnostic snapshot functions (`:866113`–`:866197`
just copy them into a debug struct) — never in any power-down path.

### Power-down / collapse path

`FUN_c0505308` (`:862791`) is the collapse sequence. It is gated by `DAT_c285ebc8 == 1`
(must be up to go down — see `FUN_c0505d90` `:863018` → `:863034`) and sets
`DAT_c285ebc8 = 0` at the end (`:862898`). Its waits are:

```c
DAT_c28602fc = 0;                                             /* :862803 — resets the budget! */
...  9 sub-block deinit checks (asserts a2_power.c:2337..2371) ...
DAT_c28602f8 = 0;
while ((sbyte)_DAT_ec320b94 != 0) FUN_c05042c8();             /* :862866 */
...  clears bits in _DAT_ec320808/80c/810/814/a98 ...
DAT_c28602f8 = 0;
while ((_DAT_ec320404 & 0x1e) != 0) FUN_c05042c8();           /* :862878 */
...  FUN_c0ce31dc(99,0); ...
DAT_c285ebc8 = 0;
```

It waits on **`0xec320b94` (low byte == 0)** and **`0xec320404 & 0x1e == 0`** — a set
**disjoint** from the five client words. **It never checks the five client words.**

| | power-UP / refresh (`FUN_c0504ccc` → `FUN_c0504fc8`) | power-DOWN / collapse (`FUN_c0505308`) |
|---|---|---|
| per-client quiesce (`ba4/ba8/bac/bd8/be0 & 7 == 0`) | **YES** (5 loops, ×2 calls) | **NO** |
| other status waits | `b98&3, b9c&3, ba0&3, bbc==0, b94&0x3000, b94>>14&7, 0x404&0x10` | `b94 (low byte)==0, 0x404&0x1e==0` |
| resets the shared budget `DAT_c28602fc` | **NO** | **YES** (`:862803`) |

---

## 4. The asymmetry, stated precisely

1. **Different prerequisites.** The bring-up enforces a per-client release
   (five words `& 7 == 0`); the collapse does not enforce the same condition — it
   collapses on a *different* pair of words.
2. **One-way budget.** The 45 000-spin assert budget (`DAT_c28602fc`) is reset **only**
   by the collapse (`FUN_c0505308` `:862803`). The bring-up path never resets it, so
   every wait in the bring-up sequence — the message-0x1d ack spin in `FUN_c0500d4c`,
   the many register waits in `FUN_c0504ccc`, and the five-client quiesce — **shares**
   one budget.

---

## 5. How this can produce `a2_power.c:1189`

Leading hypothesis (matches the observed A2-churn-triggered crash):

1. Under `control=auto` the A2 cycles power-down/up roughly every 8 s (measured:
   `pc_vote/unvote` ≈ 62/61 over 488 s).
2. The collapse (`FUN_c0505308`) does **not** wait for the five client words, so it can
   complete while a client word is still `& 7 != 0` — the AP's client is left holding
   the A2.
3. The next bring-up (`FUN_c0504fc8`) spins on that client word. The spin shares the
   single 45 000-spin budget with all the other bring-up waits, so it trips the assert
   sooner than 45 000 spins on that word alone.
4. `FUN_c05042c8` fires `a2_power.c:1189` → the modem crashes; the AP then sees the
   `bam-dmux` down-ack / pc-ack timeouts ~2 s later (consequences, not causes).

In the observed fatal coredump the stuck word was `_DAT_ec320bac & 7 == 1`.

The AP-side A/B **rules out** the alternative "crossed votes / AP handshake race"
explanation: patch 834 removed the resume timeouts entirely yet the fatal rate was
unchanged, and the fatal always preceded its timeout. Whatever is wrong is **inside the
modem's collapse↔bring-up handshake**, and it needs the collapse to happen at all —
which is why pinning `control=on` (no collapse) eliminates it.

---

## 6. PROVEN vs OPEN

**Proven (read from the disassembly):**
- The `a2_power.c:1189` assert lives in `FUN_c05042c8`, budget `DAT_c28602fc > 900`
  (≈ 45 000 spins).
- The five-client quiesce (`ba4/ba8/bac/bd8/be0 & 7 == 0`) exists **only** in the
  bring-up path (`FUN_c0504fc8`, called from `FUN_c0504ccc`).
- The collapse path (`FUN_c0505308`) waits on a **disjoint** set (`b94`, `0x404`) and
  never on the five client words.
- The budget `DAT_c28602fc` is reset **only** by `FUN_c0505308` (`:862803`).

**Open:**
- Which of the five words is the AP's client, and what sets/clears each (`0xec320bac`
  was the stuck one at the observed fatal).
- Whether the collapse *actually* leaves a client word set on the failing cycles
  (needs a per-edge trace of `0xec320bXX` across a collapse — not yet captured; the
  driver's `dev_dbg` is unavailable, `CONFIG_DYNAMIC_DEBUG` is not set).
- Whether the intended design is that the collapse should quiesce the clients (making
  the bring-up check redundant), or that a client may legitimately hold across a
  collapse and the bring-up should tolerate it.

---

## 7. Candidate modem-side fix (NOT attempted)

Make the collapse **symmetric**: in `FUN_c0505308`, before completing, wait for the
same five client words to reach `& 7 == 0` that `FUN_c0504fc8` requires — so a client
can never be left un-quiesced across a collapse. (Alternatively, reset the shared
budget `DAT_c28602fc` at the start of the bring-up so a slow collapse cannot starve it.)

**Why not done:** this is a **baseband (`modem.bNN`) patch**. The project SOP treats
baseband changes as high-risk (MPSS RSA auth, segment-signing traps — see
`reference_mdt_segment_auth_trap.md`), and the shipped AP-side pin-hold already gives a
crash-free device (0 fatals / 1857 s). Documented here as the *true* root-fix candidate
rather than attempted.

---

## Verification / reproducibility

```bash
F="Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c"

# the assert helper + budget
sed -n '862146,862168p' "$F"

# the five-client quiesce (only in the bring-up path)
grep -n "ec320ba4\|ec320ba8\|ec320bac\|ec320bd8\|ec320be0" "$F"

# the budget counter: incremented 862157, tested 862161, reset ONLY 862803
grep -n "DAT_c28602fc" "$F"

# the collapse path (waits b94 / 0x404, no client words)
sed -n '862791,862900p' "$F"

# resolve the assert descriptor to its message
python3 scratch/a2_descr.py scratch/coredump_live/modem_coredump_up919.52.elf 0xc3c1f3a0
#   -> a2_power.c:1189  id=64  file='a2_power.c'  msg='A2 Assertion Failed'
```

**SOP statement.** Offline RE only; no baseband written. All addresses/line numbers are
from the decompiled stock HMU05 modem and the descriptor DB of live coredumps; the
one-way budget reset and the disjoint word sets were confirmed by exhaustive `grep` of
the five client words and `DAT_c28602fc` (no other writers). The causal step (§5) is
labelled a hypothesis, consistent with the two independent live results (the A/B
negative for the AP-side fix, and the `control=on` pin-hold eliminating the fatal).

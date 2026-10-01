# PRE-REGISTRATION — v12: instrument the RESPONDER side (`0xc03518c8`)

Written **before** the build and before the deploy. 2026-10-01.
Builds on ledger items 83/84/85/86. Same device, same reader family (`0xc1455000`).

## 1. The question this run must answer

Items 85/86 established that the state-20 transaction's reply at ~902 s is **LATE, not
LOST**, and that the 50 ms watchdog is only the **tightest tripwire** on an **ML1-wide
stall** (`≥ ~1.6 s`). They did **not** identify which side stops.

`0xc03518c8` is the **responder**: it takes an incoming ML1 message (`r17 = arg1`), builds a
0x28-byte reply in the ML1 instance buffer (`r20+0xde0`, `r20 = memw(0xc312d4d8)`), and
**sends** it via the same primitive the armer uses (`0xc02d26d0`). It is reached only from
**exactly three literal call sites** (verified: a full-text and a raw-byte scan of all 21
segments both return exactly these three; no indirect reference exists):

| # | site | return addr (r31) | r2 | r3 |
| :- | :-- | :-- | :-- | :-- |
| 1 | `0xc02a595c` (thin wrapper) | `0xc02a5968` | pass-through | `0` |
| 2 | `0xc03498d4` | `0xc03498e0` | `0` | `1` |
| 3 | `0xc0349dc8` | `0xc0349dd4` | `0xffffffff` | `0` |

**The discriminator:**

* **H1 — the responder stops being invoked.** The last RSP record precedes the last ARM
  record (the requester armed and nobody ever answered it).
* **H2 — the responder keeps being invoked but its reply does not land.** RSP records
  continue *past* the last ARM record.

## 2. The instrument

`modem.b16`:
* `0xc0326874` : `call 0xc02fda90` → `call 0xc003054c`  (**ARM** cave, unchanged from v9)
* `0xc033c0f4` : `call 0xc02fda90` → `call 0xc003054c`  (**ARM** cave, unchanged from v9)
* `0xc02a595c` : `call 0xc03518c8` → `call 0xc0030598`  (**RSP** cave, NEW)
* `0xc03498d4` : `call 0xc03518c8` → `call 0xc0030598`  (**RSP** cave, NEW)
* `0xc0349dc8` : `call 0xc03518c8` → `call 0xc0030598`  (**RSP** cave, NEW)

**NOT patched in v12:** the CB site (`0xc02d7bd0`) and the timeout site (`0xc02fbc40`) —
so v12 keeps the **stock 50 ms** deadline and the **stock dispatcher**. This is deliberate:
(a) it makes v12 a clean single-variable delta over v9's baseline regime; (b) the CB
evidence is already fully established (items 83/84/86) and is not needed here.

`modem.b05`: **ARM** cave at `0xc003054c` (76 B, byte-identical to v9), **RSP** cave at
`0xc0030598` (new; the 180 B NOP run ends at `0xc0030600`).

The RSP cave is entered by `call` (so `r31` = the hook site's return address = **which
caller**) and **jumps** to `0xc03518c8` with `r31` intact — the proven v9 pattern. It
clobbers only `r5..r8` (dead at a call site); `r0..r3` (the responder's arguments) are read
but not written.

## 3. Header / entry extension (backwards-compatible with v9)

```
SAVE 0xc1455000
  +0x00 magic      = 0xc1455000
  +0x04 count      (shared; slot = (count-1) & 0x7f)
  +0x08 arm_total
  +0x0c cb_total   (0 in v12 — the CB site is not patched)
  +0x10 rsp_total  (NEW)
RING entry (16 B at +0x40 + slot*16):
  +0x00 seq   ARM: seq            RSP: seq | 0x40000000
  +0x04 a     ARM: caller r31     RSP: caller r31 (0xc02a5968 / 0xc03498e0 / 0xc0349dd4)
  +0x08 b     ARM: r2 selector    RSP: r2 (the responder's arg2)
  +0x0c c     ARM: not written    RSP: r3 (the responder's arg3)
```

## 4. Pre-registered predictions (scored after the run)

| id | prediction | falsified by |
| :-- | :-- | :-- |
| **P-R1** | the instrument is resident and executing: `magic == 0xc1455000`, `arm_total > 0`, `rsp_total > 0`, and at least one RSP record is present | any of these false ⇒ the run is void, not a result |
| **P-R2** | the three caller return addresses appear, i.e. `a ∈ {0xc02a5968, 0xc03498e0, 0xc0349dd4}` for every RSP record | any other `a` ⇒ an unmodelled caller exists |
| **P-R3** | the RSP rate is **not** constant: the RSP records are **sparse or absent in the last stretch** before the fatal (the stall is visible as a gap) | RSP records continue at their normal rate **right up to the last ARM** ⇒ H2 |
| **P-R4** | **the discriminator**: the last RSP `seq` is **less than** the last ARM `seq` (H1) | last RSP `seq` ≥ last ARM `seq` (H2) |
| **P-R5** | the fatal is the stock signature `lte_ml1_common_timer.c:390` at ~902 s (v12 is the v9 regime) | any other signature / time ⇒ the regime moved and the comparison is void |

P-R3 and P-R4 are the **whole point**; P-R1/P-R2 are instrument-validity gates; P-R5 is a
regime gate. A run in which the modem **wedges** instead of fataling is **inconclusive**
for P-R4 (no terminal event to compare against) and must be reported as such.

## 5. Validation checks (V)

| id | check |
| :-- | :-- |
| V-R1 | `modem.b16` at each of the 3 RSP sites reads `call 0xc0030598`; at the 2 ARM sites reads `call 0xc003054c`; `0xc02d7bd0` and `0xc02fbc40` are **byte-identical to pristine** |
| V-R2 | the b05 RSP cave is exactly the assembled length and lies in `0xc0030598..0xc0030600`; the ARM cave is byte-identical to v9's |
| V-R3 | the b01 segment hashes are recomputed and re-verified after the edit |
| V-R4 | host md5 == device md5 for `modem.b16`, `modem.b05`, `modem.b01`, `modem.mdt` |

## 6. Scope / honesty notes

* This is a **probe, not a fix**. It adds no behaviour change beyond three `call` retargets.
* The run is expected to be the **idle (attached-but-no-flow)** Android regime, as in v10/v11.
  That regime is disclosed; the ARM/RSP comparison is internal to the same boot, so it does
  not depend on traffic.
* The RSP ring shares the v9 ring. If the responder runs at ≫ the ARM rate the ring will be
  RSP-dominated and the ARM history shortens; the **shared `count`** (recorded in every
  entry's `seq`) keeps the two orderable regardless.
* Reversible: v11 is backed up on-device before the v12 deploy; the v9/v10/v11 images are on
  the host.

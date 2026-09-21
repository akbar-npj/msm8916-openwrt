# Doc 163 — The ERR_FATAL descriptor carries an INLINE filename and matches the dmesg signature 11/11

**Date:** 2026-09-21
**Device:** Melbon HMU05 (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5, Linux 6.12.94
**Instrument:** the modem coredump (the only one — Doc 158 measured that the modem's memory is
unreadable from the AP by any route)
**Status:** measurement only. No firmware, driver or DTS change.

This document **corrects two corpus claims** and **closes the "is the `file:line` real?" question
from the firmware's own bytes**. It also records the preservation of a 36-dump coredump corpus
that was one write away from filling the overlay partition.

---

## 1. Why this matters

Two rules are in the corpus about the `file:line` in the dmesg line

```
qcom-q6v5-mss 4080000.remoteproc: fatal error received: <file>:<line>:
```

* Doc 149: *"the assert string is a mutable global and **must not classify a fatal**"* — derived
  from observing **three different signatures** all firing near ~900 s.
* Doc 162 refined that to: the three signatures have **measurably different distributions**, so the
  string *does* say which mechanism fired — but that was inferred from dmesg timings alone.

Neither had checked the string against **what the modem actually wrote into its own memory**. That
is what the coredump can settle, and it is decisive: the modem keeps a single "last fatal" record,
and if that record names the same file and line the dmesg line does, the string is a faithful
report of the fatal's site (and the ~900 s mystery is "one root cause, several assert sites", not
"the string is noise").

---

## 2. The record layout, corrected

The record lives at **ELF VA `0xC35B1280`** (`dump_va = elf_va − 0x39800000`). Dumped raw from run
8's `devcd3` (the `lte_ml1_sleepmgr_stm.c` fatal):

```
0xc35b1280  03 00 00 00 01 00 00 00 bc be c0 c3 03 00 00 00
0xc35b1290  d6 0f 00 00 dc 09 be 78 fc 8d 12 01 00 00 00 00
0xc35b12a0  00 00 00 00 6c 74 65 5f 6d 6c 31 5f 73 6c 65 65
0xc35b12b0  70 6d 67 72 5f 73 74 6d 2e 63 00 00 00 00 00 00
```

| offset | size | field |
| :-- | :-- | :-- |
| `+0x00` | u32 | `3` |
| `+0x04` | u32 | `1` |
| `+0x08` | u32 | pointer — per-fatal, varies (`devcd2` `0xC3C0BBA4`, `devcd3` `0xC3C0BEBC`) |
| `+0x0C` | u32 | `3` |
| `+0x10` | **u16** | **line** — here `0x0FD6` = **4054** |
| `+0x14` | u32 | **A** — runtime-varying |
| `+0x18` | u32 | **B** — runtime-varying; advances **+11/+12 per fatal** |
| `+0x1C` | u32 | `0` |
| `+0x20` | u32 | `0` |
| `+0x24` | **char[]** | **filename**, NUL-terminated, **INLINE** — here `lte_ml1_sleepmgr_stm.c` |

**The filename is plaintext and inline at `+0x24`.** The corpus previously recorded the descriptor
as *"obfuscated"* with *"no 16-byte filename table"* — true of the **ELF file on disk**, but the
**runtime copy in a coredump is plaintext**, and the earlier reader followed the `+0x08` pointer
instead of reading `+0x24`. The `+0x08` pointer leads somewhere else entirely (it points at
`0xC3C0BF84`, a table of unrelated 8-byte pairs).

Tool: `evidence/163_errfatal_descriptor/errfatal_descriptor.py` (re-runnable; prints file, line, A,
B and the rpm-LPR counter for every dump given).

---

## 3. The result: 11 dumps, 11 matches

Two independent boots. In every case the coredump's `file:line` **is exactly** the `file:line` in
that fatal's dmesg line.

### Run 8 (soak814, this boot — 5 fatals, 5 dumps)

| # | AP uptime | modem uptime | dmesg signature | coredump `file` | coredump `line` | B | match |
| --: | --: | --: | :-- | :-- | --: | :-- | :-- |
| 1 | 912.915 | 900.965 | `lte_ml1_sleepmgr_stm.c:4054` | `lte_ml1_sleepmgr_stm.c` | 4054 | `0x01128DE5` | ✓ |
| 2 | 1855.593 | 941.288 | `a2_power.c:1189` | `a2_power.c` | 1189 | `0x01128DF1` | ✓ |
| 3 | 2757.618 | 900.662 | `lte_ml1_sleepmgr_stm.c:4054` | `lte_ml1_sleepmgr_stm.c` | 4054 | `0x01128DFC` | ✓ |
| 4 | 3699.367 | 940.238 | `a2_power.c:1189` | `a2_power.c` | 1189 | `0x01128E07` | ✓ |
| 5 | 4647.902 | 947.161 | `a2_power.c:1189` | `a2_power.c` | 1189 | `0x01128E13` | ✓ |

`B` advances by **+11, +11, +11, +12** across the five — the "11 per period" property (Doc 152),
re-confirmed here on a fresh boot.

### The earlier boot (the 6 dumps already in `scratch/coredump_live/`)

All six decode to `lte_ml1_common_timer.c` line `390`, and that boot's dmesg table (Doc 149 §2)
gives `lte_ml1_common_timer.c:390` for every one of its first five fatals. The six dumps cover
**four distinct fatals** (fatals #1 and #2 each produced two dumps ~4 s apart, which is the
watcher capturing twice, not two crashes).

### What this establishes

1. **The descriptor is per-fatal, not stale.** It is a single global slot (so it holds only the
   most recent fatal), but it is **rewritten on each fatal** and it holds **that** fatal's site.
2. **The dmesg `file:line` is a faithful report.** It is not a leftover from a previous crash.
3. Therefore Doc 149's *"must not classify a fatal by its `file:line`"* is **correct as a warning
   and wrong as a conclusion**. The string does not identify the *root cause* — three different
   sites can all trip at ~900 s — but it does identify **which site tripped**. The reconciliation
   is the modem RE's own §5.2: one root event (the MCPM `system_sleep_check` Q6-PC-voting failure)
   at different assert sites. **Doc 162's revision stands, and is now confirmed from the firmware's
   own bytes rather than from timing alone.**

---

## 4. Consequence for the ~901 s question

Doc 162 §3 recorded that the deterministic ~900.8 s fatal fired on only 2 of run 8's 5 modem boots,
with an `a2_power.c:1189` fatal at 940.2–947.2 s on the other 3. It left open whether the
*reported* signature might simply be lagging or leading by one event.

**This document closes that reading.** §3 above shows the firmware's own record names
`lte_ml1_sleepmgr_stm.c` on the boots where dmesg says sleepmgr and `a2_power.c` on the boots where
dmesg says a2_power — 5/5. The two are genuinely different assert sites, so the alternation-then-
break is a real property of the modem's behaviour, not a reporting artefact.

The live hypothesis is therefore the other one: **two competing mechanisms whose relative phase
depends on the state the SSR recovery leaves behind.** Still unexplained.

---

## 5. The 36-dump corpus was one write from destroying itself

While preparing this, `/overlay` was found at **100 % with 9.2 MB free**, holding
`/overlay/coredump_live/` — **36 dumps × ~85 MB = 2.9 GB** on a 3.2 GB partition. The coredump
watcher (Doc 153) is autostarting and writes a **new 85 MB dump on every fatal**, so the next fatal
would have failed to write, silently losing the capture the watcher exists to make.

All 36 dumps were copied to `scratch/coredump_live_full/coredump_live/` and verified intact
(ELF magic `\x7fELF`, `du` shows full allocation, not sparse). Only 6 of them were in the repo
before this. **This is the raw evidence for every future coredump analysis and it should not be
deleted from the host.**

**Note for the next session:** `/overlay` is *still* nearly full. Freeing
`/overlay/coredump_live/` is required before the next soak, or the watcher will lose its captures.

---

## 6. What is NOT claimed

* **Not** that the `file:line` identifies the root cause. It identifies the *site*. The root cause
  is shared.
* **Not** that `lte_ml1_sleepmgr_stm.c:4054` and `a2_power.c:1189` are different timers. They are
  different *sites*; whether one timer trips both is exactly the open question.
* **Not** that word `B` is understood. It advances 11–12 per fatal and does not reset across a
  reboot (Doc 152); its meaning is unknown.
* **Not** that the older boot's 6 dumps are 6 fatals. They are 4 fatals; two were captured twice.
* **Not** that the run-8 dumps were taken at the same instant as the fatal. The watcher captures
  ~1–9 s after (e.g. fatal at 4647.90, dump named 4649.09), so the record is the post-fatal state.

---

## 7. Next steps

1. **Free `/overlay/coredump_live/`** before the next soak (see §5).
2. Use the descriptor to **label every future fatal from its coredump**, not from dmesg — it is the
   same information, but it survives a lost dmesg.
3. Compare the run-8 dumps pairwise (sleepmgr vs a2_power boots) for what *else* differs. §3
   establishes the sites differ; the mechanism question is what state differs.

---

## 8. Artifacts

| path | contents |
| :-- | :-- |
| `evidence/163_errfatal_descriptor/errfatal_descriptor.py` | the decoder — file, line, A, B, LPR counter for any dump |
| `scratch/coredump_live_full/coredump_live/` | **all 36 dumps**, 2.9 GB (gitignored by size; the authoritative raw copy) |
| `scratch/coredump_live/` | the 6 dumps that were already tracked-adjacent, plus `fatal_fields.py` / `vadump.py` |

---

## 9. One line

**The ERR_FATAL coredump descriptor holds a plaintext, inline filename at `+0x24` and its
`file:line` matches that fatal's dmesg signature in 11/11 dumps across two boots — so the reported
`file:line` is a faithful report of the assert *site* (Doc 149's "never classify a fatal by its
`file:line`" is a correct warning and a wrong conclusion), which means Doc 162's
suppressed-~901 s-fatal finding is real behaviour and not a reporting artefact.**

---

## 10. Provenance and SOP compliance

**SOP steps taken:**

* **Ground truth before interpretation.** The descriptor was read from the modem's own dumped
  memory and compared against the dmesg line for the *same* fatal — the comparison is against an
  independent record of the same event, not against a narrative.
* **Comparative protocol.** Two independent boots, two different signature families
  (`common_timer.c:390` and `sleepmgr`/`a2_power`), 11 dumps, 11 matches. The negative control is
  built in: if the descriptor were a stale global, the older boot's dumps would have shown a
  *mixture* of signatures. They show one, matching that boot's dmesg.
* **A message is not a cause until its rate is known** (Doc 159's rule). §3 counts dumps per fatal
  rather than assuming one dump = one crash, which is how the "6 dumps = 4 fatals" correction was
  caught.
* **Measurement artefacts are first-class.** The dump is taken 1–9 s *after* the fatal; that is
  stated in §6 rather than glossed.
* **Corrections are recorded, not edited away.** Doc 162's superseded "2-cycle" reading is left
  visible in Doc 162 §3, marked as corrected.

**SOP steps skipped, and why:**

* **No Android A/B.** The claim is about the modem's own memory format, which is
  firmware-internal; the firmware segments are already proven byte-identical (Doc 30/`Stock_Android_Live/01`).
  The one-device constraint makes an Android reflash costly and it would not add evidence here.
* **No live modem read.** Doc 158 measured it impossible (TrustZone).
* **No firmware patch.** Nothing here needs one.

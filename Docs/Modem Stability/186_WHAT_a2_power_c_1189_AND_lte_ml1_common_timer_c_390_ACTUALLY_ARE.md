# 186 — What `a2_power.c:1189` and `lte_ml1_common_timer.c:390` actually are

**Date:** 2026-09-23
**Question asked:** *"why not inspect modem about what these `a2_power.c:1189` or
`lte_ml1_common_timer.c:390` actually are, what they need, why it happens"*
**Answer in one line:** they are **not two assert sites in two source files** — they are two
*labels* the modem stamps into **one mutable global record**, and the two are not even produced by
the same mechanism: `a2_power.c:1189` is a slot in the firmware's registered **line-table**
mechanism, `lte_ml1_common_timer.c:390` is in **none** of the seven registered tables.
**Firmware:** stock HMU05 `HIMI_U01_MODEM_V1.0` / `MPSS.DPM.1.0.C7`, byte-identical to Android
(`project_modem_firmware_identical_proof`).
**Artifacts:** `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` (50,049,024 B,
ELF32 `elf32-hexagon`); `scratch/firmware/modem.asm` (202,061,320 B, `llvm-objdump -d
--triple=hexagon`); **36 modem coredumps** in `scratch/coredump_live_full/coredump_live/`
(85,398,475 B each, captured 2026-09-21).
**Nothing in the firmware was modified.** This is a read-only analysis.

---

## 1. SOP Compliance Statement

Per the standing rule for this numbered series, the Dual-Firmware Comparative Workflow steps
(`133_…` §1) taken and skipped:

| SOP step | Status in this session |
|---|---|
| backup | **n/a** — no write of any kind. No image was copied, patched, or re-signed. |
| ground-truth verification against stock HMU05 | **PERFORMED.** Every structural claim below is read out of the *stock* `modem.elf` **and** cross-checked against a *runtime* copy of the same structures inside a real HMU05 coredump (two independent sources, two independent parsers). |
| reconcile transport/config | **SKIPPED, deliberately.** This document does not touch the transport, the DT, or the AP config; it is a firmware-identity question only. |
| surgical Hexagon patching + re-signing | **SKIPPED, deliberately.** The corpus's constraint is that the modem is not binary-patched. This document locates and describes; it does not propose a patch. |
| live empirical validation | **PERFORMED** for the label census (36 real coredumps from real crashes). **NOT performed** for the read-path of the line tables — see §8. |

**Errors caught and corrected in this session** (recorded rather than hidden, per `134_…` §2):

1. A first parse of the line-table initialisers walked **overlapping function windows** and
   reported "317 stores at offsets `0x0..0x9e0`" for *seven different functions*. That is
   impossible (seven distinct tables of identical size and offset range) and was a parser
   artefact. **Fixed** by abandoning the initialiser parse and reading the tables **out of a live
   coredump** instead.
2. That same broken parse put `1189` at offset `0x240` (slot 72, VA `0xc3a44f90`). The live read
   puts it at **slot 74, VA `0xc3a44fa0`**. The corpus's pre-existing address was **right** and my
   parse was **wrong**. Do not trust an initialiser-window parse over a live read.
3. A search for "descriptor = filename literal − `0x14`" returned a **filename string pool**
   (`a2_ul_per.c\0`, `a2_power.c\0`, … packed at 16-byte stride), not descriptors. The `−0x14`
   heuristic is invalid; the filename literals are pooled by source file and are **not** adjacent
   to their descriptors.

---

## 2. The short answer

The AP kernel prints `fatal error received: <file>:<line>:` from SMEM `SMEM_SSR_REASON_MSS0`, which
the modem writes when it takes its `ERR_FATAL` path. That path formats a **single global record** in
BSS (`FUN_c0879150` @ `0xc0879150`; 17,708 direct `call` sites, each passing a *static* descriptor).

So `a2_power.c:1189` is **not** "a bug in `a2_power.c` at line 1189". It is **the last label written
into that one record**. Doc 163 already proved the record is rewritten per fatal and its
`file:line` matches that fatal's dmesg line **11/11 across two boots**
(`reference_err_fatal_file_line_decode`). This session adds a **36-dump census** that shows the
consequence directly (§3), and then locates the *storage* behind the line number (§5, §6).

**Why it happens** is unchanged and is upstream of the label: the Q6's Low-Power-Resource chain
never completes because `rpm.sync` spins in an unbounded flush loop, so `q6pcvote`
(`lpr_get("rpm")+0x18`) stops advancing, and MCPM's `system_sleep_check` (`FUN_c0ce7fe0`) fires the
`HARD_FAIL … sleep count not incrmnt` / `UT detects Q6 PC Voting failure` fatal after 400 cycles
(`900S_CRASH_LPR_FRAMEWORK_RE.md` §6; `project_root_cause_q6pc_vote_failure`). The label is
whatever code happened to be running when that fired. **Neither label "needs" anything** — the
*event* needs the LPR chain to complete.

---

## 3. The label is mutable: a census of 36 coredumps

Decoded with `Docs/Modem Stability/evidence/163_errfatal_descriptor/errfatal_descriptor.py` over all
36 dumps in `scratch/coredump_live_full/coredump_live/`.

| label | dumps | AP uptimes (s) |
|---|---:|---|
| `lte_ml1_sleepmgr_stm.c:4054` | **18** | 913.64, 915.55, 1328.04, 1824.57, 1827.23, 2728.06, 2760.09, 3810.44, 4792.50, 5696.69, 9143.88, 10044.62, 10946.58, 11849.71, 12753.29, 13656.59, 14559.07, 15982.84 |
| `a2_power.c:1189` | **11** | 426.20, 923.12, 923.71, 1406.94, 1857.52, 2906.94, 3701.47, 3888.64, 4649.09, 6619.26, 8238.92 |
| `lte_ml1_common_timer.c:390` | **3** | 915.42, 915.44, 1818.92 |
| `a2_power.c:2949` | **3** | 2389.74, 7196.86, 15079.78 |
| `a2_taskq.c:759` | **1** | 2275.74 |

**Five distinct labels over 36 dumps, one record.** That is the whole point: the record has no
memory of which site fired last, so the label sequence is a property of *what was running*, not of a
per-site counter.

**Caveat — 36 dumps is not 36 independent fatals.** Several dumps are sub-second apart
(`915.42`/`915.44` = 0.02 s; `915.42`→`915.55` = 0.13 s), i.e. repeat reads of the same crash. Treat
the counts as an *upper* bound on distinct fatals. The last 13 dumps (`5696.69` → `15982.84`,
10,286 s) are the clean steady idle series and are internally consistent.

**A second, independent observation — the label is not the only mutable thing.** The record's
`+0x18` word (the corpus calls it **B**) advances **+11 per fatal** across that whole clean series
(10/10 intervals, `0x01128d39` → `0x01128db6`, Δ = 125 over 10,286.15 s = **1 per 82.3 s**). It does
**not** reset per fatal, so it is not per-fatal state. It is also **not** a clean clock: in the early
dumps B oscillates inside a band of ~387 counts (`0x01128c90`–`0x01128e13`) and is **not
monotonic** (`915.42`→`0x01128cb9`, `915.44`→`0x01128c90`, a *drop* of 41). Semantics unresolved;
recorded so nobody re-derives "B is a counter" from the clean series alone.

---

## 4. The record layout (confirmed, for reference)

ELF VA `0xC35B1280`; coredump VA = ELF VA − `0x39800000` (so dump `0x89db1280`).

```
+0x00  u32  3
+0x04  u32  1
+0x08  u32  ptr          per-fatal; points at a {u32 size; u32 addr} region table, NOT the site
+0x0C  u32  3
+0x10  u16  LINE         <-- the number in the dmesg message
+0x14  u32  A            runtime-varying, uninterpreted
+0x18  u32  B            see §3
+0x1C  u32  0
+0x20  u32  0
+0x24  char FILENAME[]   NUL-terminated, INLINE
```

The formatter (`0xc08794e0`) reads `line = zxth(memw(desc+0x00))` and `filename = desc+0x14`, so the
record is a `{line; A; B; …; name}` descriptor sitting `0x10` bytes into a wrapper. **The `+0x08`
pointer is a decoy** — it varies per fatal but points at a coredump region table, not at the assert
site; following it is what produced the corpus's old "descriptors are obfuscated" conclusion.

---

## 5. Where the line number is *stored*: seven registered 317-slot line tables

This is the new structural result.

Reading the live coredump at the address the corpus's own verified control named
(`DAT_c3a44fa0` = `0x4a5` = 1189) shows a plaintext, fully populated array. It is one of **seven
contiguous tables**, each **317 slots × 8 bytes** (`stride 0x9e8`), holding
`{u32 line; u32 flag}`:

| k | table VA | slots with a plausible line | min | max | contains |
|---:|---|---:|---:|---:|---|
| 0 | `0xc3a44d50` | 309 | 1143 | 27540 | **1189** |
| 1 | `0xc3a45738` | 308 | 3850 | 27541 | **4054**, **4014** |
| 2 | `0xc3a46120` | 308 | 519 | 27539 | — |
| 3 | `0xc3a46b08` | 308 | 1814 | 27542 | **2949** |
| 4 | `0xc3a474f0` | 286 | 1794 | 27545 | — |
| 5 | `0xc3a47ed8` | 269 | 2845 | 27544 | — |
| 6 | `0xc3a488c0` | 289 | 3650 | 27543 | — |

(`k = -3…-1` and `k = 7…39` on the same `0x9e8` lattice are zero or hold non-line data — one
preceding block has 159 slots whose *second* word is non-zero and whose first is zero. Only **seven**
tables carry source lines.)

**Slot identities** (live read):

| line | file (per the record) | table | slot | slot VA |
|---:|---|---|---:|---|
| 1189 | `a2_power.c` | `0xc3a44d50` | **74** | **`0xc3a44fa0`** |
| 4054 | `lte_ml1_sleepmgr_stm.c` | `0xc3a45738` | **58** | **`0xc3a45908`** |
| 4014 | `lte_ml1_rfmgr_trm.c` | `0xc3a45738` | 73 | `0xc3a45980` |
| 2949 | `a2_power.c` | `0xc3a46b08` | **111** | `0xc3a46e80` |

The first two rows **exactly reproduce the corpus's two pre-existing "verified controls"**
(`DAT_c3a44fa0` = 1189, `DAT_c3a45908` = 4054) — independently, from a live coredump rather than
from an inference off the line-table initialiser.

**The tables are per-*component*, not per-source-file.** Table `0xc3a45738` contains **4014 from
`lte_ml1_rfmgr_trm.c` and 4054 from `lte_ml1_sleepmgr_stm.c`** — two different `.c` files in one
table. Conversely 1189 and 2949, both reported as `a2_power.c`, sit in **two different** tables
(`0xc3a44d50` and `0xc3a46b08`) whose contents are disjoint. So the table index is **not** a file
id; whatever the seven components are, they do not map 1:1 onto the filenames in the record.

**The registry.** The only place any of these table addresses appears in the whole coredump is a
7-entry pointer array at **ELF VA `0xc1e3cb30`**:

```
0xc1e3cb30  0xc3a46120   (k=2)
0xc1e3cb34  0xc3a44d50   (k=0)   <- holds 1189
0xc1e3cb38  0xc3a47ed8   (k=5)
0xc1e3cb3c  0xc3a45738   (k=1)   <- holds 4054, 4014
0xc1e3cb40  0xc3a46b08   (k=3)   <- holds 2949
0xc1e3cb44  0xc3a488c0   (k=6)
0xc1e3cb48  0xc3a474f0   (k=4)
0xc1e3cb4c  0x00000000
```

and immediately before it, **six function pointers** (`0xc1e3cb18..0xc1e3cb28`:
`0xc0e91820`, `0xc0e91cc4`, `0xc0e91dd4`, `0xc0e91854`, `0xc0e91900`, then `0`).

**Initialisers (static side).** Each table has exactly one code reference — its own initialiser:

* `FUN_c0e93d94` @ `0xc0e93d94`: `r1 = #0x13d` (317), `r0 = ##0xc3a44d50`, then a loop zeroing the
  `+4` word of every slot, then ~317 `memw(r0+#<8k>) = rN` stores carrying the line numbers.
* `FUN_c0e983a4` @ `0xc0e983a4`: same shape, `r0 = ##0xc3a46b08`.

Both are called from one dispatcher (`c0e92db4`–`c0e92dd0`) guarded by an "already initialised" byte
at `r24+8`, alongside six sibling initialisers — i.e. **seven per-component table initialisers, run
once at boot.**

---

## 6. Where the two requested labels sit — and where they do not

**`a2_power.c:1189` is in the line-table mechanism.** Slot 74 of registered table `0xc3a44d50`,
VA `0xc3a44fa0`, `flag = 0`.

**`lte_ml1_common_timer.c:390` is in none of the seven tables.** Nor is `a2_taskq.c:759`. Both were
searched for by exact value across all seven tables in the live coredump: absent.

This matches the corpus's own note that "the family is partial — other observed lines (390, 2192,
712, 351) are absent from it". It is now specific: **4 of the 5 observed signatures are in the
tables (1189, 2949, 4014, 4054); 390 and 759 are not.** So the two labels the question named are
**not even the same class of report**, and "the `file:line` is stored in a per-file line table" is
**not** a general rule for this firmware.

**The filename has a different provenance again.** The runtime pointer to the `a2_power.c` literal
is `0xc17e63d8`, and it appears **exactly 35 times in the entire coredump** — all 35 inside the
16-byte-entry `a2_power.c` **MSG table** at `0xc15040c8..0xc15042e8` (`{fmt_ptr; file_ptr;
(lvl<<16|line); flags}`). Those 35 entries carry the file's *log* lines (102, 1382, 1470, …, 4910,
4921) and **1189 is not among them**. No code anywhere in `modem.asm` loads the `a2_power.c` literal
directly. So the *line* and the *filename* come from two different structures.

---

## 7. Why it fires, and what the sites "need"

Unchanged from the firmware RE, restated because it is the actual answer to "why":

1. The Q6 enters sleep through the LPR chain
   `npa_scheduler → CLM → l2 → tcm → cxo → rpm → mcpm_lpr → cpu_vdd`
   (`900S_CRASH_LPR_FRAMEWORK_RE.md` §6.1; the order is data, at `0xc1d464f8`).
2. **`rpm.sync`** (`enter = 0xc08bebd0`) calls `FUN_c08b9690(1)` then `FUN_c08b9690(2)`, whose two
   flush loops (`0xc08b96f4`, `0xc08b988c`) have **no retry counter and no timeout**. A stalled RPM
   parks the Q6 there forever.
3. `q6pcvote` = `lpr_get("rpm") + 0x18` therefore never advances, so the snapshot written at each
   sleep entry (`DAT_c30fd9a8[tech]`) is never exceeded.
4. MCPM's `system_sleep_check` (`FUN_c0ce7fe0` @ `0xc0ce7fe0`) tests
   `if (current <= snapshot)` after both guards pass 400 (`0xc0ce81d4`, `cmp.gtu`), logs
   `HARD_FAIL #%u[%u] sleep count not incrmnt …` (`0xc1a77422`) and
   `UT detects Q6 PC Voting failure` (`0xc1a774a9`), and calls the non-returning `FUN_c0879150`.
5. **That** writes the label. The label is whatever descriptor the currently-executing code handed
   it — which is why the same physical fault prints `a2_power.c:1189`, `lte_ml1_sleepmgr_stm.c:4054`,
   `lte_ml1_common_timer.c:390`, `a2_power.c:2949` or `a2_taskq.c:759` on different boots.

**"What they need"** — nothing site-specific. The individual sites are ordinary asserts; they are
*downstream* consumers of one upstream failure. There is no per-site condition to satisfy.

**Regime matters, and it is the one place the two labels genuinely differ.** Doc 149's E1 A/B/A
experiment: idle → `lte_ml1_common_timer.c:390` ×5 at a 902.267 s modem-uptime period (112 ppm);
1 Hz ping → `a2_power.c:1189` ×2 at 895.490 s then 932.855 s; idle again →
`lte_ml1_sleepmgr_stm.c:4054` ×1 at 900.864 s. So `1189` is the **activity-correlated** label and
`390` is the **idle deterministic-clock** label, and they **substitute** for each other. Quoting a
period for one while running the other's traffic regime is the error the corpus has already
retracted once.

**The AP-side asymmetry stands.** The firmware is byte-identical to the Android build, where it
never faults, so the trigger is AP-side (`project_modem_firmware_identical_proof`,
`900S_CRASH_LPR_FRAMEWORK_RE.md` §6.6 branch (b)).

---

## 8. What this does NOT establish

* **The read path was not located.** No data pointer to any line table exists except the registry at
  `0xc1e3cb30`, and no code reference to any table base exists except its own initialiser. The
  link "line table slot → the line written into the ERR_FATAL record" is a **strong association**
  (4 of 5 observed signatures sit at their exact lines in these tables) but it is **not proven**.
  Do not cite it as a mechanism yet.
* **The seven components were not identified.** The tables are per-component, not per-file (§5), and
  no filename is stored anywhere near the registry (searched ±`0x2000`). Mapping component → source
  file needs the registration code, which was not found.
* **The `flag` word is unexplained.** It is `0` in every slot of all seven tables. It is not a
  hit-counter and not a fired-bit for these dumps.
* **Why `390` and `759` are outside the tables is unknown.** It may be a second, unregistered
  mechanism, or a table the coredump did not contain. Not tested.
* **`A` and `B` remain uninterpreted.** §3 characterises B's *behaviour*; it does not explain it.
* **Nothing here changes the stability picture.** The label is not the root cause, and this document
  does not propose, test, or endorse a firmware patch.

---

## 9. Next actions

1. **Locate the read path.** Find the code that indexes the registry at `0xc1e3cb30` and reads
   `{line; flag}` — that is the one missing link between §5 and §7, and it would also give the
   component→file mapping for free.
2. **Test the "second mechanism" hypothesis** for `390`/`759` by searching the coredump for their
   line numbers in any table-shaped structure, rather than only on the `0x9e8` lattice.
3. **Re-run the census with de-duplication.** 36 dumps ≠ 36 fatals; pair dumps that are < 1 s apart
   and report distinct-fatals-per-label before any rate is quoted.
4. **Do not re-open "which site is worse".** The A/B SSR-powerup outcome is a correlate that Doc 183
   already collapsed (Fisher p = 0.153846) — see `project_ssr_powerup_outcome_by_signature`.
5. Unrelated and still blocking the soak: patch 825 and the gated `LOG_LEVEL` are **shadowed by
   `/overlay`** and are not live (Doc 185 §8 step 2; `project_bearer_rebuild_is_the_data_stall`).

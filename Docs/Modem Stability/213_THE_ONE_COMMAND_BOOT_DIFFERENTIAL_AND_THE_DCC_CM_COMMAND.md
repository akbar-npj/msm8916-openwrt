# 213 — THE ONE-COMMAND BOOT DIFFERENTIAL: UZ801's SECOND CM→MMOC COMMAND IS `OPRT_MODE_CHGD` WHERE STOCK's IS `SUBSCRIPTION_CHGD`, AND THAT ALONE SETS `Prot_state [MAIN] 7(OFFLINE)`

**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — §3 (Achieved vs Expected) and §7 item 18.
**Status:** RESULTS
**⚠ CORRECTED BY DOC 214 (2026-09-27) — read `214_THE_DCC_CARRIER_CONFIRMED_AND_THE_SEG26_MMOC_STRINGS.md`
alongside this doc. Three corrections, none of which changes the headline finding:**
* **§6.3's reading of `u` is RETRACTED.** `u` is a **CM command id**, not the modem's uptime (stock runs
  1…238; UZ801's *boot* capture reports **0 for every alloc**; UZ801's *live* capture 131…830).
* **§6.3's "0 allocs on both UZ801 boots" is capture-specific.** Those are *boot* captures; the live UZ801
  capture allocates `tsk=dcc` commands. The `dcc`↔AP-request link is now **proven with a negative control**,
  and the UZ801 `dcc` lifetime is **0.352 ms vs stock's 9.180 ms (26.1×)**.
* **§10's verdict is re-scoped.** The MMOC debug strings are not absent from the firmware — they live in
  **ELF segment 26, whose `filesz` is 0** (they *are* in every coredump; coredump VA + `0x39800000` = ELF VA).
  Route (a) is blocked by a **fixable input defect**, not by missing data.
* **§3.3's "the command id is the variable" holds only for the bring-up slot.** Stock *also* emits
  `Recvd command 2(OPRT_MODE_CHGD)` on the AP path — the id is identical; what differs is the **target
  op-mode in the payload** (`2(ONLINE)` vs `3(OFFLINE)`) and the actor. See Doc 214 §7.1.

**Supersedes nothing; extends 211 + 212.** Doc 212 established the event-anchored differential and the
`OPRT_MODE_CHGD` census. This doc answers the question Doc 212 left open — *what actually decides
ONLINE vs OFFLINE* — by finding that the two firmwares' boot bring-up differs in **exactly one
command id**, and it settles the queued `PROT_GEN_CMD` cause-vs-consequence question.

---

## 1. SOP compliance and Achieved vs Expected

### 1.1 SOP-compliance statement (per `197 §1`, modelled on `194 §1` / `133 §1`)

| Step | Taken? | Detail |
| :-- | :-- | :-- |
| 1. Backup | **YES (prior session)** | `scratch/doc211/restore_uz801_v19.sh` + the v19 set verified 68/68 on the device; the bootloop guard (`modem-guard`) armed. No deployment happened in this session, so no new backup was required. |
| 2. Ground-truth verification against stock HMU05 | **YES** | Every claim here is a **matched-instrument** comparison: `stock_boot_240.bin` and `uz801_v19_boot_240.bin` carry the **same** `script_md5 = 9cd993a7f214b6847e92a7c21ca8c8c8`, the same `DIAGBOOT_SECS=240`, the same `sweep=64`, and both are cold boots (`start_uptime` 10.45 s / 10.55 s). `stock_boot_211.bin` is a third independent stock boot. |
| 3. Reconcile transport/config | **YES** | The parser is the Doc-211-fixed `scratch/f3parse.py`, md5 `9f9d6edf15d64faf3b47897f7838c9ac` (asserted before use). Every interleaving is quoted **within a single `code`** (Doc 211 §: `ts` is per-`code`). |
| 4. Surgical Hexagon patching + re-signing | **NOT TAKEN — deliberately** | This doc is **diagnostic only**. The differential it finds is a **command id chosen by CM at runtime**, not a byte in the image; there is nothing to patch until §12 step 2 identifies the emitter. Per `feedback_operating_rules.md` rule 1, no blind baseband patch was attempted. |
| 5. Live empirical validation | **PARTIAL** | Six independent captures (3 stock, 2 UZ801, 1 live-stock-online), one of them 94.6 MB / 33 743 messages. **No new device run this session** — the device is unchanged (see §11.4). |

**Steps skipped and why:** step 4, because the finding is a runtime decision, not a static constant.
**No `sync` was needed** — nothing was written to the device or to `/overlay`.

### 1.2 Achieved vs Expected

| # | Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- | :-- |
| 1 | Answer the queued question: is `PROT_GEN_CMD` a **cause** or a **consequence** of the online state? | One of the two, with a measurement that separates them | `live_stock_online.bin` — a stock modem that **is** `ONLINE_GWL` — emits `PROT_GEN_CMD` **periodically at 6 144 4xx ticks ≈ 30.0 s** and **0** `OPRT_MODE_CHGD` ⇒ it is a **routine 30 s protocol poll gated on the protocol being active** | **MET — consequence** |
| 2 | Sharpen the ONLINE-vs-OFFLINE decision to a single observable | A discrete, countable differential, not a window statistic | The boot bring-up differs in **exactly one command id**: stock's 2nd CM→MMOC command is `Recvd command 0(SUBSCRIPTION_CHGD)`, UZ801's is `Recvd command 2(OPRT_MODE_CHGD)`; the transaction **bodies are identical** and only UZ801's terminal `Prot_state [MAIN]` becomes `7(OFFLINE)` | **MET** |
| 3 | Find the upstream carrier of the AP's online request | A CM command that exists in the online capture and not in the offline ones | `CMD alloc u=17, tsk=dcc` — present **only** in `hmu05_stock_boot.bin` (the only capture that reaches `ONLINE_GWL`), allocated at modem uptime 17 s and freed after the transition; **0 allocs** in both 240 s stock boots and in both UZ801 boots | **MET (n=1 — see §6.3 caveat)** |
| 4 | Verify the Doc 212 claim that `CM->MCS: op_mode` is identical on both stacks | Identical on both | 10 → 11 on **all four** boot captures; the earlier "UZ801-only" reading was an artefact of `f3clean.py` deleting the stock-long boot chunk | **MET (Doc 212 re-confirmed, and the trap recorded)** |
| 5 | Establish whether `scratch/doc210/boot_210_canon.bin` is stock or UZ801 | A definitive identity | **UZ801** — fingerprint `mcfg_uim.c` 9 / `uim.c` 1 / `uimsub_manager.c` 9 / `uimgen.c` 1 / `dcc` 8 / `pbm` 2 / `cmss.c` 1, versus stock's `mcfg_uim.c` 0 / `uim.c` 0 / `dcc` 0 / `cmss.c` 18 | **MET** |
| 6 | Find the trigger of stock's **second** bring-up command | A named F3 event immediately upstream | `qmi_nas_mmgsdi.c` `new ef spn size: 17 old cached size: 0 session: 0` — 444 ticks (2.17 ms) before the second `cmmsc_auto` (mask 544), which is 12 ticks (58.6 µs) before the second `SUBSCRIPTION_CHGD`. **The identical EF_SPN record occurs on UZ801 at the same ~1.5 s offset and triggers nothing.** | **MET** |
| 7 | Locate the MMOC decision site statically | A descriptor → function mapping | **NOT MET** — re-confirmed blocked: the MMOC debug format strings (`Recvd command %d(%s)`, `Trans_state %d(%s)`, …) are **not present in `modem.elf` at all** (0 hits, while the control `old_op_mode` is present at VA `0xc4563df2`), and the decompile materialises **zero** `0xc45xxxxx` addresses (387 743 `DAT_` refs, none in phdr[25]) | **NOT MET — route (a) stays blocked (§10)** |

---

## 2. The headline table — the complete census over six captures

Every column is a count of F3 records. All six captures were parsed with the same parser
(`f3parse.py` md5 `9f9d6edf15d64faf3b47897f7838c9ac`).

| capture | `SUBSCRIPTION_CHGD` | `OPRT_MODE_CHGD` | `PROT_GEN_CMD` | `DUAL_STANDBY_CHGD` | `Prot_state [MAIN] 7(OFFLINE)` | `Prot_state [MAIN] 5(ONLINE_GWL)` | `CMD alloc tsk=dcc` | `CMD free tsk=dcc` | `cmmsc_auto` runs |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| `hmu05_stock_boot.bin` (STOCK-LONG) | 1 | **1** | 15 | 0 | **0** | **54** | **1** | 1 | 3 |
| `stock_boot_240.bin` (STOCK-240) | **2** | 0 | 0 | 1 | **0** | 0 | 0 | 0 | 2 |
| `stock_boot_211.bin` (STOCK-211) | **2** | 0 | 0 | 1 | **0** | 0 | 0 | 0 | 2 |
| `doc210/boot_210_canon.bin` (UZ801) | 1 | **1** | 0 | 1 | **26** | 0 | 0 | **8** | 1 |
| `uz801_v19_boot_240.bin` (UZ801) | 1 | **1** | 0 | 1 | **26** | 0 | 0 | **8** | 1 |
| `live_stock_online.bin` (LIVE-ONLINE) | 0 | 0 | 6 | 0 | **0** | **24** | 0 | 0 | 0 |

**The four things this table settles.**

1. **`Prot_state [MAIN] 7(OFFLINE)` is emitted 26× on UZ801 and 0× on every stock capture.** It is a
   clean, binary discriminator — the first one this campaign has had for the offline condition.
2. **`OPRT_MODE_CHGD` is not "UZ801-only".** Doc 212 §3 already corrected that; the table shows it
   again from the other side: STOCK-LONG emits it once, at the online transition.
3. **The stock boot emits `SUBSCRIPTION_CHGD` **twice**; the UZ801 boot emits it **once** and
   substitutes an `OPRT_MODE_CHGD`.** The substitution is the differential (§3).
4. **`PROT_GEN_CMD` tracks the protocol being *active*, not the transition** — it is 15 on the long
   stock capture (54 `ONLINE_GWL` states), 6 on the live-online capture, and **0 on every capture that
   never goes online** (both stock 240 s boots *and* both UZ801 boots). §5.

---

## 3. The one-command differential, shown side by side

Both sequences below are printed from the same parser, compressed to the MMOC state machine
(`MAIN` = the `[MAIN]` field of `Prot_state`; consecutive identical `MAIN` values are collapsed).
Indices and `ts` are quoted verbatim.

### 3.1 STOCK-240 (`stock_boot_240.bin`, `code=0`)

```
   [452] ts=    96132  cmph.c        CM->MCS: op_mode=10
   [476] ts=    96412  cmmsc_auto.c  MSC_AUTO: 0x200 & 0xebe = 0x200          <- run 1 (mask 3774)
   [480] ts=    96416  cmph.c        CM->MCS: op_mode=11
   [485] ts=    96432  mmocdbg.c     Recvd command 0(SUBSCRIPTION_CHGD)        <- bring-up #1
   [492] ts=    96520  mmocdbg.c     New transaction : 1(SUBSC_CHGD)
         ...         WAIT_DEACTD_CNF_GWL -> PROT_PH_STAT_ENTER -> NULL        (short body)
   [598] ts=   144492  mmocdbg.c     Recvd command 7(DUAL_STANDBY_CHGD)
         ...         MMGSDI_INFO_IND(12) -> WAIT_SESSION_OPEN_CNF -> ... -> NULL
   [903] ts=   403644  qmi_nas_mmgsdi.c  new ef spn size: 17 old cached size: 0 session: 0
   [905] ts=   404088  cmmsc_auto.c  MSC_AUTO: 0x200 & 0x220 = 0x200          <- run 2 (mask 544)
   [908] ts=   404088  cmmsc_auto.c  old_op_mode:11 new_op_mode:11            <- a NO-OP
   [910] ts=   404100  mmocdbg.c     Recvd command 0(SUBSCRIPTION_CHGD)        <- bring-up #2
   [917] ts=   404124  mmocdbg.c     New transaction : 1(SUBSC_CHGD)
         ...         WAIT_DEACTD_CNF_GWL -> PROT_DEACTD_CNF -> WAIT_PH_STAT_CNF
                     -> PH_STAT_CHGD_CNF x3 -> PROT_PH_STAT_ENTER -> NEW 0(NULL)
         ...         *** MAIN stays 0(NULL) ***
```

### 3.2 UZ801 (`uz801_v19_boot_240.bin`, `code=4774`)

```
   [504] ts=4119791712  cmph.c        CM->MCS: op_mode=10
   [533] ts=4119792188  cmmsc_auto.c  MSC_AUTO: 0x200 & 0xebe = 0x200          <- run 1 (mask 3774)
   [537] ts=4119792192  cmph.c        CM->MCS: op_mode=11
   [542] ts=4119792212  mmocdbg.c     Recvd command 0(SUBSCRIPTION_CHGD)        <- bring-up #1
   [549] ts=4119792236  mmocdbg.c     New transaction : 1(SUBSC_CHGD)
         ...         WAIT_DEACTD_CNF_GWL -> PROT_PH_STAT_ENTER -> NULL        (short body)
   [635] ts=4119807520  mmocdbg.c     Recvd command 2(OPRT_MODE_CHGD)           <- *** bring-up #2 ***
   [638] ts=4119807620  mmocdbg.c     Trans_state 25(WAIT_DEACTD_CNF_GWL)       <- no "New transaction"
   [645] ts=4119807924  mmocdbg.c     Recvd report 0(PROT_DEACTD_CNF)
   [646] ts=4119807928  mmocdbg.c     Curr_trans 3(OFFLINE)
         ...         WAIT_PH_STAT_CNF -> PH_STAT_CHGD_CNF x3 -> PROT_PH_STAT_ENTER -> NEW 0(NULL)
   [689] ts=4119808544  mmocdbg.c     *** Prot_state [MAIN] 7(OFFLINE) ***
   [870] ts=4119848028  mmocdbg.c     Recvd command 7(DUAL_STANDBY_CHGD)
         ...         MMGSDI_INFO_IND(12) -> WAIT_SESSION_OPEN_CNF -> ... -> NULL
  [1107] ts=4120122556  qmi_nas_mmgsdi.c  new ef spn size: 17 old cached size: 0 session: 0
         *** no cmmsc_auto run 2, no second SUBSCRIPTION_CHGD ***
  [1715] ts=4123248780  wms.c        Putting WMS_CMD_CFG_GET_ROUTES (client 18)  <- ModemManager starts
```

### 3.3 What the two share, and the one thing they do not

**Shared, byte for byte, in the same order:** `CM->MCS op_mode 10 → 11` via `cmmsc_auto` mask
`3774`; `Recvd command 0(SUBSCRIPTION_CHGD)` **20 ticks** (UZ801) / **16 ticks** (stock-240) after the
`CM->MCS`; `New transaction : 1(SUBSC_CHGD)`; the **short** transaction body; `Recvd command
7(DUAL_STANDBY_CHGD)`; the `MMGSDI_INFO_IND(12)` transaction with five `Recvd report 5(MMGSDI_CNF)`;
the EF_SPN read at `+1.5 s` with identical args `[17, 0, 0]`.

**Different — exactly one thing:**

| | stock | UZ801 |
| :-- | :-- | :-- |
| the 2nd bring-up command's **id** | `0 (SUBSCRIPTION_CHGD)` | `2 (OPRT_MODE_CHGD)` |
| the 2nd bring-up transaction **body** | `WAIT_DEACTD_CNF_GWL → PROT_DEACTD_CNF → WAIT_PH_STAT_CNF → PH_STAT_CHGD_CNF ×3 → PROT_PH_STAT_ENTER → NEW 0(NULL)` | **identical** |
| terminal `Prot_state [MAIN]` | `0 (NULL)` — unchanged | **`7 (OFFLINE)`** |

**⇒ The transaction is the same; the command id is the variable; and the command id determines the
terminal `Prot_state`.** MMOC's `SUBSCRIPTION_CHGD` handler does not touch `Prot_state [MAIN]`;
its `OPRT_MODE_CHGD` handler sets it — and on UZ801 the value it sets is `OFFLINE`.

**⇒ The op-mode the UZ801 command carries is OFFLINE (or a value MMOC maps to `7`).** That is the
last unknown, and §10 says why it is still unknown.

### 3.4 The `New transaction` print is emitted only on the activate path

A decoding rule worth recording, because it explains an apparent missing record:

* stock's `OPRT_MODE_CHGD` → `Curr_trans 0`, `Trans_state 0`, `Prot_state ×2`, `Curr_trans 0`,
  `Trans_state 0`, **`New transaction : 2(ONLINE)`**, `Curr_trans 2(ONLINE)`, `Trans_state
  6(WAIT_PH_STAT_CNF)`.
* UZ801's `OPRT_MODE_CHGD` → `Curr_trans 0`, `Trans_state 0`, **`Trans_state
  25(WAIT_DEACTD_CNF_GWL)`** — and the transaction only becomes visible as `Curr_trans 3(OFFLINE)`
  *after* the `PROT_DEACTD_CNF` report.

**⇒ Do not read "no `New transaction` record" as "no transaction started."** The deactivate path sets
`Curr_trans`/`Trans_state` directly. (Doc 212 §5's enum tables still hold; only this print-coverage
rule is new.)

---

## 4. The event-anchored timing — UZ801's command is a **boot-path** event, stock's is **MM-triggered**

Doc 212's rule is: *a window is not a control — anchor to the event.* Doing that, with the F3 clock
at **204 800 Hz** (validated twice inside this data, §4.1):

| measurement | ticks | seconds |
| :-- | --: | --: |
| UZ801: boot `SUBSCRIPTION_CHGD` → `OPRT_MODE_CHGD` | 15 308 | **0.075** |
| UZ801: `OPRT_MODE_CHGD` → `Prot_state [MAIN] 7(OFFLINE)` | 1 024 | 0.005 |
| UZ801: `OPRT_MODE_CHGD` → `DUAL_STANDBY_CHGD` | 40 508 | 0.198 |
| UZ801: `OPRT_MODE_CHGD` → **ModemManager's `WMS_CMD_CFG_GET_ROUTES`** | **−3 441 260** | **−16.80** |
| UZ801: boot `SUBSCRIPTION_CHGD` → EF_SPN read | 330 344 | 1.613 |
| STOCK-LONG: ModemManager's `WMS_CMD_CFG_GET_ROUTES` → `OPRT_MODE_CHGD` | **+101 660** | **+0.496** |
| STOCK-LONG: `CMD alloc u=17, tsk=dcc` → `OPRT_MODE_CHGD` | 1 340 | 0.007 |
| STOCK-LONG: `OPRT_MODE_CHGD` → `Prot_state [MAIN] 5(ONLINE_GWL)` | 1 484 | 0.007 |
| STOCK-240: boot `SUBSCRIPTION_CHGD` → EF_SPN read | 307 212 | 1.500 |
| STOCK-240: EF_SPN read → `cmmsc_auto` run 2 | 444 | 0.002 |
| STOCK-240: `cmmsc_auto` run 2 → 2nd `SUBSCRIPTION_CHGD` | 12 | 0.00006 |

**The reading.** UZ801 emits its `OPRT_MODE_CHGD` **75 ms after the boot `SUBSCRIPTION_CHGD`** and
**16.8 s before ModemManager even configures its WMS routes**. Stock emits its `OPRT_MODE_CHGD`
**0.50 s after** ModemManager's route configuration, at the end of the `dcc` command's lifetime.

**⇒ UZ801's command is produced by the firmware's own boot sequence. Stock's is produced in response
to the AP.** That is the whole difference in one sentence, and it is event-anchored, not
window-based.

### 4.1 Why 204 800 Hz is trusted here

Two internal consistencies, neither of which used the tick rate as an input:

1. `3 441 260 / 204 800 = 16.80 s` — and ModemManager is known to start ~15–20 s after modem boot
   on this board (Doc 210's boot timeline: rmtfs 10.66 s, modem power-up 10.94 s, DIAG channel
   12.91 s, **ModemManager after the AP's userspace is up**).
2. `15 308 / 204 800 = 74.7 ms` — a boot-path command 75 ms after the command it follows, which is
   what "same bring-up sequence" looks like.

Both hold without tuning. **This is the first time this campaign has had two independent internal
checks on the F3 tick rate.**

---

## 5. `PROT_GEN_CMD` is a **30 s periodic protocol poll** — the queued question is answered

Doc 212 §3.4 raised: *is `PROT_GEN_CMD` a cause or a consequence of being online?* It was left open.

**The measurement.** `live_stock_online.bin` is a stock modem that **is** `ONLINE_GWL`. Its MMOC
timeline is nothing but `PROT_GEN_CMD` transactions, each printing `Prot_state [MAIN] 5(ONLINE_GWL)`
and **no** `OPRT_MODE_CHGD` at all:

| index | ts | Δ from previous |
| --: | --: | --: |
| 2444 | 1 485 242 636 | — |
| 6472 | 1 491 386 976 | 6 144 340 |
| 8227 | 1 497 531 416 | 6 144 440 |
| 10612 | 1 503 676 084 | 6 144 668 |
| 16846 | 1 515 965 032 | 12 288 948 (2×) |
| 22216 | 1 528 253 464 | 12 288 432 (2×) |

**6 144 4xx ticks = 30.00 s at 204 800 Hz.** A clean, regular 30 s period.

**⇒ VERDICT: `PROT_GEN_CMD` is a routine 30 s protocol-stack poll, emitted only while the protocol
is active.** Its absence on UZ801 is therefore a **CONSEQUENCE** of never being online — not a cause.
The queue item is closed, and Doc 212 §3.4 should be read with this correction.

**Corollary — the three-record burst is a different thing.** Stock-LONG's `PROT_GEN_CMD` at
[811] / [835] / [862] are 280 / 276 ticks apart (≈1.4 ms), i.e. **not** the 30 s poll. They sit
between the `dcc` allocation and the `OPRT_MODE_CHGD` and each carries an `mmoc.c:13500
addl_action` record. **⇒ There are two `PROT_GEN_CMD` populations: a transition burst and a 30 s
poll. Only the poll is diagnostic of being online.**

---

## 6. The `tsk=dcc` CM command — the carrier of the AP's online request

### 6.1 The record

```
=CM= CMD alloc u=17, tsk=dcc            (hmu05_stock_boot.bin idx 806, ts 3040010292)
=CM= CMD free  u=17, tsk=dcc, ftsk=cm   (hmu05_stock_boot.bin idx 912, ts 3040012172)
```

**`u` is the modem's uptime in seconds** (Doc 201 §3.1) — and this record is the cleanest
confirmation of that: it is the **only** `dcc` command in the archive, it is allocated at
**uptime 17 s**, and its lifetime **brackets the whole online transition**
(`alloc` ts 3 040 010 292 → `cmmsc_auto` → `PROT_GEN_CMD` ×3 → `OPRT_MODE_CHGD` → `New
transaction : 2(ONLINE)` → `free` ts 3 040 012 172 → `Prot_state [MAIN] 5(ONLINE_GWL)` ts
3 040 013 116).

### 6.2 The census

| capture | `CMD alloc tsk=dcc` | `CMD free tsk=dcc` | reaches `ONLINE_GWL` |
| :-- | --: | --: | :-- |
| `hmu05_stock_boot.bin` | **1** (`u=17`) | 1 (`u=17`) | **YES** |
| `stock_boot_240.bin` | 0 | 0 | no |
| `stock_boot_211.bin` | 0 | 0 | no |
| `uz801_v19_boot_240.bin` | **0** | **8** (`u=0`) | no |
| `doc210/boot_210_canon.bin` | **0** | **8** (`u=0`) | no |

**⇒ The `dcc` CM command is the only command in the entire archive whose allocation precedes the
online transition. It is present in exactly the one capture that goes online.**

### 6.3 The caveat, stated plainly

**n = 1 for the "stock allocates a dcc command and goes online" link.** The other two stock boots do
not allocate one and do not go online — which is consistent, but does not distinguish
"MM never asked" from "MM asked and the command took a different path". **What would settle it:**
a stock boot capture whose `WMS_CMD_CFG_GET_ROUTES` is followed within a second by a
`CMD alloc u=<N>, tsk=dcc`, plus a ModemManager-side trace of the QMI DMS
`Set Operating Mode` request. Until then this is a **strong correlation with a named mechanism**,
not a proven cause. Recorded as such; do **not** cite it as proven.

### 6.4 What `dcc` is

`dcc` is not a mystery string — it is a task, and its source files are in the image's phdr[18]
string table at VAs `0xc18c0d48` … `0xc18c0d88`:

```
ps_sys_conf.c | ps_sys_event.c | dcc_task.c | dcc_task_svc.c | dcc_taski.c
| qmi_modem_task.c | qmi_modem_taski.c | qmi_modem_task_svc.c | ds_sig_task.c | ds_sig_taski.c
```

**⇒ `tsk=dcc` is `dcc_task.c` / `dcc_task_svc.c` / `dcc_taski.c`** — a CM sub-task adjacent to the
`ps_sys_*` protocol-stack task names and the `qmi_modem_task` family. The `dcc` task itself emits no
F3 records (0 hits for `dcc_task` across all six captures' file fields); it is visible **only**
through `cmdbg.c`'s `CMD alloc/free`.

---

## 7. `CM->MCS: op_mode` is **identical** on both stacks — and the trap that hid it

Doc 212's session briefly read `=CM= CM->MCS: op_mode=%d` as a UZ801-only record. **That was an
artefact.** With all four boot captures parsed:

| capture | `CM->MCS: op_mode` | values |
| :-- | --: | :-- |
| `stock_boot_240.bin` | 8 | 10 → 11 |
| `stock_boot_211.bin` | 8 | 10 → 11 |
| `uz801_v19_boot_240.bin` | 8 | 10 → 11 |
| `doc210/boot_210_canon.bin` | 8 | 10 → 11 |
| `hmu05_stock_boot.bin` | **0** | — |

**⇒ 10 → 11 on every boot, on both stacks. Byte-identical.** This re-confirms Doc 212 §3's
"every CM-layer numeric is byte-identical".

**⇒ The trap: `hmu05_stock_boot.bin` scores 0 because `f3clean.py`'s median-`ts` window deleted its
boot chunk** (already documented in `reference_modem_f3_bootlog_instrument.md`). **A 0 on that
capture is not evidence of absence on stock — it is evidence that the capture has no boot section.**
Any "X is UZ801-only" claim must be checked against `stock_boot_240.bin` **and**
`stock_boot_211.bin` before it is written down.

---

## 8. `scratch/doc210/boot_210_canon.bin` is a **UZ801** capture

It had been labelled stock in working notes. The fingerprint settles it:

| marker | STOCK-240 | UZ801v19-240 | `boot_210_canon.bin` | verdict |
| :-- | --: | --: | --: | :-- |
| `mcfg_uim.c` | 0 | 9 | **9** | UZ801 |
| `uim.c` | 0 | 1 | **1** | UZ801 |
| `uimsub_manager.c` | 0 | 6 | **9** | UZ801 |
| `uimgen.c` | 0 | 1 | **1** | UZ801 |
| `tsk=dcc` | 0 | 8 | **8** | UZ801 |
| `tsk=pbm` | 0 | 2 | **2** | UZ801 |
| `cmss.c` | 18 | 0 | **1** | UZ801 |

**⇒ The archive holds TWO independent UZ801 boot captures**, and **both** emit the boot-time
`OPRT_MODE_CHGD` and both reach `Prot_state [MAIN] 7(OFFLINE)` 26×. The differential in §3 is
therefore reproduced on two independent boots, not one.

**Action:** the working label must be corrected wherever it appears; `boot_210_canon.bin` is a
**UZ801** capture, code `4773`.

---

## 9. Cross-build `file:line` comparison is invalid

The same log statement has **different source line numbers** in the two builds:

| record | stock | UZ801 |
| :-- | --: | --: |
| `cmph.c` `CM->MCS: op_mode` | **15052** | **15285** |
| `cmph.c` `CM->MCS: Set SxLTE simul cap` | 15057 | 15290 |
| `mmoc.c` `standby_pref` | **10433** | **6752** *and* **10358** |
| `mmoc.c` `cmd_ptr->param.subsc_chgd.active_ss` | 10442 | 10367 |
| `mmocdbg.c` (all MMOC records) | **291** | **288** |

**⇒ Any comparison keyed on `file:line` across the two builds is wrong by construction.** This is
the same trap class as Doc 208's "`code` is not the AP-selected SSID". `mmoc.c` in particular has a
**~3 700-line offset** between the two builds, so a stock line number cannot be searched for in the
UZ801 decompile (and vice versa).

---

## 10. Why the payload is still unknown — route (a) is blocked, re-confirmed

Doc 212 §13.1 recorded route (a) as blocked. This session made the blockage sharper and added a new
reason:

1. **The MMOC debug format strings are not in the shipped image at all.**
   `scratch/uz801_fw/modem.elf` (md5 `50ee8c56d6b836329ef97ac83ed34bff`):
   `Recvd command` **0**, `Trans_state` **0**, `New transaction` **0**, `Curr_trans` **0**,
   `Prot_state` **0**, `OPRT_MODE` **5** (all in the *code* segment or unrelated string blobs),
   `mmocdbg` **0** — while the **control** `old_op_mode` is present at file offset `0x2fdcdf2` →
   **VA `0xc4563df2`** (phdr[25], `0xc4558000..0xc4614cec`). This is the same class as Doc 212 §8's
   finding that the MMOC name tables are runtime-populated.
2. **The decompile materialises zero addresses in that segment.** 387 743 `DAT_` references in
   `modem_full_decompiled.c`, **none** with prefix `c45`; 4 047 `s_…_c_<va>` names, **none**
   matching `op_mode`/`oprt`/`mmoc`/`cmmsc`. The code addresses phdr[25] by a **computed
   base+offset**, so no absolute VA ever appears as a literal.
3. **The decompile is blind to `dcc_task`** (0 hits) — so the emitter of the `dcc` command cannot be
   reached that way either.

**⇒ Route (a) — static — remains blocked. Route (b) — a coredump taken at the OFFLINE transition —
is the cheaper path, and it is now the *only* path to the payload.**

---

## 11. What this rules out, what it does not, and the device state

### 11.1 Ruled out by this session's measurements

* **"`PROT_GEN_CMD` is the missing link."** It is a 30 s poll gated on the protocol being active
  (§5). It is a consequence.
* **"`CM->MCS: op_mode` differs between the builds."** It does not — 10 → 11 on both (§7).
* **"The `OPRT_MODE_CHGD` is UZ801-only."** Both the stock long capture and the live-online capture
  have it; and Doc 212 already corrected the window reading (§2).
* **"The EF_SPN read is the differentiator."** It is present on both, with identical args
  `[17, 0, 0]` and the same ~1.5 s offset (§11.2).
* **"The bring-up transaction body differs."** It is byte-identical; only the command id differs
  (§3.3).

### 11.2 A new, unresolved asymmetry — the EF_SPN read's *effect* differs

`qmi_nas_mmgsdi.c new ef spn size: 17 old cached size: 0 session: 0` occurs on **both** stacks with
**identical args** and at the **same ~1.5 s** offset from the boot `SUBSCRIPTION_CHGD`. But:

* **stock:** EF_SPN read → 444 ticks (2.17 ms) → `cmmsc_auto` **run 2** (mask `544`) → 12 ticks
  (58.6 µs) → the **second `SUBSCRIPTION_CHGD`**.
* **UZ801:** EF_SPN read → **nothing.** No `cmmsc_auto` run 2, no second `SUBSCRIPTION_CHGD`.

**⇒ The same SIM event, with the same payload, produces a CM bring-up command on stock and nothing
on UZ801.** This is a second, independent divergence, and it is *not* the one that sets `OFFLINE`
(UZ801's `OPRT_MODE_CHGD` is 315 036 ticks **earlier** than the EF_SPN read). It is recorded as an
open lead, not as the root cause.

**Also note:** stock's `cmmsc_auto` run 2 has `old_op_mode:11 new_op_mode:11` — a **no-op** — and yet
it still emits a `SUBSCRIPTION_CHGD`. So the `cmmsc_auto` mask, not the op-mode delta, selects the
command.

### 11.3 Not ruled out

* The **value** of the op-mode in UZ801's `OPRT_MODE_CHGD` (§10).
* Whether the `dcc` command is the AP's QMI DMS request (§6.3, n = 1).
* Why UZ801's boot path emits an `OPRT_MODE_CHGD` at all, and why stock's does not.

### 11.4 Device state — unchanged this session

| item | value |
| :-- | :-- |
| firmware | **UZ801 v19** |
| `boot_id` | `26d74096-874f-45ee-9de4-67a8ee87caf1` — **identical to the one recorded in `uz801_v19_boot_240.status`**, i.e. the device has not rebooted since that capture |
| `/lib/firmware/modem.mdt` | `9f39ce579114bcf1383bbdce62a8d284` |
| `/lib/firmware/modem.b16` | `c71d53464a1e88f2072cbbb6f7f87475` |
| `modem-guard` | no `BOOTLOOP_REVERT.txt`; guard clean |
| AP uptime | 4 603 s |

**No deployment, no reboot, no device-side write.**

---

## 12. Next steps, in priority order

1. **Route (b): take a coredump at the OFFLINE transition** and read the `OPRT_MODE_CHGD` command
   struct out of MMOC's RAM. This is the only remaining route to the payload (§10). It needs the
   coredump to be armed *before* the boot-time command (which fires ~75 ms after the boot
   `SUBSCRIPTION_CHGD`), so the coredump trigger must be the `OPRT_MODE_CHGD` itself or the modem
   must be crashed immediately after boot.
2. **Find what makes CM choose `OPRT_MODE_CHGD` over `SUBSCRIPTION_CHGD` in that slot.** The
   selection is inside CM and is not the op-mode delta (§11.2). The candidate instruments are the
   `cmmsc_auto` mask (`0xEBE` vs `0x220`) and the `dcc` command's parameters — both of which need
   route (b) or a working descriptor→code instrument.
3. **Cheap, independent: test the `dcc` hypothesis (§6.3).** On a stock boot, trace the AP's QMI DMS
   `Set Operating Mode` and check for a `CMD alloc tsk=dcc` within a second of
   `WMS_CMD_CFG_GET_ROUTES`. If confirmed, the port's target becomes "make the AP's op-mode request
   reach CM", which is a userspace/AP-side lever, not a baseband patch.
4. **Correct the `boot_210_canon.bin` label** wherever it is used (§8).

**Do NOT re-open:** `cmcc.c` / GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch,
`0x1000001`, `cm_state+0x39e8`, "the CM code differs", the `mode_pref` 9728-vs-38 difference, the
`MSC_AUTO` `0x220`-vs-`0xebe` build difference **as a code difference** (it is a runtime mask, §3.1),
the policyman RAT-mask-0 lead, the `dcc` break as a *cause of the offline* (it is downstream, Doc 212
§9), the `policyman_rf` EFS failure, the `cmph` `rat_disabled_mask` sequence as the emitter, or
`PROT_GEN_CMD` as the online gate (§5).

**Do NOT:** conclude anything about `OPRT_MODE_CHGD` from a same-window pair; score a CM-layer
negative on a capture with `rcinit_init.c` = 0 **or** on `hmu05_stock_boot.bin`'s boot section
(§7); take an `args` value from a parser older than `9f9d6edf15d64faf3b47897f7838c9ac`; compare
`file:line` across the two builds (§9).

---

## 13. Evidence table

| artefact | md5 | size | note |
| :-- | :-- | --: | :-- |
| `scratch/doc211/stock_boot_240.bin` | `0ff4e4d22c23443e2c6e82bcb24cbc83` | 1 500 441 | stock, cold boot, `script_md5 9cd993a7…`, 240 s, boot_id `4c36af82…` |
| `scratch/doc211/stock_boot_211.bin` | `e49515eff18afcf069215bde6a35a835` | 1 424 573 | stock, cold boot, `script_md5 3b8fc200…`, boot_id `7580936d…` |
| `scratch/doc211/uz801_v19_boot_240.bin` | `81318b04f7e0da3e74675d2973366758` | 1 240 397 | **UZ801 v19**, cold boot, `script_md5 9cd993a7…`, 240 s, boot_id `26d74096…` |
| `scratch/doc210/boot_210_canon.bin` | `b693faebb2bab65ca5c9eb948285658b` | 1 163 362 | **UZ801** (code 4773) — label corrected §8 |
| `scratch/doc211/live_stock_online.bin` | `e765744410d570e9eb0423bc46b58f9f` | 65 902 256 | stock, **online**, 25 862 msgs |
| `scratch/hmu05_stock_boot.bin` | `21506cace2e94824c21c597cb33f35a1` | 94 623 391 | stock long; **boot section deleted by `f3clean.py`** |
| `scratch/f3parse.py` | `9f9d6edf15d64faf3b47897f7838c9ac` | 3 716 | the Doc-211-fixed parser |
| `scratch/uz801_fw/modem.elf` | `50ee8c56d6b836329ef97ac83ed34bff` | 50 912 492 | the decompile's source |
| `scratch/doc212_pgc_ctx.py`, `doc212_cmp.py`, `doc212_cmds.py`, `doc212_gap.py`, `doc212_wms.py`, `doc212_mscauto.py`, `doc212_uz_trace.py`, `doc212_raw.py` | — | — | the census/trace scripts used here |

---

## 14. Traps recorded by this session

1. **A 0 on `hmu05_stock_boot.bin` is not evidence of absence** — its boot chunk was deleted by
   `f3clean.py` (§7). Check `stock_boot_240.bin` **and** `stock_boot_211.bin`.
2. **`file:line` is not comparable across the two builds** (§9) — `mmoc.c` is offset by ~3 700
   lines, `cmph.c` by 233, `mmocdbg.c` by 3.
3. **"No `New transaction` record" ≠ "no transaction started"** (§3.4) — the deactivate path sets
   `Curr_trans`/`Trans_state` directly.
4. **`ts` is per-`code`** (Doc 211) — and here the same capture carried **two** `code`s for one
   function (`4774` enter / `42741` exit, Doc 212 §7.2). Never order F3 across `code`s.
5. **`PROT_GEN_CMD` has two populations** — a transition burst (ms apart) and a 30 s poll. Counting
   them together hides the mechanism (§5).
6. **A capture is not a window** — `live_stock_online.bin` has **0** `cmdbg.c` records because its
   sweep mask differs, so a "no `dcc`" reading from it would have been a false negative.
7. **The tick rate needs two independent internal checks** before a "before/after" claim is quoted
   in seconds (§4.1).

---

## 15. Ledger fold-in

Appended to `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` as **§7 item 18** in the same session, per
`197 §1`.

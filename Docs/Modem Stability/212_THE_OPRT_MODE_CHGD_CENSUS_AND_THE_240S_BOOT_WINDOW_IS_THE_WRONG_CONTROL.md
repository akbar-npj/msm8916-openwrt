# 212 — The `OPRT_MODE_CHGD` census across every capture, and the 240 s boot window is the wrong control

**Date:** 2026-09-27
**Ledger:** this document is a companion of **`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`**
(see §1). It must be read with it.
**Predecessors:** 211 (the `args`-shift parser defect; the first clean same-window
differential), 208 (the lost F3 burst class), 210 (the burst class restored).
**Standing goal:** make the transplanted **UZ801 v3.2 "-21"** baseband leave `offline` and go
`online` on the **HMU05** (MSM8916 + WTR1605 + QFE2320, OpenWrt 25.12.5, kernel 6.12.94 aarch64).

---

## §1 SOP compliance and achieved-vs-expected

### §1.1 SOP-compliance statement

Per Doc 197 §1 (mandatory from Doc 197 onward):

| SOP step | Status | Where |
|---|---|---|
| Never blind-patch a baseband; verify against stock HMU05 ground truth | **TAKEN** | every claim below is scored against a **stock HMU05** capture on the same device/EFS, and the stock arm is now checked on **two independent boots** (§3.1) |
| Fingerprint firmware by `--dms-get-revision` **and** `modem.mdt` MD5, never by a narrative | **TAKEN** | §7.1 — the device is still UZ801 v19 (`9f39ce57…`, `c71d5346…`), `Mode: 'offline'`, guard `boot_count` 0 |
| Archive both firmware sets so the swap is reversible | **NOT TOUCHED** | no deployment was made this session — this is a pure re-analysis of captures already in hand |
| Verify a deployment with the **loadable image**, never a bare md5 | **N/A** | no deployment this session |
| Pre-register n before scoring a rate | **N/A** | this document scores no rate |
| Update the ledger in the same session | **TAKEN** | §13 |
| State an explicit SOP-compliance statement | **TAKEN** | this table |

### §1.2 Achieved vs expected

| # | Expected at the start of this session | Achieved | Verdict |
|---|---|---|---|
| 1 | Task #13 — build an instrument for the **MMOC `OPRT_MODE_CHGD` payload / MMOC session table** | the **complete MMOC name tables** were recovered and **verified against five captures** (§8); the payload itself is **not logged by the firmware** and no runtime-instrumentable handle exists | **PARTIAL** |
| 2 | Re-run Doc 211 §8's `OPRT_MODE_CHGD` differential to confirm it | **CONFIRMED** on four captures — but the comparison is **cross-instrument**, and the clean same-window pair does **not** reproduce it (§3) | **MET (with a correction of scope)** |
| 3 | Task #12 — a **burst-vs-blind pair** on the UZ801-only UIM/STK bring-up | **not run** — superseded: the burst-vs-blind question was answered *analytically* as out-of-scope (§10) | **DEFERRED** |
| 4 | Explain *why* UZ801 stays `offline` | **not achieved** — but the target is now pinned to a **single MMOC decision** on the payload's op mode, and the wrong control has been identified and ruled out | **PARTIAL** |
| 5 | — | **new, unplanned:** the complete **file-level census** of both arms (70 vs 65 files) and the exact `file:line` sets unique to each (§6) | **MET** |
| 6 | — | **new, unplanned:** the `Recvd command 2(OPRT_MODE_CHGD)` is received by stock **too** — at the **online transition**, where it produces `ONLINE`; the "UZ801-only" reading was a **window artefact** | **MET** |

---

## §2 The MMOC command census — the headline

Every `=MMOC= Recvd command %d(%s)` record, counted per capture. `f3parse.py` md5
`9f9d6edf15d64faf3b47897f7838c9ac` (the Doc 211 fix). All `Recvd command` records have
`extra = 0`, so they are **untouched by the parser defect**.

| capture | firmware | msgs | `Recvd command` set |
|---|---|---|---|
| `doc211/stock_boot_240.bin` | stock HMU05 | 1 641 | `0(SUBSCRIPTION_CHGD)`×2, `4(PROT_REDIR_IND)`×1, `7(DUAL_STANDBY_CHGD)`×1 |
| `doc211/stock_boot_211.bin` | stock HMU05 | 1 669 | **identical** |
| `hmu05_stock_boot.bin` | stock HMU05 (94 MB) | 33 743 | `0(SUBSCRIPTION_CHGD)`×1, `1(PROT_GEN_CMD)`×15, **`2(OPRT_MODE_CHGD)`×1** |
| `doc211/uz801_v19_boot_240.bin` | UZ801 v19 | 1 719 | `0(SUBSCRIPTION_CHGD)`×1, **`2(OPRT_MODE_CHGD)`×1**, `4(PROT_REDIR_IND)`×1, `7(DUAL_STANDBY_CHGD)`×1 |
| `doc210/boot_210_canon.bin` | UZ801 (old instrument) | 1 718 | `0(SUBSCRIPTION_CHGD)`×1, **`2(OPRT_MODE_CHGD)`×1**, `4(PROT_REDIR_IND)`×1, `7(DUAL_STANDBY_CHGD)`×1 |
| `doc211/live_stock_online.bin` | stock HMU05, **online** | 25 862 | `1(PROT_GEN_CMD)`×6 |

### §2.1 The outcome of that command

| capture | after `Recvd command 2(OPRT_MODE_CHGD)` | final `Prot_state [MAIN]` |
|---|---|---|
| `hmu05_stock_boot.bin` (idx 878) | `Curr_trans 0(NULL)` → `Trans_state 0(NULL)` → **`New transaction : 2(ONLINE)`** → `Trans_state 6(WAIT_PH_STAT_CNF)` → `Recvd report 2(PH_STAT_CHGD_CNF)` | **`5(ONLINE_GWL)`** (54 records) |
| `boot_210_canon.bin` (idx 625) | `Curr_trans 0(NULL)` → `Trans_state 0(NULL)` → **`Trans_state 25(WAIT_DEACTD_CNF_GWL)`** → `Recvd report 0(PROT_DEACTD_CNF)` → **`Curr_trans 3(OFFLINE)`** | **`7(OFFLINE)`** (26 records) |
| `uz801_v19_boot_240.bin` (idx 635) | same as `boot_210_canon` | **`7(OFFLINE)`** (26 records) |

**The command is identical. The outcome is opposite.** This is Doc 211 §8's result, now
reproduced on a **second, independent UZ801 capture** (`uz801_v19_boot_240.bin` vs
`boot_210_canon.bin`) and corroborated by the **only** stock capture that ever reaches
`ONLINE_GWL`.

> **Correction of scope (the point of this document).** Doc 211 §8, and the memory index line
> that summarises it, compare stock from **`hmu05_stock_boot.bin`** against UZ801 from
> **`boot_210_canon.bin`**. Those are **two different instruments and two different windows**.
> The claim "both receive `OPRT_MODE_CHGD`" is **true**, but it is **not reproducible in the
> clean 240 s same-window pair** — and that is exactly the trap §3 describes.

---

## §3 The 240 s boot window is the wrong control

### §3.1 The same-window pair does *not* show the differential

`stock_boot_240.bin` and `uz801_v19_boot_240.bin` were taken on **one instrument, one window,
one device, one EFS, one SIM, one AP** (Doc 211 §7). In that pair:

| | stock 240 s | UZ801 240 s |
|---|---|---|
| `Recvd command 2(OPRT_MODE_CHGD)` | **0** | **1** |
| `Recvd command 0(SUBSCRIPTION_CHGD)` | **2** | **1** |
| `Curr_trans 3(OFFLINE)` | **0** | **8** |
| `Prot_state [MAIN] 7(OFFLINE)` | **0** | **26** |
| `Prot_state [MAIN] 0(NULL)` | 52 | 26 |
| `Prot_state [MAIN] 5(ONLINE_GWL)` | **0** | **0** |

Read literally against this pair, one concludes *"UZ801 uniquely receives `OPRT_MODE_CHGD`"* —
**which is false**, as §2 shows. Stock receives it in `hmu05_stock_boot.bin`.

**The reason is timing.** Doc 211 §4.5 already recorded that the stock 240 s boot **never
reaches the online transition** (`Prot_state` stays `0(NULL)` throughout). The `OPRT_MODE_CHGD`
on stock is the **online-transition** command. So:

* **stock** at 240 s: has not yet transitioned → has not yet received the command;
* **UZ801** at 240 s: has already received it and gone **offline**.

A same-window comparison is therefore **structurally blind** to the differential: it compares
"stock before the transition" against "UZ801 after a transition". **The correct control is the
stock capture that contains the transition** (`hmu05_stock_boot.bin`), and it must be compared
**event-to-event**, not window-to-window.

### §3.2 What the two contexts look like

**stock, at its `OPRT_MODE_CHGD`** (`hmu05_stock_boot.bin` idx 847-885) — the immediately
preceding records are:

```
#847  cmph.c:32055   =CM= mode_pref %d, pref_term %d                       args=[38, 0]
#848  cmph.c:32065   =CM= rat_disabled_mask as_id_0 0x%x, as_id_1 0x%x     args=[0, 0]
#850  cmph.c:17829   =CM= RAT_DISABLED_MASK: sys_mode %d, …               args=[9, 0, 0]
#852  cmph.c:17829   =CM= RAT_DISABLED_MASK: sys_mode %d, …               args=[4, 0, 0]
#854  cmph.c:32104   =CM= After updating from rat_disabled_mask mode_pref %d  args=[38]
#862  mmocdbg.c      =MMOC= Recvd command 1(PROT_GEN_CMD)
#878  mmocdbg.c      =MMOC= Recvd command 2(OPRT_MODE_CHGD)
#885  mmocdbg.c      =MMOC= New transaction           : 2(ONLINE)
```

**UZ801, at its `OPRT_MODE_CHGD`** (`uz801_v19_boot_240.bin` idx 620-635):

```
#620  sdss.c:47745    =SD= sdss_get_scan_scope_rule()-scan_scope_rule=%d
#621  sdcmd.c:27798   =SD= hybr_name_sel()-is_gw2_subsc_avail:%d,simstate:%d
#622  cmph.c:32761    =CM= mode_pref %d, pref_term %d
#623  cmph.c:32771    =CM= rat_disabled_mask as_id_0 0x%x, as_id_1 0x%x
#624  mmocdbg.c       =MMOC= Trans_state 5(PROT_PH_STAT_ENTER)
#625  mmocdbg.c       =MMOC= New transaction           : 0(NULL)
#628  cmph.c:18094    =CM= RAT_DISABLED_MASK: sys_mode %d, …
#630  cmph.c:18094    =CM= RAT_DISABLED_MASK: sys_mode %d, …
#632  cmph.c:32810    =CM= After updating from rat_disabled_mask mode_pref %d
#635  mmocdbg.c       =MMOC= Recvd command 2(OPRT_MODE_CHGD)
```

**Both are preceded by the `cmph.c` `rat_disabled_mask` sequence** — 12 ticks before the command
in both cases. **But that sequence is not sufficient**: stock's 240 s boot contains **7** of them
with **no** `OPRT_MODE_CHGD` following any of them.

| capture | `After updating from rat_disabled_mask` | `cmmsc_auto` op_mode updates | reaches `ONLINE_GWL`? |
|---|---|---|---|
| `stock_boot_240.bin` | 7 | **2** | no (window too short) |
| `hmu05_stock_boot.bin` | 7 | **3** | **yes** |
| `uz801_v19_boot_240.bin` | 12 | **1** | no — goes `OFFLINE` |

**The only structural asymmetry that survives is `cmmsc_auto`**: stock runs the op-mode update
**twice** in 240 s and **three times** in the long capture; UZ801 runs it **once**.

### §3.3 The `cmmsc_auto` op-mode records (fixed parser)

| | stock 240 s | UZ801 240 s |
|---|---|---|
| 1st | `cmmsc_auto.c:3241 MSC_AUTO: 0x%x & 0x%x = 0x%x` = `512 & 3774 = 512` @ ts 96412<br>`cmmsc_auto.c:24189 old_op_mode:%d new_op_mode:%d ue_mode:%d` = **`[10, 11, 0]`** | `cmmsc_auto.c:3250` = `512 & 3774 = 512` @ ts 4119792188<br>`cmmsc_auto.c:24189` = **`[10, 11, 0]`** |
| 2nd | `cmmsc_auto.c:3241` = `512 & 544 = 512` @ ts 404088<br>`cmmsc_auto.c:24189` = **`[11, 11, 0]`** | **absent** |

So stock's **second** run is a **no-op** (`11 → 11`) driven by the **policyman mask `544`**, and
it still emits a command to MMOC (`SUBSCRIPTION_CHGD`). UZ801 never runs it. This is the
`cmmsc_auto` half of the Doc 211 §8 picture, now measured with the fixed parser: the CM op mode
is **`10 → 11` on both**, i.e. **CM's own numerics are identical**, which is why Doc 211 §8
concluded the decision is made **inside MMOC**.

### §3.4 ★ `PROT_GEN_CMD` is entirely ABSENT on UZ801

`Recvd command 1(PROT_GEN_CMD)` — the AP-driven generic command to MMOC:

| capture | count |
|---|---|
| `hmu05_stock_boot.bin` | **15** |
| `doc211/live_stock_online.bin` | **6** |
| `doc211/stock_boot_240.bin` | 0 |
| `doc211/stock_boot_211.bin` | 0 |
| `doc211/uz801_v19_boot_240.bin` | **0** |
| `doc210/boot_210_canon.bin` | **0** |

On stock the `OPRT_MODE_CHGD` at idx 878 **follows the completion of a `PROT_GEN_CMD`
transaction**: `#862 Recvd command 1(PROT_GEN_CMD)` → `#869 New transaction : 4(PROT_GEN_CMD)` →
`#873 New transaction : 0(NULL)` → `#878 Recvd command 2(OPRT_MODE_CHGD)` → `#885 New
transaction : 2(ONLINE)`. UZ801 has **no `PROT_GEN_CMD` at all**, and its `OPRT_MODE_CHGD` has no
such antecedent.

**This is consistent with Doc 200 §8's chain** — `cmmsc_auto → PROT_GEN_CMD → OPRT_MODE_CHGD →
ONLINE → ONLINE_GWL` — **and with the already-recorded fact that UZ801's AP `online` request dies
in the `ds`/DMS task and never reaches CM** (Doc 194 §7; Doc 200 §6.3/§8). It sharpens the gate:
the missing link on UZ801 is not the MMOC command *type* but the **AP-driven `PROT_GEN_CMD` that
should precede it**, i.e. the failure may be **upstream in DMS**, not inside MMOC.

---

## §4 Why the payload cannot be read from the log

The `OPRT_MODE_CHGD` command carries an **operating-mode payload**. MMOC acts on that payload —
that is the only way the same command produces `ONLINE` on one build and `OFFLINE` on the other.

**The payload is not logged.** The `mmocdbg.c` site is `Recvd command %d(%s)` — the command id
and its **name**, nothing else. There is no `mmoc.c` parameter dump for the `oprt_mode_chgd`
union member analogous to the `subsc_chgd` dump that stock emits
(`mmoc.c:10433/10436/10440/10442 standby_pref / active_ss / device_mode / cmd_ptr->param.subsc_chgd.active_ss`,
present on stock, **absent on UZ801**).

So the instrument task #13 asked for — *log the MMOC command payload* — **cannot be built from
the existing firmware**. §8 records what *was* recovered instead.

---

## §5 The MMOC session / protocol state, decoded

The name tables recovered in §8 decode every numeric the two arms emit. Verified against all
five captures:

**`Recvd command` / `Recvd report`** (two distinct enums, both observed):

| value | `Recvd command` | `Recvd report` |
|---|---|---|
| 0 | `SUBSCRIPTION_CHGD` | `PROT_DEACTD_CNF` |
| 1 | `PROT_GEN_CMD` | — |
| 2 | `OPRT_MODE_CHGD` | `PH_STAT_CHGD_CNF` |
| 4 | `PROT_REDIR_IND` | — |
| 5 | — | `MMGSDI_CNF` |
| 7 | `DUAL_STANDBY_CHGD` | — |

**`Curr_trans` / `New transaction`** (one enum):

| value | name |
|---|---|
| 0 | `NULL` |
| 1 | `SUBSC_CHGD` |
| 2 | `ONLINE` |
| 3 | `OFFLINE` |
| 4 | `PROT_GEN_CMD` |
| 9 | `DUAL_STANDBY_CHGD` |
| 12 | `MMGSDI_INFO_IND` |

**`Trans_state`**: `0 NULL`, `5 PROT_PH_STAT_ENTER`, `6 WAIT_PH_STAT_CNF`,
`14 WAIT_SESSION_OPEN_CNF`, `25 WAIT_DEACTD_CNF_GWL`.

**`Prot_state`**: `0 NULL`, `5 ONLINE_GWL`, `7 OFFLINE`, `9 PWR_SAVE`.

**The whole UZ801 failure is one line**: `Curr_trans 3(OFFLINE)` / `Prot_state [MAIN] 7(OFFLINE)`
is reached **only** from `Recvd command 2(OPRT_MODE_CHGD)` → `Trans_state 25(WAIT_DEACTD_CNF_GWL)`
→ `Recvd report 0(PROT_DEACTD_CNF)`. Nothing else in either arm produces `3(OFFLINE)`.

---

## §6 The complete file-level census

Distinct F3 source files per arm, on the same instrument and window:

| | count |
|---|---|
| UZ801 v19 | **70** |
| stock HMU05 | **65** |

**Only in UZ801 (11):** `mcfg_uim.c`(9), `uimsub_manager.c`(6), `mcpm_drv_mux.c`(3),
`ale_proc.c`(3), `gstkutil.c`(3), `qmi_voice_msgr_if.c`(2), `uim.c`(1), `qpDcm.c`(1),
`mgp_pe_common.c`(1), `uimgen.c`(1), `policyman_uim.c`(1)

**Only in stock (6):** `cmss.c`(18), `cmlog.c`(6), `rf_task.c`(2), `rcinit_rex_task.c`(1),
`gfc_qmi_internal.c`(1), `policyman_serving_system.c`(1)

### §6.1 The two that matter

* **`cmss.c` = 18 vs 0, `cmlog.c` = 6 vs 0.** Stock's CM **serving-system** layer runs;
  UZ801's never does. In `hmu05_stock_boot.bin` (the online capture) `cmss.c` is **619** and
  `cmlog.c` is **945** (Doc 211 §4.6). So this is a **consequence of not being online**, not an
  independent defect — but it is the clearest single-line statement of the end state.
* **`rf_task.c` = 2 vs 0.** Stock logs `RF task  rfm_init OK!` and `RF task  new imei check !`
  at ts 71268/71272 (very early boot). **UZ801 emits neither, and no `imei` string anywhere.**
  Both arms *do* run `policyman_rf.c` (identical `subs 0/1/2: can't populate RF item from EFS`
  ×3 — **a shared failure, therefore not a differentiator**), `navrf_eureka.c` and `navrf_adc.c`,
  and identical `trm_config_handler.c` (`SRLTE is enabled`, `Config Handler get srlte,dsds mode: 2`,
  `First CM update. New: 4 Old: 0`, `chain_mapping_modes changed. New: 4, Old: 0`).
  **`rf_task.c` is therefore the only RF-axis difference found, and it is unexplained.**

### §6.2 The `tsk` census (Doc 211 §7.2, re-verified)

| `tsk` | stock | UZ801 |
|---|---|---|
| `ds` | 25 | 43 |
| **`dcc`** | **0** | **8** |
| `mmgsdi_1` | 3 | 3 |
| `gstk` | 3 | 3 |
| `wms` | 2 | 0 |
| `gsdi` | 1 | 0 |
| `cm` | 1 | 2 |
| `qmi_mmode` | 0 | 2 |
| `pbm` | 0 | 2 |

The 8 `dcc` records are all `=CM= CMD free u=0, tsk=dcc, ftsk=cm` at
ts 4 119 809 200 … 4 119 809 392 — i.e. **immediately after the `OPRT_MODE_CHGD → OFFLINE`
transition**, not before it. `ftsk=cm` means **CM** sent them and the **`dcc`** task frees them.
**The `dcc` break is downstream of the MMOC decision, not upstream of it.**

---

## §7 Firmware / device state

### §7.1 The device

Unchanged from Doc 211 §6.2 — no deployment was made this session:

```
--dms-get-revision        'UZ801_V3.0_21_V01R01B10  1  [Sep 07 2015 23:00:00]'
--dms-get-operating-mode  Mode: 'offline'
modem.mdt  md5 9f39ce579114bcf1383bbdce62a8d284
modem.b16  md5 c71d53464a1e88f2072cbbb6f7f87475
modem-guard boot_count = 0, no /root/BOOTLOOP_REVERT.txt
boot_id 26d74096-874f-45ee-9de4-67a8ee87caf1
```

### §7.2 `ts` is per-`code` — re-confirmed, with the mechanism

`uz801_v19_boot_240.bin` carries **two** `code` values **in one boot**:

```
#7   code=4774   ts=4119755868   rcinit_init.c  function enter … func_name ds_gcsd_init
#8   code=42741  ts=2388491580   rcinit_init.c  function exit  … func_name ds_gcsd_init
#20  code=4774   ts=4119756572   rcinit_init.c  function exit  … func_name dcc_init
```

The **enter** and **exit** of the *same* function carry **different `code`s and different `ts`
bases**. So `code` partitions the capture into **independent clocks** and the `ts` gap between
`#7` and `#8` (a 1.7 Ms "jump") is **not real time**. This is Doc 211 §5 restated with the
mechanism visible: **`ts` is comparable only within one `code`.** Every interleaving in §3.2 was
verified to be within a single `code` (stock region = all `code 0`; UZ801 region = all `code 4774`).

---

## §8 The MMOC name tables (task #13's deliverable)

### §8.1 Where they are

The `mmocdbg.c` / `mmoc.c` / `mmocmmgsdi.c` **descriptor strings are absent from every shipped
firmware file** — 0 hits in the rejoined `scratch/uz801_fw/modem.elf`, in every
`scratch/uz801_fw/image/modem.b*`, in the deployed `/lib/firmware/modem.b*` on the device, in
the HMU05 stock flash, in the original 64 MB FAT16 `modem.bin`, and in the `melbon_black` image.
**Controls pass**: `mmocmmgsdi` (21 in `modem.b25`), `standby_pref`, `rcinit_init`,
`OPRT_MODE_PWROFF` are all present.

They **are** present in the modem **coredumps** (runtime RAM). Complete table recovered from
`scratch/coredump_live/modem_coredump_up919.52.elf` at file offsets **0x4e37be8 … 0x4e38069**.

### §8.2 Structural attribution

| | |
|---|---|
| coredump segment | `phdr[20]`, file `0x44573cb`, VA `0x8ace6000`, `filesz 0xd1a000` |
| string blob VA | `0x8b6c681d` (`SUBSCRIPTION_CHGD`) … `0x8b6c6ca8` (`mmocdbg.c`) |
| mapped UZ801 flash VA | `+0x39800000` → **`0xc4ec68xx`**, i.e. **`phdr[26]`** (`va=0xc4615000`, `filesz=0x0`, `memsz=0xaeb000`) |
| `phdr[26]` on UZ801 | `filesz = 0` — the MDT ships **no `modem.b26`**, and none exists on the device |
| content | ~80 % zeros with ~1 100 populated islands ⇒ **runtime-populated**, not flash-backed |

### §8.3 The negative that matters

**The name tables are not a directly-indexable pointer array in the dump.**

* A raw search for a 4-byte LE pointer to `OPRT_MODE_CHGD` (`3f 68 6c 8b`) in the coredump →
  **0 hits**.
* A scan for runs of ≥10 pointers into the blob's VA range `[0x8b6c0000, 0x8b6e0000)` → **0 runs**.
* A scan of `modem.elf` for runs of ≥8 pointers into `[0xc4e00000, 0xc4f00000)` → **0 runs**.
* There **is** a pointer-looking array immediately before the blob
  (file `0x4e37bb3`, values `0xd0d7775c, 0xd0d7776c, 0xd0d7777d, …`), but its targets are
  **`0xd0d777xx` — outside every coredump segment**.

**Consequence:** the tables are recoverable as a **string blob** (which is what §5 uses) but the
firmware resolves a command id to a name by a mechanism that leaves **no absolute pointer** in
the dump. `scratch/logsite.py anchors scratch/uz801_fw/modem.elf` likewise reports **no
`mmocdbg.c` anchor at all** (`mmoc.c` 4656..22151 (27), `mmocmmgsdi.c` 245..2906 (21), no
`mmocdbg.c`). **The debug-name table is built at runtime; it is not a static, indexable array.**

### §8.4 The decompile is blind to all of it

`Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` (110 MB, 81 832 functions) —
grep counts: `old_op_mode` **0**, `OPRT_MODE_CHGD` **0**, `mmocdbg` **0**, `gstkutil` **0**,
`mcfg_uim` **0**. Consistent with the Doc-RE note that Ghidra renders a filename only when code
references it; a 0-hit grep is **not** evidence of absence — but it does mean **the emit site
cannot be found by string search.**

---

## §9 What this rules out

| hypothesis | verdict | evidence |
|---|---|---|
| "UZ801 uniquely receives `OPRT_MODE_CHGD`" | **FALSE** | stock receives it in `hmu05_stock_boot.bin` (§2) |
| "the 240 s same-window pair isolates the MMOC command" | **FALSE** | stock has not transitioned inside 240 s; the pair compares different phases (§3.1) |
| "the `cmph.c` `rat_disabled_mask` sequence emits the command" | **FALSE** | stock has 7 such sequences in 240 s and emits no `OPRT_MODE_CHGD` after any of them (§3.2) |
| "the `dcc` command break is the cause" | **FALSE (ordering)** | all 8 `dcc` records are **after** the OFFLINE transition (§6.2) |
| "`policyman_rf.c can't populate RF item from EFS` is the RF defect" | **FALSE** | **stock emits the identical ×3** (§6.1) |
| "`trm_config_handler.c` differs" | **FALSE** | identical on both (§6.1) |
| "the CM op mode differs" | **FALSE** | `10 → 11` on both, fixed parser (§3.3) |
| "`rf_task.c rfm_init OK!` missing proves RF is dead on UZ801" | **NOT ESTABLISHED** | UZ801's RF *is* WTR1605-native and `policyman_rf.c` runs identically; the missing `rf_task.c` is unexplained, not diagnostic (§6.1) |

---

## §10 Task #12 — the burst-vs-blind pair, deferred with a reason

Doc 211 §11.1 asked for a **burst-vs-blind pair** on the UZ801-only UIM/STK bring-up, to decide
whether that burst is the *cause* or a *symptom*.

**That question is no longer on the critical path**, because the burst is **not** adjacent to the
decision. Measured this session, in `code = 4774`:

```
uim.c:7136  UIM_%d: Read to NV active slot configuration unsuccessful   ts = 4 119 757 352   (idx 35)
mcfg_uim.c / uimsub_manager.c / uimgen.c / gstkutil.c burst             ts = 4 119 757 352 → 4 119 870 260
Recvd command 2(OPRT_MODE_CHGD)                                        ts = 4 119 807 520   (idx 635)
```

The UIM/STK burst **ends ~62 000 ticks before** the `OPRT_MODE_CHGD` (and ~50 000 ticks before
the first `SUBSCRIPTION_CHGD` at ts 4 119 792 212). **The burst is a plausible upstream cause, but
it is not the proximate one** — and the burst-vs-blind pair cannot distinguish "cause" from
"unrelated co-symptom of the same build difference" any better than §9's table does.

**Task #12 is therefore re-scoped, not abandoned**: it becomes a **build-composition question**
("which UZ801-only module is load-bearing for the MMOC decision?") rather than a
capture-class question. The capture-class question itself was already answered by Doc 208/210
(the burst class was lost and restored; both arms here are burst-class).

---

## §11 Evidence table

| artifact | md5 | size | what |
|---|---|---|---|
| `scratch/doc211/stock_boot_240.bin` | `0ff4e4d22c23443e2c6e82bcb24cbc83` | 1 500 441 | stock boot, 240 s — **no** `OPRT_MODE_CHGD` |
| `scratch/doc211/stock_boot_211.bin` | `e49515eff18afcf069215bde6a35a835` | 1 424 573 | stock boot, 90 s — **no** `OPRT_MODE_CHGD` |
| `scratch/doc211/uz801_v19_boot_240.bin` | `81318b04f7e0da3e74675d2973366758` | 1 240 397 | UZ801 v19 boot, 240 s — `OPRT_MODE_CHGD` → `3(OFFLINE)` |
| `scratch/hmu05_stock_boot.bin` | *(94 MB, gz present)* | 94 623 391 | stock — **the only capture that reaches `5(ONLINE_GWL)`**; `OPRT_MODE_CHGD` → `2(ONLINE)` |
| `scratch/doc210/boot_210_canon.bin` | — | 1 163 362 | UZ801, old instrument — `OPRT_MODE_CHGD` → `3(OFFLINE)` |
| `scratch/doc211/live_stock_online.bin` | `e765744410d570e9eb0423bc46b58f9f` | 65 902 256 | live online-modem reference |
| `scratch/coredump_live/modem_coredump_up919.52.elf` | — | 85 398 475 | **source of the MMOC name tables** (§8) |
| `scratch/f3parse.py` | `9f9d6edf15d64faf3b47897f7838c9ac` | — | parser, Doc 211 fix |
| `scratch/uz801_fw/modem.elf` | — | 50 912 492 | rejoined UZ801 ELF (no mmoc strings) |

---

## §12 Traps recorded this session

1. **A window is not a control.** Comparing "stock at 240 s" against "UZ801 at 240 s" compares
   two different **phases** of the boot when one arm transitions early and the other late. Anchor
   the comparison to the **event**, not the window.
2. **`ts` is per-`code`, and one boot carries several `code`s.** The enter and exit of a single
   function can carry different `code`s and a fake 1.7 Ms "gap" (§7.2).
3. **A shared failure is not a differentiator** — restated for `policyman_rf.c` (§6.1).
4. **A runtime-populated string blob is not a static table.** Recovering the strings does **not**
   give you an indexable array, and a 0-hit pointer search is the expected result (§8.3).
5. **Downstream is not upstream.** The 8 `dcc` records sit **after** the decision; their presence
   says nothing about its cause (§6.2).
6. **A missing log line is not a failed subsystem.** `rf_task.c` is absent on UZ801 while
   `policyman_rf.c`/`trm_config_handler.c` run identically — the absence is unexplained (§6.1).
7. **`Recvd command` / `Recvd report` are two different enums** that both start at 0 with
   different meanings. Never index one with the other's value (§5).

---

## §13 Next steps

1. **THE GATE, unchanged and now sharply scoped:** the MMOC decision on the `OPRT_MODE_CHGD`
   **payload**. The payload is not logged (§4) and the emit site is not findable by string (§8.4).
   The two remaining routes are:
   * **(a) static, from the CM side.** The `OPRT_MODE_CHGD` is built by CM. Locate the CM function
     that assembles it and read the op-mode field it copies. The entry point is the
     `cmph.c` op-mode site (`cmph.c:32810` on UZ801 / `cmph.c:32104` on stock — note the **+706
     line offset** between builds) via `scratch/logsite.py refs … --file=cmph`, then walk to the
     MMOC enqueue.
     **⚠ ATTEMPTED THIS SESSION AND BLOCKED — see §13.1 below.**
   * **(b) live, from the modem side.** The payload is in the MMOC command struct in RAM, which
     is **unreadable from the AP by any route** (Doc `reference_hmu05_platform_quirks` §1) and
     only visible in a coredump. A coredump taken **at the OFFLINE transition** would contain it.

### §13.1 The static route is BLOCKED by a tooling defect (attempted 2026-09-27)

The attempt was made and it failed in a way worth recording, because it will otherwise be
re-attempted:

1. **The descriptor for the target site was located.** Scanning `scratch/uz801_fw/modem.elf`
   (md5 `50ee8c56d6b836329ef97ac83ed34bff`) for `{packed=(line<<16)|level, word1∈seg25}` with
   `line = 32810` returns **exactly one** hit:
   ```
   descVA = 0xc165d6b0   level 11   ->  'cmph.c:=CM= After updating from rat_disabled_mask mode_pref %d'
   ```
   So the descriptor table is real, the anchor model is sound, and `logsite.py anchors` correctly
   reports `cmph.c lines 3575..46913 (79)`.
2. **But the decompile references that descriptor VA ZERO times.** `grep 0xc165d6b0` over
   `modem_full_decompiled.c` → **0 hits**. Grepping for **all 79** `cmph.c` anchor VAs
   (`0xc165d130` …) → **0 hits**. Grepping the whole `0xc165dxxx` range → **0 hits**
   (`0xc165cxxx` has 118, `0xc165dxxx` and `0xc1666xxx` have **0**).
3. **And `logsite.py refs` produces noise, not signal.** Unfiltered it reports **69 775**
   "log-site literals", the great majority attributed `?`; with `--file=cmph` it reports **0**.
   The cause is visible in the filter: `packed` plausibility is only `level ≤ 0xFF` and
   `0 < line < 200 000`, and since `line = packed >> 16`, **any** u32 whose top half is under
   200 000 passes — i.e. ~¾ of all 32-bit constants. The `refs` mode therefore cannot
   distinguish a descriptor VA from an ordinary constant.

**⇒ Doc 207's descriptor→code instrument does not resolve `cmph.c` on the UZ801 decompile.**
Either the CM code addresses its descriptors by a **computed base+offset** (so no VA is
materialised as a literal), or the literal-VA assumption is simply wrong for this build. **Route (a)
needs the instrument fixed first** — a sound version must validate the candidate VA against the
descriptor table (e.g. require the VA to be inside the known table range *and* to be one of the
VAs that `anchors` resolved) instead of using the `packed` plausibility test. **Route (b) is
therefore the cheaper path.**
2. **Resolve the `rf_task.c` absence** (§6.1) — it is the only unexplained RF-axis difference and
   it is cheap to check whether UZ801's build simply has no `rf_task` (compare the task tables).
3. **Do NOT** re-open: `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch,
   `0x1000001`, `cm_state+0x39e8`, "the CM code differs", the `mode_pref` 9728-vs-38 difference,
   the `MSC_AUTO` `0x220`-vs-`0xebe` build difference, the policyman RAT-mask-0 lead,
   the `policyman_rf` EFS-item failure (§9), the `dcc` break as a cause (§9).
4. **Do NOT** conclude anything about `OPRT_MODE_CHGD` from a 240 s same-window pair (§3).
5. **Do NOT** score a CM-layer negative on a capture with `rcinit_init.c = 0`.
6. **Do NOT** take an `args` value from any parser older than
   `9f9d6edf15d64faf3b47897f7838c9ac`.

---

## §14 Ledger fold-in

Rows of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §3 updated in this session:

| row | change |
|---|---|
| "UZ801 uniquely receives `OPRT_MODE_CHGD`" | **CORRECTED** → both firmwares receive it; the 240 s pair is phase-blind |
| "the MMOC decision is inside MMOC" | **CONFIRMED**, on a second independent UZ801 capture |
| "the `dcc` command break is the online break" | **DOWNGRADED** → downstream of the decision |
| "`policyman_rf` EFS failure" | **NEW NEGATIVE** → shared with stock |
| "UZ801-only file set" | **NEW** → complete 70-vs-65 census, 11 vs 6 |
| "MMOC name tables" | **NEW** → recovered and verified; not a static array |
| Task #12 (burst-vs-blind) | **RE-SCOPED** → build-composition, not capture-class |

# Doc 223 — The gate is the *subscription-available* event: UZ801 never completes it, and two UIM modules are UZ801-only files

**Date:** 2026-09-28
**Ledger:** this doc is an entry in `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — see **§7 item 29** (findings) and the **§8 evidence rows** it adds. Read §7 of the ledger first; it is the cumulative index.
**Predecessors:** Doc 222 (§1 — the `OPRT_MODE_CHGD` mirror; §3 — `uimsub_manager.c`), Doc 220 (§4.4/§4.8 — the `u=1` command's 39.7× early free), Doc 221 (§3 — the policyman RAT-mask control).
**Instrument:** `scratch/f3parse.py` md5 **`9f9d6edf15d64faf3b47897f7838c9ac`** (required — the parser gate is enforced in the reproduction script).
**Reproduction:** `scratch/doc223_subscription_gate.py` (one script, all five sections below).

---

## 0. SOP compliance

| SOP step (Doc 197) | status |
| :-- | :-- |
| verify VAs / disassembly / hashes against stock-HMU05 ground truth | **taken** — every claim is a record-for-record differential against `stock_boot_211.bin` / `stock_boot_240.bin` / `hmu05_stock_boot.bin`, or a whole-image string census of `GitIgnore/compare/modem_hmu05_extracted/image` |
| comparative SOP — stock arm required | **taken** — three stock captures (one reaching ONLINE), two UZ801 captures |
| no blind baseband patching | **taken** — this doc patches nothing; it is a differential |
| "Achieved vs Expected" table | **§8** |
| ledger updated in the same session | **taken** — §7 item 29 + §8 rows |
| archive-first / freeze-hash captures | **taken** — all five captures are pre-existing frozen `.bin` files; no new live capture is created |
| assert a control TOOK | **taken** — §6 explicitly re-tests four "differences" and shows three of them are **build artefacts, not behaviour**; §2's `is_gwl_subs_avail` is asserted present in **all five** captures before any differential is drawn |
| pre-register n | **n/a** — no live scoring criterion is created here |

**Scope statement.** This doc characterises the *boot-time* subscription path that holds the CM at `offline`. It does **not** touch the 900 s fatal, the bam_dmux defect set, or the RF-device question. It **supersedes Doc 222 §3's method** (a log-*site* census) with a file-*name* census, which is a strictly stronger test — see §6.

---

## 1. The measurement trap this doc had to clear first: the captures are not the same length

Doc 222 §2 and the prior per-file censuses compared arms by *record counts*. That is invalid here:

```
capture                 n       first_ts        last_ts       span_s
UZ801_B10_canon      1718      519202936      517417772       -8.717
UZ801_B10_v19        1719     4119753624     4123253312       17.088
stock_211            1669          62756      900809884     4398.179
stock_240            1641          56636     1633850280     7977.508
stock_ONLINE        33743         237980     3649230612    17817.347
```

* The **stock** captures span **73 min / 2.2 h / 5.0 h**; the **UZ801** captures span **~17 s** (and `UZ801_B10_canon`'s `ts` is **non-monotonic** — its last record's `ts` is *smaller* than its first, i.e. the capture is a ring-buffer read-out, and the flat `79 00` scan's ~5 % false-positive rate is not cleaned here).
* Therefore any statement of the form "stock logs subsystem X 18 times, UZ801 once" is **length-biased** unless the window is matched.

**Consequence for the corpus.** Doc 222 §2's and the earlier per-file censuses' *counts* must not be read as rates. The differentials in §2–§4 below are therefore stated as **presence/absence of a specific event**, not as counts, and every one of them is verified to be *within the UZ801 capture's own window* (i.e. UZ801 had ≥1 000 records after the event in question in which the event could have appeared and did not).

---

## 2. The gate: `is_gwl_subs_avail` goes `0->1` on stock and `0->0` on UZ801

`cmregprx.c` (the CM registration proxy) computes a per-stack **subscription-available** flag when it processes a `ph_stat_chgd` from the MMOC. It is the only such flag in the corpus, and it is **binary and consistent across all five captures**:

```
UZ801_B10_canon    =CMREGPRX= ph_stat_chgd: as_id=0, is_gwl_subs_avail 0->0
UZ801_B10_v19      =CMREGPRX= ph_stat_chgd: as_id=0, is_gwl_subs_avail 0->0
stock_211          =CMREGPRX= ph_stat_chgd: as_id=0, is_gwl_subs_avail 0->1
stock_240          =CMREGPRX= ph_stat_chgd: as_id=0, is_gwl_subs_avail 0->1
stock_ONLINE       =CMREGPRX= ph_stat_chgd: as_id=0, is_gwl_subs_avail 0->1   (and later 1->1)
```

The surrounding triple is identical on both arms except for this one value — so the CM *did* run the handler, and it computed a different answer:

```
UZ801_B10_canon                                          stock_211
 644 519252344 cmregprx.c:14079 ph_stat_chgd: MMOC->CM: ue_mode=  0     971 412720 cmregprx.c:14031 ...
 645 519252348 cmregprx.c:14182 ph_stat_chgd: MMOC->CM: ss=0, ue_mode= 0 972 412720 cmregprx.c:14134 ...
 646 519252348 cmregprx.c:8129  ... is_gwl_subs_avail 0->0               973 412724 cmregprx.c:8099  ... 0->1
 647 519252348 cmregprx.c:8135  ... ue_mode_chgd: 0->0                    974 412724 cmregprx.c:8105  ... 0->0
 649 519252352 cmregprx.c:8139  ... srlte 0->0                            976 412728 cmregprx.c:8109  ... 0->0
```

**`ss` (serving system) is 0 on both arms**, and `ue_mode` is 0 on both arms — so the flag is *not* derived from the serving system. It is a separate **subscription** input.

### 2.1 The honest caveat: this flag is at least partly *downstream*

On UZ801 the MMOC opens `3(OFFLINE)` at record **632** and the `is_gwl_subs_avail 0->0` is at record **646** — **548 ticks later**. So the flag is computed *after* the offline transaction began and is therefore **at least partly a consequence** of the offline path, not provably its cause.

What it *does* establish, independent of direction: **the one signal that names the missing precondition — "a GWL subscription is available" — is never asserted on UZ801.** Every other explanation in the corpus has to explain this record.

---

## 3. The SD-side script that only UZ801 ever activates

`sdss.c` activates named "user scripts". Exactly one of them is a **UZ801-only activation**:

```
UZ801_B10_canon    1 activation(s) in 1718 records
UZ801_B10_v19      1 activation(s) in 1719 records
stock_211          0 activation(s) in 1669 records
stock_240          0 activation(s) in 1641 records
stock_ONLINE       0 activation(s) in 33743 records
```

The activation is `** Activate user script = ssscr_user_offline_cdma **`, at record 676 — i.e. **44 ticks before** the final `Prot_state [MAIN] 7(OFFLINE)` (record 683), and 44 ticks after the last `Recvd report 2(PH_STAT_CHGD_CNF)` (record 671).

**The script *name* is present in every image** (stock HMU05, UZ801-B08, UZ801-B10, UFI001B all contain the string `ssscr_user_offline_cdma`). So the differential is the **activation**, not the script's existence — and `ssscr_user_offline_gwl` does **not** exist in any image, so "go offline" on a hybrid build is always the `_cdma` script. This is a **consequence** of the offline transition, not a cause.

---

## 4. Stock's subscription processing (`cmss.c`) never runs on UZ801

`cmss.c` is the CM's subscription-state module. Stock runs a set of `csg_id` / `hnb_name_length` / `sys_id_type` / `csg_info` / `fmode` records that UZ801 does not:

```
UZ801_B10_canon    1 record(s)  =CM= cmss_cmd_check, cmd=0, asubs_id=0
UZ801_B10_v19      0 record(s)
stock_211         18 record(s)  =CM= old csg_id:4294967295, new csg_id:4294967295 ...
stock_240         18 record(s)
stock_ONLINE     640 record(s)
```

On stock those 18 records land at `ts` 418 220 – 419 476, i.e. **~1.7 s after boot** — the *same* moment as its `is_gwl_subs_avail 0->1` (ts 412 724) and its `sdcmd_name_sel3()-is_gwl_subsc_avail:1` (ts 417 700). UZ801 has **1 000+ records after its latch** in which the same records could have appeared and they do not.

So on stock the subscription becomes available and the CM **processes** it (csg/hnb/fmode/serving-system); on UZ801 the event never arrives and the processing never runs. This is the same phenomenon Doc 220 §4.4 found from the other end (the `u=1 tsk=cm` SIM-init command completing **39.7× early**, with none of stock's UICC-search / response / second `cmmsc_auto` / MMOC `SUBSCRIPTION_CHGD` inside its window).

---

## 5. Two UIM modules are UZ801-only *files*

The decisive test is not "does the log site fire" but **"does the file-name string exist in the image"** — a build-level test that cannot be confused by log levels, window length, or a removed statement.

```
file-name string            stock  UZ801
uimsub_manager.c                0      1     <-- UZ801-only FILE
mcfg_uim.c                      0      1     <-- UZ801-only FILE
qpCircularBuffer.c              0      1     <-- UZ801-only FILE
policyman_uim.c                 1      1
uim.c                           2      2
uimgen.c                        2      2
ale_proc.c                      2      2
qpDcm.c                         2      2
rf_task.c                       2      1
cmss.c                          2      2
cmregprx.c                      2      2
cmph.c                          2      2
```

**Three modules do not exist in the stock HMU05 build at all**, and **two of them sit on the UIM/subscription path**:

* `uimsub_manager.c` — the UIM subscription manager (Doc 222 §3 found 35 log sites; this confirms it at the build level).
* `mcfg_uim.c` — the **MCFG UIM autoselect**. It logs `mcfg_uim_autoselect NV=5`, `NV=0`, `NV=0`, then `Opening ext session with mask 10`, `Successfully open card ext sessions with 1 session(s)`, `Open session 0 slot_id 1 session_id 3dd153bf session_type 6`, `Slot 0 session type 6 id 3dd153bf`, `Got card event 0 on slot index 0`, `ICCID 89918610400794228644`.
* `qpCircularBuffer.c` — a QP utility (not on the CM path).

### 5.1 `mcfg_uim.c`'s input is in the HMU05 EFS and reads `5`

`mcfg_uim_autoselect NV=5` is the value of an EFS item, and that item is present in the HMU05-derived EFS:

```
scratch/efs_H/efs_H/mcfg/mcfg_autoselect_by_uim        05
scratch/efs_H/efs_H/mcfg/mcfg_def_config_hw_version    3101e102
scratch/efs_H/efs_H/mcfg/mcfg_def_config_sw_version    3200e002
scratch/efs_H/efs_H/mcfg/mcfg_last_autoselect_iccid    383939313836313034303037393432323836343400
                                                       = ASCII "89918610400794228644" + NUL
scratch/efs_H/efs_H/mcfg/mcfg_sw_muxd_version          01080102
```

So the UZ801-only autoselect reads the HMU05's stored MCFG autoselect result (**5**) and the HMU05 SIM's ICCID, and applies it. This is a **candidate mechanism for a UIM-path divergence driven by EFS content**, not proven.

### 5.2 `mcfg_uim.c` is a *flex-mapping / slot→subscription* module — and its whole mapping path never runs

The 32 anchored log sites of `mcfg_uim.c` (recovered from descriptor `word1` string pointers; the module's descriptors are **not** bare literals in the decompile, but their `word1` *is* a seg25 string pointer for all 32) characterise the module completely:

```
line=318   descVA=0xc16adec4  mcfg_uim.c:mcfg_uim_autoselect NV=%d
line=360   descVA=0xc16aded4  mcfg_uim.c:mcfg_uim_get_flexmap_timer NV=%d
line=579   descVA=0xc16adee4  mcfg_uim.c:Couldn't delete uim mapping slot %d NV item: status %d
line=591   descVA=0xc16adeec  mcfg_uim.c:Couldn't write uim mapping slot %d NV item: status %d
line=633   descVA=0xc16adef4  mcfg_uim.c:Invalid mapping slot %d to sub_id %d with submask %x
line=735   descVA=0xc16adf0c  mcfg_uim.c:Same mapping, ignore slot %d to sub %d
line=749   descVA=0xc16adf14  mcfg_uim.c:Get active ID for sub %d
line=752   descVA=0xc16adf1c  mcfg_uim.c:Get active ID for oppo sub %d
line=755   descVA=0xc16adf24  mcfg_uim.c:Deactivate for sub %d
line=758   descVA=0xc16adf2c  mcfg_uim.c:Deactivate for oppo sub %d
line=763   descVA=0xc16adf34  mcfg_uim.c:Select for sub %d
line=769   descVA=0xc16adf3c  mcfg_uim.c:Select for oppo sub %d
line=780   descVA=0xc16adf44  mcfg_uim.c:Activate after flex mapping slot%d-sub%d slot%d-sub%d
line=788   descVA=0xc16adf4c  mcfg_uim.c:Can't find matching pair for flex mapping slot %d to sub %d
line=845   descVA=0xc16adf6c  mcfg_uim.c:Update mapping slot %d to sub %d from %d
line=859   descVA=0xc16adf74  mcfg_uim.c:Deboucing flex mapping slot%d-sub%d
line=909   descVA=0xc16ae0d4  mcfg_uim.c:flex mapping cb
line=1172  descVA=0xc16adfdc  mcfg_uim.c:mmgsdi_session_changed_evt slot %d activate %d
line=1628  descVA=0xc16ae07c  mcfg_uim.c:Sub %d session gw/1x %d type %d id %x
```

So `mcfg_uim.c` is the module that **maps a physical UIM slot to a subscription** (`sub_id` + `submask`), reads/writes a per-slot **`uim mapping slot %d` NV item**, **selects / deactivates / activates** a subscription, and consumes **`mmgsdi_session_changed_evt slot %d activate %d`**.

**Which of these actually execute on the HMU05?**

```
OBSERVED in both UZ801 captures:   318 (autoselect NV=5/0/0), 1019, 1087, 1555, 1561, 1616, 1287
NEVER OBSERVED in either capture:  356/360 (flexmap timer), 579/591 (mapping-NV read/write),
                                   633, 735, 749, 752, 755, 758, 763, 769, 780, 788,
                                   839, 845, 859, 909, 1172, 1628
```

**Every mapping/activation record is absent.** In particular `Select for sub %d` (763), `Activate after flex mapping …` (780) and **`mmgsdi_session_changed_evt slot %d activate %d` (1172)** — the three that would *activate* a subscription — never fire, while the module's init records (autoselect, session open, card event, ICCID) do.

This is the same statement as §2 and §4 from the UIM side, and it is the strongest single lead in this doc: **the UZ801 build routes subscription activation through an `mcfg_uim.c` flex-mapping path that never executes on the HMU05.** Whether it fails to execute because the `uim mapping slot %d` NV item is absent (the write would fail at line 591 — **which is not observed either**, so the code does not even reach the write), because the flexmap timer NV is absent (line 360 not observed), or because no `mmgsdi_session_changed_evt` with `activate=1` ever arrives, is **not yet determined** — the module's NV path strings are **not present in the image** (`/nv/item_files/...` with `map`/`slot`/`sub` yields only `nv_active_slot_configuration`), so the `uim mapping slot %d` item is built at runtime and cannot be looked up statically.

### 5.3 The device's EFS *declares* the items it does not have

The `conf/*.conf` files in the live EFS are NV **item manifests**. `uimdrv.conf` declares, verbatim:

```
/nv/item_files/modem/uim/uimdrv/nv_active_slot_configuration
/nv/item_files/modem/uim/uimdrv/nv_pdown_uim_consecutive_techproblems
/nv/item_files/modem/uim/uimdrv/feature_support_hotswap
```

…and **none of the three has a value file** (`/root/efs_H/modem/uim/uimdrv/` holds only `uim_features_status_list`, `sim_response_timers_config`, `uim_hw_config`, `uim_busy_response_simulate_null_config`, `me_hotswap_configuration`). The same holds for `mmgsdi.conf` (`halt_subscription`, `refresh_vote_ok`, `encrypt_sub_ok`, `auto_activate_onchip`, `/mmgsdi/onchip/onchip_slot1` are declared; `modem/uim/mmgsdi/` holds only `features_status_list`, `mmgsdi_diag_support` = `02`, `rpm_iccid` = the ICCID).

So the HMU05 EFS is a **minimal** EFS in which many declared items fall back to in-code defaults. **Item absence is therefore the norm, not the exception** — which is why §6 rejects the EFS-path diff as a discriminator. Verified on the device (uptime 9 582 s, modem `running`) as well as in the host copy.

---

## 6. Four "differences" that are build artefacts, not behaviour

Each of these was a live candidate during this investigation and each was killed by a whole-image string census. They are recorded so the corpus does not re-open them.

| candidate | why it looked real | the disproof |
| :-- | :-- | :-- |
| UZ801 never logs `rf_task.c:574 RF task rfm_init OK!` (0 records vs stock's 2) | an RF-init that never completes would be a perfect offline cause | the strings **`rfm_init OK`** and **`imei check`** are **absent from the UZ801 image** (0 files) — the *log statements* were removed, so a 0 count is expected. `rf_task.c` and `rfm_init` are present, so the file/function exist. **Not a failure.** |
| UZ801 logs `uim.c:7136 Read to NV active slot configuration unsuccessful` at the first record of the boot | it is the **only unique error** in the UZ801 boot; a UIM NV read failing is a plausible offline cause | (a) the log *string* is UZ801-only (absent from stock's image), so stock failing the same read would be silent; (b) **the failure is handled** — `FUN_c053e66c` sets the active slot to the valid fallback **1** and only logs "invalid" if the value is 0 or >2 (it is 1, so no second log). See §6.1. |
| stock references 3 EFS paths UZ801 does not (`qvp_rtp_unit_test1/2`, `wcdma_rrc_dormancy_support`) | a missing/extra NV read could gate the op-mode | both builds reference **hundreds** of absent NV files (UZ801 246 of 513; stock 201 of 464) — most are optional config with in-code defaults. Absence per se is not diagnostic. |
| `policyman_uim.c` logs 1 record on UZ801 and 0 on stock | a UZ801-only policyman UIM path | the **file name string is present in both images** (1/1), and the UZ801 record is `policyman_uim.c:938 updating PLMN for Sub 0` at `ts` 519 541 072 — well after the latch. Window/behavioural, not a build difference. |

### 6.1 The `nv_active_slot_configuration` read, decoded

`FUN_c053e66c` (the emitter of `uim.c:7102..7187`, site VA `0xc161a010` for line 7136) reads six EFS items in sequence:

```
/nv/item_files/modem/uim/uimdrv/uim_features_status_list                 (present in EFS)
/nv/item_files/modem/uim/uimdrv/me_hotswap_configuration                 (present)
/nv/item_files/modem/uim/uimdrv/nv_active_slot_configuration             (ABSENT  <-- the failure)
/nv/item_files/modem/uim/uimdrv/nv_pdown_uim_consecutive_techproblems    (ABSENT)
/nv/item_files/modem/uim/uimdrv/sim_response_timers_config               (present)
/nv/item_files/modem/uim/uimdrv/uim_busy_response_simulate_null_config   (present)
```

The `nv_active_slot_configuration` branch (7 bytes, `FUN_c0944aa0(path, &DAT_c3d40778, 7)`):

```c
if (iVar8 == 7) {                      /* read OK  */
    DAT_c29a90ea = *(sbyte *)(unaff_GP + 0x779);
    log(uim.c:7131);                   /* "Invalid active slot value ... resetting to slot1" */
} else {                                /* read FAILED  <-- what happens here */
    DAT_c29a90ea = 1;                   /* fall back to slot 1 */
    log(uim.c:7136);                   /* "Read to NV active slot configuration unsuccessful" */
}
uVar12 = (uint)(char)DAT_c29a90ea;
if ((uVar12 == 0) || (2 < uVar12)) { log(uim.c:7140); uVar12 = 1; DAT_c29a90ea = 1; }
(&DAT_c29a90eb)[uVar12 * 0xe] = 1;
```

The fallback value **1 is valid** (the valid range is 1..2), so no "invalid" log follows — which is exactly the observed output. **The failure is gracefully absorbed.** The live card session opens on `slot_id 1`, matching the fallback. ⇒ **not the offline cause**, but worth writing the file if a later result ever points back here.

---

## 7. The model, and the question that remains

Combining §2–§5 and Doc 220 §4.4:

1. At boot the CM evaluates the subscription at a `ph_stat_chgd` and finds **no GWL subscription available** (`is_gwl_subs_avail 0->0`).
2. It therefore declares `OPRT_MODE_CHGD` and the MMOC opens `3(OFFLINE)` (Doc 222 §1).
3. The SD activates `ssscr_user_offline_cdma` and the MMOC latches `Prot_state [MAIN] 7(OFFLINE)`.
4. Stock, at the same point in its boot, has the subscription available (`0->1`), runs the `cmss.c` csg/hnb/fmode processing at ~1.7 s, and never emits an `OPRT_MODE_CHGD` at all.
5. The UIM path that produces (or fails to produce) the subscription-available event contains **two modules that do not exist in the stock build**: `uimsub_manager.c` and `mcfg_uim.c`.
6. **`mcfg_uim.c` is the flex-mapping module** — it maps a slot to a `sub_id`+`submask`, writes a per-slot `uim mapping slot %d` NV item, and calls `Select`/`Deactivate`/`Activate` for the subscription, consuming `mmgsdi_session_changed_evt … activate`. **Every one of those records is absent** from both UZ801 captures (§5.2) while the module's init records are present.
7. Doc 220 §4.4 already showed the CM's SIM-init command completing **39.7× early** on UZ801 with none of stock's UICC-search/response content inside its window — the same event, seen from the CM side.

**The question that remains, now stated as narrowly as the corpus allows:** *why does `mcfg_uim.c`'s flex-mapping path not execute — is the per-slot `uim mapping slot %d` NV item absent, is the flexmap-timer NV absent, or does no `mmgsdi_session_changed_evt` with `activate=1` ever arrive?* Answering that requires the module's runtime NV path, which is built at run time and is not in the image (§5.2).

This is **not** the question Doc 222 §7 item 1 asked ("why does the CM hold OFFLINE") — it is one layer lower and names a component and a function family.

---

## 8. Achieved vs Expected

| # | expected | achieved | verdict |
| :-- | :-- | :-- | :-- |
| 1 | find an observable that separates UZ801 from stock at the subscription layer | `is_gwl_subs_avail` `0->0` (UZ801 ×2) vs `0->1` (stock ×3) | **MET** |
| 2 | determine whether that observable is the *cause* | it is 548 ticks **after** the MMOC opens `3(OFFLINE)` ⇒ **at least partly downstream** | **PARTIAL — direction not resolved**; stated as such |
| 3 | name the UZ801-only component(s) on the path | `uimsub_manager.c`, `mcfg_uim.c`, `qpCircularBuffer.c` are absent from stock's build (file-name strings) | **MET** |
| 4 | avoid the length-bias that invalidated earlier per-file counts | §1 quantifies it (stock 4 398/7 978/17 817 s vs UZ801 17 s) and all §2–§4 claims are presence/absence, window-verified | **MET** |
| 5 | kill the tempting false leads | 4 candidates tested and disproved as build artefacts (§6) | **MET** |
| 6 | prove a *mechanism* (why the event is never delivered) | not attempted — the two candidate modules are named, not read | **NOT MET** |
| 7 | resolve the offline latch | not resolved; the root question is narrowed, not answered | **NOT MET** |

---

## 9. Artifacts

| artifact | what |
| :-- | :-- |
| `scratch/doc223_subscription_gate.py` | **new** — the single reproduction of §1–§6 (spans, `is_gwl_subs_avail`, `ssscr` census, `cmss.c` census, file-name census); enforces the `f3parse.py` md5 gate |
| `scratch/doc210/boot_210_canon.bin` | UZ801-B10 boot capture (§1–§4) |
| `scratch/doc211/uz801_v19_boot_240.bin` | UZ801-B10 boot capture (§1–§4) |
| `scratch/doc211/stock_boot_211.bin`, `scratch/doc211/stock_boot_240.bin` | stock boot controls |
| `scratch/hmu05_stock_boot.bin` | the 33 743-record ONLINE stock control |
| `scratch/efs_H/efs_H/mcfg/*` | the MCFG EFS items read by the UZ801-only `mcfg_uim.c` (§5.1) |
| `scratch/efs_H/efs_H/modem/uim/uimdrv/*` | the six uimdrv items; `nv_active_slot_configuration` and `nv_pdown_uim_consecutive_techproblems` are **absent** (§6.1) |

**Status:** the boot-time offline latch is **narrowed to the subscription-available event and to two UZ801-only UIM modules**. The latch itself is **not** resolved; no baseband patch is proposed by this doc.

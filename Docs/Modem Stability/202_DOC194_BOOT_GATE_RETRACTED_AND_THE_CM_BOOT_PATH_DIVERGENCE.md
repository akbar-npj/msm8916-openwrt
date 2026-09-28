# 202 — Doc 194's "boot-path gate" is RETRACTED: stock does **not** auto-online, `clean()` deleted two real ts clusters, and the true divergence is inside the `u=1, tsk=cm` CM command

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband `MPSS.DPM.1.0.1.C1-00121`, patched in `modem.b16`.
**v19 remains the deployed lineage** (`modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt`
`9f39ce579114bcf1383bbdce62a8d284`) — **no firmware was built, patched, deployed or rebooted in this
session.** Every result below is re-analysis of already-archived captures plus static RE.
**Status:** RESULTS + RETRACTIONS. Doc 194 §7's central claim and Doc 201 §6's "mask confound closed"
are both corrected; the campaign's named next step (Doc 201 §7.1 step 2) is **replaced**.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — **its §3/§7 are updated in the same
session as this doc.**

**Companions:** `194` (the UZ801 boot log and the stock baseline — **§2 item 4, §7 and §7.1 of which this
doc retracts**), `195` (v1–v14), `196` (the DIAG instrument), `197` (the ledger), `198` (v15/v16),
`199` (v18/GROUP 14), `200` (the online gate and the `dcc` break), **`201`** (the dead `0x1000001`
gate, the `cmmsc_auto` pivot — **§6 of which this doc corrects**).

---

## 1. SOP compliance

Per the mandatory five-step protocol (`133 §1`): **backup → ground-truth verification against stock HMU05
→ reconcile transport/config → surgical Hexagon patching + re-signing → live empirical validation**.

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **Yes, trivially.** Nothing on the device was read, written, patched or rebooted this session. All inputs are files already in the repo (`scratch/hmu05_stock_boot.bin`, `scratch/uz801_boot.bin`, `scratch/uz801_boot_4.bin`, `scratch/diag_boot_v{7,8,13,16}.bin`, `scratch/v19_boot.bin`, `scratch/boot_sweep256.bin`, `scratch/boot_256_prep_64sweep.bin`, `scratch/uz801_fw/modem.elf`) plus the Ghidra decompile. The device was verified reachable and unchanged at session start (v19 md5s, guard `boot_count` 0, no revert file). |
| **Ground-truth verification against stock HMU05** | **Yes, and it is the doc's method.** Every UZ801 claim is paired with the stock HMU05 arm on the same AP/NV/hardware. The doc's *central act* is re-reading the stock capture **without the lossy `clean()` filter** and discovering that two of its clusters had been silently deleted (§3). |
| **Reconcile transport/config** | **Yes — this is the doc's other act.** The F3 `ts` field is shown to be **discontinuous inside one capture** (§3.1), so `ts` ordering is not trustworthy; the analysis was redone in **emission order** (the order the modem emitted the records, i.e. the parse index), which is monotonic by construction. The per-capture **coverage** (whether the capture caught the early boot burst) is shown to vary and is controlled (§5). |
| **Surgical Hexagon patching + re-signing** | **NOT APPLICABLE — no patch is proposed, derived or withdrawn here.** No byte was written, so `ufi001b_hash_tool.py` was not run and no re-signing verdict is claimed. |
| **Live empirical validation** | **NOT PERFORMED this session** — deliberately. The session's finding is that *the previous* live result (the 256-sweep capture) was **over-read**: the capture it used had missed the modem's early boot burst. A new live capture would be needed to test §6's claim properly; that is named as the next step (§8) rather than guessed at. |
| **No blind patching** | **Yes — and this is the doc's point.** The previous session's negative ("UZ801 never initialises MMOC / the LTE stack") is traced to a *parser artifact* and a *capture-coverage artifact*, not to the firmware. Correcting it before acting is the whole value of the doc. |
| **Irreversible / high-blast-radius actions declared first** | **NOT APPLICABLE** — none were taken. |

**Errors caught and corrected in this session** (recorded rather than hidden):

1. **A parser-induced loss of real data.** `scratch/f3clean.py` keeps only messages within ±40e6 ticks
   (~195 s) of the **median** `ts`. The stock capture is **not** a single continuous `ts` span: it contains
   a boot chunk at `ts ≈ 2.4e5–5.8e5` and an online-transition cluster at `ts ≈ 3.037e9–3.040e9`, both of
   which the median window **deletes**. Doc 194 §7's "all 25 subsystems appear within 0.78 s of the capture
   start" is measured from the *surviving* cluster, which begins **after** the online transition (§3.2).
2. **An over-read of a capture's coverage.** `mmocdbg.c` is present at **60** messages in six UZ801 boot
   captures and at **0** in six others — *of the same firmware*. The zero is therefore a **capture-coverage**
   artifact (the capture bound after the modem's early boot burst), not a firmware property (§5).
3. **A too-narrow mask control.** Doc 201 §6's 256-SSID sweep control was run on a capture
   (`scratch/boot_sweep256.bin`) that **did not catch the boot burst** — so its "one extra file
   (`rcinit_rex.c`)" is true but its implied reassurance is weaker than stated (§5.2).

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Decide whether the stock firmware auto-onlines at boot (Doc 194 §7 vs Doc 200 §3) | a yes/no | **Doc 200 §3 is right, Doc 194 §7 is wrong.** The stock capture's own boot chunk shows only `Prot_state 0(NULL)`; `5(ONLINE_GWL)` appears at emission index **915**, immediately after `=CM= CMD alloc u=17, tsk=dcc` (§4.1) | **MET** — Doc 194 §7 **RETRACTED** |
| Explain how Doc 194 §7's "0.78 s / modem uptime 2.1 s / before ModemManager" arose | a mechanism | the `clean()` median-`ts` window **deleted the boot chunk and the online-transition cluster**, leaving only the post-online steady state, whose start was then mistaken for the capture start (§3.2) | **MET** |
| Establish a trustworthy ordering for the captures | a method | `ts` is **discontinuous** within the stock capture (two forward jumps, idx 324 and idx 1596) ⇒ use **emission order** (parse index); corroborated by the `u` field, which Doc 201 §3.1 proved is the modem's uptime in seconds (`u=1` at boot, `u=17` at the online request) (§3.1) | **MET** |
| Split the "25 stock-only subsystems" into boot-path vs online-gated | two sets | **boot-path (in stock's first 324):** `cmss.c`, `cmlog.c`, `cmregprx.c`, `sdss.c`, `sdcmd.c`, `cmmsc_auto.c`, `mmoc.c`, `policyman_serving_system.c`, `wmsmsg.c`. **online-gated (first appear only after `ONLINE_GWL`):** `rflte_core_rxctl.c`, `rflte_mc_meas.c`, `pgi_msgr.c`, `mcpm_*`, `a2_ipfilter.c`, `trm_config_handler.c`, `tle_log.c`, `rfmeas_mc.c` (§4.2) | **MET** |
| Re-test whether the UZ801-vs-stock differential is a mask artifact | a yes/no | **the earlier control was under-powered.** `mmocdbg.c` is **60** in six UZ801 captures and **0** in six others of the *same firmware* ⇒ per-capture **coverage** varies; a negative must be scored only on a capture that demonstrably caught the boot burst (§5.1) | **MET** — Doc 201 §6's assurance **WEAKENED** |
| Re-state the UZ801 negative under that control | a valid negative | on the six burst-caught captures, `cmss.c` / `cmlog.c` / `cmmsc_auto.c` / `cmregprx.c` / `sdss.c` / `sdcmd.c` are **all still 0** ⇒ the CM serving-system layer genuinely never runs (§5.3) | **MET** |
| Name the boot-path divergence precisely | a specific event | **the `u=1, tsk=cm, ftsk=cm` CM command.** Stock's completion path runs 50 records and contains **`cmmsc_auto.c` ×3** then MMOC **`Recvd command 0(SUBSCRIPTION_CHGD)`**; UZ801's runs **12** records and contains **no `cmmsc_auto`** and **no MMOC post** (§6.1) | **MET** |
| Characterise the UZ801-only log modules | a list | `nvruim.c`, `mcfg_uim.c`, `estk_bip.c`, `gstk_proactive_cmd.c`, `gstklib.c`, `gstk_s_term_profile_rsp_wait.c`, `mmgsdi_session.c`, `policyman_uim.c` — a **UIM / SIM-Toolkit / BIP** stack the stock build never runs (§6.2) | **MET** |
| Re-check whether `FUN_c06928a8` (`cmmsc_auto`) has a caller | a yes/no | **still no caller and no pointer reference** in the decompile (2 occurrences = the header comment + the signature); its first log string is its **early-return** path; the function is invoked through **external `d0…` service thunks** (§7) | **MET** (negative confirmed) |

**The one-line truth:** the campaign's premise — *"the UZ801 firmware's boot path skips a gated RF/protocol
init that stock performs"* — is **wrong**; stock performs no such boot-time init, the LTE stack is
**online-gated**, and the real, still-standing divergence is that **UZ801's `u=1, tsk=cm` CM command never
reaches `cmmsc_auto`**.

---

## 3. The parser and clock defect that produced Doc 194 §7

### 3.1 `ts` is discontinuous inside the stock capture — so ordering must use emission order

`scratch/f3parse.py` yields messages in **emission order** (the parse index is the order in the DIAG
stream). Walking the stock capture in that order, `ts` jumps twice:

| at emission index | `ts` before → after | Δ | file at the jump |
| --: | :-- | --: | :-- |
| **324** | 576 164 → 3 037 017 468 | **+14 826 s** | `qmi_mmode_task.c` |
| **1596** | 3 040 317 840 → 3 603 661 244 | **+2 750 s** | `cmss.c` |

The content is **continuous and chronological** across both jumps (boot → online → steady state), so the
modem did **not** restart: the `ts` field is simply **not a monotonic clock across the whole capture**.

**An independent, boot-relative clock settles it.** Doc 201 §3.1 proved that `u` in
`=CM= CMD alloc u=%d` **is** the command's `+0x14` field and advances **1/second** — i.e. it is the
modem's uptime in seconds. In the stock capture:

```
idx   32  =CM= CMD alloc u=1,  tsk=cm         <- modem uptime ~1 s   (boot chunk)
idx   82  =CM= CMD free  u=1,  tsk=cm, ftsk=cm
idx  198  =CM= CMD alloc u=2,  tsk=mmgsdi_1
idx  794  =CM= CMD alloc u=17, tsk=dcc        <- modem uptime ~17 s  (the AP's online request)
idx  893  =CM= CMD free  u=17, tsk=dcc, ftsk=cm
```

`u` says the online request happens at **modem uptime ≈ 17 s** — a few seconds after boot, exactly where
ModemManager's S70 start would put it. The `ts` value at that point (3 040 010 292) would place it at
**14 843 s** of uptime. **The two disagree by a factor of ~870, and `u` is the boot-relative one.**
⇒ **`ts` must not be used for absolute positions; emission order and `u` must be.**

### 3.2 What `clean()` deleted, and why it mattered

`scratch/f3clean.py`:

```python
def clean(path, window=40_000_000):
    ts = sorted(m['ts'] for m in ms)
    med = ts[len(ts)//2]
    return [m for m in ms if abs(m['ts'] - med) <= window]   # +/- 195 s around the median
```

The stock capture's raw `ts` histogram is **bimodal-plus**:

| `ts` bin (5e6 ticks ≈ 24.4 s) | ≈ uptime | n |
| --: | --: | --: |
| 0 – 5e6 | 0–24 s | **324** |
| 3 035e6 | 14 819 s | 468 |
| 3 040e6 | 14 844 s | 804 |
| 3 600e6 – 3 645e6 | 17 578–17 818 s | **27 085** |

The **median** is 3 617 761 824, so the ±40e6 window keeps only the 3.600e9–3.645e9 cluster and **deletes
both the 324-message boot chunk and the 1 272-message online-transition cluster**.

That is exactly what Doc 194 §7 measured from. Its table's "first seen 0.00 s" is the start of the
**post-online steady state**, and its inference — *"all 25 appear within 0.78 s of the capture start, i.e.
at modem uptime ≈ 2.1 s, before ModemManager exists, so none of it can be AP-driven"* — is measured from an
origin that is **already past the online transition**. The `clean()` filter is sound as a
**false-positive filter** (its original purpose, Doc 194 §4.1) but must not be used as the basis for
**first-occurrence timing**.

> **Rule (new):** before reporting any per-message timing from an F3 capture, print the **`ts` histogram**
> and the **emission-order `ts` jumps**. If the capture has more than one cluster, `clean()` is not
> admissible for timing, and `u` (1 Hz, boot-relative) is the clock to use.

---

## 4. Doc 194 §7 retracted: the stock capture is three segments, and the LTE stack is online-gated

### 4.1 Stock does not auto-online — the transition is AP-driven

The stock capture's `mmocdbg.c` `Prot_state` timeline, in emission order:

| emission index | `ts` | message |
| --: | --: | :-- |
| 375 868 … 389 376 (21 msgs) | 3.8e5 | `Prot_state [MAIN] 0(NULL)` — **the boot chunk; no ONLINE** |
| 794 | 3 040 010 292 | `=CM= CMD alloc u=17, tsk=dcc` |
| 799–801 | 3 040 010 796 | `cmmsc_auto.c` ×3 (`MSC_AUTO`, `is_ue_mode_csfb`, **`updating op_mode`**) |
| 803 | 3 040 010 876 | `=MMOC= Recvd command 1(PROT_GEN_CMD)` |
| 806 | 3 040 011 632 | `=MMOC= Recvd command 2(OPRT_MODE_CHGD)` |
| 808 | 3 040 011 660 | `=MMOC= New transaction : 2(ONLINE)` |
| **915** | **3 040 013 116** | **`=MMOC= Prot_state [MAIN] 5(ONLINE_GWL)`** |
| 935 | 3 040 020 868 | `rflte_core_rxctl.c` — **+7.75 ms after `ONLINE_GWL`** |

⇒ **Doc 194 §7's table row quoting `Prot_state [MAIN] 5(ONLINE_GWL)` at "0.78 s" is wrong**: the boot
chunk's `mmocdbg.c` messages are **all `0(NULL)`**, and `ONLINE_GWL` appears only after a `dcc`-task CM
command — i.e. **after the AP asks**. This confirms **Doc 200 §3(b)** and retires Doc 195 §12.1.1's
hand-drawn arrow for good.

### 4.2 Which of the "25 subsystems" are boot-path and which are online-gated

Using the same emission-order segmentation, the stock capture splits cleanly:

| group | first appears | members (stock counts) |
| :-- | :-- | :-- |
| **boot-path** — present in the boot chunk (idx 0–323), *before* the AP's request | modem uptime ~1–3 s | `cmss.c` 18, `cmlog.c` 6, `cmmsc_auto.c` 3, `mmocdbg.c` 51, `sdss.c` 6, `cmregprx.c` 7, `mmocmmgsdi.c`, `wms.c`, `cmph.c`, `mmgsdiutil.c`, `wmssim.c`, `qmi_mmode_task.c` |
| **online-gated** — first appear only in/after the online transition (idx ≥ 324) | after `ONLINE_GWL` | `rflte_core_rxctl.c` 8 652, `rflte_mc_meas.c` 668, `pgi_msgr.c` 6 006, `mcpm_npa.c` 2 417, `mcpm_saw.c` 760, `mcpm.c` 108, `a2_ipfilter.c` 79, `trm_config_handler.c` 12, `tle_log.c` 302, `rfmeas_mc.c` 88 |

**Consequence.** The LTE RF / protocol stack (`rflte_*`, `pgi_msgr`, `mcpm_*`) is **gated by MMOC
`5(ONLINE_GWL)`** — Doc 200 §3(a)'s conclusion, now with the boot chunk as its own control. There is **no
separate boot-time RF/protocol gate** for the campaign to find. Doc 201 §7.1 step 2's premise ("find what
starts the LTE/CM-SS layer on stock") is therefore **replaced**: the LTE layer starts when the AP's CM
command completes, so the only target is **why UZ801's CM command does not complete** (§6).

---

## 5. The instrument trap, sharpened: per-capture *coverage*, not just the mask

### 5.1 `mmocdbg.c` = 60 in six UZ801 captures and 0 in six others — same firmware

| capture | n | files | `mmocdbg.c` | `cmss.c` | `cmlog.c` | `cmmsc_auto.c` | `cmregprx.c` | `sdss.c` |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: |
| `diag_boot_v7.bin` | 2 547 | 32 | **60** | 0 | 0 | 0 | 0 | 0 |
| `diag_boot_v8.bin` | 2 472 | 32 | **60** | 0 | 0 | 0 | 0 | 0 |
| `diag_boot_v13.bin` | 1 900 | 32 | **60** | 0 | 0 | 0 | 0 | 0 |
| `diag_boot_v16.bin` | 829 | 32 | **60** | 0 | 0 | 0 | 0 | 0 |
| `v19_boot.bin` | 841 | 31 | **60** | 0 | 0 | 0 | 0 | 0 |
| `reboot_postpatch.bin` | 2 487 | 32 | **60** | 0 | 0 | 0 | 0 | 0 |
| `uz801_boot.bin` | 5 568 | 24 | **0** | 0 | 0 | 0 | 0 | 0 |
| `uz801_boot_4.bin` | 5 707 | 24 | **0** | 0 | 0 | 0 | 0 | 0 |
| `boot_hwid48.bin` | 2 018 | 24 | **0** | 0 | 0 | 0 | 0 | 0 |
| `patched_boot.bin` | 2 306 | 24 | **0** | 0 | 0 | 0 | 0 | 0 |
| `reboot_devcfg_boot.bin` | 2 387 | 24 | **0** | 0 | 0 | 0 | 0 | 0 |
| `boot_sweep256.bin` | 670 | 24 | **0** | 0 | 0 | 0 | 0 | 0 |
| `boot_256_prep_64sweep.bin` | 673 | 25 | **0** | 0 | 0 | 0 | 0 | 0 |
| **`hmu05_stock_boot.bin`** | 33 039 | 56 | 326 | 609 | 835 | 36 | 12 | 73 |

**All 14 are UZ801 captures of the same firmware** (v7, v8, v13, v16, v19 and the unpatched set), yet
`mmocdbg.c` is either 60 or 0. Since the *firmware* is identical within each pair, the difference is
**instrumental**: the `diagboot` harness polls for the SMD DIAG device and binds when it appears, and the
modem's early boot burst (`mmocdbg.c` ×60, `cmph.c` ×22, `nvruim.c` ×2, `mcfg_uim.c` ×2, `estk_bip.c` ×7)
is emitted **before** the bind in the 24-file class. **A `0` in the 24-file class is a coverage artifact,
not evidence.**

### 5.2 Doc 201 §6's control was run on a coverage-blind capture

Doc 201 §6 raised the mask confound and closed it with a 256-SSID sweep on `scratch/boot_sweep256.bin`.
That capture is in the **24-file / `mmocdbg.c` = 0** class — i.e. it **missed the boot burst**. Its result
(64 → 256 adds exactly one file, `rcinit_rex.c`) is true and does show the **sweep** dimension is
saturated, but it cannot speak to the files that only appear in the boot burst. **The mask conclusion
stands; the "therefore the whole differential is real" step was under-powered.** The stronger control is
to compare **within the 32-file class** (§5.3).

### 5.3 The UZ801 negative, re-scored on burst-caught captures — it survives

Restricting to the six captures that **did** catch the boot burst (`mmocdbg.c` = 60, so the CM boot path is
demonstrably inside the window), the following are **still exactly 0**:

`cmss.c`, `cmlog.c`, `cmmsc_auto.c`, `cmregprx.c`, `sdss.c`, `sdcmd.c`, `mmoc.c`,
`policyman_serving_system.c`, `wmsmsg.c`.

**⇒ the UZ801 firmware's CM serving-system / MMOC layer genuinely never runs — this is now a
coverage-controlled negative, and it is stronger than Doc 201 §6's version.**

The 33 stock files with **no** UZ801 counterpart on a burst-caught capture:

```
rflte_core_rxctl.c 8652   pgi_msgr.c 6006     mcpm_npa.c 2417   cmlog.c 835
mcpm_saw.c 760            rflte_mc_meas.c 668 cmss.c 609        mcpm.c 108
rfmeas_mc.c 88            a2_ipfilter.c 79    sdss.c 73         cmcall.c 40
cmmsc_auto.c 36           qm_util.c 36        cmsoa.c 34        gstkutil.c 23
mmoc.c 19                 a2_dl_phy.c 19      cmsds.c 16        cmregprx.c 12
mc_fee.c 12               trm_config_handler.c 12               tlm_ptm.c 9
cmcc.c 9                  tm_lpp_cp.c 9       tm_umts_up_supl.c 8  a2_log.c 6
sdcmd.c 5                 policyman_serving_system.c 4          tm_cm_iface.c 2
cmltecall.c 2             policyman_call_events.c 2             mc_timetag.c 1
```

---

## 6. ★ The real divergence: the `u=1, tsk=cm` CM command completes differently

### 6.1 The same command is issued on both — and its completion path differs

Stock's boot chunk (emission order) — **the `u=1, tsk=cm` command's window is idx 32 → 82**:

```
idx   32  =CM= CMD alloc u=1, tsk=cm
idx   33-37  mmgsdi_refresh.c  ×5
idx   38-42  mmgsdiutil.c  UICC SEARCH PATTERN
idx   44-55  wms.c / wmssim.c  WMS_CMD_MMGSDI_RESPONSE_CB ×6
idx   56  qmi_nas_mmgsdi.c  new ef spn size
idx   58  cmmsc_auto.c  =CM= MSC_AUTO: 0x%x & 0x%x = 0x%x
idx   59  cmmsc_auto.c  =CM= CMMSC_AUTO:is_ue_mode_csfb=%d hybr_pref %d is_cdma_subsc_avail %d
idx   60  cmmsc_auto.c  =CM= CMMSC_AUTO: updating op_mode, cdma sub %d hybr1_allowed %d ...
idx   61  mmocdbg.c  =MMOC= Recvd command 0(SUBSCRIPTION_CHGD)      <-- the MMOC post
idx   68  mmocdbg.c  =MMOC= New transaction           : 1(SUBSC_CHGD)
idx   88-93  cmregprx.c  ph_stat_chgd: ...                          <-- the serving-system bridge
idx  123-126 cmph.c  RAT_DISABLED_MASK
idx  127-137 sdss.c / sdcmd.c
idx  138-168 cmss.c  Mapping csg_info / fmode            (18 msgs)
idx  147-152 cmlog.c  cmlog_ss_event_handler_msim
idx  176-182 mmocdbg.c  -> 5(PROT_PH_STAT_ENTER) -> 0(NULL)
idx   82  =CM= CMD free u=1, tsk=cm, ftsk=cm
```

UZ801's equivalent (`scratch/uz801_boot.bin`) — **the `u=1, tsk=cm` window is idx 33 → 45**:

```
idx   33  =CM= CMD alloc u=1, tsk=cm
idx   34  qmi_mmode_task.c
idx   35-39  mmgsdi_refresh.c  ×5
idx   40-43  qmi_nas.c  qmi_nas_cmsubs_evt_cb: default data subs - %d, voice id - %d, ...
idx   44  qmi_mmode_task.c
idx   45  =CM= CMD free u=1, tsk=cm, ftsk=cm          <-- 12 records; NO cmmsc_auto, NO MMOC post
```

Corroborated by `scratch/diag_boot_v13.bin`, whose `u=3, tsk=cm` window is idx **245 → 256** (11
records) with the same content shape.

**⇒ The break is inside the CM command's own completion path, and it is present at the very first boot
command (`u=1`), not only at the AP's online request (`u=17`, Doc 200 §4).** `cmmsc_auto` — the function
that computes and applies the operating mode — is the step that is skipped, on both paths, because both
paths share the same missing call.

### 6.2 The two builds run complementary CM boot paths

Comparing the **boot chunk only** (stock idx 0–323 vs UZ801 first 324):

| present in stock's boot chunk, absent from UZ801's | present in UZ801's boot chunk, absent from stock's |
| :-- | :-- |
| `cmss.c`, `cmlog.c`, `cmmsc_auto.c`, `cmregprx.c`, `sdss.c`, `sdcmd.c`, `mmoc.c`, `policyman_serving_system.c`, `wmsmsg.c` | `nvruim.c`, `mcfg_uim.c`, `estk_bip.c`, `gstk_proactive_cmd.c`, `gstklib.c`, `gstk_s_term_profile_rsp_wait.c`, `mmgsdi_session.c`, `policyman_uim.c` |

The two sets are **disjoint and complementary**: stock's boot path runs the
**serving-system / MMOC / system-determination** stack; UZ801's runs a **UIM / SIM-Toolkit / BIP** stack
that stock never runs at all.

### 6.3 Where UZ801's MMOC ends up (and where stock's does not)

`scratch/diag_boot_v13.bin` / `scratch/v19_boot.bin`, emission order:

```
idx   11  =CM= CMD alloc u=1, tsk=mmgsdi_1          <- a UIM-bridge command, not `tsk=cm`
idx   12  nvruim.c    nvruim_mmgsdi_evt_cb 0x0      <- UZ801-only
idx   13  mcfg_uim.c  Got card event %x on slot index %x; sending cmd   <- UZ801-only
idx   15-21 cmph.c    mode_pref / rat_disabled_mask / After updating from rat_disabled_mask
idx   25  mmocmmgsdi.c  card_status_cb: Received MMGSDI Event
idx   27  mmocdbg.c   =MMOC= Recvd command 7(DUAL_STANDBY_CHGD)         <- NOT command 0
idx   30  mmocdbg.c   =MMOC= Prot_state [MAIN] 7(OFFLINE)               <- UZ801 lands OFFLINE
idx   34  mmocdbg.c   =MMOC= New transaction           : 12(MMGSDI_INFO_IND)
idx   35-40 mmocmmgsdi.c  Opening session type :%d
idx   41-43 mmocdbg.c     -> 14(WAIT_SESSION_OPEN_CNF)                   <- and it stays there
```

Stock never emits `DUAL_STANDBY_CHGD` and never enters `Prot_state 7(OFFLINE)`; UZ801 does both, and then
opens an MMGSDI session and **remains in `WAIT_SESSION_OPEN_CNF`** for the rest of the capture — the
"uninitialised session" of Doc 199 §4 / Doc 200 §5.

**Chain:** UZ801-only UIM/STK bridge → the boot CM command arrives on `tsk=mmgsdi_1` instead of `tsk=cm` →
`cmmsc_auto` is never called → MMOC receives `7(DUAL_STANDBY_CHGD)` instead of `0(SUBSCRIPTION_CHGD)` →
`Prot_state 7(OFFLINE)` → MMGSDI session open hangs → MMOC never reaches the state in which the AP's
`dcc` online command can drive `ONLINE_GWL` → `rflte_*` never starts.

---

## 7. Static re-check of `FUN_c06928a8` (`cmmsc_auto`)

> ⚠ **RETRACTED 2026-09-26 by Doc 206 §3.** `FUN_c06928a8` is **`cmcc.c`** (CM Call Control), **not**
> `cmmsc_auto.c`: the descriptors it passes resolve to `cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d` (L3081) and
> `cmcc.c:=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d` (L3115), and its embedded symbols
> are `s_cmcc_service_available_cb` / `s_cmcc_call_control_processing_lte`. The claim below that "its
> `MSC_AUTO: …` string is its **early-return** path" is **unverifiable and wrong** — the early-return
> descriptor (`0xc165c398`) has no retained string and is not `MSC_AUTO`. The real `cmmsc_auto.c` entry
> point is **unlocated**.

Extracted from `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` (line 1144716):

```
FUN_c06928a8(param_1, param_2, param_3, param_4, param_5, param_6):
    if (param_3 == 0 || param_4 == 0) {                 // <-- the EARLY-RETURN path
        FUN_c091d940(0xc165c398, param_3, param_4);      // "=CM= MSC_AUTO: 0x%x & 0x%x = 0x%x"
        goto LAB_c0692ab8;                               // return 0
    }
    iVar2 = FUN_c0692508(0xff);
    FUN_c0692544(iVar2, 0xff, ..., param_3, 0, param_4);
    FUN_c091d9d0(&DAT_c165c3a0, ...);                    // "is_ue_mode_csfb=... hybr_pref=..."
    ...
    FUN_c091d8c0(0xc165c3a8, iVar3);  /  (0xc165c3b0, iVar3)   // "updating op_mode, ..."
    ...
    FUN_c06917f0(iVar2, 0xffffffff);                     // the v14 patch target
```

**Three findings.**

1. **The `MSC_AUTO: 0x%x & 0x%x = 0x%x` string is the early-return branch.** Stock logs it *and* the
   `updating op_mode` string, which cannot come from one call ⇒ stock invokes `FUN_c06928a8` **at least
   twice** at boot. The campaign's v14 patch targeted `FUN_c06917f0`, the *deep* callee — which is only
   reached on the **second** (full) invocation. **A patch on the callee cannot help if the caller is never
   reached**; that is now explained, not just observed.
2. **The function is registered through external service thunks** (`thunk_EXT_FUN_d0587538(..., 0x456,
   FUN_c0691d9c, iVar2)` and `thunk_EXT_FUN_d0589bb8(..., FUN_c069282c, iVar2)`) — the same **`d0…`
   external boundary** that blocks the static route to the online command's code (Doc 201 §4). Its own
   invoker is therefore **outside `modem.elf`**.
3. **A raw search of `scratch/uz801_fw/modem.elf` for a little-endian pointer to `0xc06928a8` (and to
   `FUN_c0691d9c` / `FUN_c069282c` / `FUN_c06917f0` / `FUN_c0692794`) returns 0 hits** — consistent with the
   documented Hexagon encoding (constants render as signed `##` immediates, so a raw `u32` search finds
   nothing). **This is a negative about the search method, not about the image** — do not read it as
   "the pointer does not exist".
4. **The `d0…` region is provably outside this image.** `modem.elf` has 27 program headers; their loaded
   ranges are `0x8b900000` (one small segment) and `0xc0000000`–`0xc50c0000` (the rest; segments 20, 21 and
   26 are `filesz = 0`, i.e. runtime BSS). **No segment covers `0xd03d3420`.** So the thunks
   `thunk_EXT_FUN_d0…` — both the online command's `thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)` (Doc 201 §4)
   and `cmmsc_auto`'s registration thunks — target a **different address space**, which is why neither the
   online command's code/ctx nor `cmmsc_auto`'s invoker can be read from `modem.elf`.

**The open question is therefore sharpened:** *which external `d0…` service invokes `FUN_c06928a8`, and
what does UZ801 do instead?* — not "who writes the field" and not "which boot gate is missing".

---

## 8. Campaign implications and the named next step

1. **Doc 194 §7 is retracted; Doc 201 §7.1 step 2 is replaced.** There is no boot-time RF/protocol gate.
   The LTE stack is gated by MMOC `5(ONLINE_GWL)`, which is driven by a CM command.
2. **The target is unchanged but now better specified:** make UZ801's `u=1, tsk=cm` (and, equivalently, the
   `u=17, tsk=dcc`) command reach `cmmsc_auto`. Everything downstream (`SUBSCRIPTION_CHGD` → MMOC state
   machine → `ONLINE_GWL` → `rflte_*`) follows from that one step.
3. **The next experiment should be a *coverage-controlled* boot capture**, because the previous control was
   not:
   - capture the UZ801 boot with the instrument bound **as early as the stock capture was** (the 32-file
     class), so `mmocdbg.c` = 60 is present;
   - sweep the mask to 256 **on that class**;
   - and, if possible, capture the **stock** firmware with the AP prevented from sending an online request
     (Doc 194 §11 route 1) — now known to be a *clean* separation, because the boot chunk is already
     online-free.
4. **The static route to name the invoker** is the external `d0…` region (§7). Until that is resolved, any
   patch to `cmmsc_auto` or its callees is aimed at a function that is provably **not reached**, which is
   what v14 already demonstrated.
5. **Scope guard (unchanged).** None of this reopens the ~900 s fatal: the fault table and crash branch are
   byte-identical between the sets (`143`/`145`), and **reaching `online` would arm the LTE-gated fatal
   clock** (Doc 194 §11). Success here is a porting milestone, not a stability fix.
6. **Device state is unchanged and healthy:** v19 deployed, guard `boot_count` 0, no
   `/root/BOOTLOOP_REVERT.txt`, modem `offline`, overlay 9 % used.

---

## 9. Evidence inventory

| artifact | what it is |
| :-- | :-- |
| `scratch/hmu05_stock_boot.bin` (94 623 391 B) | the stock HMU05 boot capture — **the control arm for every claim here**; three emission segments, two `ts` discontinuities |
| `scratch/uz801_boot.bin`, `scratch/uz801_boot_4.bin` | UZ801 captures, **24-file / `mmocdbg.c` = 0** class (missed the boot burst) |
| `scratch/diag_boot_v7.bin`, `…_v8.bin`, `…_v13.bin`, `…_v16.bin`, `scratch/v19_boot.bin`, `scratch/reboot_postpatch.bin` | UZ801 captures, **32-file / `mmocdbg.c` = 60** class (caught the boot burst) — the class a negative must be scored on |
| `scratch/boot_sweep256.bin`, `scratch/boot_256_prep_64sweep.bin` | the Doc 201 §6 sweep control — **24-file class; coverage-blind for the boot burst** |
| `scratch/f3parse.py` | the F3 parser (emission order = parse index) |
| `scratch/f3clean.py` | the median-`ts` filter — **sound as a false-positive filter, NOT admissible for timing** (§3.2) |
| `scratch/uz801_fw/modem.elf` | the rejoined UZ801 ELF (pointer-search target, §7) |
| `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` | the Ghidra decompile (`FUN_c06928a8` @ line 1144716) |
| `scratch/re_func.py` | function extractor for the 105 MB decompile |

## 10. How to re-run

```bash
# 1. the ts-histogram check — ALWAYS run this before trusting any timing (§3.2)
python3 - <<'PY'
import sys,collections; sys.path.insert(0,'scratch')
from f3parse import parse
d,ms=parse('scratch/hmu05_stock_boot.bin')
ts=sorted(m['ts'] for m in ms)
h=collections.Counter(t//5_000_000 for t in ts)
for k in sorted(h): print("%12d ticks  n=%d"%(k*5_000_000,h[k]))
PY

# 2. the emission-order ts jumps (§3.1)
#    (walk ms in order; report |ts[i]-ts[i-1]| > 2e6)

# 3. the boot-path differential (§6.2) — compare the FIRST 324 records of each capture,
#    not the whole file, and confirm the UZ801 arm carries mmocdbg.c = 60 first.
```

# 221 — The device runs UZ801-**B08** (not the campaign's B10), the RF **card classes are identical to stock** (the port is *not* RF-blocked), the policyman RAT-mask-0 is **not** the gate, and the offline latch is a `cmph.c`-driven `OPRT_MODE_CHGD`

**Status:** complete (analysis + one live experiment). **Supersedes** three claims: (a) "the UZ801 port is RF-blocked / the transceiver driver sets are disjoint"; (b) "the policyman `Filtered RAT mask 0` is a candidate gate" (Doc 210 §5); (c) the implicit assumption that the deployed UZ801 firmware is the "-21"/B10 set the campaign analysed.
**Ledger:** this doc is item **27** of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §7; its evidence rows are in that ledger's §8.
**Date:** 2026-09-28.

---

## 0. SOP-compliance statement

| step | taken? | note |
| :-- | :-- | :-- |
| Read the live device state before drawing conclusions | **YES** | §1 — firmware md5, revision, mode, SIM, NV, QMI |
| Verify every VA/hash against a *named* local image | **YES** | §1.2 — the device's 21-file modem set is **byte-identical** to `scratch/fwdl/uz801_v32_x/image/` |
| Prefer a *differential* over a single-arm observation | **YES** | §4/§5/§6 — every claim is UZ801-vs-stock or UZ801-B08-vs-B10 |
| Use a control when claiming a negative | **YES** | §5 (UFI001B as a third firmware), §4.2 (`qfe*` as a positive control for the `rfdevice_*` census) |
| Instrument the live device rather than infer | **YES** | §3 — a live F3 capture of an `online` attempt |
| State what the instrument **cannot** see | **YES** | §3.3 (the live capture is in Doc 203's *blind* class: `mmocdbg.c` = 0) |
| Do **not** re-open a closed axis | **YES** | §7 lists the closed axes this doc touches and why it does not re-open them |
| Record the retracted claims explicitly | **YES** | §2 (the three retractions above) |

---

## 1. The deployed state is **not** the state the campaign was written against

### 1.1 Live measurement (2026-06-29 device clock; AP uptime 6 609 s → 7 186 s during the session)

| probe | result |
| :-- | :-- |
| `/lib/firmware/modem.mdt` | `c132421fe6d7766b012f59d3f851db87` |
| `/lib/firmware/modem.b16` | `f6f9900ea3845a1cdab67f6a3c0f1e26` |
| `--dms-get-revision` | **`UZ801_V01R01B08  1  [Sep 07 2015 23:00:00]`** |
| `--dms-get-ids` | IMEI `864293052253917`, MEID `A27C0A80046ED6`, ESN `80EBE077` (the **HMU05** identity) |
| `--dms-get-operating-mode` | `Mode: 'offline'`, `HW restricted: 'no'` |
| `--uim-get-card-status` | `Card state: 'present'`, app `usim (2)`, `Application state: 'ready'`, PIN1 `disabled` |
| `--dms-set-operating-mode=online` | **`QMI protocol error (52): 'DeviceNotReady'`** (twice, 5 s apart) |
| `--dms-get-band-capabilities` | `Bands: wcdma-2100, wcdma-850-us, wcdma-900`; **`LTE bands: 1, 3, 5, 8`** |
| `--nas-get-system-selection-preference` | `Mode preference: 'lte'`, `LTE band preference: '1, 3, 5, 8'`, `Acquisition order: lte, umts, gsm, cdma-1x, cdma-1xevdo` |
| `dmesg` | `bam_dmux: RX watchdog: quiesced Ns (pc_state=0, pc_line=0, rx=held, ring disarmed)` every 60 s, since boot |
| `remoteproc0/state` | `running`, firmware `mba.mbn` |
| ModemManager | **running** (`/usr/sbin/ModemManager --log-level=INFO`, pid 2599; `/org/freedesktop/ModemManager1/Modem/10`) |

### 1.2 The firmware is byte-identical to the **B08** ("-11") image — not the campaign's B10 ("-21")

`md5sum` of the device's `/lib/firmware/modem.b*` + `mba.mbn` (21 files) vs the local candidate sets:

```
diff <(device manifest) <(scratch/fwdl/uz801_v32_x/image manifest)   ->  *** IDENTICAL ***
diff <(device manifest) <(GitIgnore/UZ801 Modem manifest)            ->  16 lines differ
```

`scratch/fwdl/uz801_v32_x/image/` was carved from `uz801_v3.2_stock.zip`, whose `modem.bin` carries the banner **`UZ801_V01R01B08`**. The set the whole campaign patched (v13…v19, `modem.b16 c71d5346…`) is `GitIgnore/UZ801 Modem/` + `scratch/uz21_swap/`, banner **`UZ801_V3.0_21_V01R01B10`** — and the ledger (§7 items 25/26, Doc 220) analysed captures taken from **that** set.

Per the UFI001B cluster's §3a.8: **B08 = the `-11` iteration, B10 = the `-21` iteration** — two UZ801 v3.2 builds whose segment sets differ in **10 of 20 segments** (`b00,b01,b02,b16,b18,b19,b22,b24` differ; `b03,b04,b05,b08,b10,b11,b13,b14,b15,b17,b23,b25` are byte-identical).

**Consequence.** Every statement in Docs 194–220 about "the deployed UZ801 firmware" describes **B10**, while the device has been running **B08** since the user's 2026-09-27 full-device swap (`project_uz801_full_device_swap_hy`: "firmware still UZ801 (`UZ801_V01R01B08`)"). Both builds are `offline`, so the campaign's *conclusions* survive — but the *provenance* of the live state does not. **Always name the build (B08/-11 vs B10/-21) when citing a UZ801 result from now on.**

---

## 2. Three retractions

| # | retracted claim | where it came from | what the data shows |
| :-- | :-- | :-- | :-- |
| R1 | "the UZ801 port is **RF-blocked**: the transceiver driver sets are DISJOINT (145 WTR4905 vs 102 WTR1605, intersection 0)" | `project_ufi001b_rf_transplant.md` headline, read as applying to UZ801 | §4.1 — **the UZ801 v3.2 `rfc_*` card-class set is byte-for-byte the same 168 names as stock HMU05's, and there is no `wtr4905` anywhere in any UZ801 image.** The disjoint-transceiver verdict is **UFI001B-only** |
| R2 | "`policyman_rat_capability.c:74 Filtered RAT mask 0 based on HW capabilities 544` … a candidate mechanism for the campaign's central symptom" | Doc 210 §5.2 | §5 — **stock HMU05 emits the identical 12-record sequence with identical args and is online.** It is a transient boot default, not a gate |
| R3 | "the deployed UZ801 firmware is the `-21` set" | implicit throughout Docs 194–220 | §1.2 — it is the **B08 (`-11`)** set |

---

## 3. Live F3 capture of an `online` attempt (the first live capture on B08)

### 3.1 Method

```
/root/diag_bind.sh                                     -> DIAG=/dev/rpmsg0  CNTL=/dev/rpmsg1
/root/diag_logtool cntl-enable-range /dev/rpmsg1 512
/root/diag_logtool log-enable
/root/diag_logtool capture 30 /tmp/diag_online_b08.bin &
   sleep 4 ; qmicli -d /dev/wwan0qmi0 --dms-set-operating-mode=online   # attempt 1 @ AP 7035.27
   sleep 5 ; qmicli -d /dev/wwan0qmi0 --dms-set-operating-mode=online   # attempt 2 @ AP 7040.31
```

Artifact: `evidence/221_b08_live_and_static/uz801_b08_live_online_attempt.bin` (221 750 B, md5 `d56f344ba0576f5b6417a3de45a8a2c5`), parsed by `scratch/f3parse.py` (md5 `9f9d6edf15d64faf3b47897f7838c9ac`) → **859 messages**.

### 3.2 Result — the op-mode command **aborts in under a millisecond**

The only `cmdbg.c` records in the capture, and they line up **exactly** with the two `qmicli` calls:

| record | F3 `ts` | args | lifetime |
| :-- | --: | :-- | --: |
| `=CM= CMD alloc u=7023, tsk=dcc` | 1 346 938 280 | — | |
| `=CM= CMD free  u=7023, tsk=dcc, ftsk=cm` | 1 346 938 416 | — | **136 ticks = 0.66 ms** |
| `=CM= CMD alloc u=7028, tsk=dcc` | 1 347 970 560 | — | |
| `=CM= CMD free  u=7028, tsk=dcc, ftsk=cm` | 1 347 970 632 | — | **72 ticks = 0.35 ms** |

* `u` is modem uptime in seconds; the AP uptimes of the two attempts (7035.27 / 7040.31) minus the modem's ~12 s boot lag give **u ≈ 7023 / 7028** — the match is not coincidence.
* Clock check: Δ`ts` = 1 032 280 over Δ`u` = 5 s ⇒ **206 456 ticks/s**, consistent with the instrument's 204 800 Hz. So 72 ticks = 0.352 ms and 136 ticks = 0.664 ms.
* The `0.352 ms` figure is **exactly** Doc 214's "`dcc` ABORTS in 0.352 ms vs 9.180 ms" ⇒ the B10 finding **reproduces on B08**, live, with no reboot.

### 3.3 What this capture cannot see (declared limit)

The capture's `code` distribution is `{4406: 840, 13904: 19}` and its file census contains **`mmocdbg.c` = 0** ⇒ it is in Doc 203's **blind** class. **The MMOC side of the refusal is invisible here.** That is not a mask problem: the capture's single SSID stream is the same shape as the campaign's `cntl-enable 64` boot captures, and `mmocdbg.c`'s SSID is inside [0, 64) ⊂ [0, 512). It is a **runtime** fact: on the live op-mode path the CM aborts **before** any MMOC transaction is created (contrast §6, where the *boot* path does create one).

---

## 4. The RF card classes are **identical** — the port is not RF-impossible

### 4.1 The `rfc_*` card-class census

Extracted with `rfc_[A-Za-z0-9_]{3,60}` over every `modem.b*` of each image:

| image | distinct `rfc_*` names | `rfc_wtr1605_*` | `rfc_wtr4905_*` | `rfc_wtr2605_*` |
| :-- | --: | --: | --: | --: |
| **HMU05 stock** (`GitIgnore/compare/modem_hmu05_extracted/image`) | **168** | 137 | **0** | 9 |
| **UZ801 B08** (`scratch/fwdl/uz801_v32_x/image`) | **168** | 137 | **0** | 9 |
| **UZ801 B10** (`GitIgnore/UZ801 Modem`) | **168** | 274* | **0** | 18* |
| UZ801 v1.x (`scratch/fwdl/uz801_v1_x/image`) | 159 | 120 | **0** | 9 |

\* the B10 directory also contains `modem.bin` (the FAT16 container), which duplicates `b18`'s pool — hence exactly 2× the B08 counts.

**Set difference:** `HMU05 − B08 = ∅` and `B08 − HMU05 = ∅`. The 168 names are the **same set**. `B08 − B10 = ∅` and `B10 − B08 = ∅` too.

### 4.2 Positive control

The same census on the non-RF token `qfe*` returns **46 / 46 / common 46** for UZ801-B08 vs stock — i.e. the method detects identity where identity is real. The `rfdevice_*` census (§4.3) detects difference where difference is real. So the §4.1 identity is a measurement, not a blind spot.

### 4.3 …but the RF **device data** (PA/ASM) sets differ

| token family | UZ801-B08 | stock | common | stock-only | UZ801-only |
| :-- | --: | --: | --: | --: | --: |
| `rfdevice_(pa\|asm)_*_data_ag` | **75** | **89** | 73 | **16** | 2 |
| `rfdevice_*` (all) | 120 | 133 | 117 | 16 | 3 |
| `qfe*` | 46 | 46 | 46 | 0 | 0 |

The 16 stock-only PA/ASM data objects are `om_8443`, `rr88643_21`, `rr88916_21`, `s2916`, `s5643_51`, `sky_77643_21`, `vc7643_61`, `vc7916_61` (each as `_pa_` and `_asm_`). The 2 UZ801-only are `rfdevice_{pa,asm}_sky77643_data_ag`.

**⚠ Naming caveat (must be stated):** the two builds use **different name conventions** for the same silicon (`sky77643` vs `sky_77643_21`), so a name-level "stock-only" does **not** prove the device is absent from the UZ801 build — it may be present under another name. **This is a lead, not a verdict.** It is, however, the *first concrete structural candidate* for the memory's "board-level RFFE" phrase, and it is now bounded: the transceiver layer is identical, so any RFFE mismatch must live in the PA/ASM layer.

### 4.4 Build provenance (new, and it explains a lot)

| | stock HMU05 | UZ801 |
| :-- | :-- | :-- |
| source root | `/workspace/work/modem/modem8916_1605/` | `/home/chiyong/UZ801_V3.0-11/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/` |
| CRMBuild label | `MPSS.DPM.1.0.c7-00193-M8916EAAAANVZM-1_20150909_103440` | `MPSS.DPM.1.0.1.c1-00121-M8936FAAAANUZM-1_20150907_230707` |
| nominal SoC | **M8916** | **M8936F** |
| variant | full source | **`_amss_qrd_no-l1-src`** |

Two things to read carefully and *not* over-read:

1. **`modem8916_1605`** is the stock build's own name — the vendor built the HMU05 modem for *this* transceiver. The UZ801's root does not name a transceiver.
2. **`no-l1-src`** — the UZ801 build has **no L1 source**; its Layer-1 is a prebuilt object. This is the first structural reason we have for why the UZ801 image contains the `rfc_*`/`rflte_*` **strings** (§4.1, `rflte_core_rxctl.c` = 4 in *both*) yet logs **nothing** from them (§6.2). It also means "the UZ801 image has fewer RF log sites" cannot be read as "the UZ801 firmware cannot do RF" — the L1 is simply not this build's source.
3. `M8936F` / `msm8939` are the shared 8916/8936 AMSS labels (already established: `project_uz801_is_msm8916_not_msm8939`). **Do not** re-raise "the UZ801 is 8939 silicon" on the strength of §4.4.

---

## 5. The policyman "RAT mask 0" is **not** the gate (Doc 210 §5 retracted)

Doc 210 §5.2 proposed `Filtered RAT mask 0 based on HW capabilities 544` as "a candidate mechanism … not on the campaign's closed list". Doc 210 §4.4/§4.5 could not test it because its stock capture did not cover stock's boot.

**That test is now available**: `scratch/doc211/stock_boot_211.bin` and `stock_boot_240.bin` are same-window stock captures that *do* contain the policyman init (12 `policyman_rat_capability.c` records each). The comparison is exact:

```
UZ801-B10 v19  (uz801_v19_boot_240.bin)          STOCK-HMU05 211 (stock_boot_211.bin)
:726 subs 0  args=[0,2,0]                        :726 subs 0  args=[0,2,0]
:98  subs 0  args=[0]                            :98  subs 0  args=[0]
:726 subs 0  args=[0,2,0]                        :726 subs 0  args=[0,2,0]
:726 subs 1  args=[1,2,0]                        :726 subs 1  args=[1,2,0]
:98  subs 1  args=[1]                            :98  subs 1  args=[1]
:726 subs 1  args=[1,2,0]                        :726 subs 1  args=[1,2,0]
:726 subs 2  args=[2,2,0]                        :726 subs 2  args=[2,2,0]
:98  subs 2  args=[2]                            :98  subs 2  args=[2]
:726 subs 2  args=[2,2,0]                        :726 subs 2  args=[2,2,0]
:74  args=[0,544]  Filtered RAT mask 0           :74  args=[0,544]  Filtered RAT mask 0
:74  args=[0,544]  Filtered RAT mask 0           :74  args=[0,544]  Filtered RAT mask 0
:907 subs 0  args=[0,2]                          :907 subs 0  args=[0,2]

policyman_rf.c:592 subs 0/1/2  "can't populate RF item from EFS"   — identical in both
```

**The sequences are identical, record for record, including the `[0, 544]` args — and the stock arm is online.** Therefore `Filtered RAT mask 0` is the *normal boot-time default* both firmwares pass through, not a gate. (It is consistent with the *live* state too: §1.1 shows the modem's RAT/band config is fully populated — LTE bands 1/3/5/8, mode `lte`.)

**Do not re-open the policyman RAT-mask axis.**

---

## 6. The offline latch: a `cmph.c`-driven `OPRT_MODE_CHGD`, pinpointed to 24 ticks

### 6.1 The MMOC differential

Both arms run the same opening: `Recvd command 4(PROT_REDIR_IND)` → `New transaction 9(DUAL_STANDBY_CHGD)` → `New transaction 0(NULL)`, then `Recvd command 0(SUBSCRIPTION_CHGD)` → `New transaction 1(SUBSC_CHGD)` → `Trans_state 25(WAIT_DEACTD_CNF_GWL)`.

They then **diverge at the `SUBSC_CHGD` completion**:

| stock HMU05 (`stock_boot_211.bin`) | UZ801 B10 (`boot_210_canon.bin`) |
| :-- | :-- |
| `116660  Curr_trans 1(SUBSC_CHGD)` | `519251776  Trans_state 5(PROT_PH_STAT_ENTER)` |
| `116660  Trans_state 5(PROT_PH_STAT_ENTER)` | `519251776  New transaction 0(NULL)` |
| `116664  New transaction 0(NULL)` | `519251780  cmph.c:18094 RAT_DISABLED_MASK: sys_mode 9, is_rat_disabled=0, on as_id 0` |
| `116736  Curr_trans 0(NULL)` | `519251780  cmph.c:18094 RAT_DISABLED_MASK: sys_mode 4, is_rat_disabled=0, on as_id 0` |
| `116740  Trans_state 0(NULL)` | `519251780  cmph.c:32810 After updating from rat_disabled_mask mode_pref 38` |
| `116744  Prot_state [MAIN] 0(NULL) …` | **`519251800  Recvd command 2(OPRT_MODE_CHGD)`** |
| `116748  Prot_state [MAIN] 0(NULL) …` | `519251824  New transaction 3(OFFLINE)` |
| `116776  wms.c:1881 Processing 628 WMS_CMD_CM_PH_EVENT_CB` | `519251896  Trans_state 25(WAIT_DEACTD_CNF_GWL)` |
| | `519252288  Recvd report 0(PROT_DEACTD_CNF)` |
| | `519252352  Trans_state 6(WAIT_PH_STAT_CNF)` |
| | `519252852  Trans_state 5(PROT_PH_STAT_ENTER)` → `New transaction 0(NULL)` |
| | **`519252872  Prot_state [MAIN] 7(OFFLINE)`** ← latched |
| | then `Recvd command 7(DUAL_STANDBY_CHGD)` → `12(MMGSDI_INFO_IND)` **with `Prot_state 7(OFFLINE)` throughout** |

**Three facts, all new or newly precise:**

1. **Stock's `Prot_state [MAIN]` is `0(NULL)` for the entire capture and never becomes `7(OFFLINE)`.** UZ801 latches `7(OFFLINE)` 1 072 ticks after the `OPRT_MODE_CHGD` and stays there.
2. The `OPRT_MODE_CHGD` arrives **24 ticks (0.12 ms) after `PROT_PH_STAT_ENTER`** — a direct causal chain, not a coincidence.
3. **UZ801 emits a `cmph.c` `rat_disabled_mask` re-evaluation in that same 4-tick window; stock emits no `cmph.c` record at all there.** `mode_pref` is `38` in both (§6.3), so the *value* does not differ — what differs is that UZ801's CM **re-runs the mode computation and then declares an op-mode change**.

This confirms Doc 199's "`7(OFFLINE)`'s driver is `Recvd command 2(OPRT_MODE_CHGD)` → `New transaction 3(OFFLINE)`, which stock never gets" and adds the `cmph.c` antecedent.

### 6.2 The `rf_task.c` silence — stock logs RF-task init, UZ801 has no such message

`rf_task.c` appears **only in the stock boot captures**:

```
STOCK-HMU05 211 / 240   ts 78428   rf_task.c:574   RF task  rfm_init OK!
                        ts 78428   rf_task.c:582   RF task  new imei check !
UZ801-B10 canon / v19   (no rf_task.c record at all)
UZ801-B08 live capture  (no rf_task.c record at all)
```

Raw-string census of the **format strings** (not the file names):

| string | UZ801-B08 | UZ801-B10 | HMU05 stock | UFI001B |
| :-- | --: | --: | --: | --: |
| `RF task  rfm_init OK!` | **0** | **0** | **1** | **1** |
| `RF task  new imei check !` | **0** | **0** | 1 | 1 |
| `new imei check` | **0** | **0** | 1 | 1 |
| `rfm_init` (any context) | 2 | 4 | 3 | 3 |
| `rf_task.c` | 1 | 2 | 8 | 9 |

The UZ801 images' two `rfm_init` occurrences are the *failure* string `ulcmd.c / wl1ulhsprachmgr.c : UL_RF_OPT: rfm_init_wcdma_tx failed, sending CPHY Fail to L1M and triggering Mode Offline` — which is present in **both** builds. So the UZ801 build has the RF-task *failure* path but not the *success* log.

**Reading (bounded):** the UZ801 build's RF task does not emit stock's `rfm_init OK!`/`imei check` messages. Combined with §4.4's `no-l1-src` (a prebuilt L1), the parsimonious reading is that the UZ801's RF-task/L1 layer is a **different artifact**, not that RF init fails loudly — **no RF failure message fires in any UZ801 capture** (searched: `Invalid Container`, `triggering Mode Offline`, `CPHY Fail`, `rfm_device`, `handles is NULL`, `antenna_tuner` → **0 hits** in `boot_210_canon.bin`, `uz801_v19_boot_240.bin`, the live B08 capture). So the RF task is **silent**, not demonstrably failing. **Do not claim "the RF init fails" from this section.**

### 6.3 `mode_pref` is not the differential (re-confirms Doc 211 §3.1)

`=CM= mode_pref 38, pref_term 0` appears in **both** arms (`38 = 0x26`), and `=CM= After updating from rat_disabled_mask mode_pref 38` shows the re-evaluation does not change it. The UZ801's `cmph.c` activity is a **re-computation**, not a value change. The differential is the **op-mode change that follows it**.

---

## 7. Closed axes this doc does **not** re-open

| axis | why not |
| :-- | :-- |
| NV / EFS as the gate | Plan A (UZ801 fsg + wiped modemst) → still `offline`; per-chip root key ⇒ UZ801 NV unrecoverable. Unchanged by anything here |
| `cmcc.c` / GROUP 11 | inert by construction (Doc 207) |
| `0x456` / `0x422`, the `u=1` race, the `evt=0` patch, `0x1000001`, `cm_state+0x39e8` | Docs 199–208; nothing here touches them |
| "replace ModemManager with a minimal QMI client" as a *stability* play | A7′ closed (hongho55 still died at ~900 s) |
| the policyman RAT-mask-0 axis | **newly closed by §5** (do not re-open) |
| the 900 s fatal | untouched; this doc is about the `offline` gate only |

---

## 8. Artifacts

| artifact | md5 / size | note |
| :-- | :-- | :-- |
| `evidence/221_b08_live_and_static/uz801_b08_live_online_attempt.bin` | `d56f344ba0576f5b6417a3de45a8a2c5`, 221 750 B | the live F3 capture (§3) |
| `evidence/221_b08_live_and_static/device_modem_manifest.txt` | — | the 21-file on-device modem set (§1.2) |
| `scratch/live_b08/` | — | working copy of the capture |
| `scratch/fwdl/uz801_v32_x/image/` | — | **the deployed set** (B08/-11) |
| `scratch/fwdl/uz801_v1_x/image/` | ver_info `M8916AAAAANLYD1127.2` | the third UZ801 family (159 `rfc_*`, never deployed) |
| `GitIgnore/compare/modem_hmu05_extracted/image/` | — | stock ground truth (168 `rfc_*`) |
| `GitIgnore/UZ801 Modem/` + `scratch/uz21_swap/` | `modem.b16 848abe7e…` | the campaign's B10/-21 set |

Reproduction scripts: the census/`rfc_*`/`rfdevice_*`/string differentials are one-shot `python3 - <<PY` blocks; the parsed numbers are reproduced verbatim in §4–§6. `scratch/f3parse.py` md5 `9f9d6edf15d64faf3b47897f7838c9ac` is required for §3/§5/§6.

---

## 9. Open items this doc creates

1. **Resolve the PA/ASM naming caveat (§4.3).** Map the 16 stock-only `rfdevice_{pa,asm}_*_data_ag` to their `rfdevice_id_enum` values and check whether the identical 168 `rfc_*` card classes reference any of them. If a card class references a device whose *data* the UZ801 build lacks, that is the RFFE blocker made concrete. (Both decompiles are available: `Docs/Modem Stability/Modem RE/{hmu05,uz801}/modem_full_decompiled.c`.)
2. **Identify the HMU05's actual PA/ASM part** (from the HMU05 DT, the OpenWrt board files, or a stock-HMU05 F3 boot log) and check it against the UZ801's 75.
3. **Characterise `no-l1-src` (§4.4).** Is the UZ801's L1 a prebuilt object whose board config is baked in? If so, the RFFE question is "which board was the prebuilt L1 built for", and the answer is not in the string pool.
4. **Boot-capture B08 with the MMOC-visible class.** The live capture is blind (§3.3). A boot capture of the *deployed* B08 build (which has never been captured) would test whether §6.1's chain reproduces on B08, and would let §4.3's devices be matched against a real boot. **Requires deploying the F3 boot instrument** — note the device currently has **only** `/etc/init.d/diag-bind`; `/root/diagboot_run.sh` and `/etc/init.d/diagboot` are **absent**, and the deployed `/root/diag_bind.sh` md5 (`4f84d28dc2162c512dd36d09860988d9`) does **not** match the Doc 210 "working" `4a13dfb8…`.
5. **Do not** re-run the three negative emitter routes of Doc 220 §4.8.

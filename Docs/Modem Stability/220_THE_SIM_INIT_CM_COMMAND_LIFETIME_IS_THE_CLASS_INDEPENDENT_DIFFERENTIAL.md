# 220 — The SIM-init CM command's **lifetime** is the class-independent, zero-overlap differential

**Date:** 2026-09-28
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.
**Status:** **RESULTS (offline; no device interaction this session).**
**Addendum 2026-09-28:** §4.8 added — the *antecedent* of the early free is identified as a UZ801-**build**-only
voice-system-id event (`cmph.c:15013 =CM= vs_id`, `0x10c01000`), confirmed dynamically (5 captures) and
statically (both firmware images). §5 items 5–7 add three corrections; §6 gains the concrete first lever;
§9 items 6–7 re-scope the next test.

**Companions:** `202` (named the `u=1, tsk=cm` CM command as the target), `203` (the capture-class confound
and the `tsk` taxonomy), `204` (the `dcc` 72-tick alloc→free; the race verdict), `208` (the identical CM
dispatcher; the burst-class blocker), `210` (the burst class restored), `211` (the first clean same-window
differential), `214` (the `dcc` carrier), `219` (the last doc before this one).

---

## 1. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Find the **emitter** of UZ801's boot-path `OPRT_MODE_CHGD`=OFFLINE command | a CM-side caller visible in the F3 stream | **Not found as an emitter** — the receipt (`mmocdbg.c:288`, idx 625) has *no* preceding CM op-mode log; the window is all `cmph.c` `RAT_DISABLED_MASK` + MMOC state dumps. The search must stay on the static side, where the route is already known to leave `modem.elf` (Doc 201 §4, `thunk_EXT_FUN_d03d3420`). | NOT MET (bounded) |
| Re-test Doc 218's "the UZ801 MMOC name table is not in the image" | the `0xd0d777xx` targets resolve under the native relation | **Doc 218 STANDS.** The name **strings** *are* in RAM (seg[20], file `0x4f77880`), but **no 4-byte word in the whole coredump equals their cdva, native, or ELF VA** (0 hits × 6 strings × 3 encodings). Consistent with Doc 218's hash/runtime-populated lookup. | MET (Doc 218 upheld) |
| **Precisely characterise the `u=1, tsk=cm` divergence** | a measurable, two-arm, reproducible observable | **FOUND — a 39.7× command-lifetime gap with zero overlap** (§4). | MET |
| Make the observable usable on *any* capture, not only the burst class | an instrument that does not need `mmocdbg.c` > 0 | **FOUND** — the alloc→free tick delta of a named `(u, tsk)` CM command needs only `cmdbg.c`, which the blind class carries (Doc 205 §5). Validated on **16 UZ801 + 2 stock** captures. | MET |
| Verify the finding does not depend on the F3 `code`/mask | a mask-independent observable | **Yes** — it is an intra-capture *difference of two `ts` values from the same record family*; no cross-boot quantity is used. | MET |
| Bound the cause to the command itself | rule the inputs in or out | **MET — the inputs are ruled OUT.** The 45 records preceding the alloc are the **same sequence** in both arms, and the Doc 199/202 "uninitialised session" (`sess_type = 0x7fffffff`) is in **both** (§4.7). | MET |
| **Name what *terminates* the command** (added 2026-09-28) | an antecedent of the free, in both arms | **FOUND — §4.8.** In both arms the free follows a `629 WMS_CMD_CM_SUBS_EVENT_CB` pair; the arms differ only in *when* it fires (UZ801 **+184**, stock **+14 696**). | MET |
| **Identify the trigger of the early `629`** (added) | an event present on one arm only | **FOUND — §4.8.** A UZ801-**build**-only voice-system-id event: `cmph.c:15013 =CM= vs_id` (`0x10c01000`) at +176, → `qmi_nas_cmsubs_evt_cb` + `lte\|wlan_voice_system_id info changed` + the `629`. Confirmed **dynamically** (0 in 3 stock captures incl. the 33 743-msg online one) **and statically** (the strings are absent from the stock firmware image; `qmi_nas.c`/`cmph.c`/`cmdbg.c` present in both as controls). | MET |
| Keep the corpus honest | Doc + ledger entry | This doc + ledger §7 item 25 + §8 rows. Also retracted one **memory-index** claim (§5.6: stock *does* emit `DUAL_STANDBY_CHGD`) and strengthened the `uimsub_manager.c` claim to a static build difference (§5.7). | MET |

**UNEXPECTED:** the campaign's *strongest* existing differential (the `cmmsc_auto.c` file count) is
**binary and class-confounded**; the tick delta is **continuous and class-independent**, and it separates
the arms by more than an order of magnitude.

---

## 2. SOP-compliance statement

Per Doc 197 §1, modelled on Doc 194 §1 (five steps: backup → ground-truth verification against stock HMU05 →
reconcile transport/config → surgical Hexagon patching + re-signing → live empirical validation):

| Step | Taken? | Note |
| :-- | :-- | :-- |
| 1. Backup | **N/A** | This doc is **offline analysis only** — no device write, no firmware change, no EFS change. The captures it reads are the already-archived artifacts of Docs 202–211. |
| 2. Ground-truth verification vs stock HMU05 | **Yes** | The stock arm is **two independent HMU05 stock boots** on the *same* instrument (`stock_boot_240`, `stock_boot_211`), and the trigger record (`wmscfg.c:401 WMS_CFG_EVENT_MS_SIM_INIT_START`) is verified present immediately before the alloc in **every** capture that has the command. |
| 3. Reconcile transport/config | **Yes** | The parser is `scratch/f3parse.py` md5 **`9f9d6edf15d64faf3b47897f7838c9ac`** — the Doc 211 args-shift-fixed parser. The instrument used here parses only `num_args = 0` records (`cmdbg.c CMD alloc/free`), so it is **immune to the args-shift defect by construction**. |
| 4. Surgical Hexagon patching + re-signing | **N/A** | No baseband patching. |
| 5. Live empirical validation | **N/A (this session)** | The result is a **re-measurement of archived captures**; the two-arm separation is reproduced across **18 captures** and **7 distinct firmware/patch generations** (v7 → v19), which is a stronger replication than a single live run. A live re-confirmation is listed as the first open item. |

**Deliberately skipped:** no new capture was taken. The finding is a *derived observable* on existing data;
taking a fresh capture would add a 19th sample but cannot change the zero-overlap result.

---

## 3. Method — the instrument

A CM command is announced by two `num_args = 0` records (`cmdbg.c`):

```
=CM= CMD alloc u=<N>, tsk=<T>
=CM= CMD free  u=<N>, tsk=<T>, ftsk=cm
```

**`u` is the modem uptime in seconds** (Doc 201 §3.2 — the allocator stores a timestamp helper at
`cmd+0x14`; Δts/Δu = 204 800 ± 1.5 %, 8 samples). So `(u, tsk)` is a *key*, and the pair brackets the
command's execution.

**The observable is `ts(free) − ts(alloc)`** for a named `(u, tsk)` key. It is:

- **intra-capture** — no cross-boot clock, no mask, no `code` field, no boot origin;
- **parser-robust** — both records have `num_args = 0`, so the Doc 211 args-shift defect cannot touch them;
- **class-agnostic** — it needs only `cmdbg.c`, which is present in the *blind* class too (Doc 205 §5
  measured `cmdbg.c = 20`, alloc/free only, at `u = 1584`).

Tool: `scratch/emitter18_evidence.py` (this doc's table), `scratch/emitter13_lifetime.py` (per-capture
lifetime dump), `scratch/emitter15_all.py` (all `u=1` commands), `scratch/emitter17b.py` (long commands).

⚠ **Pairing rule.** The allocator/free pair is matched **first-in-first-out per `(u, tsk)` key**, because
several allocs can share a key within one second (e.g. UZ801 allocates seven `u=0, tsk=cm` commands at
`boot_210_canon` idx 478–484). A free consumes the oldest unmatched alloc of that key.

---

## 4. The result

### 4.1 The table

`u=1` CM command lifetimes. **`SIM_INIT b4` = `WMS_CFG_EVENT_MS_SIM_INIT_START` within the 12 records
preceding the first `u=1` alloc.** `resp_off` = offset of the first `wms_sim_mmgsdi_response_cb_proc`
record from that alloc. `cma_after` = offset of the first `cmmsc_auto.c` record after it.

| arm | capture | msgs | `mmocdbg` | **`u=1 cm` life** | SIM_INIT b4 | resp_off | cma_after |
| :-- | :-- | --: | --: | --: | :--: | --: | --: |
| **UZ801** | `doc210/boot_210_canon.bin` | 1 718 | 144 | **284** | yes | 11 744 | **None** |
| **UZ801** | `doc211/uz801_v19_boot_240.bin` | 1 719 | 138 | **292** | yes | 11 548 | **None** |
| **UZ801** | `uz801_boot.bin` | 5 651 | 0 | **284** | yes | 11 032 | **None** |
| **UZ801** | `uz801_boot_4.bin` | 5 837 | 0 | **312** | yes | 10 916 | **None** |
| **UZ801** | `patched_boot.bin` | 2 354 | 0 | **280** | yes | 11 008 | **None** |
| **UZ801** | `reboot_devcfg_boot.bin` | 2 432 | 0 | **340** | yes | 11 100 | **None** |
| **UZ801** | `doc209/boot_early_try1.bin` | 1 917 | 131 | **348** | yes | 11 880 | **None** |
| **UZ801** | `race_boot_1.bin` | 679 | 0 | **296** | yes | 11 808 | **None** |
| **UZ801** | `race_boot_2.bin` | 676 | 0 | **280** | yes | 11 792 | **None** |
| **UZ801** | `race_boot_3.bin` | 673 | 0 | **276** | yes | 11 800 | **None** |
| **UZ801** | `race_boot_4.bin` | 684 | 0 | **280** | yes | 11 800 | **None** |
| **stock** | `doc211/stock_boot_240.bin` | 1 641 | 140 | **14 816** | yes | 11 900 | **14 524** |
| **stock** | `doc211/stock_boot_211.bin` | 1 669 | 142 | **14 852** | yes | 11 924 | **14 548** |
| **stock** | `hmu05_stock_boot.bin` (**reaches `ONLINE_GWL`**) | 33 743 | 327 | **13 808** | yes | 10 948 | **13 488** |

Five further UZ801 captures have **no `u=1 tsk=cm` command at all** — the Doc 203 "burst class", whose
first `u=1` command is `tsk=mmgsdi_1` (and `gstk`): `diag_boot_v7` (`mmgsdi_1`:124, 64; `gstk`:8, 8, 4),
`diag_boot_v8` (128, 132), `diag_boot_v13` (192, 68), `reboot_postpatch` (192, 68),
`g14_test/post_v18_group14_boot` (192, 64). **Every one of their `u=1` commands is also short
(4–192 ticks).** They are excluded from the table because their `u=1` baseline is a different task, so
`resp_off` is not comparable (it reads 434 752–435 008, i.e. ~10× later).

### 4.2 The separation

```
UZ801   u=1 tsk=cm lifetime:  276, 280, 280, 280, 284, 284, 292, 296, 312, 340, 348   (n = 11 captures)
stock   u=1 tsk=cm lifetime:  13 808, 14 816, 14 852                                  (n = 3  captures)

max(UZ801) = 348   <   min(stock) = 13 808            gap = 39.7×   overlap = ZERO
```

**Addendum 2026-09-28:** a **third stock sample** was added — `hmu05_stock_boot.bin`, the 33 743-record
capture that **does reach `ONLINE_GWL`** — at **13 808**. It lowers the gap from 42.6× to **39.7×** and it
matters methodologically: it is the **only** stock capture that gets online, so it rules out "stock is just
early in its boot" as an explanation (the Doc 212 §3.1 trap).

The UZ801 spread is 276–348 (**1.26×**); the stock spread is 13 808–14 852 (**1.076×**). The two arms are
separated by **more than an order of magnitude** and are **nowhere near touching**.

### 4.3 The invariant — the response is *not* the variable

`resp_off` (the first `wms_sim_mmgsdi_response_cb_proc`, `cnf = 51`) is **10 916–11 924 in every one of the
**14** comparable captures** (11 UZ801 + 3 stock; the online capture's `resp_off` is 10 948) — UZ801 and
stock alike, across the whole 276→14 852 lifetime range. That is a
**1.09× spread over an 53× range of command lifetimes.**

**⇒ The SIM/UICC response arrives at the same time on both builds.** The SIM card, the UIM bring-up and the
MMGSDI response path are **not** what differs. What differs is **whether the CM command is still alive when
the response lands.**

### 4.4 What is inside each command — the same window, both arms

`boot_210_canon` (UZ801), `u=1 tsk=cm`, idx 1081 → 1093 (13 records):

```
cmdbg.c      =CM= CMD alloc u=1, tsk=cm
qmi_mmode_task.c   qmi_mmode_task(): loop_sigs 0 set_sigs 0
mmgsdi_refresh.c   Refresh: Refresh State 0x0, Req Type 0x2, retry_req 0x1d
cmph.c       =CM= vs_id 0x10c01000
wms.c        Putting 629 WMS_CMD_CM_SUBS_EVENT_CB
wms.c        Processing 629 WMS_CMD_CM_SUBS_EVENT_CB
mmgsdi_refresh.c   Refresh: ... retry_req 0x0
qmi_nas.c    qmi_nas_cmsubs_evt_cb: default data subs ...
qmi_nas.c    qmi_nas_cmsubs_evt_cb: subscription id ...
qmi_nas.c    lte_voice_system_id info changed
qmi_nas.c    wlan_voice_system_id info changed
qmi_mmode_task.c   qmi_mmode_task()
cmdbg.c      =CM= CMD free u=1, tsk=cm, ftsk=cm
```

`stock_boot_240` (stock), `u=1 tsk=cm`, idx 879 → 931 (**53** records):

```
cmdbg.c      =CM= CMD alloc u=1, tsk=cm
mmgsdi_refresh.c   ×5   (retry_req 1d, 0, 6, 0, 1)
mmgsdiutil.c  UICC SEARCH PATTERN ×4
wms.c         Putting/Processing 626 WMS_CMD_MMGSDI_RESPONSE_CB ×4
wmssim.c      wms_sim_mmgsdi_response_cb_proc  cnf = 51, 26, 26, 26     <-- the response
qmi_nas_mmgsdi.c  new ef spn size: 17 old cached size: 0 session: 0
qmi_mmode_task.c  qmi_mmode_task()
cmmsc_auto.c  =CM= MSC_AUTO: 0x200 & 0x220 = 0x200
cmmsc_auto.c  =CM= CMMSC_AUTO: is_ue_mode_csfb=0 hybr_pref 1 is_cdma_subsc_avail 0
cmmsc_auto.c  =CM= CMMSC_AUTO: updating op_mode, cdma sub 0 hybr1_allowed 1 hybr2_allowed 0
cmmsc_auto.c  =CM= old_op_mode:11 new_op_mode:11 ue_mode:0
mmocdbg.c     =MMOC= Recvd command 0(SUBSCRIPTION_CHGD)
mmocdbg.c     =MMOC= New transaction 1(SUBSC_CHGD)
mmocdbg.c     =MMOC= Trans_state 25(WAIT_DEACTD_CNF_GWL)
mmoc.c        =MMOC=  standby_pref / active_ss / device_mode / active_ss
mmocmmgsdi.c  =MMOC= subsc_chg = 0x28 hybr_gw_subs_chg = 0x0 hybr_gw3_subs_chg = 0x0
wms.c         Putting/Processing 629 WMS_CMD_CM_SUBS_EVENT_CB
cmdbg.c      =CM= CMD free u=1, tsk=cm, ftsk=cm
```

**UZ801's command contains none of `UICC SEARCH PATTERN`, `626 WMS_CMD_MMGSDI_RESPONSE_CB`,
`wms_sim_mmgsdi_response_cb_proc`, `qmi_nas_mmgsdi.c:1368`, `cmmsc_auto.c`, or any `MMOC Recvd command`.**

And in UZ801 the same records **do** happen — just **after** the free:

```
idx 1093  =CM= CMD free u=1, tsk=cm, ftsk=cm            <-- the command ends here (+284)
idx 1097  mmgsdiutil.c  UICC SEARCH PATTERN              (+2 036)
idx 1103  wms.c         Putting 626 WMS_CMD_MMGSDI_RESPONSE_CB
idx 1105  wmssim.c      wms_sim_mmgsdi_response_cb_proc cnf = 51   (+11 744)
idx 1115  qmi_nas_mmgsdi.c  new ef spn size: 17 old cached size: 0  (+11 808)
idx 1116  qmi_mmode_task.c  qmi_mmode_task()
          ---- and then NO cmmsc_auto, NO MMOC command ----
```

### 4.5 The reading

> **Both builds issue the same CM command in response to `WMS_CFG_EVENT_MS_SIM_INIT_START`. Stock's
> command stays alive ~14 820 ticks and therefore *brackets* the UICC search and the MMGSDI response;
> on the response it resumes and calls `cmmsc_auto`, which posts MMOC `SUBSCRIPTION_CHGD` and drives the
> CM serving-system layer. UZ801's command returns after ~285 ticks — 39.7× too early — so the response
> lands on a command that no longer exists, `cmmsc_auto` is never called from that path, MMOC never gets
> `SUBSCRIPTION_CHGD`, and the CM serving-system layer never initialises.**

This is a **mechanism**, not a correlation: the response offset is invariant, so the only free variable is
the command's own lifetime, and the missing downstream records are exactly the ones that the response
would have triggered **inside** the command.

**Leading hypothesis for *why* UZ801's command returns early — ⚠ FALSIFIED 2026-09-28 by §4.7.** The
hypothesis was: the command arms a wait on an MMGSDI request, and on UZ801 that request is made against
the **uninitialised session** (`mmocmmgsdi.c:1718 Found session, sess_type=0x7fffffff, ss=4, session id=0`
— the Doc 199/202 divergence) or is rejected outright (`mmocmmgsdi.c:1712 Invalid session, sess_type=3,
ss=4`), so the wait is never armed. **§4.7 shows BOTH events are present identically in the stock arm**,
immediately before the identical command, so neither can be the cause. The hypothesis is replaced by the
much stronger statement of §4.7: **the inputs are identical; the divergence is inside the command.**

### 4.6 Is UZ801's long `u=0 cm` the *same* command as stock's `u=1 cm`? **NO — refuted.**

§5.4 raised the possibility of a one-command ordering shift. Set-comparing the two **long `tsk=cm`**
windows (UZ801 `boot_210_canon` idx 478→695, 218 records, 43 sites; stock `stock_boot_240` idx 879→931,
53 records, 21 sites):

```
common sites = 4   (cmmsc_auto.c:24189, cmmsc_auto.c:2651, cfm_cpu_monitor.c:308, qmi_mmode_task.c:363)
UZ801-only   = 39  (mmocdbg.c:288 ×84, cmdbg.c:2186 ×62, cmph.c:18094/18103 ×8 each, …)
stock-only   = 17  (mmocdbg.c:291 ×12, mmgsdi_refresh.c:9730 ×5, wms.c:1811/1881 ×5 each,
                    wmssim.c:4322 ×4, mmgsdiutil.c:8242/8260, qmi_nas_mmgsdi.c:1368,
                    cmmsc_auto.c:3241/2849, mmoc.c:10433–10442, mmocmmgsdi.c:706/711)
```

**They are different commands.** Stock's long `u=1 cm` is the **SIM/EF-read** command (it carries
`mmgsdi_refresh`, the `UICC SEARCH PATTERN`, the `626` response callbacks, `qmi_nas_mmgsdi.c:1368`, and
`cmmsc_auto.c:3241/2849`). UZ801's long `u=0 cm` is the **MMOC subscription/op-mode** command (it carries
the MMOC state machine and, later, `OPRT_MODE_CHGD` → `3(OFFLINE)`).

**⇒ The `u=0`/`u=1` difference is not an ordering shift of one command; UZ801 issues an extra, different
long `cm` command at `u=0`.** (Stock's seven unmatched `u=0 tsk=cm` allocs at `stock_boot_240` idx 445–451
are **never freed** anywhere in that capture — so there is no free to pair and no lifetime to compare. That
is a hole in the stock arm, recorded, not explained.)

**The same test on the two `u=1` windows** (UZ801 idx 1081→1093, 13 records, 10 sites; stock idx 879→931,
53 records, 21 sites) gives **1 common site** (`qmi_mmode_task.c:363`) after normalising the per-family line
shifts:

| family | UZ801 line | stock line | shift |
| :-- | --: | --: | --: |
| `cmdbg.c` | 2186 | 2181 | **+5** |
| `mmgsdi_refresh.c` | 9732 | 9730 | **+2** |
| `mmocdbg.c` | 288 | 291 | **−3** |
| `mmgsdiutil.c` | 8267 / 8285 | 8242 / 8260 | **+25** |
| `wms.c` | 1820 / 1890 | 1811 / 1881 | **+9** |
| `cmmsc_auto.c` | 3250 / 2858 | 3241 / 2849 | **+9** |
| `mmoc.c` | 10358 / 10361 | 10433 / 10436 | **−75** |

⚠ **Normalise before any cross-build `file:line` diff** — this is Doc 206 §3's +19 trap in a second place.
After normalisation UZ801's `u=1` window still lacks the `626 WMS_CMD_MMGSDI_RESPONSE_CB` handling, the
`UICC SEARCH PATTERN`, `qmi_nas_mmgsdi.c:1368`, `cmmsc_auto`, and every `MMOC` post; it carries only the
`629 WMS_CMD_CM_SUBS_EVENT_CB` / `qmi_nas.c cmsubs_evt_cb` / `mmgsdi_refresh` ×2 prefix.

### 4.7 The inputs are IDENTICAL — the divergence is strictly *inside* the command (and §4.5's hypothesis is FALSIFIED)

The 45 records preceding the `u=1 tsk=cm` alloc are **the same sequence** in both arms (UZ801
`boot_210_canon` idx 1036–1080 vs stock `stock_boot_240` idx 834–878). After normalising the per-family
line shifts (§4.6) the two windows share **7 of 16 / 13** sites, and the residue is line-shift and
ordering noise, not different events:

```
nvruim.c:12342          nvruim_mmgsdi_evt_cb 0x4
mmocmmgsdi.c:1694/:1637 mmocmmgsdi_card_status_cb: evt=4, sess_id=0
mmocmmgsdi.c:245        Invalid session type=3
mmocmmgsdi.c:250        Sess type=3, ss=4
mmocmmgsdi.c:1712/:1655 Invalid session, sess_type=3, ss=4, session id=0
cfm_cpu_monitor.c:308   ×7
mmocmmgsdi.c:1718/:23933 Found session, sess_type=0, ss=0
qmi_mmode_task.c:363
cfm_cpu_monitor.c:308   ×7 ; DalVAdc.c:1257 ; a2_task.c:818/:817 A2 inactivity timer
mmocmmgsdi_card_status_cb evt=21 → Found session
qmi_mmode_task.c:363
mmocmmgsdi_card_status_cb evt=12 → Found session
wms.c:1820/:1811        Putting 12 WMS_CMD_CFG_SEND_SIM_EFS_READ_EVT
wms.c:1820/:1811        Putting 625 WMS_CMD_MMGSDI_EVENT_CB
wmscfg.c:401            Notify: 19 WMS_CFG_EVENT_MS_SIM_INIT_START
qmi_mmode_task.c:363
```

**★★ The "uninitialised session" is NOT a UZ801 divergence — it is in BOTH arms.** The
`mmocmmgsdi.c:1718` `Found session, sess_type = 0x7fffffff, ss = 4, session id = 0` record (Doc 199 §4 /
Doc 202 §6.3's named divergence) appears in **all four** captures:

| capture | idx | args |
| :-- | --: | :-- |
| UZ801 `boot_210_canon` | 887 | `[2147483647, 4, 0]` |
| UZ801 `uz801_v19_boot_240` | 869 | `[2147483647, 4, 0]` |
| **stock** `stock_boot_240` | **597** | `[2147483647, 4, 0]` |
| **stock** `stock_boot_211` | **653** | `[2147483647, 4, 0]` |

So is `Invalid session, sess_type=3, ss=4, session id=0` (UZ801 `mmocmmgsdi.c:1712` / stock `:1655`).
**⇒ §4.5's leading hypothesis (UZ801's command arms its wait against an uninitialised session) is
FALSIFIED: stock has the identical event immediately before the identical command.**

**⇒ The divergence is strictly inside the `u=1 tsk=cm` command.** Everything the command could read —
the MMGSDI card-status events, the MMOC session lookups, the WMS SIM-init notification, the CFM/A2 state —
is the same on both builds at the moment the command is allocated.

**Two ordering observations (recorded, not interpreted):**
1. **The WMS queue order around the notify differs.** Stock: `Putting 625` → `Putting 12` →
   `Notify SIM_INIT_START` → `Processing 12`. UZ801: `Putting 12` → `Processing 12` → `Putting 625` →
   `Processing 625` → `Notify SIM_INIT_START`. In stock the notify is emitted while the
   `12 SEND_SIM_EFS_READ_EVT` is **put-but-not-yet-processed**; in UZ801 it is emitted after everything is
   processed.
2. **`mmocmmgsdi.c` has a major structural change between the builds.** `Invalid session type=%d` is at
   line **245 in both**; `Invalid session, sess_type` is UZ801 `1712` / stock `1655` (**+57**); but
   `Found session` is UZ801 `1718` / stock **`23933`** — **not a shift at all**. That statement moved to a
   different function. ⚠ **`mmocmmgsdi.c` cannot be diffed by line offset** the way the other families can.

### 4.8 ★★★ The antecedent of the early free — a UZ801-build-only *voice-system-id* event

§4.5 says the command returns early. This section names **what returns it**, and it is a **build
difference**, not a state difference: it is a UZ801-only *source statement* that is absent from the stock
HMU05 image.

**Method.** Anchor at the `u=1 tsk=cm` alloc and ask a different question from §4.4: not "what is *inside*
the window" but **"what immediately precedes the free, in each arm"**. Tool: `scratch/emitter36_afterfree.py`,
`scratch/emitter37_vsid.py`, `scratch/emitter38_cmmsc.py`.

**Observation 1 — in *both* arms the free follows a `629 WMS_CMD_CM_SUBS_EVENT_CB` pair.**

| arm | `629 … Putting` / `Processing` (rel. to alloc) | free | Δ(free − 629) |
| :-- | :-- | --: | --: |
| UZ801 `boot_210_canon` | **+184 / +188** | **+284** | **100** |
| UZ801 `uz801_v19_boot_240` | **+124 / +132** | **+292** | **168 / 160** |
| stock `stock_boot_240` | **+14 696 / +14 704** | **+14 816** | **120 / 112** |
| stock `stock_boot_211` | **+14 732 / +14 740** | **+14 852** | **120 / 112** |
| stock `hmu05_stock_boot` (**online**) | **+13 624 / +13 632** | **+13 808** | **184 / 176** |

The free follows the **same callback** in both arms (Δ is 96–184 ticks, not constant, so this is an
ordering fact, not a fixed latency); the only difference is **when the callback fires** — **~80× earlier**
on UZ801 (+184 vs +14 696).

**The tail is structurally identical in all five captures** — this is what makes the claim strong rather
than merely correlational:

```
UZ801 boot_210_canon:  … +184   629 Putting → +188   629 Processing → +256   qmi_mmode_task.c:363 → +284   FREE
UZ801 uz801_v19:       … +124   629 Putting → +132   629 Processing → +264   qmi_mmode_task.c:363 → +292   FREE
stock stock_boot_240:  … +14696 629 Putting → +14704 629 Processing → +14792 qmi_mmode_task.c:363 → +14816 FREE
stock stock_boot_211:  … +14732 629 Putting → +14740 629 Processing → +14820 qmi_mmode_task.c:363 → +14852 FREE
stock hmu05_online:    … +13624 629 Putting → +13632 629 Processing → +13772 qmi_mmode_task.c:363 → +13808 FREE
```

Same four record *types*, same order, in **all five** — only the absolute offset differs. So the command is
terminated by **the same four-step sequence** on both builds; UZ801 just enters it 14 512 ticks early.

**Observation 2 — the UZ801 `629` is triggered by a UZ801-only `vs_id` event, 8 ticks earlier.**

```
UZ801 boot_210_canon, rel. to the u=1 alloc:
  +176  cmph.c:15013            =CM= vs_id %x                        [0x10c01000]
  +184  wms.c:1820/:1890        Putting / Processing 629 WMS_CMD_CM_SUBS_EVENT_CB
  +248  qmi_nas.c:1329/:1330    qmi_nas_cmsubs_evt_cb  default data subs 1, voice id 0x10c01000,
                                IMS WLAN voice id 0x10002000
  +252  qmi_nas.c:1447/:1454    lte_voice_system_id info changed   [0x10c02000]
                                wlan_voice_system_id info changed  [0x10002000]
  +284  cmdbg.c:2186            =CM= CMD free u=1, tsk=cm, ftsk=cm   <-- ENDS HERE
```

`uz801_v19_boot_240` is the same sequence at +120 / +124 / +256 / +260 / +292. The `vs_id` value is
**`0x10c01000` in both UZ801 boots** — deterministic, not a race.

**Observation 3 — the whole family is UZ801-*build*-only. Two independent instruments agree.**

*Dynamic* — census over every capture (f3parse md5 `9f9d6edf15d64faf3b47897f7838c9ac`):

| family | UZ801 `boot_210` | UZ801 v19 | stock 240 | stock 211 | stock `hmu05_stock_boot` (**33 743 msgs, online**) |
| :-- | --: | --: | --: | --: | --: |
| `cmph.c` `=CM= vs_id` | **1** | **1** | **0** | **0** | **0** |
| `qmi_nas.c` `cmsubs_evt_cb` | **2** | **2** | **0** | **0** | **0** |
| `lte_voice_system_id info changed` | **1** | **1** | **0** | **0** | **0** |
| `wlan_voice_system_id info changed` | **1** | **1** | **0** | **0** | **0** |
| `629 WMS_CMD_CM_SUBS_EVENT_CB` | 2 | 2 | 2 | 2 | 6 |
| `626 WMS_CMD_MMGSDI_RESPONSE_CB` | 28 | 28 | 28 | 28 | 28 |

The three stock captures include one of **33 743 records that reaches `ONLINE_GWL`** — so this is not
"stock had not got there yet" (the §3.1 trap of Doc 212). The family is **absent by construction**.

*Static* — the log-format strings in the two firmware images (whole-image search):

| string | stock `hmu05_modem.bin` | UZ801 `modem.bin` |
| :-- | --: | --: |
| `=CM= vs_id` | **0** | **1** |
| `default data subs` | **0** | **1** |
| `voice id - %d` | **0** | **1** |
| `lte_voice_system_id` | **0** | **1** |
| `wlan_voice_system_id` | **0** | **1** |
| `cmsubs_evt_cb` | **0** | **1** |
| — controls — | | |
| `qmi_nas.c` | **2** | **2** |
| `cmph.c` | **2** | **2** |
| `cmdbg.c` | **1** | **1** |

The controls (`qmi_nas.c`, `cmph.c` are in **both** images) show the negative is not a coverage gap: the
*file* exists on both sides, the *statement* exists on one.

**★ A third firmware control — the family is UZ801-*firmware*-only.** The same search on the **UFI001B**
image (`GitIgnore/compare/modem_hmu05_vs_ufi001b/ufi001b_modem.bin`) returns **0** for all three families:

| string | HMU05 stock | **UFI001B** | UZ801 |
| :-- | --: | --: | --: |
| `=CM= vs_id` | 0 | **0** | **1** |
| `cmsubs_evt_cb` | 0 | **0** | **1** |
| `uimsub_manager.c` | 0 | **0** | **1** |

So this is **not** "a feature the HMU05 build happened to leave out" — it is a feature present in the UZ801
build and in **neither** of the other two msm8916 modem builds available locally. That is the shape of a
**build/feature-flag** difference, which is what a cross-tree port should expect.

**★ Bounded negative — the emitter is not reachable by the descriptor route (yet).** `cmph.c:15013`'s F3
descriptor is `{packed = 0x3aa50005, word1 = 0x00627ab7}` (level 5; found by scanning `modem.elf` for
`(15013 << 16) | level`, `scratch/emitter42_desc.py`). But:

* the string's **native / ELF / coredump VA** (`0xd04139bb` / `0xc45629bb` / `0x8ad629bb`) has **0
  occurrences in all three images** (`scratch/emitter41_vsidptr.py`) — the same hash/runtime-populated
  lookup Doc 218 proved for the MMOC name table;
* the descriptor VA `0xc3df43dc` has **0 occurrences** as a 4-byte word in all three images;
* `logsite.py refs … --file=cmph.c` returns **0 log-site literals** — the identical method artefact Doc 207
  §7.2 documented for `cmmsc_auto.c` and Doc 220 §9 item 6 for `mmgsdi_refresh.c`.

⇒ The emitter hunt needs a **different** instrument than the descriptor→code join. This is recorded as
§9 item 6's state, not as a result.

**Observation 4 — the consequence is exactly the §4.4/§5.2 missing work, now with positions.**

| record | UZ801 `boot_210` | UZ801 v19 | stock 240 | stock 211 |
| :-- | :-- | :-- | :-- | :-- |
| `cmmsc_auto` burst **#1** (op_mode **10→11**, mask `0xEBE`=3774) | 1, **before** the alloc | 1, before | 1, before | 1, before |
| `cmmsc_auto` burst **#2** (op_mode **11→11**, policyman mask `0x220`=544) | **0** | **0** | **1 — INSIDE**, +14 524 | **1 — INSIDE**, +14 548 |
| MMOC `Recvd command 0(SUBSCRIPTION_CHGD)` **#2** | **0** | **0** | **1 — INSIDE**, +14 536 | **1 — INSIDE**, +14 560 |
| MMOC `New transaction 1(SUBSC_CHGD)` **#2** | **0** | **0** | **1 — INSIDE**, +14 560 | **1 — INSIDE**, +14 588 |

Both arms run burst #1 *before* the command. Only stock runs burst #2, and only stock's burst #2 is
**inside** the command — i.e. it is reached *because the command was still alive* when the MMGSDI response
landed. UZ801's command is already gone (freed at +284, response at +11 744).

**The reading (a mechanism, with one stated caveat).**

> **A UZ801-build-only voice-system-id change (`cmph.c:15013 =CM= vs_id`, value `0x10c01000`) fires ~176
> ticks into the SIM-init CM command. It drives `qmi_nas_cmsubs_evt_cb` / `lte|wlan_voice_system_id info
> changed` and a `629 WMS_CMD_CM_SUBS_EVENT_CB`, which **completes the command at +284**. The command
> therefore never brackets the MMGSDI response (+11 744), so the post-response path — `qmi_nas_mmgsdi.c:1368`
> → `cmmsc_auto` burst #2 → MMOC `SUBSCRIPTION_CHGD` → `New transaction 1(SUBSC_CHGD)` — is never taken from
> this command. Stock has no such event, so its identical command stays alive to +14 816 and runs that path
> in full.**

**⚠ Caveat, stated explicitly.** The F3 stream is shared: a record whose `ts` falls between the alloc and
the free is not *proven* to be emitted by the command's own handler. The `vs_id` event is therefore the
**prime causal candidate**, not a proven cause. What *is* proven is the tight coupling: the `vs_id` family
fires **exactly once per UZ801 boot**, **inside** the `u=1 tsk=cm` window, **8 ticks** before the `629` that
is **100 ticks** before the free; and the family does not exist in the stock build at all. §9 item 6's
"code test" is therefore re-scoped: the next test is to **find the emitter of `cmph.c:15013` and its
trigger**, not the command's handler.

---

## 5. Corrections and scope

1. **Doc 203 §3.2's class taxonomy is not clean.** `boot_210_canon` carries **burst-grade coverage**
   (`mmocdbg.c = 144`) yet its first `u=1` command is `tsk=cm`, not `tsk=mmgsdi_1`. So
   "burst class ⇒ `mmgsdi_1` wins the `u=1` race" does not hold for the Doc 210-era captures. The
   *lifetime* observable does not care: it is short on **both** classes (11 captures `cm`: 276–348;
   5 captures `mmgsdi_1`/`gstk`: 4–192).
2. **Doc 204 §6's "class-invariant gate" needs a footnote.** Its "`cmmsc_auto.c` = 0 in all 22 UZ801
   captures" is **not true of the Doc 210/211 captures**: `boot_210_canon` has `cmmsc_auto.c` **4** and
   `uz801_v19_boot_240` has **4** (Doc 210 already refuted the "never runs" form). The correct statement
   is **"the *second* `cmmsc_auto` burst — the one inside the SIM-init command — is absent"**, which is
   what §4.4 shows.
3. **The instrument does not need the burst class.** Doc 208 §4's admissibility rule (`mmocdbg.c` > 0) was
   introduced for *file-presence* negatives. The lifetime observable is admissible on **any** capture with
   `cmdbg.c`, including the blind class — which is 7 of the 11 UZ801 samples above.
4. **Structural note (measured, not interpreted):** UZ801 also runs a **long `tsk=cm` command at `u=0`**
   — `boot_210_canon` idx 478→695 = **15 812** ticks, `uz801_v19_boot_240` idx 497→701 = **17 620** ticks
   — whose window contains the *first* `cmmsc_auto` (idx 514–517), MMOC `SUBSCRIPTION_CHGD` (523),
   `New transaction 1(SUBSC_CHGD)` (530), and later `OPRT_MODE_CHGD` (625) → `New transaction 3(OFFLINE)`
   (632) → `Prot_state [MAIN] 7(OFFLINE)` (683). Stock's 240 s capture has **no** `u=0 tsk=cm`; it has
   seven unmatched `u=0 tsk=cm` allocs (idx 445–451) that are **never freed** in the capture.
   `stock_boot_211` has three short ones (3 116 / 3 120 / 3 120). **§4.6 resolves the question this
   raised: the two long `cm` windows are *different commands*, so this is not an ordering shift.**
5. **The `u=1` window is *not* a content differential — only a *free-timestamp* differential.** §4.4's
   phrasing ("UZ801's command contains none of …") is true of the **window**, but the *content* is present
   on UZ801 too, at the same relative offsets, **after** the free (§4.4's own second listing, and §4.8's
   Observation 4). ⚠ **Do not read a window census as a content census.** The correct statement is:
   *the command is released early; the modem's downstream work is unchanged.* `scratch/emitter32_window.py`
   and `scratch/emitter33_intrawindow.py` are the tools; the latter's `mmgsdi_refresh.c` row is the tell
   (`2 / 2` inside the window, `5 / 5` in the whole capture on both arms).
6. **A memory-index claim is retracted (docs were right, the memory was wrong).** The index line for
   `project_uz801_mmoc_divergence.md` said stock "never emits `DUAL_STANDBY_CHGD`". **Stock emits it in
   both boot captures** — `Recvd command 7(DUAL_STANDBY_CHGD)` ×1 and `New transaction : 9(DUAL_STANDBY_CHGD)`
   ×1, **exactly as UZ801 does** (Doc 212 §2 already recorded this). The `0` that was mis-read is the
   **long online** capture (`hmu05_stock_boot.bin`), where the count really is 0. Correction applied to
   the memory file this session; no doc change needed.
7. **`uimsub_manager.c` is a UZ801-*build*-only module (static proof added).** Doc 211/212/213/214
   established it as UZ801-only at *runtime* and called the UZ801-only file set "a candidate set, not a
   mechanism". The static form is stronger: the string `uimsub_manager.c` is **absent from the stock HMU05
   firmware image** (whole-image and per-segment), while **358 of 513** neighbouring strings in the same
   phdr[19] pool *are* present in stock — so it is a per-file build difference, not a coverage gap. ⚠ It is
   nonetheless **one of ~155** string differences in a single ±0x6000 window, so it remains a lead, not a
   cause. Tool: `scratch/emitter30_ctrl.py`.

---

## 6. What this changes for the port

- The target is no longer "find a caller of `cmmsc_auto`" (Doc 206 §4.2 / Doc 207 §5 closed that) nor
  "restore the burst class" (Doc 208 §6 — Doc 210 restored it, and this doc shows it was never needed
  for this question). **The target is: make the SIM-init CM command wait for its MMGSDI response.**
- **★ §4.8 gives that target a concrete first handle.** The command is completed by a
  `629 WMS_CMD_CM_SUBS_EVENT_CB` that, on UZ801, is triggered **~176 ticks in** by the UZ801-build-only
  `cmph.c:15013 =CM= vs_id` event (`0x10c01000`). So the two new candidate levers are, in order of cost:
  1. **the `vs_id` emitter** — find `cmph.c:15013`'s call site and its trigger (a *code* question, in the
     UZ801 build only, so it can be answered offline from `Modem RE/uz801/modem_full_decompiled.c`);
  2. **the subscription-event path** — `qmi_nas_cmsubs_evt_cb` / `629 WMS_CMD_CM_SUBS_EVENT_CB`.
  Either way the scoring instrument is unchanged (§6's pre-registration below) and still costs one boot.
- **A new, cheap scoring instrument exists.** Any candidate patch can be scored by one boot:
  `u=1 tsk=cm` lifetime → **14 800** (fixed) vs **~300** (not fixed). This needs **one boot and one
  capture**, not a burst-class capture, and the pass/fail boundary (300 vs 14 800) is 42× wide.
- **A pre-registration is now possible** (Doc 197 / `feedback_measurement_discipline.md`): the patch is
  accepted iff `u=1 tsk=cm` lifetime ≥ 10 000 ticks **and** `cmmsc_auto.c` appears inside the window
  **and** MMOC `Recvd command 0(SUBSCRIPTION_CHGD)` appears inside it. n = 3 boots (the UZ801 spread is
  1.26×, so 3 is ample at a 30× margin).

---

## 7. Traps

1. **`u` is not a command handle — it is the modem uptime in seconds** (Doc 201 §3.2). Several commands
   share a `(u, tsk)` key; pair **FIFO**, never by uniqueness.
2. **`num_args = 0` is the reason this instrument is trustworthy.** Do not extend the key to records with
   args without re-checking the Doc 211 args-shift fix.
3. **`resp_off` is only comparable when the baseline is the same task.** For the five captures whose
   `u=1` is `mmgsdi_1`, the baseline differs and the offset is meaningless (it reads ~435 000).
4. **`boot_210_canon` is a *burst-grade* capture with a `cm`-winner `u=1`.** Do not infer the class from
   `mmocdbg.c` alone.
5. **The `code` field is per-boot, not the mask** (Doc 208 §4). Nothing here reads it.
6. **`f3clean.py`'s median-`ts` window deletes boot chunks** (Doc 202). Nothing here uses it.
7. **Do not read the `u=1` lifetime off a *live* capture** — `cntl-enable` flushes the modem's buffered
   F3 history, so a live capture is valid for presence, not for a bracketed alloc→free pair that may
   straddle the flush (Doc 205 §7).
8. **★ Normalise the per-family line shift before any cross-build `file:line` diff.** The shifts are
   **not uniform**: `cmdbg.c` +5, `mmgsdi_refresh.c` +2, `mmocdbg.c` −3, `mmgsdiutil.c` +25, `wms.c` +9,
   `cmmsc_auto.c` +9 (only from line 2849 up), `mmoc.c` **−75**. A raw set-diff of two windows will report
   the *same* statement as UZ801-only **and** stock-only. §4.6's table is the calibration for this pair of
   builds.

---

## 8. Artifacts

| Artifact | md5 / note |
| :-- | :-- |
| `scratch/emitter18_evidence.py` | **new** — the §4.1 table generator (the doc's evidence script) |
| `scratch/emitter13_lifetime.py` | **new** — per-capture alloc→free lifetime dump for every `(u, tsk)` |
| `scratch/emitter15_all.py` | **new** — all `u=1` commands, any `tsk` |
| `scratch/emitter17b.py` | **new** — long (≥ 1 000 tick) commands + all `u=1` |
| `scratch/emitter19_setdiff.py` | **new (Doc 220 §4.6)** — the set-compare of the two long `tsk=cm` windows, and the `u=0 tsk=cm` alloc/free dump for stock |
| `scratch/emitter20_before.py` | **new (Doc 220 §4.7)** — the 45 records preceding the `u=1 tsk=cm` alloc, both arms |
| `scratch/emitter21_crux.py` | **new (§4.7/§4.8)** — align both arms at `New transaction 9(DUAL_STANDBY_CHGD)`, dump forward |
| `scratch/emitter22_counts.py` | **new (§5.6)** — per-capture MMOC marker census (the `DUAL_STANDBY_CHGD` correction) |
| `scratch/emitter23_fmts.py` | **new (§5.6)** — the exact `fmt` strings for the MMOC transaction/`Prot_state` records |
| `scratch/emitter24_lifecycle.py` | **new** — the MMOC transaction state machine, side by side |
| `scratch/emitter25_dcc.py` / `emitter31_opmode.py` | **new** — the `dcc`/op-mode carriers and the unfiltered window around `OPRT_MODE_CHGD` |
| `scratch/emitter26_gap.py` | **new** — the `WAIT_DEACTD_CNF_GWL → PROT_PH_STAT_ENTER` gap, both arms |
| `scratch/emitter27_files.py` | **new (§5.7)** — the file-presence census for the gap-specific files |
| `scratch/emitter28_pool.py` | **new (§5.7)** — maps the F3 source-file strings to ELF segments (Hexagon ⇒ **ELF32**, `e_phoff` at `0x1c`) |
| `scratch/emitter30_ctrl.py` | **new (§5.7)** — the ±0x6000 local control for the `uimsub_manager.c` static negative |
| `scratch/emitter32_window.py` / `emitter33_intrawindow.py` | **new (§5.5)** — the `u=1` window dump and the intra-window record census |
| `scratch/emitter34_refresh.py` | **new (§5.5)** — the `mmgsdi_refresh.c` census; shows the group is complete on **both** arms |
| `scratch/emitter35_cmds.py` | **new** — every `cmdbg.c` CMD alloc/free record, in `ts` order |
| `scratch/emitter36_afterfree.py` | **new (§4.8)** — what UZ801 emits *after* the `u=1` free, annotated IN/FREE/OUT |
| `scratch/emitter37_vsid.py` | **new (§4.8)** — the `vs_id` / `cmsubs_evt_cb` / `voice_system_id` family census, 5 captures |
| `scratch/emitter38_cmmsc.py` | **new (§4.8)** — `cmmsc_auto.c` / MMOC `SUBSCRIPTION_CHGD` positions relative to the `u=1` alloc |
| `scratch/emitter39_final.py` | **new (§4.8)** — **the single consolidated reproduction** of every §4.8 number (the lifetime + the four-step tail in all 5 captures, the vs_id census, the `cmmsc_auto` positions, and the static string table) |
| `scratch/emitter40_vsidva.py` | **new (§4.8)** — maps the `vs_id` family strings to file offset / coredump VA / ELF VA / native VA in the UZ801 coredump |
| `scratch/emitter41_vsidptr.py` | **new (§4.8)** — the three-encoding pointer search for those strings (0 hits in all three images) |
| `scratch/emitter42_desc.py` | **new (§4.8)** — finds the F3 descriptor `{packed=(line<<16)\|level, word1}` for `cmph.c:15013` in `modem.elf` / the coredump |
| `scratch/emitter8_traj.py` … `emitter16_u0cm.py` | **new** — the intermediate probes (op-mode trajectory, `dcc`, the `u=0 cm` window, the Doc 218 re-test) |
| `scratch/emitter7_tbltest.py` | **new** — the Doc 218 re-test. ⚠ it must `os.chdir()` into `scratch/coredump_uz801/` first, because `xlat.py` opens the coredump by a **relative** path |
| `scratch/doc210/boot_210_canon.bin` | `b693faebb2bab65ca5c9eb948285658b` |
| `scratch/doc211/uz801_v19_boot_240.bin` | `81318b04f7e0da3e74675d2973366758` |
| `scratch/doc211/stock_boot_240.bin` | `0ff4e4d22c23443e2c6e82bcb24cbc83` |
| `scratch/doc211/stock_boot_211.bin` | `e49515eff18afcf069215bde6a35a835` |
| `scratch/uz801_boot.bin` | `34208c078aa8aae3402cce3faa98dea9` |
| `scratch/uz801_boot_4.bin` | `ebf0d6b9d2689d92ea0fcea8ff569cfa` |
| `scratch/patched_boot.bin` | `6486848163eb5519b221d075d04d99ae` |
| `scratch/reboot_devcfg_boot.bin` | `975c28fa10c355b8a913194b746c05e2` |
| `scratch/doc209/boot_early_try1.bin` | `bd3d79d93a590af50fcdf53161e0b084` |
| `scratch/race_boot_1..4.bin` | `83b7a263…` / `565c4a03…` / `6858004e…` / `a7f55a97…` |
| `scratch/diag_boot_v7.bin` | `ea1dac6332d69634b4e964301333ef97` |
| `scratch/diag_boot_v8.bin` | `1e393ef3de2537ec02dd1f5a71dec0e7` |
| `scratch/diag_boot_v13.bin` | `934e10b19c472dc0460255ab9372e64e` |
| `scratch/reboot_postpatch.bin` | `d4f1017fd49c713b15968f9a6314939f` |
| `scratch/g14_test/post_v18_group14_boot.bin` | `73843bfcd0975f8c9c171e6436df1475` |
| `scratch/f3parse.py` | `9f9d6edf15d64faf3b47897f7838c9ac` (the Doc 211 fixed parser) |

---

## 9. Open items (ordered)

1. **Live re-confirmation (n = 1).** Boot UZ801 v19, capture with `diagboot`, and confirm the `u=1 tsk=cm`
   lifetime is ~300 — the first *live* sample of the observable, and the calibration for the pre-registration
   in §6.
2. ~~Resolve §5.4 — is UZ801's long `cm` command the *same* command as stock's, shifted from `u=1` to
   `u=0`, or a different one?~~ **CLOSED 2026-09-28 (§4.6) — they are DIFFERENT commands** (4 common sites
   of 43 vs 21; UZ801's is the MMOC subscription/op-mode command, stock's is the SIM/EF-read command).
   Tool: `scratch/emitter19_setdiff.py`.
3. ~~Test the §4.5 hypothesis — does UZ801's SIM-init command reference the uninitialised session?~~
   **CLOSED 2026-09-28 (§4.7) — the hypothesis is FALSIFIED.** The `sess_type = 0x7fffffff` and
   `Invalid session, sess_type = 3` events are in **both** arms, immediately before the identical command;
   the 45 records preceding the alloc are the same sequence. **The divergence is inside the command.**
   Tool: `scratch/emitter20_before.py`.
   **The next test therefore has to be a *code* test, not an input test** — see item 6.
6. **Locate the command's handler — RE-SCOPED 2026-09-28 by §4.8.** The pre-command inputs are ruled out
   (§4.7), so the difference is in the command's own lifetime. **§4.8 narrows it further: the terminator is
   known** (a `629 WMS_CMD_CM_SUBS_EVENT_CB` that fires ~80× early on UZ801) **and its trigger is a
   UZ801-build-only statement** (`cmph.c:15013 =CM= vs_id`). The next test is therefore **not** "find the
   command's handler" but **"find the emitter of `cmph.c:15013` and what calls it"** — a *single-build*
   question, answerable offline from `Modem RE/uz801/modem_full_decompiled.c` (110 MB, 81 832 fns), with the
   F3 descriptor table (`scratch/logsite.py` / `desc.py`) as the statement→code join.
   **⚠ STATE AS OF 2026-09-28: all three cheap routes to the emitter are NEGATIVE (§4.8's bounded negative).**
   The string has **0** pointer occurrences in three encodings across three images; the descriptor VA
   `0xc3df43dc` has **0** occurrences as a 4-byte word; and `logsite.py refs --file=cmph.c` returns **0
   log-site literals** (the Doc 207 §7.2 artefact, now seen for a third file). **The descriptor *is* found**
   — `{packed = 0x3aa50005, word1 = 0x00627ab7}`, level 5, via `scratch/emitter42_desc.py` — so the next
   route is the **descriptor-hash cross-build key** of Doc 208 §9 item 2: compute the hash of the statement
   and search the decompile for it, or find the per-file descriptor *base* and the indexing code. **Do not
   repeat the three negative routes.**
7. **Find what *generates* the `vs_id` value `0x10c01000`.** It is identical in both UZ801 boots, so it is
   deterministic. Decode the mask (`0x10c01000` / `0x10c02000` / `0x10002000`) and locate the CM path that
   computes it. If it is a *band/RAT* mask derived from the RF configuration, this is the WTR1605-vs-WTR4905
   mismatch surfacing inside CM — which would make §4.8 the first *mechanistic* link between the RF axis
   (`project_ufi001b_rf_transplant.md`) and the `offline` gate.
4. **Pre-register and score the first patch** against the §6 criterion (n = 3 boots).
5. **Do not** re-open: the `cmcc.c` axis (Doc 207 §5/§6), GROUP 11, `0x456`/`0x422`, the `u=1` race
   (Doc 204), the `evt=0` patch (Doc 204), the F3 burst class as a *prerequisite* for this question
   (this doc), or "the CM code differs" (Doc 208 §3).

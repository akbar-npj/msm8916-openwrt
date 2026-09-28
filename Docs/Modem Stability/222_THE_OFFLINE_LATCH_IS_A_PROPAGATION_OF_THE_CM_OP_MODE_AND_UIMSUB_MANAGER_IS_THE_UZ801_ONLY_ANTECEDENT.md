# Doc 222 — The offline latch is a *propagation* of the CM's op-mode, and its UZ801-only antecedent is `uimsub_manager.c`

**Date:** 2026-09-28
**Ledger:** this doc is an entry in `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` — see **§7 item 28** (findings) and the **§8 evidence rows** it adds. Read §7 of the ledger first; it is the cumulative index.
**Predecessors:** Doc 220 (§4.8 — the `vs_id` event), Doc 221 (§6.1 — the `SUBSC_CHGD` → `OPRT_MODE_CHGD` chain), Doc 214 (`tsk=dcc` is the AP op-mode carrier).
**Instrument:** `scratch/f3parse.py` md5 **`9f9d6edf15d64faf3b47897f7838c9ac`** (required — an older parser mis-reads this corpus).
**Reproduction:** `scratch/doc222_latch_differential.py` (one script, all four sections below).

---

## 0. SOP compliance

| SOP step (Doc 197) | status |
| :-- | :-- |
| verify VAs / disassembly / hashes against stock-HMU05 ground truth | **taken** — every claim is a record-for-record differential against `stock_boot_211.bin` / `stock_boot_240.bin` / `hmu05_stock_boot.bin` (the 33 743-record ONLINE control) |
| comparative SOP — stock arm required | **taken** — three stock captures, one of which reaches ONLINE |
| no blind baseband patching | **taken** — this doc patches nothing; it is a differential |
| "Achieved vs Expected" table | **§7** |
| ledger updated in the same session | **taken** — §7 item 28 + §8 rows |
| archive-first / freeze-hash captures | **taken** — §1–§4 use pre-existing frozen `.bin` files; §5.2's new live capture is frozen as `scratch/live_b08/online_try_222.bin` md5 `15a89f35960349838c46fdb1209a1196` |
| assert a control TOOK | **taken** — §5.2 asserts the capture is MMOC-**blind** (`mmocdbg.c` = 0 of 699) before drawing any negative from it; §1's stock ONLINE capture is asserted to have actually processed an `OPRT_MODE_CHGD` |
| pre-register n | **n/a** — no live scoring criterion is created here |

**Scope statement.** This doc characterises the *boot-time* `offline` latch only. It does **not** touch the 900 s fatal, the bam_dmux defect set, or the RF-device question.

---

## 1. The headline correction: `OPRT_MODE_CHGD` is a *mirror*, not a trigger

Doc 221 §6.1 read the chain `cmph.c RAT_DISABLED_MASK` → `Recvd command 2(OPRT_MODE_CHGD)` → `New transaction 3(OFFLINE)` as "UZ801's CM declares an op-mode change". That is **incomplete in a load-bearing way**, and the control that shows it was already in the corpus:

```
hmu05_stock_boot.bin  (the ONLINE control), ts 3040011632:
  878  3040011632  =MMOC= Recvd command 2(OPRT_MODE_CHGD)
  879  3040011636  =MMOC= Curr_trans  0(NULL)
  880  3040011640  =MMOC= Trans_state 0(NULL)
  881  3040011644  =MMOC= Prot_state [MAIN] 0(NULL)   (unchanged)
  885  3040011660  =MMOC= New transaction           : 2(ONLINE)     <-- ONLINE
  886  3040011852  cmregprx.c:14031  =CM= ph_stat_chgd: MMOC->CM: ue_mode= 0, ...
  889  3040011860  =MMOC= Trans_state 6(WAIT_PH_STAT_CNF)
  892  3040011876  =MMOC= Recvd report 2(PH_STAT_CHGD_CNF)
```

**Stock receives the very same `OPRT_MODE_CHGD` and the MMOC opens `New transaction 2(ONLINE)`.** The MMOC's handler for `OPRT_MODE_CHGD` simply **transitions to the CM's current op-mode**:

| arm | `OPRT_MODE_CHGD` → | op-mode the CM held |
| :-- | :-- | :-- |
| stock, `hmu05_stock_boot` | `New transaction 2(ONLINE)` | ONLINE |
| UZ801, `boot_210_canon` | `New transaction 3(OFFLINE)` | OFFLINE |

⇒ **`3(OFFLINE)` is a propagation of an op-mode the CM already holds; it is not itself the decision.** The question the campaign has been asking ("what makes MMOC go OFFLINE?") therefore decomposes into "why does UZ801's **CM** hold OFFLINE at this point, and why does it emit an `OPRT_MODE_CHGD` at all?"

`OPRT_MODE_CHGD` counts (whole-capture):

| capture | `OPRT_MODE_CHGD` | `7(OFFLINE)` |
| :-- | --: | --: |
| `stock_boot_211` | 0 | 0 |
| `stock_boot_240` | 0 | 0 |
| `hmu05_stock_boot` (**ONLINE**) | **1** | **0** |
| `boot_210_canon` (UZ801 B10) | 1 | **26** |
| `uz801_v19_boot_240` (UZ801 B10) | 1 | **26** |

**One `OPRT_MODE_CHGD` is normal and benign — stock emits one and never latches.** UZ801's single one latches.

---

## 2. The boot `SUBSC_CHGD` transaction, record for record

Both arms run the identical opening (`Recvd command 4(PROT_REDIR_IND)` → `9(DUAL_STANDBY_CHGD)` → `0(NULL)` → `Recvd command 0(SUBSCRIPTION_CHGD)` → `New transaction 1(SUBSC_CHGD)` → `Trans_state 25(WAIT_DEACTD_CNF_GWL)`), then sit in `WAIT_DEACTD_CNF_GWL` for ~13 400 ticks. They then diverge:

| | stock (`stock_boot_211`) | UZ801 (`boot_210_canon`) |
| :-- | :-- | :-- |
| `SUBSC_CHGD` start | `103088  New transaction 1(SUBSC_CHGD)` | `519238288  New transaction 1(SUBSC_CHGD)` |
| `WAIT_DEACTD_CNF_GWL` | `103212` | `519238400` |
| next MMOC record | `108928  Recvd report 2(PH_STAT_CHGD_CNF)` | **`519251776  Trans_state 5(PROT_PH_STAT_ENTER)`** |
| then | `108932  Trans_state 6(WAIT_PH_STAT_CNF)` | `519251776  New transaction 0(NULL)` |
| then | `116660  Trans_state 5(PROT_PH_STAT_ENTER)` | `519251800  Recvd command 2(OPRT_MODE_CHGD)` |
| then | `116664  New transaction 0(NULL)` | `519251824  New transaction 3(OFFLINE)` |
| final | `116744  Prot_state [MAIN] 0(NULL)` | `519252872  Prot_state [MAIN] 7(OFFLINE)` |

**Two concrete differences:**

1. **Stock's transaction receives `Recvd report 2(PH_STAT_CHGD_CNF)` and passes through `Trans_state 6(WAIT_PH_STAT_CNF)`; UZ801's does not** — it jumps straight from `25(WAIT_DEACTD_CNF_GWL)` to `5(PROT_PH_STAT_ENTER)`.
2. **UZ801 then receives an `OPRT_MODE_CHGD`; stock does not.**

The same asymmetry holds for stock's *second* `SUBSC_CHGD` (`412060` → `412348 Recvd report 0(PROT_DEACTD_CNF)` → `412728 Trans_state 6(WAIT_PH_STAT_CNF)` → `425840 Trans_state 5(PROT_PH_STAT_ENTER)` → `425856 Prot_state [MAIN] 0(NULL)`): it too completes cleanly.

**Do not over-read (1).** The `PH_STAT_CHGD_CNF` report is a *report*, not a command; the difference is consistent with the two builds taking different internal paths, and this doc does **not** claim the missing report *causes* the `OPRT_MODE_CHGD`. What is claimed is the pairing: **only UZ801 emits an `OPRT_MODE_CHGD` at this point.**

---

## 3. The UZ801-only antecedent: `uimsub_manager.c`

The records immediately preceding UZ801's `OPRT_MODE_CHGD`, unfiltered (`boot_210_canon`, i = 606…625):

```
606  519239196  cmss.c:13085        =CM= cmss_cmd_check, cmd=0, asubs_id=0
607  519249516  uimsub_manager.c:809 UIM_1: sub_mgr slot : iccid 0x3 = 0x1
608  519249520  uimsub_manager.c:809 UIM_1: sub_mgr slot : iccid 0x5 = 0x70
609  519249520  uimsub_manager.c:809 UIM_1: sub_mgr slot : iccid 0x7 = 0x22
610  519251728  sdss.c:47745         =SD= sdss_get_scan_scope_rule()-scan_scope_rule=0
611  519251728  sdcmd.c:27798        =SD= hybr_name_sel()-is_gw2_subsc_avail:0,simstate:0
612  519251768  cmph.c:32761         =CM= mode_pref 38, pref_term 0
613  519251768  cmph.c:32771         =CM= rat_disabled_mask as_id_0 0x0, as_id_1 0x0
614  519251776  mmocdbg.c:288        =MMOC= Trans_state 5(PROT_PH_STAT_ENTER)
615  519251776  mmocdbg.c:288        =MMOC= New transaction : 0(NULL)
616  519251780  cmph.c:18094         =CM= RAT_DISABLED_MASK: sys_mode 9, is_rat_disabled=0, on as_id 0
617  519251780  cmph.c:18103         =CM= rat_disabled_mask as_id_0 0x0, as_id_1 0x0
618  519251780  cmph.c:18094         =CM= RAT_DISABLED_MASK: sys_mode 4, is_rat_disabled=0, on as_id 0
619  519251780  cmph.c:18103         =CM= rat_disabled_mask as_id_0 0x0, as_id_1 0x0
620  519251780  cmph.c:32810         =CM= After updating from rat_disabled_mask mode_pref 38
621  519251780  mmocdbg.c:288        =MMOC= Curr_trans  0(NULL)
622  519251784  mmocdbg.c:288        =MMOC= Trans_state 0(NULL)
625  519251800  mmocdbg.c:288        =MMOC= Recvd command 2(OPRT_MODE_CHGD)     <-- the latch
```

**`uimsub_manager.c` is UZ801-*firmware*-only** (whole-image string census):

| string | HMU05 stock | **UZ801 B08** | **UZ801 B10** | UFI001B |
| :-- | --: | --: | --: | --: |
| `uimsub_manager.c` | **0** | **35** | **35** | **0** |
| `=CM= vs_id` | 0 | 1 | 1 | 0 |
| `cmsubs_evt_cb` | 0 | 2 | 2 | 0 |
| `lte_voice_system_id` | 0 | 1 | 1 | 0 |
| `cmss.c` (control) | 47 | 46 | 46 | 1 |
| `cmph.c` (control) | 71 | 88 | 88 | 11 |
| `cmdbg.c` (control) | 1 | 1 | 1 | 1 |

`cmss.c` is present on **both** sides (46/47) — so the negative is not a coverage gap: the UZ801 build carries a **UIM subscription manager** (`uimsub_manager.c`, 35 sites) that neither the HMU05 stock build nor the UFI001B build contains. `uimsub_manager.c:809` reads ICCID bytes (`iccid 0x3`, `0x5`, `0x7`) — a subscription-matching operation.

**Reading (bounded, and deliberately weak on causality).** The antecedent chain at the latch is `cmss_cmd_check` → `uimsub_manager` slot/ICCID query → `hybr_name_sel` → CM mode-pref update → `OPRT_MODE_CHGD`. `uimsub_manager` is a **UZ801-build-only subsystem** sitting on that path, which makes it the natural next target. But the F3 stream is shared and log proximity is not proof: **`uimsub_manager` is a *prime candidate*, not a proven cause** — exactly the caveat Doc 220 §4.8 attached to `vs_id`.

---

## 4. Two corrections to the campaign's recent record

### 4.1 Doc 220's `vs_id` fires ~1.4 s **after** the latch — it cannot be the latch's cause

| event | `boot_210_canon` ts |
| :-- | --: |
| `OPRT_MODE_CHGD` → `New transaction 3(OFFLINE)` | 519251824 |
| **`Prot_state [MAIN] 7(OFFLINE)` latched** | **519252872** |
| `cmph.c:15013 =CM= vs_id 0x10c01000` (Doc 220 §4.8) | **519541324** |

Δ = **288 452 ticks ≈ 1.41 s**. Doc 220 §4.8's `vs_id` is a real UZ801-build-only event and it really does complete the *later* `u=1 tsk=cm` command early — but that command is a **different, post-latch** command. **The `vs_id` event is not the cause of the `offline` latch**; it is downstream of it. Doc 220's own caveat ("prime causal candidate, not a proven cause") should now be read with this ordering.

### 4.2 The `cmph.c` mode-pref block is **not** UZ801-only — only its *timing* differs

Doc 221 §6.1 fact 3 said "UZ801 emits a `cmph.c` `rat_disabled_mask` re-evaluation … stock emits no `cmph.c` record at all there." **The "there" is doing the work, and it is easy to misread.** The statements exist in **both** builds at shifted line numbers:

| statement | stock line | UZ801 line |
| :-- | --: | --: |
| `=CM= mode_pref %d, pref_term %d` | 32055 | 32761 |
| `=CM= rat_disabled_mask as_id_0 …` | 32065 | 32771 |
| `=CM= After updating from rat_disabled_mask mode_pref %d` | 32104 | 32810 |
| `=CM= RAT_DISABLED_MASK: sys_mode …` | 17829 | 18094 |
| `=CM= rat_disabled_mask as_id_0 …` (2nd) | 17838 | 18103 |
| `=CM= vs_id %x` | **absent** | 15013 |

The offset is not constant (≈ +265 at 17829, ≈ +706 at 32055) — the UZ801 `cmph.c` is a **later source revision**, not a superset. Stock *does* run the block:

```
stock_boot_211:
  595  108928  =MMOC= Recvd report 2(PH_STAT_CHGD_CNF)
  602  108940  cmph.c:32055  =CM= mode_pref 38, pref_term 0
  603  108940  cmph.c:32065  =CM= rat_disabled_mask as_id_0 0x0, as_id_1 0x0
  604  108940  cmph.c:17829  =CM= RAT_DISABLED_MASK: sys_mode 9, is_rat_disabled=0, on as_id 0
  606  108944  cmph.c:17829  =CM= RAT_DISABLED_MASK: sys_mode 4, is_rat_disabled=0, on as_id 0
  608  108944  cmph.c:32104  =CM= After updating from rat_disabled_mask mode_pref 38
```

— it runs it **at its `PH_STAT_CHGD_CNF` report**, 7 700 ticks *before* the `SUBSC_CHGD` completion, and **emits no `OPRT_MODE_CHGD` afterwards**. UZ801 runs the identical block **at the `SUBSC_CHGD` completion** and *does* emit one.

⇒ The differential is **the trigger/placement of the mode-pref update**, not the existence of the statements. `mode_pref` is `38` in both and is unchanged by the update in both — so **the op-mode change is not a `mode_pref` change**.

---

## 5. Live probe on the deployed B08 build (2026-09-28)

Two live reads were taken while the device was up (AP uptime 7 784 s, boot_id `73a08a06-…`, modem in
`offline`). Neither required a reboot.

### 5.1 QMI op-mode state and the rejection codes

```
$ qmicli -d /dev/wwan0qmi0 --dms-get-operating-mode
        Mode: 'offline'          HW restricted: 'no'
$ qmicli -d /dev/wwan0qmi0 --dms-get-power-state
        Power state: 'external-source'      Battery level: '0 %'
$ qmicli -d /dev/wwan0qmi0 --dms-get-ids
        ESN 80EBE077   IMEI 864293052253917   MEID A27C0A80046ED6
$ qmicli -d /dev/wwan0qmi0 --dms-set-operating-mode=online
        error: QMI protocol error (52): 'DeviceNotReady'
$ qmicli -d /dev/wwan0qmi0 --dms-set-operating-mode=low-power
        error: QMI protocol error (60): 'InvalidTransition'
```

**Three facts worth recording.**

1. **`HW restricted: 'no'`** — the offline state is **not** a hardware lockout. (This is the live counterpart
   of Doc 221 R1: the RF layer is not refusing.)
2. **The IMEI is `864293052253917`, i.e. the HMU05's own IMEI** — the device is in the stock-NV state
   described in `project_uz801_full_device_swap_hy.md`, not the (unrecoverable) UZ801 NV state.
3. **`low-power` is rejected with `InvalidTransition` (QMI 60), not `DeviceNotReady`.** The DMS op-mode
   state machine is refusing the transition *from the current state*. This is a **new** live datapoint: it
   means the modem is not merely "not yet ready" (which `online`'s error alone would suggest) — the state
   machine considers the current state terminal for that edge. **Do not read `DeviceNotReady` as "still
   booting": the device had been up 7 784 s.**

### 5.2 The `online` command's F3 trace is silent

A 25 s F3 capture (`cntl-enable-range /dev/rpmsg0 512`, then two `--dms-set-operating-mode=online`
attempts) is **blind for MMOC** (`mmocdbg.c` = 0 of 699 records) and shows only:

```
131  1606150212  cmdbg.c:2186  =CM= CMD alloc u=8289, tsk=dcc
132  1606150284  cmdbg.c:2186  =CM= CMD free  u=8289, tsk=dcc, ftsk=cm     # 72 ticks = 0.352 ms
190  1606567596  cmdbg.c:2186  =CM= CMD alloc u=8291, tsk=dcc
191  1606567732  cmdbg.c:2186  =CM= CMD free  u=8291, tsk=dcc, ftsk=cm     # 136 ticks = 0.664 ms
```

**The alloc→free pair has nothing between it** — no `cmph.c`, no `mmocdbg.c`, no error. The CM receives the
`dcc` command and frees it silently in 0.352 ms. This reproduces Doc 214's `tsk=dcc` abort and Doc 221 §3's
values **on the deployed B08 build**, and confirms the rejection is **above the MMOC layer** — consistent
with §1's conclusion that the MMOC is only a mirror.

Capture artifact: `scratch/live_b08/online_try_222.bin`, md5 **`15a89f35960349838c46fdb1209a1196`**,
157 492 B, 699 records (device copy `/root/online_try.bin`).

### 5.3 The AT path is a second, independent rejection (and it is `tsk=ds`, not `tsk=dcc`)

The AT channel `/dev/wwan0at0` answers (`scratch/at_tool`, deployed to `/root/at_tool`), and the modem is
in **`+CFUN: 7`** — offline functionality — while the SIM is fine:

```
AT                -> OK
AT+CFUN=?         -> +CFUN: (0-1,4-7),(0-1)
AT+CFUN?          -> +CFUN: 7
AT+CPIN?          -> +CPIN: READY
AT+CGREG?         -> +CGREG: 0,6          AT+CREG? -> +CREG: 0,6
AT+CFUN=1         -> +CME ERROR: phone failure          # online  -- attempted, fails inside
AT+CFUN=0         -> +CME ERROR: operation not supported # offline -- rejected before attempt
AT+CFUN=4         -> +CME ERROR: operation not supported # offline -- rejected before attempt
AT+CFUN=5         -> +CME ERROR: operation not supported # offline -- rejected before attempt
AT+CFUN=6         -> OK                                  # NO state change
AT+CFUN=7         -> OK                                  # NO state change (already 7)
```

**The full sweep of the advertised range matters** — `+CFUN=?` advertises `(0-1,4-7)`, i.e. it *claims* to
accept both an online value (1) and the whole offline band (0, 4, 5, 6, 7). The modem does not honour that
claim symmetrically: **every value on the online side is refused and every value on the offline side is
either refused or a no-op.** `CFUN=6` / `=7` return `OK` but change nothing (the mode is already 7), so the
only observable *transition* the modem permits is **toward** offline — and even `0` / `4` / `5` are refused
outright. There is no accepted edge that moves the op-mode **up**.

**The error codes are not interchangeable and the distinction matters:**

| request | result | reading |
| :-- | :-- | :-- |
| `AT+CFUN=1` (online) | **`+CME ERROR: phone failure`** | the request is **attempted** and fails **inside** the modem |
| `AT+CFUN=0` / `=4` / `=5` | **`+CME ERROR: operation not supported`** | rejected **before** any attempt |
| `AT+CFUN=6` / `=7` | **`OK`** (no state change) | accepted but inert — already at 7 |
| QMI `online` | `QMI 52 DeviceNotReady` | a different layer's refusal |
| QMI `low-power` | `QMI 60 InvalidTransition` | state machine refuses the edge |

`phone failure` is a **generic CME 100-class** code, which is itself informative: the online path is not
being turned away by the *command parser* (that would be `operation not supported`, as the offline values
get) — it is admitted and then **fails in the CM's own op-mode handler**. That is the same site §5.2's
`tsk=dcc` alloc/free lands on.

**The AT path is a genuinely independent second route**, and it lets us separate the two AP-origin paths in
the CM by task:

| lever | CM task | alloc→free |
| :-- | :-- | :-- |
| QMI `--dms-set-operating-mode=online` (§5.2) | **`tsk=dcc`** | **72** ticks (0.352 ms) and **136** ticks (0.664 ms) |
| AT `AT+CFUN=1` (22 s capture `scratch/live_b08/at_cfun_try_222.bin`, md5 `7609de07ea24053528d3ec85cb2972e0`, 533 records) | **`tsk=ds`** | **84** ticks (0.410 ms), twice |

Both are allocated by the AP-side task and freed by **`cm`** in well under 1 ms, with **nothing between alloc
and free** in the F3 stream. ⇒ **two different AP command paths converge on the same CM rejection, which is
silent.** This is a stronger statement than §5.2 alone: the refusal is not a property of the QMI/DMS
service, it is in the **CM**.

### 5.4 What the live probe adds to §7's root question

The rejection is at the **CM** layer — not the MMOC, not the RF, not the hardware (`HW restricted: 'no'`),
and not the QMI service (the AT path fails the same way). Combined with §1, the root question is now stated
precisely: **at boot the CM computes an op-mode of OFFLINE and pushes it to MMOC, and it then refuses every
subsequent transition to online from that state, silently.** §7 item 1 is the search for what makes the
CM's boot op-mode OFFLINE.

---

## 6. What this does and does not establish

**Established**
* The MMOC's `OPRT_MODE_CHGD` handler mirrors the CM's op-mode (stock control: → `2(ONLINE)`); `3(OFFLINE)` is a propagation, not a decision.
* At the boot `SUBSC_CHGD` completion, **only UZ801** emits an `OPRT_MODE_CHGD` (stock: 0 across 3 captures; UZ801: 1 across 2).
* The UZ801 build carries `uimsub_manager.c` (**35** sites) which is **absent from both** the HMU05 stock and the UFI001B builds; it sits on the antecedent chain at the latch.
* The `cmph.c` mode-pref block exists in both builds; the difference is *when* it runs.
* Doc 220's `vs_id` is **downstream** of the latch by ~1.41 s.
* The device is **`+CFUN: 7`** (offline) with `+CPIN: READY`, `HW restricted: 'no'`, and the **HMU05's own IMEI**.
* **Two independent AP command paths** (QMI `online` → `tsk=dcc`; AT `CFUN=1` → `tsk=ds`) both reach the CM and are **both rejected by `cm` in under 1 ms, silently** ⇒ the refusal is in the **CM**, not in a service layer.
* The live refusal codes are distinct and **asymmetric**: `+CME ERROR: phone failure` (online, attempted, failed internally), `+CME ERROR: operation not supported` (`0`/`4`/`5`, not attempted), `OK` but inert (`6`/`7`), QMI 52 `DeviceNotReady`, QMI 60 `InvalidTransition`. **No accepted transition moves the op-mode up.**

**Not established**
* That `uimsub_manager` *causes* the `OPRT_MODE_CHGD`. Log proximity in a shared F3 stream is not causality.
* Why the CM's op-mode is OFFLINE on UZ801 and not on stock. This is now **the** open question (§7 item 1).
* Why UZ801's transaction skips `Recvd report 2(PH_STAT_CHGD_CNF)` / `Trans_state 6`.
* **Why the CM rejects `online`.** The rejection is silent in F3 on both paths, so it has not been localised to a code site yet.

---

## 7. Open items

1. **Why does UZ801's CM hold OFFLINE at boot?** With §1's control this is now the single root question, and
   §5.3 narrows it: the rejection is at the **CM/DMS** layer (not MMOC, not RF, not HW — `HW restricted:
   'no'`). Candidate instruments: the CM op-mode NV over DIAG EFS (`packages/diag-efs`, on the device as
   `/root/diag_efs`) compared against the HMU05-derived dump `scratch/efs_H/` — note `scratch/efs_H/` has
   **no op-mode preference file** under `modem/mmode/` (`device_mode` = `00`, `ue_usage_setting` = `00`,
   `sms_domain_pref` = `01`, `sw_version` = `msm8916_32_512-userdebug 4.4.4`), which is itself worth testing
   against the hypothesis "the UZ801's newer CM looks for an NV item the HMU05 EFS lacks and defaults to
   OFFLINE". **Not yet tested.**
2. **Find the `uimsub_manager.c:809` caller** and what ICCID bytes it matches. **Progress (§3):** the
   descriptor route **works for this file** — `logsite.py refs --file=uimsub_manager.c` returns **98
   log-site literals**; `:809` emits from **`FUN_c055bfd8`** (site VA `0xc161b960`), whose callers are
   **`FUN_c053c7bc`** (= **`uim.c:3805`**), `FUN_c055bed4`, `FUN_c055c1ec` (both `uimsub_manager.c`), and
   `FUN_c055c7d4` (`uimsub_manager.c:910–949`). **The live F3 trace shows no `uimsub_manager.c` record
   during the `online` attempt** (§5.2), so if it is on the rejection path it is silent there. Next: pick
   the caller active at boot.
3. **Boot-capture B08** (Doc 221 §9 item 4, still open). All of §1–§4 is B10; §5 is a live B08 capture but
   **blind for MMOC**. §3's static census shows B08 carries the same `uimsub_manager`/`vs_id` families, so
   the mechanism should transfer — but it is untested on the deployed build with a boot capture.
4. **`cmph.c`'s descriptors are not bare literals.** The descriptor VAs for the four mode-pref statements
   were recovered by scanning `modem.elf` for `(line << 16) | level` (`cmph.c:32761` → `0xc165d6a0`,
   `:32810` → `0xc165d6b0`, `:18094` → `0xc165d288`, `:18103` → `0xc165d290`) — but **all four have 0
   occurrences in the decompile**. `uimsub_manager.c` does **not** have this problem (98 literals). The two
   files therefore reach their descriptors by different mechanisms. This is a **method note**, not a
   result: do not conclude "the emitter is unlocatable" from a `cmph.c` zero.
5. **Do not** re-run Doc 220 §4.8's three negative emitter routes, and **do not** re-open the policyman
   RAT-mask axis (Doc 221 §5) or `cmcc.c`/GROUP 11 (Doc 207).

---

## 8. Achieved vs Expected

| expected of this work | achieved | note |
| :-- | :-- | :-- |
| explain the `7(OFFLINE)` latch | **partly** | the latch is shown to be a *propagation*; the CM's op-mode origin is not explained |
| a stock/online control for the `OPRT_MODE_CHGD` step | **yes, and it changed the reading** | `hmu05_stock_boot` → `2(ONLINE)` |
| a UZ801-only antecedent on the path | **yes** | `uimsub_manager.c` (35 / 0 / 0) |
| correct any stale campaign claim | **yes, two** | §4.1 (`vs_id` ordering), §4.2 (`cmph.c` block is not UZ801-only) |
| a live read of the rejection on the deployed build | **yes** | §5 — `online` → QMI 52, `low-power` → QMI 60, `HW restricted: no`, silent `dcc` abort |
| a proven cause | **no — deliberately** | stated as a prime candidate with the caveat |

---

## 9. Artifacts

| artifact | note |
| :-- | :-- |
| `scratch/doc222_latch_differential.py` | **new** — the single reproduction of §1–§4 (MMOC timelines, report census, string census, `cmph.c` line map) |
| `scratch/live_b08/online_try_222.bin` | **new (§5.2)** — md5 `15a89f35960349838c46fdb1209a1196`, 157 492 B, 699 records; the live 25 s F3 capture spanning two `--dms-set-operating-mode=online` attempts on the deployed B08 build (device copy `/root/online_try.bin`) |
| `scratch/live_b08/at_cfun_try_222.bin` | **new (§5.3)** — md5 `7609de07ea24053528d3ec85cb2972e0`, 90 949 B, 533 records; the live 22 s F3 capture spanning two `AT+CFUN=1` attempts (device copy `/root/at_cfun_try.bin`) |
| `scratch/at_tool` | the static aarch64 AT tool (opens `/dev/wwan0at0`), deployed to `/root/at_tool` for §5.3 |
| `scratch/doc210/boot_210_canon.bin` | UZ801 B10 boot (§1–§3) |
| `scratch/doc211/uz801_v19_boot_240.bin` | UZ801 B10 v19 boot (repeat) |
| `scratch/doc211/stock_boot_211.bin`, `stock_boot_240.bin` | stock HMU05 boot |
| `scratch/hmu05_stock_boot.bin` | stock HMU05, **33 743 records, ONLINE** — the control for §1 |
| `scratch/fwdl/uz801_v32_x/image/` | **the deployed B08 set** (Doc 221 §1) |
| `GitIgnore/compare/modem_hmu05_extracted/image/` | stock HMU05 ground truth |
| `GitIgnore/compare/modem_hmu05_vs_ufi001b/ufi001b_modem.bin` | UFI001B (third firmware control) |

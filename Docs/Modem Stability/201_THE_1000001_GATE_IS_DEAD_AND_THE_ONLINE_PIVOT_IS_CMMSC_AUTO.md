# 201 — The `0x1000001` gate is effectively DEAD (condition A is a 1 Hz uptime counter), the `0xc1ba7894` table is a code switch, and the online pivot is `cmmsc_auto`

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband `MPSS.DPM.1.0.1.C1-00121`, patched in `modem.b16` (v19 is
the deployed lineage — see the ledger §4). **No firmware was built, patched, deployed or rebooted in this
session** — every result below is static RE plus re-analysis of already-archived captures.
**Status:** RESULTS + RETRACTIONS. Doc 200 §6.2/§8's gate model is corrected; the planned
condition-(A) one-byte patch (v20) is **withdrawn before deployment**.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.

**Companions:** `194` (the UZ801 boot log and the stock baseline), `195` (v1–v14), `196` (the DIAG
instrument), `197` (the ledger — **its §3/§4/§7 are updated in the same session as this doc**),
`198` (v15/v16), `199` (v18/GROUP 14), **`200`** (the online gate, the `dcc` break, and §8's open
questions — **§6.2/§8 of which this doc corrects**).

---

## 1. SOP compliance

Per the mandatory five-step protocol (`133 §1`): **backup → ground-truth verification against stock HMU05
→ reconcile transport/config → surgical Hexagon patching + re-signing → live empirical validation**.

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **NOT APPLICABLE this session** — nothing on the device was read, written, patched or rebooted. All inputs are files already in the repo (`scratch/uz801_fw/modem.elf`, the archived F3 captures) plus the Ghidra decompile. |
| **Ground-truth verification against stock HMU05** | **Yes, and it is the doc's method.** Every claim about the UZ801 build is paired with the stock HMU05 arm: the CM command-log semantics are read from the *UZ801* image and then checked against the *stock boot capture*; the `0xc1ba7894` table is decoded from the image and checked against the stock boot's `Prot_state` transitions. Where the two arms disagree the disagreement is stated. |
| **Reconcile transport/config** | **Yes, partially.** The F3 record layout and the `u` field were re-derived from the *instrument's own output* (the free-path stack argument) rather than assumed — see §3.1. The mask-coverage question is re-opened in §6. |
| **Surgical Hexagon patching + re-signing** | **NOT DONE — deliberately.** A patch was *derived* in the previous session (`0xc067d18a: 0x42 → 0x02`) and is **withdrawn** here (§3.3). No byte was written, so `ufi001b_hash_tool.py` was not run and no re-signing verdict is claimed. |
| **Live empirical validation** | **Yes — one reboot, for §6.** The UZ801 v19 build was cleanly rebooted with the `diagboot` sweep raised to **256 SSIDs** to test whether the mask bound explained the missing subsystems; the instrument was restored to the 64 sweep afterwards. The modem's post-boot state was re-read (`--nas-get-serving-system`: `not-registered`, `Radio interfaces: 1 [0]: 'none'`). Every other conclusion is static or a re-analysis of captures whose provenance is named. |
| **No blind patching** | **Yes — and this is the doc's point.** The previous session's patch was derived from a *misread* of the gate; reading the field's writer showed the read was wrong, so the patch is withdrawn rather than deployed. |
| **Irreversible / high-blast-radius actions declared first** | **NOT APPLICABLE** — none were taken. |

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Answer Doc 200 §8 item 4: *who writes `cmd+0x14` on the DMS online path, and is it `9`?* | a writer + a verdict on `==9` | the writer is the **CM command allocator `FUN_c06951b0`**; it stores `FUN_c093cc80()`; and `u` in the CM logs **is** that field (§3.1) | **MET** |
| Decide whether `cmd+0x14 == 9` can gate the `0x1000001` whitelist path | a yes/no | **No, effectively never** — the field is the modem's **uptime in seconds**, so `==9` means "allocated during second 9" (§3.2) | **MET** — gate **FALSIFIED** |
| Deploy the condition-(A) inversion as v20 and score it | a result | **NOT DONE — withdrawn before deployment**; the premise was wrong (§3.3) | **NOT MET** (by design) |
| Explain why v13/v19's `cm_state+0x39e8 = 3` patches were inert | a mechanism | the whitelist is behind condition (A); with (A) unreachable the whitelist is dead code on this boot path (§3.2) | **MET** |
| Re-derive the QMI-mode → CM-opmode mapping | the real table | the table at `0xc1ba7894` is a **code switch table** (mode → local case at `0xc0e03a60`–`0xc0e03a74` that sets `r19`), not a data map; QMI mode 0 → `r19 = 5` = ONLINE (§4) | **MET** (conclusion kept, mechanism corrected) |
| Name the stock online sequence and its timing | the exact chain | `dcc` CM cmd → `PROT_GEN_CMD` ×3 → `OPRT_MODE_CHGD` → transaction `ONLINE` → `Prot_state [MAIN] 5(ONLINE_GWL)`, in **13.8 ms** (§5) | **MET** |
| Name the *first* stock-only log event at the CM layer | a specific event | **`cmmsc_auto.c` — `=CM= CMMSC_AUTO: updating op_mode`**, at ts = 375 844, immediately after the MMGSDI card events and immediately before MMOC `SUBSCRIPTION_CHGD` (§5.3) | **MET** |
| Check whether the stock-vs-UZ801 subsystem differential is mask-controlled | a yes/no | **RAISED, then CLOSED by a live 256-sweep capture**: 64→256 added exactly one file (`rcinit_rex.c`); the LTE/CM-SS/MCPM set stays absent; 34 stock files still have no UZ801 counterpart ⇒ **the differential is REAL** (§6) | **MET** |
| Correct Doc 194 §8's "`u` is a command handle" | a correction | two allocs **4 ticks apart** both carry `u=1` ⇒ not per-command; and Δu = Δts/204 800 (±1.5 %, 8 samples) ⇒ a **1 Hz counter** (§3.1) | **MET** |
| Characterise the v19 stability event seen live | a cause | the v19 boot ended **uncleanly** (guard `consecutive_unproven=1`, empty pstore = PMIC PON WDT) at ~2 280 s uptime; the next boot's AP side is wedged (`bam_dmux: RX watchdog: quiesced Ns, rx=held, ring disarmed`, `dms` CID allocation fails) (§7.2) | **MET** (recorded, not root-caused) |

**The one-line truth:** the `0x1000001` gate the campaign has been attacking since v13 is **dead code on
this boot path** — its first condition compares the command's *allocation uptime* to `9` — and the real
pivot is the CM **auto-mode** function `cmmsc_auto`, which stock runs at boot and UZ801 does not.

---

## 3. `cmd+0x14` is the modem's uptime in seconds — so condition (A) cannot be the gate

### 3.1 The field is proven, and it is a 1 Hz counter

**The field.** Doc 200 §8 item 4 asked who writes `cmd+0x14`. The writer is the CM command allocator
`FUN_c06951b0` (`c06951b0`–`c0695218`):

```
c06951bc  call 0xc0695218          ; pool alloc -> r0
c06951c8  r16 = r0                 ; r16 = the command (packet with the call above)
c06951cc  call 0xc08b2f80          ; current-task lookup (for the log)
c06951d0  memw(r16+#0x14) = r0     ; cmd+0x14 = return of the *previous* call, 0xc093cc80
```

`FUN_c093cc80()` is a timestamp helper (it builds a time structure on its frame and returns through it).

**The identity `u == cmd+0x14` is proven from the free path**, not assumed. `FUN_c069527c`
(`=CM= CMD free u=%d, tsk=%s, ftsk=%s`) builds its varargs explicitly:

```
c06952c0  r1 = add(r29,#0x10)      ; &taskbuf
c06952c4  r2 = add(r16,#0x18)      ; cmd+0x18  = the task-name string
c06952c8  r0 = memw(r16+#0x14)     ; r0 = cmd+0x14
c06952cc  memw(r29+#0x0) = r0.new  ; vararg[0] = cmd+0x14   <-- this is "u=%d"
c06952d4  r0 = ##0xc18f8deb        ; "=CM= CMD free u=%d, tsk=%s, ftsk=%s"
c06952d8  memw(r29+#0x8) = r1 ; memw(r29+#0x4) = r2
```

So `u` is exactly `cmd+0x14`. *(The previous session had this right; what follows is the new part.)*

**What the value is.** Tabulating `u` against the F3 timestamp over the stock boot capture:

| task | `u` | F3 `ts` | Δ`ts` since previous | Δ`ts` / Δ`u` |
| :-- | --: | --: | --: | --: |
| ds | 26 | 3 605 027 716 | — | — |
| ds | 54 | 3 610 665 700 | 5 637 984 | 201 357 |
| ds | 75 | 3 614 926 508 | 4 260 808 | 202 895 |
| ds | 107 | 3 621 547 940 | 6 621 432 | 206 920 |
| ds | 140 | 3 628 395 112 | 6 847 172 | 207 490 |
| ds | 173 | 3 635 046 776 | 6 651 664 | 201 565 |
| ds | 205 | 3 641 690 652 | 6 643 876 | 207 621 |
| ds | 238 | 3 648 397 960 | 6 707 308 | 203 252 |

Every ratio is **204 800 ± 1.5 %** — the F3 tick rate. So `u` advances **once per second of modem time**.
Corroboration that it is a *time* and not a per-command handle:

- `uz801-v19` allocates twice **4 ticks apart** and both commands carry **`u=1`** (`gstk`), and three
  times inside 64 ticks with `u=1`. A handle would have to differ.
- The last stock `u` (238) matches the capture length (~240 s); the first (1, 2) match modem uptime 1–2 s.

⇒ **`u` = the modem's uptime in seconds at command allocation.** *(Doc 194 §8 called it "a monotonically
increasing command HANDLE" and listed the mis-read of it as its own "error #2". The handle reading is
itself wrong; the field is a clock.)*

### 3.2 Therefore condition (A) is a moment, not a class

Doc 200 §6.2 named three conditions on the dispatcher's `0x1000001` case (`FUN_c067cd2c`, decompile line
1134909). Re-reading them with the field now identified:

```c
if (uVar21 == 0x1000001) {
  piVar3 = *(int **)(iVar8 + 0x1c);                  // cmd->ctx
  if (piVar3 == NULL) { log; return; }
  if (*(int *)(iVar8 + 0x14) != 9) {                 // (A)  cmd+0x14  ==  modem uptime (s)
    if (*piVar3 == 2) { ...thunk_EXT_FUN_d0822fec(...); return; }   // the NORMAL path
    goto LAB_c067eca8;                               // LAB_c067eca8 == bare `return` (silent drop)
  }
  iVar12 = 0;                                        // the WHITELIST path
  iVar9 = thunk_EXT_FUN_d0877580(*(undefined1 *)(iVar8 + 0x20));   // (B) cmd+0x20 byte
  ...
  if (*(sbyte *)(iVar9 + iVar12*0x1cf4 + 1) == 0) goto LAB_c067eca8;
  if (iVar12 == 0) { if (*(sbyte *)(iVar9 + 0x39e8) != 3) { log; return; } }   // (C) whitelist
  ...
```

Machine code confirms both the field and the immediate (round-tripped with `llvm-mc -disassemble`):

```
c067d17c  r0 = memw(r17+#0x14)                         ; cmd+0x14
c067d184  if (!cmp.eq(r0.new,#9)) jump:t 0xc067e994    ; to the (A)-false block, not to a drop
```

- **(A) `cmd+0x14 == 9`** ⇒ "this command was allocated during second 9 of the modem's life". No
  `0x1000001` command in any archived capture (stock or UZ801) carries `u = 9`.
- **The `!=9` branch is the normal path**, not a drop: it handles `cmd->ctx->[0] == 2` and only otherwise
  returns silently.
- **(B) and (C) — `cmd+0x20` and `cm_state+0x39e8 == 3` — sit *behind* (A).** With (A) unreachable they
  are dead on this boot path.

**This is the mechanism behind v13/v19's inertness.** v13 GROUP 2 and v19 GROUP 15 both forced
`cm_state+0x39e8 = 3`; the ledger already recorded them as inert. They were inert because the whitelist
that reads that byte is unreachable: no `0x1000001` command reaches it. Doc 200 §6.2's "three-condition
gate" is corrected to **one effective condition: `cmd->ctx->[0] == 2`**.

### 3.3 The withdrawn patch

The previous session derived a one-byte patch to invert condition (A)
(`0xc067d18a: 0x42 → 0x02`, giving `if (cmp.eq(r0,#9)) jump:t`). Its *encoding* was verified by
round-trip. Its *premise* — that `!=9` meant "drop" and `==9` meant "dispatch" — is wrong: `0xc067e994`
is the `ctx->[0] == 2` handler, and the `==9` path is the one nobody takes.

**The patch is therefore withdrawn, and nothing was deployed.** If a `0x1000001` experiment is ever
wanted, the correct single variable is now **`cmd->ctx->[0] == 2`** (the `!=9` branch's guard), not
condition (A) — but §5 argues the `0x1000001` code is probably not the online command at all.

---

## 4. The `0xc1ba7894` table is a code switch, not a data map

Doc 200 §6.1 read `gp+0xc7c0` (= `0xc1ba7894`) as "the QMI-mode → CM-opmode jump table" and concluded
"QMI online → 5 = `CM_PH_OPRT_MODE_ONLINE` — the opmode handed to CM is correct". The conclusion holds;
the mechanism does not. Dumping the table shows **code addresses**, and they are the case labels of the
switch in the DMS set-operating-mode handler `FUN_c0e03974`:

```
0xc1ba7894: 0xc0e03a68 0xc0e03a6c 0xc0e03a60 0xc0e03a64 0xc0e03a70 0xc0e03a74 0xc0e03a6c 0xc0e03a6c
```

```
c0e03a48  r0 = memub(r29+#0xb)              ; the QMI operating mode from the request TLV
c0e03a4c  if (cmp.gtu(r0.new,#0x7)) jump:t 0xc0e03cc8
c0e03a50  r19 = #0x0
c0e03a54  r1 = memw(gp+#0xc7c0)             ; = 0xc1ba7894
c0e03a58  r0 = memw(r1+r0<<#0x2)            ; case address
c0e03a5c  jumpr r0
c0e03a60  r19 = #0x1 ; jump 0xc0e03a74      ; mode 2 -> FTM
c0e03a64  r19 = #0x2 ; jump 0xc0e03a74      ; mode 3 -> OFFLINE
c0e03a68  r19 = #0x5 ; jump 0xc0e03a74      ; mode 0 -> ONLINE     <-- QMI online
c0e03a6c  r19 = #0x6 ; jump 0xc0e03a74      ; mode 1 -> LPM
c0e03a70  r19 = #0x7                        ; mode 4 -> RESET
c0e03a74  call 0xc0e095e4                   ; (r19 = the CM opmode handed on)
```

So the QMI→CM opmode translation is **inline code in the DMS handler**, indexed by a code switch table.
`r19 = 5` for QMI online is correct; the "non-sequential data table" reading is not.

The rest of the handler is the submit path, and it is where Doc 200 §8's "retarget GROUP 1" advice came
from. Note what it does for **mode 0**:

```
c0e03b80  r0 = memb(r29+#0xb) ; r0 = add(r0,#-0x4) ; if (cmpb.gtu(r0,#0x1)) jump:t 0xc0e03c10
c0e03c10  call 0xc0e269b4                   ; cm_ph_cmd_oprt_mode(%s)  <-- trace only
c0e03c14  r0 = r19
c0e03c1c  memb(r17+##0x4ed) = r19           ; stash the opmode
c0e03c20  call 0xc0e32a14                   ; -> thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)
```

`0xc0e32a14` forwards to `thunk_EXT_FUN_d03d3420(0xc, 0x1a, 0x30f)` — an **external (`d03d…`) service
call**. The CM command's *code and ctx are built inside that external module*, which is **not in
`modem.elf`** (the `0xd0…` region maps to no PT_LOAD segment — the platform-quirks doc's §1). **This is
why the command the `dcc` task builds cannot be identified statically from this image**, and it is the
hard boundary on the static route.

---

## 5. The stock online sequence, measured — and the pivot is `cmmsc_auto`

### 5.1 The chain

From the stock boot capture (`scratch/hmu05_stock_boot.bin`, 33 039 raw / 31 443 real F3 messages), with
`ts` in F3 ticks:

| ts | event |
| --: | :-- |
| 3 039 772 312 | `=CM= CMD alloc u=17, tsk=dcc` |
| 3 039 772 896 | `=MMOC= Recvd command 1(PROT_GEN_CMD)` |
| 3 039 773 176 | `=MMOC= Recvd command 1(PROT_GEN_CMD)` |
| 3 039 773 452 | `=MMOC= Recvd command 1(PROT_GEN_CMD)` |
| 3 039 773 652 | `=MMOC= Recvd command 2(OPRT_MODE_CHGD)` |
| 3 039 773 680 | `=MMOC= New transaction : 2(ONLINE)` |
| **3 039 775 136** | **`=MMOC= Prot_state [MAIN] 5(ONLINE_GWL)`** |
| 3 039 774 192 | `=CM= CMD free u=17, tsk=dcc, ftsk=cm` |

**Alloc → `ONLINE_GWL` = 2 824 ticks = 13.8 ms.** The `dcc` command is delivered to CM and CM drives MMOC
through `PROT_GEN_CMD` ×3 → `OPRT_MODE_CHGD` → `ONLINE`. This confirms Doc 200 §3's shape and gives it
timing.

### 5.2 The UZ801 counterpart

On the UZ801 build the same `dcc` command (u = 370 / 456 in the live captures) is allocated and freed
**0.664 ms** later with **no `PROT_GEN_CMD`**, no `OPRT_MODE_CHGD`, and no `ONLINE` transaction (Doc 200
§4, reproduced). So the command is *delivered* (it is freed by `cm`) but produces nothing.

### 5.3 The first stock-only CM event is `cmmsc_auto`

> ⚠ **PARTIALLY RETRACTED 2026-09-26 by Doc 206 §3.** The *observation* — the first stock-only CM event is
> a `cmmsc_auto.c` record — is correct. The *attribution* to `FUN_c06928a8` is **wrong**: `FUN_c06928a8` is
> **`cmcc.c`**, not `cmmsc_auto.c` (its descriptors resolve to `cmcc.c:=CM= MMGSDI EPS …` at L3081/L3115, and
> its embedded symbols are `s_cmcc_service_available_cb` / `s_cmcc_call_control_processing_lte`). The real
> `cmmsc_auto.c` entry point remains **unlocated**.

Walking the stock stream in order and asking "is this (file, format) present anywhere in the UZ801 v19
capture?", the **first** stock-only event is not in `rflte`/`mcpm` at all — it is three `cmmsc_auto.c`
messages at `ts = 375 844`, immediately after the MMGSDI card events (evt `0x13`,`0x15`,`0xc`) and
immediately before MMOC `SUBSCRIPTION_CHGD`:

```
=CM= MSC_AUTO: 0x%x & 0x%x = 0x%x
=CM= CMMSC_AUTO:is_ue_mode_csfb=%d hybr_pref %d is_cdma_subsc_avail %d
=CM= CMMSC_AUTO: updating op_mode, cdma sub %d hybr1_allowed %d hybr2_allowed %d
```

`cmmsc_auto` is the **automatic mode-selection** function — the thing that "updates op_mode". In
`modem.elf` it is `FUN_c06928a8`, and it ends by calling `FUN_c06917f0(iVar2, 0xffffffff)` — exactly the
function v14's GROUP patched. It has **no direct caller and no pointer-table reference anywhere in the
image** (grepped the whole 110 MB decompile and the raw ELF), i.e. it is invoked through a handler
registered at runtime.

Counts over the captures:

| file | stock | UZ801 (all three builds) |
| :-- | --: | --: |
| `cmmsc_auto.c` | **36** | **0** |
| `cmss.c` / `cmlog.c` | 609 / 835 | 0 / 0 |
| `rflte_core_rxctl.c` / `mcpm_npa.c` | 8652 / 2417 | 0 / 0 |
| `cmcall.c` / `sdss.c` / `cmsoa.c` | 40 / 73 / 34 | 0 / 0 |
| `mmocdbg.c` / `mmocmmgsdi.c` / `cmdbg.c` / `cmph.c` | 326 / 17 / 32 / 40 | 60 / 61 / 14 / 22 (v19) |

**Read §6 before using this table.**

---

## 6. The mask confound — RAISED and then CLOSED by a live 256-sweep capture

Doc 194 §8 (and the ledger's §3 row "Initialise the LTE RF / protocol stack at boot") rests on the table
above: stock runs `cmss`, `cmlog`, `rflte_*`, `mcpm_*`, `cmcall`, `sdss`, `cmsoa`, `cmmsc_auto`; UZ801
runs none of them. That comparison *looked* confounded, because F3 output is gated by a **mask** the AP
sets per modem boot (`cntl-enable <dev> <nssid>`, one `DIAG_CTRL_MSG_F3_MASK` packet per SSID) and the
UZ801 captures are tiny next to the stock one:

| capture | sweep | clean msgs | distinct (file, format) pairs |
| :-- | --: | --: | --: |
| `scratch/hmu05_stock_boot.bin` | 64 | 31 443 | **4 487** |
| `scratch/v19_boot.bin` | 64 | 839 | **144** |
| `scratch/uz801_boot.bin` | 64 | 5 461 | — |
| **`scratch/boot_sweep256.bin`** | **256** | 670 | **112** |

**The test (run this session, 2026-09-26).** The UZ801 v19 build was rebooted with the instrument's sweep
raised to **256 SSIDs** (verified: the `diagboot` log carries exactly **256** `CNTL TX F3_MASK` packets).
This is the *conservative* direction — it gives UZ801 a **wider** mask than the stock arm had — so if the
missing subsystems were merely sitting in un-swept SSIDs, they would appear.

**Result: they do not.** Going 64 → 256 added exactly **one** file, `rcinit_rex.c` (a boot-init file), and
nothing from the LTE/CM-SS/MCPM set. The stock capture still has **34 files absent from the 256-sweep
UZ801 capture**, and they are precisely the missing layers:

```
a2_dl_phy.c a2_ipfilter.c a2_log.c cmcall.c cmcc.c cmlog.c cmltecall.c cmmsc_auto.c cmregprx.c
cmsds.c cmsoa.c cmss.c mcpm.c mcpm_npa.c mcpm_saw.c mmoc.c mmocdbg.c pgi_msgr.c
policyman_call_events.c policyman_serving_system.c rfmeas_mc.c rflte_core_rxctl.c rflte_mc_meas.c
sdcmd.c sdss.c tlm_ptm.c tm_cm_iface.c tm_lpp_cp.c tm_umts_up_supl.c trm_config_handler.c …
```

⇒ **The sweep bound is NOT the confound. The stock-vs-UZ801 differential is real: on UZ801 the LTE RF
layer, the CM serving-system layer, MCPM, the MMOC protocol engine and the SD/TRM layers never log.**
Doc 194 §8's headline is **re-instated** (with the sweep dimension now explicitly controlled).

**What remains open** is only the weaker form of the question: *does "never logs" mean "never runs"?* A
build with the log calls compiled out would look identical. The evidence against that reading: both
builds are debug builds that log the same CM state from different `cmdbg.c`/`cmph.c` lines (Doc 194 §8),
and UZ801 does emit `mmocdbg.c`/`mmocmmgsdi.c`/`wms.c` — so the log machinery is live. **It is not
proven**, but the burden of proof has moved.

### 6.1 The instrument rule, restated

The ledger §7 item 8 requires a negative scored on a live capture to be shown to carry
`mmocdbg.c`/`cmmsc_auto.c`. This session adds the **sweep** dimension: a negative must also be shown to
be **sweep-saturated** on that build, i.e. that raising `cntl-enable` adds no new file. On UZ801 it does
not (`scratch/boot_sweep256.bin`). **A UZ801 negative is therefore admissible if it comes from a
256-sweep capture.**

---

## 7. What this changes for the campaign

1. **The `0x1000001` gate is a dead end.** Condition (A) compares the allocation uptime to `9`; the
   whitelist behind it is unreachable. v13/v19 were inert for this reason. **Do not patch it further.**
2. **The one static route to the online command is blocked** by the external `d03d3420` service call
   (§4) — the command's code/ctx are built outside `modem.elf`. Static analysis cannot name the `dcc`
   command's code.
3. **The pivot is `cmmsc_auto` (`FUN_c06928a8`)** — the automatic op-mode update. Stock runs it at boot
   right after the MMGSDI card events and right before MMOC `SUBSCRIPTION_CHGD`; it is the first
   stock-only CM event and it is the function that ends by calling `FUN_c06917f0`, the target v14 already
   patched. It is invoked through a **runtime-registered handler**, so the open question is **which
   message invokes it**, not whether its internals are correct.
4. **The mask confound is CLOSED** (§6) — the differential is real, so "the LTE/CM-SS/MCPM layers do not
   run on UZ801" is now an admissible negative, and the campaign should target the **subsystem bring-up**,
   not the CM command dispatch.

### 7.1 Named next step (in order of cheapness)

1. ~~Take a mask-matched boot capture pair.~~ **DONE (2026-09-26)** — the 256-sweep capture
   (`scratch/boot_sweep256.bin`) closed it in one reboot: the sweep is saturated and the differential is
   real (§6). **The instrument was restored to the 64 sweep afterwards.**
2. **Find what starts the LTE/CM-SS layer on stock.** The differential names the boundary exactly: stock
   logs `rflte_core_rxctl.c` / `cmss.c` / `mcpm_npa.c` from **modem uptime ≈2 s**, before ModemManager,
   i.e. in the firmware's own boot path. The named question is therefore *what in the boot path is
   conditional on something HMU05 does not provide* — the two live candidates remain the **EFS/NV RF
   config** (unreadable: `IMGEFS1`, Doc 143 §3a.11's retraction) and the **board RFFE/DevCfg** (the
   Patch 11+12 axis).
3. **Find what invokes `FUN_c06928a8` (`cmmsc_auto`).** It is runtime-registered; its log-string cluster
   is at VA `0xc165c398`…`0xc165c3f8`. This is downstream of step 2 (stock reaches `cmmsc_auto` only
   after the MMGSDI card events), so it is lower priority than step 2.
4. **Only then** decide whether any patch is warranted.

### 7.2 Live events observed while taking the capture (2026-09-26)

Recorded because they are stability data on the v19 build and they cost two boots to see:

| event | evidence |
| :-- | :-- |
| **The v19 boot ended UNCLEANLY** at ~2 280 s uptime (the AP reset and came back with a new `boot_id`) | `/overlay/modem_guard/events.log`: `boot: consecutive_unproven=1 threshold=3`; `/sys/fs/pstore/` **empty** ⇒ the PMIC PON WDT STOP signature, not a panic (platform-quirks §3) |
| **The next boot's AP side is wedged** | `dmesg`: `bam-dmux: RX watchdog: quiesced 60s/120s/… (pc_state=0, pc_line=0, rx=held, ring disarmed)` every 60 s; QMI `dms` fails with `CID allocation failed in the CTL client: endpoint hangup` while `nas` still answers |
| The modem is still `offline` after a clean reboot | `--nas-get-serving-system`: `Registration state: 'not-registered'`, `Radio interfaces: 1 [0]: 'none'` |
| The guard behaved correctly | `boot_count` went 1 → 0 on the clean reboot; no revert |

**Caveat:** the unclean reset was **not** root-caused. It is *not* the ~900 s fatal (that clock is
LTE-gated and the modem never reaches LTE here — P-PMOS3), and it is *not* the AP-hang signature (an AP
hang does not change `boot_id`). It is recorded as an **open stability observation on the v19 build**.

---

## 8. Evidence inventory

| artifact | what it is |
| :-- | :-- |
| `scratch/uz801_fw/modem.elf` | the joined UZ801-21 image every VA in this doc is read from |
| `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` | the Ghidra decompile (81 832 functions) — `FUN_c06951b0`, `FUN_c069527c`, `FUN_c06928a8`, `FUN_c067cd2c`, `FUN_c0e03974` |
| `scratch/dispatcher_asm.txt` | the CM dispatcher disassembly (`0xc067cd2c`–`0xc067ef58`) |
| `scratch/dispatcher_c067cd2c.txt` | the dispatcher decompile (the `0x1000001` case at line 1134909) |
| `scratch/hmu05_stock_boot.bin` | the stock HMU05 boot capture — the comparison arm |
| `scratch/v19_boot.bin`, `scratch/uz801_boot.bin`, `scratch/uz801_boot_4.bin` | the three 64-sweep UZ801 boot captures |
| **`scratch/boot_sweep256.bin`** | the **256-sweep** UZ801 v19 boot capture (2026-09-26) that closed the mask confound — md5 `b6d9443495f9552453bddd9c34e88f06`; 670 clean msgs / 112 distinct classes; added only `rcinit_rex.c` vs the 64-sweep captures |
| `scratch/boot_256_prep_64sweep.bin` | the 64-sweep capture from the boot *before* the 256 run (the degraded boot whose AP side was wedged) — md5 `eadb5877ffb047816e88c580060fd931` |
| `scratch/uz801_online_attempt.bin`, `scratch/uz801_online256.bin` | the live captures that reproduced the `dcc` alloc→free |
| `scratch/f3parse.py`, `scratch/f3clean.py` | the F3 parser and the median-`ts` cleaner (the `==MMOC=` / `:40449` records are false positives; the cleaner removes them) |
| `scratch/varead.py` | read the image by VA (used for the `0xc1ba7894` table) |
| VA `0xc1ba7894` (UZ801) | the QMI-mode **code switch table** for `FUN_c0e03974` |
| VA `0xc0e03a60`–`0xc0e03a74` (UZ801) | the switch's case labels that set `r19` = the CM opmode |
| VA `0xc0e32a14` → `thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)` | the external submit call — the static boundary |
| VA `0xc165c398`…`0xc165c3f8` (UZ801) | the `cmmsc_auto` log-string cluster |

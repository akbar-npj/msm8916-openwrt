# 200 — The online gate, corrected: stock does not auto-online either, the CM code is identical, and the break is in the `dcc`-task command

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick, OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, patched `modem.b16` — **v18** (`a90524d72577d5604ff5ee15014e01a6`).
**Status:** RESULTS — one corpus premise corrected (Doc 199 §4), the online gate re-derived from ground truth, and the break localised to a single runtime step. **AMENDED 2026-09-26 (same session): §6 was WRONG and is retracted below; §4's `mmocdbg.c` evidence is confounded; §6.1–§6.3 are new and name the exact CM gate plus a concrete defect in the v13 patch.**
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.

**Companions:** `194` (the boot-log differential), `195` (the `offline → online` campaign, v1–v14), `198` (v15/v16 recovered), `199` (v18/GROUP 14 negative + the "named divergence" — **§4 of which this doc corrects**), `196` (DIAG tooling).

---

## 1. SOP compliance

Per the five-step protocol in `133 §1` (model: `194 §1`):

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **Yes.** The live v18 set was pulled to `scratch/ref_v18/` (md5 `a90524d7…`/`6360a026…`) **before** anything changed; the bootloop guard was armed throughout. **v19 was then deployed** (§6.4) as a verified 1-byte delta, `modem.mdt` re-hashed with `ufi001b_hash_tool.py patch`, deploy helper's 22-file md5 + segment-hash checks PASS. |
| **Ground-truth verification against stock HMU05** | **Yes — this doc's core.** §3 re-derives the online gate from the archived **stock** boot capture (`scratch/hmu05_stock_boot.bin`) and re-checks the Doc 199 §4 premise against it; §4 diffs the *code* against the stock HMU05 decompile; §6.1 reads the mode jump table and the opmode enum **directly out of the image**. |
| **Reconcile transport/config** | **Yes.** The live captures are parsed with the Doc-194 F3 parser; the median-`ts` guard is applied. **§4 amendment: the live capture's F3 mask was found to be narrower than the boot capture's and is declared.** |
| **Surgical Hexagon patching + re-signing** | **Yes (amended).** v19 is a **one-byte** Hexagon edit at `0xc067f4c0` (`22`→`62`), encoding verified with `llvm-mc -triple=hexagon -show-encoding`; the segment hash was re-signed into `modem.b01`/`modem.mdt` with the project's hash tool (§6.4). |
| **Live empirical validation** | **Yes.** One live DIAG capture during the AP's `--dms-set-operating-mode=online` (§4, mask-confounded), plus a **post-deploy boot capture** scoring v19 (§6.4). |
| **No blind patching** | **Yes.** The one-byte edit was derived from a decompiled caller/callee relationship and verified against the deployed image before it was built (§6.3). |
| **Irreversible actions declared first** | **Yes.** The v18 backup was taken first, the change is one byte, the guard is armed, and a clean `reboot` clears the boot counter. §6.3's hypothesis was **falsified** by the test rather than assumed (§6.4). |

**Errors caught and corrected in this session — recorded rather than hidden:**

1. **Doc 199 §4 is wrong on two counts** (§2). `mmocmmgsdi.c` is **not** a UZ801-only module — the
   stock HMU05 image contains the identical strings *and* the stock boot capture contains **17**
   `mmocmmgsdi.c` messages. The stock-vs-UZ801 "0 vs 59" table in Doc 199 §4 was produced from a
   filtered subset, not the whole capture.
2. **Doc 195 §12.1.1's causal arrow is wrong** (§3). The stock `SUBSCRIPTION_CHGD → … → Prot_state
   5(ONLINE_GWL)` arrow in that section is a hand-drawn leap: the stock **boot** log ends at
   `Prot_state 0(NULL)` and does not reach `ONLINE_GWL`. `ONLINE_GWL` first appears ~4.1 h into the
   reference capture, and only after a `dcc`-task CM command.
3. **A first-pass conclusion that "the producer is in `mmocmmgsdi.c`" is wrong** (§5). Every writer of
   MMOC command `0x1000007` in the image is a **CM** function in the decompiled range; `b17`–`b25` are
   *data* segments (`p_flags` R|W), so no code lives there.

---

## 2. Correction: `mmocmmgsdi.c` is not UZ801-only

Doc 199 §4 asserts stock emits **0** `mmocmmgsdi.c` messages. Parsing the whole stock capture
(`scratch/hmu05_stock_boot.bin`, 33 039 messages) gives **17**. The same strings are present in the
stock image at `0xc44a5110…` (`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`), e.g.
`mmocmmgsdi_card_status_cb` at `0xc44a519c` and `Invalid session, sess_type` at `0xc44a524c`.

| | STOCK HMU05 | UZ801 v18 |
| :-- | --: | --: |
| `mmocmmgsdi.c` messages in the boot capture | **17** | 61 |
| first event seen | `evt=0x13, sess_id=0x3ddbfa4f` → `Sess type=0, ss=0` | `evt=0, sess_id=0` → `Sess type=0x7fffffff, ss=4` |
| `mmocmmgsdi.c` strings in the image | **present** | present |

**⇒ the divergence is not the module's existence but the *content of the first MMGSDI event it is
handed*.** Stock's first event carries a real `sess_id`; UZ801's first event is `evt=0, sess_id=0` and
resolves to an uninitialised session (`0x7fffffff`).

The UZ801-only F3 modules are a different set: `nvruim.c`, `mcfg_uim.c`, `policyman_uim.c`,
`estk_bip.c`, `gstk*`, `mmgsdi_session.c`, `rcinit_rex.c` — i.e. **UIM/NV + SIM-Toolkit/BIP support
that the stock build does not run at all.** UZ801's first event is produced by exactly that path:

```
UZ801 boot, ts order (ts is the 204800 Hz F3 clock)
 2004420480 cmdbg.c      CMD alloc u=1, tsk=mmgsdi_1
 2004420480 nvruim.c     nvruim_mmgsdi_evt_cb 0x0            <-- UZ801-only
 2004420516 mcfg_uim.c   Got card event 0x0 on slot index 0x0; sending cmd   <-- UZ801-only
 2004420672 cmdbg.c      CMD free u=1, tsk=mmgsdi_1, ftsk=cm
 2004420708 mmocmmgsdi.c card_status_cb: evt=0, sess_id=0
 2004420708 mmocmmgsdi.c Found session, sess_type=0x7fffffff, ss=4
 2004420728 mmocdbg.c    Recvd command 7(DUAL_STANDBY_CHGD)
 2004420740 mmocdbg.c    Prot_state [MAIN] 7(OFFLINE)
```

---

## 3. ★ The online gate, re-derived: `ONLINE_GWL` is what starts LTE — and nothing starts it automatically

Two facts, both read off the stock capture:

**(a) `rflte`/`pgi_msgr` begin only after `ONLINE_GWL`.**

| source | first ts | 1st msg is … |
| :-- | --: | :-- |
| `mmocdbg.c` `Prot_state [MAIN] 5(ONLINE_GWL)` | 3 040 013 116 | — |
| `rflte_core_rxctl.c` (8652 msgs) | **3 040 020 868** | +7.75 ms after `ONLINE_GWL` |
| `pgi_msgr.c` (6006 msgs) | 3 040 019 368 | +6.25 ms after `ONLINE_GWL` |

⇒ the LTE RF / protocol stack is **gated by MMOC `5(ONLINE_GWL)`**. Doc 194's conclusion was right in
substance; only its timing claim ("within 0.78 s of capture start") was wrong.

**(b) The stock boot does *not* reach `ONLINE_GWL` on its own.** The stock capture's boot chunk
(ts 237 980 → ~576 000, i.e. the first ~2.8 s) walks MMOC
`0(NULL)` → `1(SUBSC_CHGD)` → `25(WAIT_DEACTD_CNF_GWL)` → `6(WAIT_PH_STAT_CNF)` →
`5(PROT_PH_STAT_ENTER)` → `0(NULL)` and **stops there**. `ONLINE_GWL` appears once, at ts 3 040 013 116,
and it is preceded by exactly this:

```
3040010292 cmdbg.c      =CM= CMD alloc u=17, tsk=dcc
3040010796 cmmsc_auto.c =CM= CMMSC_AUTO: updating op_mode, cdma sub %d hybr1_allowed %d hybr2_allowed %d
3040010876 mmocdbg.c    =MMOC= Recvd command 1(PROT_GEN_CMD)
3040011632 mmocdbg.c    =MMOC= Recvd command 2(OPRT_MODE_CHGD)
3040011660 mmocdbg.c    =MMOC= New transaction           : 2(ONLINE)
3040013116 mmocdbg.c    =MMOC= Prot_state [MAIN] 5(ONLINE_GWL)
```

**⇒ the online transition is driven by a CM command that arrives on the `dcc` task, and `dcc` is the
task that carries the AP's QMI request.** Neither firmware auto-onlines; the AP has to ask. Doc 195
§12.1.1's arrow from `SUBSCRIPTION_CHGD` straight to `ONLINE_GWL` conflates the two independent MMOC
transactions.

---

## 4. ★ The live A/B on the same command

The AP sent the identical request on both firmwares. One live capture on the running UZ801 v18 device
(40 s, `scratch/diag_online_test.bin`, md5 `746cd4c38fd81420ee05d74a4d65b562`), one archived stock
capture:

| | STOCK HMU05 | UZ801 v18 (live) |
| :-- | :-- | :-- |
| AP action | `--dms-set-operating-mode=online` | `--dms-set-operating-mode=online` |
| QMI result | (already online) | `Operating mode set successfully` (v13 patch) |
| mode after | `online` | **`offline`** |
| `cmdbg.c` | `CMD alloc u=17, tsk=dcc` | `CMD alloc u=1897, tsk=dcc` |
| … then | `cmmsc_auto.c` ×3 (`MSC_AUTO`, `is_ue_mode_csfb`, **`updating op_mode`**) | **nothing** |
| … then | `MMOC Recvd command 1(PROT_GEN_CMD)`, `2(OPRT_MODE_CHGD)`, `New transaction 2(ONLINE)`, `5(ONLINE_GWL)` | **nothing** |
| `cmdbg.c` | `CMD free u=17, tsk=dcc, ftsk=cm` at +11.5 ms | `CMD free u=1897, tsk=dcc, ftsk=cm` at **+0.66 ms** |
| `mmocdbg.c` in the window | 40+ messages | **0** |
| `cmmsc_auto.c` in the window | 3 | **0** |

**⇒ The break is a single step: on UZ801 the `dcc`-task CM command is allocated and freed by CM
without ever invoking `cmmsc_auto`, and no MMOC command is posted.** Everything downstream of
`cmmsc_auto` (MMOC `PROT_GEN_CMD`/`OPRT_MODE_CHGD` → `ONLINE` → `rflte`) therefore never happens, and
the LTE stack never starts. This also explains why every one of v1–v18 failed: they patched the DMS
*reply* and the CM *consumers*, but the command that arrives is dropped before any of that matters.

> **⚠ AMENDMENT (2026-09-26) — the `mmocdbg.c` column above is CONFOUNDED, do not rely on it.**
> The live capture's F3 mask is **narrower than the boot capture's**. `diag_online_test.bin` contains
> **0** `mmocdbg.c` messages in its *entire* 40 s (and a re-run with `cntl-enable /dev/rpmsg2 64`
> first — `scratch/diag_online_full.bin`, 1090 msgs — *still* gives 0), whereas the UZ801 **boot**
> capture `scratch/g14_test/post_v18_group14_boot.bin` has **60**. So "0 `mmocdbg.c` in the window" is
> an artefact of what was enabled, **not** evidence that MMOC did not run.
>
> **What survives:** (a) the **timing** — `CMD alloc`→`CMD free` is **0.664 ms** on UZ801 vs **11.5 ms**
> on stock, both from `cmdbg.c`, which *is* enabled in both captures; (b) **`cmmsc_auto.c` = 0 in
> *both* UZ801 captures** (boot 809 msgs *and* live) vs **36** in the stock capture, while those same
> UZ801 captures do carry `mmocdbg.c` (60), `cmph.c` (22) and `mmocmmgsdi.c` (61) — so the CM
> subsystem is logging on UZ801 and `cmmsc_auto` still never appears. The conclusion stands on (a)+(b);
> only the `mmocdbg.c` sub-evidence is withdrawn.

---

## 5. The static result: the CM code is identical, so the divergence is runtime state

Both builds were decompiled in full (`uz801` 81 800 functions, `hmu05` 77 067). `scratch/fdiff.py`
normalises VAs, global symbol names, label names, Ghidra's temporary variable numbering and
`prolog_/epilog_` helpers, then diffs.

| function | UZ801 | HMU05 | lines | similarity |
| :-- | :-- | :-- | :-- | --: |
| CM command **dispatcher** (cases `0x1000001`/`0x100001c`/`0x1000021`/`0x1000026`/`0x100002e`/`0x1000038`/`0x100006a`/`0x100006c`) | `FUN_c067cd2c` | `FUN_c0659ef0` | **1097 / 1097** | **identical** (only GP offsets + label names differ) |
| CM **MMOC-command handler** (`0x1000007` / `0x1000002` / `0x1000026`) | `FUN_c067ef58` | `FUN_c065c11c` | 64 / 64 | 93.75 % — the 2 differing blocks are `gp+0x4a18`↔`gp+0x4968` and `gp+0x32fc`↔`gp+0x3278` |

**⇒ the UZ801 build's CM logic is the same code as stock's.** The port's blocker is therefore **runtime
data/state**, not different CM logic — which is why "patch CM" could never work, and it moves the
search to *what command arrives* and *what state CM is in when it arrives*.

**Corollary — where MMOC command 7 comes from.** A raw scan of every PT_LOAD segment for the
`##0x1000007` immediate finds writers **only** in the decompiled range, and all six are CM functions
(`FUN_c067cd2c`, `FUN_c067ef58`, `FUN_c0b54c40`, `FUN_c0b55260`, `FUN_c0b57c2c`, `FUN_c0b59de8`).
`b17`/`b18`/`b19`/`b22`/`b23`/`b24`/`b25` carry `p_flags` R|W — they are **data**, so no code lives
there and `mmocmmgsdi.c` cannot be the writer. The dispatcher's `0x1000038` case is the one that
allocates and posts `0x1000007`, and it is **present and identical in stock HMU05** (HMU05 writes
`0x1000007` at its own dispatcher, line 1116931).

---

## 6. ~~The DMS gate, decoded~~ — ⚠ RETRACTED AND REPLACED (2026-09-26, same session)

**The original §6 was WRONG on its central fact.** It read the mode jump table as *sequential* (case 0
at `0xc0e03a60`, hence `r19 = 1` for QMI mode 0) and concluded that the v13 GROUP 1 patch hands CM a
*wrong* opmode. The table is **not** sequential. It has been read directly out of the image, and the
mapping is exactly right — QMI mode 0 yields `r19 = 5 = CM_PH_OPRT_MODE_ONLINE`.

### 6.1 The real jump table, read from the image

`FUN_c0e03974` (`ds_qmi_dms.c`, set-operating-mode) dispatches with
`r1 = memw(gp+#0xc7c0); r0 = memw(r1+r0<<#0x2); jumpr r0`. The table lives at **VA `0xc1ba7894`**
(found by scanning the whole image for the 4-byte case-handler address `0xc0e03a60`; the single hit
sits immediately below the `"ds_qmi_dms.c"` literal at `0xc1ba78b4`). It implies `gp = 0xc1b9b0d4`.

| QMI DMS operating mode | handler | `r19` | CM opmode (`cm_ph_oprt_mode_e_type`) |
| :-- | :-- | --: | :-- |
| 0 ONLINE | `0xc0e03a68` | **5** | **ONLINE** |
| 1 LOW_POWER | `0xc0e03a6c` | 6 | LPM |
| 2 FACTORY_TEST | `0xc0e03a60` | 1 | FTM |
| 3 OFFLINE | `0xc0e03a64` | 2 | OFFLINE |
| 4 RESET | `0xc0e03a70` | 7 | RESET |
| 5 SHUTDOWN | `0xc0e03a74` | 0 | (unchanged from the `r19 = #0x0` seed) |
| 6 PERSISTENT_LPM | `0xc0e03a6c` | 6 | LPM |
| 7 (reserved) | `0xc0e03a6c` | 6 | LPM |

The CM opmode name table is at **VA `0xc1f5a820`** (the `%s` consumed by the `cm_ph_cmd_oprt_mode(%s)`
trace): `0 PWROFF, 1 FTM, 2 OFFLINE, 3 OFFLINE_AMPS, 4 OFFLINE_CDMA, 5 ONLINE, 6 LPM, 7 RESET,
8 NET_TEST_GW, 9 OFFLINE_IF_NOT_FTM, 10 PSEUDO_ONLINE, 11 RESET_MODEM`. Every QMI mode maps to the
semantically correct CM opmode.

**⇒ the opmode handed to CM is CORRECT. "CM rejects it because `r19` is wrong" is dead.** The dispatch
sequence itself (the `r20+0x5a` ready byte, the `cmp.gtu(r1,#0x5)` filter, the `0xc0e03b80` →
`0xc0e03c10` split) is unchanged from the original §6 and is not in dispute.

### 6.2 The CM dispatcher's `0x1000001` gate — three conditions, three drop paths

`FUN_c067cd2c` @ `0xc067cd2c` (the CM command dispatcher; decompile line 1134909) handles
`0x1000001` (OPRT_MODE) like this:

```c
if (uVar21 == 0x1000001) {
  piVar3 = *(int **)(iVar8 + 0x1c);
  if (piVar3 == NULL) { <log>; return; }                 // drop 1
  if (*(int *)(iVar8 + 0x14) != 9) {                     // (A) command field +0x14 must be 9
    if (*piVar3 == 2) { ... }
    goto LAB_c067eca8;                                   // drop 2
  }
  iVar12 = 0;
  iVar9 = thunk_EXT_FUN_d0877580(*(undefined1 *)(iVar8 + 0x20));   // stack id 0x28/0x29/0x2a
  if (iVar9 != 0x28) { iVar12 = 1; ... if (iVar9 != 0x29) { <log>; return; } }
  iVar9 = *(int *)(unaff_GP + 0x32fc);                   // cm_state
  if (*(sbyte *)(iVar9 + iVar12 * 0x1cf4 + 1) == 0) goto LAB_c067eca8;   // (B) drop
  if (iVar12 == 0) {
    if (*(sbyte *)(iVar9 + 0x39e8) != 3) { <log>; return; }   // (C)  sub 0
  } else if (*(sbyte *)(iVar9 + 0x39f0) != 3) { <log>; return; } // (C') sub 1
  ...
}
```

So the online command is dropped unless **all three** hold: `cmd+0x14 == 9`, the per-subscription
record byte `cm_state[iVar12*0x1cf4 + 1] != 0`, and the **whitelist byte `cm_state+0x39e8 == 3`**
(sub 0) or `cm_state+0x39f0 == 3` (sub 1). A 0.66 ms alloc→free is exactly what one of these early-outs
looks like.

### 6.3 ★ The v13 GROUP 2 patch is SELF-DEFEATING — the caller overwrites the byte

`cm_state+0x39e8` is a **0 → 1 → 3** state machine:

1. `FUN_c067f0bc` (the subscription/stack setup, decompile 1135999–1136184) calls
   `FUN_c0683c88()`, then at **decompile line 1136152-1136155**:
   ```c
   iVar3 = FUN_c0683c88(iVar3);
   *(undefined1 *)(*(int *)(unaff_GP + 0x32fc) + 1) = 1;
   if (iVar3 == 2) {
     *(undefined1 *)(*(int *)(unaff_GP + 0x32fc) + 0x39e8) = 1;   // <-- writes 1
   }
   ```
2. The dispatcher's `0x1000021` case promotes it: `if (cm_state[0x39e8] == 1) cm_state[0x39e8] = 3;`
   (decompile line 1134777-1134778).
3. Only then does `0x1000001` pass condition (C).

**v13 GROUP 2 patches `FUN_c0683c88` to set `cm_state+0x39e8 = 3` and `return 2`.** But its only
relevant caller is step 1 above — and **`return 2` is precisely the value that makes the caller
overwrite the byte with 1.** Net effect: `0x39e8 = 1`, the `0x1000021` promotion is still required, and
the online command still fails condition (C). This is verifiable in the deployed image:

| site | pristine | deployed v18 |
| :-- | :-- | :-- |
| `0xc0683c88` (callee, patched) | `c461355b02c09da0…` | `61400078e2d78649…` (**patch applied**) |
| `0xc067f4c0` (caller's store) | `22400078e0d78649…` | `22400078e0d78649…` (**unchanged — still writes 1**) |

**The one-byte fix.** The store is
`{ r2 = #0x1 ; r0 = memw(gp+#0x32fc) } { immext(#0x39c0) ; memb(r0+##0x39e8) = r2 }` at
`0xc067f4c0`/`0xc067f4cc`. `r2 = #0x3` encodes `62 40 00 78` against `r2 = #0x1`'s `22 40 00 78`
(verified with `llvm-mc -triple=hexagon -show-encoding`). **Change byte `0xc067f4c0` from `0x22` to
`0x62`** and the caller writes 3 instead of 1 — which is what GROUP 2 was trying to achieve.

**The sub-1 twin has the same class of defect, one layer worse.** The sub-1 caller
`FUN_c0b51b10` writes `*(cm_state2 + 0x3a00) = 1` where `cm_state2 = gp+0x3d44` (decompile
2080926-2080930), but v13 GROUP 3 patches `FUN_c0b57c2c` to write `*(gp+0x32fc + 0x39f0) = 3` —
**the other state object *and* a different offset**. So GROUP 3 writes a byte nothing on this path
reads. The two CM stacks are distinct: `gp+0x32fc` carries `+0x39e8`/`+0x39f0`, `gp+0x3d44` carries
`+0x3a00`.

### 6.4 ★ The §6.3 fix was DEPLOYED and tested — **NEGATIVE**

**v19 = v18 + the one byte at `0xc067f4c0`** (`0x22` → `0x62`). Built, verified as a **1-byte delta**
against the live v18 (`a90524d7…` → `c71d5346…`; `modem.mdt` re-hashed to `9f39ce57…`), deployed with
the bootloop guard armed, and rebooted. Result:

| | v18 | v19 |
| :-- | :-- | :-- |
| `qmicli --dms-get-operating-mode` | `offline` | **`offline`** |
| boot-capture msgs | 809 | 841 |
| `cmmsc_auto.c` | 0 | **0** |
| `mmocdbg.c` / `cmph.c` / `mmocmmgsdi.c` | 60 / 22 / 61 | 60 / 22 / 61 |
| `rflte_core_rxctl.c` | 0 | 0 |

**⇒ forcing `cm_state+0x39e8 = 3` at the caller does NOT unblock the online command.** So either that
byte is not the operative condition, or `FUN_c067f0bc` never runs on this boot (in which case the byte
was never written by that path at all), or one of the *other* two conditions (`cmd+0x14 == 9`, the
per-subscription `+1` byte) is the binding one. **The hypothesis is falsified as stated; the byte is
retained in v19 because it is provably inert, and v19 is now the deployed lineage.**

**What this narrows:** the drop is *not* a stale whitelist byte written by the subscription-setup path.
The remaining candidates inside the dispatcher's `0x1000001` case are conditions (A) `cmd+0x14 == 9`
and (B) `cm_state[iVar12*0x1cf4 + 1] != 0` — or the command is dropped **before** the dispatcher
reaches that case at all. Note also that `cmmsc_auto` (`FUN_c06928a8`) has **no direct caller** in the
decompiled image — it is reached through a registered handler, so its absence from the log is a
*dispatch* question, not a "function not called" question.

---

## 7. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Re-check Doc 199 §4's "mmocmmgsdi is UZ801-only" claim | a confirmation | **wrong**: stock has the strings *and* 17 boot messages (§2) | **NOT MET** (claim retracted) |
| Re-derive what gates the LTE stack | a specific state | `rflte`/`pgi_msgr` start 6–8 ms **after** `Prot_state 5(ONLINE_GWL)` (§3a) | **MET** |
| Determine whether stock auto-onlines at boot | a yes/no | **no** — its boot stops at `0(NULL)`; `ONLINE_GWL` needs a `dcc`-task CM command (§3b) | **MET** |
| Localise the UZ801 break with a live A/B | a named step | the `dcc`-task CM command is freed in 0.66 ms with **no** `cmmsc_auto` and **no** MMOC command; stock drives the full chain in 11.5 ms (§4) | **MET** |
| Decide whether the CM *code* differs | identical or not | **identical** (1097/1097 dispatcher lines; 93.75 % handler with only GP offsets differing) (§5) | **MET** |
| Identify the true writer of MMOC command 7 | a function | all writers are CM functions in the decompiled range; `b17`–`b25` are data (§5) | **MET** |
| Explain why v1–v18 failed | a reason | they patched the DMS reply and CM's *consumers*; the arriving command is dropped before either matters (§4) | **MET** |
| Re-derive the QMI→CM opmode mapping | the real table | the table at `0xc1ba7894` is **non-sequential**; QMI online → `r19 = 5 = CM_PH_OPRT_MODE_ONLINE`, i.e. **correct** (§6.1) | **MET** — and it **retracts the original §6** |
| Name the exact CM gate that drops `0x1000001` | a condition | three: `cmd+0x14 == 9`, `cm_state[iVar12*0x1cf4+1] != 0`, and `cm_state+0x39e8 == 3` (§6.2) | **MET** |
| Test whether v13's whitelist patch actually sets the byte | a yes/no | **no** — it sets 3, then its caller overwrites 1; verified in the deployed image (§6.3) | **MET** (defect found) |
| Establish the live capture's F3 mask as adequate | a check | **FAILED** — the live capture has 0 `mmocdbg.c` in its whole 40 s vs 60 in the boot capture; the `mmocdbg` sub-evidence is withdrawn (§4 amendment) | **NOT MET** (confound declared) |
| Test whether forcing `cm_state+0x39e8 = 3` unblocks online | online, or a ruled-out cause | **v19 deployed and scored: still `offline`; boot log unchanged, `cmmsc_auto.c` still 0 (§6.4)** | **NOT MET** — hypothesis **FALSIFIED** |
| Keep the device safe | no unrecoverable state | v18 backed up first; v19 is a 1-byte, guard-armed, hash-verified deploy; no bootloop, device reachable | **MET** |

**The one-line truth:** the port's blocker is **not** different CM/MMOC code and **not** the
`DUAL_STANDBY_CHGD` "producer" Doc 199 §4 named — it is that on UZ801 the AP's online request arrives
on the `dcc` task as a command CM does not recognise, so `cmmsc_auto` never runs and MMOC never enters
`ONLINE`.

---

## 8. What this changes / next steps

1. **Doc 199 §4 must be corrected** (done in the ledger §7). The divergence is the *first MMGSDI event
   content*, not the module's presence.
2. ~~**Retarget GROUP 1 from `0xc0e03b80` to `0xc0e03ab4`.**~~ **WITHDRAWN — it was based on the
   retracted §6.** `0xc0e03ab4` is the **low-power** success path (`call 0xc0e000bc`, reached for QMI
   modes 1 and 6 only); routing QMI *online* there would set LPM, not ONLINE. With the corrected
   mapping (§6.1) GROUP 1 already sends mode 0 to `0xc0e03c10` with the **correct** opmode 5.
3. ~~**The next patch is the one-byte fix in §6.3.**~~ **DONE AND FALSIFIED — see §6.4.** v19 (the
   `0xc067f4c0` byte) was deployed and scored: still `offline`, boot log unchanged. Forcing the
   whitelist byte at its caller is **not** sufficient. The byte is retained (provably inert) and v19 is
   the current lineage.
4. **★ The next candidate, in order of cheapness.** The dispatcher's `0x1000001` case has two remaining
   conditions — (A) `cmd+0x14 == 9` and (B) `cm_state[iVar12*0x1cf4 + 1] != 0` — and there is the
   possibility that the command is dropped **before** that case. Decide between them by finding what
   *builds* the `dcc` command for a QMI online request: the DMS handler's `0xc0e03c10` path calls
   `0xc0e269b4` (the `cm_ph_cmd_oprt_mode(%s)` trace, §6.1) and `0xc0e32a14`, so the `cmd+0x14` field is
   set somewhere on that path — **read the field's writer and check it against 9**. This is the single
   highest-value static question left.
5. **Instrument first, then score.** The live-capture F3 mask is inadequate (§4 amendment). Any
   negative scored on a live capture must be shown to carry `mmocdbg.c`/`cmmsc_auto.c`; the v19 test in
   §6.4 sidesteps this by scoring on a **boot** capture, which demonstrably carries both.
6. **Still open, and possibly the real question:** *what makes `cm_state+0x39e8` reach 3 on stock?*
   The promotion is the dispatcher's `0x1000021` case (decompile 1134777), gated on a per-subscription
   record (`cm_state[idx*0x1cf4+1] == 1` and a matching id). Whether UZ801 ever receives `0x1000021`,
   and whether those per-subscription conditions hold, is not yet determined. Note also that
   `cmmsc_auto` (`FUN_c06928a8`) has **no direct caller** in the decompiled image — it is reached via a
   registered handler, so the question is a *dispatch* question, not a "function never called" one.
7. **Alternative, independent route (kept open):** the stock boot shows `cmmsc_auto` running during
   *boot* too (ts 375 844, from the `WMS_CMD_MMGSDI_EVENT_CB` path). UZ801 never runs it at all. A
   differential of the `wms.c` MMGSDI-event → CM command path between the two builds is the other
   half of the picture and is not yet done.
8. **Scope guard unchanged.** Reaching `online` *arms* the LTE-gated ~900 s fatal clock (Doc 194 §11).
   This is a porting milestone, not a stability fix.

---

## 9. New tooling (all under `scratch/`, gitignored)

| tool | what it does |
| :-- | :-- |
| `desc.py` | decode an F3 descriptor at a VA — resolves `{ptr,file,line}` entries to `"<file>.c:<format>"` |
| `fdiff.py` | normalised diff between a UZ801 function and its HMU05 counterpart (`--full` for the blocks) |
| `bootreport.py` | one-shot F3 boot summary (from Doc 199) |

`fdiff.py` is the useful one: it answers "did this build's *logic* change, or only its data?" — which is
the question that decides whether a port needs a code patch or a data/EFS fix.

---

## 10. Evidence inventory

| artifact | md5 / value | what it is |
| :-- | :-- | :-- |
| `scratch/diag_online_test.bin` | `746cd4c38fd81420ee05d74a4d65b562` | live 40 s F3 capture spanning the AP's `set-operating-mode=online` (973 msgs, 0 `mmocdbg.c` — **narrow mask, see §4 amendment**) |
| `scratch/diag_online_full.bin` | (2026-09-26 re-run) | the same test with `cntl-enable /dev/rpmsg2 64` run first — 1090 msgs, **still 0 `mmocdbg.c`**, 0 `cmmsc_auto.c`, 2 `cmdbg.c`; proves `cntl-enable` in a live session does **not** restore the boot capture's mask |
| `/lib/firmware/modem.b16` (live, 2026-09-26) | `a90524d72577d5604ff5ee15014e01a6` | v18; the §6.3 verification read the deployed bytes from `scratch/uz801_patched/modem.b16` (same lineage) |
| `scratch/ref_v18/modem.b16` + `modem.mdt` | `a90524d7…` / `6360a026…` | the **v18 backup pulled off the device before the v19 deploy** (§6.4) |
| `scratch/apply_v19_patches.py` | — | v19 patcher = v17/v18 groups + GROUP 15 (the `0xc067f4c0` byte) |
| `scratch/uz801_patched/modem.b16` (v19) | `c71d53464a1e88f2072cbbb6f7f87475` | v19 image — 291 diff bytes vs pristine, **1 byte vs v18** |
| `scratch/uz801_patched/modem.mdt` (v19) | `9f39ce579114bcf1383bbdce62a8d284` | re-signed by `ufi001b_hash_tool.py patch … 16 …` (§6.4) |
| `scratch/v19_boot.bin` | (956 379 B, 841 msgs) | the post-deploy **boot capture scoring v19** — `cmmsc_auto.c` 0, `mmocdbg.c` 60, `cmph.c` 22, `mmocmmgsdi.c` 61, `rflte_*` 0 (§6.4) |
| `boot_id` (v19 boot) | `cb740804-2aef-4f8a-88e9-584106721d93` | the v19 deploy boot |
| `scratch/hmu05_stock_boot.bin` | (Doc 194) | stock HMU05 capture — the ground truth for §2/§3 |
| `scratch/g14_test/post_v18_group14_boot.bin` | `73843bfcd0975f8c9c171e6436df1475` | UZ801 v18 boot capture |
| `/lib/firmware/modem.b16` (live) | `a90524d72577d5604ff5ee15014e01a6` | v18, unchanged this session |
| `/lib/firmware/modem.mdt` (live) | `6360a026aaa58f7ee9b2d0a34180786d` | v18, unchanged |
| `boot_id` | `6b5b7fb2-acbd-4703-9290-504688b3ece1` | unchanged since the v18 deploy |

`scratch/` is gitignored — these captures live on the build host only. **Do not delete them.**

# 199 — v18 (GROUP 14) tested: negative. The deployed "v17" was really v16. And the stock-vs-UZ801 MMOC divergence is now named.

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick, OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, patched `modem.b16` — v18 = v17 + GROUP 14.
**Status:** RESULTS — one patch hypothesis tested and falsified; one long-standing bookkeeping error corrected; one new, named divergence.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.

**Companions:** `194` (the boot-log differential), `195` (the patching campaign v1–v14), `198` (the v15/v16 recovery + the invariant MMOC boot), `143`/`145` (the transplant verdict).

---

## 1. SOP compliance

Per the five-step protocol in `133 §1` (model: `194 §1`):

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **Yes.** The previously-deployed `modem.b16` was copied aside before any rebuild (`/tmp/modem.b16.deployed_v17.md5_982a824e`). The device retains `/overlay/fwbackup/hmu05_stock/` and the bootloop guard was armed for the whole trial. |
| **Ground-truth verification against stock HMU05** | **Yes — this doc's §4 is a fresh differential against the archived stock boot capture** (`scratch/hmu05_stock_boot.bin`, same AP/NV/hardware, only the firmware differs). |
| **Reconcile transport/config** | **Yes.** The F3 parser is the Doc-194 one; the median-`ts` false-positive guard is applied to every capture quoted (§3), and the stock and UZ801 spans are both consistent with the capture window. |
| **Surgical Hexagon patching + re-signing** | **Yes.** One byte patched at `0xc0fa09f8`; the encoding was verified by disassembly *before* deployment (§2.2) and the segment hash table (`modem.b01` / `modem.mdt`) was regenerated and re-verified (`19 MATCH / 0 MISMATCH`). |
| **Live empirical validation** | **Yes.** Deployed to the device, one cold reboot, the boot log captured with the `diagboot` instrument, and the live operating mode read back over QMI. |
| **No blind patching** | **Yes.** The patch was derived from the decompiled control flow, its reachability was checked against the call graph, and it was tested as a single variable. |
| **Irreversible actions declared first** | **Yes.** The reboot and the firmware swap were announced; the guard reverted automatically if the patch had bootlooped (it did not). |

**Errors caught and corrected in this session — recorded rather than hidden:**

1. **The deployed "v17" was not v17.** The image on the device and in `scratch/uz801_patched/`
   (`modem.b16` md5 `982a824e…`, 289 diff bytes) was built by an *earlier* revision of
   `apply_v17_patches.py`; the script was edited at 15:58 to add **GROUP 14** but was never re-run and never
   re-deployed. **Doc 197 §4's "v17" result is therefore the v16 result**, and GROUP 14 had never been
   tested. (§2.1)
2. **A real deploy-helper bug.** `deploy_uz801_patched.sh` embedded `$FILES` (which contains newlines) in a
   double-quoted remote command, so the remote shell treated every line after the first as a *separate
   command* — only `modem.b00` was copied and `sync` never ran. The live-image check caught it (good), but
   the install had already run, leaving a partially-updated set. Fixed: the list is collapsed to one line
   and the install's exit status is now checked. (§2.4)
3. **A wrong conclusion, caught by cross-checking.** "`FUN_c0f980fc` has no caller, therefore the boot
   bring-up chain is dead" was **wrong**: the identical function in stock HMU05 (`FUN_c0eb5148`) also has no
   direct caller. These are service-registered handlers, so "no direct caller" is normal. (§2.3)

---

## 2. What was done

### 2.1 The v17 mis-build

A byte-level diff of `scratch/uz801_fw/image/modem.b16` (pristine) against the deployed
`scratch/uz801_patched/modem.b16` shows **289 differing bytes in 45 runs** — groups 1–13 only.
GROUP 14's one byte at `0xc0fa09f8` is **absent**:

| | bytes at `0xc0fa09f8` | decodes to |
| :-- | :-- | :-- |
| pristine | `18 62 ff 5b 00 c0 9d a0` | `{ call 0xc0f9ce28; allocframe(#0x0) }` |
| deployed "v17" | `18 62 ff 5b 00 c0 9d a0` | **unchanged** |
| re-run of the script | `1c 62 ff 5b 00 c0 9d a0` | `{ call 0xc0f9ce30; allocframe(#0x0) }` |

Re-running `apply_v17_patches.py` produces `modem.b16` md5 `a90524d7…` with **290** diff bytes and the
patch present. **GROUP 14 had never been exercised.**

### 2.2 v18 = v17 + GROUP 14 — what it is supposed to do

`FUN_c0fa09f8` is a gate that reads `gp+0x4244` (a platform struct) and returns 0 if it is NULL:

```c
iVar1 = FUN_c0f9ce28();                 /* returns memw(gp+#0x4244) */
if (iVar1 != 0) return FUN_c0f9cf08(iVar1);   /* returns memb(ptr+0x5b6c) */
log(...); return 0;
```

GROUP 14 retargets the call to `FUN_c0f9ce30`, the **lazy allocator**:

```
c0f9ce30: { memd(r29+#-0x10) = r17:16; allocframe(#0x8) }
c0f9ce34: { r16 = memw(gp+#0x4244)
c0f9ce38:   if (!cmp.eq(r16.new,#0x0)) jump:t 0xc0f9ce50 }
c0f9ce3c: { call 0xc0d67370;  r0 = #0x62c0 }   # allocate 0x62c0
c0f9ce44: { call 0xc0f9cf14;  r16 = r0 }       # init: memb(ptr+0x5b6c) = 1
c0f9ce4c: { memw(gp+#0x4244) = r16 }
c0f9ce50: { ... r0 = r16 ; jump 0xc0030098 }   # return the pointer
```

The encoding was verified by disassembling the rebuilt image (§2.1) and the segment hashes were
regenerated (`ufi001b_hash_tool.py patch … 16 …` → `19 MATCH / 0 MISMATCH`).

### 2.3 Reachability — checked, and the first answer was wrong

`FUN_c0fa09f8` is referenced **only** from `FUN_c0f980fc` (line 2882825). `FUN_c0f980fc` is the boot
bring-up root: it loops subscriptions 0..6 (`FUN_c0f9be68` / `FUN_c0f92024` / `FUN_c0f928dc` /
`FUN_c0f92578`) and then registers the bring-up handler table via
`FUN_c0f981e8` → `FUN_c0d6eb10(2, &PTR_FUN_c1f64dd8)`.

`FUN_c0f980fc` has **no direct caller**. That looks fatal — but the identical function in stock HMU05,
`FUN_c0eb5148`, also has no direct caller, and stock HMU05 *does* reach `online`. The two are a
byte-for-byte structural match, confirmed by their F3 descriptor (both log the descriptor with
format-string hash `0x218565d2`):

```
UZ801 FUN_c0f980fc   HMU05 FUN_c0eb5148
  log(&DAT_c17c5284)   log(&DAT_c16c30bc)     # hash 0x218565d2, file_id 0x15, msg_id 0x5e
  FUN_c0fa09f8()       FUN_c0ebd96c()         # the gate
  FUN_c0f9cc10()       FUN_c0ebca34()
  FUN_c0f9be68()       FUN_c0ebbc8c()
  FUN_c0f92024()       FUN_c0eb0af8()
  FUN_c0f928dc()       FUN_c0eb13b0()
  FUN_c0f92578()       FUN_c0eb104c()
  FUN_c0f981e8()       FUN_c0eb5234()
```

**⇒ these are service-registered handlers, and "no direct caller" is the normal shape for both builds.**
The reachability argument is void; only the experiment can decide.

### 2.4 Deployment

`deploy_uz801_patched.sh` was run with the corrected file list, `--label "v18 (GROUP14 lazy-alloc a90524d7)"`
and `--reboot`. The guard counted the boot (`boot_count` 1 → cleared at 180 s).

| | value |
| :-- | :-- |
| `modem.b16` (live) | `a90524d72577d5604ff5ee15014e01a6` |
| `modem.mdt` (live) | `6360a026aaa58f7ee9b2d0a34180786d` |
| `boot_id` | `6b5b7fb2-acbd-4703-9290-504688b3ece1` |
| bootloop guard | armed, `boot_count` 1, no revert |

---

## 3. Result — GROUP 14 changes nothing

Two boot captures from the same device, one cold reboot apart, one byte of firmware different:

| | pre (v17 as deployed, no GROUP 14) | post (v18, GROUP 14) |
| :-- | --: | --: |
| md5 | `dfed904b82b9b88249ad1228988604b7` | `73843bfcd0975f8c9c171e6436df1475` |
| messages | 839 | 807 |
| span (median-`ts`) | 16.1 s | 15.7 s |
| distinct files | 32 | 28 |
| `Prot_state … 5(ONLINE_GWL)` | 0 | 0 |
| `Prot_state … 7(OFFLINE)` | 24 | 24 |
| `Prot_state … 0(NULL)` | 31 | 35 |
| `DUAL_STANDBY_CHGD` | 1 | 1 |
| `mmocdbg.c` | 60 | 60 |
| stock-only subsystems present | 1 of 25 (`mmocdbg.c`) | 1 of 25 (`mmocdbg.c`) |
| RFC-ish files | **NONE** | **NONE** |

**Live operating mode after the reboot: `offline`** (and still `offline` at 4 min uptime).

> **⇒ GROUP 14 is a NEGATIVE result.** Either `FUN_c0fa09f8` is never reached on this boot path, or the
> platform struct is not the gate. The lazy-allocator hypothesis is falsified as a standalone fix.

**v18 is behaviourally identical to v17-as-deployed.** It is left deployed because it is the
*correctly-built* v17 lineage and the extra byte is inert — but it is not a step forward.

---

## 4. ★ The named divergence: stock never enters `OFFLINE`; UZ801 is driven there and stays

Fresh differential, same AP / same NV / same hardware, **only the firmware set differs**:

| | STOCK HMU05 (`HIMI_U01_MODEM_V1.0`) | UZ801 v18 |
| :-- | --: | --: |
| messages | 31 443 | 807 |
| `Prot_state [MAIN] 5(ONLINE_GWL)` | **40** | **0** |
| `Prot_state [MAIN] 7(OFFLINE)` | **0** | **24** |
| `Prot_state … 0(NULL)` | 110 | 35 |
| `DUAL_STANDBY_CHGD` | **0** | **1** |
| `mmocmmgsdi.c` messages | **0** | 59 |

Two facts fall out:

1. **The stock firmware never enters `Prot_state 7(OFFLINE)` at all** — not as a transient, not at boot.
   Its MMOC walks `0(NULL)` → `5(ONLINE_GWL)` and stays.
2. **The `DUAL_STANDBY_CHGD` → `7(OFFLINE)` transition is unique to UZ801**, and UZ801 never leaves it.

`mmocmmgsdi.c` — the MMOC↔MMGSDI bridge — is a **UZ801-only module** (0 messages in the stock boot), and
Doc 195 §12.1.2 records the event that fires it: an **invalid session**,
`evt=0, sess_id=0`, `Sess type=0x7fffffff, ss=4` (`0x7fffffff` = an uninitialised field).

**⇒ The boot-path divergence is now a named event, not a gap.** UZ801's own MMGSDI bridge injects a
spurious `DUAL_STANDBY_CHGD` with an uninitialised session, which drives MMOC to `7(OFFLINE)`; the
platform then never recovers, so the LTE RF/protocol stack is never initialised and QMI DMS keeps
answering `DeviceNotReady`.

**Why GROUP 12/13 did not test this.** The v15–v17 `DUAL_STANDBY_CHGD` suppression was applied to **CM's
MMOC *event handler*** (`FUN_c0b54c40` @ `0xc0b54c48`; `FUN_c067cd2c` @ `0xc067ee58`) — i.e. the
*consumer*, not the producer. Doc 198 §5.2 confirms the command is still received. **The producer has not
been touched.**

---

## 5. New tooling left behind (all under `scratch/`, gitignored)

| tool | what it does |
| :-- | :-- |
| `re_func.py` | extract one function body from a Ghidra full-decompile `.c` |
| `re_calls.py` | list references/call sites of a symbol |
| `re_index.py` | exact function index (uses Ghidra's `/* ---- Function: NAME @ VA ---- */` markers); `--at`, `--callers` |
| `re_callers.py` | caller map (line → enclosing function) |
| `xmap.py` | **cross-map a UZ801 function to its HMU05 counterpart via F3 descriptor hashes** — the F3 format-string hash is build-independent, so it is a reliable anchor |
| `bootreport.py` | one-shot F3 boot summary (MMOC state, stock-only subsystems, RFC files, span guard) |

`xmap.py` is the useful one: the descriptor hash made it possible to prove the bring-up root is the same
function in both builds despite different VAs (the two segment maps diverge from `b16` up).

---

## 6. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Determine whether the deployed "v17" is v17 | a byte-level confirmation | it is **not** — GROUP 14 absent, 289 vs 290 diff bytes (§2.1) | **NOT MET** (as documented) |
| Test GROUP 14, the one never-deployed patch | bring-up proceeds / mode changes | boot log unchanged; mode still `offline` (§3) | **NOT MET** |
| Establish whether the bring-up root is reachable | a yes/no | it is a service-registered handler in **both** builds — the question is void (§2.3) | **MET** (question dissolved) |
| Name the stock-vs-UZ801 divergence | a specific event | stock never enters `OFFLINE`; UZ801 is driven there by a spurious `DUAL_STANDBY_CHGD` from the UZ801-only `mmocmmgsdi.c` (§4) | **MET** |
| Keep the device safe while iterating | no unrecoverable state | guard armed, one clean boot, no revert; one partial-install incident caught and repaired (§1) | **MET** |
| Fix the deploy helper | a correct install path | newline collapse + install exit-status check; stale `>2` comment corrected (§1) | **MET** |

---

## 7. What this changes

1. **Doc 197 §4** must be corrected: the row labelled `v17` is a **v16** result; a `v18` row is added.
2. **The next attempt should not be another DMS/CM/MMOC *consumer* patch, nor GROUP 14.** The producer of
   `DUAL_STANDBY_CHGD` — the UZ801-only `mmocmmgsdi.c` bridge — is the named target, and it has never
   been patched.
3. **The scope guard is unchanged.** Reaching `online` would *arm* the LTE-gated ~900 s fatal clock
   (Doc 194 §11). This is a porting milestone, not a stability fix.

---

## 8. Evidence inventory

| artifact | md5 / value | what it is |
| :-- | :-- | :-- |
| `scratch/g14_test/pre_v17_noG14_boot.bin` | `dfed904b82b9b88249ad1228988604b7` | v17-as-deployed boot capture (839 msgs) |
| `scratch/g14_test/post_v18_group14_boot.bin` | `73843bfcd0975f8c9c171e6436df1475` | v18 GROUP-14 boot capture (807 msgs) |
| `scratch/hmu05_stock_boot.bin` | (Doc 194) | stock HMU05 boot capture (31 443 msgs) |
| `scratch/uz801_patched/modem.b16` | `a90524d72577d5604ff5ee15014e01a6` | v18 image, GROUP 14 applied (290 diff bytes) |
| `/tmp/modem.b16.deployed_v17.md5_982a824e` | `982a824efca7c517d77f6eb5bca1f89e` | the previous (mis-labelled) image, preserved |
| `scratch/uz801_fw/image/modem.b16` | `848abe7eea4b772741eaaf3225dc5434` | pristine UZ801 segment 16 |
| `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` | — | 81 800 functions |
| `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` | — | 77 067 functions (the stock arm) |

`scratch/` is gitignored — these captures live on the build host only. **Do not delete them.**

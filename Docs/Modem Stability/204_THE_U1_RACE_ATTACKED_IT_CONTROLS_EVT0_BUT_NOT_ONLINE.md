# 204 — Attacking the `u=1` boot race: it is real and it controls `evt=0`, but it is NOT the online gate

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed as **v19** — `modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt` md5 `9f39ce579114bcf1383bbdce62a8d284`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10 1 [Sep 07 2015]`. **No firmware was built, patched or deployed in this session.**
**Status:** RESULTS + a **CORRECTION of Doc 203 §6.1** ("the patch target is now the `u=1` race"). The race is confirmed as a real, exactly-separating phenomenon — and confirmed **not** to be the online gate.
**Ledger:** companion to **`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`**; its §3/§4/§7/§8 are updated in the same session as this doc.
**Predecessors:** `200` (the online gate / the `dcc` break), `201` (the `0x1000001` gate is dead / the pivot is `cmmsc_auto`), `202` (the boot gate retracted / the CM boot-path divergence), **`203`** (the capture-class confound / the spurious `evt=0` / the instrument fix).

---

## 1. SOP compliance statement

| SOP step | Taken? | Detail |
| :-- | :-- | :-- |
| Verify against stock HMU05 ground truth | **Yes** | Every class/differential in §3–§6 is computed in the same session against `scratch/hmu05_stock_boot.bin` (30 958 F3 msgs by this doc's parser), and the race table in §3.2 is re-run over the whole corpus. |
| Byte-level / hash verification before attributing | **Yes** | `scratch/online_attempt2.bin` md5 `009b145c…` **equals** the device's live `/root/online_attempt.bin`; `scratch/race_boot_4.bin` md5 `a7f55a97…` **equals** the live `/root/diag_boot.bin` **and** the instrument's own `out_md5` in `/root/diagboot.status`. The instrument's `script_md5` (`d385dad0…`) is the Doc 203 build in every run. |
| Backup / reversibility | **Yes** | The only device writes are the four reboots the probe performed, `/root/online_attempt.bin`, and the DIAG re-bind + `cntl-enable` needed for the live capture. **No firmware, NV, guard or partition was touched.** No revert occurred (`modem-guard: stable for 180s; boot counter cleared`). |
| No blind patching of the baseband | **Yes** | **Nothing was patched.** §9 names the next target and marks any patch **UNBUILT**. |
| Reboot discipline / instrument-not-perturbing | **Yes** | The four reboots are the measurement (they *are* the experiment). The instrument is the Doc 203 async procd job (`START=11`), unchanged; its `SWEEP=64` default matches the Doc 203 baseline (§3.1). |
| Mandatory ledger update in the same session | **Yes** | Doc 197 §3/§4/§7/§8. |

## 2. Achieved vs Expected

| Objective (stated before the work) | Expected | Achieved |
| :-- | :-- | :-- |
| Attack the `u=1` boot race (Doc 203 §6.1's named patch target) | a lever on the race | **MET, with a negative verdict.** The race is real and exactly separates the two capture classes; **but both classes are `offline`, so winning the race is not sufficient for online** (§5). |
| Classify the two classes without the `f3clean` median-`ts` trap | a parser fix | **MET** — `scratch/race_classify.py`, delimiter-aware, emission-index ordered, no median filter. Reproduces Doc 203's 16/16 separation on the archived corpus and adds 4 new boots. |
| Establish whether the race decides `evt=0` | unknown | **MET — CONFIRMED.** Every `cm`-winner boot has no leading `evt=0`; every `mmgsdi_1`-winner boot does. Doc 203 §6.1's hypothesis **holds**. §4. |
| Establish whether the race decides online | Doc 203 §4 called it "**not established**" | **MET — NO.** The `cm`-winner (blind) class is `offline` on a **measured, same-boot** pair; the `mmgsdi_1`-winner (burst) class is `offline` by its own `Prot_state [MAIN] 7(OFFLINE)` log. §5. |
| Find the class-invariant gate | unknown | **MET (new).** `cmmsc_auto.c` = `cmss.c` = **0 in all 22 UZ801 captures** (both classes) vs **35 / 590** in stock. §6. |
| Confirm the modem itself refuses online (not an MM artifact) | a yes/no | **MET** — `AT+CFUN?` = `7`, `AT+CPIN?` = `READY`, `AT+CFUN=1` → **`+CME ERROR: phone failure`**, `CFUN` stays `7`. §7. |

**The one-line truth:** the `u=1` race is a **real, reproducible** boot-order phenomenon that **does** decide whether the spurious zeroed `evt=0` MMGSDI event is produced — but the `cm`-wins class, which is the *stock-like* outcome, is **still `offline`**, because a **class-invariant** gate (the CM serving-system / `cmmsc_auto` layer never running) blocks online in **both** classes. **Patching the race cannot take UZ801 online.**

---

## 3. The attack

### 3.1 Method and instrument

Doc 203 §3.2's instrument rule — *a negative is admissible only from a burst-caught capture* — makes the class the measurement unit, so the first job was to **measure the class cheaply and correctly**.

`scratch/f3clean.py` (used by `scratch/bootreport.py`) applies a **median-`ts` window** that Doc 202 proved deletes the stock boot chunk and its online-transition cluster. A race marker lives **in** the boot chunk, so the existing tooling could not be used. `scratch/race_classify.py` (new) parses with the **delimiter-aware** `f3parse2` (`0x7e` framing), orders strictly by **emission index**, applies **no** window, and extracts:

* the class (`mmocdbg.c` > 0 ⇒ BURST, else BLIND);
* every `=CM= CMD alloc u=<n>, tsk=<task>` in emission order — note the `tsk` is **pre-rendered into the record text** by `cmdbg.c` (an `snprintf` into the logged buffer), so it is directly greppable and needs no arg decoding;
* the `mmocmmgsdi_card_status_cb ... evt=%d,sess_id=%d` argument pairs;
* `gstk*`, `nvruim.c`/`mcfg_uim.c` counts.

It reproduces Doc 203's archived 16/16 separation exactly, then adds four fresh boots.

The instrument (`/root/diagboot_run.sh`, md5 `d385dad0905f33637e0f6cfa2cc1a594`) is the Doc 203 build, unmodified; `SWEEP=64` is its default and matches the Doc 203 baseline (the pre-Doc-203 script also hardcoded `64`, verified by reading `/root/diagboot_run.sh.pre203.bak` — so the sweep dimension stays retired and **cannot** explain a class flip).

### 3.2 ★ The race, re-measured — 22 UZ801 boots + stock

All numbers from `scratch/race_classify.py` (emission index; `mmocdbg.c` count; first `u=1` task; `gstk*` count; presence of a leading `evt=0`; `nvruim.c`/`mcfg_uim.c` count):

| capture | class | `mmocdbg.c` | first `u=1 tsk` | `cm` at `u=1`? | `gstk*` | leading `evt=0` | `nvruim` |
| :-- | :-- | --: | :-- | :-- | --: | :-- | --: |
| **`hmu05_stock_boot.bin`** | BURST | **316** | **`cm`** | yes | 22 | no | 0 |
| `v19_boot.bin` | BURST | 57 | `mmgsdi_1` | **no** | 6 | **YES** | 4 |
| `diag_boot_v7.bin` | BURST | 60 | `mmgsdi_1` | **no** | 6 | **YES** | 4 |
| `diag_boot_v8.bin` | BURST | 58 | `mmgsdi_1` | **no** | 6 | **YES** | 4 |
| `diag_boot_v13.bin` | BURST | 59 | `mmgsdi_1` | **no** | 6 | **YES** | 4 |
| `diag_boot_v16.bin` | BURST | 59 | `mmgsdi_1` | **no** | 6 | **YES** | 4 |
| `reboot_postpatch.bin` | BURST | 59 | `mmgsdi_1` | **no** | 5 | **YES** | 4 |
| `uz801_boot.bin`, `uz801_boot_4.bin`, `boot_hwid48.bin`, `patched_boot.bin`, `reboot_devcfg_boot.bin`, `boot_sweep256.bin`, `boot_256_prep_64sweep.bin`, `boot_after_user_reboot.bin`, `diag_boot_patch1.bin`, `diag_v14_boot.bin`, `diag_v16_boot.bin` | BLIND | 0 | `cm` | yes | 0 | no | 0–1 |
| **`race_boot_1.bin`** (new) | BLIND | 0 | `cm` | yes | 0 | no | 0 |
| **`race_boot_2.bin`** (new) | BLIND | 0 | `cm` | yes | 0 | no | 1 |
| **`race_boot_3.bin`** (new) | BLIND | 0 | `cm` | yes | 0 | no | 0 |
| **`race_boot_4.bin`** (new) | BLIND | 0 | `cm` | yes | 0 | no | 0 |

**Four fresh reboots of the same v19 image produced four BLIND (`cm`-wins) boots.** So the class is not a coin flip that the campaign happened to sample evenly; the current regime is heavily `cm`-wins. (The six archived BURST captures span v7…v19 and two days, so BURST is *reachable* — it is simply not what a reboot produces now.)

**Device-side corroboration (the probe's own output, `scratch/race_probe.sh`).** Each of the four boots carries `script_md5=d385dad0905f33637e0f6cfa2cc1a594` (the instrument was unchanged throughout) and **`fw=c71d53464a1e88f2072cbbb6f7f87475`** — i.e. **all four boots ran the identical v19 image**, so the class is a property of the *boot*, not of the firmware. The instrument's **own** on-device counters (independent of this doc's parser) report, on every one of the four boots:

```
count mmocdbg.c=0   count cmss.c=0   count cmmsc_auto.c=0
count cmregprx.c=0  count sdss.c=0   count rflte_core_rxctl.c=0
```

so `cmss.c`/`cmmsc_auto.c` = 0 in the BLIND class is confirmed **twice** (device counter and repo parser). `guard=1` on all four (threshold 3) — the bootloop guard never fired and no revert occurred.

The ordered `cmd` timelines show the race exactly:

```
BURST  v19_boot.bin : u=1 mmgsdi_1 (idx12) -> u=1 mmgsdi_1 (idx49) -> u=1 gstk x3 (idx142-147)
                      -> u=3 cm (idx237) -> u=3 mmgsdi_1 (idx281)          [cm never at u=1]
BLIND  race_boot_1  : u=1 cm (idx36) -> u=2 mmgsdi_1 (idx79)               [gstk absent]
STOCK  hmu05_stock  : u=1 cm (idx31) -> u=2 mmgsdi_1 (idx190) -> u=17 dcc ... 
```

The BLIND UZ801 timeline is **identical in shape to stock's first two commands** (`u=1 cm`, then `u=2 mmgsdi_1`). The BURST timeline is the anomaly: the UIM/STK side takes the `u=1` slot and CM's own first command is pushed to `u=3`.

---

## 4. ★ Result 1 — the race **does** control `evt=0` (Doc 203 §6.1 confirmed)

Doc 203 §4 found the spurious **zeroed** `mmocmmgsdi_card_status_cb(evt=0, sess_id=0)` immediately before `Recvd command 7(DUAL_STANDBY_CHGD)` and `Prot_state [MAIN] 7(OFFLINE)`, 16/16. Doc 203 §6.1 hypothesised that the race decides whether it is produced.

Re-measured on the four fresh boots, the separation holds with **no exceptions**:

| class | `u=1` winner | leading `card_status_cb` event sequence | `evt=0`? |
| :-- | :-- | :-- | :-- |
| BURST (6 archived) | `mmgsdi_1` | **`0`,** `13, 26, 14, 15, 4,` `19, 21, 12` | **YES** |
| BLIND (11 archived + 4 new) | `cm` | `19, 21, 12, 25, …` | no |
| stock | `cm` | `19, 21, 12, 25, 23` | no |

The BLIND class's `mmocmmgsdi.c` stream also shows a **healthy** session path — `Found session, sess_type=0, ss=0, session id=1036737951` — with **no** `Opening session type` and **no** `WAIT_SESSION_OPEN_CNF`. The BURST class shows the failure: `evt=0` → `Found session, sess_type=2147483647(0x7fffffff), ss=4, session id=0` (uninitialised) → `Opening session type : 1,6,7,0,2` → `Updating state: MMOC_TRANS_STATE_WAIT_SESSION_OPEN_CNF`, never leaving.

**⇒ Doc 203 §6.1's causal link is correct: the race determines whether the UIM/NV bridge's command becomes the zeroed `evt=0` that wedges MMOC.**

---

## 5. ★★ Result 2 — the race does **not** determine online. Both classes are `offline`.

This is the finding Doc 203 §4 explicitly left open (*"it is therefore **not established** that suppressing `evt=0` alone would take UZ801 to `online`"*) and §6.1's "winner = `cm` → MMOC state **unobservable** (blind), so whether it reaches `online` is **not known**".

It is now known.

### 5.1 The measured pair (BLIND ⇒ offline)

`race_boot_4.bin` md5 `a7f55a97…` is the live `/root/diag_boot.bin` of boot `a1b118ba-4b99-4fa8-b2af-47ce8ce9dde4` (the instrument's `out_md5` and the device's own md5 both match). On **that same boot**:

```
--dms-get-operating-mode  ->  Mode: 'offline'   (HW restricted: 'no')
--nas-get-serving-system  ->  Registration state: 'not-registered'
                              Radio interfaces: '1'  Detailed status: 'none'
```

So the `cm`-wins class — the *stock-like* race outcome — is **`offline`**, with MMOC healthy and no `evt=0`.

### 5.2 The BURST arm (offline by its own log)

The BURST captures need no new measurement: their own MMOC stream states `Prot_state [MAIN] 7(OFFLINE)` (Doc 203 §3.1's table; 60/60 in every burst capture), and it never leaves.

### 5.3 The verdict

| class | race winner | `evt=0` | MMOC | online? |
| :-- | :-- | :-- | :-- | :-- |
| BURST | `mmgsdi_1` | YES | wedged in `WAIT_SESSION_OPEN_CNF` | **no** (own `Prot_state 7(OFFLINE)`) |
| BLIND | `cm` | no | healthy session path | **no** (measured, §5.1) |

**Both outcomes of the race are `offline`.** Therefore the race is **necessary-condition-at-best, and provably not sufficient**: winning it (the BLIND class) does not restore online. Doc 203 §6.1's "the patch target is now the `u=1` race" is **corrected** — the race is a **class marker and the cause of the `evt=0`/MMOC wedge**, not the online gate.

---

## 6. ★★★ Result 3 — the class-invariant gate: `cmmsc_auto` / `cmss` never run in **any** UZ801 boot

> ⚠ **The runtime observation stands; the label does not (Doc 206 §3, 2026-09-26).** `cmmsc_auto.c` = 0 in
> all 22 UZ801 captures vs 35 in stock is **correct**. But the function this campaign has been calling
> `cmmsc_auto` (`FUN_c06928a8`, and the GROUP 11 patch target `0xc0691950` inside `FUN_c06917f0`) is
> **`cmcc.c`**. `cmcc.c` is *also* 0 in UZ801 (vs stock's 9), so the gate is real — but its
> *identity* was wrong, and the deployed GROUP 11 patch has never touched `cmmsc_auto.c`.

The gate that survives both classes is the one Doc 201 §5.3 named. Re-counted over **all 22 UZ801 captures plus stock**:

| file | stock | every UZ801 capture (BURST **and** BLIND) |
| :-- | --: | :-- |
| `cmmsc_auto.c` | **35** | **0** (all 22) |
| `cmss.c` | **590** | **0** (all 22) |

`cmmsc_auto` is the CM **automatic op-mode update** (`FUN_c06928a8`) — Doc 201 §5.3's "the first stock-only CM event", at modem uptime ≈1.8 s, immediately **after** the MMGSDI card events (`0x13, 0x15, 0xc`) and immediately **before** MMOC `SUBSCRIPTION_CHGD`. It is the function that "updates op_mode".

**This is the class-invariant differential.** It does not move when the race flips, which is exactly consistent with §5's result: flipping the race changes the MMOC branch but cannot start the CM serving-system layer.

**Admissibility caveat (stated, per Doc 203 §3.2).** A negative from a BLIND capture is not admissible under Doc 203's rule because MMOC is invisible there. That rule is about *MMOC observability*, not about file coverage — and §3.1 shows the class is **not** a coverage artifact (same `SWEEP=64`, four identical runs, and the class is set at the modem's first second while the capture is demonstrably listening — both classes contain their `u=1` boot chunk). So the BLIND `cmmsc_auto=0` is **consistent** evidence, not admissible evidence — and it is now confirmed by **two independent instruments** (the device's own `/root/diagboot.status` counter and this doc's parser, §3.2). **The admissible claim stands on the six BURST captures**, which Doc 203 §3.1 already tabulated at `cmss.c=0, cmmsc_auto.c=0`; §6 here only adds that the BLIND class does not contradict it.

---

## 7. The modem's own refusal — it is not a ModemManager artifact

On the same boot (`a1b118ba…`, `offline`), with ModemManager running (`mmcli -L` lists a modem object):

| probe | result |
| :-- | :-- |
| `qmicli --dms-set-operating-mode=online` | **`Operating mode set successfully`** — then the QMI services restart (`endpoint hangup`, the normal post-transition churn) |
| `qmicli --dms-get-operating-mode` (after) | **`Mode: 'offline'`** — unchanged |
| `at_tool AT` | `OK` — the AT channel is live |
| `at_tool AT+CFUN?` | **`+CFUN: 7`** |
| `at_tool AT+CPIN?` | **`+CPIN: READY`** — the SIM is fine |
| `at_tool AT+CFUN=1` | **`+CME ERROR: phone failure`** |
| `at_tool AT+CFUN?` (after) | **`+CFUN: 7`** — unchanged |

So the SIM is READY, the AT and QMI channels work, the DMS request is **accepted** — and the modem **refuses** to enter full functionality. This is the byte-for-byte UFI001B signature already recorded in the ledger (Doc 90), reproduced here as the control for §5.

**The live online-attempt capture** (`scratch/online_attempt2.bin`, md5 `009b145c…`, 3 645 msgs) shows the CM side of that refusal and re-confirms Doc 200 §4's **`dcc` break**:

```
idx 2196  ts=1008711020  =CM= CMD alloc u=192, tsk=dcc
idx 2197  ts=1008711092  =CM= CMD free  u=192, tsk=dcc, ftsk=cm     <- 72 ticks = 0.35 ms
idx 2965  ts=1017020780  =CM= CMD alloc u=232, tsk=dcc
idx 2966  ts=1017020852  =CM= CMD free  u=232, tsk=dcc, ftsk=cm     <- 72 ticks = 0.35 ms
```

**Nothing happens between alloc and free** — no `PROT_GEN_CMD`, no `OPRT_MODE_CHGD`, no `ONLINE` transaction (stock's chain is 2 824 ticks = **13.8 ms**, Doc 201 §5.1). The command is delivered and dropped. `dcc` is the only CM task seen in the whole capture; there is no `tsk=ds` record and **no opmode/oprt log line at all**. This is the mechanism behind §7's `AT+CFUN=1` failure, and it is **class-invariant** (it is a `cm`-side drop, and it occurs in a BLIND boot).

---

## 8. What this changes

1. **Doc 203 §6.1's "the patch target is now the `u=1` race" is corrected.** The race is a real, exactly-separating phenomenon and it *does* decide `evt=0` (§4) — but it is **not sufficient** for online (§5). Patching the race ordering would at best remove the MMOC wedge in the BURST class, leaving the modem `offline` exactly as the BLIND class already is.
2. **Doc 203 §4's open question is closed** ("not established that suppressing `evt=0` alone would take UZ801 to `online`"): it would not. The `cm`-wins class is the natural `evt=0`-free arm, and it is `offline`.
3. **The `evt=0` patch is downgraded from "fallback" to "not worth building".** It fixes a branch that is not on the critical path to online.
4. **The gate is the class-invariant one:** the CM serving-system / `cmmsc_auto` layer never running, plus the `dcc` command being dropped in 0.35 ms. Doc 201 §7.1's step 2 ("what starts the LTE/CM-SS layer on stock") remains **the** question — and §5/§6 here *raise* its priority by removing the race as a competing explanation.
5. **The instrument rule is refined.** The class is real but is **not** a coverage artifact (four identical `SWEEP=64` runs, class set in the modem's first second, both classes carrying their boot chunk). Doc 203 §3.2's rule stands as written; §3.1's data is the evidence that the class is a modem-behaviour property, not a capture property.

---

## 9. Named next step

**Attack the class-invariant gate, not the race.** In order of cheapness:

1. **Find what invokes `FUN_c06928a8` (`cmmsc_auto`).** Doc 201 §5.3: it has no direct caller and no pointer-table reference in the image — it is invoked through a **runtime-registered handler**, and it fires **after** the MMGSDI card events. The named question is *which message invokes it*. Log-string cluster: VA `0xc165c398`…`0xc165c3f8` (UZ801). The UZ801 build's `cmmsc_auto` log lines are **absent from every capture**, so the invocation — not the function body — is what to find.
2. **Re-enter the `dcc` break from the CM side.** §7's capture proves the command is delivered and dropped with no `PROT_GEN_CMD`. Doc 201 §4 showed the *submit* path leaves `modem.elf` at `thunk_EXT_FUN_d03d3420(0xc,0x1a,0x30f)`. The open question is what CM's dispatcher does with the delivered command — this is now the *only* measured CM-side failure common to both classes.
3. **Do not patch the race and do not build the `evt=0` patch** (per §8.1/§8.3).

**Immediate, cheap follow-up:** the four new BLIND boots prove the class is now stably `cm`-wins. A useful control is therefore to **re-obtain a BURST capture** (Doc 203 §3.2's "re-boot until burst") and confirm §6's `cmmsc_auto`=0 on a *fresh admissible* capture — cheap, and it closes the last admissibility gap in §6.

---

## 10. Evidence inventory

| artifact | md5 / value | what it is |
| :-- | :-- | :-- |
| `scratch/race_classify.py` | `8cbb1e67af1826aa0e4cb4ccc617071b` | the new class/race classifier (delimiter-aware, emission-index ordered, no median-`ts` filter) |
| `scratch/race_boot_1.bin` | `83b7a2638e1ddbe8d90b744433a36f64` | fresh v19 boot — **BLIND**, `u=1 cm` |
| `scratch/race_boot_2.bin` | `565c4a03f77e3252b2c25861b6521373` | fresh v19 boot — **BLIND**, `u=1 cm` |
| `scratch/race_boot_3.bin` | `6858004ee78d65ed6077ad062ff14641` | fresh v19 boot — **BLIND**, `u=1 cm` |
| `scratch/race_boot_4.bin` | `a7f55a973108e325f6e5a20312b7f6e1` | fresh v19 boot — **BLIND**; **the measured `offline` pair** (§5.1); equals the device's `/root/diag_boot.bin` and the instrument's `out_md5` |
| `scratch/race_boot_{1..4}.status` | — | the instrument's per-boot health records (`script_md5=d385dad0…`, `sweep=64`, `boot_id`, counts) |
| `scratch/online_attempt2.bin` | `009b145c161609677db5de518d0761ff` | the live capture taken **during** `--dms-set-operating-mode=online`; equals the device's `/root/online_attempt.bin`; shows the `dcc` 72-tick alloc→free (§7) |
| `scratch/race_probe.sh` | — | the 4-reboot probe that produced `race_boot_1..4` |
| `scratch/v19_boot.bin` | `231d9931b3185b4f1b528c5d55c5761f` | the archived **BURST** arm (deployed v19) |
| `scratch/hmu05_stock_boot.bin` | (94 623 391 B) | the stock control arm — `mmocdbg.c`=316, `cmss.c`=590, `cmmsc_auto.c`=35, `u=1 cm`, no `evt=0` |
| `/root/diagboot.status`, `/root/diagboot.log` | — | the on-device instrument health record and log |
| `scratch/diagboot_run.sh.current` | `d385dad0…` | the deployed instrument (Doc 203 build, unchanged this session) |

---

## 11. Reproduce

```python
import sys, glob; sys.path.insert(0, 'scratch')
from race_classify import classify
for p in sorted(glob.glob('scratch/race_boot_*.bin') + glob.glob('scratch/*boot*.bin')):
    r = classify(p)
    print("%-30s %-5s mmocdbg=%-4d u1=%-10s cm_absent=%-5s gstk=%-3d evt0=%s"
          % (p.split('/')[-1], r['klass'], r['mmocdbg'], r['u1_tsk'],
             r['u1_cm_absent'], r['gstk'], r['evt0']))
```

```sh
# the measured pair (BLIND => offline), on the boot whose capture is race_boot_4
ssh root@192.168.8.1 'cat /proc/sys/kernel/random/boot_id; \
  qmicli -d /dev/wwan0qmi0 --dms-get-operating-mode; \
  /root/at_tool "AT+CFUN?" /dev/wwan0at0; /root/at_tool "AT+CPIN?" /dev/wwan0at0; \
  /root/at_tool "AT+CFUN=1" /dev/wwan0at0'
```

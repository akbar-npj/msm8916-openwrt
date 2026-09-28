# Doc 203 — The capture-class confound, the spurious `evt=0` MMGSDI event, and the instrument fix

**Status:** RESULTS + RETRACTIONS (two docs corrected) + INSTRUMENT CHANGE (deployed)
**Ledger:** this doc is a companion to **`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`** and must be read with
it. The ledger's §3 rows, §4 patch history, §7 open questions and §8 evidence inventory are updated in the
same session as this doc.
**Predecessors:** `194` (boot-log differential), `199` (v18 negative), `200` (the online gate), `201` (the
gate is dead / the pivot is `cmmsc_auto`), `202` (Doc 194 §7 retracted / the CM boot-path divergence).

---

## 1. SOP compliance statement

| SOP step | Taken? | Detail |
| :-- | :-- | :-- |
| Verify against stock HMU05 ground truth | **Yes** | Every differential in §2/§4 is computed against `scratch/hmu05_stock_boot.bin` (33 039 msgs) in the same session. |
| Byte-level / hash verification before attributing | **Yes** | `scratch/boot_sweep256.bin` md5 `b6d94434…` **equals** the device's live `/root/diag_boot.bin` md5 — the capture identity is hash-proven, not narrated. Instrument versions are md5'd in §5. |
| Backup / reversibility | **Yes** | The only device writes are `/root/diagboot_run.sh` (backup `diagboot_run.sh.pre203.bak`, md5 `72e3ba04…`), `/root/diagboot_run.sh.new`, and the new `/root/diagboot.status`. No firmware, NV, guard or partition was touched. No reboot was performed in this session. |
| No blind patching of the baseband | **Yes** | Nothing was patched. §6 names a *candidate* patch and explicitly marks it **UNBUILT**. |
| Mandatory ledger update in the same session | **Yes** | Doc 197 §3/§4/§7/§8. |

## 2. Achieved vs Expected

| Objective (stated before the work) | Expected | Achieved |
| :-- | :-- | :-- |
| Confirm `boot_sweep256.bin` really is a 256-sweep capture | provenance proven by the live log | **MET** — the live `diagboot.log` head carries exactly **256** `CNTL TX F3_MASK` packets and the marker `--- cntl-enable 256 SSIDs at uptime=13.58 ---`. Doc 201 §6's provenance claim **stands**. |
| Re-check Doc 201 §6's "256 added exactly one file, `rcinit_rex.c`" | the artifact shows the delta | **FAILED — corrected.** The delta between the 64-sweep and 256-sweep captures is **`nvruim.c` (present at 64, absent at 256)**; `rcinit_rex.c` is present in **both**. §3. |
| Confirm the sweep bound is not the confound | the differential survives | **MET, by a different and stronger argument** — the 64- and 256-sweep captures are near-identical, so the sweep setting has no measurable effect on the delivered file set. §3. |
| Re-check Doc 202's central "same `u=1, tsk=cm`" claim | holds | **FAILED — corrected.** The `tsk` of the first CM command is **class-dependent**; Doc 202 read it from a capture of the wrong class. §3. |
| Find the immediate cause of `Prot_state 7(OFFLINE)` | unknown | **MET (new).** A **spurious `card_status_cb(evt=0, sess_id=0)`** precedes it, with a **16/16** correlation. §4. |
| Make the instrument self-reporting | new script | **MET** — deployed and counter-validated. §5. |

---

## 3. The capture-class confound — the campaign's central measurement trap

`scratch/boot_sweep256.bin` md5 `b6d94434…` is **byte-identical** to the file that was on the device at
session start. Cross-checking the device's own mtimes settles the provenance beyond argument:

| device fact | value | consequence |
| :-- | :-- | :-- |
| `diagboot_run.sh` mtime | **12:05:34** | the script was edited **after** the boot |
| device boot time (from `/proc/uptime`) | **12:00:09** | ⇒ the boot ran the **256-SWEEP** script |
| `/root/diag_boot.bin` mtime | 12:00:39 (boot+30 s) | the capture is **fresh**, not stale |
| live log head | `cntl-enable 256 SSIDs`, **256** `F3_MASK` packets | the sweep really was 256 |

So Doc 201 §6's *provenance* is correct. **What is not correct is the delta it reports.** Parsing the two
saved captures:

| capture | sweep | clean msgs | distinct files | `rcinit_rex.c` | `nvruim.c` | `mmocdbg.c` |
| :-- | --: | --: | --: | --: | --: | --: |
| `boot_256_prep_64sweep.bin` | 64 | 673 | 25 | 1 | **1** | 0 |
| `boot_sweep256.bin` | 256 | 670 | 24 | 1 | **0** | 0 |

⇒ the 64 → 256 delta is **`nvruim.c` lost**, not `rcinit_rex.c` gained. `rcinit_rex.c` is in both. Doc
201 §6's sentence "Going 64 → 256 added exactly **one** file, `rcinit_rex.c`" is **retracted**.

The *conclusion* of Doc 201 §6 survives and is in fact stronger: two captures whose sweeps differ by 4×
differ by **one file and three messages**, so **the sweep setting has no measurable effect on the delivered
file set** — it cannot be the explanation for a 4 487-pair stock capture versus a 112-pair UZ801 one.

### 3.1 ★ The trap: two UZ801 capture classes, and the docs mixed them

Tabulating every boot capture in the repo separates them into **two classes that are perfectly separated by
one observable**:

| capture | n | `mmocdbg.c` | first `CMD alloc` `tsk` | first MMOC `Recvd command` | first `Prot_state [MAIN]` | `cmss.c` | `cmmsc_auto.c` |
| :-- | --: | --: | :-- | :-- | :-- | --: | --: |
| **`hmu05_stock_boot.bin`** | 33 039 | **326** | `cm` | `0(SUBSCRIPTION_CHGD)` | `0(NULL)` | **609** | **36** |
| `v19_boot.bin` | 841 | 60 | `mmgsdi_1` | `7(DUAL_STANDBY_CHGD)` | `7(OFFLINE)` | 0 | 0 |
| `diag_boot_v13.bin` | 1 900 | 60 | `mmgsdi_1` | `7(DUAL_STANDBY_CHGD)` | `7(OFFLINE)` | 0 | 0 |
| `diag_boot_v7.bin` | 2 547 | 60 | `mmgsdi_1` | `7(DUAL_STANDBY_CHGD)` | `7(OFFLINE)` | 0 | 0 |
| `diag_boot_v8.bin` | 2 472 | 60 | `mmgsdi_1` | `7(DUAL_STANDBY_CHGD)` | `7(OFFLINE)` | 0 | 0 |
| `diag_boot_v16.bin` | 829 | 60 | `mmgsdi_1` | `7(DUAL_STANDBY_CHGD)` | `7(OFFLINE)` | 0 | 0 |
| `reboot_postpatch.bin` | 2 487 | 60 | `mmgsdi_1` | `7(DUAL_STANDBY_CHGD)` | `7(OFFLINE)` | 0 | 0 |
| `boot_sweep256.bin` | 670 | **0** | `cm` | *(invisible)* | *(invisible)* | 0 | 0 |
| `boot_after_user_reboot.bin` | 670 | **0** | `cm` | *(invisible)* | *(invisible)* | 0 | 0 |
| `boot_256_prep_64sweep.bin` | 673 | **0** | `cm` | *(invisible)* | *(invisible)* | 0 | 0 |
| `uz801_boot.bin` | 5 568 | **0** | `cm` | *(invisible)* | *(invisible)* | 0 | 0 |
| `uz801_boot_4.bin` | 5 707 | **0** | `cm` | *(invisible)* | *(invisible)* | 0 | 0 |
| `boot_hwid48.bin`, `diag_boot_patch1.bin`, `patched_boot.bin`, `reboot_devcfg_boot.bin` | 2 018–2 387 | **0** | `cm` | *(invisible)* | *(invisible)* | 0 | 0 |

Two consequences, both of which invalidate specific sentences in Docs 201 and 202:

1. **`tsk` is class-dependent, not firmware-dependent.** In the MMOC-visible class the first CM command is
   allocated by `tsk=mmgsdi_1` (the UIM bridge); in the MMOC-blind class it is `tsk=cm`. Doc 202's central
   claim that *both* firmwares issue the *same* `u=1, tsk=cm` command — and that the difference is only in
   how long the handler runs — was read from a **blind** capture. **Retracted as stated.**
2. **`mmocdbg.c` is not always present on UZ801.** Six captures show it (exactly 60 messages); nine show
   zero. Doc 194 §8 / Doc 199 §4 / Doc 201 §6 all listed `mmocdbg.c` among the "stock-only" subsystems.
   That is **true of the blind class only** — an MMOC-visible UZ801 capture emits it. **Corrected.**

**What survives — and it is the campaign's load-bearing claim.** Even in the MMOC-visible class, `cmss.c`
= **0** and `cmmsc_auto.c` = **0** across all six captures, while stock has 609 and 36. The CM
serving-system layer and `cmmsc_auto` genuinely never run on UZ801. **The differential is real; it is the
"missing `mmocdbg`" corollary that was an artifact.**

### 3.2 The instrument rule, restated (supersedes Doc 201 §6.1)

A negative scored from a UZ801 capture is admissible only if that capture is **burst-caught**, i.e. its
`mmocdbg.c` count is **non-zero** (60 in every burst-caught capture seen). Sweep-saturation is **not** the
discriminator — `boot_sweep256.bin` is sweep-saturated and still blind. From Doc 203 on, the sweep
dimension is **retired** and the burst dimension replaces it.

---

## 4. ★ The spurious `evt=0` MMGSDI card-status event (new, 16/16)

`mmocmmgsdi_card_status_cb` is the bridge that decides which MMOC command a card event becomes. Its F3
record carries the event id and the session id as arguments. Extracting them from every capture:

| capture | class | `card_status_cb` `evt` sequence | `evt=0`? |
| :-- | :-- | :-- | :-- |
| `hmu05_stock_boot.bin` | stock | `19, 21, 12, 25, 23` | **no** |
| `v19_boot.bin` | burst | **`0, 13, 26, 14, 15, 4,`** `19, 21, 12` | **YES** |
| `diag_boot_v13.bin` | burst | **`0, 13, 26, 14, 15, 4,`** `19, 21, 12` | **YES** |
| `diag_boot_v16.bin` | burst | **`0, 13, 26, 14, 15, 4,`** `19, 21, 12` | **YES** |
| `diag_boot_v8.bin` | burst | **`0, 13, 26, 14, 15, 4,`** `19, 21, 12` | **YES** |
| `diag_boot_v7.bin` | burst | **`0, 13, 26, 14, 15, 4,`** `4864, 21, 12` | **YES** |
| `reboot_postpatch.bin` | burst | **`0, 13, 26, 14, 15, 4,`** `19, 21, 12` | **YES** |
| `boot_sweep256.bin`, `boot_after_user_reboot.bin`, `boot_256_prep_64sweep.bin`, `uz801_boot.bin`, `uz801_boot_4.bin`, `boot_hwid48.bin`, `diag_boot_patch1.bin`, `patched_boot.bin`, `reboot_devcfg_boot.bin` | blind | `19, 21, 12, 25, …` (no leading `0`) | no |

**The correlation is 16/16 and the ordering is causal in the trace.** In `v19_boot.bin` the burst-class
boot chunk runs, in emission order (which equals `ts` order — verified, not assumed):

```
idx  12  cmdbg.c            =CM= CMD alloc u=1, tsk=mmgsdi_1          <- UIM bridge fires first
idx  13  nvruim.c           nvruim_mmgsdi_evt_cb 0x%x                 <- the UZ801-only UIM/NV bridge
idx  14  mcfg_uim.c         Got card event %x on slot index %x; sending cmd
idx  26  mmocmmgsdi.c       card_status_cb: Received MMGSDI Event: evt=0, sess_id=0   <-- ZEROED
idx  28  mmocdbg.c          =MMOC= Recvd command 7(DUAL_STANDBY_CHGD)
idx  31  mmocdbg.c          =MMOC= Prot_state [MAIN] 7(OFFLINE)
idx  35  mmocdbg.c          =MMOC= New transaction 12(MMGSDI_INFO_IND)
idx  41  mmocmmgsdi.c       Updating state: MMOC_TRANS_STATE_WAIT_SESSION_OPEN_CNF
idx  43  mmocdbg.c          =MMOC= Trans_state 14(WAIT_SESSION_OPEN_CNF)   <- never leaves
```

Stock's equivalent first event is `evt=19` and yields `0(SUBSCRIPTION_CHGD)`. UZ801's first event is
**`evt=0` with `sess_id=0`** — a zeroed structure — and yields `7(DUAL_STANDBY_CHGD)`.

**Reading.** A zeroed MMGSDI event is not a valid card event; it is the signature of a callback invoked
with an uninitialised or already-consumed event buffer. MMOC maps it onto the dual-standby branch, parks
`Prot_state` at `7(OFFLINE)`, opens a session, and waits forever for a `SESSION_OPEN_CNF` that the
uninitialised session cannot produce. **The UIM/NV bridge (`nvruim.c` / `mcfg_uim.c`) is the UZ801-only
component upstream of it.**

**What this does NOT yet prove.** In the blind class the leading `evt=0` is absent — but that class is also
blind to MMOC, so we cannot read its `Prot_state`. It is therefore **not established** that suppressing
`evt=0` alone would take UZ801 to `online`; it is established that `evt=0` is the immediate precursor of
the OFFLINE transition in every boot where MMOC is observable. The two classes may themselves be two
outcomes of one upstream race (see §6).

---

## 5. The instrument fix (deployed)

Two defects were found and fixed in `/root/diagboot_run.sh`:

1. **The capture tool's stdout was not redirected.** `diag_logtool capture` writes the raw stream to `$OUT`
   *and* emits the same bytes on stdout, which `exec >> "$LOG"` sent into `/root/diagboot.log` — inflating
   it to **134 726 075 bytes** per boot for a 698 774-byte capture. Fixed with `>/dev/null 2>&1`; `$OUT` is
   unaffected (it is written by the tool itself, proven by its size/md5).
2. **The capture's health was not recorded.** Some boots come back with the whole MMOC/STK layer missing
   (§3.1). A new `/root/diagboot.status` records `boot_id`, the instrument's own md5, start/end uptime,
   sweep, `out_size`, `out_md5`, and per-file occurrence counts for the discriminator files
   (`mmocdbg.c`, `cmss.c`, `cmmsc_auto.c`, `cmregprx.c`, `rflte_core_rxctl.c`, `nvruim.c`, `mcfg_uim.c`,
   `gstk_s_term_profile_rsp_wait.c`, `estk_bip.c`, …). A blind capture is now detectable **without
   re-parsing the blob**.

Also parameterised: `SWEEP=${DIAGBOOT_SWEEP:-64}` (behaviour unchanged at the default).

| artifact | md5 |
| :-- | :-- |
| `/root/diagboot_run.sh` (new, deployed) | `d385dad0905f33637e0f6cfa2cc1a594` |
| `/root/diagboot_run.sh.pre203.bak` (previous) | `72e3ba04870b88efc3a61b860f179ab0` |
| local copy | `scratch/diagboot_run.sh.current` |

**Counter validation.** The on-device counter was checked against the repo parser on the same artifact
(`boot_sweep256.bin`): `cmdbg.c` 4/4, `mmgsdi_refresh.c` 8/8, `cfm_cpu_monitor.c` 315/314, `qmi_nas.c`
83/81, `mmocmmgsdi.c` 17/13. It over-counts by a few (substring hits inside format strings) but is exact
for the **presence** question that matters (`mmocdbg.c` 0 vs 60).

---

## 6. What this changes, and the named next step

1. **The sweep dimension is retired; the burst dimension replaces it.** Any future negative must be scored
   on a capture with `mmocdbg.c` > 0. Re-boot until one is obtained — `/root/diagboot.status` now says
   immediately whether it is usable.
2. **The named static target is `mmocmmgsdi_card_status_cb`'s handling of `evt=0`.** The candidate patch is
   to make the callback ignore a zeroed event (or to stop the UIM/NV bridge from emitting it). **UNBUILT** —
   recorded here as the single-variable experiment, not performed.
3. **The competing hypothesis to test first (cheap).** The burst and blind classes differ in *which* CM
   command wins the boot race: burst = `u=1 tsk=mmgsdi_1` (UIM bridge) and the CM's own command only at
   `u=3`; blind = `u=1 tsk=cm` and the UIM bridge at `u=2`. If that race is what decides whether `evt=0`
   is produced, then the lever is **the ordering of the boot-time CM command**, not MMOC at all. The burst
   class also carries `gstk` (SIM Toolkit) activity that the blind class lacks — a testable correlate.

### 6.1 ★ The race hypothesis, tabulated — it separates the classes perfectly

Run on every capture in the repo (indices are parse/emission positions, which equal `ts` order):

| capture | `mmocdbg.c` | first `u=1 tsk=cm` | first `u=1 tsk=mmgsdi_1` | `gstk*` | `evt=0` | winner |
| :-- | --: | --: | --: | --: | :-- | :-- |
| `v19_boot.bin` | 60 | **absent** | idx 12 | 6 | YES | **mmgsdi** |
| `diag_boot_v13.bin` | 60 | **absent** | idx 11 | 6 | YES | **mmgsdi** |
| `diag_boot_v16.bin` | 60 | **absent** | idx 9 | 6 | YES | **mmgsdi** |
| `diag_boot_v7.bin` | 60 | **absent** | idx 10 | 6 | YES | **mmgsdi** |
| `diag_boot_v8.bin` | 60 | **absent** | idx 10 | 6 | YES | **mmgsdi** |
| `reboot_postpatch.bin` | 60 | **absent** | idx 10 | 6 | YES | **mmgsdi** |
| `boot_sweep256.bin` | 0 | idx 37 | absent | 0 | no | **cm** |
| `boot_after_user_reboot.bin` | 0 | idx 37 | absent | 0 | no | **cm** |
| `boot_256_prep_64sweep.bin` | 0 | idx 45 | absent | 0 | no | **cm** |
| `uz801_boot.bin` | 0 | idx 33 | idx 76 | 0 | no | **cm** |
| `uz801_boot_4.bin` | 0 | idx 32 | idx 75 | 0 | no | **cm** |
| `boot_hwid48.bin`, `diag_boot_patch1.bin`, `patched_boot.bin`, `reboot_devcfg_boot.bin` | 0 | idx 33–35 | idx 76–78 | 0 | no | **cm** |
| **`hmu05_stock_boot.bin`** | 326 | idx 32 | **absent** | 23 | no | **cm** |

**The separation is exact and it is not a truncation artifact.** The blind captures are *longer* than the
burst ones (2 000–5 707 messages vs 829–2 547), and the burst captures do **not** contain a `u=1 tsk=cm`
record at *any* index — so the blind class is not a truncated prefix of the burst class. The two are
**genuinely different boot sequences of the same firmware**, distinguished by which task wins the `u=1`
CM-command race:

* **UIM bridge wins** (`u=1 tsk=mmgsdi_1`) → `gstk` STK activity present → MMOC receives the **zeroed
  `evt=0`** event → `7(DUAL_STANDBY_CHGD)` → `Prot_state 7(OFFLINE)` → stuck in `WAIT_SESSION_OPEN_CNF`.
* **CM wins** (`u=1 tsk=cm`) → the normal `19, 21, 12, 25` event sequence → MMOC state **unobservable**
  (blind), so whether it reaches `online` is **not known**.

**What this changes.** The `mmocdbg.c` count — until now treated as a pure *instrument coverage* tell —
is in fact a **boot-type tell**: it is MMOC's own debug logging, which only appears in the boots where MMOC
does substantial work. Stock is `cm`-winner *and* MMOC-visible, so "mmocdbg present" is not by itself
"burst caught"; the honest reading is that **the two UZ801 classes are two different boots**, and the
campaign's "coverage" framing (Doc 202 §5) was one hypothesis too narrow. The negative
(`cmss.c`/`cmmsc_auto.c` = 0) holds in **both** classes, so no conclusion of the campaign is overturned —
but **the patch target is now the `u=1` race, upstream of MMOC**, and the `evt=0` patch is the fallback.

---

## 7. Evidence inventory

| artifact | what it is |
| :-- | :-- |
| `scratch/hmu05_stock_boot.bin` (94 623 391 B) | the stock control arm — 33 039 msgs, `mmocdbg.c`=326, `cmss.c`=609, `cmmsc_auto.c`=36, no `evt=0` |
| `scratch/v19_boot.bin` | **the usable UZ801 capture** — burst-caught (`mmocdbg.c`=60), first event `evt=0`, `DUAL_STANDBY_CHGD`, `7(OFFLINE)` |
| `scratch/boot_sweep256.bin` (md5 `b6d94434…`) | the 256-sweep capture — provenance **proven**, but **blind** (`mmocdbg.c`=0) |
| `scratch/boot_256_prep_64sweep.bin` (md5 `eadb5877…`) | the 64-sweep capture it was compared against — also blind; the delta is `nvruim.c` |
| `scratch/boot_after_user_reboot.bin` (md5 `b6d94434…`) | byte-identical to `boot_sweep256.bin`; pulled from the same device file |
| `scratch/diag_boot_v{7,8,13,16}.bin`, `scratch/reboot_postpatch.bin` | the other five burst-caught UZ801 captures — all show `evt=0` |
| `scratch/diagboot_run.sh.current` | the fixed instrument (deployed as `d385dad0…`) |
| `/root/diagboot.status` | new per-boot capture-health record |
| `/root/diagboot.log` | the live instrument log; head verified ASCII, carries the 256-packet sweep proof |

**Reproduce the key table:**

```python
import sys, glob, collections; sys.path.insert(0,'scratch')
from f3parse import parse
for p in sorted(glob.glob('scratch/*boot*.bin')+glob.glob('scratch/diag_boot_*.bin')):
    d, ms = parse(p)
    c = collections.Counter(m['file'] for m in ms)
    ev = [m['args'][0] for m in ms
          if m['file']=='mmocmmgsdi.c' and 'card_status_cb' in m['fmt'] and m['args']]
    print("%-32s mmocdbg=%-4d evt=%s evt0=%s"
          % (p.split('/')[-1], c.get('mmocdbg.c',0), ev[:6], 0 in ev))
```

# 211 — The `args`-shift parser defect, and the first clean same-window stock-vs-UZ801 differential

**Date:** 2026-09-27
**Ledger:** this document is a companion of **`197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`**
(see §1). It must be read with it.
**Predecessors:** 208 (the burst class was lost), **210** (the burst class restored; the
refuted CM negatives; the policyman RAT mask zero).
**Standing goal:** make the transplanted **UZ801 v3.2 "-21"** baseband leave `offline` and go
`online` on the **HMU05** (MSM8916 + WTR1605 + QFE2320, OpenWrt 25.12.5, kernel 6.12.94 aarch64).

---

## §1 SOP compliance and achieved-vs-expected

### §1.1 SOP-compliance statement

Per Doc 197 §1 (mandatory from Doc 197 onward):

| SOP step | Status | Where |
|---|---|---|
| Never blind-patch a baseband; verify against stock HMU05 ground truth | **TAKEN** | §4 — stock was restored, booted, and measured `online` + registered on this exact device/EFS before any UZ801 claim was made |
| Fingerprint firmware by `--dms-get-revision` **and** `modem.mdt` MD5, never by a narrative | **TAKEN** | §4.2, §6.2 |
| Archive both firmware sets so the swap is reversible | **TAKEN** | §6.1 (UZ801 v19 = 68-file manifest archive); stock revert path already existed |
| Verify a deployment with the **loadable image**, never a bare md5 | **TAKEN** | §4.2, §6.2 (`--dms-get-revision` + operating mode + serving system) |
| Pre-register n before scoring a rate | **N/A** | this document scores no rate |
| Update the ledger in the same session | **TAKEN** | §12 |
| State an explicit SOP-compliance statement | **TAKEN** | this table |

### §1.2 Achieved vs expected

| # | Expected at the start of this session | Achieved | Verdict |
|---|---|---|---|
| 1 | Finish the Doc 210 ledger fold-in | done | **MET** |
| 2 | Execute Doc 210 §11's decisive follow-on: a stock boot on the **restored** instrument | stock reverted, booted, `admissible=yes` capture taken (`rcinit_init.c` = 76) | **MET** |
| 3 | Decide Doc 210 §5's policyman lead | **FALSIFIED** — stock emits the identical `Filtered RAT mask 0 based on HW capabilities 544` and still reaches `online` | **MET (as a negative)** |
| 4 | Obtain a clean stock-vs-UZ801 differential on **one instrument, one window** | obtained — §7 | **MET** |
| 5 | Restore UZ801 v19 and verify | done — 68/68 manifest match, `Modem: 'offline'` | **MET** |
| 6 | Explain *why* UZ801 stays offline | **not achieved** — but the differential is now sharply localised to the **UZ801-only UIM/STK bring-up + the SD `ssscr_user_offline_cdma` script + the MMOC `OPRT_MODE_CHGD → 3(OFFLINE)` decision** | **PARTIAL** |
| 7 | — | **new, unplanned:** found and fixed a **second `f3parse.py` defect** that had been scaling the decoded `args` of ~2 % of records by 256^extra | **MET** |

---

## §2 The `f3parse.py` `args`-shift defect (defect #2)

### §2.1 What was wrong

Doc 210 §3.4 fixed **defect #1**: a minority of F3 records carry one (rarely two) extra `0x00`
byte, and the pre-210 parser silently **dropped** them (0.5–2.1 % of records). The Doc-210 fix
searched for that pad byte so the records were no longer dropped — **but it still read the
arguments at `k+20+4*j`.**

The pad sits **before** the arguments, so the whole `args[]` block is shifted. Reading at `k+20`
returns every argument **multiplied by 256^extra**.

### §2.2 Raw proof

`stock_boot_211.bin`, record at `off=0x30d33`, `na=2`, `extra=1`:

```
79 00 02 00 | 00 28 94 01 | 00 00 00 00 | 37 7d | 5e 05 | 00 04 00 00 | 00 | 26 00 00 00 | 00 00 00 00 | 00 | 3d 43 4d 3d ...
```

* `line` = `0x7d37` = **32055** → `cmph.c:32055`
* the byte at `k+20` is `0x00` — **the pad**
* `args@k+20`   = `[0x2600, 0]` = `[9728, 0]`
* `args@k+20+1` = `[0x26,   0]` = `[38,   0]`
* the format string starts at `k+29` (`=CM= mode_pref %d, pref_term %d`)

`9728 = 38 × 256`. The pad is one byte; the shift is one byte.

### §2.3 Corroboration (three independent lines, one capture)

| file:line | fmt | extra | old (wrong) | true |
|---|---|---|---|---|
| `cmph.c:32055` | `=CM= mode_pref %d, pref_term %d` | 1 | `9728` | **`38`** |
| `cmph.c:14080` | `=CM= mode_pref %d, pref_term %d` | 2 | `2490368` | **`38`** |
| `cmmsc_auto.c:24189` | `=CM= old_op_mode:%d new_op_mode:%d ue_mode:%d` | 1 | `2560, 2816` | **`10, 11`** |
| `cmph.c:9984` | `=CM= NV hybr pref set to %d` | 1 | `256` | **`1`** |

The corrected values are self-evidently right: `10 → 11` is an op-mode transition, `1` is a
boolean, and `38 = 0x26` is a plausible `cm_mode_pref` bitmask (`9728 = 0x2600` and
`2490368 = 0x260000` are not).

### §2.4 Why the `extra` heuristic itself is still sound

`extra` is found by searching for the first offset (0..3) at which a printable `fmt` beginning at
`k+20+4*na+extra` parses cleanly. Two worries were checked and dismissed:

* **A pad byte that is printable.** The pad is always `0x00`, so the search is unambiguous.
* **A genuine doubled prefix.** `mmocmmgsdi.c` emits `==MMOC= Found session, sess_type=%d, …` —
  with a **double `=`**. This looked like a mis-detected pad, but the raw bytes show the `fmt`
  really does start at `base+0` and **both builds** emit the doubled `=` — it is a typo in the
  vendor source, not an artefact. The heuristic is correct; only the `args` offset was wrong.

### §2.5 What changed

`scratch/f3parse.py` now reads `args` at **`k+20+extra+4*j`** (one-line change, documented in the
docstring). `scratch/f3parse.py.pre211` is the pre-fix file, kept for provenance.

| file | md5 |
|---|---|
| `scratch/f3parse.py` (fixed) | `9f9d6edf15d64faf3b47897f7838c9ac` |
| `scratch/f3parse.py.pre211` | `59ccb3db69d084eae415dd940d7cdf1a` |

**Scope of the damage.** Only ~2 % of records are affected, but they include **every CM
`mode_pref` / `old_op_mode` / `NV hybr pref` record in every stock capture** (those call sites all
carry the pad). Any conclusion drawn from a shifted `args` value in Docs 194–210 must be re-checked.
The verdicts that move are listed in §3.

---

## §3 What the fix changes

### §3.1 The `mode_pref` difference is FALSIFIED

The most promising lead of the session was that `=CM= mode_pref %d` reads **9728 on stock** and
**38 on UZ801**. With the fix, **it is 38 in every arm**:

| capture | `cmph.c mode_pref %d, pref_term %d` | extra |
|---|---|---|
| `stock_boot_240.bin` | `[38, 0]` | 1 |
| `stock_boot_211.bin` | `[38, 0]` | 1, 2 |
| `hmu05_stock_boot.bin` (archived, online) | `[38, 0]` | 1 |
| `boot_210_canon.bin` (UZ801) | `[38, 0]` | 0 |
| `uz801_v19_boot_240.bin` (UZ801 v19) | `[38, 0]` | 1 |

Stock's own `cmsoa.c main mode_pref=%d` (**extra = 0**) and `cmmsc_auto.c HICPS:
cm_mode_pref: %d, ph obj mode_pref: %d, ph_ptr mode_pref: %d` = `[39, 38, 38]` (**extra = 0**)
independently confirm 38. **There is no `mode_pref` differential.**

### §3.2 Every other CM-layer numeric is identical too

With the fix, on the same instrument and the same 240 s window (§7):

| quantity | stock | UZ801 v19 | extra |
|---|---|---|---|
| `mode_pref` | 38 | 38 | 1 |
| `old_op_mode → new_op_mode` | 10 → 11 | 10 → 11 | 1 |
| `NV hybr pref set to %d` | 1 | 1 | 0 / 1 |
| `MSC_AUTO: 0x%x & 0x%x = 0x%x` | `0x200 & 0xebe = 0x200`, `0x200 & 0x220 = 0x200` | `0x200 & 0xebe = 0x200` | 0 |
| `Filtered RAT mask %d based on HW capabilities %d` | `[0, 544]` | `[0, 544]` | 0 |
| `RAT_DISABLED_MASK: sys_mode %d, is_rat_disabled=%d, on as_id %d` | `[9,0,0]`, `[4,0,0]` | `[9,0,0]`, `[4,0,0]` | 0 |

**So the CM *inputs* are byte-identical. The divergence is entirely downstream.**

### §3.3 Leads killed by the fix

* **"`mode_pref` 9728 (stock) vs 38 (UZ801)"** — dead (§3.1).
* **"stock `MSC_AUTO` mask `0x220` vs UZ801 `0xebe`"** (Doc 210 §8) — dead as a *build*
  difference. **Stock emits `0xebe` first and `0x220` later in the same boot**
  (`stock_boot_240.bin`, `stock_boot_211.bin`); the archived online capture caught only `0x220`
  and the UZ801 captures caught only `0xebe`. It is **which invocation the capture happened to
  catch**, not a build difference. Doc 210 §8's framing is **RETRACTED**.
* **"stock `cmph.c` logs `mode_pref` at 3 call sites, UZ801 at 1"** — an artefact of the same
  thing plus the different `cmph.c` source revision (stock `:32055`, UZ801 `:32761`).

### §3.4 Leads that survive the fix

* Doc 210 §5's **policyman** failure (`Filtered RAT mask 0 based on HW capabilities 544`,
  `policyman_rat_capability.c:726 … status 2, filesize 0`, `policyman_efs.c:334 Error writing
  file`) is **shared with stock** — stock emits it and still reaches `online`. **Not a
  differentiator.** (extra = 0 throughout, so this verdict is unaffected by the fix.)
* The **MMOC `OPRT_MODE_CHGD` → `2(ONLINE)` vs `3(OFFLINE)`** differential (§9) — those records
  have **`num_args = 0`**, so the defect cannot touch them. **SOLID.**

---

## §4 The stock arm on the restored instrument

### §4.1 Why it was needed

Doc 210 §11 left exactly one decisive follow-on: the campaign's archived stock capture
(`hmu05_stock_boot.bin`) was taken with an **older** instrument, and the Doc-210-era stock capture
(`stock_boot_211.bin`) never reached the policyman block. A stock boot on the **restored**
instrument was required to decide Doc 210 §5.

### §4.2 The stock arm

`bash scratch/swap_uz21_full.sh --revert` restored stock and deleted the UZ801-only files. All four
expected hashes matched:

| file | md5 |
|---|---|
| `modem.mdt` | `1a6f9507e03d4ddbbf1977af81ecdbd7` |
| `wcnss.mdt` | `62bb56b2bfd0a1aa1ef57ec44b51a396` |
| `cmnlib.mdt` | `78752167469a6b0d981765445fbb235e` |
| `mcfg_sw.mbn` | `0be0d361224873be71e012637e04b3e4` |

### §4.3 Stock reaches `online` on THIS device, on THIS EFS

```
--dms-get-revision          Revision: 'HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]'
--dms-get-operating-mode    Mode: 'online'   HW restricted: 'no'
--nas-get-serving-system    Registration state: 'registered'
                            Selected network: '3gpp'
                            Radio interfaces: '1'
                            MCC: '405'  MNC: '861'  Description: 'JIO 4G'
--nas-get-technology-pref   Active: '3gpp, lte', duration: 'permanent'
```

This is **the control the campaign never had**: same hardware, same SIM, same EFS, same AP — stock
registers on JIO 4G, UZ801 never leaves `offline`.

### §4.4 The decisive negative: the policyman failure is SHARED

The stock boot capture (`stock_boot_211.bin`, `admissible=yes`, `rcinit_init.c` = 76) reaches
**both** `rcinit` **and** the policyman block — which the archived campaign capture never did. It
emits the **identical** sequence, decoded from `args[]`:

```
policyman_rat_capability.c:726  subs 0/1/2: policyman_retrieve_rats_bands returned status 2, filesize 0
policyman_rat_capability.c:98   can't populate RAT item from EFS
policyman_rf.c:592  ->  policyman_ue_mode.c:122
policyman_rat_capability.c:74   Filtered RAT mask 0 based on HW capabilities 544     args=[0, 544]
policyman_efs.c:334             Error writing file in policyman_efs_put_file()
policyman_rat_capability.c:907  efs rat & band write status = 2
```

Stock's only extra record is `policyman_serving_system.c:1024 ss precondition checks fail, reset
PRECOND_SS`.

**A filtered RAT mask of 0 is therefore NOT why UZ801 stays offline.** Doc 210 §5's lead is
**FALSIFIED**.

### §4.5 A 240 s stock boot capture on the restored instrument

The instrument's window was raised to `DIAGBOOT_SECS=240` (backup kept at
`/root/diagboot_run.sh.secs90`; script md5 `9cd993a7f214b6847e92a7c21ca8c8c8`) and stock rebooted.

| | |
|---|---|
| `scratch/doc211/stock_boot_240.bin` | md5 **`0ff4e4d22c23443e2c6e82bcb24cbc83`**, 1 500 441 B |
| window | modem uptime 12.75 s → 260.05 s |
| admissibility | `rcinit_init.c = 81` → **`admissible=yes`** |
| CM files | `cmph.c` 60, `cmss.c` 18, `cmlog.c` 6, `cmmsc_auto.c` 8, `cmregprx.c` 7, `mmocdbg.c` 141, `mmoc.c` 11 |
| `Prot_state` | `0(NULL)` in all 52 records — **the online transition is not reached inside 240 s** |

So the archived `hmu05_stock_boot.bin` remains the online-transition reference (§9).

### §4.6 The live online reference

`scratch/doc211/live_stock_online.bin` (md5 `e765744410d570e9eb0423bc46b58f9f`, 65 902 256 B;
gzip md5 `119085b9bb75ff4b18ad90e2e45bf0af`) — 25 862 records / 60 s from the **online** modem,
dominated by `rflte_core_rxctl.c` 4020, `pgi_msgr.c` 3962, `a2_power.c` 3815, `mcpm_npa.c` 2173,
`mcpm_drv.c` 1262, **`cmlog.c` 945**, `qmi_nas.c` 901, **`cmss.c` 619**, `mmocdbg.c` 90,
`sdss.c` 48, with **`Prot_state [MAIN] 5(ONLINE_GWL)`**. An online modem emits ~1 MB/s.

---

## §5 The `ts` field is per-subsystem

The F3 `code` field partitions a capture into **independent clocks**. `ts` is only comparable
*within* one `code`:

| capture | `code` partitions (`n`, `ts` range) |
|---|---|
| `stock_boot_240.bin` | `0` (1186), `4773` (454), `42337` (1) |
| `stock_boot_211.bin` | `0` (1130, 62 756–106 855 820), `4773` (536, 897 917 824–900 809 884), `42293` (3) |
| `hmu05_stock_boot.bin` | `4764` (31 477), `4756` (1286), `40151` (454), `0` (334), … |
| `uz801_v19_boot_240.bin` | `4773` (1565), `1` (120), `42270` (33) |

`code = 4773` appears in **both** the stock boot and the UZ801 boot, so `code` is a **per-subsystem
id**, not a per-boot one. Ordering F3 messages by `ts` across codes is meaningless — this is the
mechanism behind the apparent multi-thousand-second "jumps" seen in earlier docs. **Order by
delivery order (file order), or within a single `code`.**

---

## §6 The UZ801 v19 arm

### §6.1 The restore

`scratch/doc211/restore_uz801_v19.sh` (md5 `51965239169cd8c048632f8678fef2d8`) extracts
`/overlay/fwbackup/uz801_v19_full.tar.gz` (28 176 443 B) over `/lib/firmware`, then removes every
file not named in `/overlay/fwbackup/uz801_v19_full.manifest` (68 files), then verifies every
manifest md5.

> **Fix made this session.** The first version used `comm -23`; **`comm` is not in this OpenWrt
> busybox** and the script aborted *after* the extraction but *before* the extra-file removal —
> i.e. it left a **partial** set. Replaced with `awk 'NR==FNR{a[$0];next} !($0 in a)'`. This is the
> same class of trap as the Doc-197 deploy script's `$FILES` bug: **an aborted deploy can leave a
> partial set.**

### §6.2 The verification

```
==> extra files: 0
==> ALL 68 FILES MATCH THE MANIFEST
9f39ce579114bcf1383bbdce62a8d284  /lib/firmware/modem.mdt
c71d53464a1e88f2072cbbb6f7f87475  /lib/firmware/modem.b16
3d028c77ebe034d7fe25d5033cccb5bf  /lib/firmware/wcnss.mdt
dec6fdd2544aac29f119d44e229f1108  /lib/firmware/cmnlib.mdt
830c13a6c02e0c2fc837005a01344dd5  /lib/firmware/mcfg_sw.mbn
```

After the reboot (boot_id `26d74096-874f-45ee-9de4-67a8ee87caf1`):

```
--dms-get-revision   Revision: 'UZ801_V3.0_21_V01R01B10  1  [Sep 07 2015 23:00:00]'
--dms-get-operating-mode   Mode: 'offline'
modem-guard boot_count = 0, no /root/BOOTLOOP_REVERT.txt
```

### §6.3 The 240 s UZ801 v19 capture

| | |
|---|---|
| `scratch/doc211/uz801_v19_boot_240.bin` | md5 **`81318b04f7e0da3e74675d2973366758`**, 1 240 397 B |
| window | modem uptime 10.55 s → 252.92 s |
| admissibility | `rcinit_init.c = 110` → **`admissible=yes`** |
| CM files | `cmph.c` 97, **`cmss.c` 0**, **`cmlog.c` 0**, `cmmsc_auto.c` 4, `cmregprx.c` 7, `mmocdbg.c` 140, `mmoc.c` 7, `uim.c` 1, `mcfg_uim.c` 9 |

---

## §7 The same-window differential (the headline)

**Same device, same SIM, same EFS, same AP, same instrument, same 240 s window. Only the firmware
differs.**

| axis | stock HMU05 | UZ801 v19 | reading |
|---|---|---|---|
| CM serving-system (`cmss.c`) | **18** | **0** | UZ801's CM **never enters the serving-system layer** |
| CM serving-system log (`cmlog.c`) | **6** | **0** | same |
| CM `cmph.c` | 60 | 97 | more CM *attempts*, no serving system |
| SD name-select | `sdcmd_name_sel3()-is_gwl_subsc_avail / is_gw2_subsc_avail / msim / is_gw3_subsc_avail` | `hybr_name_sel()-is_gw2_subsc_avail / simstate` | **different SD source** |
| SD user script | — | **`=SD= ** Activate user script = ssscr_user_offline_cdma **`** | **UZ801 explicitly activates an OFFLINE-CDMA script** |
| MMOC session-open source | `sess opn cnf: 1x=%d,gw=%d,card=%d` | `sess opn cnf: 1x=%d,gw=%d,card_1=%d` + `card_2 %d` | **different `mmocmmgsdi.c`** |
| UIM/STK stack | *(absent)* | `mcfg_uim.c` ×9 (`mcfg_uim_autoselect NV=%d`, `Opening ext session with mask %X`, `Successfully open card ext sessions with %d session(s)`, `Open session %d slot_id %x session_id %x session_type %d`, `Got card event %d`, `ICCID 89918610400794228644`), `uimsub_manager.c` ×5, `uimgen.c` (`Terminal Profile, select df is 0x%`), `gstkutil.c` ×3 | **UZ801 runs a whole MCFG-UIM/STK bring-up that stock does not** |
| UIM failure | — | **`UIM_%d: Read to NV active slot configuration unsuccessful`** | **a UIM NV read FAILS on UZ801** |
| MMOC `Prot_state` | `0(NULL)` only | `0(NULL)` → **`7(OFFLINE)`** (×26) | **UZ801 is driven offline** |
| MMOC `Curr_trans` | `0/1/9/12` | `0/1/3(OFFLINE)/9/12` | |
| `policyman_serving_system.c ss precondition checks fail` | ×1 | — | stock at least *attempts* SS preconditions |
| `policyman_uim.c updating PLMN for Sub %d` | — | ×1 | UZ801 takes the UIM PLMN path |

### §7.1 The reading

UZ801's firmware carries a **different UIM/SIM-Toolkit bring-up** (`mcfg_uim.c` → `uimsub_manager.c`
→ `uimgen.c` → `gstkutil.c`), one of whose steps **fails** (`Read to NV active slot
configuration unsuccessful`). That bring-up is **not present in the stock HMU05 firmware at all**.
Its SD layer then activates the **`ssscr_user_offline_cdma`** user script, and its CM **never
reaches the serving-system layer** (`cmss.c = 0`, `cmlog.c = 0`).

**This corroborates Doc 202 §6.3's chain** — "UZ801's boot CM command arrives on `tsk=mmgsdi_1`
(from the UZ801-only UIM/STK bridge `nvruim.c`/`mcfg_uim.c`) instead of `tsk=cm`" — which had been
called into doubt when the fixed parser showed UZ801 *does* receive `0(SUBSCRIPTION_CHGD)`. Both
are true: UZ801 receives the command **and** runs the extra UIM/STK bridge; the bridge is what
stock lacks.

### §7.2 Gated step 1, executed from the captures already in hand

The `tsk` of every CM command in the two 240 s windows (`cmdbg.c CMD alloc/free … tsk=X, ftsk=cm`):

| `tsk` | stock | UZ801 v19 |
|---|---|---|
| `ds` | 25 | 43 |
| `dcc` | **0** | **8** |
| `mmgsdi_1` | 3 | 3 |
| `gstk` | 3 | 3 |
| `wms` | 2 | 0 |
| `gsdi` | 1 | 0 |
| `cm` | 1 | 2 |
| `qmi_mmode` | 0 | 2 |
| `pbm` | 0 | 2 |

**`mmgsdi_1` is 3 in *both*** — so Doc 202 §6.3's literal "on `tsk=mmgsdi_1` *instead of* `tsk=cm`"
is **not** supported by this window. What *is* supported is that UZ801 issues **`dcc` (8)**,
**`qmi_mmode` (2)** and **`pbm` (2)** commands that stock does not issue in the same window, and
stock issues **`wms` (2)** and **`gsdi` (1)** that UZ801 does not.

**The UZ801-only UIM/STK bring-up is a tight ~113 ms burst** (`code = 4774`,
`ts` 4 119 757 352 → 4 119 870 260), and **its first record is a FAILURE**:

```
uim.c:7136            UIM_%d: Read to NV active slot configuration unsuccessful   args=[2, 0, 0, 0]
mcfg_uim.c:318        mcfg_uim_autoselect NV=%d        args=[5], then [0], [0]
mcfg_uim.c:1019       Opening ext session with mask %X                       args=[16]
mcfg_uim.c:1555       Successfully open card ext sessions with %d session(s)  args=[1]
mcfg_uim.c:1561       Open session %d slot_id %x session_id %x session_type %d  args=[0, 1, 1037129215, 6]
mcfg_uim.c:1616       Slot %d session type %d id %x                          args=[0, 6, 1037129215]
cmph.c:26955          =CM= NV hybr pref set to %d                            args=[1]
uimsub_manager.c:643  Registering QMI sub manager callback pointer with UIMDRV
uimsub_manager.c:708  copying instance 0x%x data to sm global                args=[0,0,0], [1,0,0]
uimsub_manager.c:711  sub_mag Setting card status as unknown as hotswap is disabled
uimsub_manager.c:735  QMI syncing latest slot information
gstkutil.c:7740       ******** dumping array gstk_terminal_profile_cache ********
uimgen.c:3331         UIM_%d: Terminal Profile, select df is 0x%x            args=[1, 0, 0, 0]
mcfg_uim.c:1087       Got card event %x on slot index %x; sending cmd        args=[0, 0]
mcfg_uim.c:1287       ICCID 89918610400794228644
gstkutil.c:7740       ******** dumping array Open Channel TR ********  (×2)
```

**So the UIM/STK bring-up *runs and fails*: the very first step is `UIM_2: Read to NV active slot
configuration unsuccessful`, and `mcfg_uim_autoselect` then reads NV as `5`, `0`, `0`.** Note that
`subs 2` is the one that fails, and `policyman_rat_capability` had already reported the failure for
**subs 0/1/2** — the same three subscription ids.

---

## §8 The MMOC `OPRT_MODE_CHGD` decision

The cleanest single-event differential (delivery order, `num_args = 0`, so **untouched by the
parser defect**):

**stock** (`hmu05_stock_boot.bin`, idx 878):
```
=MMOC= Recvd command 2(OPRT_MODE_CHGD)
=MMOC= Curr_trans  0(NULL)
=MMOC= Trans_state 0(NULL)
=MMOC= Prot_state [MAIN] 0(NULL) -- [HYBR_GW] 0(NULL) -- [HYBR_HDR] 0(NULL)
=MMOC= Prot_state [MAIN] 0(NULL) -- [HYBR_GW] 0(NULL) -- [HYBR_GW3] 0(NULL)
=MMOC= Curr_trans  0(NULL)
=MMOC= Trans_state 0(NULL)
=MMOC= New transaction           : 2(ONLINE)          <-- ONLINE
=MMOC= Trans_state 6(WAIT_PH_STAT_CNF)
=MMOC= Recvd report 2(PH_STAT_CHGD_CNF)
```

**UZ801** (`boot_210_canon.bin`, idx 625):
```
=MMOC= Recvd command 2(OPRT_MODE_CHGD)
=MMOC= Curr_trans  0(NULL)
=MMOC= Trans_state 0(NULL)
=MMOC= Prot_state [MAIN] 0(NULL) -- [HYBR_GW] 0(NULL) -- [HYBR_HDR] 0(NULL)
=MMOC= Prot_state [MAIN] 0(NULL) -- [HYBR_GW] 0(NULL) -- [HYBR_GW3] 0(NULL)
=MMOC= Curr_trans  0(NULL)
=MMOC= Trans_state 0(NULL)
=MMOC= New transaction           : 3(OFFLINE)         <-- OFFLINE
=MMOC= Trans_state 25(WAIT_DEACTD_CNF_GWL)
=MMOC= Recvd report 0(PROT_DEACTD_CNF)
```

**Record-for-record identical up to and including the `Trans_state 0(NULL)` immediately before the
transaction; then opposite.** The command is the same; the *payload's op mode* is what MMOC acts
on, and CM's op mode is byte-identical on both (§3.2: `10 → 11`). So the decision is made **inside
MMOC**, on inputs that the CM log does not expose — most plausibly the **protocol/session state**
that UZ801's extra UIM/STK bring-up leaves in a different shape.

---

## §9 Evidence table

| artifact | md5 | size | what |
|---|---|---|---|
| `scratch/doc211/stock_boot_240.bin` | `0ff4e4d22c23443e2c6e82bcb24cbc83` | 1 500 441 | stock boot, restored instrument, 240 s, `admissible=yes` |
| `scratch/doc211/stock_boot_211.bin` | `e49515eff18afcf069215bde6a35a835` | 1 424 573 | stock boot, 90 s, `admissible=yes`, reaches the policyman block |
| `scratch/doc211/uz801_v19_boot_240.bin` | `81318b04f7e0da3e74675d2973366758` | 1 240 397 | UZ801 v19 boot, 240 s, `admissible=yes` |
| `scratch/doc211/live_stock_online.bin` | `e765744410d570e9eb0423bc46b58f9f` | 65 902 256 | live online-modem reference, 60 s |
| `scratch/doc211/live_stock_online.bin.gz` | `119085b9bb75ff4b18ad90e2e45bf0af` | 22 570 582 | gzip |
| `scratch/hmu05_stock_boot.bin` | *(archived)* | 94 MB | the online-transition reference |
| `scratch/doc210/boot_210_canon.bin` | *(archived)* | — | UZ801 boot canon (old instrument) |
| `scratch/f3parse.py` | `9f9d6edf15d64faf3b47897f7838c9ac` | — | **fixed** (args at `k+20+extra`) |
| `scratch/f3parse.py.pre211` | `59ccb3db69d084eae415dd940d7cdf1a` | — | pre-fix, provenance |
| `scratch/doc211/restore_uz801_v19.sh` | `51965239169cd8c048632f8678fef2d8` | 1 991 | UZ801 v19 restore (awk, not comm) |
| on-device `/root/diagboot_run.sh` | `9cd993a7f214b6847e92a7c21ca8c8c8` | — | instrument, `DIAGBOOT_SECS=240` |
| on-device `/overlay/fwbackup/uz801_v19_full.tar.gz` | — | 28 176 443 | UZ801 v19 archive (68-file manifest) |

---

## §10 Traps recorded this session

1. **`f3parse.py` defect #2** — the pad byte sits *before* the args; reading at `k+20` scales every
   arg by `256^extra`. ~2 % of records, but **all** stock `cmph.c mode_pref` records.
2. **`comm` is not in the OpenWrt busybox.** A script that aborts after extraction but before
   cleanup leaves a **partial firmware set**. Use `awk`.
3. **A doubled prefix in an F3 `fmt` is not necessarily a mis-detected pad** — `==MMOC= Found
   session` is a vendor typo present in both builds.
4. **`code` partitions the capture into independent `ts` clocks.** Never order F3 messages by `ts`
   across `code` values.
5. **A shared failure is not a differentiator.** The policyman RAT-mask-0 sequence is emitted by
   stock, which still reaches `online`.
6. **The stock 240 s boot does not reach the online transition** — `Prot_state` stays `0(NULL)`.
   The online transition needs the archived, longer capture.

---

## §11 Next steps

1. **DONE (§7.2):** the UZ801-only UIM/STK bring-up is a ~113 ms burst whose **first record fails**
   (`UIM_2: Read to NV active slot configuration unsuccessful`), and `mcfg_uim_autoselect` then reads NV
   as `5, 0, 0`. The failing subs id (**2**) is in the same `0/1/2` set policyman reports. **Next:** a
   **burst-vs-blind pair** to establish whether this burst is the *cause* or a *symptom*, and to see
   whether the `uim.c` NV read ever succeeds on UZ801.
2. **Gate:** explain the MMOC `OPRT_MODE_CHGD → 3(OFFLINE)` decision. The MMOC input that differs
   is not in the CM log; the next instrument must log the **MMOC command payload** (the op mode in
   `OPRT_MODE_CHGD`) or the MMOC session table.
3. **Do NOT** re-open: `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch,
   `0x1000001`, `cm_state+0x39e8`, "the CM code differs", the `mode_pref` 9728-vs-38 difference,
   the `MSC_AUTO` 0x220-vs-0xebe build difference, the policyman RAT-mask-0 lead.
4. **Do NOT** score a CM-layer negative on a capture with `rcinit_init.c = 0`.
5. **Do NOT** take an `args` value from any parser older than
   `9f9d6edf15d64faf3b47897f7838c9ac`.

---

## §12 Ledger fold-in

The following rows of `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §3 were updated in this session:
`mode_pref` differential → **RETRACTED**; `MSC_AUTO` mask build difference → **RETRACTED**;
policyman RAT-mask-0 lead → **RETRACTED**; new rows: the parser defect #2, the stock-online control,
the same-window differential, the UIM/STK bring-up, the `ssscr_user_offline_cdma` script, the MMOC
`OPRT_MODE_CHGD` differential, and the UZ801 v19 restore verification.

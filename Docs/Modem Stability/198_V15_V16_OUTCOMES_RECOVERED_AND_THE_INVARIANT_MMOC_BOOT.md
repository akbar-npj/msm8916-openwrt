# 198 — The v15/v16 outcomes, recovered: neither changed anything, and the MMOC boot burst is invariant v7 → v16

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick, OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`.
**Firmware under test:** UZ801 v3.2 "-21" baseband, patched `modem.b16` (v15, v16; v17 is current).
**Status:** RESULTS — the two unrecorded patch versions are now accounted for from their archived captures.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.

**Companions:** `194` (the UZ801 boot log), `195` (the patching campaign v1–v14), `196` (the DIAG bridge).

---

## 1. SOP compliance

Per the five-step protocol in `133 §1` (model: `194 §1`):

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **N/A — nothing was deployed or modified.** This doc only reads captures already on disk. |
| **Ground-truth verification against stock HMU05** | **Partial, and by signature rather than by a paired control.** The captures are confirmed **UZ801-based** (`mmocmmgsdi.c` present, `cmsoa.c` absent, `Prot_state 7(OFFLINE)`), which distinguishes them from a stock boot (`5(ONLINE_GWL)`), but no stock arm was captured in this session. |
| **Reconcile transport/config** | **Yes.** The F3 parser is the Doc-194 one; the result is checked at three parser windows (49 s / 195 s / 391 s) and is unchanged (§6.2). |
| **Surgical Hexagon patching + re-signing** | **NOT APPLICABLE — deliberately.** No byte of any image was modified. |
| **Live empirical validation** | **Partial.** The evidence is archived live captures, not a re-run. No new device capture was taken. |
| **No blind patching** | **Yes.** No patch is proposed. |
| **Irreversible actions declared first** | **N/A.** Nothing was changed on the device or on disk. |

**The one methodological caveat, stated up front:** the captures carry **no wall clock** — the F3 `ts` field is a
modem-internal tick with an unknown epoch (Doc 194 §4). Version attribution therefore rests on **filename +
mtime ordering relative to the `apply_v*.py` scripts**, not on an in-band marker. That ordering is tight and
consistent (§3), but it is inference, not proof. Nothing below depends on it for the *invariance* result.

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Recover what v15 did | a documented outcome | recovered: no observable change vs v14 (§4) | **MET** |
| Recover what v16 did | a documented outcome | recovered: no observable change vs v14; boot still `OFFLINE` (§5) | **MET** |
| v15's `DUAL_STANDBY_CHGD` suppression changes the boot | `Recvd command 7(DUAL_STANDBY_CHGD)` disappears | it is **still the first MMOC event** (§5.2) | **NOT MET** |
| v16's forced `mode = 5` + direct jump to `0xc0691974` moves MMOC | `Prot_state 5(ONLINE_GWL)` | `Prot_state 7(OFFLINE)`, 0 × `ONLINE_GWL` (§5.2) | **NOT MET** |
| Determine whether any patch v7→v16 changed the boot outcome | — | the MMOC boot burst is a **text-identical multiset** across all four (§6) | **UNEXPECTED (negative)** |

---

## 3. Why these captures are the v15/v16 runs

No v15/v16 result was written to a doc, and the device has **no shell history** and the boot log carries no QMI
record. The evidence is the archived host captures, and their mtimes bracket the version scripts exactly:

| time (host) | artifact | reads as |
| --: | :-- | :-- |
| 14:53 | `scratch/apply_v15_patches.py` | v15 authored |
| **14:58** | `scratch/diag_v15_online.bin` | **v15 runtime capture** |
| 15:18 | `scratch/apply_v16_patches.py` | v16 authored |
| 15:19 | `scratch/uz801_patched/modem.b01`, `modem.mdt` | v16 applied |
| **15:25** | `scratch/diag_v16_boot.bin` | v16 capture |
| **15:26** | `scratch/diag_v16_online.bin` | **v16 runtime capture** |
| **15:42** | `scratch/diag_boot_v16.bin` | **v16 boot capture** |
| 15:58 | `scratch/apply_v17_patches.py` | v17 authored |

Both v15 and v16 captures predate the v17 script by ≥ 16 min, and the v15 capture predates the v16 script.
All captures are distinct files (distinct md5), so none is a duplicate of another version's.

---

## 4. v15 outcome

`scratch/diag_v15_online.bin` — 383 messages, 17.3 s, **6 files**.

| observation | value |
| :-- | :-- |
| CM command path | `=CM= CMD alloc u=224, tsk=dcc` → `CMD free u=224, tsk=dcc, ftsk=cm` |
| `mmocdbg.c` | 0 lines |
| `cmmsc_auto.c` | 0 lines |
| any `oprt_mode` / `ONLINE` tag | 0 lines |

**This is the v14 shape exactly** (Doc 195 §12.4: `CMD alloc u=173, tsk=dcc` → free, no `cmmsc_auto.c`,
no `mmocdbg.c`). **v15's only new content — GROUP 12/13, suppressing `DUAL_STANDBY_CHGD` — produced no
observable runtime change.** The online request still enters CM on the `dcc` task and is freed without a
mode transition.

---

## 5. v16 outcome

### 5.1 Runtime — `scratch/diag_v16_online.bin` (4495 msgs, 203.1 s, 11 files)

| observation | value |
| :-- | :-- |
| CM command path | `=CM= CMD alloc u=260, tsk=dcc` → `CMD free u=260, tsk=dcc, ftsk=cm` (at +195 s) |
| `mmocdbg.c` / `cmmsc_auto.c` | 0 / 0 lines |
| files seen | `qmi_nas.c`, `mmgsdi_refresh.c`, `a2_sio.c`, `wms*.c`, heartbeats — **no CM/MMOC transition** |

Same as v14/v15. (The other v16 capture, `diag_v16_boot.bin`, is a **45 s post-boot runtime** capture — 8
files, no MMOC burst — despite its name.)

### 5.2 Boot — `scratch/diag_boot_v16.bin` (829 msgs, 15.7 s, 32 files)

The boot **does** reach MMOC, but it lands on the wrong state:

```
+0.037s  =MMOC= Recvd command 7(DUAL_STANDBY_CHGD)        <-- the v15/v16 suppression did NOT stop it
+0.037s  =MMOC= Prot_state [MAIN] 7(OFFLINE) -- [HYBR_GW] 0(NULL) -- [HYBR_HDR] 0(NULL)
+0.546s  [mmocmmgsdi.c:245]  =MMOC= Invalid session type=3
+0.546s  [mmocmmgsdi.c:1712] =MMOC= Invalid session, sess_type=3, ss=4 ,session id=0
```

| observation | value |
| :-- | :-- |
| `mmocdbg.c` | **60 lines** |
| `Prot_state [MAIN] 7(OFFLINE)` | **24** |
| `ONLINE_GWL` | **0** |
| `cmmsc_auto.c` | 0 lines |
| `DUAL_STANDBY_CHGD` received | **yes** (first MMOC event) |

**⇒ v16 did not achieve `online`.** The `DUAL_STANDBY_CHGD` command that Doc 195 §12.1.2 identifies as the
thing that drives MMOC to `OFFLINE` is **still received**, so GROUP 12/13 either targeted the wrong site or
did not take effect; and the forced `mode = 5` in the CM handlers (GROUP 4/5) never reached MMOC because
`cmmsc_auto.c` — the only path that would carry it — never runs.

---

## 6. The negative result: the boot outcome is invariant v7 → v16

### 6.1 Finding

Four archived boot captures, spanning nine patch versions, have **identical MMOC boot bursts**:

| capture | msgs | span | files | `mmocdbg.c` lines | `Prot_state 7(OFFLINE)` | `ONLINE_GWL` |
| :-- | --: | --: | --: | --: | --: | --: |
| `diag_boot_v7.bin` | 2545 | 90.3 s | 32 | 60 | 24 | 0 |
| `diag_boot_v8.bin` | 2472 | 90.4 s | 32 | 60 | 24 | 0 |
| `diag_boot_v13.bin` | 1900 | 63.8 s | 32 | 60 | 24 | 0 |
| `diag_boot_v16.bin` | 829 | 15.7 s | 32 | 60 | 24 | 0 |

The **mmocdbg message multiset is identical across all four** — same 10 distinct messages, same
multiplicities. Only the relative timing/order jitters. All four also share the UZ801 signature
(`mmocmmgsdi.c` present, `cmsoa.c` absent, no `rflte_*`, no `trm_*`).

### 6.2 Robustness

The result is independent of the parser's median-`ts` filter window: checked at **49 s, 195 s and 391 s**, the
multiset is identical at every setting. (This is the Doc-194 §4.1 false-positive guard, applied.)

### 6.3 What it means

**Every patch from v7 to v16 leaves the modem's boot-time MMOC sequence untouched: it always ends
`Prot_state [MAIN] 7(OFFLINE)`, driven by `DUAL_STANDBY_CHGD` plus an invalid MMGSDI session.** The boot
never reaches `ONLINE_GWL`. This is a much stronger statement than "v15/v16 didn't work": it says the
**boot path was never the lever the campaign was pulling**, which is consistent with Doc 195 §12.4's finding
that `cmmsc_auto.c` is never reached, and with Doc 194 §11's verdict that the gate is upstream of MMOC.

---

## 7. What this changes

1. **Doc 197 §4** is updated: v15 and v16 now have recorded outcomes (§4/§5 here).
2. **The next attempt should not be another CM/DMS/MMOC patch.** v7–v16 all leave the same boot signature;
   the gate is the one Doc 194 §11 names — the platform/HWID-derived device-config step that runs *before*
   MMOC. Two routes stand: (a) boot stock with the modem prevented from auto-onlining, to separate cause
   from consequence; (b) differential RE of the UZ801 boot path's RF/device-config gate.
3. **The scope guard is unchanged.** Reaching `online` would *arm* the LTE-gated ~900 s fatal clock
   (Doc 194 §11) — this is a porting milestone, not a stability fix.

---

## 8. Evidence inventory

| artifact | md5 | what it is |
| :-- | :-- | :-- |
| `scratch/diag_v15_online.bin` | `7999181cd93d76d321476f8f56e91046` | v15 runtime capture (383 msgs) |
| `scratch/diag_v16_boot.bin` | `7179c80ae838dcddb4b10a8ecf1a4a67` | v16 45 s post-boot runtime capture |
| `scratch/diag_v16_online.bin` | `c79423ec87b908f4281811c5a30c4e38` | v16 runtime capture (4495 msgs) |
| `scratch/diag_boot_v16.bin` | `0aec1e0a3e87841f68b7230f02071931` | v16 **boot** capture (829 msgs) |
| `scratch/diag_boot_v13.bin` | `934e10b19c472dc0460255ab9372e64e` | v13 boot capture (comparison) |
| `scratch/diag_boot_v8.bin` | `1e393ef3de2537ec02dd1f5a71dec0e7` | v8 boot capture (comparison) |
| `scratch/diag_boot_v7.bin` | `ea1dac6332d69634b4e964301333ef97` | v7 boot capture (comparison) |

Parsers: `scratch/f3parse.py`, `scratch/f3clean.py` (Doc 194 §4). `scratch/` is gitignored — these captures
live on the build host only; **do not delete them**, they are the only record of v15/v16.

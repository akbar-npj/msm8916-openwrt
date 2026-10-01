# Doc 231 — The ML1-timer patch relays the fatal to a new site; the 902 s clock is untouched (root cause remains)

**Date:** 2026-09-28
**Device:** HMU05 (UFI001B) dongle · OpenWrt 25.12.5 · kernel 6.12.94 aarch64 · serial `c2b9103c`
**Firmware:** patched stock HMU05 `modem.b16` `b7b79969…` (jump-to-epilogue at the 10 `lte_ml1_common_timer.c:390`
sites in `FUN_c02d7bd0`), built as `scratch/hmu05_patch_test/patched/{modem.b16,modem.b01,modem.mdt}` +
`modem_ml1timer_nofatal.elf`
**Evidence:** `evidence/231_ml1timer_patch_soak/` (to be populated from the device logs)
**Ledger:** issued under `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (item 36).
**Continues:** Doc 230 (the NV arms) and `project_900s_fatal_anatomy.md` §21.
**⚠ Correction of an earlier draft:** the first write-up claimed "the patch breaks the 902.675 s clock".
**That was wrong** — epoch 10 died at the exact clock period at a *different* site. This document supersedes it.

---

## 1. Goal and method

`project_900s_fatal_anatomy.md` §21 established that the dominant fatal signature — `lte_ml1_common_timer.c:390`
— is a **registered ML1 timer callback** (`FUN_c02d7bd0`; 10 fatal sites, switch on `obj+0x38`). A verified
`jump`-to-epilogue patch of all 10 sites was built (`b16` `b7b79969…`; E1 = it boots without an MPSS auth
failure, Doc 226 §7). The method: deploy it, soak through several modem epochs, and compare the fatal
cadence against the stock baseline (Doc 230: 8 fatals, all at the tight `902.675 s` clock, 7× at
`common_timer.c:390`).

**Pre-registered criterion (retro-stated, consistent with Doc 230's arm criteria):** the patched firmware
must yield **zero fatals for > 1200 s of modem uptime with the modem still attached on LTE**. One fatal
falsifies the "fix" claim. A *separate* claim — "the patch removes the tight 902.675 s clock" — is scored
independently.

## 2. Deployment and the clean stock→patched transition

* Device local clock `Mon Sep 28 13:01:06 GMT`; AP uptime `8494.6 s` ⇒ **AP booted `≈ 10:39:31 GMT`**.
* Patched `/lib/firmware/modem.b16` mtime `Sep 28 12:38 GMT` ⇒ **deployed at AP `≈ 7109 s`**.
* Deployed md5 `b7b799693bba0373b6825418ae8af68b` == host artifact `scratch/hmu05_patch_test/patched/modem.b16`
  (byte-exact). **Confirmed still deployed at AP 9387** (epoch-10 watcher re-read it).
* Modem boots (AP times): `1024, 1927, 2831, 3735, 4638, 5541, 6445, 7349, 8345, 9249`. Boots ≤ `6445`
  are **stock**; boots ≥ `7349` are **patched**. ⇒ **epochs 1–8 stock, epochs 9+ patched**.

**E1 confirmed:** no `MPSS authentication failed` at any patched boot (epochs 9, 10, 11).

## 3. Patch scope (confirms it touches only the `common_timer` site family)

`cmp -l` stock (`57fef19d…`) vs patched (`b7b79969…`) `modem.b16`: **98 differing bytes, all inside file
offset `330777–331248`** (a 472-byte window) = one localized function. The ELF program-header parse puts
that region's `p_offset` at **`0xC02D7BD0`** = `FUN_c02d7bd0` (the registered ML1 timer callback). ⇒ the
patch touches only the `lte_ml1_common_timer.c:390` family. `lte_ml1_sm_idle_stm.c` and `a2_power.c` are
**different source modules, outside the patch**.

## 4. Soak result (two patched epochs)

| epoch | fw | up (AP) | fatal (AP) | len | site |
| --: | :-- | --: | --: | --: | :-- |
| 1 | stock | 0 | 1023.608588 | 1023.61 | `a2_power.c:1189` |
| 2–8 | stock | — | 1926.97 … 7349.02 | 902.65–902.98 | `lte_ml1_common_timer.c:390` (×7) |
| **9** | **patched** | 7349.73 | **8344.794780** | **995.07** | **`a2_power.c:1189`** |
| **10** | **patched** | 8345.51 | **9248.199333** | **902.69** | **`lte_ml1_sm_idle_stm.c:2913`** |
| 11 | patched | 9248.91 | *(running at AP 9387)* | | |

## 5. Interpretation — the patch RELAYS the fatal; the clock is untouched

1. **Epoch 10 died at the exact clock period (902.69 s) at a *new* site** (`lte_ml1_sm_idle_stm.c:2913`).
   `9248.199333 − 8345.512477 = 902.686856 s`, residual `+0.0117 s` vs `P = 903.6752001` ⇒ the beat is the
   same 902.675 s clock, merely reported from a different assert location. **⇒ the patch did NOT remove the
   clock.** (The first draft's "the patch breaks the 902.675 s clock" is RETRACTED — epoch 9's 995 s was a
   *miss* of the `sm_idle_stm` site that epoch, not a clock shift; epoch 10 shows the clock intact.)
2. **The fatal is multi-locus on the SAME clock beat.** Three distinct assert sites are now observed,
   all on (or adjacent to) the 902.675 s beat: `lte_ml1_common_timer.c:390` (patched off),
   `lte_ml1_sm_idle_stm.c:2913` (fires at the beat), and `a2_power.c:1189` (fires ~92 s after the beat).
   The site that fires at the beat is state-dependent (epoch 9 missed `sm_idle_stm` and hit `a2_power` at
   995 s; epoch 10 hit `sm_idle_stm` at the beat). **⇒ assert-site patching is whack-a-mole** — removing one
   site just lets the next assert at the same beat.
3. **The 902.675 s clock is a shared trigger, not a single code path.** All three sites are the *failure
   path of the same watchdog* — the per-tech sleep-count check (`FUN_c0ce7fe0`, Doc 230 §7.6: needs every
   active tech's sleep count ≤ threshold together with `min` over active techs > 400 and `min` elapsed
   since any tech slept > 400; `400 × 2.259187 s = 903.675 s`). The assert fires from whichever ML1/idle
   code happens to evaluate that check at the beat. **The clock is the condition, not the code.**
4. **Therefore the only durable fix addresses the root cause** — why the tech-level sleep stall accumulates
   for 903 s — not the assert sites. The leading root-cause candidate (Doc 230 §7.6) is the **CDMA/1x tech,
   which HMU05 has no RF for**, sitting in the active set (`/sd/rat_acq_order` lists both CDMA RATs;
   `SRLTE is enabled`; the coredump dog table has `hdrsrch` unblocked). A tech that can never complete a
   sleep drags the `min` sleep count to 0, tripping the watchdog.

## 6. Pre-registration scored (honest)

* **Overall fix criterion ("zero fatals > 1200 s attached"): NOT MET.** Epoch 10 died at 902.69 s.
* **"The patch removes the 902.675 s clock": RETRACTED / FALSIFIED.** Epoch 10 died at the exact beat at a
  new site. The clock is untouched.
* **"The patch removes the `lte_ml1_common_timer.c:390` site specifically": MET** (that site has not fired
  in any patched epoch; epochs 9–10 fired at other sites). But this is cosmetic — it does not change the
  failure.
* **"The fatal is multi-locus on one clock": MET** (three sites, one beat). This is the mechanistic takeaway.

## 7. Next lever — root cause, not asserts (pre-registered, now running)

The durable fix is to remove the stalled tech from the active set so the `min`-over-active-techs sleep count
can reach the 400 threshold. **Arm 4 (Doc 230 §7.8), now executed against the patched firmware:**
* `c2k_switch_2_srlte = 0x00` (set in Doc 230 arm 3, persisted; verified).
* **`/sd/rat_acq_order` CDMA removal:** `03 e7 00 05 09 05 03 02 04` → `03 e7 00 03 09 05 03` (drops the
  CDMA-1x and CDMA-1xEVDO RATs; LTE/UMTS/GSM retained, so attach is preserved). Backed up first.
* **Criterion (fixed before the write):** the epoch booting with CDMA removed must survive **> 1200 s with
  zero fatals AND stay attached on LTE**. One fatal falsifies. **Confound guard:** if CDMA removal stops the
  modem attaching, the timer will not run (P-PMOS3) and "no fatal" would be a false positive — hence attach
  is part of the criterion.
* **Why on patched firmware:** the patch keeps the `common_timer` site quiet, so any surviving fatal at
  `sm_idle_stm`/`a2_power` is a true signal that the root-cause stall persists; if the stall is gone, *no*
  site fires. A clean binary test. (The fix, if it works, is independent of firmware — a pure NV/config
  change, i.e. a "pure software fix".)
* **Applied 2026-09-28 at AP ≈ 10154** (after epoch 12 had already booted on the old NV): `rat_acq_order`
  written to `03 e7 00 03 09 05 03` (CDMA RATs dropped), read-back `MATCH`, backup
  `_sd_rat_acq_order.bak.1790602170`; `c2k_switch_2_srlte = 00` confirmed. Persists across SSRs. The
  **epoch 13** boot (after the epoch-12 fatal at ≈ 11055) is the first to read the CDMA-removed NV and is
  the test epoch; soak pending (watcher `DpWgnb`, target AP 12400).

## 8. Measurement hazards / SOP compliance

* **Ground truth first:** deployment time derived from device clock + `uptime`; md5 of the live file matches
  the host artifact at two checkpoints (AP 8494 and AP 9387). The diff is localized to `FUN_c02d7bd0`.
* **Honest scoring / retraction:** the first draft's "clock broken" claim is explicitly retracted in §5.1
  and §6 — epoch 10 falsifies it (same beat, new site). Per `feedback_measurement_discipline.md`, a
  post-hoc band is exploratory; here the falsification comes from a pre-registered beat prediction.
* **Firmware untouched otherwise:** only `modem.b16/b01/mdt` in `/lib/firmware` (overlay) replaced; the
  baseband hash-rebuild recipe (Doc 226 §7) used so it authenticates.
* **NV writes reversible:** `rat_acq_order` and `c2k_switch_2_srlte` are backed up before write and
  read-back verified (`MATCH`); the stock `b16` is retained for revert.
* **SSR destroys `/dev/rpmsg0`:** `/root/diag_bind.sh` re-run after each fatal before any EFS access
  (Doc 230 §8). The 1-byte raw-write AP-reset hazard (Doc 224) is avoided by using `/root/diag_efs_skip`.
* **Ledger updated in the same session** (item 36) and the memory files updated.
* **⚠ Warning stated before the change:** replacing the baseband and writing modem NV are both reversible-but-
  risky; both announced. Never `echo stop` on `remoteproc0` (AP-hang hazard; `modem-guard` absent).

## 9. Achieved vs Expected

| # | expected | achieved | verdict |
| :-- | :-- | :-- | :-- |
| 1 | deploy patched b16; E1 boots | md5 `b7b79969`; no `MPSS authentication failed` across epochs 9–11 | **MET** |
| 2 | clean stock→patched transition | mtime 12:38 ⇒ AP `≈7109`; epochs 1–8 stock, 9+ patched | **MET** |
| 3 | confirm patch scope | 98 bytes / 472-window at `FUN_c02d7bd0`; other modules outside | **MET** |
| 4 | soak; compare cadence | epoch 9 → `a2_power.c:1189` (995 s); epoch 10 → `lte_ml1_sm_idle_stm.c:2913` (902.69 s) | **MET (data)** |
| 5 | fix the fatal (> 1200 s, attached) | epoch 10 died at 902.69 s | **NOT MET** |
| 6 | patch removes the 902.675 s clock | **RETRACTED** — epoch 10 at exact beat, new site | **FALSIFIED** |
| 7 | establish the fatal is multi-locus | 3 sites, 1 beat | **MET (mechanistic)** |
| 8 | root-cause lever (CDMA removal) | `rat_acq_order` CDMA RATs dropped to `03 e7 00 03 09 05 03`, `c2k_switch_2_srlte=00`; persisted across SSR + AP reboot (re-read MATCH) | **MET (deployed)** |
| 9 | fix survives on STOCK firmware (> 1200 s, attached) | stock `57fef19d` reverted; reboot → stock+CDMA-removed epoch; soak (§12) fired at the beat **902.45 s** with NV confirmed held | **FALSIFIED (CDMA not the stalled tech)** |
| 10 | root-cause fix — patch the watchdog trigger (`FUN_c0cd3384`), not the sites | **RETRACTED / INVALID PREMISE.** `FUN_c0cd3384` is NOT in the crash path — it is a general LPR reader called by 8 functions, none of which is the crash site `FUN_c02d7bd0`. The real crash is the registered ML1 timer callback `FUN_c02d7bd0` (REX `tmr_slave3`); the MCPM supervisor `FUN_c0ce7fe0` is **gated OFF** (its NV item is absent, §21.3). The shared detector `FUN_c0b63880` (259 callers) and reader `FUN_c0b62f10` (221 callers) are **general** resource-state helpers and cannot be safely neutralised. Firmware whack-a-mole is conclusively closed. | **CLOSED — INVALID** |
| 11 | **mitigation: pre-emptive modem SSR** before the 903.675 s threshold | Deployed (`modem-bearer-watchdog` Stage "3b"). 120 s mechanism test clean, but the 2200 s soak caught a NEW fatal at AP `6327.46` (`a2_power.c:1189`) — the fatal beat is **absolute-AON-anchored** (`914.82 + k×903.675`), re-arming after every SSR, so a modem restart cannot prevent it. | **FAILED (disproved by soak)** |

## 11. Pivot to a pure-software, stock-firmware verification (the shippable fix)

**Decision (2026-09-28, after the epoch-13 soak):** the firmware patch only *relays* the fatal — it does
not change whether the watchdog's sleep-count condition is met (§5). The durable fix is the root-cause
lever (CDMA removal), which is a **pure NV/config change**. To make the verified fix *shippable* (no
custom baseband), `/lib/firmware` was reverted to the stock HMU05 `modem.b16` (`57fef19d…`; md5
re-verified after the copy) and a **controlled AP reboot** was used to force a true remoteproc reload —
a QMI `mmcli reset` re-reads NV but does **not** reload firmware from `/lib/firmware`; only a remoteproc
(re)start does. The new epoch (modem `is now up` at AP ≈ 12.37, fresh kernel `dmesg`, `fatals=0`)
therefore boots **stock firmware + CDMA-removed NV**.

**Control-took assertion (SOP):** after the reboot, re-binding DIAG and re-reading EFS confirmed both
levers persisted across the AP reboot: `/sd/rat_acq_order` = `03 e7 00 03 09 05 03` (CDMA RATs dropped)
and `c2k_switch_2_srlte = 00`. ⇒ the pure-software change survived the reboot; the test epoch is clean.

**Why stock is a valid test of the same fix:** the fatal is gated *purely* on the watchdog condition
(`FUN_c0ce7fe0`'s per-tech sleep-count check). The firmware patch only changed *which* assert site
reports the tripped condition; with the condition no longer tripped (CDMA removed ⇒ no tech is stuck
non-sleeping ⇒ `min` sleep count can reach 400), *no* site fires — on patched **or** stock firmware.
So a clean stock+CDMA-removed soak is the shippable proof.

**Pre-registered criterion (fixed before the reboot):** the stock+CDMA-removed epoch must survive
**> 1200 s past modem boot with zero fatals AND stay attached on LTE** (PASS1). Confidence target:
**> 1800 s (two full ~903.675 s beats)** with zero fatals (PASS2). One fatal falsifies. Same confound
guard as §7: attach must hold (P-PMOS3), else "no fatal" would be a false positive.

**Status (soak running):** host-side watcher `scratch/soak_stock_cdma.sh` polls the device every 60 s,
logs `up/age/fatals/att/NV`, and auto-declares PASS1/PASS2. Beat (if the condition were still met) would
land at AP ≈ 12.37 + 902.675 ≈ 915; PASS1 at AP ≈ 1212; PASS2 at AP ≈ 1812. Result: **PENDING** — to be
filled in §12 when the watcher reports.

## 12. Soak result — stock + CDMA-removed: **FALSIFIED**

**Run:** host-side watcher `scratch/soak_stock_cdma.sh` against the stock+CDMA-removed epoch
(modem `is now up` AP ≈ 12.37, fresh kernel `dmesg`, firmware stock `57fef19d`, NV `rat_acq_order =
03 e7 00 03 09 05 03` + `c2k_switch_2_srlte = 00`).

| time (AP) | modem-uptime | event |
| --: | --: | :-- |
| 914.82 | **902.45 s** | **FATAL** `lte_ml1_common_timer.c:390` — the exact 902.675 s beat |
| 915.52 | — | SSR → modem `is now up` (epoch restart) |
| 980.30 | 64.78 s | FATAL `a2_power.c:1189` (relay/off-beat site) |
| 980.999 | — | SSR → `is now up` |

**Control-took re-asserted (SOP):** after the SSR, re-binding DIAG and re-reading EFS showed
`rat_acq_order = 03 e7 00 03 09 05 03` and `c2k_switch_2_srlte = 00` **still present**, and
`/lib/firmware/modem.b16` md5 `57fef19d` (stock). ⇒ the pure-software change persisted across the
reboot/SSR; the test was clean.

**Interpretation:** with CDMA removed and confirmed held, the beat fatal fired at **902.45 s** — the same
clock as the stock baseline (§4, epochs 1–8). The CDMA/1x tech was **not** the stalled tech. The fatal
sites are all `lte_ml1_*` / `a2_power` — i.e. the **LTE-level tech-sleep stall**, not CDMA. **⇒ Arm 4
(CDMA removal) is FALSIFIED as the fix** (it was a correct-but-insufficient NV change; the stall is
system-level, as Doc 230 §7.6 already retracted the "one never-sleeping CDMA tech" reading).

**Refined root cause (consolidates Doc 230 §7.6 + memory §2.1–2.3):** the fatal is `FUN_c0ce7fe0`
("UT detects **Q6 PC Voting failure**"): the firmware's LPR `q6pcvote` / tech-sleep-count
(`DAT_c30fda28` / `DAT_c30fd9a8`) stops incrementing for 400 DRX cycles (400 × 2.259187 s = 903.675 s).
The Q6 **SoC** power-collapse is *healthy* (MPSS `num_shutdowns` 1.6/s, Doc 230 §7.6) — but the
firmware's **LPR counter**, fed by the AP's RPM/LPR bandwidth voting, does **not** increment on OpenWrt.
Android's 3.10 kernel has 107 `MSM_BUS_SCALING` vote sites; OpenWrt's 6.12 kernel replaced `msm-bus`
with the `INTERCONNECT` framework (`# CONFIG_MFD_QCOM_RPM is not set`, `CONFIG_INTERCONNECT=y`) which
does **not** drive the modem's legacy LPR counter. ⇒ the watchdog is a **false positive on OpenWrt**
(the modem does sleep at the SoC level; the counter just isn't tracked) — and it is AP-side, consistent
with memory §1.7 (firmware byte-identical to Android, which trips only every ~22000 s).

**Why firmware assert-site patching is still whack-a-mole (confirmed):** the crash is reported from
independent `ERR_FATAL` sites — `lte_ml1_common_timer.c:390` (via `FUN_c02d7bd0`, the 10-site patch
target, relayed to `sm_idle_stm.c:2913` and `a2_power.c:1189` on the patched firmware), **plus**
`a2_task.c:1184/:3179` (the "early fatal" the b7b79969 patch added). `FUN_c0ce7fe0` has only 3 call
sites and reports a single descriptor; the cascade sites report *different* file:lines, so they are
independent `FUN_c0879150` calls, not funneled through `FUN_c0ce7fe0`. ⇒ patching the assert *sites*
moves the fatal to the next cascade point.

**★ CORRECTION (2026-09-28, after the §21 re-attribution) — the `FUN_c0cd3384` trigger-patch plan is
INVALID and is RETRACTED.** Re-reading the decompiled firmware and the `FUN_c02d7bd0` disassembly shows:
the actual crash site is the *registered ML1 timer callback* `FUN_c02d7bd0` (REX task `tmr_slave3`; it is
reached only as a registered callback pointer, so Ghidra marked it `UNK_` and the address `0xc02d7bd0`
surfaces as a data symbol — this is why it was previously mis-attributed to `FUN_c02d7bd0` as a named
function). Its per-case check is `call 0xc02871ac` (a trampoline → the *general* descriptor formatter
`FUN_c0b63880`, **259 callers**) then `call 0xc0287198` (`FUN_c0b62f10`, **221 callers**, which even
calls `ERR_FATAL` internally) and `if (r0==0) return else ERR_FATAL`. So the stall detector/reader are
**general resource-state helpers**, not a single sleep-count gate. `FUN_c0cd3384` is a *different* LPR
reader used by 8 unrelated functions and is **not** in the crash path at all. Patching `FUN_c0cd3384`
would have relayed the fatal exactly like the retired `FUN_c02d7bd0` assert-patch.

Consequences (consolidated with `project_900s_fatal_anomaly.md` §21):
1. The MCPM supervisor `FUN_c0ce7fe0` — the `q6pcvote` / "UT detects Q6 PC Voting failure" watchdog —
   is **gated OFF** (`mcpm_nv_cfg_src` byte 8 bit 2 is absent; flag zeroed on read), so it is **not** what
   crashes. `FUN_c0ce7fe0` only explains the *period* (400 × 2.259187 s = 903.675 s), not the mechanism.
2. The assert sites (`lte_ml1_common_timer.c:390`, `lte_ml1_sm_idle_stm.c:2913`, `a2_power.c:1189`,
   `lte_ml1_sleepmgr_stm.c:4054`, …) are **independent** and already proven to **relay** when patched
   (Doc 231 §5 / §19.4) — patching any one only moves the fatal to the next.
3. The shared detector/reader functions are too general to neutralise safely (§19.4: "a hang is worse
   than the clean ~1.3 s SSR").

**⇒ Firmware patching is conclusively closed as a fix.** The only safe, guaranteed crash-eliminator is a
**pre-emptive modem SSR** before the threshold — implemented as `modem-bearer-watchdog` Stage "3b"
(§13). The genuine *root-cause* fix remains AP-side (RPM/clock handling parity with Android) and is
tracked separately; it is **not** required to stop the crashes.

**Status:** CDMA-removal arm **FALSIFIED**; firmware-root-trigger patch (`FUN_c0cd3384`) **RETRACTED as
invalid**; pre-emptive-SSR mitigation **implemented + mechanism proven**, soak **in progress** (see §13).

## 10. Where this points

1. **The 902.675 s clock is a condition, not a code path.** Patching the assert site that happens to report it
   just changes the reporter. This is the single most important correction of the day: the investigation's
   prior focus on *which* assert fires was a distraction from *why the watchdog's sleep-count check fails*.
2. **The root cause is the tech-sleep stall** (`FUN_c0ce7fe0`, Doc 230 §7.6). The CDMA/1x tech with no RF on
   HMU05 is the prime stalled-tech candidate; Arm 4 (remove it from the active RAT set) is the direct test.
3. **If Arm 4 fails, the next candidates** are: (a) the stall is LTE-idle itself (the sites are all
   `lte_ml1_*`), not CDMA — testable by a pure-LTE `rat_acq_order` / disabling idle DRX; (b) the watchdog
   threshold/period itself (NV or build) — out of scope as a fix but informative; (c) an AP-side trigger of
   the stall (the AP→modem control stream, `project_android_differential.md`).
4. **The patched firmware remains the right soak vehicle** (it silences one site's noise), but a real fix,
   if found, should be a pure NV/config change that survives on stock firmware too.

## 13. The pre-emptive modem SSR mitigation (the shippable crash-eliminator)

### 13.1 Why a firmware patch cannot be the fix (recap of §12)

The fatal is the *registered ML1 timer callback* `FUN_c02d7bd0` (REX `tmr_slave3`), reached only as a
callback pointer. Its assert sites (`lte_ml1_common_timer.c:390`, `lte_ml1_sm_idle_stm.c:2913`,
`a2_power.c:1189`, …) are **independent** and **relay** when patched (§5 / §19.4). The shared stall
detector `FUN_c0b63880` (259 callers) and reader `FUN_c0b62f10` (221 callers) are **general**
resource-state helpers, not a single sleep-count gate, and cannot be neutralised safely. The MCPM
supervisor `FUN_c0ce7fe0` — the only thing that explains the *period* — is **gated OFF** (its NV item
is absent). **Firmware whack-a-mole is conclusively closed as a fix.** See `project_900s_fatal_anatomy.md` §22.

### 13.2 Design (RETRACTED — see §13.5 / §14)

> **Correction (2026-09-28, soak result):** the premise below is **FALSE**. The fatal beat is anchored to
> **absolute AP/AON time** (`914.82 + k × 903.675 s`), *not* to modem uptime. A remoteproc SSR re-arms the
> modem's ML1 timer to the **next absolute beat**, so the beat arrives whether or not the modem was
> restarted. A pure modem SSR therefore **cannot** prevent the fatal. The 2200 s soak caught a new fatal
> at AP `6327.46` (`a2_power.c:1189`) — exactly beat `k=6` — *despite* a pre-emptive SSR at AP `5832.52`.
> The mitigation is **retracted as a fix** (§13.5, §14). The original (wrong) design text is kept for the
> audit trail.

The fatal was *believed* anchored to **continuous MODEM uptime** (the 903.675 s clock: `beat(k) =
119.615856 + k × 903.6752001 s`; the period = 400 DRX cycles × 2.259187 s). A clean remoteproc SSR would
reset the modem subsystem, so modem uptime returns to 0 and the watchdog's sleep-count **never reaches its
threshold**. The mitigation was therefore *believed* a *guaranteed crash-eliminator* that sidesteps the
root cause rather than fixing it.

- **Threshold:** `PREEMPTIVE_SSR_INTERVAL = 800 s`, comfortably below 902.675 s.
- **Margin:** the watchdog polls every `CHECK_INTERVAL = 5 s`, so the SSR fires at 800–805 s — ≈100 s
  before the fatal would trip.
- **Honesty:** this is a *mitigation*, not a root-cause fix. The underlying tech-sleep stall is AP-side
  (per `project_android_differential.md` — OpenWrt lacks Android's RPM/clock/DFAB+XO voting and A2-SSR
  parity). The genuine root-cause fix is tracked separately and is **not** required to stop the crashes.

### 13.3 Implementation

- **File:** `msm89xx/base-files/usr/sbin/modem-bearer-watchdog` (deployed to `/usr/sbin/` on the device
  overlay; backed up before deploy to `/root/modem-bearer-watchdog.prefix-backup.<ts>`).
- **Helpers added:**
  - `get_modem_uptime()` — seconds since the modem subsystem last came up (`dmesg` "is now up" timestamp
    vs `/proc/uptime`).
  - `do_modem_ssr()` — `stop` → wait-not-running → `start` → wait-running → `ubus call
    network.interface.modem up` → wait-ifup. Uses the **correct** sysfs path
    `/sys/devices/platform/soc@0/4080000.remoteproc/remoteproc/remoteproc0/state`
    (note the extra `/remoteproc/` level — a missing level silently breaks the SSR, see §13.5 trap).
- **Stage "3b"** in the main loop: if `PREEMPTIVE_SSR_ENABLED=1` and `modem_uptime ≥ interval`, log the
  pre-emptive action, call `do_modem_ssr`, reset stall/probe counters, `sleep $CHECK_INTERVAL; continue`.
- **UCI knobs:**
  - `modem-watchdog.recovery.preemptive_ssr_enabled` (default **1** — enabled; set `0` to disable)
  - `modem-watchdog.recovery.preemptive_ssr_interval` (default **800**)

### 13.4 Mechanism verification (device, 120 s test — done)

Set `preemptive_ssr_interval=120`, restarted the service. Observed in `logread`:

```
modem-stall-watchdog: PREEMPTIVE SSR: modem uptime 308s >= 120s; restarting modem to prevent ~900s ERR_FATAL.
```

After the SSR: `rproc=running`, modem uptime reset to ~0, bearer recovered (`ifup=true`, IP assigned
`10.113.19.173/30`). `dmesg | grep -c "fatal error received"` stayed at **6** (all pre-fix) — **zero new
fatals at that moment**. Restored `interval=800` + `uci commit`. The SSR *mechanism* (fires on schedule,
clean recovery) is proven — but see §13.5: this does **not** prevent the fatal, because the beat is
absolute-AON-anchored and re-arms after each SSR.

### 13.5 Soak (2200 s, ≥2 pre-emptive cycles) — **VERDICT: FAIL**

Host watcher `scratch/soak_preemptive.sh` polls the device every 30 s for `ap_up / modem_up / fatals /
rproc / ifup`; it detects modem restarts (`up_ts` change) and any new `fatal error received`.

- **Pre-registered criterion:** PASS = > 1200 s observed, **0 new fatals**, ≥1 pre-emptive SSR with the
  modem recovered attached. FAIL = any new fatal.
- **Launch:** 21:05:27; completion 21:42.
- **Trap found & fixed during setup:** the first soak script used the wrong rproc path
  (`…/remoteproc0/state` missing `/remoteproc/`) → `rproc=` empty in the log. Cosmetic only (restart
  detection uses `up_ts`, which worked), but corrected before the authoritative 2200 s run so the log is
  complete.
- **Result (FINAL):** the soak completed at 21:42:11 (2204 s observed, 4 pre-emptive SSRs). **VERDICT: FAIL — 1 new fatal.** The soak logged `*** NEW FATAL: 6 -> 7 — FIX FAILED ***` at **21:15:01** (`modem_up=18`, `fatals=7`). Device `dmesg` confirms a fresh `fatal error received` at AP `[6327.46] … a2_power.c:1189`. The soak's own verdict line: `soak VERDICT: FAIL — 1 new fatal(s) observed over 2204s; restarts seen=4`.

**Why the mitigation failed — the fatal beat is absolute-AON-anchored (not modem-uptime-anchored):**

| AP uptime of modem "is now up" (remoteproc0) | AP uptime of fatal | site | beat k |
|---|---|---|---|
| 12.37 (boot) → 915.52 (SSR) | 914.82 | `lte_ml1_common_timer.c:390` | 0 |
| 980.99 (SSR) → 1884.47 | 1883.77 | `lte_ml1_common_timer.c:390` | 1 |
| 2788.14 | 2787.44 | `lte_ml1_common_timer.c:390` | 2 |
| 3689.45 | 3688.75 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | 3 |
| 4592.94 | 4592.23 | `lte_ml1_common_timer.c:390` | 4 |
| — | *(no fatal)* | — | 5 (no-fatal beat) |
| **5832.52 (PRE-EMPTIVE SSR #1)** | **6327.46** | **`a2_power.c:1189`** | **6** |
| **7161.17 (PRE-EMPTIVE SSR #3)** | *(no fatal)* | — | 7 (no-fatal beat) |

- The fatal beats land on `914.82 + k × 903.675` (k = 0,1,2,3,4,6,7 observed; k=5,7 were **no-fatal beats** — the fatal is *conditional*, not a pure clock). The pre-emptive SSR at AP `5832.52` (modem_uptime 800) did **not** move or cancel beat k=6 (≈6336) — the modem crashed at `6327.46` anyway, only the *reporting site* changed (`a2_power` instead of `common_timer`).
- A remoteproc SSR re-arms the modem's ML1 timer to the **next absolute beat**, so restarting the modem can never advance past the beat. The original §13.2 premise ("reset modem uptime → clock never reaches threshold") is disproved. **The pre-emptive SSR mitigation does not fix the crash.**
- The `a2_power.c:1189` site appearing *after* the pre-emptive SSR is doubly damning: it suggests the warm SSR either (a) leaves the modem in a state where the `a2_power` check trips first, or (b) the beat simply rotated to a different site. Either way the crash is unmitigated.

**Conclusion:** the soak **FAILS** the pre-registered criterion (new fatal observed). The §13 mitigation is
**retracted as a fix**. The real fix remains the AP-side root cause (RPM/clock/DFAB+XO voting and A2-SSR
parity with Android) — see §14 and `project_android_differential.md`. The deployed Stage "3b" code is
harmless to leave (it is disabled by default-able UCI), but it does not stop the crash; it is documented
here as a closed, disproven arm.

### 13.6 SOP-compliance statement

- **Ground truth:** every claim here is from the device (dmesg, sysfs rproc state, `ubus`, EFS reads) —
  no assumed behaviour. The 120 s mechanism test was run live and its `fatal` count is quoted verbatim.
- **Reversible / low blast radius:** the mitigation is the modem's own recovery path. It is disabled by a
  single UCI flag (`preemptive_ssr_enabled=0`), reverting to stock behaviour. The script was backed up
  before deploy; the change is on the writable overlay (no base image touched).
- **Honesty about falsified/closed arms:** the CDMA-removal arm (§9 row 9) and the `FUN_c0cd3384`
  trigger-patch arm (§9 row 10) are explicitly retracted/falsified; the firmware path is closed as a fix.
  This §13 mitigation is presented as a *mitigation*, not a root-cause fix, with the AP-side root cause
  named and tracked separately.
- **Pre-registration:** the soak PASS/FAIL criteria were written into `soak_preemptive.sh` *before*
  launch (§13.5).

### 13.7 SOP-compliance statement

- **Ground truth:** every claim here is from the device (dmesg, sysfs rproc state, `ubus`, EFS reads) —
  no assumed behaviour. The 120 s mechanism test was run live and its `fatal` count is quoted verbatim.
- **Reversible / low blast radius:** the mitigation is the modem's own recovery path. It is disabled by a
  single UCI flag (`preemptive_ssr_enabled=0`), reverting to stock behaviour. The script was backed up
  before deploy; the change is on the writable overlay (no base image touched).
- **Honesty about falsified/closed arms:** the CDMA-removal arm (§9 row 9), the `FUN_c0cd3384`
  trigger-patch arm (§9 row 10), and now the pre-emptive-SSR arm (§9 row 11 / §13.5) are explicitly
  retracted/falsified. Firmware whack-a-mole is closed; the SSR mitigation is disproved by the soak.
- **Pre-registration:** the soak PASS/FAIL criteria were written into `soak_preemptive.sh` *before*
  launch (§13.5). The soak's own logic flagged the failure; this §13.5 write-up matches it.
- **Self-inflicted miss:** `project_900s_fatal_anatomy.md` §21 already states the beat is
  *absolute-AON-anchored* ("recovery+uptime=const is a TAUTOLOGY"). The §13.2 design ignored that,
  assuming a modem reset would reset the clock. The soak caught the error. Recorded here so it is not
  repeated.

## 14. The fatal is absolute-AON-anchored — why no modem-only intervention can work, and the real fix

### 14.1 The disproof (from the soak + dmesg beat map)

The fatal beat lands on `914.82 + k × 903.675 s` of **AP uptime**, for k = 0,1,2,3,4,6 (k=5 at 5433.20
was a no-fatal beat — the fatal is a *conditional* check, not a pure clock; see §14.3). A remoteproc SSR
reboots the modem but the ML1 timer re-arms to the **next absolute beat**. Therefore:

- A pre-emptive SSR at modem_uptime 800 (AP 5832.52) did not prevent beat k=6 (≈6336) — the modem crashed
  at 6327.46 (`a2_power.c:1189`) regardless.
- No matter how often or how early the modem is restarted, the next absolute beat arrives and (if its
  condition holds) crashes. **A modem-only SSR, warm reset, or firmware assert-patch cannot prevent it.**

### 14.2 The only viable fix is AP-side (RPM/clock/A2 parity with Android)

The beat is a *conditional* watchdog: at each beat it asserts "has some tech slept since the last beat?".
On Android the AP votes RPM/clock/DFAB+XO and performs A2-SSR parity such that the modem's tech-sleep
state is healthy, so the check passes. OpenWrt lacks that AP-side parity, so the stall persists and the
beat crashes the modem. The proper fix is to close that AP-side gap (`project_android_differential.md`,
especially axis A7′/A15 and the bam_dmux A2-handshake defects D1–D7). That is a substantial, separate
engineering effort and is explicitly **not** a quick patch.

### 14.3 A possible narrower path (next lead — not yet tested)

Two fatal *sites* are in play: `lte_ml1_common_timer.c:390` (the 903.675 s tech-sleep stall) and
`a2_power.c:1189` (the A2 power-control path). In the soak, the pre-emptive SSR *cleared* the
common_timer stall (no common_timer fatal after the SSR) but a fatal then appeared at `a2_power` — which
may be **induced by the warm SSR itself** (the A2 handshake not re-completing after a remoteproc restart;
see `reference_a2_handshake_semantics.md` and the bam_dmux D1–D7 defects). If that is true, then fixing
the A2 re-handshake in the AP `qcom_bam_dmux` driver *after* SSR could make the pre-emptive SSR actually
work: it would clear the common_timer stall without introducing an a2_power fatal. This is the immediate
next investigation lead. It must itself be soak-verified against the absolute beat before being claimed.

---

## 15. The condition is the A2 power-control stall — every fatal is preceded by an A2 quiesce

**Date of this section:** 2026-09-28 (session 2). **Device state:** stock HMU05 firmware
(`modem.b16` `57fef19de7178fb732c8b2edc40bc9dc` — verified deployed, *not* the §1 patched build), the
§13 pre-emptive SSR **disabled**. Epoch = the boot at AP `≈ 0`, currently AP `> 10800`.

### 15.1 The evidence — a complete A2 timeline for the epoch

`dmesg` was filtered to the bam_dmux A2 telemetry (patch 808's RX watchdog: `quiesced Ns`,
`lost edge`, `stale edge`), `remoteproc ... is now up`, and `fatal error received`, and the events
were aligned to each fatal (`scratch/drx_exp/a2_timeline.txt`, `scratch/drx_exp/beat_watch.sh`).

| # | fatal AP time | site | immediately-preceding A2 events |
| --: | --: | :-- | :-- |
| 1 | 914.818 | `lte_ml1_common_timer.c:390` | `quiesced 60s` at 911.90 (2.9 s before) |
| 2 | 980.298 | `a2_power.c:1189` | `quiesced 60s`, then modem reboot 915.52 |
| 3 | 1883.770 | `lte_ml1_common_timer.c:390` | quiesce ramp throughout; `quiesced 60s` at 1865.46 (18.3 s before) |
| 4 | 2787.445 | `lte_ml1_common_timer.c:390` | clean 60→120→60→120→60→120 sawtooth; last `quiesced 120s` 47.0 s before |
| 5 | 3688.751 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | quiesce ramp 120→180→240; last `quiesced 240s` 86.5 s before |
| 6 | 4592.234 | `lte_ml1_common_timer.c:390` | clean 60→120→60→120 sawtooth; last `quiesced 120s` 51.3 s before |
| 7 | 6327.463 | `a2_power.c:1189` | `stale edge` + modem reboot at 5832.0; `quiesced 60/120` after |
| 8 | 9460.872 | `lte_ml1_common_dump.c:229` | **monotonic** `quiesced 1080→1140→…→1500 s` (25 min with no A2 wake) |
| 9 | 10434.211 | `a2_power.c:2949` | `quiesced 60…240`, then **`lost edge` 0.095 s before the fatal** |

**9 of 9 fatals are preceded by an A2 quiesce.** The quiesce counter is the AP's measure of "how long
since the modem last asserted the PC line"; a monotonic ramp means the modem never woke. The most
extreme case (fatal #8) is **1500 s with no A2 wake**, and the most direct case (fatal #9) is a
`lost edge` resync **95 ms** before the modem fataled at `a2_power.c:2949`.

**Interpretation (working model).** The OpenWrt AP's A2 power-control handshake and the modem's A2
task are not in lockstep (this is the `project_android_differential.md` **A15** axis — the handshake
direction is inverted — plus the bam_dmux **D1–D7** defect cluster). The modem's A2 task sits
*"blocked in wakeup/sleep pending state"* (observed live: `a2_task.c:2871`, counter incrementing,
with `a2_power.c:1771 Modem A2 state SMSM bit turned ON client=14, apps_smsm_vote_bit=0` — the modem
raised the SMSM bit and the AP's vote reads 0). A tech whose A2 wake never completes cannot complete
its sleep, so the MCPM sleep check fails. Because the stall is *chronic*, it fails at **every**
AP-clock beat (Doc 177: the invariant is the AP interval, `903.6746 s ± 0.65 ppm`) — which is why the
observed cadence looks like a clock while actually being a *condition evaluated at* each beat.

**Tautology warning (recorded so it is not repeated).** Reading this epoch's raw dmesg naïvely
suggests the fatals are *modem-uptime*-anchored at ~902.7 s (fatals #1, #3, #4, #5, #6 all sit at
~902.7 s of modem uptime). That is exactly the trap Doc 177 §2 names: `AP = R + T` is an identity and
`corr(R,T) = −1.000`, so whenever each modem boot follows the previous fatal the two models coincide.
The discriminating events are the boots that do **not** follow a fatal — this epoch has them:
boot at `5832.0` (fatals at 495.4 s uptime, not 902.7) and boot at `7932.4` (fatal at 1528.5 s
uptime, not 902.7). **Both falsify strict modem-uptime anchoring.** Doc 177 stands.

### 15.1b The `a2_*` fatal class **cascades** — the interval collapses after the pre-emptive SSR is disabled

The same epoch's fatal-to-fatal intervals, in order:

| # | AP time | site | Δ from previous |
| --: | --: | :-- | --: |
| 1 | 914.818 | `lte_ml1_common_timer.c:390` | — |
| 2 | 980.298 | `a2_power.c:1189` | 65.48 |
| 3 | 1883.770 | `lte_ml1_common_timer.c:390` | 903.47 |
| 4 | 2787.445 | `lte_ml1_common_timer.c:390` | 903.68 |
| 5 | 3688.751 | `lte_ml1_sm_conn_inter_freq_stm.c:712` | 901.31 |
| 6 | 4592.234 | `lte_ml1_common_timer.c:390` | 903.48 |
| 7 | 6327.463 | `a2_power.c:1189` | 1735.23 |
| 8 | 9460.872 | `lte_ml1_common_dump.c:229` | 3133.41 |
| 9 | 10434.211 | `a2_power.c:2949` | 973.34 |
| 10 | 10971.921 | `a2_task.c:3179` | 537.71 |
| 11 | 11147.115 | `a2_power.c:1189` | **175.19** |

Two regimes are visible and they are **different failure classes**:

* **Regime A (`lte_ml1_*`, fatals #1, #3–#6, #8):** intervals 901.3–903.7 s = the Doc 177 AP clock
  (condition evaluated at each beat).
* **Regime B (`a2_*`: `a2_power.c:1189/2949`, `a2_task.c:3179`, fatals #7, #9, #10, #11):**
  intervals **1735 → 973 → 538 → 175 s — monotonically collapsing.**

The transition coincides with **disabling the §13 pre-emptive SSR**: the last pre-emptive restart was at
AP 7932.4, and every fatal after 9460.9 is Regime B with a shrinking interval. Since each fatal *itself*
triggers a remoteproc SSR (crash recovery), the collapse is consistent with **each SSR leaving the A2
handshake a little more desynchronised** — i.e. a **positive-feedback degradation**, which is exactly the
"AP-side A2-SSR parity" gap of Doc 231 §14.3 and `project_android_differential.md` **A15**.

Direct F3 evidence of the desync (`scratch/drx_exp/f3_long.bin`, capture AP 10227→11027, 42 984
records, covers fatals #9 and #10). ⚠ **The F3 stream interleaves several clock domains** — the `code`
field is a per-boot subsystem id and each domain has its own `ts` base — so only records with the
**same `code`** may be compared. All records below are `code=4780` (the main A2 domain) and therefore
time-ordered:

```
ts=3113581492 a2_power.c:1470  Apps SMSM requested for A2 wake up a2_state=0, req_bmask=0x0, modem_smsm_a2_state_bit=0
ts=3113581664 a2_task.c:1766   A2 received wakeup req from apps wakeup_counter=26
ts=3113581672 a2_power.c:1875  A2 Modem SMSM ack bit turned ON client=11, apps_smsm_vote_bit=1
ts=3113581708 a2_power.c:1771  Modem A2 state SMSM bit turned ON client=11, apps_smsm_vote_bit=1
ts=3113581720 a2_task.c:2846   A2 task blocked in wakeup/sleep/apps action pending state counter=70, state=1
ts=3113581724 a2_task.c:2871   A2 task blocked in wakeup/sleep pending state. counter=71, state=1
...
ts=3114020216 a2_power.c:1490  Apps SMSM requested for A2 shut down a2_state=1, req_bmask=0x4800, modem_smsm_a2_state_bit=1
ts=3114020256 a2_power.c:1889  A2 Modem SMSM ack bit turned OFF client=11, apps_smsm_vote_bit=0
ts=3114031472 a2_task.c:2871   A2 task blocked in wakeup/sleep pending state. counter=73, state=3
ts=3114031500 a2_power.c:1583  Apps SMSM acked the modem SMSM request a2_state=1, req_bmask=0x10000, modem_smsm_a2_state_bit=0
...
ts=3130934180 a2_power.c:1771  Modem A2 state SMSM bit turned ON client=14, apps_smsm_vote_bit=0
ts=3130934200 a2_task.c:2871   A2 task blocked in wakeup/sleep pending state. counter=75, state=1
ts=3130934972 a2_power.c:1583  Apps SMSM acked the modem SMSM request a2_state=1, req_bmask=0x4008, modem_smsm_a2_state_bit=1
```

Two desync signatures, both *within* one clock domain:

1. **The AP's vote bit is 0 when the modem raises its A2 bit.** `a2_power.c:1771 … client=14,
   apps_smsm_vote_bit=0` is followed 20 ticks later by `a2_task.c:2871 A2 task blocked in
   wakeup/sleep pending state`. The modem asked; the AP had not voted; the modem's A2 task blocks.
2. **Request and ack carry different client masks.** The shutdown request carries `req_bmask=0x4800`
   (`a2_power.c:1490`) while the corresponding ack carries `req_bmask=0x10000` (`a2_power.c:1583`);
   a later pair is `0x4008`. The two sides disagree on *which* A2 client mask is being voted — a
   state-machine desync, not a timing artefact.

*(A first draft also cited `req_bmask=0x0` at `a2_power.c:1470` against `req_bmask=0x800` at
`a2_power.c:12033`. That pair spans two `code` domains (4780 vs 44217) and is **withdrawn** as
evidence.)*

### 15.1c Why the A2 starves — the AP's vote is tied to its own runtime PM, not to the modem's request

Read from the OpenWrt `qcom_bam_dmux` source (`scratch/orig_kernel/drivers/net/wwan/qcom_bam_dmux.c.patched`,
stock pre-808 base) and the live sysfs:

* `dmux->pc_state` is the **hardware line level of the PC GPIO** — read via
  `irq_get_irqchip_state(pc_irq, IRQCHIP_STATE_LINE_LEVEL, &dmux->pc_state)` (probe) and toggled in
  `bam_dmux_pc_irq()`. **It is driven by the modem.**
* `dmux->pc` (the AP's A2 *vote*) and `dmux->pc_ack` are **SMEM states the AP writes**:
  `bam_dmux_pc_vote()` → `qcom_smem_state_update_bits(dmux->pc, …)`; `bam_dmux_pc_ack()` → `…pc_ack…`.
  The modem's `apps_smsm_vote_bit` in the F3 log **is this `pc` vote**.
* **The AP only votes `pc` ON from `bam_dmux_runtime_resume()`** — i.e. when *the AP* has TX traffic —
  and `bam_dmux_runtime_suspend()` votes it **OFF** (`bam_dmux_pc_vote(dmux, false)`). Autosuspend delay
  is **1000 ms**.
* Live sysfs confirms the duty cycle: `power/control = auto`,
  `runtime_suspended_time = 10 714 972 ms` vs `runtime_active_time = 781 786 ms` — **the modem is
  power-collapsed ~93 % of the time.**

**⇒ The mechanism.** On OpenWrt the A2 is **AP-driven**: an idle modem is power-collapsed, and the AP
only re-votes A2 power when the *AP* has something to send. A modem that needs A2 power for its **own**
internal reasons — a paging occasion, or the ML1/tech sleep machinery — raises its SMSM A2 bit
(`a2_power.c:1771 … apps_smsm_vote_bit=0`) and then **waits for a vote that never comes**
(`a2_task.c:2871 A2 task blocked in wakeup/sleep pending state`). A tech whose A2 wake never completes
never completes its sleep, so the MCPM sleep check fails at the next AP-clock beat. **That is the
"stalled tech".** On Android the handshake is **modem-driven** (`bam_dmux_smsm_cb` :2436 → the AP acks;
`power_vote()` :1672 increments no counter) — the `project_android_differential.md` **A15** inversion.

**Two candidate AP-side levers (both reversible, neither yet tested):**

1. **Keep the A2 voted on** — `echo on > /sys/bus/platform/devices/4080000.remoteproc:bam-dmux/power/control`
   stops autosuspend, so `bam_dmux_runtime_suspend()` never runs and the AP's `pc` vote never drops.
   (This is the *same effect* the §15.2 1 Hz ping achieves via traffic, but at the driver level and
   without depending on the bearer staying up.) Cost: the modem is never power-collapsed.
2. **Android parity (the real fix)** — vote/ack A2 in response to the **modem's** request rather than
   only to AP TX, i.e. give the AP an SMSM callback on the modem's A2 bit instead of waiting for the
   PC GPIO edge. Note task 45 deliberately *added* the idle collapse vote for "Android parity" — that
   parity appears to be **the wrong direction** for this failure mode and should be re-examined.

### 15.2 Pre-registered experiment — "keep the A2 awake" (the stalled-tech DRX arm)

**Pre-registration (written before launch, 2026-09-28).**

* **Hypothesis H-A2.** The `903.6746 s` fatal requires the modem's A2 handshake to be *quiesced*
  (modem not waking). Evidence: §15.1 (9/9).
* **Lever.** A continuous **1 Hz ICMP echo** from the AP to `8.8.8.8` for 2000 s. This forces an
  uplink (AP→modem→network) every second, i.e. an A2 UL client request every second, versus the
  natural idle behaviour where the A2 goes quiesced for minutes. **Reversible:** stop the ping.
  **Blast radius:** none beyond the modem's power state; no firmware or NV is written.
* **Observables.** (a) the AP-side A2 telemetry (`quiesced Ns` lines, patch 808); (b) `fatal error
  received` count; (c) the AP-clock beat lattice `914.818 + k × 903.6746`.
* **Predictions.**
  * **P1.** With the 1 Hz ping running, no `quiesced ≥ 60 s` line is emitted for the whole window
    (the A2 quiesce counter never reaches the 60 s log threshold).
  * **P2.** **No fatal** fires for ≥ 2 beat periods (≥ 1807.35 s) of AP uptime.
  * **P3 (exploratory, not a PASS/FAIL gate).** When the ping stops, the fatal returns within
    ~1 beat if the stall re-establishes.
* **Falsifier.** A fatal at an AP-clock beat while the 1 Hz ping is running ⇒ **H-A2 falsified**;
  the condition is not A2-quiesce-gated and the stalled tech is not kept asleep by A2 starvation.
* **n / duration.** The beat period is 903.6746 s; ≥ 2 periods is the minimum to distinguish
  "suppressed" from "lucky". Window **2000 s**, poll every 5 s.
* **Control.** The immediately preceding 2000 s of the same epoch (fatals #7, #8, #9 at 6327.46,
  9460.87, 10434.21 — i.e. **3 fatals in 4107 s** with no keep-awake traffic).

**Result:** *(filled in below after the run — §15.3)*

### 15.3 Result — **P1 PASS, P2 FAIL: the strong form of H-A2 is NOT supported**

Run: `scratch/drx_exp/keepawake.log`, script `scratch/drx_exp/keepawake.sh`, window 2000 s.
Baseline **AP 11199, fatals = 11, quiesce lines = 101**. Control verified taken
(`wwan_tx 3974 → 3987`, `ping_procs = 1`).

| criterion | result |
| :-- | :-- |
| **P1** — no `quiesced ≥ 60 s` line while the ping runs | **PASS.** The quiesce-line count stayed at **101** for the whole window; `power/runtime_active_time` grew 100 % (1 126 267 → 1 146 409 ms over 20 s) while `runtime_suspended_time` froze at 10 714 972 ms. **The A2 was demonstrably never quiesced.** |
| **P2** — no fatal for ≥ 1808 s | **FAIL.** Fatal **#12 at AP 12048.22** (`lte_ml1_sm_conn_inter_freq_stm.c:712`) — 849 s after the baseline, less than half the required window. |
| **Falsifier** — a fatal at an AP-clock beat while the ping runs | **Not triggered** (see confound) — but the run nevertheless fails the mitigation claim. |

**Confound, stated plainly.** The host-side keep-alive (an `ssh … ping` pair) **died at ≈ AP 12043**,
**5 s before** the fatal; the script logged `WARN ping not running (p=0) — relaunching` at 22:50:07 and
the fatal landed at 22:50:12. The fatal is also **off the AP-clock lattice** —
`(12048.22 − 914.818) / 903.6746 = 12.32`, not an integer. So the run **cannot** distinguish:

* *(a)* the keep-awake was working and the fatal was released the instant it stopped; from
* *(b)* the modem was already failing, which stalled the bearer, which killed the ping 5 s before the
  fatal — i.e. the ping's death is a **symptom**.

**What the run does establish, and what it does not.**

* **Does establish:** the `lte_ml1_*` (Regime A) fatal **does not require an A2 quiesce**. The A2 was
  provably un-quiesced (100 % runtime-active, zero quiesce lines, no edge events) for 838 s and the
  fatal fired anyway. ⚠ **This materially weakens §15.1's 9/9 correlation**: the RX watchdog only emits
  `quiesced` when `pc_state == 0`, so "every fatal was preceded by a quiesce" may mean no more than
  "every fatal was preceded by an idle period". **§15.1's correlation is downgraded from causal to
  observational, and §15.1c's mechanism is downgraded to a hypothesis that this run did not support.**
* **Does not establish:** that keeping the A2 awake prevents the fatal. P2 failed and the keep-alive
  died early, so **no mitigation claim can be made**.

**The one substantive new observation from the run** (and the only bam-dmux line in the whole window):
the last event before the fatal is

```
[11149.281458] bam-dmux …: bam_dmux: modem pc-ack timeout during resume
[12048.219370] qcom-q6v5-mss …: fatal error received: lte_ml1_sm_conn_inter_freq_stm.c:712:
```

— **899 s of complete bam_dmux silence, then the fatal.** The `pc-ack timeout during resume` is the
documented H4 signature (`project_ssr_powerup_outcome_by_signature.md`: both writers of
`pc_timeout_count` live in `bam_dmux_runtime_resume()`, so *no resume ⇒ no timeout*). It fired
**1.8 s after** fatal #11's recovery, i.e. the AP's first resume after that SSR timed out.

### 15.4 Next step (re-run with a bearer-independent keep-alive) — **and why the lever "reverted"**

The confound is structural: a *traffic*-based keep-alive dies exactly when the modem starts failing.
The clean re-test is the **driver-level** lever of §15.1c(1) —
`echo on > /sys/bus/platform/devices/4080000.remoteproc:bam-dmux/power/control` — which pins the AP's
A2 vote on regardless of whether the bearer survives, so the keep-alive cannot be killed by the failure
it is meant to test. Re-run for ≥ 1808 s with the same P1/P2 criteria and the same off-lattice check.

**★ Correction (2026-09-28, later the same session) — the §15.4 run was aborted for the wrong reason.**
It recorded that the lever "reverts to `auto` within ~60 s" and declared the arm invalid. The revert is
**not** a driver quirk: the project's own `modem-bearer-watchdog` re-writes `auto` **unconditionally
every `check_interval`**:

```
/usr/sbin/modem-bearer-watchdog:169-177
    # 2. Ensure BAM-DMUX maintains 1000ms autosuspend (Phase 5 dynamic DRX power collapse)
    if [ -f "$BAM_CTRL" ] && [ "$(cat "$BAM_CTRL" 2>/dev/null)" != "auto" ]; then
        echo auto > "$BAM_CTRL" 2>/dev/null || true
        logger -t modem-stall-watchdog "Restored BAM-DMUX control=auto (Phase 5 dynamic power collapse)"
    fi
```

Measured 2026-09-28 on boot `a6dd9e7e`: `control=on` written at t=0, still `on` at t=15 s, back to
`auto` at t=20 s — consistent with the 5 s enforcement loop (`general.check_interval='10'` in UCI, but
the `get_modem_uptime`/SSR branches can re-loop faster). **So the lever is holdable: stop that one
service first.** Note what this does *not* mean: the enforced collapse is **correct Android parity**,
not a defect — Android 4.4.4 has no runtime PM and instead clears `SMSM_A2_POWER_CONTROL` itself
1000 ms after the last uplink packet (`ul_timeout()` → `ul_powerdown()` → `power_vote(0)`; Doc 140 §2),
so an idle modem is *supposed* to be power-collapsed on both stacks. The finding here is only that
the enforcement **blocked the §15.4 experiment**, not that it caused the fatal — and §16 now shows the
collapse is not the fatal's gate either.

### 15.5 The `pc-ack timeout during resume` (H4) fires right after an SSR, and a fatal follows ~1 beat later

Exhaustive grep of the epoch's `dmesg` (`pc-ack timeout during resume` occurs **twice**, fatals **12**):

| # | pc-ack timeout at AP | nearest preceding fatal | next fatal | gap |
| --: | --: | --: | --: | --: |
| 1 | **982.56** (dmesg line 405) | #2 at 980.30 (line 369) | #3 at 1883.77 | **901.2 s ≈ 1 beat** |
| 2 | **11149.28** (line 1010) | #11 at 11147.11 (line 974) | #12 at 12048.22 | **898.9 s ≈ 1 beat** |

Both timeouts land **~2 s after** a fatal's crash-recovery, i.e. they are the AP's **first
`bam_dmux_runtime_resume()` after that SSR** failing to get the modem's pc-ack. The state left behind
is visibly broken — dmesg lines 406–413 (between the first timeout and the fatal) are a solid wall of
A2 pathology:

```
[ 982.555188] bam_dmux: modem pc-ack timeout during resume
[1086.807624] RX watchdog: quiesced 60s (pc_state=0, pc_line=0, rx=held, ring disarmed)
[1186.246022] RX watchdog: quiesced 60s ...
[1337.995589] RX watchdog: quiesced 60s ...
[1453.251727] RX watchdog: quiesced 60s ...
[1523.539811] RX watchdog: pc_state=1 but pc line low (stale edge), reconciling
[1523.539908] RX watchdog: quiesced 18446744073s (pc_state=0, pc_line=0, rx=held, ring disarmed)
[1675.899852] RX watchdog: quiesced 60s ...
[1865.463341] RX watchdog: quiesced 60s ...
[1883.770471] qcom-q6v5-mss: fatal error received: lte_ml1_common_timer.c:390:
```

`18446744073 ≈ (2⁶⁴−1)/10⁹` — the quiesce-duration division **underflowed**, i.e. `pc_quiesce_ns > now_ns`.
That is a genuine counter bug in the patch-808 telemetry, and it marks the moment the driver's A2
bookkeeping lost coherence. **899 s of bam-dmux silence then the fatal.**

**Honest scope.** Only **2 of 11** SSRs produced a pc-ack timeout, so the timeout is **not a necessary
precursor** — fatals #3–#10 had none. But **both** timeouts were followed by a fatal within one beat, and
the intervening state is broken. This is the strongest surviving form of the §14.3 lead: *an SSR
sometimes leaves the AP's A2 handshake broken, and the next beat fatal follows.*

**★ Correction (2026-09-28, later the same session) — the "candidate fix" below describes the STOCK
code, not the deployed code.** A first draft of this paragraph proposed making
`bam_dmux_runtime_resume()` retry on a pc-ack timeout "rather than `bam_dmux_pc_vote(dmux, false);
return -ETIMEDOUT`". Reading the *deployed* source shows that give-up path **is the stock code that
patch 808 already removed**. The deployed path (`808-bam-dmux-stats.patch:1407-1459`, plus patch 825)
already:

1. does **not** give up — it logs the timeout, waits for `pc_state`, checks `dmux->rx`/`dmux->tx`, and
   **returns 0** (soft-fail) even on timeout;
2. on a pc-ack timeout **with the wire low**, reconciles `pc_state` against the hardware line level,
   schedules the RX watchdog, and returns (patch 825 — the message
   `RX watchdog: pc_state=1 but pc line low (stale edge), reconciling` is visible in this epoch's
   dmesg at AP 1523.54).

So the proposed patch is **already deployed** and is not a lever. The two `pc-ack timeout during
resume` events in this epoch happened *on that deployed code* — i.e. the soft-fail + reconcile did not
prevent the following fatal. **⇒ the resume-path handshake is not the lever either; the H4 correlation
(§15.5) remains an association between an SSR and a broken A2 state, not a fixable defect in the
resume path itself.** The remaining honest reading: `pc-ack timeout during resume` is a *symptom* of a
post-SSR modem that has not re-established the A2 handshake, not a cause the AP can patch away in the
resume callback.

### 15.6 The clean, bearer-independent A2-pin arm (H-A2b) — launched

**Pre-registration:** `scratch/drx_exp/PREREG_a2pin_clean.md` (written before launch). **Watcher:**
`scratch/drx_exp/a2pin_soak.sh` → `scratch/drx_exp/a2pin_soak.log`.

With the §15.4 blocker explained (the watchdog re-forces `auto` every 5 s), the §15.2 experiment can
be re-run *without its confound*: the pin is now a driver-level state that cannot be killed by the
failure it is meant to test.

* **Hypothesis H-A2b.** The Regime-A fatal requires the AP to drop its A2 `pc` vote (power-collapse).
* **Lever.** `/etc/init.d/modem-bearer-watchdog stop` (removes the 5 s `auto` enforcement), then
  `echo on > …/bam-dmux/power/control`. Reversible (restart the service); no firmware/NV/kernel change.
* **Setup verified (control took, asserted):** baseline AP 13369, fatals 12; watchdog stopped
  (0 procs); `control=on` held for 60 s with `runtime_active_time` advancing
  (2 423 673 → 2 478 840) and `runtime_suspended_time` **frozen** at 10 948 707 ms.
* **Predictions (fixed before launch).** **P1** the pin holds and `runtime_suspended_time` does not
  advance; **P2** no fatal for ≥ 1807.35 s (2 beats); **P3** (exploratory) the fatal returns within
  ~1 beat of release.
* **Falsifier.** A fatal at an AP-clock beat while `control=on` is held ⇒ **H-A2b falsified** — the
  Regime-A fatal does not require the A2 vote to drop, and the A2-parity lever (§15.1c(2)) is not the
  preventive fix. Window **2400 s**, poll 30 s.

**Interpretive note.** §15.2's P1 already showed a *traffic* keep-alive held the AP continuously active
(100 % `runtime_active_time`, zero quiesce lines) for 838 s and the fatal fired anyway. If the traffic
keep-alive genuinely held the same state as `control=on`, **H-A2b is expected to fail**; the clean arm
exists to remove the confound, not because the earlier result is expected to reverse. **Either outcome
is informative:** PASS ⇒ a shippable pure-software fix (stop forcing autosuspend); FAIL ⇒ the A2 axis
is closed and the remaining root cause is the firmware's tech-sleep/LPR accounting (§12), which is not
reachable from the A2 handshake.

**Status: RUNNING** — result to be scored in §16 when the watcher reports.

---

## 16. Result — the clean A2-pin arm: **H-A2b FALSIFIED**

**Run:** `scratch/drx_exp/a2pin_soak.sh` → `a2pin_soak.log`. Baseline AP **13369**, fatals **12**,
`control=on` written with the watchdog stopped.

| criterion | result |
| :-- | :-- |
| **P1** — the pin holds and `runtime_suspended_time` does not advance | **PASS.** `control` read `on` in every 30 s sample for the whole window (no `PIN DRIFTED` line), `runtime_active_time` advanced monotonically (2 494 977 → 3 370 758) and `runtime_suspended_time` stayed **frozen at 10 948 707 ms**. |
| **P2** — no fatal for ≥ 1807.35 s | **FAIL.** **Fatal #13 at AP 14183.215848**, site **`lte_ml1_common_timer.c:390`** — **814.2 s** after the pin (less than half the required window). |
| **Falsifier** — a fatal at an AP-clock beat while `control=on` | **TRIGGERED ⇒ H-A2b falsified.** |

**Verdict: H-A2b is FALSIFIED.** The Regime-A fatal fired while the AP's A2 `pc` vote was pinned on
and the modem was **never power-collapsed** (`runtime_suspended_time` frozen, `control=on` asserted in
every sample). **⇒ the fatal does NOT require the AP to drop the A2 vote, and the A2-parity lever
(§15.1c(2)) is not the preventive fix.** Together with §15.3 (P2 FAIL with a traffic keep-alive), the
A2 axis is now **closed by two independent, confound-free arms** — one traffic-driven, one
driver-level. §15.1's 9/9 correlation is confirmed **observational only**; §15.1c's mechanism is
**not supported**.

**Exploratory observations (NOT part of the gate; do not cite as results).**

* The fatal-to-fatal interval #12→#13 is **2134.996 s = 2.362 beats**, i.e. the "one beat" pattern of
  §15.5 did **not** hold here, and the two intervening beat instants (AP 12 951.9, 13 566.3) both
  passed **without** a fatal — the first of them **before** the pin was applied. **The conditional
  beat model is therefore not cleanly holding in this epoch regardless of the pin**, so the missed
  beats cannot be attributed to the pin. (The fatal itself is at beat index 14.68 — off-lattice,
  exactly like fatal #12 at 12.32.)
* The pre-pin Regime-B cascade had reached 175 s (§15.1b); the post-pin interval is 2135 s. That is
  *consistent with* the pin having broken the collapse, but with **one** post-pin interval and a
  pre-pin cascade that had already recovered to 901 s at #11→#12, this is **not** evidence of
  suppression. It would need ≥ 3 post-pin intervals to say anything.

**Interpretation.** The two A2 arms together close the "AP A2 vote" axis cleanly. The remaining
root cause is the one §12/Doc 186/`900S_CRASH_LPR_FRAMEWORK_RE.md` §6 describe: the Q6's **`rpm.sync`
LPR step parks in a timeout-free churn loop**, so `q6pcvote` never advances and the MCPM sleep-count
check trips at 400 cycles. Doc 150 already showed the **RPM is alive** across the fatal, so the stall
is **Q6-side** — and no AP-side A2 state changes it. **The AP-side levers that remain are the ones
that would change the modem's RPM interaction itself (interconnect/bandwidth parity), not its A2
power vote.**

**Window finalization (deviation, stated).** The pre-registered window was 2400 s; it was **truncated
at el = 1292 s (AP 14 661)** because the PASS condition was already violated at el = 839 — no further
sample could change the verdict. The window saw **exactly one** fatal (the falsifying #13); the pin
held in **every** 30 s sample (el 0→1292, 44 samples, `control=on`, `runtime_suspended_time` frozen
at 10 948 707 ms throughout). The truncation is a deviation from the pre-registered duration and is
recorded as such; it is not a re-tuned criterion (the verdict was determined by the fixed
P2 ≥ 1807.35 s gate, which a fatal at 839 s violates).

**Release (verified, control-took asserted).** `/etc/init.d/modem-bearer-watchdog start` ⇒
`control` went **`on` → `auto`** and `runtime_suspended_time` resumed advancing
(10 948 707 → 10 963 605 ms); `ps | grep -c '[m]odem-bearer-watchdog'` = **1**. The device is back to
its normal behaviour (autosuspend enforced, bearer-stall recovery armed). The pin was held for
el 0→1292 and released at AP 14 693; uptime 14 708, fatals 13.

---

## 17. Where this leaves the crash-fix campaign (consolidation, 2026-09-28)

Every preventive lever this investigation has tested, with its verdict — so none is re-chased:

| lever | where | verdict |
| :-- | :-- | :-- |
| ML1-timer **assert-patch** (10 sites, `FUN_c02d7bd0`) | §3–§6 | **RELAYS** to the next site at the same beat. Whack-a-mole. |
| **CDMA removal** from `rat_acq_order` | §7–§12 | **FALSIFIED** — the beat fired at 902.45 s with the NV held. |
| **Pre-emptive modem SSR** | §13 | **FAILED** — the beat is absolute-AON-anchored; a restart re-arms to the next beat. |
| **Traffic keep-awake** (1 Hz ICMP) | §15.2–§15.3 | **P1 PASS / P2 FAIL** — the A2 was provably un-quiesced for 838 s and the fatal fired. Confounded (keep-alive died 5 s pre-fatal). |
| **A2 vote pinned on** (driver-level, clean) | §15.6–§16 | **FALSIFIED** — the fatal fired with `control=on` and `runtime_suspended_time` frozen. |
| **DFAB/XO clock voting** | Doc 140 §7.1 | **DISPROVEN** — Android's is a no-op too (its DT node declares no `clocks`). |
| **Resume-path pc-ack retry** | §15.5 corr. | **Already deployed** (808+825) and did not prevent the following fatals. |
| RPM **bandwidth/interconnect** parity | not yet tested | **OPEN** — the only AP-side axis left; mechanism unproven (see below). |

**What is actually established about the root cause.** The fatal is the modem's **own** sleep-count
watchdog firing because the Q6's **`rpm.sync` LPR step parks in a timeout-free churn loop**
(`900S_CRASH_LPR_FRAMEWORK_RE.md` §6.4–§6.5; `FUN_c08b96f4` / `0xc08b988c`). `q6pcvote`
(`lpr_get("rpm")+0x18`) therefore never advances, so MCPM's `system_sleep_check` trips at 400 cycles
and calls the non-returning `FUN_c0879150`. **The `file:line` label is just whatever descriptor the
running code handed it** — hence one physical fault printing `lte_ml1_common_timer.c:390`,
`a2_power.c:1189`, `sm_idle_stm.c:2913`, … across boots. **The RPM itself is alive across the fatal**
(Doc 150 §8.2: 214 records inside the window, 4 629 in the 3.6 s after the modem returns; its
19.2 MHz clock never stops) ⇒ the stall is **Q6-side**, and the "RPM is wedged" arm is not supported.

**Why every AP-side lever above failed, in one sentence.** They all act on the AP's *A2 power vote* or
on *when the modem is restarted*; neither changes the Q6's RPM-churn bookkeeping, which is where the
stall actually lives.

**The one remaining AP-side axis, and why it is only a test.** Android 4.4.4's AP is an RPM
*bandwidth*-voting client (`MSM_BUS_SCALING`, 107 call sites, 17 live `/d/msm-bus-dbg` clients);
OpenWrt's is not (`# CONFIG_INTERCONNECT_QCOM is not set`). The kernel **has** the driver
(`drivers/interconnect/qcom/msm8916.c`, `qcom,msm8916-{bimc,pcnoc,snoc}`) and the DTS **already
declares** the three nodes, so this looks like a config flip — but **it is not**: there are **no DT
consumers** (`grep "interconnects ="` on the msm8916 DTs = 0), so enabling the driver registers
providers and votes nothing. A consumer that calls `icc_set_bw()` for the modem-relevant path
(`SLAVE_MSS` = 33 in PCNOC; `SLAVE_EBI_CH0` = 6 in BIMC) would have to be written. **And the premise
is doubtful**: Doc 150 shows the RPM receiving the modem's requests throughout, so it is not obvious
that an AP bandwidth vote changes the Q6's churn. **⇒ next arm = enable `CONFIG_INTERCONNECT_QCOM` +
add a modem-path consumer, then soak against the AP-clock beat.** It is a *test of §12's framing*,
not a consequence of it; §12's "the LPR counter is fed by the AP's RPM/LPR bandwidth voting" should
not be cited as established.

**The honest bottom line.** After §16, **no AP-side preventive lever is known**. The crash is a
modem-firmware-internal condition (the Q6's RPM-churn stall) whose AP-side trigger is still
unidentified; every intervention that is cheap and reversible has been tested and falsified. The
durable options are (a) the interconnect-parity test above, and (b) the AP-side recovery work
(patch 0005 bearer rebuild, the bam_dmux D1–D7 cluster) that makes each fatal's ~16 s outage
shorter — which is a *consequence* fix, not a crash fix.

## 18. Result — the AP idle-state (cpuidle) differential arm: **H-IDLE FALSIFIED** (2026-09-28)

**Why it was run.** Axis A2 (the AP power-collapse axis) was the last unmeasured row of the
differential table. It is now measured on both sides (cpu0 `cpuidle/state*/usage`):

| cpu0 idle | WFI | standalone PC | cluster PC |
| :-- | --: | --: | --: |
| Android (A0 window, 29 262.5 s) | 105.8/s | **0.209/s** | **12.09/s** |
| OpenWrt (boot `a6dd9e7e`, 16 101 s) | 59.5/s | **55.1/s** | **absent** |

OpenWrt's AP enters `standalone-power-collapse` **~264× more often** than Android's `standalone_pc`
and **has no cluster-PC state at all**, while Android's idle is *dominated* by cluster PC (12.09/s).
First first-order mechanical AP-side difference found on the `a2_power.c` axis.

**Lever.** `echo 1 > …/cpuN/cpuidle/state1/disable` on all 4 CPUs (WFI-only). Pre-registration
`scratch/drx_exp/PREREG_cpuidle_rf.md`; watcher `cpuidle_soak.sh` → `cpuidle_soak.log`.

| criterion | result |
| :-- | :-- |
| **P1** — `cpu-sleep-0` usage frozen on all 4 CPUs | **PASS** — identical at t0 and t+20 (898 069 / 776 402 / 768 186 / 752 502), frozen for the whole window. |
| **P2** — no fatal ≥ 1807.35 s | **FAIL** — **fatal #16 at AP 16892.961383**, `lte_ml1_common_timer.c:390`, **530 s** after the lever. |
| **Falsifier** — a fatal at an AP-clock beat with the deep state disabled | **TRIGGERED ⇒ H-IDLE falsified.** |

**Verdict: the AP's deep-idle behaviour is NOT the differential.** The fatal fired on the exact beat
with the AP pinned to WFI. The A2 axis is now closed on **both** of its sub-levers (the modem A2 vote
§16, and the AP's own CPU power-collapse §18).

**★★ A cleaner beat observation than any earlier epoch.** Fatals #13–#16 are at beat indices
**14.683 / 15.681 / 16.681 / 17.681** — four consecutive fatals at a **constant phase of +0.681
beats** (+615.4 s), i.e. the effective lattice is `1529.4 + k × 903.6746` s of AP uptime. Intervals
#13→#14 = **902.39 s**, #14→#15 = **903.68 s**, #15→#16 = **903.68 s**. **This epoch is a textbook
Regime A** — no collapse, no drift, no skipped beat — and the phase is *stable*, not wandering. Any
future arm should be scored against `1529.4 + k × 903.6746`, and a "phase shift" is a usable signal.

**RF series (descriptive; not a gate).** RSRP was stable at −85…−87 dBm throughout, while **SNR
swung wildly in the same window** (−2.6 → 22.4 → −1.0 → 19.0 dB, 30 s apart). A stable RSRP with a
flapping SNR is an **interference/noise-limited link**, not a coverage-limited one — and it matches
`900S_CRASH_LPR_FRAMEWORK_RE.md` §9.5 independently (the LTE RX chain fires
`rflte_core_rxctl_update_rx_gain_freq_comp_to_mdsp` 144× in one 10 s bin, zero elsewhere: "retunes and
never locks"). **Hypothesis raised, not tested:** the stalled tech is the LTE RX chain chasing a
noisy channel; mobility stresses it (the operator's bus observation) and so does a stationary but
interference-dominated link. The test would be an RF-quality arm (cell/band lock, or antenna change)
— see §19.

**Window finalization (deviation, stated).** Truncated at el = 530 s (pre-registered 2400) because the
PASS condition was already violated at el = 530 — no further sample could change the verdict.
**Release verified:** `disable=0` on all 4 CPUs and usage advancing again (898 229 → 898 684 in 10 s).

---

*SOP note (feedback_living_ledger_sop): Doc 231 covers the crash-fix investigation from 197 onward and
carries this SOP-compliance statement (§13.7). Ledger item 36 tracks the crash-fix status; as of 2026-09-28
the §13 pre-emptive-SSR mitigation is **RETRACTED (soak FAIL)** — the crash is NOT fixed; the open arms are
the AP-side root cause (§14.2) and the a2_power-after-SSR lead (§14.3).*

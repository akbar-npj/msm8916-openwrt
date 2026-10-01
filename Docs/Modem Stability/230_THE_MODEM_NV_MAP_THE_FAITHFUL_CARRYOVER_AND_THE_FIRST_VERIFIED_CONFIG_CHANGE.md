# Doc 230 — The modem NV map, the faithful EFS carry-over, and the first verified modem-config change

**Date:** 2026-09-28
**Device:** HMU05 (UFI001B) dongle · OpenWrt 25.12.5 · kernel 6.12.94 aarch64 · serial `c2b9103c`
**Firmware:** pristine stock HMU05 (`modem.b16 57fef19d…`, `modem.b01 b85b86ce…`, `modem.mdt 1a6f9507…`)
**Evidence:** `evidence/230_ue_usage_setting/`
**Ledger:** issued under `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (item 34).
**Answers:** Doc 229 §6.2 (the CS/voice-domain lead) and the open item in
`project_modem_firmware_identical_proof.md` ("whether the carry-over was byte-exact").

---

## 1. Goal and method

Doc 229 left two surviving leads for the ~900 s fatal: axis A15 (the A2 handshake inversion) and the
**CS/voice-domain configuration**, which was flagged as "cheap, non-invasive, UNTESTED". This document
executes the CS/voice-domain lead, and in doing so maps the modem's NV/EFS for the first time.

Method: (a) re-establish the EFS instrument; (b) **verify the EFS carry-over** against the stock dump;
(c) **map the modem's `mmode`/`nas` NV** and the firmware's item manifests; (d) apply the one NV item
whose effect is independently verifiable, and test whether it suppresses the fatal.

## 2. Result 1 — the EFS carry-over is FAITHFUL (closes an open item)

`project_modem_firmware_identical_proof.md` recorded that the OpenWrt port carries the stock NV over by
design, and left one thing unverified: *"whether the carry-over was byte-exact. … a lossy copy would be
a finding"*. **It is byte-exact.** The factory-static partitions match the stock dump exactly:

| partition | device md5 | stock dump | verdict |
| :-- | :-- | :-- | :-- |
| p1 `fsc` | `0f343b0931126a20f133d67c2b018a3b` | `fsc.bin` `0f343b09…` | **IDENTICAL** |
| p2 `fsg` | `4201f7f39557578e1c8904e11c5529a4` | `fsg.bin` `4201f7f3…` | **IDENTICAL** |
| p4 `modemst1` | `8f09b6e4d0331b6755a0e678281662c4` | `modemst1.bin` `68ac92ff…` | differs (live EFS) |
| p5 `modemst2` | `3dc0a1c4b63ebd1bee0908d53999274d` | `modemst2.bin` `0e04025c…` | differs (live EFS) |

⇒ **the "lossy carry-over" hypothesis is dead**, and the `modemst` divergence is the modem's own runtime
EFS writes (RPLMN, backoff info, …) accumulated since the dump.

## 3. Result 2 — EFS writes DO persist (`rmtfs` fdatasyncs every write)

The memory carried a claim that *"an EFS write does NOT survive an AP reboot"*. **The write path says
otherwise.** From the rmtfs source (`GitIgnore/dl/rmtfs-1.0.tar.zst`):

```c
/* rmtfs.c, iovec write handler, after the "respond:" label */
if (is_write)
        storage_sync(rmtfd);            /* rmtfs.c:225 */
/* storage.c:249 */
ssize_t storage_sync(struct rmtfd *rmtfd) { return fdatasync(rmtfd->fd); }
```

`rmtfs` runs `-P -s` and holds `modemst1`(p4), `modemst2`(p5), `fsg`(p2), `fsc`(p1) open — **every EFS
write iovec is `fdatasync`'d to the backing partition immediately.** This was then confirmed
empirically: the `ue_usage_setting` write (§5) **survived the modem SSR**.

## 4. Result 3 — the modem's NV is mostly ABSENT; the compiled-in defaults are what run

The EFS carries plaintext **manifests** of the NV items each module reads
(`/nv/item_files/conf/mmode.conf` — **54 items**; `/nv/item_files/conf/nas_mm.conf` — **30 items**).
Only ~10 `mmode` items exist as files; the rest return **ENOENT (`err 0x2`)** and fall back to the
firmware's compiled-in defaults.

**Present (`mmode/`):** `ue_usage_setting=00` (voice-centric) · `device_mode=00` ·
`sms_domain_pref=01` (CS) · `sms_mandatory=01` · `sms_only=00`

**Absent (defaulted) — every one of them CS/CDMA-relevant:**
`voice_domain_pref` · `supplement_service_domain_pref` · `c2k_switch_2_srlte` ·
`allow_csfb_upon_ims_reg` · `lte_do_irat_duration` · `3gpp2_ps_call_optimization` · `sxlte_timers` ·
`allow_sms_in_ecbm` · `sms_over_s102` · `sd/1xcsfb_ecbm_status` · `sd/c2k_resel_splmn_supp` ·
`1xsrvcc_stn_sr_number` · `nas/l21xsrvcc_support` · `nas/nas_srvcc_support` ·
`nas/sglte_nas_nv_config` · `nas/lte_nas_srlte_esr_time` · `nas/emm_combined_proc` · `nas/isr`

**Effective state (QMI):** `Mode preference: lte` · `Service domain preference: cs-ps` ·
`Usage preference: voice-centric` · `Voice domain preference: cs-preferred` ·
`Acquisition order: lte, umts, gsm, cdma-1x, cdma-1xevdo`

**`/sd/rat_acq_order` = `03 e7 00 05 09 05 03 02 04`** — decoded as
`{ver=0x03, ?, num=0x0005, rats=[09,05,03,02,04]}`, i.e. `09=lte, 05=umts, 03=gsm, 02=cdma-1x,
04=cdma-1xevdo`. **Both CDMA RATs are still in the order** although `--dms-get-capabilities` says
`umts, lte` and HMU05 has no CDMA RF.

## 5. Result 4 — the first verified modem-config change, and it PERSISTS

**`ue_usage_setting` 0x00 (voice-centric) → 0x01 (data-centric)**, chosen because it maps 1:1 onto the
QMI-reported `Usage preference`, so its effect is **independently verifiable**:

```
$ /root/diag_efs_skip put /tmp/ue1.bin /nv/item_files/modem/mmode/ue_usage_setting
  backing up -> _nv_item_files_modem_mmode_ue_usage_setting.bak.1790592546
  wrote 1 bytes; read-back 1 bytes -> MATCH

  (same boot)  qmicli … --nas-get-system-selection-preference  →  Usage preference: 'voice-centric'
  (after SSR)  qmicli … --nas-get-system-selection-preference  →  Usage preference: 'data-centric'
               /root/diag_efs_skip get …/ue_usage_setting      →  01
```

Two things are established:

1. **The NV write persists across a modem restart** — correcting §3's memory claim.
2. **The modem re-reads it at NAS init and acts on it** — the QMI value changed.
   (It does **not** take effect live: the value read `voice-centric` in the boot that wrote it.)

**This is the first time a modem configuration change has been applied, persisted, and independently
verified on this device.** It is a reusable capability, not just a test.

Note: the change altered `Usage preference` **only** — `Voice domain preference` stayed
`cs-preferred`, i.e. `ue_usage_setting` and `voice_domain_pref` are separate items.

## 6. The hypothesis, and why it can only be a *workaround*

The consolidated model (§12) recorded at **every collapse onset**:

```
=SD= SDSS:FRFL: Updating core ss= %d, lte_cs_cap= %d ext_srv_info =%d
** Activate GWL opr script = ssscr_gw_opr_srv_info **
SRLTE is enabled
```

and the crash coredump's dog table has **`hdrsrch` (1x-EVDO search) as one of only six unblocked,
59/60-expiring tasks**. At runtime the modem therefore runs a **CS/CDMA2000 voice path it can never
complete** — the exact class of "the tech never reaches a stable sleep state" condition the MCPM
watchdog (`sleep count not incrmnt`) fires on.

**But this cannot be the Android differential**: the firmware defaults are identical and the EFS was
carried over from the same stock image (§2), so Android runs the same CS/CDMA configuration. It is a
**workaround candidate**, not an explanation of the ~25× rate gap.

## 7. The experiment and its result

**Pre-registered.** Apply `ue_usage_setting=1` (data-centric). Success criterion: **zero fatals for
> 1200 s** of modem uptime with the modem still connected on LTE. **One fatal falsifies it.**

**Arm 1 (data-centric alone).** The write was applied at AP 574.89 s. The natural fatal that provides
the required modem restart arrived at **AP 1023.608588 s** (`a2_power.c:1189`), i.e. **1012.40 s of
modem uptime**. That is the **activity-correlated** signature (Doc 162: mean 695 s, **spread 872 s**),
*not* the tight 902.6 s clock, so its lateness is unremarkable and is **not** evidence of suppression.
The post-SSR epoch boots with `Usage preference: data-centric`.

**Result:** see §7.1.

### 7.1 Arm-1 outcome — **FALSIFIED**

The epoch that booted with `Usage preference: data-centric` died at:

| quantity | value |
| :-- | :-- |
| modem up (dmesg) | `[ 1024.311654] … is now up` |
| fatal (dmesg) | `[ 1926.966036] fatal error received: lte_ml1_common_timer.c:390:` |
| **epoch length** | **902.654382 s** |
| site | `lte_ml1_common_timer.c:390` — **the tight-clock site** |
| attach | `wwan0 10.155.71.113/30` + default route (LTE attached) |

**902.654 s < 1200 s ⇒ criterion NOT MET. Arm 1 is FALSIFIED.** The modem re-read the NV at
NAS init (QMI confirmed `data-centric`), so the lever *was* applied — it simply does not gate
the fault. The site is the tight-clock signature, not the variable `a2_power.c:1189`, so this
is not a lucky/unlucky draw.

**⇒ `ue_usage_setting` is closed. The voice/CS-domain family is not closed** — arm 2 tests the
item that QMI showed unchanged (`Voice domain preference: cs-preferred`).

### 7.2 ★ Bonus: an independent confirmation that the clock is AP/AON-anchored

Two consecutive fatals in this AP boot (see `evidence/230_ue_usage_setting/05_arm1_result.txt`):

```
#1  AP 1023.608588   a2_power.c:1189            (the LATE-firing site)
#2  AP 1926.966036   lte_ml1_common_timer.c:390
fatal-to-fatal AP interval = 903.357448 s
```

Against Doc 177/179's free-running AP clock `P = 903.675206 s`:

```
beat                    = 1926.966036 - 903.675206 = 1023.290830 s
#1 residual vs the beat = +0.317758 s   (a2_power is the late site)
#2 residual             =  0.000000 s   (exact)
phase                   =  119.615624 s
```

**The discriminator:** this modem epoch started at AP **1024.311654**, i.e. the beat that
produced fatal #2 (**1023.290830**) was scheduled **1.021 s before the epoch existed**. A
modem-anchored counter cannot own a deadline that precedes its own boot ⇒ the time base is the
**AP always-on (AON/XO/RPM) domain**. Doc 177 §3.1's conclusion is thereby reproduced from a
**modem restart** rather than an AP reboot — a new and independent route to it.

The beat at 119.615624 s was **survived**: the modem does not assert at the first beat after
boot, so the condition is accumulated, not "first beat wins".

### 7.3 ★★ The clock, confirmed to sub-millisecond, and the A2 telemetry across a beat

**Fatal #3 landed at AP 2830.641649** (`lte_ml1_common_timer.c:390`). Against the §7.2 model
`beat(k) = 119.615624 + k × 903.675206`:

| k | predicted | observed | residual |
| --: | --: | --: | --: |
| 1 | 1023.290830 | 1023.608588 | +0.317758 (`a2_power.c:1189` — the late site) |
| 2 | 1926.966036 | 1926.966036 | **0.000000** |
| 3 | 2830.641242 | 2830.641649 | **+0.000407** |

Solving the two exact points for the period gives **P = 903.675613 s** vs the established
903.675206 s — **agreement to 0.5 ppm**, from a *single AP boot* and across a modem restart. This is
the tightest clock fit recorded for this fault.

**The A2/PC telemetry across the beat** (`06_telemetry_across_beat.txt`). The handshake was **frozen
for 144.7 s before the fatal** — `pc_irq_count` pinned at 186, `pc_ack_irq_count` at 180,
`pc_quiesce_ms` climbing monotonically 74.6 s → 144.7 s — and resumed only after the SSR.

**But this is NOT a precursor.** Characterising the whole capture, the interface quiesces in
*recurring* cycles and collapses of **128 s and 169 s occurred earlier with no fatal**:

```
AP 2244.7..2324.9  quiesce 47.7 -> 127.9 s   then reset (woke, no fatal)
AP 2470.1..2590.4  quiesce 48.8 -> 168.9 s   then reset (woke, no fatal)
AP 2735.7..2830.8  quiesce 49.6 -> 144.7 s   then THE FATAL
```

So the fatal merely **landed at the end of a normal-length collapse**; the freeze is not specific to
it. **⇒ axis A15 is WEAKENED, not confirmed:** "the modem never handshakes" is false (§7a), and "the
handshake stalls before the fatal" is not supported. The honest state of A15 is **UNTESTED, with its
two obvious predictions both failing** — the remaining version of it ("the handshake is *present but
semantically wrong*") has no cheap test yet.

### 7.4 Arm 2 — pre-registered and applied

`voice_domain_pref` = **`0x01` (PS_ONLY)** — single variable, the item QMI showed as
`cs-preferred` (stock default `0x02`; the NV file was absent/ENOENT). Read-back `MATCH`.

**Criterion (fixed before the write):** the epoch booting with `voice_domain_pref=0x01` must
survive **> 1200 s with zero fatals AND still be attached on LTE**. One fatal falsifies it.
**Confound guard:** if PS_ONLY stops the modem attaching, the timer will not run (P-PMOS3) and
"no fatal" would be a **false positive** — hence attach is part of the criterion.

⚠ **Method catch:** the staged arm-2 script used `voice_domain_pref = 0x04`, which is **not a
valid enum value** (`QmiNasVoiceDomainPreference` ends at `0x03`). Caught by reading the
in-tree libqmi header *before* running; corrected to `0x01`. Recorded because it would have
produced a silent no-op that looked like a negative.

### 7.6 ★★★ The modem's Q6 power collapse is HEALTHY on OpenWrt — so the stalled "tech" is a specific one

Patch 824 exposes the RPM master stats; `rpm_master_stats`'s `num_shutdowns` is at struct offset **+4**,
so the **MPSS** record is at physical **`0x61154`** (and **APSS** at `0x60154`). `devmem` reads it
directly (the RPM msg RAM is not TrustZone-blocked, unlike modem memory):

```
AP 3257.41   MPSS num_shutdowns = 0x1440 = 5184    APSS num_shutdowns = 0
AP 3317.43   MPSS num_shutdowns = 0x14A1 = 5281    APSS num_shutdowns = 0
             => 97 shutdowns / 60.02 s = 1.616 /s => one Q6 PC cycle every 0.619 s
MPSS active_cores = 1   MPSS xo_count = 0x1252 -> 0x12AF (1.55/s)   MPSS wakeup_reason = 1 (scheduled)
```

**⇒ "the modem cannot power-collapse on OpenWrt" is FALSE.** The modem PC's ~1.6×/s, which matches the
Android control (`/d/rpm_master_stats` MPSS `numshutdowns` 2548 at ~0.13 s/cycle — Android is *faster*,
but both are healthy). Note also **APSS `num_shutdowns = 0`**: the AP never power-collapses.

**This sharpens the mechanism decisively.** `FUN_c0ce7fe0` (§2.1) checks the sleep count **per tech**:
`uVar6 = FUN_c0cd3384()` is *the current tech's* count and the crash needs `uVar6 <= *puVar12`
(that tech not incrementing) **together with** `uVar7 = FUN_c0ce7f98()` (the **minimum** over active
techs) `> 400` and `uVar8 = FUN_c0ce7e58()` (the **minimum** elapsed since *any* tech slept) `> 400`.
With `400 × 2.259187 s = 903.675 s`, that is **exactly the observed clock**. So the fault is not
"the modem doesn't sleep" — it is a stall at the **tech (RAT/ML1) level**, not the SoC level.

**⚠ CORRECTION (same session) — the first reading of this section was wrong.** Disassembling
`FUN_c0ce7f98` at `0xc0ce7f98` shows it is the **MINIMUM over the ACTIVE techs** of the sleep count,
where "active" is the bitmask `(gp+0x3e80 | gp+0x3e84)`:

```
c0ce7f98:  immext(#0xc30fda00)
c0ce7f9c:  r1:0 = combine(##-0x3cf025d8, #0x0)   ; r1 = 0xc30fda28, r0 = 0
c0ce7fa0:  r3 = memw(gp+#0x3e84)
c0ce7fa4:  r4 = memw(gp+#0x3e80)
c0ce7fa8:  loop0(0xc0ce7fb4, #0xf)               ; 15 tech slots
c0ce7fb0:  r3 = or(r4, r3)                       ; r3 = the ACTIVE-TECH MASK
c0ce7fb4:  p0 = !tstbit(r3, r2)                  ; is tech r2 active?
c0ce7fb8:  if (p0.new) jump:t 0xc0ce7fd4         ;   no  -> skip it
c0ce7fc0:  r4 = memw(r1+#0x0)                    ;   yes -> r4 = DAT_c30fda28[tech]
c0ce7fc8:  p0 = cmp.gtu(r0, r4)
c0ce7fcc:  if (p0.new) r0 = add(r4, #0x0)        ; r0 = min(r0, r4)
c0ce7fd8:  r2 = add(r2,#1); r1 = add(r1,#4)      ; :endloop0
```

**A tech whose count is 0 and which IS in the mask would drive the min to 0 and make `uVar7 > 400`
FALSE — it would *prevent* the crash, not cause it.** And `uVar8` is also a **min** ("elapsed since any
active tech slept"), so a *single* stalled tech cannot satisfy `uVar8 > 400` either. **⇒ the crash
requires the tech-level sleep to stall across ALL active techs for 903.675 s** — a system-level
tech-sleep stall, while the SoC-level Q6 PC keeps running at 1.6/s. The "one never-sleeping CDMA tech"
reading is **retracted**.

**The CDMA/1x stack remains the leading *empirical* candidate** (it is in the active set: the
collapse-onset log names `SRLTE is enabled`, `/sd/rat_acq_order` lists **both CDMA RATs**, and the
coredump dog table has `hdrsrch` unblocked — with **no CDMA RF on HMU05**), but the mechanism is now
**"all active techs stop sleeping"**, and *why* they all stop while the SoC PC continues is the open
question.

**⇒ Arms 3/4 are kept** (pre-registered, cheap) but re-scoped: they now test **whether the active-tech
mask governs the beat at all**. A negative is informative rather than surprising.

### 7.7 Arm-2 outcome — **FALSIFIED**, and the clock fit tightened again

The arm-2 epoch (modem up AP **2831.351967**) died at **AP 3734.316776**
(`lte_ml1_common_timer.c:390`) — epoch length **902.964809 s** < 1200 s ⇒ **FALSIFIED**. The confound
guard is satisfied: the modem was verified attached on LTE mid-epoch (`wwan0 10.33.127.161/30` +
default route), so the timer was running and this is not an attach artefact.

**Four consecutive fatals, one AP boot, against `beat(k) = 119.615624 + k × 903.675206`:**

| k | predicted | observed | residual |
| --: | --: | --: | --: |
| 1 | 1023.290830 | 1023.608588 | +0.317758 (`a2_power` — the late site) |
| 2 | 1926.966036 | 1926.966036 | **0.000000** |
| 3 | 2830.641242 | 2830.641649 | **+0.000407** |
| 4 | 3734.316448 | 3734.316776 | **+0.000328** |

Three exact points (k = 2, 3, 4) within **0.4 ms** across a 3734 s span, with the only large residual
on the single fatal whose signature is the known-late `a2_power.c:1189`.

**The CS/voice-domain family is now CLOSED as a suppression lever** — arm 1 (`ue_usage_setting`) and
arm 2 (`voice_domain_pref`) were both applied, read-back verified, persisted across SSRs, confirmed
effective at the QMI level, and neither gates the fault.

### 7.8 Arm 3 — targeting the CDMA/1x stack

Per §7.6 the stall is at the **tech/RAT** level. Arm 3 removes the CDMA/1x tech from the active set:

`c2k_switch_2_srlte` = **`0x00`** (was ABSENT/defaulted; read-back `MATCH`), written at AP ~3960 —
**after** epoch 5 booted (AP 3735.022279), so epoch 5 keeps the default and **the arm-3 epoch is epoch
6**, beginning after the k=5 beat at AP **4637.991654**.

**Criterion (fixed before the write):** the epoch booting with `c2k_switch_2_srlte=0x00` must survive
**> 1200 s with zero fatals AND still be attached on LTE**. Report armed at AP 5950. If negative,
arm 4 removes the CDMA RATs from `/sd/rat_acq_order` (`03 e7 00 05 09 05 03 02 04` → `03 e7 00 03 09 05 03`,
backed up first).

## 8. Measurement hazards found

* **`qmicli -d /dev/wwan0qmi0 …` disturbs ModemManager.** After the failed
  `--nas-set-system-selection-preference` the log shows
  `[modem0] port 'wwan0qmi0' no longer controllable, reprobing` and MM re-creates `modem1`. Any QMI
  read perturbs the system under test ⇒ **the soak watcher is deliberately passive** (dmesg + EFS only).
* **The modem REFUSES `Set System Selection Preference`** (QMI 3 `Internal`) — reconfirming the Doc 229
  RAT-lever negative. The NV route is the only write path.
* **The SSR destroys `/dev/rpmsg0`/`1`** — `/root/diag_bind.sh` must be re-run after every fatal.
* The F3 stream floods `/dev/rpmsg0`; the `diag-efs` skip-loop handles it (Doc 224).

## 9. Achieved vs Expected

| # | expected | achieved | verdict |
| :-- | :-- | :-- | :-- |
| 1 | re-establish the EFS instrument | `diag_bind.sh` + `/root/diag_efs_skip`; reads and a write verified | **MET** |
| 2 | check the NV carry-over | `fsc`/`fsg` byte-identical to the stock dump | **MET** |
| 3 | map the modem's CS/voice config | 54-item + 30-item manifests; 9 CS/CDMA items absent; `rat_acq_order` decoded | **MET** |
| 4 | apply a verifiable config change | `ue_usage_setting` 0→1, **persisted across an SSR**, QMI confirms `data-centric` | **MET** |
| 5 | test whether it suppresses the fatal | arm 1: fatal at **902.654 s** at the tight-clock site ⇒ **FALSIFIED** (§7.1); arm 2 pre-registered + applied (§7.3) | **NOT MET (arm 1)** |
| 6 | fix the 15-minute crash | not claimed | **NOT MET** |
| 7 | *(unplanned)* an independent confirmation that the clock is AP/AON-anchored | the beat **precedes the modem epoch's own boot** by 1.021 s (§7.2) | **MET (bonus)** |
| 8 | *(unplanned)* the clock fit, tightened | three consecutive fatals fit `119.615624 + k×903.675206` with residuals **+0.318 / 0.000000 / +0.000407 s**; P solves to 903.675613 vs 903.675206 = **0.5 ppm** (§7.3) | **MET (bonus)** |
| 9 | *(unplanned)* test axis A15 with a real instrument | `rx_telemetry` acquired; the A2/PC handshake is **ACTIVE**, and its 144.7 s freeze before fatal #3 is **NOT a precursor** (128 s / 169 s collapses occurred earlier with no fatal) ⇒ **A15 WEAKENED** (§7.3) | **MET (negative)** |
| 10 | test arm 2 (`voice_domain_pref=PS_ONLY`) | fatal at **902.964809 s** at the tight-clock site ⇒ **FALSIFIED** (§7.7) | **NOT MET (arm 2)** |
| 11 | *(unplanned)* decide whether the modem's Q6 PC works at all | MPSS `num_shutdowns` **5184 → 5281 in 60.02 s = 1.616/s** (one cycle per 0.619 s); APSS = 0 ⇒ **PC is HEALTHY**; the stall is per-tech (§7.6) | **MET (decisive)** |
| 12 | test arm 3 (`c2k_switch_2_srlte=0`) | applied; criterion pre-registered; epoch 6 (§7.8) | **PENDING** |

## 10. SOP-compliance statement

* **Ground truth first:** every claim was read from the device (`/root/diag_efs_skip` over `/dev/rpmsg0`)
  or from the tracked source (`rmtfs-1.0` from `GitIgnore/dl/`), never inferred. The stock-dump hashes
  were computed on the host from `GitIgnore/MelbonWhiteStock_Dump/` and compared with `md5sum` over the
  device's partitions.
* **Atomic backups + rollback:** `diag_efs put` backs the target up before writing
  (`_nv_item_files_modem_mmode_ue_usage_setting.bak.1790592546`) and always verifies by read-back
  (`MATCH`). The change is one byte and reversible by restoring the backup.
* **Pre-registered criterion, scored honestly:** "zero fatals for > 1200 s"; one fatal falsifies it.
* **Firmware untouched:** the baseband was **not** modified in this document; the device runs pristine
  stock (`b16 57fef19d…`, `b01 b85b86ce…`, `mdt 1a6f9507…`). No `modem-guard` dependency was needed.
* **Ledger updated in the same session** (item 34) and the memory files updated.
* **⚠ Warning stated before the change:** writing modem NV is reversible-but-risky and was announced as
  such before the first `put`.

## 11. Where this points

1. **The NV route is a working lever.** With §5's capability, every CS/CDMA/voice-domain hypothesis is
   now testable non-invasively and persistently — the blocked QMI route is no longer the only option.
2. **The CS/CDMA configuration is a coherent mechanism candidate** (§6) but is **not** the Android
   differential (§6, §2). A positive arm-1 result would have been a *workaround*, and would still have
   left the ~25× rate gap unexplained. Arm 1 is **negative** (§7.1); arm 2 is in flight (§7.3).
3. **§7.2 changes the shape of the remaining problem.** The deadline is a **free-running AP/AON beat**,
   not a modem-uptime counter. Combined with Doc 177 §3.1 ("the beat does not change across an SSR,
   during which the modem is down"), the reading is:

   > the modem must *do something* (most plausibly: complete a power-collapse/sleep) **at least once per
   > ~903.675 s beat**, and the check that fails is evaluated against an **always-on domain clock the AP
   > also owns**.

   That is exactly the shape of an **MCPM/Q6 power-collapse vote deadline** (`project_900s_fatal_anatomy.md`
   §1: "MCPM watchdog + Q6 PC vote failure", the `sleep count not incrmnt` message). It also explains
   why the fault time is invariant to the AP's *traffic* votes but sensitive to AP *idle-ness*.
4. **The levers that this re-opens — and one it closes.** Because the deadline lives in a shared
   always-on domain, an AP-side lever is not excluded by the 5th AP-independence proof — that proof
   shows only that *removing* `qcom_bam_dmux` does not help (no handshake at all still misses the
   deadline). It does **not** test a *correct* handshake. **But §7.3's telemetry weakens A15's two
   obvious predictions**: the handshake is present (168 IRQs / 164 acks / 83 suspends) and it does
   *not* stall before the fatal (a 144.7 s freeze is within the normal 128–169 s range). The surviving
   version of A15 is "present but *semantically* wrong", which has no cheap test yet.
5. **A4 (CONFIG_SUSPEND) stays CLOSED, with a refinement noted, not re-opened.** A4 was killed by
   measurement: Android ran 2423 s with `suspend success count = 0` and 0 fatals. This document adds
   two data points that do **not** re-open it: `/sys/power/state` is **empty** on OpenWrt (no
   system-suspend path), and the AP *does* idle deeply (`cpu-sleep-0` used 2881 s of 3038 s uptime).
   The one variant A4's measurement does not literally cover is **runtime VDD-min (the QCOM `lpm`
   idle path) as distinct from system suspend** — flagged as an open question for the axis owner, not
   as a claim.
6. **The differential remains the AP→modem control stream** (`project_android_differential.md`).

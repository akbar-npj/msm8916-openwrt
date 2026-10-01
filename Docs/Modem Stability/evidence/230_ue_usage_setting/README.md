# Evidence 230 — the modem EFS/NV configuration, the faithful carry-over, and the CS/CDMA-domain lever

**Date:** 2026-09-28
**Device:** HMU05 (UFI001B) dongle · OpenWrt 25.12.5 · kernel 6.12.94 aarch64 · serial `c2b9103c`
**Firmware:** pristine stock HMU05 (`modem.b16 57fef19d…`, `modem.b01 b85b86ce…`, `modem.mdt 1a6f9507…`)
**Issued under:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`
**Instrument:** `/root/diag_efs_skip` (the repaired `diag-efs` EFS2 client) over `/dev/rpmsg0`, bound by `/root/diag_bind.sh`.

This directory tests the last untested non-invasive lead from Doc 229 §6.2 — the **CS / voice-domain
configuration** — and, in the course of it, closes three long-standing open questions about the modem's
NV/EFS and about EFS write persistence.

---

## 1. Headline results

| # | finding | status |
| :-- | :-- | :-- |
| 1 | The OpenWrt port's `fsc` (p1) and `fsg` (p2) are **byte-identical** to the stock dump | **the NV carry-over is FAITHFUL** |
| 2 | `rmtfs` **`fdatasync`s every EFS write** (`rmtfs.c:225`), so writes reach the partition | **EFS writes DO persist** (corrects the memory) |
| 3 | The modem's `mmode`/`nas` NV is **mostly ABSENT** → compiled-in defaults are what run | new |
| 4 | The defaults enable **CDMA2000 / SRLTE / 1x-CSFB / SRVCC / combined EPS-IMSI attach** — none of which HMU05 can do (no CDMA, voice stubbed) | new, mechanism candidate |
| 5 | The modem **refuses `Set System Selection Preference`** (QMI 3 `Internal`) | reconfirms the RAT-lever negative |
| 6 | The NV config is **identical on Android** (same firmware defaults + same carried-over EFS) | ⇒ **the CS/CDMA config is NOT the Android differential** — only a workaround candidate |
| 7 | A direct `qmicli` on `/dev/wwan0qmi0` **disturbs ModemManager** (port reprobe) | measurement hazard |

---

## 1b. Later work in the same evidence directory

| file | finding | status |
| :-- | :-- | :-- |
| `09_k5_confirmation_and_mechanism.txt` | the k=5 beat, the MCPM/NV-gate contradiction | k=5 confirmed |
| `10_arm5_arm3_result.txt` | **arm 5 (QMI DMS `reset`) FALSIFIED** at the k=6 beat (residual +0.105 ms; no `remoteproc0: recovering`, no `MBA booted` ⇒ it never re-initialised the modem); **arm 3 (`c2k_switch_2_srlte=0`) FALSIFIED**; the AP-side power-voting axis CLOSED (invariant + backwards); deployed firmware verified **stock** | both arms falsified |
| `11_ml1_timer_nofatal_patch.txt` | **k=7 confirmed** (n=7, P=903.6752001 s); the **absolute-AON anchor re-derived** from the recovery/uptime variance ratio; the crash site attributed to the **ML1 common-timer callback** `0xc02d7bd0` (10 ERR_FATAL sites, `lte_ml1_common_timer.c:390`, `tmr_slave3`) — **NOT the MCPM supervisor**; and a **verified `jump`-to-epilogue patch** of all 10 sites, deployed with the Doc 226 hash fix. **E1 PASSED** (the patched image authenticates, boots, attaches). E2/E3/E4/E5 pending | deployed, E1 pass |

---

## 2. The EFS carry-over is faithful (closes an open item)

`project_modem_firmware_identical_proof.md` left one thing "still unverified: whether the carry-over was
byte-exact". **It is.** Partition hashes, device vs `GitIgnore/MelbonWhiteStock_Dump/`:

| partition | device md5 (full partition) | stock dump | verdict |
| :-- | :-- | :-- | :-- |
| p1 `fsc` | `0f343b0931126a20f133d67c2b018a3b` | `fsc.bin` `0f343b09…` | **IDENTICAL** |
| p2 `fsg` | `4201f7f39557578e1c8904e11c5529a4` | `fsg.bin` `4201f7f3…` | **IDENTICAL** |
| p4 `modemst1` | `8f09b6e4d0331b6755a0e678281662c4` | `modemst1.bin` `68ac92ff…` | differs (live EFS) |
| p5 `modemst2` | `3dc0a1c4b63ebd1bee0908d53999274d` | `modemst2.bin` `0e04025c…` | differs (live EFS) |

`fsc`/`fsg` are factory-static, so a match proves the EDL carry-over copied them exactly. `modemst1/2`
are the **live** EFS and the modem has written to them in the months since the dump (RPLMN, backoff
info, …) — which is itself the proof that **EFS writes reach the partition**.

## 3. EFS writes DO persist — `rmtfs` fdatasyncs every write

`rmtfs` runs `/usr/sbin/rmtfs -P -s` and holds `modemst1`(p4), `modemst2`(p5), `fsg`(p2), `fsc`(p1)
open. From the rmtfs source (`rmtfs-1.0`, extracted from `GitIgnore/dl/rmtfs-1.0.tar.zst`):

```c
/* rmtfs.c, in the iovec write handler, AFTER the label "respond:" */
if (is_write)
        storage_sync(rmtfd);            /* rmtfs.c:225 */

/* storage.c:249 */
ssize_t storage_sync(struct rmtfd *rmtfd) { return fdatasync(rmtfd->fd); }
```

⇒ **every EFS write iovec is `fdatasync`'d to the backing partition immediately.** The memory's
"an EFS write does NOT survive an AP reboot" is therefore either a different item's behaviour (an item
the modem re-derives at boot) or a bad measurement — **not** a property of the write path.
(`-s` separately enables rmtfs's *rproc* sync, i.e. rmtfs gating the modem's start/stop.)

## 4. The modem's NV is mostly ABSENT — the defaults are what run

The EFS carries plaintext **manifests** of the NV items each module reads:

* `/nv/item_files/conf/mmode.conf` — **54 items**
* `/nv/item_files/conf/nas_mm.conf` — **30 items**

Only ~10 `mmode` items actually exist as files; the rest are **ENOENT** (`err 0x2`). Absent items fall
back to the firmware's compiled-in defaults — and the firmware is byte-identical to Android's.

**Present (`mmode/`)** — read over EFS2:

| item | value | meaning |
| :-- | :-- | :-- |
| `ue_usage_setting` | `00` | **voice-centric** (matches QMI) |
| `device_mode` | `00` | — |
| `sms_domain_pref` | `01` | CS-domain SMS |
| `sms_mandatory` | `01` | **SMS is MANDATORY** |
| `sms_only` | `00` | — |

**Absent (defaulted) — and all CS/CDMA-relevant:**

`voice_domain_pref` · `supplement_service_domain_pref` · `c2k_switch_2_srlte` ·
`allow_csfb_upon_ims_reg` · `lte_do_irat_duration` · `3gpp2_ps_call_optimization` · `sxlte_timers` ·
`allow_sms_in_ecbm` · `sms_over_s102` · `sd/1xcsfb_ecbm_status` · `sd/1xcsfb_call_end_opt` ·
`sd/c2k_resel_splmn_supp` · `1xsrvcc_stn_sr_number` · `nas/l21xsrvcc_support` ·
`nas/nas_srvcc_support` · `nas/sglte_nas_nv_config` · `nas/lte_nas_srlte_esr_time` ·
`nas/emm_combined_proc` · `nas/isr`

**QMI read of the effective state** (`--nas-get-system-selection-preference`):

```
Mode preference: 'lte'
Service domain preference: 'cs-ps'
Usage preference: 'voice-centric'
Voice domain preference: 'cs-preferred'
Acquisition order preference: 'lte, umts, gsm, cdma-1x, cdma-1xevdo'
```

**`/sd/rat_acq_order` = `03 e7 00 05 09 05 03 02 04`** — decoded as
`{ver=0x03, ?, num=0x0005, rats=[09,05,03,02,04]}`, i.e. `09=lte, 05=umts, 03=gsm, 02=cdma-1x,
04=cdma-1xevdo`. The order still contains **both CDMA RATs** even though `--dms-get-capabilities` says
`umts, lte` and HMU05 has no CDMA RF.

## 5. The mechanism candidate, and why it is only a *workaround*

The DIAG capture (§12 of the consolidated model) recorded, at every collapse onset:

```
=SD= SDSS:FRFL: Updating core ss= %d, lte_cs_cap= %d ext_srv_info =%d
** Activate GWL opr script = ssscr_gw_opr_srv_info **
SRLTE is enabled
```

and the crash coredump's dog table has **`hdrsrch` (1x-EVDO search) as one of only six unblocked,
59/60-expiring tasks**. So at runtime the modem runs a **CS/CDMA2000 voice path it can never complete**.
That is a plausible "the tech never reaches a stable sleep state" mechanism — which is exactly the
upstream condition the MCPM watchdog (`sleep count not incrmnt`, §2 of the consolidated model) fires on.

**But it cannot be the Android differential**: the firmware defaults are identical and the EFS was
carried over from the same stock image, so Android runs the same CS/CDMA configuration. It is therefore
a **workaround candidate**, not an explanation of the ~25× rate gap.

## 6. The experiment

**Pre-registration.** Change `ue_usage_setting` `0x00` (voice-centric) → `0x01` (data-centric) — the
single NV item that maps 1:1 onto the QMI-reported `Usage preference`, so the change's effect is
**independently verifiable** via QMI. Success criterion: **zero fatals for > 1200 s** with the modem
still connected on LTE. Falsified by one fatal.

**Applied 2026-09-28 10:49** (AP uptime 574.89 s, 0 fatals):
`/root/diag_efs_skip put /tmp/ue1.bin /nv/item_files/modem/mmode/ue_usage_setting` →
`backing up -> _nv_item_files_modem_mmode_ue_usage_setting.bak.1790592546`, `wrote 1 bytes;
read-back 1 bytes -> MATCH`.

**The value did NOT change live** (QMI still reports `voice-centric` at 10:46 and after) ⇒ the modem
reads it at NAS init, i.e. **a modem restart is required for the change to take effect**. The natural
~900 s fatal provides that restart, so the experiment is: write → natural fatal + SSR → the next MPSS
epoch boots with the new value → observe whether the next fatal is suppressed.

**Arm 2 (applied 2026-09-28, after arm 1 was FALSIFIED — see `05_arm1_result.txt`):**
`voice_domain_pref = 0x01` (**PS_ONLY**), a **single variable**, the item QMI showed as
`cs-preferred`. The epoch that boots with it must survive **> 1200 s with zero fatals AND still be
attached on LTE** — the attach clause is a **confound guard**, because P-PMOS3 shows a non-attached
modem never runs the timer, so a bare "no fatal" would be a false positive.

⚠ **CORRECTION to an earlier plan:** an earlier draft of this experiment staged a *combined* arm with
`c2k_switch_2_srlte=00`, `allow_csfb_upon_ims_reg=00` and **`voice_domain_pref=04`**. **`0x04` is not a
valid enum value** — `QmiNasVoiceDomainPreference` (in-tree libqmi header) is `0x00 CS_ONLY`,
`0x01 PS_ONLY`, `0x02 CS_PREFERRED`, `0x03 PS_PREFERRED`. Using it would have been a silent no-op that
looked like a negative. Corrected to `0x01` and reduced to one variable.

## 7. ARM 1 RESULT — FALSIFIED (see `05_arm1_result.txt`)

The epoch that booted with `data-centric` died at **902.654382 s** of modem uptime at
**`lte_ml1_common_timer.c:390`** — the **tight-clock site** — with LTE attached. Criterion was
"> 1200 s"; **FALSIFIED**. `ue_usage_setting` is CLOSED.

**Bonus (same capture): the clock is AP/AON-anchored, re-confirmed from a MODEM restart.** Fatal #1
`a2_power.c:1189` @AP 1023.608588, fatal #2 `common_timer.c:390` @AP 1926.966036. Against
`P = 903.675206 s`: beat = **1023.290830** (residuals +0.317758 and 0.000000). The modem epoch started
at AP **1024.311654**, so **the beat was scheduled 1.021 s BEFORE the epoch existed** ⇒ the deadline
cannot be modem-anchored. The beat at 119.615624 s was **survived** ⇒ the condition is accumulated.

## 7a. NEW INSTRUMENT — the `qcom_bam_dmux` A2/PC telemetry

`/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry`
(patch 808 + 825). **First reading (AP ~2180 s): the A2/PC handshake IS ACTIVE.**

| counter | value | reading |
| :-- | :-- | :-- |
| `pc_irq_count` | 168 | the modem raises its PC line often |
| `pc_ack_irq_count` | 164 | the AP acks |
| `pm_suspend_attempts` / `_completions` | 83 / **80** | 3 suspends never completed |
| `pc_timeout_count` | **1** | one ack timeout (patch 825's class) |
| `pc_state` / `pc_line_level` | 0 / **0** | **agreeing — no desync** |
| `rx_tearing_down` | 1 | coherent with `pc_state: 0` (quiesced while collapsed) |
| `runtime_status` | `suspended` | the link is idle-collapsed, which is *normal* |

⇒ **"the modem never handshakes" is NOT the mechanism.** `pm_last_suspend_ms: 981` is the *modem's*
collapse latency (AP unvote → falling edge), not an AP defect. A 5 s sampler
(`scratch/arm2/telemetry_log.sh` → `/tmp/telemetry.log`) runs **across the next fatal** to test
whether the handshake **stalls before the beat** — the open question for axis A15.

## 7b. Measurement hazards found

* **`qmicli -d /dev/wwan0qmi0 …` disturbs ModemManager.** After the failed `--nas-set-system-selection-preference`
  the modem log shows `[modem0] port 'wwan0qmi0' no longer controllable, reprobing` and MM re-creates
  `modem1`. Any QMI read therefore perturbs the very system under test — **the soak watcher is
  deliberately passive** (dmesg + EFS reads only, no QMI).
* **The F3 stream floods `/dev/rpmsg0`** — the `diag-efs` skip-loop handles it (Doc 224).
* **The EFS instrument requires the DIAG binding**; `/dev/rpmsg0` did not exist until
  `/root/diag_bind.sh` was re-run.

## 7c. ★★★ The modem's Q6 power collapse is HEALTHY on OpenWrt

Patch 824 exposes the RPM master stats. `struct rpm_master_stats` has `num_shutdowns` at offset **+4**,
and the DTS slices are **APSS `0x60150` / MPSS `0x61150` / PRONTO `0x62150`** (0x50 bytes each, in the
RPM msg RAM — **not** TrustZone-blocked, so plain `devmem` works, unlike modem memory):

```
devmem 0x61154 32     # MPSS num_shutdowns
devmem 0x60154 32     # APSS num_shutdowns
```

| sample | AP | MPSS `num_shutdowns` | APSS `num_shutdowns` |
| :-- | --: | --: | --: |
| 1 | 3257.41 | `0x1440` = **5184** | `0` |
| 2 | 3317.43 | `0x14A1` = **5281** | `0` |

**Δ = 97 in 60.02 s = 1.616 /s ⇒ one Q6 PC cycle every 0.619 s.** Also `MPSS active_cores = 1`,
`xo_count` 1.55/s, `wakeup_reason = 1` (scheduled, not rude). Android control: MPSS `numshutdowns`
2548 at ~0.13 s/cycle.

**⇒ "the modem cannot power-collapse on OpenWrt" is FALSE.** This kills the VDD-min/suspend reading of
the fault (already closed as a differential — Android shows `vmin count: 0`, `xosd count: 0`), and it
**rules out "the modem doesn't sleep" as the mechanism.**

**What it leaves.** `FUN_c0ce7fe0` is **per-tech**: the crash needs `uVar6 = FUN_c0cd3384()` (that
tech's count) `<= *puVar12`, together with `uVar7 = FUN_c0ce7f98()` (min over active techs) `> 400` and
`uVar8 = FUN_c0ce7e58(0xf, 0xc8881)` (min elapsed since *any* tech slept) `> 400`. With
`400 × 2.259187 s = 903.675 s` that is **exactly the clock**. So the stall is at the **tech (RAT/ML1)
level**, not the SoC level. The prime candidate remains the **CDMA/1x stack** — active (`SRLTE is
enabled`, `/sd/rat_acq_order` lists both CDMA RATs, `hdrsrch` unblocked in the dog table) with **no
CDMA RF on HMU05**.

## 8. Files

| file | what |
| :-- | :-- |
| `README.md` | this document |
| `01_partition_hashes.txt` | the device-vs-stock partition hash comparison |
| `02_nv_mmode_reads.txt` | the `mmode/` item reads |
| `03_manifests.txt` | `mmode.conf` and `nas_mm.conf` |
| `04_ue_write_and_soak.log` | the write, the verification, and the soak |
| `05_arm1_result.txt` | the arm-1 result (FALSIFIED), the clock re-confirmation, the arm-2 pre-registration |
| `06_telemetry_across_beat.txt` | the A2/PC telemetry window around the AP 2830.641649 beat + the recurring-quiesce characterisation |
| `07_arm2_result.txt` | the arm-2 result (FALSIFIED), the four-beat clock fit, and the arm-3 pre-registration |

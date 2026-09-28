# 193 evidence — the P-W and P-MM scoring soak on the patch-825 + patch-0005 build

**Window opened:** 2026-09-23T09:34:38Z, AP uptime **585.08 s**, boot `b08763ad`.
**Device:** HMU05 serial `c2b9103c` at `192.168.8.1` (USB ethernet, MAC `02:00:c2:b9:10:3c`).
**OpenWrt:** 25.12.5, r33051-f5dae5ece4, kernel 6.12.94.
**Purpose:** score the two pre-registrations that were **deployed but unscored** —
Part A (P-W1/P-W2/P-W3, patch 825) and Part B (P-MM1/P-MM2/P-MM3, patch 0005) — from
`../112_bam_reinit_ab/preregistration_2026-09-22_build.md`. **Thresholds were frozen
2026-09-22 and are not re-tuned here.**

---

## 1. Deployment verification (done before the window was opened)

Nothing was assumed from a filename, a timestamp, or a doc's narrative.

| artifact | device value | verdict |
| :-- | :-- | :-- |
| `qcom_bam_dmux.ko` | `9c08871ff0d29f714acfa89b41893898` | **== Doc 187 §3's patch-825 image artifact** |
| `qcom_bam_dmux` srcversion | `A9AC55FEA83B66B8EC1DD97` | matches the recorded post-825 fingerprint |
| `/overlay/upper/lib/modules/…/qcom_bam_dmux.ko` | absent | no shadowing — the image's module is what runs |
| `/usr/sbin/ModemManager` | `8182d40d91396df70e3f02ca91ab7a6b` | **patch 0005 live** (see §1.1) |
| `/lib/netifd/proto/modemmanager.sh` | `5829474fe9354751435ecb94c03f4508` | == host overlay file, byte-identical (WS2 live) |
| `/etc/modemmanager-log-level` | absent | `LOG_LEVEL=INFO` — the Doc 185 requirement for the timing soak |
| `/overlay/pcfine.sh` | `3231b1185e7ff67b24459ae6937ce5ac` | == `soak_instruments_2026-09-23/` (sampler **v4**) |
| `/overlay/logroll.sh` | `3cd1748c033252f5668e9c830f61af5e` | == recorded |
| `/overlay/soak_start.sh` | `7c334dfde6300464c843b0baa925b1ae` | == recorded |

### 1.1 Patch 0005 — proven by the loadable image, not by `md5sum`

`md5sum` cannot prove a deployment here (Doc 187 §5). The device binary was pulled and
compared to the build artifact by `../112_bam_reinit_ab/verify_deployed_artifacts.py`:

```
PT_LOAD flags=0x5  vaddr=0x400000  size=0x26e4c8   DIFFERS
  differing bytes: 5 / 2548936 (0.0002 %)
  off 0x000028 (3 B)  <-- ELF header: section-header table descriptor (e_shoff)
  off 0x00003c (1 B)  <-- ELF header: section-header table descriptor (e_shnum)
  off 0x00003e (1 B)  <-- ELF header: section-header table descriptor (e_shstrndx)
PT_LOAD flags=0x6  vaddr=0x66f6b0  size=0x6604     IDENTICAL
```

Five bytes, all in the ELF header fields that merely *describe* the section header table
which OpenWrt strips on the way into the image. That is the **exact** signature Doc 187 §5
established for the patched build. Device copy archived as `ModemManager.device`.

### 1.2 A stale md5 in Doc 185's evidence table — corrected

Doc 185 records the WS2 script as `76bcb52f732c5371ed80849b208c09f5`. The device has
`5829474fe9354751435ecb94c03f4508`. This is **not** a deployment failure: `git show`
confirms `76bcb52f…` is the file at `990c645` (Doc 185's own commit) and `5829474f…` is the
file at `f19d850` ("WS2: bound the modempath poll in WALL TIME, not iteration count"), which
landed *after* Doc 185. The device matches the **current** revision.

---

## 2. The window

| item | value |
| :-- | :-- |
| boot_id | `b08763ad-1004-4203-be44-ec674b3c4289` |
| window opened | uptime **585.08 s** (logroll's `BOOT` marker) |
| sampler | `/overlay/pcfine_b08763ad.txt` |
| logroll | `/overlay/logroll_b08763ad.log` |
| `t0` telemetry (uptime 552.48) | `pc_timeout_count 0`, `pm_resume_attempts 54`, `pc_vote_tx_count 54`, `pc_unvote_tx_count 53`, `pm_suspend_attempts 53`, `tx_defer_preserved 68`, `tx_defer_wiped 0` |

**A fatal at AP 429.147359 s (`a2_taskq.c:759`) is PRE-WINDOW and is excluded.** It landed
before logroll opened (585.08 s), so logroll holds 0 ModemManager lines from it — asserted,
not assumed.

### 2.1 Instrument shape asserted before trusting the data

* sampler: 704 records in the first ~75 s (~9.4 rec/s); **16 fields**; the v4 clock bracket
  is working — `…|584.08 5599 584.09` (UP and UP1 differ by 0.01 s, so the ~73 ms
  misalignment is bracketed). Format: 14 key fields `|` UP qm UP1.
* logroll: capturing `PMKNOB auto/1000/suspended <-> active` transitions with uptime.
* **ModemManager's timeline does reach `logread`** — verified from the pre-window SSR
  (remove `09:32:02` → add `09:32:03` → `state available` `09:32:11` → bearer `09:32:16`),
  which is the control Part B depends on.

---

## 3. Reboot resilience — `rc.local` autostart armed

A ~900 s fatal cadence over a multi-hour soak, with a ~3-5 % AP-hang race per SSR, makes a
mid-soak watchdog reset likely; without autostart the window would silently end. The guard
documented in `soak_instruments_2026-09-23/README.md` §4 was therefore added:

| | md5 |
| :-- | :-- |
| `/etc/rc.local` **before** (stock, archived `rc.local.before`) | `6f8bfdaf36d3ec4aa9be6e96fa61d32b` |
| `/etc/rc.local` **after** (guarded autostart) | `adf8e114f334f5ad14b2eecd86a14e31` |

`sh -n` clean. `soak_start.sh` is pidfile-guarded, so a normal boot is a no-op. Output is
per-`boot_id`, so a reset never truncates the pre-hang tail. **Disarm:** delete the block, or
`soak_start.sh stop` for the current boot.

---

## 4. Both scorers calibrated BEFORE any post-fix data existed

Calibration is against the frozen pre-fix controls, with the control file's md5 asserted.

### 4.1 `score_pw.py` (Part A, patch 825) — control boot `59d9c272`

Input `../112_bam_reinit_ab/pcfine_final_boot59d9c272.txt`,
md5 `d616346c6dca6fe6c4052998330424cb`. Output `score_pw_calibration_control.txt`.

```
CALIBRATION REPRODUCES the frozen control
  race      expected  838/19   got  838/19   OK
  race_td   expected  174/0    got  174/0    OK
  teardown  expected   41/7    got   41/7    OK
  stale     expected   25/10   got   25/10   OK
  totals    expected 1078/36   got 1078/36   OK  OK
```

It imports the SAME resume census as Doc 182/183/184 (`resume_trigger.census`), so "what is a
resume" is not re-derived. **Pooling across boots sums the per-capture class tallies — it never
concatenates the files**, which would fabricate a resume at each boot boundary where the
counters reset to 0. Pooling is legitimate because the kernel state (patch 825) is held.

### 4.2 `score_mm_phases.py` (Part B, patch 0005) — control boot `a9fd907c`

Input `../170_two_fixes_for_the_smd_poll_uaf/bootA_a9fd907c_logroll.log.gz`,
md5 `fa4d67a47809ebc804f342418d93fb75`.

```
SSRs found: 11
  add -> created   median=4s  (9x4, 2x5)
  created -> SIM    median=5s  (9x5, 2x6)
  add -> up         median=16s (10 of 11 in 14..17, one at 25)
  ports             9/9 in every SSR
  P-MM1 FALSIFIED | P-MM2 CONFIRMED | P-MM3 FALSIFIED
```

This is exactly the calibration Doc 185 §9 prescribes, so the pre-fix boot scores as the
control it is.

---

## 5. Frozen thresholds and required n (from the 2026-09-22 pre-registration)

| id | prediction | falsified if | required n |
| :-- | :-- | :-- | :-- |
| **P-W1** | STALE (`pl=0,td=0`) timeout rate 40.0 % (10/25) → **< 5 %** | ≥ 5 % | ≥ 20 STALE resumes |
| **P-W2** | overall `pc_timeout_count` rate 3.3 % (36/1078) → **< 2.0 %** | ≥ 2.0 % | ≥ 500 resumes |
| **P-W3** | control: race (`pl=1,td=0`) stays **2.3 % ± 1.5 pp** (0.8–3.8 %) | outside band | order of 838 |
| **P-MM1** | `add → modem created` 4.0 s → **≤ 2.0 s** | median > 2.5 s | ≥ 5 SSRs |
| **P-MM2** | control: every SSR still yields **all 9 ports** | any SSR < 9 ports | ≥ 11 SSRs |
| **P-MM3** | `add → Interface up` falls **≥ 1.5 s** vs 16 s median | median falls < 1.0 s | ≥ 10 SSRs |

**Neither part is claimed to fix the 903 s fatal.** Part A is a ~2 s latency defect per event;
Part B is the ~16 s userspace bearer rebuild. A statement about the fatal must come from a
fatal count, not from these files.

---

## 6. Files

| file | what |
| :-- | :-- |
| `t0_baseline_and_rclocal.txt` | the `t0` telemetry, rc.local before, overlay free space |
| `rc.local.before` | the stock rc.local (md5 `6f8bfdaf…`) |
| `ModemManager.device` | the pulled binary, for §1.1 |
| `score_pw.py` | the Part A scorer (`--control`, `--n`, multi-capture pooling) |
| `score_pw_calibration_control.txt` | §4.1 output |
| `monitor_soak.sh` | poll/pull/score loop; exits early at n |
| `monitor_progress.log` | the poller's running log |
| `score_pw_latest.txt` | most recent pooled score |

## 7. How to score

```sh
cd "Docs/Modem Stability/evidence/193_p825_0005_soak"
python3 score_pw.py /tmp/soak_p825_caps/pcfine_*.txt          # Part A
python3 ../112_bam_reinit_ab/score_mm_phases.py <logroll.log> # Part B
```

---

## 8. INTERIM results (not verdicts — below the pre-registered n)

Recorded 2026-09-23 ~10:10Z, boot `b08763ad`, uptime ~2 730 s. **Neither part has reached its
pre-registered n**, so nothing here is a verdict. It is recorded because the Part A mechanism is
already legible and would be lost if the session ended.

### 8.1 Part B (patch 0005) — moving the right way, n = 8 SSRs

| phase | pre-fix | post-fix | prediction | n / required |
| :-- | --: | --: | :-- | :-- |
| `add → modem created` | 4 s | **2 s** | P-MM1 ≤ 2.0 s → **CONFIRMED** | 8 / ≥5 |
| `add → Interface up` | 16 s | **12 s** | P-MM3 ≥1.5 s fall → direction hit | 7 / ≥10 |
| ports | 9/9 | **9/9** | P-MM2 control → holding | 8 / ≥11 |

SSR 4 was a **double fatal** (09:45:21 superseded 19 s later by 09:45:40), so it has no
`Interface up` line. `score_mm_phases.py` was made tolerant of that (phases individually
optional) and the superseded SSR is flagged in its output. **Re-verified: the change is
byte-identical on the pre-fix control**, so the calibration still holds.

### 8.2 Part A (patch 825) — the timeout MOVED, it did not vanish

The reconcile branch **is** live and firing: 8 × `bam_dmux: RX watchdog: pc_state=1 but pc line
low (stale edge), reconciling` in dmesg, `pc_resync_count: 4`.

Splitting the census by `(pl, ps, td)` exposes what the pre-registered `(pl, td)` classes hide:

**Pre-fix control, 1078 resumes** — note there is **no `pl=0 ps=0 td=0` row at all**:

| `pl` | `ps` | `td` | resumes | timeouts | rate |
| --: | --: | --: | --: | --: | --: |
| 0 | 1 | 0 | 25 | 10 | **40.0 %** ← the pre-registered class *and* patch 825's target |
| 0 | 0 | 1 | 41 | 7 | 17.1 % |
| 1 | 1 | 0 | 838 | 19 | 2.3 % |

**Post-fix, 167 resumes**:

| `pl` | `ps` | `td` | resumes | timeouts | rate |
| --: | --: | --: | --: | --: | --: |
| 0 | 1 | 0 | **1** | **0** | 0.0 % ← the patch's target: 25 → 1 |
| 0 | 0 | 0 | **6** | **5** | **83.3 %** ← **absent from the control's 1078 resumes** |
| 1 | 1 | 0 | 135 | 0 | 0.0 % |

In the control the pre-registered class `(pl=0, td=0)` **was** the patch's target
`(ps=1, pl=0)` — all 25 events. Post-fix that target is nearly gone, but the `(pl=0, td=0)`
bucket is now filled by `ps=0` events that never occurred pre-fix. So **P-W1 as written reads
71.4 % (5/7) against a target of < 5 %** — heading for FALSIFIED at n = 7 ≪ 20. Overall rate
2.99 % (5/167) vs 3.34 % pre-fix; P-W2 needs < 2.0 % at n ≥ 500.

**Reading, stated as a hypothesis not a result:** the reconcile clears the stale cache, which
turns a `ps=1, pl=0` resume into a `ps=0, pl=0` one — and that still times out, because the
modem is genuinely collapsed and cannot ack. The patch changes *which branch* the driver takes,
not the outcome. If that holds at n ≥ 20, P-W1 fails **informatively**: the pre-registration
assumed the stale cache was the cause of the timeout, and the migration shows it was a
*correlate* of the resume-while-collapsed state.

**Caveat on the post-fix rate.** 167 resumes is small, and the post-fix fatal cadence is much
faster than the control's (8 SSRs in 644 s ≈ 80 s/SSR vs ≈613 s/SSR). If the instruments
themselves raise the fatal rate, that is a confound for the *fatal count* — not for the pc-ack
class rates, which are per-resume — but it should be checked before the final report.

---

### 8.3 Update at 508 resumes / 15 SSRs — one verdict scored, one shape check passed

Capture `pcfine_b08763ad.txt`, window 584.1 .. 8819.3 s, boot `b08763ad`, no reboot.

**Shape check first — the census is not truncating timeouts.** The census attributes a timeout
by finding a `pc_timeout_count` increment within `span = 3.0 s` *after* a resume increment. If
patch 825 delayed the timeout past 3 s, every class would deflate and both P-W2 and P-W3 would
be artefacts. Checked against the counter itself:

| window | `pc_timeout_count` delta (truth) | census-detected | missed | max lag resume→timeout |
| :-- | --: | --: | --: | --: |
| control `59d9c272` | 36 | 36 | **0** | 2.23 s |
| post-fix `b08763ad` | 6 | 6 | **0** | 2.54 s |

Nothing is missed, and the lag distribution did not move. **The drop is real, not a window
artefact.** (Control lag median 2.10 s, post-fix 2.18 s — both are the `UL_WAKEUP_TIMEOUT_MS`
250 ms + collapse latency, as before.)

**P-W2 — SCORED, CONFIRMED.** 6 / 508 = **1.18 %** against a pre-fix **3.34 %** (36/1078) and a
pre-registered target of `< 2.0 %`, at n = 508 ≥ 500. Note the post-fix window is if anything
*harsher*: 15 in-window fatals / 8235 s (1 per 549 s) vs the control's ring density, and 33.9
resumes per fatal vs 51.3 — i.e. more collapses per unit time, yet fewer timeouts.

**Stability of the rate.** The window contains an early burst (SSRs 1–8 within ~650 s) followed
by a steady cadence. The timeout rate is flat across the split, so the result is not a burst
artefact:

| segment | resumes | timeouts | rate |
| :-- | --: | --: | --: |
| early (585–6990 s) | 441 | 5 | 1.13 % |
| late (7001–8819 s) | 67 | 1 | 1.49 % |

**P-W1 — UNSCORED, and likely unreachable in this window.** The pre-registered class
`(pl=0, td=0)` now holds **7** events (5 timeouts = 71.4 %) at n = 7 ≪ 20. The class *shrank*:
25/1078 = 2.32 % pre-fix → 7/508 = 1.38 % post-fix, so reaching n = 20 needs ≈1450 resumes
≈4.2 h more, against a monitor cap ≈3.3 h away. Per the pre-registration this must be reported
as **n and count, never "it stopped happening"**.

**P-W3 — heading to FALSIFIED.** Race class `(pl=1, td=0)`: **0 / 424** timeouts vs pre-fix
19 / 838 = 2.3 %. If the rate had held, ~9.8 events were expected (Poisson p ≈ 5.5e-5). At 0/424
the rule-of-three 95 % upper bound is **0.71 %**, already below the 0.8 % lower edge of the
pre-registered band 0.8–3.8 %. This is P-W3 doing its job: it says the patch moved the healthy
path, not only the stale one.

**The open question this raises — do not resolve it by assumption.** Patch 825's reconcile fires
only when `ps=1 && !pl`; it *cannot* fire in the race class (`pl=1`). So the patch's own code
path does not explain a 19 → 0 collapse in the race class. Two candidates, unresolved:

1. The patch also changed the RX-watchdog path (the pre-registration notes its resync at `:1304`
   is gated on `!pc_state` and its awake branch at `:1381` never re-reads the wire) — in which
   case the race-class drop may be a genuine second effect.
2. `pl` is a **live sample** of the wire, quantised to the sampler's ~106 ms tick and taken at
   the record containing the resume. A change in *when* the vote happens can move an event
   between `pl=1` and `pl=0`. The new `pl=0 ps=0 td=0` class — **6 events, 5 timeouts, absent
   from the control's 1078 resumes** — supports "events moved" rather than "events vanished".

Until one of these is settled, the class *split* must not be quoted as a mechanism, and the
race-class drop must not be quoted as either a benefit or a defect. The **total** (P-W2) is
counter-verified and stands on its own.

**Crash status: not fixed, as pre-registered.** 16 fatals in ~2.4 h (1 pre-window + 15 in). The
logroll `add` timestamps are wall clock (seconds-of-day) and the post-burst spacing is
**898 / 904 / 902 / 901 / 902 s** — the ~903.675 s cadence reasserting. Neither patch claims the
fatal, and the fatal count here is the evidence that the claim is correctly scoped.


## 9. The post-soak firmware: three changes built and verified (2026-09-23 18:54)

Built with `./build.sh build hmu05` (full kernel re-prepare; `BUILD EXIT=0`, 572 s).
`./build.sh guard` afterwards: *"In sync"*.

| # | change | how it was verified | result |
|---|--------|---------------------|--------|
| 1 | `kernel.printk = 6 4 1 6` persisted in the **image** | `unsquashfs -l` on `squashfs-system.img` lists `etc/sysctl.d/99-console-loglevel.conf`; extracted file's last line is `kernel.printk = 6 4 1 6` | **PASS** |
| 2 | patch 817 (the temporary `q6v5-trace`) removed | `grep -c q6v5-trace` = **0** in the built `Image`, in the built `qcom_q6v5_mss.ko`, and in the module extracted from `squashfs-system.img`; `git status` shows `D msm89xx/patches/817-...patch`; series runs 815, 816, 818…825, 999 | **PASS** |
| 3 | patch 822 (SMD drain pollers before free) in the kernel | `grep -c "pollers after 2s"` = **1** in the built `Image` **and** = **1** in the kernel decompressed out of `squashfs-boot.img` | **PASS** |

**Verification method note.** The flashable artifacts are compressed, so a raw `grep` on them is
meaningless: `squashfs-boot.img` is an **Android boot image** (`ANDROID!` magic, gzipped kernel
payload) and `squashfs-system.img` is squashfs. The checks above decompress the boot payload
(zlib `wbits=31`; the result is byte-for-byte the 15 026 184-byte build `Image`) and extract files
from squashfs. `qcom_smd_drain_channel_pollers` does **not** appear as a symbol name in the Image —
that is expected and is not a failure: the function is `static` and `CONFIG_KALLSYMS_ALL` is off,
so the string `"pollers after 2s"` is the correct evidence of presence.

**Artifacts** (`openwrt/bin/targets/msm89xx/msm8916/`, version `r33051-f5dae5ece4`):

```
sysupgrade.bin  3a0133fe8b745928f08dc23f377306882836202fad2b6b4de787bd8a7f179035
boot.img        6a500bfd17420f7780bcac4057911e2b5c132f5828e55d25a954d766cd437c8c
system.img      c685ece70101c664f689cffb7c3b2f83acecf12cf049f58d71fdb31a03ddd596
firmware.zip    ee7012769b0c1e70619be5104675cf233594fab3344d0084411e07be05aa6304
```

**Why change 1 is in a sysctl.d file and not `/etc/rc.local`.** Doc 164 set the loglevel at
runtime and persisted it in `/etc/rc.local`, which lives on the **overlay** and is therefore lost
on every reflash — and the loss is silent, because the symptom is a slower kernel, not an error.
The pre-soak `rc.local.before` confirms the file was **already stock** (md5
`adf8e114f334f5ad14b2eecd86a14e31`) when this soak began, i.e. the mitigation had already been
dropped by the reflash that started it, and nothing here removed it. The sysctl.d file is part of
the **image**, so it survives the flash.

**What this does and does not claim.** Restoring `console_loglevel = 6` is the correct **control**
for the recovery-window inflation (0.333 s at level 7 vs a recorded 0.119–0.138 s at level 6; Doc
168 measured the ~8 ms/probe console cost moving the teardown past the modem power-down). It is
**not** the cure for the AP hang — patch 822 is the candidate for that, and patch 822's
"deterministic" claim was already FALSIFIED in the project record. The 3–5 % hang rate needs
n ≥ 60 SSRs to say anything, so the post-flash soak must run to that bar before any "no hangs"
statement.

---

## 10. WINDOW CLOSED 2026-09-23 17:28Z — closed by reflash, NOT by reaching n

**How it ended.** The monitor's last iteration was **iter 36 at 14:51:07Z** (`up = 19 570.86 s`,
`fatals = 22`, `ssrs = 33`); the last scorer write was **15:06Z**. At **17:28Z** no
`monitor_soak.sh` process was alive and no `ssh` poller was running — **the monitor died ~2.4 h
before the window was closed**, so the soak was not collecting at close. The window is closed by the
reflash to the §9 three-change firmware, not by the pre-registration being satisfied.

**Final scored state — `score_pw_latest.txt` / `score_mm_latest.txt` at close.** Part A capture
`pcfine_b08763ad.txt`, window **584.1 .. 19 552.2 s**, boot `b08763ad`, no reboot.

| id | verdict | final evidence | required n |
| :-- | :-- | :-- | :-- |
| **P-W1** | **CLOSED — UNSCORED (UNDERPOWERED)** | STALE 9/11 = **81.8 %** vs control 40.0 % (10/25), target < 5 % | 20 STALE → got **11** |
| **P-W2** | **CONFIRMED** | overall 15/888 = **1.69 %** vs control 3.34 % (36/1078), target < 2.0 % | 500 → got 888 |
| **P-W3** | **FALSIFIED** | race 1/729 = **0.1 %**, pre-registered band 0.8–3.8 % (control 2.3 % = 19/838) | ~838 → got 729 |
| **P-MM1** | **CONFIRMED** | `add → created` median **2 s** ≤ 2.0 s | 5 → got 33 |
| **P-MM2** | **CONFIRMED** | **9/9 ports in all 33 SSRs** | 11 → got 33 |
| **P-MM3** | **CONFIRMED** | `add → up` median **12 s**, delta **+4 s** vs pre-fix 16 s | 10 → got 32 (1 excluded: double fatal) |

**P-W1 is recorded as UNDERPOWERED, and its threshold is NOT re-tuned.** Per the standing rule, a
post-hoc band would be EXPLORATORY and cannot rescue a pre-registration. What may be reported is
**n and count, never "it stopped happening"**: the class held **11** events (9 timeouts) at close,
against a required 20. The direction is unambiguous and **negative** — 81.8 % at n = 11 versus a
40.0 % control and a < 5 % target — and it continued *away* from the target as the window grew
(§8.3 had it at 71.4 %, 5/7). Reaching n = 20 would have needed roughly another **~4.5 h**
(STALE accrues at 11/888 ≈ 1.24 % of resumes; 888 resumes took ≈ 5.4 h), i.e. ~2× the run. **So
patch 825's STALE claim is unresolved-but-failing: it must not be quoted as a fix.**

**P-W3's FALSIFIED verdict is the one that constrains interpretation.** The race-class rate fell
19/838 (2.3 %) → 1/729 (0.1 %), outside the pre-registered band. §8.3's two unresolved candidates
stand unchanged (the patch's RX-watchdog path; `pl` being a quantised live wire sample). The class
*split* still must not be quoted as a mechanism — only the **total (P-W2)**, which is
counter-verified against `pc_timeout_count` itself, is a mechanism-independent result.

**Scope, unchanged and re-confirmed by the fatal count.** **21** `crash detected` lines in `dmesg`
at close (`fatals = 22` in the monitor's own count). Neither part claims the 903 s fatal; the
post-burst spacing in §8.3 was 898 / 904 / 902 / 901 / 902 s. **No statement about the fatal may be
drawn from this window.**

**What this window did buy, stated plainly.**
1. **Patch 0005's rebuild improvement is CONFIRMED** — 16 s → 12 s median, all three P-MM verdicts,
   n = 33 vs a bar of 11. This is the durable result of the soak.
2. **Patch 825's overall timeout rate improvement is CONFIRMED** (P-W2) and its class-level
   mechanism is **NOT** (P-W1 unscored, P-W3 falsified).
3. **A monitor that dies silently is the failure mode of this instrument.** The soak ran ~2.4 h
   unattended after the last write with no alarm. Any future soak must either be supervised or
   write a liveness heartbeat that something checks — the same class of defect as
   `reference_hmu05_platform_quirks.md`'s "a `/dev/kmsg` watcher can never be a liveness beacon".


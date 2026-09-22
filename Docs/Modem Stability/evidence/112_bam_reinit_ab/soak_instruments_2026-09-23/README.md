# Soak instruments — deployment and verification, 2026-09-23

**Purpose.** R5a of the 2026-09-23 session: deploy the two soak instruments and
**verify they work**, but do **not** start the soak. The measurement window is
opened later, by the operator, per the frozen pre-registration in
`../preregistration_2026-09-22_build.md`.

**Device state at deployment.** boot_id `380f6779`, uptime 1 923 s, post-reboot.
Live: patch 825 (`qcom_bam_dmux` srcversion `A9AC55FE…`), the gated
`LOG_LEVEL` (INFO — `/etc/modemmanager-log-level` absent), patch 0005, and the
WS2 bounded-timeout change. `remoteproc0/state = running`; `wwan0` UP.

---

## 1. What was deployed

| file | role | md5 (host = device) |
| :-- | :-- | :-- |
| `/overlay/pcfine.sh` | bam-dmux `rx_telemetry` sampler, v4 | `3231b1185e7ff67b24459ae6937ce5ac` |
| `/overlay/logroll.sh` | durable userspace log + PM-knob recorder | `3cd1748c033252f5668e9c830f61af5e` |
| `/overlay/soak_start.sh` | start/stop/status launcher | `7c334dfde6300464c843b0baa925b1ae` |

Sources: `scratch/pcfine.sh`, `scratch/soak_start.sh`,
`../170_two_fixes_for_the_smd_poll_uaf/Y_logroll_sh.sh`.

**`scratch/` is gitignored** (`.gitignore:26`; 0 files under it are tracked), so
the authoritative, version-controlled copies of the two new/changed instruments
are in this directory: `pcfine_v4.sh` and `soak_start.sh` — both byte-identical
to what is deployed (md5s above). `logroll.sh` is unchanged from its tracked
home in Doc 170; its md5 is recorded here to guard drift.

Note the pre-existing `../pcfine.sh` is the **older, pre-fix** sampler — it is
what produced the pre-fix control capture, so it is left as-is for provenance.
Do not deploy it for the soak; deploy `pcfine_v4.sh`.

`/etc/rc.local` was **not** touched (md5 `6f8bfdaf36d3ec4aa9be6e96fa61d32b`,
the stock 132-byte file). Autostart is deliberately absent: the window is opened
by running the launcher, not by rebooting.

---

## 2. The defect this deployment found — v3's clock bracket never worked

`pcfine.sh` v3 was written to fix a known instrument misalignment: `/proc/uptime`
was read in awk's **BEGIN** block, i.e. *before* the telemetry file, so the
printed `<UP>` is a **lower bound** on the true sample time — calibrated at
**~73 ms early** (Doc 182 §8.5.1, n = 10 pairs, 0.0624–0.0806 s). v3 was to
bracket it: `UP` (pre-read) and a third field `UP1` (post-read), so the true
sample time lies in `[UP, UP1]`.

**It printed an empty field.** awk keys redirections by the *string*, so the
BEGIN-block `getline < "/proc/uptime"` left that handle open **at EOF** (the file
is one line); the END-block read returned 0 immediately and `UP1` was unset.

Measured on the device, both forms in one run:

```
TEST A (v3 as written, no close):  UP=2047.18 UP1=[]
TEST B (with close("/proc/uptime")): UP=2047.19 UP1=[2047.19]
TEST C (pipe form, close(cmd)):     UP=2047.19 UP1=[2047.20]
```

A 200-iteration run of the deployed v3 confirmed it in the real output: every
record ended `|2061.21 0 ` with a trailing space and **0 of 200** records carried
a `UP1`.

**Impact had this shipped.** The field is not merely missing — it is
**silently absent**, so a parser reading field 3 gets nothing and a scorer that
trusts the bracket would fall back to the ~73 ms-early `UP` *while believing it
had a bracket*. This is the "an instrument misalignment produces a confident
wrong answer, not noise" trap. It was caught only because the verification
counted the fields instead of eyeballing the record.

**Fix (v4).** `close("/proc/uptime")` before the END-block read, with a comment
marking it load-bearing. Re-verified: **3 fields after `|`, 0 of 200 records with
an empty `UP1`**, bracket `UP1 − UP0` = 0–10 ms (quantised by `/proc/uptime`'s
0.01 s resolution).

Backwards compatibility is preserved: `UP` is still the **first** field after
`|`, so `census_pc.py` (which takes `t[0]`) parses v4 unchanged.

---

## 3. Verification results

**Sampler** — 200 iterations, heartbeat = 1:

```
287 299 1 1 1 0 0 0 206721 151 150 151 150 32|2061.21 0 2061.22
```

14 key fields before `|`, then `UP0 qm UP1`. 200/200 records. Loop cost
22.4 ms/iteration (200 iterations in 4.48 s), so a 6 h soak yields ~9.6 × 10⁵
iterations and the change-detected output rate is traffic-governed (the pre-fix
control ran 87 616 records over 12 727 s = 6.9 /s).

**Logroll** — 15 s standalone run, then an 8 s run under the launcher:

```
=== BOOT 380f6779 uptime=2075.63 2026-09-22T19:09:02Z ===
2026-09-22T19:09:02Z uptime=2075.64 PMKNOB auto/1000/active (was )
```

It creates the boot-named file, writes the boot header, streams new `logread`
lines (dropbear/`authpriv` filtered), and records the bam-dmux PM knobs
(`control/autosuspend_delay_ms/runtime_status`) on change. **`logread -f` emits
0 history** — it follows from its start point only — which is precisely why the
capture must be armed *before* the window, not after a trigger.

**Launcher** — full `start` → `status` → `stop` cycle:

```
$ /overlay/soak_start.sh start
boot_id=380f6779
started: /overlay/pcfine.sh (pid 13221)
started: /overlay/logroll.sh (pid 13307)
$ /overlay/soak_start.sh status
sampler RUNNING pid 13221 -> /overlay/pcfine_380f6779.txt
  records: 47
logroll RUNNING pid 13307 -> /overlay/logroll_380f6779.log
  lines: 4
$ /overlay/soak_start.sh stop
stopped: pid 13221
stopped: pid 13307
reaped logread pid 13313
```

`stop` reaps the orphaned `logread -f` (a child of the killed `logroll.sh`
shell, matched by `/proc/<pid>/cmdline` — safe because the launcher runs as a
script, so its own cmdline cannot match).

After the test both output files were removed so the soak's window starts clean.
**No instrument is running now.**

---

## 4. How to open the measurement window (not done)

```
/overlay/soak_start.sh start      # opens the window
/overlay/soak_start.sh status     # n so far
/overlay/soak_start.sh stop       # closes it
```

**Run at INFO, not DEBUG** (Doc 185 — DEBUG logging perturbs the timings the
WS3 scoring measures). The gated `LOG_LEVEL` already gives INFO on this build.

Scoring, per the frozen pre-registration:

- **Part A (WS1, patch 825)** from `/overlay/pcfine_<boot>.txt` via
  `census_pc.py` → the four `pl`/`td` classes. Pre-registered n: **≥ 20**
  post-fix `pl=0, td=0` resumes for P-W1; **≥ 500** resumes for P-W2.
- **Part B (WS3, patch 0005)** from `/overlay/logroll_<boot>.log` via
  `score_mm_phases.py` → P-MM1/P-MM2/P-MM3. Pre-registered n: **≥ 11 SSRs** for
  P-MM2 (the safety control), **≥ 10** for P-MM3.

Report the two parts separately. Neither is claimed to fix the 903 s fatal.

To survive a watchdog reset mid-soak, add the guarded `rc.local` block then —
not now.

---

## 5. SOP compliance

- **Comparative protocol.** No baseband was touched. The instruments were
  verified against the **live device**, and the sampler defect was proven by a
  direct A/B measurement on that device (TEST A vs B vs C), not inferred.
- **Pre-registration.** The frozen thresholds in
  `../preregistration_2026-09-22_build.md` were read before deployment and are
  unchanged; nothing here re-tunes a cut.
- **Measurement hygiene.** The verification **counted fields** rather than
  eyeballing records (which is what exposed the empty `UP1`); the instrument was
  confirmed to produce well-formed output before being trusted; the output files
  were reset so the soak's window is not polluted by verification lines; and the
  control (`rc.local`) is asserted untouched by md5.
- **Not done, by instruction.** The soak was **not** started. No autostart was
  armed. No `rc.local` edit. No firmware change.

---

## 6. Final pre-wipe snapshot — and a fatal signature absent from the census

The operator restored the stock Android image on the same unit (see §7), so a
last snapshot was taken while OpenWrt was still up. Files:
`final_openwrt_snapshot_2026-09-23.txt`, `final_dmesg_2026-09-23.txt` (520 lines,
md5 `de053ffdd65e6bda82bdceebd839c6c4`),
`final_console_ramoops_2026-09-23.txt` (542 lines, md5
`9869b89d7646c1516a5af03318d1666a`).

**This boot had three fatals in 2 327 s:**

| uptime | signature |
| --: | :-- |
| 410.715 s | `a2_power.c:1189` |
| **1 312.015 s** | **`FW@lte_LL1_gap_rf_tune.c:351 Assertion (lte_LL1_get_cmd_proc_sys_pending_cmd_ca…`** |
| 1 449.999 s | `a2_power.c:1189` |

**The middle one is not in the 36-dump census** (Doc 186), whose five labels were
`lte_ml1_sleepmgr_stm.c:4054`, `a2_power.c:1189`, `lte_ml1_common_timer.c:390`,
`a2_power.c:2949`, `a2_taskq.c:759`. Note its shape: the `FW@` prefix and the
`Assertion (<text>)` suffix, i.e. the **second class of report** — the same class
as `lte_ml1_common_timer.c:390`, which Doc 186 found in **none** of the seven
registered `{line;flag}` tables. So this is an independent, unplanned
corroboration of Doc 186's key negative: *there is a class of fatal report that
does not go through the line-table mechanism.*

**It fired on the ~902 s clock.** The nearest preceding `CMD_OPEN` burst is at
**412.241 s**, and `1312.015 − 412.241 = 899.77 s`. That is the always-on-clock
beat again (Doc 177), now with a signature from the other report class — which
means the clock is not tied to any one signature.

**The ramoops is the pre-fix control boot.** Its two fatals —
`lte_ml1_sleepmgr_stm.c:4054` @ **1 144.736 s** and `a2_power.c:2949` @
**1 983.653 s** — are exactly two of the three in the frozen control
(`../control_boot_postflash_oldmodule.txt`). That is an independent confirmation
that the pre-fix control boot's fatal set is what it was recorded to be.

**Caveat.** `console-ramoops-0` is the *previous* boot's console, not this one's;
the 542 lines are a partial window, so absence of a line there is not evidence of
absence. It is byte-corrupted in places (as documented), so events are paired by
order, not by timestamp.

---

## 7. Device identity — why this is a re-flash, not a side-by-side move

The Android unit and the OpenWrt unit are **the same physical device**:

- `adb devices` is empty; no Android USB device is attached; a `/24` sweep finds
  only `192.168.8.1`.
- The OpenWrt device's `device-tree/serial-number` is **`c2b9103c`**, which is
  exactly the Android adb serial recorded in the corpus
  (`reference_android_device_access`: *"Device: adb serial `c2b9103c`"*). Its
  LAN MAC `02:00:c2:b9:10:3c` encodes the same serial.
- No stock-Android image exists anywhere in this tree, and the port
  **re-partitioned** the eMMC — the pre-port stock map
  (`modemst1=p13`, `modemst2=p14`, `fsg=p20`, `fsc=p16`) is now
  `p4/p5/p2/p1`, with `p15` as `/overlay`.

So the Android-side experiment (R5b) requires a **full EDL re-flash of the only
unit**, which destroys the OpenWrt deployment and defers the soak. The operator
elected to do that. **What was preserved first:** the sampler and launcher are
tracked here (`pcfine_v4.sh`, `soak_start.sh`) and are byte-identical to what was
deployed, so the OpenWrt deployment is fully reproducible from this directory
after a re-flash — no instrument state is lost.

**Before re-flashing, weigh this.** The corpus already localizes the
Android-vs-OpenWrt differential to the AP-side **kernel**: Android's `bam_dmux`
gives the modem a **2000 ms** pc-ack timeout where OpenWrt gives **250 ms**
(`reference_android_bam_dmux_comparison`), and the pre-stated falsifier was *not*
falsified (`51 × 250 + 2 × 1000 = pc_timeout_count 53`, 96.2 %). Stopping Android
*userspace* applications does not change the kernel driver, so that experiment
tests a **different** hypothesis — modem sensitivity to the AP userspace stack
going quiet — than the sharpest existing lead.

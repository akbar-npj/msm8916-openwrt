# 164 — The q6v5 SSR window is now observable (patch 817) — and the instrument was 4× the phenomenon

**Date:** 2026-09-21
**Subject:** AP-side kernel diagnostic. **No modem firmware, no WCNSS firmware, no DTS and no
bootloader byte was changed.** The only build input added is one temporary kernel patch
(`msm89xx/patches/817-q6v5-ssr-window-trace.patch`) that adds `dev_info()` calls to
`drivers/remoteproc/qcom_q6v5_mss.c`.
**Deployed:** the rebuilt `qcom_q6v5_mss.ko` only. It is a loadable module
(`kmod-qcom-rproc-modem`), so **no kernel image flash was needed and none was done.**
**Result:** the 13 steps of the Doc 159 hang window are now individually observable with µs
timestamps. The instrument was measured and found to cost up to **4.2× the window it measures**;
that was fixed by one sysctl (`console_loglevel = 6`) and re-verified. The soak for the hang is
running.

---

## 0. SOP compliance

| SOP step | Status |
|---|---|
| No baseband change; no firmware write | **Honoured.** Zero firmware bytes touched. Verified: the resident baseband is unchanged (Doc 153), and this work adds only an AP-side `.ko`. |
| Dual-firmware comparative protocol | **Not applicable this round, and deliberately so.** The protocol exists to stop blind *baseband* patching. This change is AP-side kernel instrumentation; there is no counterpart on the Android side to compare against, and no baseband hypothesis is being advanced. |
| Verify against ground truth before acting | **Honoured.** The halt/load/reclaim call order used in §2 was read from the **source** (`qcom_q6v5_mss.c`), not inferred from function names — this is the "read the definition, not the name" rule (memory `feedback_read_the_definition_not_the_name`). It is what caught that `port failed halt` is emitted from **two** different functions (`:1249-1253` in `q6v5_mba_reclaim`, `:1193-1197` in `q6v5_mba_load`'s error path), so the message alone does not say which. |
| Hash the artifact, verify it in situ | **Honoured.** Build artifact, deployed file and the running module's `srcversion` all agree (§3). |
| Never classify a fatal by its `file:line` | **Honoured and used** — §6 *relies* on the corrected form of that rule (Doc 163: the string names *which site tripped*). |
| Record what was done, the result, and what is next | This document, plus §10. |
| Do not treat corpus docs as fact | Honoured — the Doc 159 window figure was **re-measured from an independent capture** (§4) rather than quoted, and the re-measurement changed a number (see §4.3). |

---

## 1. What Doc 159 left open

Doc 159 established that the AP can **hang** on a spontaneous fatal-triggered SSR: a 4 h 27 min boot
took 21 modem fatals, recovered from 20, and hung on the 21st. The console stops dead — no panic
banner, no `BUG:`, no `dmesg-ramoops` — and only the hardware watchdog recovers the device.

It also established the shape of the search space:

* **`port failed halt` is normal.** 21 crashes, 21 occurrences, 21 `MBA booted`; 20 of those crashes
  recovered **43–47 ms** after printing it. It is a `dev_err()` on a 100 ms `AXI_IDLE` poll timeout
  (`qcom_q6v5_mss.c:974`). A message is not a cause until its rate is known.
* Because the next printk normally follows in 43–47 ms, the hang is bounded by **two adjacent
  printk calls**, and its site is enumerable from source.
* Leading candidate: one of the **three untimed synchronous TrustZone transfers**
  (`q6v5_xfer_mem_ownership()` → `qcom_scm_assign_mem()`), at the exact moment the modem — the other
  VMID owner of that memory (Doc 158) — has just crashed. **Unproven.**

Doc 159's recommendation was a temporary `dev_info()` build. That is this document.

---

## 2. The instrument: patch 817

`msm89xx/patches/817-q6v5-ssr-window-trace.patch` (5 535 B, 12 hunks, one file, tracked, commit
`99cbb55`). It adds

```c
#define Q6V5_TRACE(_q, _step) \
	dev_info((_q)->dev, "q6v5-trace: " _step "\n")
```

and **23 call sites, one immediately *before* each step** of the window. Placing the print *before*
the call is deliberate: the next step's print is itself proof that the previous call returned, so a
hang names its own step and the trace volume is halved. Print volume matters — §5 is the measurement
of exactly how much.

The window is the tail of `q6v5_mba_reclaim()` (called from `q6v5_stop()`, `:1615`) followed by the
head of `q6v5_mba_load()` (called from `q6v5_start()`, `:1589`). `port failed halt` is printed by the
halt loop at the **top** of `q6v5_mba_reclaim()` (`:1249-1253`); `MBA booted` is printed by
`q6v5_start()` **after** `q6v5_mba_load()` returns. So the entire window, in execution order, is:

| # | step | source |
|---|---|---|
| — | `q6v5proc_halt_axi_port` × 3–4 → **`port failed halt`** | `:1249-1253` |
| 01 | `disable_qchannel mdm` | `:1308` |
| 02 | `disable_qchannel cx` | |
| 03 | `disable_qchannel axi` | |
| 04 | `reset_assert` | |
| 05 | `clk_disable reset` | |
| 06 | `clk_disable active` | |
| 07 | `regulator_disable active` | |
| 08 | **`SCM mba_perm reclaim (TrustZone)`** | |
| 09 | `qcom_q6v5_unprepare` | |
| 10 | `qcom_q6v5_prepare` | `q6v5_mba_load():1075` |
| 11 | `pds_enable proxy` | |
| 12 | `regulator_enable fallback_proxy` | |
| 13 | `regulator_enable proxy` | |
| 14 | `clk_enable proxy` | |
| 15 | `regulator_enable active` | |
| 16 | `clk_enable reset` | |
| 17 | `reset_deassert` | |
| 18 | `clk_enable active` | |
| 19 | `enable_qchannel axi` | |
| 20 | **`SCM mpss_perm (TrustZone)`** | `:1175` |
| 21 | **`SCM mba_perm (TrustZone)`** | `:1184` |
| 22 | `pil_info_store + rmb writel` | |
| 23 | `q6v5proc_reset` | |
| — | **`MBA booted without debug policy, loading mpss`** | `:1589` |

Steps **08, 20 and 21** are the three TrustZone transfers — the leading candidates. The numbering is
intentional: 10–23 are `q6v5_mba_load`, 01–09 are `q6v5_mba_reclaim`, so the two halves are
distinguishable at a glance in a log.

---

## 3. Build and deploy

| | |
|---|---|
| Patch applies cleanly, target file reproduced byte-for-byte | md5 `7f530b118fbdac79875416f02c74f460` |
| `qcom_q6v5_mss.c` touched by any other patch? | **No** (checked — so patch 817 is the only variable) |
| Build | `./build.sh kernel hmu05` → **full re-prepare** (963 s), exactly as Doc 161 §2 predicted |
| Build artifact | `build_dir/…/linux-6.12.94/drivers/remoteproc/qcom_q6v5_mss.ko`, **177 328 B**, md5 **`79e7a858d41b6c9f7edf8b26b90c77f8`** |
| `srcversion` | `3F0B3E3101FFE0FFC75F8A3` — **matches the running module** |
| `vermagic` | `6.12.94 SMP preempt mod_unload aarch64` — matches the running kernel, so **no kernel flash** |
| Trace strings in the artifact | **23** |
| Pre-817 backup on device | `/overlay/modbackup/qcom_q6v5_mss.ko.pre817`, md5 `6ac6603141c25b1e3462615411e92f7c` |

**Two traps, both of which have cost this project time before:**

1. **Take the `.ko` from the kernel tree, not from the staging tree.**
   `build_dir/…/root-msm89xx/lib/modules/6.12.94/qcom_q6v5_mss.ko` is the **stale pristine baseline**
   — 37 KB, **0** trace strings. The fresh artifact is the kernel-tree one (177 KB, 23 strings).
2. **The rootfs is squashfs.** `/lib/modules/6.12.94/` reverts on every reboot, so the instrumented
   `.ko` must be re-copied after **every** boot, including after a watchdog reboot caused by the very
   hang this soak exists to catch. **If a hang-triggered reboot is not followed by a re-copy, the
   next soak runs the un-instrumented module and any subsequent trace lines are from a stale boot.**

---

## 4. The window, measured independently (n = 5)

Before trusting an instrument you need the size of what it measures. The previous boot's console
survived in `console-ramoops-0` (38 403 B, the whole boot, 0.000 s → 5 337 s), and it contains five
complete fatal→recovery cycles. Measured `port failed halt` → `MBA booted`:

| # | AP uptime of `port failed halt` | → `MBA booted` | gap |
|---|---|---|---|
| 1 | 913.695356 | 913.740308 | **44.952 ms** |
| 2 | 1856.353558 | 1856.400313 | **46.755 ms** |
| 3 | 2758.359664 | 2758.403390 | **43.726 ms** |
| 4 | 3700.121627 | 3700.166653 | **45.026 ms** |
| 5 | 4648.650836 | 4648.697780 | **46.944 ms** |

**n = 5, range 43.726–46.944 ms, mean 45.481 ms.** Doc 159's 43–47 ms figure reproduces exactly, on
an independent boot, from a capture it did not use. That span contains **all 23 trace points plus the
MBA firmware load** — which is the number the instrument has to fit inside.

### 4.1 A methodological trap in this very capture

The first pass counted **4** fatals; there are **5**. The ramoops console copy suffers byte
corruption — `received` appears once as `rEceived`, `crash` as `handlijg`, `remoteproc` as
`remnteproc` — and a plain `grep -c "fatal error received"` silently undercounts.

**Cross-check with a marker that is not the one you are counting.** `MBA booted` = 6 (one initial
boot + five recoveries) and `port failed halt` = 5, and both agree on 5 recoveries; only then does
`fatal error .eceived` (case-insensitive, one wildcard) give 5. This is the same class of error as
"a message is not a cause until its rate is known" — here, *a count is not a count until two
independent markers agree*.

---

## 5. The instrument was 4× the phenomenon — measured, then fixed

### 5.1 The measurement

The console is `console=ttyMSM0,115200`. Writing to `/dev/kmsg` runs the full `printk` path (ring
buffer **and** console emission) from process context — the same path `dev_info()` takes — and a
`<N>` prefix selects the printk level, so the same probe measures both a printed and a suppressed
message. Re-runnable: `evidence/164_q6v5_ssr_window_trace/console_cost_probe.sh`; output in
`console_cost_measurement.txt`.

```
printk = 6	4	1	7        N = 300 writes per measurement
control: loop only, no write                0.010 s     0.03 ms/line
KERN_WARNING(4), ~10 ch, printed            0.580 s     1.93 ms/line
KERN_WARNING(4), ~120 ch, printed           3.560 s    11.87 ms/line
KERN_INFO(6), ~120 ch, suppressed           0.110 s     0.37 ms/line
KERN_INFO(6), ~10 ch, suppressed            0.090 s     0.30 ms/line
```

Fitting the two **printed** points: **cost ≈ 1.03 ms + 0.0904 ms/char.**
At 115200 baud, 8N1 = 10 bits/char = **0.0868 ms/char**. The measured slope matches the UART
serialisation rate to **4 %** — the cost is essentially pure serialisation, and it is linear in line
length.

### 5.2 What that means for patch 817

A trace line on this device is `[  ts] qcom-q6v5-mss 4080000.remoteproc: q6v5-trace: NN <step>` ≈ 80
characters → **≈ 8.3 ms each**. Patch 817 adds **23** of them:

| configuration | cost of the 23 lines | vs. the 45.481 ms window |
|---|---|---|
| `console_loglevel = 7` (as built) | **≈ 190 ms** | **4.2×** |
| `console_loglevel = 6` (fixed) | **≈ 8 ms** | **0.18×** |

The cold-boot bring-up trace gives an independent upper bound on the same quantity: steps 10→23 plus
`MBA booted` span **159.32 ms** on a cold boot, against **45.48 ms** for the same code on a real
recovery — a difference of **113.8 ms ≈ 8.1 ms/line**, consistent with the direct measurement.

**An instrument that is four times the size of what it measures is not a measurement.** Worse, the
perturbation is not neutral: it changes the timing of exactly the kind of handoff the leading
hypothesis blames, so it could plausibly either *prevent* the hang (a race moves out of its window)
or *cause* one (the console lock is held 4× longer). Either way a null result would be
uninterpretable.

### 5.3 The fix, and why it is safe

`console_loglevel = 6` suppresses **KERN_INFO** (`dev_info` = 6) on **every** console — including the
`ramoops-1` console — while still storing it in the ring buffer. So:

* The trace becomes **free** (0.37 ms/line vs 11.87 ms/line — **32×** cheaper, and the remaining cost
  is the `printk` + ring-buffer store itself).
* **The trace is still captured**, because the soak reads `/dev/kmsg`, not the console. Verified
  directly:

```
<6>PROBE_INFO ...   ->  dmesg | grep -c PROBE_INFO = 1     (in the ring)
<4>PROBE_WARN ...   ->  dmesg | grep -c PROBE_WARN = 1     (in the ring)
```

* The two **reference points keep a kernel-side sink**: `fatal error received` and
  `port failed halt` are `dev_err` (KERN_ERR = 3), so they still reach the serial console **and** the
  ramoops console, and survive a userspace-wide hang.

Applied at runtime (`echo 6 > /proc/sys/kernel/printk`) **and** persisted in `/etc/rc.local`, because
a watchdog reboot would otherwise restore 7 and silently re-arm the 4× perturbation. The soak
records the level it started under in `/overlay/q6trace.log`:

```
[469.35s]     printk='6	4	1	7'  (console_loglevel must be 6 so dev_info is ring-only)
```

### 5.4 The tradeoff, stated plainly

Suppressing `dev_info` also removes the trace from the `ramoops-1` console, so the trace's only sink
is now the userspace `/dev/kmsg` reader. That sink is robust — reading the ring does not take the
console lock, and its backstop `/dev/pmsg0` is reserved RAM with no filesystem in the path — but it
is userspace, and a full AP lockup would kill it.

The counter-argument, and the reason this is the right trade: **Doc 159's hang left no
`console-ramoops` record at all**, so the kernel-side console sink is not dependable for this
particular failure mode anyway. And the alternative — a 4× perturbed window — risks not reproducing
the hang in the first place, which is a worse failure because it is silent.

**Pre-registered A/B:** if no hang lands within ~2× the expected time (~10 h), the perturbed arm
(`console_loglevel = 7`, 4.2×) is the *next* configuration to try, not a repeat of this one. The
switch is one sysctl.

---

## 6. An unplanned finding: the interval predicts the signature (8/8)

The same capture gives a clean, falsifiable pattern that is unrelated to the instrument.

**Previous boot — 5 fatals, 5 SSR recoveries, 5 `port failed halt`, 6 `MBA booted`** (1 initial +
5 recoveries; all three markers agree):

| # | AP uptime | signature | interval since previous fatal |
|---|---|---|---|
| 1 | 912.915289 | `lte_ml1_sleepmgr_stm.c:4054` | — |
| 2 | 1855.593254 | `a2_power.c:1189` | 942.678 s |
| 3 | 2757.617525 | `lte_ml1_sleepmgr_stm.c:4054` | **902.024 s** |
| 4 | 3699.366951 | `a2_power.c:1189` | 941.749 s |
| 5 | 4647.901729 | `a2_power.c:1189` | 948.535 s |

The cadence is **S, A, S, A, A** — **identical to run 8** (Doc 162 §3), on a different boot. Doc 162
had to record that as "unexplained, n=4, a property of the boot". It reproduces.

Subtracting the measured reload time (≈1.5 s: teardown → `MBA booted`) to get modem uptime, and
adding run 8's four values:

| modem uptime at fatal | signature | source |
|---|---|---|
| 900.5 s | sleepmgr | this boot |
| 900.662 s | sleepmgr | run 8 |
| 900.965 s | sleepmgr | run 8 |
| 940.2 s | a2_power | this boot |
| 940.238 s | a2_power | run 8 |
| 941.2 s | a2_power | this boot |
| 941.288 s | a2_power | run 8 |
| 947.0 s | a2_power | this boot |

**8/8, with no overlap:** the short fatal (≈900.5–901.0 s, spread **0.46 s**) is always
`lte_ml1_sleepmgr_stm.c:4054`; the long fatal (≈940.2–947.0 s, spread **6.8 s**) is always
`a2_power.c:1189`. The short one is a clock; the long one is not (its spread is 15× larger).

This reframes Doc 162 §3's "alternation" as an artefact of **two mechanisms with different periods
competing inside one boot**, rather than one mechanism whose phase flips. It is consistent with the
taxonomy (sleepmgr's own mean is 901.230 s; a2_power's range is 68.5–947.2 s, i.e. it is
activity-correlated and only *sometimes* lands at ~941 s).

**Testable prediction for the next boot:** every ~901 s fatal will be `lte_ml1_sleepmgr_stm.c:4054`
(or `lte_ml1_common_timer.c:390`), and every ~940–948 s fatal will be `a2_power.c:1189`. The soak
will score it.

---

## 7. The sinks, and what survives what

`/proc/cmdline`: `earlycon console=tty0 console=ttyMSM0,115200 root=/dev/mmcblk0p14 rootfstype=squashfs rootwait`

Four consoles register, in this order: `tty0` (0.0188 s), **`ramoops-1` (0.2403 s)**, `ttyMSM0`
(0.6698 s); the `msm_serial_dm0` bootconsole is disabled at 0.678 s. ramoops is at `0x8DB00000`
(2377121792), 1 MiB, with `console_size` = `pmsg_size` = `record_size` = 262144 (256 KB each).

| sink | cost/line | survives reboot | survives AP hang | kernel-side |
|---|---|---|---|---|
| `ttyMSM0` serial console | 8.3 ms | n/a | no | yes |
| `tty0` | ~0 | n/a | no | yes |
| `ramoops-1` → `console-ramoops-0` | **8.3 ms** | yes (verified: full 4-fatal boot recovered) | **not for Doc 159's hang** | yes |
| `/overlay/q6trace_stream.log` + `sync` | ~0 | yes, ≤0.5 s loss | if the reader lives | no |
| `/overlay/q6trace_trace.log` | ~0 | yes | if the reader lives | no |
| **`/dev/pmsg0`** → `pmsg-ramoops-0` | ~0 | **yes — verified** | if the reader lives | no |

**`/dev/pmsg0` is a reset-surviving sink and this was verified empirically, not assumed.** Three
lines written to `/dev/pmsg0` in the previous boot came back after the reboot via
`/sys/fs/pstore/pmsg-ramoops-0`:

```
PROBE 4722
PMSG_PROBE uptame=4746 bootid=4e441379-e2de-49a3-bd5f-7ab7d26e80d4
PMSG_TEST_12990 uptime=5089
```

It has no filesystem and no page cache in the path — it is reserved RAM — which is exactly the case
it exists for: the sync loop dying with the AP.

**netconsole is impossible on this device**, and this was re-confirmed rather than assumed: `usb0` is
a **configfs USB gadget** (`/sys/bus/gadget/drivers/configfs-gadget.g1`), so it has no netpoll; and
`CONFIG_NETCONSOLE` is off, as are `CONFIG_DYNAMIC_DEBUG` and `CONFIG_FTRACE`. The only
hang-surviving instruments are pstore and this userspace reader.

---

## 8. The harness

`evidence/164_q6v5_ssr_window_trace/`:

| file | what it is |
|---|---|
| `q6trace_soak.sh` | The AP-side capture. Three sinks (`$OUT` full stream + `sync` every 500 ms; `$TRACE` filtered; `/dev/pmsg0` mirrored), a `ping -c 1` burst generator, a 5 s CSV sampler, and a log of every `q6v5-trace:` line and every fatal. |
| `host_hang_watch.sh` | The host-side clock. `ping -c 1 -W 3 -q 192.168.8.1` every 5 s; declares `*** AP UNREACHABLE ***` after 3 consecutive failures and `AP-BACK after Ns`. Cannot capture the console (no netconsole) but pins the hang's start and duration. |
| `console_cost_probe.sh` | §5's measurement, re-runnable. |
| `console_cost_measurement.txt` | Its output. |
| `boot_bringup_trace.log` | Steps 10–23 with µs timestamps from the instrumented cold boot. |
| `console_prev_boot.txt` / `console_clean.txt` | The previous boot's `console-ramoops-0`, raw and NUL-stripped. Five complete fatal→recovery cycles. |
| `pmsg_prev_boot.txt` | The reset-surviving proof from §7. |
| `host_hang_watch.log` | The host clock's log across the instrumented reboot. |

### 8.1 Device quirks the harness works around (each one has bitten this project)

* **busybox `sleep` takes integers only.** `sleep 0.5` is `invalid number`. Same trap class as
  `ping -i 0.05`. `usleep` exists — the sync loop uses it.
* **There is no `timeout` and no `pkill` on this image.** `timeout 30 cat /dev/kmsg | …` fails
  instantly and reads zero, which looks like "no output" rather than "command not found".
* **`while read l; do … done < /dev/kmsg` reads nothing.** `/dev/kmsg` must be *piped from* `cat`.
* **`/dev/kmsg` replays the whole ring on open**, so the first seconds of `$OUT` are history, not
  live. Expect it; do not read it as a burst of activity.
* **`/dev/kmsg` records are multi-line**: the payload on one line, then metadata lines beginning with
  a space (`SUBSYSTEM=`, `DEVICE=`).
* **`busybox cat` has no `-n`**; `sort -h`, `grep --line-buffered`, `sed -u` are all unrecognised.
* **`[ -n "0" ]` is TRUE.** A bare `-n` on a fatal counter logs a spurious `FATAL COUNT 0` on the
  first iteration of every boot. Use `[ "$fatal" -gt 0 ]`.
* **A trailing `&` in an ssh command backgrounds the whole `&&` chain**, leaving empty files and no
  reboot.

### 8.2 Two incidents, and what they cost

* **A dry run healed a real drift.** `DRY_RUN=1 ./build.sh kernel hmu05` printed "not executing" for
  the make steps but still ran `sync_bsp()`, so patch 817 landed in the live tree for real.
  **`DRY_RUN` guards only the make/config steps, not `sync_bsp`.** Benign here, but worth knowing.
* **`pkill` does not exist**, so an old soak survived a restart and **three soaks ran concurrently**
  (14 processes, two extra full-ring replays). The fix is `ps w | awk '/q6trace_soak/ {print $1}'` +
  `kill -9`, then remove the pidfile, then start exactly one. **Always verify the process count after
  a restart** — `ps w | awk '/q6trace_soak/ && !/awk/'` should show exactly 5.
* **The rc.local block first landed after `exit 0`** and could never run. Caught by reading the file
  back with line numbers (`cat -n` does not exist; use `awk '{printf "%d| %s\n", NR, $0}'`) and
  fixed by rebuilding the file, then verifying with `sh -n`.

---

## 9. Pre-registered prediction

Written down **before** the soak that will test it, so it cannot be rationalised afterwards.

> When the hang lands, the **last `q6v5-trace:` line** will be **08, 20 or 21** — the three
> `q6v5_xfer_mem_ownership()` TrustZone transfers. They are the only steps in the window that are
> synchronous, untimed, and depend on the other VMID owner of the memory, which has just crashed.
>
> If the last line is instead a regulator or clock step, the SCM hypothesis is **falsified** and the
> failure is in the RPM/regulator handoff.

The prediction is falsifiable in a single line of a log, and the instrument now costs 0.18× of the
window rather than 4.2×, so a null result is informative.

---

## 10. What to do when the hang lands

1. **The AP will be unreachable and then reboot.** The host watcher gives the exact start and
   duration: `*** AP UNREACHABLE ***` … `AP-BACK after Ns`.
2. **Read `/overlay/q6trace_trace.log` first** — tiny, filtered, and the last `q6v5-trace:` line in
   it names the step. Then `/overlay/q6trace_stream.log` for context.
3. **If the filesystem lost the tail, read `/sys/fs/pstore/pmsg-ramoops-0`** — the reserved-RAM
   mirror. Note it is populated at *probe* time from the previous boot's writes, so it is read
   **after** the reboot, and it is the sink of last resort.
4. **Re-copy the instrumented module** (§3 trap 2) — squashfs reverted `/lib/modules`, and a
   watchdog reboot is exactly the case this soak produces.
5. **Check `df /overlay`** — the coredump watcher writes **85 MB per fatal**, and `/overlay` was at
   100 % with 9.2 MB free once already (Doc 163 §5).
6. **Verify the module is still the instrumented one** before believing any trace: `md5sum
   /lib/modules/6.12.94/qcom_q6v5_mss.ko` must be `79e7a858d41b6c9f7edf8b26b90c77f8`.
7. **Re-check `printk`** — `cat /proc/sys/kernel/printk` must start with `6`. If rc.local did not
   run, the perturbation is back.
8. **Score §6's prediction too** — the fatal's `file:line` against its interval.

---

## 11. Status

* **Deployed and live.** Rebooted 12:00:37 (+05:45); module md5 `79e7a858…`, `srcversion`
  `3F0B3E31…`, 23 trace strings; both autostarts fired (coredump watcher + soak); host watcher logged
  `AP-BACK after 21s unreachable`.
* **`console_loglevel = 6`** applied at runtime and persisted in `/etc/rc.local`.
* Soak restarted cleanly at AP uptime 469 s with `printk='6 4 1 7'` recorded; **exactly 5 processes**;
  telemetry healthy (`tx_defer_queued 66` = `tx_defer_submitted 66`, `tx_defer_preserved 62`,
  `tx_defer_wiped_live 0`, `tx_sweep_guard_hits 0` — patch 812 holding at load).
* **First fatal expected at ≈912 s AP uptime**, i.e. ≈443 s after the restart. That first cycle is
  itself the instrument's validation: it should produce 23 trace lines, 01→23, with no console cost.
* Nothing has been changed in the modem firmware, WCNSS firmware, DTS or bootloader.

## 12. Next

1. **Watch the first fatal→recovery cycle** and confirm all 23 trace steps appear in the ring, in
   order, with the console suppressed. This validates the instrument before the hang can test it.
2. **Soak for the hang.** ~1 hang per 21 fatals at the observed rate; run 8 produced 5 fatals in
   70 min, so ~5 h per hang.
3. **Score both pre-registrations** (§6, §9).
4. **Task #82 — the ~902 s timer.** Leading unexplored lead: **NV / mcfg**. The firmware *segments*
   are proven byte-identical Android vs OpenWrt (21 + 9, Doc 153), but NV/mcfg were **never checked**,
   and `qcom-carrier-autocfg` is an AP-side package that could provision them differently. Use
   `./build.sh package`.

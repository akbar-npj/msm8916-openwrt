# 188 — PRE-REGISTRATION: can the ~900 s modem fatal be reproduced from the Android side?

**Date:** 2026-09-23
**Status:** pre-registration — written **before** the Android image is restored and
before any measurement. Must not be edited after the first run.
**Companion:** `evidence/112_bam_reinit_ab/soak_instruments_2026-09-23/README.md`
§6–§7 (the device-identity finding and the final OpenWrt snapshot).

---

## 1. SOP compliance

- **Comparative protocol (the standing rule).** The modem firmware is **not**
  touched. The differential this tests is AP-side only, and the corpus already
  proves the baseband is byte-identical across the two stacks
  (`project_modem_firmware_identical_proof`, all 21 segments hashed). No baseband
  hypothesis is being reopened.
- **Pre-registration before measurement.** The thresholds, the control, and the
  required `n` in §8 are written now, before the device is flashed back. This is
  the Doc 167 / 183 / 184 discipline.
- **Measurement hygiene.** The detection marker is **read out of the Android
  kernel source in this tree**, not guessed (§6). The control arm is stated, and
  the manipulation must be **verified present** (process gone / file renamed),
  not merely issued — the corpus's `busybox killall ping` incident
  (`reference_android_device_access`) is the precedent.
- **Errors caught while writing this doc:**
  1. I first assumed the Android crash would be visible with OpenWrt's marker
     `fatal error received:`. It is **not** — Android 3.10 uses the `pil`/
     `subsystem_restart` framework, and the marker is a different string (§6).
     Searching for the OpenWrt string on Android would have produced a
     **confident false negative**.
  2. I nearly wrote the manipulation as "kill the modem apps" without a
     **verification step**. Killing a userspace daemon on Android can silently
     fail (or be auto-restarted by `init`), which is exactly the "control that
     silently did not take" failure.

---

## 2. The question

The modem faults on OpenWrt at a **~900 s beat** and does **not** fault on stock
Android. The firmware is identical. So the trigger is on the AP side. **Which AP
axis?**

The candidate axes are:

| axis | Android | OpenWrt | already tested? |
| :-- | :-- | :-- | :-- |
| **(a) kernel driver** | `bam_dmux` pc-ack **2000 ms**; kernel 3.10.28 **armv7l** | pc-ack **250 ms**; kernel 6.12.94 **aarch64** | lead only — never isolated |
| **(b) userspace stack** | RIL/`qcril`/`qmuxd`/`netmgrd` | netifd + ModemManager | **not tested** |
| **(c) AP boot firmware** | stock `hyp`/`tz` | `qhypstub`, `TZ.BF.3.0-00714` | `project_ap_side_firmware_substitution` — the UFI001B control (same tz+qhyp, no crash) **weakens** it |

**R5b isolates axis (b).** It asks a single question:

> If the AP-side modem-communication stack is **removed** while the modem stays
> up, does the modem fault on the ~900 s beat?

This is deliberately **not** the sharpest test of the whole differential — axis
(a) is the sharper lead (see §9) — but it is the one the operator asked for, it
is cheap, and a positive result would be highly informative: it would mean the
fault is driven by the **AP going quiet**, which is a state OpenWrt may reach
even while its stack is "working".

---

## 3. Why this is a re-flash, not a side-by-side move

The Android unit and the OpenWrt unit are **the same physical device**
(device-tree serial `c2b9103c` == the Android adb serial; LAN MAC
`02:00:c2:b9:10:3c`). There is no second unit: `adb devices` is empty and a `/24`
sweep finds only `192.168.8.1`. The port re-partitioned the eMMC, so this is a
full EDL flash. Consequence for the OpenWrt work: the frozen soak (WS1/WS3) is
**deferred**, and its instruments are preserved in the evidence directory so the
deployment is reproducible after a re-flash.

---

## 4. What "the same fatal" means here — and what it cannot mean

The OpenWrt fatal carries a **firmware-side site label**
(`a2_power.c:1189`, `lte_ml1_sleepmgr_stm.c:4054`, …). Android's framework
**does not report that label** — `modem_err_fatal_intr_handler()` is an IRQ
handler that only knows *that* the modem asserted ERR_FATAL, not which site
(Doc 186: the label lives in a mutable record inside the modem image, readable
only from a coredump or DIAG).

So on Android the observable is **binary**: *did the modem assert ERR_FATAL?*
Not *which site*. **A reproduction therefore cannot claim the same signature** —
only the same **class** of event (a modem ERR_FATAL). This is stated now so a
later write-up cannot overclaim.

---

## 5. The manipulation — a ladder, weakest-blast-radius first

Each rung is a **separate arm**, run in its own window. Do not stack them in one
run (stacking makes a positive unattributable).

| rung | manipulation | mechanism broken | restarts? |
| :-- | :-- | :-- | :-- |
| **A0** | *(control)* nothing | — | — |
| **A1** | stop `netmgrd` + `qmuxd` | network-management + QMI mux | `init` may restart |
| **A2** | stop `rild` (and `com.qualcomm.qcrilmsgtunnel` if present) | the entire RIL/QMI path | `init` may restart |
| **A3** | rename `libril-qc-qmi-1.so` / `libqmi*.so` in `/system/lib` | RIL cannot start at all | needs `/system` rw |
| **A4** | stop `time_daemon` + `com.qualcomm.timeservice` | QMI TIME only | — |

**Verification is part of the rung, not an afterthought.** After each
manipulation, assert the target is **actually gone** (process absent from `ps`,
file absent from `ls`) *and* that the modem is **still up** (modem uptime still
advancing — §6). A rung whose manipulation did not take is **VOID**, not a
negative result.

**Deliberately NOT on the ladder:** `rmt_storage`. It owns the modem's EFS; a
failure there risks the modem's persistent state and is not worth the blast
radius for this question.

---

## 6. The detection instrument (read out of the kernel source, not guessed)

**The marker.** `drivers/soc/qcom/pil-q6v5-mss.c:84`, in
`modem_err_fatal_intr_handler()` (registered as `subsys_desc.err_fatal_handler`
at `:197`):

```c
pr_err("Fatal error on the modem.\n");
subsys_set_crash_status(drv->subsys, true);
```

So on Android the primary marker is the literal

```
Fatal error on the modem.
```

**The recovery marker.** `subsystem_restart.c:13` sets
`pr_fmt(fmt) = "subsys-restart: %s(): " fmt`, so a restart logs
`subsys-restart: <func>(): …`. The corpus notes the real recovery line
`subsys-restart: Resetting` returned **0** during a clean Android capture.

**The liveness instrument (so a crash is not confused with a quiet log).**
`dmesg` on Android is a full-boot control when its first line is `[ 0.000000]`.
Modem **uptime** must be read independently, because the AP-side log can be quiet
for reasons unrelated to the modem:

- `dmesg | grep -c "Fatal error on the modem"` — **expect 0**.
- `dmesg | grep -n "subsys-restart"` — expect only boot-time registration.
- **Modem uptime must be monotonic.** The instrument is the modem's own counter
  (`ATS_RTC` over QMI TIME, `reference_modem_ats_rtc_uptime_instrument`) — it
  stops when the modem stops. A fall to ~0 is a modem restart; a monotonic climb
  across the window is the "no crash" evidence, exactly as in
  `02_DIFFERENTIAL_DIAG_ANALYSIS.md` §4.

**Do NOT use OpenWrt's marker on Android.** `fatal error received:` is a
`remoteproc` string from kernel 6.12; Android 3.10 has no `remoteproc`. Grepping
for it yields 0 hits whether or not the modem crashed.

---

## 7. The control

**A0 is the control and it is the same boot, the same instrument, the same
duration.** The pre-existing Android evidence (a 594K-frame soak with no FATAL,
`project_android_differential_900s`) is *supporting* but was taken in a different
session; it does not replace an A0 window measured with the §6 instrument.

If A0 itself shows a modem restart, the platform is not quiet and **every**
treatment arm is uninterpretable — stop and report that.

---

## 8. Frozen predictions and required n

| id | prediction | falsified if |
| :-- | :-- | :-- |
| **P-A1** | at least one treatment arm (A1–A4) produces **≥ 1** `Fatal error on the modem.` | no arm does, at the required n |
| **P-A2** (control) | the A0 arm produces **0** | any fatal in A0 |
| **P-A3** | any induced fatal lands on the **~902 s always-on clock beat** (Doc 177), measured from the modem's own uptime, **not** the AP clock | fatals land off-beat |

**Required n — stated before the run.** The fatal's period is ~900 s and it is
not claimed to be deterministic, so **each arm needs ≥ 2 full ~900 s beats**
(≈ 30 min) *with the manipulation verified present for the whole window*. A
single 900 s window is **not** evidence of a negative (Doc 183/184: the fatal is
a rate, and one clean window proves nothing). Report `n` windows and the beat
count with every number; never report "it did not happen".

**Split the cheap question from the expensive one.** "Does the manipulation take
effect and does the modem stay up?" is answered in ~2 min (n = 1) and should be
reported first, separately. "Does the fatal still happen?" needs the ≥ 2-beat n.

---

## 9. What this does NOT establish

- **It does not test axis (a)**, the kernel driver — which is the sharper lead.
  Android's `bam_dmux` gives the modem a **2000 ms** pc-ack timeout where OpenWrt
  gives **250 ms**, and the pre-stated falsifier was *not* falsified
  (`51 × 250 + 2 × 1000 = pc_timeout_count 53`, 96.2 %). **Stopping Android
  userspace does not change the kernel driver**, so a null result here does
  **not** exonerate axis (a), and a positive result does not implicate it.
- **It cannot identify a signature.** Android reports the event, not the site
  (§4). "Same fatal" can only ever mean "same class".
- **It does not touch the 903 s clock's origin.** P-A3 only checks whether an
  induced fatal *lands* on the beat; it says nothing about what sets the offset.
- **It is not a fix.** Nothing here changes either stack.
- **A null result is weak.** With no userspace stack, the modem may simply idle
  in a state OpenWrt never reaches; absence of a fatal then says nothing about
  OpenWrt.

---

## 10. Procedure (ready to execute once the device is up)

1. **Access.** `adb shell 'setprop service.adb.root 1; busybox killall adbd'`,
   then `adb wait-for-device; adb shell id` → expect `uid=0(root)`. Do **not**
   trust `adb root` (it silently does nothing).
2. **Baseline A0.** Start the modem-uptime instrument; run ≥ 2 beats; assert
   `dmesg | grep -c "Fatal error on the modem"` = 0 **and** modem uptime
   monotonic.
3. **Enumerate the stack** (this is what makes the rungs concrete): `ps` for
   `rild|qmuxd|netmgrd|rmt_storage|time_daemon`, and `ls /system/lib | grep -iE
   'libril|libqmi'`. Record the exact names — do not assume the 4.4.4 set.
4. **For each rung A1…A4:** apply → **verify present** → run ≥ 2 beats →
   read the §6 markers → record `n` windows.
5. **Stop on the first positive** and characterise it; do not continue up the
   ladder.
6. Preserve `dmesg`, `logcat -b all -v time`, and the modem-uptime log before any
   reboot (`dmesg` is a ring and a destructive read).

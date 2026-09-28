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
| **A3** | mask `libril-qc-qmi-1.so` / `libqmi*.so` in **`/system/vendor/lib`** (§12.2 — the path in the first draft was wrong) | RIL cannot start at all | no — bind-mount overlay, reversible with `umount` |
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
   `rild|qmuxd|netmgrd|rmt_storage|time_daemon`, and `ls /system/vendor/lib |
   grep -iE 'libril|libqmi'`. Record the exact names — do not assume the 4.4.4
   set. **Verified 2026-09-23:** the QMI/RIL libraries are in
   **`/system/vendor/lib/`**, *not* `/system/lib/` — `getprop rild.libpath` =
   `/system/vendor/lib/libril-qc-qmi-1.so`, and that directory holds 10 of them
   (`libqmi.so`, `libqmi_cci.so`, `libqmi_client_qmux.so`, `libqmiservices.so`,
   `libril-qc-qmi-1.so` 5 032 528 B, …). `/system/lib/` holds only the generic
   AOSP `libril.so` / `libreference-ril.so` / `librilutils.so`. **A rung that
   greps the wrong directory fails its own "manipulation took" check and is
   VOID, not a negative.**
4. **For each rung A1…A4:** apply → **verify present** → run ≥ 2 beats →
   read the §6 markers → record `n` windows.
5. **Stop on the first positive** and characterise it; do not continue up the
   ladder.
6. Preserve `dmesg`, `logcat -b all -v time`, and the modem-uptime log before any
   reboot (`dmesg` is a ring and a destructive read).

---

## 11. Addendum 2026-09-23 — the module-profile axis, and a new cheap test

**Why this is here:** the operator asked to *"check inbuilt and loadble kernel modules,
they also may be what protects 15 minute crash"*. The full census and analysis are in
`evidence/188_android_side_repro/android_module_profile_comparison.md`; the load-bearing
results for this pre-registration are:

### 11.1 Step 10.3 is already done — the baseline is now known

The stack is **running**, not absent:

```
ril-daemon running   qmuxd running   netmgrd running
rmt_storage running  time_daemon running   qcomsysd running
ps: 196 rild, 243 time_daemon, 247 qmuxd, 249 rmt_storage, 274 netmgrd
```

Verified three ways (`init.svc`, `ps`, `/proc/<pid>/cmdline`). An earlier "all absent"
reading was **wrong**. **Consequence for the ladder: rung A2 is a genuine manipulation
with a genuine control** — nothing is already stopped.

Also: the image is a **HiMI vendor engineering build**
(`…:4.4.4/KTU84P/eng.edwin.20250828:userdebug/test-keys`, baseband
`HIMI_U01_MODEM_V1.0`), not a stock carrier image.

### 11.2 A free control measurement already in hand

`A0` (the control) has effectively been run once by accident, and it is stronger than
§8's minimum of two beats:

| measurement | value |
| :-- | :-- |
| uptime | **2 423 s = 2.69 × the 902 s clock** |
| modem subsystem | `modem: state=ONLINE` (up since 6.7 s) |
| `dmesg \| grep -c "Fatal error on the modem"` | **0** |
| `[system] suspend` success count | **0** (never suspended) |
| `[cpu0] pc` (power collapse) | 39 664 success / 8 866 failed |
| `/proc/modules` | `wlan` only |

This is a *this-boot, adb-attached* window, so it is a control for **"an awake Android AP
does not crash"**, which is exactly the differential. It is **not** a control for a stock
idle dongle.

### 11.3 Two candidate mechanisms were killed before the ladder runs

* **Suspend.** `# CONFIG_SUSPEND is not set` on OpenWrt vs full suspend+wakelocks on
  Android *looked* decisive — and §11.2 kills it: Android never suspended and never
  crashed.
* **Interconnect / bus scaling.** `# CONFIG_INTERCONNECT_QCOM is not set` on OpenWrt vs
  `CONFIG_MSM_BUS_SCALING=y` (Android `msm_bus_core`) *looked* like the AP→RPM traffic
  difference — but the msm8916 DT declares `bimc`/`pcnoc`/`snoc` with **zero consumers**,
  so enabling the symbol would register providers that issue no votes. Inert.
* **`CONFIG_MSM_PM_TIMEOUT_HALT=y`** ("the AP's action when Power Management times out
  waiting for Modem's handshake") is **dead config** — no `.c`/`.h` in the 46 096-file
  reference tree reads it. Do not build on its help text.

### 11.4 ★ New pre-registered prediction (P-A6), for when OpenWrt is back

**P-A6 (frozen):** on OpenWrt, read

```
for s in /sys/devices/system/cpu/cpu0/cpuidle/state*/; do
    echo "$(cat $s/name): usage=$(cat $s/usage)"
done
```

* **If `state1` = `standalone-power-collapse` has `usage` in the millions** → the OpenWrt AP
  power-collapses like Android's (39 664 in 40 min) ⇒ **the AP low-power axis is closed**,
  and the module/config axis yields nothing; the differential must be elsewhere.
* **If `usage == 0`** → the OpenWrt AP never power-collapses while Android does ~16/s ⇒ the
  first concrete *mechanical* AP-side difference on the exact axis the modem's
  `a2_power.c` assert lives on. (Suspicion to test, not assume: `qhypstub` may not implement
  the PSCI suspend param `0x40000002`.)

**Falsifier, stated in advance:** a `state1` usage that is non-zero but *tiny* (say < 100 in
1 000 s) counts as **"does not power-collapse"**, not as ambiguous — Android's rate is
~16 s⁻¹, three orders of magnitude apart.

This is **one command** and it must be run **before** the R5b ladder, because it can retire
the whole axis and save the ladder.


---

## 12. Addendum 2026-09-23 (second) — the `/system` mount posture, and three corrections found while setting up A0

**Why this is here:** the operator asked *"shall i make /system ro now just to protect it
from unwanted changes"*. Answering it required reading the live mount table, which turned up
the A3 path error, and the instrument setup turned up a durability failure. All three are
recorded before the ladder starts, because each one would otherwise have silently corrupted
an arm.

### 12.1 The mount posture, and what `ro` does and does not protect

Live `/proc/mounts`, 2026-09-23 (uptime 4 167 s):

```
/dev/block/bootdevice/by-name/system   /system  ext4 rw,seclabel,relatime,discard,data=ordered 0 0
/dev/block/bootdevice/by-name/userdata /data    ext4 rw,seclabel,nosuid,nodev,relatime,...    0 0
/dev/block/bootdevice/by-name/cache    /cache   ext4 rw,seclabel,nosuid,nodev,relatime,...    0 0
/dev/block/bootdevice/by-name/modem    /firmware vfat ro,relatime,uid=1000,gid=1000,...errors=remount-ro 0 0
```

* `/system` was **`rw`** when first read. **CORRECTION (operator, 2026-09-23):** the image
  mounts `/system` **`ro` at boot, like stock Android** — the `rw` state was
  **operator-induced** (a deliberate `remount,rw` to change the ssh root password), not a
  property of the build. The operator has since restored it to `ro`. The first draft of this
  section inferred "this `eng.`/`userdebug` build mounts rw" from a single live reading; that
  inference was **wrong** and is retracted. **Lesson:** one live mount reading tells you the
  *current* flag, not the *fstab* flag — the two differ whenever anyone has remounted.
  Remounting ro is therefore not "restoring the stock posture", it is simply **reverting an
  operator change**.
* **But `/system` is not where the blast radius is.** The baseband firmware partition
  `/firmware` is **already `ro`**, so the modem image is already protected from us. The
  high-churn surfaces are `/data` and `/cache` (both rw), which a ro `/system` does not
  touch. The modem's NV/EFS (`modemst1/2`, `fsg`, `fsc`) is owned by `rmt_storage` on raw
  block devices — orthogonal to `/system` entirely.
* Net: a ro `/system` protects against **our own edits to the ROM** and nothing else.

**The cost, and why the ladder does not need rw anyway.** The one durable-autostart mechanism
on Android is an `init` service or a line in `/system/etc/init.qcom.post_boot.sh` — both live
in `/system`. §12.2 shows the instrument currently has no durable launcher. A **host-side
background `adb` task** is the substitute (the CSV lives on `/data`, so data survives a
launcher death and the gap is visible in the `ap_uptime` column — which is what the
instrument was designed to expose).

**A3 no longer needs `/system` rw either** (§12.3): it is implemented as a bind-mount
overlay, which does not write to the underlying filesystem.

**Operational rule if the remount is done:** do it **before** the A0 window opens, never
mid-window (a mount-state change is a platform state change, and §5 requires each arm in its
own window on a stable platform), verify it took, and record the exact `/proc/mounts` line as
part of the A0 environment:

```sh
mount -o remount,ro /system
busybox grep ' /system ' /proc/mounts      # must show 'ro' — a silent failure VOIDs A0
```

### 12.2 CORRECTION — the A3 paths in §5 were wrong, and A3 is now non-destructive

The first draft of §5 said to rename `libril-qc-qmi-1.so` / `libqmi*.so` in `/system/lib`.
**That directory does not contain them.** Verified on the device:

```
getprop rild.libpath  ->  /system/vendor/lib/libril-qc-qmi-1.so
```

`busybox find /system -name 'libril-qc-qmi*.so' -o -name 'libqmi*.so'` returns exactly ten
files, **all** under `/system/vendor/lib/`: `libqmi.so`, `libqmi_cci.so`,
`libqmi_client_helper.so`, `libqmi_client_qmux.so`, `libqmi_common_so.so`, `libqmi_csi.so`,
`libqmi_csvt_srvc.so`, `libqmi_encdec.so`, `libqmiservices.so`, `libril-qc-qmi-1.so`
(5 032 528 B). `/system/lib/` holds only the generic AOSP `libril.so`,
`libreference-ril.so`, `librilutils.so`.

**Consequence had this not been caught:** A3's mandatory "manipulation took" check
(`ls /system/lib | grep -iE 'libril|libqmi'` — §10 step 3) would have returned nothing to
rename, the arm would have been scored VOID, and a VOID arm read as a negative is exactly the
error §5 forbids.

**A3 is re-specified as a bind-mount overlay**, which is non-destructive (the original file is
never touched), needs no rw mount, and is reversible in one command:

```sh
busybox touch /data/local/tmp/empty
mount -o bind /data/local/tmp/empty /system/vendor/lib/libril-qc-qmi-1.so
busybox ls -la /system/vendor/lib/libril-qc-qmi-1.so   # size 0 => took
# revert:
umount /system/vendor/lib/libril-qc-qmi-1.so
```

`dlopen()` of a 0-byte file fails with an invalid-ELF error, so the RIL cannot start.
Repeat for each `libqmi*.so` the arm needs.

**Open design point, flagged not settled:** A3's stated mechanism is "RIL cannot start at
all", but `rild` is already running with the library mapped. So A3 only becomes observable
after `rild` is stopped — i.e. in effect A3 ⊇ A2. The clean separation is: **A2 = rild is
stopped but restarts; A3 = rild is stopped and cannot restart.** The distinguishing
observable is `ps` showing `rild` *absent and staying absent* versus *reappearing*. Whichever
way it is run, the rung must state which of the two it is measuring.

### 12.3 The instrument is NOT durable — the watcher died, and my 20 s check was too short

`watch.sh` was launched with `nohup … &` from a shell session. Its CSV shows samples from
`ap_uptime` **3889.22** to **3899.69**, then stops. Uptime at the next reading was **4 256.79**
— a **357.1 s hole** with the instrument not running.

* It did **not** die from memory pressure: `dmesg | grep -iE 'lowmemorykiller|Killed
  process|oom'` returns only the boot-time `oom_adj` conversion table, no kill.
* It died when its launching session went away. **The earlier check that "nohup survives the
  disconnect" ran only 20 s and observed the log grow 2 → 6 lines. Twenty seconds is not
  evidence of durability** — that was a measurement-hygiene error, and it is the reason the
  hole exists rather than being a surprise.
* **Lesson, added to the record:** a detached-process liveness test must span the window it
  is claimed to cover, not a token interval. The instrument's own `ap_uptime` column is what
  caught this — which is the design working, but only because the column was read.

**Consequence for §5/§10:** no arm may be scored from a window whose CSV shows a gap in
`ap_uptime`. Coverage is now a precondition of every rung, checked before the result.

### 12.4 The frozen `pc` counter — resolved, and it is real, not a reporting artifact

The blocker from the previous session (cpu0 `pc` usage stuck at 53 867 across a 90 s and then
a 60 s quiet window) was re-measured over a 45 s window, reading **both** counters:

| counter | T0 | T1 (+45 s) | verdict |
| :-- | --: | --: | :-- |
| `/d/lpm_stats/cpu0` `wfi` success | 825 907 | 832 171 | **+6 264** (~139 s⁻¹) — advancing |
| `/d/lpm_stats/cpu0` `standalone_pc` success | 742 | 742 | **frozen** |
| `/d/lpm_stats/cpu0` `pc` success | 44 110 | 44 110 | **frozen** |
| `/d/lpm_stats/cpu0` `pc` total success time | 574.939090375 | 574.939090375 | **frozen to 9 decimals** |
| `/d/lpm_stats/cpu0` `pc` failed count | 9 757 | 9 757 | **frozen** |
| sysfs `state1/usage` (c1) | 903 | 903 | frozen |
| sysfs `state2/usage` (c2) | 53 867 | 53 867 | frozen |

**Conclusion:** Android's AP on this boot is entering **`wfi` only** — it is doing **no power
collapse at all**, at any level, and has been for the whole 6+ minutes observed. This is a
genuine state, not a stale counter.

**Trap recorded — the two counters are different quantities and must not be substituted:**

| | sysfs cpuidle `usage` | `/d/lpm_stats` success count |
| :-- | --: | --: |
| `standalone_pc` / state1 | 903 | 742 |
| `pc` / state2 | 53 867 | 44 110 |

Reading only sysfs would have mis-stated the magnitude by ~22 %; reading only `/d/lpm_stats`
would have missed that sysfs counts something else. **Report which counter, always.**

**Substantive consequence for the pre-registration.** The earlier Android control boot ran
**2 423 s with 0 fatals and `pc` = 39 664** — i.e. *with* the AP power-collapsing. This boot
has run **4 256.79 s with 0 fatals and no power collapse at all**. So on Android **the fatal
does not correlate with AP power collapse: both regimes are fatal-free.** That weakens — it
does not kill — the "AP deep-idle is the protection" reading of the P-A6 test, and it means
P-A6 must be interpreted against *both* Android baselines, not just the 39 664 one.

### 12.5 State at the close of this addendum

* uptime **4 256.79 s** (4.7 × the 902 s clock), `subsys2` = **ONLINE**,
  `dmesg | grep -c "Fatal error on the modem"` = **0**.
* **No arm of the ladder has been run.** No manipulation applied. No file on `/system`
  modified.
* Open before A0 can be scored: a durable launcher (§12.3), and a decision on the `/system`
  remount timing (§12.1).

**SOP compliance.** SOP steps observed: the comparative protocol (every claim above is read
from the live device or the tracked kernel tree, not from a document's narrative); the
"verify the manipulation took" rule (§5), which is what caught the A3 path error; the
pre-registration rule (the corrections are recorded *before* any arm runs, so no cut is
re-tuned after seeing data); and the deployment-verification rule (counters and mount flags
read from the live system, not from a cached copy). Steps deliberately not taken: no modem
firmware was read, patched, or written; the SOP's dual-firmware byte-comparison step is not
applicable because this addendum makes no claim about the baseband image.

---

## 13. The HiMI vendor stack — what `himiwebserver` is, and whether it is AP-side modem traffic

**Why this is here:** the operator asked *"now what does this himi server do"*, after the
process list showed `/system/bin/himiwebserver` running as root. The question matters to this
investigation for one reason: **if a vendor daemon polls the modem on a timer, it is exactly
the class of AP-side modem traffic the Android-vs-OpenWrt differential is about.** So it was
read from the live process, not from a vendor narrative.

### 13.1 What it is

| property | value |
| :-- | :-- |
| binary | `/system/bin/himiwebserver`, 2 254 232 B, `root:2000`, mode 755 |
| **file date** | **Aug 28 2025** — i.e. **stock vendor ROM** (matches `ro.build.date` 2025-08-28) |
| process | pid 1211, **root**, **single-threaded** (1 task) |
| listens | **TCP `0.0.0.0:8000`** (inode 7699) and **UDP `0.0.0.0:50601`** (0xC5A9, inode 7700) |
| serves | the vendor "4G UFI" admin single-page app (Vue.js), `X-Frame-Options: SAMEORIGIN` |
| API | `POST /himiapi/json` with `{"cmdid": "..."}`; also `/himimifi/apijson` and `/upload` |
| auth | **enforced** — see 13.2 |
| **sockets** | **only its two listeners.** No QMI socket, no `/dev/smd*`, no `/dev/tty*`, no unix socket to `rild`/`netmgrd` |
| other fds | `/dev/null` ×3, `/dev/log/{main,radio,events,system}` (write), `/dev/__properties__` |
| reads | `/dev/block/bootdevice/by-name/config` (= `/dev/block/mmcblk0p26`) — currently **all-zero** (256 B read) |
| state files | `/data/property/persist.himi.sim.curr`, `/data/property/persist.himi.sim.user` |

**The command surface** (recovered from the JS bundle embedded in the binary — the full set):

`getoverview`, `getsysinfo`, `getallstatus`, `getapninfo`, `setcurrentapn`, `setnewapn`,
`getnetworkmode`, `setnetworkmode`, `getcondetect`, `setcondetect`, `getdhcpinfo`,
`setdhcpinfo`, `getsimset`, `setsimnum`, `sethimiimei`, `gethimiusbtether`,
`sethimiusbtether`, `getconnectedsta`, `selectlang`, `logout`.

So it is a **full dongle management UI**: APN, network mode (LTE/3G/2G), DHCP, WiFi
SSID/password, USB tethering, connected-station list, **`sethimiimei` (writes the modem
IMEI)**, and — per `/data/himiprot.json` — `shutdown`, `forceReset`, `forceRestart`. That
config file also exposes the **WiFi key in BASE64** as a readable field.

### 13.2 The API is authenticated

Unauthenticated probes of every read command return an auth error, not data:

```
$ for c in getoverview getsysinfo getallstatus getapninfo getconnectedsta; do
      curl -s -X POST -H 'Content-Type: application/json' \
           -d "{\"cmdid\":\"$c\"}" http://<device>:8000/himiapi/json; done
{"reply":"SessionOut"}   # ×5 — every command, no data
```

`GET /himiapi/json` (no POST body) returns `{"reply":"error"}`. The client sends a
`sessionId` in the `Authorization` header, and there is a Login/PassWord flow plus a
`logout` command. **So `himiwebserver` is not an unauthenticated backdoor** — it is a
session-gated admin UI. (The *strength* of that session implementation was not tested and
must not be asserted either way; no credential guessing was performed.)

### 13.3 It does NOT generate periodic modem traffic — but the web UI does

This is the part that matters for the differential:

* **The daemon itself is request-driven.** It holds no QMI/RIL socket, so it cannot query
  the modem on its own; it answers HTTP and nothing else. It is **not** an AP-side modem
  traffic source.
* **Nothing is polling it.** `/proc/net/tcp` established-connection scan shows no client on
  `:8000` other than the operator's own probe (which closed). So there is no background
  poller today.
* **But the admin UI polls when a browser has it open.** The front-end issues
  `getallstatus` / `getoverview` on a timer, and *those* reach the modem through the RIL
  path. **A browser tab left open on `192.168.100.1:8000` is a periodic AP-side modem traffic
  generator** — and on a dongle, the natural place that tab lives is a phone or laptop on the
  dongle's own WiFi, which is outside our visibility.
* **Consequence for the ladder:** "is anything polling the admin UI?" is now a **confounder
  to record before each arm**, alongside the daemon-liveness columns. It is cheap to check
  (established-connection scan on `:8000`).

### 13.4 The RIL work is done by `HiMILauncher2`, not by the web server

`com.himilink.HiMILauncher2` (`/system/priv-app/HiMILauncher2.apk`, 7 621 694 B, also dated
**Aug 28 2025** = stock) runs as pid 1038, **uid 1000 (system)**, and is the component that
actually touches telephony: its fds include `telephony-common.jar`, `telephony-msim.jar`,
`oem-services.jar`, `org.codeaurora.Performance.jar`, and it writes `/dev/log/radio`.
`/dev/socket/rild`, `rild-debug`, `rild_ims`, `rild_oem0` are present as normal.

So the split is: **`HiMILauncher2` = the RIL client (the modem-facing half);
`himiwebserver` = the HTTP front-end that drives it** — communicating through system
properties and the `config` partition, not through a socket of its own.

### 13.5 `telnetd`, `sshd` and SELinux are NOT vendor — the file dates separate them

The operator's question sits next to a security-relevant observation, so the attribution is
recorded explicitly. The dates split cleanly:

| file | date | attribution |
| :-- | :-- | :-- |
| `/system/bin/himiwebserver` | **Aug 28 2025** | vendor ROM |
| `/system/priv-app/HiMILauncher2.apk` | **Aug 28 2025** | vendor ROM |
| `/system/xbin/telnet_login` | **Aug 14 2026** | **added ~13 months after the ROM** |
| `/system/xbin/sshd` (2 101 972 B) | **Aug 15 2026** | **added** |
| `/system/etc/init.qcom.post_boot.sh` | **Aug 16 2026** | **modified** (contains the "Master Boot Trigger" ssh/telnet block and `setenforce 0`) |

`ro.build.date` is 2025-08-28, so every Aug-2026 file is a **later addition, not vendor
code**. `getenforce` = **Permissive** (set by the `setenforce 0` lines in that block).

`/system/xbin/telnet_login` is a 448-byte shell script that **compares the typed password
against a hardcoded plaintext string and, on a match, `exec`s `/system/xbin/bash` as root**.
It is mode **755** — readable by every user on the device, so the password is not a secret
from anything running locally. `telnetd` is gated on `/data/local/tmp/start_telnet`
(mode 600 root, present since Aug 14 2026), so the gate itself is sound; but `telnetd` is
**root, on `:23`, over an unencrypted protocol**, and `sshd_config` carries
`PermitRootLogin yes` / `PasswordAuthentication yes` / **`PermitEmptyPasswords yes`**.

**Flagged, not acted on:** these are the operator's own additions (the operator confirmed
changing the ssh root password), so nothing was changed. But three things are worth a
decision outside this investigation: the hardcoded world-readable telnet password, the
`PermitEmptyPasswords yes` in `sshd_config`, and SELinux being Permissive. None of them
touches the modem path, so none of them affects the ladder.

### 13.6 What this establishes

* **Established:** `himiwebserver` is a stock-vendor, root, session-authenticated HTTP admin
  UI on `:8000`, request-driven, holding no modem-facing socket. It is **not** a background
  AP-side modem traffic source. The modem-facing half is the stock `HiMILauncher2` priv-app.
* **Established:** a **browser left open on the admin UI does poll the modem** — recorded as
  a ladder confounder.
* **Not established:** the strength of the session implementation; whether the vendor UI's
  periodic poll interval is long enough to matter; whether `sethimiimei` writes NV directly
  or via RIL. None of these is needed for the ladder.

**SOP compliance.** SOP steps observed: the comparative protocol (every claim is read from
the live device — fds, sockets, dates, live HTTP probes — not from a vendor document or a
narrative); the deployment-verification rule (the *running* binary's own strings were read
rather than trusting a filename or a version string); and the pre-registration rule (the
"admin-UI polling" confounder is recorded **before** any arm runs, so it cannot be invoked
after the fact to explain a result). Steps deliberately not taken: **no credential guessing
against the login endpoint** — establishing that auth exists is a different question from
establishing that it is strong, and only the first was asked; no modem firmware was read,
patched or written; the SOP's dual-firmware byte-comparison step is not applicable because
this section makes no claim about the baseband image.

---

## 14. Execution log — telnet removed, instrument relaunched, A0 window opened (2026-09-23)

### 14.1 Telnet removed from `/system` (operator instruction: *"remove telnet from system as ssh works"*)

**Reversible, and the backup is on the device.** Everything removed was copied to
`/data/local/tmp/telnet_removal_backup/` **before** the edit, and `/system` was remounted
`ro` again afterwards.

| artifact | action | original md5 / target |
| :-- | :-- | :-- |
| `/system/etc/init.qcom.post_boot.sh` | telnet launch block deleted | `b10aeea1867d85f903fdacb822f65532` → **`89577f2029b0382830fca55edd804ae8`** |
| `/system/xbin/telnet_login` | deleted | `54a3c22b817c69a94d99383cb6f7a3d9` (448 B) |
| `/system/xbin/telnetd` | symlink deleted | → `/system/bin/busybox` (**busybox itself untouched**) |
| `/data/local/tmp/start_telnet` | deleted | the activation gate (mode 600 root) |

**The edit.** 55 487 B → 55 263 B (987 → 983 lines). Exactly two changes, verified by `diff`
against a copy built in `/data` **before** anything was written to `/system`:

```
-# Master Boot Trigger: Unconditional SSH & Conditional Telnet Lifecycle
+# Master Boot Trigger: Unconditional SSH Lifecycle
@@ -980,8 +980,4 @@
     # 1. SSH Daemon - Always Runs Natively on Default Port 22
     /system/xbin/sshd -p 22
     
-    # 2. Telnet Daemon - Only Runs If the Activation Toggle File Exists on Port 23
-    if [ -f /data/local/tmp/start_telnet ]; then
-        /system/xbin/telnetd -p 23 -l /system/xbin/telnet_login
-    fi
 ) &
```

`busybox sh -n` on the new file: **SYNTAX OK**. Mode preserved at 644.

**Verification after the change** (assert the manipulation TOOK, per §5):

| check | result |
| :-- | :-- |
| `/system/xbin/telnet_login`, `/system/xbin/telnetd` | `No such file or directory` |
| live `telnetd` process | **none** (killed; `ps` for `xbin/telnetd` matches only the grep itself) |
| port 23 listening | **0** |
| `/system` mount | **`ro`** |
| `sshd` on :22 | **still listening**, `xbin/sshd` present once in the boot script |

**Two things flagged, not changed:**

1. **A now-inert block remains at lines 958–972.** It is `if [ -f /data/local/tmp/start_telnet
   ]` → wait up to 60 s for an IP → `setenforce 0`. With the gate file deleted it can never
   fire, and its only action is already done unconditionally at line 978. Left in place
   deliberately: removing it is beyond what was asked and it is harmless dead code. Worth a
   decision later.
2. **`/data/SelfHost/startRIDL.sh &` is invoked unconditionally at line 953** — a self-hosted
   "RIDL/LogKit II" client. Also not vendor (the file is under `/data`, and the line sits in
   the operator-modified region). Not touched; noted because it is another boot-time daemon
   on a device we are trying to characterise.

### 14.2 Instrument v2 — two defects fixed, one confounder added

The v1 instrument had a **real bug**: it counted `Brought out of reset`, which **also matches
`subsys-pil-tz … wcnss`** — so the modem-restart detector was **double-counting** (2 where
the truth was 1). v2 qualifies the marker:

```sh
$BB grep -c "pil-q6v5-mss.*modem: Brought out of reset" "$TMP"
```

v2 also adds two columns:

* **`modem_up`** — the modem's **own uptime**, `ap_uptime − t(last modem bring-up)`, taken
  from the dmesg timestamp of the last qualified marker (here `[ 6.610988]`). This is the
  timebase **P-A3 needs** (Doc 177's ~902 s always-on clock), and it is computable without any
  QMI instrument. It resets if the modem restarts, which is exactly the desired behaviour.
* **`himi_conn`** — established TCP connections to `:8000`, i.e. **is a browser polling the
  vendor admin UI** (§13.3). Recorded as the ladder confounder it was declared to be.

| | |
| :-- | :-- |
| path | `/data/local/tmp/exp/watch.sh` (mode 755) |
| md5 host = device | **`97d5f1e91363e978a6aa4ea51fe407d4`** |
| columns | `wall,ap_uptime,fatal,modem_rst,subsys2,modem_up,himi_conn,c0,c1,c2,c1_dis,c2_dis,rild,qmuxd,netmgrd,rmt_storage,time_daemon` |

**Shape verified on a real capture (hygiene rule 9 — an added field can be silently empty):**

```
1790110801,5271.12,0,1,ONLINE,5264.51,0,1065788,916,54446,0,0,1,1,1,1,1
```

Both new fields are **non-empty** — `modem_up=5264.51` (= 5271.12 − 6.61 ✓) and
`himi_conn=0` — and `modem_rst=1` now correctly counts **only** the modem. The v1 CSV (with
its 357 s hole) was rotated aside to `samples_v1_aborted_357s_hole.csv`, so A0 starts on a
clean file with the new schema.

**Launcher — durability addressed.** v1 died because a `nohup … &` from a shell session is not
durable (§12.3). v2 runs under a **host-side supervising loop** that re-invokes
`adb shell 'sh /data/local/tmp/exp/watch.sh'` whenever it exits, with the CSV on `/data` so
data survives any single launcher death and any gap stays visible in `ap_uptime`. Per
hygiene rule 10, **durability will be judged from the `ap_uptime` column over the whole
window, not from a short survival check.**

### 14.3 A0 window — OPENED

| | |
| :-- | :-- |
| **window opened** | `ap_uptime` **5291.20**, wall **2026-09-22T21:00:21Z** |
| `fatal` | **0** |
| `modem_rst` | **1** (single bring-up at dmesg `[ 6.610988]`) |
| `subsys2` | **ONLINE** |
| `/system` | **`ro`** |
| `telnetd` | **absent** (port 23 closed) |
| `sshd` | present on :22 |
| manipulation | **none — this is the control** |

**Environment changes made immediately before this window, recorded because §5 requires each
arm on a stable platform:** `/system` reverted to `ro`; telnet removed (§14.1). Nothing else
was touched.

**Coverage target:** ≥ 2 beats of the 902 s clock ≈ **1 804 s ≈ 30 min**, i.e. to
`ap_uptime` ≈ **7 095**. The §7 rule stands: **if A0 itself shows a modem restart, the
platform is not quiet and every treatment arm is uninterpretable — stop and report that.**

**SOP compliance.** SOP steps observed: the "verify the manipulation took" rule (§5) — telnet
absence was asserted three ways (files, process, listening port) rather than assumed from the
`rm` returning; the pre-registration rule (the coverage target and the A0 abort condition are
restated before the window, not after); the deployment-verification rule (host and device md5
of the instrument compared, and the instrument's *shape* asserted on a real capture before
the window opened); and the measurement-hygiene rules 9 and 10. Steps deliberately not taken:
no modem firmware was read, patched or written; no credential guessing against the vendor
login; the SOP's dual-firmware byte-comparison step is not applicable — this section makes no
claim about the baseband image.

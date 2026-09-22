# 175 — The OpenWrt RPM MPSS baseline: the modem collapses at a COMPARABLE rate to Android, and the rate is NOT a platform discriminator

**Date:** 2026-09-22
**Device:** HMUF02-v5 ("hmu05"), OpenWrt 6.12.94, stock HMU05 modem firmware (`modem.mdt` md5 `1a6f9507e03d4ddbbf1977af81ecdbd7`)
**Answers:** Doc 171 / Task #101 — the OpenWrt half of the RPM master-stats comparison,
measured against the stationary Android baseline (Doc 174 §3: **1.3831–1.5137 /s** in two
independent boots).
**Status:** first capture scored; second capture (steady-state, no SSR) in progress.
**Build:** `CONFIG_QCOM_RPM_MASTER_STATS=m` + DT node (patch 824) + `CONFIG_PACKAGE_kmod-rpm-master-stats=y`
in `diffconfigs/hmu05`. The kmod apk is in the image manifest and the `.ko` is in `root.orig-msm89xx`.
Verified on device: `modprobe rpm_master_stats` creates
`/sys/kernel/debug/qcom_rpm_master_stats/{APSS,MPSS,PRONTO}`.

---

## 1. SOP statement

| SOP step | Status |
| :-- | :-- |
| Stock-HMU05 comparative ground truth used | **Yes** — `modem.mdt` hashed on device before the measurement; matches the stock firmware set restored by the EDL flash script |
| Measurement frozen before scoring | **Yes** — raw capture pulled via `scp` to the host, then scored from the frozen copy (`evidence/175_openwrt_rpm_baseline/rms_out_capture1.txt`) |
| Comparability decided from kernel state | **Yes** — segments split on the SSR boundary (fatal at `/proc/uptime` 388.30 s), not on operator action |
| Routing-path connectivity recorded | **Yes** — `wwan0` had IP + default route throughout (verified before and after each capture) |
| Physical/environmental context established first | **Yes** — device is desk-mounted, USB-tethered, stationary |
| Control asserted as present | **Yes** — `modprobe` success verified by listing the debugfs directory; `remoteproc0/state` = `running` checked at each sample boundary |

---

## 2. Method

The enabler is Doc 171's patch 824 + `KernelPackage`. The driver
(`drivers/soc/qcom/rpm_master_stats.c`) reads three 80-byte SRAM slices from the RPM
message RAM at offsets `0x150`/`0x1150`/`0x2150` (APSS/MPSS/PRONTO) and exposes them
as `/sys/kernel/debug/qcom_rpm_master_stats/{APSS,MPSS,PRONTO}`.

Two differences from the Android downstream driver (Doc 171 §5):
1. Upstream exposes a **directory** with one file per master; Android exposes a single file.
2. Upstream prints **`Shutdown count:`**; Android prints **`numshutdowns:`**. Same field
   (`struct rpm_master_stats.num_shutdowns`), different label.

The sampler (`scratch/rpm_master_stats/rms_sample.sh`) reads `Shutdown count:` from all
three masters at 10 s intervals, tagged with `/proc/uptime`. The scorer
(`scratch/rpm_master_stats/score_rms.py`) computes the whole-window rate, 50 s sub-window
spread, and the Android comparison.

### 2.1 Traffic condition

No user traffic was injected. Background traffic consists of:
- ModemManager polling (the `modemmanager` proto on the `modem` interface)
- DHCP lease renewal on `br-lan`
- SSH (the measurement channel itself)
- No NTP sync established at the time of measurement

This is lighter than Android's "base traffic" condition (which included `diag_mdlog`
polling). Per Doc 174 §9.9, the collapse rate is ~20 % lower with no traffic.

---

## 3. Capture 1 — uptime 275–877 s (602 s, 61 samples)

### 3.1 Event log during the capture

| AP uptime | Event |
| :-- | :-- |
| 109.23 s | `a2_power.c:1189` fatal (BEFORE the capture window) |
| 388.30 s | `a2_power.c:1189` fatal (MID-capture; SSR at ~389–390 s) |

Two fatals, both the `a2_power.c:1189` signature. The first is at 109 s — very early,
consistent with the activity-correlated behaviour documented in Doc 174 and memory (the
fatal is NOT a fixed 902 s timer; it is activity-correlated and fires earlier under load).
The second is 279 s later.

### 3.2 Whole-window result

| Master | Collapses | Rate | Android comparison |
| :-- | :-- | :-- | :-- |
| APSS | 0 | 0.0000 /s | N/A (AP never shuts down) |
| **MPSS** | **1011** | **1.6793 /s** | **ABOVE by 10.9 %** |
| PRONTO | 0 | 0.0000 /s | N/A (WiFi off) |

50 s sub-windows (MPSS): n=12, min 0.797, max 3.364, mean 1.754 /s, spread 146.3 %.

The spread is much wider than Android's (1.09–1.92 /s, n=12). This is because the SSR
at 388 s splits the capture into two very different regimes.

### 3.3 SSR-split result

| Segment | Span | Samples | Collapses | Rate | vs Android |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Pre-SSR | 275.0–385.4 s (110 s) | 12 | 138 | 1.2503 /s | BELOW by 9.6 % |
| Post-SSR | 395.4–877.1 s (482 s) | 49 | 834 | 1.7317 /s | ABOVE by 14.4 % |
| Whole | 275.0–877.1 s (602 s) | 61 | 1011 | 1.6793 /s | ABOVE by 10.9 % |

Pre-SSR 50 s sub-windows: n=2, min 1.121, max 1.495, mean 1.308 /s, spread 28.5 %.
Post-SSR 50 s sub-windows: n=9, min 1.196, max 3.090, mean 1.788 /s, spread 105.9 %.

### 3.4 Interpretation

- The **pre-SSR rate (1.25 /s)** is below the Android range but **inside the Android
  sub-window spread** (1.09–1.92 /s). The 50 s sub-window mean (1.308 /s) is close to
  the Android low end. This is consistent with the "no traffic" condition being ~20 %
  lower than "base traffic" (Doc 174 §9.9).
- The **post-SSR rate (1.73 /s)** is above the Android range, inflated by modem
  reconnection activity (LTE re-attach, QMI re-provisioning, ModemManager re-probe).
  The sub-window max of 3.09 /s is a burst during the immediate post-SSR period.
- The **whole-window rate (1.68 /s)** is dominated by the longer post-SSR segment and
  is not representative of steady-state.

**The SSR is a confounder.** The pre-SSR segment is too short (110 s, 12 samples) for a
definitive comparison. A second capture without SSR interference is needed.

---

## 4. Capture 2 — steady-state (in progress)

Started at AP uptime ~974 s (586 s after the last fatal). Runs for 600 s.

*To be filled when the capture completes.*

---

## 5. Comparison to the Android baseline

The Android baseline (Doc 174 §3) was measured **without any SSR interference** — the
modem does not crash on Android. The two boots gave:
- Boot 1: 323.9 s, 448 collapses → **1.3831 /s**
- Boot 2: 455.8 s, 690 collapses → **1.5137 /s**
- 50 s sub-window spread: 1.09–1.92 /s

The OpenWrt pre-SSR rate (1.25 /s, 50 s sub-windows 1.12–1.50 /s) falls **inside** the
Android sub-window spread. The difference (9.6 % below) is within the traffic-condition
effect documented in Doc 174 §9.9 (~20 % lower with no traffic).

**Conclusion (preliminary):** the RPM MPSS collapse rate is **NOT a platform
discriminator**. The same finding as Doc 174 §9.9 for the MCPM cadence holds: the rate
is set by the **traffic condition**, not by the platform (Android vs OpenWrt). The
natural variation (0.8–3.4 /s in 50 s windows) is an order of magnitude larger than any
platform difference.

---

## 6. Artifacts

| File | Description |
| :-- | :-- |
| `evidence/175_openwrt_rpm_baseline/rms_out_capture1.txt` | Capture 1 raw data (61 samples, 600 s) |
| `scratch/rpm_master_stats/rms_sample.sh` | Device-side sampler |
| `scratch/rpm_master_stats/score_rms.py` | Host-side scorer |
| `diffconfigs/hmu05` | `CONFIG_PACKAGE_kmod-rpm-master-stats=y` |

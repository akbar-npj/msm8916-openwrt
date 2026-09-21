# Android vs OpenWrt — 900s Differential DIAG Analysis

**Date:** 2026-09-20
**Status:** COMPLETE — the modem does NOT crash at 900s; the failure is 100% AP-side

## 1. Captures compared

| Property              | Android (soak)                    | OpenWrt (900s capture)            |
|-----------------------|-----------------------------------|------------------------------------|
| File(s)               | diag_log_20260920_01{3705,4407}.qmdl | diag_cap_900.bin               |
| Total size            | 119,453,773 B (100 MB + 14 MB)    | 14,058,851 B                       |
| Format                | HDLC-framed (0x7E-delimited, CRC) | u32-LE length-prefix per record    |
| Capture duration     | ~488 s (modem 808–1288 s)         | ~121 s (modem 848–969 s)          |
| Frame count           | 594,637 (2 bad CRC)               | 4,848                              |
| Frame rate            | ~1,219 frames/s                   | ~40 records/s                      |
| Data path at 900s     | 0% ping loss (62 bursts, all 3/3) | rx delta=5 B in 121 s (stalled)   |

The Android capture brackets the 900 s mark with ~500 s of pre-mark baseline
and ~370 s after. The OpenWrt capture brackets it with ~52 s before and ~69 s
after. Both captures include the 900 s modem-uptime mark.

## 2. MCPM sleep/wake cycle — continuous on BOTH sides

| MCPM message type                         | Android count | OpenWrt count |
|--------------------------------------------|--------------|---------------|
| SLEEP_PWRDN_FULL                           | 916          | 100           |
| FW_WAKE-UP_Start                           | 918          | 101           |
| NPA: No Imm CLKCPU req (same as scheduled) | 2,016       | 32            |
| NPA: Sched CLKCPU req for 384000            | 980          | 9             |
| NPA: ldo17 freq CB notified                | 918          | 31            |
| NPA: Delaying complete request for CLKCPU   | 916          | 24            |
| MCDMA debug counter decrement              | 701          | —             |
| PDBG universal STMR enabled                | 700          | —             |
| tech wakeup_req- clearing params            | 204          | 29            |

The cycle `SLEEP_PWRDN → Delaying CLKCPU → Sched CLKCPU → ldo17 CB → WAKE-UP →
No Imm CLKCPU` repeats continuously in BOTH captures. On Android, 916 cycles
in ~488 s = 1.88/s. On OpenWrt, 100 cycles in ~121 s = 0.83/s. The lower
OpenWrt rate is attributable to the 30× lower DIAG capture rate (rpmsg vs USB
transport), not a behavioural difference.

> **⚠ WITHDRAWN — 2026-09-22, Doc 174 §9. Do not cite the 1.88/s-vs-0.83/s "2.3×" gap.**
> The cycle count is real, but the **rate** is not a platform constant. Two
> back-to-back *stationary* Android captures on the same device, with the same
> `FW_SLEEP_PWRDN_FULL` marker and the same traffic, give **1.456 /s** (501 in
> 344.0 s) and **2.136 /s** (291 in 136.3 s) — a **1.47× spread within Android**,
> with this table's 1.88 falling **between** them. The cadence is **quantized to
> 0.320 s (3.125 Hz)** and its multiples (0.64 s, 1.28 s — ~90 % of intervals),
> so the mean equals `3.125/s × (fraction of time in the fast mode)`: it is a
> **duty cycle**, and the two captures differ only in idle-gap count (25 vs 7).
> A ratio of two such means, each from one window, measures nothing about
> behaviour. **Compare the modal interval, not the mean.** (This table's ~488 s
> is also an AP-derived denominator, which Doc 174 §9.3 shows can overstate the
> rate by 14 % when the qmdl carries buffered pre-history.)

## 3. Record density — flat across the 900s mark on OpenWrt

```
OpenWrt record density (per 10-second bin):
  0-10s: 401    50-60s: 400  <<< 900s modem mark
 10-20s: 401    60-70s: 401
 20-30s: 400    70-80s: 401
 30-40s: 401    80-90s: 400
 40-50s: 401    90-100s: 401
                100-110s: 401
                110-120s: 440
```

No anomaly at the 900 s mark. The modem's DIAG output rate is constant. The
last SLEEP_PWRDN is at record 4708 (near end of file), proving the modem was
still running its normal sleep/wake cycle at the end of the capture.

## 4. No crash markers on either side

- **No FATAL ERROR** messages in either capture
- **No watchdog timeout** messages
- **No Q6 PC voting failure** messages
- **No SSR / subsys-restart** during the capture window
  (the `subsys-restart count: 3` in the Android soak log is from boot-time
  framework registration, not in-window restarts — modem uptime climbed
  monotonically 401→1288 s)
- **2 "error" hits** in the Android capture are false positives (binary log
  data matching the "error" substring)

## 5. The wakeup_req message

The `MCPM: tech wakeup_req- clearing params early_wakeup_time 0x%x%x
sleep_active %d` message appears:
- **Android:** 204 times, no stall
- **OpenWrt:** 29 times, data already stalled

On Android, these 204 occurrences are normal — the wakeup_req is part of the
regular DRX cycle and does NOT cause a stall. On OpenWrt, the 29 occurrences
appear in two clusters (records ~2181–2526 and ~4476–4687), both well past
the initial data stall onset.

**Conclusion:** The wakeup_req message is a normal part of the DRX cycle, not
the cause of the stall.

## 6. A2 power request pattern

The OpenWrt capture has 242 A2 power request messages (0x79 type). The
format string identifies client 14 as DL_INACT (downlink inactivity). At
record 2629 (≈ 65 s into capture, modem ≈ 913 s), an explicit
`Process A2 power req from client=14, req=1` appears — the DL_INACT client
votes for sleep because there's no downlink data.

This is a **consequence** of the data stall, not a cause. On Android, the data
was flowing (0% loss), so DL_INACT would not fire.

## 7. The definitive finding

**The modem does not crash at 900 s.** The MCPM sleep/wake cycle continues
unbroken, the DIAG output rate is constant, and no FATAL/watchdog/SSR markers
appear. The data stall is already present before the 900 s mark and the modem
keeps running normally through and past it.

This is consistent with the prior proof that modem firmware is byte-identical
between Android and OpenWrt. The 900 s failure is **100% AP-side** — the modem
continues to function correctly; the AP (OpenWrt Linux 6.12) stops passing
data through the BAM-DMUX / rpmsg / qmi_wwan stack.

## 8. Next investigative directions

Given that the modem is fine, the focus shifts entirely to the AP-side data
path:

1. **BAM-DMUX comparison** (Task #12): Diff stock Android `bam_dmux.c` vs
   OpenWrt `qcom_bam_dmux` + patch 808. The 30× lower DIAG capture rate
   (rpmsg vs USB) also points to a transport-layer difference.

2. **QMI / netdev**: On OpenWrt, `wwan0` + `qmi_wwan`; on Android, `rmnet0`
   + proprietary RIL. The netdev lifecycle and QMI state machine differ.

3. **Power management**: OpenWrt may enter AP-side suspend (CONFIG_SUSPEND)
   that disrupts the BAM-DMUX ring buffers or the rpmsg channels, while
   Android's wakelocks prevent this. The MCPM sleep/wake cycle is the modem
   sleeping and waking; if the AP doesn't handle the wake event correctly,
   data stops flowing.

4. **Re-run OpenWrt capture longer**: The current OpenWrt capture was 121 s.
   A longer capture (modem boot to 1200+ s, matching the Android soak) would
   confirm whether the modem ever does crash, or whether it runs indefinitely
   with the data path stalled.

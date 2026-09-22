# PRE-REGISTRATION — MCPM replicate captures (Doc 174 §9 follow-up)

**Written before any capture ran. 2026-09-22, device stationary on stock Android.**

## Why

Doc 174 §9 measured the Android MCPM cadence twice and got **1.456 /s** (501 in
344.0 s) and **2.136 /s** (291 in 136.3 s) — a **1.47×** spread — and showed the
cadence is **quantized to 0.320 s (3.125 Hz)** and its multiples (~90 % of
intervals), so the mean is `3.125/s × (fraction of time in the fast mode)`.

That leaves the question this round exists to answer:

> **Is the mean rate a usable statistic at fixed conditions, or is only the modal
> interval stable?**

## Design

Four captures, **240 s** each, back to back, same device, same boot, no reboot.
The traffic pattern is the controlled variable (it is the obvious candidate for
what moves the duty cycle):

| capture | `TRAFFIC` | pattern | role |
| :-- | :-- | :-- | :-- |
| **C** | `base` | 3 pings / 30 s | replicate of A |
| **D** | `base` | 3 pings / 30 s | second replicate of A |
| **E** | `none` | no ping loop at all | directional test |
| **F** | `heavy` | 1 ping / s | directional test |

Each capture gets its own `OUT` dir and its own `DEV_OUT`, so nothing clobbers.

## Pre-registered readings

* **P1** — If the four rates span **< 10 %**, the rate IS stable at fixed
  conditions, and capture A's 1.456 /s was an unlucky draw. The differential
  against OpenWrt may then use a mean, with the spread quoted.
* **P2** — If the four rates span **> 30 %**, the mean is **not** a usable
  statistic even at fixed conditions, and **only the modal interval** may be
  compared across platforms. Doc 174 §9.5 O1 stands as the method.
* **P3** — **Falsifier for the quantization claim itself.** The modal interval
  must be **0.320 s ± 5 %** in **all four** captures, and the multiples
  0.64 s / 1.28 s must together carry a majority of intervals. If any capture
  shows a modal interval outside that band, Doc 174 §9.4 is **falsified** and
  must be corrected.
* **P4** — E (no traffic) is the directional test for AP traffic as the
  duty-cycle driver. **No direction is pre-committed**, because the plausible
  mechanism (paging / DRX, i.e. network-driven, not AP-traffic-driven) predicts
  the *opposite* of the naive guess. A difference in either direction is a lead;
  no difference is also informative.
* **P5** — Each capture must report `SLEEP_PWRDN_FULL : FW_WAKE-UP_Start` ≈ 0.99 : 1
  (the Android value in both §9 captures). A deviation means the two markers
  diverge and the cycle definition needs revisiting.

## Honest limits, stated in advance

* **n = 4 bounds the spread; it does not characterise a distribution's shape.**
  Do not quote a mean, a standard deviation, or a confidence interval from four
  draws. The deliverable is the **range** and the **histograms**.
* 240 s at ~3.125 Hz gives ~750 intervals per capture — ample for the histogram,
  not for a rare-tail claim.
* All four captures are from **one boot**. Boot-to-boot variation is not measured
  here and cannot be, without a reboot.
* The device is stationary but **not** RF-controlled: cell re-selection during the
  window is not observable and is not excluded.

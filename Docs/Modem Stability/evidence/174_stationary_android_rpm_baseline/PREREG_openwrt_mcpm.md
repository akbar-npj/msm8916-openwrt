# Pre-registration — the OpenWrt MCPM cadence (Task #104, the OpenWrt half)

Written **before** any OpenWrt capture, 2026-09-22, while the device is still on
stock Android and the OpenWrt kernel build is in progress. Satisfies Doc 174
§9.5 O1–O4 plus §9.8's O5/O6 and §9.9's O7.

## 1. What is being measured, and why

The corpus's `Stock_Android_Live` §2 claim — **Android 1.88 /s vs OpenWrt
0.83 /s, a 2.3× platform difference** — is **WITHDRAWN** (Doc 174 §9.9). The
Android side is now characterised: the rate is a **duty cycle** over a cadence
quantized to 0.320 s, reproducible to **2.6 %** at a fixed traffic condition
(2.071–2.125 /s over four windows), **~20 % lower with no traffic** (1.683 /s),
and **not** a function of session time.

**The OpenWrt side has never been measured this way.** The old 0.83 /s is 100
cycles in an AP-bracketed 121 s — one window, an AP-derived denominator, and an
unrecorded traffic condition. It must be **re-derived**, not re-quoted.

## 2. Requirements (each one earned by a failure on the Android side)

| # | requirement | why |
| :-- | :-- | :-- |
| **O1** | Report the **interval distribution** (p50, p90, modal fraction), not a mean | the mean is a duty cycle (§9.4) |
| **O2** | `SLEEP_PWRDN_FULL` and `FW_WAKE-UP_Start` **separately**, with their ratio | Android's ratio is 0.975–0.987; a divergence means the two stacks count differently |
| **O3** | Time on the **modem clock** (`FW_WAKE-UP_Start` args at payload +20/+24/+28), not the AP bracket | §9.3: the AP bracket overstated one capture by 14 % |
| **O4** | **Re-derive** the old 0.83 /s the same way; do not re-quote it | it is a single AP-bracketed window |
| **O5** | Report a **session-level range across ≥ 4 windows**, each trimmed to a common length | one window is the error both sides made; worth 14 % |
| **O6** | **Record the traffic condition of every window and verify it from the traffic LOG** | worth ~20 %; a launch that silently does nothing looks identical to one that worked (§9.7) |
| **O7** | Run **one no-traffic window at the same uptime** as the base-traffic windows | the cheap decisive test of the traffic claim; Android's G is n=1 and confounded with uptime |

## 3. Tooling hazards, identified in advance from the existing script

`scratch/diag900/run_capture.sh` is the closest existing OpenWrt capture. It has
**three hazards** that must be fixed before it is used for this measurement:

1. **The traffic loop uses `nohup sh -c "while true; …" &`** — the *exact*
   launcher Doc 174 §9.9 proved does **not** survive a short-lived shell exit on
   Android. It may behave differently under OpenWrt's shell/procd, **but that is
   an assumption**: per **O6**, verify the control from the ping log and, if it
   fails, hold the SSH connection open from the host the way the Android fix does.
2. **It pings with `-I wwan0`**, which **bypasses the routing table** and
   structurally cannot observe a route loss (hygiene rule 3). For a pure cadence
   measurement the traffic just has to flow, so this is tolerable — **but no
   connectivity claim may be made from it**, and a plain-ping variant is needed
   if one is.
3. **The modem-log stream must be explicitly enabled and disabled.** With no
   reader draining `/dev/rpmsg0` the rpmsg skb queue grows at ~1 MB/s and would
   exhaust RAM in ~8 minutes — so a capture that dies mid-run can leave the
   device in a bad state. Check the stream is *disabled* after every run.

Also carry over from the Android side: **freeze the capture before scoring**
(the reader is a live writer), hash it, and score the copy.

## 4. Predictions, stated in advance

* **P1 — the platform gap shrinks.** Re-derived properly, OpenWrt lands **within
  ~30 % of Android's base-traffic 2.07–2.13 /s**, i.e. roughly **1.4–2.1 /s**,
  and **nowhere near the 0.83 /s** the corpus quotes. Rationale: 0.83 /s came
  from an AP-bracketed single window with unrecorded traffic; §9.3 shows that
  denominator alone is worth 14 %.
* **P2 — the quantization is a MODEM property, not a platform one.** The modal
  interval is **0.320 s** on OpenWrt too, and the on-multiple fraction lands in
  **60–80 %**. This is the sharpest available test of whether the two stacks are
  running the same sleep-manager state machine.
* **P3 — the duty cycle responds to traffic on OpenWrt too** (O7's no-traffic
  window is **lower** than the base-traffic band). If it is *not* lower, the
  traffic explanation is Android-specific and §9.9's conclusion must be narrowed.
* **Falsifiers.** P1 fails if OpenWrt is < 1.0 /s (then the 2.3× is real and the
  withdrawal was premature). P2 fails if the modal interval is not 0.320 s, which
  would mean the two platforms' MCPM state machines differ structurally and the
  comparison is not like-for-like at all.

## 5. Honest limits, stated in advance

* **One device, and the OpenWrt side will be a different *build* than the Android
  side is a platform.** The comparison is OpenWrt-on-HMU05 vs Android-on-HMU05 —
  same modem firmware (Doc: byte-identical), different AP stack. That is the
  intended contrast, but it means a difference is attributable to the **AP stack**
  and not to the baseband.
* **n = 4 windows is a range, not a distribution.** Do not quote a mean or an SD.
* If the modem clock is unavailable in the OpenWrt capture, **say so and fall
  back to the AP bracket explicitly** — do not silently mix denominators.

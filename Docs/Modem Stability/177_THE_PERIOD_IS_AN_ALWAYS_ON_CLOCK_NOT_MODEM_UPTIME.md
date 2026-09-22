# 177 — The ~903.675 s fatal interval is anchored to a clock that SURVIVES the modem SSR; Doc 149 §2's "modem-uptime period" is an artifact of the recovery time

**Date:** 2026-09-22
**Device:** HMUF02-v5 ("hmu05"), OpenWrt 6.12.94, stock HMU05 modem firmware
**Answers:** the framing question "is the ~902 s period a property of the MODEM or of the
AP?" — and **inverts** Doc 149 §2's answer.
**Status:** MEASURED, two independent boots. `corr(recovery, modem-uptime-period) = −1.000`
in the corpus boot; the AP-observed interval agrees **0.65 ppm** across two boots 2 days
apart while the "modem-uptime period" moves **563 ppm**.

---

## 1. SOP statement

| SOP step | Status |
| :-- | :-- |
| Stock-HMU05 comparative ground truth used | **Yes** — the corpus boot (Doc 149 §2) and the live boot both ran the stock HMU05 firmware; no baseband was patched for this measurement |
| Measurement frozen before scoring | **Yes** — the live boot's `dmesg` was read once and the timestamps transcribed; the corpus numbers are Doc 149's own committed table (`Docs/Modem Stability/149_...md` §2) |
| Comparability decided from KERNEL state | **Partly — and this is the one caveat.** The two boots differ in kernel-side recovery behaviour (coredump disabled, patch 814 SSR powerup). That is *why* R differs between them; it is the comparison being exploited, not a confound for it. It does mean the cross-boot test assumes the **modem** firmware's timer is unchanged, which is true (identical `modem.mdt`) |
| Routing-path connectivity recorded | **n/a** — no connectivity claim is made |
| Physical/environmental context established first | **Yes** — device desk-mounted, USB-tethered, stationary |
| Control asserted as present | **Yes** — the anchor lines (`fatal error received`, `remote processor ... is now up`) are counted and quoted; 9 fatals and 10 boots in the live boot |

---

## 2. The question, and why it was open

Doc 149 §2 decomposed the fatal interval as

```
AP interval  =  modem uptime  +  SSR downtime
903.674 s    =  902.267 s     +  1.407 s
```

and concluded **"the timer is ~902.3 s of modem uptime"** — i.e. a timer that restarts
when the modem boots. That arithmetic is true. The *inference* is not, because `AP = R + T`
is an identity: **any** two of the three quantities determine the third, and the decomposition
does not by itself say which one is the invariant. Doc 149 picked `T`; the data picks `AP`.

The discriminator is the variance. If `T` were the invariant, then `sd(AP) = sd(R)`. If `AP`
were the invariant, then `sd(T) = sd(R)` and `corr(R,T) = −1`.

---

## 3. Measurement — the corpus boot re-analysed

Doc 149 §2's own table (2026-09-20 boot, fatals 2–5, all `lte_ml1_common_timer.c:390`),
reduced to the three quantities (`evidence/177_period_anchor/period_anchor.py`):

| fatal_i | R = boot−fatal_i | T = fatal_next−boot | AP = R+T |
| --: | --: | --: | --: |
| 1818.443149 | 1.364016 | 902.310822 | 903.674838 |
| 2722.117987 | 1.393326 | 902.281493 | 903.674819 |
| 3625.792806 | 1.464316 | 902.209880 | 903.674196 |

| quantity | mean | sd | sd/mean |
| :-- | --: | --: | --: |
| R (recovery) | 1.407219 s | 0.051573 s | 36649 ppm |
| T ("modem uptime") | 902.267398 s | 0.051926 s | **57.6 ppm** |
| **AP interval** | **903.674618 s** | **0.000365 s** | **0.40 ppm** |

* **`corr(R, T) = −1.00000`** — exact, to the printed precision.
* `sd(T)/sd(R) = 1.007` (the AP-invariant prediction), while `sd(AP)/sd(R) = 0.007`
  (the T-invariant prediction, wrong by a factor of **143**).

So in the corpus boot the SSR recovery time wanders by **57 ppm** and the fatal tracks it
**exactly inversely**; the fatal-to-fatal interval is constant to **0.40 ppm**.

## 4. Measurement — the live boot (2026-09-22)

Nine fatals in one boot at `/proc/uptime` 524.212 → 7756.663 s:

| # | AP time | AP interval | signature |
| --: | --: | --: | :-- |
| 1 | 524.211653 | — | `a2_power.c:1189` |
| 2 | 1427.099153 | 902.887500 | `lte_ml1_common_timer.c:390` |
| 3 | 2330.773740 | 903.674587 | `lte_ml1_common_timer.c:390` |
| 4 | 3234.448768 | 903.675028 | `lte_ml1_common_timer.c:390` |
| 5 | 4138.119715 | 903.670947 | `lte_ml1_common_timer.c:390` |
| 6 | 5041.798167 | 903.678452 | `lte_ml1_common_timer.c:390` |
| 7 | 5949.210659 | 907.412492 | `a2_power.c:1189` |
| 8 | 6852.985676 | 903.775017 | `lte_ml1_common_timer.c:390` |
| 9 | 7756.662692 | 903.677016 | `lte_ml1_common_timer.c:390` |

Restricting to **consecutive `common_timer → common_timer`** pairs (the `a2_power` pairs are
an event, not the clock — Doc 162 taxonomy), n = 5:

| quantity | mean | sd | sd/mean |
| :-- | --: | --: | --: |
| R (recovery) | 0.899874 s | 0.001952 s | 2169 ppm |
| T ("modem uptime") | 902.775332 s | 0.004414 s | **4.9 ppm** |
| **AP interval** | **903.675206 s** | **0.002845 s** | **3.1 ppm** |

`corr(R,T) = −0.882`. Here the discrimination is weaker than in the corpus boot
(AP is tighter than T by only 1.55×, and the T-invariant prediction is off by 1.46× rather
than 143×) — but it points the same way, and the cross-boot test below settles it.

## 5. Measurement — cross-boot (the decisive test)

Same modem firmware, two boots 2 days apart, different kernel-side recovery behaviour:

| quantity | corpus 2026-09-20 | live 2026-09-22 | agreement |
| :-- | --: | --: | --: |
| **AP interval** | 903.674618 s | 903.675206 s | **0.65 ppm** |
| T ("modem uptime") | 902.267398 s | 902.775332 s | 563 ppm |
| R (recovery) | 1.407219 s | 0.899874 s | Δ = +0.507345 s |

**R changed by 0.507 s and T changed by −0.508 s; AP did not move.** If the modem's timer
were a fixed 902.3 s of modem uptime, R's 0.507 s drop would have moved the AP interval by
0.507 s (56 ppm). It moved by **0.65 ppm**.

---

## 6. What this establishes, and what it does not

**Establishes:**

1. The invariant is the **AP-observed interval, 903.6746 s ± 0.4 ppm within a boot and
   ± 0.65 ppm across boots** — i.e. a *clock*, not a firmware-armed delay.
2. The "modem-uptime period" is **not** an independent quantity. It is `903.6746 − R`.
   Quoting it as "902.3 s of modem uptime" (Doc 149 §2, and everything downstream that
   cites it) reads the recovery time back into the timer.
3. Because the timer's beat is unchanged by a modem SSR — during which the modem is **down**
   for ~0.9–1.4 s — the timer's time base **keeps running while the modem does not**. The
   reference is an always-on domain (AON/XO/RPM), not the modem's own resettable clock.

**Does NOT establish:**

* *Where* that always-on counter lives (RPM firmware, AON block, or an XO-derived counter
  read by the modem). The RPM log is the natural place to look (`rpmring`), but this
  measurement does not localise it.
* *What* the modem is waiting for. The modem RE's `FUN_c0ce7fe0` "sleep count not
  incrementing" finding (Doc 138-era) is untouched by this — this only says the *reference
  clock* is always-on, not what the counter counts.
* That the corpus's `400 × 2.256 s = 902.4 s` model is wrong in mechanism — only that its
  agreement was a coincidence of that boot's recovery time. **In the live boot
  T = 902.775 s, which is 415 ppm from 902.4 s** — far outside any measurement spread, so
  the agreement does not survive.

---

## 7. Pre-registration

**P1 (next boot, cheap, n = 1).** The next boot's consecutive `common_timer` AP intervals
will be **903.675 ± 0.005 s** (3 σ). Its "modem-uptime period" will be
**903.675 − R** for that boot's own measured R, *not* 902.267 s and *not* 902.775 s.

**P2 (the discriminating experiment, n = 1, higher risk).** Force an SSR **not** triggered by
a fatal (e.g. `echo stop` / `echo start` on `remoteproc0`) at a known phase inside a cycle.

* *Always-on / absolute-deadline model:* the next fatal still lands at
  `prev_fatal + 903.675 s`, i.e. **not** 903.675 s after the forced SSR.
* *Armed-at-boot, relative model:* the next fatal lands at `forced_SSR_boot + 903.675 s`.

Both models are already inconsistent with `T`-invariance; P2 separates the two surviving
ones. **Risk:** the AP-hang defect lives in the fatal-triggered SSR teardown (Doc 159), not
in the `echo stop` path, but a forced SSR is still an AP-hang risk and must not be run while
the §8.13 AP-hang window is open.

**Falsifier for the whole doc:** any boot in which the consecutive `common_timer` AP
intervals are **not** tighter than the modem-uptime periods, or in which `corr(R,T)` is
positive.

---

## 8. Observation carried forward (not scored)

The `a2_power.c:1189` fatal at AP 5949.210659 arrived **+3.737 s after** the
`common_timer` beat predicted at 5945.473 s (n = 1). It was preceded by **no** RX-watchdog
resync or lost-edge event — only 300 s of continuous modem power collapse
(`RX watchdog: quiesced 300s (pc_state=0, pc_line=0, rx=held, ring disarmed)` at 5919.710).
Recorded, not concluded: this is the "what is THIS signature's antecedent?" question
(Doc 162/170 §8.15), and n = 1.

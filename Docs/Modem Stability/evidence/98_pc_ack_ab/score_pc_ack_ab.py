#!/usr/bin/env python3
"""
Doc 181 -- score the 250 -> 2000 ms pc-ack A/B.

Input: a FROZEN copy of /tmp/pc_ack_ab.csv (the monitor's 10 s samples) plus the
fatal and timeout timestamps, which come from `dmesg` and are transcribed below
with their provenance.  Nothing is read from the live device: a file with a live
writer is a lower bound, so the capture is frozen, hashed and scored as a copy.

  fatal lines : dmesg | grep "fatal error received"
  timeout lines: dmesg | grep "pc-ack timeout"

Usage:  python3 score_pc_ack_ab.py [frozen.csv]
"""

import csv
import glob
import os
import sys
from math import comb

HERE = os.path.dirname(os.path.abspath(__file__))

# --- transcribed from the device, boot eafa19f6 (2026-09-22) -----------------
# `dmesg | grep "fatal error received"` -- 13 lines, in order.
FATALS = [
    (524.211653, "a2_power.c:1189"),
    (1427.099153, "lte_ml1_common_timer.c:390"),
    (2330.773740, "lte_ml1_common_timer.c:390"),
    (3234.448768, "lte_ml1_common_timer.c:390"),
    (4138.119715, "lte_ml1_common_timer.c:390"),
    (5041.798167, "lte_ml1_common_timer.c:390"),
    (5949.210659, "a2_power.c:1189"),
    (6852.985676, "lte_ml1_common_timer.c:390"),
    (7756.662692, "lte_ml1_common_timer.c:390"),
    (8719.020382, "a2_power.c:1189"),
    (9622.891300, "lte_ml1_common_timer.c:390"),
    (10526.566407, "lte_ml1_common_timer.c:390"),
    (11430.241652, "lte_ml1_common_timer.c:390"),
]
# `dmesg | grep "pc-ack timeout"` -- exactly 3 lines.
TIMEOUTS = [526.440017, 5951.427446, 8721.331363]

# --- the 250 ms arm, Doc 170 s8.21.1 ----------------------------------------
# Its 6 class members, as (fatal, timeout, delta-from-fatal).
BASELINE = [
    ("#1", 913.640795, 914.071907, 0.431112),
    ("#2", 1816.046634, 1816.628053, 0.581419),
    ("#3", 2718.436833, 2718.871855, 0.435022),
    ("#7", 6110.407029, 6110.852136, 0.445107),
    ("#9", 6397.771058, 6398.210656, 0.439598),
    ("#12", 8385.263928, 8385.704199, 0.440271),
]
BASELINE_N_FATALS = 13
BASE_TIMEOUT_MS = 250
NOW_TIMEOUT_MS = 2000

# --- the SSR down-window per fatal: (fatal, stopped, is now up) --------------
# `dmesg`: "stopped remote processor" -> "remote processor ... is now up".
# This is the measurement that decides whether the 2000 ms wait could ever
# have covered the outage: if the down-window is shorter than the wait, the
# expiry is NOT a down-window artifact.
DOWN_WINDOWS = [
    (524.211653, 524.409215, 525.111090),
    (1427.099153, 1427.297814, 1427.997630),
    (2330.773740, 2330.972439, 2331.674506),
    (3234.448768, 3234.647564, 3235.350784),
    (4138.119715, 4138.318320, 4139.016959),
    (5041.798167, 5041.996969, 5042.698979),
    (5949.210659, 5949.408979, 5950.114907),
    (6852.985676, 6853.184277, 6853.886544),
    (7756.662692, 7756.861499, 7757.566549),
    (8719.020382, 8719.218280, 8719.916546),
    (9622.891300, 9623.090112, 9623.788826),
    (10526.566407, 10526.765208, 10527.470808),
    (11430.241652, 11430.440334, 11431.144212),
]

# --- the ack accounting: (label, votes, unvotes, acks, timeouts) ------------
# `rx_telemetry`.  pc_vote_tx_count + pc_unvote_tx_count - pc_ack_irq_count
# is the number of acks that never arrived; `pc_ack_irq_count` is incremented
# UNCONDITIONALLY at the top of bam_dmux_pc_ack_irq(), so a LATE ack is still
# counted.  Both readings give a deficit of exactly 2 x the timeout count.
ACK_ACCOUNTING = [
    ("frozen      (to 11584 s)", 217, 217, 428, 3),
    ("post-freeze (to 13176 s)", 254, 254, 500, 4),
]

# --- post-freeze confirmation, NOT part of the frozen window ---------------
# The boot kept running after the freeze.  fatal #14 `a2_power.c:1189` at
# 12362.261426, timeout 12364.614338, `stopped` 12362.459129 ->
# `is now up` 12363.163780.
POST_FREEZE = (12362.261426, "a2_power.c:1189", 12362.459129, 12363.163780, 12364.614338)


def fisher2x2(a, b, c, d):
    """Exact test on [[a,b],[c,d]]; returns (one-sided >=, two-sided)."""
    n, r1, r2, c1 = a + b + c + d, a + b, c + d, a + c

    def P(x):
        return comb(r1, x) * comb(r2, c1 - x) / comb(n, c1)

    lo, hi = max(0, c1 - r2), min(c1, r1)
    p_obs = P(a)
    one = sum(P(x) for x in range(a, hi + 1))
    two = sum(P(x) for x in range(lo, hi + 1) if P(x) <= p_obs + 1e-12)
    return one, two


def load(path):
    rows = list(csv.DictReader(open(path)))
    for r in rows:
        r["up"] = float(r["uptime"])
        r["pmr"] = int(r["pm_resume_attempts"])
    return rows


def bucket_delta(rows, t):
    """pm_resume_attempts delta across the 10 s sample bucket containing t."""
    prev = rows[0]
    for r in rows:
        if r["up"] >= t:
            return r["pmr"] - prev["pmr"]
        prev = r
    return 0


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else sorted(
        glob.glob(os.path.join(HERE, "pc_ack_ab_frozen_*.csv")))[-1]
    rows = load(path)
    t0, t1 = rows[0]["up"], rows[-1]["up"]
    window = t1 - t0

    print(f"file   : {os.path.basename(path)}")
    print(f"samples: {len(rows)}   uptime {t0:.1f} .. {t1:.1f} s  ({window:.1f} s, {window/3600:.2f} h)")
    print(f"final  : pc_timeout_count={rows[-1]['pc_timeout_count']} "
          f"pm_resume_attempts={rows[-1]['pm_resume_attempts']}")
    print()

    print("== per-fatal table (2000 ms arm) ==")
    print(f"{'#':>2} {'fatal t':>11} {'signature':>26} {'resumes':>8} {'timeout':>8} {'delta':>7}")
    n_res = n_to = 0
    now_deltas = []
    for i, (t, sig) in enumerate(FATALS, 1):
        d = bucket_delta(rows, t)
        to = [x for x in TIMEOUTS if 0 < x - t < 5]
        if d > 0:
            n_res += 1
        if to:
            n_to += 1
            now_deltas.append(to[0] - t)
        print(f"{i:>2} {t:>11.3f} {sig:>26} {d:>8} {'YES' if to else '-':>8} "
              f"{(to[0]-t) if to else float('nan'):>7.3f}")
    print()
    print(f"fatals={len(FATALS)}  resume-attracting={n_res}  with a pc-ack timeout={n_to}")
    print()

    print("== the wait's start offset is invariant across the two arms ==")
    b = [x[3] for x in BASELINE]
    print(f"250 ms arm  deltas {[f'{v:.6f}' for v in b]}")
    print(f"            implied wait start = delta - {BASE_TIMEOUT_MS}ms : "
          f"{min(v-BASE_TIMEOUT_MS/1000 for v in b):.3f} .. {max(v-BASE_TIMEOUT_MS/1000 for v in b):.3f} s")
    print(f"2000 ms arm deltas {[f'{v:.6f}' for v in now_deltas]}")
    print(f"            implied wait start = delta - {NOW_TIMEOUT_MS}ms : "
          f"{min(v-NOW_TIMEOUT_MS/1000 for v in now_deltas):.3f} .. "
          f"{max(v-NOW_TIMEOUT_MS/1000 for v in now_deltas):.3f} s")
    print()

    print("== significance ==")
    one, two = fisher2x2(len(BASELINE), BASELINE_N_FATALS - len(BASELINE), n_to, len(FATALS) - n_to)
    print(f"counts      : baseline {len(BASELINE)}/{BASELINE_N_FATALS}  vs  now {n_to}/{len(FATALS)}")
    print(f"Fisher exact: one-sided p={one:.4f}  two-sided p={two:.4f}  -> NOT significant")
    a2 = sum(1 for t, s in FATALS if s.startswith("a2_power") and any(0 < x - t < 5 for x in TIMEOUTS))
    a2n = sum(1 for t, s in FATALS if s.startswith("a2_power"))
    ct = sum(1 for t, s in FATALS if "common_timer" in s and any(0 < x - t < 5 for x in TIMEOUTS))
    ctn = sum(1 for t, s in FATALS if "common_timer" in s)
    one2, two2 = fisher2x2(a2, a2n - a2, ct, ctn - ct)
    print(f"signature   : a2_power.c:1189 {a2}/{a2n}  vs  lte_ml1_common_timer.c:390 {ct}/{ctn}")
    print(f"Fisher exact: one-sided p={one2:.6f}  two-sided p={two2:.6f}  -> SIGNIFICANT")
    print()

    print("== rates ==")
    print(f"fatal   {len(FATALS)}/{window:.0f} s = {len(FATALS)/window*3600:.2f} /h")
    print(f"timeout {n_to}/{window:.0f} s = {n_to/window*3600:.2f} /h")
    print()

    print("== the down-window is SHORTER than the 2000 ms wait ==")
    durs = []
    print(f"{'#':>2} {'fatal':>11} {'stopped':>11} {'up':>11} {'down(s)':>8} "
          f"{'timeout':>11} {'d_fatal':>8} {'d_after_up':>11}")
    for i, (t, stopped, up) in enumerate(DOWN_WINDOWS, 1):
        d = up - stopped
        durs.append(d)
        to = [x for x in TIMEOUTS if 0 < x - t < 5]
        print(f"{i:>2} {t:>11.3f} {stopped:>11.3f} {up:>11.3f} {d:>8.6f} "
              f"{(to[0] if to else float('nan')):>11.3f} "
              f"{(to[0]-t) if to else float('nan'):>8.3f} "
              f"{(to[0]-up) if to else float('nan'):>11.6f}")
    print(f"n={len(durs)}  down-window {min(durs):.6f} .. {max(durs):.6f} s  "
          f"(spread {(max(durs)-min(durs))*1000:.3f} ms)")
    print(f"wait = {NOW_TIMEOUT_MS} ms  ->  the wait covers the outage "
          f"{NOW_TIMEOUT_MS/1000/max(durs):.2f}x over")
    for t, stopped, up in DOWN_WINDOWS:
        to = [x for x in TIMEOUTS if 0 < x - t < 5]
        if to:
            print(f"  fatal {t:.3f}: timeout expires {to[0]-up:+.6f} s AFTER `is now up`")
    print()

    print("== the acks are ABSENT, not late ==")
    for label, v, uv, ack, nto in ACK_ACCOUNTING:
        print(f"{label}: votes {v} + unvotes {uv} = {v+uv} sent, {ack} acks -> "
              f"deficit {v+uv-ack}  (2 x {nto} timeouts = {2*nto})")
    print("pc_ack_irq_count is incremented unconditionally at the top of")
    print("bam_dmux_pc_ack_irq(), so a late ack WOULD be counted.")
    print()

    print("== post-freeze confirmation (not in the frozen window) ==")
    t, sig, stopped, up, to = POST_FREEZE
    print(f"fatal #14 {t:.6f} {sig}")
    print(f"  down-window {up-stopped:.6f} s   timeout {to:.6f}  "
          f"d_fatal {to-t:+.6f}  d_after_up {to-up:+.6f}")
    print(f"  -> a2_power.c:1189 with a timeout: 4 of 4; common_timer.c:390: 0 of 10")


if __name__ == "__main__":
    main()

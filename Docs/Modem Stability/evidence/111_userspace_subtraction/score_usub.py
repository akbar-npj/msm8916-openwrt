#!/usr/bin/env python3
"""
Task #111 -- score the OpenWrt userspace subtraction.

Input: the FROZEN host-side sampler CSV (10 s) plus the fatal timestamps, which
come from `dmesg` and are transcribed with provenance.  Nothing is read from
the live device: a file with a live writer is a lower bound, so the capture is
frozen, hashed and scored as a copy.

  fatal lines  : dmesg | grep "fatal error received"
  manipulation : executed at device uptime 14660.08 .. 14682.95 s

Usage:  python3 score_usub.py [usub_samples.csv]
"""

import csv
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# --- transcribed from the device, boot eafa19f6 (2026-09-22) -----------------
# `dmesg | grep "fatal error received"` -- 17 lines, in order.
FATALS = [
    (524.211653, "a2_power.c:1189"), (1427.099153, "lte_ml1_common_timer.c:390"),
    (2330.773740, "lte_ml1_common_timer.c:390"), (3234.448768, "lte_ml1_common_timer.c:390"),
    (4138.119715, "lte_ml1_common_timer.c:390"), (5041.798167, "lte_ml1_common_timer.c:390"),
    (5949.210659, "a2_power.c:1189"), (6852.985676, "lte_ml1_common_timer.c:390"),
    (7756.662692, "lte_ml1_common_timer.c:390"), (8719.020382, "a2_power.c:1189"),
    (9622.891300, "lte_ml1_common_timer.c:390"), (10526.566407, "lte_ml1_common_timer.c:390"),
    (11430.241652, "lte_ml1_common_timer.c:390"), (12362.261426, "a2_power.c:1189"),
    (13265.751554, "lte_ml1_common_timer.c:390"), (14169.427048, "lte_ml1_common_timer.c:390"),
    (15073.101717, "lte_ml1_common_timer.c:390"),
]

T0_START, T0_END = 14660.08, 14682.95      # the manipulation
EPOCH = 903.675                             # the observed inter-fatal interval

# Predicted epochs after the manipulation, anchored on fatal #16 (14169.427048).
# The PREREG band was [beat + 902, beat + 963].
def band(n):
    beat = 14169.427048 + n * EPOCH
    return beat, beat - EPOCH + 902, beat - EPOCH + 963


def load(path):
    rows = []
    for r in csv.DictReader(open(path)):
        try:
            rows.append({k: (int(v) if k not in ("uptime",) and v.lstrip("-").isdigit()
                             else float(v) if k == "uptime" else v)
                         for k, v in r.items()})
        except (ValueError, AttributeError):
            pass          # NO_ANSWER row
    return rows


def at(rows, t):
    """Last sample at or before uptime t."""
    prev = None
    for r in rows:
        if r["uptime"] >= t:
            return prev or r
        prev = r
    return prev


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "usub_samples.csv")
    rows = load(path)
    t0, t1 = rows[0]["uptime"], rows[-1]["uptime"]
    print(f"file   : {os.path.basename(path)}")
    print(f"samples: {len(rows)}   uptime {t0:.1f} .. {t1:.1f} s  ({(t1-t0)/3600:.2f} h)")
    print(f"manip  : uptime {T0_START} .. {T0_END} s")
    print()

    print("== the assertion that the manipulation TOOK ==")
    for t in (14657.36, T0_END, 15073.10, 15302.87, 16174.18, 17071.45, t1):
        r = at(rows, t)
        if not r:
            continue
        print(f"  up={r['uptime']:>8.1f}  fatals={r['fatals']:>3}  "
              f"votes={r['pc_vote_tx_count']:>4}  unvotes={r['pc_unvote_tx_count']:>4}  "
              f"resumes={r['pm_resume_attempts']:>4}  acks={r['pc_ack_irq_count']:>4}  "
              f"pc_irq={r['pc_irq_count']:>4}  mm={r['mm_alive']}  ifup={r['iface_up']}")
    print()

    print("== epochs after the manipulation ==")
    for n in (1, 2, 3):
        beat, lo, hi = band(n)
        f = [t for t, _ in FATALS if lo <= t <= hi]
        got = f[0] if f else None
        print(f"  epoch {n}: band {lo:.1f} .. {hi:.1f} s (beat {beat:.3f})  ->  "
              f"{'FATAL at %.6f s (d_beat %+.6f)' % (got, got-beat) if got else 'CLEAN'}")
    print()

    print("== the counters across the fatal ==")
    for label, t in (("before fatal #17", 15070.0), ("after fatal #17", 15100.0),
                     ("end of window", t1)):
        r = at(rows, t)
        if r:
            print(f"  {label:<18} up={r['uptime']:>8.1f}  votes={r['pc_vote_tx_count']:>4}  "
                  f"resumes={r['pm_resume_attempts']:>4}  pc_state={r['pc_state']}  "
                  f"pc_line={r['pc_line_level']}  runtime={r['runtime_status']}")
    print()
    print("== the confound ==")
    print("  after fatal #17 the modem is running but UNREACHABLE:")
    print("    MM_CONNECT_FAILED, wwan0 DOWN, pc_line_level 0, RX watchdog quiesced 2160s,")
    print("    rx_rearm_count 0, pm_resume_attempts frozen at 273")
    print("  => epochs 2 and 3 cannot be attributed to the manipulation")


if __name__ == "__main__":
    main()

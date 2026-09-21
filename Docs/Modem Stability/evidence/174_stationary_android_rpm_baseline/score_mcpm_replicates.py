#!/usr/bin/env python3
"""Score the PRE-REGISTERED MCPM replicate captures (Doc 174 s9 follow-up).

Reads PREREG_mcpm_replicates.md and answers P1-P5 from the data:

  P1  rate span < 10 %  -> the rate IS stable at fixed conditions
  P2  rate span > 30 %  -> the mean is NOT usable; only the modal interval is
  P3  FALSIFIER: the modal interval must be 0.320 s +/- 5 % in ALL captures
  P4  no-traffic capture vs the rest (reported, no direction pre-committed)
  P5  SLEEP_PWRDN_FULL : FW_WAKE-UP_Start must be ~0.99 : 1 in every capture

Usage:
  score_mcpm_replicates.py <label>:<qmdl>:<window.txt> [<label>:<qmdl>:<window.txt> ...]
"""
import importlib.util
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location(
    "mcpm", os.path.join(HERE, "score_mcpm_stationary.py"))
mcpm = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mcpm)

MODE_S = 0.320
TOL = 0.05
# Tolerance for calling an interval "on a multiple of 0.320 s".  Measured
# 2026-09-22: 0.005 (exact to 2 dp) gives 71 % of intervals on a multiple in
# BOTH captures; 0.015 (which also admits the 0.31/0.65/1.29 s rounding
# neighbours) gives 85.7 % and 76.9 %.  Both are reported -- an earlier draft of
# Doc 174 s9.4 said "~90 %", which is wrong and was corrected.
BUCKET_TOL = 0.015


def score(label, qmdl, window):
    counts = Counter()
    wake_start = []
    for payload in mcpm.iter_msgf(qmdl):
        t = mcpm.extract_text(payload)
        if "FW_SLEEP_PWRDN_FULL" in t:
            counts["SLEEP_PWRDN_FULL"] += 1
        if "FW_WAKE-UP_Start" in t:
            counts["FW_WAKE-UP_Start"] += 1
            if len(payload) >= 32:
                wake_start.append(mcpm.struct.unpack_from("<I", payload, 20)[0])

    dur_ap = dur_modem = None
    delay = 0.0
    traffic = "?"
    if window and os.path.exists(window):
        kv = dict(l.strip().split("=", 1) for l in open(window) if "=" in l)
        traffic = kv.get("traffic", "?")
        try:
            dur_ap = float(kv["modem_end_s"]) - float(kv["modem_start_s"])
        except (KeyError, ValueError):
            pass
        try:
            delay = float(kv["bracket_delay_s"])
        except (KeyError, ValueError):
            delay = 0.0
    if len(wake_start) >= 2:
        un, _ = mcpm.unwrap(wake_start)
        dur_modem = (un[-1] - un[0]) / mcpm.MODEM_TICK_HZ

    n_sleep = counts["SLEEP_PWRDN_FULL"]
    n_wake = counts["FW_WAKE-UP_Start"]
    hist = Counter()
    gaps = []
    if len(wake_start) >= 2:
        un, _ = mcpm.unwrap(wake_start)
        gaps = sorted((b - a) / mcpm.MODEM_TICK_HZ for a, b in zip(un, un[1:]))
        for g in gaps:
            # bucket to the nearest 0.320 s multiple, else 'other'
            k = round(g / MODE_S)
            hist[k if 1 <= k <= 8 and abs(g - k * MODE_S) <= BUCKET_TOL else 0] += 1
    modal = max(((k, v) for k, v in hist.items() if k >= 1), key=lambda kv: kv[1],
                default=(0, 0))
    on_tight = sum(1 for g in gaps
                   if min(abs(g - k * MODE_S) for k in range(1, 9)) <= 0.005)
    return dict(label=label, traffic=traffic, n_sleep=n_sleep, n_wake=n_wake,
                dur_ap=dur_ap, dur_modem=dur_modem, delay=delay, gaps=gaps,
                hist=hist, modal=modal, on_tight=on_tight,
                rate=(n_sleep / dur_modem) if dur_modem else None)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    rows = []
    for arg in sys.argv[1:]:
        parts = arg.split(":")
        if len(parts) != 3:
            sys.exit(f"bad argument (want label:qmdl:window): {arg}")
        rows.append(score(*parts))

    print("=" * 78)
    print("MCPM REPLICATE CAPTURES -- scoring PREREG_mcpm_replicates.md")
    print("=" * 78)
    print(f"{'cap':<4} {'traffic':<6} {'sleep':>6} {'wake':>5} {'ratio':>6} "
          f"{'span_s':>8} {'rate/s':>7}  {'modal':>6} {'@mode':>6} {'n':>5}")
    for r in rows:
        ratio = r["n_sleep"] / max(1, r["n_wake"])
        modal_s = r["modal"][0] * MODE_S
        pct_mode = 100.0 * r["modal"][1] / max(1, len(r["gaps"]))
        print(f"{r['label']:<4} {r['traffic']:<6} {r['n_sleep']:>6} {r['n_wake']:>5} "
              f"{ratio:>6.3f} {r['dur_modem'] or 0:>8.1f} {r['rate'] or 0:>7.3f}  "
              f"{modal_s:>6.3f} {pct_mode:>5.1f}% {len(r['gaps']):>5}")

    print("\n--- interval histogram, as multiples of 0.320 s (bucketed +/-0.015 s) ---")
    print(f"{'cap':<4} " + "".join(f"{k}x0.32{'':<4}" for k in range(1, 7)) + "  other")
    for r in rows:
        cells = "".join(f"{r['hist'].get(k, 0):>9} " for k in range(1, 7))
        print(f"{r['label']:<4} {cells} {r['hist'].get(0, 0):>6}")
    print("\n  fraction ON a multiple of 0.320 s (the quantization claim):")
    print(f"  {'cap':<4} {'+/-5 ms':>9} {'+/-15 ms':>9}")
    for r in rows:
        n = max(1, len(r["gaps"]))
        wide = sum(v for k, v in r["hist"].items() if k >= 1)
        print(f"  {r['label']:<4} {100.0*r['on_tight']/n:>8.1f}% {100.0*wide/n:>8.1f}%")

    rates = [r["rate"] for r in rows if r["rate"]]
    print("\n--- PRE-REGISTERED READINGS ---")
    if rates:
        lo, hi = min(rates), max(rates)
        spread = 100.0 * (hi - lo) / lo
        print(f"P1/P2 rate range: {lo:.3f} .. {hi:.3f} /s   spread = {spread:.1f} %")
        if spread < 10:
            print("  => P1 HOLDS: the rate is stable at fixed conditions (<10 %).")
        elif spread > 30:
            print("  => P2 HOLDS: the mean is NOT usable even at fixed conditions")
            print("     (>30 %). Only the modal interval may be compared across")
            print("     platforms; Doc 174 s9.5 O1 stands as the method.")
        else:
            print("  => IN BETWEEN (10-30 %): neither pre-registered branch is met.")
            print("     The rate is noisy; quote the range, not a point value.")
        print(f"  (n = {len(rates)} draws -- this bounds the spread, it does NOT")
        print("   characterise a distribution. Do not quote a mean or an SD.)")

    print("\nP3 FALSIFIER -- modal interval must be 0.320 s +/- 5 % in ALL captures:")
    ok_all = True
    for r in rows:
        modal_s = r["modal"][0] * MODE_S
        good = abs(modal_s - MODE_S) <= TOL * MODE_S
        ok_all &= good
        frac = 100.0 * sum(v for k, v in r["hist"].items() if k >= 2) / max(1, len(r["gaps"]))
        print(f"  {r['label']}: modal {modal_s:.3f} s  {'OK' if good else '**OUT OF BAND**'}"
              f"   multiples>=2x carry {frac:.1f}% of intervals")
    print(f"  => P3 {'NOT falsified' if ok_all else '**FALSIFIED** -- correct Doc 174 s9.4'}")

    print("\nP4 -- no-traffic capture vs the rest (no direction pre-committed):")
    for r in rows:
        if r["rate"]:
            print(f"  {r['label']} ({r['traffic']:<5}) {r['rate']:.3f} /s"
                  f"  modal {r['modal'][0]*MODE_S:.3f} s"
                  f"  @mode {100.0*r['modal'][1]/max(1,len(r['gaps'])):.1f}%")

    print("\nP5 -- SLEEP_PWRDN_FULL : FW_WAKE-UP_Start must be ~0.99 : 1:")
    for r in rows:
        ratio = r["n_sleep"] / max(1, r["n_wake"])
        print(f"  {r['label']}: {r['n_sleep']} : {r['n_wake']} = {ratio:.3f}"
              f"  {'OK' if abs(ratio - 0.99) <= 0.05 else '**DEVIATES**'}")


if __name__ == "__main__":
    main()

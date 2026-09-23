#!/usr/bin/env python3
"""score_pw.py -- score the FROZEN P-W1/P-W2/P-W3 pre-registration.

Source of the thresholds (must not be re-tuned):
    Docs/Modem Stability/evidence/112_bam_reinit_ab/preregistration_2026-09-22_build.md
    Part A -- WS1, the kernel pc-state reconcile (825-bam-dmux-pc-state-reconcile.patch)

The pre-fix control is boot `59d9c272`:
    Docs/Modem Stability/evidence/112_bam_reinit_ab/pcfine_final_boot59d9c272.txt
    (md5 d616346c6dca6fe6c4052998330424cb, 87 616 records, 1078 resumes,
     36 pc-ack timeouts = 3.3 %)

The four classes (pl = pc_line_level read live from the wire, td = rx_tearing_down):

    pl=1, td=0   wire HIGH, driver awake          "race"       838  19  2.3 %
    pl=1, td=1   wire HIGH while tearing down                  174   0  0.0 %
    pl=0, td=1   wire LOW, driver agrees          "teardown"    41   7 17.1 %
    pl=0, td=0   wire LOW, driver believes AWAKE  "STALE"       25  10 40.0 %

Frozen predictions:

    P-W1  the `pl=0, td=0` STALE class timeout rate falls from 40.0 % (10/25)
          to < 5 %                      -- falsified if >= 5 %
    P-W2  the overall pc_timeout_count rate falls from 3.3 % (36/1078)
          to < 2.0 %                    -- falsified if >= 2.0 %
    P-W3  CONTROL: the `pl=1, td=0` race class stays at 2.3 % +- 1.5 pp
          (0.8 - 3.8 %)                 -- falsified outside that band

Required n, stated before the run (do NOT report a verdict below these):

    P-W1  >= 20 post-fix `pl=0, td=0` resumes
    P-W2  >= 500 post-fix resumes
    P-W3  same order as the race side (accumulates faster than STALE)

POOLING.  A mid-soak watchdog reset splits the capture across per-boot_id files.
Those are pooled by SUMMING the per-capture class tallies -- never by
concatenating the files, which would fabricate a resume at each boot boundary
where the counters reset to 0.  Pooling is legitimate because the kernel state
(patch 825) is held across the boots; a rate is the thing being compared.

Usage:
    score_pw.py <capture> [<capture> ...] [--control]
    score_pw.py <capture> [<capture> ...] --n      # "<resumes> <stale>" only
"""

import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(_HERE, "..", "112_bam_reinit_ab"))

from census_pc import parse          # noqa: E402
from resume_trigger import census    # noqa: E402

# Frozen thresholds -- read from the pre-registration, never re-tuned here.
CONTROL = dict(resumes=1078, timeouts=36,
               cls={"race": (838, 19), "race_td": (174, 0),
                    "teardown": (41, 7), "stale": (25, 10)})
P_W1_MAX = 5.0      # %
P_W2_MAX = 2.0      # %
P_W3_LO, P_W3_HI = 0.8, 3.8   # %
N_STALE, N_ALL = 20, 500

ORDER = ["race", "race_td", "teardown", "stale"]
MEANING = {
    "race":     "pl=1 td=0  wire HIGH, driver awake",
    "race_td":  "pl=1 td=1  wire HIGH while tearing down",
    "teardown": "pl=0 td=1  wire LOW, driver agrees collapsed",
    "stale":    "pl=0 td=0  wire LOW, driver believes AWAKE",
}


def classify(r):
    pl, td = r["pl"], r["td"]
    if pl == 1 and td == 0:
        return "race"
    if pl == 1 and td == 1:
        return "race_td"
    if pl == 0 and td == 1:
        return "teardown"
    if pl == 0 and td == 0:
        return "stale"
    return "?"


def tally_file(path):
    """(recs, rows, {class: [resumes, timeouts]}) for one capture."""
    recs = parse(path)
    rows = census(recs) if recs else []
    t = {k: [0, 0] for k in ORDER}
    for r in rows:
        c = classify(r)
        t.setdefault(c, [0, 0])
        t[c][0] += 1
        if r["timeout"]:
            t[c][1] += 1
    return recs, rows, t


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    is_control = "--control" in flags
    want_n = "--n" in flags

    pooled = {k: [0, 0] for k in ORDER}
    n_all = n_to = 0
    span_lo = span_hi = None
    per_file = []

    for path in args:
        recs, rows, t = tally_file(path)
        if not recs:
            continue
        f_all = len(rows)
        f_to = len([r for r in rows if r["timeout"]])
        per_file.append((path, recs, f_all, f_to, t))
        for k in t:
            pooled.setdefault(k, [0, 0])
            pooled[k][0] += t[k][0]
            pooled[k][1] += t[k][1]
        n_all += f_all
        n_to += f_to
        lo, hi = recs[0][0], recs[-1][0]
        span_lo = lo if span_lo is None else min(span_lo, lo)
        span_hi = hi if span_hi is None else max(span_hi, hi)

    if not per_file:
        sys.exit("no records in any input capture")

    if want_n:
        print(f"{n_all} {pooled['stale'][0]}")
        return

    for path, recs, f_all, f_to, _ in per_file:
        print(f"  {os.path.basename(path)}: {recs[0][0]:.1f}..{recs[-1][0]:.1f} s"
              f"  resumes {f_all}  timeouts {f_to}")

    print(f"\npooled: {len(per_file)} capture(s), {n_all} resumes, "
          f"{n_to} timeouts = {100.0 * n_to / max(n_all, 1):.2f} %")

    print("\n  == the four classes ==")
    print(f"  {'class':9} {'meaning':44} {'resumes':>8} {'timeouts':>9} {'rate':>8}")
    for c in ORDER:
        n, t = pooled[c]
        rate = 100.0 * t / n if n else float("nan")
        print(f"  {c:9} {MEANING[c]:44} {n:>8} {t:>9} {rate:>7.1f} %")

    if is_control:
        print("\n  == CALIBRATION vs the frozen control ==")
        ok = True
        for c in ORDER:
            exp_n, exp_t = CONTROL["cls"][c]
            n, t = pooled[c]
            mark = "OK " if (n == exp_n and t == exp_t) else "MISMATCH"
            if mark != "OK ":
                ok = False
            print(f"  {c:9} expected {exp_n:>4}/{exp_t:<3}  got {n:>4}/{t:<3}  {mark}")
        m1 = "OK " if n_all == CONTROL["resumes"] else "MISMATCH"
        m2 = "OK " if n_to == CONTROL["timeouts"] else "MISMATCH"
        if m1 != "OK " or m2 != "OK ":
            ok = False
        print(f"  totals    expected {CONTROL['resumes']}/{CONTROL['timeouts']}"
              f"  got {n_all}/{n_to}   {m1} {m2}")
        print(f"\n  CALIBRATION {'REPRODUCES the frozen control' if ok else 'DOES NOT REPRODUCE -- do not score with this'}")
        return

    # ---- verdicts, gated on the pre-registered n ----
    print("\n  == frozen predictions ==")

    n_stale, t_stale = pooled["stale"]
    r_stale = 100.0 * t_stale / n_stale if n_stale else float("nan")
    n_race, t_race = pooled["race"]
    r_race = 100.0 * t_race / n_race if n_race else float("nan")
    r_all = 100.0 * n_to / max(n_all, 1)

    def gate(need, have):
        return "SCORED" if have >= need else f"UNDERPOWERED (n={have} < {need})"

    g1 = gate(N_STALE, n_stale)
    print(f"\n  P-W1  STALE rate {r_stale:.1f} % ({t_stale}/{n_stale})"
          f"  target < {P_W1_MAX} %   [{g1}]")
    if g1 == "SCORED":
        print(f"        VERDICT: {'CONFIRMED' if r_stale < P_W1_MAX else 'FALSIFIED'}"
              f"  (control 40.0 % = 10/25)")

    g2 = gate(N_ALL, n_all)
    print(f"  P-W2  overall rate {r_all:.2f} % ({n_to}/{n_all})"
          f"  target < {P_W2_MAX} %   [{g2}]")
    if g2 == "SCORED":
        print(f"        VERDICT: {'CONFIRMED' if r_all < P_W2_MAX else 'FALSIFIED'}"
              f"  (control 3.3 % = 36/1078)")

    in_band = P_W3_LO <= r_race <= P_W3_HI
    g3 = "SCORED" if n_race >= N_ALL else f"UNDERPOWERED (n={n_race})"
    print(f"  P-W3  race rate {r_race:.1f} % ({t_race}/{n_race})"
          f"  band {P_W3_LO}-{P_W3_HI} %   [{g3}]")
    if g3 == "SCORED":
        print(f"        VERDICT: {'CONFIRMED' if in_band else 'FALSIFIED'}"
              f"  (control 2.3 % = 19/838)")

    print("\n  NOTE: this file scores the pc-ack TIMEOUT only. Patch 825 is not")
    print("        claimed to fix the 903 s fatal; that needs a fatal count.")


if __name__ == "__main__":
    main()

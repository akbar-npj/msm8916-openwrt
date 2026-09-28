#!/usr/bin/env python3
"""
score_fatal2.py -- score an Android modem fatal against Doc 191's pre-registration.

Usage:
    score_fatal2.py <samples.csv> [--dumps <dumps_dir>] [--expect N]

WHY THIS EXISTS
    The A0 window's sampler (watch.sh) writes one CSV row per ~5.24 s and dumps the
    full dmesg verbatim on the fatal edge. This script turns that into the scored
    numbers Doc 191 pre-registered, so the event is scored by a tool that was
    already tested against fatal #1 -- not by a script written after seeing the data.

DESIGN NOTES (each is a trap this script is written around)
  * Columns are read BY NAME from the header, never by position: the schema already
    changed once (v1 -> v2 added modem_up and himi_conn).
  * The fatal's modem uptime is taken from the DMESG timestamps when a dump is
    available, because the CSV's modem_up at the fatal edge is sampled up to one
    interval (~5.24 s) AFTER the fatal and reads high by that amount. Both are
    printed so the discrepancy is visible, never silently picked.
  * `modem_rst` counts "pil-q6v5-mss.*modem: Brought out of reset" -- qualified, so
    it does NOT double-count wcnss (the v1 bug).
  * Coverage is computed and printed BEFORE any statistic: a window with an
    ap_uptime gap cannot be scored (Doc 188 sec.12.3).
  * The beat period used is P = 903.675206 s (Doc 177/179's free-running clock,
    from five consecutive common_timer intervals, spread 7.5 ms). The 902.470 s
    epoch gap is deliberately NOT used as a period -- it is lifetime-to-fatal plus
    the post-fatal tail, and the modem's RTC stops with the modem.
"""
import argparse
import csv
import glob
import os
import re
import sys

P_CLOCK = 903.675206          # s -- Doc 177/179 free-running always-on clock
P_TAXONOMY = 902.353          # s -- Doc 162 taxonomy mean for common_timer (secondary)
SAMPLE_NOMINAL = 5.24         # s -- effective sampler interval (sleep 5 + dmesg cost)
STALE_GAP = 12.0              # s -- an ap_uptime gap above this is lost coverage


def load(path):
    with open(path, newline="") as fh:
        rows = list(csv.DictReader(fh))
    if not rows:
        sys.exit(f"FATAL: {path} has no data rows")
    need = {"wall", "ap_uptime", "fatal", "modem_rst", "modem_up", "c2"}
    missing = need - set(rows[0].keys())
    if missing:
        sys.exit(f"FATAL: {path} is missing columns {sorted(missing)} -- wrong schema?")
    return rows


def fnum(v, default=None):
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def coverage(rows):
    ups = [fnum(r["ap_uptime"]) for r in rows]
    ups = [u for u in ups if u is not None]
    span = ups[-1] - ups[0]
    gaps = []
    for a, b in zip(ups, ups[1:]):
        if b - a > STALE_GAP:
            gaps.append((a, b, b - a))
    return {
        "n": len(ups), "first": ups[0], "last": ups[-1], "span": span,
        "expected": span / SAMPLE_NOMINAL + 1,
        "gaps": gaps, "lost": sum(g[2] for g in gaps),
    }


def fatal_edges(rows):
    """Return indices where the `fatal` count increments (one entry per fatal)."""
    edges, prev = [], None
    for i, r in enumerate(rows):
        f = fnum(r["fatal"], 0)
        if prev is not None and f > prev:
            edges.append(i)
        prev = f
    return edges


def rst_edges(rows):
    edges, prev = [], None
    for i, r in enumerate(rows):
        v = fnum(r["modem_rst"], 0)
        if prev is not None and v > prev:
            edges.append(i)
        prev = v
    return edges


def parse_dmesg(path):
    """Extract (fatal_ts, last_bringup_before, reason) from a dumped dmesg."""
    try:
        txt = open(path, errors="replace").read()
    except OSError:
        return None
    fatal_ts = [float(m) for m in re.findall(r"^\[\s*([0-9]+\.[0-9]+)\]\s*SMSM: Modem SMSM state changed to SMSM_RESET", txt, re.M)]
    if not fatal_ts:
        fatal_ts = [float(m) for m in re.findall(r"^\[\s*([0-9]+\.[0-9]+)\]\s*Fatal error on the modem", txt, re.M)]
    bring = [float(m) for m in re.findall(r"^\[\s*([0-9]+\.[0-9]+)\]\s*pil-q6v5-mss.*modem: Brought out of reset", txt, re.M)]
    reasons = re.findall(r"modem subsystem failure reason: (.+?)\.\s*$", txt, re.M)
    return {"fatal_ts": fatal_ts, "bringups": bring, "reasons": reasons, "path": path}


def nearest_beat(t, period):
    """Nearest integer beat and the signed residual (t - k*period)."""
    k = round(t / period)
    return k, t - k * period


def window_rate(rows, key, a, b):
    """Entry rate (per second) of a monotonic counter over the ap_uptime window [a,b]."""
    lo = hi = None
    for r in rows:
        u = fnum(r["ap_uptime"])
        if u is None:
            continue
        if u >= a and lo is None:
            lo = (u, fnum(r[key]))
        if u <= b:
            hi = (u, fnum(r[key]))
    if lo is None or hi is None or hi[0] <= lo[0]:
        return None
    if lo[1] is None or hi[1] is None:
        return None
    return (hi[1] - lo[1]) / (hi[0] - lo[0])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("samples")
    ap.add_argument("--dumps", default=None, help="dir holding dmesg_fatal_*.txt")
    ap.add_argument("--expect", type=int, default=2, help="number of fatals to score")
    a = ap.parse_args()

    rows = load(a.samples)
    cov = coverage(rows)

    print("=" * 78)
    print("Doc 191 -- ANDROID MODEM FATAL SCORER")
    print("=" * 78)
    print(f"samples : {a.samples}")
    print(f"rows    : {cov['n']}   coverage: up {cov['first']:.1f} .. {cov['last']:.1f} "
          f"({cov['span']/3600:.2f} h)")
    print(f"expected rows at {SAMPLE_NOMINAL} s : {cov['expected']:.0f}  "
          f"=> actual/expected {cov['n']/cov['expected']:.4f}")
    if cov["n"] > 1:
        print(f"observed mean interval            : {cov['span']/(cov['n']-1):.3f} s "
              f"(the sampler's effective cadence)")
    if cov["gaps"]:
        print(f"  !! {len(cov['gaps'])} COVERAGE GAPS > {STALE_GAP} s, total {cov['lost']:.1f} s")
        for g in cov["gaps"][:20]:
            print(f"     gap: up {g[0]:.1f} -> {g[1]:.1f}  = {g[2]:.1f} s")
        print("  !! a window with a gap cannot be scored for a rate -- report the gap")
    else:
        print("  coverage: CONTINUOUS (no gaps) -- scorable")

    dumps = []
    if a.dumps:
        dumps = sorted(glob.glob(os.path.join(a.dumps, "dmesg_fatal_*.txt")))
        print(f"dumps   : {len(dumps)} in {a.dumps}")

    fedges = fatal_edges(rows)
    redges = rst_edges(rows)
    print(f"\nfatal increments: {len(fedges)}   modem bring-up increments: {len(redges)}")

    # ---- per-fatal block -------------------------------------------------
    mu_best_list = []      # best available modem uptime per fatal (dmesg > CSV)
    sig_list = []
    for n, idx in enumerate(fedges, start=1):
        r = rows[idx]
        ap_up = fnum(r["ap_uptime"])
        mu_csv = fnum(r["modem_up"])
        print("\n" + "-" * 78)
        print(f"FATAL #{n}   (CSV row {idx}, ap_uptime {ap_up:.2f}, wall {r['wall']})")
        print("-" * 78)
        print(f"  modem_up at the fatal edge (CSV) : {mu_csv:.2f} s  "
              f"[sampled up to ~{SAMPLE_NOMINAL} s late => reads high]")

        # dmesg-derived value, if we have the matching dump
        dm = None
        mu_best = mu_csv          # fall back to the CSV value
        if dumps and n - 1 < len(dumps):
            dm = parse_dmesg(dumps[n - 1])
        if dm and dm["fatal_ts"]:
            fts = dm["fatal_ts"][-1]
            prior = [b for b in dm["bringups"] if b <= fts]
            if prior:
                mu_dm = fts - prior[-1]
                mu_best = mu_dm       # the dmesg stamps are ms-precise; prefer them
                print(f"  modem_up from dmesg timestamps   : {mu_dm:.3f} s  "
                      f"[fatal {fts:.6f} - bringup {prior[-1]:.6f}]  <-- USE THIS")
                print(f"  CSV minus dmesg                  : {mu_csv - mu_dm:+.2f} s")
            if dm["reasons"]:
                print(f"  SIGNATURE (reason string)        : {dm['reasons'][-1]}")
            else:
                print("  SIGNATURE (reason string)        : (none found in the dump)")
        elif dm:
            print("  dmesg dump present but no fatal timestamp parsed")

        mu_best_list.append(mu_best)
        sig_list.append(dm["reasons"][-1] if (dm and dm["reasons"]) else None)

        for label, period in (("clock P=903.675206", P_CLOCK),
                              ("taxonomy P=902.353", P_TAXONOMY)):
            k, res = nearest_beat(mu_best, period)
            src = "dmesg" if mu_best != mu_csv else "CSV"
            print(f"  beat vs {label:22s}: beat {k:3d}  residual {res:+8.1f} s  ({src})")

        # pre-fatal window statistics
        f_ap = ap_up
        r900 = window_rate(rows, "c2", f_ap - 900, f_ap)
        r1800 = window_rate(rows, "c2", f_ap - 1800, f_ap - 900)
        print(f"  state2 (pc) entry rate:")
        print(f"     pre-fatal 900 s      : {r900:.2f} /s" if r900 is not None else "     pre-fatal 900 s      : n/a")
        print(f"     preceding 900 s      : {r1800:.2f} /s" if r1800 is not None else "     preceding 900 s      : n/a")
        if r900 is not None and r1800:
            print(f"     ratio (last/preceding): {r900/r1800:.2f}x")

        # Daemon liveness over the fatal's own window. These are process COUNTS, so
        # the healthy value is >= 1 and a drop to 0 is the finding.
        lo = max(0, idx - int(1800 / SAMPLE_NOMINAL))
        win = rows[lo:idx + 1]
        for col in ("rild", "qmuxd", "netmgrd", "rmt_storage", "time_daemon"):
            if col in rows[0]:
                vals = [fnum(x[col]) for x in win if fnum(x[col]) is not None]
                if vals:
                    print(f"  {col:12s}: min={min(vals):.0f} max={max(vals):.0f} "
                          f"({'OK' if min(vals) >= 1 else '!! DROPPED'})")
        # himi_conn is NOT a liveness flag -- it is a connection COUNT, and 0 is the
        # healthy value (nothing polling the vendor admin UI, Doc 188 sec.13.3).
        # Do NOT score it with the >=1 rule; a non-zero value is the CONFOUNDER.
        if "himi_conn" in rows[0]:
            vals = [fnum(x["himi_conn"]) for x in win if fnum(x["himi_conn"]) is not None]
            if vals:
                nz = sum(1 for v in vals if v > 0)
                verdict = ("OK (no admin-UI poller)" if max(vals) == 0
                           else f"!! CONFOUNDER PRESENT in {nz}/{len(vals)} samples")
                print(f"  {'himi_conn':12s}: min={min(vals):.0f} max={max(vals):.0f}  {verdict}")

    # ---- P-A7b: residual repetition across fatals ------------------------
    if len(mu_best_list) >= 2:
        res = []
        for mu in mu_best_list:
            _, r = nearest_beat(mu, P_CLOCK)
            res.append(r)
        print("\n" + "=" * 78)
        print("P-A7b (latched offset): residuals against P=903.675206")
        for i, r in enumerate(res, 1):
            sig = sig_list[i - 1] if i - 1 < len(sig_list) else None
            print(f"   fatal #{i}: {r:+8.1f} s" + (f"   [{sig}]" if sig else ""))
        spread = max(res) - min(res)
        print(f"   spread = {spread:.1f} s  ->  P-A7b (tolerance 60 s): "
              f"{'CONFIRMED' if spread <= 60 else 'FALSIFIED'}")
    else:
        print("\n(only one fatal so far -- P-A7b needs n>=2)")

    print("\nNOTE: P-A7b is falsifiable by a legitimate a2_power offset jump "
          "(Doc 179), so a FALSIFIED verdict must be read against the signature.")


if __name__ == "__main__":
    main()

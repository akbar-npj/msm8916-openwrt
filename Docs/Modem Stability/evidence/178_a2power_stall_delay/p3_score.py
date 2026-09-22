#!/usr/bin/env python3
"""
178_a2power_stall_delay/p3_score.py

Scores the pre-registered predictions in P3_PREREG.md against the live
`rpmring -t -n 17000 -i 50` capture taken on boot eafa19f6 from t=8950.8 s.

Inputs:
  argv[1]  capture file (plain or .gz).  Default /tmp/rpm_track_p3.txt
  FATALS   the fatal list for this boot, below (dmesg times, seconds)

Prints:
  * the RPM-log rate profile and the >2 s gap profile  (P3')
  * the stall bracketing any fatal in the capture      (P-STALL)
  * the residual of any fatal against the Doc 177 clock (P-NEXT)

Run:  python3 p3_score.py [capture]
"""
import gzip, re, os, sys, statistics as st

NEW = re.compile(r"t=(\d+).*?counter=(0x[0-9a-f]+).*?start=(\d+) nrec=(\d+)")
OV = re.compile(r"t=(\d+) counter=(0x[0-9a-f]+) delta=(\d+)")

# fatal list for boot eafa19f6 (dmesg, seconds) -- from dmesg | grep "fatal error received"
FATALS = [
    (1,  524.211653, "a2_power.c:1189"),
    (2, 1427.099153, "lte_ml1_common_timer.c:390"),
    (3, 2330.773740, "lte_ml1_common_timer.c:390"),
    (4, 3234.448768, "lte_ml1_common_timer.c:390"),
    (5, 4138.119715, "lte_ml1_common_timer.c:390"),
    (6, 5041.798167, "lte_ml1_common_timer.c:390"),
    (7, 5949.210659, "a2_power.c:1189"),
    (8, 6852.985676, "lte_ml1_common_timer.c:390"),
    (9, 7756.662692, "lte_ml1_common_timer.c:390"),
    (10, 8719.020382, "a2_power.c:1189"),
    (11, 9622.891300, "lte_ml1_common_timer.c:390"),   # observed during the P3' capture
]
P = 903.675206
ANCHOR = 1427.099153  # fatal #2, the phase reference of the free-running clock


def load(path):
    op = gzip.open if path.endswith(".gz") else open
    new, ov = [], []
    with op(path, "rt", errors="replace") as f:
        for line in f:
            if line.startswith("# NEW"):
                m = NEW.search(line)
                if m:
                    new.append((int(m.group(1)) / 1e9, int(m.group(2), 16), int(m.group(4))))
            elif "OVERRUN" in line:
                m = OV.search(line)
                if m:
                    ov.append((int(m.group(1)) / 1e9, int(m.group(3))))
    return new, ov


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/rpm_track_p3.txt"
    new, ov = load(path)
    if not new:
        print("no NEW lines"); return
    t0, t1 = new[0][0], new[-1][0]
    print(f"capture {os.path.basename(path)}: {len(new)} NEW  {t0:.3f} -> {t1:.3f} s "
          f"({t1-t0:.1f} s)   overruns={len(ov)}")

    # --- rate profile (counter is authoritative) ---
    span = new[-1][1] - new[0][1]
    print(f"  RPM log rate = {span/32/(t1-t0):.0f} records/s   "
          f"({span/32:.0f} records over {t1-t0:.1f} s)")

    # --- gap profile ---
    gaps = [(new[i][0] - new[i-1][0], new[i-1][0], new[i][0]) for i in range(1, len(new))]
    big = sorted([g for g in gaps if g[0] > 2.0], reverse=True)
    print(f"  NEW-gap: median={st.median([g[0] for g in gaps])*1000:.0f} ms  "
          f"max={big[0][0]:.3f} s" if big else "  NEW-gap: max < 2 s")
    print(f"  gaps >2 s: {len(big)}  total={sum(g[0] for g in big):.1f} s "
          f"({100*sum(g[0] for g in big)/(t1-t0):.1f} % of span)")
    for d, a, b in big[:6]:
        print(f"     {d:8.3f} s   {a:.3f} -> {b:.3f}")

    # --- 30 s rate bins ---
    bins = {}
    for i in range(1, len(new)):
        dt = new[i][0] - new[i-1][0]
        dc = new[i][1] - new[i-1][1]
        b = int((new[i][0] - t0) // 30)
        a, w = bins.get(b, [0, 0.0])
        bins[b] = [a + dc, w + dt]
    rates = sorted((a / 32 / w, t0 + b * 30) for b, (a, w) in bins.items() if w > 15)
    print(f"  30 s bins: n={len(rates)}  median={st.median([r[0] for r in rates]):.0f}  "
          f"min={rates[0][0]:.0f} rec/s  bins<20: {sum(1 for r in rates if r[0] < 20)}")
    for r, t in rates[:3]:
        print(f"     lowest: {r:7.0f} rec/s  bin t={t:.1f}")

    # --- P3' verdict ---
    print()
    print("P3' (instrument control): a 50 ms-polled idle window with NO fatal must not")
    print("  reproduce the fatal-#10 capture's 60.3 s / 26.0 s-gap behaviour.")
    fatal_in = [f for f in FATALS if t0 <= f[1] <= t1]
    print(f"  fatals inside the capture: {[(f[0], f[2]) for f in fatal_in] or 'NONE'}")

    # --- P-NEXT / P-STALL ---
    print()
    for i, t, sig in fatal_in:
        k = round((t - ANCHOR) / P)
        beat = ANCHOR + k * P
        print(f"  fatal #{i} {sig} @ {t:.6f}: beat k={k} = {beat:.6f}  residual {t-beat:+.6f} s")
        run = [g for g in big if beat - 5 <= g[1] and g[2] <= t + 5]
        if run:
            print(f"     RPM stall bracketing: [{min(g[1] for g in run):.3f}, "
                  f"{max(g[2] for g in run):.3f}] = "
                  f"{max(g[2] for g in run)-min(g[1] for g in run):.3f} s"
                  f"   (residual {t-beat:+.3f} s)")
        else:
            print("     NO >2 s RPM stall bracketing this fatal")


main()

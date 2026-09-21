#!/usr/bin/env python3
"""Enrichment test: do AP PC-handshake events cluster inside long RPM gaps?

For each threshold T, compare
    time_frac  = fraction of the capture spent inside an RPM gap >= T
    event_frac = fraction of in-capture AP events falling inside such a gap
    enrichment = event_frac / time_frac

enrichment ~ 1  -> events are spread at random w.r.t. RPM gaps
enrichment >> 1 -> events preferentially occur while the RPM is quiet

A raw "ratio to the local median" is NOT usable here: most consecutive
`# NEW` blocks are only ~4 ms apart, so a median over all gaps is ~0.03 s and
every >=500 ms gap scores a meaningless 30-400x.
"""
import re
import sys


def parse_blocks(path):
    blocks = []
    for line in open(path, errors="replace"):
        if line.startswith("# NEW"):
            m = re.search(r"t=(\d+) counter=(0x[0-9a-fA-F]+) start=\d+ nrec=(\d+)", line)
            if m:
                blocks.append((int(m.group(1)) / 1e9, int(m.group(2), 16),
                               int(m.group(3))))
    return blocks


def load_events(path):
    ev = []
    for line in open(path):
        line = line.strip()
        if not line:
            continue
        p = line.split(None, 1)
        if len(p) == 2:
            try:
                ev.append((float(p[0]), p[1].strip()))
            except ValueError:
                pass
    return ev


def main():
    blocks = parse_blocks(sys.argv[1])
    events = load_events(sys.argv[2])
    ts = [b[0] for b in blocks]
    gaps = [(ts[i], ts[i + 1], ts[i + 1] - ts[i], blocks[i + 1][2])
            for i in range(len(ts) - 1)]
    T = ts[-1] - ts[0]
    inc = [(t, k) for t, k in events if ts[0] <= t <= ts[-1]]

    print(f"capture {ts[0]:.2f}..{ts[-1]:.2f} = {T:.1f} s; "
          f"{len(inc)} of {len(events)} events in capture")

    print(f"\n{'T(s)':>6} {'time_frac':>10} {'event_frac':>11} {'enrich':>8} "
          f"{'#events':>8} {'#gaps':>6}")
    for thr in (0.5, 1, 2, 3, 5, 8):
        big = [g for g in gaps if g[2] >= thr]
        tfrac = sum(g[2] for g in big) / T
        # events inside a >= thr gap
        n = 0
        for t, k in inc:
            for a, b, d, _ in big:
                if a <= t <= b:
                    n += 1
                    break
        efrac = n / len(inc) if inc else 0
        enr = efrac / tfrac if tfrac else float("nan")
        print(f"{thr:6.1f} {tfrac:10.4f} {efrac:11.4f} {enr:8.2f} {n:8d} {len(big):6d}")

    # ---- the three stalls, explicitly ------------------------------------
    print("\nthe three gaps >= 8 s:")
    for a, b, d, nrec in sorted([g for g in gaps if g[2] >= 8]):
        inside = [f"{t:.3f} {k}" for t, k in inc if a <= t <= b]
        print(f"  {a:.3f} -> {b:.3f}  {d:.3f} s  ({nrec} recs)  "
              f"AP events inside: {inside if inside else 'NONE'}")

    # ---- per-kind, which events fall in gaps >= 2 s ----------------------
    print("\nper-kind placement in gaps >= 2 s:")
    big2 = [g for g in gaps if g[2] >= 2]
    for kind in sorted(set(k for _, k in inc)):
        tot = [t for t, k in inc if k == kind]
        n = sum(1 for t in tot if any(a <= t <= b for a, b, _, _ in big2))
        print(f"  {kind:<22} {n:3d}/{len(tot):<3d} = {100*n/len(tot):5.1f}%")


if __name__ == "__main__":
    main()

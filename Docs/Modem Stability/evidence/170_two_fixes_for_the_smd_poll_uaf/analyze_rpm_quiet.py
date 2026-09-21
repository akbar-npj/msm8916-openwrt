#!/usr/bin/env python3
"""Place RPM quiet periods on the absolute AP timeline and overlay the fatals.

Answers the round's discriminating question (Doc 170 section 8.19):
    does a ~13 s RPM silence PRECEDE a fatal, or is it an independent stall?

The capture (scratch/rpm11/rpm11.txt) is `rpmring -t -n 500000 -i 4` output:
    # TRACK start t=<monotonic ns> counter=<hdr+0x38 byte counter>
    # NEW t=<monotonic ns> counter=... start=.. nrec=.. copy_ns=..
    R <8 words>
    # TRACK OVERRUN t=.. counter=.. delta=<bytes> (<n> records > ring)

A quiet period is the wall gap between consecutive # NEW blocks.  Because the
RPM only emits a # NEW block when its byte counter moves, a long gap means the
RPM wrote nothing for that long.  The counter delta printed alongside lets us
separate a GENUINE silence (small delta) from a ring turnover (delta ~= ring
size, flagged by a TRACK OVERRUN line).
"""
import re
import sys

RING_BYTES = 2048 * 4          # words=2048, 4 bytes/word
FATALS = [
    (6397.771058, "#9  a2_power.c:1189"),
    (6580.507441, "#10 a2_task.c:3179"),
    (7483.839750, "#11 lte_ml1_sleepmgr_stm.c:4054"),
]


def parse(path):
    blocks = []          # (t_ns, counter, nrec)
    overruns = []        # (t_ns, counter, delta_bytes, nrec)
    for line in open(path, errors="replace"):
        if line.startswith("# NEW"):
            m = re.search(r"t=(\d+) counter=(0x[0-9a-fA-F]+) start=\d+ nrec=(\d+)", line)
            if m:
                blocks.append((int(m.group(1)), int(m.group(2), 16), int(m.group(3))))
        elif line.startswith("# TRACK OVERRUN"):
            m = re.search(r"t=(\d+) counter=(0x[0-9a-fA-F]+) delta=(\d+) bytes \((\d+) records", line)
            if m:
                overruns.append((int(m.group(1)), int(m.group(2), 16),
                                 int(m.group(3)), int(m.group(4))))
    return blocks, overruns


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "scratch/rpm11/rpm11.txt"
    blocks, overruns = parse(path)
    if not blocks:
        sys.exit("no blocks")

    t0, t1 = blocks[0][0], blocks[-1][0]
    print(f"blocks={len(blocks)}  overruns={len(overruns)}  "
          f"AP span {t0/1e9:.2f} -> {t1/1e9:.2f} s")

    # index overruns by the block they precede (same t as a # NEW)
    ov_by_t = {o[0]: o for o in overruns}

    # ---- all quiet periods >= 500 ms -------------------------------------
    print("\n=== quiet periods >= 500 ms (RPM wrote nothing) ===")
    print(f"{'start(s)':>10} {'end(s)':>10} {'gap(s)':>8} {'ctr delta':>10} "
          f"{'recs':>6}  note")
    quiet = []
    for i in range(1, len(blocks)):
        gap_ns = blocks[i][0] - blocks[i - 1][0]
        if gap_ns < 500_000_000:
            continue
        delta = (blocks[i][1] - blocks[i - 1][1]) & 0xFFFFFFFF
        note = ""
        ov = ov_by_t.get(blocks[i][0])
        if ov:
            note = f"OVERRUN {ov[2]}B ({ov[3]} recs > ring)"
        elif delta >= RING_BYTES:
            note = "delta>=ring"
        quiet.append((blocks[i - 1][0] / 1e9, blocks[i][0] / 1e9,
                      gap_ns / 1e9, delta, blocks[i][2], note))
        print(f"{blocks[i-1][0]/1e9:10.3f} {blocks[i][0]/1e9:10.3f} "
              f"{gap_ns/1e9:8.3f} {delta:10d} {blocks[i][2]:6d}  {note}")

    # ---- fatals vs the nearest preceding quiet period ---------------------
    print("\n=== fatals vs nearest preceding quiet period >= 1 s ===")
    big = [q for q in quiet if q[2] >= 1.0]
    for ft, name in FATALS:
        if not (t0 / 1e9 <= ft <= t1 / 1e9):
            print(f"  AP {ft:10.3f}  {name:34s}  (outside capture)")
            continue
        prev = [q for q in big if q[1] <= ft]
        if not prev:
            print(f"  AP {ft:10.3f}  {name:34s}  no quiet >=1 s before it")
            continue
        q = prev[-1]
        print(f"  AP {ft:10.3f}  {name:34s}  "
              f"nearest quiet {q[2]:7.3f} s at AP {q[1]:.3f} "
              f"({ft - q[1]:+7.3f} s before fatal)"
              + (f"  [{q[5]}]" if q[5] else ""))

    # ---- the counter sanity check: total records vs wall ------------------
    total = sum(b[2] for b in blocks)
    print(f"\ntotal records={total}  wall={(t1-t0)/1e9:.1f} s  "
          f"rate={total/((t1-t0)/1e9):.1f} rec/s")
    print(f"overrun record loss = {sum(o[3] for o in overruns)} records "
          f"in {len(overruns)} events")


if __name__ == "__main__":
    main()

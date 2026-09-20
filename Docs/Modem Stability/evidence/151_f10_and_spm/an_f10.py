#!/usr/bin/env python3
"""Fatal #10 analysis of an `rpmring -t` capture.

Answers Doc 150 section 12 item 1: at fatal #10, does client 0 reappear only in
a sub-second window (falsifier) or is it a normal long-lived client?
"""
import re
import struct
import sys

NEW_RE = re.compile(r"^# NEW t=(\d+) counter=0x([0-9a-f]+) start=(\d+) nrec=(\d+)")
CLIENT_IDS = (0x00D0, 0x00D1, 0x00D2)


def a4(w):
    return "".join(chr(c) if 32 <= c < 127 else "." for c in struct.pack("<I", w))


def parse(path):
    """Yield (t_ns, counter, [8-word records])."""
    t = ctr = None
    recs = []
    with open(path) as f:
        for line in f:
            if line.startswith("# NEW"):
                if t is not None:
                    yield t, ctr, recs
                m = NEW_RE.match(line)
                t, ctr, recs = int(m.group(1)), int(m.group(2), 16), []
            elif line.startswith("R "):
                p = line.split()
                if len(p) == 9:
                    try:
                        recs.append([int(x, 16) for x in p[1:]])
                    except ValueError:
                        pass
        if t is not None:
            yield t, ctr, recs


def main(path, fatal_ns=None):
    bursts = list(parse(path))
    allrec = [(t, r) for t, _, rs in bursts for r in rs]
    print(f"bursts={len(bursts)} records={len(allrec)}")
    if not bursts:
        return
    t0, t1 = bursts[0][0], bursts[-1][0]
    print(f"span {t0/1e9:.3f} .. {t1/1e9:.3f} s  ({'%.1f' % ((t1-t0)/1e9)} s)")

    # ---- client census -------------------------------------------------
    print("\n=== client census (word[3] in 0xd0/0xd1/0xd2 -> word[4] = client) ===")
    per = {}
    for t, r in allrec:
        if r[3] in CLIENT_IDS:
            c = r[4]
            d = per.setdefault(c, {"n": 0, "t0": t, "t1": t, "ids": {}})
            d["n"] += 1
            d["t1"] = t
            d["ids"][r[3]] = d["ids"].get(r[3], 0) + 1
    for c in sorted(per):
        d = per[c]
        print(f"  client {c:#x} ({c:>3}): n={d['n']:>6}  first={d['t0']/1e9:.3f} "
              f"last={d['t1']/1e9:.3f}  span={(d['t1']-d['t0'])/1e9:.3f}s  "
              f"ids={{{', '.join('%#x:%d' % kv for kv in sorted(d['ids'].items()))}}}")

    # ---- per-client timeline of 0xd0 (client request) -------------------
    print("\n=== 0xd0 request timeline (client, seq) ===")
    for t, r in allrec:
        if r[3] == 0x00D0:
            print(f"  {t/1e9:12.3f}  client={r[4]:#x} seq={r[5]:#x}")

    # ---- gaps ----------------------------------------------------------
    print("\n=== burst gaps > 0.5 s ===")
    prev = None
    for t, _, rs in bursts:
        if prev is not None and t - prev > 500_000_000:
            print(f"  gap {(t-prev)/1e9:8.3f} s  {prev/1e9:.3f} -> {t/1e9:.3f}")
        prev = t

    # ---- phase rates around the fatal ----------------------------------
    if fatal_ns:
        print(f"\n=== phases around fatal {fatal_ns/1e9:.6f} ===")
        marks = [(-10, "A pre"), (-1.5, "B pre-edge"), (0, "FATAL"),
                 (0.7, "C SSR"), (1.4, "D post-up"), (3, "E settle"),
                 (8, "F storm"), (15, "G late"), (30, "H tail")]
        for i in range(len(marks) - 1):
            lo, name = marks[i]
            hi = marks[i + 1][0]
            a, b = fatal_ns + lo * 10**9, fatal_ns + hi * 10**9
            n = sum(1 for t, _ in allrec if a <= t < b)
            span = (b - a) / 1e9
            print(f"  {name:10s} {lo:6.1f}..{hi:5.1f}  n={n:>6}  {n/span:8.1f}/s")


if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else None)

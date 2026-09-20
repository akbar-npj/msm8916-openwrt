#!/usr/bin/env python3
"""SUPERSEDED 2026-09-21 by evidence/150_rpm_track/rpm_track.py -- see Doc 150 §3.

This tool was written against `devmem` ring dumps, which are not snapshots: a
2048-word devmem dump takes 5.94 s and the RPM turns the whole ring over in
~0.5-3 s, so the "records" it analyses are a mixture of many different ring
generations.  Its "span" and "largest gap" outputs are therefore meaningless
(e.g. the reported `span=453.865 s` for a ~6 s ring, and the two bogus 223.7 s
"gaps" that were just the 19.2 MHz counter's wrap period).  Retained only for
audit; do not use.

--- original docstring ---
Build a wall-clock timeline from an RPM log-ring sample (Doc 149 §5.2 / §7 exp. 1).

Findings this encodes:
  * records are fixed 8-word (32-byte) entries; word[0] is the constant 0x00200000
  * word[1] is a **19.2 MHz** counter (measured: 2.460e9 ticks over ~126 s), which
    therefore wraps every 2**32/19.2e6 = 223.7 s
  * the ring is 256 records and covers only ~6.5 s, i.e. ~39 records/s

So a sample is a ~6.5 s window, and the ring turns over completely in well under a
minute. To watch a collapse, sample continuously and concatenate.

Usage:
  python3 rpm_timeline.py <sample.txt> [<sample2.txt> ...]
"""
import re
import struct
import sys

TICK_HZ = 19_200_000
RING_WORDS = 2048
REC_WORDS = 8


def ascii4(w):
    return "".join(chr(c) if 32 <= c < 127 else "." for c in struct.pack("<I", w))


def is_name(w):
    s = ascii4(w)
    return s.replace(".", "").isalnum() and any(c.isalpha() for c in s)


def load(path):
    words = []
    for line in open(path):
        words.extend(int(x, 16) for x in re.findall(r"0x[0-9A-Fa-f]{8}", line))
    return words[:RING_WORDS]


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)

    for path in sys.argv[1:]:
        hdr = [l.strip() for l in open(path) if l.startswith("###")]
        words = load(path)
        recs = [words[i * REC_WORDS:(i + 1) * REC_WORDS] for i in range(RING_WORDS // REC_WORDS)]
        recs = [r for r in recs if len(r) == REC_WORDS]

        print(f"\n=== {path} ===")
        if hdr:
            print(hdr[0])

        # The ring is in write order; the newest record is the last one written.
        # Unwrap the 19.2 MHz counter across the sequence so gaps are visible.
        ts = [r[1] for r in recs]
        base = ts[0]
        unwrapped = []
        prev = base
        wraps = 0
        for t in ts:
            if t < prev:
                wraps += 1
            unwrapped.append(t + wraps * (1 << 32))
            prev = t

        span = (unwrapped[-1] - unwrapped[0]) / TICK_HZ
        print(f"records={len(recs)}  wraps_in_ring={wraps}  span={span:.3f} s "
              f"({len(recs)/span:.1f} rec/s)" if span > 0 else "")

        # Report the largest inter-record gaps -- a stall shows up as a hole.
        gaps = []
        for i in range(1, len(recs)):
            d = unwrapped[i] - unwrapped[i - 1]
            if d > 0:
                gaps.append((d, i))
        gaps.sort(reverse=True)
        print("largest inter-record gaps:")
        for d, i in gaps[:6]:
            r = recs[i]
            print(f"   {d/TICK_HZ*1e3:8.3f} ms before idx={i:3d} "
                  f"id={r[3]:#06x} args={[ascii4(x) if is_name(x) else hex(x) for x in r[4:]]}")

        # Transactions: 0x00d2 ("req.") opens, 0x00d1 carries the resource+value.
        reqs = [(i, recs[i]) for i in range(len(recs)) if recs[i][3] == 0x00D2]
        print(f"transactions (id 0x00d2): {len(reqs)}")
        res = {}
        for i, r in enumerate(recs):
            if r[3] == 0x00D1 and is_name(r[5]):
                res[ascii4(r[5])] = res.get(ascii4(r[5]), 0) + 1
        print("resources requested (id 0x00d1):", sorted(res.items(), key=lambda kv: -kv[1]))

        deep = [k for k in res if any(t in k for t in ("vmin", "xosd", "cxo", "vlow"))]
        print("deep-sleep resources:", deep if deep else "NONE")


if __name__ == "__main__":
    main()

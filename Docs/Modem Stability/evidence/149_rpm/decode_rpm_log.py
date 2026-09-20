#!/usr/bin/env python3
"""SUPERSEDED 2026-09-21 by evidence/150_rpm_track/rpm_track.py -- see Doc 150 §3.

A full `devmem` ring dump takes **5.94 s** (measured), while the RPM overwrites all
256 ring records in ~0.5-3 s.  So a devmem dump is not a snapshot: slots are
overwritten while you are still walking them, the record sequence aliases, and the
resulting histogram/ordering is an artefact.  Everything derived from devmem dumps
(Doc 149 §5.3's histogram, evidence/149_rpm/rpm_timeline.py's "span") is retracted.
Kept only so the retraction is auditable.  Use `rpmring` + `rpm_track.py` instead.

--- original docstring ---
Decode the HMU05 RPM's log ring (Doc 149 §5.2).

The RPM's log structure lives at AP physical 0x29dc00 and its 8 KB ring at 0x29dc58.
`/dev/mem` read() is blocked for that range, but `devmem`'s mmap path works, so the
dump must be taken one word at a time on the device:

    # header (24 words)
    for i in $(seq 0 23); do devmem $((0x29dc00 + i*4)); done

    # ring (2048 words) -- ~1-2 min over ssh
    i=0; while [ $i -lt 2048 ]; do
        printf '%04x ' $i; devmem $((0x29dc58 + i*4)); i=$((i+1));
    done

Feed that output to this script (it scrapes every 0x[0-9A-Fa-f]{8} token, so the
per-line index prefixes and devmem's own newlines do not matter).

Usage:  python3 decode_rpm_log.py <dump.txt> [--all]
"""
import re
import struct
import sys
from collections import Counter

REC_WORDS = 8
RING_WORDS = 2048


def ascii4(w):
    """Render a u32 as its 4 little-endian ASCII chars ('.' for non-printable)."""
    return "".join(chr(c) if 32 <= c < 127 else "." for c in struct.pack("<I", w))


def is_name(w):
    s = ascii4(w)
    return s.replace(".", "").isalnum() and any(c.isalpha() for c in s)


def load(path):
    words = []
    for line in open(path):
        words.extend(int(x, 16) for x in re.findall(r"0x[0-9A-Fa-f]{8}", line))
    return words


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    show_all = "--all" in sys.argv
    words = load(sys.argv[1])

    print(f"words scraped: {len(words)}")
    markers = [i for i, w in enumerate(words) if w == 0x00200000]
    if len(markers) >= 2:
        strides = Counter(markers[i + 1] - markers[i] for i in range(len(markers) - 1))
        print(f"record markers: {len(markers)}  strides: {strides.most_common(4)}")
    else:
        print("WARNING: no 0x00200000 record markers -- wrong region or a partial dump?")

    recs = [words[i * REC_WORDS:(i + 1) * REC_WORDS] for i in range(RING_WORDS)]
    recs = [r for r in recs if len(r) == REC_WORDS]

    ids = Counter(r[3] for r in recs)
    print("\nevent-id histogram:")
    for k, v in sorted(ids.items()):
        print(f"  {k:#06x} x{v}")

    names = Counter()
    for r in recs:
        for w in r[4:]:
            if is_name(w):
                names[ascii4(w)] += 1
    print("\nascii arguments:", names.most_common(40))

    print("\nrecords:")
    rng = range(len(recs)) if show_all else range(max(0, len(recs) - 32), len(recs))
    for i in rng:
        r = recs[i]
        args = [ascii4(x) if is_name(x) else hex(x) for x in r[4:]]
        print(f"{i:3d} id={r[3]:#06x} ts={r[1]:#010x} args={args}")

    # Doc 149 §5.3: the modem's deep-sleep resources should show up here if they reach the RPM.
    interesting = ("vmin", "xosd", "cxo", "vlow")
    hits = [n for n in names if any(k in n for k in interesting)]
    print(f"\ndeep-sleep resources present in ring: {hits if hits else 'NONE'}")


if __name__ == "__main__":
    main()

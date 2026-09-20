#!/usr/bin/env python3
"""Analyse `rpmring -t` output: the RPM's own external log, in order, with wall clock.

Background (Doc 149)
--------------------
The RPM (a Cortex-M3 resource-power-manager core) keeps an "RPM External Log"
in its SRAM.  The AP can see it at physical 0x29dc00 (header) / 0x29dc58 (ring).
The header word at +0x38 is a byte counter over the ring that advances in
exact multiples of 32, so

    write_pos = (counter >> 5) & 0xFF      newest_record = (write_pos - 1) & 0xFF

`rpmring -t` polls that counter and, whenever it moves, emits exactly the
records the RPM wrote in between, stamped with CLOCK_MONOTONIC.  The result is
a lossless, ordered event stream -- the first trustworthy view of the RPM.

Record layout (8 words, 32 bytes):
    [0] 0x00200000 constant marker
    [1] RPM timestamp (free-running counter, see TICK_HZ below)
    [2] constant per capture (0x23 observed idle, 0x24 under load) -- not decoded
    [3] event id
    [4..7] arguments; small ints, or 4-char little-endian resource names

Usage:
  python3 rpm_track.py <f9_track.txt> [...]
"""
import re
import struct
import sys

# The RPM timestamp counter.  19.2 MHz is the value the corpus assumed; this
# script *measures* it instead, by regressing record[1] against the wall clock
# of the enclosing "# NEW" block.  Treat the constant below as a fallback only.
TICK_HZ_FALLBACK = 19_200_000

ID_NAMES = {
    0x00CB: "txn-open?",
    0x00CD: "txn-close?",
    0x00D1: "req",
    0x00D2: "txn-begin",
    0x00D4: "echo-req",
    0x00D5: "res-request",
    0x00D6: "smp2p-out",
    0x00D9: "late-req",
    0x00DA: "late-lat",
}


def a4(w):
    return "".join(chr(c) if 32 <= c < 127 else "." for c in struct.pack("<I", w))


def is_name(w):
    """True only for real 4-char resource names.

    A naive "all bytes printable" test produces false positives on small
    integers: 0x00003148 renders as 'H1..', 0x00000072 as 'r...', 0x00000065
    as 'e...'.  Real resource names are like ldoa/bslv/clk0/req. -- at least
    three alphabetic characters.
    """
    s = a4(w)
    if not all(c.isalnum() or c in "_-." for c in s):
        return False
    return sum(c.isalpha() for c in s) >= 3


def fmt_args(r):
    out = []
    for w in r[4:]:
        out.append(a4(w) if is_name(w) else ("0x%x" % w))
    return out


def parse(path):
    """Yield (t_ns, counter, record) once per log record, in write order."""
    t = ctr = None
    for line in open(path, errors="replace"):
        line = line.rstrip("\n")
        if line.startswith("# TRACK start") or line.startswith("# NEW"):
            m = re.search(r"t=(\d+) counter=(0x[0-9a-fA-F]+)", line)
            if m:
                t, ctr = int(m.group(1)), int(m.group(2), 16)
        elif line.startswith("R "):
            try:
                w = [int(x, 16) for x in line.split()[1:]]
            except ValueError:
                continue	# torn line (file read while being written)
            if len(w) == 8:
                yield t, ctr, w


def to_blocks(stream):
    """Group a record stream into (t_ns, counter, [records]) write bursts."""
    blocks = []
    for t, ctr, rec in stream:
        if not blocks or blocks[-1][0] != t:
            blocks.append((t, ctr, []))
        blocks[-1][2].append(rec)
    return blocks


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)

    for path in sys.argv[1:]:
        blocks = to_blocks(parse(path))

        print(f"\n=== {path} ===")
        if not blocks:
            print("no records")
            continue

        n = sum(len(b[2]) for b in blocks)
        t0, t1 = blocks[0][0], blocks[-1][0]
        wall = (t1 - t0) / 1e9
        print(f"blocks={len(blocks)} records={n} wall={wall:.3f} s  ({n/wall:.1f} rec/s)")

        # --- measure the RPM tick rate against the wall clock -----------------
        xs, ys = [], []
        for t, ctr, rs in blocks:
            xs.append(t)
            ys.append(rs[0][1])
        if len(xs) > 2:
            # unwrap record[1] using its own ordering, then least-squares
            un = [ys[0]]
            for i in range(1, len(ys)):
                d = (ys[i] - ys[i - 1]) % (1 << 32)
                un.append(un[-1] + d)
            mx = sum(xs) / len(xs)
            my = sum(un) / len(un)
            num = sum((x - mx) * (y - my) for x, y in zip(xs, un))
            den = sum((x - mx) ** 2 for x in xs)
            hz = num / den * 1e9 if den else 0
            print(f"measured RPM tick rate = {hz/1e6:.4f} MHz "
                  f"(vs {TICK_HZ_FALLBACK/1e6:.3f} MHz assumed; "
                  f"{100*(hz/TICK_HZ_FALLBACK-1):+.2f}%)")
            hz = hz or TICK_HZ_FALLBACK
        else:
            hz = TICK_HZ_FALLBACK

        # --- quiet periods: a stall shows up as a long gap between blocks ----
        gaps = []
        for i in range(1, len(blocks)):
            gaps.append((blocks[i][0] - blocks[i - 1][0], i))
        gaps.sort(reverse=True)
        print("longest quiet periods (no RPM log writes):")
        for d, i in gaps[:8]:
            print(f"   {d/1e6:10.3f} ms   before block {i} "
                  f"(prev ctr {blocks[i-1][1]:#010x} -> {blocks[i][1]:#010x}, "
                  f"{len(blocks[i][2])} recs)")

        # --- per-second record rate ------------------------------------------
        bins = {}
        for t, ctr, rs in blocks:
            k = int((t - t0) / 1e9)
            bins[k] = bins.get(k, 0) + len(rs)
        print("records per second:", " ".join(f"{k}:{bins[k]}" for k in sorted(bins)))

        # --- event census -----------------------------------------------------
        ids = {}
        for t, ctr, rs in blocks:
            for r in rs:
                ids[r[3]] = ids.get(r[3], 0) + 1
        print("event ids:", " ".join(f"{k:#06x}x{v}" for k, v in sorted(ids.items(), key=lambda kv: -kv[1])))

        # --- resources --------------------------------------------------------
        res = {}
        for t, ctr, rs in blocks:
            for r in rs:
                for w in r[4:]:
                    if is_name(w):
                        res[a4(w)] = res.get(a4(w), 0) + 1
        print("resource names seen:", sorted(res.items(), key=lambda kv: -kv[1]))
        deep = [k for k in res if any(s in k for s in ("vmin", "xosd", "cxo", "vlow", "vdd"))]
        print("deep-sleep / rail resources:", deep if deep else "NONE")

        # --- the gap that matters: does the stream stop dead? -----------------
        print("first 6 blocks:")
        for t, ctr, rs in blocks[:6]:
            print(f"   t={t} ctr={ctr:#010x} n={len(rs)} first_id={rs[0][3]:#06x}")
        print("last 6 blocks:")
        for t, ctr, rs in blocks[-6:]:
            print(f"   t={t} ctr={ctr:#010x} n={len(rs)} first_id={rs[0][3]:#06x}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Diff two modem coredumps to isolate the runtime-varying state.

All 4 dumps share the same 21 PT_LOAD segments at the same VAs, so a byte diff
between consecutive fatals shows exactly what evolved over one ~903 s period.
Region VAs are reported as both dump-VA and ELF-VA (elf = dump + 0x39800000).
"""
import struct
import sys

ELF_BIAS = 0x39800000


def load(path):
    d = open(path, "rb").read()
    e_phoff, = struct.unpack_from("<I", d, 0x1C)
    e_phentsize, e_phnum = struct.unpack_from("<HH", d, 0x2A)
    segs = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz = \
            struct.unpack_from("<IIIIII", d, off)
        if p_type == 1:
            segs.append((p_vaddr, p_offset, p_filesz))
    return d, segs


def main(a, b):
    da, segs = load(a)
    db, _ = load(b)
    print("A = %s (%d B)" % (a, len(da)))
    print("B = %s (%d B)" % (b, len(db)))
    total = 0
    for v, o, f in segs:
        sa = da[o:o + f]
        sb = db[o:o + f]
        if len(sa) != len(sb):
            print("  seg 0x%08x: length mismatch" % v)
            continue
        diffs = [i for i in range(f) if sa[i] != sb[i]]
        n = len(diffs)
        total += n
        if n == 0:
            continue
        print("  seg dump-VA 0x%08x elf-VA 0x%08x size 0x%x : %d differing bytes"
              % (v, v + ELF_BIAS, f, n))
        # coalesce into runs, print the first few runs with context
        runs = []
        s = diffs[0]
        p = diffs[0]
        for i in diffs[1:]:
            if i == p + 1:
                p = i
                continue
            runs.append((s, p))
            s = p = i
        runs.append((s, p))
        for (s, e) in runs[:6]:
            va = v + s
            print("      run 0x%08x..0x%08x (len %d)  A=%s B=%s" % (
                va, v + e, e - s + 1,
                sa[s:s + min(e - s + 1, 16)].hex(),
                sb[s:s + min(e - s + 1, 16)].hex()))
        if len(runs) > 6:
            print("      ... (%d more runs)" % (len(runs) - 6))
    print("TOTAL differing bytes: %d (%.4f%% of %d)" % (
        total, 100.0 * total / len(da), len(da)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

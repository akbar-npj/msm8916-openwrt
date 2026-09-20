#!/usr/bin/env python3
"""Print the ERR_FATAL descriptor fields and the rpm-LPR counter for every dump.

ERR_FATAL record (ELF VA 0xC35B1280):
    +0x00 u32 3            +0x04 u32 1         +0x08 ptr 0xC3C0BF84
    +0x0C u32 3            +0x10 u16 line      +0x14 u32 A   +0x18 u32 B
rpm LPR descriptor (ELF VA 0xc1d473f8): +0x18 = 0xc1d47410 = u32 counter

usage: fatal_fields.py <dump.elf> [<dump.elf> ...]
"""
import glob
import os
import re
import struct
import sys

ELF_BIAS = 0x39800000
ERRFATAL = 0xC35B1280
COUNTER = 0xC1D47410


def segs_of(d):
    e_phoff, = struct.unpack_from("<I", d, 0x1C)
    e_phentsize, e_phnum = struct.unpack_from("<HH", d, 0x2A)
    out = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz = \
            struct.unpack_from("<IIIIII", d, off)
        if p_type == 1:
            out.append((p_vaddr, p_offset, p_filesz))
    return out


def read_elf_va(d, segs, elf_va, ln):
    dump_va = elf_va - ELF_BIAS
    for v, o, f in segs:
        if v <= dump_va < v + f:
            start = o + (dump_va - v)
            if dump_va + ln > v + f:
                ln = v + f - dump_va
            return d[start:start + ln]
    return None


def uptime_of(path):
    m = re.search(r"up(\d+\.\d+)", os.path.basename(path))
    return float(m.group(1)) if m else float("nan")


def main():
    paths = sys.argv[1:] or sorted(glob.glob("modem_coredump_up*.elf"))
    rows = []
    for p in paths:
        d = open(p, "rb").read()
        segs = segs_of(d)
        rec = read_elf_va(d, segs, ERRFATAL, 0x20)
        cnt = read_elf_va(d, segs, COUNTER, 4)
        if rec is None or cnt is None:
            print("%-52s  MISSING" % os.path.basename(p))
            continue
        line, = struct.unpack_from("<H", rec, 0x10)
        a, = struct.unpack_from("<I", rec, 0x14)
        b, = struct.unpack_from("<I", rec, 0x18)
        counter, = struct.unpack_from("<I", cnt, 0)
        ab = (b << 32) | a
        rows.append((uptime_of(p), line, a, b, counter, ab, os.path.basename(p)))

    rows.sort()
    print("%-10s %-5s %-12s %-12s %-7s %-16s %s" %
          ("uptime", "line", "A", "B", "counter", "A:B (64-bit)", "file"))
    print("-" * 110)
    prev = None
    for up, line, a, b, counter, ab, name in rows:
        print("%-10.2f %-5d 0x%08x   0x%08x   %-7d 0x%016x" % (up, line, a, b, counter, ab))
        if prev is not None:
            dup, da, db, dc, dab = prev
            print("%-10s  dU=%.6f  dA=0x%08x  dB=0x%08x  dcounter=%+d  dA:B=0x%016x" %
                  ("", up - dup, (a - da) & 0xFFFFFFFF, (b - db) & 0xFFFFFFFF,
                   counter - dc, (ab - dab) & 0xFFFFFFFFFFFFFFFF))
        prev = (up, a, b, counter, ab)


if __name__ == "__main__":
    main()

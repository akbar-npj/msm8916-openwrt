#!/usr/bin/env python3
"""Print the modem ELF program headers and, for the known-failed + candidate VAs,
say which segment they fall in (if any)."""
import struct

ELF = "scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
BIAS = 0x39800000
FLAGS = {0: "-", 1: "X", 2: "W", 4: "R", 6: "RW", 5: "RX", 7: "RWX"}

d = open(ELF, "rb").read()
e_phoff = struct.unpack_from("<I", d, 28)[0]
e_phentsize = struct.unpack_from("<H", d, 42)[0]
e_phnum = struct.unpack_from("<H", d, 44)[0]
print("e_phnum=%d" % e_phnum)
print("%-4s %-6s %-11s %-11s %-11s %-11s %-11s %s" % (
    "idx", "type", "vaddr", "paddr", "filesz", "memsz", "bss", "flags"))
segs = []
for i in range(e_phnum):
    o = e_phoff + i * e_phentsize
    t, off, va, pa, fsz, msz, flg, al = struct.unpack_from("<8I", d, o)
    segs.append((i, t, va, pa, fsz, msz, flg))
    print("%-4d %-6d 0x%08x  0x%08x  0x%08x  0x%08x  0x%08x  %s" % (
        i, t, va, pa, fsz, msz, msz - fsz, FLAGS.get(flg, hex(flg))))

print()
print("=== BSS (memsz>filesz) segments, sorted by gap size ===")
bss = [(msz - fsz, i, va, fsz, msz, flg) for (i, t, va, pa, fsz, msz, flg) in segs
       if t == 1 and msz > fsz]
for (gap, i, va, fsz, msz, flg) in sorted(bss, reverse=True):
    print("ph%-3d VA 0x%08x..0x%08x filesz=0x%x bss_gap=0x%x flags=%s" % (
        i, va, va + msz, fsz, gap, FLAGS.get(flg, hex(flg))))

TARGETS = [
    ("v1 FAILED ", 0xc51bf000),
    ("v3 FAILED ", 0xc4546800),
    ("cand A    ", 0xc360f000),
    ("cand B    ", 0xc2a13000),
    ("cand C    ", 0xc2a1b000),
    ("cand D    ", 0xc45b1000),
    ("cand E    ", 0xc45e5000),
    ("cand F    ", 0xc1444000),
    ("cand G    ", 0xc21a5000),
    ("cand H    ", 0xc454f000),
]
print()
print("=== target VAs vs segments ===")
for (nm, va) in TARGETS:
    pa = va - BIAS
    hit = None
    for (i, t, sva, spa, fsz, msz, flg) in segs:
        if t == 1 and sva <= va < sva + msz:
            inbss = va >= sva + fsz
            hit = "ph%-3d 0x%08x..0x%08x flags=%s %s" % (
                i, sva, sva + msz, FLAGS.get(flg, hex(flg)),
                "IN-BSS(filesz end 0x%08x)" % (sva + fsz) if inbss else "in-file")
            break
    print("%s VA 0x%08x (phys 0x%08x): %s" % (nm, va, pa, hit or "NO SEGMENT"))

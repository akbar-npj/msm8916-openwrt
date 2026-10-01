#!/usr/bin/env python3
"""For a page VA, show per-64B-block status across all dumps and find the largest
run that is ZERO in every dump (a genuinely free slot)."""
import struct, os, sys
import numpy as np

BIAS = 0x39800000
FILL = 0xf8f8f8f8
DUMPS = [
    "scratch/android_dump/cw1_cold.elf",
    "scratch/android_dump/cw1_cold_run1.elf",
    "scratch/android_dump/cw1_warm.elf",
    "scratch/android_dump/cw1_warm_run1.elf",
    "scratch/android_dump/warm_restart_41431.elf",
    "scratch/android_dump/modem_20260930T052551Z.elf",
]


def build(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    segs = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, va, pa, fsz, msz, flg, al = struct.unpack_from("<8I", d, o)
        if t == 1 and fsz > 0:
            segs.append((va, off, fsz))
    lo = min(s[0] for s in segs)
    hi = max(s[0] + s[2] for s in segs)
    img = np.full(hi - lo, 0xFF, dtype=np.uint8)
    for (pa, off, fsz) in segs:
        img[pa - lo:pa - lo + fsz] = np.frombuffer(d[off:off + fsz], dtype=np.uint8)
    return lo, img


lo, base = build(DUMPS[0])
imgs = [base]
for p in DUMPS[1:]:
    l, im = build(p)
    imgs.append(im)
stack = np.stack(imgs)
n = len(imgs)

for arg in sys.argv[1:]:
    va = int(arg, 16)
    pa = va - BIAS
    pg = stack[:, pa - lo:pa - lo + 0x1000].reshape(n, 0x1000 // 4, 4).view(np.uint32).reshape(n, 0x1000 // 4)
    diff = (pg != pg[0]).any(axis=0)          # word differs from dump0
    allzero = (pg == 0).all(axis=0)           # word zero in every dump
    allfill = (pg == FILL).all(axis=0)
    print("=== VA 0x%08x ===" % va)
    # largest zero-in-all run
    best = (0, 0)
    i = 0
    while i < len(allzero):
        if allzero[i]:
            j = i
            while j < len(allzero) and allzero[j]:
                j += 1
            if (j - i) * 4 > best[0]:
                best = ((j - i) * 4, i * 4)
            i = j
        else:
            i += 1
    print("  live words: %d/%d   fill words: %d   largest all-zero run: 0x%x bytes at page+0x%x"
          % (int(diff.sum()), len(diff), int(allfill.sum()), best[0], best[1]))
    # 64-byte block map
    line = ""
    for b in range(0, 0x1000, 0x40):
        blk_diff = diff[b // 4:(b + 0x40) // 4].mean()
        blk_zero = allzero[b // 4:(b + 0x40) // 4].mean()
        blk_fill = allfill[b // 4:(b + 0x40) // 4].mean()
        if blk_fill > 0.5:
            line += "F"
        elif blk_zero > 0.9:
            line += "."
        elif blk_diff > 0.02:
            line += "L"
        else:
            line += " "
    print("  page+0x000: " + line[:32])
    print("  page+0x800: " + line[32:])

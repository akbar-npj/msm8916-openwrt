#!/usr/bin/env python3
"""Hexdump a modem VA from every dump, so we can see the exact page content."""
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
names = [os.path.basename(DUMPS[0])]
for p in DUMPS[1:]:
    l, im = build(p)
    imgs.append(im)
    names.append(os.path.basename(p))

va = int(sys.argv[1], 16)
ln = int(sys.argv[2], 16) if len(sys.argv) > 2 else 0x40
pa = va - BIAS
print("VA 0x%08x phys 0x%08x  len 0x%x" % (va, pa, ln))
for (nm, im) in zip(names, imgs):
    sl = im[pa - lo:pa - lo + ln]
    w = sl.reshape(-1, 4).view(np.uint32).reshape(-1)
    fillc = int((w == FILL).sum())
    print("%-32s fill=%d/%d  %s" % (
        nm, fillc, len(w),
        " ".join("%08x" % x for x in w[:min(8, len(w))])))

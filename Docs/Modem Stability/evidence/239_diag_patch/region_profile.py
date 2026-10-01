#!/usr/bin/env python3
"""Profile a VA range at 64KB granularity: for each chunk show diff (live), fill,
zero.  Reveals the mapped-vs-hole boundary inside a declared BSS segment."""
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
    assert l == lo and len(im) == len(base)
    imgs.append(im)
stack = np.stack(imgs)
n = len(imgs)

start = int(sys.argv[1], 16) - BIAS
end = int(sys.argv[2], 16) - BIAS
step = 0x10000

print("VA range 0x%08x..0x%08x, 64KB chunks" % (start + BIAS, end + BIAS))
print("%-12s %6s %6s %6s %6s  %s" % ("VA", "diff", "fill", "zero", "realw", "bar"))
for a in range(start, end, step):
    sl = stack[:, a - lo:a - lo + step]
    w = sl.reshape(n, step // 4, 4).view(np.uint32).reshape(n, step // 4)
    diff = (sl != sl[0]).mean()
    fill = (w == FILL).mean()
    zero = (w == 0).mean()
    realw = int(((w != 0) & (w != FILL)).sum(axis=1).min())
    # bar: L=live(diff), F=fill, Z=zero, ' '=other
    b = ""
    for k in range(0, step, step // 32):
        dd = (sl[:, k:k + step // 32] != sl[0, k:k + step // 32]).mean()
        ww = sl[:, k:k + step // 32].reshape(n, -1, 4).view(np.uint32).reshape(n, -1)
        ff = (ww == FILL).mean()
        zz = (ww == 0).mean()
        if dd > 0.02:
            b += "L"
        elif ff > 0.5:
            b += "F"
        elif zz > 0.9:
            b += "."
        else:
            b += " "
    print("0x%08x %6.3f %6.3f %6.3f %6d  %s" % (a + BIAS, diff, fill, zero, realw, b))

#!/usr/bin/env python3
"""Pick a save area: a page that is (a) written in EVERY dump -- including the two
early-crash patched-firmware dumps, so it is mapped early -- and (b) has a large run
that is zero in every dump."""
import struct, os
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
    "scratch/android_dump/excep_239.elf",   # patched, crashes early
    "scratch/android_dump/excep_240.elf",   # patched, crashes early
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
    assert l == lo and len(im) == len(base), p
    imgs.append(im)
stack = np.stack(imgs)
n = len(imgs)
L = len(base)
P = L // 0x1000

w = stack.reshape(n, P, 0x1000 // 4, 4).view(np.uint32).reshape(n, P, 0x1000 // 4)
realw = ((w != 0) & (w != FILL)).sum(axis=2)              # [n,P] real words
written_all = (realw >= 8).all(axis=0)                    # written in EVERY dump
nfill = (w == FILL).sum(axis=2)
fillfree = (nfill == 0).all(axis=0)                       # no fill in any dump

# largest run zero in every dump, per page
allzero = (stack == 0).reshape(n, P, 0x1000).all(axis=0)  # [P,4096]
runs = np.zeros(P, dtype=np.int32)
roff = np.zeros(P, dtype=np.int32)
for i in range(P):
    row = allzero[i]
    if not row.any():
        continue
    d = np.diff(np.concatenate(([0], row.view(np.int8), [0])))
    starts = np.flatnonzero(d == 1)
    ends = np.flatnonzero(d == -1)
    lens = ends - starts
    k = int(np.argmax(lens))
    runs[i] = lens[k]
    roff[i] = starts[k]

cand = written_all & fillfree & (runs >= 0x200)
idxs = np.flatnonzero(cand)
print("pages written in ALL %d dumps, no fill, zrun>=0x200: %d" % (n, len(idxs)))
# score: heavily written (mapped) * free run
score = realw.min(axis=0) * runs
order = idxs[np.argsort(-score[idxs])]
print("%-12s %7s %7s %6s %6s" % ("VA", "minreal", "zrun", "zoff", "seg"))
for i in order[:25]:
    va = lo + i * 0x1000 + BIAS
    print("0x%08x %7d 0x%04x 0x%04x" % (va, int(realw[:, i].min()), runs[i], roff[i]))

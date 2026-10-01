#!/usr/bin/env python3
"""Compare the ELF image vs the coredumps for a VA range: shows where the dump equals
the image (loaded, untouched), where it is zero (padding), and where it diverges
(runtime-written)."""
import struct, os, sys
import numpy as np

ELF = "scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
BIAS = 0x39800000
DUMPS = [
    "scratch/android_dump/cw1_cold.elf",
    "scratch/android_dump/cw1_warm.elf",
    "scratch/android_dump/warm_restart_41431.elf",
    "scratch/android_dump/excep_240.elf",
]


def elf_image():
    d = open(ELF, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    lo, hi = None, None
    segs = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, va, pa, fsz, msz, flg, al = struct.unpack_from("<8I", d, o)
        if t == 1 and fsz > 0:
            segs.append((va, off, fsz))
            lo = va if lo is None else min(lo, va)
            hi = va + fsz if hi is None else max(hi, va + fsz)
    img = np.full(hi - lo, 0xFF, dtype=np.uint8)
    for (va, off, fsz) in segs:
        img[va - lo:va - lo + fsz] = np.frombuffer(d[off:off + fsz], dtype=np.uint8)
    return lo, img


def dump_image(path):
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


elo, eimg = elf_image()
dlos, dimgs = [], []
for p in DUMPS:
    l, im = dump_image(p)
    dlos.append(l)
    dimgs.append(im)

for arg in sys.argv[1:]:
    va = int(arg, 16)
    print("=== VA 0x%08x ===" % va)
    ei = eimg[va - elo:va - elo + 0x1000].reshape(-1, 4).view(np.uint32).reshape(-1)
    print("  image[0:16]: " + " ".join("%08x" % x for x in ei[:16]))
    for (nm, l, im) in zip(DUMPS, dlos, dimgs):
        di = im[va - BIAS - l:va - BIAS - l + 0x1000].reshape(-1, 4).view(np.uint32).reshape(-1)
        eq = int((ei == di).sum())
        z = int((di == 0).sum())
        print("  %-28s eq_img=%4d/1024 zero=%4d  [0:8]=%s" % (
            os.path.basename(nm), eq, z, " ".join("%08x" % x for x in di[:8])))

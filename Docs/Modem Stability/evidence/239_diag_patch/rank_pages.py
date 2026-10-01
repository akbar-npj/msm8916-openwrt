#!/usr/bin/env python3
"""Rank pages by (proof of modem write) x (free stable run).
For a page covered by the ELF image (loaded segment) 'written' = words differing from
the image; for a BSS page (not in the image) 'written' = nonzero words (PIL zeroes BSS,
so nonzero => the modem wrote it)."""
import struct, os
import numpy as np

ELF = "scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
BIAS = 0x39800000
FILL = 0xf8f8f8f8
DUMPS = [
    "scratch/android_dump/cw1_cold.elf",
    "scratch/android_dump/cw1_cold_run1.elf",
    "scratch/android_dump/cw1_warm.elf",
    "scratch/android_dump/cw1_warm_run1.elf",
    "scratch/android_dump/warm_restart_41431.elf",
    "scratch/android_dump/modem_20260930T052551Z.elf",
    "scratch/android_dump/excep_239.elf",
    "scratch/android_dump/excep_240.elf",
]


def image(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phe = struct.unpack_from("<H", d, 42)[0]
    e_phn = struct.unpack_from("<H", d, 44)[0]
    segs = []
    for i in range(e_phn):
        o = e_phoff + i * e_phe
        t, off, va, pa, fsz, msz, flg, al = struct.unpack_from("<8I", d, o)
        if t == 1 and fsz > 0:
            segs.append((va, off, fsz))
    lo = min(s[0] for s in segs)
    hi = max(s[0] + s[2] for s in segs)
    img = np.full(hi - lo, 0xFF, dtype=np.uint8)
    cov = np.zeros(hi - lo, dtype=bool)
    for (va, off, fsz) in segs:
        img[va - lo:va - lo + fsz] = np.frombuffer(d[off:off + fsz], dtype=np.uint8)
        cov[va - lo:va - lo + fsz] = True
    return lo, img, cov


elo, eimg, ecov = image(ELF)
dumps = []
for p in DUMPS:
    l, im, cv = image(p)
    dumps.append((os.path.basename(p), l, im))
n = len(dumps)

# scan every page of the dump's physical coverage
lo = dumps[0][1]
hi = lo + len(dumps[0][2])
P = (len(dumps[0][2])) // 0x1000
rows = []
for k in range(P):
    dpa = k * 0x1000
    va = lo + dpa + BIAS
    eoff = va - elo
    covered = 0 <= eoff and eoff + 0x1000 <= len(eimg) and ecov[eoff:eoff + 0x1000].all()
    ewords = None
    if covered:
        ewords = eimg[eoff:eoff + 0x1000].reshape(-1, 4).view(np.uint32).reshape(-1)
    minw = 10 ** 9
    zrun = 10 ** 9
    fill_any = False
    for (nm, l, im) in dumps:
        dp = im[dpa:dpa + 0x1000]
        dw = dp.reshape(-1, 4).view(np.uint32).reshape(-1)
        if (dw == FILL).any():
            fill_any = True
        w = int((dw != ewords).sum()) if covered else int((dw != 0).sum())
        minw = min(minw, w)
        z = (dp == 0)
        if not z.any():
            zrun = 0
        else:
            dd = np.diff(np.concatenate(([0], z.view(np.int8), [0])))
            st = np.flatnonzero(dd == 1)
            en = np.flatnonzero(dd == -1)
            zrun = min(zrun, int((en - st).max()))
    if minw < 8 or zrun < 0x100 or fill_any:
        continue
    rows.append((minw, zrun, va, covered))

rows.sort(key=lambda r: -(min(r[0], 2000) * r[1]))
print("%-12s %6s %6s %5s" % ("VA", "minw", "zrun", "cov"))
for (minw, zrun, va, cov) in rows[:30]:
    print("0x%08x %6d 0x%04x %5s" % (va, minw, zrun, cov))

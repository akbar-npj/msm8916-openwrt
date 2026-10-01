#!/usr/bin/env python3
"""Find a save area inside a WRITABLE LOADED segment (filesz>0): the zero bytes there
are explicit image padding -> never touched at runtime, and the segment is mapped.

Cross-check each candidate page against the coredumps: it must be written (nonzero,
non-fill) in every dump, and the chosen run must be zero in every dump.
"""
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


def elf_segs(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    segs = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, va, pa, fsz, msz, flg, al = struct.unpack_from("<8I", d, o)
        if t == 1 and fsz > 0:
            segs.append((i, off, va, pa, fsz, msz, flg, d))
    return segs


def build_dump(path):
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


dlo, dbase = build_dump(DUMPS[0])
dumps = [dbase]
for p in DUMPS[1:]:
    l, im = build_dump(p)
    assert l == dlo and len(im) == len(dbase), p
    dumps.append(im)
dstack = np.stack(dumps)
n = len(dumps)

results = []
for (i, off, va, pa, fsz, msz, flg, d) in elf_segs(ELF):
    if not (flg & 0x2):          # writable only
        continue
    body = np.frombuffer(d[off:off + fsz], dtype=np.uint8)
    # scan 4K pages of this segment, aligned to the ELF va
    base = va & ~0xFFF
    end = va + fsz
    for pv in range(base, end, 0x1000):
        # image bytes of this page
        lo_o = max(0, pv - va)
        hi_o = min(fsz, pv + 0x1000 - va)
        if lo_o >= hi_o:
            continue
        img = np.full(0x1000, 0xFF, dtype=np.uint8)
        img[0:hi_o - lo_o] = body[lo_o:hi_o]
        # largest zero run in the image page
        z = img == 0
        if not z.any():
            continue
        dd = np.diff(np.concatenate(([0], z.view(np.int8), [0])))
        st = np.flatnonzero(dd == 1)
        en = np.flatnonzero(dd == -1)
        k = int(np.argmax(en - st))
        zrun = int(en[k] - st[k])
        zoff = int(st[k])
        if zrun < 0x100:
            continue
        # cross-check dumps: page written in every dump? chosen run zero in every dump?
        dpa = (pv - BIAS) - dlo
        if dpa < 0 or dpa + 0x1000 > len(dbase):
            continue
        dp = dstack[:, dpa:dpa + 0x1000]
        wp = dp.reshape(n, 0x1000 // 4, 4).view(np.uint32).reshape(n, 0x1000 // 4)
        realw = ((wp != 0) & (wp != FILL)).sum(axis=1)
        if realw.min() < 8:
            continue
        run_ok = (dp[:, zoff:zoff + zrun] == 0).all()
        nz = int(((img != 0) & (img != 0xFF)).sum())
        if nz < 16:          # page must be genuinely used (loaded) so it is mapped
            continue
        if not run_ok:
            continue
        results.append((zrun, pv, i, realw.min(), zoff, run_ok, nz))

results.sort(reverse=True)
print("%-12s %6s %5s %8s %6s %6s %6s" % ("VA", "zrun", "ph", "minreal", "zoff", "runok", "img_nz"))
for (zrun, pv, i, mr, zoff, run_ok, nz) in results[:30]:
    print("0x%08x 0x%04x ph%-3d %8d 0x%04x %6s %6d" % (pv, zrun, i, mr, zoff, run_ok, nz))

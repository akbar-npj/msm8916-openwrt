#!/usr/bin/env python3
"""Find a save area large enough for a RING (>= 0x800 bytes), proven MAPPED.

The v1/v3 crash-loops (ledger 57.5) taught the sound test.  A coredump is
PHYSICAL RAM: it holds loader fill and zero pages that persist across boots but
are NOT mapped.  So "zero / free-looking" is NOT proof.  The sound proofs are:

  (A) the page is in a WRITABLE, LOADED (filesz>0) segment;              [ELF]
  (B) the page holds words that VARY across dumps  -> a live writer       [mapped]
      (loader-only fill is byte-identical in every dump),  OR
      the page holds the PIL poison guard 0xdeadc0fe -> the loader touched it;
  (C) a run of >= MINRUN bytes is BYTE-IDENTICAL in EVERY dump -> no live
      writer touches that run, in ANY regime (idle/traffic/cold/warm/post-SSR);
  (D) the stock image is ZERO over that run -> it is free padding, not data.
"""
import struct, os, sys
import numpy as np

ELF = "scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
BIAS = 0x39800000
DUMPS = [
    "scratch/android_dump/cw1_cold.elf",
    "scratch/android_dump/cw1_cold_run1.elf",
    "scratch/android_dump/cw1_warm.elf",
    "scratch/android_dump/cw1_warm_run1.elf",
    "scratch/android_dump/warm_restart_41431.elf",
    "scratch/android_dump/modem_20260930T052551Z.elf",
    "scratch/android_dump/excep_239.elf",
    "scratch/android_dump/excep_240.elf",
    "scratch/android_dump/diag_v4.elf",
    "scratch/android_dump/diag_v6.elf",
]
MINRUN = 0x800
POISON = bytes.fromhex("fec0adde")   # 0xdeadc0fe little-endian


def load(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    segs = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, va, pa, fsz, msz, flg, al = struct.unpack_from("<8I", d, o)
        if t == 1 and fsz > 0:
            segs.append((i, off, va, fsz, flg))
    lo = min(s[2] for s in segs)
    hi = max(s[2] + s[3] for s in segs)
    img = np.full(hi - lo, 0xFF, dtype=np.uint8)
    for (i, off, va, fsz, flg) in segs:
        img[va - lo:va - lo + fsz] = np.frombuffer(d[off:off + fsz], dtype=np.uint8)
    return lo, img, segs


elo, eimg, esegs = load(ELF)
dumps = []
for p in DUMPS:
    l, im, _ = load(p)
    dumps.append((os.path.basename(p), l, im))
print(f"[+] {len(dumps)} dumps loaded; elf base 0x{elo:08x}", file=sys.stderr)
print("[+] dump bases: " + ", ".join("0x%08x" % l for (_, l, _) in dumps), file=sys.stderr)

results = []
for (i, off, va, fsz, flg) in esegs:
    if not (flg & 0x2):
        continue
    end = va + fsz
    for pv in range(va & ~0xFFF, end, 0x1000):
        if pv < va or pv + 0x1000 > end:
            continue
        epage = eimg[pv - elo:pv - elo + 0x1000]
        if (epage == 0xFF).any():
            continue
        pages = []
        ok = True
        for (nm, l, im) in dumps:
            dpa = (pv - BIAS) - l
            if dpa < 0 or dpa + 0x1000 > len(im):
                ok = False
                break
            pages.append(im[dpa:dpa + 0x1000])
        if not ok:
            continue
        P = np.stack(pages)
        nvar = int((P != P[0]).any(axis=0).sum())          # live-write evidence
        has_poison = POISON in P[0].tobytes()
        same = (P == P[0]).all(axis=0)
        dd = np.diff(np.concatenate(([0], same.view(np.int8), [0])))
        st = np.flatnonzero(dd == 1)
        en = np.flatnonzero(dd == -1)
        if len(st) == 0:
            continue
        k = int(np.argmax(en - st))
        runlen = int(en[k] - st[k])
        runoff = int(st[k])
        if runlen < MINRUN:
            continue
        imgzero = bool((epage[runoff:runoff + runlen] == 0).all())
        if nvar == 0 and not has_poison:
            continue                     # (B) not proven mapped -> reject
        results.append((runlen, nvar, has_poison, imgzero, pv, i, runoff))

results.sort(key=lambda r: (-r[0], -r[1]))
print("%-12s %6s %6s %5s %5s %5s %6s" % ("VA", "run", "nvar", "pois", "img0", "ph", "zoff"))
for (runlen, nvar, hp, iz, pv, i, runoff) in results[:40]:
    print("0x%08x 0x%04x %6d %5s %5s ph%-3d 0x%04x" % (pv, runlen, nvar, hp, iz, i, runoff))

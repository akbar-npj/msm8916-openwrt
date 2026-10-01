#!/usr/bin/env python3
"""Map the modem's physical memory: which 4K pages are LIVE (differ across boots
=> the modem writes them => mapped+writable) vs FILL (0xf8f8f8f8 loader fill => hole)
vs ZERO.  Then find a page that is live AND has a stable all-zero run we can use as
a save area.

phys = modem_va - 0x39800000   (same bias as the OpenWrt coredumps)
"""
import struct, os, sys
import numpy as np

BIAS = 0x39800000
FILL = 0xf8f8f8f8

STOCK = [
    "scratch/android_dump/cw1_cold.elf",
    "scratch/android_dump/cw1_cold_run1.elf",
    "scratch/android_dump/cw1_warm.elf",
    "scratch/android_dump/cw1_warm_run1.elf",
    "scratch/android_dump/warm_restart_41431.elf",
    "scratch/android_dump/modem_20260930T052551Z.elf",
]
PATCHED = [
    "scratch/android_dump/excep_239.elf",
    "scratch/android_dump/excep_240.elf",
]


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
            segs.append((va, off, fsz))   # here va is the PHYS addr in the dump
    return d, segs


def build_image(path):
    d, segs = load(path)
    lo = min(s[0] for s in segs)
    hi = max(s[0] + s[2] for s in segs)
    img = np.full(hi - lo, 0xFF, dtype=np.uint8)  # 0xFF = "not covered"
    for (pa, off, fsz) in segs:
        img[pa - lo:pa - lo + fsz] = np.frombuffer(d[off:off + fsz], dtype=np.uint8)
    return lo, img


def main():
    names = []
    lo, base = build_image(STOCK[0])
    names.append(os.path.basename(STOCK[0]))
    imgs = [base]
    for p in STOCK[1:]:
        l, im = build_image(p)
        assert l == lo, (p, hex(l), hex(lo))
        assert len(im) == len(base), (p, len(im), len(base))
        imgs.append(im)
        names.append(os.path.basename(p))
    print("covered phys: 0x%x .. 0x%x  (%.1f MB)  VA 0x%x .. 0x%x" % (
        lo, lo + len(base), len(base) / 1e6, lo + BIAS, lo + len(base) + BIAS))

    stack = np.stack(imgs)                      # [N, L]
    L = len(base)
    n = len(imgs)
    P = L // 0x1000

    # word view for fill / code-ptr analysis
    w = stack.reshape(n, P, 0x1000 // 4, 4).view(np.uint32).reshape(n, P, 0x1000 // 4)
    fill_frac = (w == FILL).mean(axis=2)                       # [n,P]
    zero_frac = (w == 0).mean(axis=2)
    codepat = ((w >= 0xc0000000) & (w < 0xc1c40000)).sum(axis=2)  # [n,P]
    covered = (stack != 0xFF).reshape(n, P, 0x1000).mean(axis=2)

    # cross-boot diff: bytes that differ between any two dumps
    diff = (stack != stack[0]).any(axis=0).reshape(P, 0x1000).mean(axis=1)  # [P]

    # stable all-zero run per page: longest run of zero bytes in EVERY dump
    pagezero = (stack == 0).reshape(n, P, 0x1000)              # [n,P,4096]
    allzero = pagezero.all(axis=0)                             # [P,4096] zero in every dump

    # longest run of allzero per page
    runs = np.zeros(P, dtype=np.int32)
    for i in range(P):
        row = allzero[i]
        if not row.any():
            continue
        # longest True run
        idx = np.flatnonzero(np.diff(np.concatenate(([0], row.view(np.int8), [0]))))
        runs[i] = (idx[1::2] - idx[0::2]).max() if len(idx) >= 2 else 0

    live = diff > 0.01
    print("pages live(diff>1%%): %d / %d" % (live.sum(), P))

    # "written every boot": >= 16 real (nonzero, non-fill) words in EVERY dump
    real_words = ((w != 0) & (w != FILL)).sum(axis=2)          # [n,P]
    written_all = (real_words >= 16).all(axis=0)               # [P]

    # candidates: live page, written in every dump, stable zero run >= 0x100
    cand = live & written_all & (runs >= 0x100)
    idxs = np.flatnonzero(cand)
    score = diff * runs
    order = idxs[np.argsort(-score[idxs])]
    print("candidates (live & written-every-boot & zrun>=0x100): %d" % len(idxs))
    print("%-12s %6s %6s %6s %6s %5s %6s" % (
        "VA", "diff", "fill", "zero", "realw", "zrun", "score"))
    for i in order[:40]:
        va = lo + i * 0x1000 + BIAS
        print("0x%08x %6.3f %6.3f %6.3f %6d %5d %6.2f" % (
            va, diff[i], fill_frac[:, i].mean(), zero_frac[:, i].mean(),
            int(real_words[:, i].min()), runs[i], score[i]))

    # exact offset of the first long stable zero run for the top candidate
    if len(order):
        i = order[0]
        row = allzero[i]
        # find first run >= 0x100
        b = 0
        while b < 0x1000:
            if row[b]:
                e = b
                while e < 0x1000 and row[e]:
                    e += 1
                if e - b >= 0x100:
                    print("TOP 0x%08x: first stable zero run at page+0x%x len 0x%x"
                          % (lo + i * 0x1000 + BIAS, b, e - b))
                b = e
            b += 1


if __name__ == "__main__":
    main()

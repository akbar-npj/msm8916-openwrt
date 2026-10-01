#!/usr/bin/env python3
"""Independent re-verification: locate the state-20 ML1 timer ctx object(s) in a
modem coredump BY INVARIANT (never by a fixed VA), and report whether the
watchdog is ARMED (pending expiry) or CLEARED at the dump instant.

ctx invariants (from FUN_c02fb8b0 / the armer FUN_c02fda90):
  +0x0c == 0xc02d7bd0   the ML1 timer callback (FUN_c02d7bd0)
  +0x14 == self          the ctx VA itself
  +0x38 in [20,28]       the state byte
  +0x20/+0x24            u64 #1  (expiry)
  +0x28/+0x2c            u64 #2  (arm)

Search window: the whole 0xc2150000..0xc2160000 page region, stride 4 (not 0x40),
so a shifted base cannot be missed.

Usage: python3 ctx_scan.py <coredump.elf> [--win LO..HI]
"""
import struct, sys

BIAS = 0x39800000
WIN_LO = 0xc2140000
WIN_HI = 0xc2170000
CB = 0xc02d7bd0


def load(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    ph = [struct.unpack_from("<7I", d, e_phoff + i * e_phentsize) for i in range(e_phnum)]
    return d, ph


def rd(d, ph, va, n=1):
    out = []
    for k in range(n):
        phys = va - BIAS + 4 * k
        v = None
        for (t, off, vv, pa, fsz, msz, flg) in ph:
            if t == 1 and pa <= phys < pa + fsz:
                v = struct.unpack_from("<I", d, off + (phys - pa))[0]
                break
        out.append(v)
    return out


def scan(path, lo, hi):
    d, ph = load(path)
    hits = []
    va = lo
    while va < hi:
        w = rd(d, ph, va, 0x40 // 4)
        if all(x is not None for x in w):
            cb = w[0x0c // 4]
            self_ = w[0x14 // 4]
            state = w[0x38 // 4]
            if cb == CB and self_ == va and 20 <= state <= 28:
                u1 = (w[0x24 // 4] << 32) | w[0x20 // 4]
                u2 = (w[0x2c // 4] << 32) | w[0x28 // 4]
                hits.append(dict(va=va, state=state, u1=u1, u2=u2,
                                 raw=(w[0x20 // 4], w[0x24 // 4], w[0x28 // 4], w[0x2c // 4])))
        va += 4
    return hits


def main():
    path = sys.argv[1]
    lo, hi = WIN_LO, WIN_HI
    if "--win" in sys.argv:
        a = sys.argv[sys.argv.index("--win") + 1]
        lo, hi = [int(x, 0) for x in a.split("..")]
    hits = scan(path, lo, hi)
    name = path.split("/")[-1]
    if not hits:
        print(f"{name:28} NO ctx matched in 0x{lo:x}..0x{hi:x}")
        return
    for h in sorted(hits, key=lambda h: h["va"]):
        armed = (h["u1"] != 0) or (h["u2"] != 0)
        print(f"{name:28} ctx 0x{h['va']:08x} state {h['state']:2d}  "
              f"u1(+0x20)=0x{h['u1']:016x} u2(+0x28)=0x{h['u2']:016x}  "
              f"{'ARMED' if armed else 'cleared'}")


if __name__ == "__main__":
    main()

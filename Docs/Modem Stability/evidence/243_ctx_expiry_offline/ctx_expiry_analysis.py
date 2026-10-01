#!/usr/bin/env python3
"""Item 82 — OFFLINE test: is the ~900 s fatal the state-20 watchdog expiry?

For every archived modem coredump:
  * locate the nine ML1 ctx objects BY INVARIANT (never a fixed VA):
        +0x0c == 0xc02d7bd0 (the ML1 timer callback)
        +0x14 == self
        +0x38 in [20,28]    (the state byte; ctx_i has state 20+i)
  * read ctx0's  +0x20 (expiry, u64)  +0x28 (arm, u64)  +0x30 (low byte)
  * report ARMED (expiry != 0) / CLEARED (expiry == 0)

Rationale (see ledger item 82): the ctx is armed by FUN_c02fda90 with a 50 ms
timeout (Δ = +0x20 - +0x28 = 960 074 ticks = 50.004 ms), at 0.2-0.75 Hz
(items 80/81).  So "expiry != 0" is a ~1-4 %-of-the-time window.  If the fatal
were independent of that window, seeing it set at 3/3 fatals is p ~ 5e-5.

Usage: python3 ctx_expiry_analysis.py <dump.elf> [...]
"""
import struct, sys

BIAS = 0x39800000
WIN_LO, WIN_HI = 0xc2140000, 0xc2170000
CB = 0xc02d7bd0


def load(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phes = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    ph = [struct.unpack_from("<7I", d, e_phoff + i * e_phes) for i in range(e_phnum)]
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


def ctxs(d, ph):
    hits = []
    va = WIN_LO
    while va < WIN_HI:
        w = rd(d, ph, va, 16)
        if all(x is not None for x in w) and w[3] == CB and w[5] == va and 20 <= w[14] <= 28:
            hits.append((va, w))
        va += 4
    return hits


def main():
    print(f"{'dump':30} {'ctx0':>10} {'state':>5} {'expiry(+0x20)':>16} {'arm(+0x28)':>16} "
          f"{'+0x30':>8} {'verdict':>8}  nctx")
    armed_events, clean_events = [], []
    for path in sys.argv[1:]:
        name = path.split("/")[-1].replace(".elf", "")
        d, ph = load(path)
        cs = ctxs(d, ph)
        if not cs:
            print(f"{name:30} {'-':>10} {'-':>5} {'-':>16} {'-':>16} {'-':>8} {'no ctx':>8}  0")
            continue
        cs.sort(key=lambda x: x[0])
        va, w = cs[0]
        exp = (w[0x24 // 4] << 32) | w[0x20 // 4]
        arm = (w[0x2c // 4] << 32) | w[0x28 // 4]
        b30 = w[0x30 // 4] & 0xff
        verdict = "ARMED" if exp else "cleared"
        print(f"{name:30} 0x{va:08x} {w[0x38 // 4]:5d} 0x{exp:016x} 0x{arm:016x} "
              f"0x{b30:02x}    {verdict:>8}  {len(cs)}")
        (armed_events if exp else clean_events).append(name)
    print()
    print(f"ARMED (expiry != 0): {len(armed_events)}  -> {armed_events}")
    print(f"cleared            : {len(clean_events)}  -> {clean_events}")


if __name__ == "__main__":
    main()

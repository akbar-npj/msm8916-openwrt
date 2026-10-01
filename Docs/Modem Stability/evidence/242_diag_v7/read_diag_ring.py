#!/usr/bin/env python3
"""Read the v7 RING-BUFFER HISTORY of FUN_c02fda90 from a modem coredump.

The v7 diagnostic firmware patch (build_diag_patch_v7.py) detours the TWO call
sites of FUN_c02fda90 (the "send an ML1 cell-measurement request + ARM the 50 ms
state-20 watchdog" primitive) through a cave that APPENDS to a ring:

    SAVE_AREA = 0xc1455000  (modem VA; phys 0x87c55000)

    HEADER (16 B):
      +0x00  magic = 0xc0030560   (the cave's own VA -> self-identifies v7)
      +0x04  count                 (total calls; the ring index is (count-1)&127)
      +0x08  idx   = (count-1)&127
    ENTRY i (16 B at +0x10 + i*16):
      +0x00  seq         the monotonic call number
      +0x04  caller_r31  0xc032687c -> A ; 0xc033c0f8 -> B
      +0x08  r2          the message selector: 1 -> 0x408020d ; 0 -> 0x4070210
      +0x0c  r0          the instance argument

Coredump addressing: p_paddr == p_vaddr == modem PHYSICAL address, and
phys = modem_va - 0x39800000.  The tool finds the PT_LOAD covering the save page.

Usage:  python3 read_diag_ring.py <coredump.elf> [SAVE_VA] [-n N]
"""
import struct, sys

SAVE_VA = 0xc1455000
MODEM_VA_BIAS = 0x39800000
MAGIC = 0xc0030560
RING_N = 128
HDR = 0x10
CALLER_A = 0xc032687c
CALLER_B = 0xc033c0f8


def load_phdrs(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    ph = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, va, pa, fsz, msz, flg = struct.unpack_from("<7I", d, o)
        ph.append(dict(i=i, type=t, off=off, va=va, pa=pa, fsz=fsz, msz=msz, flg=flg))
    return d, ph


def read_word(d, ph, phys):
    for s in ph:
        if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["fsz"]:
            return struct.unpack_from("<I", d, s["off"] + (phys - s["pa"]))[0]
    return None


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    save_va = SAVE_VA
    n_show = 128
    args = sys.argv[2:]
    if args and not args[0].startswith("-"):
        save_va = int(args.pop(0), 0)
    if "-n" in args:
        n_show = int(args[args.index("-n") + 1])
    phys = save_va - MODEM_VA_BIAS

    d, ph = load_phdrs(path)
    print(f"coredump : {path}  ({len(d)} B, {len(ph)} phdrs)")
    print(f"ring VA  : 0x{save_va:08x}  ->  phys 0x{phys:08x}")

    seg = None
    for s in ph:
        if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["fsz"]:
            seg = s
            break
    if seg is None:
        sys.exit("  !! ring page not covered by any dumped PT_LOAD in this capture")
    print(f"segment  : phdr {seg['i']}  pa=0x{seg['pa']:08x} fsz=0x{seg['fsz']:08x}")

    magic = read_word(d, ph, phys + 0x00)
    count = read_word(d, ph, phys + 0x04)
    idx = read_word(d, ph, phys + 0x08)
    print(f"\n  +0x00 magic = 0x{magic:08x}   (expect 0x{MAGIC:08x})")
    print(f"  +0x04 count = {count}")
    print(f"  +0x08 idx   = {idx}")
    if magic != MAGIC:
        print("\n  !! NOT a v7 export (magic mismatch) -- the cave never ran, "
              "or the image is not v7.")
        return

    def rd(off):
        return read_word(d, ph, phys + off)

    entries = []
    for i in range(RING_N):
        b = HDR + i * 16
        seq = rd(b + 0x00)
        caller = rd(b + 0x04)
        sel = rd(b + 0x08)
        inst = rd(b + 0x0c)
        entries.append((seq, caller, sel, inst))

    # The cave writes call N to slot (N & 127), so slot 0 is the 128th slot, not
    # the first: for count < 128 slot 0 is UNWRITTEN (seq 0).  seq starts at 1,
    # so seq == 0 identifies an unwritten slot -- filter it and sort by seq, which
    # is robust to the wrap (it also gives the chronological order directly).
    live = sorted([e for e in entries if e[0] != 0], key=lambda e: e[0])

    def cn(c):
        if c == CALLER_A: return "A(FUN_c032685c mobility)"
        if c == CALLER_B: return "B(ACQ CELL_MEAS)"
        return f"0x{c:08x} ?"
    def sn(s):
        if s == 1: return "1 -> 0x408020d"
        if s == 0: return "0 -> 0x4070210"
        return f"{s} ?"

    print(f"\n  === {len(live)} recorded calls, chronological ===")
    print("   seq      caller_r31            r2               r0(inst)")
    for (seq, caller, sel, inst) in live[-n_show:]:
        print(f"   {seq:<8} {cn(caller):<22} {sn(sel):<16} 0x{inst:08x}")

    ca = sum(1 for e in live if e[1] == CALLER_A)
    cb = sum(1 for e in live if e[1] == CALLER_B)
    s1 = sum(1 for e in live if e[2] == 1)
    s0 = sum(1 for e in live if e[2] == 0)
    print(f"\n  histogram (of the {len(live)} in the ring):")
    print(f"    caller A (mobility) : {ca}")
    print(f"    caller B (ACQ)      : {cb}")
    print(f"    r2 == 1 (0x408020d) : {s1}")
    print(f"    r2 == 0 (0x4070210) : {s0}")
    if live:
        print(f"\n  seq span in ring: {live[0][0]} .. {live[-1][0]}")
        print(f"  last call       : caller {cn(live[-1][1])}, selector {sn(live[-1][2])}")


if __name__ == "__main__":
    main()

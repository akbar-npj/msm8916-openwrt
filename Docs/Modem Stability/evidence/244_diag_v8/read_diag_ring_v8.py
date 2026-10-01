#!/usr/bin/env python3
"""Read the v8 PAIRED ARM+CALLBACK ring from a modem coredump.

v8 (build_diag_patch_v8.py) detours THREE sites into two caves that share ONE
ring and ONE monotonic counter:

    ARM      = the two call sites of FUN_c02fda90 (the "send an ML1 cell-measure
               request + arm the 50 ms state-20 watchdog" primitive)
    CALLBACK = the entry of FUN_c02d7bd0 (the shared ML1 state dispatcher, whose
               dispatch index is the STATE BYTE at ctx+0x38 -- see ledger 83 s1)

    SAVE_AREA = 0xc1455000  (modem VA; phys 0x87c55000)

    HEADER (64 B):
      +0x00  magic = 0xc1455000   (the save VA itself -> self-identifies v8)
      +0x04  count                (total events, both kinds)
    ENTRY i (16 B at +0x40 + i*16), i = count & 0x7f  (slot 0 == the 128th slot)
      +0x00  seq   ARM: seq            CALLBACK: seq | 0x80000000  (bit31 = tag)
      +0x04  a     ARM: caller r31     CALLBACK: ctx (r0)
      +0x08  b     ARM: r2 (selector)  CALLBACK: state (ctx+0x38)
      +0x0c  c     ARM: r0 (index)     CALLBACK: ctx+0x20 (expiry low word)

The two sources share the counter, so the GAP between an ARM's seq and the next
CALLBACK's seq is a coarse clock (the CALLBACK stream runs ~12 Hz -> 1 entry
~ 83 ms; see ledger item 82).  `analyse()` therefore prints, for every ARM, the
number of entries and the number of STATE-20 callbacks in the following window.

Coredump addressing: p_paddr == p_vaddr == modem PHYSICAL address, and
phys = modem_va - 0x39800000.  The tool finds the PT_LOAD covering the save page.

Usage:  python3 read_diag_ring_v8.py <coredump.elf> [-n N] [--all]
"""
import struct, sys

SAVE_VA = 0xc1455000
MODEM_VA_BIAS = 0x39800000
MAGIC = 0xc1455000
RING_N = 128
HDR = 0x40
CALLER_A = 0xc032687c
CALLER_B = 0xc033c0f8
STATE_CTX0 = 20


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


def cn(c):
    if c == CALLER_A: return "A(mobility FUN_c032685c)"
    if c == CALLER_B: return "B(ACQ CELL_MEAS)"
    return f"0x{c:08x} ?"


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    n_show = 128
    show_all = "--all" in sys.argv
    if "-n" in sys.argv:
        n_show = int(sys.argv[sys.argv.index("-n") + 1])

    phys = SAVE_VA - MODEM_VA_BIAS
    d, ph = load_phdrs(path)
    print(f"coredump : {path}  ({len(d)} B, {len(ph)} phdrs)")
    print(f"ring VA  : 0x{SAVE_VA:08x}  ->  phys 0x{phys:08x}")

    seg = None
    for s in ph:
        if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["fsz"]:
            seg = s
            break
    if seg is None:
        sys.exit("  !! ring page not covered by any dumped PT_LOAD in this capture")
    print(f"segment  : phdr {seg['i']}  pa=0x{seg['pa']:08x} fsz=0x{seg['fsz']:08x}")

    def rd(off):
        return read_word(d, ph, phys + off)

    magic = rd(0x00)
    count = rd(0x04)
    print(f"\n  +0x00 magic = 0x{magic:08x}   (expect 0x{MAGIC:08x})")
    print(f"  +0x04 count = {count}")
    if magic != MAGIC:
        print("\n  !! NOT a v8 export (magic mismatch) -- the cave never ran, "
              "or the image is not v8.")
        return

    entries = []
    for i in range(RING_N):
        b = HDR + i * 16
        entries.append((rd(b + 0x00), rd(b + 0x04), rd(b + 0x08), rd(b + 0x0c)))

    live = [e for e in entries if e[0] != 0]
    live.sort(key=lambda e: e[0] & 0x7FFFFFFF)

    n_arm = sum(1 for e in live if not (e[0] & 0x80000000))
    n_cb = sum(1 for e in live if (e[0] & 0x80000000))
    print(f"\n  === {len(live)} entries in the ring: {n_arm} ARM, {n_cb} CALLBACK ===")
    print("   seq      kind  detail")
    shown = live if show_all else live[-n_show:]
    for (s, a, b, c) in shown:
        seq = s & 0x7FFFFFFF
        if s & 0x80000000:
            print(f"   {seq:<8} CB    ctx=0x{a:08x} state={b:<3} expiry_lo=0x{c:08x}")
        else:
            print(f"   {seq:<8} ARM   caller={cn(a):<26} r2={b} r0=0x{c:08x}")

    if live:
        print(f"\n  seq span in ring: {live[0][0] & 0x7FFFFFFF} .. {live[-1][0] & 0x7FFFFFFF}")

    # --- the ARM -> next-CALLBACK analysis (the point of v8) ---
    print("\n  === ARM -> following CALLBACKs (state-20 == the ctx0 watchdog) ===")
    print("   arm_seq  caller                     #CB_after  #state20_after   first state20 at")
    for idx, (s, a, b, c) in enumerate(live):
        if s & 0x80000000:
            continue
        arm_seq = s & 0x7FFFFFFF
        n_after = 0
        first20 = None
        for (s2, a2, b2, c2) in live[idx + 1:]:
            n_after += 1
            if (s2 & 0x80000000) and b2 == STATE_CTX0 and first20 is None:
                first20 = s2 & 0x7FFFFFFF
            if n_after >= 20:
                break
        n20 = sum(1 for (s2, a2, b2, c2) in live[idx + 1:idx + 21]
                  if (s2 & 0x80000000) and b2 == STATE_CTX0)
        print(f"   {arm_seq:<8} {cn(a):<26} {n_after:<10} {n20:<15} "
              f"{('+%d' % (first20 - arm_seq)) if first20 else '-'}")

    # state histogram of the callbacks
    hist = {}
    for (s, a, b, c) in live:
        if s & 0x80000000:
            hist[b] = hist.get(b, 0) + 1
    if hist:
        print("\n  callback state histogram (in ring): " +
              ", ".join(f"{k}:{v}" for k, v in sorted(hist.items())))


if __name__ == "__main__":
    main()

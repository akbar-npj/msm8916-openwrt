#!/usr/bin/env python3
"""Read the diagnostic export from a modem coredump.

The diagnostic firmware patch (build_diag_patch.py) detours the ML1 timer
callback entry (VA 0xc02d7bd0) through a cave (VA 0xc003054c) that records, on
every invocation:

    SAVE_AREA = 0xc14408f0  (modem VA)
      +0x00  r0   the callback's first argument   (the timer context pointer)
      +0x04  r1   the second argument             (the dispatch index)
      +0x08  r31  the return address into the callback (a fixed marker)
      +0x0c  r29  the frame pointer at entry
      +0x10  counter  incremented once per callback invocation

The save area is the free tail of page 0xc1440000, the first page of phdr17 (a
LOADED writable data segment).  v1 (0xc51bf000) and v3 (0xc4546800) both FAULTED
because those pages, although free-LOOKING in the dumps, are never written by the
modem and are therefore NOT MAPPED.  The v4 page is chosen because the modem
itself writes it (39..122 words, varying across boots) while the image is all
zero there -- proving it is mapped and writable.

Coredump addressing: for a modem coredump ELF, p_paddr == p_vaddr == the modem
PHYSICAL address, and phys = modem_va - 0x39800000.  So this tool finds the
PT_LOAD whose [p_paddr, p_paddr+p_filesz) contains SAVE_VA-0x39800000 and reads
the 5 words from there.

Usage:  python3 read_diag_export.py <coredump.elf> [SAVE_VA]
"""
import struct, sys

DEFAULT_SAVE_VA = 0xc14408f0
MODEM_VA_BIAS   = 0x39800000
MARKER          = 0xc02d7bdc   # r31 at entry: the packet after the call at 0xc02d7bd0
                               # (packet spans 0xc02d7bd0..0xc02d7bdc; confirmed live
                               #  in the excep coredump task context at 0xc1d19984)


def load_phdrs(path):
    d = open(path, "rb").read()
    e_phoff     = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum     = struct.unpack_from("<H", d, 44)[0]
    ph = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, va, pa, fsz, msz, flg = struct.unpack_from("<7I", d, o)
        ph.append(dict(i=i, type=t, off=off, va=va, pa=pa, fsz=fsz, msz=msz, flg=flg))
    return d, ph


def read_word(d, ph, phys, off):
    for s in ph:
        if s["type"] == 1 and s["pa"] <= phys + off < s["pa"] + s["fsz"]:
            return struct.unpack_from("<I", d, s["off"] + (phys + off - s["pa"]))[0]
    return None


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    save_va = int(sys.argv[2], 0) if len(sys.argv) > 2 else DEFAULT_SAVE_VA
    phys = save_va - MODEM_VA_BIAS

    d, ph = load_phdrs(path)
    print(f"coredump : {path}  ({len(d)} B, {len(ph)} phdrs)")
    print(f"save VA  : 0x{save_va:08x}  ->  phys 0x{phys:08x}")

    seg = None
    for s in ph:
        if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["fsz"]:
            seg = s
            break
    if seg is None:
        # not present with p_filesz: report whether it is merely un-dumped
        for s in ph:
            if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["msz"]:
                print(f"  !! save area is in phdr {s['i']} (p_memsz=0x{s['msz']:x}) "
                      f"but p_filesz=0x{s['fsz']:x} -> NOT dumped in this capture")
                sys.exit(2)
        sys.exit("  !! save area is not covered by any PT_LOAD in this coredump")

    print(f"segment  : phdr {seg['i']}  pa=0x{seg['pa']:08x} fsz=0x{seg['fsz']:08x}")

    names = ["r0  (timer ctx)", "r1  (dispatch idx)", "r31 (return marker)",
             "r29 (frame)", "counter"]
    vals = [read_word(d, ph, phys, 4 * k) for k in range(5)]
    if any(v is None for v in vals):
        sys.exit("  !! a word fell outside the dumped segments")

    print("\n  offset  value       field")
    for k, (n, v) in enumerate(zip(names, vals)):
        print(f"  +0x{4*k:02x}   0x{v:08x}  {n}")

    # ---- interpretation ----
    print()
    if vals[2] == MARKER:
        print(f"  marker r31 = 0x{vals[2]:08x} == 0x{MARKER:08x}  -> the cave RAN (sanity OK)")
    else:
        print(f"  marker r31 = 0x{vals[2]:08x} != 0x{MARKER:08x}  -> CAVE NEVER RAN "
              f"(or the callback entry differs)")

    if vals[4] == 0:
        print("  counter = 0  -> the callback was never entered this power cycle")
    else:
        print(f"  counter = {vals[4]}  -> the callback ran {vals[4]}x since power-on")

    ctx = vals[0]
    if ctx:
        print(f"  ctx = 0x{ctx:08x}  (the ML1 timer-context object the callback received)")

    # dump any other non-zero word in the first 0x100 bytes (unexpected writes)
    extra = []
    for off in range(0, 0x100, 4):
        if off in (0, 4, 8, 12, 16):
            continue
        v = read_word(d, ph, phys, off)
        if v:
            extra.append((off, v))
    if extra:
        print("  NOTE: unexpected non-zero words in the save page:")
        for off, v in extra:
            print(f"    +0x{off:03x} = 0x{v:08x}")


if __name__ == "__main__":
    main()

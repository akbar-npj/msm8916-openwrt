#!/usr/bin/env python3
"""Read the v5 diagnostic export from a modem coredump.

The v5 firmware patch (build_diag_patch_v5.py) detours BOTH call sites of
FUN_c02fda90 (the primitive that sends an ML1 cell-measurement request and arms
the 50 ms "state 20" watchdog) through a shared cave.  The cave records, on every
call into FUN_c02fda90:

    SAVE_AREA = 0xc14408f0  (modem VA)
      +0x00  r31   the caller's return address   ** which caller **
      +0x04  r0    inst
      +0x08  r1    arg2   (A: &local_34 stack ptr ; B: r17+2 object ptr)
      +0x0c  r2    arg3   ** the message selector **
      +0x10  r3    arg4
      +0x14  r4    arg5
      +0x18  r29   the caller's frame pointer
      +0x1c  counter  incremented once per call into FUN_c02fda90
      +0x20  magic = 0xc0030560 (the cave's own VA) -- self-identifies the layout

The two call sites and the values they pass:
    A  0xc0326874  inside FUN_c032685c (ML1 measurement/mobility evaluator)
         r2 (= FUN_c032685c arg4) -> 0x408020d when 1, 0x4070210 when 0
    B  0xc033c0f4  inside 0xc033c0a4 (LTE_ML1_SM_ACQ_STM CELL_MEAS activity)
         r2 = 0 -> 0x4070210

r31 (the return address) is the UNAMBIGUOUS caller id; r2 alone is NOT (because
FUN_c032685c forwards its own arg4, which is 0 at two of its three call sites).
Expected r31: near 0xc032687c for A, near 0xc033c0f8 for B (exact value = the
packet after the call; matched by proximity below).

Coredump addressing: p_paddr == p_vaddr == modem PHYSICAL address,
phys = modem_va - 0x39800000.

Usage:  python3 read_diag_export_v5.py <coredump.elf> [SAVE_VA]
"""
import struct, sys

DEFAULT_SAVE_VA = 0xc14408f0
MODEM_VA_BIAS   = 0x39800000

SITE_A = 0xc0326874   # call inside FUN_c032685c
SITE_B = 0xc033c0f4   # call inside 0xc033c0a4 (ACQ CELL_MEAS)
MAGIC  = 0xc0030560   # the cave's own VA, stored at +0x20

FIELDS = [
    ("r31", "caller return address  ** which caller **"),
    ("r0",  "inst"),
    ("r1",  "arg2"),
    ("r2",  "arg3  ** message selector **"),
    ("r3",  "arg4"),
    ("r4",  "arg5"),
    ("r29", "caller frame pointer"),
    ("ctr", "counter"),
    ("mag", "magic (expect 0xc0030560)"),
]


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


def classify_caller(r31):
    if r31 is None:
        return "?"
    if abs(r31 - SITE_A) <= 0x10:
        return "A  (FUN_c032685c, ML1 measurement/mobility evaluator)"
    if abs(r31 - SITE_B) <= 0x10:
        return "B  (0xc033c0a4, LTE_ML1_SM_ACQ_STM CELL_MEAS activity)"
    return "UNKNOWN (not near either call site)"


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
        for s in ph:
            if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["msz"]:
                print(f"  !! save area is in phdr {s['i']} (p_memsz=0x{s['msz']:x}) "
                      f"but p_filesz=0x{s['fsz']:x} -> NOT dumped in this capture")
                sys.exit(2)
        sys.exit("  !! save area is not covered by any PT_LOAD in this coredump")

    print(f"segment  : phdr {seg['i']}  pa=0x{seg['pa']:08x} fsz=0x{seg['fsz']:08x}")

    vals = [read_word(d, ph, phys, 4 * k) for k in range(len(FIELDS))]
    if any(v is None for v in vals):
        sys.exit("  !! a word fell outside the dumped segments")

    print("\n  offset  value       field")
    for k, ((nm, desc), v) in enumerate(zip(FIELDS, vals)):
        print(f"  +0x{4*k:02x}   0x{v:08x}  {nm:4s} {desc}")

    r31, r0, r1, r2, r3, r4, r29, ctr, mag = vals

    print()
    if mag != MAGIC:
        print(f"  magic = 0x{mag:08x} != 0x{MAGIC:08x}  -> NOT a v5 export "
              f"(wrong image, or the cave never ran)")
        if ctr:
            print(f"  (counter reads {ctr}; if this is a Doc-239 v4 dump the layout differs)")
        return
    print(f"  magic  = 0x{mag:08x}  -> v5 export confirmed")

    if ctr == 0:
        print("  counter = 0  -> the cave NEVER ran: FUN_c02fda90 was not called "
              "this power cycle")
        return

    print(f"  counter = {ctr}  -> FUN_c02fda90 was called {ctr}x since power-on")
    print(f"  caller  = {classify_caller(r31)}   [r31 = 0x{r31:08x}]")
    msg = {1: "0x408020d", 0: "0x4070210"}.get(r2, "?")
    print(f"  message = {msg}   [r2 = {r2}]")
    print(f"  inst    = 0x{r0:08x}   caller frame = 0x{r29:08x}")

    # unexpected non-zero words in the first 0x100 bytes
    extra = [(o, read_word(d, ph, phys, o)) for o in range(0, 0x100, 4)
             if o not in [4 * k for k in range(len(FIELDS))]]
    extra = [(o, v) for o, v in extra if v]
    if extra:
        print("  NOTE: unexpected non-zero words in the save page:")
        for o, v in extra:
            print(f"    +0x{o:03x} = 0x{v:08x}")


if __name__ == "__main__":
    main()

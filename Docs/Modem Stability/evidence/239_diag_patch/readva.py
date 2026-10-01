#!/usr/bin/env python3
"""Read N words at a modem VA from a modem coredump.

Coredump addressing: p_paddr == p_vaddr == modem PHYSICAL, phys = modem_va - 0x39800000.

Usage:  python3 readva.py <coredump.elf> <modem_va> [n_words]
"""
import struct, sys

BIAS = 0x39800000


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    path = sys.argv[1]
    va = int(sys.argv[2], 0)
    n = int(sys.argv[3], 0) if len(sys.argv) > 3 else 16
    phys = va - BIAS

    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    ph = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        t, off, v, pa, fsz, msz, flg = struct.unpack_from("<7I", d, o)
        ph.append((i, t, off, v, pa, fsz, msz))

    print(f"dump {path}  VA 0x{va:08x} -> phys 0x{phys:08x}  ({n} words)")
    for k in range(n):
        a = va + 4 * k
        pa_ = phys + 4 * k
        seg = None
        for (i, t, off, v, pa, fsz, msz) in ph:
            if t == 1 and pa <= pa_ < pa + fsz:
                seg = (i, off + (pa_ - pa))
                break
        if seg is None:
            note = ""
            for (i, t, off, v, pa, fsz, msz) in ph:
                if t == 1 and pa <= pa_ < pa + msz:
                    note = f"  [in phdr {i} but p_filesz={fsz:#x} -> NOT dumped]"
                    break
            print(f"  +0x{4*k:03x}  0x{a:08x}  <not dumped>{note}")
            continue
        i, fo = seg
        val = struct.unpack_from("<I", d, fo)[0]
        print(f"  +0x{4*k:03x}  0x{a:08x}  0x{val:08x}   (phdr {i})")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Find every reference (little-endian u32) to a modem VA inside a coredump, and
map each file offset back to a modem VA.  Layout-free: no assumption about the
timer pool structure.

Usage: python3 find_refs.py <coredump.elf> <va> [va2 ...]
"""
import struct, sys

BIAS = 0x39800000


def load(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_phentsize = struct.unpack_from("<H", d, 42)[0]
    e_phnum = struct.unpack_from("<H", d, 44)[0]
    ph = [struct.unpack_from("<7I", d, e_phoff + i * e_phentsize) for i in range(e_phnum)]
    return d, ph


def off_to_va(ph, fo):
    for (t, off, v, pa, fsz, msz, flg) in ph:
        if t == 1 and off <= fo < off + fsz:
            return pa + (fo - off) + BIAS, (fo - off) % 4
    return None, None


def main():
    path = sys.argv[1]
    vas = [int(x, 0) for x in sys.argv[2:]]
    d, ph = load(path)
    name = path.split("/")[-1]
    for va in vas:
        pat = struct.pack("<I", va)
        hits = []
        i = 0
        while True:
            j = d.find(pat, i)
            if j < 0:
                break
            hits.append(j)
            i = j + 1
        print(f"{name}: VA 0x{va:08x} -> {len(hits)} raw occurrence(s)")
        for fo in hits:
            mva, al = off_to_va(ph, fo)
            tag = "ALIGNED  " if al == 0 else f"off+{al}   "
            print(f"    file+0x{fo:08x}  modem_va 0x{mva:08x}  {tag}")


if __name__ == "__main__":
    main()

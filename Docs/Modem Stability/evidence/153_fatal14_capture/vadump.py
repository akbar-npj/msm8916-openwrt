#!/usr/bin/env python3
"""Dump a region of a modem coredump by ELF VA (elf = dump_va + 0x39800000).

usage: vadump.py <coredump> <elf_va_hex> <len_hex> [--words]
"""
import struct
import sys

ELF_BIAS = 0x39800000


def segs_of(d):
    e_phoff, = struct.unpack_from("<I", d, 0x1C)
    e_phentsize, e_phnum = struct.unpack_from("<HH", d, 0x2A)
    out = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz = \
            struct.unpack_from("<IIIIII", d, off)
        if p_type == 1:
            out.append((p_vaddr, p_offset, p_filesz))
    return out


def read_elf_va(d, segs, elf_va, ln):
    dump_va = elf_va - ELF_BIAS
    for v, o, f in segs:
        if v <= dump_va < v + f:
            start = o + (dump_va - v)
            if dump_va + ln > v + f:
                ln = v + f - dump_va
            return d[start:start + ln], v, dump_va
    return None, None, dump_va


def main():
    path = sys.argv[1]
    elf_va = int(sys.argv[2], 0)
    ln = int(sys.argv[3], 0)
    d = open(path, "rb").read()
    segs = segs_of(d)
    buf, seg_v, dump_va = read_elf_va(d, segs, elf_va, ln)
    if buf is None:
        print("ELF VA 0x%08x (dump 0x%08x) is not inside any PT_LOAD" % (elf_va, dump_va))
        return
    print("file=%s  elf_va=0x%08x dump_va=0x%08x seg=0x%08x len=0x%x" %
          (path, elf_va, dump_va, seg_v, len(buf)))
    for i in range(0, len(buf), 16):
        chunk = buf[i:i + 16]
        hexs = " ".join("%02x" % b for b in chunk)
        asc = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
        print("  0x%08x  %-47s  %s" % (elf_va + i, hexs, asc))


if __name__ == "__main__":
    main()

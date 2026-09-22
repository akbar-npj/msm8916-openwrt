#!/usr/bin/env python3
"""Prove which build of a deployed artifact is actually live on the device.

WHY THIS EXISTS
---------------
`md5sum` on a deployed binary is NOT a deployment check.  OpenWrt strips
section headers on the way into the image, so the installed file can never
match the build-tree file byte-for-byte even when the CODE is identical.
Doc 187 hit this: `/usr/sbin/ModemManager` on the device (`8182d40d...`) matched
neither build artifact (`aee3e751...` pre-install, `74d0bf88...` .pkgdir), which
looks exactly like "the patch did not deploy".

It had deployed.  The two files differ in 5 bytes out of 2,548,936, and all five
are ELF-header fields that merely DESCRIBE the removed section header table:

    0x28  e_shoff      (offset of the section header table)   0 on the device
    0x3c  e_shnum      (number of section headers)            0 on the device
    0x3e  e_shstrndx   (section name string table index)      0 on the device

WHAT TO COMPARE INSTEAD
-----------------------
Compare the LOADABLE IMAGE, not the file:

* executable / shared objects (ET_EXEC, ET_DYN) -> the PT_LOAD segments.  Those
  are what the loader maps and what actually executes, and they survive
  stripping.
* kernel modules (ET_REL, e.g. *.ko) -> these have **no program headers at
  all**, so a segment comparison silently finds NOTHING and prints an empty
  result.  Fall back to the SHF_ALLOC sections (`.text`, `.rodata`, `.data`,
  ...), which are the module's loadable image.

The script md5s each unit and, when one differs, reports the differing runs
byte-by-byte with file offset and virtual address -- so a real code difference
is distinguishable from ELF-header bookkeeping.

usage: verify_deployed_artifacts.py <device_copy> <build_artifact> [<more>...]
"""
import struct
import sys
import hashlib

PT_LOAD = 1
SHT_NOBITS = 8
SHF_ALLOC = 0x2


def units(path):
    """Return [(label, vaddr, bytes)] for the loadable image of `path`."""
    d = open(path, "rb").read()
    e_phoff, = struct.unpack_from("<Q", d, 0x20)
    e_shoff, = struct.unpack_from("<Q", d, 0x28)
    e_phentsize, e_phnum = struct.unpack_from("<HH", d, 0x36)
    e_shentsize, e_shnum, e_shstrndx = struct.unpack_from("<HHH", d, 0x3A)

    out = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        p_type, p_flags = struct.unpack_from("<II", d, o)
        p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = \
            struct.unpack_from("<QQQQQQ", d, o + 8)
        if p_type == PT_LOAD:
            out.append((f"PT_LOAD flags={p_flags:#x}",
                        p_vaddr, d[p_offset:p_offset + p_filesz]))
    if out:
        return out, "PT_LOAD segments"

    # ET_REL (kernel module): no program headers -> use allocatable sections.
    shstr = e_shoff + e_shstrndx * e_shentsize
    shstr_off, = struct.unpack_from("<Q", d, shstr + 0x18)
    for i in range(e_shnum):
        o = e_shoff + i * e_shentsize
        sh_name, sh_type, sh_flags = struct.unpack_from("<III", d, o)
        sh_addr, sh_offset, sh_size = struct.unpack_from("<QQQ", d, o + 0x10)
        if not (sh_flags & SHF_ALLOC) or sh_type == SHT_NOBITS:
            continue
        end = d.index(b"\0", shstr_off + sh_name)
        name = d[shstr_off + sh_name:end].decode("ascii", "replace")
        out.append((f"section {name}", sh_addr,
                    d[sh_offset:sh_offset + sh_size]))
    return out, "SHF_ALLOC sections (ET_REL: no program headers)"


def compare(a_path, b_path):
    a, how_a = units(a_path)
    b, how_b = units(b_path)
    print(f"\n=== {a_path}\n    vs {b_path}\n    ({how_a})")
    if not a:
        print("  *** NOTHING TO COMPARE -- refusing to report a result ***")
        return
    if len(a) != len(b):
        print(f"  unit count differs: {len(a)} vs {len(b)}")
        return
    for (la, va, da), (lb, vb, db) in zip(a, b):
        if la != lb or va != vb:
            print(f"  UNIT MISMATCH: {la}@{va:#x} vs {lb}@{vb:#x}")
            continue
        if da == db:
            print(f"  {la:28s} vaddr={va:#x} size={len(da):#x} "
                  f"md5={hashlib.md5(da).hexdigest()[:16]} IDENTICAL")
            continue
        n = min(len(da), len(db))
        diffs = [i for i in range(n) if da[i] != db[i]]
        print(f"  {la:28s} vaddr={va:#x} size={len(da):#x} "
              f"md5={hashlib.md5(da).hexdigest()[:16]} DIFFERS")
        print(f"    differing bytes: {len(diffs)} / {n} "
              f"({100.0 * len(diffs) / n:.4f} %)")
        runs = []
        if diffs:
            start = prev = diffs[0]
            for i in diffs[1:]:
                if i == prev + 1:
                    prev = i
                else:
                    runs.append((start, prev))
                    start = prev = i
            runs.append((start, prev))
        for s, e in runs[:20]:
            v = va + s
            tag = ""
            if v in (0x400028, 0x40003c, 0x40003e):
                tag = "   <-- ELF header: section-header table descriptor"
            print(f"    off 0x{s:06x}..0x{e:06x} ({e - s + 1} B)  "
                  f"vaddr 0x{v:08x}{tag}")
            print(f"      A: {da[s:s + 16].hex()}")
            print(f"      B: {db[s:s + 16].hex()}")
        if len(runs) > 20:
            print(f"    ... and {len(runs) - 20} more runs")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    for p in sys.argv[2:]:
        compare(sys.argv[1], p)
    return 0


if __name__ == "__main__":
    sys.exit(main())

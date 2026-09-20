#!/usr/bin/env python3
"""Scan a modem coredump for ERR_FATAL descriptors.

Mechanism (from reference_err_fatal_file_line_decode.md):
  FUN_c0879150(descriptor) -> ERR_FATAL
  formatter at 0xc08794e0 reads:
     line     = zxth(memw(r18 + 0x00))      # u16 at +0x00
     filename = (char *)(r18 + 0x14)        # inline NUL-terminated at +0x14
  i.e. descriptor layout:
     +0x00  u16 line
     +0x02  ...unknown...
     +0x14  "file.c\0"

Goal: find descriptors whose inline filename is lte_ml1_common_timer.c and read the
line field. If the u16 at -0x14 is 0x0186 (390), the assert site is located.
"""
import struct
import sys

PATH = sys.argv[1]
TARGETS = [b"lte_ml1_common_timer.c", b"a2_power.c", b"lte_ml1_sleepmgr_stm.c",
           b"mmoc.c", b"coex_interface.c"]

data = open(PATH, "rb").read()
print("file size: %d bytes" % len(data))

# --- parse ELF32 program headers to map file offset -> vaddr ---
if data[:4] != b"\x7fELF":
    print("not an ELF"); sys.exit(1)
e_phoff, = struct.unpack_from("<I", data, 0x1C)
e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x2A)
print("e_phoff=0x%x  phnum=%d  phentsize=%d" % (e_phoff, e_phnum, e_phentsize))

segs = []  # (vaddr, filesz, offset)
for i in range(e_phnum):
    off = e_phoff + i * e_phentsize
    p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz = struct.unpack_from("<IIIIII", data, off)
    if p_type == 1:  # PT_LOAD
        segs.append((p_vaddr, p_filesz, p_offset))
segs.sort(key=lambda s: s[2])
print("PT_LOAD segments: %d" % len(segs))
for v, f, o in segs[:6]:
    print("   vaddr=0x%08x filesz=0x%08x off=0x%08x" % (v, f, o))
if len(segs) > 6:
    print("   ... (%d more)" % (len(segs) - 6))


def off2va(o):
    for v, f, so in segs:
        if so <= o < so + f:
            return v + (o - so)
    return None


def va2off(a):
    for v, f, so in segs:
        if v <= a < v + f:
            return so + (a - v)
    return None


for tgt in TARGETS:
    print("\n" + "=" * 72)
    print("TARGET %s" % tgt.decode())
    print("=" * 72)
    pos, hits = 0, []
    while True:
        i = data.find(tgt, pos)
        if i < 0:
            break
        hits.append(i)
        pos = i + 1
    print("occurrences: %d" % len(hits))
    for i in hits[:40]:
        va = off2va(i)
        # descriptor hypothesis: filename at +0x14, line u16 at +0x00
        back = i - 0x14
        line = None
        if back >= 0:
            line, = struct.unpack_from("<H", data, back)
        pre = data[max(0, i - 24):i]
        prehex = " ".join("%02x" % b for b in pre)
        print("  off=0x%08x va=%s  line@-0x14=%-6s (0x%04x)  pre=%s"
              % (i, ("0x%08x" % va) if va else "n/a", line, line or 0, prehex))

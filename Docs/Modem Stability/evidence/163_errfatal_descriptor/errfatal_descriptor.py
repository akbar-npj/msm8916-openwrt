#!/usr/bin/env python3
"""Decode the ERR_FATAL descriptor from a modem coredump, including its FILENAME.

WHAT THIS PROVES
----------------
The modem writes a single "last fatal" record into its BSS before it dies.  That
record is what the ERR_FATAL formatter turns into the dmesg line

    qcom-q6v5-mss 4080000.remoteproc: fatal error received: <file>:<line>:

so reading it back out of a coredump lets us check the dmesg line against the
firmware's own bytes -- i.e. decide whether the reported `file:line` is the
*current* fatal's assert site or a stale/obfuscated global.

RECORD LAYOUT  (ELF VA 0xC35B1280;  dump_va = elf_va - 0x39800000)

    +0x00  u32   3
    +0x04  u32   1
    +0x08  u32   ptr            per-fatal, varies (devcd2 0xC3C0BBA4, devcd3 0xC3C0BEBC)
    +0x0C  u32   3
    +0x10  u16   LINE           <-- the line number in the dmesg message
    +0x14  u32   A              runtime-varying
    +0x18  u32   B              runtime-varying, advances +11 per fatal period
    +0x1C  u32   0
    +0x20  u32   0
    +0x24  char  FILENAME[]     NUL-terminated, INLINE -- not behind a pointer

The filename being inline at +0x24 is the part the corpus previously recorded as
"obfuscated" / "no filename table".  It is plaintext; it is just not where the
earlier reader looked (it followed the +0x08 pointer instead, which is unrelated).

usage: errfatal_descriptor.py <dump.elf> [<dump.elf> ...]
       (defaults to modem_coredump_up*.elf in the current directory)
"""
import glob
import os
import re
import struct
import sys

ELF_BIAS = 0x39800000
ERRFATAL_ELF_VA = 0xC35B1280
RPM_LPR_COUNTER_ELF_VA = 0xC1D47410
FILENAME_OFF = 0x24
FILENAME_MAX = 64


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
            return d[start:start + ln]
    return None


def uptime_of(path):
    m = re.search(r"up(\d+\.\d+)", os.path.basename(path))
    return float(m.group(1)) if m else float("nan")


def decode(path):
    d = open(path, "rb").read()
    segs = segs_of(d)
    rec = read_elf_va(d, segs, ERRFATAL_ELF_VA, 0x20 + FILENAME_MAX)
    cnt = read_elf_va(d, segs, RPM_LPR_COUNTER_ELF_VA, 4)
    if rec is None:
        return None
    line, = struct.unpack_from("<H", rec, 0x10)
    a, = struct.unpack_from("<I", rec, 0x14)
    b, = struct.unpack_from("<I", rec, 0x18)
    ptr, = struct.unpack_from("<I", rec, 0x08)
    raw = rec[FILENAME_OFF:FILENAME_OFF + FILENAME_MAX]
    name = raw.split(b"\0")[0].decode("ascii", "replace")
    counter = struct.unpack_from("<I", cnt, 0)[0] if cnt else -1
    return dict(uptime=uptime_of(path), file=name, line=line, a=a, b=b,
                ptr=ptr, counter=counter, name=os.path.basename(path))


def main():
    paths = sys.argv[1:] or sorted(glob.glob("modem_coredump_up*.elf"))
    rows = [r for r in (decode(p) for p in paths) if r]
    if not rows:
        print("no decodable dumps")
        return
    rows.sort(key=lambda r: r["uptime"])
    print("%-10s  %-26s %-6s %-10s %-10s %-7s" %
          ("AP uptime", "file", "line", "A", "B", "lpr ctr"))
    print("-" * 78)
    prev = None
    for r in rows:
        print("%-10.2f  %-26s %-6d 0x%08x 0x%08x %-7d" %
              (r["uptime"], r["file"], r["line"], r["a"], r["b"], r["counter"]))
        if prev is not None:
            print("%-10s  %-26s %-6s dB=%+d" %
                  ("", "  ^ dU=%.2fs" % (r["uptime"] - prev["uptime"]), "",
                   (r["b"] - prev["b"]) & 0xFFFFFFFF if r["b"] >= prev["b"]
                   else (r["b"] - prev["b"])))
        prev = r


if __name__ == "__main__":
    main()

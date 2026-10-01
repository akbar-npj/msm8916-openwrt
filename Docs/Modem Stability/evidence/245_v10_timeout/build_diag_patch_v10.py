#!/usr/bin/env python3
"""Build the HMU05 diagnostic modem image v10: v9 + a STATE-20 WATCHDOG TIMEOUT EXTENSION.

WHY v9 (the v8 validation capture, ledger item 83 s3).  v8's shared ring was
read on-device and it WORKED (magic 0xc1455000, count 2804) -- but it exposed a
design error:

  * FUN_c02d7bd0 is a GENERIC state dispatcher, NOT an ML1-timer-only callback.
    Over a 93 s idle boot its 2804 calls carried states **0,1,3,12,13,14,15**
    (histogram 0:36 1:12 3:55 12:3 13:6 14:12 15:4) -- **none** in 20..28.
  * the CALLBACK rate is therefore ~30 Hz, not the 12 Hz the v4 export implied,
    so the 128-slot ring held only ~4.3 s and **no ARM entry survived**.

v9 keeps v8's structure and adds ONE thing: the CALLBACK cave records ONLY when
the dispatch index (the state byte at ctx+0x38) is in **20..28**, i.e. one of the
nine ML1 timer ctxs.  The recorded `b` field is then `state-20` (0..8).  Two
wrap-proof counters disambiguate:

  +0x08 arm_total   every call to FUN_c02fda90 (the armer)
  +0x0c cb_total    every entry to FUN_c02d7bd0, FILTERED OR NOT

so `cb_total - (ring CB entries)` quantifies the filter's selectivity, and the
ring itself becomes an ~171 s ARM history with the rare ML1 callbacks inline.

Everything else (patches, save area, cave VAs, entry layout) is v8's.

PATCHES
  modem.b16 @ 0xc0326874 : `call 0xc02fda90` -> `call 0xc003054c`   (ARM site A)
  modem.b16 @ 0xc033c0f4 : `call 0xc02fda90` -> `call 0xc003054c`   (ARM site B)
  modem.b16 @ 0xc02d7bd0 : `call 0xc02d1140` -> `call 0xc0030590`   (CALLBACK entry)
  modem.b05 @ 0xc003054c (ARM cave, 76 B) / 0xc0030590 (CB cave, 100 B)

SAVE AREA 0xc1455000:
  +0x00 magic = 0xc1455000
  +0x04 count      (shared; ring slot = (count-1)&0x7f)
  +0x08 arm_total
  +0x0c cb_total
  +0x40 ring[128] of 16 B
    +0x00 seq   ARM: seq           CB: seq | 0x80000000
    +0x04 a     ARM: caller r31    CB: ctx (r0)
    +0x08 b     ARM: r2 selector   CB: state-20 (0..8)
    +0x0c c     ARM: STALE (not written)  CB: ctx+0x20 expiry low word
"""
import os, sys, shutil, hashlib, subprocess, re

BASE = "GitIgnore/compare/modem_hmu05_extracted/image"
OUT  = "scratch/diag_patch_v10/image_patched"
VA_B16 = 0xc0287000
VA_B05 = 0xc0030000
HASH_HDR = 0x28

SAVE_AREA   = 0xc1455000
FUN         = 0xc02fda90
ARM_CAVE_VA = 0xc003054c
CB_CAVE_VA  = 0xc0030598
CAVE_LIMIT  = 0xc0030600

RING_N    = 128
RING_MASK = RING_N - 1
HDR_BYTES = 0x40

SITES = [
    ("A", 0xc0326874, bytes.fromhex("0e79fa5b")),
    ("B", 0xc033c0f4, bytes.fromhex("ceccf85b")),
]
CB_SITE = ("CB", 0xc02d7bd0, bytes.fromhex("b84aff5b"))

# v10: the state-20 watchdog timeout.  FUN_c02fbba4's 0x80 branch does
#   { call 0xc02a54b0 ; r0 = add(r16,#0x328) ; r1 = memh(r16+#0x568) }
# i.e. arm the ctx0 timer with the instance's 50 ms timeout.  Replace the
# timeout READ with a constant so the deadline becomes 5000 ms.
#   0x9350d681 = `r1 = memh(r16+#0x568)`  ->  0x7809f101 = `r1 = #0x1388`
# (verified: the same encoding in every packet slot; parse bits 01 in both)
TIMEOUT_SITE = ("TO", 0xc02fbc40, bytes.fromhex("81d65093"))
TIMEOUT_NEW  = bytes.fromhex("01f10978")

ARM_CAVE_ASM = f""".text
{{ r5 = ##0x{SAVE_AREA:x} }}
{{ memw(r5+#0x00) = r5 }}
{{ r7 = memw(r5+#0x04) }}
{{ r7 = add(r7,#1) }}
{{ memw(r5+#0x04) = r7 }}
{{ r8 = memw(r5+#0x08) }}
{{ r8 = add(r8,#1) }}
{{ memw(r5+#0x08) = r8 }}
{{ r6 = and(r7,#0x{RING_MASK:x}) }}
{{ r6 = asl(r6,#0x4) }}
{{ r6 = add(r6,r5) }}
{{ r6 = add(r6,#0x{HDR_BYTES:x}) }}
{{ memw(r6+#0x00) = r7 }}
{{ memw(r6+#0x04) = r31 }}
{{ memw(r6+#0x08) = r2 }}
{{ r6 = ##0x{FUN:x} }}
{{ jumpr r6 }}
"""

CB_CAVE_ASM = f""".text
{{ r5 = ##0x{SAVE_AREA:x} }}
{{ memw(r5+#0x00) = r5 }}
{{ r7 = memw(r5+#0x04) }}
{{ r7 = add(r7,#1) }}
{{ memw(r5+#0x04) = r7 }}
{{ r8 = memw(r5+#0x0c) }}
{{ r8 = add(r8,#1) }}
{{ memw(r5+#0x0c) = r8 }}
{{ r1 = memub(r0+#0x38) }}
{{ r1 = add(r1,#-0x14) }}
{{ p0 = cmp.gtu(r1,#0x8) }}
{{ if (p0) jump:nt skiprec }}
{{ r6 = and(r7,#0x{RING_MASK:x}) }}
{{ r6 = asl(r6,#0x4) }}
{{ r6 = add(r6,r5) }}
{{ r6 = add(r6,#0x{HDR_BYTES:x}) }}
{{ r8 = setbit(r7,#31) }}
{{ memw(r6+#0x00) = r8 }}
{{ memw(r6+#0x04) = r0 }}
{{ memw(r6+#0x08) = r1 }}
{{ r8 = memw(r0+#0x20) }}
{{ memw(r6+#0x0c) = r8 }}
skiprec:
{{ r0 = #0x0 }}
{{ jumpr r31 }}
"""


def assemble(src):
    """Assemble via an OBJECT FILE, not --show-encoding.

    `--show-encoding` renders an unresolved PC-relative fixup as a placeholder
    (`[A,0xc0'A',A,0x5c'A']`), and a naive hex-scrape silently DROPS the whole
    instruction -- v9's first build lost its `if (p0) jump` that way and the
    state filter became a no-op.  Going through `-filetype=obj` + objcopy
    resolves the fixup.
    """
    import tempfile
    d = tempfile.mkdtemp()
    s, o, b = os.path.join(d, "c.s"), os.path.join(d, "c.o"), os.path.join(d, "c.bin")
    open(s, "w").write(src)
    r = subprocess.run(["llvm-mc", "-triple=hexagon", "-mcpu=hexagonv60",
                        "-filetype=obj", "-o", o, s], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("llvm-mc failed:\n" + r.stderr)
    r = subprocess.run(["llvm-objcopy", "-O", "binary", "--only-section=.text", o, b],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("llvm-objcopy failed:\n" + r.stderr)
    data = open(b, "rb").read()
    shutil.rmtree(d)
    return data


def call_bytes(old4, target, pc):
    w = int.from_bytes(old4, "little")
    field = ((target - pc) // 2) & 0x3FFFFF
    w = (w & 0xFF00C000) | (((field >> 14) & 0xFF) << 16) | (field & 0x3FFF)
    return w.to_bytes(4, "little")


def sha256f(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(65536), b""):
            h.update(c)
    return h.digest()


def main():
    arm_cave = assemble(ARM_CAVE_ASM)
    cb_cave = assemble(CB_CAVE_ASM)
    arm_end = ARM_CAVE_VA + len(arm_cave)
    cb_end = CB_CAVE_VA + len(cb_cave)
    print(f"[+] ARM cave: {len(arm_cave)} B  VA 0x{ARM_CAVE_VA:x}..0x{arm_end:x}")
    print(f"    {arm_cave.hex()}")
    print(f"[+] CB  cave: {len(cb_cave)} B  VA 0x{CB_CAVE_VA:x}..0x{cb_end:x}")
    print(f"    {cb_cave.hex()}")
    # a dropped instruction (see assemble()) must be impossible to miss
    assert len(arm_cave) == 76, f"ARM cave size {len(arm_cave)} != 76"
    assert len(cb_cave) == 100, f"CB cave size {len(cb_cave)} != 100"
    assert arm_end <= CB_CAVE_VA, f"ARM cave overruns the CB cave: ends 0x{arm_end:x}"
    assert cb_end <= CAVE_LIMIT, f"CB cave overruns the nop run: ends 0x{cb_end:x}"
    ring_end = SAVE_AREA + HDR_BYTES + RING_N * 16
    print(f"[+] ring: {RING_N} entries, save 0x{SAVE_AREA:x}..0x{ring_end:x} "
          f"(poison guard at +0xd58 -> margin 0x{SAVE_AREA + 0xd58 - ring_end:x})")
    assert ring_end <= SAVE_AREA + 0xd58, "ring overlaps the poison guard"

    os.makedirs(OUT, exist_ok=True)
    for fn in os.listdir(BASE):
        s = os.path.join(BASE, fn)
        if os.path.isfile(s):
            shutil.copy2(s, os.path.join(OUT, fn))
    print(f"[+] pristine image copied -> {OUT}")

    p16 = os.path.join(OUT, "modem.b16")
    b16 = bytearray(open(p16, "rb").read())
    for name, va, old in SITES:
        off = va - VA_B16
        assert b16[off:off+4] == old, f"site {name} mismatch: {b16[off:off+4].hex()} != {old.hex()}"
        new = call_bytes(old, ARM_CAVE_VA, va)
        b16[off:off+4] = new
        print(f"[+] b16 ARM site {name} @0x{off:x} (VA 0x{va:08x}): {old.hex()} -> {new.hex()}")
    name, va, old = CB_SITE
    off = va - VA_B16
    assert b16[off:off+4] == old, f"CB site mismatch: {b16[off:off+4].hex()} != {old.hex()}"
    new = call_bytes(old, CB_CAVE_VA, va)
    b16[off:off+4] = new
    print(f"[+] b16 CB  site {name} @0x{off:x} (VA 0x{va:08x}): {old.hex()} -> {new.hex()}")
    name, va, old = TIMEOUT_SITE
    off = va - VA_B16
    assert b16[off:off+4] == old, f"timeout site mismatch: {b16[off:off+4].hex()} != {old.hex()}"
    b16[off:off+4] = TIMEOUT_NEW
    print(f"[+] b16 TIMEOUT site {name} @0x{off:x} (VA 0x{va:08x}): {old.hex()} -> {TIMEOUT_NEW.hex()}  (50 ms -> 5000 ms)")
    open(p16, "wb").write(b16)

    p05 = os.path.join(OUT, "modem.b05")
    b05 = bytearray(open(p05, "rb").read())
    for cname, cva, cave in (("ARM", ARM_CAVE_VA, arm_cave), ("CB", CB_CAVE_VA, cb_cave)):
        off05 = cva - VA_B05
        assert b05[off05:off05 + len(cave)] == b"\x00\xc0\x00\x7f" * (len(cave) // 4), \
            f"b05 {cname} cave site not nop: {b05[off05:off05+32].hex()}"
        b05[off05:off05 + len(cave)] = cave
        print(f"[+] b05 {cname} cave @0x{off05:x} (VA 0x{cva:08x}): wrote {len(cave)}B")
    assert len(b05) == 81920, f"b05 size changed: {len(b05)}"
    open(p05, "wb").write(b05)

    b00 = open(os.path.join(OUT, "modem.b00"), "rb").read()
    b01 = bytearray(open(os.path.join(OUT, "modem.b01"), "rb").read())
    for seg, path in ((16, p16), (5, p05)):
        o = HASH_HDR + seg * 32
        old = b01[o:o+32]; new = sha256f(path)
        b01[o:o+32] = new
        print(f"[+] b01 seg{seg} @0x{o:x}: {old[:6].hex()}.. -> {new[:6].hex()}..")
    open(os.path.join(OUT, "modem.b01"), "wb").write(b01)
    open(os.path.join(OUT, "modem.mdt"), "wb").write(b00 + bytes(b01))
    print(f"[+] rebuilt modem.mdt ({len(b00)+len(b01)} B)")

    ok = True
    for seg, path in ((16, p16), (5, p05)):
        o = HASH_HDR + seg * 32
        if b01[o:o+32] != sha256f(path):
            ok = False; print(f"[!] seg{seg} hash MISMATCH")
    print(f"[+] hash re-verify: {'PASS' if ok else 'FAIL'}")
    for seg, name in ((16, "modem.b16"), (5, "modem.b05")):
        a = open(os.path.join(BASE, name), "rb").read()
        b = open(os.path.join(OUT, name), "rb").read()
        diff = [i for i in range(min(len(a), len(b))) if a[i] != b[i]]
        print(f"[+] {name}: {len(diff)} differing bytes")
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()

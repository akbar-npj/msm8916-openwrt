#!/usr/bin/env python3
"""Build the HMU05 diagnostic modem image v7: a RING-BUFFER HISTORY of FUN_c02fda90.

WHY A RING AND NOT ANOTHER SNAPSHOT (the Redundancy Rule, ledger item 63.4 / 57.4):
  A coredump already contains every byte of modem RAM at the fatal, so an
  instrument that merely SNAPSHOTS state at the fatal adds nothing the dump does
  not already contain.  The v4/v5/v6 exports were legitimate (1)-class results --
  they captured a TRANSIENT the dump cannot attribute (a caller's r31 at a call
  boundary) -- but a single slot keeps only the LAST call.  v6 proved that is
  misleading: the cave counter showed FUN_c02fda90 ran only 4x in the whole
  900.6 s traffic window (ledger 63.5), i.e. the single slot watched a dormant
  path and could not show the SEQUENCE.

  A firmware instrument is justified ONLY by (1) a transient (EXHAUSTED),
  (2) HISTORY (a ring/trace over time), or (3) an actual fix.  This is (2).

WHAT IT RECORDS
  FUN_c02fda90 is the primitive that SENDS an ML1 cell-measurement request and
  ARMS the 50 ms "state 20" watchdog -- the watchdog whose expiry is fatal by
  construction (Doc 239 / ledger item 60).  It has exactly TWO callers:
      A  0xc0326874   inside FUN_c032685c   (the ML1 measurement/mobility evaluator)
      B  0xc033c0f4   inside 0xc033c0a4     (LTE_ML1_SM_ACQ_STM CELL_MEAS activity)
  v7 records, for each of the LAST 128 calls, { seq, caller r31, selector r2,
  instance r0 } -- the caller/selector SEQUENCE, which no single slot and no F3
  log line can show.

PATCHES
  modem.b16 @ 0xc0326874 : `call 0xc02fda90` -> `call 0xc0030560`   (site A)
  modem.b16 @ 0xc033c0f4 : `call 0xc02fda90` -> `call 0xc0030560`   (site B)
  modem.b05 @ 0xc0030560 : 80-byte ring cave (dead nop run 0xc003054c..0xc0030600)

CAVE (llvm-mc, hexagonv60).  Entered by `call <cave>`, so r31 = the caller's
return address; it tail-jumps (`jumpr`, r31 untouched) into FUN_c02fda90, so the
target sees EXACTLY the register state a direct call would have produced.  The
instrumentation is semantically transparent.

  RING HEADER (16 B at SAVE_AREA+0x00):
    +0x00  u32 magic  = 0xc0030560 (the cave's own VA) -- self-identifies v7
    +0x04  u32 count  = total calls seen (monotonic; the ring index is count-1)
    +0x08  u32 idx    = (count-1) & 127   (debug)
    +0x0c  u32 pad
  ENTRY i (16 B at SAVE_AREA+0x10 + i*16), i = count & 127:
    +0x00  u32 seq        = the monotonic call number
    +0x04  u32 caller_r31 = 0xc032687c (A) | 0xc033c0f8 (B)  -> WHICH caller
    +0x08  u32 r2         = 0 or 1     -> message 0x4070210 | 0x408020d
    +0x0c  u32 r0         = the instance argument

  Scratch: r5 (base), r6 (entry ptr / target), r7 (counter/magic) -- all
  CALLER-saved, NOT gp(r28), fp(r29), sp(r30), lr(r31), and NOT in the
  callee-saved set r16..r27.  r0..r4 (the target's arguments) are never written.

SAVE AREA 0xc1455000 (phys 0x87c55000).  Chosen by the SOUND criterion of
  ledger 57.5, over 10 complete coredumps (idle/traffic/cold/warm/post-SSR) --
  see find_ring_area.py:
    * phdr17 (VA 0xc1440000, filesz 0xa1fe0, flags 0x8000006 = R+W, LOADED)
      -> the SAME segment as the v4/v5/v6-proven save area; physical memory exists.
    * the page holds 546 words that VARY across dumps -> the modem WRITES the
      page -> it is MAPPED and WRITABLE (the proof v1/v3 lacked).
    * the PIL poison guard 0xdeadc0fe is present at +0xd58..+0xd7c -> the loader
      touched the page.
    * the run [+0x000,+0xd58) = 3416 B is BYTE-IDENTICAL (all zero) in EVERY dump
      and all-zero in the stock image -> no live writer touches it, in ANY regime.
  The 2064-byte ring fits with 1352 B of margin before the poison guard.

BUILD / RE-SIGN: modem.b16 offset = VA - 0xc0287000 ; modem.b05 offset = VA - 0xc0030000 ;
  segments 16 & 5 are re-SHA256'd into modem.b01 at HASH_HDR(0x28)+seg*32 ;
  modem.mdt = modem.b00 + modem.b01.
"""
import os, sys, shutil, hashlib, subprocess, re

BASE = "GitIgnore/compare/modem_hmu05_extracted/image"
OUT  = "scratch/diag_patch_v7/image_patched"
VA_B16 = 0xc0287000
VA_B05 = 0xc0030000
HASH_HDR = 0x28

CAVE_VA   = 0xc0030560
SAVE_AREA = 0xc1455000
FUN       = 0xc02fda90
RING_N    = 128                       # entries
RING_MASK = RING_N - 1
HDR_BYTES = 0x10

SITES = [
    ("A", 0xc0326874, bytes.fromhex("0e79fa5b")),   # inside FUN_c032685c
    ("B", 0xc033c0f4, bytes.fromhex("ceccf85b")),   # inside 0xc033c0a4 (ACQ CELL_MEAS)
]

CAVE_ASM = f""".text
.org 0x1000
{{ r5 = ##0x{SAVE_AREA:x} }}
{{ r7 = ##0x{CAVE_VA:x} }}
{{ memw(r5+#0x00) = r7 }}
{{ r7 = memw(r5+#0x04) }}
{{ r7 = add(r7,#1) }}
{{ memw(r5+#0x04) = r7 }}
{{ r6 = and(r7,#0x{RING_MASK:x}) }}
{{ memw(r5+#0x08) = r6 }}
{{ r6 = asl(r6,#0x4) }}
{{ r6 = add(r6,r5) }}
{{ r6 = add(r6,#0x{HDR_BYTES:x}) }}
{{ memw(r6+#0x00) = r7 }}
{{ memw(r6+#0x04) = r31 }}
{{ memw(r6+#0x08) = r2 }}
{{ memw(r6+#0x0c) = r0 }}
{{ r6 = ##0x{FUN:x} }}
{{ jumpr r6 }}
"""


def assemble(src):
    out = subprocess.run(["llvm-mc", "-triple=hexagon", "-mcpu=hexagonv60", "-show-encoding"],
                         input=src, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("llvm-mc failed:\n" + out.stderr)
    bs = []
    for m in re.finditer(r"encoding: \[([0-9a-fx, ]+)\]", out.stdout):
        bs += [int(x, 16) for x in m.group(1).split(",")]
    return bytes(bs)


def call_bytes(old4, target, pc):
    """Re-encode a `call` (same slot/parse bits) to a new PC-relative target.
    field22 = ((target-PC)/2) 2's-comp; bits[23:16]=field[21:14]; bits[13:0]=field[13:0];
    bits[31:24] and [15:14] kept.  (Validated against the Doc-239 / Doc-240 encodings.)"""
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
    cave = assemble(CAVE_ASM)
    print(f"[+] cave assembled: {len(cave)} bytes")
    print(f"    {cave.hex()}")
    end_va = CAVE_VA + len(cave)
    assert end_va <= 0xc0030600, f"cave overruns the nop run: ends 0x{end_va:x}"
    ring_end = SAVE_AREA + HDR_BYTES + RING_N * 16
    print(f"[+] ring: {RING_N} entries, save 0x{SAVE_AREA:x}..0x{ring_end:x} "
          f"(poison guard at +0xd58 -> margin 0x{SAVE_AREA+0xd58-ring_end:x})")
    assert ring_end <= SAVE_AREA + 0xd58, "ring overlaps the poison guard"

    os.makedirs(OUT, exist_ok=True)
    for fn in os.listdir(BASE):
        s = os.path.join(BASE, fn)
        if os.path.isfile(s):
            shutil.copy2(s, os.path.join(OUT, fn))
    print(f"[+] pristine image copied -> {OUT}")

    # --- patch b16: the two call sites ---
    p16 = os.path.join(OUT, "modem.b16")
    b16 = bytearray(open(p16, "rb").read())
    for name, va, old in SITES:
        off = va - VA_B16
        assert b16[off:off+4] == old, f"site {name} mismatch: {b16[off:off+4].hex()} != {old.hex()}"
        new = call_bytes(old, CAVE_VA, va)
        b16[off:off+4] = new
        print(f"[+] b16 site {name} @0x{off:x} (VA 0x{va:08x}): {old.hex()} -> {new.hex()}")
    open(p16, "wb").write(b16)

    # --- patch b05: the cave ---
    p05 = os.path.join(OUT, "modem.b05")
    b05 = bytearray(open(p05, "rb").read())
    off05 = CAVE_VA - VA_B05
    assert b05[off05:off05+len(cave)] == b"\x00\xc0\x00\x7f" * (len(cave)//4), \
        f"b05 cave site not nop: {b05[off05:off05+32].hex()}"
    b05[off05:off05+len(cave)] = cave
    assert len(b05) == 81920, f"b05 size changed: {len(b05)}"
    open(p05, "wb").write(b05)
    print(f"[+] b05 @0x{off05:x} (VA 0x{CAVE_VA:08x}): wrote {len(cave)}B cave")

    # --- re-hash seg 16 and seg 5 in b01, rebuild mdt ---
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

    # --- verify ---
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
        print(f"[+] {name}: {len(diff)} differing bytes at {[hex(d) for d in diff]}")
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()

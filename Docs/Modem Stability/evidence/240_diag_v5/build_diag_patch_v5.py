#!/usr/bin/env python3
"""Build the HMU05 diagnostic modem image v5: FUN_c02fda90 call-boundary argument export.

WHY THE *CALL BOUNDARY* AND NOT THE ENTRY (the design decision, Doc 240 s3):
  FUN_c02fda90 is the primitive that SENDS an ML1 cell-measurement request and
  ARMS the 50 ms "state 20" watchdog (the watchdog whose expiry is fatal by
  construction).  It has exactly TWO callers (llvm-objdump whole image):
      A  0xc0326874   inside FUN_c032685c   (the ML1 measurement/mobility evaluator)
      B  0xc033c0f4   inside 0xc033c0a4     (LTE_ML1_SM_ACQ_STM CELL_MEAS activity)
  We want to know WHICH caller armed the watchdog at the ~902 s fatal, and which
  message it sent (r2==1 -> 0x408020d ; r2==0 -> 0x4070210).

  The Doc-239 technique (replace the function's ENTRY `call` with `call <cave>`)
  CANNOT answer "which caller": the entry instruction IS a `call`, so it has
  already overwritten r31 (= the caller's return address) before the cave runs.
  Therefore we detour the CALL SITES instead: at a call site, r31 at cave entry
  IS the caller's return address (0xc032687c vs 0xc033c0f8 -> unambiguous).

  Both sites tail-jump to ONE shared cave.  The cave stores r31 + args, then
  tail-jumps (jumpr, r31 untouched) into FUN_c02fda90 -- so FUN_c02fda90 is
  entered with EXACTLY the register state it would have seen from a direct call
  (same r31, same r0..r4).  The instrumentation is semantically transparent.

WHY r2 ALONE IS NOT ENOUGH (Doc 240 s3.2):
  FUN_c032685c passes its own arg4 through as FUN_c02fda90's r2, and FUN_c032685c
  is itself called from 3 sites -- with arg4=1 at 0xc03264f8 but arg4=0 at
  0xc032681c and 0xc03278f4.  So r2==0 is ambiguous (caller A via those two paths,
  or caller B).  r31 (the return address) removes the ambiguity.

PATCHES
  modem.b16 @ 0xc0326874 : `call 0xc02fda90` -> `call 0xc0030560`   (site A)
  modem.b16 @ 0xc033c0f4 : `call 0xc02fda90` -> `call 0xc0030560`   (site B)
  modem.b05 @ 0xc0030560 : 60-byte cave (dead nop run 0xc003054c..0xc0030600)

CAVE (assembled with llvm-mc, hexagonv60; exported to SAVE_AREA 0xc14408f0):
  +0x00 r31   caller's return address  (0xc032687c=A / 0xc033c0f8=B)
  +0x04 r0    inst
  +0x08 r1    arg2  (A: &local_34 stack ptr ; B: r17+2 object ptr)
  +0x0c r2    arg3  ** the message selector ** (1 -> 0x408020d ; 0 -> 0x4070210)
  +0x10 r3    arg4
  +0x14 r4    arg5
  +0x18 r29   caller frame pointer
  +0x1c counter  incremented once per call into FUN_c02fda90
  +0x20 magic = 0xc0030560 (the cave's own VA) -- self-identifies the v5 layout
        (the save area is SHARED with the v4 Doc-239 patch, whose layout differs,
         so a magic makes a mismatched readback impossible to mistake for v5)

  Scratch: r5 (base) and r6 (counter/target) -- both CALLER-saved, NOT gp(r28),
  fp(r29), sp(r30), lr(r31) and NOT in the callee-saved set r16..r27 that the
  target's prolog (stub 0xc0030020) saves.  r0..r4 (the target's arguments) are
  never written.

SAVE_AREA 0xc14408f0: the first page of phdr17 (LOADED writable, filesz 0xa1fe0).
  Chosen (Doc 239) because the modem WRITES it at runtime with CROSS-BOOT-VARYING
  content while the image is all-zero there -- the only sound proof of mapped+
  writable (v1 0xc51bf000 and v3 0xc4546800 both FAULTED: "free-looking in a
  coredump" does NOT imply mapped).  [page+0x1e6, page+0x1000) is all-zero in
  8/8 dumps; we store at +0x8f0 with >0x6d0 bytes of margin.

BUILD / RE-SIGN: modem.b16 offset = VA - 0xc0287000 ; modem.b05 offset = VA - 0xc0030000 ;
  segments 16 & 5 are re-SHA256'd into modem.b01 at HASH_HDR(0x28)+seg*32 ;
  modem.mdt = modem.b00 + modem.b01.
"""
import os, sys, shutil, hashlib, subprocess, re

BASE = "GitIgnore/compare/modem_hmu05_extracted/image"
OUT  = "scratch/diag_patch_v5/image_patched"
VA_B16 = 0xc0287000
VA_B05 = 0xc0030000
HASH_HDR = 0x28

CAVE_VA = 0xc0030560
SAVE_AREA = 0xc14408f0
FUN = 0xc02fda90

# ---- the two call sites (VA, expected old 4 bytes) ----
SITES = [
    ("A", 0xc0326874, bytes.fromhex("0e79fa5b")),   # inside FUN_c032685c
    ("B", 0xc033c0f4, bytes.fromhex("ceccf85b")),   # inside 0xc033c0a4 (ACQ CELL_MEAS)
]

# ---- the cave, assembled with llvm-mc (hexagonv60) ----
CAVE_ASM = f""".text
.org 0x1000
{{ r5 = ##0x{SAVE_AREA:x} }}
{{ memw(r5+#0x00) = r31 }}
{{ memw(r5+#0x04) = r0 }}
{{ memw(r5+#0x08) = r1 }}
{{ memw(r5+#0x0c) = r2 }}
{{ memw(r5+#0x10) = r3 }}
{{ memw(r5+#0x14) = r4 }}
{{ memw(r5+#0x18) = r29 }}
{{ r7 = ##0x{CAVE_VA:x} }}
{{ memw(r5+#0x20) = r7 }}
{{ r6 = memw(r5+#0x1c) }}
{{ r6 = add(r6,#1) }}
{{ memw(r5+#0x1c) = r6 }}
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
    Verified against the two v4 encodings: field22 = ((target-PC)/2) 2's-comp,
    bits[23:16]=field[21:14], bits[13:0]=field[13:0]; bits[31:24] and [15:14] kept."""
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
    assert len(cave) <= 0x600 - CAVE_VA + VA_B05, "cave does not fit the nop run"

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

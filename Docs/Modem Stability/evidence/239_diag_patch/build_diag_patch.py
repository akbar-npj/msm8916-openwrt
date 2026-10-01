#!/usr/bin/env python3
"""Build the HMU05 diagnostic modem image: ML1-callback entry argument export.

Patch 1 (modem.b16): redirect the callback entry call at VA 0xc02d7bd0
  from `call 0xc02d1140` (a `return 0` stub) to `call 0xc003054c` (our cave).
  Encoding: 22-bit two's-complement offset/2 in bits[23:16]+[13:0]; bits[15:14]
  (packet parse) and bits[31:24] preserved.
Patch 2 (modem.b05): write the 44-byte cave at VA 0xc003054c (dead nop padding
  after an unconditional jump; verified no branch targets it).
Cave: store r0(ctx), r1(idx), r31(caller), r29(frame) to SAVE_AREA 0xc14408f0,
  then r0=0 and return  -- i.e. the original stub's semantics, plus recording.
"""
import os, sys, shutil, hashlib, struct

BASE = "GitIgnore/compare/modem_hmu05_extracted/image"
OUT  = "scratch/diag_patch/image_patched"
VA_B16 = 0xc0287000
VA_B05 = 0xc0030000
HASH_HDR = 0x28

# ---- patch 1 ----
ENTRY_VA = 0xc02d7bd0
ENTRY_OLD = bytes.fromhex("b84aff5b")           # call 0xc02d1140
CAVE_VA  = 0xc003054c
ENTRY_NEW = bytes.fromhex("be44ab5b")           # call 0xc003054c
# ---- patch 2 ----
# SAVE_AREA history:
#   0xc51bf000  (v1) -- the tail of ph26's declared BSS, but NOT MAPPED at runtime:
#       the first store faulted (PC=0xc0030554, BADVA=0xc51bf000) and the modem
#       crash-looped.  The declared BSS runs to 0xc5200000 but live data ends at
#       0xc5000000; [0xc5000000,0xc5200000) is all-zero in 49/49 dumps.
#   0xc4d90000  (v2) -- picked as "all-zero", but that control was run on too few
#       dumps; across all 49 it IS written.  Rejected before flashing.
#   0xc4546800  (v3) -- page 0xc4546000 looked used, but its first 16 B are
#       `f8f8f8f8` loader FILL and the page is byte-identical in 6/6 dumps => the
#       modem never wrote it => NOT MAPPED.  Flashed; the first store faulted
#       (PC=0xc0030554, BADVA=0xc4546800) and the modem crash-looped again.
#       **LESSON: "nonzero/free-looking in a coredump" does NOT imply mapped.**
#   0xc14408f0  (v4) -- the criterion that DOES imply mapped+writable: a page the
#       modem itself WRITES.  Evidence for page 0xc1440000:
#         * it is the first page of ph17, a LOADED writable data segment
#           (filesz=0xa1fe0), so PIL loads it -> the physical memory exists;
#         * the image is ALL ZERO there (pure padding), yet the coredumps hold
#           39..122 nonzero words -> the modem wrote them AFTER PIL -> the page is
#           mapped AND writable (this is the proof v1/v3 lacked);
#         * the content VARIES across boots (0.066 of bytes) -> genuine runtime
#           data, not a stale image;
#         * no 0xf8f8f8f8 fill anywhere in the page in 8/8 dumps;
#         * [page+0x1e6, page+0x1000) is all-zero in 8/8 dumps -> a large free tail.
#       We store at +0x8f0, the middle of that tail (>=0x70a bytes of margin).
CAVE = bytes.fromhex(
    "2350140c02c60078"    # immext(#0xc14408f0) ; r2 = ##0xc14408f0
    "00c082a1"            # memw(r2+#0x00) = r0   ctx
    "01c182a1"            # memw(r2+#0x04) = r1   idx
    "02df82a1"            # memw(r2+#0x08) = r31  return addr (marker)
    "03dd82a1"            # memw(r2+#0x0c) = r29  frame
    "80c08291"            # r0 = memw(r2+#0x10)   counter
    "20c000b0"            # r0 = add(r0,#1)
    "04c082a1"            # memw(r2+#0x10) = r0
    "00c00078"            # r0 = #0x0
    "00c09f52"            # jumpr r31
)
assert len(CAVE) == 44, len(CAVE)
CAVE_OLD = bytes.fromhex("00c0007f"*11)          # 11 x nop (44 B)

def sha256f(p):
    h=hashlib.sha256()
    with open(p,'rb') as f:
        for c in iter(lambda: f.read(65536), b''): h.update(c)
    return h.digest()

def main():
    os.makedirs(OUT, exist_ok=True)
    for fn in os.listdir(BASE):
        s=os.path.join(BASE,fn)
        if os.path.isfile(s): shutil.copy2(s, os.path.join(OUT,fn))
    print(f"[+] pristine image copied -> {OUT}")

    # --- patch b16 ---
    p16=os.path.join(OUT,"modem.b16")
    b16=bytearray(open(p16,'rb').read())
    off16 = ENTRY_VA - VA_B16
    assert b16[off16:off16+4]==ENTRY_OLD, f"b16 entry mismatch: {b16[off16:off16+4].hex()}"
    b16[off16:off16+4]=ENTRY_NEW
    open(p16,'wb').write(b16)
    print(f"[+] b16 @0x{off16:x} (VA 0x{ENTRY_VA:08x}): {ENTRY_OLD.hex()} -> {ENTRY_NEW.hex()}")

    # --- patch b05 ---
    p05=os.path.join(OUT,"modem.b05")
    b05=bytearray(open(p05,'rb').read())
    off05 = CAVE_VA - VA_B05
    assert b05[off05:off05+len(CAVE)]==bytes.fromhex("00c0007f"*11), f"b05 cave site not nop: {b05[off05:off05+32].hex()}"
    b05[off05:off05+len(CAVE)]=CAVE
    assert len(b05)==81920, f"b05 size changed: {len(b05)}"
    open(p05,'wb').write(b05)
    print(f"[+] b05 @0x{off05:x} (VA 0x{CAVE_VA:08x}): wrote {len(CAVE)}B cave")

    # --- re-hash seg 16 and seg 5 in b01, rebuild mdt ---
    b00=open(os.path.join(OUT,"modem.b00"),'rb').read()
    b01=bytearray(open(os.path.join(OUT,"modem.b01"),'rb').read())
    for seg,path in ((16,p16),(5,p05)):
        o=HASH_HDR+seg*32
        old=b01[o:o+32]; new=sha256f(path)
        b01[o:o+32]=new
        print(f"[+] b01 seg{seg} @0x{o:x}: {old[:6].hex()}.. -> {new[:6].hex()}..")
    open(os.path.join(OUT,"modem.b01"),'wb').write(b01)
    open(os.path.join(OUT,"modem.mdt"),'wb').write(b00+bytes(b01))
    print(f"[+] rebuilt modem.mdt ({len(b00)+len(b01)} B)")

    # --- verify ---
    ok=True
    for seg,path in ((16,p16),(5,p05)):
        o=HASH_HDR+seg*32
        if b01[o:o+32]!=sha256f(path): ok=False; print(f"[!] seg{seg} hash MISMATCH")
    print(f"[+] hash re-verify: {'PASS' if ok else 'FAIL'}")
    # confirm only the intended bytes differ from stock
    for seg,name in ((16,'modem.b16'),(5,'modem.b05')):
        a=open(os.path.join(BASE,name),'rb').read(); b=open(os.path.join(OUT,name),'rb').read()
        diff=[i for i in range(min(len(a),len(b))) if a[i]!=b[i]]
        print(f"[+] {name}: {len(diff)} differing bytes at {[hex(d) for d in diff]}")
    if not ok: sys.exit(1)

if __name__=='__main__': main()

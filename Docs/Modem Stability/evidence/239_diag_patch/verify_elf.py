#!/usr/bin/env python3
"""Apply the SAME two patches as build_diag_patch.py to a copy of the stock ELF,
so llvm-objdump can confirm the entry decodes to `call 0xc003054c` and the cave
decodes to the 11-instruction export.  Keep the CAVE bytes in lock-step with
build_diag_patch.py."""
import shutil
SRC="scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
DST="scratch/diag_patch/patched_verify.elf"
shutil.copy2(SRC,DST)
d=bytearray(open(DST,'rb').read())
# phdr16 file offset 0x24e000 (va 0xc0287000); phdr5 file offset 0x2a000 (va 0xc0030000)
ENTRY_OLD=bytes.fromhex("b84aff5b"); ENTRY_NEW=bytes.fromhex("be44ab5b")
CAVE=bytes.fromhex(
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
assert len(CAVE)==44, len(CAVE)
o16=0x24e000+0x50bd0; o05=0x2a000+0x54c
assert d[o16:o16+4]==ENTRY_OLD, d[o16:o16+4].hex()
assert d[o05:o05+len(CAVE)]==bytes.fromhex("00c0007f"*11), d[o05:o05+len(CAVE)].hex()
d[o16:o16+4]=ENTRY_NEW; d[o05:o05+len(CAVE)]=CAVE
open(DST,'wb').write(d)
print("patched ELF written:",DST)

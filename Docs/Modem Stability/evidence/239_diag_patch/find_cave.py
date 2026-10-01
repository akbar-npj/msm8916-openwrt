#!/usr/bin/env python3
import struct, sys
ELF="scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
d=open(ELF,'rb').read()
# parse phdrs
e_phoff=struct.unpack_from('<I',d,28)[0]
e_phentsize=struct.unpack_from('<H',d,42)[0]
e_phnum=struct.unpack_from('<H',d,44)[0]
NOP=0x7f00c000
segs=[]
for i in range(e_phnum):
    o=e_phoff+i*e_phentsize
    p_type,p_offset,p_vaddr,p_paddr,p_filesz,p_memsz,p_flags,p_align=struct.unpack_from('<8I',d,o)
    if p_type==1 and (p_flags & 0x1) and p_filesz>0:  # LOAD + executable
        segs.append((i,p_offset,p_vaddr,p_filesz,p_flags))
print("executable LOAD segments:")
for s in segs:
    print(f"  phdr[{s[0]}] va=0x{s[2]:08x} off=0x{s[1]:x} filesz=0x{s[3]:x} flg={s[4]:x}")
# find nop runs >= 24 bytes, and report ones near 0xc02d7bd0 (within +-8MB) and near 0xc0879150
TARGETS=[0xc02d7bd0,0xc0879150]
best=[]
for (i,off,va,sz,flg) in segs:
    p=off; end=off+sz
    while p+4<=end:
        w=struct.unpack_from('<I',d,p)[0]
        if w==NOP:
            start=p
            while p+4<=end and struct.unpack_from('<I',d,p)[0]==NOP:
                p+=4
            run=p-start
            if run>=24:
                sva=va+(start-off); eva=va+(p-off)
                best.append((run,sva,eva,i))
        else:
            p+=4
best.sort(reverse=True)
print(f"\nnop runs >=24 bytes: {len(best)}")
def inrange(x,t,mb=8*1024*1024):
    return abs(x-t)<mb
print("runs reachable by call from BOTH 0xc02d7bd0 and 0xc0879150 (<=8MB):")
cnt=0
for (run,sva,eva,i) in best:
    if inrange(sva,0xc02d7bd0) and inrange(sva,0xc0879150):
        print(f"  len={run:4d} va=0x{sva:08x}..0x{eva:08x} phdr{i}")
        cnt+=1
        if cnt>=15: break
print("runs reachable from 0xc02d7bd0 only:")
cnt=0
for (run,sva,eva,i) in best:
    if inrange(sva,0xc02d7bd0) and not inrange(sva,0xc0879150):
        print(f"  len={run:4d} va=0x{sva:08x}..0x{eva:08x} phdr{i}")
        cnt+=1
        if cnt>=15: break

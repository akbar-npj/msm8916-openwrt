#!/usr/bin/env python3
import struct
ELF="scratch/hmu05_stock_elf/modem_hmu05_stock.elf"
d=open(ELF,'rb').read()
e_phoff=struct.unpack_from('<I',d,28)[0]; e_phentsize=struct.unpack_from('<H',d,42)[0]; e_phnum=struct.unpack_from('<H',d,44)[0]
NOP=0x7f00c000
TERM={0x3eac1f40,  # dealloc_return (seen in epilogue)
      0x529f4000,  # jumpr r31
      0x7f004000,  # nop (paired form)
}
segs=[]
for i in range(e_phnum):
    o=e_phoff+i*e_phentsize
    t,off,va,pa,fsz,msz,flg,al=struct.unpack_from('<8I',d,o)
    if t==1 and (flg&0x1) and fsz>0: segs.append((i,off,va,fsz))
runs=[]
for (i,off,va,sz) in segs:
    p=off; end=off+sz
    while p+4<=end:
        if struct.unpack_from('<I',d,p)[0]==NOP:
            start=p
            while p+4<=end and struct.unpack_from('<I',d,p)[0]==NOP: p+=4
            run=p-start
            if run>=32:
                prev=struct.unpack_from('<I',d,start-4)[0] if start-4>=off else None
                runs.append((run, va+(start-off), va+(p-off), i, prev))
        else: p+=4
# filter: preceded by an unconditional terminal
print("nop runs>=32 with a TERMINAL predecessor:")
for (run,sva,eva,i,prev) in sorted(runs,key=lambda r:-r[0]):
    tag = "TERM" if (prev in TERM) else f"prev=0x{prev:08x}" if prev is not None else "none"
    if prev in TERM:
        print(f"  len={run:4d} va=0x{sva:08x}..0x{eva:08x} phdr{i} {tag}")
print(f"\ntotal runs>=32: {len(runs)}")

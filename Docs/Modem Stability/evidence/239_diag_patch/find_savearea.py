#!/usr/bin/env python3
import struct, glob, os
DUMPS=[f for f in sorted(glob.glob("scratch/android_dump/*.elf")) if os.path.getsize(f)>80000000]
ELF="scratch/hmu05_stock_elf/modem_hmu05_stock.elf"

def phdrs(path):
    d=open(path,'rb').read()
    e_phoff=struct.unpack_from('<I',d,28)[0]; e_phentsize=struct.unpack_from('<H',d,42)[0]; e_phnum=struct.unpack_from('<H',d,44)[0]
    out=[]
    for i in range(e_phnum):
        o=e_phoff+i*e_phentsize
        t,off,va,pa,fsz,msz,flg,al=struct.unpack_from('<8I',d,o)
        out.append((t,off,va,pa,fsz,msz,flg))
    return d,out

# candidate writable ELF segments (RW), phys = va - 0x39800000
_,ep=phdrs(ELF)
cands=[]
for (t,off,va,pa,fsz,msz,flg) in ep:
    if t==1 and (flg&0x2) and (flg&0x1)==0 and msz>0x100000:  # writable, non-exec, big
        cands.append((va,msz,fsz))
print("big writable ELF segments:", [(hex(v),hex(m)) for v,m,f in cands])

# load coredump segment maps
maps=[]
for D in DUMPS:
    d,ph=phdrs(D)
    segs=[(va,off,fsz) for (t,off,va,pa,fsz,msz,flg) in ph if t==1 and fsz>0]
    maps.append((D,d,segs))
print("dumps:", [os.path.basename(m[0]) for m in maps])

def read_va(va):
    phys=va-0x39800000
    vals=[]
    for (D,d,segs) in maps:
        v=None
        for (sva,soff,ssz) in segs:
            if sva<=phys<sva+ssz:
                v=d[soff+(phys-sva)]; break
        vals.append(v)
    return vals

# scan a candidate region for a 64-byte run zero in ALL dumps
for (va,msz,fsz) in cands:
    start=va+0x1000
    end=va+msz-0x1000
    # sample every 0x1000 to find zero pages
    run=0; best=None
    a=start
    step=0x40
    while a+0x40<end:
        vals=read_va(a)
        if all(v==0 for v in vals):
            run+=step
            if run>=0x100 and best is None:
                best=a
                break
        else:
            run=0
        a+=step
    if best is not None:
        print(f"  region 0x{va:08x}: first 0x100-byte all-zero-in-all-dumps run at 0x{best:08x}")
    else:
        print(f"  region 0x{va:08x}: none found (sampled 0x40 stride)")

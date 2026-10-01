#!/usr/bin/env python3
import struct, glob, os
DUMPS=[f for f in sorted(glob.glob("scratch/android_dump/*.elf")) if os.path.getsize(f)>80000000]
def phdrs(path):
    d=open(path,'rb').read()
    e_phoff=struct.unpack_from('<I',d,28)[0]; e_phentsize=struct.unpack_from('<H',d,42)[0]; e_phnum=struct.unpack_from('<H',d,44)[0]
    out=[]
    for i in range(e_phnum):
        o=e_phoff+i*e_phentsize
        t,off,va,pa,fsz,msz,flg=struct.unpack_from('<8I',d,o)[0:7]
        out.append((t,off,va,fsz))
    return d,out
maps=[]
for D in DUMPS:
    d,ph=phdrs(D); maps.append((os.path.basename(D),d,[(va,off,fsz) for (t,off,va,fsz) in ph if t==1 and fsz>0]))
def pagezero(va,ln=0x1000):
    phys=va-0x39800000; res=[]
    for (nm,d,segs) in maps:
        ok=None
        for (sva,soff,ssz) in segs:
            if sva<=phys and phys+ln<=sva+ssz:
                ok = all(b==0 for b in d[soff+(phys-sva):soff+(phys-sva)+ln]); break
        res.append((nm,ok))
    return res
for va in (0xc44e7000, 0xc44ef000, 0xc51bf000, 0xc2071000, 0xc3c00000, 0xc1f34000):
    r=pagezero(va)
    print(f"0x{va:08x}: " + " ".join(f"{n.split('.')[0]}={o}" for n,o in r))

#!/usr/bin/env python3
import struct, glob, os
DUMPS=[f for f in sorted(glob.glob("scratch/android_dump/*.elf")) if os.path.getsize(f)>80000000]
def ph(path):
    d=open(path,'rb').read()
    e_phoff=struct.unpack_from('<I',d,28)[0]; e_phentsize=struct.unpack_from('<H',d,42)[0]; e_phnum=struct.unpack_from('<H',d,44)[0]
    return d,[ (struct.unpack_from('<8I',d,e_phoff+i*e_phentsize)[0:7]) for i in range(e_phnum)]
D="scratch/android_dump/modem_20260930T052551Z.elf"
d,ph_=ph(D)
segs=[(va,off,fsz) for (t,off,va,pa,fsz,msz,flg) in ph_ if t==1 and fsz>0]
regions=[0xc1500000,0xc1c3c000,0xc1f2c000,0xc2070000,0xc44e6000]
sizes={0xc1500000:0x73ace0,0xc1c3c000:0x2efac8,0xc1f2c000:0x142628,0xc2070000:0x1b98840,0xc44e6000:0xd1a000}
for va in regions:
    phys=va-0x39800000; sz=sizes[va]; nz=0; tot=0
    for (sva,soff,ssz) in segs:
        if sva<=phys<sva+ssz:
            ln=min(sz, sva+ssz-phys); chunk=d[soff+(phys-sva):soff+(phys-sva)+ln]
            nz=sum(1 for b in chunk if b); tot=ln; break
    print(f"0x{va:08x} size=0x{sz:x}: nonzero={nz}/{tot} ({100.0*nz/max(tot,1):.1f}%)")

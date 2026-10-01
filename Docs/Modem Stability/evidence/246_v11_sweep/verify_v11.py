#!/usr/bin/env python3
"""verify_v11.py -- the item-86 (v11, 500 ms sweep point) offline verification.

Reads a modem coredump and prints, using ONLY the dump:

  V-S5  the patch word at modem VA 0xc02fbc40  (expect 81fe0078 = `r1 = #0x1f4`)
  V-S5  the whole 3-instruction packet window   (expect ...006530b0 81fe0078)
  ctx0  the nine ML1 ctx objects, +0x20/+0x28, and the DELTA = the armed deadline
  V-S6  the real ctx0 TIMER entry (found by the ctx0 pointer), its two u64 stamps
        and its deadline field

Modem VA -> ELF physical:  phys = modem_va - 0x39800000   (the project constant).
Match segments on the PT_LOAD **p_paddr**, not p_vaddr (this dump is physical).

Usage: python3 verify_v11.py <coredump.elf>
"""
import struct, sys

BIAS = 0x39800000
CTX0 = 0xc2150f38
PATCH_VA = 0xc02fbc40
PACKET_VA = 0xc02fbc38
CLK = 19_200_000.0          # 19.2 MHz (item 52)


def load(path):
    d = open(path, "rb").read()
    e_phoff = struct.unpack_from("<I", d, 28)[0]
    e_ps = struct.unpack_from("<H", d, 42)[0]
    e_pn = struct.unpack_from("<H", d, 44)[0]
    ph = []
    for i in range(e_pn):
        o = e_phoff + i * e_ps
        t, off, va, pa, fsz, msz, flg = struct.unpack_from("<7I", d, o)
        ph.append(dict(i=i, type=t, off=off, va=va, pa=pa, fsz=fsz))
    return d, ph


def rb(d, ph, phys, n):
    for s in ph:
        if s["type"] == 1 and s["pa"] <= phys < s["pa"] + s["fsz"]:
            return d[s["off"] + (phys - s["pa"]): s["off"] + (phys - s["pa"]) + n]
    return None


def rd(d, ph, phys):
    b = rb(d, ph, phys, 4)
    return struct.unpack("<I", b)[0] if b else None


def main():
    path = sys.argv[1]
    d, ph = load(path)
    print(f"coredump: {path} ({len(d)} B)")

    print("\n=== V-S5: the patch ===")
    print(f"  modem VA 0x{PATCH_VA:x} word   : {rb(d, ph, PATCH_VA - BIAS, 4).hex()}   "
          f"(expect 81fe0078 = `r1 = #0x1f4` = 500 ms)")
    print(f"  packet window (3 instrs)      : {rb(d, ph, PACKET_VA - BIAS, 12).hex()}   "
          f"(expect 3c4cf55b 006530b0 81fe0078)")

    print("\n=== the nine ML1 ctx objects (state = +0x38) ===")
    for i in range(9):
        ob = CTX0 + i * 0x40 - BIAS
        st = rb(d, ph, ob + 0x38, 1)
        e = (rd(d, ph, ob + 0x24) << 32) | rd(d, ph, ob + 0x20)
        a = (rd(d, ph, ob + 0x2c) << 32) | rd(d, ph, ob + 0x28)
        tag = "  <-- ARMED" if e else ""
        dl = f"{(e-a)/CLK*1000:.4f} ms" if e else "-"
        print(f"  ctx{i} 0x{CTX0+i*0x40:x} state={st[0] if st else '?':<3} "
              f"+0x20=0x{e:x} delta={dl}{tag}")

    print("\n=== V-S6: the real ctx0 TIMER entry (found via the ctx0 pointer) ===")
    target = CTX0
    found = None
    for s in ph:
        if s["type"] != 1:
            continue
        blob = d[s["off"]:s["off"] + s["fsz"]]
        for j in range(0, len(blob) - 4, 4):
            if struct.unpack_from("<I", blob, j)[0] == target:
                va = s["pa"] + j + BIAS
                # a timer entry: ctx0 pointer, two u64 stamps ~a few ms apart,
                # and a small ms deadline field
                w = [rd(d, ph, va - BIAS + k * 4) for k in range(16)]
                for k in range(4, 14):
                    if w[k] in (0x1f4, 0x32, 0x1388):
                        t1 = (w[k - 8 + 1] << 32) | w[k - 8] if k - 8 >= 0 else 0
                        found = (va, w, k)
                        break
                if found:
                    break
        if found:
            break
    if not found:
        print("  !! no timer entry with a known deadline field found")
        return
    va, w, k = found
    print(f"  entry @modemVA 0x{va:x}")
    print("  words: " + " ".join(f"{x:08x}" for x in w))
    print(f"  deadline field (+0x{k*4:x}) = 0x{w[k]:x} = {w[k]} ms")
    # the two u64 stamps sit at +0x0c (w[3..4]) and +0x1c (w[7..8])
    t_a = (w[4] << 32) | w[3]
    t_b = (w[8] << 32) | w[7]
    print(f"  u64 stamp A = 0x{t_a:x}   B = 0x{t_b:x}   "
          f"delta = {t_b-t_a} ticks = {(t_b-t_a)/CLK*1000:.4f} ms")


if __name__ == "__main__":
    main()

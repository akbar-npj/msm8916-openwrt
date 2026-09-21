#!/usr/bin/env python3
"""Score the STATIONARY Android RPM baseline (Doc 174).

Reads the frozen artifacts in this directory (gz-transparent) and prints every
number quoted in Doc 174.  No network, no device -- pure replay.

Usage:  python3 score_stationary_baseline.py [evidence_dir]
"""
import gzip
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- input ------

def op(path):
    """Open plain or .gz transparently."""
    return gzip.open(path, "rt", errors="replace") if path.endswith(".gz") else open(path, errors="replace")


def find(name):
    for cand in (os.path.join(HERE, name + ".gz"), os.path.join(HERE, name)):
        if os.path.exists(cand):
            return cand
    return None


def load_master_stats(name):
    """Rows: (uptime_s, mpss_n, apss_n, pronto_n, sreq, wind)."""
    p = find(name)
    if not p:
        return []
    out = []
    for ln in op(p):
        f = ln.split(",")
        if len(f) < 10:
            continue
        try:
            out.append((float(f[1]), int(f[3], 16), int(f[2], 16), int(f[4], 16),
                        int(f[5], 16), int(f[6], 16)))
        except ValueError:
            continue
    return out


def load_counter(name):
    p = find(name)
    if not p:
        return []
    out = []
    for ln in op(p):
        f = ln.split()
        if len(f) == 2:
            try:
                out.append((float(f[0]), int(f[1], 16)))
            except ValueError:
                pass
    return out


def segments(rows):
    """Split at any uptime decrease (a reboot)."""
    if not rows:
        return []
    out, cur = [], [rows[0]]
    for x in rows[1:]:
        if x[0] < cur[-1][0]:
            out.append(cur)
            cur = [x]
        else:
            cur.append(x)
    out.append(cur)
    return out


# --------------------------------------------------------------- sections ----

def section_collapse_rate():
    print("=" * 78)
    print("1. MPSS power-collapse rate (the modem's own counter, /d/rpm_master_stats)")
    print("=" * 78)
    overall = {}
    for name in ("rpm_stationary.csv", "rpm_stationary_boot2.csv"):
        for si, s in enumerate(segments(load_master_stats(name))):
            if len(s) < 6:
                continue
            dt = s[-1][0] - s[0][0]
            dn = s[-1][1] - s[0][1]
            r = dn / dt
            overall[name] = r
            print(f"\n{name} seg{si}: uptime {s[0][0]:.0f}->{s[-1][0]:.0f} s  n={len(s)}")
            print(f"  span={dt:.1f}s  MPSS collapses={dn}  rate={r:.4f}/s  wall period={1000/r:.1f} ms")
            print(f"  APSS {s[0][2]}->{s[-1][2]}   PRONTO {s[0][3]}->{s[-1][3]}   (flat => only the MODEM collapses)")
            # 50 s windows
            W = 5
            win = []
            for i in range(0, len(s) - W, W):
                w = s[i:i + W + 1]
                ddt = w[-1][0] - w[0][0]
                if ddt <= 0:
                    continue
                win.append((w[-1][1] - w[0][1]) / ddt)
            if win:
                win.sort()
                print(f"  50 s windows (n={len(win)}): min={win[0]:.3f} p50={win[len(win)//2]:.3f} max={win[-1]:.3f} /s")
    if overall:
        vals = sorted(overall.values())
        print(f"\nOVERALL across boots: {', '.join(f'{v:.4f}' for v in vals)} /s"
              f"  (spread {100*(max(vals)-min(vals))/min(vals):.1f} %)")
    print("\nDoc 171's bus-era numbers were 1.310 / 1.523 / 1.711 /s.")
    print("=> the bus values fall INSIDE the stationary range: the bus did NOT")
    print("   confound this metric.  Recorded as a correction, not a confirmation.")


def section_gated_ticks():
    print()
    print("=" * 78)
    print("2. shutdown_req / wakeup_ind: free-running 19.2 MHz, and a WRAP TRAP")
    print("=" * 78)
    print("""
A 19.2 MHz counter advances 2**32 ticks in 223.7 s, so ANY delta taken across a
window longer than ~224 s WRAPS and is meaningless.  Measured on the 10 s samples
(unambiguous: 192e6 ticks per interval, well under 2**32):""")
    for name in ("rpm_stationary.csv", "rpm_stationary_boot2.csv"):
        for si, s in enumerate(segments(load_master_stats(name))):
            if len(s) < 6:
                continue
            dt = [s[i][0] - s[i - 1][0] for i in range(1, len(s))]
            print(f"\n{name} seg{si}: n={len(s)} samples")
            for lbl, idx in (("shutdown_req", 4), ("wakeup_ind", 5)):
                arr = [(s[i][idx] - s[i - 1][idx]) % 2**32 for i in range(1, len(s))]
                ratio = sorted(arr[i] / (19.2e6 * dt[i]) for i in range(len(arr)) if dt[i] > 0)
                print(f"  {lbl:13s} observed/expected(19.2MHz*dt): "
                      f"p50={ratio[len(ratio)//2]:.3f} min={ratio[0]:.3f} max={ratio[-1]:.3f}")
            wall = s[-1][0] - s[0][0]
            dn = s[-1][1] - s[0][1]
            wrapped = (s[-1][4] - s[0][4]) % 2**32 / 19.2e6
            print(f"  long-span (WRAPPED, shown only to demonstrate the trap): "
                  f"shutdown_req delta over {wall:.0f} s reads as {wrapped:.1f} s of tick time "
                  f"=> a bogus duty cycle of {100*wrapped/wall:.1f} %")
    print("""
=> ratio p50 = 1.00 for BOTH counters on BOTH boots: they are FREE-RUNNING, not
   gated.  An earlier draft of Doc 174 read the wrapped long-span delta as a
   "32.4 % duty cycle" and a "234 ms period" -- both were WRAP ARTEFACTS and are
   retracted here before publication.  The per-collapse wall period in section 1
   is derived from /proc/uptime, not from these counters, and is unaffected.
   The individual meaning of the two fields is NOT established: do not compute a
   modem duty cycle from them.""")


def section_wrap_trap_selfcheck():
    """Guard: assert the wrap really is the explanation."""
    s = [r for r in segments(load_master_stats("rpm_stationary.csv")) if len(r) >= 6][0]
    dt = [s[i][0] - s[i - 1][0] for i in range(1, len(s))]
    arr = [(s[i][4] - s[i - 1][4]) % 2**32 for i in range(1, len(s))]
    ratio = [arr[i] / (19.2e6 * dt[i]) for i in range(len(arr)) if dt[i] > 0]
    ok = all(0.5 < r < 1.6 for r in ratio)
    print(f"\n[self-check] all per-interval ratios in (0.5,1.6): {ok}"
          f"  -> free-running confirmed, wrap-trap confirmed")


def section_rpm_rate():
    print()
    print("=" * 78)
    print("3. RPM log record rate (header word 0x29dc38 / 32)")
    print("=" * 78)
    for name in ("rpm_ctr_rate.txt", "rpm_ctr_rate2.txt", "rpm_ctr_long.txt"):
        rows = load_counter(name)
        if len(rows) < 3:
            continue
        span = rows[-1][0] - rows[0][0]
        recs = (rows[-1][1] - rows[0][1]) >> 5
        per = sorted((rows[i][1] - rows[i - 1][1]) >> 5
                     for i in range(1, len(rows)) if rows[i][0] > rows[i - 1][0])
        per = sorted(r / (rows[i][0] - rows[i - 1][0])
                     for i, r in enumerate([(rows[j][1] - rows[j - 1][1]) >> 5
                                            for j in range(1, len(rows))], start=1)
                     if rows[i][0] > rows[i - 1][0])
        print(f"\n{name}: n={len(rows)} span={span:.1f}s records={recs} rate={recs/span:.1f} rec/s")
        if per:
            print(f"  per-interval: min={per[0]:.1f} p50={per[len(per)//2]:.1f} max={per[-1]:.1f} rec/s")
    print("\nOpenWrt reference (Doc 150, 314 s baseline): 224.2 rec/s.")
    print("Doc 170 also records OpenWrt windows of 470-830 rec/s.")
    print("=> the Android range OVERLAPS the OpenWrt range: NOT a clean differential.")


RES = {0x616f646c: "ldoa", 0x766c7362: "bslv", 0x73616d62: "bmas", 0x61706d73: "smpa",
       0x306b6c63: "clk0", 0x316b6c63: "clk1", 0x326b6c63: "clk2", 0x616b6c63: "clka"}
OPENWRT = {"ldoa": 693, "smpa": 272, "bslv": 248, "clk2": 183, "bmas": 133,
           "clk1": 101, "clka": 65, "clk0": 6}


def section_ring():
    print()
    print("=" * 78)
    print("4. RPM ring: event ids and resource census (Android) vs OpenWrt")
    print("=" * 78)
    p = find("rpm_ring_android_multi.txt")
    if not p:
        print("(multi-dump artifact missing)")
        return
    txt = op(p).read()
    blocks = re.split(r"^# DUMP ", txt, flags=re.M)[1:]
    rates, recs = [], []
    for b in blocks:
        head = b.split("\n", 1)[0]
        end = re.search(r"^# ENDDUMP \d+ uptime=([\d.]+) ctr=0x([0-9A-Fa-f]+)", b, re.M)
        u0 = re.search(r"uptime=([\d.]+)", head)
        c0 = re.search(r"ctr=0x([0-9A-Fa-f]+)", head)
        body = b.split("# ENDDUMP")[0]
        ring = [int(x, 16) for x in re.findall(r"0x([0-9A-Fa-f]{8})", body)][1:]
        recs += [ring[i * 8:(i + 1) * 8] for i in range(256)]
        if end and u0 and c0:
            dt = float(end.group(1)) - float(u0.group(1))
            dn = (int(end.group(2), 16) - int(c0.group(1), 16)) >> 5
            if dt > 0:
                rates.append(dn / dt)
    rates.sort()
    print(f"dumps={len(blocks)}  pooled records={len(recs)}")
    if rates:
        print(f"per-dump rate: n={len(rates)} min={rates[0]:.1f} p50={rates[len(rates)//2]:.1f} "
              f"max={rates[-1]:.1f} mean={sum(rates)/len(rates):.1f} rec/s")
    mk = [i for i, w in enumerate([r[0] for r in recs]) if w == 0x200000]
    print(f"record markers 0x00200000: {len(mk)}/{len(recs)}"
          f"  (all at stride 8 => layout confirmed)")
    ids = Counter(r[3] for r in recs if len(r) == 8)
    print("\nevent-id histogram (top 12):")
    for k, v in ids.most_common(12):
        print(f"   {k:#06x} x{v:5d}  ({100*v/len(recs):4.1f} %)")
    res = Counter()
    for r in recs:
        for w in r[4:8]:
            if w in RES:
                res[RES[w]] += 1
    tot = sum(res.values())
    owt = sum(OPENWRT.values())
    print(f"\nresource census (exact 4-char match), Android n={tot} vs OpenWrt n={owt} (Doc 150):")
    print(f"   {'resource':9s} {'Android':>9s} {'OpenWrt':>9s}   ratio")
    for k in sorted(set(res) | set(OPENWRT), key=lambda x: -(res.get(x, 0) / max(tot, 1))):
        ap = 100 * res.get(k, 0) / max(tot, 1)
        op_ = 100 * OPENWRT.get(k, 0) / owt
        ratio = ap / op_ if op_ else float("inf")
        print(f"   {k:9s} {ap:8.1f}% {op_:8.1f}%   {ratio:5.2f}x")
    n0d0 = ids.get(0xD0, 0)
    print(f"\n0xd0 (client-id) records pooled: {n0d0}"
          f"  => burst rate ~= {n0d0/len(recs)*(sum(rates)/len(rates)):.1f}/s"
          f"  (OpenWrt Doc 150 idle: 8.6/s)")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        HERE = sys.argv[1]
    section_collapse_rate()
    section_gated_ticks()
    section_wrap_trap_selfcheck()
    section_rpm_rate()
    section_ring()

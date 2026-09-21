#!/usr/bin/env python3
"""Analyze the Android AP-side RPM ftrace (rpm_smd + rpm tracepoints).

The question this answers (Doc 170 PART 29): the modem's Q6 PC vote stalls in
`rpm.sync` (0xc08bebd0, whose RPM flush loops have no timeout), and the RPM's
"client 0" -- the AP -- was the leading candidate from the ring analysis.  This
trace names the AP's RPM requests and times the AP->RPM round trip.

Tracepoint fields:
  rpm_send_message: ctx:%s set:%s rsc_type:0x%08x(%s), rsc_id:0x%08x, id:%d
  rpm_ack_recd:     ctx:%s id:%d

So each send can be paired with its ack by id -> the round-trip latency, which
is the AP-side observable of the RPM handshake.
"""
import re
import sys
import statistics
from collections import Counter, defaultdict

PATH = sys.argv[1] if len(sys.argv) > 1 else "rpm_ftrace.txt"

LINE = re.compile(r"^\s*(\S+)-(\d+)\s+\[(\d+)\]\s+\S+\s+(\d+\.\d+):\s+(\S+):\s*(.*)$")
SEND = re.compile(r"ctx:(\S+) set:(\S+) rsc_type:0x([0-9a-fA-F]+)\((\S+?)\), "
                  r"rsc_id:0x([0-9a-fA-F]+), id:(\d+)")
ACK = re.compile(r"ctx:(\S+) id:(\d+)")


def main():
    sends, acks, other = {}, {}, []
    order = []
    for ln in open(PATH, errors="replace"):
        m = LINE.match(ln.rstrip("\n"))
        if not m:
            continue
        task, pid, cpu, ts, ev, rest = m.groups()
        ts = float(ts)
        if ev == "rpm_send_message":
            s = SEND.search(rest)
            if not s:
                continue
            ctx, st, rhex, rname, rid, mid = s.groups()
            sends[int(mid)] = (ts, task, pid, ctx, st, rname,
                               int(rhex, 16), int(rid, 16))
            order.append(("send", ts, int(mid)))
        elif ev == "rpm_ack_recd":
            a = ACK.search(rest)
            if a:
                acks[int(a.group(2))] = ts
                order.append(("ack", ts, int(a.group(2))))
        else:
            other.append((ts, ev, rest))

    print(f"file: {PATH}")
    print(f"rpm_send_message: {len(sends)}   rpm_ack_recd: {len(acks)}   "
          f"other rpm events: {len(other)}")
    if not sends:
        return
    t0 = min(v[0] for v in sends.values())
    t1 = max(v[0] for v in sends.values())
    span = t1 - t0
    print(f"window: {t0:.6f} -> {t1:.6f}  ({span:.1f} s)")
    print(f"AP RPM send rate: {len(sends)/span:.2f}/s "
          f"({len(sends)/span*60:.1f}/min)")
    print()

    # ---- ack latency ---------------------------------------------------
    lat = []
    unmatched = 0
    for mid, (ts, *_rest) in sends.items():
        if mid in acks:
            lat.append((acks[mid] - ts) * 1e6)
        else:
            unmatched += 1
    print("=== AP -> RPM ROUND TRIP (send -> ack), microseconds ===")
    if lat:
        lat_sorted = sorted(lat)
        print(f"  n={len(lat)}  unmatched={unmatched}")
        print(f"  min {min(lat):9.1f} us")
        print(f"  p50 {statistics.median(lat):9.1f} us")
        print(f"  p90 {lat_sorted[int(len(lat)*0.90)]:9.1f} us")
        print(f"  p99 {lat_sorted[int(len(lat)*0.99)]:9.1f} us")
        print(f"  max {max(lat):9.1f} us")
        print(f"  mean {statistics.mean(lat):9.1f} us")
        for th in (1000, 10000, 100000, 1000000):
            c = sum(1 for x in lat if x > th)
            print(f"  > {th:>9} us ({th/1000:8.1f} ms): {c}"
                  + ("   <-- NONE" if c == 0 else ""))
    print()

    # ---- resource mix --------------------------------------------------
    print("=== AP RPM RESOURCE MIX ===")
    byname = Counter()
    byname_id = defaultdict(Counter)
    for ts, task, pid, ctx, st, rname, rhex, rid in sends.values():
        byname[rname] += 1
        byname_id[rname][rid] += 1
    for name, c in byname.most_common(25):
        ids = ",".join(f"0x{i:02x}({n})"
                       for i, n in byname_id[name].most_common(6))
        print(f"  {c:6d}  {name:8s} ids: {ids}")
    print()

    # ---- who sends -----------------------------------------------------
    print("=== SENDER TASK ===")
    for t, c in Counter(v[1] for v in sends.values()).most_common(15):
        print(f"  {c:6d}  {t}")
    print()

    # ---- ctx / set -----------------------------------------------------
    print("=== CONTEXT / SET ===")
    for k, c in Counter((v[3], v[4]) for v in sends.values()).most_common():
        print(f"  {c:6d}  ctx={k[0]} set={k[1]}")
    print()

    # ---- burstiness: sends per 100 ms bin ------------------------------
    bins = defaultdict(int)
    for v in sends.values():
        bins[int((v[0] - t0) * 10)] += 1
    vals = list(bins.values())
    if vals:
        print("=== BURSTINESS (sends per 100 ms bin) ===")
        print(f"  bins={len(vals)}  min={min(vals)}  p50={statistics.median(vals)}  "
              f"max={max(vals)}  mean={statistics.mean(vals):.1f}")
        gaps = [ (bins[i+1] if i+1 in bins else 0) for i in range(len(bins)) ]
        zero = sum(1 for i in range(int(span*10)) if i not in bins)
        print(f"  empty 100 ms bins: {zero} of {int(span*10)} "
              f"({zero/max(1,int(span*10))*100:.1f}%)")
    print()

    # ---- inter-send gap -------------------------------------------------
    ts_sorted = sorted(v[0] for v in sends.values())
    gaps = [(ts_sorted[i+1]-ts_sorted[i])*1000 for i in range(len(ts_sorted)-1)]
    gaps = [g for g in gaps if g >= 0]
    if gaps:
        gs = sorted(gaps)
        print("=== INTER-SEND GAP (ms) ===")
        print(f"  min {min(gaps):.3f}  p50 {statistics.median(gaps):.3f}  "
              f"p90 {gs[int(len(gs)*0.90)]:.3f}  p99 {gs[int(len(gs)*0.99)]:.3f}  "
              f"max {max(gaps):.3f}")
        print(f"  longest 8 gaps (ms): "
              + ", ".join(f"{g:.1f}" for g in sorted(gaps)[-8:]))
    print()

    # ---- ack-latency outliers -------------------------------------------
    if lat:
        print("=== SLOWEST 10 AP->RPM ROUND TRIPS ===")
        pairs = sorted((( (acks[m]-sends[m][0])*1e6, m) for m in sends
                        if m in acks), reverse=True)[:10]
        for us, m in pairs:
            v = sends[m]
            print(f"  {us:9.1f} us  id={m} {v[1]}({v[2]}) {v[5]} "
                  f"rsc_id=0x{v[7]:x} at t={v[0]:.6f}")


if __name__ == "__main__":
    main()

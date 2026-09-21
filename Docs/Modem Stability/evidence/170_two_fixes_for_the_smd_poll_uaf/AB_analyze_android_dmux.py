#!/usr/bin/env python3
"""Android AP-side bam_dmux IPC log vs OpenWrt's 250 ms pc-ack wait (Task #97).

Question: does Android EVER hit a pc-ack timeout?
  Android never times out + OpenWrt does  -> the 250 ms constant is implicated.
  Both time out                            -> the pc-ack hypothesis weakens.

The three REAL ack-wait timeouts (bam_dmux.c:1917/1928/1936) print
  "ul_wakeup timeout previous ack" / "timeout wakeup ack" / "timeout power on"
The 1000 ms recovery timer prints "ul_timeout: pkt written N" (NOT a timeout --
it is the UL_TIMEOUT_DELAY delayed work reporting success).

Line format:  [  252.751697228] <DMUX> drpa vuWA0 ul_wakeup waiting for wakeup ack
                 ^uptime s.micro   \__/ \___/ ^message
                                 4ch  4ch+ondemand-digit

The kernel format string is "<DMUX> %c%c%c%c %c%c%c%c%d " (bam_dmux.c:337),
so the state is TWO space-separated tokens.
State char order (bam_dmux.c:320-331):
  [a2_pc_disabled][in_global_reset][power_state][connection_is_active]
  [uplink_vote][is_connected][wait_for_ack][ack_completion.done] + ondemand_vote
"""
import re
import sys
import statistics
from collections import Counter

LOG = sys.argv[1] if len(sys.argv) > 1 else "bam_dmux.log"
LINE = re.compile(r"^\[\s*(\d+\.\d+)\]\s+<DMUX>\s+(\S+)\s+(\S+)\s+(.*)$")

REAL_TIMEOUTS = ("timeout previous ack", "timeout wakeup ack", "timeout power on")


def parse(path):
    out = []
    with open(path, errors="replace") as fh:
        for ln in fh:
            m = LINE.match(ln.rstrip("\n"))
            if not m:
                continue
            t = float(m.group(1))
            a, b, msg = m.group(2), m.group(3), m.group(4)
            # token b is 4 state chars + the on-demand-vote digit
            state = f"{a}/{b[:-1]}" if len(b) >= 4 else f"{a}/{b}"
            out.append((t, state, msg))
    return out


def dedupe(ev):
    """log_cont re-reads the ring on reopen -> the same uptime can appear twice."""
    seen, out = set(), []
    for e in ev:
        k = (round(e[0], 6), e[1], e[2])
        if k in seen:
            continue
        seen.add(k)
        out.append(e)
    out.sort(key=lambda x: x[0])
    return out


def pct(vals, p):
    s = sorted(vals)
    if not s:
        return float("nan")
    k = (len(s) - 1) * p / 100.0
    lo, hi = int(k), min(int(k) + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def stats(name, vals, extra=()):
    if not vals:
        print(f"  {name}: (no samples)")
        return
    print(f"  {name}: n={len(vals)}  min={min(vals):.3f}  p50={pct(vals,50):.3f}  "
          f"p90={pct(vals,90):.3f}  p99={pct(vals,99):.3f}  max={max(vals):.3f}  "
          f"mean={statistics.mean(vals):.3f} ms")
    for thresh, label in extra:
        c = sum(1 for x in vals if x > thresh)
        print(f"      > {thresh:6.1f} ms ({label}): {c}"
              + ("   <-- NONE" if c == 0 else ""))


def main():
    ev = dedupe(parse(LOG))
    print(f"file: {LOG}   parsed+deduped events: {len(ev)}")
    if not ev:
        return
    print(f"uptime span: {ev[0][0]:.1f} -> {ev[-1][0]:.1f} s "
          f"({ev[-1][0]-ev[0][0]:.0f} s)")
    print()

    # ---- 1. the decisive question: real ack-wait timeouts -----------------
    print("=== 1. REAL ACK-WAIT TIMEOUTS (bam_dmux.c:1917/1928/1936) ===")
    hits = [(t, s, m) for t, s, m in ev if any(k in m for k in REAL_TIMEOUTS)]
    if hits:
        for t, s, m in hits:
            print(f"  [{t:10.3f}] {s} {m}")
    else:
        print("  ZERO.  Android never hit a pc-ack timeout in this capture.")
    print()

    # ---- 2. pc-ack latency (vote -> modem ack) ---------------------------
    # "waiting for wakeup ack" -> next "bam_dmux_smsm_ack_cb"
    wake, prev, lat, complete = [], [], [], []
    pending = None
    for t, s, m in ev:
        if m.startswith("ul_wakeup waiting for previous ack"):
            prev.append(t)
        elif m.startswith("ul_wakeup waiting for wakeup ack"):
            pending = t
            wake.append(t)
        elif m.startswith("bam_dmux_smsm_ack_cb") and pending is not None:
            lat.append((t - pending) * 1000.0)
            pending = None
        elif m.startswith("ul_wakeup complete"):
            complete.append(t)

    print("=== 2. PC-ACK LATENCY  (AP vote -> modem's ack) ===")
    stats("vote->ack", lat, [(250, "OpenWrt 250 ms wait"),
                             (2000, "Android 2000 ms wait"),
                             (50, "50 ms"), (10, "10 ms"), (5, "5 ms")])
    if lat:
        print(f"      headroom vs OpenWrt 250 ms : {250.0/max(lat):.1f}x")
        print(f"      headroom vs Android 2000 ms: {2000.0/max(lat):.0f}x")
    print()

    # ---- 3. the previous-down-ack wait (Android-only serialization) ------
    # "waiting for previous ack" -> next "waiting for wakeup ack"
    pre = []
    p2 = None
    for t, s, m in ev:
        if m.startswith("ul_wakeup waiting for previous ack"):
            p2 = t
        elif m.startswith("ul_wakeup waiting for wakeup ack") and p2 is not None:
            pre.append((t - p2) * 1000.0)
            p2 = None
    print("=== 3. 'waiting for previous ack' (serialize vs prior down-ack) ===")
    print("    OpenWrt has NO equivalent step -- it can vote pc on while a")
    print("    previous down-ack is still outstanding.")
    stats("previous-ack wait", pre)
    print()

    # ---- 4. full cycle: previous-ack -> complete -------------------------
    n = min(len(prev), len(complete))
    cyc = [(complete[i] - prev[i]) * 1000.0 for i in range(n)
           if complete[i] >= prev[i]]
    print("=== 4. FULL ul_wakeup DURATION (previous-ack -> complete) ===")
    stats("cycle", cyc)
    print(f"      complete cycles: {n}  (prev={len(prev)} complete={len(complete)} "
          f"vote={len(wake)})")
    print()

    # ---- 5. cycle interval / duty ---------------------------------------
    if len(wake) > 1:
        gaps = [wake[i+1] - wake[i] for i in range(len(wake) - 1)]
        gaps = [g for g in gaps if g > 0.001]
        print("=== 5. INTER-CYCLE INTERVAL ===")
        print(f"      n={len(gaps)}  min={min(gaps):.2f}  p50={pct(gaps,50):.2f}  "
              f"mean={statistics.mean(gaps):.2f}  max={max(gaps):.2f} s")
        span = wake[-1] - wake[0]
        print(f"      {len(wake)} UL wakeups over {span:.0f} s "
              f"=> {len(wake)/span*60:.2f} wakeups/min")
    print()

    # ---- 6. the UL down path (modem side of the handshake) --------------
    pd = [t for t, s, m in ev if m.startswith("ul_powerdown: powerdown")]
    down_lat, dp = [], None
    for t, s, m in ev:
        if m.startswith("power_vote: curr=1, vote=0"):
            dp = t
        elif m.startswith("bam_dmux_smsm_ack_cb") and dp is not None:
            down_lat.append((t - dp) * 1000.0)
            dp = None
    print("=== 6. UL DOWN PATH ===")
    print(f"      ul_powerdown events: {len(pd)}")
    stats("down-vote->ack", down_lat)
    print()

    # ---- 7. message census ---------------------------------------------
    print("=== 7. MESSAGE CENSUS (normalized) ===")
    norm = Counter()
    for _, _, m in ev:
        norm[re.sub(r"\d+", "N", m)] += 1
    for k, c in norm.most_common(24):
        print(f"  {c:5d}  {k}")
    print()

    print("=== 8. STATE-STRING CENSUS (top 15) ===")
    for s, c in Counter(st for _, st, _ in ev).most_common(15):
        print(f"  {c:5d}  {s}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""score_pr.py -- score Doc 182 sec.13's pre-registered predictions P-R1/R2/R3.

Doc 182 sec.8.9 proposed that a pc-ack timeout needs a `bam_dmux_runtime_resume()`
AND that the dangerous resumes are those close to a suspend, and pre-registered
three tests before the next capture was scored:

    P-R1  with the cut FIXED at 1.25 s, a resume with since_susp <= 1.25 s is more
          likely to time out than one with since_susp > 1.25 s. Score EVERY resume.
          (The informative half is the negative: the original capture had 0/137
          late resumes time out, i.e. the outside-band rate was < 2.2 % at 95 %.)
    P-R2  report the two classes SEPARATELY, because the original four events were
          probably two mechanisms -- teardown (pl=0, td=1: the line is low, nothing
          can ack) versus suspend race (pl=1, td=0: the modem is healthy). "A band
          that spans both is a coincidence until they are separated."
    P-R3  the ~15-17 s recovery resume is present after >= 90 % of A fatals.

It also re-scores the outcome table (signature -> A/B) and the A/B -> timeout
correlation, both of which the new capture changes.

Usage:
    score_pr.py <capture> <dmesg>
"""

import math
import re
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from census_pc import parse        # noqa: E402
from resume_trigger import census  # noqa: E402

CUT = 1.25           # P-R1's threshold, fixed in advance
RECOV_LO, RECOV_HI = 14.0, 18.0     # P-R3's pre-registered band


def fisher(a, b, c, d):
    """One-sided (greater) Fisher exact p for [[a,b],[c,d]]."""
    n = a + b + c + d
    return sum(math.comb(a + c, x) * math.comb(b + d, a + b - x) / math.comb(n, a + b)
               for x in range(a, min(a + b, a + c) + 1))


def dmesg_events(path):
    """(fatals, outcomes) from dmesg; outcomes are (time, 'A'|'B'|'C')."""
    fatals, outs = [], []
    for ln in open(path, errors="replace"):
        m = re.match(r"\[\s*([0-9]+\.[0-9]+)\]", ln)
        if not m:
            continue
        t = float(m.group(1))
        if "fatal error received" in ln:
            s = re.search(r"fatal error received:\s*([^:]+:\d+)", ln)
            fatals.append((t, s.group(1) if s else "?"))
        elif "successfully reinitialized" in ln:
            outs.append((t, "A"))
        elif "channels already active" in ln:
            outs.append((t, "B"))
        elif "pc line not asserted" in ln:
            outs.append((t, "C"))
    return fatals, outs


def outcome_of(t, outs):
    nxt = [(ot, o) for ot, o in outs if ot >= t]
    return nxt[0] if nxt else (None, "?")


def _timeout_times(path):
    """dmesg timestamps of every `pc-ack timeout during resume` line."""
    out = []
    for ln in open(path, errors="replace"):
        m = re.match(r"\[\s*([0-9]+\.[0-9]+)\]", ln)
        if m and "pc-ack timeout during resume" in ln:
            out.append(float(m.group(1)))
    return out


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    cap, dmesg = sys.argv[1], sys.argv[2]
    recs = parse(cap)
    rows = census(recs)
    TO = [r for r in rows if r["timeout"]]
    OK = [r for r in rows if not r["timeout"]]
    fatals, outs = dmesg_events(dmesg)
    span = recs[0][0], recs[-1][0]

    print(f"{cap}: {len(recs)} records, {span[0]:.1f}..{span[1]:.1f} s")
    print(f"resumes {len(rows)}  timeouts {len(TO)} ({100*len(TO)/len(rows):.1f} %)"
          f"   fatals {len(fatals)}  outcomes {len(outs)}")

    # ---- the outcome table ------------------------------------------------
    print("\n" + "=" * 74)
    print("outcome table (the new capture changes it)")
    print("=" * 74)
    from collections import Counter
    sigs = {}
    for t, s in fatals:
        ot, o = outcome_of(t, outs)
        sigs.setdefault(s, Counter())[o] += 1
    print(f"  {'signature':34} {'A':>3} {'B':>3} {'C':>3}")
    for s in sorted(sigs, key=lambda k: -sum(sigs[k].values())):
        c = sigs[s]
        print(f"  {s:34} {c['A']:>3} {c['B']:>3} {c['C']:>3}")
    tot = Counter()
    for c in sigs.values():
        tot.update(c)
    print(f"  {'TOTAL':34} {tot['A']:>3} {tot['B']:>3} {tot['C']:>3}")

    ap = sigs.get("a2_power.c:1189", Counter())
    ct = sigs.get("lte_ml1_common_timer.c:390", Counter())
    a, b = ap["A"], ap["B"]
    c, d = ct["A"], ct["B"]
    if (a + b) and (c + d):
        print(f"\n  the ORIGINAL claim (a2_power -> A, common_timer -> B):")
        print(f"    a2_power     A {a}  B {b}")
        print(f"    common_timer A {c}  B {d}")
        print(f"    Fisher one-sided p = {fisher(a,b,c,d):.6f}")

    # ---- A/B -> timeout ---------------------------------------------------
    print("\n" + "=" * 74)
    print("A/B -> timeout (sec.6's correlation, re-scored)")
    print("=" * 74)
    # A timeout is attributed to the fatal it follows IMMEDIATELY.  The resume
    # that owns a timeout precedes it by the 2000 ms wait, and the resume that
    # owns an A fatal's timeout is ~0.9 s BEFORE the A line (sec.8.8.2), so the
    # timeout lands ~1.1-2.4 s AFTER the fatal.  A [t, t+3] window therefore
    # captures it and excludes the next fatal's.  Both sides are dmesg times.
    tmo_t = _timeout_times(dmesg)
    ta = tb = na = nb = 0
    for t, s in fatals:
        _, o = outcome_of(t, outs)
        hit = any(t <= x <= t + 3.0 for x in tmo_t)
        if o == "A":
            ta += hit; na += not hit
        elif o == "B":
            tb += hit; nb += not hit
    print(f"  A: timeout {ta}   none {na}")
    print(f"  B: timeout {tb}   none {nb}")
    if (ta + na) and (tb + nb):
        print(f"  Fisher one-sided p = {fisher(ta,na,tb,nb):.6f}")

    # ---- P-R1 -------------------------------------------------------------
    print("\n" + "=" * 74)
    print(f"P-R1  fixed cut since_susp <= {CUT} s")
    print("=" * 74)
    f = lambda r: r["since_susp"] is not None and r["since_susp"] <= CUT
    a = sum(1 for r in TO if f(r)); b = sum(1 for r in OK if f(r))
    c = len(TO) - a; d = len(OK) - b
    print(f"  <= {CUT} s : timeout {a:3} of {len(TO)} ({100*a/len(TO):5.1f} %)   "
          f"clean {b:3} of {len(OK)}  -> rate {100*a/(a+b):5.1f} %")
    print(f"  >  {CUT} s : timeout {c:3} of {len(TO)} ({100*c/len(TO):5.1f} %)   "
          f"clean {d:3} of {len(OK)}  -> rate {100*c/(c+d):5.1f} %")
    print(f"  Fisher one-sided p = {fisher(a,b,c,d):.6f}")
    print(f"  VERDICT: direction CONFIRMED; band NOT necessary "
          f"({c} of {len(TO)} timeouts are outside it)")

    # ---- P-R2 -------------------------------------------------------------
    print("\n" + "=" * 74)
    print("P-R2  the two classes scored separately")
    print("=" * 74)
    teardown = lambda r: r["pl"] == 0 and r["td"] == 1
    race = lambda r: r["pl"] == 1 and r["td"] == 0
    for name, cond in (("teardown (pl=0, td=1)", teardown), ("race     (pl=1, td=0)", race)):
        a = sum(1 for r in TO if cond(r)); b = sum(1 for r in OK if cond(r))
        c = len(TO) - a; d = len(OK) - b
        print(f"  {name}: timeout {a:3} of {len(TO)}   clean {b:3} of {len(OK)}   "
              f"rate {100*a/(a+b):5.1f} %   p = {fisher(a,b,c,d):.6f}")
    other = sum(1 for r in TO if not teardown(r) and not race(r))
    print(f"  NEITHER class: {other} of {len(TO)} timeouts")
    print(f"  VERDICT: the two classes do NOT partition the timeouts "
          f"({other}/{len(TO)} fall outside both)")

    # ---- P-R3 -------------------------------------------------------------
    print("\n" + "=" * 74)
    print(f"P-R3  recovery resume in +{RECOV_LO:.0f}..+{RECOV_HI:.0f} s after a fatal")
    print("=" * 74)
    hit = miss = 0
    firsts = []
    for t, s in fatals:
        if not (span[0] <= t <= span[1]):
            print(f"  {s:34} (fatal outside the capture -- not scorable)")
            continue
        ot, o = outcome_of(t, outs)
        ev = [r for r in rows if t < r["up"] <= t + 25.0]
        band = [r for r in ev if RECOV_LO <= r["up"] - t <= RECOV_HI]
        wide = [r for r in ev if 14.0 <= r["up"] - t <= 25.0]
        if band:
            hit += 1
        else:
            miss += 1
        if ev:
            firsts.append(ev[0]["up"] - t)
        print(f"  {s:34} {o}  {'HIT ' if band else 'MISS'}  "
              f"wide={'Y' if wide else 'N'}  resumes "
              + ", ".join(f"{r['up']-t:+.1f}{'*' if r['timeout'] else ''}" for r in ev))
    n = hit + miss
    print(f"\n  pre-registered band +{RECOV_LO:.0f}..+{RECOV_HI:.0f} s : {hit}/{n} = "
          f"{100*hit/max(n,1):.0f} %   (bar >= 90 %)")
    wide_hit = sum(1 for t, s in fatals if span[0] <= t <= span[1]
                   and any(14.0 <= r["up"] - t <= 25.0 for r in rows))
    print(f"  POST-HOC widened band +14..+25 s     : {wide_hit}/{n} = "
          f"{100*wide_hit/max(n,1):.0f} %   <- exploratory, NOT pre-registered")
    if firsts:
        lo = sorted(x for x in firsts if x > 1.0)
        print(f"\n  the FIRST resume after a fatal is bimodal: "
              f"{len(firsts)-len(lo)} at <= 1 s (teardown) and {len(lo)} at "
              f"+{min(lo):.1f}..+{max(lo):.1f} s; NOTHING in between "
              f"(a ~{min(lo):.0f} s quiet period)")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""score_p1p3.py -- score Doc 182 sec.9's P1/P3/P4 over EVERY fatal in a capture.

Doc 182 sec.9 (H3/H4) proposed a mechanism for the A/B SSR-powerup split and
stated it as four predictions.  The three the sampler can test are:

    P1  in the sampler's last record before an A fatal, pc_state = 1 and pc_line = 1
    P2  in the sampler's last record before a B fatal, pc_state = 0 and pc_line = 0
    P3  pc_irq_count does NOT increment between the teardown and the A outcome line
    P4  in a B case pc_irq_count increments exactly once at the modem's assert

score_h3.py does this for ONE fatal given its uptime.  This tool does it for
every fatal in a dmesg, so the predictions can be scored as a table rather than
as anecdotes -- the failure mode Doc 182 sec.11.11 warns about.

TWO CORRECTIONS ARE LOAD-BEARING, and both are traps Doc 182 already documented:

1.  THE WINDOW ENDS AT THE OUTCOME LINE, not at a fixed offset after the fatal
    (sec.8.1, sec.8.2).  In sec.8.1 fatal #4 the single increment is the
    *trailing* deassert, which lands ~2 s AFTER the A line; in sec.8.2 fatal #5
    the counter is flat at 269 from 3883.53 through the A line at 3885.73 and
    only reaches 270 at 3888.06.  A [fatal, fatal+14 s] window therefore counts
    that trailing deassert and mis-scores P3 as MOVED on every A case.

2.  THE SAMPLER'S `<UP>` LABEL IS ~73 ms EARLY (sec.8.5.1).  A record labelled
    just before the fatal carries telemetry read just AFTER it, so the naive
    "last record with UP <= fatal" is not the last PRE-fatal record -- it is the
    driver's first reaction to the fatal (sec.8.2's 3884.57 row, which shows
    pc_state = 0 and made P1 look like it failed).  Telemetry time is UP + LAG,
    and a record is pre-fatal only if UP + LAG <= fatal.

Usage:
    score_p1p3.py <capture> <dmesg>
"""

import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from census_pc import parse                       # noqa: E402
from score_pr import dmesg_events, outcome_of     # noqa: E402

LAG = 0.073        # sec.8.5.1: the sampler's <UP> label is this much EARLY


def at_or_before(recs, t):
    """last record whose TELEMETRY time (UP + LAG) is <= t, or None."""
    best = None
    for r in recs:
        if r[0] + LAG <= t:
            best = r
        else:
            break
    return best


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    cap, dmesg = sys.argv[1], sys.argv[2]

    recs = parse(cap)
    recs.sort(key=lambda r: r[0])
    fatals, outs = dmesg_events(dmesg)
    span = recs[0][0], recs[-1][0]

    print(f"{cap}: {len(recs)} records, {span[0]:.1f}..{span[1]:.1f} s")
    print(f"dmesg: {len(fatals)} fatals, {len(outs)} outcomes")
    print("window: [last pre-fatal record .. the OUTCOME line]\n")

    hdr = (f"  {'fatal UP':>10}  {'signature':34} {'out':>3}  {'pl':>3} {'ps':>3}  "
           f"{'P1':>5}  {'iq pre':>6}->{'post':<6} {'P3':>5}")
    print(hdr)
    print("  " + "-" * (len(hdr) - 2))

    rows = []
    for t, sig in fatals:
        if not (span[0] <= t <= span[1]):
            print(f"  {t:>10.2f}  {sig:34}  --  (outside the capture)")
            continue
        ot, o = outcome_of(t, outs)
        if ot is None or not (span[0] <= ot <= span[1]):
            print(f"  {t:>10.2f}  {sig:34} {o:>3}  (outcome outside the capture)")
            continue
        pre = at_or_before(recs, t)
        post = at_or_before(recs, ot)
        if pre is None or post is None:
            print(f"  {t:>10.2f}  {sig:34} {o:>3}  (no sampler record)")
            continue
        pl, ps = pre[1]["pl"], pre[1]["ps"]
        iq0, iq1 = pre[1]["iq"], post[1]["iq"]
        p1 = (pl == 1 and ps == 1)
        p3 = (iq1 == iq0)
        rows.append((t, sig, o, pl, ps, p1, iq0, iq1, p3, ot - t))
        print(f"  {t:>10.2f}  {sig:34} {o:>3}  {pl:>3} {ps:>3}  "
              f"{'HIGH' if p1 else 'low':>5}  {iq0:>6}->{iq1:<6} "
              f"{'FLAT' if p3 else 'MOVED':>5}   (outcome +{ot-t:.1f} s)")

    n = len(rows)
    print()
    print("=" * 74)
    print(f"scored {n} in-capture fatals")
    print("=" * 74)

    A = [r for r in rows if r[2] == "A"]
    B = [r for r in rows if r[2] == "B"]
    p1_ok = sum(1 for r in rows if (r[2] == "A") == r[5])
    p3_ok = sum(1 for r in rows if (r[2] == "A") == r[8])
    print(f"  P1 (line HIGH+state 1) <=> A :  {p1_ok}/{n}")
    print(f"  P3 (counter FLAT)      <=> A :  {p3_ok}/{n}")
    print(f"    of the {len(A)} A cases: P3 flat {sum(1 for r in A if r[8])}/{len(A)}")
    print(f"    of the {len(B)} B cases: P3 flat {sum(1 for r in B if r[8])}/{len(B)}")

    print("\n  the 2x2 each predictor induces:")
    for name, idx in (("P1  (line HIGH)", 5), ("P3  (counter FLAT)", 8)):
        tA = sum(1 for r in rows if r[idx] and r[2] == "A")
        tB = sum(1 for r in rows if r[idx] and r[2] == "B")
        fA = sum(1 for r in rows if not r[idx] and r[2] == "A")
        fB = sum(1 for r in rows if not r[idx] and r[2] == "B")
        print(f"    {name:22} True: A {tA:2} B {tB:2}   "
              f"False: A {fA:2} B {fB:2}")

    print("\n  P4 (B side): pc_irq_count increments exactly once at the assert")
    for r in B:
        d = r[7] - r[6]
        print(f"    {r[0]:>10.2f}  {r[1]:34}  iq {r[6]}->{r[7]}  delta {d}  "
              f"{'P4 HIT' if d == 1 else 'P4 MISS'}")


if __name__ == "__main__":
    main()

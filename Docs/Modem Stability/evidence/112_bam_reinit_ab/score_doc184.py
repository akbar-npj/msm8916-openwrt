#!/usr/bin/env python3
"""score_doc184.py -- score Doc 184's pre-registered predictions P-R4 and P-R5.

Doc 184 (WIP) extends Doc 183's predictions.  Two new pre-registrations, both
scored from the same pcfine sampler and dmesg that Doc 183 used:

  P-R4  the timeout rate as a function of since_susp, binned at 0.5 s
        Pre-registered CONFIRM: rate at bucket [0.0, 0.5) > rate at bucket [1.0, 1.5)
        by at least 4x.  This is P-R1's "is it really a rate?" test made continuous
        -- it asks whether the 1.25 s cut was a true boundary (P-R1 falsified it as
        one) or whether the rate smoothly decays (a rate is a continuum, not a
        single threshold).

  P-R5  the 17 A and 4 B fatals split on a userspace/traffic feature at fatal time
        Pre-registered FALSIFY: NONE of the five features
          (a) rx_callbacks rate over the 10 s before the fatal
          (b) cmd_open delta over the 30 s before the fatal
          (c) pc_vote_tx_count / pc_unvote_tx_count ratio at the fatal
          (d) pc_irq_count increments in the 5 s before the fatal
          (e) pc_ack_irq_count increments in the 5 s before the fatal
        separate the 17 A's from the 4 B's at Fisher one-sided p < 0.05.
        Reasoning (Doc 183 sec.10): the ~15 s post-fatal quiet period is decoupled
        from the resync, so userspace-driven activity should not be the discriminator.
        If NONE separate, A/B is a kernel/baseband property -- which is consistent
        with Doc 183's H4 (the pc_irq thread run is the kernel-side discriminator).

Both pre-registrations were written BEFORE this script ran against the new capture.

Usage:
    score_doc184.py <capture> <dmesg>
"""

import math
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from census_pc import parse                       # noqa: E402
from score_pr import dmesg_events, outcome_of     # noqa: E402
from resume_trigger import census, rate_before   # noqa: E402

BUCKET = 0.5         # P-R4's bin width, fixed in advance
PR4_HI = (0.0, 0.5)  # P-R4's "close to suspend" bucket
PR4_LO = (1.0, 1.5)  # P-R4's "far from suspend" bucket
PR4_MIN_RATIO = 4.0  # P-R4's confirm threshold (close rate >= 4x far rate)

PR5_WINDOW_PC = 5.0   # (d)/(e) window before the fatal (s)
PR5_WINDOW_RC = 10.0  # (a) rx_callbacks window (s)
PR5_WINDOW_CO = 30.0  # (b) cmd_open window (s)
PR5_PVAL = 0.05       # (any predictor) Fisher one-sided alpha


def fisher(a, b, c, d):
    """One-sided (greater) Fisher exact p for [[a,b],[c,d]]."""
    n = a + b + c + d
    if n == 0:
        return 1.0
    return sum(math.comb(a + c, x) * math.comb(b + d, a + b - x) / math.comb(n, a + b)
               for x in range(a, min(a + b, a + c) + 1))


def increments_before(recs, t, key, span):
    """Number of `key` increments with timestamp in (t - span, t]."""
    base = None
    for u, d in recs:
        if u <= t - span:
            base = d[key]
        if u > t:
            break
    end = None
    for u, d in recs:
        if u <= t:
            end = d[key]
        else:
            break
    if base is None or end is None:
        return None
    return max(0, end - base)


def at_record(recs, t):
    """The last record whose TELEMETRY time (UP + LAG) is <= t."""
    LAG = 0.073        # Doc 182 sec.8.5.1
    best = None
    for u, d in recs:
        if u + LAG <= t:
            best = (u, d)
        else:
            break
    return best


def pr4(rows):
    """P-R4: timeout rate by 0.5 s bucket of since_susp."""
    print("=" * 74)
    print(f"P-R4  since_susp buckets of {BUCKET} s  (rate should DECAY with bucket)")
    print("=" * 74)
    print(f"  {'bucket':>10}  {'resumes':>7}  {'timeouts':>8}  {'rate':>6}")
    buckets = {}
    for r in rows:
        ss = r["since_susp"]
        if ss is None:
            continue
        b = int(ss // BUCKET) * BUCKET
        buckets.setdefault(b, [0, 0])
        buckets[b][0] += 1
        buckets[b][1] += int(r["timeout"])
    pre_close = pre_far = 0, 0
    for b in sorted(buckets):
        n, t = buckets[b]
        rate = 100.0 * t / n if n else 0.0
        flag = ""
        if PR4_HI[0] <= b < PR4_HI[1]:
            pre_close = t, n
            flag = " <- close"
        if PR4_LO[0] <= b < PR4_LO[1]:
            pre_far = t, n
            flag = " <- far"
        print(f"  {b:>10.2f}  {n:>7}  {t:>8}  {rate:>5.1f}%{flag}")
    if pre_close[1] and pre_far[1]:
        rc = 100.0 * pre_close[0] / pre_close[1]
        rf = 100.0 * pre_far[0] / pre_far[1]
        ratio = (rc / rf) if rf > 0 else float("inf")
        verdict = ("CONFIRMED" if ratio >= PR4_MIN_RATIO else
                   f"NOT CONFIRMED (ratio {ratio:.2f} < {PR4_MIN_RATIO})")
        print(f"\n  close [{PR4_HI[0]}..{PR4_HI[1]}): {pre_close[0]}/{pre_close[1]} = {rc:.1f}%")
        print(f"  far   [{PR4_LO[0]}..{PR4_LO[1]}): {pre_far[0]}/{pre_far[1]} = {rf:.1f}%")
        print(f"  ratio: {ratio:.2f}x   -> P-R4 {verdict}")


def pr5(recs, fatals, outs):
    """P-R5: the five channel-state features at every in-capture fatal."""
    print()
    print("=" * 74)
    print(f"P-R5  channel-state features at fatal time  (pre-registered FALSIFY)")
    print("=" * 74)
    span = recs[0][0], recs[-1][0]
    rows = []
    for t, sig in fatals:
        if not (span[0] <= t <= span[1]):
            continue
        ot, o = outcome_of(t, outs)
        if ot is None or not (span[0] <= ot <= span[1]):
            continue
        rec = at_record(recs, t)
        if rec is None:
            continue
        u, d = rec
        # (a) rx_callbacks rate over the 10 s before
        rc_rate = rate_before(recs, t, PR5_WINDOW_RC)
        # (b) cmd_open delta over the 30 s before
        co_delta = increments_before(recs, t, "co", PR5_WINDOW_CO)
        # (c) vt/vu ratio at the fatal
        vt_vu = (d["vt"], d["vu"])
        # (d) pc_irq_count increments in the 5 s before
        iq_pre = increments_before(recs, t, "iq", PR5_WINDOW_PC)
        # (e) pc_ack_irq_count increments in the 5 s before
        aq_pre = increments_before(recs, t, "aq", PR5_WINDOW_PC)
        rows.append((t, sig, o, rc_rate, co_delta, vt_vu, iq_pre, aq_pre))

    print(f"  scored {len(rows)} in-capture fatals")
    A = [r for r in rows if r[2] == "A"]
    B = [r for r in rows if r[2] == "B"]
    print(f"    A: {len(A)}    B: {len(B)}")

    headers = ("fatal UP", "signature", "out", "rc/s", "d(co)", "vt/vu", "d(iq)", "d(aq)")
    print(f"  {headers[0]:>10}  {headers[1]:34} {headers[2]:>3}  "
          f"{headers[3]:>5}  {headers[4]:>5}  {headers[5]:>9}  "
          f"{headers[6]:>5}  {headers[7]:>5}")
    for r in rows:
        u, s, o, rc, co, vt, iq, aq = r
        rc_s = "n/a" if rc is None else f"{rc:5.1f}"
        co_s = "n/a" if co is None else f"{co:>5}"
        vt_s = f"{vt[0]:>4}/{vt[1]:<4}"
        iq_s = "n/a" if iq is None else f"{iq:>5}"
        aq_s = "n/a" if aq is None else f"{aq:>5}"
        print(f"  {u:>10.2f}  {s:34} {o:>3}  {rc_s}  {co_s}  {vt_s}  "
              f"{iq_s}  {aq_s}")

    # ---- feature-by-feature Fisher test (median split, fixed pre-scoring) ----
    print()
    print(f"  per-feature Fisher (median split, one-sided greater, alpha {PR5_PVAL})")
    predictors = [
        ("(a) rc/s",          lambda r: r[3]),
        ("(b) d(co)",         lambda r: r[4]),
        ("(c) vt/vu - 1",     lambda r: (r[5][0] - r[5][1]) if r[5][1] else None),
        ("(d) d(iq)",         lambda r: r[6]),
        ("(e) d(aq)",         lambda r: r[7]),
    ]
    hits = []
    for name, f in predictors:
        a_vals = [v for v in (f(r) for r in A) if v is not None]
        b_vals = [v for v in (f(r) for r in B) if v is not None]
        if not a_vals or not b_vals:
            print(f"  {name:18}: (insufficient data)")
            continue
        med = sorted(a_vals + b_vals)[len(a_vals + b_vals) // 2]
        a_hi = sum(1 for v in a_vals if v > med)
        a_lo = len(a_vals) - a_hi
        b_hi = sum(1 for v in b_vals if v > med)
        b_lo = len(b_vals) - b_hi
        if (a_hi + b_hi) == 0 or (a_lo + b_lo) == 0:
            print(f"  {name:18}: (degenerate)")
            continue
        p = fisher(a_hi, a_lo, b_hi, b_lo)
        ok = "HIT" if p < PR5_PVAL else "miss"
        if p < PR5_PVAL:
            hits.append(name)
        print(f"  {name:18}: A hi/lo {a_hi:>2}/{a_lo:<2}   B hi/lo {b_hi:>2}/{b_lo:<2}   "
              f"p = {p:.4f}   {ok}")

    print()
    if hits:
        print(f"  VERDICT: P-R5 NOT falsified -- {len(hits)} feature(s) separate A from B:")
        for h in hits:
            print(f"    {h}")
    else:
        print(f"  VERDICT: P-R5 FALSIFIED -- NONE of the five features separate A from B at p < {PR5_PVAL}")
        print(f"  the A/B outcome is DECOUPLED from userspace/traffic state at the fatal")


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    cap, dmesg = sys.argv[1], sys.argv[2]
    recs = parse(cap)
    rows = census(recs)
    fatals, outs = dmesg_events(dmesg)
    print(f"{cap}: {len(recs)} records, {recs[0][0]:.1f}..{recs[-1][0]:.1f} s")
    print(f"resumes {len(rows)}  timeouts {sum(r['timeout'] for r in rows)}")
    print()
    pr4(rows)
    pr5(recs, fatals, outs)


if __name__ == "__main__":
    main()
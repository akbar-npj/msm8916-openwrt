#!/usr/bin/env python3
"""resume_trigger.py -- what makes a bam_dmux runtime_resume happen, and what
makes one time out?

Doc 182 sec.11.9 / sec.13 item 2.  The timeout was reduced to

    timeout <=> a bam_dmux_runtime_resume() runs while the pc line is already
                asserted

so the open question is the ANTECEDENT: what makes a resume run at all, and
why does only a small fraction of them time out?

Read from source, the SSR path itself never triggers one -- the teardown work
only calls pm_runtime_set_suspended() (:2311) and the powerup work only calls
pm_runtime_set_active() (:2413).  Every actual resume comes from outside:

    :501  bam_dmux_send_cmd()           -- CMD_OPEN / CMD_CLOSE (netdev open/stop)
    :559  bam_dmux_netdev_open()        -- userspace bringing wwan0 up
    :667  bam_dmux_netdev_start_xmit()  -- an AP TX packet
    :739  bam_dmux_tx_wakeup_work()     -- draining deferred TX

all of which are TRAFFIC or USERSPACE events.

bam_dmux_runtime_resume() increments pm_resume_attempts (mra, :2057), so a
resume is exactly an mra increment, and pc_timeout_count (to) has exactly two
writers, both inside that function (:2069 the 2000 ms pc-ack wait, :2078 the
1000 ms pc_state wait).  dmesg names which one fired.

This script therefore:

  1. censuses EVERY mra increment in the capture (not just the fatal windows);
  2. for each, records the local state (pl/ps/td), the traffic rate before it,
     the time since the previous suspend, and whether a pc-ack timeout followed;
  3. scans candidate predictors for separation between the timeouts and the rest;
  4. optionally reports named windows (LABEL:T_fatal[:T_end]).

Usage:
    resume_trigger.py <capture> [LABEL:T_fatal[:T_end] ...]

Capture format: see census_pc.py (both sampler formats accepted).
"""

import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from census_pc import parse  # noqa: E402

EVENT_KEYS = ("iq", "aq", "to", "rs", "ra", "td", "rc", "vt", "vu", "mra", "msa", "co")


def increments(recs):
    """[(index, uptime, dict, prev_dict)] for every counter increment."""
    out = {k: [] for k in EVENT_KEYS}
    prev = None
    for i, (up, d) in enumerate(recs):
        if prev is not None:
            for k in EVENT_KEYS:
                if d[k] != prev[k]:
                    out[k].append((i, up, d, prev))
        prev = d
    return out


def first_event_after(recs, i, base, key, span):
    """Uptime of the first increment of `key` in (recs[i].UP, recs[i].UP+span]."""
    up0 = recs[i][0]
    j = i
    while j < len(recs) and recs[j][0] <= up0 + span:
        if recs[j][1][key] > base:
            return recs[j][0]
        j += 1
    return None


def rate_before(recs, up, span):
    """Per-second rate of rx_callbacks over [up-span, up]; None if unresolved."""
    lo = hi = None
    for u, d in recs:
        if u <= up - span:
            lo = (u, d)
        if u <= up:
            hi = (u, d)
        if u > up:
            break
    if lo is None or hi is None or hi[0] <= lo[0]:
        return None
    return (hi[1]["rc"] - lo[1]["rc"]) / (hi[0] - lo[0])


def census(recs, span=3.0):
    inc = increments(recs)
    resumes = inc["mra"]
    suspends = [u for _, u, _, _ in inc["msa"]]

    rows = []
    for i, up, d, p in resumes:
        # time since the most recent suspend increment
        prior = [u for u in suspends if u <= up]
        since_susp = up - prior[-1] if prior else None
        rows.append(dict(
            i=i, up=up, pl=d["pl"], ps=d["ps"], td=d["td"],
            r10=rate_before(recs, up, 10.0),
            r60=rate_before(recs, up, 60.0),
            since_susp=since_susp,
            # a completion event is a pc_irq or pc-ack-irq increment, whether it
            # lands in the same sampler record as the resume or in a later one
            same_iq=(d["iq"] > p["iq"]), after_iq=first_event_after(recs, i, d["iq"], "iq", span),
            same_aq=(d["aq"] > p["aq"]), after_aq=first_event_after(recs, i, d["aq"], "aq", span),
            tmo=first_event_after(recs, i, d["to"], "to", span),
        ))
    for r in rows:
        r["ev"] = bool(r["same_iq"] or r["after_iq"] or r["same_aq"] or r["after_aq"])
        r["timeout"] = r["tmo"] is not None
    return rows


def report_census(rows, recs):
    to = [r for r in rows if r["timeout"]]
    ok = [r for r in rows if not r["timeout"]]
    dur = recs[-1][0] - recs[0][0]
    print(f"\n== whole-capture resume census ==")
    print(f"  capture            : {recs[0][0]:.1f} .. {recs[-1][0]:.1f} s ({dur:.0f} s)")
    print(f"  resumes (mra++)    : {len(rows)}   (one per {dur/max(len(rows),1):.1f} s)")
    print(f"  timeouts (to++)    : {len(to)}  = {100.0*len(to)/max(len(rows),1):.1f} % of resumes")

    print("\n  -- the timeouts --")
    print("        UP    pl  ps  td   rc/s(-10s)  since_susp  completion  delay")
    for r in to:
        r10 = "n/a" if r["r10"] is None else f"{r['r10']:.1f}"
        ss = "n/a" if r["since_susp"] is None else f"{r['since_susp']:.3f}"
        print(f"  {r['up']:9.3f}  {r['pl']:3} {r['ps']:3} {r['td']:3}   "
              f"{r10:>9}   {ss:>10}   "
              f"{'YES' if r['ev'] else 'NONE':>10}   {r['tmo'] - r['up']:+.3f}s")

    print("\n  -- completion event (pc_irq or pc-ack irq) at or within 3 s --")
    print(f"     timeout : event {len([r for r in to if r['ev']])}/{len(to)}"
          f"   no-event {len([r for r in to if not r['ev']])}/{len(to)}")
    print(f"     clean   : event {len([r for r in ok if r['ev']])}/{len(ok)}"
          f"   no-event {len([r for r in ok if not r['ev']])}/{len(ok)}")

    print("\n  -- separation on since_susp (time since the previous suspend) --")
    sv = [r["since_susp"] for r in to if r["since_susp"] is not None]
    wv = [r["since_susp"] for r in ok if r["since_susp"] is not None]
    if sv and wv:
        sv.sort(); wv.sort()
        print(f"     timeout : n={len(sv):3}  min {sv[0]:8.3f}  med {sv[len(sv)//2]:8.3f}  max {sv[-1]:8.3f}")
        print(f"     clean   : n={len(wv):3}  min {wv[0]:8.3f}  med {wv[len(wv)//2]:8.3f}  max {wv[-1]:8.3f}")
        print("     threshold scan (a data-chosen cut -- exploratory, not pre-registered):")
        for th in (0.5, 1.0, 1.25, 1.5, 2.0, 3.0, 5.0, 10.0):
            a = len([r for r in to if r["since_susp"] is not None and r["since_susp"] <= th])
            b = len([r for r in ok if r["since_susp"] is not None and r["since_susp"] <= th])
            print(f"       <= {th:5.2f}s : timeout {a}/{len(to)}   clean {b}/{len(ok)}")

    print("\n  -- separation on the traffic rate (the pre-registered busy/idle cut) --")
    rv = sorted(r["r10"] for r in to if r["r10"] is not None)
    rw = sorted(r["r10"] for r in ok if r["r10"] is not None)
    if rv and rw:
        print(f"     timeout : n={len(rv):3}  min {rv[0]:8.1f}  med {rv[len(rv)//2]:8.1f}  max {rv[-1]:8.1f}")
        print(f"     clean   : n={len(rw):3}  min {rw[0]:8.1f}  med {rw[len(rw)//2]:8.1f}  max {rw[-1]:8.1f}")


def report_windows(recs, spec):
    parts = spec.split(":")
    if len(parts) == 2:
        parts.append(str(float(parts[1]) + 25.0))
    if len(parts) != 3:
        sys.exit(f"bad spec {spec!r}; want LABEL:T_fatal[:T_end]")
    label, t0, t1 = parts[0], float(parts[1]), float(parts[2])

    pre = None
    for up, d in recs:
        if up <= t0:
            pre = (up, d)
        else:
            break
    print(f"\n== {label}: fatal at {t0} (window to {t1}) ==")
    if pre is None:
        print("  (no pre-event record)")
        return
    pup, pd = pre
    active = (pd["vt"] > pd["vu"]) or (pd["mra"] > pd["msa"])
    print(f"  PM pair at the fatal     : vt={pd['vt']} vu={pd['vu']} "
          f"mra={pd['mra']} msa={pd['msa']}  -> {'ACTIVE' if active else 'SUSPENDED'}")
    for span in (60, 10):
        r = rate_before(recs, t0, span)
        print(f"  rx_callbacks rate, -{span:>2}s  : "
              f"{'n/a' if r is None else f'{r:8.1f} /s'}")

    win = [(up, d) for up, d in recs if t0 < up <= t1]
    if not win:
        print("  (no records in the window)")
        return
    # count TRANSITIONS, not "every record after the first change"
    res, prev = [], pd
    tmo = []
    for up, d in win:
        if d["mra"] > prev["mra"]:
            res.append(up)
        if d["to"] > prev["to"]:
            tmo.append(up)
        prev = d
    if res:
        print(f"  resume(s) after fatal    : YES  x{len(res)}  at "
              + ", ".join(f"{u - t0:+.2f}s" for u in res))
    else:
        print(f"  resume(s) after fatal    : NONE (mra flat at {pd['mra']} "
              f"to UP={win[-1][0]:.2f})")
    if tmo:
        print(f"  timeout after fatal      : YES at {tmo[0]:.2f} "
              f"({tmo[0] - t0:+.2f}s from the fatal)")
    else:
        print(f"  timeout after fatal      : NONE (to flat at {pd['to']})")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    recs = parse(path)
    if not recs:
        sys.exit(f"{path}: no records parsed")
    print(f"{path}: {len(recs)} records, uptime {recs[0][0]:.2f} .. {recs[-1][0]:.2f} s")
    report_census(census(recs), recs)
    for spec in sys.argv[2:]:
        report_windows(recs, spec)


if __name__ == "__main__":
    main()

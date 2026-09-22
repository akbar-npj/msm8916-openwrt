#!/usr/bin/env python3
"""score_resume_window.py -- does a pc-ack timeout REQUIRE a runtime resume?

Reproduces Doc 182 sec.8.6.  The claim under test:

    pc_timeout_count has exactly two writers, both inside
    bam_dmux_runtime_resume() (qcom_bam_dmux.c:2069 and :2078).  Therefore a
    timeout is IMPOSSIBLE unless bam_dmux_runtime_resume() runs.

bam_dmux_runtime_resume() increments pm_resume_attempts (mra, :2057) and its
vote increments pc_vote_tx_count (vt, :2063).  Both are part of the sampler's
change key, so "did a resume run in this window?" is answerable exactly.

For each labelled window the script reports:

  * the last record at or before T0 -- the pre-event PM state.  vt > vu (or
    mra > msa) means a resume was already outstanding, i.e. the device was
    runtime-ACTIVE; all-equal means it was suspended.
  * whether mra incremented in (T0, T1] -- i.e. a NEW resume ran.
  * whether to  incremented in (T0, T1] -- i.e. a timeout occurred, and the
    delay from the resume to the timeout (expected ~2000 ms, the
    msecs_to_jiffies(2000) at :2067).

Usage:
    score_resume_window.py <capture> LABEL:T0:T1 [LABEL:T0:T1 ...]

Example (boot 59d9c272, from Doc 182 sec.8.6):
    score_resume_window.py pcfine_f7_boot59d9c272.txt \
        fatal5:3884.6:3888.1 fatal6:4808.1:4811.6 \
        orphan:4184.1:4187.0 fatal7:5709.4:5713.5

Capture format: see census_pc.py (both sampler formats are accepted).
"""

import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from census_pc import parse  # noqa: E402


def analyse(recs, label, t0, t1):
    pre = None
    for up, d in recs:
        if up <= t0:
            pre = (up, d)
        else:
            break
    win = [(up, d) for up, d in recs if t0 < up <= t1]

    print(f"\n== {label}: window {t0} .. {t1} ==")
    if pre is None:
        print("  (no pre-event record -- window starts before the capture)")
        return
    pup, pd = pre
    active = (pd["vt"] > pd["vu"]) or (pd["mra"] > pd["msa"])
    print(f"  last record at/before T0 : UP={pup:.2f}  "
          f"vt={pd['vt']} vu={pd['vu']} mra={pd['mra']} msa={pd['msa']}  "
          f"-> {'ACTIVE' if active else 'suspended'}")

    if not win:
        print("  (no records inside the window)")
        return

    # A resume is an mra increment; the vote (vt) moves with it but the
    # unvote/vote pair can both move, so key on mra -- it has one writer.
    res = [u for u, d in win if d["mra"] > pd["mra"]]
    tmo = [u for u, d in win if d["to"] > pd["to"]]

    if res:
        print(f"  resume in window         : YES, first at UP={res[0]:.2f} "
              f"(mra {pd['mra']} -> {pd['mra'] + 1})")
    else:
        print("  resume in window         : NO  (mra flat at "
              f"{pd['mra']} through UP={win[-1][0]:.2f})")

    if tmo:
        d = tmo[0] - res[0] if res else float("nan")
        print(f"  timeout in window        : YES, first at UP={tmo[0]:.2f} "
              f"(to {pd['to']} -> {pd['to'] + 1}), "
              f"resume->timeout = {d:.2f} s")
    else:
        print(f"  timeout in window        : NO  (to flat at {pd['to']} "
              f"through UP={win[-1][0]:.2f})")

    verdict = "timeout" if tmo else "no timeout"
    if not res and not tmo:
        verdict += "  <-- consistent: no resume, so no timeout"
    elif res and tmo:
        verdict += "  <-- consistent: resume, wait expired"
    elif res and not tmo:
        verdict += "  <-- resume completed (line transition or ack)"
    else:
        verdict += "  <-- *** INCONSISTENT: timeout with no resume ***"
    print(f"  verdict                  : {verdict}")


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    path = sys.argv[1]
    recs = parse(path)
    if not recs:
        sys.exit(f"{path}: no records parsed")
    print(f"{path}: {len(recs)} records, "
          f"uptime {recs[0][0]:.2f} .. {recs[-1][0]:.2f} s")
    for spec in sys.argv[2:]:
        parts = spec.split(":")
        if len(parts) != 3:
            sys.exit(f"bad window spec {spec!r}; want LABEL:T0:T1")
        analyse(recs, parts[0], float(parts[1]), float(parts[2]))


if __name__ == "__main__":
    main()

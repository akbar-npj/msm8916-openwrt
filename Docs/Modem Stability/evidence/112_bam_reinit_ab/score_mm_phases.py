#!/usr/bin/env python3
"""
score_mm_phases.py -- score the ModemManager recovery phases after each SSR.

Pre-registration: Docs/Modem Stability/evidence/112_bam_reinit_ab/preregistration_2026-09-22_build.md
Part B.  Frozen predictions:

  P-MM1  add -> modem object created falls from 4.0 s (9/11 exactly 4, 2/11 exactly 5)
         to <= 2.0 s;  FALSIFIED if the median > 2.5 s
  P-MM2  (control) every SSR still yields ALL 9 ports (8 x net/wwanN + 1 x wwan/wwan0qmi0)
         with no `missing net port`;  FALSIFIED if any SSR has < 9 ports
  P-MM3  (secondary) add -> Interface up falls by >= 1.5 s from a pre-fix median of 16 s;
         FALSIFIED if the median falls by < 1.0 s

CALIBRATION (do not trust this script until it reproduces these):
  Run against the pre-fix capture
    evidence/170_two_fixes_for_the_smd_poll_uaf/bootA_a9fd907c_logroll.log.gz
  it must report, over 11 SSRs:
    add->created   4 s in 9, 5 s in 2   (median 4)
    created->SIM   5 s in 9, 6 s in 2
    add->up        median 16 s, 10 of 11 in 14..17, one at 25
    ports          9/9 in every SSR

INSTRUMENT NOTES (from the corpus's own trap list -- read before editing):
  * The logread stream in the Doc 170 capture is BATCHED and the timestamps have
    ONE-SECOND granularity.  Every duration here is therefore an integer number of
    seconds and a "+3 s" row means "2000-2999 ms", not a second mechanism.  Do not
    add sub-second arithmetic to this file.
  * `creating modem with plugin ... and 'N' ports` appears TWICE per SSR: the first
    attempt (which fails, port unprobed) and the successful one.  N is the PORT LIST
    size at that moment, which is NOT the created modem's port set -- use the
    per-port `[modemN] ...` lines for P-MM2.
  * The `additional_port ... will retry` line belongs to the FAILED attempt, not the
    successful one.  Do not use it as the success marker.
"""

import gzip
import re
import statistics
import sys

# --- the log grammar -------------------------------------------------------
# logread line:  "Mon Sep 21 13:38:08 2026 daemon.info ModemManager[12589]: ..."
#             or "Mon Sep 21 13:38:08 2026 daemon.notice [2593]: <msg> [modem12] ..."
TS = re.compile(r"^\w{3} \w{3} +\d+ (\d{2}):(\d{2}):(\d{2}) (\d{4}) ")

ADD = "hotplug: add wwan control port wwan0qmi0"
PROBE_START = "probe step: start"
CREATING = re.compile(r"creating modem with plugin '([^']+)' and '(\d+)' ports")
CREATED = "successfully created"
RUNNING_SETUP = "running setup for device"
SIM_OK = "SIM hot swap setup succeeded"
DISABLED = "state changed (unknown -> disabled)"
IFACE_UP = "Interface 'modem' is now up"
MISSING_NET = "missing net port"
# the created modem's own port list
PORT_LINE = re.compile(r"\[(modem\d+)\]\s+(\S+):\s+(net \(data\)|qmi)$")


def hms(h, m, s):
    return int(h) * 3600 + int(m) * 60 + int(s)


def load(path):
    """Yield (seconds, line) for every timestamped line."""
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", errors="replace") as fh:
        for line in fh:
            mo = TS.match(line)
            if mo:
                yield hms(mo.group(1), mo.group(2), mo.group(3)), line.rstrip("\n")


def ssrs(path):
    """Split the log into one record per SSR, keyed off the hotplug add."""
    out = []
    cur = None
    for sec, line in load(path):
        if ADD in line:
            cur = {"add": sec, "add_line": line, "ports": {}}
            out.append(cur)
            continue
        if cur is None:
            continue
        if PROBE_START in line and "probe" not in cur:
            cur["probe"] = sec
        mo = CREATING.search(line)
        if mo:
            # keep only the LAST attempt before a success, but remember every N
            cur.setdefault("attempts", []).append((sec, int(mo.group(2))))
            cur["creating"] = sec
        if CREATED in line and "created" not in cur:
            cur["created"] = sec
        if RUNNING_SETUP in line and "setup" not in cur:
            cur["setup"] = sec
        if SIM_OK in line and "sim" not in cur:
            cur["sim"] = sec
        if DISABLED in line and "disabled" not in cur:
            cur["disabled"] = sec
        if IFACE_UP in line and "up" not in cur:
            cur["up"] = sec
        if MISSING_NET in line:
            cur["missing_net"] = True
        mo = PORT_LINE.search(line)
        if mo:
            # The port list is logged BEFORE `successfully created`, and a failed
            # attempt logs a partial list too.  Key by modem index and keep the
            # HIGHEST index seen in this SSR -- that is the object that survived.
            idx = int(mo.group(1)[len("modem"):])
            cur.setdefault("ports_by_idx", {}).setdefault(idx, {})
            cur["ports_by_idx"][idx][mo.group(2)] = mo.group(3)
    for cur in out:
        by_idx = cur.get("ports_by_idx", {})
        cur["ports"] = by_idx[max(by_idx)] if by_idx else {}
    return out


def d(a, b):
    return None if a is None or b is None else b - a


def main(paths):
    for path in paths:
        recs = ssrs(path)
        print(f"=== {path} ===")
        print(f"SSRs found: {len(recs)}\n")
        if not recs:
            continue

        hdr = f"{'#':>3} {'add':>8} {'probe':>6} {'creat':>6} {'SIM':>5} {'disab':>6} {'up':>5} " \
              f"{'a->c':>5} {'c->s':>5} {'s->d':>5} {'d->u':>5} {'a->u':>5} {'ports':>5} {'n':>3}"
        print(hdr)
        print("-" * len(hdr))

        a_to_c, c_to_s, s_to_d, d_to_u, a_to_u, nports = [], [], [], [], [], []
        incomplete = []
        for i, r in enumerate(recs, 1):
            # Every phase is optional: a DOUBLE FATAL (the next SSR lands before
            # this one finished recovering) leaves an SSR with `add` and some
            # later phases but NO `Interface 'modem' is now up`.  d() returns
            # None for a missing endpoint, so such an SSR drops out of that
            # phase's statistics instead of crashing the scorer.
            ac = d(r.get("add"), r.get("created"))
            cs = d(r.get("created"), r.get("sim"))
            sd = d(r.get("sim"), r.get("disabled"))
            du = d(r.get("disabled"), r.get("up"))
            au = d(r.get("add"), r.get("up"))
            np_ = len(r["ports"])
            if r.get("up") is None:
                incomplete.append(i)
            for lst, v in ((a_to_c, ac), (c_to_s, cs), (s_to_d, sd),
                           (d_to_u, du), (a_to_u, au), (nports, np_)):
                if v is not None:
                    lst.append(v)

            def f(v):
                return "-" if v is None else f"{v}s"
            print(f"{i:>3} {r['add']:>8} "
                  f"{(d(r['add'], r.get('probe')) if r.get('probe') else 0):>6} "
                  f"{(ac if ac is not None else 0):>6} "
                  f"{(d(r['add'], r.get('sim')) if r.get('sim') else 0):>5} "
                  f"{(d(r['add'], r.get('disabled')) if r.get('disabled') else 0):>6} "
                  f"{(au if au is not None else 0):>5} "
                  f"{f(ac):>5} {f(cs):>5} {f(sd):>5} {f(du):>5} {f(au):>5} "
                  f"{np_:>5} {len(r.get('attempts', [])):>3}"
                  + ("   <-- SUPERSEDED (no Interface-up line)" if r.get("up") is None else ""))

        if incomplete:
            print(f"\n  NOTE: SSR(s) {incomplete} carry no `Interface 'modem' is now up` line.")
            print("        Their recovery was cut short by the NEXT fatal (a double fatal).")
            print("        They are excluded from `add -> up` by construction, but they are")
            print("        NOT excluded from P-MM2 below -- see the note under the verdicts.")

        def summary(name, lst, pre=None):
            if not lst:
                print(f"  {name:<22} (no data)")
                return
            med = statistics.median(lst)
            line = f"  {name:<22} n={len(lst):<3} median={med:g}s  min={min(lst)}  max={max(lst)}"
            if pre is not None:
                line += f"   pre-fix median={pre}s  delta={pre - med:+g}s"
            print(line)

        print()
        summary("add -> created", a_to_c, pre=4)
        summary("created -> SIM", c_to_s, pre=5)
        summary("SIM -> disabled", s_to_d, pre=2)
        summary("disabled -> up", d_to_u, pre=5)
        summary("add -> up", a_to_u, pre=16)
        print(f"  {'ports':<22} min={min(nports) if nports else '-'} "
              f"max={max(nports) if nports else '-'}")

        # ---- the frozen predictions ---------------------------------------
        print("\n  --- pre-registered verdicts ---")
        med_ac = statistics.median(a_to_c) if a_to_c else None
        if med_ac is None:
            print("  P-MM1  INSUFFICIENT DATA")
        elif med_ac <= 2.0:
            print(f"  P-MM1  CONFIRMED   (median {med_ac:g}s <= 2.0s)")
        elif med_ac <= 2.5:
            print(f"  P-MM1  INCONCLUSIVE (median {med_ac:g}s, band 2.0..2.5s)")
        else:
            print(f"  P-MM1  FALSIFIED   (median {med_ac:g}s > 2.5s)")

        bad = [i for i, r in enumerate(recs, 1) if len(r["ports"]) < 9]
        miss = [i for i, r in enumerate(recs, 1) if r.get("missing_net")]
        if not recs:
            print("  P-MM2  INSUFFICIENT DATA")
        elif bad or miss:
            print(f"  P-MM2  FALSIFIED   (SSRs with <9 ports: {bad}; missing-net-port: {miss})")
        else:
            print(f"  P-MM2  CONFIRMED   (9/9 ports in all {len(recs)} SSRs)")
        superseded_bad = [i for i in bad if i in incomplete]
        if superseded_bad:
            print(f"        NOTE: SSR(s) {superseded_bad} are SUPERSEDED (no Interface-up line),")
            print("        so a <9-port list there is an artifact of the recovery being cut")
            print("        short, not a missing port.  The pre-registered rule is unchanged;")
            print("        this is disclosed so the falsification can be judged, not re-tuned.")

        med_au = statistics.median(a_to_u) if a_to_u else None
        if med_au is None:
            print("  P-MM3  INSUFFICIENT DATA")
        else:
            delta = 16 - med_au
            verdict = ("CONFIRMED" if delta >= 1.5 else
                       "INCONCLUSIVE" if delta >= 1.0 else "FALSIFIED")
            print(f"  P-MM3  {verdict:<11} (median {med_au:g}s, delta {delta:+g}s vs pre-fix 16s)")

        print(f"\n  REPORT n = {len(recs)} SSRs.  "
              "P-MM1 needs n>=5, P-MM2 needs n>=11, P-MM3 needs n>=10.\n")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1:])

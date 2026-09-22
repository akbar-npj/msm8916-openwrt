#!/usr/bin/env python3
"""census_pc.py -- pc-line / PM census over a pcfine sampler capture.

Reproduces the measurements in Doc 182 sec.8.4 (the orphan pc-ack timeout) and the
ack-flatness check that qualifies sec.6.

The pcfine sampler writes one record per change of a fixed tuple:

    v2 (key first, "iq aq ps ... co|UP qm"):
        <iq> <aq> <ps> <pl> <to> <rs> <ra> <td> <rc> <vt> <vu> <mra> <msa> <co>|<UP> <qm>
    v1 (key = pc_irq_count only, "iq|aq ps pl ... co UP qm"):
        <iq>|<aq> <ps> <pl> <to> <rs> <ra> <td> <rc> <vt> <vu> <mra> <msa> <co> <UP> <qm>

Field meanings:
    iq  pc_irq_count          aq  pc_ack_irq_count      ps  pc_state
    pl  pc_line_level         to  pc_timeout_count      rs  pc_resync_count
    ra  rx_rearm_count        td  rx_tearing_down        rc  rx_callbacks
    vt  pc_vote_tx_count      vu  pc_unvote_tx_count
    mra pm_resume_attempts    msa pm_suspend_attempts  co  cmd_open
    UP  device uptime (s)     qm  pc_quiesce_ms

Both formats are parsed; v1 simply cannot resolve aq/ps/pl changes that did not
coincide with an iq change, so prefer a v2 capture.

Usage:
    census_pc.py <capture> [--window T0 T1]
"""

import sys

KEYS = ["iq", "aq", "ps", "pl", "to", "rs", "ra", "td",
        "rc", "vt", "vu", "mra", "msa", "co"]


def parse(path):
    """Yield (uptime, dict) per record, handling both sampler formats."""
    out = []
    with open(path, "r", errors="replace") as fh:
        for ln in fh:
            ln = ln.strip()
            if not ln or "|" not in ln:
                continue
            head, tail = ln.split("|", 1)
            h = head.split()
            t = tail.split()
            if len(h) >= 14:                      # v2: key first, 14 fields
                vals = h[:14]
                up = t[0] if t else None
            elif len(t) >= 15:                    # v1: 15 fields after '|'
                vals = [h[0]] + t[:13]
                up = t[13]
            else:
                continue
            try:
                d = {k: int(v) for k, v in zip(KEYS, vals)}
                d["UP"] = float(up)
            except ValueError:
                continue
            out.append((d["UP"], d))
    return out


def transitions(recs, fields, label):
    print(f"\n== {label} ==")
    prev = None
    for up, d in recs:
        if prev is None or any(d[f] != prev[f] for f in fields):
            cols = " ".join(f"{f}={d[f]}" for f in fields)
            print(f"  {up:9.2f}  {cols}")
        prev = d


def flatness(recs, t0, t1, label):
    print(f"\n== {label}: window {t0}..{t1} ==")
    win = [(u, d) for u, d in recs if t0 <= u <= t1]
    if not win:
        print("  (no records in window)")
        return
    for f in ("iq", "aq", "ps", "pl", "to", "td"):
        vals = sorted({d[f] for _, d in win})
        flag = "FLAT" if len(vals) == 1 else "moved"
        print(f"  {f:>3}: {flag:4}  {vals}")
    print(f"  records in window: {len(win)}")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    recs = parse(path)
    print(f"{path}: {len(recs)} records, "
          f"uptime {recs[0][0]:.2f} .. {recs[-1][0]:.2f} s" if recs else "no records")
    if not recs:
        return

    transitions(recs, ["iq", "aq", "ps", "pl", "to"], "pc_irq runs and timeouts")
    transitions(recs, ["vt", "vu", "mra", "msa"], "PM votes / suspend / resume")

    if "--window" in sys.argv:
        i = sys.argv.index("--window")
        flatness(recs, float(sys.argv[i + 1]), float(sys.argv[i + 2]),
                 "flatness check")


if __name__ == "__main__":
    main()

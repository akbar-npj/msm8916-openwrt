#!/usr/bin/env python3
"""
Doc 182 scorer: the bam_dmux SSR powerup outcome (A/B) vs the fatal signature,
and the pc-ack timeout as a consequence of A.

Input  : console_ramoops_prev.txt  -- the pstore console of boot eafa19f6,
         md5 c6253e4bf582b889f73829c278a0c0fc, 1655 lines / 146379 B.

WHY ORDER, NOT TIMESTAMPS
-------------------------
The ramoops console carries bit corruption (the known trap for this device).
Observed in this very capture:

  line  549  "[ 3234.44768]"      -- a digit lost from the fraction
  line 1318  "[13266.8&9229]"     -- '&' substituted for a digit
  line 1364  "[13066.869437]"     -- "13266" rendered as "13066": a bit flip
                                     in the INTEGER part, i.e. a plausible-
                                     looking but wrong time
  line  756  "fatal error receIved" -- capital I for 'l'
  line 1487  "Z15073.101920]"     -- '[' replaced

A scorer that pairs events by timestamp would silently mis-pair on the
13066 case.  This scorer pairs by FILE ORDER, which corruption cannot reorder,
and reports timestamps only as labels.

OUTCOMES (from bam_dmux_ssr_powerup_work_func(), qcom_bam_dmux.c:2402-2423)
-------------------------------------------------------------------------
  A  "successfully reinitialized BAM channels and rings"  -> dmux->rx was NULL
  B  "channels already active"                            -> dmux->rx was set

Both are preceded by an unconditional
  "SSR powerup: modem pc_state=%d (waited %d ms)"
whose argument is pc_line, NOT dmux->pc_state -- the label is a misnomer.
"""

import re
import sys
from collections import Counter

PATH = sys.argv[1] if len(sys.argv) > 1 else "console_ramoops_prev.txt"

# ---- tolerant patterns (corruption-aware) ---------------------------------
RE_TS = re.compile(r"[\[ZK]?\s*(\d{1,6}\.\d{3,6})")          # best-effort label
RE_FATAL = re.compile(r"fatal error\s+rece\w*ved")            # 'received'/'receIved'
RE_A2 = re.compile(r"a2_power\.c:1189")
# 'common_timer' is corrupted to 'common_tiler' on the last fatal (line 1486),
# so the name must be matched with the corrupted middle letter tolerated.
RE_CT = re.compile(r"common_ti\w*er\.c:390")
RE_CMDOPEN = re.compile(r"received CMD_OPEN")
RE_POWERUP = re.compile(r"SSR powerup: modem pc_state=(\d) \(waited (\d+) ms\)")
RE_OUTCOME_A = re.compile(r"successfully reinitialized")
RE_OUTCOME_B = re.compile(r"channels already active")
RE_TIMEOUT = re.compile(r"pc-ack timeout")
RE_WD_RESYNC = re.compile(r"rebuilding|resyncing|lost edge")
RE_WD_QUIESCE = re.compile(r"RX watchdog: quiesced (\d+)s \(pc_state=(\d), pc_line=(\d)")


def ts(line):
    m = RE_TS.search(line)
    return float(m.group(1)) if m else None


def fisher_one_sided(a, b, c, d):
    """P(X >= a) for the 2x2 [[a,b],[c,d]] with fixed margins."""
    from math import comb
    n = a + b + c + d
    r1, c1 = a + b, a + c
    lo = max(0, r1 + c1 - n)
    hi = min(r1, c1)
    tot = comb(n, r1)
    return sum(comb(c1, k) * comb(n - c1, r1 - k) for k in range(a, hi + 1)) / tot


def fisher_two_sided(a, b, c, d):
    from math import comb
    n = a + b + c + d
    r1, c1 = a + b, a + c
    tot = comb(n, r1)
    obs = comb(c1, a) * comb(n - c1, r1 - a) / tot   # NORMALISED
    p = 0.0
    for k in range(max(0, r1 + c1 - n), min(r1, c1) + 1):
        pr = comb(c1, k) * comb(n - c1, r1 - k) / tot
        if pr <= obs * (1 + 1e-9):
            p += pr
    return p


lines = open(PATH, errors="replace").read().splitlines()

# ---- walk the file in order, building an ordered event stream -------------
events = []
pending_fatal = None
pending_powerup = None

for idx, ln in enumerate(lines, 1):
    if RE_FATAL.search(ln):
        sig = "a2_power" if RE_A2.search(ln) else ("common_timer" if RE_CT.search(ln) else "UNREADABLE")
        pending_fatal = {"line": idx, "t": ts(ln), "sig": sig}
        events.append(("fatal", pending_fatal))
    elif RE_POWERUP.search(ln):
        m = RE_POWERUP.search(ln)
        pending_powerup = {"line": idx, "t": ts(ln), "pc_line": int(m.group(1)),
                           "waited": int(m.group(2)), "outcome": None, "timeout": None}
        # attach the fatal that this powerup answers
        if pending_fatal is not None:
            pending_powerup["fatal"] = pending_fatal
            pending_fatal = None
        events.append(("powerup", pending_powerup))
    elif RE_OUTCOME_A.search(ln):
        if pending_powerup is not None and pending_powerup["outcome"] is None:
            pending_powerup["outcome"] = "A"
            pending_powerup["outcome_t"] = ts(ln)
            pending_powerup["outcome_line"] = idx
    elif RE_OUTCOME_B.search(ln):
        if pending_powerup is not None and pending_powerup["outcome"] is None:
            pending_powerup["outcome"] = "B"
            pending_powerup["outcome_t"] = ts(ln)
            pending_powerup["outcome_line"] = idx
    elif RE_TIMEOUT.search(ln):
        if pending_powerup is not None and pending_powerup["timeout"] is None:
            pending_powerup["timeout"] = ts(ln)

powerups = [e[1] for e in events if e[0] == "powerup"]

# ---- line-type census ----------------------------------------------------
print("=" * 78)
print("SOURCE")
print("=" * 78)
print(f"file            : {PATH}")
print(f"lines           : {len(lines)}")
print(f"fatal lines     : {sum(1 for e in events if e[0]=='fatal')}")
print(f"SSR powerup     : {len(powerups)}")
print(f"outcome A       : {sum(1 for p in powerups if p['outcome']=='A')}")
print(f"outcome B       : {sum(1 for p in powerups if p['outcome']=='B')}")
print(f"pc-ack timeouts : {sum(1 for p in powerups if p['timeout'])}")
print(f"watchdog resync : {sum(1 for ln in lines if RE_WD_RESYNC.search(ln))}")
print(f"watchdog quiesce: {sum(1 for ln in lines if RE_WD_QUIESCE.search(ln))}")
print(f"CMD_OPEN lines  : {sum(1 for ln in lines if RE_CMDOPEN.search(ln))}")

# ---- the watchdog pc_line census (H2 falsifier) --------------------------
qs = [RE_WD_QUIESCE.search(ln) for ln in lines]
qs = [m for m in qs if m]
census = Counter((int(m.group(2)), int(m.group(3))) for m in qs)
print("\nwatchdog quiesce pc_state/pc_line census (H2 falsifier):")
for k in sorted(census):
    print(f"  pc_state={k[0]} pc_line={k[1]} : {census[k]}")
maxq = max((int(m.group(1)) for m in qs), default=0)
print(f"  longest quiesce counter observed: {maxq}s")

# ---- the main table ------------------------------------------------------
print("\n" + "=" * 78)
print("THE TABLE  (paired by file order, not timestamp)")
print("=" * 78)
print(f"{'#':>3} {'fatal t':>13} {'signature':<14} {'pc_line':>7} {'waited':>6} "
      f"{'outcome':>7} {'A t':>13} {'timeout t':>13} {'dt':>6}")
rows = []
for n, p in enumerate(powerups, 1):
    f = p.get("fatal")
    sig = f["sig"] if f else "-"
    ft = f"{f['t']:.6f}" if f and f["t"] is not None else "-"
    ot = p.get("outcome_t")
    tt = p["timeout"]
    dt = f"{tt - ot:.6f}" if (tt is not None and ot is not None) else "-"
    print(f"{n:>3} {ft:>13} {sig:<14} {p['pc_line']:>7} {p['waited']:>6} "
          f"{str(p['outcome']):>7} {(f'{ot:.6f}' if ot else '-'):>13} "
          f"{(f'{tt:.6f}' if tt else '-'):>13} {dt:>6}")
    rows.append((n, sig, p["outcome"], tt is not None))

# ---- 2x2: signature vs outcome ------------------------------------------
print("\n" + "=" * 78)
print("RESULT 1 -- the outcome is predicted by the fatal signature")
print("=" * 78)
tab = Counter((sig, out) for _, sig, out, _ in rows if sig in ("a2_power", "common_timer")
              and out in ("A", "B"))
a2A, a2B = tab[("a2_power", "A")], tab[("a2_power", "B")]
ctA, ctB = tab[("common_timer", "A")], tab[("common_timer", "B")]
print(f"{'':<16}{'A':>5}{'B':>5}")
print(f"{'a2_power.c:1189':<16}{a2A:>5}{a2B:>5}")
print(f"{'common_timer:390':<16}{ctA:>5}{ctB:>5}")
print(f"\nFisher exact one-sided p = {fisher_one_sided(a2A, a2B, ctA, ctB):.8f}")
print(f"Fisher exact two-sided p = {fisher_two_sided(a2A, a2B, ctA, ctB):.8f}")

# ---- A vs pc-ack timeout -------------------------------------------------
print("\n" + "=" * 78)
print("RESULT 2 -- the pc-ack timeout is a consequence of A")
print("=" * 78)
tab2 = Counter((out, bool(to)) for _, _, out, to in rows if out)
print(f"{'':<10}{'timeout':>9}{'none':>7}")
for out in ("A", "B"):
    print(f"{out:<10}{tab2[(out, True)]:>9}{tab2[(out, False)]:>7}")
print(f"\nFisher exact one-sided p = "
      f"{fisher_one_sided(tab2[('A',True)], tab2[('A',False)], tab2[('B',True)], tab2[('B',False)]):.8f}")
print("\ndelta(outcome A -> timeout), i.e. how long after the rebuild the wait expires:")
for n, _, out, to in rows:
    p = powerups[n - 1]
    if out == "A" and p.get("outcome_t") and p["timeout"]:
        print(f"  fatal #{n}: A at {p['outcome_t']:.6f}  timeout at {p['timeout']:.6f}  "
              f"dt = {p['timeout'] - p['outcome_t']:.6f} s")

# ---- rule out the modem's own CMD_OPEN burst ----------------------------
print("\n" + "=" * 78)
print("RESULT 3 -- the modem's CMD_OPEN burst does NOT separate A from B")
print("=" * 78)
cmdopen_lines = [i for i, ln in enumerate(lines, 1) if RE_CMDOPEN.search(ln)]
for n, _, out, _ in rows:
    p = powerups[n - 1]
    lo = p.get("fatal", {}).get("line") if p.get("fatal") else None
    hi = p.get("outcome_line")
    if lo is None or hi is None:
        continue
    # CMD_OPEN strictly AFTER this powerup outcome and before the next fatal
    nxt = None
    for m, _, _, _ in rows:
        q = powerups[m - 1].get("fatal")
        if q and q["line"] > hi:
            nxt = q["line"]
            break
    burst = [c for c in cmdopen_lines if hi < c < (nxt if nxt else 10 ** 9)]
    print(f"  row {n:>2}  outcome {out}  CMD_OPEN lines after the outcome, before the "
          f"next fatal: {len(burst)}")
print("\nEvery fatal SSR is followed by a CMD_OPEN burst -- both A and B rows. The")
print("burst is therefore not the discriminator. On the A rows it also arrives")
print("*after* the 'successfully reinitialized' line (e.g. row 2: A at 525.323701,")
print("CMD_OPEN at 525.730), so it cannot be the rebuilder in those rows either.")

# ---- the two readings of A ----------------------------------------------
print("\n" + "=" * 78)
print("WHAT A DOES *NOT* YET SEPARATE")
print("=" * 78)
print("Both print pc_state=1 (waited 200 ms), so the outcome alone cannot tell")
print("  (i)  the pc_irq assert edge was LOST (handler never ran), from")
print("  (ii) the edge was delivered but bam_dmux_power_on() failed in the handler.")
print("pc_irq_count across the SSR window separates them; it is incremented")
print("unconditionally as the first statement of bam_dmux_pc_irq().")
print("The RX watchdog is NOT the B-case rebuilder: it has two rebuild paths,")
print("both dev_warn, and this capture contains ZERO of them (pc_resync_count=0).")

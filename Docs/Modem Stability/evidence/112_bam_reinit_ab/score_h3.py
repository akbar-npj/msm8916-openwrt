#!/usr/bin/env python3
"""
Doc 182 H3 scorer: did the pc line hand a RISING edge to bam_dmux_pc_irq()?

usage: score_h3.py <sampler.txt> <fatal_uptime> <signature> [outcome]

H3 (Doc 182 9): an A outcome requires that the pc line was ALREADY HIGH at the
fatal and never fell, so the teardown's pc_state=false + remote-bit clear leaves
NO rising edge for the interrupt handler, and only the level-poll rebuilds.

  A  <=>  line HIGH at the fatal AND no rising edge in the window
  B  <=>  line LOW  at the fatal AND a rising edge consumed at the modem's assert

P3 -- "no rising edge was consumed" -- is the LOAD-BEARING prediction.  A high
pre-fatal line is weak evidence on its own, because the pc line toggles every
few seconds to ~100 s (measured); the edge count is what discriminates.

Sampler format (pcfine.sh, the FIXED key):
    iq aq ps pl to rs ra td rc vt vu mra msa co | <uptime> <quiesce_ms>
    1  2  3  4  5  6  7  8  9  10 11 12  13 14 |  15        16
The '|' sits between field 14 and the uptime, so the record is split on '|'.

Edge classification: consecutive records where pc_state (or pc_line) goes
0 -> 1 is a RISING edge; 1 -> 0 is a falling edge.  pc_irq_count is incremented
unconditionally as the first statement of bam_dmux_pc_irq(), so the edge count
in the window is exactly the pc_irq_count delta.
"""

import sys

if len(sys.argv) < 4:
    print(__doc__)
    sys.exit(2)

path = sys.argv[1]
fatal_t = float(sys.argv[2])
signature = sys.argv[3]
outcome = sys.argv[4] if len(sys.argv) > 4 else "?"

recs = []
for ln in open(path, errors="replace"):
    ln = ln.rstrip("\n")
    if not ln.strip():
        continue
    if "|" in ln:
        key, data = ln.split("|", 1)
        k = key.split()
        d = data.split()
    else:
        k, d = [], []
    if len(k) < 14:
        # v1 format: "iq|aq ps pl ... co UP qm" — 15 whitespace fields, where
        # field 1 joins iq and aq, fields 2..13 are the other 12 event fields,
        # field 14 is the uptime and field 15 the quiesce counter.  (Splitting
        # on '|' therefore yields a 1-field key.)
        f = ln.split()
        if len(f) < 15 or "|" not in f[0]:
            continue
        iq, aq = f[0].split("|")
        k = [iq, aq] + f[1:13]
        d = f[13:]
    if len(k) < 14 or len(d) < 1:
        continue
    try:
        recs.append({
            "iq": int(k[0]), "aq": int(k[1]), "ps": int(k[2]), "pl": int(k[3]),
            "to": int(k[4]), "rs": int(k[5]), "ra": int(k[6]), "td": int(k[7]),
            "rc": int(k[8]), "vt": int(k[9]), "vu": int(k[10]),
            "mra": int(k[11]), "msa": int(k[12]), "co": int(k[13]),
            "up": float(d[0]), "qm": int(d[1]) if len(d) > 1 else 0,
        })
    except ValueError:
        continue

recs.sort(key=lambda r: r["up"])

before = [r for r in recs if r["up"] <= fatal_t]
after = [r for r in recs if r["up"] > fatal_t]
if not before:
    print(f"NO RECORD BEFORE {fatal_t} — sampler did not cover the fatal")
    sys.exit(1)

last = before[-1]

print("=" * 78)
print("H3 SCORER — Doc 182 9")
print("=" * 78)
print(f"sampler          : {path}  ({len(recs)} records)")
print(f"fatal uptime     : {fatal_t}")
print(f"signature        : {signature}")
print(f"outcome (dmesg)  : {outcome}")
print()
print("last record BEFORE the fatal:")
print(f"  uptime {last['up']}  (lead time {fatal_t - last['up']:.3f} s)")
print(f"  pc_irq_count={last['iq']}  pc_state={last['ps']}  pc_line={last['pl']}  "
      f"pc_timeout={last['to']}  rx_tearing_down={last['td']}  "
      f"quiesce_ms={last['qm']}")

# the window: the fatal through the post-SSR settle
win = [r for r in recs if last["up"] <= r["up"] <= fatal_t + 14]
print(f"\nrecords in the window [{last['up']} .. {fatal_t + 14}]: {len(win)}")
print(f"{'uptime':>10} {'pc_irq':>7} {'ps':>3} {'pl':>3} {'timeout':>8} {'td':>3} "
      f"{'rc':>7} {'co':>4}  edge")
prev = None
rises = 0
falls = 0
for r in win:
    edge = ""
    if prev is not None:
        if r["iq"] != prev["iq"]:
            if r["ps"] > prev["ps"] or r["pl"] > prev["pl"]:
                edge = "RISING"
                rises += 1
            elif r["ps"] < prev["ps"] or r["pl"] < prev["pl"]:
                edge = "falling"
                falls += 1
            else:
                edge = "? (count moved, state flat)"
    print(f"{r['up']:>10.2f} {r['iq']:>7} {r['ps']:>3} {r['pl']:>3} {r['to']:>8} "
          f"{r['td']:>3} {r['rc']:>7} {r['co']:>4}  {edge}")
    prev = r

print()
print("=" * 78)
print("VERDICT")
print("=" * 78)
line_at_fatal = "HIGH" if last["pl"] == 1 else "LOW"
iq_delta = (win[-1]["iq"] - last["iq"]) if win else 0
print(f"pc line at the fatal        : {line_at_fatal}")
print(f"pc_irq_count over the window: {last['iq']} -> {win[-1]['iq']}  (delta {iq_delta})")
print(f"rising edges consumed       : {rises}")
print(f"falling edges               : {falls}")
print()

verdict = None
if outcome == "A":
    if line_at_fatal == "HIGH" and rises == 0:
        verdict = "P1 HIT, P3 HIT -> H3 CONFIRMED on this case"
    elif rises > 0:
        verdict = ("P3 MISSED — a RISING edge WAS consumed, so the lost-edge mechanism "
                   "is FALSIFIED for this case (the A/B split itself still stands)")
    else:
        verdict = "P1 MISSED — the line was LOW at the fatal -> H3 FALSIFIED for this case"
elif outcome == "B":
    if line_at_fatal == "LOW" and rises == 1:
        verdict = "P2 HIT, P4 HIT -> H3's B-side CONFIRMED on this case"
    else:
        verdict = f"B-side anomaly (line {line_at_fatal}, {rises} rising edges)"
else:
    verdict = "outcome not supplied — read the two lines above"

print(verdict)
print()
print("Caveat: 'line HIGH at the fatal' is weak on its own (the pc line toggles")
print("every few seconds). The load-bearing quantity is 'rising edges consumed'.")

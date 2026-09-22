#!/usr/bin/env python3
"""
178_a2power_stall_delay/analyze_stall.py

Question: is the `a2_power.c:1189` fatal's LATENESS explained by an RPM-log stall
that begins at the predicted clock beat and ends at the fatal?

Method: rpmring -t emits a "# NEW t=<ns> counter=... nrec=..." line each time the
RPM log ring has new records. The ring turns over at ~200-300 ms when the RPM is
busy; a gap much longer than that means the RPM wrote nothing for that long.

Inputs (gzipped):
  rpm_track_fatal10.txt.gz   live 2026-09-22 capture spanning fatal #10
  rpm_control_rpm11.txt.gz   2026-09-21 capture, NO fatal in the analysed window

Run:  python3 analyze_stall.py
"""
import gzip, re, os, statistics as st

HERE = os.path.dirname(os.path.abspath(__file__))
NEW = re.compile(r"t=(\d+).*?counter=(0x[0-9a-f]+).*?start=(\d+) nrec=(\d+)")


def load(path):
    out = []
    op = gzip.open if path.endswith(".gz") else open
    with op(path, "rt", errors="replace") as f:
        for line in f:
            if line.startswith("# NEW"):
                m = NEW.search(line)
                if m:
                    out.append((int(m.group(1)), int(m.group(2), 16), int(m.group(3)), int(m.group(4))))
    return out


def report(name, n, thresh_s=2.0):
    span = (n[-1][0] - n[0][0]) / 1e9
    gaps = [((n[i][0] - n[i - 1][0]) / 1e6, n[i - 1][0] / 1e9, n[i][0] / 1e9) for i in range(1, len(n))]
    g = sorted(x[0] for x in gaps)
    big = [x for x in gaps if x[0] > thresh_s * 1000]
    print(f"\n=== {name} ===")
    print(f"  batches={len(n)}  span={span:.1f} s  ({n[0][0]/1e9:.1f} -> {n[-1][0]/1e9:.1f})")
    print(f"  gap ms: median={st.median(g):.1f}  p90={g[int(.90*len(g))]:.1f}  p99={g[int(.99*len(g))]:.1f}  max={g[-1]:.1f}")
    print(f"  gaps >{thresh_s:.0f} s: {len(big)}   total={sum(x[0] for x in big)/1000:.1f} s"
          f"   ({100*sum(x[0] for x in big)/1000/span:.1f} % of span)")
    for d, a, b in sorted(big, reverse=True)[:8]:
        print(f"     {d:9.1f} ms   {a:9.3f} -> {b:9.3f} s")
    return gaps


live = load(os.path.join(HERE, "rpm_track_fatal10.txt.gz"))
ctrl = load(os.path.join(HERE, "rpm_control_rpm11.txt.gz"))
report("LIVE 2026-09-22 (fatal #10)", live)
report("CONTROL 2026-09-21 (rpm11)", ctrl)

print("\n" + "=" * 68)
print("FATAL #10 vs the Doc 177 clock")
F9, BOOT9, F10 = 7756.662692, 7757.566549, 8719.020382
PERIOD = 903.675206
beat = F9 + PERIOD
# stall = the contiguous run of >2 s gaps bracketing the fatal
gaps = [((live[i][0] - live[i - 1][0]) / 1e6, live[i - 1][0] / 1e9, live[i][0] / 1e9)
        for i in range(1, len(live))]
run = [x for x in gaps if x[0] > 2000 and 8600 < x[1] < 8730]
stall_start = min(x[1] for x in run)
stall_end = max(x[2] for x in run)
print(f"  fatal #9              = {F9:.6f}   (common_timer)")
print(f"  predicted beat        = {beat:.6f}   (= #9 + {PERIOD:.6f})")
print(f"  fatal #10             = {F10:.6f}   (a2_power.c:1189)  -> {F10-beat:+.3f} s LATE")
print(f"  AP interval #9->#10   = {F10-F9:.6f} s  ({F10-F9-PERIOD:+.3f} s vs the clock)")
print(f"  RPM stall             = [{stall_start:.3f}, {stall_end:.3f}]  = {stall_end-stall_start:.3f} s")
print(f"    stall_start - beat  = {stall_start-beat:+.3f} s")
print(f"    fatal - stall_end   = {F10-stall_end:+.3f} s")
print(f"  excess ({F10-beat:.3f} s) vs stall ({stall_end-stall_start:.3f} s): diff {abs((F10-beat)-(stall_end-stall_start)):.3f} s")
print("\n  => the stall begins AT the predicted beat and ends AT the fatal.")
print("     H1: the ~903.675 s timer expires on schedule; the a2_power.c:1189 assert")
print("     surfaces only when the RPM stall clears, so the dmesg time = beat + stall.")
print("     NOT established: n=1, and RPM stalls >2 s are routine (see CONTROL).")

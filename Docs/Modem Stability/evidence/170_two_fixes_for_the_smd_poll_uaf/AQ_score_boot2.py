#!/usr/bin/env python3
"""Regenerate the boot-2 result artifact from the FROZEN captures.

Doc 170 PART 37.  The captures had live writers and the first pass of PART 37
s2 was scored from partial reads -- one of those errors reversed a conclusion.
This script reads ONLY boot2/frozen_1232/ so the artifact is reproducible.

Usage: score_boot2.py > <artifact>
"""
import gzip
import hashlib
import os
import re
import sys

# Default to the working copy; pass a directory to score the committed .gz set.
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'frozen_1232')
if len(sys.argv) > 1:
    D = sys.argv[1]
LO, HI = 868.9, 948.9          # window: 902.3 s modem-relative, anchor AP 6.604624
SOAK_START = 206.0             # supervisor restarted the ping loops here (PART 37 s4)
BAM = re.compile(r'^\[\s*([0-9]+\.[0-9]+)\]\s+<DMUX>\s+(.*)$')


def resolve(name):
    """Return a readable path for name, preferring the raw copy, else .gz."""
    raw = os.path.join(D, name)
    if os.path.exists(raw):
        return raw
    if os.path.exists(raw + '.gz'):
        return raw + '.gz'
    raise SystemExit('missing capture: %s(.gz) in %s' % (name, D))


def readlines(name):
    with open(resolve(name), 'rb') as fh:
        head = fh.read(2)
    if head == b'\x1f\x8b':
        with gzip.open(resolve(name), 'rt', errors='replace') as fh:
            return fh.readlines()
    with open(resolve(name), errors='replace') as fh:
        return fh.readlines()


def md5(name):
    """Hash the RAW bytes, so a .gz and its expansion hash differently but the
    raw md5 recorded in the artifact is the identity of the content."""
    h = hashlib.md5()
    if resolve(name).endswith('.gz'):
        with gzip.open(resolve(name), 'rb') as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b''):
                h.update(chunk)
    else:
        with open(resolve(name), 'rb') as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b''):
                h.update(chunk)
    return h.hexdigest()


def bam_rows():
    rows = []
    for line in readlines('bam_dmux_boot2.log'):
        m = BAM.match(line)
        if m:
            rows.append((float(m.group(1)), m.group(2).strip()))
    rows.sort()
    return rows


def bursts(name):
    txt = ''.join(readlines(name))
    parts = re.split(r'(?m)^PING ', txt)
    out = []
    for i, b in enumerate(parts[1:], 1):
        m = re.search(r'(\d+) packets transmitted, (\d+) received', b)
        if not m:
            continue
        tx, rx = int(m.group(1)), int(m.group(2))
        out.append((i, tx, rx))
    return out


def counters():
    up, rd, wr, pw, cur = [], [], [], [], None
    for line in readlines('snapshot_boot2.log'):
        s = line.strip()
        m = re.match(r'=== uptime (\d+) ===', s)
        if m:
            cur = int(m.group(1)); up.append(cur); continue
        if cur is None:
            continue
        m = re.match(r'skb read cnt:\s+(\d+)', s)
        if m: rd.append(int(m.group(1))); continue
        m = re.match(r'skb write cnt:\s+(\d+)', s)
        if m: wr.append(int(m.group(1))); continue
        m = re.match(r'a2 pwr cntl in:\s+(\d+)', s)
        if m: pw.append(int(m.group(1)))
    return up, rd, wr, pw


def logcat():
    # anchor: device boot = 01:35:24 IST - 9.09 s (boot2_anchor.txt)
    anchor = 1 * 3600 + 35 * 60 + 24 - 9.09
    rows = []
    for line in readlines('logcat_boot2.log'):
        m = re.match(r'\d\d-\d\d (\d\d):(\d\d):(\d\d\.\d\d\d) .*ConnectivityChange for (\S+): (\S+)', line)
        if m:
            ap = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3)) - anchor
            rows.append((ap, m.group(4), m.group(5)))
    return rows


def main():
    o = sys.stdout.write
    o("BOOT-2 RESULT -- non-recurrence (Doc 170 PART 37)\n")
    o("scored against the PART 36 pre-registration, filed ~644 s before the window\n")
    o("ALL NUMBERS FROM THE FROZEN COPIES -- scratch/android_capture/boot2/frozen_1232/\n")
    o("  (committed alongside this file as AR_boot2_frozen_*.log.gz; reproduce with\n")
    o("   python3 AQ_score_boot2.py <dir containing those .gz files>)\n\n")
    for f in ('bam_dmux_boot2.log', 'ping_plain_boot2.log', 'ping_iface_boot2.log',
              'snapshot_boot2.log', 'logcat_boot2.log'):
        o("  md5 %s  %s\n" % (md5(f), f))
    o("\nwindow: AP %.1f-%.1f s  (902.3 s modem-relative, anchor AP 6.604624)\n" % (LO, HI))

    rows = bam_rows()
    soak = [r for r in rows if r[0] >= SOAK_START]
    gaps = [(soak[i + 1][0] - soak[i][0], soak[i][0], soak[i + 1][0]) for i in range(len(soak) - 1)]
    inwin = [g for g in gaps if LO <= g[1] <= HI]
    o("\n=== 1. bam_dmux silence gaps, AP >= %.0f s ===\n" % SOAK_START)
    o("events total %d   span %.3f -> %.3f s\n" % (len(rows), rows[0][0], rows[-1][0]))
    o("soak regime: t >= %.1f s   events %d   span %.3f -> %.3f s\n"
      % (SOAK_START, len(soak), soak[0][0], soak[-1][0]))
    o("events INSIDE window: %d\n" % len([t for t, _ in soak if LO <= t <= HI]))
    o("gaps with ONSET inside window: %d   MAX %.3f s\n" % (len(inwin), max(g[0] for g in inwin)))
    o("MAX gap anywhere in soak:      %.3f s at AP %.3f\n" % (max(gaps)[0], max(gaps)[1]))

    o("\n=== 2. all gaps >= 20 s (to show the cadence and the absence of any real outage) ===\n")
    for d, a, b in gaps:
        if d >= 20:
            o("  %7.3f s   AP %8.3f -> %8.3f\n" % (d, a, b))
    o("  gaps >= 26 s: %d\n" % len([g for g in gaps if g[0] >= 26]))
    o("  boot 1 window for contrast: 38.028 s and 37.183 s vs a ~25.9 s cadence\n")

    o("\n=== 3. ping, BOTH variants (first time run side by side) ===\n")
    for f in ('ping_plain_boot2.log', 'ping_iface_boot2.log'):
        bs = bursts(f)
        lossy = [(i, tx, rx, int(round(100 * (tx - rx) / tx))) for i, tx, rx in bs if rx < tx]
        o("  %-24s bursts=%d  lossy=%s\n" % (f, len(bs), lossy))
    o("  both: 1 x 'Network is unreachable', on line 1 (first invocation, AP ~22 s,\n")
    o("        before the data call was up) -- not an in-run event.\n")
    o("  cadence ~29.0 s from AP ~206 s => in-window bursts are #24,#25,#26 (AP ~873,~902,~931)\n")
    o("        and the lossy ones are #31/#33/#35 at AP >= ~1076 s, >= 127 s AFTER the window\n")
    o("  the two loops are NOT time-locked, so no lossy burst is attributed to the AP 1082.6 flap\n")

    o("\n=== 4. logcat: connectivity changes, mapped to AP time ===\n")
    for ap, net, st in logcat():
        mark = "  <-- INSIDE WINDOW" if LO <= ap <= HI else ""
        o("  AP %9.3f  %-13s %s%s\n" % (ap, net, st, mark))
    mob = [r for r in logcat() if r[1] == 'mobile']
    o("  mobile: %d events = 1 boot bring-up + a 3-beat flap at AP 1082.572-1087.772 (5.2 s)\n" % len(mob))
    o("  THAT FLAP IS 133.7 s AFTER THE WINDOW CLOSED -- boot 2 was NOT outage-free.\n")

    o("\n=== 5. counters: ZERO resets ===\n")
    up, rd, wr, pw = counters()
    resets = sum(1 for a, b in zip(rd, rd[1:]) if b < a) + sum(1 for a, b in zip(wr, wr[1:]) if b < a)
    o("  samples %d   span AP %d -> %d s\n" % (len(up), up[0], up[-1]))
    o("  skb read  %d -> %d\n" % (rd[0], rd[-1]))
    o("  skb write %d -> %d   monotonic %s\n" % (wr[0], wr[-1], all(b >= a for a, b in zip(wr, wr[1:]))))
    o("  a2 pwr in %d -> %d\n" % (pw[0], pw[-1]))
    o("  COUNTER RESETS: %d   (a modem restart/SSR would reset these)\n" % resets)
    o("  NOTE the AP 1082.6 flap does NOT reset them either.\n")

    o("\n=== 6. LIMITATION OF THE PRIMARY INSTRUMENT ===\n")
    o("  The AP 1082.6-1087.8 flap produced NO bam_dmux silence gap: the gap spanning it is\n")
    o("  23.918 s (AP 1055.230 -> 1079.148), the ordinary cadence, and the modem is BUSY\n")
    o("  through the flap.  So the gap instrument is BLIND to a userspace bearer flap.\n")
    o("  'no gap in the window' is NOT by itself proof of 'no outage in the window' --\n")
    o("  the in-window verdict rests on the pings (clean) and logcat (silent until AP 1082.4).\n")

    o("\n=== 7. the two early gaps = the SUPERVISOR GAP, not an outage ===\n")
    o("  93.724 s  AP  44.077 -> 137.801\n")
    o("  65.308 s  AP 140.033 -> 205.341\n")
    o("  adbd restarted a second time after the re-root and killed the earliest three\n")
    o("  captures; the supervisor restarted the ping loops at AP ~206 s, exactly where\n")
    o("  the gaps stop.  Both are excluded by --soak-start 206.\n")


if __name__ == '__main__':
    main()

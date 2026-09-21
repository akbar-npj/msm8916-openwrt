#!/usr/bin/env python3
"""bam_dmux SILENCE-GAP analyzer for the Android AP-side control.

Motivation (Doc 170 PART 32): the message census in analyze_android_dmux.py
counts events; it does not measure the GAPS between them.  A connectivity
outage shows up as a gap, not as a message, so the census is blind to it.

TRAPS this script handles explicitly:
  1. `log_cont` is a CONTINUOUS reader: the capture opens with whatever the
     ipc_logging ring already held, i.e. lines from BOOT, not from the
     capture's start.  The leading region is idle-modem history and produces
     huge meaningless gaps.  Always pass --soak-start (taken from an
     instrument that stamps its own start, e.g. the snapshot's uptime
     markers), never assume the first line is the capture start.
  2. The ping loop cadence (ping -c 3 -W 2; sleep 27) creates a tight cluster
     of gaps at ~25.9 s.  Compare against THAT, not against the median.

Usage:
  analyze_android_gaps.py <bam_dmux.log> [--soak-start 380] [--top 20]
"""
import argparse
import re
import statistics
import sys

LINE = re.compile(r'^\[\s*([0-9]+\.[0-9]+)\]\s+<DMUX>')


def load(path):
    ev = []
    with open(path, errors='replace') as fh:
        for line in fh:
            m = LINE.match(line)
            if m:
                ev.append((float(m.group(1)), line.strip()))
    ev.sort()
    return ev


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('logfile')
    ap.add_argument('--soak-start', type=float, default=None,
                    help='uptime at which the traffic loop started; the '
                         'log_cont pre-history before this is discarded')
    ap.add_argument('--top', type=int, default=20)
    args = ap.parse_args()

    ev = load(args.logfile)
    if not ev:
        sys.exit('no <DMUX> events parsed -- check the format string at '
                 'bam_dmux.c:337 (the state prefix is TWO space-separated '
                 'tokens)')

    print(f'file: {args.logfile}')
    print(f'events: {len(ev)}   span {ev[0][0]:.3f} -> {ev[-1][0]:.3f} s')

    t0 = args.soak_start
    if t0 is None:
        print('\nWARNING: --soak-start not given.  The leading region may be '
              'log_cont pre-history (idle modem) and its gaps are artifacts.')
        t0 = ev[0][0]

    sel = [e for e in ev if e[0] >= t0]
    if len(sel) < 3:
        sys.exit('too few events in the soak regime')
    print(f'soak regime: t >= {t0:.1f} s   events {len(sel)}   '
          f'span {sel[0][0]:.3f} -> {sel[-1][0]:.3f} s')

    gaps = [(sel[i + 1][0] - sel[i][0], sel[i][0], sel[i + 1][0],
             sel[i][1][-64:], sel[i + 1][1][-64:])
            for i in range(len(sel) - 1)]
    gaps.sort(key=lambda x: x[0], reverse=True)

    print(f'\n=== TOP {args.top} SILENCES ===')
    for d, a, b, la, lb in gaps[:args.top]:
        print(f'  {d:8.3f} s   {a:9.3f} -> {b:9.3f}')
        print(f'        after: ...{la}')
        print(f'        next : ...{lb}')

    vals = [g[0] for g in gaps]
    print(f'\nall gaps: n={len(vals)} median={statistics.median(vals):.4f} '
          f'mean={statistics.mean(vals):.4f} '
          f'p99={sorted(vals)[int(0.99 * len(vals))]:.3f} max={max(vals):.3f}')

    print('\n=== HISTOGRAM ===')
    buckets = [('0-0.1', 0, 0.1), ('0.1-1', 0.1, 1), ('1-5', 1, 5),
               ('5-20', 5, 20), ('20-26', 20, 26), ('26-30', 26, 30),
               ('30-40', 30, 40), ('>40', 40, 1e9)]
    for name, lo, hi in buckets:
        n = sum(1 for v in vals if lo <= v < hi)
        if n:
            print(f'  {name:>8} s : {n}')
    print('\nThe ping cadence (ping -c 3 -W 2; sleep 27) puts the bulk of the '
          'gaps in 20-26 s.  A gap outside that bucket is a candidate outage; '
          'confirm it against the ping log and the driver counters.')

    # UL wakeup rate, for the 1:1 check against disconnect/reconnect pairs
    uw = [e for e in sel if 'ul_wakeup waiting for previous ack' in e[1]]
    dc = [e for e in sel if 'disconnect_to_bam: device reset' in e[1]]
    span = sel[-1][0] - sel[0][0]
    if span > 0:
        print(f'\nUL wakeups: {len(uw)}  ({len(uw)/span*60:.2f}/min)')
        print(f'disconnect/reconnect pairs: {len(dc)}  '
              f'({len(dc)/span*60:.2f}/min)')
        print('  NOTE: these should track 1:1.  The pair is the modem\'s '
              'SMSM_A2_POWER_CONTROL/SMSM_PROC_AWAKE sleep handshake, NOT an '
              'SSR (Doc 170 PART 33 s3).')


if __name__ == '__main__':
    main()

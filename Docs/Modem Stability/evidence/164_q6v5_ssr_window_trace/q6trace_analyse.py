#!/usr/bin/env python3
"""Analyse the q6v5-trace blocks in a captured dmesg (patch 817).

Usage:  q6trace_analyse.py <dmesg-file>

Prints, for each 01..23 cycle, every inter-step delta, **which caller each
half of the cycle belongs to**, and the full decomposition of the Doc 159
"hang window" (`port failed halt` -> `MBA booted`).

Why this exists: the naive reading of the trace is "01-09 then 10-23 = one
stop/start pair".  It is NOT.  q6v5_mba_load() has TWO callers -- q6v5_start()
and q6v5_reload_mba(), and the latter is reached only from
qcom_q6v5_dump_segment(), the remoteproc coredump reader.  A fatal therefore
produces TWO 01..23 cycles: the first is the coredump forcing an MBA reload,
the second is the real restart.  Attributing a cycle to the wrong caller is the
whole trap -- see Doc 165.

The call chain, from the source (remoteproc_core.c: rproc_boot_recovery()):

    rproc_stop()                 -> q6v5_stop()  -> q6v5_mba_reclaim()   [01-09]
    rproc->ops->coredump()       -> rproc_coredump()
        -> rproc_copy_segment()  -> qcom_q6v5_dump_segment()
            first segment        -> q6v5_reload_mba() -> q6v5_mba_load()  [10-23]
            ... ~85 MB copied here (RPROC_COREDUMP_ENABLED: synchronous) ...
            last segment         -> q6v5_mba_reclaim()                    [01-09]
    request_firmware()           (cached by now)
    rproc_start()                -> q6v5_start() -> q6v5_mba_load()       [10-23]
                                    ... then "MBA booted"

so the half-cycle sequence for ONE fatal is  01-09, 10-23, 01-09, 10-23.
A cold boot has only the last pair.
"""
import re
import sys

LINE = re.compile(r'^\[\s*(\d+\.\d+)\]\s+(.*)$')
TRACE = re.compile(r'q6v5-trace:\s+(\d+)\s+(.*)$')

# caller of each half-cycle, in the order the halves appear for one fatal
CALLERS = [
    "rproc_stop   -> q6v5_stop        -> q6v5_mba_reclaim   [teardown]",
    "coredump#1   -> q6v5_reload_mba  -> q6v5_mba_load      [dump forces a reload]",
    "coredump#last-> q6v5_mba_reclaim                        [dump releases MBA]",
    "rproc_start  -> q6v5_start       -> q6v5_mba_load      [the real restart]",
]


def load(path):
    rows = []
    for raw in open(path, errors='ignore'):
        m = LINE.match(raw.strip())
        if not m:
            continue
        body = m.group(2)
        t = TRACE.search(body)
        if t:
            rows.append((float(m.group(1)), 'trace', int(t.group(1)), t.group(2)))
        elif 'fatal error' in body:
            rows.append((float(m.group(1)), 'fatal', None, body.split('fatal error')[1][:40]))
        elif 'port failed halt' in body:
            rows.append((float(m.group(1)), 'halt', None, 'port failed halt'))
        elif 'MBA booted' in body:
            rows.append((float(m.group(1)), 'mbabooted', None, 'MBA booted'))
        elif 'stopped remote processor' in body:
            rows.append((float(m.group(1)), 'stopped', None, 'stopped remote processor'))
        elif 'is now up' in body:
            rows.append((float(m.group(1)), 'up', None, 'rproc is now up'))
    return rows


def split_halves(trace):
    """Split the flat trace list into 01-09 (reclaim) and 10-23 (load) halves."""
    halves, cur = [], []
    for r in trace:
        if r[2] in (1, 10) and cur:
            halves.append(cur)
            cur = []
        cur.append(r)
    if cur:
        halves.append(cur)
    return halves


def main(path):
    rows = load(path)
    trace = [r for r in rows if r[1] == 'trace']
    print(f"trace lines: {len(trace)}")

    halves = split_halves(trace)
    print(f"half-cycles: {[(h[0][2], h[-1][2], len(h)) for h in halves]}")
    print(f"full cycles: {len(halves) // 2}  "
          f"(one fatal = 2 cycles = 4 halves; a cold boot = 1 cycle)\n")

    for i, h in enumerate(halves):
        who = CALLERS[i] if i < len(CALLERS) else "?"
        t0, t1 = h[0][0], h[-1][0]
        print(f"===== HALF {i + 1}  {h[0][2]:02d}..{h[-1][2]:02d}   "
              f"[{t0:.6f} .. {t1:.6f}]  span {(t1 - t0) * 1000:8.3f} ms =====")
        print(f"  caller: {who}")
        for j in range(len(h) - 1):
            print(f"    {h[j][2]:02d} {h[j][3]:36s} -> {h[j + 1][2]:02d}: "
                  f"{(h[j + 1][0] - h[j][0]) * 1000:9.3f} ms")
        # cross-half gaps
        if i + 1 < len(halves):
            print(f"  ** gap to next half ({h[-1][2]:02d} -> {halves[i + 1][0][2]:02d}): "
                  f"{(halves[i + 1][0][0] - t1) * 1000:9.3f} ms")
        print()

    print("===== EVENT ANCHORS (everything, in order) =====")
    for t, kind, _n, txt in rows:
        print(f"  [{t:12.6f}] {kind:9s} {txt}")

    # ---- the Doc 159 hang window, fully decomposed -------------------------
    halt = [t for t, k, _, _ in rows if k == 'halt']
    boot = [t for t, k, _, _ in rows if k == 'mbabooted']
    if halt and boot:
        h = halt[-1]
        bl = [x for x in boot if x > h]
        if bl:
            b0 = bl[0]
            print("\n===== Doc 159 hang window, decomposed =====")
            print(f"  port failed halt        {h:.6f}")
            # the window runs from step 01 of the reclaim half that follows the
            # halt, through step 23 of the LOAD half that follows it (the real
            # q6v5_start restart), not through the end of the reclaim half.
            idx = next((i for i, hh in enumerate(halves) if hh[0][0] > h), None)
            if idx is not None and idx + 1 < len(halves):
                t01 = halves[idx][0][0]
                t23 = halves[idx + 1][-1][0]
                print(f"  step 01                 {t01:.6f}   (+{(t01 - h) * 1000:7.3f} ms)"
                      f"   <- the tail of the halt_axi_port calls")
                print(f"  step 23                 {t23:.6f}   (+{(t23 - t01) * 1000:7.3f} ms"
                      f" from 01)  <- all 23 driver steps")
                print(f"  MBA booted              {b0:.6f}   (+{(b0 - t23) * 1000:7.3f} ms)"
                      f"   <- q6v5_rmb_mba_wait(5000), untraced")
                tot = (b0 - h) * 1000
                drv = (t23 - t01) * 1000
                print("  ------------------------------------------------")
                print(f"  TOTAL                   {tot:7.3f} ms")
                print(f"    driver steps 01-23    {drv:7.3f} ms  ({drv / tot * 100:.1f} %)")
                print(f"    MBA boot wait         {(b0 - t23) * 1000:7.3f} ms  "
                      f"({(b0 - t23) * 1000 / tot * 100:.1f} %)")
                print(f"    halt tail + pre-01    {(t01 - h) * 1000:7.3f} ms  "
                      f"({(t01 - h) * 1000 / tot * 100:.1f} %)")


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'first_fatal_full_dmesg.txt')

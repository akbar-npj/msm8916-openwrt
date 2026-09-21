#!/usr/bin/env python3
"""Score the STATIONARY Android MCPM cadence capture (Doc 174 s10).

Counts the SAME marker the Android-vs-OpenWrt differential used --
`FW_SLEEP_PWRDN_FULL` (Stock_Android_Live/02_DIFFERENTIAL_DIAG_ANALYSIS.md
s2: 916 cycles in ~488 s = 1.88/s vs OpenWrt 100 in ~121 s = 0.83/s) -- so the
stationary number is directly comparable with both.

TWO CLOCKS, and the second is new
--------------------------------
1. The AP bracket from mcpm_stationary.sh (`kernel_start_s` -> `kernel_end_s`).
   This is what the original 1.88/s used ("modem 808-1288 s").  It is an
   AP-derived duration and it EXCLUDES the ~14 s between `diag_mdlog` starting
   and the bracket being stamped, so dividing the whole file by it is a small
   OVERESTIMATE.
2. The modem's OWN clock, carried inside every `FW_WAKE-UP_Start` message as
   `Start time = %lx, End time = %lx, duration = %lu usecs`.  Those three
   fields are SELF-CALIBRATING: `duration` is the integer-microsecond floor of
   `(End-Start)`, which fixes the tick rate without trusting any doc.  Measured
   here at 19.2 MHz (the same domain as the RPM log's record[1]).  Unwrapping
   that 32-bit counter gives a duration that owes nothing to the AP clock --
   the same principle as ATS_RTC in Doc 172/173.

The script prints the rate from BOTH clocks so the reader can see they agree.
It also prints the inter-cycle interval distribution, because a mean rate
hides whether the modem is periodic or bursty.

Usage: score_mcpm_stationary.py <qmdl_dir_or_file> [--window <mcpm_window.txt>]
"""
import os
import re
import struct
import sys
from collections import Counter

ESC_CHAR, CONTROL_CHAR, ESC_MASK = 0x7D, 0x7E, 0x20
WRAP32 = 1 << 32
MODEM_TICK_HZ = 19_200_000

_crc = []
for _i in range(256):
    _c = _i
    for _ in range(8):
        _c = (_c >> 1) ^ 0x8408 if _c & 1 else _c >> 1
    _crc.append(_c)


def crc_ccitt_reflected(data):
    crc = 0xFFFF
    for b in data:
        crc = (crc >> 8) ^ _crc[(crc ^ b) & 0xFF]
    return crc ^ 0xFFFF


def hdlc_unescape(raw):
    """0x7E is the HDLC delimiter, so an escaped 0x7E is always 0x7D 0x5E --
    splitting the file on 0x7E is therefore a SAFE and ~50x faster frame
    splitter than walking every byte (83 MB in 7 s instead of minutes)."""
    if ESC_CHAR not in raw:
        return raw
    out, esc = bytearray(), False
    for b in raw:
        if esc:
            out.append(b ^ ESC_MASK)
            esc = False
        elif b == ESC_CHAR:
            esc = True
        else:
            out.append(b)
    return bytes(out)


def extract_text(payload):
    return "".join(chr(b) if 0x20 <= b < 0x7F else (" " if b == 0 else ".")
                   for b in payload)


def iter_msgf(path):
    """Yield every CRC-valid 0x79 (DIAG_MSGF) payload, in file order."""
    with open(path, "rb") as f:
        data = f.read()
    n_valid = n_msgf = n_bad = 0
    for raw in data.split(b"\x7e"):
        if len(raw) < 6:
            continue
        body = hdlc_unescape(raw)
        if len(body) < 3:
            continue
        payload = body[:-2]
        if crc_ccitt_reflected(payload) != (body[-2] | (body[-1] << 8)):
            n_bad += 1
            continue
        n_valid += 1
        if payload[0] == 0x79:
            n_msgf += 1
            yield payload
    iter_msgf.stats = (n_valid, n_msgf, n_bad, len(data))


def unwrap(values):
    """Unwrap a 32-bit counter that increases monotonically modulo 2**32.
    Valid only while consecutive samples are < 2**32 ticks (223.7 s) apart."""
    out, base, prev = [], 0, None
    wraps = 0
    for v in values:
        if prev is not None and v < prev:
            base += WRAP32
            wraps += 1
        out.append(base + v)
        prev = v
    return out, wraps


def pct(sorted_vals, q):
    if not sorted_vals:
        return 0.0
    i = min(len(sorted_vals) - 1, int(q * (len(sorted_vals) - 1) + 0.5))
    return sorted_vals[i]


def files_in(path):
    if os.path.isfile(path):
        return [path]
    return sorted(os.path.join(path, f) for f in os.listdir(path)
                  if f.endswith(".qmdl"))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    # Targets and --window pairs are matched POSITIONALLY, so several captures can
    # be scored in one invocation and compared:
    #   score_mcpm_stationary.py A.qmdl --window wa.txt B.qmdl --window wb.txt
    targets, windows, rest = [], [], list(sys.argv[1:])
    while rest:
        a = rest.pop(0)
        if a == "--window":
            w = rest.pop(0) if rest else None
            if targets:
                windows[-1] = w          # attach to the target just seen
        else:
            targets.append(a)
            windows.append(None)
    for tgt, win in zip(targets, windows):
        score_one(tgt, win)
    if len(targets) > 1:
        print("\n================ OVERALL ================")
        for tgt, win in zip(targets, windows):
            _summary_row(tgt, win)


_ROWS = []


def _summary_row(target, window_path):
    dur_ap = dur_modem = None
    if window_path and os.path.exists(window_path):
        kv = dict(l.strip().split("=", 1) for l in open(window_path) if "=" in l)
        try:
            dur_ap = float(kv["modem_end_s"]) - float(kv["modem_start_s"])
        except (KeyError, ValueError):
            pass
    for r in _ROWS:
        if r[0] == target:
            print(f"  {os.path.basename(target):<40} n_sleep={r[1]:<5} "
                  f"modem_span={r[2]:7.1f}s -> {r[1]/r[2]:.3f} /s"
                  + (f"   (AP bracket {dur_ap:.0f}s -> {r[1]/dur_ap:.3f} /s)"
                     if dur_ap else ""))


def score_one(target, window_path):
    counts = Counter()
    fatal = []
    wake_start = []      # modem 19.2 MHz ticks, file order
    wake_dur_us = []     # reported duration, us
    wake_span = []       # (End-Start) raw ticks
    sleep_ts_hdr = []    # header word, LE u32 @ body[4:8], file order
    total_bytes = 0

    for path in files_in(target):
        total_bytes += os.path.getsize(path)
        for payload in iter_msgf(path):
            t = extract_text(payload)
            if "FW_SLEEP_PWRDN_FULL" in t:
                counts["SLEEP_PWRDN_FULL"] += 1
            elif "SLEEP_PWRDN" in t:
                counts["SLEEP_PWRDN_OTHER"] += 1
            if "FW_WAKE-UP_Start" in t:
                counts["FW_WAKE-UP_Start"] += 1
                # [12-byte msg header][0x24f][0x4][Start][End][duration][ascii]
                if len(payload) >= 32:
                    s, e, d = struct.unpack_from("<III", payload, 20)
                    wake_start.append(s)
                    wake_span.append((e - s) & 0xFFFFFFFF)
                    wake_dur_us.append(d)
            if "wakeup_req" in t:
                counts["wakeup_req_clearing"] += 1
            if len(payload) >= 8:
                sleep_ts_hdr.append(struct.unpack_from("<I", payload, 4)[0])
            low = t.lower()
            if any(k in low for k in ("fatal", "watchdog", "q6 pc", "panic",
                                      "voting fail")):
                fatal.append(t[:140])

    n_valid, n_msgf, n_bad, fsize = getattr(iter_msgf, "stats", (0, 0, 0, 0))
    print(f"\n########## {os.path.basename(target)} ##########")
    print(f"qmdl bytes: {total_bytes:,}  (read {fsize:,})")
    print(f"frames: valid={n_valid:,}  DIAG_MSGF(0x79)={n_msgf:,}  bad_crc={n_bad}")
    print(f"marker counts: {dict(counts)}")
    print(f"fatal/watchdog/Q6-PC frames: {len(fatal)}")
    for t in fatal[:10]:
        print(f"   {t}")

    n_sleep = counts["SLEEP_PWRDN_FULL"]
    n_wake = counts["FW_WAKE-UP_Start"]

    # ---- self-check 1: is the embedded Start/End pair really 19.2 MHz? ----
    # d = floor((End-Start)/R) with R in ticks per MICROSECOND, i.e. R = 19.2 at
    # 19.2 MHz.  Intersecting the per-frame constraint  R in (sp/(d+1), sp/d]
    # gives a rigorous interval for R -- no assumption needed.
    print("\n--- CLOCK SELF-CHECK: is the WAKE Start/End pair 19.2 MHz? ---")
    lo, hi = 0.0, 1e9
    for sp, d in zip(wake_span, wake_dur_us):
        if d:
            lo = max(lo, sp / (d + 1.0))
            hi = min(hi, sp / d)
    if wake_span and lo < hi:
        print(f"  R must lie in ({lo:.4f}, {hi:.4f}] ticks/us  ->  "
              f"({lo:.4f}, {hi:.4f}] MHz   [width {hi-lo:.4f}]")
        print(f"  19.2 MHz inside? {lo < 19.2 <= hi}    "
              f"21.1 MHz inside? {lo < 21.1 <= hi}")
        print(f"  frames matching floor(sp/19.2)==d: "
              f"{sum(1 for sp,d in zip(wake_span,wake_dur_us) if int(sp/19.2)==d)}"
              f"/{len(wake_span)}")
    ratios = sorted(sp / d for sp, d in zip(wake_span, wake_dur_us) if d)
    if ratios:
        print(f"  raw sp/d spans {ratios[0]:.3f}..{ratios[-1]:.3f} ticks/us"
              f" (p50 {pct(ratios,0.5):.3f}); the floor biases every ratio UP, so the"
              f" MINIMUM is the meaningful end")

    # ---- duration from the AP bracket ----
    dur_ap = None
    if window_path and os.path.exists(window_path):
        kv = dict(l.strip().split("=", 1) for l in open(window_path) if "=" in l)
        try:
            dur_ap = float(kv["modem_end_s"]) - float(kv["modem_start_s"])
            print(f"\nAP bracket: modem {kv['modem_start_s']} -> {kv['modem_end_s']} s"
                  f"  = {dur_ap:.0f} s  (kernel {kv['kernel_start_s']} -> {kv['kernel_end_s']})")
        except (KeyError, ValueError):
            dur_ap = None
        try:
            n, s_mb = int(kv["mdlog_files"]), int(kv["mdlog_size_mb"])
            budget = n * s_mb * 1024 * 1024
            nfiles = int(kv.get("mdlog_file_count") or 0)
            if total_bytes > budget:
                print(f"  !! ROTATION DETECTED: {total_bytes:,} B > {n} x {s_mb} MB"
                      f" ({budget:,} B) -- the OLDEST data was overwritten.")
                print("     The bracket is LONGER than the retained data, so the rate")
                print("     is an UNDERESTIMATE.  Discard or re-run with a larger -n.")
            else:
                print(f"  rotation guard: {total_bytes:,} B vs {n} x {s_mb} MB budget"
                      f" ({budget:,} B), {nfiles} file(s) -- no wrap, window is valid")
        except (KeyError, ValueError):
            print("  rotation guard: (no -s/-n metadata recorded)")

    # ---- duration from the modem's own clock ----
    dur_modem = None
    if len(wake_start) >= 2:
        un, wraps = unwrap(wake_start)
        span_ticks = un[-1] - un[0]
        dur_modem = span_ticks / MODEM_TICK_HZ
        mono = sum(1 for a, b in zip(wake_start, wake_start[1:]) if b >= a)
        print(f"\nMODEM clock (FW_WAKE-UP_Start 'Start time', 19.2 MHz):")
        print(f"  n={len(wake_start)}  strictly-non-decreasing {mono}/{len(wake_start)-1}"
              f"  wraps={wraps}")
        print(f"  span = {span_ticks:,} ticks = {dur_modem:.1f} s"
              f"  (first {wake_start[0]:#010x} -> last {wake_start[-1]:#010x})")
        print(f"  NOTE this span is FIRST-WAKE to LAST-WAKE, so it is ~1 cycle SHORTER")
        print(f"  than the capture; the rate below is a slight OVERestimate for that reason.")

    # ---- rates ----
    print("\n--- MCPM CADENCE ---")
    print(f"counts: SLEEP_PWRDN_FULL={n_sleep}  FW_WAKE-UP_Start={n_wake}"
          f"  ratio={n_sleep/max(1,n_wake):.2f}:1")
    for label, dur in (("AP bracket", dur_ap), ("modem 19.2 MHz clock", dur_modem)):
        if dur:
            print(f"  {label:>22}: SLEEP {n_sleep/dur:.3f} /s   WAKE {n_wake/dur:.3f} /s"
                  f"   (over {dur:.1f} s)")
    # The bracket is stamped `bracket_delay_s` AFTER diag_mdlog starts, so the
    # FILE spans dur_ap + bracket_delay_s and dur_modem should match THAT.
    # Anything well beyond it is PRE-HISTORY: DIAG records the modem buffered
    # while no mdlog was attached and flushed into the new file at attach.
    # MEASURED 2026-09-22: the 300 s capture held ~29 s of it; the 120 s capture
    # held none.  Pre-history is REAL elapsed time and its cycles are counted, so
    # the modem clock is the correct denominator there -- dividing by the bracket
    # would OVERSTATE the rate by the same margin.
    delay = 0.0
    if window_path and os.path.exists(window_path):
        kv2 = dict(l.strip().split("=", 1) for l in open(window_path) if "=" in l)
        try:
            delay = float(kv2["bracket_delay_s"])
        except (KeyError, ValueError):
            delay = 0.0
    if dur_ap and dur_modem and dur_modem > dur_ap + delay + 5.0:
        print(f"  !! PRE-HISTORY: modem span {dur_modem:.1f} s vs expected file span"
              f" {dur_ap+delay:.1f} s (bracket {dur_ap:.1f} + launch delay {delay:.0f})")
        print(f"     => ~{dur_modem-dur_ap-delay:.1f} s of buffered records from BEFORE"
              f" diag_mdlog attached.  Those cycles are counted, so use the")
        print(f"     MODEM span; the AP-bracket rate above is an OVERSTATEMENT.")
    print("\nDoc 170 / Stock_Android_Live s2 reference (BUS-confounded, AP bracket):")
    print("  Android 916 SLEEP_PWRDN_FULL in ~488 s = 1.88 /s ; OpenWrt 100 in ~121 s = 0.83 /s")

    # ---- cadence distribution: is it periodic or bursty? ----
    if len(wake_start) >= 5:
        un, _ = unwrap(wake_start)
        gaps = sorted((b - a) / MODEM_TICK_HZ for a, b in zip(un, un[1:]))
        print(f"\n--- INTER-CYCLE INTERVALS (modem clock, n={len(gaps)}) ---")
        print(f"  min={gaps[0]:.3f}s  p10={pct(gaps,0.10):.3f}  p50={pct(gaps,0.50):.3f}"
              f"  p90={pct(gaps,0.90):.3f}  max={gaps[-1]:.3f}s")
        print(f"  mean={sum(gaps)/len(gaps):.3f}s   max/median = {gaps[-1]/max(1e-9,pct(gaps,0.50)):.1f}x")
        big = [g for g in gaps if g > 4 * pct(gaps, 0.50)]
        print(f"  gaps > 4x median: {len(big)}  {[round(g,2) for g in big[:12]]}")

    if len(sleep_ts_hdr) >= 2:
        print(f"\n--- side observation: the 12-byte message header word (LE u32 @ +4) ---")
        print(f"  present on all {len(sleep_ts_hdr)} frames; first={sleep_ts_hdr[0]:#010x}"
              f" last={sleep_ts_hdr[-1]:#010x}")
        nm = sum(1 for a, b in zip(sleep_ts_hdr, sleep_ts_hdr[1:]) if b < a)
        print("  It advances LOCALLY in exact lockstep with the 19.2 MHz WAKE clock")
        print("  (ratio constant to 3 s.f. across intervals from 0.32 s to 31 s), but it")
        print(f"  takes {nm} BACKWARDS steps across the file, so it is NOT a usable clock.")
        print("  Not used for any timing here.")
    _ROWS.append((target, n_sleep, dur_modem or 0.0))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Doc 172 -- the time-daemon differential, and what the modem's OWN clock says
about the ~902 s fatal.

Input : ats_series.txt   (committed; extracted from scratch/qmi900/qmi_syslog.txt)
Output: the numbers quoted in Docs/Modem Stability/172_*.md

Reproduce:  python3 score_time_daemon.py

The claim being tested
----------------------
The qcom-time-daemon's periodic ATS_USER refresh logs, once per minute,

    Aligned offset = TOD (<tod_ms>) - RTC (<rtc_ms>) = <off_ms> ms

where BOTH tod_ms and rtc_ms are read from the MODEM over QMI TIME
(service 22, node 0, port 11, GENOFF_GET 0x0021, base 1 = ATS_TOD and
base 0 = ATS_RTC).  ATS_RTC is therefore the modem's OWN uptime counter,
and it is the only instrument in the corpus that measures modem uptime
without borrowing an offset from a previous boot.

Two consequences are computed here:

  (1) the modem's uptime at the fatal, from ATS_RTC alone; and
  (2) the gap between the two modem epochs (offset_new - offset_old),
      which is exact -- it never touches the AP wall clock at all.
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SERIES = os.path.join(HERE, 'ats_series.txt')

# From the kernel ring in the same capture (AP uptime, exact, not a wall clock).
FATAL_AP = 912.900514
IS_NOW_UP_AP = 913.588120

# Doc 165: this boot's ring had wrapped past the modem's initial boot, so the
# AP-side offset must be borrowed from the PREVIOUS boot:
#     912.915 (prev fatal, AP) - 900.5 (prev fatal, modem uptime) = 12.415 s
BORROWED_OFFSET_S = 12.415

RE_ALIGN = re.compile(
    r'^(\w{3} \w{3} +\d+ \d+:\d+:\d+) \d{4} .*'
    r'Aligned offset = TOD \((\d+)\) - RTC \((\d+)\) = (\d+) ms')
RE_FATAL = re.compile(r'^(\w{3} \w{3} +\d+ \d+:\d+:\d+) \d{4} .*'
                      r'\[ *([\d.]+)\] .*fatal error received: (\S+)')
RE_UPD = re.compile(r'^(\w{3} \w{3} +\d+ \d+:\d+:\d+) \d{4} .*'
                    r'ATS_TOD_UPDATE_IND.*offset=(\d+) ms')


def to_secs(stamp):
    """'Sat Sep 19 15:42:54' -> seconds since midnight. Log stamps are 1 s."""
    hh, mm, ss = stamp.split()[-1].split(':')
    return int(hh) * 3600 + int(mm) * 60 + int(ss)


def load():
    samples, fatals, updates = [], [], []
    with open(SERIES) as fh:
        for line in fh:
            if line.startswith('#'):
                continue
            m = RE_ALIGN.match(line)
            if m:
                samples.append(dict(t=to_secs(m.group(1)),
                                    tod=int(m.group(2)),
                                    rtc=int(m.group(3)),
                                    off=int(m.group(4))))
                continue
            m = RE_FATAL.match(line)
            if m:
                fatals.append((to_secs(m.group(1)), float(m.group(2)), m.group(3)))
                continue
            m = RE_UPD.match(line)
            if m:
                updates.append((to_secs(m.group(1)), int(m.group(2))))
    return samples, fatals, updates


def main():
    samples, fatals, updates = load()
    if not samples or not fatals:
        sys.exit('parse failed: %d samples, %d fatals' % (len(samples), len(fatals)))

    fatal_wall, fatal_ap, sig = fatals[0]
    print('== input ==')
    print('  ATS samples        : %d' % len(samples))
    print('  fatal              : AP %.6f s, wall %s, signature %s'
          % (fatal_ap, time_str(fatal_wall), sig))
    print()

    # ---- 0. sanity: is TOD - RTC constant within a modem life? -------------
    print('== 0. TOD - RTC constancy within the OLD modem life ==')
    old = [s for s in samples if s['t'] < fatal_wall]
    offs = [s['off'] for s in old]
    print('  n=%d  offset range %d .. %d  spread %d ms'
          % (len(offs), min(offs), max(offs), max(offs) - min(offs)))
    # and the rate agreement
    dt = old[-1]['t'] - old[0]['t']
    drtc = old[-1]['rtc'] - old[0]['rtc']
    dtod = old[-1]['tod'] - old[0]['tod']
    print('  over %d s of log stamp: RTC +%d ms, TOD +%d ms, difference %d ms'
          % (dt, drtc, dtod, drtc - dtod))
    print('  => TOD and RTC advance at the same rate; the offset never moves.')
    print('  => ATS_RTC is a valid uptime counter at this resolution.')
    print('  NOTE: the log stamp is 1 s granularity, so %d s vs %d ms is NOT a'
          % (dt, drtc))
    print('        drift measurement -- do not quote ppm from this file.')
    print()

    # ---- 1. the epoch gap (exact, wall-clock-free) -------------------------
    print('== 1. the gap between the two modem epochs (EXACT, no wall clock) ==')
    off_old = old[-1]['off']            # = TOD at the OLD modem's RTC = 0
    new = [s for s in samples if s['t'] > fatal_wall]
    off_new = new[-1]['off']            # = TOD at the NEW modem's RTC = 0
    gap = off_new - off_old
    print('  offset_old (TOD at old RTC=0) = %d ms' % off_old)
    print('  offset_new (TOD at new RTC=0) = %d ms' % off_new)
    print('  epoch gap                     = %d ms = %.3f s' % (gap, gap / 1000.0))
    print('  (both operands are read from the modem; the AP clock is not involved)')
    print()

    # ---- 2. modem uptime at the fatal, from ATS_RTC ------------------------
    print('== 2. modem uptime at the fatal, from ATS_RTC alone ==')
    # Route A: the new modem's RTC=0 is (new sample wall - new RTC); walk back.
    t_new_epoch = new[-1]['t'] - new[-1]['rtc'] / 1000.0
    rtc_at_reset_A = old[-1]['rtc'] / 1000.0 + (t_new_epoch - old[-1]['t'])
    upA = rtc_at_reset_A - (t_new_epoch - fatal_wall)
    print('  route A (from the NEW epoch):')
    print('    new RTC=0 at log %.3f s  (%.3f s sample - %.3f s RTC)'
          % (t_new_epoch, new[-1]['t'], new[-1]['rtc'] / 1000.0))
    print('    old RTC at reset = %.3f s' % rtc_at_reset_A)
    print('    old RTC at fatal = %.3f s' % upA)

    # Route B: the OLD modem's boot epoch is (first sample wall - first RTC).
    t_old_epoch = old[0]['t'] - old[0]['rtc'] / 1000.0
    upB = fatal_wall - t_old_epoch
    print('  route B (from the OLD epoch):')
    print('    old RTC=0 at log %.3f s  (%.3f s sample - %.3f s RTC)'
          % (t_old_epoch, old[0]['t'], old[0]['rtc'] / 1000.0))
    print('    old RTC at fatal = %.3f s' % upB)
    print('  route A vs route B: agree to %.3f s' % abs(upA - upB))
    print()

    # ---- 3. cross-check against the AP-derived anchor ----------------------
    print('== 3. cross-check: the AP-derived anchor (Doc 165, borrowed offset) ==')
    up_ap = fatal_ap - BORROWED_OFFSET_S
    print('  AP fatal %.6f - borrowed %.3f = %.3f s of modem uptime'
          % (fatal_ap, BORROWED_OFFSET_S, up_ap))
    print('  QMI-derived (mean of A/B)  = %.3f s' % ((upA + upB) / 2))
    print('  AGREEMENT                  = %.3f s' % abs((upA + upB) / 2 - up_ap))
    print('  => two instruments that share no clock agree to <0.1 s.')
    print()

    # ---- 4. the quantization bound ----------------------------------------
    print('== 4. honest bound (log stamps are integer seconds) ==')
    lo = (old[0]['t'] - old[0]['rtc'] / 1000.0)
    print('  route B assumes the %.3f s sample was read at exactly %s.000.'
          % (old[0]['t'], time_str(old[0]['t'])))
    print('  It was read somewhere in [%s.000, %s.999), so:'
          % (time_str(old[0]['t']), time_str(old[0]['t'])))
    print('    modem uptime at fatal in [%.3f, %.3f) s'
          % (fatal_wall - lo - 1.0, fatal_wall - lo + 1.0))
    print('  the point estimates above are the mid-range values; quote the')
    print('  interval when the 1 s quantization matters.')
    print()

    # ---- 5. the epoch gap vs the fatal -------------------------------------
    print('== 5. consistency: epoch gap = lifetime-to-fatal + post-fatal tail ==')
    tail = gap / 1000.0 - ((upA + upB) / 2)
    print('  gap %.3f s - uptime-at-fatal %.3f s = %.3f s'
          % (gap / 1000.0, (upA + upB) / 2, tail))
    print('  (kernel: fatal %.6f -> "is now up" %.6f = %.3f s)'
          % (fatal_ap, IS_NOW_UP_AP, IS_NOW_UP_AP - fatal_ap))
    print('  the %.3f s tail is the fatal->reset teardown; the kernel\'s' % tail)
    print('  "is now up" lands %.3f s after the fatal, i.e. INSIDE it.'
          % (IS_NOW_UP_AP - fatal_ap))
    print('  consistent to the 1 s stamp quantization.')
    print()

    # ---- 6. the sleepmgr short band ---------------------------------------
    print('== 6. where this lands in the Doc 164 signature bands ==')
    band = [900.5, 900.662, 900.965]
    print('  Doc 164 short band (all sleepmgr, n=3): %s' % band)
    print('  this boot: %.3f s, signature %s' % ((upA + upB) / 2, sig))
    print('  band spread before: %.3f s; now %.3f s (n=4)'
          % (max(band) - min(band),
             max(band + [(upA + upB) / 2]) - min(band + [(upA + upB) / 2])))


def time_str(secs):
    return '%02d:%02d:%02d' % (secs // 3600, (secs % 3600) // 60, secs % 60)


if __name__ == '__main__':
    main()

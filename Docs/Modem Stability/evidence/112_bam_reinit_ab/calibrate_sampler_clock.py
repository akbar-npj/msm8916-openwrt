#!/usr/bin/env python3
"""calibrate_sampler_clock.py -- measure the pcfine sampler's UP-label offset.

WHY THIS EXISTS
---------------
`pcfine.sh` reads /proc/uptime in awk's BEGIN block and THEN reads the telemetry
file, so the `<UP>` it prints is a *lower bound* on the time the telemetry values
were actually sampled.  Doc 182 sec.8.2 used sampler timestamps as if they were
comparable to dmesg timestamps; this script measures the error.

Three INDEPENDENT co-observed event types are used, so the result does not rest
on one pairing:

  (a) `cmd_open` counter vs the `received CMD_OPEN (1) on channel N` dmesg lines
      -- the counter and the print are 1:1, so the Nth line pairs with the
      record where `co` reaches N.
  (b) the `pc_state` 1->0 transition vs the `SSR before shutdown: scheduling
      teardown work` line -- `bam_dmux_ssr_notifier_cb()` sets
      `WRITE_ONCE(dmux->pc_state, false)` (qcom_bam_dmux.c:2435) in the
      QCOM_SSR_BEFORE_SHUTDOWN case, i.e. on that very print.
  (c) the `rx_tearing_down` 0->1 transition vs `bam-dmux T5 rx released` --
      `bam_dmux_ssr_teardown()` calls `bam_dmux_power_off()` (:1918), which sets
      `dmux->rx_tearing_down = true` (:1572) before T5 prints.

For (b) and (c) the transition must be located as the FIRST record whose field
value differs from the previous record's -- that record's UP is the lower bound.

Usage:
    calibrate_sampler_clock.py <sampler.txt> <dmesg.txt>
"""

import re
import sys


def sampler_records(path):
    """Yield (UP, dict) for a v2-format capture."""
    out = []
    for ln in open(path, errors="replace"):
        ln = ln.strip()
        if "|" not in ln:
            continue
        head, tail = ln.split("|", 1)
        h = head.split()
        t = tail.split()
        if len(h) < 14 or not t:
            continue
        try:
            out.append((float(t[0]), {
                "iq": int(h[0]), "aq": int(h[1]), "ps": int(h[2]),
                "pl": int(h[3]), "to": int(h[4]), "rs": int(h[5]),
                "ra": int(h[6]), "td": int(h[7]), "rc": int(h[8]),
                "vt": int(h[9]), "vu": int(h[10]), "mra": int(h[11]),
                "msa": int(h[12]), "co": int(h[13]),
            }))
        except ValueError:
            continue
    return out


def first_transition(recs, field, target, after, max_skew=1.0):
    """First record at/after `after` whose `field` == target and differs from the
    previous record.  Returns (UP, prev_UP) or None.

    `max_skew` guards against pairing an event that the capture does not actually
    cover: the capture starts partway through the boot, so without it a fatal
    from before the first record would be paired with that first record."""
    prev = None
    for up, d in recs:
        if prev is not None and d[field] == target and prev[field] != target \
                and up >= after and (up - after) <= max_skew:
            return up, prev_up
        prev = d
        prev_up = up
    return None


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    recs = sampler_records(sys.argv[1])
    dmesg = open(sys.argv[2], errors="replace").read().splitlines()

    cmd = []
    notif = []
    t5 = []
    for ln in dmesg:
        m = re.match(r"\[\s*([0-9]+\.[0-9]+)\]\s.*CMD_OPEN \(1\) on channel (\d+)", ln)
        if m:
            cmd.append(float(m.group(1)))
        m = re.match(r"\[\s*([0-9]+\.[0-9]+)\]\s.*SSR before shutdown", ln)
        if m:
            notif.append(float(m.group(1)))
        m = re.match(r"\[\s*([0-9]+\.[0-9]+)\]\s.*T5 rx released", ln)
        if m:
            t5.append(float(m.group(1)))

    print(f"sampler: {len(recs)} records, {recs[0][0]:.2f}..{recs[-1][0]:.2f} s")
    print(f"dmesg:   {len(cmd)} CMD_OPEN, {len(notif)} 'SSR before shutdown', "
          f"{len(t5)} 'T5 rx released'")
    print()

    print("(a) cmd_open counter  vs  dmesg CMD_OPEN lines")
    print(f"    {'co':>4} {'sampler UP':>11} {'dmesg ts':>11} {'skew':>9}")
    skews = []
    prev_co = None
    for up, d in recs:
        co = d["co"]
        if prev_co is not None and co > prev_co and co <= len(cmd):
            ts = cmd[co - 1]
            skews.append((ts - up, "cmd_open", co))
            print(f"    {co:>4} {up:>11.2f} {ts:>11.6f} {ts-up:>9.4f}")
        prev_co = co

    print()
    print("(b) pc_state 1->0  vs  'SSR before shutdown' (notifier :2435)")
    print(f"    {'fatal#':>6} {'sampler UP':>11} {'dmesg ts':>11} {'skew':>9}")
    for i, ts in enumerate(notif, 1):
        tr = first_transition(recs, "ps", 0, ts - 0.5)
        if tr:
            up, _ = tr
            skews.append((ts - up, "pc_state=0", i))
            print(f"    {i:>6} {up:>11.2f} {ts:>11.6f} {ts-up:>9.4f}")

    print()
    print("(c) rx_tearing_down 0->1  vs  'T5 rx released'")
    print(f"    {'fatal#':>6} {'sampler UP':>11} {'dmesg ts':>11} {'skew':>9}")
    for i, ts in enumerate(t5, 1):
        tr = first_transition(recs, "td", 1, ts - 0.5)
        if tr:
            up, _ = tr
            skews.append((ts - up, "rx_tearing_down=1", i))
            print(f"    {i:>6} {up:>11.2f} {ts:>11.6f} {ts-up:>9.4f}")

    if not skews:
        print("\nno pairs found")
        return
    vals = [s for s, _, _ in skews]
    print(f"\nALL PAIRS (n={len(vals)}): "
          f"min {min(vals):.4f}  max {max(vals):.4f}  mean {sum(vals)/len(vals):.4f} s")
    print("=> the sampler's <UP> label is EARLIER than the telemetry it carries by")
    print("   this much; relative ordering WITHIN the sampler is unaffected.")


if __name__ == "__main__":
    main()

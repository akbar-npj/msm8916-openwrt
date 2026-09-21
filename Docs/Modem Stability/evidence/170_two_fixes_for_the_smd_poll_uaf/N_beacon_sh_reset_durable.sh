#!/bin/sh
# beacon.sh - a liveness witness that does NOT go through printk or /dev/kmsg.
#
# WHY THIS EXISTS (Doc 166 section 5.5, which RETRACTS section 5.4)
#   Both sinks the harness already has are LOG-PATH readers:
#     * the soak's filter copies /dev/kmsg -> /dev/pmsg0 (pmsg-ramoops-0)
#     * the soak's CSV loop calls `dmesg` 3x per iteration
#   devkmsg_read() (kernel/printk/printk.c:822) does NOT poll: it SLEEPS on
#   log_wait whenever the ring holds no new records.  So BY CONSTRUCTION a
#   /dev/kmsg reader cannot distinguish "the kernel has nothing to say" from
#   "the AP is dead" -- its silence is the normal appearance of a healthy
#   reader on an idle kernel.
#
#   This beacon can distinguish them.  It reads ONLY /proc/uptime and appends
#   to a file.  No dmesg, no /dev/kmsg, no sysfs, no shell builtin that can
#   block on a kernel lock.
#
# TWO INDEPENDENT PROCESSES, TWO FILES -- on purpose
#   beacon_a.txt   writes, then calls sync()   -> survives a watchdog reset,
#                                                 but could block if the FS or
#                                                 the writeback path wedges
#   beacon_b.txt   writes, never syncs         -> cannot block on writeback,
#                                                 but its tail may be lost on a
#                                                 reset
#   Comparing A against B therefore separates "the filesystem/writeback path
#   wedged" from "the process itself is gone".
#
# HOW TO READ IT AFTER A HANG (Doc 166 section 11.3; polarity corrected in 5.5)
#   beacon STILL ADVANCING past the last pmsg line
#       -> kernel + userspace are ALIVE; only PRINTING stopped
#       => the stall is CONFINED to the code path that stopped printing, which
#          is exactly what a driver-level lock cycle on one thread looks like.
#          It does NOT exonerate the driver locks (patch 820 section 8).
#   beacon STOPPED at or before the last pmsg line
#       -> a genuine global stall; a watchdog reset is expected.
#
# DEVICE QUIRKS HONOURED (see the q6trace_soak.sh header for the full list)
#   * busybox `sleep` takes INTEGERS only -- never `sleep 0.5`.
#   * there is no `timeout` on this image.
#
# usage: beacon.sh [outdir]        (default /overlay)

OUTDIR=${1:-/overlay}

# --- DO NOT TRUNCATE (Doc 170 §8.10, after the 2026-09-21 17:03 reset) -------
# The original design did `: > beacon_a.txt` at every boot, which DESTROYS the
# previous boot's tail.  On a reset that followed an AP hang, that tail is
# exactly the evidence we need (the last advanced uptime tells us when the
# hang began, and a-vs-b divergence tells us whether the FS/writeback path
# wedged before the global stall).  The 17:03 reset was uncharacterisable
# because of this.  Append a BOOT marker instead, so each boot's segment is
# delimited and the previous boot's final entries survive.
MARK="BOOT $(cut -d' ' -f1 /proc/uptime)s pid=$$ host=$(cat /proc/sys/kernel/hostname 2>/dev/null)"
printf '%s\n' "$MARK" >> "$OUTDIR/beacon_a.txt"
printf '%s\n' "$MARK" >> "$OUTDIR/beacon_b.txt"

# --- A: syncs.  Durable across a reset; can block on writeback. -------------
(
	n=0
	while true; do
		n=$((n + 1))
		printf '%s %s\n' "$(cut -d' ' -f1 /proc/uptime)" "$n" >> "$OUTDIR/beacon_a.txt"
		sync
		sleep 2
	done
) &
A_PID=$!

# --- B: never syncs.  Cannot block on writeback; tail may be lost. ---------
(
	n=0
	while true; do
		n=$((n + 1))
		printf '%s %s\n' "$(cut -d' ' -f1 /proc/uptime)" "$n" >> "$OUTDIR/beacon_b.txt"
		sleep 2
	done
) &
B_PID=$!

echo "[$(cut -d' ' -f1 /proc/uptime)s] beacon start: a=$A_PID (sync) b=$B_PID (nosync) outdir=$OUTDIR" \
	>> "$OUTDIR/beacon.log"

wait

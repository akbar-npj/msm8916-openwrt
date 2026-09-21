#!/bin/sh
# hang_probe_fast.sh - two loops with very different periods, on purpose.
#
# WHY (Doc 168 §9, Priority 2 + 3)
#   Run B's single sampler had a **1.9 s period against a ~2 s failure** and
#   therefore captured NOTHING of the interesting window; its 12 `NO ANSWER`
#   ssh liveness probes began ~6 s AFTER the reset had started.  Neither was a
#   measurement.  So the period is now part of the instrument:
#
#     loop A (~10-20 ms period)  uptime + rx_telemetry only.
#         -> liveness, and the H3-vs-H2 discriminator: if the T4->T5 block is
#            `wait_event(rx_submit_wait, rx_active_submitters == 0)` then
#            rx_active_submitters is NON-ZERO for the whole hang; if it is
#            `dmaengine_terminate_sync()` (H2, an unclocked BAM access) the
#            counters stay 0 and the CPU simply stops.
#
#     loop B (~0.5 s period)     /proc/<pid>/stack for the workers.
#         -> names the blocked function.  Slower because a full 162-pid stack
#            sweep costs ~0.42 s (measured); it may miss a very short hang, so
#            it is a bonus, not the primary instrument.
#
#   Both files are APPENDED (never truncated) and sync()ed periodically, so they
#   survive the reset that ends the hang.
#
# DEVICE QUIRKS HONOURED
#   * busybox `sleep` takes INTEGERS only -- never `sleep 0.5`.
#   * no `timeout` on this image.
#   * `dmesg | grep -c` is safe (busybox dmesg returns EOF); `grep -c /dev/kmsg`
#     is NOT (quirk 13b).
#
# usage: hang_probe_fast.sh [outdir] [seconds]   (default /overlay 120)

OUTDIR=${1:-/overlay}
SECS=${2:-120}
TEL=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry

FAST="$OUTDIR/hp2_fast.log"
STACKS="$OUTDIR/hp2_stacks.log"

# --- loop A: fast, telemetry only.  This is the primary instrument. ---------
(
	n=0
	while [ "$n" -lt 20000 ]; do
		n=$((n + 1))
		up=$(cut -d' ' -f1 /proc/uptime)
		tel=$(grep -E 'rx_active_submitters|rx_active_callbacks|rx_tearing_down|rx_queued_buffers|rx_slots_submitted|pc_state|runtime_status' "$TEL" 2>/dev/null | tr '\n' ' ')
		printf '%s %s %s\n' "$n" "$up" "$tel" >> "$FAST"
		[ $((n % 20)) -eq 0 ] && sync
	done
) &

# --- loop B: slow, stacks.  Bonus localisation. ----------------------------
(
	m=0
	end=$((SECS * 2))
	while [ "$m" -lt "$end" ]; do
		m=$((m + 1))
		up=$(cut -d' ' -f1 /proc/uptime)
		t4=$(dmesg | grep -c 'SSR teardown T4')
		{
			echo "=== B$m up=$up t4=$t4 ==="
			for p in /proc/[0-9]*; do
				c=$(cat "$p/comm" 2>/dev/null)
				case "$c" in
				kworker*|*bam*|*rproc*|*qmi*)
					s=$(cat "$p/stack" 2>/dev/null)
					echo "--- ${p#/proc/} $c wchan=$(cat "$p/wchan" 2>/dev/null)"
					[ -n "$s" ] && echo "$s"
					;;
				esac
			done
		} >> "$STACKS" 2>&1
		sync
		sleep 1
	done
	echo "=== B DONE m=$m up=$(cut -d' ' -f1 /proc/uptime) ===" >> "$STACKS"
) &

echo "[$(cut -d' ' -f1 /proc/uptime)s] hang_probe_fast start outdir=$OUTDIR secs=$SECS" >> "$OUTDIR/hp2_start.log"
wait

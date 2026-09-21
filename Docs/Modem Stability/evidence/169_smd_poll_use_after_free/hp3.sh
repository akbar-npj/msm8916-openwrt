#!/bin/sh
# hp3.sh - fast SSR-window sampler for the bam_dmux teardown hang (Doc 168 / Doc 169).
#
# WHY THIS EXISTS
#   Run B's sampler had a 1.9 s period against a ~2 s failure and captured
#   NOTHING of the interesting window.  The period is part of the instrument, so
#   this one uses ONLY shell builtins + /proc reads (no fork per field), giving a
#   period of a few ms -- >100x faster than the phenomenon.
#
# INSTRUMENT SAFETY -- checked in the SOURCE, not assumed
#   rx_telemetry_show() (qcom_bam_dmux.c:2119) takes ONLY rx_lock, a short
#   spinlock; it does NOT take state_lock.  Consequences:
#     * a sampler that blocks is NOT confusable with a state_lock deadlock;
#     * if telemetry DOES stop while uptime keeps advancing, that itself
#       localises the hang to rx_lock (or to the sysfs/print path).
#   The uptime line is therefore written BEFORE sysfs is touched, so the two
#   failure modes are distinguishable:
#       U lines stop          -> CPU/scheduler dead (or the writer is stuck)
#       U advances, T stops   -> the telemetry read blocked (rx_lock held)
#
# DEVICE QUIRKS HONOURED
#   * busybox `sleep` takes INTEGERS only.
#   * no `timeout`, no `pkill` on this image.
#   * `dmesg | grep -c` is safe; `grep -c /dev/kmsg` never terminates (quirk 13b).
#
# usage: hp3.sh [outdir] [seconds]      (default /overlay 120)

OUTDIR=${1:-/overlay}
SECS=${2:-120}
TEL=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry
FAST="$OUTDIR/hp3_fast.log"
STACKS="$OUTDIR/hp3_stacks.log"

# --- loop A: fast, telemetry only.  The primary instrument. ----------------
(
	n=0
	while [ "$n" -lt 200000 ]; do
		n=$((n + 1))
		read -r up _ < /proc/uptime
		printf 'U %s %s\n' "$n" "$up" >> "$FAST"
		acc=""
		while IFS= read -r line; do
			case "$line" in
			rx_active_submitters:*|rx_active_callbacks:*|rx_tearing_down:*|\
			rx_slots_submitted:*|rx_slots_free:*|rx_slots_mapped:*|\
			rx_queued_buffers:*|rx_submit_failed:*|pc_state:*|pc_line_level:*|\
			rx_rearm_count:*|rx_last_callback_ms_ago:*|pc_quiesce_ms:*|\
			runtime_status:*|pm_usage_count:*|tx_defer_queued:*|\
			tx_defer_submitted:*|tx_defer_preserved:*|tx_defer_wiped_live:*)
				acc="$acc ${line%%:*}=${line#*:}" ;;
			esac
		done < "$TEL"
		printf 'T %s %s%s\n' "$n" "$up" "$acc" >> "$FAST"
		[ $((n % 10)) -eq 0 ] && sync
	done
) &

# --- loop B: slow, stacks.  Names the blocked function. --------------------
(
	m=0
	while [ "$m" -lt "$SECS" ]; do
		m=$((m + 1))
		read -r up _ < /proc/uptime
		{
			echo "=== S$m up=$up ==="
			for p in /proc/[0-9]*; do
				c=$(cat "$p/comm" 2>/dev/null)
				case "$c" in
				kworker*|*bam*|*rproc*|*qmi*|*netifd*|*procd*)
					echo "--- ${p#/proc/} $c wchan=$(cat "$p/wchan" 2>/dev/null)"
					cat "$p/stack" 2>/dev/null
					;;
				esac
			done
		} >> "$STACKS" 2>&1
		sync
		sleep 1
	done
	read -r u _ < /proc/uptime
	echo "=== S DONE m=$m up=$u ===" >> "$STACKS"
) &

echo "[start] hp3 outdir=$OUTDIR secs=$SECS" >> "$OUTDIR/hp3_start.log"
wait

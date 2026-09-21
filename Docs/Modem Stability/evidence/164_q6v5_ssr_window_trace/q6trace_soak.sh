#!/bin/sh
# q6trace_soak.sh - capture the q6v5 SSR-window trace across a modem fatal, and
#                   across an AP hang, on the HMU05.
#
# WHY THIS EXISTS
#   Doc 159 established that the AP can hang inside the q6v5 SSR window (20 of
#   21 crashes recovered; the 21st stopped the whole AP until the hardware
#   watchdog reset it), that it leaves NO panic banner and NO dmesg-ramoops
#   record, and that `port failed halt` is a red herring (21 crashes, 21
#   occurrences, 20 recoveries 43-47 ms later).  Patch 817 puts one
#   `q6v5-trace: NN <step>` line immediately BEFORE each of the 13 steps in
#   that window, so the LAST trace line printed names the step that hung.
#
# WHY /dev/kmsg AND NOT THE CONSOLE
#   printk() stores into the ring buffer BEFORE it takes the console lock, so a
#   line is visible to a /dev/kmsg reader even when the console write that
#   follows never completes.  Reading /dev/kmsg is therefore the only in-guest
#   instrument that can still see the last line of a console-lock hang.
#
# WHY THERE ARE THREE SINKS
#   $OUT   /overlay, the full stream, flushed by a `sync` loop every 0.5 s.
#          Survives a watchdog reset; can lose up to ~0.5 s of page cache.
#   $TRACE /overlay, only the interesting lines.  Tiny, so it is what a human
#          reads first after a reboot.
#   /dev/pmsg0  the SAME interesting lines, written into the reserved ramoops
#          RAM region.  No filesystem and no page cache, so it survives a reset
#          by construction -- it is the backstop for exactly the case where the
#          sync loop dies with the AP.
#
# DEVICE QUIRKS THIS SCRIPT WORKS AROUND (each one has bitten this project)
#   * busybox `sleep` takes INTEGERS only -- `sleep 0.5` is "invalid number".
#     Same class of trap as `ping -i 0.05` (Doc 156 section 6).  `usleep` exists.
#   * there is NO `timeout` on this image.
#   * `/dev/kmsg` does not work with the shell's `read` redirect
#     (`while read l; do ... done < /dev/kmsg` reads nothing); it must be piped
#     from `cat`.  On open it REPLAYS the whole ring buffer from the oldest
#     record, so the first seconds of $OUT are history, not live.
#   * /dev/kmsg records carry the payload on one line and the metadata
#     (`SUBSYSTEM=`, `DEVICE=`) on following lines that start with a space.
#
# usage: q6trace_soak.sh [outdir]        (default /overlay)

OUTDIR=${1:-/overlay}
OUT=$OUTDIR/q6trace_stream.log
TRACE=$OUTDIR/q6trace_trace.log
CSV=$OUTDIR/q6trace.csv
LOG=$OUTDIR/q6trace.log
TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
NET=/sys/class/net/wwan0/statistics

log() { echo "[$(cut -d' ' -f1 /proc/uptime)s] $*" >> "$LOG"; }

# ---------------------------------------------------------------------------
# 1. Full kernel-log stream -> $OUT, flushed by a sync loop.
# ---------------------------------------------------------------------------
: > "$OUT"
(
	cat /dev/kmsg >> "$OUT"
) &
STREAM_PID=$!

(
	while true; do
		usleep 500000
		sync
	done
) &
SYNC_PID=$!

# ---------------------------------------------------------------------------
# 2. Filtered trace -> $TRACE and -> /dev/pmsg0.
#    The message rate on this device is ~0.1 lines/s (486 records in 4700 s),
#    so a shell `case` filter is far faster than the input; the `>>` opens are
#    only taken on a match.
# ---------------------------------------------------------------------------
: > "$TRACE"
(
	cat /dev/kmsg | while IFS= read -r line; do
		case "$line" in
		*q6v5-trace:*|*"fatal error received"*|*"port failed halt"*|\
		*"MBA booted"*|*"is now up"*|*"stopped remote processor"*|\
		*"SSR "*|*"SSR:"*|*"Unable to handle"*|*"Kernel panic"*|\
		*"BUG:"*|*"watchdog"*|*"Watchdog"*)
			printf '%s\n' "$line" >> "$TRACE"
			printf '%s\n' "$line" >> /dev/pmsg0 2>/dev/null
			;;
		esac
	done
) &
FILTER_PID=$!

# ---------------------------------------------------------------------------
# 3. Burst traffic, to raise the fatal rate.
#    A shell loop of `ping -c 1`, never `ping -i <fraction>`: this busybox ping
#    accepts only integer -i values and fails instantly on `-i 0.05`, which with
#    the output redirected is INVISIBLE -- it silently invalidated a whole soak
#    once (Doc 156 section 6).
# ---------------------------------------------------------------------------
burst() {
	i=0
	while [ "$i" -lt "${BURST_N:-40}" ]; do
		ping -c 1 -W 1 -q 8.8.8.8 >/dev/null 2>&1
		i=$((i + 1))
	done
}
(
	while true; do
		sleep "${BURST_IDLE:-6}"
		burst
	done
) &
BURST_PID=$!

log "=== q6trace_soak start; stream=$STREAM_PID filter=$FILTER_PID sync=$SYNC_PID burst=$BURST_PID"
log "    oops=$(dmesg | grep -c 'Unable to handle') fatal=$(dmesg | grep -ci 'fatal error received') ssr=$(dmesg | grep -c 'stopped remote processor')"

echo "uptime,oops,fatal,ssr,traces,defer_q,defer_sub,defer_keep,defer_wipe_live,guard_hits,pc_irq,pc_resync,pc_state,rx_mapped,cmd_open,tx_pkts,rx_pkts" > "$CSV"

last_fatal=""
last_trace=""
while true; do
	up=$(cut -d' ' -f1 /proc/uptime)
	oops=$(dmesg | grep -c 'Unable to handle')
	fatal=$(dmesg | grep -ci 'fatal error received')
	ssr=$(dmesg | grep -c 'stopped remote processor')
	traces=$(grep -c 'q6v5-trace:' "$TRACE" 2>/dev/null)
	[ -n "$traces" ] || traces=0

	g() { grep "^$1:" "$TEL" 2>/dev/null | awk '{print $2}'; }
	echo "$up,$oops,$fatal,$ssr,$traces,$(g tx_defer_queued),$(g tx_defer_submitted),$(g tx_defer_preserved),$(g tx_defer_wiped_live),$(g tx_sweep_guard_hits),$(g pc_irq_count),$(g pc_resync_count),$(g pc_state),$(g rx_slots_mapped),$(g cmd_open),$(cat $NET/tx_packets 2>/dev/null),$(cat $NET/rx_packets 2>/dev/null)" >> "$CSV"

	# --- the headline: every q6v5-trace line, in order, with its timestamp ---
	# If the AP hangs, the LAST line this prints is the step that hung.  Print
	# only new ones so the log stays readable.
	now_trace=$(grep 'q6v5-trace:' "$TRACE" 2>/dev/null | tail -1)
	if [ -n "$now_trace" ] && [ "$now_trace" != "$last_trace" ]; then
		grep 'q6v5-trace:' "$TRACE" 2>/dev/null | tail -40 >> "$LOG"
		last_trace=$now_trace
	fi

	# --- fatals, with the full trace block that surrounds each one ---
	# NOTE the `-gt 0` guard: `[ -n "0" ]` is TRUE, so a bare `-n` logs a
	# spurious "FATAL COUNT 0" on the first iteration of every boot.
	if [ "$fatal" -gt 0 ] && [ "$fatal" != "$last_fatal" ]; then
		log "FATAL COUNT $fatal at AP ${up}s"
		dmesg | grep -B2 -A2 'fatal error received' | tail -20 >> "$LOG"
		last_fatal=$fatal
	fi

	if [ "$oops" -gt 0 ]; then
		log "OOPS at ${up}s"
		dmesg | grep -A40 'Unable to handle' >> "$LOG"
	fi

	sleep 5
done

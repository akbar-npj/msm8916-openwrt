#!/bin/sh
# hang_probe.sh - localise the T4->T5 block in bam_dmux_power_off().
#
# WHY (Doc 168 section 9 / the 821 result)
#   With patch 821, `echo stop` blocks the *writer* inside flush_work() in the
#   QCOM_SSR_BEFORE_SHUTDOWN notifier, and the last console line is
#   "SSR teardown T4 state_lock acquired".  That means the teardown *work*
#   itself is stuck somewhere between T4 (:2305) and T5 (:1606), i.e. in one of
#     cancel_delayed_work_sync(&rx_rearm_work)          (:1591)
#     wait_event(rx_submit_wait, rx_active_submitters==0) (:1595)
#     dmaengine_terminate_sync(rx) + dma_release_channel  (:1600)
#   and with the modem still powered (no q6v5-trace at all).
#
#   Two things must be separated before anything else:
#     (a) is the AP a STALLED CPU (bus hang) or only a BLOCKED PATH?
#     (b) if only a path, WHICH function is it blocked in?
#
#   This probe answers both, from userspace, with no kernel build:
#     * it appends /proc/uptime to a file every sweep -> if the file stops
#       growing the CPU is gone (a genuine global stall)
#     * it records /proc/<pid>/stack for every thread whose stack is non-empty
#       -> the blocked kworker names the function
#     * it records rx_telemetry -> if the block is the wait_event, then
#       rx_active_submitters is non-zero for the whole hang
#
#   The file is APPENDED, never truncated, and sync()ed every sample, so it
#   survives the watchdog reset that ends the hang.  (That is the one thing
#   /overlay/beacon_a.txt gets wrong: it truncates at start, so each reboot
#   destroys the previous hang's tail.)
#
# DEVICE QUIRKS HONOURED
#   * busybox `sleep` takes INTEGERS only -- never `sleep 0.5`
#   * no `timeout` on this image; no `pkill`
#   * a full 162-pid stack sweep costs ~0.42 s (measured), so each sample is
#     ~1 s with the sync()
#
# usage: hang_probe.sh [outfile] [max_samples]   (default /overlay/hangprobe.log 300)

OUT=${1:-/overlay/hangprobe.log}
MAX=${2:-300}
TEL=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry

n=0
while [ "$n" -lt "$MAX" ]; do
	n=$((n + 1))
	up=$(cut -d' ' -f1 /proc/uptime)
	np=$(ls -d /proc/[0-9]* 2>/dev/null | wc -l)
	t4=$(dmesg | grep -c "SSR teardown T4")

	{
		echo "=== S$n up=$up pids=$np t4=$t4 ==="
		if [ -r "$TEL" ]; then
			grep -E "rx_active_submitters|rx_active_callbacks|rx_tearing_down|rx_queued_buffers|rx_slots_submitted|rx_slots_submitting|runtime_status|pc_state|tx_defer" "$TEL"
		fi
		for p in /proc/[0-9]*; do
			s=$(cat "$p/stack" 2>/dev/null)
			[ -n "$s" ] || continue
			pid=${p#/proc/}
			echo "--- $pid $(cat "$p/comm" 2>/dev/null) $(cut -d' ' -f3 "$p/stat" 2>/dev/null) wchan=$(cat "$p/wchan" 2>/dev/null)"
			echo "$s"
		done
	} >> "$OUT" 2>&1

	sync
done

echo "=== DONE n=$n up=$(cut -d' ' -f1 /proc/uptime) ===" >> "$OUT"

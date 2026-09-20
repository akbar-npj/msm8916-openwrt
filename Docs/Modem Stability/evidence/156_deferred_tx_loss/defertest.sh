#!/bin/sh
# defertest.sh - A/B test for the deferred-TX loss (patch 811 / 812).
#
# QUESTION: when the AP sends the first packet after an A2 power collapse,
# bam_dmux_netdev_start_xmit() takes the defer branch (the async
# pm_runtime_get() it just issued is still in flight), so the packet is only
# marked in tx_deferred_skb.  Does that packet ever reach the BAM?
#
# METHOD: let the modem collapse (idle), take one snapshot, send exactly ONE
# packet, take another snapshot.  The counters are cumulative, so the deltas
# across the single ping are the whole story.
#
# READING THE RESULT
#   tx_defer_queued    +1  -> the packet took the defer branch (expected)
#   tx_defer_wiped_live +1  -> bam_dmux_pm_restart() destroyed the real packet
#   tx_defer_submitted  +0  -> bam_dmux_tx_wakeup_work() never submitted it
#   tx_complete         +0  -> the BAM never completed it
#   rx_packets          +0  -> the ping was lost (drx=0)
# is the loss.  A healthy wake is tx_defer_submitted +1 / tx_complete +1 /
# rx_packets +1.

TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
NET=/sys/class/net/wwan0/statistics

snap() {
	for k in tx_defer_queued tx_defer_submitted tx_defer_wiped \
		 tx_defer_wiped_live tx_submit_ok tx_complete \
		 tx_sweep_guard_hits pc_vote_tx_count pm_suspend_attempts \
		 pm_resume_attempts; do
		v=$(grep "^$k:" "$TEL" 2>/dev/null | awk '{print $2}')
		printf '%s=%s ' "$k" "${v:-NA}"
	done
	printf 'tx_pkts=%s rx_pkts=%s ' \
		"$(cat $NET/tx_packets 2>/dev/null)" \
		"$(cat $NET/rx_packets 2>/dev/null)"
	printf 'rs=%s pc=%s\n' \
		"$(grep '^runtime_status:' "$TEL" | awk '{print $2}')" \
		"$(grep '^pc_state:' "$TEL" | awk '{print $2}')"
}

round=0
while [ "$round" -lt "${ROUNDS:-5}" ]; do
	round=$((round + 1))
	echo "=== round $round ==="
	# Idle long enough for autosuspend (1 s) + the modem's own collapse
	# (~5.4 s measured).  Nothing here generates traffic.
	sleep 25
	before=$(snap)
	out=$(ping -c 1 -W 5 8.8.8.8 2>&1 | grep -E 'packets transmitted|100% packet loss' | tr '\n' ' ')
	after=$(snap)
	echo "before: $before"
	echo "ping  : $out"
	echo "after : $after"
done

echo "=== done ==="

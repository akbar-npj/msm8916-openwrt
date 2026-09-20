#!/bin/sh
# repro120.sh - the EXACT documented reproduction of the stall, with patch 812.
#
# project_stall_first_packet_lost.md recorded this as 5/5 before the fix:
#   120 s idle, then ONE ping  ->  replies=0/1 took=5s, dtx=1 drx=0
#   the retry always worked
# This runs the same thing and reports the bam_dmux counters across each ping.

TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
NET=/sys/class/net/wwan0/statistics
g() { grep "^$1:" "$TEL" 2>/dev/null | awk '{print $2}'; }

round=0
while [ "$round" -lt "${ROUNDS:-3}" ]; do
	round=$((round + 1))
	sleep 120
	bq=$(g tx_defer_queued); bs=$(g tx_defer_submitted)
	bw=$(g tx_defer_wiped_live); bp=$(g pc_state)
	btx=$(cat $NET/tx_packets); brx=$(cat $NET/rx_packets)
	out=$(ping -c 1 -W 5 8.8.8.8 2>&1)
	aq=$(g tx_defer_queued); as=$(g tx_defer_submitted)
	aw=$(g tx_defer_wiped_live)
	atx=$(cat $NET/tx_packets); arx=$(cat $NET/rx_packets)
	recv=$(echo "$out" | sed -n 's/.*, \([0-9]*\) packets received.*/\1/p')
	echo "round $round: pc_state_before=$bp defer +$((aq-bq)) submitted +$((as-bs)) wiped_live +$((aw-bw)) dtx=+$((atx-btx)) drx=+$((arx-brx)) received=${recv:-?}/1"
done
echo "done"

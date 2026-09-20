#!/bin/sh
# soak812.sh - long soak of the patch-812 deferred-TX fix.
#
# WHAT IT CHECKS
#   1. No oops / no fatal / no SSR (the usual stability gates).
#   2. tx_defer_queued == tx_defer_submitted, i.e. every packet that took the
#      defer branch was eventually submitted.  The gap
#      (queued - submitted) is a running count of lost packets; before patch
#      812 it grew by one for every A2 collapse.
#   3. tx_defer_wiped_live stays 0 (nothing destroyed by the wake path).
#
# TRAFFIC PATTERN
#   Idle 6 s, then 40 packets at 0.05 s.  Continuous traffic holds the modem
#   permanently awake and never opens the defer window; the burst pattern
#   collapses the modem and then hits it with TX while the resume is in
#   flight, which is exactly the path under test.

TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
NET=/sys/class/net/wwan0/statistics
OUT=/overlay/soak812.csv
LOG=/overlay/soak812.log

log() { echo "[$(cut -d' ' -f1 /proc/uptime)s] $*" >> "$LOG"; }

(
	while true; do
		sleep 6
		ping -c 40 -i 0.05 -W 1 -q 8.8.8.8 >/dev/null 2>&1
	done
) &
GEN=$!
log "started burst generator pid $GEN (6 s idle, then 40 packets @0.05 s)"
log "=== soak812 start; oops=$(dmesg | grep -c 'Unable to handle') ==="

echo "uptime,oops,fatal,ssr,pc_irq,pc_vote,pm_susp,pm_res,pc_state,rx_cb,tx_pkts,rx_pkts,defer_q,defer_sub,defer_keep,defer_wipe_live,submit_ok,tx_complete,guard_hits" > "$OUT"

last_q=""
while true; do
	up=$(cut -d' ' -f1 /proc/uptime)
	oops=$(dmesg | grep -c 'Unable to handle')
	fatal=$(dmesg | grep -ci 'fatal error received')
	ssr=$(dmesg | grep -c 'stopped remote processor')

	t=$TEL
	g() { grep "^$1:" "$t" 2>/dev/null | awk '{print $2}'; }
	pc_irq=$(g pc_irq_count)
	vote=$(g pc_vote_tx_count)
	psusp=$(g pm_suspend_attempts)
	pres=$(g pm_resume_attempts)
	pcst=$(g pc_state)
	rcb=$(g rx_callbacks)
	dq=$(g tx_defer_queued)
	ds=$(g tx_defer_submitted)
	dk=$(g tx_defer_preserved)
	dwl=$(g tx_defer_wiped_live)
	so=$(g tx_submit_ok)
	tc=$(g tx_complete)
	gh=$(g tx_sweep_guard_hits)
	tp=$(cat $NET/tx_packets 2>/dev/null)
	rp=$(cat $NET/rx_packets 2>/dev/null)

	echo "$up,$oops,$fatal,$ssr,$pc_irq,$vote,$psusp,$pres,$pcst,$rcb,$tp,$rp,$dq,$ds,$dk,$dwl,$so,$tc,$gh" >> "$OUT"

	# The headline metric: a deferred packet that was never submitted is a
	# lost packet.  Report the first time the gap grows.
	if [ -n "$last_q" ] && [ "$dq" -gt "$last_q" ] && [ "$ds" -lt "$dq" ]; then
		log "DEFERRED PACKET LOST: queued=$dq submitted=$ds gap=$((dq - ds)) at uptime ${up}s"
	fi
	last_q=$dq

	if [ "$oops" -gt 0 ]; then
		log "OOPS DETECTED at uptime ${up}s"
		dmesg | grep -A40 'Unable to handle' >> "$LOG"
		cp "$t" "/overlay/soak812_telemetry_oops.txt" 2>/dev/null
	fi

	if [ "$fatal" -ge 2 ]; then
		log "two fatals seen; stopping soak"
		dmesg > /overlay/soak812_dmesg_final.txt
		break
	fi

	sleep 10
done

log "=== soak812 end; oops=$(dmesg | grep -c 'Unable to handle') fatal=$(dmesg | grep -ci 'fatal error received') ==="

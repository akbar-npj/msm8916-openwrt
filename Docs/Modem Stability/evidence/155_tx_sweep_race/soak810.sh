#!/bin/sh
# soak810.sh - soak the patch-810 bam_dmux fix.
#
# WHY A BURST PATTERN (not 1 Hz):
#   The TX-sweep race needs TWO things at once:
#     (a) the modem to be power-collapsed so start_xmit takes the defer branch
#         at qcom_bam_dmux.c:597, and
#     (b) a wake edge (bam_dmux_pm_restart -> bam_dmux_free_skbs) to land in the
#         window between start_xmit's tx_queue() and its tx_deferred_skb write.
#   A continuous 1 Hz ping keeps the modem permanently awake -- measured: pc_irq
#   frozen at 29 for >100 s -- so it never collapses and the window never opens.
#   The oops boot (Doc 154) had pm_suspend_attempts: 185, i.e. a collapsing modem.
#
#   So: stay idle long enough to collapse (~20 s), then fire a short burst of
#   packets, so TX and the wake edge collide repeatedly.  One keepalive ping per
#   cycle also keeps the bearer from dropping.
#
# Samples: uptime, oops, fatal, ssr, and the bam_dmux telemetry words that move
# with the sweep.

TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
OUT=/overlay/soak810.csv
LOG=/overlay/soak810.log

log() { echo "[$(cut -d' ' -f1 /proc/uptime)s] $*" >> "$LOG"; }

# Burst generator: idle 20 s, then 8 packets at 0.2 s, forever.
(
	while true; do
		sleep 20
		ping -c 8 -i 0.2 -W 2 -q 8.8.8.8 >/dev/null 2>&1
	done
) &
GEN=$!
log "started burst generator pid $GEN (20 s idle, then 8 packets @0.2 s)"
log "=== soak810 start; oops=$(dmesg | grep -c 'Unable to handle') ==="

echo "uptime,oops,fatal,ssr,pc_irq,pc_vote,pm_susp,pm_res,pc_state,rx_cb,rx_last_ms,pm_last_susp_ms" > "$OUT"

while true; do
	up=$(cut -d' ' -f1 /proc/uptime)
	oops=$(dmesg | grep -c 'Unable to handle')
	fatal=$(dmesg | grep -ci 'fatal error received')
	ssr=$(dmesg | grep -c 'stopped remote processor')

	t=$TEL
	pc_irq=$(grep '^pc_irq_count:' "$t" 2>/dev/null | awk '{print $2}')
	vote=$(grep '^pc_vote_tx_count:' "$t" 2>/dev/null | awk '{print $2}')
	psusp=$(grep '^pm_suspend_attempts:' "$t" 2>/dev/null | awk '{print $2}')
	pres=$(grep '^pm_resume_attempts:' "$t" 2>/dev/null | awk '{print $2}')
	pcst=$(grep '^pc_state:' "$t" 2>/dev/null | awk '{print $2}')
	rcb=$(grep '^rx_callbacks:' "$t" 2>/dev/null | awk '{print $2}')
	rlast=$(grep '^rx_last_callback_ms_ago:' "$t" 2>/dev/null | awk '{print $2}')
	plast=$(grep '^pm_last_suspend_ms:' "$t" 2>/dev/null | awk '{print $2}')

	echo "$up,$oops,$fatal,$ssr,$pc_irq,$vote,$psusp,$pres,$pcst,$rcb,$rlast,$plast" >> "$OUT"

	# The moment an oops appears, snapshot everything for the record.
	if [ "$oops" -gt 0 ]; then
		log "OOPS DETECTED at uptime ${up}s"
		dmesg | grep -A40 'Unable to handle' >> "$LOG"
		cp "$t" "/overlay/soak810_telemetry_oops.txt" 2>/dev/null
	fi

	# Stop once the modem has fataled twice; the boot is no longer clean.
	if [ "$fatal" -ge 2 ]; then
		log "two fatals seen; stopping soak"
		dmesg > /overlay/soak810_dmesg_final.txt
		break
	fi

	sleep 10
done

log "=== soak810 end; oops=$(dmesg | grep -c 'Unable to handle') fatal=$(dmesg | grep -ci 'fatal error received') ==="

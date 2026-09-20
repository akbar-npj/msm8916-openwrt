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
#   So: stay idle long enough to collapse, then fire a short burst of packets, so
#   TX and the wake edge collide repeatedly.  One keepalive ping per cycle also
#   keeps the bearer from dropping.  See the tuning note at the burst generator.
#
# Samples: uptime, oops, fatal, ssr, and the bam_dmux telemetry words that move
# with the sweep.

TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
OUT=/overlay/soak810.csv
LOG=/overlay/soak810.log

log() { echo "[$(cut -d' ' -f1 /proc/uptime)s] $*" >> "$LOG"; }

# ===========================================================================
# RETRACTED 2026-09-21 (Doc 155 §8.5).  As originally run, this harness used
# `ping -c 40 -i 0.05`, and this device's busybox `ping` accepts INTEGER `-i`
# ONLY -- the command failed instantly with "ping: invalid number '0.05'", and
# the error was hidden by the /dev/null redirect.  The generator stayed alive,
# looped on its `sleep`, and transmitted NOTHING.  Every edge count attributed
# to a "burst" in Doc 155 §8.2/§8.3 was the modem's own ~5.4 s collapse cadence
# plus background AP traffic.  The burst below is the corrected form; the
# original run's numbers are NOT reproducible with it.
#
# The tuning rationale (TX density while a resume is in flight) is plausible
# but was never actually tested.
# ===========================================================================
burst() {
	i=0
	while [ "$i" -lt "${BURST_N:-40}" ]; do
		ping -c 1 -W 1 -q 8.8.8.8 >/dev/null 2>&1
		i=$((i + 1))
	done
}

# Burst generator: idle 6 s, then 40 x `ping -c 1`, forever.
#
# `ping -c 1` needs no `-i` at all, which is the whole point: measured 20
# packets in 1.24 s (tx_pkts +20, rx_pkts +20, pc_irq +1).  Do NOT substitute
# `ping -i 0` -- it hangs unboundedly and there is no `timeout(1)` here.
(
	while true; do
		sleep 6
		burst
	done
) &
GEN=$!
log "started burst generator pid $GEN (6 s idle, then ${BURST_N:-40} x ping -c 1)"
log "=== soak810 start; oops=$(dmesg | grep -c 'Unable to handle') ==="

echo "uptime,oops,fatal,ssr,pc_irq,pc_vote,pm_susp,pm_res,pc_state,rx_cb,rx_last_ms,pm_last_susp_ms,guard_hits" > "$OUT"

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
	ghits=$(grep '^tx_sweep_guard_hits:' "$t" 2>/dev/null | awk '{print $2}')
	[ -z "$ghits" ] && ghits=NA

	echo "$up,$oops,$fatal,$ssr,$pc_irq,$vote,$psusp,$pres,$pcst,$rcb,$rlast,$plast,$ghits" >> "$OUT"

	# The moment an oops appears, snapshot everything for the record.
	if [ "$oops" -gt 0 ]; then
		log "OOPS DETECTED at uptime ${up}s"
		dmesg | grep -A40 'Unable to handle' >> "$LOG"
		cp "$t" "/overlay/soak810_telemetry_oops.txt" 2>/dev/null
	fi

	# A guard hit is a crash that did NOT happen -- the whole point of patch 810.
	if [ -n "$last_ghits" ] && [ "$ghits" != "NA" ] && [ "$ghits" != "$last_ghits" ]; then
		log "TX-SWEEP GUARD FIRED: guard_hits $last_ghits -> $ghits at uptime ${up}s (0 oopses)"
	fi
	last_ghits=$ghits

	# Stop once the modem has fataled twice; the boot is no longer clean.
	if [ "$fatal" -ge 2 ]; then
		log "two fatals seen; stopping soak"
		dmesg > /overlay/soak810_dmesg_final.txt
		break
	fi

	sleep 10
done

log "=== soak810 end; oops=$(dmesg | grep -c 'Unable to handle') fatal=$(dmesg | grep -ci 'fatal error received') ==="

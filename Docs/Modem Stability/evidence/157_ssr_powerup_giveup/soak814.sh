#!/bin/sh
# soak814.sh - soak of the patch-814 SSR-powerup-retry fix.
#
# WHAT IT CHECKS, in priority order
#
#   1. THE FIX: after every SSR, does the data plane come back on its own?
#      Before patch 814 the powerup work gave up after 3.2 s, dmux->rx/tx
#      stayed NULL, wwan0 stayed DOWN and the default route was deleted.  So
#      after each SSR this script probes `ip route show default` and pings,
#      and logs the verdict.  A "DATA PLANE DOWN" line is the bug.
#
#   2. WHETHER THE RETRY PATH RAN: every "SSR powerup: ... retry N/20" line is
#      a case where the modem was slower than the original 3.2 s budget, i.e.
#      a case that would have failed before the patch.
#
#   3. WHETHER THE WATCHDOG REBUILD RAN: "modem awake (pc line asserted) but
#      no channels, rebuilding" and pc_resync_count.
#
#   4. The patch-812 gates still hold: tx_defer_queued - tx_defer_submitted
#      must stay 0, tx_defer_wiped_live must stay 0.
#
#   5. The fatal cadence (patch 814 is NOT a stability fix; fatals are expected
#      and each one triggers the SSR recovery path this script exercises).
#
# TRAFFIC PATTERN
#   Idle 6 s, then 40 x `ping -c 1`.  See the TRAP note below -- this MUST be a
#   shell loop, never `ping -i <fraction>`.

TEL=/sys/bus/platform/devices/4080000.remoteproc:bam-dmux/rx_telemetry
NET=/sys/class/net/wwan0/statistics
OUT=/overlay/soak814.csv
LOG=/overlay/soak814.log

log() { echo "[$(cut -d' ' -f1 /proc/uptime)s] $*" >> "$LOG"; }

# ---------------------------------------------------------------------------
# TRAP: this device's busybox `ping` accepts only INTEGER `-i` values.
# `ping -c 40 -i 0.05 ...` fails instantly with "ping: invalid number '0.05'",
# and with the output redirected the failure is invisible: the generator stays
# alive, loops on its sleep, and transmits nothing.  That silently invalidated
# the first soak812 run and Doc 155's burst tunings.  Use a loop of `ping -c 1`,
# which needs no -i at all (measured: 20 packets in 1.24 s).
# ---------------------------------------------------------------------------
burst() {
	i=0
	while [ "$i" -lt "${BURST_N:-40}" ]; do
		ping -c 1 -W 1 -q 8.8.8.8 >/dev/null 2>&1
		i=$((i + 1))
	done
}

# Data-plane probe.  This is the direct test of the fix: after an SSR the
# interface and the default route must come back without a reboot.
#
# DO NOT PROBE ONCE, AND DO NOT PROBE EARLY.  An SSR is followed by
# ModemManager re-registration, a bearer reconnect, and netifd re-installing
# the address and the route.  That legitimately takes ~30 s: measured
# 2026-09-21, SSR at 912.7-914.8 s and the route back by ~960 s.  A single
# probe 8 s after the SSR therefore reported "DATA PLANE DOWN" for a perfectly
# healthy recovery -- the same class of measurement artefact as the
# `ping -i 0.05` bug of Doc 156.  Poll instead, and report the TIME TO
# RECOVERY; "down" means it never came back inside the window.
probe_dataplane() {
	why="$1"
	tries="${PROBE_TRIES:-12}"
	every="${PROBE_EVERY:-5}"
	i=0
	trace=""
	while [ "$i" -lt "$tries" ]; do
		route=$(ip route show default 2>/dev/null | head -1)
		if [ -n "$route" ] && ping -c 1 -W 3 -q 8.8.8.8 >/dev/null 2>&1; then
			log "DATA PLANE OK ($why): recovered after ~$((i * every))s; $route"
			return 0
		fi
		# Record WHY this attempt failed.  A bare "down" cannot distinguish
		# "no route yet" (netifd has not re-installed it) from "route present
		# but the modem is not passing packets" -- and run 6 produced exactly
		# that ambiguity for SSR #5 (see Doc 157 section 7.6).  One awk over
		# the telemetry, not four greps, to keep the iteration short.
		set -- $(awk -F: '/^(pc_state|pc_line_level|rx_slots_mapped|cmd_open):/ {
				      gsub(/ /, "", $2); printf "%s ", $2 }' "$TEL" 2>/dev/null)
		if [ -n "$route" ]; then r="route"; else r="NOroute"; fi
		trace="$trace $((i * every))s[$r pc=$1 line=$2 rxmap=$3 open=$4]"
		i=$((i + 1))
		sleep "$every"
	done
	log "DATA PLANE DOWN ($why): NOT recovered after $((tries * every))s"
	log "  trace:$trace"
	log "  wwan0: $(ip link show wwan0 2>/dev/null | head -1)"
	log "  telemetry: $(grep -E '^(pc_state|pc_line_level|pc_resync_count|rx_slots_mapped|cmd_open|rx_tearing_down):' "$TEL" | tr '\n' ' ')"
	return 1
}

(
	while true; do
		sleep 6
		burst
	done
) &
GEN=$!
log "started burst generator pid $GEN (6 s idle, then ${BURST_N:-40} x ping -c 1)"
log "=== soak814 start; oops=$(dmesg | grep -c 'Unable to handle') module=$(md5sum /lib/modules/6.12.94/qcom_bam_dmux.ko | cut -d' ' -f1) ==="

echo "uptime,oops,fatal,ssr,retries,rebuilds,pc_irq,pc_resync,pc_state,pc_line,rx_mapped,cmd_open,tx_pkts,rx_pkts,defer_q,defer_sub,defer_keep,defer_wipe_live,guard_hits" > "$OUT"

last_q=""
last_tx=""
last_tx_up=""
last_fatal=""
last_ssr=""
last_cmdopen=""

while true; do
	up=$(cut -d' ' -f1 /proc/uptime)
	oops=$(dmesg | grep -c 'Unable to handle')
	fatal=$(dmesg | grep -ci 'fatal error received')
	ssr=$(dmesg | grep -c 'stopped remote processor')
	retries=$(dmesg | grep -c 'SSR powerup: modem pc line not asserted, retry')
	rebuilds=$(dmesg | grep -c 'no channels, rebuilding')

	t=$TEL
	g() { grep "^$1:" "$t" 2>/dev/null | awk '{print $2}'; }
	pc_irq=$(g pc_irq_count)
	pc_resync=$(g pc_resync_count)
	pcst=$(g pc_state)
	pcline=$(g pc_line_level)
	rxm=$(g rx_slots_mapped)
	copen=$(g cmd_open)
	dq=$(g tx_defer_queued)
	ds=$(g tx_defer_submitted)
	dk=$(g tx_defer_preserved)
	dwl=$(g tx_defer_wiped_live)
	gh=$(g tx_sweep_guard_hits)
	tp=$(cat $NET/tx_packets 2>/dev/null)
	rp=$(cat $NET/rx_packets 2>/dev/null)

	echo "$up,$oops,$fatal,$ssr,$retries,$rebuilds,$pc_irq,$pc_resync,$pcst,$pcline,$rxm,$copen,$tp,$rp,$dq,$ds,$dk,$dwl,$gh" >> "$OUT"

	# LIVENESS: a dead burst generator produces the same observation as a
	# perfectly clean soak -- nothing happens.  Fail loudly instead.
	if [ -n "$last_tx" ] && [ "$tp" = "$last_tx" ]; then
		if [ -n "$last_tx_up" ]; then
			log "WARNING: no TX for $((up - last_tx_up))s (tx_pkts stuck at $tp) -- burst generator dead, or data plane down?"
			last_tx_up=""
		fi
	else
		last_tx_up=$up
	fi
	last_tx=$tp

	# patch-812 gate: a deferred packet never submitted is a lost packet.
	if [ -n "$last_q" ] && [ "$dq" -gt "$last_q" ] && [ "$ds" -lt "$dq" ]; then
		log "DEFERRED PACKET LOST: queued=$dq submitted=$ds gap=$((dq - ds)) at uptime ${up}s"
	fi
	last_q=$dq

	if [ "$oops" -gt 0 ]; then
		log "OOPS DETECTED at uptime ${up}s"
		dmesg | grep -A40 'Unable to handle' >> "$LOG"
	fi

	# --- THE TEST: an SSR happened.  Watch the recovery. ---
	if [ -n "$last_ssr" ] && [ "$ssr" -gt "$last_ssr" ] 2>/dev/null; then
		log "SSR #$ssr detected at sampler uptime ${up}s"
		dmesg | grep -E 'SSR (before shutdown|after powerup)|SSR powerup|channels not initialized|no channels, rebuilding' | tail -8 >> "$LOG"
		probe_dataplane "SSR #$ssr"
	fi
	last_ssr=$ssr

	# Independent of SSR counting: if the modem is awake but cmd_open has
	# stopped advancing and there is no default route, the driver is stuck.
	if [ -n "$last_cmdopen" ] && [ "$copen" = "$last_cmdopen" ] && [ "$pcst" = "1" ]; then
		if [ -z "$(ip route show default 2>/dev/null)" ]; then
			log "STUCK: pc_state=1 but cmd_open frozen at $copen and no default route (uptime ${up}s)"
			log "  rx_mapped=$rxm pc_line=$pcline pc_resync=$pc_resync tearing=$(g rx_tearing_down)"
		fi
	fi
	last_cmdopen=$copen

	if [ "$fatal" -gt "$last_fatal" ] 2>/dev/null; then
		line=$(dmesg | grep 'fatal error received' | tail -1)
		ft=$(echo "$line" | sed 's/^\[ *\([0-9.]*\)\].*/\1/')
		fs=$(echo "$line" | sed 's/.*fatal error received: //')
		# Take the last "is now up" line that is OLDER than the fatal.  Using
		# the last one unconditionally is wrong whenever a recovery has already
		# happened in this boot: it picks the NEW modem's start time and yields
		# a NEGATIVE modem uptime at the fatal (observed 2026-09-21).
		mup=$(dmesg | grep '4080000.remoteproc is now up' |
			sed 's/^\[ *\([0-9.]*\)\].*/\1/' |
			awk -v f="$ft" '$1 <= f {v=$1} END{print v}')
		log "FATAL #$fatal at AP ${ft}s (sampler saw it at ${up}s): $fs"
		if [ -n "$mup" ]; then
			log "  modem uptime at fatal = $(awk "BEGIN{printf \"%.6f\", $ft - $mup}")s (modem up since ${mup}s)"
		else
			log "  modem uptime at fatal = n/a (no 'is now up' before ${ft}s)"
		fi
	fi
	last_fatal=$fatal

	if [ "$fatal" -ge "${STOP_AT_FATALS:-8}" ]; then
		log "reached $fatal fatals; stopping soak"
		dmesg > /overlay/soak814_dmesg_final.txt
		break
	fi

	sleep 10
done

log "=== soak814 end; oops=$(dmesg | grep -c 'Unable to handle') fatal=$(dmesg | grep -ci 'fatal error received') retries=$retries rebuilds=$rebuilds ==="

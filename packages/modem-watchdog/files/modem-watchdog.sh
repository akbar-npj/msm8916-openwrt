#!/bin/sh
# /usr/sbin/modem-watchdog
# Qualcomm MSM8916 Modem Link Supervisor & Progressive Self-Healing Daemon

. /lib/functions.sh

TAG="modem-watchdog"
log() { logger -t "$TAG" "$*"; }

load_config() {
	config_load "modem-watchdog"
	config_get_bool ENABLED "main" "enabled" 1
	config_get CHECK_INTERVAL "main" "check_interval" 15
	config_get PING_HOSTS "main" "ping_hosts" "1.1.1.1 8.8.8.8"
	config_get PING_TIMEOUT "main" "ping_timeout" 3
	config_get MAX_FAILS "main" "max_fails" 3
	config_get MAX_STALLS "main" "max_stalls" 3
	config_get IFNAME "main" "ifname" "wwan0"
	config_get INTERFACE "main" "interface" "modem"
	config_get_bool AUTO_PM_ENFORCE "main" "auto_pm_enforce" 1
}

load_config

[ "$ENABLED" -eq 1 ] || {
	log "Disabled in /etc/config/modem-watchdog, exiting."
	exit 0
}

log "Started MSM8916 Hybrid Modem Supervisor (interval=${CHECK_INTERVAL}s, max_fails=${MAX_FAILS})"

# Initial startup grace period
sleep 30

enforce_runtime_pm() {
	if [ "$AUTO_PM_ENFORCE" -eq 1 ]; then
		for f in $(find /sys/devices/platform/soc@0/4080000.remoteproc/ -name "control" 2>/dev/null); do
			[ "$(cat "$f" 2>/dev/null)" = "on" ] || echo on > "$f" 2>/dev/null || true
		done
		for f in $(find /sys/devices/platform/soc@0/4080000.remoteproc/ -name "autosuspend_delay_ms" 2>/dev/null); do
			[ "$(cat "$f" 2>/dev/null)" = "-1" ] || echo -1 > "$f" 2>/dev/null || true
		done
	fi
}

check_connectivity() {
	for host in $PING_HOSTS; do
		if ping -c 1 -W "$PING_TIMEOUT" "$host" >/dev/null 2>&1; then
			return 0
		fi
	done
	return 1
}

FAIL_COUNT=0
STALL_COUNT=0
LAST_RX_BYTES=0

while true; do
	load_config
	[ "$ENABLED" -eq 1 ] || break

	enforce_runtime_pm

	# 1. Verify Hexagon Modem Remoteproc State
	RPROC_STATE=$(cat /sys/class/remoteproc/remoteproc0/state 2>/dev/null || echo "unknown")
	if [ "$RPROC_STATE" != "running" ]; then
		log "WARNING: Modem remoteproc state is '$RPROC_STATE' (not running). Bringing modem online..."
		/etc/init.d/rmtfs restart 2>/dev/null || true
		sleep 1
		echo start > /sys/class/remoteproc/remoteproc0/state 2>/dev/null || true
		sleep 10
		ifup "$INTERFACE" 2>/dev/null || true
		FAIL_COUNT=0
		STALL_COUNT=0
		sleep "$CHECK_INTERVAL"
		continue
	fi

	# 2. Check Cellular L3 Connectivity
	if check_connectivity; then
		if [ "$FAIL_COUNT" -gt 0 ]; then
			log "Cellular internet connectivity active and healthy."
		fi
		FAIL_COUNT=0
		STALL_COUNT=0
	else
		FAIL_COUNT=$((FAIL_COUNT + 1))
		log "Cellular connectivity check missed ($FAIL_COUNT/$MAX_FAILS)"

		if [ "$FAIL_COUNT" -ge "$MAX_FAILS" ]; then
			CUR_RX_BYTES=$(cat "/sys/class/net/$IFNAME/statistics/rx_bytes" 2>/dev/null || echo 0)
			
			if [ "$CUR_RX_BYTES" -eq "$LAST_RX_BYTES" ] && [ "$CUR_RX_BYTES" -gt 0 ]; then
				STALL_COUNT=$((STALL_COUNT + 1))
				log "Detected 0 RX data stall on $IFNAME (rx_bytes=$CUR_RX_BYTES, stall_count=$STALL_COUNT/$MAX_STALLS)"
			else
				STALL_COUNT=$((STALL_COUNT + 1))
			fi
			LAST_RX_BYTES="$CUR_RX_BYTES"

			if [ "$STALL_COUNT" -lt "$MAX_STALLS" ]; then
				# STAGE 1: Soft Bearer Cycle
				log "Stage 1 Recovery: Cycling cellular bearer interface '$INTERFACE'..."
				ifdown "$INTERFACE" 2>/dev/null || true
				sleep 3
				ifup "$INTERFACE" 2>/dev/null || true
				FAIL_COUNT=0
				sleep 20
			else
				# STAGE 2: Subsystem Remoteproc Clean Restart
				log "Stage 2 Recovery: Performing clean modem remoteproc reset..."
				ifdown "$INTERFACE" 2>/dev/null || true
				sleep 1
				echo stop > /sys/class/remoteproc/remoteproc0/state 2>/dev/null || true
				sleep 2
				/etc/init.d/rmtfs restart 2>/dev/null || true
				sleep 1
				echo start > /sys/class/remoteproc/remoteproc0/state 2>/dev/null || true
				sleep 6
				ifup "$INTERFACE" 2>/dev/null || true
				FAIL_COUNT=0
				STALL_COUNT=0
				sleep 30
			fi
		fi
	fi

	sleep "$CHECK_INTERVAL"
done

#!/bin/bash
# host_hang_watch.sh - host-side liveness watcher for an HMU05 AP hang.
#
# WHY IT MUST BE ON THE HOST
#   The AP-side soak harness dies with the AP.  A hang (Doc 159) leaves the AP
#   unresponsive until the hardware watchdog resets it, so the only record of
#   WHEN it happened, and for how long, has to come from off-box.
#
# WHAT IT ESTABLISHES
#   * the hang's start (last successful ping) and its duration (until the AP
#     answers again after the watchdog reset), to the second;
#   * that the AP really was unreachable, rather than the harness having died
#     while the AP stayed up.
#
# WHAT IT DOES NOT DO
#   It cannot capture the console.  The AP's management path is `usb0`, a
#   configfs USB *gadget* (no netpoll), so netconsole is impossible on this
#   device, and `CONFIG_NETCONSOLE` is off anyway.  The in-guest sinks
#   (/dev/kmsg -> /overlay + /dev/pmsg0) are the capture; this is the clock.
#
# usage: host_hang_watch.sh [logfile] [interval_s]
#        default: scratch/q6trace/host_hang_watch.log, 5 s

LOG=${1:-scratch/q6trace/host_hang_watch.log}
INTERVAL=${2:-5}
TARGET=192.168.8.1

mkdir -p "$(dirname "$LOG")"
echo "# host_hang_watch start $(date -Is) target=$TARGET interval=${INTERVAL}s" >> "$LOG"

fail=0
hang_started=""
last_ok=$(date +%s)

while true; do
	ts=$(date -Is)
	if ping -c 1 -W 3 -q "$TARGET" >/dev/null 2>&1; then
		if [ -n "$hang_started" ]; then
			now=$(date +%s)
			echo "$ts AP-BACK after $((now - hang_started))s unreachable (last ok $(date -Is -d @$last_ok))" >> "$LOG"
			hang_started=""
		fi
		fail=0
		last_ok=$(date +%s)
		# Only log a heartbeat every ~60 s so the file stays small.
		if [ $(( $(date +%s) % 60 )) -lt "$INTERVAL" ]; then
			echo "$ts ok (uptime-check: $(timeout 8 ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=5 root@$TARGET 'cut -d. -f1 /proc/uptime' 2>/dev/null || echo '?'))" >> "$LOG"
		fi
	else
		fail=$((fail + 1))
		if [ "$fail" -eq 3 ] && [ -z "$hang_started" ]; then
			hang_started=$(date +%s)
			echo "$ts *** AP UNREACHABLE *** (3 consecutive failures; last ok $(date -Is -d @$last_ok))" >> "$LOG"
		fi
		if [ $((fail % 12)) -eq 0 ]; then
			echo "$ts still unreachable (${fail} failures)" >> "$LOG"
		fi
	fi
	sleep "$INTERVAL"
done

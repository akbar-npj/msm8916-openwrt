#!/bin/bash
# dev_state_mon.sh - host-side sampler of the DEVICE'S OWN state, for the Doc 170 §8.10
# follow-up: the AP reset that survived patch 823.
#
# WHY THIS EXISTS
#   host_hang_watch.sh is the CLOCK (it pings and timestamps the unreachable window),
#   but it records nothing about what the device was doing.  The reset at 17:03 left
#   pstore empty, no fourth coredump and no ledger row -- so the only thing that could
#   have said WHEN the AP stopped advancing was the beacon, which at the time truncated
#   its own log at every boot (now fixed).  This sampler keeps a HOST-SIDE copy of the
#   device's state every INTERVAL seconds, so that even if the AP hangs and its own
#   /overlay write path wedges, the host still holds the last-known-good readings.
#
#   It deliberately pulls the two beacons (A syncs, B never syncs) and the last dmesg
#   line: after a hang, "A stopped but B advanced" => the FS/writeback path wedged,
#   "both stopped together" => a global stall.
#
# usage: dev_state_mon.sh [logfile] [interval_s]     default: scratch/dev_state_mon.log 20

LOG=${1:-scratch/dev_state_mon.log}
INTERVAL=${2:-20}
TARGET=192.168.8.1

mkdir -p "$(dirname "$LOG")"
echo "# dev_state_mon start $(date -Is) target=$TARGET interval=${INTERVAL}s" >> "$LOG"

while true; do
	ts=$(date -Is)
	out=$(timeout 15 ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=8 \
		root@$TARGET '
		up=$(cut -d" " -f1 /proc/uptime)
		rp=$(cat /sys/class/remoteproc/remoteproc0/state 2>/dev/null)
		a=$(tail -1 /overlay/beacon_a.txt 2>/dev/null)
		b=$(tail -1 /overlay/beacon_b.txt 2>/dev/null)
		nc=$(dmesg 2>/dev/null | grep -c "fatal error received")
		led=$(tail -1 /overlay/ssr_ledger.csv 2>/dev/null)
		dl=$(dmesg 2>/dev/null | tail -1 | cut -c1-140)
		printf "up=%s rproc=%s fatals=%s\n  A=%s\n  B=%s\n  led=%s\n  last_dmesg=%s\n" \
			"$up" "$rp" "$nc" "$a" "$b" "$led" "$dl"' 2>/dev/null)
	if [ -n "$out" ]; then
		printf '%s\n%s\n' "$ts OK" "$out" >> "$LOG"
	else
		echo "$ts *** UNREACHABLE (ssh failed) ***" >> "$LOG"
	fi
	sleep "$INTERVAL"
done

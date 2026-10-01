#!/bin/sh
# 227 — watch for the 902.8 s modem fatal on stock HMU05 firmware.
# Host-side poller: records AP uptime, opmode, remoteproc state, and any
# "modem subsystem failure reason" line, with a wall-clock stamp.
#
# Usage: sh watch_fatal.sh [duration_s] [interval_s]
# Output: appends to fatal_watch.log in the CWD.

DUR=${1:-2400}
INT=${2:-15}
DEV=root@192.168.8.1
SSH="ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=8"

end=$(( $(date +%s) + DUR ))
prev_reason=""

while [ "$(date +%s)" -lt "$end" ]; do
	now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
	out=$($SSH $DEV '
		u=$(cut -d. -f1 /proc/uptime)
		b=$(cat /proc/sys/kernel/random/boot_id)
		r=$(cat /sys/class/remoteproc/remoteproc0/state 2>/dev/null)
		m=$(qmicli -d /dev/wwan0qmi0 --dms-get-operating-mode 2>/dev/null | sed -n "s/.*Mode: .\(.*\)./\1/p")
		f=$(dmesg | grep -m1 "modem subsystem failure reason")
		c=$(dmesg | grep -c "crash detected in 4080000.remoteproc")
		printf "u=%s|b=%s|rproc=%s|mode=%s|crashes=%s|reason=%s\n" "$u" "$b" "$r" "$m" "$c" "$f"
	' 2>/dev/null)

	if [ -n "$out" ]; then
		echo "$now $out" >> fatal_watch.log
		# announce a NEW fatal reason immediately
		reason=$(echo "$out" | sed -n 's/.*|reason=//p')
		if [ -n "$reason" ] && [ "$reason" != "$prev_reason" ]; then
			echo "*** FATAL SEEN $now :: $reason" >> fatal_watch.log
			prev_reason="$reason"
		fi
	fi
	sleep "$INT"
done
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) watch ended after ${DUR}s" >> fatal_watch.log

#!/bin/sh
# soak_start.sh -- start/stop/status for the two bam-dmux soak instruments.
#
#   pcfine.sh  -> /overlay/pcfine_<boot_id>.txt   bam-dmux rx_telemetry sampler
#   logroll.sh -> /overlay/logroll_<boot_id>.log  durable userspace log + PM knobs
#
# Output is named by boot_id ON PURPOSE.  A ~900 s fatal can hang the AP and the
# 30 s PON watchdog resets it; a plain filename would be truncated seconds after
# the next boot, destroying the pre-hang tail the capture exists to keep.
#
# This is NOT wired into rc.local.  The measurement window is opened by running
# `soak_start.sh start`, not by rebooting -- Doc 185 requires the timing soak to
# run at ModemManager INFO, and a stray reboot is not a measurement.  To survive
# a watchdog reset DURING a long soak, add the same guarded block to rc.local.
#
# NOTE: busybox `start-stop-daemon -S` is NOT idempotent for a script (its -x
# match cannot see a cmdline of "/bin/sh /overlay/..."), so every start is
# guarded on the pidfile first.
#
# usage: soak_start.sh {start|stop|status}

BID=$(cut -c1-8 /proc/sys/kernel/random/boot_id)
SAMPLER=/overlay/pcfine.sh
LOGROLL=/overlay/logroll.sh
PC_PID=/var/run/pcfine.pid
LR_PID=/var/run/logroll.pid
PC_OUT=/overlay/pcfine_$BID.txt
LR_OUT=/overlay/logroll_$BID.log

alive() { [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null; }

start_one() { # <pidfile> <script> [args...]
	pidf=$1; script=$2; shift 2
	if alive "$pidf"; then
		echo "already running: $script (pid $(cat "$pidf"))"
		return 0
	fi
	/sbin/start-stop-daemon -S -b -m -p "$pidf" -x "$script" -- "$@"
	sleep 1
	if alive "$pidf"; then
		echo "started: $script (pid $(cat "$pidf"))"
	else
		echo "FAILED to start: $script" >&2
		return 1
	fi
}

stop_one() { # <pidfile>
	pidf=$1
	if alive "$pidf"; then
		pid=$(cat "$pidf")
		kill "$pid" 2>/dev/null
		sleep 1
		alive "$pidf" && kill -9 "$pid" 2>/dev/null
		echo "stopped: pid $pid"
	else
		echo "not running: $pidf"
	fi
	rm -f "$pidf"
}

# logroll.sh runs `logread -f | grep | while read`, so killing the logroll.sh
# shell orphans the pipeline's head.  Reap it by cmdline.  Safe here because
# this runs as a script, so our own cmdline is "/bin/sh /overlay/soak_start.sh"
# and cannot match -- but exclude our pid anyway.
reap_logread() {
	me=$$
	for p in /proc/[0-9]*; do
		[ -r "$p/cmdline" ] || continue
		pid=${p#/proc/}
		[ "$pid" = "$me" ] && continue
		case "$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null)" in
			*"logread -f"*) kill -9 "$pid" 2>/dev/null && echo "reaped logread pid $pid";;
		esac
	done
}

case "$1" in
start)
	echo "boot_id=$BID"
	start_one "$PC_PID" "$SAMPLER" "$PC_OUT"
	start_one "$LR_PID" "$LOGROLL"
	echo "sampler -> $PC_OUT"
	echo "logroll -> $LR_OUT"
	;;
stop)
	stop_one "$PC_PID"
	stop_one "$LR_PID"
	reap_logread
	;;
status)
	echo "boot_id=$BID"
	if alive "$PC_PID"; then
		echo "sampler RUNNING pid $(cat "$PC_PID") -> $PC_OUT"
		[ -f "$PC_OUT" ] && wc -l < "$PC_OUT" | sed 's/^/  records: /'
	else
		echo "sampler not running"
	fi
	if alive "$LR_PID"; then
		echo "logroll RUNNING pid $(cat "$LR_PID") -> $LR_OUT"
		[ -f "$LR_OUT" ] && wc -l < "$LR_OUT" | sed 's/^/  lines: /'
	else
		echo "logroll not running"
	fi
	;;
*)
	echo "usage: $0 {start|stop|status}" >&2
	exit 2
	;;
esac

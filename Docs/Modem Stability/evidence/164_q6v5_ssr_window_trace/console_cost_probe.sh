#!/bin/sh
# console_cost_probe.sh - measure what one printk line costs on the HMU05.
#
# WHY
#   Patch 817 puts 23 dev_info() lines inside the q6v5 SSR window that Doc 159
#   bounded to 43-47 ms.  Before trusting it as an instrument, we need to know
#   what the instrument itself costs.  The console is a 115200-baud serial line,
#   so the hypothesis is that the cost is pure UART serialisation and therefore
#   proportional to line length.
#
# METHOD
#   Writing to /dev/kmsg runs the full printk path (ring buffer + console
#   emission) from a process context, which is the same path dev_info() takes.
#   A `<N>` prefix on the written text selects the printk level, so the same
#   script measures both a message that IS printed and one that is SUPPRESSED.
#
#   /proc/uptime has 10 ms resolution, so N must be large: N=300 gives ~0.03 ms
#   of quantisation per line, well below the effect being measured.  A control
#   loop with no write runs first, to subtract shell overhead.
#
# DEVICE QUIRKS
#   * busybox `sleep` is integer-only; this script uses no fractional sleeps.
#   * there is no `stty` on this image, so the baud rate cannot be changed --
#     suppressing the console (console_loglevel) is the only lever available.
#
# USAGE
#   sh console_cost_probe.sh            # measures at the current loglevel
#   echo 6 > /proc/sys/kernel/printk    # ...then re-run to see the difference
#
# RESULTS (2026-09-21, HMU05, Linux 6.12.94) are in console_cost_measurement.txt.

N=${N:-300}
LONG="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
SHORT="SHORT"

# measure <label> <text-to-write>   -- writes "$text" N times, prints ms/line
measure() {
	label="$1"
	text="$2"
	t0=$(cut -d' ' -f1 /proc/uptime)
	i=0
	while [ "$i" -lt "$N" ]; do
		echo "$text" > /dev/kmsg
		i=$((i + 1))
	done
	t1=$(cut -d' ' -f1 /proc/uptime)
	awk -v l="$label" -v a="$t0" -v b="$t1" -v n="$N" \
		'BEGIN { printf "%-40s %8.3f s   %6.2f ms/line\n", l, b - a, (b - a) * 1000 / n }'
}

echo "printk = $(cat /proc/sys/kernel/printk)   (console_loglevel default_message min default)"
echo "N = $N writes per measurement"
echo

# control: identical loop, no write -- subtracts busybox shell overhead
t0=$(cut -d' ' -f1 /proc/uptime)
i=0
while [ "$i" -lt "$N" ]; do
	:
	i=$((i + 1))
done
t1=$(cut -d' ' -f1 /proc/uptime)
awk -v a="$t0" -v b="$t1" -v n="$N" \
	'BEGIN { printf "%-40s %8.3f s   %6.2f ms/line\n", "control: loop only, no write", b - a, (b - a) * 1000 / n }'

measure "KERN_WARNING(4), ~10 ch, printed"    "<4>$SHORT"
measure "KERN_WARNING(4), ~120 ch, printed"   "<4>$LONG"
measure "KERN_INFO(6), ~120 ch, suppressed"   "<6>$LONG"
measure "KERN_INFO(6), ~10 ch, suppressed"    "<6>$SHORT"

echo
echo "--- proof that a suppressed line still reaches the ring buffer ---"
echo "<6>PROBE_INFO suppressed-must-be-in-ring" > /dev/kmsg
echo "<4>PROBE_WARN printed-must-be-in-ring" > /dev/kmsg
sleep 1
echo "dmesg | grep -c PROBE_INFO = $(dmesg | grep -c PROBE_INFO)   (must be 1)"
echo "dmesg | grep -c PROBE_WARN = $(dmesg | grep -c PROBE_WARN)   (must be 1)"
dmesg | grep PROBE_

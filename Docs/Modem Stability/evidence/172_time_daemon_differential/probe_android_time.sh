#!/bin/sh
# Doc 172 -- Android-side time-daemon probes.
#
# Run against the live HMU05 while it is booted into stock Android:
#     sh probe_android_time.sh
#
# The output quoted in Docs/Modem Stability/172_*.md came from this script.
# NOTE: the Android shell is uid 2000; `adb root` SILENTLY DOES NOTHING.
#       Read-only probes below, so uid 2000 is sufficient.

echo "=== 1. is there a time daemon, and who owns the modem QMI link? ==="
ps | grep -iE 'time_daemon|timeservice'
echo '--- diag kernel threads ---'
ps | grep -i diag

echo
echo "=== 2. what does the daemon PERSIST across reboots? ==="
ls -la /data/time/
for f in /data/time/ats_1 /data/time/ats_2 /data/time/ats_13; do
	printf '%s: ' "$f"
	od -An -tu8 "$f" 2>/dev/null || echo '(od unavailable)'
done

echo
echo "=== 3. does the AP RTC track wall time? ==="
a=$(cat /sys/class/rtc/rtc0/since_epoch)
u=$(cut -d. -f1 /proc/uptime)
d=$(date +%s)
echo "rtc=$a  uptime=$u  system_clock=$d"
echo "rtc name: $(cat /sys/class/rtc/rtc0/name)"
echo "rtc as a date: $(date -d @$a 2>/dev/null || echo 'n/a')"
sleep 10
a2=$(cat /sys/class/rtc/rtc0/since_epoch)
u2=$(cut -d. -f1 /proc/uptime)
echo "after 10 s: rtc_delta=$((a2-a))  uptime_delta=$((u2-u))"

echo
echo "=== 4. is the modem time service driven from the AP wall clock? ==="
logcat -d -v time | grep -iE 'TimeService|nitz' | tail -6

echo
echo "=== 5. what is INSIDE the daemon (architecture strings) ==="
echo '--- local socket, persistence path, clock setting ---'
strings -a /system/bin/time_daemon 2>/dev/null \
	| grep -E '^#|^/data|settimeofday|genoff|ats_|RTC|MODEM' | head -30

echo
echo "=== 6. WHAT is the persisted offset? (sample 3x, 4 s apart) ==="
echo '    hypothesis: ats_1 = (AP system time - AP RTC) * 1000'
echo '    NOTE: this device shell does 32-BIT arithmetic, and ats_1 ~ 1.79e12'
echo '          overflows it. Use awk (doubles) for the subtraction.'
i=1
while [ $i -le 3 ]; do
	r=$(cat /sys/class/rtc/rtc0/since_epoch)
	d=$(date +%s)
	a=$(od -An -tu8 /data/time/ats_1 | tr -d ' ')
	echo "$r $d $a" | awk '{
		printf "rtc=%s sys=%s ats_1_ms=%s | sys_minus_rtc_ms=%.0f | residual_ms=%.0f\n",
			$1, $2, $3, ($2-$1)*1000, $3 - ($2-$1)*1000 }'
	i=$((i+1))
	sleep 4
done
echo '    If the residual is CONSTANT while sys and rtc advance in lockstep, the'
echo '    hypothesis holds and the residual is a ONE-TIME skew from the instant the'
echo '    file was last written -- not a growing error.'


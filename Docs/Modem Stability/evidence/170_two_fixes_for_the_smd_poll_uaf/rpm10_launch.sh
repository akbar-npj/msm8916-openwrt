#!/bin/sh
# rpm10 -- continue the RPM external-log capture past the previous run's sample
# limit.  The previous run (/overlay/rpm9.txt, -n 500000) exited CLEANLY at
# 660 382 records / AP ~8600 s, so the capture was NOT running when Doc 170
# section 8.20/8.21 said it was -- see PART 14.
#
# The question it is kept running for is now a DIFFERENT one: the stall itself
# is ANSWERED NEGATIVE as the fatal's mechanism (section 8.20), so what remains
# is whether the stall's RATE changes across a cascade (section 8.21 / PART 13).
#
# NOTE: -n counts POLLS, not records (samples=500000 in the header, ~4.5 ms per
# poll), so 2 000 000 polls is ~2.5 h of wall time and ~200 MB of output.
# /overlay had 1.4 G free when this was written.
OUT=/overlay/rpm10.txt
exec > "$OUT" 2>&1
echo "RPM10 START uptime=$(cut -d' ' -f1 /proc/uptime) fatals=$(dmesg | grep -c 'fatal error received')"
exec /overlay/rpmring -t -n 2000000 -i 4

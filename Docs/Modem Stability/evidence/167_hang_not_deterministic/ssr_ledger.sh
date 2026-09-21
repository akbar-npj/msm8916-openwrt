#!/bin/sh
# ssr_ledger.sh - one persistent line per fatal/SSR, for the n>=60 protocol (Doc 167 §7).
#
# WHY THIS EXISTS
#   Doc 167 measured the AP hang at ~1 in 20-40 SSRs (3-5%), so validating patch 820
#   needs n >= 60 recovered SSRs (~18 h).  Two things make that impossible with the
#   existing instruments alone:
#     * dmesg is lost on every reboot, and an 18 h soak will span reboots;
#     * "did the teardown work complete?" is answered by the T0-T4 probes, which are
#       dev_err and therefore only in dmesg.
#   So the per-SSR facts are copied to a file on /overlay as they happen.
#
# WHAT ONE LINE MEANS (CSV; header written once)
#   uptime        AP uptime (s) when the new fatal was noticed
#   n             cumulative "fatal error received" count in THIS boot
#   sig           the fatal's own file:line, from dmesg
#   t0..t4        cumulative counts of each "SSR teardown Tn" line in THIS boot
#   exec          cumulative "executing serialized asynchronous SSR teardown"
#   wwan          cumulative "port wwan0at0 disconnected"
#   coredumps     files currently in /overlay/coredump_live
#
#   The counts are CUMULATIVE, so the per-SSR values are the deltas between consecutive
#   lines.  Each recovered SSR should add exactly +1 to t0..t4 and to exec, and the
#   signature column says which fatal it was.
#
# HOW A HANG SHOWS UP
#   A hang produces NO line for that fatal (the box stops), and no coredump.  So:
#     * a line whose deltas are short (e.g. t0 +1 but t1..t4 +0) => the teardown work
#       did not complete, but the AP survived;
#     * a GAP in `n` between two lines, or a boot whose last fatal has no matching
#       coredump, => the AP hung there.
#   Cross-check against /overlay/coredump_watch.log, which is the authoritative
#   recovered-vs-hung record (it is silent for a hung fatal).
#
# DEVICE QUIRKS HONOURED
#   * `grep -c PATTERN /dev/kmsg` NEVER terminates -- use `dmesg | grep -c`.
#   * busybox `sleep` takes INTEGERS only.
#   * there is no `timeout` on this image.
#
# usage: ssr_ledger.sh [outdir]      (default /overlay)

OUTDIR=${1:-/overlay}
LEDGER=$OUTDIR/ssr_ledger.csv
CDDIR=$OUTDIR/coredump_live

[ -f "$LEDGER" ] || echo "uptime,n,sig,t0,t1,t2,t3,t4,exec,wwan,coredumps" > "$LEDGER"

cnt() { dmesg | grep -c "$1" 2>/dev/null; }

last=0
while true; do
	n=$(cnt "fatal error received")
	[ -n "$n" ] || n=0

	if [ "$n" -gt "$last" ]; then
		sig=$(dmesg | grep "fatal error received" | tail -1 \
			| sed -n 's/.*fatal error received: *\([^:]*:[0-9]*\).*/\1/p')
		[ -n "$sig" ] || sig="?"
		up=$(cut -d' ' -f1 /proc/uptime)
		cds=$(ls "$CDDIR" 2>/dev/null | wc -l)

		echo "$up,$n,$sig,$(cnt 'SSR teardown T0'),$(cnt 'SSR teardown T1'),$(cnt 'SSR teardown T2'),$(cnt 'SSR teardown T3'),$(cnt 'SSR teardown T4'),$(cnt 'executing serialized asynchronous SSR teardown'),$(cnt 'port wwan0at0 disconnected'),$cds" \
			>> "$LEDGER"
		sync
		last=$n
	fi

	sleep 10
done

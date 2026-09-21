#!/bin/sh
# ssr_ledger.sh - one persistent line per fatal/SSR, for the n>=60 protocol (Doc 167 §7).
#
# WHY THIS EXISTS
#   Doc 167 measured the AP hang at ~1 in 20-40 SSRs (3-5%), so validating patch 820
#   needs n >= 60 recovered SSRs (~18 h).  Two things make that impossible with the
#   existing instruments alone:
#     * dmesg is lost on every reboot, and an 18 h soak will span reboots;
#     * "did the teardown work complete?" is answered by the T0-T9 probes, which are
#       dev_err and therefore only in dmesg.
#   So the per-SSR facts are copied to a file on /overlay as they happen.
#
# WHAT ONE LINE MEANS (CSV; header written once)
#   uptime        AP uptime (s) when the new fatal was noticed
#   n             cumulative "fatal error received" count in THIS boot
#   sig           the fatal's own file:line, from dmesg
#   t0..t4        cumulative counts of each "SSR teardown Tn" line in THIS boot
#   t5..t9        cumulative counts of the teardown's LATER stages (Doc 169 §6):
#                   t5 "T5 rx released"          t6 "T6 tx released"
#                   t7 "T7 power_off returned"   t8 "T8 work done"
#                   t9 "T9 flush returned"       (T9 exists only with patch 821)
#                 These are the columns that answer "did bam_dmux_power_off() run
#                 the FULL blocking teardown, or take the idempotent early return
#                 at qcom_bam_dmux.c:1568 (rx_tearing_down && !rx && !tx)?".
#                 A line with t0..t4 incremented but t5..t7 NOT incremented is the
#                 signature of the early return.
#   exec          cumulative "executing serialized asynchronous SSR teardown"
#   wwan          cumulative "port wwan0at0 disconnected"
#   pc_state      bam_dmux rx_telemetry pc_state at the moment the fatal was seen
#   rx_tearing_down  rx_telemetry rx_tearing_down at the same moment
#   coredumps     files currently in /overlay/coredump_live
#
#   The counts are CUMULATIVE, so the per-SSR values are the deltas between consecutive
#   lines.  Each recovered SSR should add exactly +1 to t0..t8 and to exec, and the
#   signature column says which fatal it was.
#
#   HISTORY: rows written before 2026-09-21 15:30 have "?" in t5..t9/pc_state/
#   rx_tearing_down -- those columns did not exist yet.  They are NOT zeros.
#
# HOW A HANG SHOWS UP
#   A hang produces NO line for that fatal (the box stops), and no coredump.  So:
#     * a line whose deltas are short (e.g. t0 +1 but t1..t8 +0) => the teardown work
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
TEL=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry

HDR="uptime,n,sig,t0,t1,t2,t3,t4,t5,t6,t7,t8,t9,exec,wwan,pc_state,rx_tearing_down,coredumps"
[ -f "$LEDGER" ] || echo "$HDR" > "$LEDGER"

cnt() { dmesg | grep -c "$1" 2>/dev/null; }

# A single telemetry field, or "?" if the attribute is missing.
tel() { sed -n "s/^$1: *\(.*\)/\1/p" "$TEL" 2>/dev/null | head -1; }

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
		pcs=$(tel pc_state); [ -n "$pcs" ] || pcs="?"
		rtd=$(tel rx_tearing_down); [ -n "$rtd" ] || rtd="?"

		echo "$up,$n,$sig,$(cnt 'SSR teardown T0'),$(cnt 'SSR teardown T1'),$(cnt 'SSR teardown T2'),$(cnt 'SSR teardown T3'),$(cnt 'SSR teardown T4'),$(cnt 'bam-dmux T5'),$(cnt 'bam-dmux T6'),$(cnt 'bam-dmux T7'),$(cnt 'bam-dmux T8'),$(cnt 'bam-dmux T9'),$(cnt 'executing serialized asynchronous SSR teardown'),$(cnt 'port wwan0at0 disconnected'),$pcs,$rtd,$cds" \
			>> "$LEDGER"
		sync
		last=$n
	fi

	sleep 10
done

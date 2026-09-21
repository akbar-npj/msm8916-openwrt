#!/bin/bash
# Doc 174 -- STATIONARY Android baseline sampler.
#
# Records, on one aligned timeline:
#   * /proc/uptime                      (AP uptime, the only trustworthy clock)
#   * /d/rpm_master_stats numshutdowns  (the MODEM's own power-collapse counter, MPSS)
#   * the MPSS RPM tick fields          (shutdown_req / wakeup_ind, 19.2 MHz)
#   * the radio context                 (RSRP, cell, data state) -- the stationarity evidence
#
# WHY: the 2026-09-22 Android run was on a MOVING BUS, which makes every
# network-sensitive metric a bus baseline.  With the platform stationary, the
# MPSS collapse rate becomes comparable to what OpenWrt will measure once the
# Doc 171 enabler (patch 824 + rpm-master-stats module) is flashed.
#
# Usage: stationary_baseline.sh <outfile> [duration_s] [interval_s]
#   defaults: duration 1800 s, interval 10 s

set -u

OUT=${1:?usage: $0 <outfile> [duration_s] [interval_s]}
DUR=${2:-1800}
INT=${3:-10}

ADB=${ADB:-adb}

echo "# stationary Android baseline -- Doc 174" > "$OUT"
echo "# started $(date -Is)" >> "$OUT"
echo "# duration=${DUR}s interval=${INT}s" >> "$OUT"
echo "# device: $($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r') / Android $($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')" >> "$OUT"
echo "#" >> "$OUT"
echo "wall,uptime_s,apss_n,mpss_n,pronto_n,mpss_shutdown_req,mpss_wakeup_ind,signalstrength_raw,servicestate_raw,dataconn_raw" >> "$OUT"

t0=$(date +%s)
while :; do
	now=$(date +%s)
	[ $((now - t0)) -ge "$DUR" ] && break

	# One adb round trip per sample; the device side only cats and greps.
	sample=$($ADB shell '
		u=$(cut -d" " -f1 /proc/uptime)
		s=$(cat /d/rpm_master_stats 2>/dev/null)
		# split the three master blocks and pull numshutdowns / tick fields
		apss=$(echo "$s" | sed -n "/^APSS/,/^MPSS/p"    | grep -E "numshutdowns" | cut -d: -f2)
		mpss=$(echo "$s" | sed -n "/^MPSS/,/^PRONTO/p"  | grep -E "numshutdowns" | cut -d: -f2)
		prnt=$(echo "$s" | sed -n "/^PRONTO/,\$p"       | grep -E "numshutdowns" | cut -d: -f2)
		sreq=$(echo "$s" | sed -n "/^MPSS/,/^PRONTO/p"  | grep -E "shutdown_req" | cut -d: -f2)
		wind=$(echo "$s" | sed -n "/^MPSS/,/^PRONTO/p"  | grep -E "wakeup_ind"   | cut -d: -f2)
		tel=$(dumpsys telephony.registry 2>/dev/null)
		# Record the RAW telephony lines rather than guessing field indices --
		# the dumpsys format differs per Android version, and a mis-parsed
		# field is a silent wrong number.  Spaces -> "_", commas dropped.
		ss=$(echo "$tel" | grep -m1 mSignalStrength | tr ' ' '_' | tr -d ',')
		sv=$(echo "$tel" | grep -m1 mServiceState   | tr ' ' '_' | tr -d ',')
		dc=$(echo "$tel" | grep -m1 mDataConnectionState | tr ' ' '_' | tr -d ',')
		echo "$u|$apss|$mpss|$prnt|$sreq|$wind|$ss|$sv|$dc"
	' 2>/dev/null | tr -d '\r')

	if [ -n "$sample" ]; then
		IFS='|' read -r u a m p sr wi ss sv dc <<EOF
$sample
EOF
		printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
			"$(date -Is)" "$u" "$a" "$m" "$p" "$sr" "$wi" "$ss" "$sv" "$dc" >> "$OUT"
	else
		printf '%s,,,,,,,,,,\n' "$(date -Is)" >> "$OUT"
	fi

	sleep "$INT"
done

echo "# finished $(date -Is)" >> "$OUT"

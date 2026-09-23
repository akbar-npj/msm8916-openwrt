#!/bin/sh
# monitor_soak.sh -- poll the P-W (Part A) and P-MM (Part B) soaks, pull the
# artifacts, and report n for BOTH.  Exits when BOTH parts have reached their
# pre-registered n.
#
# Usage: monitor_soak.sh [max_iterations] [sleep_seconds]
#   default 60 x 300 s = 5 h
#
# Exit condition (both must hold):
#   Part A: >= 500 resumes AND >= 20 STALE resumes      (P-W1, P-W2)
#   Part B: >= 11 SSRs                                  (P-MM2, the safety control)
#
# POOLING: every /overlay/pcfine_*.txt is pulled and scored together, because a
# mid-soak watchdog reset splits the capture across per-boot_id files.  The
# scorer sums the per-capture class tallies; it never concatenates the files.
#
# The device-side instruments are autonomous and pidfile-guarded, and rc.local
# re-arms them after a mid-soak watchdog reset, so this host-side poller is
# CONVENIENCE ONLY -- killing it does not stop the measurement.

DEV=root@192.168.8.1
EV="$(cd "$(dirname "$0")" && pwd)"
# The evidence dir path contains a SPACE ("Modem Stability"), so captures are
# staged in a space-free dir and passed to python unquoted-and-safe.
CAPDIR="${TMPDIR:-/tmp}/soak_p825_caps"
mkdir -p "$CAPDIR"
MAX="${1:-60}"
SLEEP="${2:-300}"
PROG="$EV/monitor_progress.log"
MM=../112_bam_reinit_ab/score_mm_phases.py

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$PROG"; }

log "monitor start: max=$MAX sleep=${SLEEP}s dev=$DEV"

i=0
while [ "$i" -lt "$MAX" ]; do
	i=$((i + 1))

	INFO=$(ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no "$DEV" '
		BID=$(cut -c1-8 /proc/sys/kernel/random/boot_id)
		UP=$(cut -d" " -f1 /proc/uptime)
		F=$(dmesg | grep -c "fatal error received")
		R=$(/overlay/soak_start.sh status 2>/dev/null | grep -c RUNNING)
		PF=$(ls /overlay/pcfine_*.txt 2>/dev/null | tr "\n" ",")
		LR=$(ls /overlay/logroll_*.log 2>/dev/null | tr "\n" ",")
		echo "$BID $UP $F $R $PF $LR"
	' 2>/dev/null)

	if [ -z "$INFO" ]; then
		log "iter $i: DEVICE UNREACHABLE (rebooting or hung)"
		sleep "$SLEEP"
		continue
	fi

	BID=$(echo "$INFO" | cut -d' ' -f1)
	UP=$(echo "$INFO" | cut -d' ' -f2)
	F=$(echo "$INFO" | cut -d' ' -f3)
	R=$(echo "$INFO" | cut -d' ' -f4)
	PFILES=$(echo "$INFO" | cut -d' ' -f5 | tr ',' ' ')
	LFILES=$(echo "$INFO" | cut -d' ' -f6 | tr ',' ' ')

	# ---- Part A: pull + pool every pcfine capture -------------------------
	PULLED=""
	NPULL=0
	for f in $PFILES; do
		b=$(basename "$f")
		if scp -q -o ConnectTimeout=10 "$DEV:$f" "$CAPDIR/$b" 2>/dev/null; then
			if [ -s "$CAPDIR/$b" ]; then
				PULLED="$PULLED $CAPDIR/$b"
				NPULL=$((NPULL + 1))
			fi
		fi
	done

	# ---- Part B: pull every logroll --------------------------------------
	LPULLED=""
	NL=0
	for f in $LFILES; do
		b=$(basename "$f")
		if scp -q -o ConnectTimeout=10 "$DEV:$f" "$CAPDIR/$b" 2>/dev/null; then
			if [ -s "$CAPDIR/$b" ]; then
				LPULLED="$LPULLED $CAPDIR/$b"
				NL=$((NL + 1))
			fi
		fi
	done

	if [ -z "$PULLED" ] && [ -z "$LPULLED" ]; then
		log "iter $i: boot=$BID up=${UP}s fatals=$F running=$R -- nothing pulled yet"
		sleep "$SLEEP"
		continue
	fi

	# Part A n
	NALL=""; NST=""
	if [ -n "$PULLED" ]; then
		# shellcheck disable=SC2086
		N=$(python3 "$EV/score_pw.py" $PULLED --n 2>/dev/null)
		NALL=$(echo "$N" | cut -d' ' -f1)
		NST=$(echo "$N" | cut -d' ' -f2)
	fi

	# Part B: SSR count + verdicts
	NSSR=0
	if [ -n "$LPULLED" ]; then
		# shellcheck disable=SC2086
		python3 "$EV/$MM" $LPULLED >"$EV/score_mm_latest.txt" 2>&1
		# sum the per-file SSR counts
		NSSR=$(awk '/^SSRs found:/ {s += $3} END {print s+0}' "$EV/score_mm_latest.txt")
	fi

	log "iter $i: boot=$BID up=${UP}s fatals=$F running=$R | Part A: resumes=${NALL:-?}/500 stale=${NST:-?}/20 | Part B: ssrs=${NSSR}/11"

	if [ -n "$PULLED" ]; then
		# shellcheck disable=SC2086
		python3 "$EV/score_pw.py" $PULLED >"$EV/score_pw_latest.txt" 2>&1
	fi
	if [ -f "$EV/score_mm_latest.txt" ]; then
		grep -E "^  P-MM[123]" "$EV/score_mm_latest.txt" | sed 's/^/    /' >>"$PROG"
	fi

	if [ "${NALL:-0}" -ge 500 ] && [ "${NST:-0}" -ge 20 ] && [ "${NSSR:-0}" -ge 11 ]; then
		log "=== BOTH THRESHOLDS MET -- final verdicts ==="
		if [ -n "$PULLED" ]; then
			# shellcheck disable=SC2086
			python3 "$EV/score_pw.py" $PULLED 2>&1 | tee "$EV/score_pw_final.txt" | sed 's/^/    /' >>"$PROG"
			for c in $PULLED; do cp -f "$c" "$EV/"; done
		fi
		if [ -n "$LPULLED" ]; then
			# shellcheck disable=SC2086
			python3 "$EV/$MM" $LPULLED 2>&1 | tee "$EV/score_mm_final.txt" | sed 's/^/    /' >>"$PROG"
			for c in $LPULLED; do cp -f "$c" "$EV/"; done
		fi
		exit 0
	fi

	sleep "$SLEEP"
done

log "monitor end: iteration cap reached without both thresholds being met"
exit 0

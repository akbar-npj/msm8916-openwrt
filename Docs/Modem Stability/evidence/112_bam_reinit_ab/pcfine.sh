#!/bin/sh
# Device-side tight-loop telemetry sampler.
#
# Purpose: resolve the bam_dmux SSR window (~1.1 s) to milliseconds so that
# `pc_irq_count` can be read across it.  The A/B outcome of
# bam_dmux_ssr_powerup_work_func() is decided by whether bam_dmux_pc_irq()
# fired during that window:
#
#   pc_irq_count incremented  -> the edge WAS delivered (irq path rebuilt rx) -> B
#   pc_irq_count unchanged    -> the edge was LOST (only the level-poll saw it)
#                                -> A
#
# The outcome line alone cannot separate those two, because a delivered edge
# whose bam_dmux_power_on() failed also leaves rx==NULL and also yields A.
#
# busybox sleep rejects fractional arguments ("sleep: invalid number '0.2'"),
# so this is a fork-per-iteration loop rather than a timed one.  One awk fork
# is the whole cost; the loop rate is measured, not assumed.
#
# Logs a full record only when a tracked field changes, plus a heartbeat, so
# the file stays small.
#
# usage: pcfine.sh <outfile> [max_iters]

TEL=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry
OUT=${1:-/tmp/pcfine.txt}
N=${2:-2000000}
HB=${3:-3000}

i=0
prev=""
hb=0
: > "$OUT"

while [ "$i" -lt "$N" ]; do
	cur=$(awk '
		BEGIN {
			while ((getline ln < "/proc/uptime") > 0) {
				split(ln, u, " "); UP = u[1]; break
			}
		}
		/^pc_irq_count:/       { iq = $2 }
		/^pc_ack_irq_count:/   { aq = $2 }
		/^pc_state:/           { ps = $2 }
		/^pc_line_level:/      { pl = $2 }
		/^pc_timeout_count:/   { to = $2 }
		/^pc_resync_count:/    { rs = $2 }
		/^pc_quiesce_ms:/      { qm = $2 }
		/^rx_rearm_count:/     { ra = $2 }
		/^rx_tearing_down:/    { td = $2 }
		/^rx_callbacks:/       { rc = $2 }
		/^pc_vote_tx_count:/   { vt = $2 }
		/^pc_unvote_tx_count:/ { vu = $2 }
		/^pm_resume_attempts:/ { mra = $2 }
		/^pm_suspend_attempts:/{ msa = $2 }
		/^cmd_open:/           { co = $2 }
		END {
			# The comparison key is the EVENT fields only.  UP and qm
			# are live counters and change on every iteration, so
			# including them would defeat change-detection entirely.
			printf "%s %s %s %s %s %s %s %s %s %s %s %s %s %s|%s %s\n",
			       iq, aq, ps, pl, to, rs, ra, td, rc, vt, vu, mra, msa, co,
			       UP, qm
		}
	' "$TEL")

	key=${cur%%|*}
	if [ "$key" != "$prev" ] || [ "$hb" -ge "$HB" ]; then
		printf '%s\n' "$cur" >> "$OUT"
		prev="$key"
		hb=0
	fi
	i=$((i + 1))
	hb=$((hb + 1))
done

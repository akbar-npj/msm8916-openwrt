#!/bin/bash
# run_hp3.sh - one `echo stop` run under the fast (ms-period) sampler.
#
# WHY: the T4->T5 block inside bam_dmux_power_off() is the leading AP-hang
# suspect and has THREE blocking steps between T4 and T5:
#     :1591 cancel_delayed_work_sync(&dmux->rx_rearm_work)      <- H5 (NEW)
#     :1595 wait_event(rx_submit_wait, rx_active_submitters==0) <- H3
#     :1600 dmaengine_terminate_sync(dmux->rx)                  <- H2
# The sampler separates them:
#     rx_active_submitters != 0            -> H3
#     rx_active_submitters == 0            -> H2 or H5
#     pc_state / rx_rearm_count            -> H5 is only ACTIVE while pc_state==1
#                                             (rx_rearm_work self-reschedules
#                                              every 200 ms while the modem is awake)
# and loop B's stacks name the blocked function, separating H2 from H5.
#
# usage: run_hp3.sh [tag]
TAG=${1:-run1}
H=root@192.168.8.1
SSH="ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=6"
LOG=/tmp/hp3_${TAG}.log
: > "$LOG"
say() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

TEL=/sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry

say "=== TAG $TAG : pre-trigger state ==="
timeout 25 $SSH $H "
  echo uptime=\$(cut -d' ' -f1 /proc/uptime)
  echo rproc=\$(cat /sys/class/remoteproc/remoteproc0/state)
  echo md5=\$(md5sum /lib/modules/6.12.94/qcom_bam_dmux.ko | cut -d' ' -f1)
  grep -E 'pc_state|pc_line_level|rx_tearing_down|rx_slots_submitted|rx_slots_free|rx_active_submitters|rx_active_callbacks|rx_rearm_count|runtime_status|pm_usage_count' $TEL
  echo qmipid=\$(pidof qmi-proxy)
" 2>&1 | tee -a "$LOG"

say "=== waking the modem (the H5 candidate is only ACTIVE while pc_state==1) ==="
timeout 40 $SSH $H "
  ping -c 4 -W 2 8.8.8.8 >/dev/null 2>&1
  sleep 1
  echo -n 'pc_state at trigger time: '; grep '^pc_state:' $TEL
  echo -n 'pc_line_level: '; grep '^pc_line_level:' $TEL
  echo -n 'rx_rearm_count: '; grep '^rx_rearm_count:' $TEL
  echo -n 'rx_slots_submitted: '; grep '^rx_slots_submitted:' $TEL
  echo -n 'rx_active_submitters: '; grep '^rx_active_submitters:' $TEL
" 2>&1 | tee -a "$LOG"

say "=== arming sampler (period ~few ms; instrument safety verified in source) ==="
timeout 25 $SSH $H 'rm -f /overlay/hp3_fast.log /overlay/hp3_stacks.log /overlay/hp3_start.log
  setsid /overlay/hp3.sh /overlay 150 >/dev/null 2>&1 </dev/null &
  sleep 4
  echo -n "fast samples armed: "; grep -c "^U " /overlay/hp3_fast.log' 2>&1 | tee -a "$LOG"

say "=== TRIGGER: echo stop ==="
( timeout 60 $SSH $H 'echo stop > /sys/class/remoteproc/remoteproc0/state; echo "WRITE_RETURNED rc=$?"' ) >> "$LOG" 2>&1 &
TRIG=$!

for i in $(seq 1 12); do
	sleep 4
	U=$(timeout 6 $SSH $H 'cut -d" " -f1 /proc/uptime' 2>/dev/null)
	if [ -n "$U" ]; then say "liveness $i: uptime=$U"; else say "liveness $i: NO ANSWER"; fi
done
wait $TRIG 2>/dev/null
say "trigger ssh exited"

say "=== sampler result ==="
timeout 30 $SSH $H "
  echo '--- fast log: last 6 U lines and the T lines for the same n ---'
  tail -12 /overlay/hp3_fast.log
  echo '--- distinct rx_active_submitters values ---'
  sed -n 's/.*rx_active_submitters= \([0-9]*\).*/\1/p' /overlay/hp3_fast.log | sort | uniq -c
  echo '--- distinct pc_state values ---'
  sed -n 's/.*pc_state= \([0-9]*\).*/\1/p' /overlay/hp3_fast.log | sort | uniq -c
  echo '--- rx_rearm_count first/last ---'
  sed -n 's/.*rx_rearm_count= \([0-9]*\).*/\1/p' /overlay/hp3_fast.log | head -1
  sed -n 's/.*rx_rearm_count= \([0-9]*\).*/\1/p' /overlay/hp3_fast.log | tail -1
  echo '--- U lines vs T lines (mismatch => telemetry read blocked) ---'
  grep -c '^U ' /overlay/hp3_fast.log; grep -c '^T ' /overlay/hp3_fast.log
  echo '--- stack samples taken ---'
  grep -c '^=== S' /overlay/hp3_stacks.log
  echo '--- stacks mentioning the suspects ---'
  grep -nE 'bam_dmux_power_off|wait_event|rx_submit_wait|dmaengine_terminate|dma_async_issue_pending|pm_runtime|bam_dma|flush_work|__cancel_work|process_one_work|state_lock|mutex' /overlay/hp3_stacks.log | tail -40
  echo '--- last stack sample (verbatim) ---'
  awk '/^=== S/{buf=\"\"} {buf=buf \$0 \"\\n\"} END{print buf}' /overlay/hp3_stacks.log
  echo '--- rproc state now ---'; cat /sys/class/remoteproc/remoteproc0/state
  echo '--- pstore ---'; ls -la /sys/fs/pstore/
" 2>&1 | tee -a "$LOG"
say "done"

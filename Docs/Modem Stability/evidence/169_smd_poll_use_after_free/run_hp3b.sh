#!/bin/bash
# run_hp3b.sh - Doc 168 §2.4 experiment 2, and the causal test for the AP reset.
#
# QUESTION
#   The wait-queue list corruption fires 57 ms after a COMPLETED SSR teardown, in
#   qmi-proxy's poll_freewait(), 2/2.  §2.4 says the clobbered head is
#   `channel->fblockread_event` -- freed by qcom_smd_edge_release()'s bare
#   kfree(channel) -- reached through the poll chain
#       /dev/wwan0qmi0 -> wwan_port_fops_poll -> rpmsg_wwan_ctrl_tx_poll
#       -> rpmsg_poll -> qcom_smd_poll -> poll_wait(&channel->fblockread_event)
#   (verified empirically: qmi-proxy fd 7 -> /dev/wwan0qmi0, backing rpmsg device
#    remoteproc0:smd-edge.DATA5_CNTL).
#
# PREDICTION
#   SIGSTOP qmi-proxy -> it is woken out of ppoll() (SIGSTOP interrupts the
#   syscall and do_sys_poll runs poll_freewait), so the entry is UNLINKED while
#   the objects are still valid, and no poller remains registered when the SMD
#   channel is freed.  Therefore:
#       * the `list_del corruption` WARNING should NOT appear, and
#       * if the corruption is what resets the AP, the AP should now SURVIVE.
#   If the corruption still appears, §2.4 is wrong.
#
# NOTE ON SIGSTOP vs SIGKILL: SIGSTOP keeps fd 7 OPEN (so the wwan_port device
#   ref is still held and port->waitqueue is provably not freed), which is the
#   condition §2.4's argument depends on.  SIGKILL would close the fd and change
#   the refcount, confounding the test.
#
# usage: run_hp3b.sh [tag]
TAG=${1:-noqmi}
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
  echo qmipid=\$(pidof qmi-proxy)
" 2>&1 | tee -a "$LOG"

say "=== §2.4 exp 2: SIGSTOP qmi-proxy (keeps fd 7 open) ==="
timeout 25 $SSH $H '
  for p in $(pidof qmi-proxy); do echo "stopping pid $p"; kill -STOP $p; done
  sleep 2
  for p in $(pidof qmi-proxy); do
    echo "pid $p state=$(awk "{print \$3}" /proc/$p/stat)  (T=stopped)"
    ls -l /proc/$p/fd 2>/dev/null | grep -E "wwan|rpmsg" || echo "  (no wwan fd listed)"
  done
' 2>&1 | tee -a "$LOG"

say "=== waking the modem ==="
timeout 40 $SSH $H "
  ping -c 4 -W 2 8.8.8.8 >/dev/null 2>&1
  sleep 1
  echo -n 'pc_state: '; grep '^pc_state:' $TEL
  echo -n 'rx_slots_submitted: '; grep '^rx_slots_submitted:' $TEL
  echo -n 'rx_tearing_down: '; grep '^rx_tearing_down:' $TEL
" 2>&1 | tee -a "$LOG"

say "=== arming sampler ==="
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

say "=== result: did the corruption fire? ==="
timeout 30 $SSH $H "
  echo '--- console-ramoops: corruption count ---'
  grep -c 'list_del corruption' /sys/fs/pstore/console-ramoops-0 2>/dev/null
  echo '--- console-ramoops: teardown trace tail ---'
  grep -nE 'SSR teardown T[0-9]|bam-dmux T[5-9]|list_del|stopped remote processor|port wwan0at0 disconnected' /sys/fs/pstore/console-ramoops-0 2>/dev/null | tail -25
  echo '--- pmsg corruption count ---'
  grep -c 'list_del corruption' /sys/fs/pstore/pmsg-ramoops-0 2>/dev/null
  echo '--- rproc state ---'; cat /sys/class/remoteproc/remoteproc0/state
  echo '--- uptime ---'; cut -d' ' -f1 /proc/uptime
  echo '--- qmi-proxy state ---'; for p in \$(pidof qmi-proxy); do awk '{print \$3}' /proc/\$p/stat; done
" 2>&1 | tee -a "$LOG"
say "done"

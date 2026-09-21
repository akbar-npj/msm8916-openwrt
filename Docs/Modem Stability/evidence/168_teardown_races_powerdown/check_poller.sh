#!/bin/bash
# check_poller.sh - identify WHICH wait queue qmi-proxy polls (Doc 168 §2.3, §9 Priority 1).
#
# The corruption's head is a heap wait queue whose `next` was overwritten, and the
# removed entry is a poll_table_entry on qmi-proxy's own kernel stack.  Only three
# poll_wait() sites exist in the running tree:
#   wwan_core.c:776      port->waitqueue        (wwan_port_fops_poll)
#   rpmsg_char.c:307     eptdev->readq          (rpmsg_eptdev_poll)
#   qcom_smd.c:998       channel->fblockread_event (qcom_smd_poll)
# `Modules linked in` lists rpmsg_wwan_ctrl + wwan and NOT cdc_wdm, so
# port->waitqueue (/dev/wwan0qmi0) is the leading candidate -- but that is a
# hypothesis.  This script turns it into a measurement: whatever device
# qmi-proxy has open IS the driver whose poll registered the entry.
H=root@192.168.8.1
SSH="ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o ConnectTimeout=6"

timeout 30 $SSH $H '
echo "=== uptime ==="; cut -d" " -f1 /proc/uptime
echo "=== bam module md5 ==="; md5sum /lib/modules/6.12.94/qcom_bam_dmux.ko
echo "=== qmi-proxy pid(s) ==="; pidof qmi-proxy
echo
echo "=== qmi-proxy open fds (THIS IS THE ANSWER) ==="
for p in $(pidof qmi-proxy); do
  echo "--- pid $p"
  ls -l /proc/$p/fd 2>/dev/null | sed "s/^/    /"
done
echo
echo "=== qmi-proxy threads: comm / wchan / stack ==="
for p in $(pidof qmi-proxy); do
  for t in /proc/$p/task/*; do
    echo "--- ${t}  comm=$(cat $t/comm 2>/dev/null)  wchan=$(cat $t/wchan 2>/dev/null)"
    cat $t/stack 2>/dev/null | sed "s/^/    /"
  done
done
echo
echo "=== every process holding a wwan/rpmsg/smd/cdc-wdm fd ==="
for p in $(ls /proc | grep -E "^[0-9]+$"); do
  ls -l /proc/$p/fd 2>/dev/null | grep -E "wwan|rpmsg|/smd|cdc-wdm" | sed "s|^|  pid $p: |"
done
echo
echo "=== device nodes ==="
ls -l /dev/wwan* /dev/rpmsg* /dev/smd* /dev/cdc-wdm* 2>&1
echo
echo "=== rpmsg endpoints ==="
ls -l /sys/bus/rpmsg/devices/ 2>&1 | head -20
'

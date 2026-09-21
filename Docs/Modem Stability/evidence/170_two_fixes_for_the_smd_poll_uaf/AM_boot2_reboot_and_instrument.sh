#!/bin/bash
# Second Android boot for the Doc 170 PART 32/35 pre-registered test.
#
# Scores the CONDITIONAL statistic only: does a connectivity outage recur at the
# SAME MODEM-RELATIVE time (902.3 +- 40 s)?  The marginal statistic ("did an
# outage happen") is uninformative while the device is on a bus.
#
# ORDER MATTERS: adbd comes back as uid 2000 after a reboot, and `adb root`
# SILENTLY DOES NOTHING.  The working re-root (`setprop service.adb.root 1;
# busybox killall adbd`) KILLS EVERY RUNNING CAPTURE -- so it must complete
# BEFORE any capture is started, never after.
set -u
cd /home/shaanair/Projects/msm8916-openwrt-clean || exit 1
OUT=scratch/android_capture/boot2
mkdir -p "$OUT"
LOG="$OUT/reboot_driver.log"
say() { echo "[$(date -u +%H:%M:%S)] $*" | tee -a "$LOG"; }

say "=== step 1: save the current boot's final state ==="
timeout 20 adb shell 'cut -d. -f1 /proc/uptime' > "$OUT/boot1_final_uptime.txt" 2>&1
wc -l scratch/android_capture/bam_dmux.log scratch/android_capture/soak_ping.log \
      scratch/android_capture/soak_ping_iface.log \
      scratch/android_capture/logcat_persistent.log \
      scratch/android_capture/rpm_master_stats2.log > "$OUT/boot1_final_sizes.txt" 2>&1
cat "$OUT/boot1_final_sizes.txt" | tee -a "$LOG"

say "=== step 2: reboot ==="
timeout 30 adb reboot 2>&1 | tee -a "$LOG"
sleep 5

say "=== step 3: wait for the device (bounded) ==="
for i in $(seq 1 90); do
    if timeout 10 adb shell 'echo ok' 2>/dev/null | grep -q ok; then
        say "shell responsive after ${i} tries"
        break
    fi
    sleep 2
done
if ! timeout 10 adb shell 'echo ok' 2>/dev/null | grep -q ok; then
    say "FATAL: device did not come back after ~180 s -- STOPPING, no captures started"
    exit 1
fi

say "=== step 4: BOOT ANCHOR (uptime + wall clock, as early as possible) ==="
timeout 20 adb shell 'cat /proc/uptime; date; getprop ro.boottime.init' \
    > "$OUT/boot2_anchor.txt" 2>&1
cat "$OUT/boot2_anchor.txt" | tee -a "$LOG"

say "=== step 5: re-root BEFORE any capture (this kills adbd) ==="
timeout 20 adb shell 'id' > "$OUT/boot2_id_before.txt" 2>&1
cat "$OUT/boot2_id_before.txt" | tee -a "$LOG"
if ! timeout 20 adb shell 'id' 2>/dev/null | grep -q 'uid=0'; then
    say "not root -- running the setprop + killall adbd dance"
    timeout 20 adb shell 'setprop service.adb.root 1; busybox killall adbd' >/dev/null 2>&1
    sleep 8
fi
for i in $(seq 1 20); do
    if timeout 10 adb shell 'id' 2>/dev/null | grep -q 'uid=0'; then break; fi
    sleep 2
done
timeout 20 adb shell 'id' > "$OUT/boot2_id_after.txt" 2>&1
cat "$OUT/boot2_id_after.txt" | tee -a "$LOG"

say "=== step 6: verify the debugfs instruments exist before relying on them ==="
timeout 20 adb shell 'ls -la /d/bam_dmux/stats /d/ipc_logging/bam_dmux/log_cont /d/rpm_master_stats 2>&1' \
    > "$OUT/boot2_instruments.txt" 2>&1
cat "$OUT/boot2_instruments.txt" | tee -a "$LOG"

say "=== step 7: start the captures ==="
# logcat FIRST: after a reboot the buffers are fresh, so this is near-complete
# coverage from boot.  (-b all does NOT work on this KitKat.)
nohup adb logcat -v time > "$OUT/logcat_boot2.log" 2>&1 &
sleep 1
# bam_dmux continuous reader
nohup adb shell 'cat /d/ipc_logging/bam_dmux/log_cont' > "$OUT/bam_dmux_boot2.log" 2>&1 &
sleep 1
# BOTH ping variants, side by side (PART 33 s2: -I cannot see a route loss)
nohup adb shell 'while true; do ping -c 3 -W 2 8.8.8.8; sleep 27; done' \
    > "$OUT/ping_plain_boot2.log" 2>&1 &
sleep 1
nohup adb shell 'while true; do ping -c 3 -W 2 -I rmnet0 8.8.8.8; sleep 27; done' \
    > "$OUT/ping_iface_boot2.log" 2>&1 &
sleep 1
# 10 s counter snapshot (pre-registered cadence, for +-10 s onset bracketing)
nohup adb shell 'while true; do echo "=== uptime $(cut -d. -f1 /proc/uptime) ==="; cat /d/bam_dmux/stats; cat /proc/net/dev | grep rmnet; ip route | head -3; sleep 10; done' \
    > "$OUT/snapshot_boot2.log" 2>&1 &
sleep 1
# RPM power-collapse series (compare against a STATIONARY Android baseline, NOT
# the bus baseline of PART 31 -- see PART 35 s3)
nohup adb shell 'while true; do echo "=== up $(cut -d. -f1 /proc/uptime) ==="; grep -E "numshutdowns|active_cores" /d/rpm_master_stats; sleep 30; done' \
    > "$OUT/rpm_boot2.log" 2>&1 &
sleep 3

say "=== step 8: verify ==="
ps -eo pid,args | grep "[a]db shell" | grep -v "bash -c" | tee -a "$LOG"
ps -eo pid,args | grep "[a]db logcat" | grep -v "bash -c" | tee -a "$LOG"
ls -la "$OUT"/ | tee -a "$LOG"

say "=== step 9: capture dmesg early (the 'one modem boot' control) ==="
sleep 60
timeout 30 adb shell 'dmesg' > "$OUT/dmesg_boot2_early.log" 2>&1
wc -l "$OUT/dmesg_boot2_early.log" | tee -a "$LOG"
say "=== boot2 instrumentation complete; captures running ==="

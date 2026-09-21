#!/bin/bash
# Supervisor for the boot2 Android captures.
#
# WHY THIS EXISTS: after `setprop service.adb.root 1; busybox killall adbd` the
# adbd daemon can restart MORE THAN ONCE.  In the first attempt the three
# captures started earliest were killed by a second restart while the two
# started 4-5 s later survived.  So: start captures, then keep checking and
# restart any that died.  This also covers USB flakiness on a moving platform.
#
# TRAP: the remote command MUST be single-quoted.  `adb shell while true; do ...`
# lets the HOST shell parse `while true` and then choke on `do` -- the process
# dies instantly and the supervisor restart-loops forever.
set -u
cd /home/shaanair/Projects/msm8916-openwrt-clean || exit 1
OUT=scratch/android_capture/boot2
mkdir -p "$OUT"
LOG="$OUT/supervisor.log"
say() { echo "[$(date -u +%H:%M:%S)] $*" >> "$LOG"; }

NAMES=(logcat bam_dmux ping_plain ping_iface snapshot rpm)

# pgrep -f pattern (must be present in the RUNNING process's cmdline)
declare -A PAT
PAT[logcat]='adb logcat -v time'
PAT[bam_dmux]='/d/ipc_logging/bam_dmux/log_cont'
PAT[ping_plain]='ping -c 3 -W 2 8.8.8.8;'
PAT[ping_iface]='ping -c 3 -W 2 -I rmnet0'
PAT[snapshot]='cat /d/bam_dmux/stats'
PAT[rpm]='grep -E "numshutdowns|active_cores" /d/rpm_master_stats'

# full launch command, redirect and background included, ready for eval.
# NOTE the single quotes around every remote command.
declare -A LAUNCH
LAUNCH[logcat]="nohup adb logcat -v time >> '$OUT/logcat_boot2.log' 2>&1 &"
LAUNCH[bam_dmux]="nohup adb shell 'cat /d/ipc_logging/bam_dmux/log_cont' >> '$OUT/bam_dmux_boot2.log' 2>&1 &"
LAUNCH[ping_plain]="nohup adb shell 'while true; do ping -c 3 -W 2 8.8.8.8; sleep 27; done' >> '$OUT/ping_plain_boot2.log' 2>&1 &"
LAUNCH[ping_iface]="nohup adb shell 'while true; do ping -c 3 -W 2 -I rmnet0 8.8.8.8; sleep 27; done' >> '$OUT/ping_iface_boot2.log' 2>&1 &"
LAUNCH[snapshot]="nohup adb shell 'while true; do echo \"=== uptime \$(cut -d. -f1 /proc/uptime) ===\"; cat /d/bam_dmux/stats; cat /proc/net/dev | grep rmnet; ip route | head -3; sleep 10; done' >> '$OUT/snapshot_boot2.log' 2>&1 &"
LAUNCH[rpm]="nohup adb shell 'while true; do echo \"=== up \$(cut -d. -f1 /proc/uptime) ===\"; grep -E \"numshutdowns|active_cores\" /d/rpm_master_stats; sleep 30; done' >> '$OUT/rpm_boot2.log' 2>&1 &"

say "supervisor starting"
while true; do
    if ! timeout 10 adb shell 'echo ok' 2>/dev/null | grep -q ok; then
        say "device not responsive -- waiting"
        sleep 15
        continue
    fi
    # re-assert root: a reboot or an adbd restart can drop us back to uid 2000,
    # and the debugfs reads then fail silently
    if ! timeout 10 adb shell 'id' 2>/dev/null | grep -q 'uid=0'; then
        say "LOST ROOT -- re-running the setprop + killall adbd dance"
        timeout 15 adb shell 'setprop service.adb.root 1; busybox killall adbd' >/dev/null 2>&1
        sleep 12
        continue
    fi
    for name in "${NAMES[@]}"; do
        if ! pgrep -f -- "${PAT[$name]}" >/dev/null 2>&1; then
            say "RESTARTING $name"
            eval "${LAUNCH[$name]}"
            sleep 1
        fi
    done
    sleep 20
done

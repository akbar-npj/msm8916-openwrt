#!/bin/bash
# Doc 174 §10 -- STATIONARY Android MCPM cadence capture.
#
# WHY: Doc 170 flagged the Android-vs-OpenWrt MCPM gap (Android 1.88/s vs
# OpenWrt 0.83/s, 2.3x) as BUS-CONFOUNDED -- the same confound Doc 174 resolved
# for the RPM collapse rate.  This measures the Android side stationary.
#
# METHOD: identical to scratch/android900/run_soak.sh except that it does NOT
# reboot (the device is already up and stationary) and the traffic pattern is
# matched to the soak (ping -c 3 / 30 s), so the number is comparable.
#
# Metric: count of "SLEEP_PWRDN_FULL" DIAG_MSGF (0x79) frames / capture seconds.
# NOTE the analyzer gives BYTE OFFSET gaps, not time -- the RATE comes from the
# kernel-uptime bracket recorded here, never from the offsets.
#
# Usage: mcpm_stationary.sh [duration_s]     (default 900 s)
set -u

ROOT=/home/shaanair/Projects/msm8916-openwrt-clean
OUT="$ROOT/scratch/android_capture/stationary"
DIAGCFG="$ROOT/scratch/android900/Diag.cfg"
DUR=${1:-900}
BOOT_OFFSET=6.64          # modem anchor = kernel uptime - 6.64 (run_soak.sh §3)
DEV_OUT=/sdcard/diag_logs/mcpm_stat

mkdir -p "$OUT"
LOG="$OUT/mcpm_capture.log"
: > "$LOG"
log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG"; }
S()   { adb shell "$@" 2>&1 | tr -d '\r'; }

log "=== stationary MCPM capture: duration ${DUR}s ==="

ID=$(S 'id')
log "id: $ID"
case "$ID" in *"uid=0"*) : ;; *) log "FATAL: not root, aborting"; exit 1 ;; esac

BOOT_LINE=$(S 'dmesg | grep -m1 "pil-q6v5-mss.*Brought out of reset"')
log "modem anchor: $BOOT_LINE"
echo "$BOOT_LINE" > "$OUT/modem_anchor.txt"

# --- mask file: abort rather than produce a silent empty capture -------------
CFGSUM=$(S 'md5sum /sdcard/diag_logs/Diag.cfg 2>/dev/null')
case "$CFGSUM" in
    *Diag.cfg*) log "Diag.cfg present: $CFGSUM" ;;
    *)  log "Diag.cfg missing - pushing $DIAGCFG"
        S 'mkdir -p /sdcard/diag_logs' >/dev/null 2>&1
        adb push "$DIAGCFG" /sdcard/diag_logs/Diag.cfg >/dev/null 2>&1
        CFGSUM=$(S 'md5sum /sdcard/diag_logs/Diag.cfg 2>/dev/null')
        log "pushed: $CFGSUM"
        case "$CFGSUM" in *Diag.cfg*) : ;; *) log "FATAL: Diag.cfg push failed"; exit 1 ;; esac ;;
esac

# --- radio context (the stationarity evidence) ------------------------------
log "rmnet0: $(S 'ip -o -4 addr show rmnet0 2>/dev/null')"
log "rsrp:   $(S 'dumpsys telephony.registry 2>/dev/null | grep -m1 mSignalStrength')"

# --- traffic pattern, matched to the soak (plain ping: routing path) --------
log "--- starting ping loop (3 pings / 30s) ---"
S 'busybox killall ping 2>/dev/null; rm -f /data/ping.log; nohup sh -c "while true; do echo \"\$(cut -d\" \" -f1 /proc/uptime) \$(ping -c 3 -W 2 8.8.8.8 2>&1 | tail -2 | tr \"\n\" \" \")\" >> /data/ping.log; sleep 27; done" >/dev/null 2>&1 &' >/dev/null 2>&1
sleep 8

# --- start capture ----------------------------------------------------------
# -s 100 -n 8 => up to 8 x 100 MB before circular logging wraps.  Measured fill
# rate is ~450 KB/s, so 300 s needs ~135 MB; -n 2 (200 MB) would have SILENTLY
# DROPPED the first ~220 s of a 600 s window and understated the rate by ~1.6x.
FILES=8
SIZE_MB=100
log "--- starting diag_mdlog (-s $SIZE_MB -n $FILES) ---"
# nohup is REQUIRED but NOT SUFFICIENT.  MEASURED 2026-09-22: a `nohup ... &`
# diag_mdlog is ALIVE while the launching adb shell is still connected and DIES
# the moment it disconnects -- so a "sleep 8; ps" check inside the same shell
# sees procs=1 and the run then silently produces nothing.  `busybox setsid`
# (new session, detached from the shell's process group) survives: verified
# procs=1 at +45 s with the shell long gone.  `< /dev/null` too, so the daemon
# does not hold the shell's stdin open.
# The launch is also flaky enough to warrant a retry.  The rm happens only ONCE,
# before the first attempt, or a retry would delete the file just opened.
S 'busybox killall -9 diag_mdlog 2>/dev/null; sleep 1' >/dev/null 2>&1
S "rm -rf $DEV_OUT" >/dev/null 2>&1
NPROC=0
for ATTEMPT in 1 2 3; do
    S "busybox setsid nohup diag_mdlog -o $DEV_OUT -s $SIZE_MB -n $FILES > /data/mdlog.out 2>&1 < /dev/null & sleep 5" >/dev/null 2>&1
    sleep 10
    NPROC=$(S 'ps | grep -c "[d]iag_mdlog"' | tr -dc '0-9')
    [ "${NPROC:-0}" -ge 1 ] && break
    log "  launch attempt $ATTEMPT failed (procs=0); retrying"
done
if [ "${NPROC:-0}" -lt 1 ]; then
    log "FATAL: diag_mdlog did not start after 3 attempts. stderr:"
    S 'cat /data/mdlog.out' | sed 's/^/    /' | tee -a "$LOG"
    exit 1
fi
log "mdlog procs:  $NPROC"
log "mask-file logcat lines: $(adb logcat -d -s Diag_Lib:V 2>/dev/null | grep -c 'mask file name is')"
log "cannot-open errors:     $(adb logcat -d -s Diag_Lib:V 2>/dev/null | grep -c "can't open MSM mask file")"

U0=$(S 'cut -d. -f1 /proc/uptime' | tr -dc '0-9')
M0=$(awk "BEGIN{printf \"%.0f\", ${U0:-0}-$BOOT_OFFSET}")
log "capture start: kernel=${U0}s modem~=${M0}s"
T0=$(date +%s)

# --- wait -------------------------------------------------------------------
while :; do
    now=$(date +%s)
    [ $((now - T0)) -ge "$DUR" ] && break
    sleep 30
    log "  elapsed=$((now - T0))s  qmdl=$(S "du -sk $DEV_OUT 2>/dev/null | cut -f1")KB"
done

U1=$(S 'cut -d. -f1 /proc/uptime' | tr -dc '0-9')
M1=$(awk "BEGIN{printf \"%.0f\", ${U1:-0}-$BOOT_OFFSET}")
log "--- stopping capture: kernel=${U1}s modem~=${M1}s ---"
S 'killall diag_mdlog 2>/dev/null' >/dev/null 2>&1
sleep 4
S 'busybox killall -9 diag_mdlog 2>/dev/null' >/dev/null 2>&1
sleep 2

# --- SSR / fatal check across the window ------------------------------------
log "subsys-restart count: $(S 'dmesg | grep -c "subsys-restart\|Subsystem restart"')"
log "fatal markers:        $(S 'dmesg | grep -c "watchdog\|Q6 PC\|FATAL"')"

S 'cat /data/ping.log' > "$OUT/mcpm_ping.log"
S 'ls -laR /sdcard/diag_logs/mcpm_stat' > "$OUT/mcpm_capture_listing.txt"

log "--- pulling capture ---"
adb pull "$DEV_OUT" "$OUT/" 2>&1 | tail -3 | tee -a "$LOG"

{
  echo "kernel_start_s=$U0"
  echo "kernel_end_s=$U1"
  echo "modem_start_s=$M0"
  echo "modem_end_s=$M1"
  echo "wall_duration_s=$(( $(date +%s) - T0 ))"
  echo "mdlog_size_mb=$SIZE_MB"
  echo "mdlog_files=$FILES"
  echo "mdlog_bytes_total=$(S "cat $DEV_OUT/*.qmdl 2>/dev/null | wc -c" | tr -dc '0-9')"
  echo "mdlog_file_count=$(S "ls $DEV_OUT/*.qmdl 2>/dev/null | wc -l" | tr -dc '0-9')"
} > "$OUT/mcpm_window.txt"
cat "$OUT/mcpm_window.txt" | tee -a "$LOG"

log "=== stationary MCPM capture: DONE ==="

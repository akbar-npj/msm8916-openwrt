#!/bin/bash
# Doc 174 s9 follow-up -- run the pre-registered MCPM replicate captures.
# See PREREG_mcpm_replicates.md (written BEFORE this ran).
#
# Four captures, 240 s each, back to back, no reboot.  The traffic pattern is the
# controlled variable.  Each capture gets its own OUT and DEV_OUT so nothing
# clobbers.  Captures that fail their own guard are recorded as FAILED and the
# run continues -- a missing cell in the table is data, not a reason to abort.
set -u

ROOT=/home/shaanair/Projects/msm8916-openwrt-clean
BASE="$ROOT/scratch/android_capture/stationary"
DRIVER_LOG="$BASE/replicates_driver.log"

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$DRIVER_LOG"; }

run_one() {
    local name="$1" traffic="$2"
    local out="$BASE/cap_$name"
    mkdir -p "$out"
    log "=== capture $name: traffic=$traffic, 240 s -> $out ==="
    OUT="$out" DEV_OUT="/sdcard/diag_logs/mcpm_$name" TRAFFIC="$traffic" \
        bash "$ROOT/scratch/android_capture/mcpm_stationary.sh" 240 \
        >> "$DRIVER_LOG" 2>&1
    local rc=$?
    if [ $rc -ne 0 ]; then
        log "!!! capture $name FAILED (rc=$rc) -- see $out/mcpm_capture.log"
        return
    fi
    if [ -s "$out/mcpm_window.txt" ]; then
        log "--- $name window ---"
        sed 's/^/    /' "$out/mcpm_window.txt" | tee -a "$DRIVER_LOG"
    fi
}

log "########## MCPM replicate run START (see PREREG_mcpm_replicates.md) ##########"
run_one C base
run_one D base
run_one E none
run_one F heavy
log "########## MCPM replicate run DONE ##########"
ls -la "$BASE"/cap_*/ 2>/dev/null | tee -a "$DRIVER_LOG"

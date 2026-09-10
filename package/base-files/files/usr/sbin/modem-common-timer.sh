#!/bin/sh
# =============================================================================
# modem-common-timer.sh – OpenWrt replacement for the firmware common timer
#
#  * monitors LTE traffic on rmnet0 (or the interface you configure)
#  * sends a one‑shot ping to the ISP‑provided DNS (49.45.0.1) when idle
#  * optionally puts the modem into low‑power mode after a long idle period
#  * calls the original watchdog binary if the ping fails twice
#
#  Configurable environment variables (export them in /etc/profile):
#    INTERVAL           – seconds between each loop iteration (default 5)
#    STALL_TIMEOUT      – seconds of no traffic before keep‑alive (default 30)
#    SLEEP_TIMEOUT      – seconds of no traffic before low‑power (default 300)
#    ISP_DNS            – DNS address to ping (default 49.45.0.1)
#    IFACE              – network interface that carries LTE traffic
#                        (default rmnet0)
# =============================================================================

# ----- configuration ---------------------------------------------------------
: "${INTERVAL:=5}"
: "${STALL_TIMEOUT:=30}"
: "${SLEEP_TIMEOUT:=300}"
: "${ISP_DNS:=49.45.0.1}"
: "${IFACE:=rmnet0}"

# ----- internal state --------------------------------------------------------
idle_secs=0
fail_cnt=0

log() {
    logger -t modem-common-timer "$@"
}

while :; do
    # read packet counters
    rx=$(cat /sys/class/net/${IFACE}/statistics/rx_packets 2>/dev/null || echo 0)
    tx=$(cat /sys/class/net/${IFACE}/statistics/tx_packets 2>/dev/null || echo 0)

    # if both counters unchanged since last loop → idle
    if [ "$rx" = "$LAST_RX" ] && [ "$tx" = "$LAST_TX" ]; then
        idle_secs=$((idle_secs + INTERVAL))
    else
        idle_secs=0
        fail_cnt=0                # reset ping‑failure counter on any traffic
        # restore modem power state if it had been put to low‑power
        mmcli -m 0 --set-modem-power-state=on >/dev/null 2>&1
    fi

    LAST_RX=$rx
    LAST_TX=$tx

    # ----- keep‑alive (stall detection) ------------------------------------
    if [ $idle_secs -ge $STALL_TIMEOUT ]; then
        log "No traffic for $idle_secs s – sending keep‑alive ping to $ISP_DNS"
        if ping -c1 -W2 $ISP_DNS >/dev/null 2>&1; then
            log "Keep‑alive succeeded"
            fail_cnt=0
        else
            log "Keep‑alive FAILED (attempt $((fail_cnt+1)))"
            fail_cnt=$((fail_cnt + 1))
        fi

        # if we have two consecutive failures → invoke full SSR recovery
        if [ $fail_cnt -ge 2 ]; then
            log "Two keep‑alive failures – falling back to original watchdog"
            exec /usr/sbin/modem-bearer-watchdog.real -c
        fi
    fi

    # ----- low‑power mode (sleep manager) ----------------------------------
    if [ $idle_secs -ge $SLEEP_TIMEOUT ]; then
        log "Idle for $idle_secs s – putting modem into low‑power mode"
        mmcli -m 0 --set-modem-power-state=low >/dev/null 2>&1
    fi

    sleep "$INTERVAL"
done

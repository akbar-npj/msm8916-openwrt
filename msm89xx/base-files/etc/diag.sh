#!/bin/sh
# Board-specific LED state machine for the battery boards (mifib-v12, mf800b).
#
# OpenWrt's preinit calls set_state preinit (and preinit_regular), the failsafe
# path calls set_state failsafe, and sysupgrade calls set_state upgrade
# (/lib/upgrade/common.sh).  The stock diag.sh is gated on DTS led-* aliases
# which these boards do not define, so nothing blinked.  This version drives
# the LEDs directly:
#
#   preinit     blink the battery lamp -> "booting".  The colour does NOT
#               encode capacity: a level cannot be read off a blink, and LuCI
#               reports it.  The battery pair is the only one free this early
#               (the Wi-Fi lamp still carries its DTS default and no monitor
#               has started yet).
#   failsafe    fast-blink BOTH Wi-Fi LEDs at 40/40 -- the same cue
#               35_failsafe_usb gives once the rescue shell is up.  The battery
#               lamp is left alone: red:bat means "fault" now, and a rescue
#               must not read as a battery problem.
#   upgrade     slow-blink green:wlan alone at 500/500.  A flash in progress
#               must not look like a rescue prompt, so it is deliberately a
#               different LED and a different rate.
#   done        no-op: the monitor daemons (battery-led-monitor,
#               wifi-led-monitor, modem-led-monitor) own the steady states and
#               override the boot blink when they start.
#
# NOTE: the battery lamp names differ per board (mifib-v12 green:bat/red:bat,
# mf800b green:charging/red:charging), so they are resolved, not hardcoded.

. /lib/functions/leds.sh

WLAN_GREEN="/sys/class/leds/green:wlan"
WLAN_RED="/sys/class/leds/red:wlan"

pick_led() {
    for n in "$@"; do
        [ -e "/sys/class/leds/$n/trigger" ] && { echo "/sys/class/leds/$n"; return 0; }
    done
    return 1
}

BAT_GREEN="$(pick_led green:bat green:charging)" || BAT_GREEN=""
BAT_RED="$(pick_led red:bat red:charging)" || BAT_RED=""

led_off() {
    [ -n "$1" ] && [ -e "$1/trigger" ] || return 0
    echo none > "$1/trigger" 2>/dev/null
    echo 0 > "$1/brightness" 2>/dev/null
}

led_blink() {
    [ -n "$1" ] && [ -e "$1/trigger" ] || return 0
    echo timer > "$1/trigger" 2>/dev/null
    echo "$2" > "$1/delay_on" 2>/dev/null
    echo "$3" > "$1/delay_off" 2>/dev/null
}

set_led_state() {
    case "$1" in
        preinit|preinit_regular)
            # Reset the battery pair, then a plain "booting" blink.
            led_off "$BAT_RED"
            led_blink "$BAT_GREEN" 200 300
            ;;
        failsafe)
            # Same set and rate as 35_failsafe_usb: every red LED except the
            # battery lamp, then green if the board has no red at all.
            for l in /sys/class/leds/*red*; do
                case "${l##*/}" in *bat*|*charging*) continue ;; esac
                led_blink "$l" 40 40
            done
            ls /sys/class/leds/*red* >/dev/null 2>&1 ||
                for l in /sys/class/leds/*green*; do
                    case "${l##*/}" in *bat*|*charging*) continue ;; esac
                    led_blink "$l" 40 40
                done
            ;;
        upgrade)
            led_off "$WLAN_RED"
            led_blink "$WLAN_GREEN" 500 500
            ;;
        done)
            # Monitors own the steady states; nothing to clear here.
            :
            ;;
    esac
}

set_state() {
    set_led_state "$1"
}

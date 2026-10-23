#!/bin/sh
# Board-specific LED state machine for mifib-v12 / HMU05-class boards.
#
# OpenWrt's preinit calls set_state preinit (and preinit_regular), the
# failsafe path calls set_state failsafe, and the main boot calls set_state done.
# The stock diag.sh is gated on DTS led-* aliases which this board does not
# define, so nothing blinked. This version drives the LEDs directly:
#
#   preinit        reset the battery pair, then blink the BATTERY LED by capacity
#                  (green:bat if capacity >= 20%, else red:bat) -> "booting"
#   failsafe       fast-blink BOTH WiFi LEDs (red:wlan + green:wlan) ->
#                  distinct "failsafe armed" indication
#   done           no-op: the per-LED monitor daemons (battery-led-monitor,
#                  wifi-led-monitor) own the steady states and override the
#                  boot blink when they start (S95/S96).  The WiFi LEDs are left
#                  untouched here so their DTS default blink survives preinit and
#                  the wifi-led-monitor takes over at S95 as normal.
#
# NOTE: the boot-blink capacity threshold (20%) is intentionally a touch higher
# than battery-led-monitor's steady-state LOW_PCT (15%) so a low pack is more
# visible during the cold-start window.

. /lib/functions/leds.sh

GREEN_BAT="/sys/class/leds/green:bat"
RED_BAT="/sys/class/leds/red:bat"
WLAN_LEDS="red:wlan green:wlan"

LOW_PCT=20

led_off() {
    [ -e "$1/trigger" ] || return 0
    echo none > "$1/trigger" 2>/dev/null
    echo 0 > "$1/brightness" 2>/dev/null
}

led_blink() {
    [ -e "$1/trigger" ] || return 0
    echo timer > "$1/trigger" 2>/dev/null
    echo "$2" > "$1/delay_on" 2>/dev/null
    echo "$3" > "$1/delay_off" 2>/dev/null
}

batt_capacity() {
    local p cap
    for p in /sys/class/power_supply/*; do
        [ -d "$p" ] || continue
        [ "$(cat "$p/type" 2>/dev/null)" = "Battery" ] || continue
        cap=$(cat "$p/capacity" 2>/dev/null)
        [ -n "$cap" ] && { echo "$cap"; return 0; }
    done
    echo ""
}

boot_led_for_capacity() {
    local cap i
    for i in 1 2 3 4; do
        cap=$(batt_capacity)
        case "$cap" in
            ''|*[!0-9]*) sleep 1 ;;
            *) break ;;
        esac
    done
    case "$cap" in
        ''|*[!0-9]*) echo "$GREEN_BAT" ;;
        *) [ "$cap" -lt "$LOW_PCT" ] && echo "$RED_BAT" || echo "$GREEN_BAT" ;;
    esac
}

set_led_state() {
    case "$1" in
        preinit|preinit_regular)
            # Reset the battery pair, then a capacity-aware battery blink.
            # WiFi LEDs are intentionally left alone (DTS default + monitor).
            led_off "$GREEN_BAT"; led_off "$RED_BAT"
            led_blink "$(boot_led_for_capacity)" 200 300
            ;;
        failsafe)
            # Distinct indication: both WiFi LEDs fast-blink.
            for l in $WLAN_LEDS; do led_blink "/sys/class/leds/$l" 100 100; done
            ;;
        upgrade)
            for l in $WLAN_LEDS; do led_blink "/sys/class/leds/$l" 100 100; done
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

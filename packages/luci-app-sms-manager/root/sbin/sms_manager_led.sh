#!/bin/sh
#
# Copyright 2026 Rafał Wabik (IceG) - From eko.one.pl forum
# Licensed to the GNU General Public License v3.0.
#
# Drives the unread-SMS LED. Runs as a daemon: every cycle it re-reads the
# modem's message list and (re-)asserts the LED state, so a transient change
# of the shared LED by another monitor (e.g. the Wi-Fi LED monitor clearing
# red:wlan) is reverted within one poll interval instead of leaving the SMS
# indication dead until the next reboot.  The helper therefore re-arms itself.

TMON=$((1 * 1000))
TMOFF=$((5 * 1000))

# Only clear the LED if we are the one driving it (the trigger is currently
# [timer]); never clobber a state set by another monitor.
LED_off() {
	trig=$(cat "$1/trigger" 2>/dev/null)
	if echo "$trig" | grep -q '\[timer\]'; then
		echo 0 > "$1/brightness" 2>/dev/null
		echo none > "$1/trigger" 2>/dev/null
	fi
}

# Assert the blink, but skip the sysfs writes if it is already blinking.
LED_blink() {
	trig=$(cat "$1/trigger" 2>/dev/null)
	if ! echo "$trig" | grep -q '\[timer\]'; then
		echo timer > "$1/trigger" 2>/dev/null
		echo "$TMOFF" > "$1/delay_off" 2>/dev/null
		echo "$TMON" > "$1/delay_on" 2>/dev/null
	fi
}

while true; do
	DEV=$(uci -q get sms_manager.@sms_manager[0].readport)
	LEDX=$(uci -q get sms_manager.@sms_manager[0].smsled)
	SMSC=$(uci -q get sms_manager.@sms_manager[0].sms_count)
	SMSD=$(echo "$SMSC" | tr -dc '0-9')
	SMSD=${SMSD:-0}

	if [ -z "$DEV" ] || [ -z "$LEDX" ]; then
		sleep 15
		continue
	fi

	MODEM_PATH="$DEV"
	MODEM_NUM=$(echo "$MODEM_PATH" | awk -F'/' '{print $NF}')

	# A stored port can go stale: when the modem re-enumerates after an SSR or
	# crash it comes back under a new index, so mmcli -m "$MODEM_NUM" fails.
	# Re-resolve from ModemManager and heal the stored ports too, so the rest of
	# the app points at the live modem.
	if ! mmcli -m "$MODEM_NUM" --messaging-list-sms >/dev/null 2>&1; then
		NEW=$(mmcli -L 2>/dev/null | sed -n 's#.*\(/org/freedesktop/ModemManager1/Modem/[0-9][0-9]*\).*#\1#p' | head -1)
		if [ -n "$NEW" ]; then
			for p in readport sendport ussdport atport; do
				uci -q set sms_manager.@sms_manager[0].$p="$NEW"
			done
			uci -q commit sms_manager
			MODEM_PATH="$NEW"
			MODEM_NUM=$(echo "$MODEM_PATH" | awk -F'/' '{print $NF}')
		fi
	fi

	# stdout is captured, not discarded -- redirecting it here made SMS_LIST
	# always empty, so the guard below exited on every run.
	SMS_LIST=$(mmcli -m "$MODEM_NUM" --messaging-list-sms 2>/dev/null)

	# If the list is momentarily unreadable (modem busy), leave the LED alone
	# and try again next cycle rather than clearing a valid indication.
	if [ -z "$SMS_LIST" ]; then
		sleep 15
		continue
	fi

	SMS=$(echo "$SMS_LIST" | grep -c '/SMS/')
	LED_SYS="/sys/class/leds/$LEDX"

	if [ "$SMS" -gt "$SMSD" ]; then
		LED_blink "$LED_SYS"
	else
		LED_off "$LED_SYS"
	fi

	sleep 30
done

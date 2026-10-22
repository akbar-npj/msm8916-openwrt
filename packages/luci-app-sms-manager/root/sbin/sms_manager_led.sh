#!/bin/sh
#
# Copyright 2026 Rafał Wabik (IceG) - From eko.one.pl forum
# Licensed to the GNU General Public License v3.0.
#

	DEV=$(uci -q get sms_manager.@sms_manager[0].readport)
	LEDX=$(uci -q get sms_manager.@sms_manager[0].smsled)
	SMSC=$(uci -q get sms_manager.@sms_manager[0].sms_count)
	SMSD=$(echo $SMSC | tr -dc '0-9')
	SMSD=${SMSD:-0}
	LEDT="/sys/class/leds/$LEDX/trigger"
	LEDON="/sys/class/leds/$LEDX/delay_on"
	LEDOFF="/sys/class/leds/$LEDX/delay_off"

	TMON=$((1 * 1000))
	TMOFF=$((5 * 1000))

# sms_manager_autoconfig publishes readport a few seconds into boot, once
# ModemManager has enumerated the modem.  Wait for it instead of exiting
# before there is anything to do -- procd starts this helper first.
if [ -z "$DEV" ]; then
	i=0
	while [ -z "$DEV" ] && [ $i -lt 15 ]; do
		sleep 2
		DEV=$(uci -q get sms_manager.@sms_manager[0].readport)
		i=$((i + 1))
	done
fi

if [ -z "$DEV" ]; then
	exit 0
fi

MODEM_NUM=$(echo $DEV | awk -F'/' '{print $NF}')

# Note: stdout is captured, not discarded -- redirecting it here made
# SMS_LIST always empty, so the guard below exited on every run.
SMS_LIST=$(mmcli -m "$MODEM_NUM" --messaging-list-sms 2>/dev/null)

if [ -z "$SMS_LIST" ]; then
	exit 0
fi

SMS=$(echo "$SMS_LIST" | grep -c "/SMS/")

if [ $SMS == $SMSD ]; then

	exit 0
fi

if [ $SMS -gt $SMSD ]; then

echo timer > $LEDT
echo $TMOFF > $LEDOFF
echo $TMON > $LEDON
exit 0

fi


exit 0

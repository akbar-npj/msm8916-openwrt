#!/bin/sh
# Minimal udhcpc handler for the late-STA interface.
#
# Deliberately does NOT install a default route: on these dongles the LTE link
# (wwan0) is the default path, and a second default route via the upstream AP
# would silently steal traffic.  Only the address and the connected route are
# configured; add a route yourself if you want to use the STA as an uplink.
#
# udhcpc passes: $1 = deconfig|bound|renew|leasefail|nak, $interface, and for
# bound/renew also $ip, $mask (prefix length) and $subnet (dotted netmask).

case "$1" in
deconfig)
	ip -4 addr flush dev "$interface" 2>/dev/null
	;;
bound|renew)
	ip -4 addr flush dev "$interface" 2>/dev/null
	ip -4 addr add "${ip}/${mask:-24}" dev "$interface"
	logger -t wifi-sta "dhcp: ${ip}/${mask:-24} on $interface"
	;;
leasefail|nak)
	logger -t wifi-sta "dhcp: $1 on $interface"
	;;
esac

exit 0

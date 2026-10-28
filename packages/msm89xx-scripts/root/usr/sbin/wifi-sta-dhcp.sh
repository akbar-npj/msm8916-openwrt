#!/bin/sh
# udhcpc handler for the late-STA interface (phy0-sta0).
#
# On a lease it installs the full uplink: the address, a default route at metric
# 100 (so the modem, at metric 50, keeps priority while it has a route of its
# own -- the station is a fallback, not a replacement) and the upstream DNS.
# It records the lease for uplink-manager, which signals dnsmasq and refines
# policy (the metric-40 override when the STA must beat an unhealthy modem).
# On deconfig it tears the route, the DNS and the lease down.  The manager is
# signalled (USR1) so policy is re-evaluated immediately.

signal_mgr() {
	kill -USR1 "$(cat /var/run/uplink-manager.pid 2>/dev/null)" 2>/dev/null
}

write_dns() {
	# $1 = DHCP-provided DNS servers.  Prefer the configured STA DNS
	# (uplink.general.sta_dns, default 8.8.8.8 8.8.4.4); fall back to $1 so the
	# preference survives even before uplink-manager runs.
	local servers
	servers=$(uci -q get uplink.general.sta_dns 2>/dev/null)
	[ -n "$servers" ] || servers="$1"
	uci -q del dhcp.@dnsmasq[0].server
	[ -n "$servers" ] || { uci commit dhcp; /etc/init.d/dnsmasq reload 2>/dev/null; return 0; }
	for s in $servers; do
		uci add_list dhcp.@dnsmasq[0].server="$s"
	done
	uci commit dhcp
	/etc/init.d/dnsmasq reload 2>/dev/null
}

case "$1" in
deconfig)
	ip -4 addr flush dev "$interface" 2>/dev/null
	ip route del default metric 100 dev "$interface" 2>/dev/null
	rm -f /tmp/wifi-sta-lease
	write_dns ""
	signal_mgr
	;;
bound|renew)
	ip -4 addr flush dev "$interface" 2>/dev/null
	ip -4 addr add "${ip}/${mask:-24}" dev "$interface"
	# Baseline candidate route: modem (metric 50) still wins while present.
	ip route replace default via "$router" dev "$interface" metric 100
	# Record the lease (including DNS) for uplink-manager.
	{
		echo "interface=$interface"
		echo "ip=$ip"
		echo "mask=$mask"
		echo "router=$router"
		echo "dns=$dns"
		echo "ts=$(date +%s)"
	} > /tmp/wifi-sta-lease
	write_dns "$dns"
	signal_mgr
	logger -t wifi-sta "dhcp: ${ip}/${mask:-24}, route+DNS via $router"
	;;
leasefail|nak)
	logger -t wifi-sta "dhcp: $1 on $interface"
	signal_mgr
	;;
esac
exit 0

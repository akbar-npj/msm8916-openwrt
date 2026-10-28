# Dual-uplink failover (modem + Wi-Fi station)

The HMU05 / HMUF02 boards have two paths to the internet:

- LTE modem -- wwan0, driven by ModemManager (network.modem). netifd owns its
  layer-3 and installs the default route.
- Wi-Fi station -- phy0-sta0, brought up late by wifi-sta-late / wifi-sta-up
  (Station Mode). netifd is deliberately kept away from this vif: a netifd-managed
  station iface makes netifd restart the radio, which crashes the WCNSS firmware.

uplink-manager picks one of these as the active default route with health probes and a
user policy. It never touches netifd's modem configuration and never restarts the radio
-- it only adds/removes an override route and points dnsmasq at the active link's DNS.

## Route ladder

Lower metric wins. The manager maintains three metrics:

    Metric  Owner              Meaning
    40      uplink-manager     STA override -- installed only when the STA is active
    50      netifd + manager   modem default route (manager normalises it to 50)
    100     wifi-sta-dhcp.sh   STA candidate -- fallback, modem wins while present

Result:
- Modem healthy, STA down -> modem (50) is the only route.
- Modem healthy, STA up -> modem (50) still wins; STA is a standby at 100.
- Modem unhealthy, STA healthy -> STA override (40) wins.
- Both down -> active=none, no default route, no internet.

If uplink-manager dies entirely, the netifd modem route (50) and the STA candidate (100)
still give modem-first service with no override -- the board degrades gracefully.

## Policy

Set in "uci set uplink.general.policy=..." (or Network -> Station Mode -> Uplink in
LuCI):

- auto (default) -- modem wins while it is healthy; the station takes over only when
  the modem has no route or fails its health probe.
- modem -- modem only; the station is never selected even if the modem is down.
- sta -- station preferred; falls back to the modem if the station is unhealthy.

## Health probes

Each link is probed with "ping -I <device> <probe_target>" every probe_interval
seconds (default 30s, target 1.1.1.1). A link is marked down after fail_threshold
consecutive probe failures (default 3) and up again after recover_threshold consecutive
successes (default 2). Changing the config and saving the LuCI page (or "kill -USR1" on
the manager) re-evaluates immediately.

## DNS

When the station is the active link, dnsmasq is pointed at uplink.general.sta_dns
(default 8.8.8.8 8.8.4.4, preferred over the AP's DNS). When the modem is active the
explicit server list is cleared so dnsmasq uses the modem's upstream resolver. The
station DHCP script also writes sta_dns on a lease, so the preference survives even
before uplink-manager runs.

## Files

msm89xx-scripts:
- /usr/sbin/uplink-manager -- the manager loop.
- /etc/init.d/uplink-manager -- procd service (START=97, respawn).
- /etc/config/uplink -- policy and probe tuning.
- /usr/sbin/wifi-sta-dhcp.sh -- on lease installs address + route (metric 100) + DNS
  and records /tmp/wifi-sta-lease; on deconfig tears them down and signals the manager.
- /usr/sbin/wifi-sta-up -- apply_firewall() binds phy0-sta0 to the wan zone (firewall
  layer only; netifd never sees the vif).
- /etc/uci-defaults/95-wifi-sta-late -- enables the manager service on first boot.

luci-app-wifi-sta:
- htdocs/luci-static/resources/view/wifi-sta/uplink.js -- Network -> Station Mode ->
  Uplink page: live status (active link, per-link state/device/gateway/DNS) plus the
  policy form. Saving restarts uplink-manager.
- menu.d / acl.d -- the Uplink menu entry and the rpcd ACL granting uplink uci access,
  /tmp/uplink.json read, and /etc/init.d/uplink-manager exec.

## Live status

/tmp/uplink.json is rewritten every probe cycle:

    {
      "active": "sta",
      "policy": "auto",
      "modem": { "present": 0, "healthy": 0, "device": "", "gateway": "" },
      "sta":   { "present": 1, "healthy": 1, "device": "phy0-sta0", "gateway": "10.0.0.1", "dns": "8.8.8.8 8.8.4.4" }
    }

## What happens when the station goes down

wifi-sta-late stop (or a DHCP deconfig / lost association) removes the station address.
wifi-sta-dhcp.sh deletes the route + DNS + lease and signals the manager. The manager's
read_sta only trusts the lease while phy0-sta0 still carries an address, so a lingering
lease after an unclean udhcpc exit can never be mistaken for a live link. With the
station gone and the modem healthy, the modem route (50) becomes the sole path and
traffic flows automatically. If the modem is also down, active=none and there is no
internet. No radio restart, no firmware crash.

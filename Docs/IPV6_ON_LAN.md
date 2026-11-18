# IPv6 on the LAN — what is advertised and when

**Status:** documented limitation (decision on #113: no firmware change) ·
**Files:** none — this describes stock OpenWrt behaviour plus the modem proto

## TL;DR

The LAN bridge advertises an IPv6 prefix by router advertisement, and on many
carriers that prefix **cannot be routed back**. Clients configure a global IPv6
address, prefer it over IPv4, and stall before falling back. Android marks the
network "connected, no internet" and stops auto-reconnecting.

The prefix is not something this port adds. `network.lan.ip6assign='60'` is the
stock OpenWrt default, generated at first boot because the kernel has IPv6
enabled. The port sets no IPv6 option anywhere.

Workaround, on the device:

```sh
uci set dhcp.lan.ra='disabled'
uci set dhcp.lan.dhcpv6='disabled'
uci set dhcp.lan.ndp='disabled'
uci commit dhcp
/etc/init.d/odhcpd restart
```

That stops the advertisement and leaves the router's own IPv6 alone. See
[Workaround](#workaround) for the full recipe and
[Re-enabling](#re-enabling-it) if your carrier does delegate a routable prefix.

---

## The symptom

From a client behind the bridge, IPv6 to the internet fails. From the device
itself it works:

```
# ping6 -I br-lan 240e:4b::88
ping6: sendto: Network unreachable
# ping6 240e:4b::88                    (from the device)
64 bytes from 240e:4b::88: ...
```

`br-lan` holds a **global** address in the carrier's prefix, clients get one by
SLAAC, and none of them can reach anything.

## Why it happens

Three independent pieces line up:

1. **`ip6assign='60'` on `lan`.** `openwrt/package/base-files/files/bin/config_generate`
   sets it on every static interface whenever `/proc/sys/net/ipv6` exists:

   ```sh
   [ -e /proc/sys/net/ipv6 ] && uci set network.$1.ip6assign='60'
   ```

   With no upstream prefix, netifd carves the /60 out of the ULA /48
   (`network.globals.ula_prefix`), so `br-lan` gets `fd..::1/60` and clients
   SLAAC a ULA. That on its own is harmless — see
   [The ULA is not the problem](#the-ula-is-not-the-problem).

2. **`dhcp.lan.ra='server'`** (also stock) advertises whatever prefix netifd
   assigned, so clients configure it.

3. **The modem proto declares the bearer's own /64 as assignable.** In
   `feeds/packages/net/modemmanager/files/lib/netifd/proto/modemmanager.sh`,
   the static IPv6 method does:

   ```sh
   proto_add_ipv6_address "${address}" "128"
   proto_add_ipv6_prefix "${address}/${prefix}"     # ← the whole /64
   ```

   and the DHCPv6 method adds the dynamic interface with
   `extendprefix 1` (RFC 7278). Both tell netifd "there is a prefix here you may
   hand to downstream interfaces", so `ip6assign` on `lan` carves a /64 out of
   it. But the bearer's address is a single /128 on the radio link: the carrier
   routes the /128, not the /64. Nothing else routes the prefix either — there
   is no NAT66 and no prefix delegation.

Which of these you hit depends on the carrier. 3GPP networks are supposed to
route the whole /64 assigned to the UE, and on those the arrangement works
exactly as intended. Carriers that route only the /128 (China Telecom mobile is
the one in the report) produce the broken state above.

Every APN entry in `packages/qcom-carrier-autocfg/files/apns.tsv` requests
`ipv4v6`, so the bearer asks for IPv6 on every carrier — this is the default
path, not an opt-in.

### The ULA is not the problem

A ULA (`fd00::/8`) is not globally routable, and clients — Android included —
do not treat it as internet-reachable. Every unit already advertises a ULA on
`br-lan` and that has never caused the symptom. The problem is only the
**global** prefix.

You can see this on a unit with no modem attached:

```
# ip -6 addr show br-lan
    inet6 fd4b:4cc6:400e::1/60 scope global noprefixroute
    inet6 fe80::.../64 scope link proto kernel_ll
# ip -6 neigh show dev br-lan
    fd4b:4cc6:400e::a79 lladdr ... STALE        ← a client, SLAAC'd from the RA
```

## Why this is not fixed in the firmware

Changing it changes user-visible behaviour for **every** bearer, and a carrier
that does delegate a routable prefix has to keep working unchanged. The port
also cannot tell the two cases apart from configuration alone: the bearer hands
netifd a prefix in both of them. Detecting "the carrier does not actually route
this" needs a reachability probe at runtime, which is a new daemon and a new
class of failure for a problem that has a one-line workaround.

The three candidate fixes and why they were rejected are in #113. The decision
was to document the limitation and keep the workaround here.

## Workaround

### Stop advertising (recommended)

The router keeps its own IPv6; clients stop being told to use it:

```sh
uci set dhcp.lan.ra='disabled'
uci set dhcp.lan.dhcpv6='disabled'
uci set dhcp.lan.ndp='disabled'
uci commit dhcp
/etc/init.d/odhcpd restart
```

Clients drop their IPv6 address within a lease/RA lifetime (or immediately on
reconnect) and use IPv4 only. This is what turns Android's "must tap to
reconnect every time" into "reconnects by itself".

### Also drop the global address from the bridge

The reporter's full recipe, if you want `br-lan` to hold no global prefix at
all:

```sh
uci set network.lan.ipv6='0'
uci commit network
ifup lan
```

This is stronger and also removes the device's own IPv6. It is only needed if
you care about the address on `br-lan` itself rather than about what clients are
told.

### Persisting across sysupgrade

`/etc/config/dhcp` and `/etc/config/network` are configuration files, so both
survive `sysupgrade`. Nothing else to do.

## Re-enabling it

If your carrier does route the prefix it hands out, turn the advertisement back
on:

```sh
uci set network.lan.ipv6='1'
uci set network.lan.ip6assign='60'
uci set dhcp.lan.ra='server'
uci set dhcp.lan.ra_slaac='1'
uci set dhcp.lan.ra_flags='managed-config' 'other-config'
uci set dhcp.lan.dhcpv6='server'
uci commit
ifup lan
/etc/init.d/odhcpd restart
```

### How to tell whether your prefix is routable

From a LAN client:

```sh
ping6 2606:4700:4700::1111      # Cloudflare, IPv6
ping  1.1.1.1                   # IPv4 control
```

IPv4 working while IPv6 fails means the prefix is advertised but not routed.
On the device, a `ping6 -I br-lan <global>` that prints *Network unreachable*
means there is no source address in a usable scope; a plain timeout means the
prefix is present but the path is not.

## See also

* [`Docs/ARCHITECTURE.md`](ARCHITECTURE.md) — how the modem bearer is plumbed
* [FAQ & Troubleshooting](https://github.com/akbar-npj/msm8916-openwrt/wiki/FAQ-and-Troubleshooting) — the short version

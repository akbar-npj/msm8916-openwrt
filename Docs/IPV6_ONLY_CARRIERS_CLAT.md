# IPv6-only carriers and the CLAT (464XLAT)

Some mobile networks hand the modem an **IPv6-only** default bearer and expect
the device to reach IPv4 through a **NAT64** gateway. That is 464XLAT
(RFC 6877), and it is what every VoLTE-capable phone does. A phone hides this
from you: its CLAT gives the phone an IPv4 address, so "IPv4 works" even
though the radio link carries only IPv6.

A router has no CLAT by default. If the bearer is IPv6-only, the router gets
only IPv6, and every IPv4-only client behind it has no internet at all — the
LAN looks connected, DNS may even answer over IPv6, but nothing IPv4 works.

This is not carrier-specific code: any network that answers a dual-stack
request with an IPv6-only bearer behaves this way, and several do.

## How to tell

On the unit:

```sh
ip -br addr show wwan0
```

Only a `2400:...` address and no `192.168.x.x` / `10.x` — that is the
symptom. Confirm that the network really does offer NAT64 by asking the
carrier's DNS64 for the well-known probe name:

```sh
nslookup -type=AAAA ipv4only.arpa
```

```
Name:   ipv4only.arpa
Address: 64:ff9b::c000:aa
Address: 64:ff9b::c000:ab
```

`64:ff9b::/96` (or a network-specific prefix) in the answer means NAT64 is
there and a CLAT will work. No AAAA answer means the network has no IPv4 path
at all and nothing on the device can fix that.

## What the firmware does

Every msm89xx image ships `464xlat` and `kmod-nat46`. On boot a uci-defaults
script creates:

```
config interface 'clat'
	option proto '464xlat'
	option tunlink 'modem'
	option zone 'wan'
	option auto '0'
```

It is kept down (`auto 0`) and a hotplug handler
(`/etc/hotplug.d/iface/40-clat`) starts it **only when the modem interface
came up without an IPv4 address**. On a dual-stack carrier the CLAT never
starts, so the native IPv4 path is untouched.

The 464xlat proto finds the NAT64 prefix itself by resolving `ipv4only.arpa`
through the carrier's DNS64, so nothing is hardcoded and a network-specific
prefix works too.

The CLAT installs its default route at **metric 2048**. A native default route
is metric 10, so even if the CLAT is running it can never shadow native IPv4.

## Verifying

```sh
ifstatus clat
ip -4 route            # default dev 464-clat ... metric 2048
```

Then from a client behind the router:

```sh
ping -c3 1.1.1.1
wget -O /dev/null http://ipv4.google.com/
```

The unit itself can be checked the same way; with the CLAT up it has IPv4 of
its own.

## Turning it off

```sh
uci set network.clat.disabled='1'
uci commit network
ifdown clat
```

Or delete the interface; the uci-defaults only recreates it on a factory reset
(`uci-defaults` scripts do not re-run on an existing config).

## Notes and limits

- **NAT64 is not dual-stack.** IPv4 throughput goes through the operator's
  translator; latency and MTU are whatever the gateway provides. Nothing to
  tune on our side.
- **ICMP works through the CLAT** in practice (the nat46 module translates
  it), but not every NAT64 gateway does this. Treat `ping` as a hint, not
  proof; an HTTP fetch is the real test.
- **Inbound IPv4 is impossible** on an IPv6-only bearer: there is no public
  IPv4 address to forward to. Same as a phone on such a network.
- **Check what the modem actually asks for before blaming the network.** A
  refused IPv4 request (`pdn-ipv4-call-disallowed`) means the network will not
  give IPv4 for that PDN, but the same network can hand IPv4 to a handset and
  an IPv6-only bearer to a data module. Verify the APN and the modem's default
  profile first; only then is the CLAT the answer.

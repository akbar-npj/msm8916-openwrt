# Concurrent AP+STA and the Station Mode LuCI app

*Status: shipped and verified on the HMUF02-V5 (2026-10-24 / 2026-10-25).*

The WCN36xx radio can act as an access point **and** as a client of another
access point at the same time.  This document is the user-facing guide to that
feature — the reverse-engineering log that established *why* it has to work this
way is in [`WCN36XX_AP_STA_RE.md`](WCN36XX_AP_STA_RE.md).

---

## 1. What you get

- The radio keeps beaconing its own AP (clients stay connected) **while** it is
  associated to an upstream network.
- Everything is configured from **Network → Station Mode** in LuCI — two views,
  *Overview* and *Configuration* — or from `/etc/config/wifista`.
- **`/etc/config/wireless` is not touched.**  That is deliberate, not an
  oversight (see §2).

Typical uses: give the box a Wi-Fi uplink while it still serves its own AP, or
reach a phone hotspot as a backup path next to the LTE bearer.

---

## 2. Why the station is not a `wifi-iface`

A second `config wifi-iface` with `option mode 'sta'` looks like the obvious
way to do this, and it does work — until the phy restarts.  netifd's Wi-Fi script
restarts the phy on **every** wireless-config change, including at boot, and a
**phy restart while a managed interface is configured on that phy kills the
WCNSS firmware**:

```
boot_exception_c_handlers.c:108: Data Abort Exception at 0x8b6b301c
```

That is not an association problem — it reproduces with a scan-only station
(no matching SSID at all) and even with the station interface never being
brought up, as long as it exists when the phy restarts.  Because netifd restarts
the phy on every wireless save, "just add a STA interface in LuCI" can never be
safe.

So the station is created **after** the AP is up and running, with `iw`:

```
iw phy phy0 interface add phy0-sta0 type managed addr 00:11:22:33:44:77
ip link set phy0-sta0 up
wpa_supplicant -D nl80211 -i phy0-sta0 -c ...
```

It is also kept out of netifd: netifd has no device object for a vif it did not
create, so `option device 'phy0-sta0'` fails with `NO_DEVICE`.  `udhcpc` is run
directly instead.

---

## 3. Quick start (LuCI)

1. **Network → Station Mode → Configuration**
2. Tick **Enable station mode**
3. **Upstream SSID** — *optional*: type it, or leave it empty and press **Scan**
   and use *Use* on a row (the station stays idle until an SSID is set, so the
   form can be saved first and filled in later)
4. **Password** (leave empty for an open network)
5. **Station MAC address** — must differ from the AP's MAC (the default
   `00:11:22:33:44:77` is fine)
6. **Save & Apply**

The service restarts immediately; the station is normally associated within a
few seconds.  **Overview** shows the live state, refreshed every 5 s.

### The same thing from the shell

```sh
uci set wifista.main.enabled=1
uci set wifista.main.ssid='MyHomeWiFi'
uci set wifista.main.key='secret'
uci commit wifista
/etc/init.d/wifi-sta-late restart

# state, machine-readable
/usr/sbin/wifi-sta-up status
```

---

## 4. Configuration reference (`/etc/config/wifista`)

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `0` | Master switch.  Off by default — the service exits immediately. |
| `ssid` | *(empty)* | Upstream network to join.  **Optional** — with it empty the station simply stays idle (logged once, then retried every 5 min), so the form can be saved before the network is known; pick one from the scan and Save & Apply afterwards. |
| `key` | *(empty)* | WPA/WPA2/WPA3 passphrase.  Empty = open network. |
| `macaddr` | `00:11:22:33:44:77` | Address for the station vif.  **Must differ from the AP's** — mac80211 clones the AP's address onto a new vif, and `ip link set up` then fails with a misleading `RTNETLINK answers: No error information`. |
| `dhcp` | `1` | Run `udhcpc`.  The lease installs **only** the address — no default route, so the LTE/mobile path stays the default. |
| `hostname` | *(empty)* | Name announced to the upstream network as DHCP option 12 (`udhcpc -x hostname:…`), so the upstream router can resolve this device by name.  Empty = the system hostname (`system.@system[0].hostname`, else the kernel's). |
| `radio` | `radio0` | Which radio's channel follows the upstream. |
| `recover_after` | `60` | Seconds the station may stay down before the watchdog recycles it. `0` disables the check. |
| `wait_ap_timeout` | `60` | How long the service waits for hostapd to report `ENABLED` before giving up (it retries on the next start). |

---

## 5. What the service actually does

`/etc/init.d/wifi-sta-late` (`START=95`) supervises **three procd instances**,
all with `respawn 3600 5 0` so a dead helper is always restarted:

| Instance | Command | Job |
|---|---|---|
| `sta` | `wifi-sta-up sta` | wait for the AP, create the vif, then run `wpa_supplicant` **in the foreground** |
| `dhcp` | `wifi-sta-up dhcp` | wait for `Connected`, then run `udhcpc` in the foreground |
| `watchdog` | `wifi-sta-up watchdog` | poll every 15 s, recycle a station that stays down |

`wpa_supplicant` runs in the foreground on purpose: with `-B` it daemonises to
PPID 1 and procd can no longer respawn it.

### Channel handling — one radio, one channel

The station has **no channel of its own**: it must sit on the same channel as
the AP it is associated to, and the AP is the one that has to move.  At
bring-up the service therefore:

1. creates the vif and `ip link set up`s it (a DOWN netdev cannot scan),
2. scans for the configured SSID and reads its channel,
3. if it differs from the AP's channel: **deletes the vif first**, sets
   `wireless.<radio>.channel`, runs `wifi reload`, waits for the AP to be
   `ENABLED` again, re-creates the vif,
4. then starts `wpa_supplicant`.

A mismatch is otherwise silent and confusing: `SME: Authentication request to
the driver failed` in a growing back-off loop, and no lease — but no crash.

The AP's own clients see a brief drop when the channel changes (no CSA is sent).

### Watchdog

Every 15 s: if the station has been down for `recover_after` seconds, scan and
compare channels.

- **mismatch** → log, delete the vif, kill `wpa_supplicant`/`udhcpc`; procd
  respawns `sta`/`dhcp`, which re-run the channel sync.
- **no mismatch** (upstream absent, wrong key) → leave it to `wpa_supplicant`;
  logged once, then one in five cycles.
- **vif gone** → recycle unconditionally.

Deleting the vif does **not** make `wpa_supplicant` exit (pid unchanged, procd
never respawns), so the watchdog kills it explicitly.

---

## 6. The LuCI app (`luci-app-wifi-sta`)

**Network → Station Mode** (order 30, after *Wireless*).  Ships on every board
via `DEFAULT_PACKAGES` (`msm89xx/Makefile`), so every image carries it.

### Overview

- status polled every 5 s from `wifi-sta-up status`
- AP: interface, hostapd state, channel, client count
- STA: interface, connected/not, SSID, BSSID, channel, signal, tx bitrate, address
- warnings: channel mismatch, and **"a station interface is still configured in
  the wireless config"** — that one is the crash risk from §2
- buttons: **Restart service**, **Stop**, **Start**, **Show log**

The status contract is **one `key=value` per line, nothing else**.  Every value
has CR/LF folded to spaces before it is printed (an SSID may legally contain a
newline, and a raw one would otherwise inject a second key).  A **missing** key
means "unknown / not applicable" and the page renders a dash — `sta_signal` and
`sta_bitrate` are absent when the station is not connected, and the whole
`sta_*` block is absent when there is no station interface at all.

Parsing notes, because `iw` output is not stable across versions: the SSID is
taken with `sed 's/^\s*SSID: //'` rather than `awk -F': '` (an SSID may itself
contain `": "`), and the signal/bitrate fields are located by label rather than
by position (iw can print `signal: -55 [-60, -58] dBm`, and older builds put
`tx bitrate:` on the same line as `signal:`).

### Configuration

The whole `wifista` section (table in §4) plus a **Scan** button that runs
`iwinfo <ap_iface> scan` and can copy an SSID into the field.  **Save & Apply**
commits `wifista` and restarts the service — a changed SSID or key takes effect
without a reboot.

The app writes **only** `/etc/config/wifista`.

The scan is a pure read-only operation — a netlink hardware scan on the AP
interface, never on the station vif and never touching the wireless config (no
phy restart, no `wifi reload`).  It only runs when the status output actually
reports an AP interface; otherwise the button explains that Wi-Fi is not up
instead of exec'ing against a guessed name.  Verified on-device 2026-10-25:
`iwinfo phy0-ap0 scan` — directly and through rpcd `file exec` (the exact LuCI
path) — is inert with the service disabled, with an orphan managed vif present,
and with the AP interface down (it just returns a netlink error); no firmware
error, no restart, uptime unaffected.  A device reboot seen around a scan click
on 2026-10-25 was the unrelated cold power loss documented in the modem notes.

### Permissions

`/usr/share/rpcd/acl.d/luci-app-wifi-sta.json` grants:

- read: uci `wifista` + `wireless`; exec `/usr/sbin/wifi-sta-up`,
  `/etc/init.d/wifi-sta-late`, `/usr/sbin/iw`, `/usr/bin/iwinfo`, `/sbin/logread`
- write: uci `wifista`; exec `/usr/sbin/wifi-sta-up` and the init script

**Verified with a restricted login.**  Root has `read '*' / write '*'` in
`/etc/config/rpcd`, so a root session bypasses ACLs entirely and proves nothing —
the check has to run as a login that holds *only* the `luci-app-wifi-sta` group.
A temporary such login was created, exercised, and removed:

| Call (as the restricted login) | Result |
|---|---|
| `exec /usr/sbin/wifi-sta-up status` | allowed |
| `exec /etc/init.d/wifi-sta-late running` | allowed |
| `exec /usr/bin/iwinfo phy0-ap0 scan` | allowed |
| `exec /sbin/logread -e wifi-sta` | allowed |
| `exec /bin/sh -c id` | **denied** |
| `read /etc/shadow` | **denied** |
| `read /etc/config/wireless` | **denied** |
| `uci get wifista` | allowed |
| `uci set wifista` | allowed |
| `uci apply` | allowed — commits, and the service restarts |
| `uci get wireless` (radio list) | allowed, read-only |
| `uci set wireless` | **denied** |
| `uci get network` | **denied** |

End-to-end through uhttpd as that login:
`/cgi-bin/luci/admin/network/wifi-sta/{overview,config}` → **200**, while
`/admin/network/wireless` and `/admin/status/overview` → **403**.  The menu
`depends.acl` gate works as well, since the routes resolve.

> **Note on the ACL shape.**  Every other app in the tree lists
> `"ubus": { "file": [ "exec" ] }` next to the file paths, but rpcd enforces the
> `file` and `uci` objects through their own scopes: with this app's ACL (which
> has no `ubus.file` entry) `session access` reports `ubus file.exec` = *false*
> while the actual `file exec` call succeeds.  So the entry is not required —
> it is only the answer `session access` gives.  Nothing in the page consults
> that answer.

---

## 7. Verification record

| Date | Check | Result |
|---|---|---|
| 2026-10-24 | Two vifs active at once (AP serving a phone while associated upstream) | both carrying traffic, 0 crashes |
| 2026-10-24 | Boot bring-up on the flashed image | vif created, associated in 2 s, lease, `crashes=0` |
| 2026-10-24 | 10-minute soak | 10/10 clean, rtt 45–108 ms |
| 2026-10-24 | Channel sync (AP forced to ch1, upstream ch6) | AP moved to ch6, connected 4 s later |
| 2026-10-24 | Watchdog (simulated upstream move) | detected at 60 s, recovered in ~40 s, 0 crashes |
| 2026-10-24 | `stop` → `wifi reload` → `start` | 0 crashes |
| 2026-10-25 | LuCI app on the flashed image | both views HTTP 200 |
| 2026-10-25 | ACL, exercised as a **restricted** login (root bypasses ACLs) | every call the page makes is allowed; `/bin/sh` exec, `/etc/shadow`, `uci set wireless` denied; restricted user gets 200 on both views and 403 on Wireless/Status |
| 2026-10-25 | Save & Apply path (`uci set` RPC → `uci apply`) | config committed, `sta` instance restarted in <1 s, re-associated, same lease |

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No **Station Mode** entry in the menu | package not in the image | `apk info -e luci-app-wifi-sta`; it is a `DEFAULT_PACKAGES` entry — check `msm89xx/Makefile` |
| Overview shows *not connected*, log repeats `SME: Authentication request to the driver failed` | the upstream is on a different channel and was not found by the scan | check the upstream is in range (`iwinfo phy0-ap0 scan`); the service re-syncs on the next restart |
| `RTNETLINK answers: No error information` on `ip link set up` | vif got the AP's MAC | set `wifista.main.macaddr` to a distinct address |
| `ctrl_iface exists and seems to be in use` | a stale `wpa_supplicant` | `/etc/init.d/wifi-sta-late restart` (the service removes the stale socket) |
| Station connects, no address | upstream has no DHCP, or `dhcp=0` | enable DHCP upstream / set `dhcp=1` |
| AP's clients drop for a second at start-up | the AP followed the upstream's channel | expected; only when the channels differed |
| AP reboots / modem drops when saving **Wireless** settings | a phy restart while the STA vif exists — the bug in §2 | stop the service, save, start it again (§9) |

---

## 9. Caveats

- **`wifi reload` (or saving anything under Network → Wireless) with the station
  up still crashes the firmware.**  Use:

  ```sh
  /etc/init.d/wifi-sta-late stop
  wifi reload
  /etc/init.d/wifi-sta-late start
  ```

  The service's own channel sync does exactly this internally (it deletes the
  vif before reloading).
- **Single channel.**  AP and station share one radio, so the AP must sit on the
  upstream's channel.  There is no second channel to put the AP on.
- **No default route from the lease.**  The station gets an address and can be
  reached on its subnet, but the mobile bearer stays the default path.
- The station is not visible to netifd/LuCI *Wireless* — that is intentional.

---

## 10. Files

| Path | Role |
|---|---|
| `msm89xx/base-files/etc/config/wifista` | UCI config (default disabled) |
| `msm89xx/base-files/usr/sbin/wifi-sta-up` | the whole service: `sta` / `dhcp` / `watchdog` / `status` |
| `msm89xx/base-files/usr/sbin/wifi-sta-dhcp.sh` | udhcpc handler — address only, never a default route |
| `msm89xx/base-files/etc/init.d/wifi-sta-late` | procd supervisor + `config.change` reload trigger |
| `msm89xx/base-files/etc/uci-defaults/95-wifi-sta-late` | enables the init script on first boot |
| `packages/luci-app-wifi-sta/` | the LuCI app (menu, ACL, `overview.js`, `config.js`) |
| `openwrt-overlay/package/kernel/mac80211/patches/ath/452-*` | mac80211: advertise `{AP}<=1,{managed}<=1` |
| `openwrt-overlay/package/kernel/mac80211/patches/ath/458-*` | wcn36xx: suppress BMPS in concurrency |
| `Docs/WiFi/WCN36XX_AP_STA_RE.md` | the experiments behind all of the above |

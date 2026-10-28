# Release Notes

> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

> This file is the body of every GitHub release (`body_path: RELEASE_NOTES.md` in
> the CI workflow). It keeps the **latest 5** release entries, newest first. Older
> entries live in the wiki and in this file's git history.

---

### Packages — dual-uplink failover (msm89xx-scripts r4, luci-app-wifi-sta r8)

*A health-checked choice between the LTE modem and the Wi-Fi station, with a user policy and no netifd/radio churn.*

**Why** — The station fallback used to install an address only and never became a real second uplink; the modem stayed the only default route and a dead station left the LAN stranded. uplink-manager now arbitrates the default route between wwan0 (modem) and phy0-sta0 (station) with ping health probes and a policy (auto / modem / sta), and points dnsmasq at the active link (station DNS defaults to 8.8.8.8 8.8.4.4).

**How it stays safe** — netifd still owns the modem and never touches phy0-sta0; the manager only adds/removes a metric-40 STA override and a metric-100 STA candidate, leaving the modem at 50. If the manager dies, modem-first service survives. A lingering DHCP lease after an unclean udhcpc exit is ignored unless the vif still carries an address, so a stale lease can never be read as a live link. No radio restart, so the WCNSS firmware never crashes.

**LuCI** — Network to Station Mode to Uplink shows live per-link state and lets you set the policy; saving restarts the manager.

See Docs/WiFi/UPLINK_FAILOVER.md for the route ladder, probe tuning and the STA-down behaviour.

---

### v25.12.6-r2 (2026-10-28) — built from `main`

*An update you can see, a theme you can pick, and LEDs that only light for something you can act on.*

**Why this release** — r1 works, but it cannot tell you that it is out of date, and its LEDs still announce states some boards do not have. r2 adds an **"updates available" notice to the dashboard**, gives **Feelgood three palettes** (dark, light, colourful) with a switch in the header, and reworks the LED cues so each board shows only what it can actually do. It also removes the "the package is installed but the service never starts" trap that a config-preserving flash leaves behind, and adds the tooling to push a single package to the live feed without rebuilding firmware.

**🔔 The dashboard tells you when there is an update (new)** — `luci-app-update-notify` runs `apk update` in the background every 6 h (retrying after 15 min on failure, so a dongle whose bearer is still coming up at boot does not wait a whole cycle) and puts **"N package updates are available"** on the dashboard with a link to the package manager. It renders **nothing** while the device is current. Two details that make the number trustworthy: it counts only `apk list --upgradable --world`, so the version-pinned `base-files`, `kernel` and `libc` are never advertised as installable; and it de-duplicates across repositories, because these devices carry the same package in the stock OpenWrt feed and in our own distfeeds (66 raw lines, 39 real updates on mifib-v12). The check is deliberately *not* in the page: the dashboard re-runs its loader every 5 s, and an `apk update` per poll would both hammer the feed and block the page on a dead uplink.

**🎨 Feelgood: three palettes, a sidebar that stays put (new)** — The theme is no longer one dark skin. `data-theme-style` on `<html>` selects **dark** (default), **light** (cream canvas, warm ink) or **colourful** (violet canvas, cyan accent), set before first paint so no page — the login screen included — flashes the wrong palette. The switch sits in the header and stores its choice under its own key, separate from the horizontal/vertical layout, so changing one never resets the other. Colour now carries meaning rather than decoration: five token sets (ok / warn / error / info / idle) drive every status surface, and a palette may only move lightness and saturation — red stays fault, amber stays degradation. Structurally, the vertical sidebar opens a section in a panel **beside** it instead of expanding in place, so the tabs below never move; **Network → Interfaces** becomes a compact list (identity left, one value per line in a fixed label column, actions right) at one type size per line. Also fixed: the sidebar scrollbar clamped its offset to `[0, max]` after every re-measure — an unclamped negative offset made it emit `translate(-0px, --53px)`, which the browser rejects, freezing the menu with no scrollbar to bring it back.

**💡 LED cues now match the board** — On the battery boards (`mifib-v12`, `mf800b`) the second LED pair reports **charging and faults only**; the modem and Wi-Fi cues live on the lamps that exist there, and the boot blink no longer encodes capacity — a level cannot be read off a blink, and LuCI reports it properly. The battery lamp names differ per board (`green:bat`/`red:bat` vs `green:charging`/`red:charging`), so they are resolved rather than hardcoded. `battery-led` also treats **"Not charging"** as charge complete, so a full pack stops looking like a fault. The rescue and flash states are now distinct: failsafe fast-blinks **both** Wi-Fi LEDs at 40/40 (the same cue the USB gadget gives once the rescue shell is up), and a `sysupgrade` in progress slow-blinks `green:wlan` alone at 500/500, so a flash cannot be mistaken for a rescue prompt and a rescue cannot be mistaken for a battery problem.

**🧹 A keep-config flash no longer leaves a stale package database (fix)** — Flashing with `--keep-config` keeps the overlay, and the overlay wins over the image. Three artefacts then make a freshly shipped package look broken: **whiteouts** for paths that moved between packages hide the new image's files (the service silently never starts), the previous image's `/lib/apk/db/installed` makes `apk info -W` lie and `apk add` hit file conflicts, and the previous image's `/etc/apk/world` makes apk treat a package the new image added as unwanted. `apk-overlay-heal` now runs from preinit **once per image change** and repairs all three: it drops whiteouts whose path the image ships, merges the world, and merges the database with the image's while keeping packages installed from the feed. It never deletes the database — apk cannot rebuild it from the filesystem — and it deliberately does nothing on an ordinary boot or an ordinary `apk add`, because a whiteout is also how a legitimate `apk del` removes a file.

**📦 Fixes reach a device without a 34 MB flash (new)** — The LED monitors and the station helpers moved out of `base-files` into [`msm89xx-scripts`](https://github.com/akbar-npj/msm8916-openwrt/tree/main/packages/msm89xx-scripts), so an LED or repeater fix is now a package. A new workflow, `publish-package.yml`, rebuilds one package, splices it into the already-published `releases/25.12.6` feed and re-signs the index, so the fix arrives with `apk upgrade`. Boot-critical pieces (`/etc/diag.sh`, the preinit hooks, `lib/batteryless.sh`) deliberately stay in the image.

**⬆️ Upgrading** — From r1: `sysupgrade` (keep config) is fine and preserves the overlay; the first boot after the flash runs the overlay heal automatically. The theme and the update notice are ordinary packages and also reach an r1 device through the feed with `apk upgrade` once r2's feed is published — the LED cues and the heal live in the image, so those need the flash.

**🧪 Tested on mifib-v12** — The theme (1-r24) and the update notice were built here, installed on a running r1 device and verified end to end: `apk add` enables and starts the service, a check writes `{"count":39,…}` in 8 s, the read the page performs returns it, and the notice renders nothing at zero. The **mf800b** `charging` LED pair compiles from the same tree but has **not** been run on hardware — if you have one, please flash it and report what the pair does.

**⚠️ Known issues** — `sysupgrade -n` wipes the overlay and the modem firmware (re-dump from the stock image); STA+AP Wi-Fi can crash the WCNSS firmware; the update notice needs an uplink, so it stays silent until its first successful check.

---

### v25.12.6-r1 (2026-10-26) — built from `main`

*A rescue image that can actually rescue, BBR by default, HTTPS everywhere — and a modem bearer that can no longer latch itself dead.*

**Why this release** — This is the largest release since the port began. Three
changes alter what the device can survive. The **recovery partition now holds a
real rescue system**: it boots the board's own production kernel, brings up USB
**and** a Wi-Fi AP, and serves a web UI that can reflash the rootfs, write a
single partition or drop to EDL — so a dongle with a broken overlay no longer
needs a cable or a teardown. The **modem bearer can no longer latch itself
dead** after a transient connect failure, which used to leave a device with a
registered modem and no data until a manual `ifup`. And **BBR replaces CUBIC**
as the default congestion control, so a bulk download no longer fills the base
station's buffer and wrecks interactive latency. Around those: HTTPS is on by
default, Station Mode grew multi-network profiles and wrong-password reporting,
the Wi-Fi firmware panic on vif teardown is fixed, and the **Feelgood** theme
ships as the default UI with a login page that finally renders correctly.
Underneath, the tree moves to OpenWrt **v25.12.6** (Linux **6.12.112**,
mac80211/backports **6.18.55**).

**🆘 A real rescue image in the recovery partition (new)** — On the three boards
with the 17-partition layout (`hmuf02v5`, `mf800b`, `mifib-v12`) the
`recovery` partition now carries a self-contained rescue system instead of a
bare initramfs. It packs **the board's own production kernel**, lifted out of
the sysupgrade `boot.img`, rather than building a second kernel from
`config-6.12-recovery` — that second kernel was the whole class of bugs: it had
devtmpfs and `wcn36xx` built in, while the kernel the device actually runs has
`CONFIG_DEVTMPFS` off and the wireless stack as modules, so the rescue image
could drift from the system it was rescuing. `/init` now falls back to
`tmpfs` + `mdev -s` and rescans once the gadget and eMMC exist, loads the
wireless modules in dependency order, and creates the AP interface itself
(`iw phy phy0 interface add wlan0 type __ap` — the backports `mac80211` makes
no default netdev). Verified on HMUF02-V5: 11.4 MiB into p17, the USB gadget
enumerates, DHCP leases `192.168.10.9`, SSH and the web UI answer, all 21
`mmcblk` nodes exist, and `wlan0` beacons `OpenWrt-Recovery` on channel 6 in
**802.11n with WMM** — 39 Mbit/s RX from a phone, against ~2 Mbit/s of
throughput when hostapd was left at 802.11g. The AP is **DHCP-only**: no router and no DNS in the lease, so
joining it does not hijack the client's default route. AP+STA was dropped from
the rescue image on purpose (two vifs, a second daemon and the whole `-EBUSY`
scan-wedge family for a benefit the AP already gives); the code is in history at
`a43efba`. Runbook: [`msm89xx/recovery/README.md`](msm89xx/recovery/README.md).

**🛟 Failsafe no longer hijacks the host's internet (fix)** — Failsafe's DHCP
server advertised `192.168.10.1` as both default router and resolver. Failsafe
has no WAN, so a host that took that lease replaced its default route with a
dead link the moment the dongle was plugged in. The lease is now address +
prefix only (`--port=0`, no option 3, no option 6): the host keeps its own
route and DNS and still reaches `192.168.10.1` over the subnet route. Same fix
as the rescue image's `dnsmasq.conf`.

**🚦 BBR is the default TCP congestion control (new)** — CUBIC's core assumption
is that packet loss means congestion. On a cellular link it is false: loss comes
from fading, handovers and the ~10 % block error rate that LTE's link adaptation
aims for, and an eNodeB buffers hundreds of milliseconds. A loss-based sender
fills that buffer *before* it sees the loss that tells it to stop, so a download
pins RTT at several hundred milliseconds and everything interactive stalls.
BBR measures the delivery rate and the minimum RTT instead and paces to that
model, keeping the pipe full with no standing queue. `tcp_bbr` was already
preinstalled and autoloaded; what changed is that `/etc/sysctl.d/10-bbr.conf`
now selects it at boot (`/etc/init.d/boot` loads the module at `START=10`,
`/etc/init.d/sysctl` applies the sysctl at `START=11`). The qdisc stays
`fq_codel` — BBR has paced internally since 4.20. Revert per boot with
`sysctl -w net.ipv4.tcp_congestion_control=cubic`, or permanently by removing
that file. Full rationale, limits and an A/B latency recipe:
[`Docs/BBR_TCP_CONGESTION_CONTROL.md`](Docs/BBR_TCP_CONGESTION_CONTROL.md).

**📶 A radio restart no longer kills Station Mode (new)** — Station Mode is
deliberately *not* a `wifi-iface`; it is created by a service after the AP is
up, because a phy restart with a managed interface present crashes the WCNSS
firmware (`Data Abort Exception at 0x8b6b301c`). The catch was that netifd knew
nothing about it, so *Save & Apply* restarted the phy underneath a live station
and took the AP down with it. `/usr/sbin/wifi-sta-guard` now brackets the
driver: `setup()` runs `down` before the phy is touched and `up` once it is
configured, `teardown()` runs `down` only (the matching setup restores it, and
a teardown with no setup after it — radio disabled, reboot — is exactly when the
station must stay down). `down` stops the service, kills any surviving
supplicant, deletes `phy0-sta0` and waits until both are really gone; `up`
restarts it only if `down` actually stopped something, so boot and no-op reloads
do not start the station early and the service's own channel-change reload
cannot kill itself. Both verbs are no-ops when Station Mode is disabled. Only
radio resets that bypass netifd (a raw `iw dev del`, `rmmod wcn36xx`) still
need the manual sequence.

**🔁 Station Mode: several networks, and a wrong password is reported (new)** —
Station mode now saves any number of networks (`wifista.ap` sections) instead
of one SSID/key pair: the generated `wpa_supplicant.conf` gets one network
block per enabled profile, and the supplicant joins whichever is in range,
moving on when a password is rejected. A legacy `wifista.main.ssid/key` pair
still works and is migrated into a row when the LuCI page loads. Wrong
passwords are no longer silent: `wpa_cli` (a new `wpaevent` procd instance)
attaches to the control socket and flags the SSID on
`CTRL-EVENT-SSID-TEMP-DISABLED reason=WRONG_KEY`, cleared per SSID on connect —
stock `wpa_cli` never dispatches that event, so hostapd overlay patch 900
forwards it. `wifi-sta-up status` prints `profiles=` and `wrong_key=`, and the
Overview page shows a red **Wrong password** alert naming the network. The
channel sync and the watchdog now scan every saved network, first in range
wins. Configuration lives in the new **Network → Station Mode** app
(`luci-app-wifi-sta`): status polled every 5 s, channel-mismatch and
"station still in `/etc/config/wireless`" warnings, restart/stop/start/log
buttons, and an `iwinfo` scan with a *Use* button — so nothing has to be put
into `/etc/config/wireless` by hand. Guide:
[`Docs/WiFi/AP_STA_AND_LUCI_APP.md`](Docs/WiFi/AP_STA_AND_LUCI_APP.md).

**⌨️ Station Mode: the SSID and password boxes are always there (fix)** — The
saved-networks section used the CBI-era `add`/`remove` option names, which
`form.TypedSection` silently ignores, so the page rendered with **no *Add
network* button and no Delete button at all**. A device with no saved profile
therefore had no field to type into, and the scan's *Use* button could only
answer *"add a network row first"* — the network was visible but could never be
saved. The section now uses `addremove`/`addbtntitle`, and the form always
keeps **one empty SSID/password row at the end**: type into it and press *Save &
Apply*, or press *Scan* → *Use* and the row is filled with the cursor already in
its password field. A password is checked (8–63 characters, or 64 hex digits)
before it is applied, a password typed without an SSID is rejected, and deleting
a row works again. Note that a browser which cached the previous view keeps
showing the old page — hard-reload once after upgrading.

**🌐 Station Mode can carry the internet when the modem cannot (fix)** — The
station used to be address-only: its DHCP client deliberately installed **no
default route** ("the LTE link must stay the default"), so a device with no SIM
had a connected Wi-Fi link and no internet at all. The station's L3 is now owned
by the netifd interface `stawan`: it takes the address, a **default route with
metric 100** and the upstream **DNS**, and the firewall zone already masquerades
it. The modem keeps priority while it has a route, so the station is a
*fallback* uplink — it takes over when the mobile link is down and steps aside
when it comes back. Two new options in **Network → Station Mode →
Configuration**: *Use this link as an internet uplink* (on by default) and
*Uplink route metric* (100). This also removes the second DHCP client that was
competing with the first for the same address, whose lease renewal used to flush
the address the route pointed at.

**🔇 No SIM now means a dark modem LED (change)** — With a SIM missing the
modem can never register, so `modem-led-monitor` used to sit in its
"searching" state and blink red forever — indistinguishable from "no coverage".
A confirmed `sim-missing` from ModemManager now switches the modem LEDs
**off**, red and blue, and leaves the rest of the panel logic untouched: red
solid still means the modem subsystem is down, red blink still means "up and
searching", green still means registered. Inserting a SIM brings the normal
indication back on the next poll. A failed `mmcli` query never produces the
dark state — it keeps the previous behaviour, so a broken query cannot hide a
broken modem. Applies on **every board**: it drives the signal pair, which has
nothing to do with the battery lamps.

**🔴 The failsafe window is visible (new)** — On a headless dongle the first
7 s of boot — the window in which one short press of the Restart button enters
failsafe — looked exactly like any other boot. The status LED now **fast-blinks
(100/100 ms) for those 7 s**, then goes solid as before. A preinit hook
(`05_failsafe_window_led`) starts the blink before the button is polled, so the
cue covers the whole window, and it is deliberately slower than the 40/40 blink
shown once failsafe is actually entered — so "press now" and "you are in" are
distinguishable. **Batteryless boards only** — on `mifib-v12` / `mf800b` the
battery pair already blinks "booting" from `diag.sh`, and two LEDs blinking at
boot for two different reasons is worse than one.

**📶 AP+STA gets its own Wi-Fi indication (new)** — When the radio is serving
its own access point **and** associated upstream (repeater mode), the Wi-Fi LED
now toggles **very fast (100/100 ms)** instead of the 250 ms "a client joined the
AP" blink. The states stay distinct: green solid = AP ready, green 250 ms = AP
with clients, green 100 ms = AP + station both active, red = AP missing.
"Both active" means the station has an address, not merely that an association
was attempted — wcn36xx reports the link ~100 ms before the 4-way handshake
finishes, so a station with a stale key would otherwise blink the LED every
minute. Applies on every board (the Wi-Fi lamp, not the battery pair).

**🔋 The battery lamps now mean charging and faults only (change)** — The
battery pair used to encode capacity: green solid above 15 %, red solid below,
red blink while low and charging. A level cannot be read off a blink, and the
colour depended on a threshold nobody remembers, so the pair now reports only
**what the charger is doing**: green slow-blink = charging, green solid =
charge complete, red fast-blink = the gauge is present but unreadable (a real
fault), both off = discharging normally. Capacity, runtime estimate and charge
limit stay in LuCI (Status → Overview and the Battery page). The boot blink in
`diag.sh` no longer colour-codes capacity either — it is a plain "booting"
blink.

**🔧 mf800b's battery lamps work at all now (fix)** — `battery-led-monitor`
had `green:bat`/`red:bat` hardcoded, which is mifib-v12's naming; mf800b calls
the pair `green:charging`/`red:charging`, so on that board the monitor found
nothing and the low-battery indication simply did not exist. The names are now
resolved per board. mf800b binds `red:charging` to the kernel trigger
`pm8916-bms-vm-charging`; the monitor takes the lamp over (`trigger=none`) and
the green half carries "charging" instead, so both boards behave the same.

**🆘 Failsafe and a flash no longer look identical (fix)** — `diag.sh` used the
same 100/100 blink on the Wi-Fi pair for both `failsafe` and `upgrade`, so a
rescue prompt and a firmware write were indistinguishable. Failsafe is now
40/40 on every red LED — matching the cue `35_failsafe_usb` gives once the
rescue shell is up — and a flash is a calm 500/500 on **green:wlan only**.
`35_failsafe_usb` also stopped grabbing every red LED: on a battery board that
included `red:bat`, so entering failsafe lit the battery fault colour.

**📡 Station Mode never runs on `channel='auto'` (fix)** — A lost scan on
wcn36xx leaves the phy answering every later scan with `-EBUSY` until the next
boot. With `wireless.radio0.channel='auto'` that is not just a failed station:
hostapd's ACS needs a scan too, so the AP comes up with no channel and no SSID
and the whole radio is dead until a reboot. `ensure_fixed_channel()` now runs
before the station vif is created: if the channel is already fixed it does
nothing; if the AP is beaconing it adopts that channel; if the AP has no channel
it pins 6 and reloads; if the radio is down it pins 6 and brings it up.

**🩹 Wi-Fi: NULL dereference in the RX survey path (fix, patch 459)** —
`wcn36xx_update_survey()` dereferenced `wiphy->bands[band]` unchecked. On this
2 GHz-only part the 5 GHz band is never registered, and `wcn36xx_rx_skb()`
feeds it `hw->conf.chandef.chan->band` unchecked; during a vif/phy teardown
(AP+STA channel sync, service restart) that pointer is stale or zeroed, so the
band reads as unregistered and `sband->channels` panics from IRQ context.
Observed as an Oops at `wcn36xx_rx_skb+0x168` and a reboot, right after a
vif-delete storm. Survey is best-effort telemetry, so the update is now skipped
when the band is not registered.

**🌐 The modem interface can no longer latch itself dead (fix)** — A failed
`--simple-connect` called `proto_block_restart()`, which brings the interface
down *and clears its autostart flag*. Nothing re-drives it after that: netifd
only auto-configures a down interface when `available && autostart`,
ModemManager-monitor only re-arms `available`, and `modem-bearer-watchdog`
gates its re-drive on `autostart=true` so it respects an intentional `ifdown`.
The result was a permanently dead `up=false autostart=false` interface until a
manual `ifup` — measured on HMUF02-V5 (cold-boot attach timeout; the interface
latched dead while the modem itself attached seconds later; bearer down for over
an hour) and on UZ801 V3. The proto now reports `MM_CONNECT_FAILED` and returns
1, so netifd's own proto task retries with `autostart` intact, and the
watchdog's `MM_CONNECT_FAILED` gate can fire. Cherry-picked from **PR #19**
(only the proto change; the PR's watchdog and firstboot edits were not taken —
`force_connection=1` keeps its legacy `MM_INTERFACE_RESTART` behaviour, and
setting it disables the watchdog backstop).

**🔐 HTTPS on by default (new)** — `uhttpd` already shipped `listen_https` on
`:443`; what a stock image was missing is the certificate, without which the
init silently skips those listeners. `px5g-mbedtls` is now in every board's
diffconfig (16 KB; it only needs `libmbedtls21`, which
`libustream-mbedtls` already pulls) and `uhttpd` generates a per-device
self-signed certificate on first start. `redirect_https` is set from a
`uci-defaults` script rather than by shipping `/etc/config/uhttpd` in the
target base-files, which apk refuses as an ownership conflict — and the script
guards on `px5g` and `libustream-ssl` being present, because `uhttpd` passes
`-q` regardless and would otherwise redirect plain HTTP to a port that never
opens. HTTP still works; see [`Docs/HTTPS.md`](Docs/HTTPS.md).

**♻️ Reboot to EDL and Reboot to Recovery in the System menu (new)** — Both
entries sit next to the stock *Reboot*. The commands already existed
(`/sbin/reboot-edl`, `/sbin/reboot-recovery` — symlinks to `reboot-mode`,
which picks its target from its own name and quiesces the services before
handing the reboot reason to the bootloader). The page renders before the
command runs and hides the button when the tool is missing rather than offering
a no-op. A "loop in gettable" crash in the reboot-mode controller is fixed at
the same time.

**🎨 Feelgood is the default theme, and its login page renders correctly (new)**
— The theme is vendored into the image (`luci-theme-feelgood`) and selected by
a `uci-defaults` script that **forces** `luci.main.mediaurlbase` on first boot
— the previous attempt only set it when absent, but `luci-base` already ships
the bootstrap value, so the theme was never actually switched on a fresh flash.
(The script does not run on an apk upgrade, so a manual choice survives.) Two
real bugs went with it. In horizontal-nav mode at ≤992 px the hamburger opened
the mobile drawer while the sidebar was `display:none`, applying
`overflow:hidden` to `.main-right` over an invisible drawer — tapping a leaf
link navigated without clearing the lock and page scrolling stayed frozen; the
drawer is now visible at that width and a guard releases the lock on any real
navigation. And the login page's sidebar `LoginIcon.png` rendered at its
natural **1016×1016** and covered the form: LuCI's CSS minifier (`csstidy`,
`LUCI_MINIFY_CSS=1`) mangles the cascade so `body.login .main-left{display:none}`
and `.main-left .brand-logo{width:28px}` stop applying. The package now ships
the unminified CSS (`LUCI_MINIFY_CSS:=0`), the icon rule carries explicit
`width/height/max-*`, and `cascade.css?v=2` is hardcoded in both
`header.htm` and `header.ut` (LuCI 25.x renders the login page from the ucode
template) so a cached copy cannot be served.

**🔑 The feed key refreshes itself without a reboot (new)** — The boot-time
one-shot left a gap: a device that gets its connection *after* boot (WWAN attach,
station associate, WAN cable) kept a stale feed signing key until the next
reboot, and an interactive `apk update` on an old image still failed with
`UNTRUSTED signature`. Three callers now cover it — a hotplug script on any
interface up, an apk commit hook, and an `apk()` shell function for interactive
shells that refreshes before `update/add/upgrade/fix`. All three are
best-effort by contract: they never fail or block the apk command, and
`apk-key-refresh` now prints every message to stderr as well as syslog and
names the remedy (`apk-key-refresh && apk update`). Wrapping `/usr/bin/apk`
itself was deliberately not done — an apk-tools upgrade writes its own binary
over an overlay wrapper and would silently remove it.

**🔋 New board, battery stack, power button, and a cooler DDR clock** — The
**HM-MIFIB-V12** joins the target (DTS patch 853, `diffconfigs/mifib-v12`,
ramoops, named LEDs, 4-bar battery, SIM hot-swap) — seven boards now. It shares
a battery stack with the MF800B: mainline `pm8916_bms_vm` / `pm8916_lbc`
patches 854–859 report real capacity, battery temperature (on demand), and an
honest charge status, with an 85 % charge limit and a `luci-app-battery` page.
A `pwrkey-monitor` daemon bridges the PMIC key to `rc.button`: double-press
reboots, long-press powers off, and a long press on VBUS reboots instead of
powering off. A `led-boot` service gives early boot LED feedback, and the board
`diag.sh` blinks the failsafe colour. On the thermal side, **patch 860**
(`icc_sync_state`) plus `opp-peak-kBps = <3200000>` at every CPU OPP drops the
DDR clock to **400 MHz — exact Android parity** (it was 8528000 on two OPPs) and
idle settles at **40.6 °C**, at or below the Android band; **patch 861** moves
the Wi-Fi and PMIC cooling maps into the shared `msm8916-ufi.dtsi` so all seven
boards get them. **Tailscale** now ships on every board.

**⬆️ OpenWrt v25.12.6 / Linux 6.12.112** — The backports bump from 6.18.26 to
6.18.55 broke the wcn36xx series: patch 453 no longer applied. It was rebased
against the pristine 6.18.55 tree, and four hardening patches (454–457: HAL
response bounds, print-reg-info truncation, trigger-BA length, TX-ack timer
shutdown) are now upstream and were deleted. The remaining six (450–453, 458,
459) replay with zero rejects, and 459 — the RX-survey NULL dereference panic
above — is unchanged and still carried. The 25.12.6 userland comes from
upstream OpenWrt feeds via `customfeeds`, with the persistent signing key
injected at build time.

**🔧 Build & release plumbing** — `build.sh` no longer silently ignores a new or
edited file in `openwrt-patches/` or `openwrt-overlay/` on an incremental build:
`.builder-state` records a content hash of both trees (`TREE_SHA`) and the fast
path is refused when it no longer matches. Package selection was consolidated:
the 112 upstream userspace packages and the universal set moved into
`DEFAULT_PACKAGES`, five dead `=m` lines were stripped, and a guard now checks
the diffconfig invariants. Two apk problems were fixed — file-ownership
conflicts at package/install, and a doubled `distfeeds` URL that made
`apk update` fetch the wrong path. The CI feed is now published to
`releases/<base>` **and** `releases/<version>`: devices read the base path
(`CONFIG_VERSION_REPO` has no `-rN` suffix), and previously only the versioned
path was written, so a fresh release served a 404 feed.

**🧪 Tested on HMUF02-V5** — `sysupgrade -o` from `25.12.5-r6`, then
`sysupgrade -n` for the theme fix, then verified live: `ubus call system board`
reports `25.12.6-r1` / kernel `6.12.112`; `/proc/sys/net/ipv4/tcp_congestion_control`
= `bbr` with `tcp_bbr` loaded and in use; the guard is present with its hooks in
the installed (ucode) driver; a forced phy restart
(`uci set wireless.radio0.channel=6; wifi reload`) produced **0 firmware
crashes**, the syslog pair `station stopped for a phy restart` → `phy restarted;
restarting the station`, a recycled `wpa_supplicant` PID, and the AP and station
both back with the same lease; the Station Mode page returns 200 on both views
with all five ACL execs passing through a real session; the Feelgood login page
hides the sidebar and draws a 68 px icon; the rescue image was booted from p17
(USB, DHCP, SSH, web UI, 802.11n AP) and the p17 hash read back from flash after
`drop_caches`. Modem: LTE attached, `wwan0` up with a default route, 0 crashes.
The other six boards compile from the same tree and are built by CI; they have
**not** been flashed with this release.

**⬆️ Upgrading** — A normal config-preserving `sysupgrade` (or `sysupgrade -n`) is fine.
The 44-file modem firmware set in `/lib/firmware` lives on the overlay and is not
in any `keep.d` list, so the flash drops it — but the first boot re-dumps it from
the modem partitions: `msm-firmware-dumper` (`START=95`) looks for
`/lib/firmware/DUMPED` and, when it is missing, re-extracts the set and restarts
the remoteprocs. Both paths work. **HTTPS** is enabled on first boot with a
freshly generated certificate, so the browser will warn once; HTTP still works.
On `hmuf02v5`, `mf800b` and `mifib-v12` the rescue image is a separate
artifact — build it with `./build-recovery.sh <board>` (it needs the board's
blobs, so CI cannot build it) and flash p17, or use
`flash.sh --mode repartition` on an older 15-partition install.

**📄 Full changeset** — `git log v25.12.5-r6..v25.12.6-r1` — the rescue image and
recovery partition, the bearer-latch fix, BBR, HTTPS, the reboot-mode menu, the
Station Mode guard/profiles/app, the wcn36xx RX-survey fix, the DDR 400 MHz
parity and shared cooling maps, the HM-MIFIB-V12 board and battery stack, the
Feelgood theme, and the build/feed work.

---

### v25.12.5-r6 (2026-10-19) — built from `main`

*Swap the SIM without rebooting — and the board finally carries its real name.*

**Why this release** — Two long-standing annoyances are gone. Replacing a SIM card used to require a reboot (or silently did nothing, because the modem never noticed the new card); it is now automatic in about **15 seconds**, even when the new card is from a different carrier. And the HMU05 target — the board this project is validated on — now uses the name printed on the silkscreen, **HMUF02-V5**, with images that report their own release version instead of the stock OpenWrt one.

**📲 Physical SIM hot-swap (new)** — These sticks have no AP-side card-detect line, and the baseband firmware is built with hot-swap **disabled**: it notices a card being *removed* but never notices one being *inserted*. The new `sim-hotplug` service closes that gap — it polls UIM at a low rate while a card is present (60 s idle, 5 s while searching), and when the card is believed absent it **pulses the `sim_hotplug` line** and re-checks every 6 s. On `absent → present` it waits for registration, forces ModemManager to re-read the SIM (`mmcli -d` + `-e`) so a different card's IMSI/APN reaches `qcom-carrier-autocfg`, then brings the bearer back up. Measured end to end: **~15 s** for a ~4 s swap (was ~35 s). `searching` alone never triggers anything, so a train or a cell reselection is not mistaken for a swap. QMI hygiene is preserved (`--device-open-proxy`, one short-lived client at a time, always under `timeout`) — no `ClientIdsExhausted`.

**🏷️ HMU05 → HMUF02-V5** — DTS, device profile, diffconfig, patch filenames and CI globs now say `hmuf02v5`. Backwards compatibility is intact: `hmu05,250605v0s` remains in `SUPPORTED_DEVICES` (an older image can sysupgrade to this one) and the on-device board globs match both names. `uf02` is a separate board and is unchanged.

**🔢 Images now identify themselves** — the `version` file (`25.12.5-r6`) is the single source of truth, propagated by `sync-version.sh` into every diffconfig with `CONFIG_VERSIONOPT=y`. `ubus call system board` and `/etc/os-release` report the project version, and firmware filenames carry the `msm8916-openwrt-` prefix.

**👥 Board maintainers** — new `CODEOWNERS` and `MAINTAINERS.md` route UZ801 and UFI003 to their board maintainer; everything else stays with the lead.

**ℹ️ `--help` everywhere** — every custom on-device program now answers `-h/--help` with its options, the `uci` keys it honours and the `logread` tag to watch. 18 of the 20 previously printed nothing or a bare usage line — including `diag-bind`, `diag-bind-watch`, `diag_nv`, `diag_efs`, `diag_logtool`, `modem-bearer-watchdog` and `sim-hotplug`. `diag_nv` moved its static binary to `/usr/libexec/diag_nv.bin` behind a wrapper.

**🧪 Tested on HMUF02-V5 only** — `sysupgrade -n`, boot, modem attach + data, a physical card swap (~15 s, new IP, 0 % loss) and `--help` on every tool were verified live. The other boards (`ufi001b`, `ufi003`, `uz801`, `uf02`, `mf800b`) compile from the same tree but have **not** been rebuilt or flashed with this branch yet — reports welcome.

**⬆️ Upgrading** — a config-preserving `sysupgrade` from an older HMU05 image is accepted and keeps the overlay. `/etc/config/sim-hotplug` is new and receives its defaults on first boot. A swap shorter than the modem's own removal debounce is invisible by nature; `/etc/init.d/sim-hotplug rescan` forces a re-read.

**📄 Full changeset** — [`Docs/HMUF02V5_BRANCH_CHANGESET.md`](Docs/HMUF02V5_BRANCH_CHANGESET.md).

---

### v25.12.5-r5 (2026-10-16) — built from `main`

*A modem-watchdog update: automatic in-vehicle handover profile + a web UI.*

**Why this release** — The headline is **automatic mobility**: the modem bearer watchdog now detects when the device is moving and switches to a handover-safe stall profile on its own, so a dongle used in a car no longer drops to "no internet" for a full minute at every cell change. A **LuCI web UI** for the watchdog ships alongside it (enabled on all five board images).

**🛰️ Automatic mobility profile (new)** — With `auto_mobile` (on by default) the watchdog boots in the **stationary** profile and switches to the handover-safe **30/5** profile the moment a genuine (forward) serving-cell change is seen, reverting to stationary after `mobile_grace_secs` (180 s) of stillness — and vice versa. A boundary ping-pong between two cells is debounced so a parked device never flaps into the mobile profile. The serving cell is read over ModemManager D-Bus (`mmcli`), reusing MM's QMI client, so it never exhausts QMI ClientIds the way a `qmicli` poll loop does. A manual `mobile_optimized` force mode (always 30/5) is also available, with `cell_poll_secs` (15 s) tuning.

**🖥️ `luci-app-modem-watchdog` (new package)** — A LuCI web UI for the watchdog (**Services → Modem Watchdog**): status banner, enable/disable, the `auto_mobile` toggle + `mobile_grace_secs`/`cell_poll_secs` tuning, the manual `mobile_optimized` flag, stall-timeout/check-interval, keepalive and recovery-escalation settings. Enabled in the five board diffconfigs.

**📄 Watchdog & mobility guide** — new [`Docs/Modem Stability/MODEM_BEARER_WATCHDOG.md`](Docs/Modem%20Stability/MODEM_BEARER_WATCHDOG.md) explains the `auto_mobile` profile, its config knobs, and how to tune handover behaviour.

**🧪 Tested only on HMU05 — your testing is needed** — The mobility profile and the LuCI app were validated **only on the HMU05** (a ~20-minute city drive, zero spurious stalls). The other boards (`ufi001b`, `uz801` v3, `uf02`, `mf800b`, `ufi003`) compile from the same tree but have **not** been drive-tested on this release. If you run one of them — or any setup different from the default — please flash it and **report back**: which board, what you exercised (boot, Wi-Fi, Bluetooth where an antenna is fitted, modem attach + data, a drive test), and the result. Positive confirmations are as useful as failures — please open an issue.

**⬆️ Upgrading** — From r4: `sysupgrade` (keep config) is fine and preserves the overlay. The new watchdog options default sensibly, so an existing `modem-watchdog` config carries over unchanged.

**⚠️ Known issues** — See r4 below (overlay module shadowing; `sysupgrade -n` wipes the modem firmware; STA+AP Wi-Fi crashes the WCNSS firmware).

---

### v25.12.5-r4 (2026-10-10) — built from `main`

*A maintenance release on top of r3: adds failsafe mode for headless dongles.*

**Why this release** — The headline is **failsafe mode** for headless MSM8916 dongles (no screen, no serial console), plus the HiMI_OK guard extended to **UF02**. Every firmware fix from r3 is included.

**🆘 Failsafe mode (new)** — Hold the button during boot to enter failsafe (7 s window; the LED flashes red→green): the device brings up a USB network gadget (`ncm.usb0` + `acm.GS0`) at **192.168.10.1** with a `ttyGS0` console, so a bricked or misconfigured unit can be recovered without opening it. A longer (5 s) hold performs a **factory reset**. Verified live on HMU05; enabled on all boards, with the button polarity corrected to active-high/pull-down (uz801 falls back to a green LED).

**🛡️ Modem stability** — The HiMI_OK guard is now gated for **UF02** as well (the third confirmed board), and the guard's init ships as an rc.d symlink so it survives `sysupgrade`.

**🧪 Tested only on HMU05 — your testing is needed** — This build has been validated **only on the HMU05**. The failsafe mode was verified live on HMU05; the other boards (`ufi001b`, `uz801` v3, `uf02`, `mf800b`) compile from the same tree but have **not** been tested on this release. If you run one of them — or any HMU05 setup different from the default — please flash it and **report back**: which board, what you exercised (boot, Wi-Fi, Bluetooth where an antenna is fitted, modem attach + data, failsafe), and the result. Positive confirmations are as useful as failures — please open an issue so we can fold findings into the next release.

**⬆️ Upgrading** — From r3: `sysupgrade` (keep config) is fine and preserves the overlay.

**⚠️ Known issues** — Overlay files shadow the image after a config-preserving flash (now repaired automatically, see r2); `sysupgrade -n` wipes the modem firmware; STA+AP Wi-Fi crashes the WCNSS firmware. The original write-ups are in the r3 entry, in this file's git history.

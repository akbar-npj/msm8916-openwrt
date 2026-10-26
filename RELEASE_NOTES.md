# Release Notes

> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

> This file is the body of every GitHub release (`body_path: RELEASE_NOTES.md` in
> the CI workflow). It keeps the **latest 5** release entries, newest first. Older
> entries live in the wiki and in this file's git history.

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
**802.11n with WMM** — 39 Mbit/s to a phone, against ~2 Mbit/s when hostapd was
left at 802.11g. The AP is **DHCP-only**: no router and no DNS in the lease, so
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

**⚠️ Known issues** — See r3 below (overlay module shadowing; `sysupgrade -n` wipes the modem firmware; STA+AP Wi-Fi crashes the WCNSS firmware).

---

### v25.12.5-r3 (2026-10-09) — built from `main` @ `e66d566`

*A clean rebuild of r3 after the CI clone fix.*

**Why this release** — A modem-stability and feature release on top of r2. The headline: the software modem-restart (SSR) lever now actually works (it was silently dead in r2), paired with AP-side fixes that eliminate the recurring ~900 s baseband watchdog crash on HMU05. It also folds in Bluetooth, WPA3, thermal/CPR, and a safer two-in-one flasher.

**🛡️ Modem stability (most important)**
- **SSR restart lever fixed (kernel patch 826).** In r2, writing to `/sys/kernel/debug/msm_subsys/modem` to restart a wedged modem did nothing — a stray duplicate `start` inflated the remoteproc power refcount so `rproc_shutdown` short-circuited. Now `atomic_set(&rproc->power, 1)` is forced before shutdown, so the restart really cycles the modem and the refcount self-heals.
- **bam-dmux re-attach on already-open (kernel patch 850).** After an SSR, the BAM-DMUX data channel re-attaches correctly even when already open, preventing a "Channel already open" / QMI-port-loss stall.
- **A2 power pin moved into the kernel driver (patches 848/849).** The A2 modem-power pin is now held by `qcom_bam_dmux` from `netdev_open` instead of a userspace script. This removes a class of `a2_power.c:1189` crashes and makes the pin reliable across suspend/resume and SSR. (The old `modem-a2-hold` userspace scripts are removed.)
- **HiMI_OK guard hardened (GitHub issue #5).** The root-cause fix for the ~900 s baseband deadline: it rewrites the `HiMI_OK` handshake after every modem boot, gated to HiMI baseband boards, and only writes when NV item 2500 reads all-zeros. DIAG rpmsg access is now serialized and the diag-bind-watch no longer rewrites the status file every 2 s.
- **Corrected A2-parity claim.** Earlier docs described Android parity as `a2_pin=0`; measured behaviour shows Android actually holds the A2 ON (`a2_pin=1`). HMU05 now ships `a2_pin=1` via device-tree, matching Android.

**🔵 Bluetooth (new)** — BlueZ userspace shipped in the image; `kmod-btqca` + `kmod-btqcomsmd` packaged. `bt-ssp-guard` fixes SSP pairing by gating on the NVM LMP-feature bit; bond persists across reboot (verified). `bt-spp` adds an SPP serial server (default-on) + on-demand client with a uci bridge. Bluetooth is not usable on UZ801 v3 because the board has no Bluetooth antenna.

**📶 Wi-Fi (new)** — **WPA3-SAE / 802.11w (MFP)** enabled on wcn36xx (firmware already supported PMF/IGTK; only the driver needed patching). **AP-only interface combinations** allow multiple SSIDs (up to 2 APs) on one radio — do NOT enable STA+AP concurrent mode (it crashes the WCNSS firmware). Wi-Fi thermal mitigation wired up (wcn36xx HAL thermal command + cooling device).

**🌡️ Thermal & power (new)** — **Mainline CPR** CPU-rail voltage scaling ported: the CPU rail now tracks frequency and matches Android's voltage (≈1.15 V → 1.0625 V at load), closing the last meaningful voltage gap. Top CPU OPP capped at 800 MHz; PMIC thermal mitigation added.

**🔧 Flashing & recovery** — **Two-in-one `flash.sh`** (Migrate + Update) with a validated safety backup of all radio/calibration partitions (`fsc fsg modemst1 modemst2 modem persist sec`) — the script aborts before writing if any backup is missing or short. Fixed two bugs that left the modem partition zeroed after a migrate flash (IMEI read as all-zeros → modem attaches but stays detached). Radio/calibration restored *before* boot/rootfs. Wi-Fi fatals no longer miscounted as modem fatals. CI release fixed (the runner's `actions/cache` pre-creating `openwrt/` broke the clone) — this r3 is a clean rebuild from `main`.

**📦 Package feeds & kmods** — **Curated kmod feed**: a hand-picked set of ~109 `=m` kernel modules (USB/network, VPN/IPsec, crypto, filesystems, BT transports) is now compiled into the published feed / offline kmods bundle, so you can `apk add` them without rebuilding. **APK feed signing-key pinning + self-heal**: the stable `public-key.pem` is embedded and `apk-key-refresh` self-heals a stale key at boot/firstboot. `coreutils-stty` and `xxd` ship on every board. Removed the non-working `reboot-fastboot` alias (only `reboot-bootloader` works on this platform).

**📟 Supported devices** — `hmu05`, `ufi001b`, `uz801` (v3), `uf02`, and `mf800b`.

**⬆️ Upgrading** — From r2 (or any OpenWrt): `sysupgrade` (keep config) is fine and preserves the overlay. The old userspace `modem-a2-hold` is gone — the driver handles the A2 pin now; no action needed. After upgrading, verify the loaded `qcom_bam_dmux.ko` is the new one.

**⚠️ Known issues / caveats** — Overlay shadowing: a stale `/overlay/upper/lib/modules/*.ko` survives `sysupgrade` and shadows the new image module (hash the *loaded* file, not `/rom`). `sysupgrade -n` wipes the modem firmware (re-dumped on first boot); keep config unless you intend a clean slate. STA+AP Wi-Fi mode crashes the WCNSS firmware.

---

*Earlier releases — `v25.12.5-r2`, `v25.12.5`, `v25.12.5-rc1` — predate this
release-notes format and carried the build guide as their body. See the
[releases page](https://github.com/akbar-npj/msm8916-openwrt/releases).*

# Release Notes

> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

### v25.12.6-r3 (2026-10-30) — built from `main`

> 🆕 **First release with in-device firmware upgrade** — flash to r3 manually once, then future releases self-upgrade from the page.

- 🆕 **Firmware upgrade (new)** — System → Firmware Upgrade page + `firmware.json` manifest; downloads in the background (rpcd's 30 s timeout no longer kills it), verifies sha256 and runs `sysupgrade` without `-n`. r1/r2 have no page, so flash r3 by hand first.
- 🧩 **Upgrade page (package)** — *System → Firmware Upgrade* ships as `luci-app-firmware-upgrade`; if the menu entry is missing, `apk update && apk add luci-app-firmware-upgrade`. It carries a **Keep settings** box — untick it for a clean `sysupgrade -n`.
- 📶 **Station Mode self-heals (fix)** — a scan the firmware never answers used to wedge the radio (`-EBUSY`) until reboot; now bounded at 15 s with a watchdog that re-probes, then reloads the driver. Also fixes the double-scan-completion WARN and gives the station its own derived MAC; a normal (re)association is no longer mistaken for a wedge.
- 🔋 **Battery (fix, mifib-v12 & mf800b only)** — gauge no longer freezes at 100 % (FIFO latch cleared at probe); voltage scale corrected (293 µV vs 300 µV, was ~2.4 % high); pulling the pack no longer powers the unit off.
- 📡 **Wi-Fi (fix)** — fixed a mac80211 WARN (`rx.c:5354`) where 2.4 GHz scan frames were mis-tagged as 5 GHz.
- 🔀 **Dual-uplink failover (new)** — `uplink-manager` switches the default route between the LTE modem (wwan0) and the Wi-Fi station (phy0-sta0) by ping health, with a policy (auto / modem / sta) and dnsmasq pointed at the active link.
- 🔐 **Security** — backported IPv4 `inet_opt`/`ireq_opt` use-after-free (**CVE-2026-98432**).
- 🧰 **Also** — `luci-app-update-notify` no longer errors after upgrade; `base-files` fails loudly if a flash can't be staged.

**Packages** (`msm89xx-scripts` r7, `luci-app-wifi-sta` r11, `msm89xx-firmware-upgrade` r10, `luci-app-firmware-upgrade` r3):

- In-device upgrade: the *System → Firmware Upgrade* page and the `firmware-upgrade` engine behind it, now with a **Keep settings** toggle (untick for `sysupgrade -n`). Both are in the feed, so `apk update && apk add luci-app-firmware-upgrade` adds the page to an r3 image that predates it.
- Dual-uplink failover manager (modem + Wi-Fi station) with ping health probes and a user policy; netifd still owns the modem, no radio restart.
- Station Mode: multi-network profiles, wrong-password reporting, and the self-heal watchdog.
- LEDs, battery monitor, and firmware-upgrade tooling moved into packages so fixes arrive via `apk upgrade`.
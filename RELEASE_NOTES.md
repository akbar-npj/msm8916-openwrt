> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

## v25.12.6-r5 — Feature highlights

- 🔁 **SIM recovery that actually recovers.** `sim-hotplug` now escalates: when the hot-swap pulse
  cannot bring the SIM back, it restarts the modem — bounded, because the first attempt at this
  wedged the modem itself. A modem parked at `failed` / `sim-missing` is now recovered instead of
  staying dead until a reboot. Closes issue #22.

- 📇 **ModemManager falls back to the UIM card status** when the last unlock attempt reports
  "sim-missing", instead of giving up. This is the UF896 case: the SIM is present, but the first
  probe races the boot. Also: the `modemmanager` proto retries PLMN registration instead of
  blocking on the first failure, and `modem-bearer-watchdog` accepts `MM_INTERFACE_RESTART` in the
  re-drive gate.

- 📶 **UF896: `uf896-modem-online` retries and verifies DMS online mode**, so a slow boot no longer
  leaves the modem offline. The dead SIM-slot helper is gone and the **A2 pin hold is enabled**,
  matching the other HiMI V1.0 boards.

- 📦 **Hotfix channel, end to end.** Each build now publishes a `hotfix.json`, LuCI shows a dashboard
  notice and offers a one-click install, and `firmware-upgrade` gained three fixes: a hotfix no
  longer leaves an ordinary package version-pinned in `/etc/apk/world`; a partial `apk` error is
  accepted when the installed version actually moved (base-files ships `rom/note` into a read-only
  `/rom`, so it always exits 1); and `hotfix apply` now reaches every feed — it previously saw only
  the target feed, so a normal package could not be installed at all.

- 🧹 **`apk-overlay-heal` merges `/etc/apk/world` by package** rather than by line, so an upgraded
  image cannot leave duplicate or lost world entries behind.

- 🔍 **`apk-key-refresh` no longer warns about a kernel the feed still offers.** It compared against
  whichever `kernel-*` line `apk list -a` printed first — index order, not the version the device
  actually runs — and so warned about perfectly healthy units.

- 🆘 **Recovery images are frozen and attached to every release**, with a guard that refuses to pair
  a production kernel with modules from another build. `lk2nd` now honours fastboot reboot reasons
  (recovery / EDL), and `flash.sh --mode backup` restores the radio even if the EDL session drops.

## ⚠️ Upgrade note

The kernel configuration is **unchanged** since r4, so the vermagic is the same and existing
`kmod-*` packages keep working. The root filesystem did change substantially, though, so upgrade
with the **full image** (*System → Firmware Upgrade*) rather than a bare `apk upgrade`.

**Boards:** hmuf02v5, mf800b, mifib-v12, uf02, uf896, ufi001b, ufi003, uz801.

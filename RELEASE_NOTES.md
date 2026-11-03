> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

## v25.12.6-r7 — Feature highlights

- 🩹 **r6 is withdrawn — it did not boot.** r6 tried to make the PM8916 RTC writable so `hwclock -w`
  would stop failing. That call runs from `sysfixtime` at `START=00`, the very first init script,
  and the RTC comes out of reset at 1970, so it fires on every boot. Without the property it returns
  `RTC_SET_TIME: No such device` before it reaches the PMIC — harmless. With it, it becomes the
  first code on these boards to write the RTC registers over SPMI, and the boot wedges there: the
  system never reaches userspace, lk2nd's 3-strike counter runs out, and the board lands in the
  recovery partition. `firstboot` cannot help, because the failure happens before it. The patch is
  removed and the DTB is byte-identical to r5 again. **If you are on r6, flash r7.**

- 📊 **Firmware upgrade now shows what it is doing.** The page gave no indication at all while an
  upgrade ran: the console only appeared once the apply log had content, and the download — the
  longest phase, ~8 MB over Wi-Fi — showed nothing, so a working upgrade looked like a dead button.
  There is now a progress bar driven by the real byte count of the image on disk (0–80 %), then
  verify (85 %) and flash (92 %); `sysupgrade` reports nothing finer, so the bar does not pretend
  otherwise, and a failure turns it red. The console appears the moment you press Upgrade, scrolls
  with the log, and survives a page reload.

- 🧪 **Every release ships as a test build first.** A tag build publishes a prerelease and
  `releases/<version>/` only; it no longer touches the shared `releases/<base>/` feed or the root
  `firmware.json`, so an image that turns out to be broken cannot be handed to devices that are
  already running. Promotion is an explicit `promote-release.yml` run that copies the feed, prunes
  superseded kernel and kmod packages, republishes the root manifest and flips the release to
  Latest.

- 🐛 **CI no longer silently drops the LuCI submodule.** `packages/luci-app-cpu-perf` is a git
  submodule and `actions/checkout` does not fetch submodules by default, so CI produced an image
  without it and OpenWrt quietly dropped it from `DEFAULT_PACKAGES`. Both checkouts now pass
  `submodules: recursive`.

## v25.12.6-r6 — Feature highlights

- ⏰ **The RTC still cannot be written, and now we know why it must not be tried.** `sysfixtime`
  runs at `START=00`, the first init script, and calls `hwclock -w` on every boot: the PM8916 RTC
  comes out of reset at 1970 and the image's build date is always later, so the "clock is behind the
  files" branch always fires. Without `allow-set-time` that call returns `RTC_SET_TIME: No such
  device` before it ever reaches the PMIC — harmless. With the property set, it becomes the first
  code on these boards to write the RTC registers over SPMI, and that wedges the boot: the system
  never reaches userspace, lk2nd's 3-strike counter runs out, and the board drops into the recovery
  partition. The property is **not** shipped. The system clock is still set from the image's own
  file timestamps on every boot, which is enough for TLS to work.

- 🔑 **`apk-key-refresh` waits for a usable clock** instead of reporting a TLS error it cannot
  explain. It no longer attempts the fetch before 2020-01-01; it says once that it is deferring,
  rather than failing with `SSL verify error: unknown error` and a misleading "offline?" warning.

- 🔢 **`base-files` carries a release floor.** Its version came from upstream's commit count, which
  does not move between our releases, so every image shipped `1719` while the hotfix channel had
  already served `1720`. `apk` compares numbers, not contents, so re-publishing the same number was
  a silent no-op on the device, and an image below the hotfix line could never supersede it. The
  image now ships `1721`, and the release workflow refuses to build one that is not strictly above
  the published high-water mark.

- 🩹 **`firmware-upgrade` 1.0-r14**: `hotfix apply` passes every feed, not only the target feed —
  a 404 on one `--repository` aborts the whole transaction — and a failed manifest fetch now says
  why instead of returning 1 with no output.

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

The kernel **configuration** is unchanged since r4, so the vermagic is the same and existing
`kmod-*` packages keep working. r6 does change the device tree (the RTC node), and that only
arrives with the image, so upgrade with the **full image** (*System → Firmware Upgrade*) rather
than a bare `apk upgrade`.

**Boards:** hmuf02v5, mf800b, mifib-v12, uf02, uf896, ufi001b, ufi003, uz801.

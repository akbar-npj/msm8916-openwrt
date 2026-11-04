> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

## v25.12.6-r8 — Feature highlights

- 🆕 **New board: `jz02`** — a battery MiFi with three SIM switch lines, a 2100 mAh / 4.35 V pack
  and the USB-gadget LAN. The external slot (3) is the one tested on hardware; slots 1 and 2 are
  internal or unpopulated on that unit. Its stock Android DTB names the LEDs misleadingly, so the
  image uses the mapping measured on the board, not the labels.
  **This board has no failsafe mode**: the reset pinhole is not software-visible — no GPIO
  transition, no PMIC GPIO change, no `pm8941_pwrkey` event, no reboot — so it is a hardware reset.
  A config that will not boot can only be recovered through EDL. Everything else works, including
  battery reporting and the SIM selector (`uci set sim.slot=3`).

- 📶 **A Wi-Fi station that dropped never came back.** A field unit sat down for ~39 minutes with
  its upstream visible in the scan on the right channel: 14 failed 4-way handshakes and 22
  `SSID-TEMP-DISABLED` in 66 minutes, while the watchdog kept concluding "no channel mismatch;
  leaving it to wpa_supplicant" until two `-EBUSY` scans happened to trip a driver reload. The
  generated supplicant config had no `key_mgmt` at all, so wpa_supplicant applied its own default,
  and two other defects kept a dead interface from being re-armed. All three are fixed, and the AKM
  is now chosen from the scan: a WPA3-capable AP gets SAE with PMF, a WPA2-only AP still connects.

- 🔄 **One failed manifest fetch silenced update checks for six hours.** The first minutes after
  boot are exactly when the station has associated but the manifests are not yet readable, and that
  run was counted as a success, so the daemon waited the full 6 h interval. A failed check now
  retries in 15 minutes instead.

- 🩹 **Installing a hotfix from the Firmware Upgrade page looked like a dead button.** No busy
  state, no progress, and the result toast was inserted at the top of the page — off-screen when
  the button at the bottom was pressed — followed by "No fixes pending for this build." A
  successful install was indistinguishable from nothing happening. The page now shows a progress
  bar with the package being installed and a result that stays visible.

- 🐛 **A hotfix could fail once for no reason.** Publishing writes the index and the `.apk` in the
  same commit, so a device reading the feed within a minute sees a version mismatch that is not a
  conflict — it is `apk` saying the only candidate it can see is the one already installed. The
  install now retries that exact failure once.

- 🧰 **The Software page offered a `base-files` update it could never install.** `base-files` is
  version-pinned in `/etc/apk/world`, so `apk upgrade` honours the pin, prints `OK: … in 307
  packages` and changes nothing — while LuCI reports success. The offer came from promotion copying
  the release's `base-files` into the shared feed every revision reads. Promotion no longer
  publishes it; base-files hotfixes still ship through the revision-scoped feed.

- 🧪 **Board support now has a test harness.** `tests/board-profile/run.sh` checks every board on
  every PR — diffconfig ↔ device profile ↔ DTS ↔ DTB registration ↔ `SUPPORTED_DEVICES`, file
  modes, `rc.d` symlinks, patch numbering, and that a change to a shared `base-files` file is gated
  to the board it is for. A `board-build` label builds a contributor's board in CI, and `signoff.sh`
  prints the hardware evidence a board PR has to paste back.
  Also fixed: `device=all` was building seven of eight boards — `uf896` was missing from the
  hardcoded matrix, which is now derived from `diffconfigs/`.

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

# Release Notes

> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

> This file is the body of every GitHub release (`body_path: RELEASE_NOTES.md` in
> the CI workflow). It keeps the **latest 5** release entries, newest first. Older
> entries live in the wiki and in this file's git history.

---

### v25.12.5-r4 (2026-10-10) — built from `main`

*A maintenance release on top of r3: adds failsafe mode for headless dongles.*

**Why this release** — The headline is **failsafe mode** for headless MSM8916 dongles (no screen, no serial console), plus the HiMI_OK guard extended to **UF02**. Every firmware fix from r3 is included.

**🆘 Failsafe mode (new)** — Hold the button during boot to enter failsafe (7 s window; the LED flashes red→green): the device brings up a USB network gadget (`ncm.usb0` + `acm.GS0`) at **192.168.10.1** with a `ttyGS0` console, so a bricked or misconfigured unit can be recovered without opening it. A longer (5 s) hold performs a **factory reset**. Verified live on HMU05; enabled on all boards, with the button polarity corrected to active-high/pull-down (uz801 falls back to a green LED).

**🛡️ Modem stability** — The HiMI_OK guard is now gated for **UF02** as well (the third confirmed board), and the guard's init ships as an rc.d symlink so it survives `sysupgrade`.

**🧪 Tested only on HMU05 — your testing is needed** — This build has been validated **only on the HMU05**. The failsafe mode was verified live on HMU05; the other boards (`ufi001b`, `uz801` v3, `uf02`, `mf800b`) compile from the same tree but have **not** been tested on this release. If you run one of them — or any HMU05 setup different from the default — please flash it and **report back**: which board, what you exercised (boot, Wi-Fi, Bluetooth, modem attach + data, failsafe), and the result. Positive confirmations are as useful as failures — please open an issue so we can fold findings into the next release.

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

**🔵 Bluetooth (new)** — BlueZ userspace shipped in the image; `kmod-btqca` + `kmod-btqcomsmd` packaged. `bt-ssp-guard` fixes SSP pairing by gating on the NVM LMP-feature bit; bond persists across reboot (verified). `bt-spp` adds an SPP serial server (default-on) + on-demand client with a uci bridge.

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

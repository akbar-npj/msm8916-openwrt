# Custom Packages

Beyond the upstream OpenWrt tree, this project ships **26 packages** under
[`packages/`](../../packages/) — the tools, daemons and LuCI apps that make an
MSM8916 USB dongle usable as a headless router with a Qualcomm baseband.

They fall into two groups:

- **Authored in-repo (17)** — written for this project, specific to the MSM8916
  modem/DIAG/Bluetooth/battery hardware.
- **Vendored (9)** — upstream or third-party packages carried in-tree (often with
  local patches) so the firmware build is self-contained.

> Kernel modules are **not** in `packages/`; they live in
> [`msm89xx/modules.mk`](../../msm89xx/modules.mk). See
> [**Kernel-Modules.md**](Kernel-Modules.md).

---

## Authored in-repo

| Package | What it does |
| :--- | :--- |
| [`ats-probe`](../../packages/ats-probe/) | Reads the modem's own **ATS_RTC uptime counter** over QMI TIME (service 22) and stamps each read with the AP's `CLOCK_BOOTTIME`, so a modem-side timestamp can be correlated with an AP-side one. Sends only the read-only `GENOFF_GET 0x0021` — it cannot write to the modem. Used to decide whether the ~902 s fatal is a counter wrap, an always-on clock, or a modem-local condition. |
| [`bt-spp`](../../packages/bt-spp/) | Bluetooth **Serial Port Profile** server and client. `bt-spp serve` bridges incoming SPP connections to a login shell (pty) or a TCP host:port; `bt-spp connect` bridges a remote SPP device to a local shell/TCP listener. Target selected in `/etc/config/bt-spp`. |
| [`bt-ssp-guard`](../../packages/bt-ssp-guard/) | Ensures the WCNSS Bluetooth controller **advertises Secure Simple Pairing**. The SSP LMP feature bit (NVM tag 6, byte 6, bit 3) is RAM-only, so this re-checks each boot and rewrites it when missing — without it, BR/EDR pairing falls back to legacy PIN pairing and fails. |
| [`diag-bind`](../../packages/diag-bind/) | Exposes the modem's SMD DIAG channels as character devices by binding `remoteproc0:smd-edge.DIAG` / `DIAG_CNTL` to the `rpmsg_chrdev` driver (`/dev/rpmsg0` data, `/dev/rpmsg1` control). Ships a one-shot helper **and** a procd service that re-binds whenever the modem subsystem restarts (SSR). |
| [`diag-efs`](../../packages/diag-efs/) | Reads/writes the modem **EFS** through the DIAG EFS2 subsystem (0x13) over `/dev/rpmsg0`. The modem decrypts its own EFS on the fly, exposing the live decrypted NV tree without the per-chip storage key. `list/read/stat/get/dump` (read-only) plus a guarded `put` that refuses calibration paths (`rfnv/`, `mcs/`) without `--force`. |
| [`diag-logtool`](../../packages/diag-logtool/) | Qualcomm **DIAG client** for the SMD DIAG channels (`/dev/rpmsg0`, `/dev/rpmsg1`). Round-trip/raw probes, decode/capture the DIAG stream, and control the log mask via `DIAG_LOG_CONFIG_F` (0x73). |
| [`msm-firmware-dumper`](../../packages/msm-firmware-dumper/) | Dumps the Qualcomm firmware partitions into `/lib/firmware` on first boot. |
| [`qcom-carrier-autocfg`](../../packages/qcom-carrier-autocfg/) | Automatic **SIM detection, carrier-profile matching, APN provisioning** and a dynamic connection manager for the MSM8916 Snapdragon modem. |
| [`qcom-time-daemon`](../../packages/qcom-time-daemon/) | **QMI Time synchronization** daemon: syncs host time with the modem's ATS/TOD and provides periodic keepalives to keep the modem DRX sleep state machine stable. |
| [`reboot-edl`](../../packages/reboot-edl/) | Safely quiesces background services, remounts filesystems read-only, and reboots the device into **EDL (9008), DLOAD (9006), bootloader or recovery**. |
| [`luci-app-battery`](../../packages/luci-app-battery/) | Battery status, **charge limit** and battery LEDs for the two battery boards (mifib-v12, mf800b): a LuCI page with capacity/voltage/temperature/health and the battery session, the 85 %/80 % charge-limit monitor (`charge_enabled` on the PM8916 LBC), the green/red LED monitor, and `battery-ctl`. Installed only where a pack exists. |
| [`msm89xx-scripts`](../../packages/msm89xx-scripts/) | The **userspace half of the board support** as a package: the modem and Wi-Fi LED monitors with their procd services, the boot blink (`led-boot`), and the Wi-Fi station (repeater) helpers that `luci-app-wifi-sta` drives. These used to live in the target's `base-files`, which meant an LED or station fix cost a full firmware flash; as a package they can be pushed through the signed feed (see [Shipping a package without a reflash](#shipping-a-package-without-a-reflash)). Boot-critical pieces deliberately stay in `base-files`: `/etc/diag.sh`, the `/lib/preinit` hooks and `/lib/batteryless.sh`. |
| [`msm89xx-firmware-upgrade`](../../packages/msm89xx-firmware-upgrade/) | The **in-device firmware upgrade engine**. `firmware-upgrade check` reads `firmware.json` — the manifest the release pipeline publishes next to the images — and prints availability as JSON for this board; `download` fetches the image and verifies its SHA-256; `apply [--no-keep]` hands it to `sysupgrade` (keeping the configuration, or wiping it with `-n`); `upgrade` does all three. `apply` detaches with `setsid`, because rpcd's 30 s timeout is shorter than a 35 MB LTE download. |
| [`luci-app-firmware-upgrade`](../../packages/luci-app-firmware-upgrade/) | LuCI page for the above — *System → Firmware Upgrade*. Shows the running version, the published version for this board, the image size and the release notes, then downloads, verifies and flashes, with a **Keep settings** box (default on; untick for a clean install). Ships in `DEFAULT_PACKAGES`, so every board has it from r3 on. |
| [`luci-app-wifi-sta`](../../packages/luci-app-wifi-sta/) | LuCI support for **Station Mode** (concurrent AP+STA): configures the late-brought-up station interface of the wcn36xx radio without touching the wireless config. |
| [`luci-app-modem-watchdog`](../../packages/luci-app-modem-watchdog/) | LuCI web UI for the **modem bearer watchdog** — status banner, enable/disable, the automatic `auto_mobile` handover profile (on by default) plus the manual `mobile_optimized` force mode, stall-timeout/check-interval, keepalive and recovery-escalation settings. |
| [`luci-app-update-notify`](../../packages/luci-app-update-notify/) | Dashboard notice for **available package updates**. A small daemon runs `apk update` every 6 h and writes `/tmp/update-notify.json`; the LuCI dashboard include renders "N package updates are available" with a link to the package manager, and nothing at all when the device is current. Counts the `Upgrading` lines of `apk upgrade --simulate`, so the version-pinned `base-files`/`kernel`/`libc` — and anything else pinned in `/etc/apk/world` — are never advertised. (`apk list --upgradable --world` looked right but is not: `--world` only adds world packages to the selection, so a pinned package still counted.) |

## Vendored

| Package | What it does |
| :--- | :--- |
| [`cups`](../../packages/cups/) | **CUPS** print server (`cupsd`) with IPP, AirPrint, raw socket and web administration on port 631. |
| [`qrtr`](../../packages/qrtr/) | Qualcomm **IPC Router** userspace — `libqrtr` plus `qrtr-utils` (tools and nameservice daemon). |
| [`rmtfs`](../../packages/rmtfs/) | Qualcomm **Remote Filesystem service** daemon — serves the modem's remote-filesystem requests from the AP. |
| [`luci-app-cpu-perf`](../../packages/luci-app-cpu-perf/) | LuCI page for CPU performance information and management. |
| [`luci-app-sms-manager`](../../packages/luci-app-sms-manager/) | LuCI JS interface for **SMS / USSD / AT commands** via ModemManager. |
| [`luci-app-tailscale`](../../packages/luci-app-tailscale/) | LuCI interface for **Tailscale**. |
| [`luci-app-usb-gadget`](../../packages/luci-app-usb-gadget/) | Web interface for managing **USB gadget** functions (RNDIS, ECM, NCM, ACM, Mass Storage). |
| [`luci-theme-feelgood`](../../packages/luci-theme-feelgood/) | The **Feelgood** LuCI theme, carried in-tree and locally patched (the login-page cascade is shipped unminified — `csstidy` mis-orders the rules and covers the login form). |
| [`uci-usb-gadget`](../../packages/uci-usb-gadget/) | **UCI-based USB gadget manager** built on the kernel configfs interface. Configures the device as a USB peripheral and integrates the USB-Ethernet interface into the LAN bridge; auto-detects the UDC controller. |

---

## Base-files services

Some on-device programs ship in `msm89xx/base-files/` rather than as a package
(they are part of the target's default configuration):

| Program | What it does |
| :--- | :--- |
| `sim-hotplug` | Detects a physical SIM swap and re-announces the card to the baseband (`/etc/init.d/sim-hotplug`, `rescan` command). |
| `himi-ok-guard` | Rewrites the modem NV `HiMI_OK` marker after every modem boot. |
| `modem-bearer-watchdog` | Watches the modem/bearer, performs pre-emptive SSR and zombie recovery (logs as `modem-stall-watchdog`). |
| `apk-key-refresh` | Re-syncs the pinned APK feed signing key at boot. |
| `apk-overlay-heal` | Repairs the overlay after a flash that kept the configuration: clears whiteouts hiding files the image ships, drops overlay copies of package files the image now ships in a different version, and merges the stale `/etc/apk/world` and package database with the image's. Runs from preinit; see [Shipping a package without a reflash](#shipping-a-package-without-a-reflash). |
| `battery-led-monitor` | Drives the battery pair (charge state and faults only). Ships with `luci-app-battery`. |

The LED monitors, the boot blink and the Wi-Fi station helpers used to be listed
here too; they now ship as the [`msm89xx-scripts`](../../packages/msm89xx-scripts/)
package so they can be updated through the feed. What stays in `base-files` is
exactly what must exist **before** a package could be installed: `/etc/diag.sh`,
`/lib/preinit/*` and `/lib/batteryless.sh`.

**Every custom program — package or base-files — answers `-h`/`--help`** with its
options, the `uci` keys it honours, and the `logread` tag to watch:

```sh
sim-hotplug --help
modem-bearer-watchdog --help
diag-bind --help
diag_nv --help
```

---

## What ships where

| Layer | File | Scope |
| :--- | :--- | :--- |
| `DEFAULT_PACKAGES` | [`msm89xx/Makefile`](../../msm89xx/Makefile) | every board — all shared packages |
| `DEVICE_PACKAGES` | [`msm8916.mk`](../../msm89xx/image/msm8916.mk) | per device |
| `diffconfigs/<board>` | [`diffconfigs/`](../../diffconfigs/) | per board: the `=m` driver feed, option symbols, failsafe tuning, the device line, board-only packages |

Everything identical across the seven boards used to be hand-copied into each
`diffconfigs/<board>`; it now lives in `DEFAULT_PACKAGES`, so an eighth board
gets it automatically. The `=m` entries stay in the diffconfigs on purpose —
they are external modules and `DEFAULT_PACKAGES` would force them built-in.
`./build.sh guard` checks both invariants.

Packages are built by [`build.sh`](../../build.sh) and also published to the
signed APK feed (see the main [README → Official Package & Kernel Driver
Repository](../../README.md#-official-package--kernel-driver-repository)), so
they can be installed on-device with `apk add`.

## Shipping a package without a reflash

A full firmware build is the wrong tool for a one-line change in, say,
`wifi-led-monitor`: the image is ~34 MB and costs a flash cycle on every board.
[`publish-package.yml`](../../.github/workflows/publish-package.yml) rebuilds
**one** package against the same toolchain and kernel as the released firmware,
splices the new `.apk` into the **already published** feed and re-signs the index.
The feed URL does not change, so devices pick it up with:

```sh
apk update
apk upgrade msm89xx-scripts   # 'apk add' if the package is not installed yet
```

This only works for files that belong to a **package**. The kernel, a driver, a
preinit hook or `base-files` are baked into the image and can only change with a
firmware release — which devices then install themselves through *System →
Firmware Upgrade* (see [Releases & Hotfixes](https://github.com/akbar-npj/msm8916-openwrt/wiki/How-Releases-and-Hotfixes-Work)).

### Dispatch

```sh
# bump PKG_RELEASE, build, publish (the usual case)
gh workflow run publish-package.yml --ref main -f package=msm89xx-scripts

# rebuild the same version (e.g. after a failed publish)
gh workflow run publish-package.yml --ref main \
  -f package=msm89xx-scripts -f bump=false

# a different feed base (defaults to ./version, minus the -rN suffix)
gh workflow run publish-package.yml --ref main \
  -f package=luci-app-wifi-sta -f base=25.12.6
```

| Input | Default | Meaning |
| :--- | :--- | :--- |
| `package` | — | directory name under `packages/` |
| `base` | `./version` minus `-rN` | which `releases/<base>/` feed to publish into |
| `board` | `hmuf02v5` | build config; only matters for `kmod-*` (vermagic) |
| `bump` | `true` | increment `PKG_RELEASE` first |

### What the job does

1. Prepares the OpenWrt tree and injects the persistent `APK_SIGNING_KEY`.
2. **Downloads the published feed** from the `gh-pages` branch into
   `openwrt/bin/` — see the trap below.
3. Bumps `PKG_RELEASE` (unless `bump=false`) and builds the one package.
4. Re-runs `make package/index`, which re-signs every feed index with the
   persistent key.
5. Refuses to publish if the total package count dropped.
6. Copies the feed back over `gh-pages` and commits it.

### Why the feed is downloaded first

`make package/index` indexes whatever `.apk` files happen to be in
`openwrt/bin/packages`. A single-package build fills exactly one directory, so
re-indexing a fresh tree would publish an index listing **one** package and every
other package would silently disappear from `apk search` / `apk add`. The job
therefore restores the published tree first and asserts the count never shrinks.

### Things that will bite

- **`PKG_RELEASE` must change.** apk compares versions; rebuilding `1.0-r1` and
  re-uploading it is invisible to devices. `bump=true` handles it, but the bump
  only exists inside the CI run — commit `packages/<name>/Makefile` afterwards or
  the next build starts from the old number again (the job summary reminds you).
- **kmods are vermagic-locked.** A `kmod-*` hotfix only installs on a device
  running the exact kernel it was built against. Everything else is
  architecture-independent and safe.
- **The package must already be in the feed.** This publishes into an existing
  `releases/<base>/`; a package that was never part of a release has no feed
  entry to extend — cut a release first.
- **Moving a file between packages leaves a whiteout.** When a path stops being
  owned by `base-files` and starts being owned by a new package, the preserved
  overlay can still carry a whiteout (a `0,0` character device) for it. The new
  package installs, `apk info` lists it, the symlinks exist in `/rom` — and the
  merged view still shows nothing, so the service silently never starts.
- **The package database is the previous image's.** `/lib/apk/db/installed` is
  copied up to the overlay the first time apk writes it, so after an image
  change the device keeps the old view of who owns what — `apk info -W` names
  the wrong package, and `apk add` can fail with a file conflict.
- **So is `/etc/apk/world`.** It lists the packages apk considers explicitly
  installed. A world from the previous image does not list a package the new
  image added, so apk treats that package as unwanted and **drops it from the
  database on the next transaction** — the files stay on disk, the record does
  not. This is the one that bites a boot *after* the flash: the package works,
  then quietly disappears.
- **Overlay copies of package files shadow the image.** The overlay always wins.
  A file a package owns gets copied up the first time anything writes to it, and
  that copy then outlives every flash — so after an image change the merged view
  serves the *old* file while `apk info` reports the new version. No error is
  logged anywhere: the daemon just runs the previous release. A LuCI theme is the
  loudest case, because the stylesheet and the template that links it are both
  stale, so the page looks untouched and `apk` swears the update landed.

All four are repaired **automatically** by
[`apk-overlay-heal`](../../msm89xx/base-files/usr/sbin/apk-overlay-heal), run
from `/lib/preinit/85_apk_overlay_heal` — after the overlay is mounted, before
any init script — and **only on the first boot after an image change**. It

1. removes every whiteout whose path the running image ships (whiteouts under
   `/etc/uci-defaults` are left alone — they are how OpenWrt records that a
   one-shot script already ran),
2. drops overlay copies of package files that differ from the image's, so the
   merged view serves the file the image actually ships. This is upstream's
   `sysupgrade -o` rule (*"preserve all changed files in /, except those from
   packages but including changed confs"*) applied at boot instead of at
   upgrade time: conffiles — `*.conffiles_static`, `/etc/sysupgrade.conf`,
   `/lib/upgrade/keep.d` — are never reverted, and neither is anything the image
   does not ship, which is how a package installed from the feed keeps its
   files. A file that already matches the image is left as it is, so the pass
   does nothing on a device that was never hand-edited. And a package you
   *upgraded* from the feed is newer than the image — reverting it would be a
   silent downgrade — so its files are kept too, and step 4 keeps the matching
   database record instead of the image's. That one needs a version compare
   (`apk version -t`, falling back to `sort -V`), because the overlay has the
   files in both cases and only the recorded version tells them apart,
3. merges `/etc/apk/world` the same way (image entries plus anything added on
   the overlay), and
4. merges the overlay's database with the image's: the image wins for every
   package it ships — unless the overlay records a *higher* version, in which
   case that record is kept so it still matches the files step 2 preserved —
   and packages installed from the feed are kept. It refuses the merge and
   keeps the overlay copy if the result would not parse.

The image change is tracked by `/etc/apk/.image_id` (an md5 of the image's
database); on an ordinary boot the hook returns immediately.

The gate matters: a whiteout is *also* how a legitimate `apk del` removes a file
the image ships, so healing on every boot would resurrect packages the user
uninstalled. Right after a flash there is no such ambiguity — the new image's
package set is authoritative.

> **No reboot is needed after `apk add`.** A package update goes through apk
> normally and takes effect immediately, exactly like upstream OpenWrt. The
> heal only exists because *this project* keeps the overlay across a firmware
> flash; it is part of the boot the flash already costs, not an extra one.

Check it with `logread -e apk-overlay-heal`, `apk info -W <file>` and
`ps w | grep led` (busybox `pgrep` truncates names to 15 characters, so
`pgrep modem-led-monitor` silently matches nothing).

### Adding a new package

A package that is new to the tree is invisible to the build until the scan
notices it, and the failure does not say so:

    make[1]: *** No rule to make target 'package/msm8916/<name>/compile'.  Stop.
    WARNING: your configuration is out of sync. Please run make menuconfig...

`include/scan.mk` builds the package list with
`grep 'call (Build/DefaultTargets|BuildPackage|KernelPackage)'` over every
`Makefile` — a text search, not a parse. A `luci.mk` package's only build call
lives *inside* `luci.mk`, so its `Makefile` must carry the trailing comment
line that makes it match:

    include $(TOPDIR)/feeds/luci/luci.mk

    # call BuildPackage - OpenWrt buildroot signature

Without it the directory is skipped in silence. A files-only package (no
`src/`) also needs an empty `Build/Compile` — otherwise the default rule runs
`make -C $(PKG_BUILD_DIR)` and fails with "No targets specified and no makefile
found" — and any `rc.d` symlink it wants must be created explicitly, because
nothing runs `default_postinst` at build time.

After adding one, refresh the scan and the config (`./build.sh package <name>`
does the sync; the container commands are):

    rm -f tmp/info/.files-packageinfo* tmp/info/.overrides-packageinfo* \
          tmp/.packageinfo tmp/.packagedeps
    make prepare-tmpinfo && make defconfig

`CONFIG_DEFAULT_<name>=y` comes from `DEFAULT_PACKAGES`; `CONFIG_PACKAGE_<name>=y`
only appears after the metadata has been regenerated, so run `defconfig` a
second time — the first pass is what writes the `DEFAULT_` symbol.

## Related docs

- [**Kernel-Modules.md**](Kernel-Modules.md) — the in-repo `kmod-*` drivers.
- [**Docs/Patches/README.md**](../Patches/README.md) — every kernel patch,
  including the driver changes these packages rely on.
- [**Docs/Modem Stability/**](../Modem%20Stability/) — the modem/DIAG tooling in
  context.

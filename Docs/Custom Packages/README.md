# Custom Packages

Beyond the upstream OpenWrt tree, this project ships **23 packages** under
[`packages/`](../../packages/) — the tools, daemons and LuCI apps that make an
MSM8916 USB dongle usable as a headless router with a Qualcomm baseband.

They fall into two groups:

- **Authored in-repo (14)** — written for this project, specific to the MSM8916
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
| [`luci-app-wifi-sta`](../../packages/luci-app-wifi-sta/) | LuCI support for **Station Mode** (concurrent AP+STA): configures the late-brought-up station interface of the wcn36xx radio without touching the wireless config. |
| [`luci-app-modem-watchdog`](../../packages/luci-app-modem-watchdog/) | LuCI web UI for the **modem bearer watchdog** — status banner, enable/disable, the automatic `auto_mobile` handover profile (on by default) plus the manual `mobile_optimized` force mode, stall-timeout/check-interval, keepalive and recovery-escalation settings. |

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
apk add msm89xx-scripts
```

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
- **A device keeps its own copy of the package database.** After flashing with
  `flash.sh --keep-config` (or a config-preserving sysupgrade) the overlay holds
  the *previous* image's `/lib/apk/db/installed`, which shadows the new one: it
  reports stale ownership (`apk info -W`) and can refuse an install.
- **Moving a file between packages leaves a whiteout.** When a path stops being
  owned by `base-files` and starts being owned by a new package, the preserved
  overlay can still carry a whiteout (a `0,0` character device) for it. The new
  package installs, `apk info` lists it, the symlinks exist in `/rom` — and the
  merged view still shows nothing, so the service silently never starts. This is
  what a package split looks like on a device that was flashed with
  `--keep-config`.

  Both are one-time cleanup after such a flash:

  ```sh
  # stale package database
  rm -f /overlay/upper/lib/apk/db/installed \
        /overlay/upper/lib/apk/db/scripts.tar.gz \
        /overlay/upper/lib/apk/db/triggers \
        /overlay/upper/lib/apk/db/lock

  # whiteouts hiding files the new image ships
  find /overlay/upper -type c -not -path '*/etc/uci-defaults/*' \
       -exec rm -f {} +

  reboot
  ```

  (`rm` alone is not enough — the merged view keeps the deleted inode until the
  next boot. Whiteouts under `/etc/uci-defaults` are *normal*: they are the
  scripts that already ran, and deleting them makes them run a second time.)

  Verify with `apk info -W /usr/sbin/wifi-led-monitor` and `ps w | grep led`.

## Related docs

- [**Kernel-Modules.md**](Kernel-Modules.md) — the in-repo `kmod-*` drivers.
- [**Docs/Patches/README.md**](../Patches/README.md) — every kernel patch,
  including the driver changes these packages rely on.
- [**Docs/Modem Stability/**](../Modem%20Stability/) — the modem/DIAG tooling in
  context.

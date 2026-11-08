# LED hotfix for 25.12.6-r8 units

An r8 image does not contain the role-based LED work: it still decides colours
from `board_name`, so a unit whose green and blue lamps are wired the other way
round drives the wrong lamp, and there is no way to change it without a rebuild.

This hotfix delivers the whole feature as **two packages through the signed
feed**. No sysupgrade, no reflash, no new image.

| Package | Version | What it adds |
|---|---|---|
| `msm89xx-scripts` | `1.0-r18` | `/etc/config/leds`, the shared resolver, `led-probe`, and the monitors rewritten to read the role table |
| `luci-app-led-setup` | `1.0-r5` | **System → LED Setup**: lamp dropdowns, per-scenario pattern dropdowns, a **Blink** button, and the stock LED Configuration page hidden |

Both are published into the r8 revision feed only
(`releases/25.12.6-r8/`), so they can never reach a different build:

```
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6-r8/
├── hotfix.json
└── packages/aarch64_generic/
    └── base/     # msm89xx-scripts, luci-app-led-setup
```

The mechanism itself (what the page and the dashboard banner show, why an entry
can linger after installing) is in [`HOTFIXES_ON_DEVICE.md`](HOTFIXES_ON_DEVICE.md).
What the LED feature does is in [`LED_SETUP.md`](LED_SETUP.md).

## Installing

### From LuCI (normal path)

**System → Firmware Upgrade** lists the two entries after its next check.
Press **Install**. Both packages are applied in one transaction; the LED
monitors are restarted afterwards, so expect the lamps to flicker for about a
second.

### From the command line

```sh
firmware-upgrade hotfix check     # what is offered for this build
firmware-upgrade hotfix apply     # install everything offered, restart services
```

`apply` is idempotent: it only installs entries newer than what is already
installed, so running it again after a partial install is safe.

### If the page shows nothing

The device caches the feed index. Force a fresh read:

```sh
rm -f /var/cache/apk/APKINDEX.*
firmware-upgrade hotfix check
```

If it still shows nothing, the Pages deployment may not have finished — the
`.apk` and the index are committed to `gh-pages` together, but GitHub Pages
publishes them with a delay of a few minutes. Retry; do not re-publish.

## Verifying

```sh
apk list -I msm89xx-scripts luci-app-led-setup
# msm89xx-scripts-1.0-r18 ...
# luci-app-led-setup-1.0-r5 ...

led-probe --caps          # cap<TAB>battery<TAB>0|1
led-probe --show | head   # the role table in effect
```

`led-probe --show` prints `auto` for every role on a unit nobody has configured
yet — that means "board default", i.e. exactly the behaviour the unit had
before the hotfix. Nothing changes until you assign a lamp.

The LuCI menu entry appears immediately, but the browser caches the menu tree:
log out and back in (or hard-refresh) once.

## Fixing a swapped unit

This is the reason the hotfix exists. On the affected unit the Wi-Fi lamp is
blue and the LTE lamp is green, but the board name says the opposite.

1. Open **System → LED Setup**.
2. On the `wifi` row press **Blink** and look at which lamp flashes.
3. Set `wifi` to that lamp's label (`green:wlan` or `blue:wan`) and `wan` to the
   other one.
4. **Save & apply**.

Same thing from the shell:

```sh
led-probe --set wifi=green:wlan wan=blue:wan
```

`led-probe --reset` puts every role back to the board default.

## What does not change

- **No firmware, no kernel, no reboot.** The image, `version` and
  `/etc/openwrt_release` are untouched; the unit still reports `25.12.6-r8`.
- **`/etc/config/leds` is a conffile.** Edits and role assignments survive
  later `apk upgrade`s of `msm89xx-scripts`.
- **The battery roles stay hidden** on a board that reports no battery
  (`led-probe --caps` answers that), so the two mains-only boards show no
  battery section at all.
- **The stock LED Configuration page is hidden** while `luci-app-led-setup` is
  installed. It writes `config led` sections into `/etc/config/system`, which
  `/etc/init.d/led` applies to the same `trigger`/`brightness` files the
  monitors drive — configuring a lamp in both places makes the page look
  broken. Removing the package brings the stock page back.

## Rollback

The previous builds stay in the feed; that is the rollback path. `apk` will not
downgrade in place — while r18 is installed, `apk add msm89xx-scripts=1.0-r14`
fails with `breaks: world[msm89xx-scripts=1.0-r14]` — so remove first:

```sh
apk del luci-app-led-setup msm89xx-scripts
apk add --repository https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6-r8/packages/aarch64_generic/base/packages.adb \
  msm89xx-scripts=1.0-r14
/etc/init.d/wifi-led-monitor restart
/etc/init.d/modem-led-monitor restart
```

r14 is the previous r8 hotfix and carries the old hardcoded colour logic. Write
down any role mapping first: it lives in `/etc/config/leds`, and removing the
package can take that file with it.

## Why this is a hotfix and not a release

The change is entirely userspace: shell scripts, a LuCI view and a uci config
file. Nothing in it needs a new kernel, a new device tree or a rebuilt image,
and the two packages are already part of every image built from `main` after
this change. Shipping an image would mean a ~40 MB download per unit and a
reflash to deliver files that `apk` can replace in place.

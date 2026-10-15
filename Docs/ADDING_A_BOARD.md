# Adding a New Board (Device) to msm8916-openwrt

This guide explains, step by step, everything required to add support for a
new MSM8916-based board (a 4G dongle / MiFi / stick) to this firmware tree.

It is written against a real, complete example: **PR #2 — the MF800B board**
(merged 2026-09-22). Every file referenced below is a file that PR actually
touched. Read the MF800B files alongside this guide; they are the canonical
template.

> Mental model: the build target `msm89xx/msm8916` already exists and builds
> for *every* MSM8916 board. "Adding a board" means adding **one Device
> profile + one Device Tree + one diffconfig**, plus a handful of small,
> usually optional, base-files tweaks for LEDs / buttons. You rarely touch the
> kernel, the network setup, or the build system itself.

---

## 0. Prerequisites — know your hardware

Before writing any code, collect these facts about the target board:

| What | Why | Where to find it |
|------|-----|------------------|
| SoC is **MSM8916** (Snapdragon 210/410) | The whole tree targets this SoC | Board label / stock firmware |
| It reuses the **`msm8916-ufi.dtsi`** layout | All current boards share this base | Confirm it boots Android/Linux with the ufi layout |
| **LED GPIOs** (TLMM pins, active-high/low) | The DTS must map them | Stock DTS, or probe the board |
| **Reset button** GPIO + polarity | Failsafe + reset handler | Stock DTS `gpio-keys` node |
| **eMMC size** in 512-byte sectors | Sets `GPT_TOT_SECTORS` | `cat /sys/block/mmcblk0/size` on stock, or read the GPT |
| **Battery / charger** present? | Drives `battery` / `pm8916_charger` nodes | Stock DTS |
| **EDL entry method** (test pads / key combo) | Required to flash & recover | Look for `FB` test point, volume/combo |

If the board is *not* a ufi-layout board, the DTS work below gets harder
(different `.dtsi` base, possibly different peripherals). The rest of this
guide assumes the common ufi stick/MiFi form factor.

---

## 1. The Device Tree source (a new kernel patch)

Create a new patch:

```
msm89xx/patches/8XX-arm64-dts-qcom-add-msm8916-generic-<board>.patch
```

Pick the **next free number in the 8xx range** (MF800B used `807`; `801` is the
qcom Makefile, so do not reuse it). The file is a `git diff --no-index` style
patch that adds one new `.dts` under
`arch/arm64/boot/dts/qcom/msm8916-generic-<board>.dts`.

Template (condensed from `807-arm64-dts-qcom-add-msm8916-generic-mf800b.patch`):

```dts
// SPDX-License-Identifier: GPL-2.0-only

/dts-v1/;

#include "msm8916-ufi.dtsi"
#include <dt-bindings/leds/common.h>

/ {
        model = "<Board> 4G ...";
        compatible = "<board>,generic", "qcom,msm8916";

        /*
         * Optional: a battery node. Only if the board has a Li-ion cell
         * and you want charge / capacity reporting. Copy the mf800b values
         * and adjust voltage / capacity to the pack label.
         */
        battery: battery {
                compatible = "simple-battery";
                device-chemistry = "lithium-ion-polymer";
                voltage-min-design-microvolt = <3350000>;
                voltage-max-design-microvolt = <4350000>;
                charge-full-design-microamp-hours = <2100000>;
                constant-charge-current-max-microamp = <1000000>;
                constant-charge-voltage-max-microvolt = <4350000>;
        };
};

/* Persistent kernel log (ramoops) — same region as every other board. */
&reserved_memory {
        ramoops@8db00000 {
                compatible = "ramoops";
                reg = <0x0 0x8db00000 0x0 0x00100000>;
                record-size = <0x00040000>;
                console-size = <0x00040000>;
                pmsg-size   = <0x00040000>;
                ecc-size = <16>;
        };
};

/*
 * LEDs. The ufi .dtsi ships a generic tri-colour "led-r/g/b". Delete those
 * and define your real, named LEDs. Match function/color to what the LED
 * scripts expect (see §4).
 */
&{/leds} {
        /delete-node/ led-r;
        /delete-node/ led-g;
        /delete-node/ led-b;

        led-wlan-green { function = LED_FUNCTION_WLAN; color = <LED_COLOR_ID_GREEN>; gpios = <&tlmm 73 GPIO_ACTIVE_HIGH>; };
        led-wlan-red   { function = LED_FUNCTION_WLAN; color = <LED_COLOR_ID_RED>;   gpios = <&tlmm 84 GPIO_ACTIVE_HIGH>; };
        led-wwan-green { function = LED_FUNCTION_WAN;  color = <LED_COLOR_ID_GREEN>; gpios = <&tlmm 85 GPIO_ACTIVE_HIGH>; };
        led-wwan-red   { function = LED_FUNCTION_WAN;  color = <LED_COLOR_ID_RED>;   gpios = <&tlmm 72 GPIO_ACTIVE_HIGH>; };
        /* ... etc ... */
};

/* The gpio_leds pinctrl must list every LED GPIO you use. */
&gpio_leds_default {
        pins = "gpio0", "gpio1", "gpio72", "gpio73", "gpio84", "gpio85";
};

/*
 * Reset button. Copy the active-high / pull-down convention used by the other
 * boards (verified on HMU05/UFI001B). gpio102 is the common ufi "key_freset".
 */
&button_restart {
        gpios = <&tlmm 102 GPIO_ACTIVE_HIGH>;
};
&button_default {
        pins = "gpio102";
        bias-pull-down;
};

/*
 * Battery charger and voltage-mode BMS are board-specific. In the pinned
 * Linux 6.12 tree, the base PM8916 DTS leaves pm8916_charger disabled and
 * msm8916-ufi.dtsi enables pm8916_usbin as the USB extcon. A battery-equipped
 * board may need the MF800B-style charger/BMS setup instead; do not enable
 * both paths because they share PMIC USB detection resources. A bus-powered
 * stick without a battery should keep the usbin extcon and omit battery/BMS
 * nodes.
 */
&pm8916_usbin   { status = "disabled"; };
&pm8916_charger {
        monitored-battery = <&battery>;
        qcom,fast-charge-safe-voltage = <4350000>;
        qcom,fast-charge-safe-current = <1000000>;
        status = "okay";
};
&pm8916_bms {
        monitored-battery = <&battery>;
        power-supplies = <&pm8916_charger>;
        status = "okay";
};

/* Peripheral-only USB, fed by the charger extcon. */
&usb         { dr_mode = "peripheral"; extcon = <&pm8916_charger>; };
&usb_hs_phy  { extcon = <&pm8916_charger>; };
```

**Rules of thumb for the DTS**

* The **first `compatible` string** (`"<board>,generic"`) becomes the
  `board_name` OpenWrt sees at runtime (`/tmp/sysinfo/board_name`). Keep it
  stable — base-files scripts glob on it.
* Keep the `ramoops` region. It is what makes kernel panics recoverable.
* Keep the `pm8916_usbin` disable / `pm8916_charger` enable split unless you
  have a specific reason not to.

---

## 2. Register the DTB in the qcom Makefile patch

The kernel only builds DTBs listed in the qcom `Makefile`. Add your board to
the existing patch that maintains that list:

`msm89xx/patches/801-arm64-dts-qcom-add-devices-makefile.patch`

```diff
 dtb-$(CONFIG_ARCH_QCOM)	+= msm8916-samsung-j5.dtb
+dtb-$(CONFIG_ARCH_QCOM)	+= msm8916-generic-<board>.dtb
 dtb-$(CONFIG_ARCH_QCOM)	+= msm8916-samsung-j5x.dtb
```

Without this line the DTB is never compiled, and the image build fails or
ships without your device tree.

---

## 3. The Device profile — `msm89xx/image/msm8916.mk`

Add a `define Device/...` block. It inherits the shared `Device/msm8916`
defaults (cmdline, squashfs, GPT artifacts, flash.sh) and only overrides what
is board-specific. Model it on the `generic-mf800b` block:

```makefile
define Device/generic-<board>
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL  := <Board>
	DEVICE_DTS    := msm8916-generic-<board>
	SUPPORTED_DEVICES := <board>,generic
	FILESYSTEMS   := squashfs
	# Only if your eMMC size differs from the 4 GB sticks:
	# GPT_TOT_SECTORS := <cat /sys/block/mmcblk0/size>
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-<board>
```

Notes:

* **`DEVICE_DTS`** must equal the `.dts` basename you created in §1.
* **`SUPPORTED_DEVICES`** must include the board identifier accepted by the
  target's sysupgrade compatibility check. In this tree it normally matches
  the board-specific first DTS compatible string (for example,
  `compatible = "mf800b,generic", ...` with
  `SUPPORTED_DEVICES := mf800b,generic`). Keep the spelling consistent.
* **`DEVICE_PACKAGES`** — start from the common set above. Add board-unique
  packages only if the board needs them (example: `qcom-time-daemon` is
  `generic-hmu05`-only, because it anchors the HMU05 modem's ATS state and its
  init is a no-op on every other board).
* **`GPT_TOT_SECTORS`** — only override when the eMMC is a different size. The
  default (`7569408`) is the 4 GB stick size. `flash.sh` now *reads* the real
  size out of the GPT at flash time, but the image's GPT itself is built from
  this value, so set it correctly for your hardware.

---

## 4. Board-specific base-files (usually small or none)

OpenWrt's runtime scripts live in `msm89xx/base-files/`. Most are already
**generic across all boards** and need no change:

* `etc/board.d/02_network` — sets `usb0` as LAN, Wi-Fi country, and the AP
  SSID. **No change needed** unless your board has different networking.
* `etc/uci-defaults/99-msm89xx-firstboot` — ModemManager WAN, removes the
  non-existent target feed, etc. **No change needed.**

You only edit these if your board's hardware differs:

### 4a. Wi-Fi LED — `etc/hotplug.d/ieee80211/99-wifi-led`

This script lights a WLAN LED when `phy0` appears. It selects the LED by
class-name fallback (`green:wlan` → `green:wan`) and skips boards that have no
suitable LED. PR #2 added an explicit skip for MF800B (it has no automatic LED
indication):

```sh
case "$(cat /tmp/sysinfo/board_name 2>/dev/null)" in
	*<board>*) exit 0 ;;   # your board has no WLAN LED, or you handle it elsewhere
esac
```

If your board *does* have a `green:wlan` / `green:wan` LED, you need **no
change here** — it is picked up automatically. If you use different LED
class names, either rename the DTS node to match, or add a board branch.

### 4b. Power button — `etc/rc.button/power`

This handler powers the device off on a long press and blinks a "goodbye"
LED first. It falls back through a *list* of LED names:

```sh
LED="/sys/class/leds/red:power"
[ -d "$LED" ] || LED="/sys/class/leds/green:power"
[ -d "$LED" ] || LED="/sys/class/leds/green:charging"
[ -d "$LED" ] || LED="/sys/class/leds/red:charging"
[ -d "$LED" ] || LED="/sys/class/leds/green:wan"
[ -d "$LED" ] || LED="/sys/class/leds/blue:wan"
```

If none of those LEDs exist on your board, the handler still powers off — it
just skips the blink. **No change strictly required**, but if your board names
its power/charging LED something else, add it to this fallback chain so users
get the visual confirmation.

### 4c. Services that already key off `board_name`

Two services glob on `board_name` and may need your board added to an
allow-list:

* `lib/himi-ok.sh` — the **HiMI baseband** "modem-OK" guard. Only boards with
  a HiMI baseband (HMU05, UF02, UZ801, …) need it. If your board is a HiMI
  baseband device, add it to the allow-list; otherwise leave it out.
* `usr/sbin/modem-led-monitor` — the cellular/charging LED monitor. It already
  branches per board; add a branch only if you want the WWAN LED behavior to
  differ from the default.

---

## 5. The board diffconfig — `diffconfigs/<board>`

This file is the board's saved OpenWrt configuration. **`build.sh` enumerates
boards from this directory** (`build.sh list` lists every file here), and the
CI / release flow builds one image + kmod feed per diffconfig.

Copy an existing one as a starting point:

```sh
cp diffconfigs/mf800b diffconfigs/<board>
```

Then edit it:

* Set the device selection (this is the single most important line):
  ```sh
  CONFIG_TARGET_msm89xx=y
  CONFIG_TARGET_msm89xx_msm8916=y
  CONFIG_TARGET_msm89xx_msm8916_DEVICE_generic-<board>=y
  ```
* **Failsafe**: the 7-second preinit window and the `192.168.10.1` preinit IP
  are already the defaults. Keep the `CONFIG_IMAGEOPT=y` / `CONFIG_PREINITOPT=y`
  / `CONFIG_TARGET_PREINIT_*` block (copied from mf800b) so they stay explicit
  and are not reset by `make defconfig`.
* Add any **board-specific package selections** (e.g. `qcom-time-daemon` if
  your board is HMU05-class).
* The large `=m` block is the **optional kmod feed** — modules built into the
  package feed but *not* baked into the image, so users can `apk add` them on
  demand. Copy it verbatim; trim only if you are sure a module is unwanted.

After editing, validate it builds before committing (§7).

---

## 6. Document it — `README.md` supported-devices table

Add one row to the supported-devices table so users can find the board:

```markdown
| **`<board>`** | `generic-<board>` | <Board description> | MSM8916 | 512 MB | 4 GB eMMC | USB NCM, ACM, Wi-Fi AP, LTE, Reboot-to-EDL, Reboot-to-Fastboot, <any unique features> |
```

(Match the existing column layout — see the `mf800b` row PR #2 added.)

---

## 7. Build, flash, verify

### Build

```sh
./build.sh list                 # confirm <board> is now listed
./build.sh prepare              # first time only: clone + prepare the OpenWrt tree
./build.sh guard                # verify the live tree matches msm89xx/ + packages/
./build.sh build <board>        # build the firmware image + kmod feed
```

`build.sh` rewrites OpenWrt's `.config` from `diffconfigs/<board>` (via
`make defconfig`), so the Device profile in §3 and the diffconfig in §5 must
agree on the device name.

### Flash

First time (stock Android → OpenWrt):

```sh
# from the build output dir (bin/targets/.../msm8916/)
./flash.sh --mode migrate --yes       # or run with no args for the menu
```

Subsequent updates:

```sh
./flash.sh --mode update --keep-config --yes
```

`flash.sh` backs up the radio/calibration partitions (`fsc fsg modemst1
modemst2 modem persist sec`) *before* writing anything, so a failed flash
never bricks the modem NV.

### Verify on the device

* `cat /tmp/sysinfo/board_name` → should print `<board>,generic`.
* `ls /sys/class/leds/` → your named LEDs exist.
* Button behavior depends on the DTS key code. On the UFI-family boards the
  Restart input is `KEY_RESTART`: after boot, a short press is ignored and a
  hold of at least five seconds performs factory reset plus reboot. It does
  not power the device off; the goodbye-blink power-off handler applies only
  to a separate `KEY_POWER` input.
* Modem: `mmcli -L` shows the modem; `mmcli -m 0` shows signal / registration.
* Wi-Fi: the `OpenWrt` AP is broadcast.
* **Failsafe**: tap Restart once during the first seven seconds of boot; do not
  hold it for seven seconds. You should be able to reach `192.168.10.1` (root
  shell over the USB gadget). A long hold after boot invokes the reset-button
  handler instead.

---

## 8. Common pitfalls

1. **Forgot to add the DTB to patch 801.** The DTS exists but is never
   compiled → no device tree in the image. Always edit the qcom `Makefile`
   patch in the same PR.
2. **`GPT_TOT_SECTORS` wrong.** If your eMMC is not 4 GB, set it from
   `cat /sys/block/mmcblk0/size`. The image GPT is built from this value.
   (`flash.sh` reads the *real* size at flash time, so a mismatch mainly
   affects the generated GPT image, not the live device — but set it right.)
3. **LED class names don't match the scripts.** `99-wifi-led` and
   `rc.button/power` look for specific names (`green:wlan`, `green:wan`,
   `*:power`, `*:charging`). Either name your DTS LEDs to match, or add a
   board branch.
4. **`SUPPORTED_DEVICES` must match the DTS board identity.** A mismatch can
   make `sysupgrade` refuse to apply the image. Keep the board identifier
   consistent with the first, board-specific `compatible` string.
5. **`board_name` changes after merge.** Base-files scripts glob on it. Pick a
   stable first `compatible` string and don't rename it later.
6. **Copied a diffconfig but forgot to change the `DEVICE_*` line.** You'll
   build the wrong board's image. The `CONFIG_TARGET_msm89xx_msm8916_DEVICE_*`
   line must name *your* Device.
7. **HiMI baseband boards need the himi-ok allow-list.** If your board carries
   a HiMI baseband, add it to `lib/himi-ok.sh` or the modem-OK gate won't run.

---

## 9. Checklist (copy/paste)

- [ ] **DTS patch** `msm89xx/patches/8XX-...-generic-<board>.patch`
  - [ ] `#include "msm8916-ufi.dtsi"`
  - [ ] `model` + `compatible = "<board>,generic", "qcom,msm8916"`
  - [ ] `ramoops` reserved-memory region
  - [ ] LED nodes (delete default `led-r/g/b`, add named LEDs) + `gpio_leds_default` pins
  - [ ] `button_restart` GPIO + `button_default` bias
  - [ ] charger / bms / usb extcon blocks (or a documented reason to omit)
  - [ ] battery node (if a cell is present)
- [ ] **DTB registration** in `msm89xx/patches/801-...-makefile.patch`
- [ ] **Device profile** in `msm89xx/image/msm8916.mk` (`TARGET_DEVICES += generic-<board>`)
  - [ ] `DEVICE_DTS` matches the `.dts` basename
  - [ ] `SUPPORTED_DEVICES` matches the DTS `compatible`
  - [ ] `GPT_TOT_SECTORS` set if eMMC ≠ 4 GB
  - [ ] `DEVICE_PACKAGES` = common set + any board-unique packages
- [ ] **base-files tweaks** (only if hardware differs)
  - [ ] `99-wifi-led` board skip/branch
  - [ ] `rc.button/power` LED fallback
  - [ ] `himi-ok.sh` / `modem-led-monitor` allow-lists (HiMI boards only)
- [ ] **diffconfig** `diffconfigs/<board>` (copied from `mf800b`, device line fixed)
- [ ] **README.md** supported-devices table row
- [ ] `./build.sh build <board>` succeeds; flash + verify on hardware

---

*Reference implementation: [PR #2 — MF800B](https://github.com/akbar-npj/msm8916-openwrt/pull/2).*

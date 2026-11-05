# MIFIB-V12 — stock Android device tree

Reference copy of the **stock Android DTB** for the MIFIB-V12 (MIFID) dongle,
captured from a live, working Android install on 2026-10-22. Kept so the
OpenWrt port can be diffed against what the vendor actually ships.

## Files

| File | What it is |
|---|---|
| `android-msm8916-mtp.dts` | Decompiled source of the board DTB that the device boots |
| `android-msm8916-mtp.dtb` | The same DTB as raw bytes (authoritative) |

sha256:
```
f52928530a367590bb0b42ae2277191aafd4c73e58115e4072315386bc5301e6  android-msm8916-mtp.dtb
1a688703c95f2837a72e2ad91054fe1e4693767e16c1f9b3e2a44c1eb406cb34  android-msm8916-mtp.dts
```

## Where it came from (reproducible)

The board DTB is **not** exposed via `/proc/device-tree` or `/sys/firmware/fdt`
on this 3.10 kernel, so it was pulled out of the boot image:

```sh
# boot header: page_size=2048 kernel=5937576 ramdisk=568157 dt_size=3748864
# DTB offset = 2048 + round_up(kernel) + round_up(ramdisk) = 6510592
ssh root@192.168.101.1 'dd if=/dev/block/bootdevice/by-name/boot \
    bs=512 skip=12716 count=7322' > dtblob.bin

# the blob starts with a QCDT table; scan for FDT magic instead of parsing it
python3 scratch/fdtscan.py dtblob.bin fdt/     # finds 23 DTBs
dtc -I dtb -O dts -o out.dts fdt/<one>.bin
```

**Which of the 23 is the board DTB?** `fdt-002-off00051000.bin`
(`model = "Qualcomm Technologies, Inc. MSM 8916 MTP"`, `qcom,board-id = <0x08 0x00>`).
Confirmed by elimination: the device reports `MemTotal 402464 kB`, so it is the
plain MTP and **not** `MSM 8916 512MB MTP` (`board-id <0x08 0x100>`). The other
candidates in the blob are reference boards (CDP/QRD/RUMI/Simulator/MSM8936/8939/
APQ8016). The vendor did not add a new `model` string — they edited the MTP DTS.

## The three push buttons

| Button | DT label | TLMM GPIO | Polarity | `linux,code` | Exposed in OpenWrt? |
|---|---|---|---|---|---|
| Power | (PMIC pwrkey) | — | — | `KEY_POWER` (116) | n/a |
| Reset | `key_freset` | **102** (`0x66`) | active-low | `0xd8` = **216** = `KEY_CHAT` | yes — `button_restart` |
| WPS | `key_wps` | **27** (`0x1b`) | active-low | `0x40` = **64** = `KEY_F6` | **NO** |

```dts
gpio_keys {
	compatible = "gpio-keys";
	input-name = "gpio-keys";
	pinctrl-0 = <0xda 0xdb>;	/* tlmm_gpio_key — pin 0x66 only */

	key_freset {
		label = "key_freset";
		gpios = <0x2f 0x66 0x01>;	/* tlmm, 102, GPIO_ACTIVE_LOW */
		linux,code = <0xd8>;		/* KEY_CHAT */
		gpio-key,wakeup;
		debounce-interval = <0x3c>;	/* 60 ms */
	};

	key_wps {
		label = "key_wps";
		gpios = <0x2f 0x1b 0x01>;	/* tlmm, 27, GPIO_ACTIVE_LOW */
		linux,code = <0x40>;		/* KEY_F6 */
		gpio-key,wakeup;
		debounce-interval = <0x3c>;	/* 60 ms */
	};
};
```

Both pins idle **high** (`/sys/kernel/debug/gpio`: `in hi`), i.e. active-low with
an external pull-up, and both are `gpio-key,wakeup` capable.

**Pinctrl:** two separate groups, one pin each, both `pin-func = 0` (GPIO),
`bias-pull-up`, 2 mA, with active + suspend variants:

```dts
tlmm_gpio_key     { qcom,pins = <0xcf 0x66>; ... }   /* pin 102, phandles 0xda / 0xdc */
tlmm_wps_gpio_key { qcom,pins = <0xcf 0x1b>; ... }   /* pin  27, phandles 0xdb / 0xdd */
```

Runtime confirmation (`/sys/kernel/debug/pinctrl/1000000.pinctrl/pinmux-pins`):

```
pin 27  (gp-27):  gpio_keys.62 gp:929  function tlmm_wps_gpio_key-func group tlmm_wps_gpio_key
pin 102 (gp-102): gpio_keys.62 gp:1004 function tlmm_gpio_key-func     group tlmm_gpio_key
```

## Differences vs the OpenWrt port

`msm89xx/patches/853-arm64-dts-qcom-add-msm8916-generic-mifib-v12.patch` only
adds `&button_restart` (`gpios = <&tlmm 102 GPIO_ACTIVE_LOW>`) and
`&button_default` (`bias-pull-up`). There is **no `gpio-keys` node and no
`key_wps`** — the WPS button is invisible to OpenWrt.

## MEASURED: the WPS button does NOT drive GPIO 27 (2026-10-22)

Tested on live Android with the button physically pressed and held for 5 s:

* `/sys/kernel/debug/gpio` sampled at ~10 Hz for the whole hold — **`gpio-929
  (key_wps)` stayed `in hi` the entire time** (no `in lo` transition logged).
* `getevent -lt /dev/input/event1` — **0 lines**; no `KEY_F6` event.

So the `key_wps` node is inherited MTP boilerplate that this PCB does not wire
up (or the button is routed to the WiFi module / a net that is not TLMM 27).
The pin itself is healthy: muxed to `tlmm_wps_gpio_key-func`, pull-up applied,
claimed by `gpio_keys.62`, idling high.

**Consequence for the recovery design:** the WPS button is **not usable** as a
recovery key on this board. GPIO 102 (`key_freset`) is the only key that is
actually wired, so a recovery button on mifib-v12 has to be the reset button.

## MEASURED: the reset button is a plain GPIO, safe as a boot-time key (2026-10-22)

Held the reset button down and then pressed power (on battery, USB unplugged):
**Android booted normally and the button had no effect.**

If `key_freset` were tied into a hardware reset path, holding it at power-on
would have held the SoC in reset and the device would never have come up. It
did, so the button is an ordinary GPIO input and **lk2nd can sample it at
boot** — this is the one pre-condition the recovery-key design needed.

## Reset button long-press in Android

A ~5 s press of the reset button while Android is running **reboots the device
and factory-resets it** — the management IP went from `192.168.101.1` back to
the factory default `192.168.100.1`. This is userspace behaviour: the key is
`KEY_CHAT` (216), which is not a standard keycode, so a vendor daemon is almost
certainly watching it for a long-press.

## Open questions

* Which net is the WPS button actually on? (Not GPIO 27. The button is
  populated, so it goes somewhere — possibly the WiFi module directly, in which
  case it is invisible to the AP entirely.)
* Whether the same 5 s long-press on GPIO 102 can be reproduced from OpenWrt,
  or whether it is purely an Android userspace policy.

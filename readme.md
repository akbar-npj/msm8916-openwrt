# MSM8916 OpenWrt

> **This file is a legacy pointer.** The authoritative, maintained project README
> is [`README.md`](README.md) (same directory) — read that first for the current
> device list, features, flashing instructions and package feed.

The project builds a production-ready OpenWrt port for Qualcomm Snapdragon 410
(MSM8916) based 4G LTE USB modems, dongles and pocket routers.

**Supported boards:** `hmu05`, `ufi001b`, `uz801` (v3), `uf02`, `mf800b`.

**Build environment (short version):** clone with submodules, then run the
interactive helper:

```bash
git clone --recurse-submodules https://github.com/akbar-npj/msm8916-openwrt.git
cd msm8916-openwrt
./build.sh
```

See [`README.md`](README.md) for flashing, recovery (EDL / Fastboot), Wi-Fi
(WPA3-SAE), Bluetooth, thermal/power and modem-stability details, and
[`Docs/BUILD_SYSTEM_GUIDE.md`](Docs/BUILD_SYSTEM_GUIDE.md) for the build system.

---

## Credits

    @ghosthgy - Initial project foundation
    @lkiuyu - MSM8916 support, patches, and OpenStick feeds
    @Mio-sha512 - USB gadget and firmware loader concepts
    @AlienWolfX - Carrier policy troubleshooting guide
    @gw826943555 & @asvow - Tailscale LuCI application
    @hkfuertes - For his work on bringing these devices to latest snapshot

This project builds upon the work of OpenWrt, msm8916-mainline, lk2nd,
qhypstub and qtestsign.

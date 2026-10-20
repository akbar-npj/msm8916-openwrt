# HMUF02-V5 branch changeset (vs `main`)

Status: **complete and verified on hardware** (2026-10-19)
Branch: `test/hmuf02v5-rename-and-version`
Commits: `8cd34fa` `2416e73` `0d49cc0` `75386bf` `52a0002` `a4da026`
Base: `main` @ `cda9d48` (six commits ahead)

This document is the reference for everything on this branch that differs from
`main`. It exists so the other docs (README, wiki, board guides) can be
updated from one accurate source instead of from commit archaeology.

---

## 1. Board rename: HMU05 → HMUF02-V5

The codename now matches the silkscreen (**HMUF02-V5**).

| Item | Change |
|---|---|
| DTS | `msm8916-generic-hmuf02v5.dts`, compatible `hmuf02v5,250605v0s` |
| Device | `DEVICE_generic-hmuf02v5`, `DEVICE_MODEL "HMUF02-V5"` |
| diffconfig | `diffconfigs/hmu05` → `diffconfigs/hmuf02v5` (and it now selects `DEVICE_generic-hmuf02v5` — the old file still selected the hmu05 device and would have built the wrong image) |
| Patches | `805`, `847`, `849` renamed; `801`/`833` compatible strings updated |
| Runtime globs | `himi-ok`, `modem-led-monitor`, `qcom-carrier-autocfg`, `qcom-time-daemon` match **both** `hmuf02v5` and `hmu05` |

**Intentionally kept for compatibility:**
- `SUPPORTED_DEVICES` still contains `hmu05,250605v0s`, so a sysupgrade from an
  older image is accepted.
- The runtime `*hmu05*` globs stay, so a device flashed with an older image
  keeps working after a config-preserving upgrade.
- Historical documents and descriptive driver comments still say HMU05.

**Not touched:** `uf02,250605v0s` is a *different* board that already existed in
the repo. It is not an alias for this one.

---

## 2. Release identification

Before this branch every image reported the stock OpenWrt version, because
`CONFIG_VERSION_*` symbols were dropped by `make defconfig` (they require
`CONFIG_VERSIONOPT=y`).

- New single source of truth: the `version` file (`25.12.5-r6`).
- `sync-version.sh` propagates it into every `diffconfigs/*`:
  `VERSION_DIST/NUMBER/CODE/REPO/HOME_URL/BUG_URL`, with `CONFIG_VERSIONOPT=y`.
- `CONFIG_VERSION_CODE` is empty so the version renders **once**
  (`25.12.5-r6`, not `25.12.5-r6 r6`).

Live check:

```sh
ubus call system board | jsonfilter -e @.release.version   # 25.12.5-r6
grep VERSION_ID /etc/os-release                            # 25.12.5-r6
```

Firmware filenames now carry the project prefix, e.g.
`msm8916-openwrt-msm89xx-msm8916-generic-hmuf02v5-squashfs-sysupgrade.bin`.
All CI globs were updated from `openwrt-msm89xx-*` to `*-msm89xx-*`.

---

## 3. Maintainer policy

New files: `CODEOWNERS`, `MAINTAINERS.md`.

- UZ801 (`803`, `851`, `diffconfigs/uz801`) and UFI003 (`852`,
  `diffconfigs/ufi003`) are routed to their board maintainer.
- `msm8916.mk` and everything else is routed to the lead.
- `MAINTAINERS.md` documents duties, the naming convention, the rename history,
  and how to identify/bump the installed release.

---

## 4. Automatic physical SIM hot-swap (the main feature)

Commit `75386bf` (mechanism), `52a0002` (latency tuning).

### 4.1 Why it was broken

- **No AP-side card-detect line.** GPIO114 `sim_hotplug` is an **output** (the
  AP drives it LOW = "card present" and toggles it to make the baseband re-read
  the card). GPIO119 `esim1_en` is the SIM rail enable.
- **ModemManager cannot detect a swap.** Its `qcom-soc` plugin reads the SIM
  once at modem enable and then caches it. After a pull, `mmcli` still reports
  the old IMSI/ICCID and `/SIM/9` never disappears; only
  `modem.generic.state` degrades to `searching`.
- **The firmware has `feature_support_hotswap` disabled.** It notices a
  **removal** (the card stops answering → UIM returns
  `error: possibly-removed (6)` on **stderr**), but it **never notices an
  insertion**. Measured: with the card physically inserted, UIM kept answering
  `possibly-removed` for minutes; **one `sim_hotplug` pulse** made it report
  `present` in <5 s and the modem registered ~10 s later.

That asymmetry is the whole design: the AP must **pulse while the card is
believed absent**. "Wait for UIM present, then pulse" is impossible.

### 4.2 The service

`/usr/sbin/sim-hotplug` + `/etc/init.d/sim-hotplug` (`START=98`),
shipped with `/etc/rc.d/S98sim-hotplug` so it survives sysupgrade.
Config: `/etc/config/sim-hotplug`.

| State | Behaviour |
|---|---|
| card present + modem registered | one UIM check per `idle_interval` (60 s) |
| card present + modem searching | one UIM check per `active_interval` (5 s) |
| card believed absent | pulse → settle 4 s → check, every `absent_interval` (6 s) |

On `absent → present`:

1. wait for registration (up to 60 s),
2. `mmcli -d` + `-e` so ModemManager re-reads the SIM (this is what makes a
   **different** card's IMSI/APN visible to `qcom-carrier-autocfg`),
3. `ifup modem` (under the shared bearer lock).

### 4.3 Latency (measured on hardware)

```
16:08:10  SIM removed (UIM absent)
16:08:17  SIM detected after re-announce      <- 7 s
16:08:22  modem registered
16:08:25  modem re-registered after SIM re-read
16:08:25  bringing the modem bearer up
          -> new IP, ping 0% loss
```

~15 s end to end for a ~4 s swap (was ~35 s before tuning). Removal detection
is 5–7 s; insertion detection is ≤10 s (6 s cycle + 4 s settle).

### 4.4 Deliberate constraints

- **`searching` alone never means "swap".** It is only used to pick the polling
  cadence. A train/car/reselection therefore triggers nothing.
- **Never pulse a card that is already present** — that keeps the baseband from
  settling (observed: modem stuck `searching` while pulsing every 5 s).
- **QMI hygiene:** always `qmicli --device-open-proxy` (share the port with MM
  through qmi-proxy), one process at a time (busybox `flock`, which has no
  `-w`), always under `timeout`. Concurrent/long-lived clients exhaust the
  modem's client-ID pool and leave MM failed with `ClientIdsExhausted`.
- **No `mmcli -r` and no SSR** from this service.

### 4.5 Device tree

Patch `805`: GPIO114 is now a `gpio-leds` node
(`/sim-presence/sim_hotplug`) instead of a `gpio-hog`, exposing
`/sys/class/leds/sim_hotplug/brightness` exactly like stock Android.

> **Board scope.** This node is defined **only in the HMUF02-V5 DTS today**.
> The other boards (`uz801`, `ufi001b`, `ufi003`, `uf02`, `mf800b`) do not
> expose a `sim_hotplug` node, but their hardware **may** still have the line —
> it is **untested**. The service ships in the shared `base-files` and cannot
> pulse where no node exists. Device owners can verify with the test procedure
> in [`Docs/SIM_HOT_SWAP.md`](SIM_HOT_SWAP.md#testing-on-another-board).

### 4.6 Known limitation

A swap shorter than the modem's own removal debounce can be **invisible**: the
modem keeps its cached card and stays `connected`, so UIM keeps answering
`present` and there is nothing for the AP to detect. Harmless when the same
card goes back in. If a *different* card is inserted that way, the stale
context persists until data fails (the bearer watchdog then does an SSR, which
re-initialises UIM) or until `/etc/init.d/sim-hotplug rescan`.

### 4.7 Manual use

```sh
sim-hotplug --help
/etc/init.d/sim-hotplug status
/etc/init.d/sim-hotplug rescan      # force one re-announce now
logread -e sim-hotplug
```

---

## 5. `--help` for every custom program

Commit `a4da026`. Before this, 12 commands printed nothing and 6 printed a bare
usage line only when called wrongly.

| Program | Before | Now |
|---|---|---|
| `apk-key-refresh` | none | full help |
| `himi-ok-guard` | none | full help + uci keys |
| `modem-bearer-watchdog` | none | full help + all uci keys + log tag |
| `modem-led-monitor` | none | full help |
| `wifi-led-monitor` | none | full help |
| `qcom-carrier-autocfg` | none | full help + files/log |
| `msm-firmware-dumper` | none | full help + env vars |
| `diag-bind` | none | full help + output format |
| `diag-bind-watch` | none | full help + status file |
| `diag_nv` | prebuilt binary, usage only | wrapper + full help |
| `bt-spp` | none | full help |
| `reboot-mode-raw` | none | full help |
| `sim-hotplug` | usage only | full help |
| `usb-gadget` | usage only | full help |
| `bt-ssp-guard` | usage only | full help |
| `diag_efs` | usage only | full help |
| `diag_logtool` | usage only | full help |
| `qcom-time-daemon` | usage on bad option | `-h` + full help |
| `ats-probe` | `-h` | unchanged (+ `-h` listed) |
| `reboot-mode` | `-h` | unchanged |

Each help block lists the commands/options, the `uci` keys or environment
variables it honours, and the `logread -e <tag>` to watch. Note that
`modem-bearer-watchdog` logs as **`modem-stall-watchdog`**.

**`diag_nv` layout change:** the static binary moved to
`/usr/libexec/diag_nv.bin`; `/usr/sbin/diag_nv` is now a thin wrapper that
answers `-h/--help` and `exec`s it. `himi-ok-guard` calls the same path and is
unaffected.

Host-side scripts: `flash.sh` already had help; `generate_firmware.sh` and
`generate_squashfs_gpt.sh` are CI-internal and were not changed.

---

## 6. CI / packaging

- Cache keys and job labels: `diffconfigs/hmu05` → `diffconfigs/hmuf02v5`.
- Artifact globs: `openwrt-msm89xx-*` → `*-msm89xx-*` (the DIST prefix now
  leads the filename).
- `msm8916.mk`, `generate_squashfs_gpt.sh`, `tests/himi-ok-guard/run.sh`
  updated for the rename.

---

## 7. Verification record (2026-10-19)

Flashed with `sysupgrade -n` on HMUF02-V5:

| Check | Result |
|---|---|
| Firmware | `25.12.5-r5` / `r33051-f5dae5ece4` |
| `/usr/sbin/sim-hotplug` md5 | `e9974c754968991e581f08597a3de19c` (matches the repo) |
| Service | running, `SIM present at startup`, no spurious pulses |
| Modem | `connected`, IPv4 + IPv6 |
| Data | ping 3/3, 0% loss, DNS OK |
| SIM swap (same card) | detected and recovered in ~15 s, new IP, data back |
| `--help` | verified for all shell tools; binaries carry the new strings |
| Build | `./build.sh build hmuf02v5` → exit 0 |

`sysupgrade -n` wipes the overlay. The modem firmware in `/lib/firmware` lives
on the overlay and is **not** in the image; the device's first-boot automation
re-populates it, but a manual flash must account for this.

---

## 8. Upgrade notes

- A config-preserving `sysupgrade` keeps the overlay, including any stale
  `/overlay/upper/lib/modules/*.ko`; refresh from `/rom` rather than deleting
  (a whiteout hides the new image file).
- `sysupgrade` from an image older than this branch still works
  (`hmu05,250605v0s` remains in `SUPPORTED_DEVICES`).
- `/etc/config/sim-hotplug` is a new conffile; a fresh flash gets the defaults
  (idle 60 / active 5 / absent 6 / pulse 300).

---

## 9. Open items

1. **Different-SIM APN switch** is implemented (MM `-d`/`-e` re-read +
   `qcom-carrier-autocfg`) but has not been exercised with a second card.
2. **Sub-debounce swaps** are invisible by nature (see §4.6). An opt-in
   periodic re-announce was considered and rejected (it blips the connection).
3. The remaining boards (`mf800b`, `uf02`, `ufi001b`, `ufi003`, `uz801`)
   have not been rebuilt/flashed with this branch.

---

## Appendix — file index

```
version                                             new
sync-version.sh                                     version propagation
CODEOWNERS, MAINTAINERS.md                          new
diffconfigs/hmu05 -> diffconfigs/hmuf02v5           rename + device fix
diffconfigs/{mf800b,uf02,ufi001b,ufi003,uz801}      version block
msm89xx/Makefile                                    rename
msm89xx/image/msm8916.mk                            rename
msm89xx/patches/805,847,849                         renamed (DTS)
msm89xx/patches/801,833                             compatible strings
msm89xx/base-files/usr/sbin/sim-hotplug             new
msm89xx/base-files/etc/init.d/sim-hotplug           new
msm89xx/base-files/etc/rc.d/S98sim-hotplug          new (symlink)
msm89xx/base-files/etc/config/sim-hotplug           new
msm89xx/base-files/usr/sbin/diag_nv                 wrapper (was ELF)
msm89xx/base-files/usr/libexec/diag_nv.bin          moved ELF
msm89xx/base-files/usr/sbin/{apk-key-refresh,himi-ok-guard,
  modem-bearer-watchdog,modem-led-monitor,wifi-led-monitor}   --help
msm89xx/base-files/lib/himi-ok.sh                   board list
msm89xx/base-files/etc/config/modem-watchdog        comments
msm89xx/base-files/etc/uci-defaults/97-himi-ok      comments
msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot  hostname
packages/{diag-bind,bt-ssp-guard,msm-firmware-dumper,
  qcom-carrier-autocfg,uci-usb-gadget}              --help
packages/{diag-efs,diag-logtool,bt-spp,
  qcom-time-daemon,reboot-edl,ats-probe}/src        --help
scripts/openwrt-prepare.sh                          LuCI branch metadata
.github/workflows/*                                 rename + globs
tests/himi-ok-guard/run.sh                          label
readme.md, README.md                                rename
```

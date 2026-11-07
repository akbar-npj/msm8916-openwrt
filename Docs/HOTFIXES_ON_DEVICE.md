# Hotfixes on the device

This explains what you see on the router itself: the **firmware-upgrade
page** in LuCI and the **"A fix for your build is available"** dashboard
banner. It covers where hotfixes come from, why a fix appears (or disappears)
on that page, and — most usefully — why a fix can *still be listed after you
installed it*, and what to do about it.

Publishing a hotfix (the maintainer side) is covered separately in
[`RELEASING_FIRMWARE.md` §3.1](RELEASING_FIRMWARE.md). This document is the
device/operator view.

## What a "hotfix" is

A hotfix is a single package rebuilt for your exact firmware image and pushed
through the signed package feed. It ships **without a new image / sysupgrade**.
Examples from this project:

- `msm89xx-scripts` — the LED monitors and the Wi-Fi station watchdog
- `luci-app-wifi-sta` — the station-mode web page
- `msm89xx-firmware-upgrade` — the upgrade tool + the dashboard banner logic
- `base-files`, `kernel`, `libc` — pinned; handled specially (see below)

Each package carries a `PKG_RELEASE` ("rN"). A hotfix is just a higher `rN`
of an existing package, e.g. `msm89xx-scripts 1.0-r11`.

## Where the device looks

A device running image `25.12.6-r8` reads its own revision feed:

```
https://<pages>/releases/25.12.6-r8/
├── hotfix.json                 # the list of available hotfixes for THIS build
└── packages/aarch64_generic/
    ├── base/packages.adb       # the apk index (what apk can install)
    └── luci/packages.adb
```

Two files matter:

- **`hotfix.json`** — *what is offered*. The firmware-upgrade page and the
  banner call `firmware-upgrade hotfix check`, which downloads this file and
  lists every entry whose version is **newer than what is installed**.
- **`packages.adb`** — *what can actually be installed*. When you press
  **Install**, the tool runs `apk add pkg=version --repository <packages.adb>`.
  The version must exist in this index or the install fails.

## How the banner/page decides what to show

The firmware-upgrade page lists a hotfix only if it is **newer than the
installed version**. So:

- Already-installed hotfixes (e.g. `msm89xx-firmware-upgrade 1.0-r19` already
  on the device) are **correctly hidden** — they are "done".
- New hotfixes not yet installed (e.g. `msm89xx-scripts 1.0-r11`) **appear**
  as available.

The dashboard banner is fed by `update-notify`, which calls the same check and
caches the result to `/tmp/hotfix-notify.json`. `update-notify` runs
**every 6 hours** (INTERVAL = 21600 s), so the banner can lag the page by up to
6 hours — but it refreshes immediately if you restart it:

```sh
/etc/init.d/update-notify restart
```

The page itself calls the check live, so it is usually fresher than the banner.

## Installing

Press **Install** on the firmware-upgrade page, or on the command line:

```sh
apk update
apk upgrade msm89xx-scripts          # for non-pinned packages
# pinned packages (base-files/kernel/libc) instead need an explicit version:
# apk add 'base-files=1719~981c978eff'
```

After a successful install the entry disappears from the list (it is no longer
"newer than installed").

## "I installed it but it still shows" — troubleshooting

This is the most common confusion. In order of likelihood:

### 1. The install silently failed because the CDN index was stale

When a feed is repaired or re-indexed, the corrected `packages.adb` is committed
to `gh-pages`, but **GitHub Pages must deploy it** before your router can fetch
it. Until then the public `packages.adb` still lists the old version, so
`apk add pkg=1.0-r11` cannot find `r11` and **fails without installing
anything** — the entry stays on the page.

This also happens if the GitHub Pages deployment itself fails (e.g. it gets
stuck in `deployment_queued` and times out). The git commit can be correct
while the CDN still serves the old file.

**Check it:**

```sh
apk list -I msm89xx-scripts          # what is ACTUALLY installed?
```

If it still shows the old `rN`, the install did not take. **Fix:** wait for the
Pages deployment to complete, then `apk update` and install again. You can watch
the deployment under the repo's *pages-build-deployment* workflow runs.

### 2. The banner is showing a 6-hour-old cache

The dashboard banner reads `/tmp/hotfix-notify.json`, refreshed by
`update-notify` only every 6 hours. Even after a successful install, the banner
may keep nagging until the cache refreshes.

**Fix:**

```sh
/etc/init.d/update-notify restart    # regenerates the cache now
```

The firmware-upgrade **page** queries live, so it usually updates first; trust
the page over the banner.

### 3. It really is already installed (expected behaviour)

If `apk list -I <package>` reports the hotfix version, the job is done — the
page hides it by design. No action needed.

### 4. Stuck in a half-installed state

A hotfix whose post-install script restarted a service, or a package that
wrote a file `apk` could not (e.g. base-files writing to the read-only
`/rom`), can report an error while actually installing. Judge by the installed
version (`apk list -I`), not the exit code.

## Verifying an install really happened

```sh
apk list -I msm89xx-scripts                                 # expect 1.0-r11
opkg ... # n/a
# for a file-level check, inspect the installed files:
ls -l /usr/sbin/wifi-sta-up                                # scripts package
```

## Key takeaways

- The **page lists** what `hotfix.json` offers and is newer than installed.
- The **install succeeds** only if `packages.adb` on the CDN actually contains
  that version. `hotfix.json` and `packages.adb` deploy independently, so it is
  possible to *see* a fix you cannot yet *install*.
- `hotfix.json` carries **one entry per package** — the newest build, which is
  cumulative. If you ever see two entries for the same package (e.g.
  `msm89xx-scripts` at both `1.0-r9` and `1.0-r14`) and the install fails with
  `breaks: world[<pkg>=<oldver>]`, the older entry is the culprit: the index only
  carries the newest version, so pinning the old one is unsatisfiable. The
  publish workflow now drops the older entry automatically; the fix is to remove
  it from `hotfix.json` (merging its `restart` services into the newest entry).
- After installing, an entry vanishing from the page = success; it lingering
  usually means the install failed (stale CDN index) or the banner is showing a
  stale 6-hour cache.
- `apk list -I <pkg>` is the source of truth.

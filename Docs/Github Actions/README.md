# GitHub Actions CI/CD — Architecture & Operations Guide

How this project builds firmware, ships packages and delivers hotfixes through GitHub Actions.

Everything runs on GitHub-hosted Ubuntu runners. There is no self-hosted infrastructure and no
state anywhere except two places:

- **GitHub Releases** — immutable firmware images people download and flash.
- **The `gh-pages` branch** — a git branch served as a website at
  `https://akbar-npj.github.io/msm8916-openwrt`, which devices read with `apk`.

Everything else in this document is bookkeeping around those two.

---

## 1. The three pipelines

There are five workflow files, but only three things you can actually *do*:

| Pipeline | Workflow | Builds | Publishes to | Roughly |
|---|---|---|---|---|
| **Firmware** | `build-and-release.yml` | every board's images + the whole feed | GitHub Release + `gh-pages` | 30-45 min |
| **Package** | `publish-package.yml` | one package | the shared `gh-pages` feed | 7-35 min |
| **Hotfix** | `publish-package.yml` (`scope=revision`) | one package | that image's own feed dir + `hotfix.json` | 7-35 min |
| **Kernel module** | `publish-kmod.yml` | the kernel + selected `kmod-*` | the target feed | 20-40 min |

All four have the same shape: **build the thing -> sign the package index -> push it where devices
look.** The differences are only *what* is built and *which directory* it lands in.

### Where things land

```text
GitHub Releases
  v25.12.6-r4/
    <board>-squashfs-boot.img, -system.img, -sysupgrade.bin, -firmware.zip
    recovery-*.img, kmods-msm8916-<version>.tar.gz, sha256sums

gh-pages branch  (served as https://akbar-npj.github.io/msm8916-openwrt)
  firmware.json                                  <- update manifest, all boards
  public-key.pem                                 <- stable feed signing key
  releases/25.12.6/                              <- SHARED by every 25.12.6 device
    packages/aarch64_generic/{base,luci,packages,routing}/
    targets/msm89xx/msm8916/packages/            <- kernel + kmod-*
  releases/25.12.6-r4/                           <- ONLY devices running r4
    ...same layout...
    hotfix.json                                  <- fixes offered to r4 devices
```

### The two feed scopes

This is the part worth internalising:

```text
releases/25.12.6/       every device on the 25.12.6 base reads this
                        written by: full releases, and package builds (scope=base)

releases/25.12.6-r4/    only devices running exactly r4 read this
                        written by: hotfix builds (scope=revision)
```

A fix compiled against image X must never reach image Y, which is why hotfixes get their own
directory. A *package* is normally architecture-independent, so it can safely go to the shared
directory and reach every device on that base version.

### Which pipeline do I need?

- Changed the kernel, a driver, a board config, or the image contents -> **firmware build**.
- Fixed a shell script or a LuCI page -> **package build**.
- Fixed something that must only reach devices already on this exact image -> **hotfix build**.
- Added or changed a kernel module -> **kernel module build**.

---

## 2. Firmware build — `build-and-release.yml`

Triggered by pushing a tag (`v*`) or manually from the Actions page.

```yaml
jobs:
  setup:    # resolve tag / version / feed base + the board matrix
  build:    # one runner per board, in parallel
  publish:  # assemble artifacts, publish the feed, cut the release
```

### Inputs

| Input | Default | Notes |
|---|---|---|
| `release_tag` | derived from `version` | `v25.12.6-r4` |
| `boards` | empty = all eight | space-separated, for a targeted fix release |

### Concurrency

```yaml
concurrency:
  group: release-build
  cancel-in-progress: false
```

Releases are serialised: every run rewrites the shared `gh-pages` feed and `firmware.json`, so two
overlapping runs would clobber each other. `cancel-in-progress` is **false** so a queued release
waits rather than being killed by a later dispatch.

> **Dispatch one release at a time.** GitHub keeps only **one pending run** per concurrency group,
> so a third dispatch silently cancels the queued one. It never shows up as a failure. Check
> `gh run list` before dispatching again.

### Job `setup`

1. Reads `version` (`25.12.6-r4`) and derives `base` by stripping the `-rN` suffix. Devices read
   `releases/<base>`, so that path must be published too.
2. Resolves the tag: input, else the pushed tag, else `v<version>`.
3. Turns `boards` into a JSON matrix. Empty means every `diffconfigs/*` entry; an unknown board
   name fails immediately and lists the valid ones.

### Job `build` — one runner per board

`fail-fast: false`, so one broken board does not cancel the other seven.

| Step | What it does |
|---|---|
| Checkout | `fetch-depth: 0` for tag metadata |
| Free disk | drops .NET/MySQL/PHP/Android SDK, ~35 GB back |
| Restore cache | `actions/cache/restore` only — see section 8 |
| Prepare + key | `./build.sh prepare`, then writes `APK_SIGNING_KEY` to `openwrt/private-key.pem` |
| Build `<board>` | `./build.sh build <board>` |
| CUPS | first board only — the package feed is target-wide, not per-board |
| Save cache | first board only |
| Upload | `board-<name>` artifact, plus `feeds` from the first board |

The signing key is written **after** `prepare`: a fresh prepare clones the OpenWrt tree and would
delete a `private-key.pem` placed there beforehand.

### Job `publish`

The only job that writes anything public.

| Step | What it does |
|---|---|
| Download artifacts | reassembles every board + the package feed |
| Frozen recovery images | re-attached unchanged from `v25.12.6-r4` (they embed git-ignored firmware) |
| Kmod bundle + checksums | `kmods-msm8916-<version>.tar.gz` and `sha256sums` |
| Merge checksums | splices into the published file — see below |
| `firmware.json` | merges into the published manifest |
| Feed -> gh-pages | writes `releases/<base>` and `releases/<version>` |
| Prune | deletes kernel/kmod files this build did not produce |
| GitHub Release | attaches every image and the checksum file |

**Why the merges matter.** A targeted release (`boards=uf02 uz801`) builds two boards. The manifest
and the checksum file are shared by all eight, so both are downloaded, edited and re-uploaded rather
than regenerated — otherwise the other six boards would silently disappear from the release metadata.

**Why the prune matters.** The feed directory is additive: `keep_files: true` preserves anything a
deploy does not mention, and every release adds another kernel generation with its own `kmod-*` set.
Left alone the directory grows forever, and because `apk list -a` prints in index order rather than
version order, a device could be told its own kernel was missing.

### Release artifacts

| File | Use |
|---|---|
| `*-squashfs-boot.img` | boot image (kernel + DTB) — fastboot / EDL |
| `*-squashfs-system.img` | read-only SquashFS rootfs — fastboot / EDL |
| `*-squashfs-sysupgrade.bin` | sysupgrade archive — LuCI or CLI |
| `*-firmware.zip` | full firmware + partition script — unbricking |
| `kmods-msm8916-<version>.tar.gz` | offline copy of the target feed |
| `recovery-<board>.img` | frozen rescue image, boards with a recovery partition |
| `sha256sums` | checksums for all of the above |

---

## 3. Package build — `publish-package.yml`

One package, no images, no reboot. This is how a fix reaches devices without a reflash.

```sh
gh workflow run publish-package.yml --ref main -f package=msm89xx-scripts
# on the device:  apk update && apk upgrade msm89xx-scripts
```

### Inputs

| Input | Default | Notes |
|---|---|---|
| `package` | — | directory name under `packages/` |
| `base` | from `version` | feed base, i.e. `25.12.6` |
| `bump` | `true` | increments `PKG_RELEASE`; without it devices see the same version and do nothing |
| `board` | `hmuf02v5` | only matters for `kmod-*`, which are vermagic-locked |
| `target_package` | `false` | for packages built into the target (e.g. `base-files`) |
| `pkg_release` | — | explicit release for a target package |
| `skip_kernel` | `false` | skip the kernel tree for packages with no kernel modules |
| `scope` | `revision` | `revision` = that image only, `base` = every device on the base |
| `delete` | — | `.apk` files to remove from the feed |
| `hotfix_title` | — | non-empty turns this into a hotfix — see section 4 |
| `hotfix_restart` | — | init scripts to restart after install |

### What it does

| Step | What it does |
|---|---|
| Resolve | validates the package and the feed base |
| Bump | `PKG_RELEASE` += 1 (unless `bump=false`) |
| Prepare | Docker image + OpenWrt tree |
| **Fetch the published feed** | restores `releases/<base>` from `gh-pages` into `openwrt/bin/` |
| Build | just that package |
| Re-index + re-sign | regenerates `packages.adb` and signs it with `APK_SIGNING_KEY` |
| Verify | refuses to publish if the package count dropped |
| Publish | copies into `gh-pages` and pushes |

### Why the feed is restored first

`make package/index` indexes **whatever is in the build tree**. On a fresh tree that is exactly one
package, so the index it produces would describe one package and every other package would vanish
from `apk search` / `apk add` on every device. The job therefore downloads the currently published
feed first, adds the rebuilt package to it, and re-indexes the whole thing. The "verify nothing fell
out" step is the guard that this actually happened.

### Why it re-signs

`apk` verifies every `packages.adb` against a public key in `/etc/apk/keys/`. OpenWrt generates a
**random key per build** unless `openwrt/private-key.pem` already exists, so a job that skips the key
produces a feed every device rejects with `UNTRUSTED signature`. Both CI workflows write the
`APK_SIGNING_KEY` secret to that path and fail hard if it is unset.

---

## 4. Hotfix build — the same workflow, `scope=revision`

A hotfix is not a separate workflow. It is `publish-package.yml` with two inputs set:

```sh
gh workflow run publish-package.yml --ref main \
  -f package=msm89xx-firmware-upgrade -f bump=false \
  -f skip_kernel=true -f scope=revision \
  -f hotfix_title="what this fixes"
```

| | Package build | Hotfix build |
|---|---|---|
| Lands in | `releases/25.12.6/` (shared) | `releases/25.12.6-r4/` (that image only) |
| Also writes | — | `hotfix.json` |
| Offered by | `apk upgrade` | `firmware-upgrade hotfix check` |

### The manifest

```json
{
  "build": "25.12.6-r4",
  "hotfixes": [
    { "id": "2026-11-02-msm89xx-firmware-upgrade",
      "package": "msm89xx-firmware-upgrade",
      "version": "1.0-r13",
      "title": "...",
      "restart": [],
      "published": "2026-11-02" }
  ]
}
```

The URL is the applies-to tag, so a fix published here can never reach a different image. Publishing
the same `id` again replaces the entry rather than duplicating it.

### Device side

`firmware-upgrade hotfix check` fetches `releases/<installed version>/hotfix.json`, compares each
entry against what is installed, and reports the ones that are newer. `hotfix apply` installs them
from the same revision directory.

Two traps worth knowing:

- **A wrong clock breaks it silently.** `wget` rejects the TLS certificate, the fetch fails, and the
  command returns 1. It now logs the URL and the `wget` error and names the clock when the error
  looks like TLS, but check `date -u` first if a check fails for no obvious reason.
- **The channel cannot fix its own installer.** If the bug is in `firmware-upgrade` itself, the
  broken copy is the one running `hotfix apply`. A device needs one manual install or a new image.

### What a hotfix cannot do

- It cannot change the kernel. A `kmod-*` built for one kernel only loads on that kernel.
- It cannot be scoped per board. The manifest is keyed by build version, not by device.

---

## 5. Kernel modules — `publish-kmod.yml`

Kernel modules are the awkward case: a module only loads into the exact kernel it was compiled
against. That identity is the **vermagic**, and it is part of the kernel package version:

```text
kernel-6.12.112~8efa871abf779b5591d7c27483980dfd-r1
              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ vermagic
```

Every `kmod-*` declares `kernel (=<version>~<vermagic>-r<N>)` as a dependency, so a module from a
different build is not "probably fine" — `apk` refuses it outright.

The vermagic is an md5 of the kernel config's `=y`/`=m` lines, so it changes whenever anything in
the kernel config changes.

### The workflow

```sh
gh workflow run publish-kmod.yml --ref main \
  -f kmods="kmod-ifb kmod-sched" -f checkout_ref=v25.12.6-r4 -f dry_run=true
```

| Step | What it does |
|---|---|
| Validate | resolves the feed base and the requested module list |
| Prepare | Docker image + OpenWrt tree at `checkout_ref` |
| Optionally bump | `LINUX_RELEASE`, so devices see a newer version |
| Fetch the published targets feed | so the index is complete afterwards |
| **Build the kernel** | populates `Module.symvers` and the configured tree |
| Build the modules | only the requested `kmod-*` |
| Verify | every requested module was actually produced |
| Re-index + re-sign | regenerates and signs `packages.adb` |
| Publish | pushes to `gh-pages` |

`checkout_ref` must be the release that produced the published kernel. Building from a different
tree gives a different vermagic and modules nobody can install. `dry_run=true` builds and verifies
but stops before pushing — use it the first time.

### The curated build-only set

Because `msm89xx` is not an upstream OpenWrt target, there is no upstream kmod feed to fall back on:
whatever this project does not compile simply cannot be installed.

By default OpenWrt only builds the kmods selected in the image plus their dependencies, which left
the feed incomplete — `apk add kmod-usb-net-rtl8152` failed even though the README suggested it.
Every `diffconfigs/<board>` therefore appends a curated block:

```text
CONFIG_PACKAGE_kmod-usb-net-rtl8152=m
```

`=m` is the point: the module is **compiled and published into the feed**, but **not installed into
the image**. Users install it on demand with `apk add <name>`.

> `CONFIG_ALL_KMODS=y` would build everything, but each kmod package `select`s its kernel
> dependencies, so it also forces ~227 extra **built-in** subsystems (cgroups/MEMCG, media/DVB/V4L2,
> DRM + fbcon, MPTCP, NFSD, ...) into a kernel that is deliberately tuned for this device. The
> curated set pulls in only modest USB/network/storage subsystems.

To extend the feed, add `CONFIG_PACKAGE_kmod-<name>=m` to `diffconfigs/<board>`. To confirm a
module is published, look for `kmod-<name>-*.apk` under `packages/`.

---

## 6. Auxiliary workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yml` | manual | build one board (or all) and upload the images as artifacts. Publishes nothing — for "does it still compile?" |
| `tests.yml` | push, pull request, manual | runs `tests/*/run.sh` (currently `himi-ok-guard`). Touches nothing else |

`tests.yml` is keyed on the branch name rather than the ref, so a push to a branch with an open pull
request runs the suite once instead of twice.

---

## 7. The feed on `gh-pages`

### URLs devices actually use

```text
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6/targets/msm89xx/msm8916/packages/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6/packages/aarch64_generic/base/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6/packages/aarch64_generic/packages/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6/packages/aarch64_generic/luci/packages.adb
https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6/packages/aarch64_generic/routing/packages.adb
```

Written at first boot by `msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot`. The upstream
OpenWrt feeds are added alongside them for the package sets this project does not build (notably
`telephony`).

There is no `CONFIG_VERSION_REPO` in this tree — the URLs are written literally by that firstboot
script.

### The signing key

OpenWrt signs every `packages.adb`. A device rejects a feed whose key it does not have, with
`UNTRUSTED signature`.

- **Secret:** `APK_SIGNING_KEY` holds the EC P-256 private key (PEM).
- **CI:** written to `openwrt/private-key.pem`; the job fails if the secret is unset.
- **Image:** the matching public key is embedded at `/etc/apk/keys/akbar-npj-v25.12.6.pem` and
  re-created by `99-msm89xx-firstboot`.
- **Published:** copied to every release dir and to the stable path
  `https://akbar-npj.github.io/msm8916-openwrt/public-key.pem`.

`apk-key-refresh` fetches that stable URL at boot and installs it when it differs from the on-disk
key, so a device carrying a stale key recovers without a reflash. It also warns when the feed no
longer offers the device's own kernel — the condition that actually breaks kmod installs.

> **Rotating the key:** generate a new EC P-256 pair, update the secret, replace the tracked public
> key in `msm89xx/base-files/etc/apk/keys/` **and** the heredoc in `99-msm89xx-firstboot`, then cut
> a release.

### Pruning

After each release the job re-clones `gh-pages` and deletes every `kernel-*.apk` / `kmod-*.apk`
that the current build did not produce. Other packages are untouched, because a hotfixed
`base-files` must survive a later release. The push retries with a rebase: the package workflows
write to the same branch under their own concurrency groups.

### The base-files version floor

Every image writes a line into `/etc/apk/world`:

```text
base-files=1719
```

That is a lock, and it is how a new image supersedes a hotfix: `apk upgrade` will not move a
locked package, so the image's own number is what wins after a flash.

**apk compares numbers, not contents.** Two consequences:

1. Re-publishing the same number with different bytes is a **silent no-op**. A device that already
   has `1720` sees "already installed", prints OK, and the fix never arrives. Nothing errors
   anywhere.
2. An image that ships a *lower* number than the hotfix channel can never supersede that hotfix.

Upstream derives the number from `COMMITCOUNT` — commits touching `package/base-files` — which
does not move between our releases. Every image we built shipped `1719` while the hotfix channel
had already served `1720`.

**The floor.** `openwrt-patches/812-base-files-release-floor.patch` replaces that line with

```make
BASE_FILES_RELEASE_FLOOR:=1721
PKG_RELEASE:=$(shell echo $$(( ... )))
```

so the package is `max(COMMITCOUNT, floor)`. Upstream's count takes over again once it passes the
floor, so this does not fork us from upstream permanently.

**The high-water mark.** `publish-package.yml` writes
`releases/<base>/published-versions.json` on every publish:

```json
{ "updated": "2026-11-03", "packages": { "base-files": "1720~981c978eff" } }
```

It is rebuilt from scratch each run — every `hotfix.json` already on `gh-pages`, then the existing
file, then this build — highest version wins. So it self-seeds on the first run and can never move
backwards.

**The guard.** The `setup` job of `build-and-release.yml` reads that file and **fails the release**
if the tree's floor is not strictly higher than the published `base-files`. Ten seconds, before
any runner starts, instead of an image nobody can hotfix.

> When it fires, bump `BASE_FILES_RELEASE_FLOOR` in
> `openwrt-patches/812-base-files-release-floor.patch` to one above the published version and
> commit that with the release. That is the whole procedure — the number is meant to be bumped by
> hand, at a release, deliberately.

### DRY_RUN

Both workflows take `dry_run`:

- **`publish-package.yml`** — everything runs for real (build, splice, index, manifests, the
  high-water mark) and the step prints the staged diff, then stops before the commit and push.
  Nothing leaves the runner, so it is safe against a live feed.
- **`build-and-release.yml`** — the floor check still runs and reports, but as a `::warning::`
  instead of failing the release.

```sh
gh workflow run publish-package.yml --ref main \
  -f package=base-files -f target_package=true -f pkg_release=1722 \
  -f scope=revision -f hotfix_title="..." -f dry_run=true
```

---

## 8. Caching

Three directories are cached, roughly 4.5 GB together:

1. `openwrt/dl` — upstream tarballs.
2. `openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl` — the cross-toolchain.
3. `openwrt/staging_dir/host` — host build tools.

```yaml
- name: Restore OpenWrt Cache
  uses: actions/cache/restore@v4
  with:
    path: |
      openwrt/dl
      openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl
      openwrt/staging_dir/host
    key: openwrt-cache-${{ needs.setup.outputs.version }}-${{ hashFiles('openwrt/feeds.conf.default', 'diffconfigs/hmuf02v5') }}
    restore-keys: |
      openwrt-cache-${{ needs.setup.outputs.version }}-${{ hashFiles('openwrt/feeds.conf.default', 'diffconfigs/hmuf02v5') }}-
      openwrt-cache-${{ needs.setup.outputs.version }}-
      openwrt-cache-
```

Three rules keep this affordable:

1. **No `${{ github.run_id }}` in the key.** A run-scoped key can never be restored, so every run
   saved a fresh ~2.3 GB copy. Four of those fill the 10 GB budget, entries get evicted, and later
   runs silently fall back to a stale prefix.
2. **Restore-only in the matrix, save once.** Eight parallel jobs each pushing 2.3 GB would blow the
   budget in one run. The matrix uses `actions/cache/restore`; only the `first` board job runs
   `actions/cache/save`.
3. **The hash input must exist.** An earlier key hashed `diffconfigs/hmu05`, a board that no longer
   exists — `hashFiles` silently returns an empty hash for a missing path, so the key was constant.

Cold cache is about 75 minutes for a full release. Warm, the toolchain and `dl/` restore in a couple
of minutes and the boards build in parallel, so wall-clock is roughly one board's build.

---

## 9. Operations

### Cut a release

```sh
# from main, with version already bumped in ./version
gh workflow run build-and-release.yml --ref main
sleep 15 && gh run list --workflow=build-and-release.yml --limit 1
```

### Replace a pre-release tag

```sh
git tag -f v25.12.6-rc1
git push origin v25.12.6-rc1 --force
```

The tag push triggers a full run; release assets with an existing name are replaced.

### Verify a release

```sh
curl -fsS https://akbar-npj.github.io/msm8916-openwrt/firmware.json | jq '.release, (.boards | keys)'
curl -fsS https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6-r4/hotfix.json | jq .
```

### Troubleshooting

| Symptom | Cause |
|---|---|
| `UNTRUSTED signature` on `apk update` | feed signed with a key the device lacks — check `APK_SIGNING_KEY` |
| `hotfix check` returns 1 with no output | fetch failed; usually a wrong clock (`date -u`) |
| `unable to select packages` on `hotfix apply` | the package is in a feed that was not passed as `--repository` |
| `apk search` shows almost nothing | an index was regenerated from a tree that was not restored first |
| kmods refuse to install | the feed's kernel vermagic differs from the device's kernel |
| a queued release vanished | a third dispatch replaced it — see section 2 |
| `base-files floor is N but … is already published` | the hotfix channel moved past the image line — bump `BASE_FILES_RELEASE_FLOOR` (section 7) |
| `Cannot read …/published-versions.json` | the high-water mark does not exist yet — publish any package once, or check gh-pages |

---

## 10. Files

| Path | What |
|---|---|
| `.github/workflows/build-and-release.yml` | firmware pipeline |
| `.github/workflows/publish-package.yml` | package + hotfix pipeline |
| `.github/workflows/publish-kmod.yml` | kernel module pipeline |
| `.github/workflows/build.yml` | manual throwaway build |
| `.github/workflows/tests.yml` | shell test suite |
| `diffconfigs/<board>` | per-board OpenWrt config, incl. the curated kmod list |
| `msm89xx/base-files/etc/uci-defaults/99-msm89xx-firstboot` | writes the feed URLs and the key |
| `msm89xx/base-files/usr/sbin/apk-key-refresh` | key self-heal + kernel check |
| `packages/msm89xx-firmware-upgrade/` | `firmware-upgrade`, incl. `hotfix check/apply` |
| `openwrt-patches/812-base-files-release-floor.patch` | the `base-files` version floor |
| `gh-pages: releases/<base>/published-versions.json` | highest version ever published per package |
| `scripts/gen-firmware-manifest.py` | builds `firmware.json` |
| `build.sh` | local build driver used by CI |

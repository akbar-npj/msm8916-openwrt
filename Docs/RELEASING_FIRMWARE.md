# Releasing a Firmware Build

The runbook for cutting a release. It covers the branch model, the three files
that carry a version, what CI does, and how to verify — or replace — a release
that went out wrong.

For building and flashing an image by hand, see the
[Build & Flashing Reference](https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference).

---

## TL;DR

```sh
# 1. bump the release (see §2) -- the version file is the only version to edit
printf '25.12.6-r4\n' > version

# 2. describe it (see §4)
$EDITOR RELEASE_NOTES.md

# 3. check it locally (see §5)
./build.sh guard
./build.sh build mifib-v12

# 4. promote: test -> staging-main -> main (see §6)
git checkout test/firmware-upgrade && git add -A && git commit -m "release: 25.12.6-r4"
git checkout staging-main && git merge --no-ff test/firmware-upgrade
git checkout main && git merge --ff-only staging-main
git push origin main staging-main

# 5. build a TEST release (see §7) -- a prerelease; no device is offered it
gh workflow run build-and-release.yml --ref main
gh run list --workflow=build-and-release.yml --limit 1

# 6. flash it on a bench unit, then promote it to production (see §7.2)
gh workflow run promote-release.yml --ref main -f tag=v25.12.6-r4
```

---

## 1. Branch model

| Branch | Role |
|---|---|
| `test/firmware-upgrade` | Where work lands. Every commit goes here first. |
| `staging-main` | Integration. `test` is merged in with `--no-ff`. |
| `main` | Released. Receives `staging-main` by **fast-forward only**. CI releases from here. |

The fast-forward is not cosmetic — it is the invariant that `main` is always an
ancestor of `staging-main`, so promotion can never silently drop a commit or
create a merge commit that later diverges. Two rules keep it true:

* Never commit directly on `main`. Its only commits arrive by FF from
  `staging-main`.
* If `main` gains a commit from somewhere else (a PR merged on GitHub, a hotfix),
  `git merge main` **into `staging-main`** before promoting. Cherry-picking the
  commit onto staging does *not* restore the fast-forward: a cherry-pick is a new
  hash with a different parent, so `main` is still not an ancestor.

```sh
git rev-list --left-right --count main...staging-main   # want: 0   0
git merge-base --is-ancestor main staging-main && echo ff_ok
```

## 2. The version

`version` (repo root) is the **single source of truth**. It holds the full release
string, base plus revision suffix:

```
25.12.6-r3
└──┬──┘ └┬┘
   │     └── release revision — bump this for every release, even a rebuild
   └──────── OpenWrt base version — only changes when the upstream tree changes
```

What reads it:

| Consumer | What it takes |
|---|---|
| `build-and-release.yml` → `TAG` | `v$(cat version)` → the git tag and release name |
| `build-and-release.yml` → `BASE` | `${VERSION%-r*}` → the feed path `releases/<base>/` devices poll |
| `gen-firmware-manifest.py` | `version` → `firmware.json`, what the LuCI upgrade page compares against |
| `build.sh prepare_config` | stamps `CONFIG_VERSION_NUMBER` into `.config` → `/etc/openwrt_release` → `DISTRIB_RELEASE` |

That last row is the one that bites. `CONFIG_VERSION_NUMBER` used to be a
hand-maintained copy inside all `diffconfigs/` files, and r3 shipped with it
still reading `25.12.6-r2` — so every r3 image *reported* r2. `firmware-upgrade`
compares the device's `DISTRIB_RELEASE` against the manifest, so such a device
believes it is one release behind forever and re-offers the same update on every
check. `build.sh` now stamps the value from `version` on every build; the
diffconfigs still carry a copy for readability, but it is overwritten.

```sh
# after any build, this must equal the version file:
grep DISTRIB_RELEASE openwrt/build_dir/target-aarch64_generic_musl/root-msm89xx/etc/openwrt_release
```

**Do not use `./build.sh version` for this.** That command manages the upstream
OpenWrt *base* checkout (`devenv/.env`, which upstream commit the tree is pinned
to). The `-rN` suffix is only ever the `version` file.

## 3. Package releases

A package whose files changed gets a `PKG_RELEASE` bump in its `Makefile`, so
`apk` sees a new version and the feed actually replaces it:

```sh
grep -rn 'PKG_RELEASE' packages/*/Makefile
```

Image contents do not depend on this (the image is assembled from the tree), but
the **published package feed** does — an unchanged `PKG_RELEASE` means
`apk upgrade` will not pick the new file up. Bump it whenever a package's
`root/` tree changes.

### 3.1 Package hotfix without a release

A fix that lives only in a package (`base-files` scripts, a LuCI app, a feed
package) ships through the feed, not through a new image. `version` and
`RELEASE_NOTES.md` stay untouched: the device keeps reporting e.g.
`25.12.6-r4`.

**1. Publish it.** `.github/workflows/publish-package.yml` rebuilds the one
package against the released toolchain and splices it into the feed that is
already published, then re-signs the index.

```sh
# a package under packages/
gh workflow run publish-package.yml --ref main -f package=luci-app-foo -f bump=true

# a TARGET package (base-files): its PKG_RELEASE is $(COMMITCOUNT), which does
# not move between releases, so stamp an explicit number instead
gh workflow run publish-package.yml --ref main \
  -f package=base-files -f target_package=true -f pkg_release=1719
```

`target_package` also re-indexes and re-signs `bin/targets/.../packages`,
where a target package lands. `bump` is skipped for it.

**2. Check what was published** - do not trust the run summary:

```sh
curl -s https://akbar-npj.github.io/msm8916-openwrt/releases/25.12.6/targets/msm89xx/msm8916/packages/index.json \
  | grep -o 'base-files[^"]*'
# base-files": "1719~981c978eff
```

and confirm the `.apk` really carries your file - the index says nothing about
contents:

```sh
A=openwrt/staging_dir/host/bin/apk
$A adbdump <feed>/base-files-1719~981c978eff.apk | grep -A3 'name: sim-hotplug'
# size + hash must equal the file in the tree (md5sum it if unsure)
```

**3. Install it on the device. `apk upgrade` is a no-op for base-files.**

The image build version-pins three packages in `/etc/apk/world`:

```
base-files=1718~981c978eff
kernel=6.12.112~8efa871abf779b5591d7c27483980dfd-r1
libc=1.2.5-r5
```

`apk` honours the pin, so `apk upgrade base-files` skips it **and still prints
`OK: 83.1 MiB in 307 packages`**. LuCI's upgrade button does the same. The
version in the command is what makes it an upgrade, and it moves the pin with
it:

```sh
apk update
apk add 'base-files=1719~981c978eff'
/etc/init.d/sim-hotplug restart      # the upgrade replaces files, not the daemon
```

Packages that are not pinned (`luci-app-*`, `msm89xx-scripts`, kmods) take the
normal form: `apk update && apk upgrade <package>`.

**4. Verify on the device.**

```sh
apk list -I | grep base-files              # 1719~981c978eff
grep -c restart_modem /usr/sbin/sim-hotplug  # the new code, not the image's
logread -e sim-hotplug | tail                # behaviour after the restart
```

**Rollback.** The previous build stays in the published feed and its index, so
the same command with the old version moves the pin back:

```sh
apk add 'base-files=1718~981c978eff'
```

Do not prune old `.apk` files from gh-pages - they are the rollback path.

## 4. `RELEASE_NOTES.md`

This file *is* the release body (`body_path: RELEASE_NOTES.md` in the workflow).
It holds the notes for **the current release only** — one `### v…` entry, no
older releases. (Historical notes live in the wiki and in git history.)

Entry shape — concise, bulleted, one release only:

```markdown
### v25.12.6-r4 (YYYY-MM-DD) — built from `main`

> 🆕 **First-release highlights** — one-line caveat if the upgrade path needs a manual step.

- 🎯 **Headline fix (fix)** — what changed and why it mattered, in user terms.
- 🔋 **Another fix** — symptom → cause → fix, kept to one or two lines.
- 🔐 **Security** — backported CVE-… if any.
- 🧰 **Also** — small fixes, as a final bullet or two.
```

Write for someone who owns the device, not for someone who reads the diff: lead
with the symptom they would have seen, then the cause, then the fix. Keep it
bulleted and scannable; add a `**⚠️ Known issues**` bullet if anything is still open.

## 5. Pre-flight

```sh
./build.sh guard                 # live openwrt/ tree matches msm89xx/ + packages/
./build.sh build mifib-v12       # ~9-11 min, one board
```

Then verify the artifact, not the source:

```sh
I=openwrt/bin/targets/msm89xx/msm8916/*mifib-v12-squashfs-sysupgrade.bin
mkdir -p /tmp/chk && tar -xf $I -C /tmp/chk
unsquashfs -d /tmp/chk/root /tmp/chk/sysupgrade-generic-mifib-v12/root
grep DISTRIB_RELEASE /tmp/chk/root/etc/openwrt_release      # must match version
```

CI owns the full multi-board matrix — build **one** board locally, and use
`apk add` over the feed to check a package rather than reflashing for it.

## 6. Promote

```sh
git checkout test/firmware-upgrade
git add -A && git commit            # the version bump, notes, and any fixes
git checkout staging-main
git merge --no-ff test/firmware-upgrade -m "Merge branch 'test/firmware-upgrade' into staging-main"
git checkout main
git merge --ff-only staging-main    # must succeed; if it refuses, see §1
git push origin main staging-main
```

Branch protection on `main` prints warnings ("changes must be made through a pull
request", required check "Shell test suites"). With `enforce_admins=false` the
admin push succeeds and the warnings are advisory.

## 7. Build and publish

```sh
gh workflow run build-and-release.yml --ref main
sleep 15 && gh run list --workflow=build-and-release.yml --limit 1
```

Inputs (all optional):

| Input | Default | Notes |
|---|---|---|
| `release_tag` | derived from `version` | `v25.12.6-r3` |
| `boards` | empty = all boards | a space-separated list for a targeted fix release |

With `boards` set, only those boards rebuild. Two merges keep the rest of the
release coherent: `firmware.json` keeps the other boards' entries, and
`sha256sums` is spliced so their checksum lines survive instead of being
replaced by a file that only covers the rebuilt boards.

The pipeline is `setup` → `build` (one runner per board, in parallel) →
`publish`. Each board builds on its own runner, so wall-clock time is roughly
one board's build rather than the sum of all of them.

### A build publishes a TEST release

The job does not publish straight to production.  It writes the GitHub release as
a **prerelease**, deploys the feed only to `releases/<version>`, and leaves the
shared `releases/<base>` feed and the root `firmware.json` alone.  Nothing a
running device polls can see it — `firmware.json` still names the previous
release, so no unit is offered an upgrade.

That isolation is deliberate.  `releases/<base>` is what every device reads, and
the kernel/kmod prune keeps only the vermagic this build produced, so publishing
a test build there would delete the kmods production devices still need.

Flash it, then promote it with §7.2.

> **Dispatch one release at a time.** Releases share a `concurrency` group, so a
> second dispatch waits instead of running concurrently — that is what stops two
> runs from writing the shared `gh-pages` feed and `firmware.json` at once.
> GitHub only keeps **one pending run** per group, though: if a third dispatch
> arrives while the first is still running, the second queued run is **silently
> cancelled** and never shows up as a failure. Run `gh run list` before
> dispatching again; if a queued run vanished, just dispatch once more.

Watch it without polling in a loop:

```sh
gh run view <run-id> --json status,conclusion -q '.status + " " + (.conclusion // "-")'
gh api repos/akbar-npj/msm8916-openwrt/actions/runs/<run-id>/jobs \
  --jq '.jobs[].steps[] | "\(.number) \(.status) \(.name)"'
```

### 7.1 Attach the frozen recovery images

The job does **not** build `recovery-<board>.img`: the image embeds the WCNSS
firmware and the calibrated wlan NV, which are git-ignored
(`msm89xx/recovery/blobs/`), so CI has nothing to build it from.  The images
are **frozen** instead — a recovery only has to bring the stick back, and one
that has booted on hardware is better than a fresh build.

They are published as assets of one release, `v25.12.6-r4`, and the workflow's
*Attach the frozen recovery images* step copies them onto **every** new
release (`RECOVERY_SOURCE_TAG` at the top of that step).  They are listed in
`sha256sums` too, so a release is complete with no manual upload at all.

The hashes and the verified-on dates live in `msm89xx/recovery/README.md`
§ "Shipped (frozen) images" — that table, not the asset, is the record.

**Only when you deliberately rebuild an image** (the rebuild triggers are
listed in that README) do you touch the source release: replace the asset
there, and every later release inherits the new one.

```sh
mkdir -p /tmp/rel
cp msm89xx/recovery/mifib-v12.img /tmp/rel/recovery-mifib-v12.img
cp msm89xx/recovery/hmuf02v5.img /tmp/rel/recovery-hmuf02v5.img
sha256sum /tmp/rel/*.img            # must match the frozen table
gh release upload v25.12.6-r4 /tmp/rel/recovery-*.img --clobber
```

Update the table in `msm89xx/recovery/README.md` (size, sha256, verified date)
in the same commit.  If `RECOVERY_SOURCE_TAG` is ever repointed, upload the
images to the new tag first: the workflow fails the release if it cannot
download them.
### 7.2 Promote to production

```sh
gh workflow run promote-release.yml --ref main -f tag=v25.12.6-r4
```

It rebuilds nothing: everything it needs was published by the test build.

1. Refuses unless the tag exists, is a **prerelease**, is not a draft, and carries
   at least eight assets — a partial or already-promoted release cannot slip
   through.
2. Copies `releases/<version>` into `releases/<base>` — the target package feed,
   the arch feed, the per-release `firmware.json` and the public key — and prunes
   every `kernel-*.apk` / `kmod-*.apk` this build did not produce.
3. Publishes that manifest as the root `firmware.json`, the file devices ask for
   first.  This is the moment the release becomes visible to them.
4. Flips the GitHub release to `--latest` and drops the "(test build)" title.

`-f dry_run=true` does everything except the push and the release edit, and
prints the staged diff.

**Promote only after the image has booted on hardware.** That is the whole point
of the split: a bad build fails on a bench unit instead of on a user's stick.  r6
added `allow-set-time` to the PM8916 RTC and made every board stop booting; it
reached production only because a release used to publish straight there.

## 8. Verify the release

```sh
gh release view v25.12.6-r3 --json tagName,isDraft,assets
gh api repos/akbar-npj/msm8916-openwrt/contents/firmware.json?ref=gh-pages \
  --jq '.content' | base64 -d | head -40
```

Check 1–3 against the **test** release, before promoting. 4–6 only become true
after §7.2, because the feed and `firmware.json` do not move until then.

Check, in order:

1. **The tag is not a draft** and carries all boards (plus `kmods-*.tar.gz`).
2. **The manifest agrees with the asset.** `firmware.json` publishes a `sha256`
   per board; it must equal the hash of the uploaded file:
   ```sh
   gh release download v25.12.6-r3 --pattern '*mifib-v12-squashfs-sysupgrade.bin'
   sha256sum *mifib-v12*sysupgrade.bin
   ```
3. **The image reports the right version.** Download it, unpack the squashfs, and
   read `/etc/openwrt_release`. This is the check that would have caught the r3
   version bug before it shipped.
4. **The feed serves it**: `https://akbar-npj.github.io/msm8916-openwrt/firmware.json`.
5. **The recovery assets are attached** and match the frozen hashes in
   `msm89xx/recovery/README.md` (§7.1 uploads them by hand).
6. **The upgrade path works** — the one thing CI cannot test. Leave a device on
   the previous release, open the LuCI update page, and let it download and flash.
   Confirm the banner, then confirm `DISTRIB_RELEASE` afterwards; a device that
   still reports the old version will offer the same upgrade forever.

## 9. Re-cutting a bad release

Same version number, replaced:

```sh
gh release delete v25.12.6-r3 --yes --cleanup-tag
git push origin :refs/tags/v25.12.6-r3     # only if the tag survived
gh workflow run build-and-release.yml --ref main
```

The new run regenerates `firmware.json` from the freshly built binaries (the
merge only preserves boards this run did not build) and re-publishes the feed
with `keep_files: true`, so the same `releases/25.12.6/` path is overwritten in
place. Devices that already flashed the bad build will re-offer the update and
correct themselves.

If the bad release is already widely installed and the version string itself is
wrong (as in r3), prefer the **next** revision instead — deleting a tag that
devices have cached in a manifest leaves them pointing at a 404 until the new
manifest lands.

## 10. Gotchas

| Symptom | Cause | Fix |
|---|---|---|
| Release dies at "Prepare OpenWrt Tree", `git clone` exit 128 | `actions/cache` pre-created `openwrt/` | handled in `clone_openwrt` (temp clone + swap) |
| Every device reports the previous release | `CONFIG_VERSION_NUMBER` stale | automatic since the `build.sh` stamp — verify with §2 |
| Upgrade offered but `do_upgrade` refuses | device is below `min_version` (`25.12.6-r1`) | flash manually once; the floor only moves for a genuinely un-flashable jump |
| Feed 404 after a release | only `releases/<version>` published | the workflow publishes `releases/<base>` too — check both |
| `gh` says the tag exists | a previous run created it before failing | `--cleanup-tag` on delete, or bump the revision |
| Local build has old code | `./build.sh kernel` is compile-only and does not install into `root-msm89xx` | use `./build.sh build <board>` |
| Build log has hundreds of `ERROR:` lines | postinst scripts echoing `ERROR: …` | look for `*** Error N` and a missing `==> Finished in` instead; use `grep -a` (the log contains escape sequences) |
| `apk upgrade base-files` prints `OK` but the version does not change | the image pins `base-files` in `/etc/apk/world` | `apk add 'base-files=<version>'` (§3.1) |
| `apk` fails with `kernel-... breaks: world[kernel=...]` | a duplicate `kernel=` line left in the world by an older image | delete the stale line, or `firstboot -y` |

## Checklist

- [ ] `version` bumped (`-rN` incremented)
- [ ] `RELEASE_NOTES.md` updated with **only this release's** notes (no older entries, no dangling cross-references)
- [ ] `PKG_RELEASE` bumped for every package whose files changed
- [ ] `./build.sh guard` in sync
- [ ] one board built locally, image `DISTRIB_RELEASE` matches `version`
- [ ] flashed and smoke-tested on the bench
- [ ] `test` → `staging-main` merged `--no-ff`, `main` fast-forwarded
- [ ] both branches pushed
- [ ] CI run green, release not a draft, all boards + kmods attached
- [ ] `recovery-*.img` present on the release and matching the frozen table (§7.1)
- [ ] manifest `sha256` matches the uploaded asset
- [ ] upgrade tested from the previous release on a real device

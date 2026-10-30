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

# 5. build and publish (see §7)
gh workflow run build-and-release.yml --ref main
gh run list --workflow=build-and-release.yml --limit 1
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
hand-maintained copy inside all seven `diffconfigs/` files, and r3 shipped with it
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

## 4. `RELEASE_NOTES.md`

This file *is* the release body (`body_path: RELEASE_NOTES.md` in the workflow).
It keeps the **five newest entries, newest first**, separated by `---`. Older
entries live in the wiki and in git history.

Entry shape:

```markdown
### v25.12.6-r4 (YYYY-MM-DD) — built from `main`

*One-line summary in plain language.*

**Why this release** — what was broken or missing, in user terms.

**🎯 Headline fix (fix)** — what changed, why it mattered, and the evidence it works.
```

Write for someone who owns the device, not for someone who reads the diff: lead
with the symptom they would have seen, then the cause, then the fix. Add a
`**⚠️ Known issues**` line if anything is still open.

When you add the sixth entry, delete the oldest and inline any "see rN below"
cross-references so nothing dangles.

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

CI owns the full seven-board matrix — build **one** board locally, and use
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
| `boards` | empty = all seven | a space-separated list for a targeted fix release |

With `boards` set, only those boards rebuild and the manifest merge keeps the
other boards on their last published version.

The job takes **~1 h 25 m – 1 h 40 m**. Steps, in order: checkout → disk → tag →
cache → prepare tree → compile images → extra packages → kmod bundle + checksums
→ `firmware.json` → gh-pages feed → publish release.

Watch it without polling in a loop:

```sh
gh run view <run-id> --json status,conclusion -q '.status + " " + (.conclusion // "-")'
gh api repos/akbar-npj/msm8916-openwrt/actions/runs/<run-id>/jobs \
  --jq '.jobs[].steps[] | "\(.number) \(.status) \(.name)"'
```

## 8. Verify the release

```sh
gh release view v25.12.6-r3 --json tagName,isDraft,assets
gh api repos/akbar-npj/msm8916-openwrt/contents/firmware.json?ref=gh-pages \
  --jq '.content' | base64 -d | head -40
```

Check, in order:

1. **The tag is not a draft** and carries all seven boards (plus `kmods-*.tar.gz`).
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
5. **The upgrade path works** — the one thing CI cannot test. Leave a device on
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

## Checklist

- [ ] `version` bumped (`-rN` incremented)
- [ ] `RELEASE_NOTES.md` entry added, still five entries, cross-references fixed
- [ ] `PKG_RELEASE` bumped for every package whose files changed
- [ ] `./build.sh guard` in sync
- [ ] one board built locally, image `DISTRIB_RELEASE` matches `version`
- [ ] flashed and smoke-tested on the bench
- [ ] `test` → `staging-main` merged `--no-ff`, `main` fast-forwarded
- [ ] both branches pushed
- [ ] CI run green, release not a draft, seven boards + kmods attached
- [ ] manifest `sha256` matches the uploaded asset
- [ ] upgrade tested from the previous release on a real device

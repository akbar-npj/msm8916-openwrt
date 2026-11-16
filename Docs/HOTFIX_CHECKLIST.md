# Hotfix checklist

Shipping a fix to an already-flashed unit without a reflash. Every line below
exists because we got it wrong once — §7 maps each check to the failure that
produced it.

Related: [`HOTFIXES_ON_DEVICE.md`](HOTFIXES_ON_DEVICE.md) (what the operator
sees) and [`RELEASING_FIRMWARE.md` §3.1](RELEASING_FIRMWARE.md#31-package-hotfix-without-a-release)
(the release-runbook view).

---

## 1. Is it hotfixable at all?

| Where the change lives | Ships as |
| :--- | :--- |
| `packages/<pkg>/root/` | package hotfix — the normal case |
| `msm89xx/base-files/` | **not** a normal hotfix. `base-files` is world-pinned; it needs `target_package=true`, `pkg_release=<n>` and `scope=revision`, and the device installs it with `apk add base-files=<ver>` |
| `msm89xx/Makefile` (`DEFAULT_PACKAGES`), `msm89xx/image/`, `diffconfigs/` | image only — needs a release, not a hotfix |

If a fix needs to reach a flashed unit and lives in `base-files`, first ask
whether it can move into a package instead. Two small migration scripts are not
worth moving the `base-files` pin.

- [ ] The package is actually installed on the target units (`DEVICE_PACKAGES`
      in `msm89xx/image/msm8916.mk`, or `DEFAULT_PACKAGES`).
- [ ] If the change adds an init script, it also ships a `uci-defaults` that
      enables it — `default_postinst` does **not** `enable` init scripts on an
      upgrade, it only starts them.
- [ ] If the change runs `apk` from a post-install path, it tolerates the
      database lock (apk holds it while the script runs).

## 2. Version

- [ ] `PKG_RELEASE` in `packages/<pkg>/Makefile` is **above the published
      high-water mark** for that package.
- [ ] The bump is **committed**, not just edited. `bump=true` bumps the
      Makefile in the runner's checkout only and prints a warning; the next
      build starts from the committed number again.

## 3. Push before you dispatch

The runner checks out the **remote** ref.

- [ ] `git rev-parse HEAD origin/main` prints the same hash.
- [ ] `git status -sb` shows the branch level with its upstream.

A commit that exists only locally is invisible to the build, and the run
publishes the stale remote version — silently, and with a green tick.

## 4. Dispatch

```sh
gh workflow run publish-package.yml --ref main \
  -f package=msm89xx-scripts \
  -f bump=false \
  -f hotfix_title="one line for the LuCI page" \
  -f hotfix_restart="time-sync"
```

- [ ] `bump=false` when §2 is already committed.
- [ ] **`hotfix_title` is set.** Without it the package is published to the
      feed but no `hotfix.json` entry is written, so the page and the banner
      show nothing. This is the quietest failure we have had.
- [ ] `hotfix_restart` lists the services that must restart for the fix to take
      effect (they are restarted after install, not by the package itself).
- [ ] `scope=revision` unless the fix genuinely applies to every revision.

For a target package: `-f target_package=true -f pkg_release=<n>` and no
`bump`.

## 5. Verify through git, never the CDN

`github.io` serves the last *successful* Pages deploy, so a failed deploy leaves
stale content live. Read the `gh-pages` ref instead.

```sh
# the index
gh api "repos/akbar-npj/msm8916-openwrt/contents/releases/25.12.6-r9/packages/aarch64_generic/base/packages.adb?ref=gh-pages" \
  --jq .content | base64 -d > /tmp/feed.adb
openwrt/staging_dir/host/bin/apk adbdump /tmp/feed.adb | grep -A2 'name: <pkg>'

# the package itself — the index says nothing about contents
gh api "repos/akbar-npj/msm8916-openwrt/contents/releases/25.12.6-r9/packages/aarch64_generic/base/<pkg>-<ver>.apk?ref=gh-pages" \
  --jq .content | base64 -d > /tmp/pkg.apk
openwrt/staging_dir/host/bin/apk adbdump /tmp/pkg.apk | grep -E 'version:|name: <the-new-file>'

# the manifest
gh api "repos/akbar-npj/msm8916-openwrt/contents/releases/25.12.6-r9/hotfix.json?ref=gh-pages" \
  --jq .content | base64 -d
```

- [ ] The index carries the new version.
- [ ] The package **count did not drop** — a single-package re-index that loses
      the rest of the feed is the worst outcome here.
- [ ] The `.apk` really contains the file you changed, not just the new version
      number.
- [ ] `hotfix.json` has **exactly one** entry for this package, at the new
      version, with the right `restart` list.
- [ ] The Pages deployment for the commit succeeded (a timeout leaves the CDN
      behind git; `gh run rerun <id> --failed` the deploy job).

## 6. Device side

```sh
apk update && apk upgrade <pkg>          # not pinned
apk add '<pkg>=<ver>'                    # pinned (base-files, kernel, libc)
apk list -I <pkg>                        # the only proof the install happened
```

- [ ] `apk list -I` shows the new version. Exit 0 proves nothing — a pinned
      package and a stale index both "succeed" without changing anything.
- [ ] `logread -e <pkg>` shows the expected behaviour, not just the new files.
- [ ] If the page still offers the fix: `rm -f /var/cache/apk/APKINDEX.*`,
      `apk update`, and `/etc/init.d/update-notify restart` (the banner caches
      for 6 h).

## 7. Rollback

The previous build stays in the published feed — do not prune `.apk` files from
`gh-pages`, they are the rollback path.

```sh
apk add '<pkg>=<older-version>'
```

---

## 8. Failures we have actually had

| Failure | What it looked like | The check that catches it |
| :--- | :--- | :--- |
| A local `PKG_RELEASE` bump and exec-bit fix were never pushed (2026-11-06) | The runner built the remote `r11`, not the local `r13`, and republished a **broken** build into `hotfix.json` | §3 |
| The Pages deploy timed out | Git had the new index, the CDN served the old one, the fix was offered but could not install | §5, last item |
| `base-files` promoted into the shared feed | Older revisions were offered an update that `apk upgrade` silently refused (world pin) | §1 |
| Two `hotfix.json` entries for one package | `breaks: world[pkg=oldver]` — the older pin is unsatisfiable because the index only carries the newest version | §5, manifest item |
| Installed, but the page still listed it | Stale CDN index or the 6-hour banner cache | §6 |
| A package published with no `hotfix_title` (2026-11-16) | Feed updated, page and banner silent — a half hotfix, green run | §4 |
| An init script added to a package | Installed and started, but not enabled, so it vanished at the next boot | §1, second item |
| `apk add`/`apk del` from a post-install script (2026-11-16) | `unable to lock database` — the migration silently did nothing | §1, third item |
| `apk del --force-broken-world` on a live unit (2026-11-16) | Purged 124 packages, including the kernel | never do this; repair from `/rom/etc/apk/world` |

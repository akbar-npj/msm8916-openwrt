# Patches & Packaging — the complete map

*How every change to this firmware is organised, where each kind lives, and how
to add one yourself.  Written against the tree as of 2026-10-25.*

There is a layman's version of this on the wiki (*The Patches Explained*); this
document is the reference you implement from.

---

## 1. The big picture: four kinds of change, four homes

| You want to change… | Edit… | Applied by |
|---|---|---|
| the Linux kernel (drivers, SoC code) | `msm89xx/patches/NNN-*.patch` | the kernel build (quilt) |
| the mac80211 backports tree (Wi-Fi drivers: wcn36xx, …) | `openwrt-overlay/package/kernel/mac80211/patches/ath/NNN-*.patch` | the mac80211 package build (quilt) |
| an upstream OpenWrt package (hostapd, …) | `openwrt-overlay/package/<feed-path>/patches/NNN-*.patch` | that package's build (quilt) |
| the OpenWrt tree itself (rare) | `openwrt-patches/*.patch` | `build.sh` (`patch -p1`, dry-run guarded) |
| ship a new program/app | `packages/<name>/` | copied to `package/msm8916/` by `build.sh` |
| the image (files, defaults, package selection) | `msm89xx/base-files/`, `msm89xx/Makefile`, `msm89xx/image/`, `diffconfigs/` | the image build |

Nothing is edited inside `openwrt/` — that directory is a build artifact (it is
git-ignored).  `build.sh` re-syncs the tracked sources into it on every run, so
a change made only inside `openwrt/` is silently lost.

---

## 2. How `build.sh` wires it together

`./build.sh build <board>` runs inside a Docker builder.  Before `make`, the
sync step copies the tracked sources into the tree:

1. `rm -rf openwrt/target/linux/msm89xx` then `cp -a msm89xx` — the whole
   target (patches, base-files, image makefiles, config).
2. `rm -rf openwrt/package/msm8916` then `cp -a packages` — our packages.
3. For every `packages/*/patches/`, copy into the matching upstream package
   under `openwrt/package/system/<name>/patches/`.
4. `cp -a openwrt-overlay/. openwrt/` — **merges** (no delete), so overlay
   files add to or shadow the tree.  This is how the mac80211 and hostapd
   patch directories get in.
5. `openwrt-patches/*.patch` are applied with `patch -p1`, each one first
   dry-run so a stale patch cannot break the build.

`./build.sh guard [--deep]` verifies the live tree still matches the tracked
sources — run it when a build behaves as if it used an old patch set.

---

## 3. Kernel patches (`msm89xx/patches/`)

53 patches, numbered `8xx` for this project (the `9xx` range is reserved for
generic fixes).  They are ordinary quilt-format patches against the kernel
source; the kernel build applies everything in this directory in name order.

Conventions:

- one concern per patch, `NNN-area-subsystem-what.patch`
  (e.g. `826-remoteproc-q6v5-mss-msm-subsys-restart.patch`)
- the header carries a real `Subject:` and a body that explains **why**, with
  the evidence (crash signature, measured behaviour) — these are the audit
  trail
- a patch that is board-specific must gate at runtime
  (`of_machine_is_compatible`) or by device-tree presence, never by editing
  another board's DTS

**Traps (both have burned this project):**

- `./build.sh kernel <board>` is **compile-only**: it does *not* install the
  modules into the image root.  A flash after it runs the OLD module — always
  do a full `build.sh build <board>` when the change must reach the device.
- `msm89xx/patches/` is the source of truth for driver changes.  The copy
  under `openwrt/` is regenerated; `diff -r` before assuming otherwise.

### Adding a kernel patch

1. Find the file in `openwrt/build_dir/target-*/linux-*/linux-6.12.94/`
   (the prepared, patched tree — that is the state your patch will apply on
   top of).
2. Make the edit, then produce the patch with `git diff` (see section 6 for why
   hand-written hunks are a bad idea), fix up the paths to `a/` `b/`, name it
   `NNN-...`, drop it in `msm89xx/patches/`.
3. Verify: `cd <prepared tree> && patch -p1 --dry-run --fuzz=0 < your.patch`
   **and** `git apply --check your.patch`.
4. `./build.sh build <board>`, then confirm the change is in the image
   (unsquashfs the sysupgrade bin and check the file — see section 8).
---

## 4. mac80211 / backports patches

The Wi-Fi stack (including the wcn36xx driver) is **not** built from the
in-tree kernel copy — it comes from the mac80211 backports package.  Patches
therefore live in:

```
openwrt-overlay/package/kernel/mac80211/patches/ath/NNN-*.patch
```

which merges into `openwrt/package/kernel/mac80211/patches/ath/` at sync time.
Numbering follows the backports' own series (`453`, `454`, `455`, ... — our
wcn36xx thermal/HAL/survey fixes).  The prepared tree to patch against is
`openwrt/build_dir/target-*/mac80211-regular/backports-*/`.

Same conventions and the same verification as section 3.

---

## 5. Patches to upstream OpenWrt packages

Any package under `openwrt/package/` can be patched the same way — create the
patches directory in the overlay and it merges into the tree:

```
openwrt-overlay/package/network/services/hostapd/patches/900-*.patch
```

Rules of thumb:

- number the patch so it applies **after** the feed's own patches (they start
  at `010`; `900` is a safe slot)
- a non-zero exit from an apk-style pre-commit hook aborts the transaction —
  hooks must always `exit 0`
- the feed may upgrade underneath you: check your patch still applies after a
  feed update (the dry-run in section 3 is the check)

Worked example: `900-wpa-cli-dispatch-ssid-temp-disabled.patch` adds one
`else if` branch to `wpa_cli_action_process()` so `wpa_cli -a` action scripts
receive `CTRL-EVENT-SSID-TEMP-DISABLED` (stock wpa_cli has no generic fallback
and silently drops it).  Station mode uses that event to report a rejected
password.

---

## 6. Writing patches that actually apply

**Generate, don't hand-write.**  GNU `patch` and quilt are unforgiving about
hunk headers; `git diff` is not.  The reliable recipe:

```sh
# work on a scratch copy of the file
mkdir -p /tmp/p/wpa_supplicant && cd /tmp/p && git init -q .
cp <prepared-tree>/wpa_supplicant/wpa_cli.c wpa_supplicant/
git add -A && git -c user.email=a@b -c user.name=t commit -qm base
# ...edit the file...
git diff > my.patch     # exact context, exact counts, trailing blank line
```

A hand-written hunk that is off by one context line or one line count applies
with "fuzz" (or fails under quilt's `--fuzz=0`) even though the bytes look
identical.  Verify with **both**:

```sh
patch -p1 --dry-run --fuzz=0 < my.patch
git apply --check my.patch
```
---

## 7. Our own packages (`packages/`)

A package is a directory with a `Makefile` and the files to install:

```
packages/luci-app-wifi-sta/
├── Makefile            # package definition
├── htdocs/             # LuCI JS/CSS  -> /www/
└── root/               # -> / (etc, usr, ...)
```

Two Makefile flavours:

- **LuCI apps**: `include $(TOPDIR)/feeds/luci/luci.mk` with `LUCI_TITLE`,
  `LUCI_DEPENDS`, `PKG_VERSION` (see `packages/luci-app-wifi-sta/Makefile`).
- **Plain packages**: `include $(TOPDIR)/rules.mk` with `PKG_NAME`,
  `PKG_VERSION`, and a `Build/Compile` (or `PKG_SOURCE*` when building
  upstream source, e.g. `packages/rmtfs`).

Packages are installed into images by adding them to `DEFAULT_PACKAGES` in
`msm89xx/Makefile` (every board) or to a board's `DEVICE_PACKAGES` in
`msm89xx/image/msm8916.mk`.  The diffconfig guard rejects a `=m` line in
`diffconfigs/` that duplicates a `DEFAULT_PACKAGES` entry — kernel modules
selected by default must not also appear in the per-board config.

### Adding a package

1. `packages/<name>/Makefile` + `root/` (or `htdocs/`).
2. Add to `DEFAULT_PACKAGES` (`msm89xx/Makefile`) or `DEVICE_PACKAGES`.
3. `./build.sh build <board>`; confirm the files landed in the image
   (`unsquashfs` + `ls`, section 8).
4. LuCI apps also need an ACL file
   (`/usr/share/rpcd/acl.d/<name>.json`) — verify it with a **restricted**
   login, never root: root's `read '*'/write '*'` bypasses ACLs entirely.

---

## 8. Verifying a built image (do this before flashing)

```sh
IMG=openwrt/bin/targets/msm89xx/msm8916/*-<board>-squashfs-sysupgrade.bin
OFF=$(grep -abo hsqs "$IMG" | head -1 | cut -d: -f1)
tail -c +$((OFF+1)) "$IMG" > /tmp/root.sqfs
unsquashfs -d /tmp/rootfs -f /tmp/root.sqfs
```

then check the actual file in `/tmp/rootfs` (not the repo copy): the patched
driver, the package files, the init script, the modules.d entry.  A build that
"looks right" in the repo can still flash the old bytes if the sync or the
install step missed it.

---

## 9. Commit flow

1. Work on `test/<topic>` (never `main`).
2. One concern per commit; the message states the evidence (what was measured,
   on which board) — these messages are the audit trail.
3. `git merge --no-ff` into `staging-main` and push `staging-main`.
4. `main` is branch-protected: land a change there with a minimal PR off
   `origin/main` (cherry-pick) and `gh pr merge --admin` — GitHub refuses
   self-approval, so an ordinary merge is blocked.
5. Update the matching doc under `Docs/` in the same commit series; the wiki
   gets the layman's summary.

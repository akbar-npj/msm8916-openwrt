# Project Architecture & Folder Map

> **Orientation guide** for the `msm8916-openwrt-clean` repository.
> Companion docs (do **not** duplicate here):
> - [BUILD_SYSTEM_GUIDE.md](BUILD_SYSTEM_GUIDE.md) — full `build.sh` command reference.
> - [PATCHES_AND_PACKAGING.md](PATCHES_AND_PACKAGING.md) — which change goes in which folder, and how to add one.
> - [msm8916-openwrt-build-flow.md](msm8916-openwrt-build-flow.md) — the BSP-as-source-of-truth design note.
> - [Docker/README.md](Docker/README.md) — the builder container in detail.

---

## 1. The three layers (mental model)

Everything in this repo exists to turn *tracked source* into a *flashed image*.
Keep these three layers separate in your head:

| Layer | Where | Editable? | Lifecycle |
| :---- | :---- | :-------- | :------- |
| **1. Tracked sources** | `msm89xx/`, `openwrt-patches/`, `openwrt-overlay/`, `packages/`, `diffconfigs/`, `devenv/`, `scripts/`, `build.sh`, `sync-version.sh`, `build-recovery.sh` | **Yes — this is the repo you commit** | Version-controlled |
| **2. Prepared OpenWrt tree** | `openwrt/` | **No — generated artifact** | Re-synced from layer 1 on *every* `build.sh` run; git-ignored |
| **3. Built image / packages** | `openwrt/bin/`, `openwrt/build_dir/`, `openwrt/staging_dir/` | No | Throwaway output of a build |

**The golden rule:** never edit anything inside `openwrt/`. `build.sh` re-syncs
the tracked sources into it on every run, so an edit made only inside `openwrt/`
is silently overwritten. Change the source in layer 1 instead.

---

## 2. Top-level folder map

| Path | Role |
| :--- | :--- |
| `build.sh` | Top-level build orchestrator (runs **on the host**, drives Docker). |
| `scripts/` | The two orchestration helpers: `openwrt-version.sh`, `openwrt-prepare.sh`. |
| `sync-version.sh` | Release-version single-source-of-truth helper (host-side). |
| `build-recovery.sh` | Builds the per-board recovery/rescue boot image (host-side). |
| `devenv/` | Docker **builder** definition: `Dockerfile`, `docker-compose.yml`, `dependencies.sh`, `.env`. |
| `msm89xx/` | **BSP source of truth** — the OpenWrt `target/linux/msm89xx` target, kernel patches, kernel config, base-files, image + recovery recipes, `Makefile` (`DEFAULT_PACKAGES`), `modules.mk`. |
| `openwrt-patches/` | 4 patches applied **to the OpenWrt tree itself** (`include/package-pack.mk`, mac80211 `ath.mk`, `rstrip.sh`, netifd's wifi driver — `wifi-scripts` shell **and** ucode `mac80211.sh`). |
| `openwrt-overlay/` | Per-package / per-feed **source patches** merged into `openwrt/` via `cp -a` before the build (wcn36xx, hostapd, libqmi, modemmanager, …). |
| `packages/` | 21 custom userspace apps + LuCI modules; copied to `package/msm8916/`. |
| `diffconfigs/` | 7 per-board kernel/package configs (`hmuf02v5`, `uf02`, `ufi001b`, `ufi003`, `uz801`, `mf800b`, `mifib-v12`). |
| `openwrt/` | The **prepared OpenWrt tree** (clone + patches + overlays). Build artifact — do not edit, do not commit. |
| `version` | Release version string (e.g. `25.12.5-r5`); source of truth for `sync-version.sh`. |
| `tests/` | Test harness (e.g. `tests/himi-ok-guard/run.sh`). |
| `TBR/` | "To-be-reviewed" packages staged but not yet promoted into `packages/`. |
| `Docs/` | This documentation tree. |
| `fsg_extracted/`, `hmu05_fsg_extracted/`, `ghidra_proj/`, `ghidra_scripts/`, `edl-ground-truth-analysis.md` | Modem-firmware reverse-engineering / extracted-NV artifacts. **Not part of the build.** |
| `README.md`, `RELEASE_NOTES.md`, `FLASHING_AND_RELEASE_GUIDE.md`, `MAINTAINERS.md`, `CODEOWNERS`, `LICENSE` | Project meta / release docs. |

> **Hygiene note:** the working tree currently also contains `doc/` and `docs/`
> directories whose contents are byte-identical to `Docs/`. `Docs/` is the
> canonical tree referenced everywhere else — treat `doc/` and `docs/` as
> stray duplicates and do not add new docs there.

---

## 3. The four "source of truth" mechanisms

`build.sh` merges four distinct change-streams into the prepared `openwrt/` tree.
Knowing which one wins, and when, prevents the class of breakage where a file in
one layer silently collides at image-assembly time.

| You want to change… | Edit… | Applied by | Mechanism |
| :------------------ | :---- | :--------- | :-------- |
| Linux kernel (drivers, SoC) | `msm89xx/patches/NNN-*.patch` | kernel build (quilt) | copied verbatim to `openwrt/target/linux/msm89xx/patches` |
| OpenWrt tree itself (rare) | `openwrt-patches/*.patch` | `build.sh` | `patch -p1` (dry-run guarded) |
| An upstream OpenWrt package (hostapd, mac80211/wcn36xx, libqmi, …) | `openwrt-overlay/package/&lt;feed&gt;/&lt;pkg&gt;/patches/NNN-*.patch` | `build.sh` | `cp -a openwrt-overlay/. openwrt/` |
| Ship a new program / LuCI app | `packages/&lt;name&gt;/` | `build.sh` | copied to `openwrt/package/msm8916/` |
| Image contents / defaults / package selection | `msm89xx/base-files/`, `msm89xx/Makefile`, `msm89xx/image/`, `diffconfigs/` | image build | copied / compiled in |

**Three invariants enforced by `build.sh`:**

1. **`cp -a` never deletes.** `openwrt-overlay/` is merged with `cp -a`, which
   copies *over* but never removes. A patch you rename or delete from
   `openwrt-overlay/` leaves the old file behind in `openwrt/` until you delete
   it by hand. (See `PATCHES_AND_PACKAGING.md` §2.) Because the overlay merge
   cannot remove files, deleting the stale copy from `openwrt/` is a required
   part of every overlay removal.
2. **BSP drift is fatal.** After syncing, `build.sh` runs `assert_bsp_synced()`,
   a `diff -rq` of `msm89xx/` and `packages/` against the live `openwrt/` tree.
   Any divergence aborts the build — a live tree missing patches would otherwise
   produce a silently wrong kernel. Run `./build.sh guard [--deep]` to check.
3. **A changed patch tree forces a re-prepare.** `ensure_prepared()` normally
   takes a fast path when `.builder-state` already records the current OpenWrt
   version: it only re-syncs the BSP and skips `openwrt-prepare.sh` — which is
   what applies `openwrt-patches/` and merges `openwrt-overlay/`. That meant a
   newly added or edited patch in either tree was silently ignored by every
   later build. `.builder-state` now also records `TREE_SHA`, a content hash of
   `openwrt-patches/` + `openwrt-overlay/`; if it no longer matches, the fast
   path is refused and the full preparation re-runs. (A state file predating
   `TREE_SHA` cannot prove sync, so it also triggers a re-prepare — once.)

The **diffconfig guard** additionally enforces: (a) the `=m` kernel-module
block is *identical* across all seven boards, and (b) no diffconfig selects a
package already in `DEFAULT_PACKAGES` (in `msm89xx/Makefile`). Divergence is
rejected before the build starts.

---

## 4. Build & release scripts (the helper scripts)

All of these run on the **host** except where noted. They shell into the Docker
container for the actual compilation.

| Script | Runs on | Role |
| :----- | :------ | :--- |
| `build.sh` | host | **Orchestrator.** Manages the Docker builder, clones/checks-out the OpenWrt tree at the pinned version, syncs the BSP, runs the guards, and invokes `make` inside the container. Subcommands: `help`, `list`, `version`, `image` (build the Docker image), `prepare [--force]`, `guard [--deep]`, `shell` (interactive container), `build &lt;board|all&gt;`, `rebuild &lt;board|all&gt;` (clean), `kernel [board]`, `package &lt;name|path&gt; [board]`, `kmod &lt;name&gt; [board]`, `menuconfig &lt;board&gt;`, `saveconfig &lt;board&gt;`. |
| `scripts/openwrt-version.sh` | host | **Version manager.** Reads/writes `devenv/.env` (`OPENWRT_VERSION`). Decides which OpenWrt tag/branch `openwrt/` is checked out at. Never clones, never touches Docker. Delegated by `build.sh version`. |
| `scripts/openwrt-prepare.sh` | **inside container** | **Tree preparer.** Installs the `msm89xx` target + project `packages`, applies `openwrt-overlay/` (`cp -a`), applies `openwrt-patches/` (`patch -p1`), updates/installs feeds, applies compatibility fixes, records prep state. Never clones, never reads `.env`, never invokes Docker. |
| `sync-version.sh` | host | **Release version SSoT.** Bumps the `version` file and rewrites the `CONFIG_VERSION_*` block in every `diffconfigs/*` so a flashed system reports the right version. |
| `build-recovery.sh` | host (no container) | Builds the per-board recovery/rescue image → `msm89xx/recovery/&lt;board&gt;.img` = the board's **production kernel** (lifted out of its `-squashfs-boot.img`) + a gzipped cpio of `msm89xx/recovery/irfs` as the ramdisk. No kernel is compiled. |
| `devenv/Dockerfile` | build-time | Defines the `devenv-builder` image: `ubuntu:22.04` + all OpenWrt build deps (`dependencies.sh`) + a non-root `builder` user whose UID/GID match the host. |
| `devenv/docker-compose.yml` | host | Defines the `builder` service; bind-mounts the repo at `/repo`; working dir `/repo/openwrt`; host networking. |
| `devenv/dependencies.sh` | build-time (in image) | `apt-get` install of every toolchain/build dependency baked into the image. |
| `devenv/.env` | host | `OPENWRT_VERSION=v25.12.5` — the version pin consumed by `openwrt-version.sh`. |

> **Runtime (device) scripts are a different class.** Scripts shipped *into* the
> image — e.g. `msm89xx/base-files/etc/diag.sh`, `lib/himi-ok.sh`,
> `usr/sbin/wifi-sta-dhcp.sh`, `msm89xx/image/flash.sh`,
> `msm89xx/recovery/fetch-device-blobs.sh`, and `packages/*/files/*.sh` — are
> copied to the rootfs at image time and run on the **device**, not during the
> build. They are not build helpers.

---

## 5. Docker separation — the host vs. container boundary

This is the least-obvious part of the project, so it gets its own section.

~~~
 HOST MACHINE                      DOCKER CONTAINER (devenv-builder)
 +---------------------------+    +------------------------------------------+
 |  repo  (msm8916-openwrt…) |    |  image: ubuntu:22.04 + toolchain deps     |
 |   |- build.sh  -----------|--->|  service: builder (container openwrt-builder)
 |   |- msm89xx/  packages/  |    |  mount:  ..  -->  /repo   (the whole repo) |
 |   |- openwrt/  (tree)     |<---|  workdir: /repo/openwrt                   |
 |   '- devenv/              |    |  user: HOST_UID:HOST_GID (non-root)        |
 +---------------------------+    |  network: host                            |
         needs only:               |  runs:  make -jN  (all compilation)       |
         docker + git              +------------------------------------------+
~~~

**How it works:**

- The host only needs **Docker + git**. No compiler, no OpenWrt toolchain.
- `devenv/docker-compose.yml` builds the `devenv-builder` image from
  `devenv/Dockerfile` (Ubuntu 22.04 + the deps in `dependencies.sh`).
- The **entire repo** is bind-mounted into the container at `/repo`
  (`volumes: - ..:/repo`, where `..` is the repo root because the compose
  *context* is the repo root). The OpenWrt tree therefore appears inside the
  container as `/repo/openwrt`, which is the container's working directory.
- `build.sh` runs on the host. For every build step it shells into the container
  via `docker compose exec builder …` (the `docker_exec` helper). **All
  `make`/`patch`/`cp` work happens inside the container.**
- The container runs as a **non-root user whose UID/GID equal the host user**
  (`HOST_UID`/`HOST_GID`, defaulted from `id -u`/`id -g`). That is why files
  written under `/repo/openwrt` during a build are owned correctly back on the
  host — no `sudo` cleanup needed.
- Networking is **host** mode so the container can fetch OpenWrt feeds/sources.

**Why this separation exists:**

1. **Reproducibility.** Every developer and CI uses the *same* Ubuntu 22.04 +
   pinned dependency set, so "works on my machine" build failures disappear.
2. **Isolation.** The OpenWrt build is invasive (writes gigabytes, needs
   specific tools); it stays inside an ephemeral container and never pollutes
   the host.
3. **The OpenWrt tree is a mount, not a copy.** Because `openwrt/` is the
   bind-mounted repo subdir, build output is visible on the host immediately and
   is naturally excluded from git (it is a build artifact).

**Mental shortcut:** *you edit on the host, `build.sh` builds in the container,
the container writes back into the host's `openwrt/` through the `/repo` mount.*

---

## 6. A build, end to end

~~~
./build.sh build hmuf02v5
        | (host)
        |- ensure_builder()        docker compose up -d builder
        |- openwrt-version.sh      read OPENWRT_VERSION from devenv/.env
        |- ensure_openwrt()        clone / checkout openwrt/ at that version
        |- prepare_tree()  -------> (container) openwrt-prepare.sh
        |                             - install msm89xx target + packages
        |                             - cp -a openwrt-overlay/  -> openwrt/
        |                             - patch -p1 openwrt-patches/*.patch
        |                             - ./scripts/feeds update/install
        |- sync_bsp()  -----------> (container) cp -a msm89xx  -> openwrt/target/linux/
        |                             cp -a packages -> openwrt/package/msm8916
        |                             assert_bsp_synced()   (diff -rq guard)
        |- prepare_config()        copy diffconfigs/hmuf02v5 -> openwrt/.config
        |- run_openwrt_make() ----> (container) make -jN  ->  openwrt/bin/targets/…
        '- image + packages land in openwrt/bin/   (visible on host via /repo)
~~~

Selective subcommands (`kernel`, `package`, `kmod`) reuse the same
`ensure_prepared` → `sync_bsp` → `make &lt;target&gt;` path but stop at the relevant
`make` goal instead of the full image.

---

## 7. Where to look for what

| I want to… | Read |
| :---------- | :--- |
| Run a build / know the `build.sh` flags | [BUILD_SYSTEM_GUIDE.md](BUILD_SYSTEM_GUIDE.md) |
| Know which folder a change belongs in | [PATCHES_AND_PACKAGING.md](PATCHES_AND_PACKAGING.md) |
| Understand BSP-as-source-of-truth | [msm8916-openwrt-build-flow.md](msm8916-openwrt-build-flow.md) |
| Understand the Docker builder | [Docker/README.md](Docker/README.md) |
| See every patch catalogued | [Patches/README.md](Patches/README.md) |
| Bump the release version | `sync-version.sh` (§4) + `version` file |
| Build the recovery image | `build-recovery.sh` (§4) |

---
*Written 2026-10-25. Reflects `build.sh` as of commit `79d102e` (guard-the-patch-tree) and the `v25.12.5` pinned tree.*

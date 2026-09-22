# 161 — BUILD TOOLING: a patch-sync guard, and selective kernel / package / kmod builds

**Date:** 2026-09-21. **Tooling only** — `build.sh` is the only file changed. No firmware, driver,
DTS, patch or config change; the deployed module is untouched.

**Status:** **implemented and validated.** Ten checks pass (§6), including a real drift-injection
cycle that proves the guard both detects and heals.

**Why it exists:** Doc 160 found that the kernel build reads the **live**
`openwrt/target/linux/msm89xx/` tree, not the tracked `msm89xx/`, and that the live tree had gone
stale at 14 patches — so a re-prepare would have silently reverted four fixes including patch 812's
root-cause data-stall fix. The first half of this doc closes that hole automatically. The second half
adds what was missing for day-to-day driver work: a way to rebuild **one** thing instead of the whole
firmware.

---

## 1. The guard

### 1.1 The invariant

`sync_bsp()` mirrors two tracked trees into the prepared OpenWrt tree:

| tracked | live |
| :-- | :-- |
| `msm89xx/` | `openwrt/target/linux/msm89xx/` |
| `packages/` | `openwrt/package/msm8916/` |

`bsp_drift()` (`build.sh`) compares both with `diff -rq` and returns **0 = in sync, 1 = drift**,
printing the differing paths (capped at 20) on drift. It runs on the **host**, which is correct
because the repo is bind-mounted at `/repo`, so `openwrt/` on the host *is* the container's tree.

### 1.2 Report before healing, assert after

`sync_bsp()` does `rm -rf live && cp -a tracked live` — that is the existing, intended behaviour and
the reason the tracked tree is authoritative. What was missing was visibility and verification:

* **Before** the copy: if `bsp_drift` reports a difference, the drift is printed and explained
  (*"this normally means a build ran outside `./build.sh`"*). A divergence is never repaired silently.
* **After** the copy: `assert_bsp_synced()` re-checks, and **dies** if the live tree still differs. A
  partial or failed copy used to mean a kernel built from the wrong patch set with no error at all.

### 1.3 Both install paths are covered

`sync_bsp()` is only the **fast** path. A fresh prepare goes through `scripts/openwrt-prepare.sh`,
which installs the same trees itself (`install_target`/`install_packages`, `openwrt-prepare.sh:178-197`).
So `assert_bsp_synced()` is also called at the end of both `ensure_prepared()`'s slow path and
`force_prepare()`. Every install path is now held to the same invariant.

### 1.4 `./build.sh guard [--deep]`

A standalone pre-flight that runs `bsp_drift()` and exits **non-zero on drift**, so it is usable in a
script or before any manual container work. `check_requirements` (which hard-requires a running Docker
daemon) is **skipped for `guard` without `--deep`** — the check is most useful precisely when the
build environment is not running.

## 2. The deep check — "will the next build re-prepare?"

A re-prepare wipes `build_dir` and costs a full kernel rebuild, so it is worth predicting before
starting.

**This is deliberately not a re-implementation of OpenWrt's `find_md5`.** That hash covers
**absolute paths**, which differ between host (`/home/.../openwrt`) and container (`/repo/openwrt`),
so a host-side copy would compute a different value. Instead the guard **asks make for its own
value**:

```
cd /repo/openwrt/target/linux/msm89xx
make TOPDIR=/repo/openwrt TARGET_BUILD=1 -r -s --no-print-directory \
     --eval='print-stamp: ; @printf "%s\n" "$(STAMP_PREPARED)"' print-stamp
```

Three details make this work, each found by testing rather than assumed:

* **`make -p` is not enough.** `STAMP_PREPARED` is a recursively-expanded variable
  (`include/kernel-build.mk:13`), and `make -p` prints recursive variables **unexpanded** — verified
  with a minimal makefile (`FOO = $(shell echo hi)` printed literally). An `--eval`'d target expands
  it correctly at recipe-expansion time.
* **`TOPDIR` must be passed.** It is set by the top-level make and exported to sub-makes; it is *not*
  set in `rules.mk`. Invoking the target Makefile directly without it fails with
  `/target.mk: No such file or directory`.
* **`TARGET_BUILD` must be exactly `1`** — `include/target.mk:389` gates `kernel-build.mk` on
  `ifeq ($(TARGET_BUILD),1)`.

If the returned stamp exists, `build_dir` is consistent with the current patch set (incremental
build). If it does not, a re-prepare is pending. If the query returns nothing, the verdict is
reported as **UNKNOWN** rather than guessed.

## 3. Selective builds

OpenWrt already supports narrower goals — the toplevel `%::` catch-all (`include/toplevel.mk:225-238`)
dispatches any goal through `$(SUBMAKE)`, and `target/linux/Makefile:11` declares
`prereq clean download prepare compile install …`. They only need a `.config`.

| command | make goal |
| :-- | :-- |
| `./build.sh kernel [board]` | `make target/linux/compile V=s` |
| `./build.sh package <name\|path> [board]` | `make <resolved-path>/compile V=s` |
| `./build.sh kmod <name> [board]` | a package, or the kernel target (§3.3) |

### 3.1 `ensure_config` — board optional

With a board, `.config` is rewritten from that board's diffconfig exactly as `build` does. **Without**
one, the existing `.config` is reused, skipping both the copy and `make defconfig` — which is what
makes single-target iteration fast. Neither present is an error with usage.

### 3.2 `package` resolution

Accepts an explicit `package/...` or `feeds/...` path as-is; otherwise resolves, in order:
`package/<name>`, `package/msm8916/<name>`, `package/feeds/*/<name>`, `feeds/**/<name>`, then
`package/**/<name>` for base-tree packages nested under a group. Verified:

| name | resolves to |
| :-- | :-- |
| `dnsmasq` | `package/network/services/dnsmasq` |
| `mac80211` | `package/kernel/mac80211` |
| `curl` | `package/feeds/packages/curl` |
| `qrtr`, `rmtfs`, `reboot-edl` | `package/msm8916/<name>` |

Unresolvable names list near-matches (`package sms` → `package/msm8916/luci-app-sms-manager`) and
exit non-zero.

### 3.3 `kmod` — two genuinely different cases

* **out-of-tree** kmod package (`package/kernel/<name>` or a feed) → built directly.
* **in-tree** (generated from the kernel config — `qcom_bam_dmux` → `kmod-bam-dmux`, with **no**
  package directory) → **there is no narrower goal than `target/linux/compile`**, which rebuilds the
  kernel and every in-tree module. The command says so explicitly rather than pretending otherwise,
  then delegates to the kernel target.

Because the file name rarely matches the module name, it also prints matching modules with md5:
`kmod bam-dmux` → `*bam_dmux*.ko` → `qcom_bam_dmux.ko` (`eca269f1…`, the hash the device is running).

### 3.4 Artifact reporting

Every selective build prints the `*.ko` / `*.ipk` produced since it started (`find -newermt "@<start>"`)
with **md5**, deduplicated by hash — a single build leaves identical copies in `build_dir`, `.pkgdir`,
`ipkg-*` and `root-*`, and five lines with three distinct hashes is noise. The md5 is there so the
result can be compared against the device immediately.

## 4. `DRY_RUN=1`

`run_openwrt_make` prints the command instead of executing it. This is what made the new commands
validatable without a multi-minute build. It is **fully side-effect-free**: `prepare_config`'s
`cp diffconfigs/<board> .config` is guarded too. That guard was added after a dry run *did* clobber
`.config` — leaving the bare diffconfig, and because the following `make defconfig` was skipped,
`TARGET_DIR_NAME` evaluated to `_` and the stamp path became `target-_/…`. Caught by the deep check
printing a mangled path, and fixed on both sides (the guard, and restoring `.config` for `hmu05`).
**A testing affordance that mutates state is worse than no testing affordance.**

## 5. Validation (all ten pass)

1. `bash -n build.sh` — syntax.
2. `./build.sh guard` — in sync, exit 0.
3. `./build.sh guard --deep` — prints the real `STAMP_PREPARED` and a verdict.
4–6. `DRY_RUN=1 ./build.sh kernel | package qrtr | kmod bam-dmux` — correct goals, no build run.
7. **Drift injection:** a stray file in the live tree → `guard` reports it and exits **1**.
8. A build reports the drift before healing it.
9. `guard` returns to **in sync**, exit 0.
10. The stray file is gone (the sync healed it).

Also verified: `DRY_RUN=1` leaves `.config` byte-identical (md5 unchanged), and exit codes are
non-zero for unknown package and missing arguments.

The deep check's verdict is **cross-checked against ground truth**: the live tree was synced 14→18
earlier the same day, so the stamp must have changed. On-disk stamp
`.prepared_355cb72e7a69043f8b20402138589420`; computed `.prepared_28a7f3ba27781b9fb696ad9199a34d30`
→ **"re-prepare pending"**, which is the correct answer.

## 6. What this does NOT cover

* The guard protects builds that go through `build.sh`. A `make` run **directly inside the container**
  (e.g. via `./build.sh shell`) still reads whatever the live tree happens to be. That is exactly what
  `./build.sh guard` is for. **Documented, not solved.**
* No `deploy` command was added — artifacts are reported with md5s for manual copy.
* `sync_bsp`'s destructive `rm -rf live` semantics are unchanged. Any hand-edit to the live tree is
  still lost on the next build; the guard's job is to make that visible, not to prevent it.
* Nothing here changes the modem, the 902 s fatal, or the AP-side hangs.

## 7. Usage

```
./build.sh guard [--deep]
./build.sh kernel  [board]
./build.sh package <name|path> [board]
./build.sh kmod    <name> [board]
DRY_RUN=1 ./build.sh <any build command>
```

## 8. One line

**The build now refuses to produce a kernel from a patch set the tracked sources do not describe —
it reports the drift, heals it, and verifies the result on both install paths — and it can rebuild
just the kernel, one package, or one kernel module, printing each artifact's md5 for comparison
against the device.**

## 9. Provenance and SOP compliance

* Implemented and validated 2026-09-21 on the build host `192.168.8.243`.
* **SOP compliance (Dual-Firmware Comparative Workflow):** **no baseband work, no firmware, driver,
  DTS, patch or NV change.** This is build tooling, so SOP steps 1–5 are not applicable. The one
  config touched (`openwrt/.config`, gitignored) was **restored to the `hmu05` board config that
  produced the deployed images** — verified: 8383 lines, 28 `CONFIG_TARGET*` entries, images in
  `bin/targets/…/openwrt-msm89xx-msm8916-generic-hmu05-*`. Ground-truth discipline was applied in the
  form the task allows: every claim above is a command that was actually run, and the deep check's
  verdict was cross-checked against a known-good stamp. One self-inflicted side effect was found and
  recorded rather than hidden (§4).
* The device was **not touched** at any point; soak run 8 continued throughout (3 fatals, 3 SSRs, all
  recovered, `retries 0`, `rebuilds 0`).
* Related: Doc 160 (the stale-live-tree finding this guard automates); Doc 147 §7 (the same drift
  class, patch 809); Doc 155/156/157 (patches 810/811/812/814, which a stale live tree would drop).

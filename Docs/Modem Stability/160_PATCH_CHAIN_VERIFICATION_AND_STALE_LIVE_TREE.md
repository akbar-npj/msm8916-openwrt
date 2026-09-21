# 160 — THE PATCH CHAIN IS VERIFIED IN THE KERNEL, AND THE *LIVE* TARGET TREE WAS STALE

**Date:** 2026-09-21. Verification only — **no firmware, driver or DTS change was made in this
session.** One derived build directory was re-synced from the tracked source of truth.

**Status:** **closed, with a hazard found and fixed.** The question asked was *"as source of truth
`msm89xx/patches`, check whether the patches landed in kernel or not."* The answer is **yes for the
built kernel** — all 18 tracked patches are byte-for-byte present in the kernel that produced the
deployed module. But the check also found that the **live OpenWrt target patch directory was stale**
(14 patches, missing `810`, `811`, `812`, `814`), and that a `make` which triggered a re-prepare in
that state would have produced a kernel whose `qcom_bam_dmux.c` **silently lacked the root-cause fix
for the data stall**. The live directory has been synced; both trees are now byte-identical and the
reconstruction re-verifies.

**Deployed module (unchanged by this session):** `qcom_bam_dmux.ko` md5
`eca269f10a685a83a8b679bc7be38d1d` — the patch-814 production build.

---

## 1. Why this doc exists

The corpus has one previous entry on this class of problem (Doc 147 §7): the `809` PM-ordering oops
fix existed **only** in `build_dir` and in no patch at all, so a rebuild would have reverted it and
reintroduced the `wwan0`-down kernel panic. It was fixed by adding patch 809 and syncing the trees.

That fix addressed the *tracked* tree. It did not address the fact that the kernel build does not read
the tracked tree — it reads the **live** `openwrt/target/linux/msm89xx/patches/` directory, which is
gitignored and only refreshed by `build.sh`. Four more patches (`810`, `811`, `812`, `814`) were added
to the tracked tree afterwards, and the live tree was never refreshed. The user asked for this check,
and it found exactly the same class of drift, in the opposite direction, with the same consequence.

## 2. The answer

**Part 1 — the built kernel contains every patch. Yes.** Reconstructing the kernel from the pristine
6.12.94 tarball plus all 18 tracked patches yields a tree that is **byte-identical to the built kernel
for all 17 target files** (17/17 `MATCH`, 0 `DIFF`, 0 `MISSING`). Nothing in the deployed kernel came
from anywhere other than the tracked patches.

**Part 2 — the live target tree was stale, and that is a real defect.** `msm89xx/patches/` had **18**
patches; `openwrt/target/linux/msm89xx/patches/` had **14**, missing:

| missing from the live tree | what it fixes |
| :-- | :-- |
| `810-bam-dmux-tx-sweep-race.patch` | the second `bam_dmux` NULL deref (TX-sweep race), Doc 155 |
| `811-bam-dmux-deferred-tx-telemetry.patch` | the `defer_q`/`defer_sub` counters, Doc 156 |
| `812-bam-dmux-preserve-deferred-tx.patch` | **the root cause of the steady-state data stall**, Doc 156 |
| `814-bam-dmux-ssr-powerup-retry.patch` | the SSR powerup give-up (third AP-side defect), Doc 157 |

Because the *live* tree is what the kernel build actually reads, the drift was a live hazard, not a
cosmetic one.

## 3. Method (reproducible)

```
TAR=openwrt/dl/linux-6.12.94.tar.xz          # 148,301,108 B, the exact kernel OpenWrt builds from
BUILT=openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94

# 1. pristine tree
mkdir -p /tmp/repchk && tar -xf $TAR -C /tmp/repchk

# 2. apply the tracked patch set (OpenWrt's own invoker: patch -f -p1)
for p in msm89xx/patches/*.patch; do patch -f -p1 -s < $p; done

# 3. compare all 17 target files against the built kernel
for f in $(collect '^+++ b/' from every patch); do cmp -s /tmp/repchk/linux-6.12.94/$f $BUILT/$f; done
```

Result: **`MATCH=17 DIFF=0 MISSING=0`** against the built kernel. Repeating the same procedure with
the live tree's 14-patch set gives **`MATCH=16 DIFF=1`**, the one difference being
`drivers/net/wwan/qcom_bam_dmux.c`.

## 4. The mechanism — why the *live* tree is a build input

This is the part worth remembering, because it is counter-intuitive:

* `PATCH_DIR ?= $(CURDIR)/patches$(if $(wildcard ./patches-$(KERNEL_PATCHVER)),-$(KERNEL_PATCHVER))`
  (`include/kernel.mk:43`) resolves to `target/linux/msm89xx/patches` — the **live** directory.
* `KERNEL_FILE_DEPENDS=$(GENERIC_BACKPORT_DIR) $(GENERIC_PATCH_DIR) $(GENERIC_HACK_DIR) $(PATCH_DIR) …`
  (`include/kernel-build.mk:12`) — so **the live patch directory is part of the prepare stamp**.
* `STAMP_PREPARED=$(LINUX_DIR)/.prepared$(if $(QUILT)$(DUMP),,_$(shell $(call find_md5,$(KERNEL_FILE_DEPENDS))))`
  (`include/kernel-build.mk:13`) — the stamp is an **md5 of the patch directories**.
* `Kernel/Prepare/Default` extracts the tarball (**tar does not delete existing files**) and then
  runs `$(Kernel/Patch)`. Only the stamp rule does `-rm -rf $(KERNEL_BUILD_DIR)`
  (`include/kernel-build.mk:92-96`).

So: **change the live patch directory → the stamp's md5 changes → the stamp is invalid → the next
`make` wipes `build_dir` and re-extracts + re-patches from the live directory.** The tracked
`msm89xx/` tree is the source of truth only because `build.sh` copies it over the live directory
first — `sync_bsp()` does
`rm -rf $CONTAINER_OPENWRT_DIR/target/linux/msm89xx && cp -a $CONTAINER_REPO_DIR/msm89xx $CONTAINER_OPENWRT_DIR/target/linux/`
(`build.sh:336`), called from `ensure_prepared()`. **If a build is driven any other way, or the sync
runs before the patches are added, the two trees drift and the build follows the stale one.**

## 5. What a stale re-prepare would have cost

A clean-room simulation (pristine tarball + the live 14-patch set) produces a
`drivers/net/wwan/qcom_bam_dmux.c` that differs from the built kernel by **26 hunks, +303 / −26
lines**, i.e. **277 lines shorter**:

| | lines | md5 |
| :-- | --: | :-- |
| built kernel (= pristine + all 18) | 2598 | `a8a3811f88d4cb19c0117d5ee188c4a8` |
| pristine + live 14 | 2321 | `44a78a0fd22bb26a9a2af905f8550662` |

All four missing patches touch **one file**, which is why the damage is concentrated: `810` and `811`
are unambiguously absent (`tx_sweep_guard_hits` 7→0 occurrences, `defer_q`/`defer_sub` 6→0), and
`812`'s additional `bam_dmux_pm_restart()` hunks are missing (12→9 occurrences). A rebuild in that
state would have **silently reverted the data-stall root-cause fix** while the counters that would
have revealed it (`defer_q`, `defer_sub`) were *also* absent — so the regression would have been
invisible in exactly the telemetry that documents it.

## 6. The fix applied

```
cp -a msm89xx/patches/. openwrt/target/linux/msm89xx/patches/
```

Both directories are now **18 patches, byte-identical** (`diff -r` silent; file lists and contents
identical). The reconstruction was then re-run **from the live tree** and again gives **17/17 MATCH**
against the built kernel, so the synced tree reproduces the deployed kernel exactly.

`openwrt/` is gitignored (`.gitignore:5`), so this produced no tracked change; `git status` is clean.
The tracked source of truth is unchanged.

## 7. Two traps found while doing this check

Both are worth recording because each produced a **wrong intermediate answer** that a less careful
pass would have shipped:

1. **A new file is not always `--- /dev/null`.** Patch `813` creates
   `drivers/power/reset/msm-poweroff.h` using the git-style new-file form `--- a/drivers/power/reset/msm-poweroff.h`
   with hunk header `@@ -0,0 +1,16 @@`. A scan for `--- /dev/null` therefore *missed* it, and the
   first simulation's "restore pristine" step did not remove it, so the second pass re-applied the
   patch on top of its own output and produced a **duplicated file**. That showed up as a spurious
   `DIFF: drivers/power/reset/msm-poweroff.h`. **Detect created files by `@@ -0,0` in the hunk
   header, not by the `---` path.** (The corrected run resets by deleting all 17 target files and
   restoring whatever the tarball actually contains, with an assertion that the created file is gone.)
2. **A first-run "804/805/806 FAILED — would create the file which already exists" was an artefact.**
   That result came from applying patches into a tree that already contained their output. On a
   genuinely pristine tree, **all 14 stale patches apply cleanly**; the real defect is the *missing
   four*, not a patch failure. **An experiment that reports a failure must be checked for a dirty
   input before the failure is believed** — the same lesson as the `tail -8` harness blind spot
   (Doc 159 §9) and the invalid-ramoops case (Doc 159 §11), in a different medium.

## 8. What is NOT claimed

* This is a **build-integrity** result. It says nothing new about the modem, the 902 s fatal, or the
  AP-side hangs. The deployed module is the same one Doc 159 characterised, and it is unchanged.
* The built kernel **did** contain all four fixes — the deployed device was never running the
  deficient kernel. The hazard was to a *future* rebuild, not to the current deployment.
* No guard has been added that would **prevent** the drift automatically. §9 item 3 proposes one; it
  is not implemented.

## 9. Next steps

1. **All patches go into `msm89xx/patches/` and nowhere else.** Never hand-edit the live tree; it is
   derived and is wiped by `build.sh`.
2. **Before any forced re-prepare** (`rm -f build_dir/…/linux-6.12.94/.prepared*`, a manual
   `make target/linux/prepare`, or a build driven outside `build.sh`), re-run §3's comparison. It is
   cheap and it is the only thing that catches this.
3. **Proposed guard (not implemented):** a pre-flight assertion that `msm89xx/patches/` and
   `openwrt/target/linux/msm89xx/patches/` are identical, so a mismatch is an error rather than a
   silent rebuild from the wrong set.
4. Continue the standing Doc 159 §8 work (instrument the 43–47 ms window) — unaffected by this.

## 10. Artifacts

| file | what |
| :-- | :-- |
| `openwrt/dl/linux-6.12.94.tar.xz` | the pristine source, 148,301,108 B — the reconstruction's input |
| `/tmp/repchk/sim.sh` | the clean-room simulation (v2; the v1 defect and its correction are in §7) |
| `/tmp/repchk/out_full/`, `/tmp/repchk/out_stale/` | the two reconstructed trees' 17 target files |

## 11. One line

**All 18 tracked patches are byte-for-byte present in the built kernel (17/17 files), but the live
OpenWrt target patch directory was stale at 14 — and because the *live* directory, not the tracked
one, is what the build reads, a re-prepare would have silently reverted four fixes in one file,
including patch 812's root-cause fix for the data stall; both trees are now synced and the
reconstruction re-verifies.**

## 12. Provenance and SOP compliance

* Checked: 2026-09-21, on the build host `192.168.8.243`. Device `192.168.8.1` was up and healthy
  (soak run 8 live) and was **not** modified.
* **SOP compliance (Dual-Firmware Comparative Workflow):** this session performed **no baseband
  work and modified no firmware, driver, DTS or NV item.** It is a *build-provenance* verification, so
  SOP steps 1–5 (backup → ground-truth verification → transport/config reconciliation → surgical
  Hexagon patching + re-signing → live empirical validation) are **not applicable**. The one change
  made is to a **derived, gitignored build directory**, re-synced from the tracked source of truth,
  which is the SOP's own reproducibility requirement (Doc 147 §7). Ground-truth discipline was still
  applied in the form the task allows: every claim above is a **byte comparison** against the actual
  built kernel or the actual pristine tarball, not an inference from a patch's name or a build log.
  Two wrong intermediate results were caught and corrected (§7) and are recorded rather than hidden.
* Related: Doc 147 §7 (the same drift class, `809`); Doc 155 (`810`), Doc 156 (`811`/`812`), Doc 157
  (`814`); Doc 159 (the module this kernel builds, and the `tail -8` / invalid-ramoops precedents for
  §7 item 2).

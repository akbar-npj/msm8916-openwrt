# 187 — The `/overlay` shadowing defect: why the flash did not deploy, the fix, and the byte-level proof

**Date:** 2026-09-23
**Trigger:** *"Fix overlay shadowing before soak"*
**Outcome:** fixed and verified. Patch 825 and the gated `LOG_LEVEL` are now live; the
ModemManager settle-timer patch (0005) and WS2 were re-verified as live too.
**Nothing about the modem firmware, the kernel source, or the patch set was changed.** The only
writes were two `unlink`s on the device's overlay partition.

---

## 1. SOP Compliance Statement

| SOP step | Status |
|---|---|
| backup | **PERFORMED before any write.** Both shadowing files copied to `/tmp/ovl_backup/` on the device **and** pulled to `evidence/112_bam_reinit_ab/overlay_backup_2026-09-23/`; md5-verified on both sides against the originals. |
| ground-truth verification | **PERFORMED, and it is the substance of this document.** "Which build is live" was decided by **section/segment byte comparison against the build tree**, not by filename, timestamp, or md5-of-file. |
| reconcile transport/config | **SKIPPED, deliberately.** No transport or config file was touched; 11 shadowing config files were deliberately **left alone** (§6). |
| Hexagon patching + re-signing | **SKIPPED, deliberately.** No firmware was touched. The modem's `/lib/firmware/modem.*` lives on the overlay (§6) and was not read or written. |
| live empirical validation | **PERFORMED.** Pre-fix control boot frozen; post-fix boot verified on-device by three independent fingerprints (§4). |

**Errors caught and corrected in this session** (recorded per `134_…` §2):

1. **My first un-shadow attempt "failed" and it was a misreading, not a failure.** After deleting
   the upper copies, the merged view still showed the old files — with `ls` reporting **link count
   0**. That is an unlinked-but-pinned inode held by overlayfs's dentry cache, not a surviving
   whiteout. The disk state was already correct; only the live view was stale.
2. **A deployment-verification tool that silently returns nothing is worse than no tool.** The first
   version of `verify_deployed_artifacts.py` compared `PT_LOAD` segments. Kernel modules are
   `ET_REL` and have **no program headers at all**, so for the very artifact that mattered it
   printed an empty result and exited 0. Fixed with an `SHF_ALLOC` section fallback and an explicit
   "NOTHING TO COMPARE — refusing to report a result" guard.

---

## 2. The defect

`sysupgrade` **preserves `/overlay`**. The device's root is

```
/dev/mmcblk0p14 on /rom     type squashfs (ro)
/dev/mmcblk0p15 on /overlay type ext4     (rw)
overlayfs:/overlay on /      lowerdir=/, upperdir=/overlay/upper, workdir=/overlay/work
```

so any file present in `/overlay/upper` **shadows** the newly-installed image copy in `/rom` — the
new file is on disk, the image hash verifies, `sysupgrade` reports success, and the **old bytes are
what actually run**. A flash that silently does not deploy is the worst kind of failure for a
measurement campaign, because the soak would then be scored against a patch that was never loaded.

The pre-fix census (`/overlay/upper` vs `/rom`) found **13 DIFFER** and **58 ONLY-UPPER** files.
Exactly two of the 13 mattered:

| path | overlay (live) | `/rom` (image) |
|---|---|---|
| `lib/modules/6.12.94/qcom_bam_dmux.ko` | `982b633e2a682e21ad69b6e85d273941` (241,232 B, Sep 22 08:07) | `9c08871ff0d29f714acfa89b41893898` (44,576 B) |
| `etc/init.d/modemmanager` | `224d919a683225aa2238d3f1c799b33e` | `9714fb2f6a5adbfb53f73a24d49a1bb7` |

The other 11 are **legitimate user state** (`etc/config/{uhttpd,cpu-perf,firewall,luci,dhcp}`,
`etc/apk/*`, `lib/apk/db/*`, `etc/inittab`) and must not be reverted — see §6.

**Patch 0005 (ModemManager) and WS2 were NOT affected**, because neither had an overlay copy
(`/overlay/upper/usr/sbin/` and `/overlay/upper/lib/netifd/proto/` do not exist). §5 shows how
easy it is to conclude the opposite.

---

## 3. The fix

Two `unlink`s, on the **upperdir's own path**, not through the overlay mount:

```sh
rm /overlay/upper/lib/modules/6.12.94/qcom_bam_dmux.ko
rm /overlay/upper/etc/init.d/modemmanager
```

**Trap 1 — the path matters.** `rm /lib/modules/.../qcom_bam_dmux.ko` (the merged path) makes
overlayfs create a **whiteout** in the upperdir, which hides the lower file *entirely* — the module
would be gone, not reverted to `/rom`. Deleting under `/overlay/upper/…` is a plain ext4 `unlink`
and simply un-shadows.

**Trap 2 — the live view lies until you reboot.** Immediately after the unlinks the merged view
still showed the **old** files, and `ls -la` reported **link count 0**. The upper inodes were
unlinked but still pinned by overlayfs's cached dentry. A reboot re-mounts overlayfs, the upperdir
has no copy, and the `/rom` file is used. (Reboot was needed anyway: `qcom_bam_dmux` was loaded.)

Reboot was clean: **down within ~3 s, reachable again in ~20 s** at uptime 23.5 s.

---

## 4. Verification — three independent proofs

### 4.1 The census moved 13 → 11 DIFFER

Post-reboot, `lib/modules/6.12.94/qcom_bam_dmux.ko` and `etc/init.d/modemmanager` are **gone from
the DIFFER list**; the remaining 11 are exactly the intended config files, and ONLY-UPPER is
unchanged at 58. No copy was recreated by the boot.
Evidence: `postfix_census_2026-09-23.txt`.

### 4.2 The *loaded* module changed — proven by `srcversion`

`/sys/module/qcom_bam_dmux/srcversion` reports the identity of the module **in the kernel**, not on
disk:

| | srcversion |
|---|---|
| before (shadowed, pre-patch) | `87BDBBC2A4B1754DED81D66` |
| after (un-shadowed) | **`A9AC55FEA83B66B8EC1DD97`** |

This is the single most important line in the document: it proves the kernel is running different
code, which no file hash can show.

### 4.3 The live module is byte-identical to the image artifact

`verify_deployed_artifacts.py` compares the **loadable image**, not the file. For an `ET_REL`
kernel module that means the `SHF_ALLOC` sections. Live vs the image's `kmod-bam-dmux` artifact:

```
section .text        size=0x3af0 md5=91329d0cba38e0f0 IDENTICAL
section .rodata.str1.8  ...                          IDENTICAL
section .modinfo     ...                             IDENTICAL
  ... all 20 SHF_ALLOC sections IDENTICAL
```

And the **pre-fix shadowing copy** against the patched build, to show the patch is really in there:

```
section .text   size=0x3a70 md5=49167f127f77c35f DIFFERS  (11213/14960 = 74.95 %)
section .modinfo   srcversion 87BDBBC2A4B1754DED81D66  ->  A9AC55FEA83B66B8EC1DD97
section .rodata.str1.8   contains "...pc_state=1 but pc line low (stale edge), reconciling"
```

The `.modinfo` srcversion in the **file** matches the two `srcversion` values read from the
**kernel** — so the disk copy, the image copy, and the running module are all the same build.
Evidence: `deployment_verification_2026-09-23.txt`.

---

## 5. The false alarm: an md5 mismatch that is NOT a deployment failure

`/usr/sbin/ModemManager` on the device is `8182d40d91396df70e3f02ca91ab7a6b`. It matches **neither**
build artifact — not the pre-install binary (`aee3e7516424bd29b7343c0191f6ec83`) and not the
package-staged one (`74d0bf8898aba17edea69f2c13e25020`). Sizes differ too (2,575,540 vs 3,295,328 B).
That looks exactly like "patch 0005 did not deploy".

**It had deployed.** Comparing loadable segments byte-by-byte:

| pair | r-x segment | rw- segment |
|---|---|---|
| device vs `.pkgdir` | **5 differing bytes / 2,548,936 (0.0002 %)** | IDENTICAL |
| device vs pre-install | 35 differing bytes | 108 differing bytes |

All 5 bytes of the first row are in the **ELF header**, and all three are fields that only
*describe the section header table* — which OpenWrt removes on the way into the image:

| offset | field | device | `.pkgdir` |
|---|---|---|---|
| `0x28` | `e_shoff` | `0` | `0x323f20` |
| `0x3c` | `e_shnum` | `0` | `37` |
| `0x3e` | `e_shstrndx` | `0` | `36` |

The 30 extra bytes in the second row are the RPATH rewrite (`$ORIGIN/../libmm…`) that OpenWrt's
install step performs, which is why the device matches the **post-install** artifact and not the
pre-install one. So the device's ModemManager is the patched build, stripped — and
**md5-of-file can never prove a deployment on this platform.**

> **Rule.** Verify a deployment with the *loadable image* (PT_LOAD segments for ELF, `SHF_ALLOC`
> sections for `.ko`) or with a runtime fingerprint (`srcversion`, a patch-only string). Never with
> `md5sum` of the installed file.

---

## 6. What was deliberately NOT changed

* **The 11 remaining DIFFER files.** `etc/config/{uhttpd,cpu-perf,firewall,luci,dhcp}` hold this
  device's network/WiFi/firewall settings; `etc/apk/*` and `lib/apk/db/*` are package-manager
  state; `etc/inittab` is a user setting. Reverting them to image defaults would lose the device's
  configuration for no benefit. (`diff` is not installed on the device, so `etc/inittab` was not
  text-diffed — it was left alone.)
* **The 58 ONLY-UPPER files.** These have no `/rom` counterpart, so they are additions, not
  shadows. They include `/etc/config/{wireless,network,system}`, the dropbear host keys, and —
  importantly — **the entire modem firmware set** (`/lib/firmware/modem.b*`, `modem.mdt`, `mba.mbn`,
  `cmnlib.*`, `keymaste.*`, `wcnss.*`, `mcfg_sw.mbn`). That is expected and correct: the baseband is
  deployed on the overlay and a `sysupgrade` does not change it
  (`project_modem_firmware_deployment`).
* **Any future `sysupgrade`.** The fix removes the *shadowing*, not the *mechanism*. If a future
  session deploys a file by hand into `/lib/modules` or `/etc/init.d`, it will shadow the image
  again. Re-run the census after every flash.

---

## 7. What is live now

| artifact | proof it is live |
|---|---|
| **patch 825** (`qcom_bam_dmux.ko`) | file `9c08871f…`; all 20 alloc sections identical to the image artifact; loaded `srcversion` `A9AC55FE…`; patch-only string `…pc_state=1 but pc line low (stale edge), reconciling` present |
| **gated `LOG_LEVEL`** (`/etc/init.d/modemmanager`) | file `9714fb2f…`; line 12 is `[ -f /etc/modemmanager-log-level ] && LOG_LEVEL="$(cat …)"`; `/etc/modemmanager-log-level` does **not** exist ⇒ **INFO**, matching the pre-fix control |
| **patch 0005** (ModemManager settle timers) | device binary vs `.pkgdir` differs in 5 bytes, all ELF-header section-table descriptors (§5) |
| **WS2** (`modemmanager.sh`) | `/lib/netifd/proto/modemmanager.sh` == `/rom` == repo source `5829474f…`; `mm_deadline` present ×2; no overlay copy exists |

Modem healthy after the reboot: `remoteproc0/state = running`, `wwan0` UP/LOWER_UP.

**Pre-fix control frozen for the soak** (this was the last boot running the shadowed, pre-patch
module): `evidence/112_bam_reinit_ab/control_boot_postflash_oldmodule.txt`
(md5 `0bfe64ba1888add0298856feb04d71fd`) — **3 distinct fatals**: `a2_power.c:1189` @ 242.227 s,
`lte_ml1_sleepmgr_stm.c:4054` @ 1144.736 s, `a2_power.c:2949` @ 1983.653 s. Also captured:
`pstore_console-ramoops-0_2026-09-23.txt` (23 fatal lines) and
`prereboot_fingerprint_2026-09-23.txt`.

---

## 8. Residual risk

1. **Re-shadowing is a live hazard.** Nothing prevents it. The census is the check; run it after
   every flash, and treat a `sysupgrade` that "succeeded" as unverified until §4.2's `srcversion`
   has moved.
2. **`srcversion` is the cheap canary.** `cat /sys/module/qcom_bam_dmux/srcversion` is a one-line
   post-flash assertion. The expected value after this fix is `A9AC55FEA83B66B8EC1DD97`.
3. **The device has no `diff`, no `modinfo`, no `stat`.** Census and fingerprint scripts must be
   written for busybox.
4. **Do not re-flash with `sysupgrade -n` to fix this.** It would destroy the overlay and with it
   the device's configuration — and, given the firmware lives there, is a much larger action than
   the problem requires.

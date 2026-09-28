# 219 — DIAG tooling as OpenWrt packages: `diag-bind`, `diag-efs` (with a guarded EFS write), `diag-logtool`

**Date:** 2026-09-28
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.
**Status:** **RESULTS.**

**Companions:** `196` (the DIAG bridge + `diag_logtool` reference — the *tool*, this doc is the *package*),
`136` (the DIAG transport), `197` (the ledger), `218` (the last doc before this one).

---

## 1. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Package the SMD DIAG bridge helper as an OpenWrt package | `diag-bind` installs `/usr/sbin/diag-bind`; `opkg`/`apk` installable; auto-pulled by dependents | Done. `PKG_NAME:=diag-bind`, `DEPENDS:=+kmod-qcom-rproc-modem`, installs the helper + a watcher + a procd service + a uci-default. | MET |
| Make the bridge survive a modem SSR | Unattended captures do not go silent after the first ~900 s restart | `diag-bind-watch` polls the rpmsg device and re-binds when unbound. **Verified: manually unbound the DIAG device → `/dev/rpmsg0`+`/dev/rpmsg1` returned in ~6 s**, with a syslog line and a status file. | MET |
| Package `diag_efs` (read the modem's EFS, decrypted on the fly) | Same behaviour as the standalone `scratch/uz801_fw/diag_efs.c` | Done. `packages/diag-efs`, installs `/usr/bin/diag_efs`, `DEPENDS:=+diag-bind`. | MET |
| Package `diag_logtool` | Same behaviour as the standalone `GitIgnore/compare/diag_logtool.c` | Done. `packages/diag-logtool`, installs `/usr/bin/diag_logtool`, `DEPENDS:=+diag-bind`. | MET |
| Add a **write** path to `diag_efs` | `put <local> <efs-path>` writes an EFS file, verified by read-back | Done — and the write **protocol was reverse-engineered from scratch** (§5). Validated by a real-file modify → MATCH → restore → MATCH cycle. | MET |
| Guard the write | Refuse calibration paths by default; back up an existing target; verify | Done: `path_is_cal()` refuses `/rfnv/` and `/mcs/` without `--force`; existing targets are backed up to `<sanitised>.bak.<epoch>`; every write is read back and printed `MATCH`/`MISMATCH` (non-zero exit on mismatch). | MET |
| Compile for the target | Both C tools build with the real aarch64 OpenWrt toolchain | Done (§7). | MET |
| Keep the corpus honest | Doc + ledger entry | This doc + a §6 append to the ledger; README narrative section. | MET |

**UNEXPECTED:** while cleaning up the write tests, **EFS2 `REMOVE` was found to be sub-command `8`** (§8) —
outside the approved scope, so it is *documented but not implemented* in the package.

---

## 2. SOP-compliance statement

Per Doc 197 §1, modelled on Doc 194 §1 (five steps: backup → ground-truth verification against stock HMU05 →
reconcile transport/config → surgical Hexagon patching + re-signing → live empirical validation):

| Step | Taken? | Note |
| :-- | :-- | :-- |
| 1. Backup | **Yes** | The on-device validation wrote only to a throwaway EFS file and to one dead-technology config file, and **backed up the target before writing**; all 13 test files were removed afterwards and the real EFS was re-read to confirm it is intact. |
| 2. Ground-truth verification vs stock HMU05 | **Yes** | The EFS2 sub-command numbers, the WRITE request layout, the response field offsets, the commit-on-CLOSE rule and the open-flag values were **all measured on the live HMU05 modem**, not assumed from documentation. |
| 3. Reconcile transport/config | **Yes** | The transport (raw DIAG over `/dev/rpmsg0`, no HDLC/CRC) is the one already reconciled in Doc 136/196; the package adds no new transport. |
| 4. Surgical Hexagon patching + re-signing | **N/A** | No baseband patching in this work. The only modem-side mutation is a **guarded, verified EFS file write** through the modem's own EFS API. |
| 5. Live empirical validation | **Yes** | §6 — every claim below is a measurement on the device, not a code reading. |

**Deliberately skipped:** the `put` path was **not** exercised against any calibration file
(`/rfnv/`, `/mcs/`) — only against a dead-technology config and a throwaway file — because a bad
per-unit RF/TCXO value is not recoverable by read-back. That is what the `path_is_cal()` refusal exists for.

---

## 3. What was packaged

Three packages under `packages/` (the directory `scripts/openwrt-prepare.sh:189-200` copies to
`openwrt/package/msm8916/`). All are `PKG_VERSION:=1.0`, `PKG_RELEASE:=1`, `PKG_LICENSE:=GPL-2.0-only`.

| Package | Depends | Installs | Files (md5) |
| :-- | :-- | :-- | :-- |
| `diag-bind` | `+kmod-qcom-rproc-modem` | `/usr/sbin/diag-bind`, `/usr/sbin/diag-bind-watch`, `/etc/init.d/diag-bind`, `/etc/uci-defaults/95-diag-bind` | `Makefile` `9b4fc036…`, `files/diag-bind` `a9c2513b…`, `files/diag-bind-watch` `8f689b56…`, `files/diag-bind.init` `b1c8c200…`, `files/diag-bind.defaults` `dd83b205…` |
| `diag-efs` | `+diag-bind` | `/usr/bin/diag_efs` | `Makefile` `349be7b0…`, `src/diag_efs.c` `56e38545…` (613 lines) |
| `diag-logtool` | `+diag-bind` | `/usr/bin/diag_logtool` | `Makefile` `8f14afe2…`, `src/diag_logtool.c` `21afc46e…` (22 384 B) |

`diag-bind` has an empty `Build/Compile` (it is scripts only). The two C packages use
`Build/Prepare` → `$(CP) ./src/* $(PKG_BUILD_DIR)/` and a `Build/Compile` that calls `$(TARGET_CC)`
directly, so no `PKG_SOURCE`/download is involved — the package builds fully offline.

The `DEPENDS:=+diag-bind` line is what makes the bridge automatic: installing either tool pulls
`diag-bind`, whose uci-default enables and starts the service on first boot.

---

## 4. `diag-bind` — the bridge, and why a *service* and not a one-shot bind

### 4.1 What the bridge is

The modem's SMD DIAG channels are not character devices until something binds
`remoteproc0:smd-edge.DIAG.-1.-1` and `…DIAG_CNTL.-1.-1` to the `rpmsg_chrdev` driver. That binding is
what creates `/dev/rpmsg0` (data) and `/dev/rpmsg1` (control) — the two nodes `diag_efs` and
`diag_logtool` open. `/usr/sbin/diag-bind` does exactly that, idempotently, and prints the node names
actually in use (`DIAG=/dev/rpmsgN`, `CNTL=/dev/rpmsgM` — the index is an ida allocation, not fixed).

### 4.2 Why the one-shot helper is not enough

The binding lives on an rpmsg device object that **does not survive an SSR**. Every modem restart —
and the modem on this platform restarts roughly every **900 s** — tears down and re-creates every
`qcom_smd` channel, which destroys that object. The `driver_override` and the `rpmsg_chrdev` binding
are both lost, and `/dev/rpmsg0` + `/dev/rpmsg1` disappear. The same loss happens on every AP reboot.

The consequence is a **silent failure class**: a capture that was running fine simply stops producing
data, with no error at the point of use (the tool's next `open("/dev/rpmsg0")` fails with `ENOENT`, or a
long soak that never re-opens just goes quiet). This is the exact class Doc 196 §5 records for the
`bam_dmux` silence gap in a different instrument — a dead channel that looks like a dead modem.

**A one-shot bind at boot cannot fix that**, because the thing that destroys the binding happens
~900 s *later*, repeatedly. So `diag-bind` ships a **watcher**:

* `diag-bind-watch` (run by `/etc/init.d/diag-bind` under procd with `respawn`) waits up to 60 s for the
  SMD DIAG device to appear at boot, binds once, then polls every `DIAG_BIND_INTERVAL` (default **2 s**)
  and **re-binds only when the device is present but unbound**.
* It never touches a *bound* device, so a capture that is holding `/dev/rpmsg0` open is not disturbed.
* Every (re)bind is logged (`logger -t diag-bind`) and written to `/root/diag-bind.status`
  (`state=bound|failed`, `time=…`, plus the node names), so a soak can be audited after the fact.
* `START=11` matches the F3 boot-capture service (`diagboot`, Doc 196 §4) so the bridge is up around
  modem power-up; the two coexist because `/usr/sbin/diag-bind` is idempotent.

### 4.3 The one hazard, and the guard that is already in the kernel

Re-binding re-opens the `rpmsg_ept_cb` window in which the endpoint's `priv` is `NULL`. Kernel patch
**819** (`rpmsg-char-guard-null-eptdev`) guards it — the running kernel's built source contains
`if (!eptdev) return 0;`. The helper's header states this explicitly so a reader does not re-open a
closed question.

### 4.4 Validation

Started the service, confirmed the bridge was bound, then **manually unbound the DIAG device** (a
stand-in for an SSR) and confirmed the watcher re-established `/dev/rpmsg0` + `/dev/rpmsg1` within
~6 s, with the expected syslog line and a fresh `/root/diag-bind.status`.

---

## 5. `diag-efs` — the EFS instrument, and the write protocol

### 5.1 What it does (read side, unchanged)

`diag_efs` speaks the **EFS2** subsystem (`0x13`) over `/dev/rpmsg0` and lets the **modem decrypt its
own EFS on the fly**, so it exposes the live, decrypted NV tree without needing the storage key —
the root key is hardware-resident and **per-chip**, so it is not recoverable from the firmware or from
the partition images. Commands:
`list`, `read`, `stat`, `get`, `detect` — plus the new `put`.

Transport is the Doc 136/196 one: requests are **raw** (no HDLC, no CRC); each `read()` returns exactly
one DIAG message; responses carry a trailing 2-byte CRC + `0x7E`. `/dev/rpmsg0` is **exclusive-open**.

EFS2 sub-commands, verified on this device (`0x3E` returns `0x13` bad-cmd):

| Name | Value | Request shape (LE) | Response |
| :-- | --: | :-- | :-- |
| OPEN | 2 | `4b 13 02 00 │ oflag(u32) mode(u32) │ path\0` | handle@4, err@8 |
| CLOSE | 3 | `4b 13 03 00 │ fdata(u32)` | — |
| READ | 4 | `4b 13 04 00 │ fdata(u32) nbytes(u32) offset(u32)` | fdata@4, off@8, nread@0xc, err@0x10, data@0x14 |
| **WRITE** | **5** | `4b 13 05 00 │ fdata(u32) offset(u32) │ data[…]` | fdata@4, off@8, nwritten@0xc, err@0x10 |
| OPENDIR | 11 | `4b 13 0b 00 │ path\0` | dirp@4, err@8 |
| READDIR | 12 | `4b 13 0c 00 │ dirp(u32) seqno(u32)` | 9 u32 then name |
| CLOSEDIR | 13 | `4b 13 0d 00 │ dirp(u32)` | — |
| STAT | 15 | `4b 13 0f 00 │ path\0` | — |
| FSTAT | 17 | `4b 13 11 00 │ fdata(u32)` | err@4, mode@8, size@0xc |

Two hard limits the code respects: **one READ caps at 1024 B** (loop until the full size is read — do
*not* break on a short read), and **only a few directories can be open at once** (`list_rec` collects
entries, CLOSEs the directory, *then* recurses).

### 5.2 The write path, and the four bugs it exposed

`put <local> <efs-path> [--force] [--oflag N]` writes a local file to an EFS path. Getting there
required reverse-engineering the WRITE request, because the original header *documented* `WRITE=5`
while the code defined `EFS_READ5 5` (an alternate READ opcode) — **there was no write path at all.**

| # | Symptom | Root cause | Fix |
| --: | :-- | :-- | :-- |
| 1 | No write capability | Sub-command 5 was named `EFS_READ5` and exposed as an alternate READ | Renamed the alternate to `EFS_READ_ALT`; defined `EFS_WRITE 5` |
| 2 | A 4-byte write landed at offset **4**; a 16-byte write at offset **20** | The modem read the field I sent as `nbytes` as the **file offset** | The layout has **no `nbytes` field**: `fdata │ offset │ data`, and the length **is the message length** |
| 3 | A fresh file still read back size 0 | The write is not committed until the handle is **CLOSEd** | (No code change — `cmd_put` already closes before verifying; documented) |
| 4 | Writing 4 bytes over a 16-byte file left 16 bytes | The open flag did not truncate | Default `EFS_OFLAG_WRITE = 0x241` (`O_WRONLY|O_CREAT|O_TRUNC`) |

Bug 2 was found by a raw experiment through `diag_logtool` (a WRITE of known bytes at a known handle),
which showed the modem treating the byte I sent as `nbytes` as the offset. Bug 4 was resolved by probing
the open flags directly:

**EFS open flags are the POSIX/Linux values** (measured):

| Flag | Value | Note |
| :-- | --: | :-- |
| `O_WRONLY` | 0x1 | |
| `O_RDWR` | 0x2 | |
| `O_CREAT` | 0x40 | |
| `O_TRUNC` | 0x200 | |
| `O_APPEND` | 0x400 | |
| — | 0x41 | create |
| — | 0x201 | truncate |
| — | 0x401 | append |
| — | **0x241** | `O_WRONLY\|O_CREAT\|O_TRUNC` — the `put` default |
| — | 0x240 | **err 9** (rejected) |

### 5.3 The safety design of `put`

1. **Refuse calibration paths** — `/rfnv/` and `/mcs/` are per-unit RF/TCXO calibration and are
   refused unless `--force` is given (`path_is_cal()`).
2. **Back up an existing target** — if the path exists, it is read out to
   `<sanitised-path>.bak.<epoch>` before the write; if the backup fails, the write is refused.
3. **Chunked write** — ≤1024 bytes per WRITE, advancing by the modem's reported `nwritten`.
4. **Close, then verify** — the handle is closed (committing), then the file is re-read and compared
   byte-for-byte; the tool prints `MATCH`/`MISMATCH` and exits non-zero on mismatch.

---

## 6. On-device validation

| Test | Result |
| :-- | :-- |
| `put` a new file, then read back | **MATCH** |
| Modify a real EFS file, read back | **MATCH** |
| Restore the original bytes, read back | **MATCH**; final file **byte-identical** to the original (`conf/hdrmac_config_info.conf`, md5 `50724a0a183e3190bf4c465549e3fa64`) |
| Write to a calibration path without `--force` | **refused** |
| Auto-bind service after a simulated SSR (manual unbind) | re-bound in ~6 s; status file + syslog line |
| Cleanup of the 13 test files | removed; the real EFS re-read and confirmed intact |

The chosen modify target was a **dead-technology** config (`hdrmac_config_info.conf` — HDR is not a
technology this stick uses), precisely so that even a failed restore could not affect a live radio path.

---

## 7. Packaging and build

`scripts/openwrt-prepare.sh:189-200` copies `packages/` to `openwrt/package/msm8916/`. The three
packages register in the feed index (`tmp/.packageinfo`, `tmp/.packagedeps`) as `diag-bind`,
`diag-efs`, `diag-logtool`, with `diag-efs` and `diag-logtool` each depending on `diag-bind/compile`.

**Verified end-to-end through the real OpenWrt build system** (with the three `CONFIG_PACKAGE_diag-*=m`
symbols enabled — see the trap below):

| Package | apk produced | Installed payload |
| :-- | :-- | :-- |
| `diag-bind` | `bin/packages/aarch64_generic/base/diag-bind-1.0-r1.apk` (3 809 B) | `/usr/sbin/diag-bind`, `/usr/sbin/diag-bind-watch`, `/etc/init.d/diag-bind` |
| `diag-efs` | `bin/packages/aarch64_generic/base/diag-efs-1.0-r1.apk` (8 115 B) | `/usr/bin/diag_efs` |
| `diag-logtool` | `bin/packages/aarch64_generic/base/diag-logtool-1.0-r1.apk` (6 524 B) | `/usr/bin/diag_logtool` |

Both C binaries are `ELF 64-bit LSB executable, ARM aarch64 … /lib/ld-musl-aarch64.so.1`. The same
sources also compile standalone with the in-tree target toolchain:

```
openwrt/staging_dir/toolchain-aarch64_generic_musl/bin/aarch64-openwrt-linux-musl-gcc \
    -static -Os -fno-stack-protector -o diag_efs diag_efs.c
```

`sh -n` passes on all three shell files.

> **Build note (a real trap).** `make package/<name>/compile` can report *"Nothing to be done for
> 'compile'"* for **two different reasons**, and neither is evidence that the package built:
> 1. the package is **not selected** in `.config` (`CONFIG_PACKAGE_<name>` absent — the case here, because
>    the package is pulled in through the image's `DEVICE_PACKAGES` rather than a hand-set symbol), in
>    which case the compile target genuinely has nothing to do; or
> 2. a stale `$(STAMP_BUILT)` exists while `$(PKG_BUILD_DIR)` is gone.
>
> To build a package standalone, enable it first (`CONFIG_PACKAGE_<name>=m` + `make defconfig`), then
> `make package/<name>/compile`. To rule out a stale stamp, `make package/<name>/{clean,compile}`.
> **Always confirm the artifact exists** (`build_dir/…/packages/.pkgdir/<name>/…`) before believing the
> build succeeded.

---

## 8. UNEXPECTED — EFS2 `REMOVE` is sub-command 8 (documented, not implemented)

While cleaning up the write-test files it was discovered, by probing, that **EFS2 `REMOVE` = sub-command
`8`** — i.e. `4b 13 08 00 │ path\0` removes an EFS file. It was used (via a raw `diag_logtool` exchange)
to delete the 13 test files.

It is **outside the approved scope** of this work (the agreed change was a *guarded write*, not a delete)
and it is **deliberately not implemented** in the shipped `diag_efs`. It is recorded here because it is a
non-obvious protocol fact that a future instrument may need, and because it is destructive: a `remove`
command in a shipped tool would be a footgun next to the `path_is_cal()` guard.

---

## 9. Open items

* `diag-bind`'s watcher polls at 2 s; a modem SSR therefore leaves `/dev/rpmsg0` absent for up to
  ~2 s. A capture that must not miss that window should re-open with a retry loop, or the interval can
  be lowered with `DIAG_BIND_INTERVAL`.
* `put` is validated on a config file and a throwaway file only; it has **not** been validated against
  a large (multi-chunk) file, nor against a calibration file (by design).
* The EFS2 `REMOVE=8` capability is documented but not exposed (§8).

---

*End of Doc 219.*

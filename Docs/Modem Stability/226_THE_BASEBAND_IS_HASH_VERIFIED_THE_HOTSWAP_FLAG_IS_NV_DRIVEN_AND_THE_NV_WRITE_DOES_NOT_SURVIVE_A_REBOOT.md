# Doc 226 — The baseband is hash-verified, the hotswap flag is NV-driven (not hardcoded), and an EFS write does not survive an AP reboot

**Ledger item 32.** Supersedes the applicable parts of
`225_..._UIMSUB_MANAGER_HOTSWAP_LATCH.md` §7 (see §4 below — its "there is no
NV-driven writer" claim is **WRONG**).

**Session date:** 2026-09-28.

---

## 0. SOP compliance statement

Per `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`:

| SOP step | Status |
|---|---|
| Read the function **definition**, not its name (Doc 202's rule) | **DONE** — the decompiled `FUN_c055f1c0` was read in full, which is what exposed the NV overwrite the Doc 225 summary had missed |
| Verify VAs / offsets against the **deployed** image, not the scratch copy | **DONE** — §2.4. The device's `modem.b16` is a **different build** from `scratch/uz801_fw/image/modem.b16` (324 830 differing bytes), so a minimal ELF was built around the deployed segment and re-disassembled |
| Hash the firmware before attributing any result to it | **DONE** — §2.1/§2.4; the deployed `modem.b16` md5 is `f6f9900e…` and the patched one `5cde1556…` |
| Never blind-patch a baseband | **DONE** — §2.2 states the exact instruction and its in-image imm=1 twin before any write |
| Assert a control **took** (measurement discipline) | **DONE** — §3.3. The `L711` counter is the control, and it is what turned a would-be "falsified" into **inconclusive** |
| Announce irreversible / high-blast-radius steps | **DONE** — Doc 225 §8 stated the blast radius before acting; a full firmware backup was taken first (§2.3) |
| Update the ledger in the same session | **DONE** — item 32 |

---

## 1. Achieved vs Expected

| # | Expected | Achieved | Verdict |
|---|---|---|---|
| 1 | Apply the Doc 225 §7.4 pre-registered falsifier (patch the hardcoded hotswap flag to 1, deploy, read `is_gwl_subs_avail`) | Ran it end to end. Result **`0->0`, unchanged** — but the **control did not take** (`L711` still 2×/boot), so the test is **inconclusive**, not a falsification | **PARTIAL** |
| 2 | Learn why the first deploy failed to boot | **MET, and it is the session's most reusable result:** the MPSS image is **SHA-256 hash-verified**; a one-byte edit makes the modem refuse to load (`MPSS authentication failed: -19`). The fix is a 3-file patch (§2) | **MET** |
| 3 | Determine whether the hardcoded flag is the real lever | **MET — it is NOT.** The same function **overwrites the whole 256-byte struct from the NV item `uim_hw_config`** at its end, discarding the hardcoded default. Doc 225 §7's "no NV-driven writer" is **WRONG** | **MET (correction)** |
| 4 | Test the NV lever instead | Wrote NV byte 7 = 1 (read-back MATCH) → still `0->0`/`+CFUN: 7`, and after a reboot the byte is **back to 0**. The write does **not persist** | **NEGATIVE** |
| 5 | Answer whether the `diag_efs` CLI needs updating | **MET — YES, and it is done.** The Doc 224 skip-fix existed only in a side file; it is now folded into the package source and rebuilt (§7) | **MET** |

---

## 2. The baseband is hash-verified — a one-byte edit does not boot

### 2.1 The failure

Deploying only a patched `modem.b16` produced, on the very next boot:

```
[   11.304048] remoteproc remoteproc0: Booting fw image mba.mbn, size 234176
[   11.349439] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
[   11.781222] qcom-q6v5-mss 4080000.remoteproc: MPSS authentication failed: -19
[   11.781906] remoteproc remoteproc0: can't start rproc 4080000.remoteproc: -19
[   11.787774] remoteproc remoteproc0: Boot failed: -19
```

`remoteproc0` stays `offline`, the DIAG device never appears, and `diagboot`
sits in its wait loop. **The failure is a hash mismatch, not a code fault.**

### 2.2 The fix — three files, not one

`modem.b01` is the MBN **hash segment**:

```
[0x000..0x027]  40 bytes    hash-segment header
[0x028..]       N*32 bytes  SHA-256 table, N = e_phnum
modem.mdt == modem.b00 + modem.b01   (byte-for-byte)
```

So a patch to segment *S* must:

1. write the new bytes into `modem.bS` (here `modem.b16`);
2. write `sha256(modem.b16)` into `modem.b01[0x028 + 16*32]` = `modem.b01[0x228]`;
3. rebuild `modem.mdt = modem.b00 + modem.b01`.

The shipped tool does exactly this and re-verifies:

```
$ python3 GitIgnore/compare/ufi001b_hash_tool.py verify scratch/device_fw/image
  Results: 19 MATCH  8 ZERO/BSS  0 MISSING  0 MISMATCH
  Overall: PASS ✓

$ python3 GitIgnore/compare/ufi001b_hash_tool.py patch scratch/device_fw/patched/image 16 <patched b16>
  Old hash:    c3d74d0c893f5b23d222d0103cb8d09277e385c6e4cd5a15c13002cca304d50a
  New hash:    448553f62862370b90d773fe01c6f7a43fc301d463846c2752fa359e1d79c78f
  b01 offset:  0x0228
  mdt offset:  0x05bc
  Verification: PASS ✓
```

After deploying **all three** files the modem boots normally
(`MBA booted … loading mpss` → `remoteproc0: … is now up`, no auth failure).
Deployed deltas vs the backup: `modem.b16` **1 byte**, `modem.b01` **32 bytes**,
`modem.mdt` **32 bytes** — exactly as predicted.

### 2.3 Backup first (there is no `modem-guard` on this device any more)

`/overlay/fwbackup/` and the `modem-guard` S01/K01 service described in Doc 197
are **not present** on the device as of this session, so the auto-revert safety
net is gone. A full manual backup was taken to
`/root/fwbackup_20260928/` (`modem.*` + `mba.mbn`), and the whole set was also
pulled to `scratch/device_fw/image/` for local verification.

### 2.4 The deployed build is NOT the scratch build — re-derive every offset

| | `scratch/uz801_fw/image/modem.b16` | deployed `/lib/firmware/modem.b16` |
|---|---|---|
| md5 | `848abe7e…` | `f6f9900e…` |
| size | 19 443 952 | 19 443 952 |
| differing bytes | — | **324 830** (1.7 %), first at `0x28cc8` |

`modem.mdt` differs too (2 134 bytes). This is the Doc 221 B08/`-11` vs
B10/`-21` split. **The phdr table is identical in layout**, so the VA→file
mapping still holds (`VA 0xc055f380 → modem.b16 + 0x2d8380`), and the site was
re-confirmed by building a minimal ELF around the **deployed** segment and
disassembling it:

```
$ llvm-objdump -d --triple=hexagon --start-address=0xc055f370 dev16.elf
c055f374:  r25 = add(r18,r16)
c055f380:  memb(r25+#0x7) = #0x0      <-- the hardcoded hotswap flag
c055f384:  memb(r25+#0x8) = #0x1
```

---

## 3. The falsifier run

### 3.1 The patch

Doc 225 §7.1's store is one instruction. Its **in-image imm=1 twin already
exists**, so the encoding is proven by the tool itself rather than inferred:

```
(r25,#0x7)=#0x0  ->  3c194380     (this site, VA 0xc055f380)
(r25,#0x7)=#0x1  ->  3c194381     (already present elsewhere, e.g. VA 0xc0c66cf4)
```

⇒ patch **byte 0 of the word**: `0x80 → 0x81`. Re-disassembling the patched
segment gives `memb(r25+#0x7) = #0x1`. The sibling `memb(r25+#0x8) = #0x1` is
untouched.

`r18 = 0xc34b1204` (from `r19:18 = combine(#0x0, ##0xc34b1204)`), so
`r25+#0x7 = DAT_c34b120b` and `r25+#0x8 = DAT_c34b120c`, matching the decompile
exactly.

### 3.2 The result

Boot capture (`/root/diag_boot.bin`, 1 521 879 B, md5 `ab647763…`), parsed with
`f3parse.py` (md5 `9f9d6edf…`):

```
=CMREGPRX= ph_stat_chgd: as_id=%d, is_gwl_subs_avail %d->%d
args: [0, 0, 0]          <-- as_id=0, is_gwl_subs_avail 0->0
```

and `AT+CFUN?` → `+CFUN: 7`. **Unchanged from baseline.**

### 3.3 The control that saved the conclusion

`uimsub_manager.c` **L711** (`"sub_mag Setting card status as unknown as hotswap is disabled"`)
is emitted **exactly twice per boot** in the baseline. In this run it is **still
exactly 2**. The consumer is

```c
/* FUN_c055c68c, L711 */
if ((&DAT_c34b15bc)[(uint)*pcVar2 * 0x14] == 0) {
    FUN_c0287020(&DAT_c161b940);   /* L711 */
    *pcVar7 = '\x03';              /* card status := 3 (UNKNOWN) */
}
```

so the branch still sees the flag as **0**. **The patch had no runtime effect**,
and the run therefore does **not** falsify Doc 225 §7.4's chain — it is
**inconclusive**. Reporting it as a falsification would have been the exact
error the project's measurement discipline exists to prevent.

---

## 4. WHY it had no effect — the flag is NV-driven (Doc 225 §7 is WRONG here)

Doc 225 §7.1 asserted: *"`DAT_c34b120b` has exactly two references in the entire
image … There is no NV-driven writer."* **That is wrong.** Reading the whole
`FUN_c055f1c0` shows the tail:

```c
DAT_c34b1287 = 1;
iVar2 = FUN_c09446b0(s__nv_item_files_modem_uim_uimdrv__c18f13fe,
                     &DAT_c34b1204, 0x100, 0x80242, 0x1ff);
```

Resolving the literal (VA `0xc18f13fe`) gives:

```
/nv/item_files/modem/uim/uimdrv/uim_hw_config
```

i.e. the 256-byte struct that the loop just populated with defaults is
**overwritten by the NV item of the same size**. Order in the function:

1. fill `uim_hw_config` with **defaults** — `[+7] = 0` (hotswap flag), `[+8] = 1`, `[+0] = 3`, …;
2. **read `/nv/item_files/modem/uim/uimdrv/uim_hw_config` over the whole 256 bytes.**

So the "hardcoded" flag is only a fallback; the live value comes from NV.
Byte ↔ field mapping (struct base `DAT_c34b1204`, stride 0x1d):

| NV byte | symbol | meaning |
|---|---|---|
| 0 | `DAT_c34b1204` | (default 3) |
| **7** | **`DAT_c34b120b`** | **the hotswap flag** — copied by `FUN_c055f8f8` into `DAT_c34b15bc`, tested at L711 |
| 8 | `DAT_c34b120c` | adjacent field (default 1) |

Corroboration: the **live** NV item's byte 0 is `0x03`, matching the in-code
default — the two are the same object. And byte 8 is **0** in NV while the
default is 1, which proves the NV read **overwrites** the defaults (a
defaults-written-to-NV reading would have byte 8 = 1).

**This also re-classifies the §3.3 control:** L711 = 2 is consistent both with
"the NV read overwrote my patch" and with "`FUN_c055f1c0` never ran", so the
control alone does not separate them. The NV-read evidence above does.

---

## 5. The NV lever — written, verified in-boot, and NOT persistent

With `diagboot` disabled (see §6) the EFS instrument works:

```
$ diag_efs detect
efs method: 0x13
$ diag_efs stat /nv/item_files/modem/uim/uimdrv/uim_hw_config
/nv/item_files/modem/uim/uimdrv/uim_hw_config mode=000081ff size=256
$ diag_efs read … /tmp/uim_hw_config.bin          # 256/256 bytes
00000000  03 3d 00 01 00 00 00 00  00 59 00 00 57 00 00 3b
                   ^^ byte 7 = 0  ^^ byte 8 = 0
```

Byte 7 was set to 1 and written with the tool's guarded `put`:

```
… exists (256 bytes); backing up -> _nv_item_files_modem_uim_uimdrv_uim_hw_config.bak.1782761593
… wrote 256 bytes; read-back 256 bytes -> MATCH
```

In-boot read-back: byte 7 = **1**. Then `diagboot` was re-enabled, the AP was
rebooted, and the capture was taken:

* `is_gwl_subs_avail` — still `0->0`; `AT+CFUN?` — still `7`;
* the `L711` control — **still 2**;
* and the NV item, re-read after the reboot, is **byte-for-byte identical to the
  original**: byte 7 = **0** again.

**The EFS write does not survive an AP reboot.**

The write path itself is not the problem: `rmtfs` runs `-P -s`, and `-P` means
"find and use the raw EFS partitions" (`storage.c`:
`{ "/boot/modem_fs1", "modem_fs1", "modemst1" }`, `{ …, "modemst2" }`,
`BY_PARTLABEL_PATH "/dev/disk/by-partlabel"`), and `storage_pwrite()` is a plain
`pwrite()` to the partition fd — no caching layer. So the bytes did reach a
partition; the modem appears to restore/ignore them on the next boot (consistent
with the `modemst1`/`modemst2` two-copy alternation, or with an EFS transaction
that only commits on a clean modem shutdown — and an AP reboot is not one).

**Consequence:** an NV-based lever needs a way to make the modem commit, or both
copies written, or a modem-side restart that is not an abrupt AP reboot. This is
the open question this doc leaves behind.

---

## 6. NEW OPERATIONAL FACT — the F3 and EFS instruments are mutually exclusive

`diagboot` arms the F3 boot log with
`diag_logtool cntl-enable <CNTL> 64`. With that armed, **every** EFS2 request
goes unanswered on the same `/dev/rpmsg0`:

| boot | `diagboot` | `diag_efs detect` | `stat …/uim_hw_config` |
|---|---|---|---|
| A | **enabled** (cntl-enable run) | `efs method: none` | (no reply; 26–167 messages skipped) |
| B | **disabled** | **`efs method: 0x13`** | **`mode=000081ff size=256`** |

The tool is not at fault — it *does* receive traffic (it skips 26–167
non-matching messages), it simply never receives the EFS2 reply. This is
consistent with, and sharpens, Doc 224's `cntl-disable ⇒ UNRESPONSIVE` finding:
the channel's log-enable state and its request/response state are not
independent.

**Practical rule:** capture the F3 boot log *or* read/write EFS, never both in
the same boot. An NV experiment therefore needs the two-phase sequence
`diagboot off → write NV → diagboot on → reboot → read F3`.

---

## 7. `diag_efs` CLI — yes, it needed updating, and it is done

Doc 224's fix (do not trust one `read()` on a channel that also carries a
continuous spontaneous F3 stream; loop until the reply whose byte 2 echoes the
request's sub-command arrives) existed **only** in a side file,
`scratch/diag_efs_skip.c`, compiled into a separate binary `/root/diag_efs_skip`.
The canonical package source still carried the broken single-read `efs_txn()`:

| file | before | after |
|---|---|---|
| `packages/diag-efs/src/diag_efs.c` | md5 `56e38545…` — single `read()` | **fix folded in** |
| `openwrt/package/msm8916/diag-efs/src/diag_efs.c` | md5 `56e38545…` | synced by `./build.sh package diag-efs` |
| `scratch/diag_efs_skip.c` | md5 `079ef0d1…` | unchanged (now redundant) |

Changes: the skip-loop (wall-clock budget, ≤250 ms slices, keep only
`resp[0]==0x4b && resp[1]==req[1] && resp[2]==req[2]`), the `EFS_DUMP` /
`EFS_DEBUG` diagnostics, and a NOTE recording §6's incompatibility.

Built and deployed:

```
$ ./build.sh package diag-efs      # 53 s; the build re-syncs the live tree
$ ./build.sh guard
==> In sync: msm89xx/ and packages/ match the live OpenWrt tree.
```

Installed on the device as `/root/diag_efs` **and** `/usr/bin/diag_efs`
(md5 `1c82d1a3…`); the previous broken binary is kept as
`/root/diag_efs.old_broken` (md5 `d2942bd0…`). Note the package had never
actually been installed on the device — `/usr/bin/diag_efs` did not exist — and
neither had the `diag-bind` service it depends on.

---

## 8. Artifacts

| artifact | what it is |
|---|---|
| `scratch/device_fw/image/` | the **deployed** firmware set (backup of `/root/fwbackup_20260928/`), hash-verified PASS |
| `scratch/device_fw/patched/image/` | the same set with the one-byte patch + re-hashed `b01`/`mdt`, hash-verified PASS |
| `scratch/device_fw/modem_b16_patched.b16` | the patched `b16` (md5 `5cde1556…`) |
| `/tmp/dev16.elf`, `/tmp/dev16_patched.elf` | minimal single-PT_LOAD ELFs used to disassemble the **deployed** segment at the patch VA |
| `scratch/p225_falsifier_boot.bin` | the boot capture for the patched run (1 521 879 B, md5 `ab647763…`) |
| `scratch/uim_hw_config.orig.bin` | the live NV item as found (256 B, md5 `7fa96962…`) |
| `scratch/uim_hw_config.byte7is1.bin` | the modified item (md5 `d48e42ee…`) |
| `GitIgnore/compare/ufi001b_hash_tool.py` | **the hash verifier/patcher** — `verify` / `patch <dir> <seg> <file>` / `info` |
| `GitIgnore/compare/build_hmu05_stock_patched.py` | the same 3-file recipe for the HMU05 stock image (the lineage of §2.2) |
| `packages/diag-efs/src/diag_efs.c` | **updated** — carries the Doc 224 skip fix (§7) |
| `scratch/diagboot_run.sh.current`, `scratch/uz801_diagboot.init` | the F3 boot-capture instrument, installed on the device this session |

---

## 9. Status

* **New, proven:**
  * the MPSS image is **hash-verified**; a baseband patch requires the
    **3-file** update (§2.2). One-byte edits alone give
    `MPSS authentication failed: -19` and no boot.
  * the deployed image is a **different build** from the scratch copy (§2.4) —
    re-confirm every site against the deployed bytes.
  * the Doc 225 §7 "hardcoded hotswap flag" is **NV-driven**: `FUN_c055f1c0`
    overwrites the whole 256-byte struct from
    `/nv/item_files/modem/uim/uimdrv/uim_hw_config` (§4).
  * an EFS write **does not survive an AP reboot** (§5).
  * the F3-capture and EFS instruments are **mutually exclusive** on
    `/dev/rpmsg0` (§6).
  * `diag_efs` package source updated, rebuilt, redeployed (§7).
* **Unchanged:** `+CFUN: 7`, `is_gwl_subs_avail 0->0`. The Doc 225 §7.4 chain is
  **not falsified and not confirmed — untested**, because the control did not
  take.
* **Open:**
  1. how to make a modem-EFS write **commit** (or write both `modemst1` and
     `modemst2`) so an NV lever is testable at all;
  2. whether card-status-UNKNOWN actually drives `is_gwl_subs_avail` (the two
     unproven links of Doc 225 §7.4) — now only testable via NV, given §4;
  3. the `cmregprx.c` `+30…+49` line delta (Doc 224 §5).
* **Device state at end of session:** original firmware restored
  (`modem.b16` md5 `f6f9900e…`, `cmp` = 0 against the backup), modem boots
  cleanly, `+CFUN: 7`, `diagboot` **disabled** (so the EFS instrument is
  available), NV item unchanged (byte 7 = 0), backup intact at
  `/root/fwbackup_20260928/`.

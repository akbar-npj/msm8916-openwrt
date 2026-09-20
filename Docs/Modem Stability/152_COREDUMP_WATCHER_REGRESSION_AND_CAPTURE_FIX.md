# 152 — The modem-coredump watcher was silently broken (fatals #11–#12 lost); the corrected capture procedure; coredump analysis status

**Date:** 2026-09-21
**Device:** Melbon HMU05 (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94,
`hmu05,250605v0s`, OpenWrt `r33051-f5d5ece4`.
**Baseband:** stock HMU05 modem firmware (byte-identical to the Android build). Nothing was
flashed, patched, or rebuilt in this session.
**Corrects:** the capture procedure recorded in `reference_modem_coredump_capture.md` and in
Doc 146 §7 (the release step there is wrong), and the carried-forward note that "pstore stays
empty" on the HMU05.
**Answers:** why no coredump was produced for fatals #11 and #12.

---

## 1. Why this document exists

The 4 coredumps in `scratch/coredump_live/` (fatals #1–#4 of the 2026-09-20 boot) are the only
ground-truth view of the modem's own BSS at the fault, and they are still unanalysed. The plan
for this session was to capture a fresh dump and analyse them. Instead, fatal #11 fired and
**no dump appeared** — even though the coredump was armed and the watcher was running. Fatal
#12 fired the same way.

The watcher was not merely unlucky. It was **structurally incapable of capturing anything**,
for two independent reasons, both of which are visible in the 6.12.94 kernel source. This
document records the root cause, the corrected procedure (verified against the source), and
where the coredump analysis stands.

## 2. SOP compliance statement

* **No firmware, DT, module, or userspace file was modified on the device.** The only
  device-side writes were `/tmp/rpmring` (the existing RPM instrument), `/tmp/coredump_watch.sh`
  (the corrected watcher), `/overlay/coredump_watch.log`, and
  `/sys/class/remoteproc/remoteproc0/coredump = enabled`.
* **Every claim about the capture mechanism is read from the running kernel's own source**
  (`drivers/base/devcoredump.c`, `drivers/remoteproc/remoteproc_coredump.c`,
  `drivers/remoteproc/remoteproc_core.c`, `fs/sysfs/file.c`, `fs/kernfs/`), not from the
  corpus. Line numbers below are from the built 6.12.94 tree.
* **The single negative that is source-supported but not directly measured is flagged as such**
  (§3.1). A direct measurement becomes possible at the next fatal.
* **Ground truth for the coredump contents is read from the dumps themselves**, using the
  address mapping that Doc 148's evidence established and this session re-verified (§6).

## 3. The regression — two independent defects

The watcher that was deployed (`/tmp/coredump_watch.sh`, identical to the copy committed under
`evidence/151_f10_and_spm/`) polls `/sys/class/devcoredump/devcd*/` every 2 s and contains:

```sh
[ -s "$d/data" ] || continue          # <-- DEFECT 1: always false
...
echo 1 > "$d/disabled" 2>/dev/null    # <-- DEFECT 2: path does not exist
```

### 3.1 Defect 1 — `[ -s "$d/data" ]` is always false

`data` is registered as a **binary attribute with `.size = 0`**:

```c
/* drivers/base/devcoredump.c:144 */
static struct bin_attribute devcd_attr_data = {
	.attr = { .name = "data", .mode = S_IRUSR | S_IWUSR, },
	.size = 0,
	.read = devcd_data_read,
	.write = devcd_data_write,
};
```

`sysfs_add_bin_file_mode_ns()` passes that size straight through to kernfs
(`fs/sysfs/file.c:343`: `__kernfs_create_file(..., battr->size, ...)`), so the inode size is
**0** and `test -s` is false **even when a complete dump is present**. The guard therefore
skips every dump.

*Source-supported, not directly measured:* no devcd device existed at the moment of the check
(no dump was present), so `-s` could not be exercised on a live device. The corrected watcher
removes the question entirely — it guards on the **device directory existing**, never on size.

### 3.2 Defect 2 — the release path does not exist

The per-device attribute set is **only `data`**:

```c
/* drivers/base/devcoredump.c:151 */
static struct bin_attribute *devcd_dev_bin_attrs[] = { &devcd_attr_data, NULL, };
```

`disabled` is a **class-level** attribute (`CLASS_ATTR_RW(disabled)`, line 233), i.e.
`/sys/class/devcoredump/disabled` — **not** `/sys/class/devcoredump/devcdN/disabled`. And it is
a **global, write-once lockdown**:

```c
/* drivers/base/devcoredump.c:214 — disabled_store() */
	/* This essentially makes the attribute write-once, since you can't
	 * go back to not having it disabled. This is intentional, it serves
	 * as a system lockdown feature. */
	if (tmp != 1) return -EINVAL;
	devcd_disabled = true;                       /* never cleared until reboot */
```

So the watcher's release silently did nothing (redirected to `/dev/null`). It is **not**
harmless to "fix" it by writing the class-level `disabled`: doing so sets `devcd_disabled` and
**kills every future dump until reboot**. The correct per-device release is to **write to
`data`**, which calls `devcd_data_write()`:

```c
/* drivers/base/devcoredump.c:127 */
static ssize_t devcd_data_write(...)
{
	if (cancel_delayed_work(&devcd->del_wk))
		schedule_delayed_work(&devcd->del_wk, 0);   /* delete now */
	return count;
}
```

### 3.3 The failure was silent, and it is *why* there is no dump

Two further silent-failure points are worth recording, because either can cost a dump with no
kernel message at all:

* `dev_coredumpm_timeout()` **discards a new coredump if one already exists for the same
  failing device** (line 376: `existing = class_find_device(...); if (existing) goto free;`).
  With Defect 2 unreleased, a dump lives the full 5-minute timeout; a second fatal inside that
  window would be dropped.
* `rproc_coredump()` allocates the whole dump with **`vmalloc(~81 MB)`** and returns silently
  if it fails (line 270: `data = vmalloc(data_size); if (!data) return;`). Memory was plentiful
  at the time (222 MB free, 9.3 MB vmalloc in use), so this is not the cause here — but it is a
  silent path to keep in mind.

### 3.4 The deployed watcher is a regression against the one that worked

A host copy of the previous session's capture log survived at `/tmp/coredump_capture.log`
(29 664 B). It shows the working watcher captured the **same** `devcd1` device **~34 times,
every ~9 s**, until the 5-minute timeout, then `capture FAILED`, then the next fatal's `devcd2`
— exactly the "live dump, re-read repeatedly" behaviour the Doc 148 evidence already warned
about ("122 dumps = 9.6 GB in ~45 min"). That watcher therefore had **no `-s` guard** (it
captured) and **no working release** (it re-captured). The version deployed for fatal #11 added
the `-s` guard and lost the ability to capture at all.

**The log is the artefact that proves the mechanism, and it also proves the fix is not
hypothetical: the same sysfs device is capturable.**

## 4. The corrected watcher (deployed and armed)

`scratch/coredump_watch.sh` (tracked copy: `evidence/152_coredump_capture/coredump_watch.sh`)
now:

* guards on `[ -d "$d" ]`, **never** on `-s`;
* **dedupes by device name** (`devcdN`), so a re-read cannot duplicate 85 MB;
* **releases by writing to `data`**, not to a nonexistent `disabled`;
* logs every action to `/overlay/coredump_watch.log` (persistent, unlike the old `/tmp` log);
* never touches the class-level `disabled`.

It is deployed at `/tmp/coredump_watch.sh` and running **detached** (`setsid`, so it survives
the launching ssh — verified: `ppid=1`, its own session id). It was armed at AP 2340.71 s.

## 5. What was lost, and a new period data point

Both lost fatals were the deterministic idle timer, and they are the first two of the current
boot:

| # | AP uptime (s) | Δ from previous | signature |
| :-- | :-- | :-- | :-- |
| 11 | 914.543091 | — (new boot) | `lte_ml1_common_timer.c:390` |
| 12 | 1818.217607 | **903.674516** | `lte_ml1_common_timer.c:390` |

Δ(#11→#12) = **903.674516 s** is a fresh, clean sample of the deterministic AP-observed period,
0.0004 s from the family mean — another confirmation that the period is real and stable, and
another `n` for the ~902.27 s modem-uptime figure after subtracting the ~1.407 s SSR downtime.
The coredumps for both are gone (the 5-minute window expired before the defect was found).

## 6. Coredump analysis status (the 4 dumps on hand)

The tooling works and the address mapping is confirmed.

* **`dump_va = elf_va − 0x39800000`** (Doc 148 evidence, re-verified here on segment bases and
  on the string pool). Tools written this session: `scratch/coredump_live/vadump.py` (dump a
  region by ELF VA) and `scratch/coredump_live/diff_dumps.py` (segment-wise diff of two dumps).
* **The ERR_FATAL descriptor is confirmed exactly as Doc 148 recorded it.** In
  `modem_coredump_up919.52.elf`, `coredump_descriptors.py` finds `lte_ml1_common_timer.c` at
  dump-VA `0x89db12a4` with the u16 line `0x0186` = **390** at `0x89db1290`.
* **It is a structured record, not a bare descriptor.** ELF VA `0xC35B1280` holds:

  ```
  +0x00 03 00 00 00                  u32 = 3
  +0x04 01 00 00 00                  u32 = 1
  +0x08 84 bf c0 c3                  u32 = 0xC3C0BF84   (pointer into ELF seg 0xC3C09000)
  +0x0c 03 00 00 00                  u32 = 3
  +0x10 86 01 00 00                  u16 line = 390   <-- r18 = 0xC35B1290
  +0x14 <A>                          runtime-varying word
  +0x18 <B>                          runtime-varying word
  +0x1c 00 00 00 00
  +0x20 00 00 00 00
  +0x24 "lte_ml1_common_timer.c\0"
  +0x46 "Assert 0 failed: \0"        the ERR_FATAL message template
  ```

* **A and B across the four fatals** (ELF `0xC35B1294` / `0xC35B1298`):

  | dump (AP uptime s) | A | B |
  | :-- | :-- | :-- |
  | 919.52 | 0xb7bb76cf | 0x01128bfc |
  | 1822.52 | 0xbffa3d53 | 0x01128c07 |
  | 2723.69 | 0xc778be62 | 0x01128c12 |
  | 3629.79 | 0xce3f7ed6 | 0x01128c1d |

  B increases by exactly **0x0b = 11 per fatal period** (one count per ~82.15 s). A increases
  by a *decreasing* amount (~0x083EC684, 0x077E810F, 0x06C6C074). If A:B is read as one 64-bit
  value the increment is ~47.3–47.4 × 10⁹ per period (~52.4 MHz) but the rate drifts by ~0.02 %,
  which is far more than the 1 ppm the period is stable to — so a plain fixed-rate counter does
  not fit. **Unresolved; it remains the descriptor's only unexplained lead.**
* **A full diff** of fatal #1 vs fatal #2 shows **4 396 967 differing bytes (5.15 %)** — the
  modem's memory evolves substantially per period, so the diff is only useful once anchored to
  named objects. The heaviest segments are `0x8ace6000` (2.85 M), `0x88870000` (0.94 M) and
  `0x8872c000` (0.22 M).

### 6.1 Following the RE's own pointer chain yields a live AP-relevant counter

The LPR RE (`Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` §3) records that
`FUN_c08bd220(name)` walks a **9-entry `{char *name; void *descriptor;}` table at `0xc1d464b0`**.
That table is intact in the dump:

| idx | name ptr | name | descriptor |
| :-- | :-- | :-- | :-- |
| 2 | `0xc1854ff5` | `"cpu_vdd"` | `0xc1d46f18` |
| 6 | `0xc1848058` | `"rpm"` | `0xc1d473f8` |

The `rpm` LPR descriptor at `0xc1d473f8` is self-consistent (its first word *is* the `"rpm"`
name pointer) and holds a **live, varying field at `+0x18` = `0xc1d47410`** — the offset the
memory record places **`q6pcvote`** at:

| dump (AP uptime s) | `rpm LPR +0x18` |
| :-- | :-- |
| 919.52 | **833** (0x341) |
| 1822.52 | **989** (0x3dd) |
| 2723.69 | **937** (0x3a9) |
| 3629.79 | **1009** (0x3f1) |

All four sit around **1000**, and the next six words are literal **1000**s (`0xe8 0x03`) — a
per-client maximum table, consistent with the RE's "Q6 PC vote" framing. **The value does not
monotonically increase across fatals** (833 → 989 → 937 → 1009), i.e. at ~903 s of modem uptime
the vote total varies by ~21 %. This is the **first live AP-relevant quantity read out of the
dumps**. Whether it is the quantity the `sleep count not incrmnt` check tests is **not
established** — that check is on a *delta*, and a dump gives only one sample.

Two negatives worth recording, because they close off the "obvious" addresses:

* The RE's `r25` base `0xc30fd9a8` (called the "q6pcvote table" there) is **all zeros** in all
  four dumps — the base in the RE is not where the live data sits.
* `DAT_c3c68290` (the descriptor passed to `FUN_c0879150`) is a **static high-entropy blob**,
  byte-identical across fatals — obfuscated rodata, not runtime state.

## 7. Established vs not established

**Established (measured / source-verified):**
1. The deployed watcher could not capture: `data` has `.size = 0` (so `-s` is false) and
   `devcdN/disabled` does not exist (§3.1, §3.2).
2. The per-device release is a write to `data`; the class-level `disabled` is a global,
   write-once lockdown that must never be written (§3.2).
3. A devcd device is capturable — the previous session's log shows ~34 successful reads of one
   device (§3.4).
4. `dump_va = elf_va − 0x39800000`; the ERR_FATAL descriptor and its `Assert 0 failed:` template
   are confirmed in the dumps (§6).
5. Δ(#11→#12) = 903.674516 s — a fresh period sample (§5).
6. The RE's LPR pointer chain resolves in the dump: the `rpm` LPR descriptor is at `0xc1d473f8`
   and its `+0x18` field — `q6pcvote` per the memory record — is a **live counter**
   (833 / 989 / 937 / 1009 at the four fatals), beside a per-client table of literal 1000s
   (§6.1).

**Not established:**
* That the `-s` guard is the *sole* reason #11/#12 were lost — it is the best-supported cause
  and the corrected watcher removes it, but the direct measurement is pending (next fatal).
* The meaning of A and B.
* Anything AP-side from the coredumps: the dumps contain the **modem's** private memory, not
  SMEM, so the SMSM APPS word and the A2 client vote list (which live in SMEM) are **not** in
  them. The `a2_power` state block is in modem BSS and *is* present, but its ELF VA has not yet
  been established.

## 8. Next experiments, in priority order

1. **Confirm the corrected watcher at fatal #13** (armed; expected ~AP 2722 s) — the dump is
   the direct test of §3.1.
2. **Pull the dump and analyse it** (priorities: the `a2_power` state block; the DRX/sleep
   counters near `FUN_c0ce7fe0`; the descriptor's A/B).
3. **Turn the `rpm LPR +0x18` counter (§6.1) into a delta.** The `sleep count not incrmnt`
   check is on a change, and one dump is one sample. Two options: (a) find the *previous* value
   the same field held (the LPR may keep a shadow), or (b) read `0xc1d47410` **live** from the
   AP with `devmem` at ~1 Hz and watch it across a fatal — this is the same class of
   measurement as `rpmring` and does not require a crash to start.
4. **Establish the `a2_power` state block's ELF VA** so it can be read directly from the dumps.
5. **Disassemble the `0xC1500000` segment** — where `lte_ml1_common_timer.c`'s own code/rodata
   live; `modem.asm` stops at `0xc1404e0c`, so the assert site is still not disassembled
   (Doc 148 §"Where the assert site actually is"). This is the standing blocker on naming the
   assert.
6. Bound coredump storage: keep one dump per fatal and prune (`/overlay` is 3.2 GB).

## 9. Artifacts

| what | where |
| :-- | :-- |
| corrected watcher | `scratch/coredump_watch.sh`, `evidence/152_coredump_capture/coredump_watch.sh` |
| watcher log (device) | `/overlay/coredump_watch.log` |
| previous session's capture log (proves the mechanism) | `/tmp/coredump_capture.log` |
| coredumps (4, fatals #1–#4) | `scratch/coredump_live/modem_coredump_up{919.52,1822.52,2723.69,3629.79}.elf` |
| VA dumper / diff tools | `scratch/coredump_live/vadump.py`, `scratch/coredump_live/diff_dumps.py` |
| descriptor scanner (tracked) | `evidence/148_fatal_periodicity/coredump_descriptors.py` |
| descriptor decode (tracked) | `evidence/148_fatal_periodicity/errfatal_descriptor_decode.txt` |

## 10. One line

The modem-coredump watcher deployed for fatal #11 **could never capture** — it guarded on
`test -s` of a `data` attribute whose size is 0, and released by writing a `disabled` file that
does not exist (the real `disabled` is a global write-once lockdown) — so fatals #11 and #12
(Δ = 903.674516 s, another clean period sample) were lost; the corrected watcher is deployed
detached and armed, the address mapping and the ERR_FATAL record are re-confirmed in the four
dumps on hand, **following the RE's own LPR pointer chain reaches a live counter at the
`rpm` LPR `+0x18` (`q6pcvote`) that reads 833 / 989 / 937 / 1009 at the four fatals** — and the
descriptor's word B (11 per period, ~82.15 s per count) is still the one unexplained lead.

# 153 — FATALS #14 AND #15 CAPTURED: THE WATCHER FIX IS CONFIRMED; THE COREDUMP IS CREATED ONLY *AFTER* `rproc_stop`; AND `/dev/mem` CANNOT READ THE MODEM AT ALL

**Date:** 2026-09-21 (host clock). All times below are **AP `CLOCK_MONOTONIC` / `/proc/uptime` seconds**,
because the device RTC is not trustworthy (see `reference_hmu05_device_quirks`). The device clock
read `Sep 20 21:15` while the host read `Sep 21 03:01` for the same instant.

**Status:** instrumentation/observation only. **No firmware, DT, module or userspace file on the
device was modified.** The only device-side writes this session were: (a) `/overlay/coredump_watch.sh`
(the watcher, already deployed in Doc 152), (b) `/overlay/rc.local.orig` (a backup), and
(c) `/etc/rc.local` (the autostart hook, §6). `/overlay/rpmring` was re-pushed.

---

## 1. Why this doc exists

Doc 152 fixed the coredump watcher on a **source-verified** argument and predicted, but could not
yet measure, that the fix would work. It also recorded a **next step that is now known to be
impossible** (§5) and explained the missing fatal-#13 dump with a **wrong mechanism** (§4).

This doc closes all three, and adds a fourth result:

1. **The corrected watcher captured the next two fatals with no intervention** — #14 and #15, each
   85 398 475 B, md5 verified end-to-end (§3). Doc 152 §3.1 is confirmed by measurement, twice.
2. **The coredump is created only after `rproc_stop()` returns** (source-verified, §4). Fatal #13
   produced no dump because the AP stopped making progress *inside* `rproc_stop()` — the dump was
   **never created**, not merely expired. This is now confirmed on both sides of the comparison.
3. **`/dev/mem` cannot read the mpss region by any method** (§5) — source-verified *and* measured.
   Doc 152 §8 item 3 option (b) ("read `0xc1d47410` live from the AP with `devmem` at ~1 Hz") is
   **withdrawn**.
4. **The deployed firmware is verified to be the clean stock HMU05 set** — all 21 modem and 9 WCNSS
   segments byte-identical to the stock device dump (§6b), discharging the standing directive's
   step 1 and ruling out any firmware-patch experiment as the cause.

## 2. SOP compliance

| SOP element | this session |
| :-- | :-- |
| Dual-firmware comparative protocol | **N/A** — no firmware was built, patched or transplanted; this is pure instrumentation of the deployed HMU05 stock baseband. |
| Verify before trusting a premise | **Applied, and it paid.** Doc 152 §8 item 3(b) asserted `devmem` could read modem memory. Measurement + source reading disproved it (§5). |
| Ground truth from source, not narrative | **Applied.** Every claim in §4 and §5 is quoted from the 6.12.94 tree in `openwrt/build_dir/…/linux-msm89xx_msm8916/linux-6.12.94/`. |
| Record what was done, the result, and what is next | This doc: §3–§7 results, §8 status, §9 next. |
| Confirm firmware provenance before attributing a result | **Applied.** The deployed modem and WCNSS sets were hashed against the stock dump (§6b) rather than assumed, per the standing SOP step. |
| Do not treat the corpus as fact | Doc 152 §3.1/§7 are *confirmed*; Doc 152 §8 item 3(b) is *withdrawn*; the §4 mechanism is *corrected*. |
| Minimal, reversible device changes | Yes — see the status block above. |

## 3. Result: fatals #14 and #15 were captured

### 3.1 The fatal and the complete SSR sequence

`dmesg`, verbatim (AP uptime in `[ ]`):

```
[  914.042208] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[  914.042289] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[  914.050750] remoteproc remoteproc0: handling crash #1 in 4080000.remoteproc
[  914.058182] remoteproc remoteproc0: recovering 4080000.remoteproc
[  914.065024] bam-dmux …: bam_dmux: SSR before shutdown: scheduling teardown work
[  914.071622] bam-dmux …: bam_dmux: executing serialized asynchronous SSR teardown
[  914.078008] wwan wwan0: port wwan0at0 disconnected
[  914.090588] wwan wwan0: port wwan0at1 disconnected
[  914.095641] wwan wwan0: port wwan0qmi0 disconnected
[  914.103358] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[  914.405108] bam-dmux …: bam_dmux: modem pc-ack timeout during resume
[  914.813232] qcom-q6v5-mss 4080000.remoteproc: port failed halt
[  914.861869] qcom-q6v5-mss 4080000.remoteproc: MBA booted without debug policy, loading mpss
[  915.418573] bam-dmux …: bam_dmux: modem pc_state wait timeout during resume
[  915.418777] bam-dmux …: bam_dmux: channels not initialized after resume
[  915.427125] bam-dmux …: bam_dmux: refusing to queue command while modem is collapsed
[  915.436572] bam-dmux …: bam_dmux: SSR after powerup: scheduling powerup work
[  915.446930] remoteproc remoteproc0: remote processor 4080000.remoteproc is now up
[  915.661643] bam-dmux …: bam_dmux: SSR powerup: modem pc_state=1 (waited 200 ms)
[  915.661719] bam-dmux …: bam_dmux: SSR powerup: channels already active
[  915.877035] wwan wwan0: port wwan0at0 attached
[  915.877463] wwan wwan0: port wwan0at1 attached
[  916.075133] bam-dmux …: bam_dmux: received CMD_OPEN (1) on channel 0
   … channels 1–7 …
[  916.134503] wwan wwan0: port wwan0qmi0 attached
```

* **This fatal did not hang the AP.** SSR downtime was **2.09 s** (914.042 → 916.135); the modem
  re-provisioned and all eight BAM channels re-opened.
* Fatal at **AP 914.042 s** — **not** the ~902 s of the deterministic idle case. Consistent with the
  activity-correlation already recorded in `project_fatal_periodicity_and_signatures`.
* The block `modem pc-ack timeout during resume` → `modem pc_state wait timeout during resume` →
  `channels not initialized after resume` → `refusing to queue command while modem is collapsed`
  is the **A2 power-collapse diagnostic path firing during recovery** (see §8 item 5).

### 3.2 The capture

Watcher log (`/overlay/coredump_watch.log`), verbatim:

```
[915.44s] dump device devcd1 at uptime 915.44s -> /overlay/coredump_live/modem_coredump_up915.44_devcd1.elf
[920.01s] captured 85398475 bytes from devcd1
```

* **85 398 475 bytes** — byte-identical in size to the four Doc-148 dumps.
* Device md5 `ed1a32554f19abf419cb67f4f6636f54`; host md5 after transfer **matches**.
* Transfer took 3.5 s (85 MB over USB Ethernet).
* The watcher **released** the device by writing `data` (Doc 152 §3.2) — `/sys/class/devcoredump/`
  was empty immediately afterwards, and `remoteproc0/coredump` remained `enabled`.
* The poll caught the device at 915.44 s; `rproc_stop` had returned at 914.103 s. The 2 s poll
  therefore cost ≈1.3 s — **the poll interval is not the bottleneck** (§4 explains why).

### 3.3 A second fatal, captured automatically — the watcher is repeatable

While this doc was being written, the **next** fatal fired in the same boot and was captured with
no intervention:

| | fatal #14 | fatal #15 |
| :-- | :-- | :-- |
| `fatal error received` at AP | 914.042208 s | **1817.721637 s** |
| Δ from the previous fatal | — | **903.679429 s** |
| devcd device | `devcd1` | `devcd2` |
| captured at AP | 915.44 s | 1818.92 s |
| bytes | 85 398 475 | 85 398 475 |
| md5 | `ed1a32554f19abf419cb67f4f6636f54` | `e66281e38061a152c282756e05ebf13e` |

Both md5s verified on device **and** host. **Two fatals, two captures, zero manual steps** — the
watcher is no longer a suspect for anything. Note also that the fatal at **1817.72 s** is again
*not* the ~902 s of the idle case: this boot's two fatals were at 914.04 s and 1817.72 s, i.e. the
period is right but the phase is offset — consistent with the activity-correlation already
recorded.

### 3.4 What this proves

* **Doc 152 §3.1 is confirmed by measurement.** The `test -s` guard really was why nothing was
  captured: the corrected watcher, changed *only* in its guard and its release, captured the very
  next fatal. (A true A/B — old and new watcher on the same fatal — is not possible, so this is
  confirmation by the strongest available means, not by controlled comparison.)
* **The watcher is no longer a suspect.** Any future missing dump is now an AP-side or
  devcoredump-side failure, not a watcher bug.

## 4. The coredump is created only *after* `rproc_stop()` returns

### 4.1 Source

`drivers/remoteproc/remoteproc_core.c`, `rproc_boot_recovery()`:

```c
static int rproc_boot_recovery(struct rproc *rproc)
{
	ret = rproc_stop(rproc, true);
	if (ret)
		return ret;

	/* generate coredump */
	rproc->ops->coredump(rproc);

	/* load firmware */
	ret = request_firmware(&firmware_p, rproc->firmware, dev);
	…
}
```

and `rproc_stop()` calls the sub-device stop chain **unconditionally**:

```c
	/* Stop any subdevices for the remote processor */
	rproc_stop_subdevices(rproc, crashed);
```

`rproc_stop_subdevices()` walks `rproc->subdevs` and calls `subdev->stop(subdev, crashed)`. For the
modem that is `ssr_notify_stop()` (`drivers/remoteproc/qcom_common.c:457`), which fires
`QCOM_SSR_BEFORE_SHUTDOWN` on the SSR notifier chain. `qcom_bam_dmux.c:2083` receives it:

```c
	case QCOM_SSR_BEFORE_SHUTDOWN:
		dev_info(dmux->dev, "bam_dmux: SSR before shutdown: scheduling teardown work\n");
		WRITE_ONCE(dmux->in_teardown, true);
		WRITE_ONCE(dmux->pc_state, false);
		schedule_work(&dmux->ssr_teardown_work);
```

So the teardown is **scheduled from inside `rproc_stop()`**, and `rproc->ops->coredump()` runs only
after `rproc_stop()` returns.

### 4.2 Measurement — both sides of the comparison

| | fatal #13 (Doc 151/152) | fatal #14 (this doc) |
| :-- | :-- | :-- |
| console ends with | `… executing serialized asynchronous SSR teardown` → port disconnects → **silence** | `… stopped remote processor` → recovery continues |
| `stopped remote processor` printed? | **no** | **yes**, at 914.103358 |
| `rproc_stop()` returned? | **no** | yes |
| coredump created? | **no** | **yes** |
| AP | hard hang → watchdog reboot | survived, SSR completed in 2.09 s |

**Conclusion:** a fatal that stops the AP's progress inside the `bam_dmux` SSR teardown loses the
coredump **because it is never created**, not because the 5-minute devcoredump window expired. Doc
152's "the window elapses during the hang" framing (carried from the session that produced it) is
**superseded**. The 5-minute window is only relevant *after* a dump exists.

**Consequence for the programme:** capturing a coredump requires a **clean** fatal. Hanging fatals
are structurally uncapturable by this mechanism — which makes §8 item 5 (characterise the hang)
more valuable, not less.

## 5. `/dev/mem` cannot read the mpss region — `devmem` live reads are impossible

Doc 152 §8 item 3(b) proposed reading `0xc1d47410` live with `devmem`. **That is not possible.**
Both mechanisms fail, and the reason is structural.

### 5.1 Measurement

The mpss reserved region (from `dmesg`, boot 0.000000):

```
OF: reserved mem: 0x0000000086800000..0x000000008bcfffff (87040 KiB) nomap non-reusable mpss@86800000
```

and `/proc/iomem` shows it as a **top-level sibling of System RAM**, not a child of it:

```
86000000-8bcfffff : reserved          <-- mpss, nomap
8bd00000-8dbfffff : System RAM
  8db00000-8dbfffff : reserved        <-- ramoops, map
```

Because the four coredumps' PT_LOAD segments span **`0x86800000`–`0x8ace6000`**, i.e. inside the
mpss region, **`dump_va` *is* the AP physical address** for the modem region. Therefore ELF VA
`0xc1d47410` → AP phys **`0x88547410`**.

| probe | result |
| :-- | :-- |
| `devmem 0x88547410 32` (mpss, `nomap`) | **Bus error** (SIGBUS) |
| `dd if=/dev/mem bs=4096 skip=$((0x88547000/4096)) count=1` | **`Bad address`** (EFAULT) |
| `devmem 0x8e200000 32` (wcnss, `nomap`) | **Bus error** — so this is `nomap`-general, not mpss-specific |
| `devmem 0x8db00000 32` (ramoops, `map`) | `0x43474244` (`DBGC`) — **works** |
| `dd if=/dev/mem … 0x8db00000` | works — control for the read path |
| `devmem 0x80000000 32` (System RAM) | `0xD503201F` (arm64 `nop`) — works |
| `zcat /proc/config.gz \| grep DEVMEM` | `CONFIG_DEVMEM=y`, **`# CONFIG_STRICT_DEVMEM is not set`** |

So the blocker is **not** `CONFIG_STRICT_DEVMEM` — it is `nomap`.

### 5.2 Source

`arch/arm64/mm/mmap.c` — the **read()** path rejects `nomap` outright:

```c
int valid_phys_addr_range(phys_addr_t addr, size_t size)
{
	return memblock_is_region_memory(addr, size) &&
	       memblock_is_map_memory(addr);      /* FALSE for nomap */
}
```

`arch/arm64/mm/mmu.c:99` — the **mmap()** path (which is what busybox `devmem` uses) silently
downgrades a `nomap` pfn to a Device mapping:

```c
pgprot_t phys_mem_access_prot(struct file *file, unsigned long pfn,
			      unsigned long size, pgprot_t vma_prot)
{
	if (!pfn_is_map_memory(pfn))
		return pgprot_noncached(vma_prot);   /* DRAM as Device-nGnRnE -> external abort */
	else if (file->f_flags & O_SYNC)
		return pgprot_writecombine(vma_prot);
	return vma_prot;
}
```

DRAM accessed as Device-nGnRnE takes an external abort → the SIGBUS we measured. **This is why
`rpmring` works and `devmem` on mpss cannot:** the RPM log ring at `0x29dc58` is *SRAM*, not DRAM,
so a `pgprot_noncached` mapping is correct for it.

### 5.3 What *would* work

`arch/arm64/mm/ioremap.c:27` refuses only **map** memory:

```c
	if (WARN_ON(pfn_is_map_memory(__phys_to_pfn(phys_addr))))
		return NULL;
```

A `nomap` region passes that test, so **a kernel module can `ioremap()`/`memremap()` the mpss
region** and expose it. Two viable routes, both non-destructive:

1. **A small kernel module** that maps the mpss region and exposes a read-only debugfs file. Gives
   continuous ~1 Hz observation of *any* modem address — by far the most useful instrument, and it
   is the same class of tool as the `edl_tcsr.ko` already built for the Android side.
2. **The debugfs `crash` trigger** (`/sys/kernel/debug/remoteproc/remoteproc0/crash`, mode `--w--`)
   forces a crash → the normal coredump path runs → an 85 MB snapshot of a **healthy** modem on
   demand. Zero build, but destructive (SSR) and one sample per crash.

The debugfs `coredump` file is **not** a trigger — `rproc_coredump_write()` accepts only
`disabled` / `enabled` / `inline` (`drivers/remoteproc/remoteproc_debugfs.c`).

## 6. The watcher now autostarts from `/etc/rc.local`

A hanging fatal reboots the AP (§4), so a watcher started by hand does not survive to the next
fatal. `/etc/rc.local` is on the overlay (`/overlay/upper/etc/rc.local`) and is run by
`/etc/init.d/done` (`[ -f /etc/rc.local ] && { sh /etc/rc.local … }`, symlinked `S95done`) — early
enough, since fatals land at ~900 s and the devcoredump window is ~5 min.

**Trap found and fixed:** busybox `start-stop-daemon -S -x /overlay/coredump_watch.sh` is **not
idempotent** for a script. Its `-x` match is applied to `/proc/PID/cmdline`, which here is
`/bin/sh /overlay/coredump_watch.sh …`, so the match fails and a second `-S` **spawns a duplicate**
(observed: PIDs 7002 + 14426). The hook therefore guards on a pidfile that the watcher writes
itself:

```sh
if [ -x /overlay/coredump_watch.sh ]; then
	if [ -f /var/run/coredump_watch.pid ] && \
	   kill -0 "$(cat /var/run/coredump_watch.pid)" 2>/dev/null; then
		/usr/bin/logger -t coredump-watch \
			"watcher already running (pid $(cat /var/run/coredump_watch.pid))"
	else
		/sbin/start-stop-daemon -S -b -x /overlay/coredump_watch.sh \
			-- /overlay/coredump_live /overlay/coredump_watch.log
		/usr/bin/logger -t coredump-watch "watcher autostarted from rc.local"
	fi
fi
```

Both branches verified on the device (`sh -n` clean; the "already running" branch exercised live and
logged; the start branch exercised when no watcher was running). `/var/run` is tmpfs, so a stale
pidfile cannot survive a reboot. The original file is preserved at `/overlay/rc.local.orig`
(132 B); the deployed file is 1299 B. A tracked copy is at `scratch/rc.local`.

## 6b. Firmware provenance verified — the deployed baseband IS the clean stock HMU05 set

The standing directive's step 1 is *"first replace the firmware with clean hmu05 modem firmware
and wifi firmware"*. That is **already satisfied**, and this session verified it end to end
against the stock device dump rather than assuming it.

`md5sum` of the deployed `/lib/firmware` set vs `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/`:

| set | segments | result |
| :-- | :-- | :-- |
| modem (`modem.mdt` + `modem.b00…b25`) | 21 | **byte-identical** |
| WCNSS/WiFi (`wcnss.mdt` + `wcnss.b00…b11`) | 9 | **byte-identical** |

Specifically, `modem.mdt` = **`1a6f9507e03d4ddbbf1977af81ecdbd7`**, which equals both
`MelbonWhiteStock_Dump/modem_extracted/image/modem.mdt` and
`compare/modem_hmu05_extracted/image/modem.mdt`, and equals **neither** the UFI001B firmware
(`56259b76…`) **nor** `compare/modem_hmu05_patched/image/modem.mdt` (`aa26ee9a…`) **nor** any of
the ~60 `modem_ufi001b_patchNN_backup` variants. The deployed WCNSS set likewise matches the stock
HMU05 dump and differs from the UFI001B set.

**Consequence:** fatal #14 (and every other fatal in this programme) fired on the **clean stock
HMU05 baseband**. No firmware-side patching experiment can be blamed for it, and the "restore
clean firmware first" precondition is discharged. Re-verify with `md5sum /lib/firmware/modem.mdt`
after any reflash — `/lib/firmware` lives on the overlay, so a sysupgrade does **not** change it
(see `project_modem_firmware_deployment`).

## 7. Fatal #14's own data

The new dump reproduces the known signature exactly:

* **ERR_FATAL record** at ELF VA `0xc35b1280` (dump `0x89db1280`): `line = 0x0186 = 390`,
  filename `lte_ml1_common_timer.c`, template `Assert 0 failed:` at `0xc35b12d6`. Unchanged.
* **`rpm` LPR descriptor** at `0xc1d473f8` — same self-consistent shape, first word = the `"rpm"`
  name pointer `0xc1848058`.

### 7.1 The `rpm` LPR `+0x18` counter — 6 samples; it is not monotonic in either direction

| dump (AP uptime s) | `rpm LPR +0x18` | Δ from previous fatal |
| :-- | :-- | :-- |
| 915.44 (fatal #14) | **1064** (`0x428`) | — |
| 919.52 | 833 (`0x341`) | — |
| 1818.92 (fatal #15) | **375** (`0x177`) | **−689** |
| 1822.52 | 989 (`0x3dd`) | — |
| 2723.69 | 937 (`0x3a9`) | −52 |
| 3629.79 | 1009 (`0x3f1`) | +72 |

Sorted by uptime within each boot: boot B gave 1064 → 375; boot A gave 833 → 989 → 937 → 1009
(Δ = +156, −52, +72). So the value neither rises nor falls monotonically and its step is not
constant.

The rest of the 16-byte entry is **byte-identical in all six dumps** — only this first word moves;
the neighbouring words are literal **1000**s. The previous four samples all sat at or below 1009,
which is why Doc 152 §6.1 read them as "per-client maxima"; **1064 exceeds every one of them, and
375 is far below**, so that reading is at best incomplete. Range across six fatals: **375–1064**.
Whether this is the quantity the RE's `sleep count not incrmnt` check tests is still **not
established** — that check is on a *delta*, and a dump gives one sample.

### 7.2 Word B increments by 11 per fatal period — now 4/4 within a boot — but does not reset across a reboot

| dump | word B (ELF `0xc35b1298`) | ΔB from previous fatal |
| :-- | :-- | :-- |
| 919.52 | `0x01128bfc` | — |
| 1822.52 | `0x01128c07` | **+0x0b** |
| 2723.69 | `0x01128c12` | **+0x0b** |
| 3629.79 | `0x01128c1d` | **+0x0b** |
| 915.44 (new boot) | `0x01128c90` | — |
| 1818.92 (new boot) | `0x01128c9b` | **+0x0b** |

The `+0x0b = 11` step is now confirmed at **4/4** within-boot transitions, across **two different
boots**. But across the reboot the advance is `0x73 = 115`, which is **not** a multiple of 11. So:

* B is **not** a per-fatal counter, and
* B **survives the modem firmware reload** — even though the descriptor lives in modem BSS, which
  one would expect SSR to reinitialise.

Read as one 64-bit little-endian value with A, the increments are `0x0B_083EC684` (within a boot,
per ~903 s) and `0x72_65EFC036` (across the reboot). If A:B is a free-running counter at the
~52.4 MHz the previous analysis inferred, the two fatals were ~**9 360 s apart in that counter's
own time** — a **falsifiable prediction** against wall-clock (§9 item 4).

### 7.3 A determinism hint, now with three comparisons

| comparison | boots | Δuptime | differing bytes |
| :-- | :-- | :-- | :-- |
| #1 vs #2 | same | 903.00 s | 4 396 967 (5.150 %) |
| #14 vs #1 | **different** | — | 4 388 621 (5.139 %) |
| #14 vs #15 | same | 903.48 s | 4 530 350 (5.305 %) |

All three land in **5.14–5.31 %**. Two dumps from *different boots* differ by about as much as two
from the *same* boot — consistent with the post-fatal memory state being largely **deterministic**,
the ~5 % being volatile buffers and counters. Worth keeping in mind before reading anything into a
diff.

## 8. Established vs not established

**Established (measured / source-verified):**

1. The corrected watcher captures, repeatably: **two fatals, two dumps, zero manual steps** in one
   boot (fatal #14 and fatal #15), each 85 398 475 B with the md5 verified on both sides (§3.2, §3.3).
2. `rproc->ops->coredump()` runs only after `rproc_stop()` returns, and the `bam_dmux` SSR teardown
   is scheduled from inside `rproc_stop()` (§4.1).
3. Fatal #13 had no dump because it never reached `coredump()`; fatal #14 did and did (§4.2).
4. `/dev/mem` cannot read `nomap` memory: read() → EFAULT, mmap() → SIGBUS, for structural reasons
   in arm64's `valid_phys_addr_range()` and `phys_mem_access_prot()` (§5).
5. `dump_va == AP physical` for the mpss region — the coredump segment range lies inside
   `mpss@86800000` (§5.1).
6. `start-stop-daemon -S -x <script>` is not idempotent in busybox 1.37 (§6).
7. Every fatal reproduces the same signature; the deployed baseband is the **clean stock HMU05**
   set, byte-identical to the stock dump across all 21 modem + 9 WCNSS segments (§6b).
8. The `+0x18` counter spans **375–1064** across six fatals, is **not monotonic in either
   direction**, and exceeds the adjacent literal 1000s (§7.1).
9. Word B advances by exactly **11 per fatal period at 4/4 within-boot transitions across two
   boots**, yet does **not** reset across a reboot (§7.2).
10. Two dumps from *different* boots differ by ~5.14 % of bytes — the same as two from the *same*
    boot (~5.15 %, ~5.31 %) (§7.3).

**Not established:**

* Why fatal #13 hung and fatal #14/#15 did not. All three printed the same `file:line`.
* What `+0x18` measures (it is not a simple counter), and what A and B mean.
* Whether B's cross-boot increment is consistent with a persistent ~52.4 MHz source (§7.2).
* Anything AP-side from the dumps: they contain the modem's private memory, **not SMEM**, so the
  SMSM APPS word and the A2 client vote list are still absent. The `a2_power` state block is in
  modem BSS and *is* present, but its ELF VA is still not established.

## 9. Next experiments, in priority order

1. **Build the mpss reader** (§5.3 item 1) — a kernel module exposing a read-only debugfs window on
   the mpss region. This is now the *only* route to live modem observation, and it unblocks the
   `+0x18` delta, the `a2_power` block, and the DRX/sleep counters in one instrument. **Top
   priority:** the `+0x18` samples (§7.1) are now clearly not a simple counter, and only a
   continuous trace can say what they are.
2. **Establish the `a2_power` state block's ELF VA** so it can be read out of the six dumps on
   hand without any new hardware access.
3. **Disassemble the `0xC1500000` segment** where `lte_ml1_common_timer.c`'s code lives
   (`modem.asm` stops at `0xc1404e0c`) — the standing blocker on naming the assert.
4. **Test the A:B prediction (§7.2)** — measure wall-clock between two fatals and compare with the
   A:B delta. Cheap, and it either validates or kills the "persistent ~52.4 MHz counter" reading.
5. **Characterise the `bam_dmux` SSR hang** — the gating obstacle for any *future* capture that
   happens to hang (§4.2), and the A2 diagnostic block in §3.1 (`pc-ack timeout`, `channels not
   initialized`, `refusing to queue command while modem is collapsed`) is the natural place to
   start.
6. **Keep the watcher running** — it is armed and autostarting; each clean fatal now yields a dump.
   Prune to one dump per fatal (`/overlay` is 3.2 GB; each dump is 85 MB).

## 10. Artifacts

| what | where |
| :-- | :-- |
| fatal #14 coredump (85 398 475 B, md5 `ed1a3255…`) | `scratch/coredump_live/modem_coredump_up915.44_devcd1.elf` |
| fatal #15 coredump (85 398 475 B, md5 `e66281e3…`) | `scratch/coredump_live/modem_coredump_up1818.92_devcd2.elf` |
| fatal #14 dmesg + firmware provenance hashes | `evidence/153_fatal14_capture/fatal14_dmesg_and_capture.txt` |
| fatal #14 dmesg (full block) | §3.1 of this doc |
| corrected watcher (with pidfile guard) | `scratch/coredump_watch.sh`, `evidence/152_coredump_capture/coredump_watch.sh` |
| autostart hook (tracked copy) | `scratch/rc.local`; deployed at `/etc/rc.local`; original at `/overlay/rc.local.orig` |
| watcher log (device) | `/overlay/coredump_watch.log` |
| VA dumper / diff tools | `scratch/coredump_live/vadump.py`, `scratch/coredump_live/diff_dumps.py` |
| field extractor (A, B, counter across all dumps) | `scratch/coredump_live/fatal_fields.py` |
| prior 4 coredumps | `scratch/coredump_live/modem_coredump_up{919.52,1822.52,2723.69,3629.79}.elf` |

## 11. One line

The coredump watcher fixed in Doc 152 **captured the next two fatals with no intervention** (#14
and #15, each 85 398 475 B, md5 verified) — confirming that the `test -s` guard was the whole reason
nothing was ever captured — while three structural facts were established the hard way: the dump is
created **only after `rproc_stop()` returns**, so fatal #13's hang meant its dump was never created
at all (not merely expired); **`/dev/mem` cannot read the modem region by any method** (read() →
EFAULT via `valid_phys_addr_range`, mmap() → SIGBUS via `pgprot_noncached` on a `nomap` pfn), which
withdraws Doc 152's "read `0xc1d47410` with `devmem`" plan and makes a small mpss-mapping kernel
module the next instrument; and the deployed baseband is verified to be the **clean stock HMU05**
set, so no firmware-patch experiment can be blamed for the fatal.


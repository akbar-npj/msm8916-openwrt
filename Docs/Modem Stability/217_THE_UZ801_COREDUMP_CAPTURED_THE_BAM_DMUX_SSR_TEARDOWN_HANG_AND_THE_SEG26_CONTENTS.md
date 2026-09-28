# 217 — THE UZ801 COREDUMP: captured, verified, and read

**Date:** 2026-09-27
**Ledger:** `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` (item 22; §3 and §8 updated in the same session)
**Device:** HMU05 4G modem stick, OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed set md5 `modem.mdt = 9f39ce579114bcf1383bbdce62a8d284`.
**Status:** **The coredump exists.** Doc 216 §5's "seg[26] content exists in no file ⇒ the UZ801 native
constant cannot be solved without a UZ801 coredump" is now **resolved**: the coredump was captured on the
first successful attempt, pulled, md5-verified, and read.

---

## 1. SOP compliance and Achieved vs Expected

**SOP compliance statement (ledger `197` §1).** Every step of this work was performed on the live HMU05
with the UZ801 v3.2 "-21" set deployed and **byte-verified before and after** (`modem.mdt` md5
`9f39ce579114bcf1383bbdce62a8d284`, unchanged). The capture instrument was read from the **tracked**
kernel tree under `openwrt/build_dir/.../linux-6.12.94/drivers/remoteproc/` before use — no baseband byte
was written. The one write to the device outside `/root` was `modem-guard`-neutral (a module unload/reload,
not a firmware change). **No `echo stop > /sys/class/remoteproc/remoteproc0/state` was ever issued** — the
documented AP-reset hazard. The overlay was `sync`ed after the 84 MB dump write. Reversibility was
established before acting: the module reload is a `modprobe`, the dump is a file, and the guard was
verified armed (`modem-guard: stable for 180s; boot counter cleared`) before the crash was fired.

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Obtain a UZ801 coredump (Doc 216 §5, ledger item 21's priority #1) | one valid dump of the UZ801 MPSS | **84 407 190 B**, md5 `59855a4cfac73e7dd2a4b54032ff5c42`, ELF32 `ET_CORE`/`EM_NONE`, 21 phdrs — **captured, pulled, byte-verified** | **MET** |
| Do it without resetting the AP | the AP survives the crash+recovery | attempt 1 **hung** (PMIC PON WDT); attempt 2 **survived** (`boot_id` unchanged, full `q6v5-trace 05..23` → `remote processor is now up`) | **MET** on the second attempt |
| Explain why the first attempt hung | a mechanism, not a guess | the **`bam_dmux` SSR teardown** blocks between `T4 state_lock acquired` and `T5 rx released` inside `bam_dmux_power_off()`, and patch 821's **synchronous `flush_work()`** in the SSR subdev `stop` callback makes `rproc_stop()` never return — which is *upstream of* `rproc->ops->coredump()` | **MET** |
| Establish the UZ801 native constant | `native = ELF VA + K` | **K = 0x0beb1000**, identical to stock; equivalently `native = coredump VA + 0x456b1000` and `ELF VA = coredump VA + 0x39800000` | **MET** |
| Reach seg[26] (the region in no file) | the content, at last | **captured**: `p_vaddr 0x8ae15000`, `filesz 0x00aeb000`, ELF VA `0xc4615000` — it holds the whole MMOC string pool | **MET** |
| Do the name-level table diff vs stock (deferred by Doc 215/216) | a name list difference | **72 of 75** stock command names present; **3 absent from UZ801**: `IMS_DEREG_CNF`, `PS_DETACH_ENTER`, `WAIT_IMS_DEREG_CNF`. All 10 protocol names present with identical counts | **MET** |
| Prove the hang is the `bam_dmux` teardown (not the crash trigger) | a controlled A/B | **A/B done**: `bam_dmux` loaded → hang, no dump; `bam_dmux` unloaded → clean dump, AP alive | **MET** |
| Keep the device usable afterwards | reachable, firmware intact | `modprobe qcom_bam_dmux` restored the module (8 `wwan` ifaces, modem `CMD_OPEN` received); UZ801 firmware intact; no `modem-guard` revert | **MET** |

---

## 2. Headline

| # | finding | one line |
| :-- | :-- | :-- |
| 1 | **The coredump exists** | 84 407 190 B, md5 `59855a4cfac73e7dd2a4b54032ff5c42`, 21 segments, and the segment map matches the UZ801 ELF phdr-for-phdr |
| 2 | **The capture needed one non-obvious step** | `rmmod qcom_bam_dmux` **before** firing the crash — without it the AP hangs and **no coredump is ever created** |
| 3 | **The hang is a fourth site, and it is the reason the coredump was unreachable** | `bam_dmux_power_off()` blocks between `T4` and `T5`; patch 821's synchronous `flush_work()` in the SSR notifier turns that into "`rproc_stop()` never returns" ⇒ `rproc->ops->coredump()` never runs |
| 4 | **`rmmod qcom_bam_dmux` is SAFE and its own teardown completes** | `T5 rx released` → `T6 tx released` → `T7 power_off returned` in **~6 ms** ⇒ the T4→T5 block is **specific to the SSR/notifier context**, not intrinsic to the function |
| 5 | **seg[26] is captured and readable** | ELF VA `0xc4615000`..`0xc50c0000`; Doc 216 §5's "exists in no file" is superseded — the coredump is the file |
| 6 | **The MMOC string pool is now readable** | 119 NUL-terminated strings from native `0xd0ea9230`: `MMOC:ready`, the 9 protocol names, ~90 command names, and every `=MMOC= …` log format |
| 7 | **The name tables are NOT native-pointer tables to that pool** | zero pointers in native, ELF-space and raw encodings — the UZ801 table location is now the open question (see §11) |
| 8 | **Three command names are genuinely absent from UZ801** | `IMS_DEREG_CNF`, `PS_DETACH_ENTER`, `WAIT_IMS_DEREG_CNF` — an IMS-dereg / PS-detach reduction |
| 9 | **The build provenance is leaked in the image** | 128 source paths, all under `/home/chiyong/UZ801_V3.0-21/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/modem_proc/` |
| 10 | **A documented `rmmod` hazard does not apply to the current tree** | ledger/memory recorded "`bam_dmux_remove()` … will hang a future `rmmod`"; measured: it returns 0 and the AP survives — the ABBA was already removed by the non-blocking watchdog cancel at `qcom_bam_dmux.c:1609` |

---

## 3. The capture

### 3.1 The instrument, from the tracked kernel tree

Read from `openwrt/build_dir/.../linux-6.12.94/drivers/remoteproc/`:

* `remoteproc_debugfs.c:407-418` registers, under `/sys/kernel/debug/remoteproc/<rproc>/`:
  `name`, `recovery` (0600), **`crash` (0200)**, `resource_table`, `carveout_memories`, `coredump` (0600).
* `rproc_coredump_write()` (`:71`) accepts **`disabled` / `enabled` / `inline`** — this is a
  **configuration**, *not* a trigger, and it is refused while `rproc->state == RPROC_CRASHED` (`:90`).
* `rproc_crash_write()` (`:251-266`) is the **trigger**: `kstrtouint_from_user` then
  `rproc_report_crash(rproc, type)`.
* `rproc_report_crash()` (`remoteproc_core.c:2703`) → `queue_work(rproc_recovery_wq, &rproc->crash_handler)`.

### 3.2 The path a synthetic crash takes — identical to a real fatal

```
rproc_crash_handler_work()          remoteproc_core.c:1864
    rproc->state = RPROC_CRASHED                             :1885
    rproc_trigger_recovery()                                 :1892
        rproc_boot_recovery()                                :1792
            rproc_stop(rproc, true)                          :1798
                rproc_stop_subdevices(rproc, true)           :1717
                    -> qcom_ssr subdev stop -> srcu_notifier_call_chain(QCOM_SSR_BEFORE_SHUTDOWN)
                       -> bam_dmux_ssr_notifier_cb()  [qcom_bam_dmux.c:2466]
                -> q6v5_stop() -> q6v5_mba_reclaim()
            rproc->ops->coredump(rproc)                      :1803   <-- dev_coredumpv()
            rproc_start(rproc, fw)                           :1813
```

Three consequences that matter:

1. **`qcom_q6v5_request_stop()` short-circuits** (`qcom_q6v5.c:205`): it returns 0 immediately when
   `q6v5->rproc->state != RPROC_RUNNING`. Because `rproc_crash_handler_work()` has *already* set the state
   to `RPROC_CRASHED`, the 5 s SMP2P stop dance is **skipped** and the Q6 is force-halted. A synthetic
   crash is therefore the *same* path a real fatal takes — no new code, no new ordering.
2. **The coredump is *inside* `rproc_boot_recovery()`**, between `rproc_stop()` and `rproc_start()`. This
   is the documented fact from the stock work; it is re-confirmed here and it is what makes finding #3
   fatal to the capture.
3. **The reader re-establishes its own access path.** `q6v5_mba_reclaim()` sets
   `dump_mba_loaded = false` (`qcom_q6v5_mss.c:1246`), so the first
   `qcom_q6v5_dump_segment()` (`:1533`) calls `q6v5_reload_mba()` + `q6v5_xfer_mem_ownership()` before
   `memremap()`. **The dump therefore does not depend on the modem having crashed** — a cleanly stopped
   modem dumps just as well, which is why the synthetic route works at all.

### 3.3 The recipe that works

```sh
# 0. sanity: the modem must not already be CRASHED, and the config attr is writable
cat  /sys/class/remoteproc/remoteproc0/state            # -> running
cat  /sys/kernel/debug/remoteproc/remoteproc0/coredump  # -> disabled

# 1. THE ESSENTIAL STEP — remove the SSR notifier that hangs the recovery
rmmod qcom_bam_dmux                                     # refcount must be 0 first

# 2. arm the dump
echo enabled > /sys/kernel/debug/remoteproc/remoteproc0/coredump

# 3. fire
echo 1 > /sys/kernel/debug/remoteproc/remoteproc0/crash

# 4. copy IMMEDIATELY — devcoredump auto-frees after 5 minutes
for i in $(seq 1 900); do
  for d in /sys/class/devcoredump/devcd*; do
    [ -r "$d/data" ] && { cat "$d/data" > /root/uz801_coredump.elf; exit 0; }
  done
  sleep 0.2
done

# 5. afterwards, restore
modprobe qcom_bam_dmux
```

Measured timing: the dump appeared **5 polls (~1 s)** after the crash write; the modem was fully back
(`remote processor 4080000.remoteproc is now up`, `wwan0at0/at1/qmi0 attached`) **1.1 s** later.

**`rmmod` must be checked, not assumed.** Pre-flight: `cat /sys/module/qcom_bam_dmux/refcnt` → `0`, all
`wwan0`–`wwan7` `DOWN`, no holders. (`rpmsg_wwan_ctrl` holds no SSR/rproc hooks at all — its source has
zero `ssr`/`notifier`/`rproc` matches — so it is *not* part of the SSR chain and does not need removing.)

---

## 4. Attempt 1 — the hang, and why it produced nothing

Fired with `bam_dmux` **loaded**. The SSH session died; the device came back with a **new `boot_id`**
(`26d74096…` → `716548d1…`, `up 0 min`). No coredump, no `/root/uz801_coredump.status`.

`/sys/fs/pstore/console-ramoops-0` (48 121 B, holding the **previous** boot from `[0.000000]`) ends:

```
[ 8858.644621] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type watchdog
[ 8858.644837] remoteproc remoteproc0: handling crash #1 in 4080000.remoteproc
[ 8858.651488] remoteproc remoteproc0: recovering 4080000.remoteproc
[ 8858.658434] bam-dmux ...: SSR before shutdown: scheduling teardown work
[ 8858.664775] bam-dmux ...: SSR teardown T0 scheduled
[ 8858.674232] bam-dmux ...: SSR teardown T1 entry
[ 8858.682037] bam-dmux ...: SSR teardown T2 tx_retry cancelled
[ 8858.689542] bam-dmux ...: SSR teardown T3 tx_wakeup cancelled
[ 8858.698006] bam-dmux ...: SSR teardown T4 state_lock acquired
[ 8858.706597] bam-dmux ...: executing serialized asynchronous SSR teardown
```

**and nothing after** — the console stops 62 ms after crash detection.

### 4.1 The exact block point

`bam_dmux_ssr_teardown_work_func()` (`qcom_bam_dmux.c:2304`) prints `T4` at `:2345` and then calls
`bam_dmux_ssr_teardown(dmux)` at `:2347`, which is (`:1933`):

```c
bam_dmux_power_off(dmux);
dev_err(dmux->dev, "bam-dmux T7 power_off returned\n");
```

and `bam_dmux_power_off()` (`:1578`) prints `T5 rx released` only at `:1623`. **Neither `T7` nor `T5`
appeared** ⇒ the block is **inside `bam_dmux_power_off()` before line 1623**, i.e. in one of:
the idempotency check (`:1584`), `cancel_delayed_work_sync(&rx_rearm_work)` (`:1608`),
`cancel_delayed_work(&rx_watchdog_work)` (`:1609`), `wait_event(rx_submit_wait, rx_active_submitters == 0)`
(`:1612`), `dmaengine_terminate_sync(dmux->rx)` (`:1617`), or `dma_release_channel(dmux->rx)` (`:1618`).

The idempotent fast-return cannot fire: it requires `rx_tearing_down && !rx && !tx` (`:1585`), and the
device's own telemetry says `rx=held` — i.e. `rx != NULL`. The modem has been power-collapsed since boot
(`pc_state=0, pc_line=0` at every one of the 148 RX-watchdog lines), so the BAM is unpowered throughout.

### 4.2 Why the block kills the coredump — patch 821

`bam_dmux_ssr_notifier_cb()` (`:2466`), `case QCOM_SSR_BEFORE_SHUTDOWN`, ends with (`:2510`):

```c
flush_work(&dmux->ssr_teardown_work);
dev_err(dmux->dev, "bam-dmux T9 flush returned\n");
```

**This is a synchronous `flush_work()` inside the SSR subdevice's `stop` callback** — i.e. inside
`rproc_stop_subdevices()`, inside `rproc_stop()`, inside `rproc_boot_recovery()`, **before**
`rproc->ops->coredump()`. Patch 821 added it deliberately, and its own comment (`:2487-2508`) explains why:
left asynchronous, the teardown raced the `q6v5_stop()` power-down and the AP reset. Making the ordering
deterministic was the right call — but it also means **any block inside the teardown is now a block in
`rproc_stop()`**, and the coredump sits after it.

`T9 flush returned` never printed. Consistent, and complete: **no coredump can exist when this block
fires.**

The reset itself is the known signature: a ≥30 s global stall with the PMIC PON WDT armed, an empty pstore
*entry for the crash*, and a `boot_id` change — not a panic.

---

## 5. The A/B — `bam_dmux` is the cause, proven

| | attempt 1 | attempt 2 |
| :-- | :-- | :-- |
| `qcom_bam_dmux` | **loaded** | **unloaded** (`rmmod`, rc 0) |
| SSR teardown | `T4` then block; `T5`/`T6`/`T7`/`T9` never print | not run (notifier unregistered) |
| coredump | **never created** | **84 407 190 B** |
| AP | **reset** (new `boot_id`, PMIC PON WDT) | **survived** (same `boot_id`, `state_after: running`) |
| modem | reloaded by the post-reset boot | `q6v5-trace 05..23` → `MBA booted` → `remote processor is now up` |

One variable, two outcomes. **The hang is the `bam_dmux` SSR teardown**, and removing the notifier is both
necessary and sufficient for the capture.

### 5.1 A surprise that narrows the hang: `rmmod` runs the *same* teardown cleanly

`bam_dmux_remove()` (`:2665`) ends with `disable_irq(pc_irq)` then, under `state_lock`,
`bam_dmux_ssr_teardown(dmux)` (`:2712`) — **the very function that blocked on the SSR path**. Its dmesg:

```
[  490.650920] bam-dmux T5 rx released
[  490.651021] bam-dmux T6 tx released
[  490.656846] bam-dmux T7 power_off returned
```

`T4`→`T7` in **~6 ms**, `rmmod_rc=0`, `boot_id` unchanged, uptime 490.42 → 490.57 s. So the block is
**not** intrinsic to `bam_dmux_power_off()` on an unpowered BAM — it is **specific to the SSR/notifier
context** (a workqueue worker running under `flush_work` while `rproc_stop_subdevices()` holds the recovery
path, with `rproc->lock` held by `rproc_trigger_recovery()`). That is a *narrower* statement than "the
function races", and it is the useful one: the next investigation should compare the two contexts
(`rproc->lock`, `state_lock`, `pc_irq`'s threaded handler, and the rpmsg/SMD teardown running concurrently),
not re-read `bam_dmux_power_off()` in isolation.

**It also retires a recorded hazard.** Ledger/memory carried "`bam_dmux_remove()` (`:2582-2626`) has the
same ABBA and **will hang a future `rmmod`**". Measured 2026-09-27: it does not. The ABBA was the
**synchronous** `cancel_delayed_work_sync(&rx_watchdog_work)` under `state_lock`, and the current tree
cancels the watchdog **non-blocking** (`:1609`, with the reasoning in the `:1592-1607` comment) while
keeping `rx_rearm_work` synchronous by design. **The stale warning is withdrawn.**

---

## 6. The coredump, verified

| property | value |
| :-- | :-- |
| size | **84 407 190 B** (stock reference: 85 398 475 B) |
| md5 | **`59855a4cfac73e7dd2a4b54032ff5c42`** (device and host agree) |
| class / type / machine | `ELFCLASS32` / `ET_CORE` / `EM_NONE` — exactly `rproc_coredump_set_elf_info(rproc, ELFCLASS32, EM_NONE)` (`qcom_q6v5_mss.c:1653`, `:2016`) |
| program headers | **21**, `e_phentsize` 32, `e_phoff` 52 |
| local path | `scratch/coredump_uz801/uz801_coredump.elf` |
| device copy | `/root/uz801_coredump.elf` (kept on the overlay) |

**Every one of the 21 segments matches the UZ801 ELF phdr-for-phdr**, `p_vaddr` = the ELF's `p_paddr`:

| coredump phdr | coredump VA | size | UZ801 ELF phdr | note |
| :-- | :-- | :-- | :-- | :-- |
| 0–4 | `0x86800000`…`0x86844000` | … | `LOAD` ×5 | `filesz > 0` |
| 5 | `0x86874000` | `0x1e5e8` | phdr 11 (`filesz 0xbe40`) | size = **`memsz`**, so BSS is included |
| 6–14 | `0x86898000`…`0x8855b000` | … | `LOAD` ×9 | `filesz > 0` |
| **15** | **`0x8884d000`** | **`0x142628`** | **phdr 22 (`filesz = 0`)** | **seg[20]** — in no file |
| **16** | **`0x88990000`** | **`0x1baf600`** | **phdr 23 (`filesz = 0`)** | **seg[21]** — in no file |
| 17–19 | `0x8a540000`…`0x8a5ce000` | … | `LOAD` ×3 | `filesz > 0` |
| **20** | **`0x8ae15000`** | **`0x00aeb000`** | **phdr 27 (`filesz = 0`)** | **seg[26]** — in no file, ELF VA `0xc4615000` |

### 6.1 Address relations — both confirmed, and both identical to stock

```
ELF VA      = coredump VA + 0x39800000        (21/21 segments)
native VA   = ELF VA      + 0x0beb1000        (K = 0x0beb1000)
            = coredump VA + 0x456b1000
```

`K` is not merely assumed: it is **verified by content**. Reading the coredump at
`ELF 0xc1d33eb0 + 0x0beb1000` and at the stock-derived offsets yields the UZ801 MMOC string pool with
correct, self-consistent ASCII (§8), and `mmoc.c` / `mmoc_init` resolve at ELF `0xc1823c02` / `0xc18e3fe4`.
The identity of `0x0beb1000` across both builds is expected — it is a property of the **MPSS runtime
memory map**, not of the firmware — and it is now measured, not inferred.

---

## 7. seg[26] — the region that exists in no file

`seg[26]` is `p_vaddr 0xc4615000` (ELF) / `0x8ae15000` (phys), `memsz = filesz = 0xaeb000` **in the ELF**,
and Doc 216 §5 established that no `modem.b26` exists and that its distinctive strings appear in none of
23 candidate artifacts, including both 64 MB FAT16 images.

**It is now in hand**, as coredump phdr 20 (file offset `0x04594396`, 11 448 320 B). Its content is
**`.rodata` for a specific module set**, and it is not empty or zeroed — it holds:

* the **entire MMOC string pool** (§8), including `MMOC:ready`, the protocol names, ~90 command names and
  every `=MMOC= …` log format;
* `mmoc.c`, `mmocdbg.c` and their siblings;
* the source-file paths of the UZ801 build (§10), dominated by **`uim/mmgsdi` (34)**, **`hdr/cp` (29)**,
  **`uim/gstk` (18)**, **`mmcp/policyman` (12)**, **`uim/estk` (6)**.

Two consequences:

1. **Doc 216 §5 / ledger item 21 are superseded on this point.** "seg[26] content exists in NO file" was
   true of the *firmware artifacts*; the coredump **is** the file. The ELF-splice plan stays void (there is
   still no `modem.b26` to splice), but the *content* is no longer unavailable.
2. **The UZ801 native constant is solved** (§6.1), which was the item Doc 216 downgraded to
   "needs a UZ801 coredump".

---

## 8. The MMOC string pool, read from the runtime

From coredump VA `0x8b7f8230` (ELF `0xc4ff8230`, native `0xd0ea9230`) to `0x8b7f8b5c`:
**119 NUL-terminated strings, 106 name-like.** In stored order, verbatim:

```
MMOC:ready | MODE_INACT | MC(AMPS) | CDMA(MC Task) | GPS | HDR(HDRMC Task) | RESERVED
HYBR NAS(REG Task) | HYBR 3(REG Task) | Invalid | mmoc | mmoc.c
NAS did not respond to deact ss = %d | Deactivate request sent to %s | GWL Deactivate request sent to %d
Ph. Status request sent to %s | DS stat chgd request sent to GW | SUBS CAP chgd request sent to GW
Generic cmd request sent to %s | Wait for deactd ind from %s | Deactivate req. entry state
Phone status req. entry state | Generic cmd req. entry state | In NULL transaction state
Invalid transaction state %d | MC(CDMA-online) | MC(CDMA-offline) | MC(AMPS-online) | MC(AMPS-offline)
MC(DED. MEAS) | REG(NAS) | MC(FTM) | GPS-MSBASED | No active protocols
PROT_GEN_CMD | OPRT_MODE_CHGD | WAKEUP_FROM_PWR_SAVE | PROT_REDIR_IND | PROT_HO_IND | MMGSDI_INFO_IND
DUAL_STANDBY_CHGD | DEACT_1XCSFB_PROT | SUSPEND_SS | DEACT_FROM_DORMANT | PROT_AUTO_DEACTD_IND
PH_STAT_CHGD_CNF | PROT_GEN_CMD_CNF | PROT_AUTO_ACTD_IND | MMGSDI_CNF | 1XCSFB_PROT_DEACTD_CNF
IRAT_HOLD_USER_ACT_CNF | UE_MODE_SWITCH_CNF | PROT_DEACT_ENTER | WAIT_DEACTD_CNF | MMGSDI_READ_ENTER
MMGSDI_READ_CNF | PROT_PH_STAT_ENTER | WAIT_PH_STAT_CNF | GEN_CMD_ENTER | WAIT_GEN_CMD_CNF
WAIT_AUTO_DEACTD_IND | WAIT_AUTO_ACTD_IND | HDR_DEACT_ENTER | WAIT_HDR_DEACTD_CNF | PROT_REDIR_ENTER
WAIT_SESSION_OPEN_CNF | PROT_HO_ENTER | DS_STAT_CHGD_ENTER | WAIT_DS_STAT_CHGD_CNF
GEN_CMD_ACTIVATION_ENTER | WAIT_ACTIVATION_CNF | WAIT_PS_DETACH_CNF | HYBR2_DEACT_ENTER
WAIT_HYBR2_DEACTD_CNF | HYBR3_DEACT_ENTER | WAIT_HYBR3_DEACTD_CNF | WAIT_DEACTD_CNF_GWL
WAIT_1XCSFB_DEACT_CNF | SUSPEND_SS_ENTER | RESUME_SS_ENTER | WAIT_HOLD_USER_ACT_CNF
WAIT_UE_MODE_SWITCH | WAIT_SUBS_CAP_CHGD_ENTER | WAIT_SUBS_CAP_CHGD_CNF | SUBSC_CHGD | ONLINE | OFFLINE
PWR_DOWN | PWR_SAVE_ENTER | DEACT_1XCSFB_CMD | ONLINE_CDMA | OFFLINE_CDMA | ONLINE_AMPS | OFFLINE_AMPS
ONLINE_GWL | ONLINE_HDR | ONLINE_DED_MEAS | GPSONE_MSBASED | DORMANT_GWL
=MMOC= %s | mmocdbg.c
Prot_state [MAIN] %d(%s) -- [HYBR_GW] %d(%s) -- [HYBR_HDR] %d(%s)
Prot_state [MAIN] %d(%s) -- [HYBR_GW] %d(%s) -- [HYBR_GW3] %d(%s)
Curr_trans  %d(%s) | Trans_state %d(%s) | Recvd report %d(%s) | Recvd command %d(%s)
New transaction           : %d(%s)
```

This **is** the MMOC name space: the 9 protocol names, the command names, and the five log formats Doc 216
§6 could only reach as hashes. Doc 216 §6's claim is untouched but must be read precisely: what carries **no
pointer** is the *F3 descriptor table entry* for those formats (hash form, ~92 %). The format **strings
themselves** exist, as literals, in seg[26] — they were simply invisible because seg[26] was invisible.

Note also that the runtime holds **pre-rendered** copies in seg[21], e.g.
`=MMOC= Prot_state [MAIN] 7(OFFLINE) -- [HYBR_GW] 0(NULL) -- [HYBR_HDR] 0(NULL)` and
`=MMOC= Recvd report 5(MMGSDI_CNF)` — i.e. the firmware stores both the template and rendered variants.

### 8.1 The pool has no pointer references — an open problem, stated

Searching the whole image for 4-byte words equal to each pool string's address, in **three** encodings
(native `cd+0x456b1000`, ELF-space `cd+0x39800000`, raw `cd`), returns **zero hits** — including for
`MODE_INACT` and `WAIT_SESSION_OPEN_CNF`, which have exactly **one** copy each in the image.

The 44 words immediately before the pool (`cd 0x8b7f8180`..`0x8b7f822f`) are a table of **function
pointers** into seg[18] (values `0xd009d948`…, resolving to non-string data), not a name table.

By contrast, the **stock** `mmoc_cmd_name[]` (read at the stock ELF VA `0xc1d33eb0`) holds native pointers
`0xd0d77820`…`0xd0d77c92` that **do** resolve — against the stock coredump's seg[26]. So the two builds
differ in *how* the name table is addressed, or the UZ801 table lives outside the 21 captured segments.
**This is now the single most valuable open question** (§11).

---

## 9. The name-level diff (the deliverable Doc 215/216 deferred)

Method: read the **stock** `mmoc_cmd_name[]` (96 slots) and the stock 14-record protocol table from the
**stock coredump** (`scratch/coredump_live/modem_coredump_up919.52.elf`, 85 398 475 B) — they cannot be read
from the stock ELF, because their pointers land in the stock's `filesz = 0` seg[26]. Then test each name
for **exact NUL-terminated presence** across the whole UZ801 image.

**The stock tables, resolved (they reproduce the recorded values exactly):**

* `mmoc_cmd_name[]`: **91 named / 3 null (`11`, `61`, `95`) / 2 unresolved (`64` = `0xd0d9fd6d`,
  `94` = `0xd0e02c91`)**, with `0xd0dd533d` shared at `26`, `62`, `78`; slots `62`–`95` re-use `0`–`60`'s
  names; **75 distinct** names.
* protocol table (14 × 12 B): `MODE_INACT`, `MC(AMPS)`, `CDMA(MC Task)`, `GPS`, `HDR(HDRMC Task)`,
  `NAS(REG Task)` ×5, `RESERVED`, `HYBR NAS(REG Task)`, `HYBR 3(REG Task)`, `Invalid` — matching Doc 216 §3.

**The diff:**

| result | names |
| :-- | :-- |
| **protocol names: identical** | all 10 present in **both**, with **identical exact-occurrence counts** (`GPS` 9/9, `Invalid` 3/3, the rest 1/1) |
| **command names present in both** | **72 of 75** |
| **★ absent from UZ801 (0 exact occurrences)** | **`IMS_DEREG_CNF`**, **`PS_DETACH_ENTER`**, **`WAIT_IMS_DEREG_CNF`** |

`SUBSCRIPTION_CHGD` **is** present in UZ801 (`cd 0x8b7f8508`, one copy) — so the recorded
`0(SUBSCRIPTION_CHGD)` vs `7(DUAL_STANDBY_CHGD)` divergence is **not** explained by a missing name, and the
Doc 202 §6.3 chain stands as the explanation.

**Reading.** The UZ801 MMOC command set is a **strict subset** of stock's, reduced by exactly the
**IMS-deregistration** and **PS-detach** names — note that `PS_DETACH_CNF` is present in both while
`PS_DETACH_ENTER` is not, i.e. a *state* was dropped, not a message. This is a compile-time feature
difference in the MMOC module, and it is the first **name-level** differential found that survives an
exact-token test. It is *not* claimed to cause the `offline` gate: `WAIT_SESSION_OPEN_CNF` — the state
Doc 202 §6.3 says UZ801 hangs in — is present in **both**.

---

## 10. Build provenance, leaked by the image

`seg[26]` and the other runtime segments carry **128 distinct source paths**, all under one root:

```
/home/chiyong/UZ801_V3.0-21/msm8939-la-1-0-2-1_amss_qrd_no-l1-src/modem_proc/
```

Subtree histogram (top): `uim/mmgsdi` 34, `hdr/cp` 29, `uim/gstk` 18, `mmcp/policyman` 12, `uim/estk` 6,
`datamodem/3gpp` 5, `uim/uimdrv` 4, `rftech_cdma/common` 3, `rfa/rf` 3, `core/mproc` 2, plus singletons
(`wcdma/rlc`, `uim/uimqmi`, `uim/uimdiag`, `uim/nvruim`, `rftech_wcdma/{rf,ftm}`, `rftech_tdscdma/ftm`,
`rftech_gsm/ftm`, `rftech_cdma/1x`, `mcs/stm2`, `datamodem/protocols`, `apr/core`).

Three things worth keeping:

* the build directory is named **`UZ801_V3.0-21`** — matching the deployed "v3.2 -21" set;
* the source package is **`msm8939-la-1-0-2-1`** — an **MSM8939** AMSS tree, not msm8916 (the two are the
  same MPSS family, which is consistent with the earlier finding that both builds are natively WTR1605);
* the package is **`no-l1-src`** — **no L1 source**. The 900 s fatal family is `lte_ml1_*`, i.e. the LTE
  modem L1. If that naming is literal, the fault site's source is **not in this package**, which would
  explain the corpus's repeated inability to resolve it statically. *(Stated as a lead, not a conclusion —
  the package name alone does not prove what was omitted.)*

---

## 11. What this changes, and the next steps

**Changed:**

1. **The UZ801 coredump exists and is archived** — `scratch/coredump_uz801/uz801_coredump.elf`, md5
   `59855a4cfac73e7dd2a4b54032ff5c42`. Every future UZ801 runtime question starts here.
2. **seg[26] is available**; Doc 216 §5's negative is superseded and the UZ801 native constant is solved
   (`K = 0x0beb1000`).
3. **The coredump-capture route is now known to require `rmmod qcom_bam_dmux`**, and the reason is a
   **fourth AP-hang site** with an exact block window (`T4`→`T5` in `bam_dmux_power_off()`) and a named
   propagation mechanism (patch 821's synchronous `flush_work()` at `qcom_bam_dmux.c:2510`).
4. **The `rmmod` hazard is retired** (it was already fixed in-tree).
5. **Three MMOC command names are absent from UZ801**; the protocol set is identical.

**Next, in priority order:**

1. **★★★ Locate the UZ801 `mmoc_cmd_name[]` / protocol tables.** They are *not* a native-pointer table to
   the seg[26] pool. Two candidate explanations to test, cheapest first: (a) the table is **outside the 21
   captured segments** — check the UZ801 ELF for any region the loader populates that is not a phdr, and
   check whether a second coredump taken with `coredump = inline` differs; (b) the table is
   **offset-encoded** — search for a run of u32 values equal to the pool's string offsets
   (`0x00, 0x0B, 0x16, 0x1F, 0x2D, 0x31, 0x41, 0x4A, 0x5D, 0x6E` is the first ten).
2. **★★★ Settle the T4→T5 block.** The two contexts differ and the difference is now the whole question:
   the `rmmod` path runs `bam_dmux_power_off()` to completion in 6 ms; the SSR path does not. Instrument the
   SSR path with the same `T4a`/`T4b`… probes between `:1608` and `:1618` (five `dev_err`, ≈40 ms of console
   time — acceptable, and the block is already ≥30 s) and re-fire. **Do not** patch `bam_dmux_power_off()`
   blind: the `rmmod` result already shows the function is fine in isolation.
3. **★ Use seg[26] for the MMOC emitter hunt.** The pool gives the exact runtime native address of every
   `=MMOC= …` format string. Cross-reference those **native** addresses against the descriptor tables to see
   which are pointer-form (the ~8 %) and which hash-form — this is now a lookup, not a search.
4. **★ Re-read UZ801's own boot log strings from seg[26]** and compare with the stock coredump's seg[26]
   (`scratch/coredump_live/`) — the two runtime pools side by side is a stronger differential than any
   file-level comparison available so far.

**Do not** re-open: the CM→seg19 adjacency lead (dead), splicing seg26 into the ELF (there is still no
`modem.b26`), the PIN2/FDN blocker (ruled out), or `cmcc.c`/GROUP 11. **Do not** conclude a name is absent
from a pool-window string extraction — use an exact NUL-terminated token search. **Do not** assume a
documented `rmmod` hazard still applies without re-measuring (tree drift).

---

## 12. Evidence

| artifact | what it is |
| :-- | :-- |
| **`scratch/coredump_uz801/uz801_coredump.elf`** | **the UZ801 coredump** — 84 407 190 B, md5 `59855a4cfac73e7dd2a4b54032ff5c42`, ELF32 `ET_CORE`/`EM_NONE`, 21 phdrs |
| `scratch/coredump_uz801/seg26.bin` | seg[26] extracted verbatim — 11 448 320 B, phys base `0x8ae15000`, ELF VA `0xc4615000` |
| `scratch/coredump_uz801/xlat.py` | coredump VA ↔ ELF VA ↔ native translation + the 96-slot reader |
| `scratch/coredump_uz801/findtables.py` | the MMOC-token string scan and the native-pointer-run finder (found the IMS tables; `findtables.out`, 6 434 lines) |
| `scratch/coredump_uz801/mmocptr.py` | the pointer-reference search for the MMOC pool (the zero-result negative of §8.1) |
| `scratch/coredump_uz801/stocktbl.py` | the stock `mmoc_cmd_name[]` reader |
| `scratch/coredump_uz801/buildpaths.txt` | the 128 leaked source paths (§10) |
| `scratch/coredump_live/modem_coredump_up919.52.elf` | the **stock HMU05** coredump (85 398 475 B) — the control arm for §9 |
| device: `/root/uz801_coredump.elf` | the on-overlay copy (md5 verified) |
| device: `/sys/fs/pstore/console-ramoops-0` | the attempt-1 hang: console ends at `8858.706597 … executing serialized asynchronous SSR teardown` |

---

## 13. Traps

1. **`/sys/kernel/debug/remoteproc/<rproc>/coredump` is a CONFIGURATION, not a trigger.** Writing
   `enabled` only arms the dump; the trigger is the write-only `crash` file (mode `0200`).
2. **`dev_coredumpv()` data is auto-freed after 5 minutes.** Copy `/sys/class/devcoredump/devcdN/data`
   immediately — and copy it **on-device first**, because an AP hang destroys the only copy.
3. **The dump only exists if the SSR teardown completes.** The coredump sits *after* the teardown in
   `rproc_boot_recovery()`; a teardown block means no dump, and the 5-minute clock never starts.
4. **`rmmod qcom_bam_dmux` is required** and is safe here (refcount 0, all `wwan` down). Check the
   refcount; do not assume.
5. **`echo stop > /sys/class/remoteproc/remoteproc0/state` is never the answer** — it resets the AP
   (re-confirmed 2026-09-25). The `crash` file is the safe trigger.
6. **A coredump's phdrs are NOT 4-byte aligned relative to the file** (phdr 20: `p_offset 0x04594396`,
   `p_vaddr 0x8ae15000` — `off%4 = 2`, `va%4 = 0`). Test **VA** alignment when hunting a pointer table; a
   file-offset test silently finds nothing.
7. **The coredump's `p_vaddr` is the ELF's `p_paddr`**, and its sizes are **`memsz`**, not `filesz` — so
   BSS and the `filesz = 0` segments appear, which is exactly what makes the dump valuable.
8. **A pool-window string extraction silently drops strings embedded in binary runs.** `SUBSCRIPTION_CHGD`
   sits in a binary gap in the UZ801 pool and was missed by a `split(b'\x00')` + all-printable filter.
   Presence/absence must be decided by an **exact NUL-terminated token search over the whole image**.
9. **`grep -ac` on a binary file counts newline-delimited chunks, not occurrences.** Only `0` vs `≥1` is
   decisive; positive counts are not comparable across files.
10. **A documented hazard can be stale.** "`rmmod` will hang" was true of an older tree; the current tree
    cancels the watchdog non-blocking and `rmmod` returns in 6 ms. Re-measure before acting on a recorded
    hazard.
11. **`/sys/kernel/debug/msm_subsys/` does not exist on this kernel** (6.12.94). The path in older notes
    is wrong; the rproc debugfs directory is the instrument.
12. **The console's last line is the only evidence of a hang site** — `console_loglevel = 6` means
    `dev_info` never reaches it. Every probe that must survive a hang has to be `dev_err` and must contain a
    greppable tag.
13. **An empty pstore entry is not "no crash"** — a PMIC PON WDT STOP writes nothing, and the `boot_id`
    change is the real signal. Conversely, a *populated* `console-ramoops-0` may hold the **previous** boot
    from `[0.000000]`; read its **tail**.

---

## 14. Ledger fold-in

* **Item 22** added to `197` §3's tail: this capture, the A/B, the block window, the retired `rmmod`
  hazard, the name diff, and the build provenance.
* **§3 rows added:** obtain a UZ801 coredump (**MET**); explain the first attempt's hang (**MET**);
  establish the UZ801 native constant (**MET**); reach seg[26] (**MET**); name-level table diff (**MET**);
  prove the hang is the `bam_dmux` teardown (**MET**); retire the `rmmod` hazard (**MET**).
* **§3 rows superseded:** "Solve the UZ801 native constant" (Doc 216 §5's **DOWNGRADED** verdict is now
  **MET**); Doc 216 §5's "seg[26] content exists in NO file" — true of the firmware artifacts, **superseded
  by the coredump**.
* **§8 evidence rows added:** the coredump, `seg26.bin`, the five analysis scripts, `buildpaths.txt`, and
  the stock-coredump control.
* **§7 next-steps updated:** priority #1 becomes "locate the UZ801 name tables"; the coredump item is
  **closed**.

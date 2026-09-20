# DOC-139 — BAM-DMUX Patch 808: Android 4.4.4 Parity Audit and Applied Fixes

**Status:** ✅ APPLIED — patch 808 corrected and verified; kernel compiles clean; plus a separate
build-blocking packaging conflict found and fixed (§8)
**Related:** Doc 137 (DIAG log stream), Doc 138 (band unlocking reference), Doc 82/88 (earlier BAM-DMUX parity reports)
**Branch:** `test/pure-software-modem` @ `0584cbc`
**Date:** 2026-09-19

---

## 1. Purpose

The user asked for a pre-build audit of `msm89xx/patches/808-bam-dmux-stats.patch`, which was
written with the stated intent of being *"as close as possible to the Android 4.4.4 / kernel
3.10.28 source"* (`Docs/Modem Stability/hmu05/bam_dmux.c`), because the HMU05 originally ran
that Android build.

This document records:

1. Where the authoritative comparison sources are (so the audit is repeatable).
2. What in patch 808 **does** match Android 4.4.4 exactly.
3. What diverges, and why that divergence is acceptable or not.
4. The **three defects found and fixed**, with the exact code.
5. The verification method that proves the regenerated patch is equivalent to the old one
   apart from the intended fixes.

---

## 2. Ground-Truth Sources

All legacy Qualcomm definitions must come from the checked-in Android 4.4.4 ZTE msm8916 tree —
**not** from mainline Linux, which renamed and restructured almost all of it.

| Question | Authoritative file |
| :--- | :--- |
| BAM-DMUX command opcodes, header struct | `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux_private.h` |
| BAM-DMUX behaviour (power collapse, SSR, UL wakeup) | `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c` |
| SMSM entry indices and bit masks | `GitIgnore/android_kernel_zte_msm8916/include/soc/qcom/smsm.h` |
| SMEM item numbers | `GitIgnore/android_kernel_zte_msm8916/include/soc/qcom/smem.h` |
| `smsm_change_state()` implementation | `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/smd.c:2747` |

`Docs/Modem Stability/hmu05/bam_dmux.c` is **byte-identical** to the tree copy (verified with
`diff -q`), so either can be read.

The mainline side of the comparison is the pristine driver,
`Docs/Modem Stability/qcom_bam_dmux_v6.12.c`.

### 2.1 ⚠️ SMEM item-number trap

`include/soc/qcom/smem.h` contains a long auto-incrementing enum. Item numbers are easy to
guess wrong, and wrong values still compile and still resolve to *a* valid SMEM item.

The numbering **matches mainline** — `SMEM_HW_SW_BUILD_ID = 137 = 0x89`, which is the anchor
to sanity-check against. Verified values:

| Constant | Value | Notes |
| :--- | :--- | :--- |
| `SMEM_SMSM_SHARED_STATE` | **85** | The SMSM state block. Word 1 = `SMSM_MODEM_STATE`; bit 1 = `SMSM_A2_POWER_CONTROL` |
| `SMEM_SMSM_INT_INFO` | 86 | |
| `SMEM_POWER_ON_STATUS_INFO` | **403** | **Not** SMSM, despite looking plausible |
| `SMEM_SMSM_SIZE_INFO` | 419 | |
| `SMEM_SMP2P_APPS_BASE` | 427 | |
| `SMEM_BAM_PIPE_MEMORY` | 468 | |
| `SMEM_NUM_ITEMS` | 476 | |

Legacy SMSM constants (`smsm.h`):

```c
enum { SMSM_APPS_STATE, SMSM_MODEM_STATE };   /* 0, 1 */
#define SMSM_A2_POWER_CONTROL      0x00000002   /* BIT(1) */
#define SMSM_A2_POWER_CONTROL_ACK  0x00000800   /* BIT(11) */
```

**Do not count the enum by hand.** Extract it and print the values:

```sh
A=GitIgnore/android_kernel_zte_msm8916
{ echo '#include <stdio.h>'
  sed -n '41p' $A/include/soc/qcom/smem.h          # SMEM_NUM_SMD_STREAM_CHANNELS
  sed -n '54,167p' $A/include/soc/qcom/smem.h      # the item enum
  echo 'int main(void){printf("%d %d\n", SMEM_SMSM_SHARED_STATE, SMEM_POWER_ON_STATUS_INFO);}'
} > /tmp/smem_enum.c && gcc -o /tmp/smem_enum /tmp/smem_enum.c && /tmp/smem_enum
```

---

## 3. Confirmed Exact Android 4.4.4 Parity

These were checked line-by-line against the Android source and **match**.

### 3.1 Command opcode ordering

`bam_dmux_private.h`:

```c
#define BAM_MUX_HDR_CMD_DATA            0
#define BAM_MUX_HDR_CMD_OPEN            1
#define BAM_MUX_HDR_CMD_CLOSE           2
#define BAM_MUX_HDR_CMD_STATUS          3 /* unused */
#define BAM_MUX_HDR_CMD_OPEN_NO_A2_PC   4
```

Patch 808's `enum { BAM_DMUX_CMD_DATA, BAM_DMUX_CMD_OPEN, BAM_DMUX_CMD_CLOSE,
BAM_DMUX_CMD_STATUS, BAM_DMUX_CMD_OPEN_NO_A2_PC }` — **identical ordering and values.**

This matters: an off-by-one here would silently mis-dispatch every modem command. Android
marks `STATUS` as unused (it falls through to `default:` and the packet is dropped); patch 808
logs it and re-arms the slot, which is harmless.

### 3.2 TX padding

Android (`bam_dmux.c`, `msm_bam_dmux_write`):

```c
hdr->pkt_len = skb->len - sizeof(struct bam_mux_hdr);
if (skb->len & 0x3)
        skb_put(skb, 4 - (skb->len & 0x3));
hdr->pad_len = skb->len - (sizeof(struct bam_mux_hdr) + hdr->pkt_len);
```

Because the header is 8 bytes (a multiple of 4), `(skb->len & 0x3)` after the push equals the
payload length's remainder. The effective pad is therefore `(4 - len % 4) % 4`.

Upstream mainline used `pad = sizeof(u32) - skb->len % sizeof(u32)`, which yields **4** when
`len % 4 == 0` — four phantom bytes on every word-aligned packet. Patch 808's
`(sizeof(u32) - (skb->len % sizeof(u32))) % sizeof(u32)` reproduces Android exactly. **This is
a genuine parity fix, not a cosmetic one.**

### 3.3 Other matches

| Concept | Android 4.4.4 | Patch 808 |
| :--- | :--- | :--- |
| A2 power-collapse flag | `a2_pc_disabled`, set on `OPEN_NO_A2_PC` | `dmux->a2_pc_disabled`, same trigger |
| PC ack toggle | `dmux->pc_ack_state` | same name and semantics |
| Reset guard | `in_global_reset`, aborts TX | `in_teardown`, same purpose |
| SSR hooks | `restart_notifier_cb` + `SUBSYS_BEFORE_SHUTDOWN` / `SUBSYS_AFTER_POWERUP` | `qcom_register_ssr_notifier("mpss")` + `QCOM_SSR_BEFORE_SHUTDOWN` / `QCOM_SSR_AFTER_POWERUP` — correct modern mapping |
| Header magic | `BAM_MUX_HDR_MAGIC_NO 0x33fc` | `BAM_DMUX_HDR_MAGIC 0x33fc` |

---

## 4. Accepted Divergences (deliberate, not defects)

| Topic | Android 4.4.4 | Patch 808 | Assessment |
| :--- | :--- | :--- | :--- |
| Keeping the path alive | `grab_wakelock()` + `vote_dfab()` while `a2_pc_disabled` | Holds a `pm_runtime` reference across `ndo_open`→`ndo_stop`; aborts `runtime_suspend` | Functionally equivalent on this platform; **not** literal parity — do not over-claim it |
| RX re-arm cadence | Adaptive polling, 2950–3050 ms (`POLLING_MIN_SLEEP`/`MAX`, `adaptive_timer_enabled` default off) | Fixed 2000 ms `rx_rearm_work` | Minor; see §7.3 for a caveat |
| Power-collapse handshake | SMSM bits + DFAB clock vote | `qcom_smem_state_update_bits()` on the DTS-provided `pc`/`pc-ack` states | Correct mainline mechanism |
| Bus | SPS/BAM via `bam_ops_if` | `dmaengine` (`qcom_bam_dma`) | Structural; unavoidable |

---

## 5. Defects Found and Fixed

Three real defects were found. All three are now fixed in
`msm89xx/patches/808-bam-dmux-stats.patch`.

### 5.1 DEFECT-1 (correctness) — wrong SMEM item in SSR teardown

`bam_dmux_ssr_teardown_work_func()` cleared the modem's power-collapse request bit by writing
raw SMEM:

```c
states = qcom_smem_get(QCOM_SMEM_HOST_ANY, 403, NULL);   /* WRONG ITEM */
if (!IS_ERR_OR_NULL(states))
        states[1] &= ~BIT(1);
```

The **word and bit indices were correct** (`states[1]` = `SMSM_MODEM_STATE`,
`BIT(1)` = `SMSM_A2_POWER_CONTROL`), but **item 403 is `SMEM_POWER_ON_STATUS_INFO`**, not the
SMSM block. The write was therefore clobbering bit 1 of word 1 of an unrelated SMEM item.

**Fix:**

```c
/*
 * Clear the stale remote PC bit so the SSR restart observes a fresh
 * rising edge. SMEM item 85 is the legacy SMSM shared-state block:
 * word 1 is SMSM_MODEM_STATE, bit 1 is SMSM_A2_POWER_CONTROL.
 * (See android_kernel_zte_msm8916 include/soc/qcom/smem.h + smsm.h.)
 * Item 403 is SMEM_POWER_ON_STATUS_INFO and must not be touched here.
 */
states = qcom_smem_get(QCOM_SMEM_HOST_ANY, 85, NULL);
if (!IS_ERR_OR_NULL(states))
        states[1] &= ~BIT(1);
```

> **Residual caveat:** on mainline these bits are normally managed by the smp2p /
> `qcom_smem_state` driver, so a raw poke bypasses that layer. The `IS_ERR_OR_NULL` guard
> makes the call a no-op if item 85 is not allocated. If SSR behaviour looks wrong later,
> re-examine whether this raw write is needed at all — the `disable_irq`/`enable_irq` resync
> immediately after it may be sufficient on its own.

### 5.2 DEFECT-2 (locking) — `dma_async_issue_pending()` called under a spinlock

The RX path called `dma_async_issue_pending(dmux->rx)` while holding
`spin_lock_irqsave(&dmux->rx_lock, flags)` (IRQs disabled). That call chain is:

```
dma_async_issue_pending()
  -> bam_issue_pending()
     -> spin_lock_irqsave(&bchan->vc.lock)          [bam_dma.c:1145]
        -> bam_start_dma()
           -> pm_runtime_get_sync(bdev->dev)         [bam_dma.c:1032]  <-- CAN SLEEP
```

`pm_runtime_get_sync()` sleeps when the BAM controller is runtime-suspended (it waits on
`dev->power.completion`). It returns without sleeping only on the fast path when the device is
already active. Because `bam_start_dma()` ends with `pm_runtime_put_autosuspend()`, the BAM
**does** autosuspend between bursts — so this bites under bursty RX, which is exactly the
intermittent failure mode that is hardest to diagnose. Upstream mainline calls
`dma_async_issue_pending()` unlocked.

Three sites were affected. All are now unlocked, and each is safe because of existing
discipline:

| Site | Why `dmux->rx` stays valid outside the lock |
| :--- | :--- |
| `bam_dmux_rx_callback()` step 5 | `power_off()` waits on `rx_active_callbacks == 0` before releasing the channel, and the callback increments that counter on entry |
| `bam_dmux_rx_rearm_work_func()` | `power_off()` calls `cancel_delayed_work_sync(&dmux->rx_rearm_work)` *before* releasing the channel |
| `bam_dmux_power_on()` | Runs under `state_lock`, which `power_off()` also takes |

Resulting pattern:

```c
if (rearmed) {
        /*
         * Outside rx_lock: dma_async_issue_pending() may sleep in
         * pm_runtime_get_sync(). power_off() cancels this work
         * synchronously before releasing dmux->rx, so it stays valid.
         */
        dma_async_issue_pending(dmux->rx);
        ...
}
```

### 5.3 DEFECT-3 (deadlock) — `disable_irq()` while holding `state_lock`

`bam_dmux_ssr_teardown_work_func()` held `mutex_lock(&dmux->state_lock)` and then called
`disable_irq(dmux->pc_irq)`.

`pc_irq` is registered as a **threaded** IRQ (`devm_request_threaded_irq(..., bam_dmux_pc_irq,
IRQF_ONESHOT, ...)`), and that handler does `mutex_lock(&dmux->state_lock)` before touching
`pc_state`. `disable_irq()` waits for the in-flight threaded handler to finish. So if a PC edge
arrives while teardown holds the lock, the handler blocks on the mutex while `disable_irq()`
blocks on the handler — **deadlock**.

**Fix:** moved the resync pair after `mutex_unlock`:

```c
        mutex_unlock(&dmux->state_lock);

        /*
         * Resync the irqchip's last_value with the actual line level. This must
         * happen after dropping state_lock: the threaded pc_irq handler acquires
         * that mutex, and disable_irq() waits for that handler to complete.
         */
        disable_irq(dmux->pc_irq);
        enable_irq(dmux->pc_irq);
```

> `bam_dmux_remove()` already had the correct order (`disable_irq()` *then* `mutex_lock()`), so
> it needed no change.

---

## 6. Verification Method

The patch was **regenerated, not hand-edited**, so the hunk line counts are guaranteed correct.

```sh
# 1. Reproduce the OLD patched file from pristine + old patch (proves the baseline is exact)
W=/tmp/p808; rm -rf $W; mkdir -p $W/b/drivers/net/wwan
cp "Docs/Modem Stability/qcom_bam_dmux_v6.12.c" $W/b/drivers/net/wwan/qcom_bam_dmux.c
cd $W/b && patch -p1 < .../808-bam-dmux-stats.patch
diff -q $W/b/drivers/net/wwan/qcom_bam_dmux.c \
        openwrt/build_dir/.../linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c
# -> identical

# 2. Apply the fixes to $W/b/..., then regenerate
mkdir -p $W/a/drivers/net/wwan
cp "Docs/Modem Stability/qcom_bam_dmux_v6.12.c" $W/a/drivers/net/wwan/qcom_bam_dmux.c
cd $W && diff -up a/drivers/net/wwan/qcom_bam_dmux.c b/drivers/net/wwan/qcom_bam_dmux.c \
  | sed -e '1s/^--- .*/--- a\/drivers\/net\/wwan\/qcom_bam_dmux.c/' \
        -e '2s/^+++ .*/+++ b\/drivers\/net\/wwan\/qcom_bam_dmux.c/' > /tmp/808-new.patch

# 3. Prove the semantic delta is exactly the intended fixes
#    Apply old and new patches to separate pristine copies and diff the RESULTS
diff -u /tmp/pv1/.../qcom_bam_dmux.c /tmp/pv2/.../qcom_bam_dmux.c
# -> only the four hunks in §5
```

`diff -up` is required — plain `diff -u` drops the trailing function-context names from the
hunk headers and makes the patch much harder to read.

**Result:**

| | Before | After |
| :--- | ---: | ---: |
| Size | 45 649 B | 46 221 B |
| Hunks | 29 | 29 |
| `patch -p1 --dry-run` vs pristine | clean | clean |
| Semantic delta vs old patch | — | exactly §5.1–§5.3 |

The original patch is git-tracked, so it is recoverable with
`git checkout HEAD -- msm89xx/patches/808-bam-dmux-stats.patch`.

---

## 7. Known Issues Deliberately Left Unfixed

These were identified during the audit but are **outside the scope the user approved for this
round**. They are recorded here so they are not lost.

### 7.1 `a2_pc_disabled` is cleared too eagerly — *semantic divergence*

Patch 808 clears `dmux->a2_pc_disabled = false` inside `bam_dmux_power_off()`, i.e. on **every**
modem power-collapse.

Android clears it only in the SSR notifier (`bam_dmux.c:2185`, inside `restart_notifier_cb`),
never on a normal power-down.

**Impact:** if the modem sends `OPEN_NO_A2_PC` once at bring-up rather than on every power
cycle, the flag is lost after the first modem sleep and the no-A2-PC protection disappears.
If the modem re-sends it on each re-open, the behaviour is self-correcting and this is benign.
**Worth confirming empirically from the `cmd_open_no_a2_pc` telemetry counter versus
`pc_irq_count`.**

### 7.2 Use-after-free read in `bam_dmux_remove()` — *low impact*

`bam_dmux_runtime_suspend()` Guard 0 reads `dmux->netdevs[i]->flags`:

```c
for (i = 0; i < BAM_DMUX_NUM_CH; i++) {
        if (dmux->netdevs[i] && (dmux->netdevs[i]->flags & IFF_UP))
                return -EBUSY;
}
```

`bam_dmux_remove()` calls `bam_dmux_runtime_suspend(dev)` **after**
`unregister_netdevice_many(&list)`. The netdevs are freed at that point
(`netdev_run_todo()` → `kobject_put()` → `netdev_release()` → `kvfree(dev)`,
`net/core/net-sysfs.c:2024`), and `dmux->netdevs[]` is never NULLed — so the pointers dangle.

Only reachable on driver unbind, which for a built-in platform driver essentially never
happens. A fix would be to NULL the `netdevs[]` entries after unregistering, or skip Guard 0
when `in_teardown` is set.

### 7.3 The RX re-arm timer cannot recover a stuck slot — *limited value*

`bam_dmux_rx_rearm_work_func()` only re-arms slots in `BAM_DMUX_RX_SLOT_FREE`. In steady state
every slot is `SUBMITTED`, so the 2 s timer is effectively a no-op and **cannot** recover a slot
stuck in `SUBMITTED` — which is the failure mode it exists to catch. To be a real safety net it
needs a "`SUBMITTED` for longer than N ms → force back to `FREE`" path.

---

## 8. Build

```sh
./build.sh build hmu05
```

`build_target()` → `ensure_prepared()` → `sync_bsp()` does `rm -rf` + `cp -a` of `msm89xx/`
into `openwrt/target/linux/`, so both corrected `808-bam-dmux-stats.patch` and the new
`819-rpmsg-char-guard-null-eptdev.patch` are picked up automatically. The `openwrt/` tree
stays clean (`git status --porcelain openwrt/` empty) — per the standing rule that kernel
patches live in `msm89xx/` (BSP) or `openwrt-patches/`, never in the OpenWrt tree.

### 8.1 Kernel result — clean

The corrected driver and the rpmsg guard both compiled without warnings:

| Artifact | Size | Built |
| :--- | ---: | :--- |
| `drivers/net/wwan/qcom_bam_dmux.o` | 181 680 B | 2026-09-19 14:33 |
| `drivers/net/wwan/qcom_bam_dmux.ko` | 202 888 B | 2026-09-19 14:34 (→ `kmod-bam-dmux`) |
| `drivers/rpmsg/rpmsg_char.o` | 75 400 B | 2026-09-19 14:34 |

No `.rej` / `.orig` files anywhere in the kernel tree, so both patches applied exactly.

### 8.2 ⚠️ Build-blocking packaging conflict (pre-existing, NOT caused by patch 808)

The first build attempt failed at `package/install`:

```
ERROR: modemmanager-1.24.0-r8: trying to overwrite etc/hotplug.d/tty/25-modemmanager-tty  owned by base-files-1711~f5dae5ece4
ERROR: modemmanager-1.24.0-r8: trying to overwrite etc/hotplug.d/wwan/25-modemmanager-wwan owned by base-files-1711~f5dae5ece4
```

**Cause.** Commit `107d3b9` — *"msm89xx: implement fast-recovery hotplug filters and re-enable
ATS time refresh"* (2026-09-12 00:25) — added modified copies of the upstream ModemManager
hotplug scripts to `msm89xx/base-files/etc/hotplug.d/{tty,wwan}/`. The target `base-files`
package ships them, and the upstream `modemmanager` package ships the **same two paths**. apk
refuses the overwrite.

**Evidence it predates this session:**

| Evidence | Value |
| :--- | :--- |
| Commit that added the files | `107d3b9`, 2026-09-12 00:25 |
| Last successful build (manifest mtime) | 2026-09-11 03:33 — **before** that commit |
| Only file modified in this session | `msm89xx/patches/808-bam-dmux-stats.patch` (a kernel patch) |

A kernel patch cannot affect package file ownership, and the kernel in fact built cleanly
(§8.1). **The tree has not built successfully since 2026-09-12.**

**Fix — override via `openwrt-overlay/`.** The repo already uses this mechanism to override a
*tracked* feed file, proven by:

```sh
$ git -C openwrt/feeds/packages status --short
 M net/modemmanager/files/lib/netifd/proto/modemmanager.sh
```

Both files were moved (git-tracked renames, modes preserved at `100755`):

```
msm89xx/base-files/etc/hotplug.d/tty/25-modemmanager-tty
  -> openwrt-overlay/feeds/packages/net/modemmanager/files/etc/hotplug.d/tty/25-modemmanager-tty

msm89xx/base-files/etc/hotplug.d/wwan/25-modemmanager-wwan
  -> openwrt-overlay/feeds/packages/net/modemmanager/files/etc/hotplug.d/wwan/25-modemmanager-wwan
```

Now only `modemmanager` owns those paths, the project's SMD AT-port filters are preserved, and
the collision is gone. The empty `msm89xx/base-files/etc/hotplug.d/{tty,wwan}/` directories
were removed.

**Why the rebuild picks it up.** `package/base-files/Makefile` declares
`PKG_FILE_DEPENDS:=$(PLATFORM_DIR)/`, and `include/package.mk:124` folds
`$(CURDIR) $(PKG_FILE_DEPENDS)` into an MD5 that names the `.prepared_*` stamp. Removing files
from `target/linux/msm89xx/` changes that hash, so `base-files` rebuilds and its stale
`.pkgdir` entries disappear. The same applies to `modemmanager`, whose package directory is
overwritten by the overlay.

> **Latent ordering issue (not fixed):** `scripts/openwrt-prepare.sh` `main()` runs
> `install_overlay` *before* `update_feeds`. On a **fresh** prepare, if `feeds update -a`
> actually re-pulls `feeds/packages`, it would overwrite overlay files. It survives today only
> because `update_feeds` skips feeds that are already installed. `sync_bsp()` re-applies the
> overlay on every subsequent build, so the tree self-heals.

---

## 9. Pending Work

1. **Flash and soak.** Validate the corrected driver: watch for `BUG: sleeping function called
   from invalid context` (would mean a `dma_async_issue_pending` site was missed) and confirm
   the 15-minute data stall does not return.
2. **Read `/sys/devices/.../rx_telemetry`** under load. Key counters:
   `rx_slots_submitted` should hover at 32; `rx_duplicate_submissions` and
   `rx_negative_queue_count` should stay 0.
3. **Settle §7.1 empirically** — compare `cmd_open_no_a2_pc` against `pc_irq_count` to learn
   whether the modem re-sends `OPEN_NO_A2_PC` per power cycle.
4. **Decide on §7.2 and §7.3.**
5. *(carried over from Doc 137 §8)* After flashing the patched kernel, capture DIAG during an
   LTE attach attempt.
6. *(carried over from Doc 138 §6)* Verify NV 6828/1877 on the HMU05.

---

## 10. Artifacts

| Artifact | Path |
| :--- | :--- |
| Corrected patch | `msm89xx/patches/808-bam-dmux-stats.patch` (46 221 B) |
| Pristine mainline driver (comparison base) | `Docs/Modem Stability/qcom_bam_dmux_v6.12.c` |
| Android 4.4.4 driver (parity reference) | `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux.c` |
| Android 4.4.4 command opcodes | `GitIgnore/android_kernel_zte_msm8916/drivers/soc/qcom/bam_dmux_private.h` |
| Android 4.4.4 SMSM constants | `GitIgnore/android_kernel_zte_msm8916/include/soc/qcom/smsm.h` |
| Android 4.4.4 SMEM item numbers | `GitIgnore/android_kernel_zte_msm8916/include/soc/qcom/smem.h` |
| Moved hotplug filters (was `msm89xx/base-files/`) | `openwrt-overlay/feeds/packages/net/modemmanager/files/etc/hotplug.d/{tty,wwan}/` |

### 10.1 Repo state after this session

```sh
$ git status --short
 M msm89xx/patches/808-bam-dmux-stats.patch
R  msm89xx/base-files/etc/hotplug.d/tty/25-modemmanager-tty
     -> openwrt-overlay/feeds/packages/net/modemmanager/files/etc/hotplug.d/tty/25-modemmanager-tty
R  msm89xx/base-files/etc/hotplug.d/wwan/25-modemmanager-wwan
     -> openwrt-overlay/feeds/packages/net/modemmanager/files/etc/hotplug.d/wwan/25-modemmanager-wwan
?? msm89xx/patches/819-rpmsg-char-guard-null-eptdev.patch
```

`openwrt/` is gitignored (`.gitignore:5`), so the prepared tree is not tracked — the BSP and
the overlay are the sources of truth.

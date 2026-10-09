# HMU05 — the dead `msm_subsys` SSR restart lever (fix) and patch 850 (bam-dmux re-attach)

**Status:** ★ **VERIFIED LIVE (2026-10-10)** — patch 826's fix was deployed to HMU05 and the inflated-refcount restart now cycles the modem and heals the count; patch 850 built into the same kernel
**Date:** 2026-10-10
**Board:** HMU05 (`hmu05,250605v0s`) — MSM8916 / Snapdragon 410 · device `192.168.8.1`
**Branch:** `staging-main`
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §112.171 (and §112.156/§112.157 for the
pre-emptive SSR this lever serves)
**Patches:** `msm89xx/patches/826-remoteproc-q6v5-mss-msm-subsys-restart.patch` (modified),
`msm89xx/patches/850-bam-dmux-reattach-on-already-open.patch` (new)

> **In one line.** The debugfs lever the SSR watchdog uses to restart the modem
> (`/sys/kernel/debug/msm_subsys/modem`) was **silently dead** whenever `rproc->power > 1` — a
> refcounted `rproc_shutdown()` plus a `rproc_boot()` that bumps the count even when it short-circuits.
> The fix normalises the count before shutdown. Separately, a **new defensive patch 850** makes
> `bam_dmux_cmd_open()` re-attach an already-open channel's netdev instead of returning early.

---

## 1. Why this exists

The mitigation for the ~900 s modem fatal is pre-emptive/reactive **SSR** (subsystem restart), driven by
`modem-bearer-watchdog`. Its restart primitive is patch **826**'s node
`/sys/kernel/debug/msm_subsys/modem`: writing `restart` shuts the modem subsystem down and powers it back
up from a workqueue, mirroring Android's `subsystem_restart` framework.

While debugging the post-SSR data path, the lever was observed to be a **no-op**: the write succeeded,
but the modem never cycled and `restart_count` never advanced. A recovery path that silently does nothing
is worse than no recovery path at all, so it had to be root-caused.

---

## 2. The dead lever — root cause

`q6v5_restart_work()` (`drivers/remoteproc/qcom_q6v5_mss.c`) calls `rproc_shutdown()`, which is
**reference-counted** (`drivers/remoteproc/remoteproc_core.c`):

```c
/* if the remote proc is still needed, bail out */
if (!atomic_dec_and_test(&rproc->power))
        goto out;
```

`rproc->power` is incremented only by `rproc_boot()` — **including the short-circuit case**:

```c
/* skip the boot or attach process if rproc is already powered up */
if (atomic_inc_return(&rproc->power) > 1) {   /* line 1935 */
        ret = 0;
        goto unlock_mutex;
}
```

So a **stray duplicate `start`** written to `.../remoteproc0/state` inflates the count (1→2). The next
`restart` decrements it only (2→1), the rproc stays RUNNING, and the lever bails:

```
msm_subsys: restarting 4080000.remoteproc
msm_subsys: 4080000.remoteproc still up after shutdown (power reference held elsewhere)
```

`rproc->power` is a **public** field of `struct rproc` (`include/linux/remoteproc.h:557`), and
`rproc_shutdown()` requires the state to be `RUNNING`/`ATTACHED` before it will stop.

### 2.1 Reproduction (live, before any code change)

On HMU05 (`staging-main`):

| step | command | observed |
| :-- | :-- | :-- |
| baseline | `cat .../msm_subsys/modem` | `restart_count = 1`, state `running` |
| inflate | `echo start > .../remoteproc0/state` | returns 0; `rproc->power` 1→2 (no boot) |
| trigger | `echo restart > .../msm_subsys/modem` | returns 0; **`restart_count` stays 1**, state stays `running`, `"still up after shutdown"` |
| recover | `echo stop`×2, then `echo start` | first `stop` drains 2→1 (no stop); second `stop` stops; `start` boots → `running` |

A clean `restart` at count==1 works fully: `stopped remote processor` → `Booting fw image mba.mbn` →
`MBA booted … loading mpss` → `SSR powerup: successfully reinitialized BAM channels` → `CMD_OPEN` on
channels 0..7 → `wwan0qmi0 attached`, and `restart_count` advances.

---

## 3. Fix — patch 826

Collapse the count to a single reference before shutting down, so the shutdown owns the last reference and
really stops the subsystem; the re-boot below then leaves the count healed at 1:

```c
if (rproc->state == RPROC_RUNNING || rproc->state == RPROC_ATTACHED) {
        /*
         * rproc_shutdown() is reference-counted: it powers the
         * subsystem off only when the last user drops its reference.
         * rproc_boot() bumps that count even when it short-circuits on
         * an already-running rproc, so a stray duplicate "start" on
         * .../remoteproc0/state inflates it and the shutdown then
         * returns success without stopping anything -- the restart
         * would silently no-op and the pre-emptive SSR watchdog would
         * never cycle the modem.  A restart is a deliberate, privileged
         * reset, so collapse the count to a single reference first;
         * shutdown then owns the last one and really stops the
         * subsystem (the re-boot below leaves it healed at 1).
         */
        atomic_set(&rproc->power, 1);

        ret = rproc_shutdown(rproc);
        ...
```

A restart is a **deliberate, privileged reset**, so discarding stale extra references is the intended
semantics; the count self-heals (a run that starts at 2, 3, … returns to 1 after one restart). The
pre-existing `"still up after shutdown"` warning is retained as a safety net.

**Validation.** The patch was re-hunked (`@@ -1981,6 +1988,146 @@`, +8 lines) and `patch --dry-run`
verified against a **pristine** upstream `qcom_q6v5_mss.c` (the toolchain `build_dir` copy, which carries
no target patches) — it applies cleanly; the fix was then confirmed present in the freshly prepared
kernel tree (`atomic_set(&rproc->power, 1)` at line 2044).

---

## 4. Patch 850 — bam-dmux re-attach on already-open (new, defensive)

`bam_dmux_cmd_open()` (`drivers/net/wwan/qcom_bam_dmux.c`) returned early when the channel bit was already
set, leaving the netdev detached:

```c
if (__test_and_set_bit(hdr->ch, dmux->remote_channels)) {
        dev_warn(dmux->dev, "Channel already open: %u\n", hdr->ch);
        return;                       /* <-- netdev left detached */
}
if (netdev)
        netif_device_attach(netdev);
```

Patch 850 keeps the warning but always runs the attach / registration path:

```c
if (__test_and_set_bit(hdr->ch, dmux->remote_channels))
        dev_warn(dmux->dev, "Channel already open: %u\n", hdr->ch);

if (netdev) {
        netif_device_attach(netdev);
} else {
        schedule_work(&dmux->register_netdev_work);
}
```

**Why it is safe.** `netif_device_attach()` is idempotent (no-op if already attached, re-enables if
detached), and `bam_dmux_register_netdev_work()` iterates `for_each_set_bit(ch, remote_channels)` and
`continue`s for any channel that already has `dmux->netdevs[ch]` — so it cannot double-register.

**Reachability.** `remote_channels` is zeroed only by `bam_dmux_power_off()`, an **AP-side SSR
operation**. A modem-internal restart that the AP does not observe as an SSR re-issues `CMD_OPEN` for
channels the AP still believes are open → the early return → a potentially detached netdev and a dead
data path with only a WARN. Not observed live on the current build (clean SSRs zero the bit first), so
this is **defensive**.

**Validation.** Applies cleanly to both the post-833 driver state (`@@ -1197,10 +1197,17 @@`) and
pristine upstream (offset −652). `bam_dmux_cmd_open` is untouched by patches 808–833, so the context is
stable regardless of apply order.

---

## 5. Option 1 vs Option 2 (the two named SSR log strings)

While root-causing, two log strings were investigated:

- `Unsupported device: at least a QMI port is required` — ModemManager 1.24.0
  `mm-plugin-qcom-soc.c` `create_modem()`. This is MM's **normal first pass**: the transient wwan control
  ports are released before the persistent bam-dmux netdevs, so a modem is briefly attempted with no QMI
  port; MM retries event-driven (`mm-base-manager.c:496`) and succeeds.
- `Channel already open: %u` — `bam_dmux_cmd_open()`. Did **not** fire on a clean crash-restart.

**Neither breaks the data session.** Decision:

| Option | What | Verdict |
| :-- | :-- | :-- |
| **1** — patch ModemManager | silence / suppress the qcom-soc race | **do NOT** — cosmetic (the retry already works), upstream-divergent, risks a QMI-less modem object |
| **2** — patch `bam_dmux_cmd_open` | re-attach on already-open | **do** (patch 850) — the only option that removes a genuine failure mode, at low risk |

---

## 6. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Lever restarts the modem | `restart_count`++ + a real stop/boot | works at `power==1`; **silent no-op at `power>1`** | **MET (control) / NOT MET (inflated)** |
| Fix: robust to an inflated count | restart regardless of count | `atomic_set(&rproc->power, 1)` before `rproc_shutdown()`; **live: inflated restart now cycles the modem and the count heals** | **★★★★★ MET — LIVE-VERIFIED 2026-10-10** |
| Patch 850 closes the detached-netdev gap | re-attach on already-open | built into the same kernel; idempotency read in source | **MET (code); defensive (failure mode not observed live)** |
| Option 1 (patch ModemManager) | remove the log line | not done — cosmetic, upstream-divergent | **NOT MET (by decision)** |

### 6.1 Live verification (2026-10-10, HMU05)

The rebuilt `qcom_q6v5_mss.ko` was deployed to `/lib/modules/6.12.94/` and loaded by reboot. The fix is
present in the **stripped** installed module — disassembly of `q6v5_restart_work` shows
`str w0, [x19, #768]` (store `1` to `rproc->power`) immediately before `bl rproc_shutdown`:

```
  38: b.ne  84          ; state not RUNNING/ATTACHED
  3c: mov   w0, #0x1
  40: str   w0, [x19, #768]   ; atomic_set(&rproc->power, 1)
  44: mov   x0, x19
  48: bl    rproc_shutdown
```

A/B against the pre-fix module:

| Step | Pre-fix module | Fixed module |
| :-- | :-- | :-- |
| clean `restart` (`power==1`) | count 1→2 ✓ | count 0→1 ✓ |
| **inflated** (stray `start`, `power==2`) + `restart` | **count frozen at 2, `still up`** ✗ | **count 1→2 ✓** |
| repeat inflated + `restart` (self-heal) | — | count 2→3 ✓ |
| plain `restart` after | — | count 3→4 ✓ |

dmesg logged four `msm_subsys: restarting 4080000.remoteproc` lines with **no** `still up after shutdown`
warning, and AP uptime ran continuously (no reset).

### 6.2 Deployment note (why the first attempt looked like a failure)

`./build.sh kernel hmu05` runs only `make target/linux/compile` — it **compiles** the in-tree modules but
does **not install** them into the root tree (`root-msm89xx`). The freshly built module landed in
`build_dir/.../linux-6.12.94/drivers/remoteproc/qcom_q6v5_mss.ko` (post-fix) while
`build_dir/.../root-msm89xx/lib/modules/.../qcom_q6v5_mss.ko` remained the **Jun 29** pre-fix copy — and
that stale copy is what the device was running, so the first on-device test reproduced the *old* behaviour.
The full firmware build (`build.sh build hmu05` → `make -j$(nproc)`) **does** run the install step, so a
released image carries the fix; for a quick iteration, deploy the built `.ko` directly:

```bash
aarch64-linux-gnu-strip --strip-unneeded qcom_q6v5_mss.ko
scp qcom_q6v5_mss.ko root@<dev>:/lib/modules/6.12.94/qcom_q6v5_mss.ko   # name the file explicitly
```

> **scp trap.** Copying `foo.ko` to a *directory* keeps the local basename (`fresh_q6v5_mss.ko`) and does
> not replace `qcom_q6v5_mss.ko` — always name the destination file.

---

## 7. SOP compliance statement

Follows the Dual-Firmware Comparative Workflow (`133 §1`) and the ledger's `197 §1` requirements:

- **Ground truth first.** The failure was **reproduced on the live device** before any code change
  (§2.1), and the refcount semantics were **read from `remoteproc_core.c`** — not inferred from the log.
- **Pristine verification.** The 826 fix was validated to apply against a **pristine** upstream
  `qcom_q6v5_mss.c` (toolchain `build_dir` copy, no target patches) with `patch --dry-run`, and the 850
  patch likewise against pristine and the post-833 state.
- **One change at a time.** 826 (q6v5) and 850 (bam_dmux) are independent files; the analysis of the two
  MM/driver strings is separated from the lever fix.
- **No baseband, no re-signing.** No `modem.bNN` was written; no cryptographic re-signing was involved.
- **Verified, not asserted.** The fix was confirmed in the **stripped installed module** by disassembly,
  deployed to HMU05, and A/B-tested against the pre-fix module: the inflated-count restart now cycles the
  modem and the count self-heals (§6.1). The stale-artifact trap that made the first attempt look like a
  failure is recorded in §6.2 so it is not repeated.

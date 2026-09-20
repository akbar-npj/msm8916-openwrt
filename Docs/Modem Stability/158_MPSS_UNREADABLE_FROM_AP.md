# 158 — THE MODEM'S MEMORY IS UNREADABLE FROM THE AP: mpss is TrustZone/XPU-protected, so the "live mpss reader" cannot exist. Corrects Doc 153.

**Date:** 2026-09-21. Times are **AP `/proc/uptime` seconds**.

**Status:** **a negative result, measured and source-confirmed.** The instrument Doc 153 named as
"the next instrument" was built, deployed and tested. It does not work, and it cannot be made to work
while the modem is running: every read of mpss from the AP raises a **synchronous external abort**,
because mpss is assigned to the modem's VMID by **TrustZone**, not merely marked `no-map` in the kernel.
The "live mpss observation" line of work is **closed**.

**Consequence for the corpus:** Doc 153's inference that "a `nomap`-capable kernel module is the next
instrument" is **half right and misleading**. A kernel module *can* `ioremap` the region (that part is
true — the mapping succeeds), but the **access** aborts. `ioremap` success is not permission to read.

---

## 1. Why this doc exists

Doc 153 §5 established that `/dev/mem` cannot reach mpss (`read()` → `EFAULT`, `mmap()` → `SIGBUS`) and
concluded that the way around it was a kernel module that `ioremap`s the region directly. That was
recorded as the next instrument for the ~902 s fatal: the coredump is a *post-mortem* snapshot taken
after the AP has already stopped the modem, so it cannot show the **transition**, and the only way to
watch the transition was assumed to be a live read window.

This doc records the attempt to build that window, and why it is impossible.

## 2. SOP compliance

| SOP element | this session |
| :-- | :-- |
| Dual-firmware comparative protocol | **N/A** — no firmware built, patched or transplanted. The deployed module was hash-checked before and after. |
| Ground truth from source, not narrative | **Applied, and it is what explains the result.** The corpus said "a kernel module can `ioremap` a `nomap` region" and stopped there. The driver source shows *why* that is not enough: `q6v5_xfer_mem_ownership()` (`qcom_q6v5_mss.c:422-450`) issues a **`qcom_scm_assign_mem()`** to move the region between `QCOM_SCM_VMID_HLOS` (the AP) and `QCOM_SCM_VMID_MSS_MSA` (the modem). The protection is enforced by TrustZone, not by the kernel's page tables. |
| Read the definition, not the name | **Applied.** "`nomap`" is a *kernel memory-management* attribute; it says nothing about whether the bus will answer an AP access. Treating the two as the same thing is what made the instrument look feasible. |
| Check the call sites, not just the body | **Applied.** `q6v5_xfer_mem_ownership()` returns early when `!qproc->need_mem_protection` (`:429-430`), so on a platform without memory protection it would be a no-op — the abort is therefore evidence that **this** platform does have it configured. |
| Measure on the live device, before and after | **Applied.** The module was built, loaded on the live device, and exercised. The abort is measured, not inferred. The device was rebooted afterwards and verified clean. |
| Minimal, reversible change | The module is out-of-tree and was never installed; a reboot removes it. It touched no tracked file. |
| Record what was done, the result, and what is next | this doc, §6–§7. |
| Never classify a fault by its `file:line` | N/A. |

## 3. What was built

`evidence/158_mpss_unreadable/mpss_mem.c` (+ `Makefile`) — a small out-of-tree module:

* module parameters `base` (default `0x86800000`) and `size` (default `0x5500000`), so the same module
  can be pointed at any reserved region without a rebuild;
* `ioremap(base, size)` at init, `iounmap` at exit;
* one read-only (`0444`) debugfs file whose **file offset is an offset within the region**, so
  `dd if=/sys/kernel/debug/mpss_mem bs=4096 skip=N count=1` reads a window;
* reads use 32-bit `readl()` into a 64 KiB `kmalloc`'d bounce buffer — Device-nGnRE semantics, because
  a cacheable mapping of memory the modem may still be writing would return stale data.

The region and the VA convention were taken from the device, not assumed:

```
/sys/firmware/devicetree/base/reserved-memory/mpss@86800000
    reg    = <0x0 0x86800000 0x0 0x05500000>     (85 MiB)
    no-map  present

AP physical = elf_va - 0x39800000
offset      = elf_va - 0xC0000000        (0x39800000 + 0x86800000 = 0xC0000000)
```

Cross-checked against the captured coredumps: the ERR_FATAL "last fatal" record at ELF va `0xC35B1280`
maps to phys `0x89DB1280`, i.e. offset `0x035B1280`, inside the region.

Build and load both succeeded:

```
insmod rc=0
[  468.310938] mpss_mem: window 0x86800000..0x8bcfffff (offset 0 == modem va 0xc0000000)
              at /sys/kernel/debug/mpss_mem
-r--r--r--    1 root     root             0 ... /sys/kernel/debug/mpss_mem
```

## 4. The result: a synchronous external abort

The very first read — offset **0**, 16 bytes — aborted. Five aborts were logged in total
(`evidence/158_mpss_unreadable/mpss_abort_capture.txt`):

```
[  469.330057] Internal error: synchronous external abort: 0000000096000010 [#1] PREEMPT SMP
[  469.581246] Internal error: synchronous external abort: 0000000096000010 [#2] PREEMPT SMP
[  475.604726] Internal error: synchronous external abort: 0000000096000010 [#3] PREEMPT SMP
[  475.851628] Internal error: synchronous external abort: 0000000096000010 [#4] PREEMPT SMP
[  484.917046] Internal error: synchronous external abort: 0000000096000010 [#5] PREEMPT SMP

CPU: 2 UID: 0 PID: 17512 Comm: dd Tainted: G   M  D    O       6.12.94 #0
pc : mpss_read+0xc0/0x238 [mpss_mem]
lr : full_proxy_read+0x5c/0xa8
x0 : ffff800090000000                     <- the ioremap'd mpss base
Code: 910c0382 cb080027 f9400040 8b050000 (b9400000)   <- `ldr w0, [x0]`, the readl()
```

`x0 = ffff800090000000` is the ioremap'd address of physical `0x86800000`, and the faulting
instruction is the 32-bit load — i.e. **the read itself**, not the mapping, not the copy-back. The
kernel handled each abort as an MCE, killed the faulting `dd`, tainted the kernel (`[M]=MACHINE_CHECK`)
and **kept running**; it did not panic.

The fault address is the *first* byte of the region, so this is not a hole or a partially-backed
region: **the whole of mpss refuses AP access while the modem owns it.**

## 5. Why — and what it corrects

`q6v5_xfer_mem_ownership()` (`qcom_q6v5_mss.c:422-450`):

```c
	if (local) {
		next[perms].vmid = QCOM_SCM_VMID_HLOS;      /* the AP */
		next[perms].perm = QCOM_SCM_PERM_RWX;
	}
	if (remote) {
		next[perms].vmid = QCOM_SCM_VMID_MSS_MSA;   /* the modem */
		next[perms].perm = QCOM_SCM_PERM_RW;
	}
	return qcom_scm_assign_mem(addr, ALIGN(size, SZ_4K), current_perm, next, perms);
```

and its call sites:

| site | direction |
| :-- | :-- |
| `:1149-1150`, `:1508-1509` | mpss handed **to the modem** (`local=false, remote=true`) |
| `:1403-1404` | mpss taken **back by the AP** (`local=true, remote=false`) — the crash/dump path |
| `:1547-1550` | taken back again before a later read |
| `:1440`, `:1555` | `memremap(mpss_phys + offset, …, MEMREMAP_WC)` — **the AP reads mpss only after taking ownership** |

So the region is protected by an **SCM (TrustZone) memory-assignment call**, and while the modem is
running the AP is not in the permitted VMID list. The AP's access is denied **in hardware**, at the
interconnect, which is exactly what a synchronous external abort is.

**This also re-reads Doc 153's `/dev/mem` result.** Doc 153 recorded `read()` → `EFAULT` and `mmap()` →
`SIGBUS` and attributed them to the region being `no-map`. They are better explained as the **same
hardware abort** arriving through two different userspace paths:

* `mmap()` + a load → the abort is delivered to the faulting process as **SIGBUS**;
* `read()` → the kernel's copy loop takes the abort, `copy_to_user()` fails, and the syscall returns
  **EFAULT**.

So the `no-map` attribute is *not* what stops us, and neither is `CONFIG_STRICT_DEVMEM`. Both were
red herrings; there is no software route around a TrustZone assignment.

## 6. Collateral: the module could not be unloaded, and debugfs wedged

This is a second, independent hazard worth recording.

After the aborts, `rmmod mpss_mem` failed (`rc=255`, no message) and `/sys/module/mpss_mem/refcnt`
read **`-1`**. From then on, **any `stat()` of the debugfs file blocked in `D` state** — two `ls -l`
processes were stuck, and `ls /sys/kernel/debug/` hung too. The rest of the system was unaffected: the
soak kept running, the default route stayed up, and ping worked.

Leading explanation (not proven): the first `rmmod` — the one inside the ssh command that timed out at
30 s — had entered `mpss_mem_exit()` and blocked inside `debugfs_remove()` →
`__debugfs_de_remove()`'s wait for the file's `active_users` count to drain. That count never reached
zero, so the module stayed in the going-away state (`refcnt -1`), the dentry removal stayed in
progress, and later lookups of that dentry blocked behind it. The candidate for the undrained
reference is a `dd` process killed by the abort while it held a `debugfs_file_get()` reference. This
was **not** established; what is established is the observable state and that **a reboot clears it
completely** (verified).

A reboot was performed and the device came back clean: module absent, production `bam_dmux` module
`eca269f10a685a83a8b679bc7be38d1d`, default route up, `5/5` ping, the coredump watcher autostarted
from `/etc/rc.local`, and the soak restarted.

## 7. What this means, and what is left

**Closed:** a live read window on mpss. It cannot be built, because the AP is not permitted to touch
the region while the modem owns it, and the permission is granted only by an SCM call that the
remoteproc driver makes during modem start/stop.

**Still available, and now clearly the only route:**

1. **The coredump.** The AP *does* read mpss — in `q6v5_mba_reclaim()`/the dump path, *after*
   `qcom_scm_assign_mem()` hands ownership back. That is a consistent, coherent snapshot, and it is
   taken while the modem is halted. It is the instrument; there is no better one.
2. **`rpmring`.** The RPM log ring is in **SRAM**, which is a normal reserved region, so `/dev/mem`
   `mmap` works and the live RPM log remains available. This is why that one tool works and this one
   cannot — the difference is TrustZone, not `no-map`.
3. **A kernel module that itself performs the ownership transfer.** Theoretically possible — call
   `qcom_scm_assign_mem()` for a window, read it, hand it back. **Not attempted and not recommended:**
   it would race the remoteproc driver's own ownership bookkeeping (`mpss_perm`), and a bug there is a
   way to hand the modem memory that the AP is still using. The cost/benefit is poor when the coredump
   already exists.

**So the "watch the fatal develop" idea is dead.** The fatal's transition can only be studied
post-mortem, from the coredump, or indirectly from the RPM log and the AP-side telemetry.

## 8. Established vs not established

**Established (measured / source-verified):**

1. `mpss@86800000` is `no-map`, base `0x86800000`, size `0x05500000` (85 MiB), read from the device's
   live device tree. §3.
2. A kernel module **can** `ioremap` the region — `ioremap()` returned a valid mapping and the debugfs
   file was created. §3.
3. **Every access to that mapping aborts**, including the very first byte, with
   `synchronous external abort: 0000000096000010`, at the 32-bit load inside `mpss_read()` (`pc :
   mpss_read+0xc0`, `x0 = ffff800090000000`). Five occurrences, `#1`–`#5`. §4.
4. The kernel treats it as an MCE, kills the faulting process, taints the kernel and **continues**; it
   does not panic. §4.
5. mpss ownership is transferred with `qcom_scm_assign_mem()` between `QCOM_SCM_VMID_HLOS` and
   `QCOM_SCM_VMID_MSS_MSA` (`qcom_q6v5_mss.c:422-450`), and the driver only `memremap`s mpss *after*
   taking ownership back (`:1403-1404`, `:1440`, `:1547-1555`). §5.
6. Therefore the abort is a **hardware/TrustZone** refusal, not a kernel policy one. §5.
7. Doc 153's `/dev/mem` `EFAULT`/`SIGBUS` are the same abort by two userspace paths, not a
   `no-map`/`CONFIG_STRICT_DEVMEM` effect. §5.
8. After the aborts the module could not be unloaded (`rmmod` rc=255, `refcnt -1`) and `stat()` of the
   debugfs file blocked in `D` state; a reboot cleared everything. §6.
9. The device was otherwise healthy throughout, and after the reboot the production module, data plane,
   coredump watcher and soak were all verified good. §6.

**Not established:**

* **Why the debugfs file's active-user count did not drain**, and hence the exact mechanism of the
  `D`-state wedge. §6 — the candidate is a `dd` killed by the abort while holding a
  `debugfs_file_get()` reference.
* **Whether an AP access would succeed while the modem is halted but before the driver's own
  ownership transfer.** Expected yes (that is the coredump's regime), but not measured directly with
  this module, and it would add nothing.
* **Whether a module that performs its own `qcom_scm_assign_mem()` window could work.** Not attempted
  (see §7.3).

## 9. Artifacts

`Docs/Modem Stability/evidence/158_mpss_unreadable/`

| file | what |
| :-- | :-- |
| `mpss_mem.c` / `Makefile` | the module that was built and tested (out-of-tree; never installed) |
| `mpss_abort_capture.txt` | the five aborts, the module state, the `D`-state processes, the DT node |

## 10. One line

**The modem's memory is not hidden from the AP by a kernel policy that a module can bypass — it is
assigned to the modem's VMID by TrustZone, so the AP's every access aborts in hardware, and the
"live mpss reader" cannot exist.**

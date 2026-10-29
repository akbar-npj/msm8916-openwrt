# WCN36XX / mifib-v12 — kernel memory corruption and spontaneous reboot

**Status: OPEN — under investigation (soak running).** First observed 2026-10-29 on
`mifib-v12` (`HM-MIFIB-V12`, 25.12.6-r2, kernel 6.12.112, backports 6.18.55).

This document records everything known so far about a **repeating kernel memory
corruption** that rebooted the unit once (panic) and has produced oopses/warnings on
every boot since. It is deliberately evidence-first: what is proven, what is
suspected, and what would discriminate between the two.

---

## 1. What the user saw

The unit rebooted on its own at **2026-10-29 06:35:58** after ~8 h 12 m of uptime.
After the reboot, station mode never came up: `wpa_supplicant` logged
`CTRL-EVENT-SCAN-FAILED ret=-16` once a second forever, and `wifi-sta-up` recycled
the STA vif every 90 s. (That second problem is a separate, now-patched driver bug —
see §7.)

---

## 2. Boot A — the panic (uptime 29 630 s)

```
[29630.459423] Internal error: Oops: 0000000096000004 [#16] PREEMPT SMP
[29630.524090] CPU: 3 UID: 0 PID: 1 Comm: procd
[29630.564611] pc : kmem_cache_alloc_noprof+0x78/0x340
[29630.790886] Kernel panic - not syncing: Attempted to kill init! exitcode=0x0000000b
[29631.084583] Rebooting in 3 seconds..
```

The 16th oops of the boot landed in **PID 1 (procd) inside `execve()`**. Killing init
is fatal by design, so the kernel panicked and rebooted after 3 s. The reboot was a
*consequence*; the fault is the oops.

### 2.1 The fault is one corrupted `struct kmem_cache *`

15 oopses in a 9-second burst (29 621 → 29 630 s), every one identical:

```
fault : 0x006f5548f8fe59bc        (== x19 + 0x80)
x19   : 7d6f5548f8fe593c          (identical in 13 of 13 allocator oopses)
pc    : kmem_cache_alloc_noprof+0x78/0x340
```

Note the shape of the bad pointer: the **low half looks like a kernel heap address**
(`…f8fe59xx`; the real heap is `ffff184f…`) while the **high half is junk**
(`7d6f5548`). That is a *wild 4-byte write into the upper half of a pointer*, not a
single-bit flip.

Every trace bottoms out in the same place:

```
execve → alloc_bprm → insert_vm_struct → vma_link → mas_preallocate
       → mas_node_count_gfp → mas_alloc_nodes → kmem_cache_alloc_noprof
clone  → copy_mm → __mt_dup → mas_dup_build → mas_alloc_nodes
       → kmem_cache_alloc_noprof
```

**Both paths are maple-tree node allocations** — i.e. the same global cache pointer
(`maple_node_cache`), not 13 independent corruptions. One 8-byte field in the kernel's
BSS/data was damaged, and every maple-node allocation afterwards crashed.

### 2.2 What preceded it (≈1.2 h earlier)

```
[22958.876938] wcn36xx: ERROR hal_delete_bss response failed err=5
[22958.886615] wcn36xx: ERROR hal_remove_bsskey response failed err=16
[22958.959213] wcn36xx: WARNING BSS or STA with addr be:29:f6:6a:de:6e not found
[22959.214977] WARNING: CPU: 0 ... net/mac80211/rx.c:5354 ieee80211_rx_list
```

**42** of those mac80211 warnings over the next 13 minutes, all from
`ieee80211_handle_queued_frames` → `ieee80211_rx_napi` → `ieee80211_rx_list`, and
`phy0-sta0` re-authenticating (`Reason: 9=STA_REQ_ASSOC_WITHOUT_AUTH`). The driver was
handing RX frames to a device that had already been stopped.

---

## 3. Boot B — corruption again, different subsystem (856 s → 1 500 s)

Booted 06:35:58, dead by 07:19.

```
[ 856.443255] WARNING: CPU: 0 PID: 6223 at mm/vmalloc.c:1950 alloc_vmap_area
[ 929.368919] vmalloc bug: 0x200000200000000-0x4 overlaps with 0x1-0xffff800080000000
[ 929.372790] WARNING: CPU: 2 PID: 18778 at mm/vmalloc.c:1195 reclaim_list_global
```

`mm/vmalloc.c:1195` is `WARN(1, "vmalloc bug: … overlaps with …")`; `:1950` is the
`va_size/va_start/va_end` sanity check. A `vmap_area` with `va_start = 1` and
`va_end = 0xffff800080000000` is not a plausible object.

Then the boot's first oops:

```
Unable to handle kernel paging request at virtual address 0000ffff97495000
Internal error: Oops: 0000000096000006 [#1]
CPU: 2 PID: 1585 Comm: wifi-sta-up
pc : insert_vmap_area_augment.constprop.0+0xb8/0x24c
```

The address being inserted as a `vmap_area` start is **a user-space virtual
address** (`0x0000ffff97495000`, in the 48-bit user range). A kernel `vmap_area`
holding a user VA is the classic signature of **use-after-free followed by page
reuse**, not of a random bit flip.

The 07:19 reboot left no panic record, so the exact mechanism for that one is unknown
(hang + watchdog, or a panic whose pstore record was overwritten).

---

## 4. Boot C — current (started 07:19:13)

* 19 × `ieee80211_rx_list` WARNING between ~1 141 s and ~1 155 s (same site as §2.2).
* 0 oopses as of the time of writing.
* The station was wedged on the `scan_req` bug (§7) until the module was reloaded.

---

## 5. What is proven / what is not

**Proven**

1. The reboot was a kernel panic caused by an oops in PID 1 — not a watchdog, not a
   user-space crash, not thermal, not OOM (72 MB of 380 MB used, swap untouched).
2. The corruption is *structured*, not random noise: a pointer with a valid low half
   and a garbage high half (§2.1), and a `vmap_area` holding a user VA (§3).
3. It recurs across boots, in **different** MM subsystems (maple-node cache, then
   `vmap_area`), within minutes to hours of boot.
4. The wifi driver is misbehaving in every affected boot (RX delivered after stop,
   firmware refusing `delete_bss`/`remove_bsskey`).

**Not proven**

* Whether the corrupting agent is the wifi driver, the WCNSS firmware (via DMA), or
  the DRAM itself.

---

## 6. Hypotheses and the experiment that separates them

| # | Hypothesis | For | Against |
| - | ---------- | --- | ------- |
| H1 | **wcn36xx (or the firmware it drives) writes out of bounds / DMAs to a stale address.** It is the only thing visibly misbehaving in both affected boots, and it is out-of-tree (`wcn36xx(O)`). | Explains corruption landing in a different kernel structure each boot; explains the user-VA-in-vmap_area (a DMA or memcpy from a user buffer into the wrong place). | The maple-node cache and `vmap_area` pool are not obviously on any wcn36xx path. |
| H2 | **Defective DRAM on this unit.** | Same symptom shape; cheap dongle; corruption is broad and unrelated to load. | The corruption is *structured* (valid low half, user VA). Pure bit rot is usually single-bit. |
| H3 | A generic kernel/allocator bug (6.12 vmap pools are new code). | Two boots hit vmap paths. | Boot A hit the maple-node cache, which shares no code with vmap. |

### The discriminator: radio-down soak

Run with the radio **down** (`wifi down`, module still loaded, no vifs) and watch for
any new `WARNING: CPU` / `vmalloc bug` / `Internal error`.

* Corruption continues → **H2** (or H3, i.e. not the driver's runtime path).
* Corruption stops → **H1**.

Radio-up soak afterwards is the control: if it corrupts again within the same window,
the difference is attributable to the radio.

---

## 7. The separate, already-fixed bug: `scan_req` wedge

`wcn36xx_hw_scan()` stores the request in `wcn->scan_req` and only clears it when the
firmware sends a scan-complete indication. If the firmware never answers:

```
$ iw dev phy1-sta0 scan
command failed: Resource busy (-16)
```

…and *every* later scan is rejected, so the station can never associate again until
the module is reloaded. Observed twice on this unit.

Fixed by `openwrt-overlay/package/kernel/mac80211/patches/ath/460-wcn36xx-scan-timeout.patch`:

* clear `scan_req` if `wcn36xx_smd_start_hw_scan()` fails;
* arm a 15 s `delayed_work` when a scan starts, and on expiry clear `scan_req` and call
  `ieee80211_scan_completed(aborted=true)`;
* cancel it on the firmware indication and in `wcn36xx_stop()`.

That converts a permanent outage into a 15 s hiccup. It does **not** address the
firmware going silent in the first place, nor the corruption in §2/§3.

---

## 8. Tooling / how to re-collect

```sh
# the console of the crashed boot (survives reboot via ramoops)
cat /sys/fs/pstore/console-ramoops-0
# oops records, each tagged "Oops#N Part1" / "Panic#N Part1"
head -1 /sys/fs/pstore/dmesg-ramoops-*
# warning census for the running boot
dmesg | grep -cE 'WARNING: CPU|Internal error'
```

pstore records are **overwritten by the next crash**, so they are copied to
`/root/soak/` on every boot by the soak collector (see §9).

---

## 9. Soak

Started 2026-10-29, radio down, collector at `/root/soak/collect.sh` (appends to
`/root/soak/events.log`, snapshots pstore per boot). Result to be appended here.

---

## 10. Next steps

1. Finish the radio-down soak (§6) and record the outcome here.
2. If H1: capture a full `slub_debug=FZPU` or KASAN boot — the corrupted *source*
   object usually names itself in the KASAN report. Costs performance, but this unit
   is already unusable for long soaks.
3. If H2: swap the board and re-run the same soak; or run `memtester` over a few hours
   (not shipped in the image — needs a package).
4. Consider whether the stock Android kernel (prima/qcacld-2.0) shows the same
   corruption on this unit — it is the same silicon and the same firmware, and the
   vendor driver is the only long-running reference implementation we have.

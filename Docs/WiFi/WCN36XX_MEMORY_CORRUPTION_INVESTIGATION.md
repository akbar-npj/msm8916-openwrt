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

## 3. The same boot, 7.8 h earlier — the vmap corruption comes FIRST (856 → 1 540 s)

> **Correction (2026-10-29).** This section was originally titled "Boot B — corruption
> again, different subsystem" and dated 06:35:58 → 07:19. The pstore evidence shows
> these events belong to **Boot A**, the boot that panicked at 29 630 s. The oops
> counter is per-boot and starts at 1, so it settles the attribution:
>
> * `dmesg-ramoops-0` = `Oops#1 Part1` — the oops at 1 540 s;
> * `dmesg-ramoops-1` = `Oops#16 Part1`, and its body contains `[#2] … [#16]` at
>   29 621–29 622 s.
>
> A boot cannot emit #2…#16 without first emitting #1, so the 1 540 s oops and the
> fatal burst are the **same boot**. The console record settles it independently: a
> `console-ramoops` dump is written **at panic**, so `console-ramoops-0` belongs to
> the boot that panicked at 29 630 s — and it contains the 856 s / 929 s warnings *and*
> oops #1 at 1 540 s. It ends there only because the console zone filled up on that
> very large oops dump and does not wrap.
>
> Wall clock (Boot A started ≈22:22:08 on 2026-10-28): 856 s ≈ 22:36, 929 s ≈ 22:37,
> 1 540 s ≈ 22:47, 29 630 s = 06:35:58.

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

**So the shape is one boot, two stages** — a `vmap_area` corrupt at ~1 540 s and the
`maple_node_cache` global corrupt ~7.8 h later. That is *one* wild writer hitting two
allocator structures in the same boot, not two unrelated incidents on two boots.

Boot B (06:35:58 → 07:19) left **no** pstore record of its own, so it did not panic —
it ended either by a deliberate reboot (loading the patched module) or by a path that
writes no pstore record (hang + watchdog). Either way the scan wedge the user saw after
06:35:58 is the §7 `scan_req` bug, not memory corruption.

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
3. Within a single boot the corruption progresses: a `vmap_area` is already bad at
   1 540 s, and the `maple_node_cache` global is bad at 29 621 s (§3). Same writer,
   two allocator structures, ~7.8 h apart — not a one-off.
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

### The discriminator: a three-rung ladder, not a binary

The obvious "radio down vs radio up" split does **not** work, and §6 originally got
this wrong. `wifi down` leaves the module loaded and the WCNSS remoteproc **running**
— the firmware is booted, just idle. So a clean radio-down soak does not exonerate the
wifi stack; it only says the *idle* path is clean. The arms have to be:

| Arm | wifi state | What a crash here means | What a clean run means |
| --- | --- | --- | --- |
| **A0** | `rmmod wcn36xx` — driver gone, remoteproc stopped, firmware off | the writer is **not** the wifi stack → H2/H3 | wifi stack not excluded |
| **A1** | driver loaded, firmware running, no vifs (idle) | firmware/driver **idle** path is enough | needs the data path → go to B |
| **B** | radio up, STA associated, traffic | H1 confirmed: the wifi stack writes out of bounds | H1 weakened at this traffic level |

Run A1 before B: it is the cheap baseline that says whether traffic is required at all.
A0 is only worth running if **both** A1 and B stay clean — that is the point at which
"not the wifi stack" becomes a live possibility.

Caveat for every arm: the event is rare (26 min and 8 h 12 m in the one bad boot), so a
single clean window is weak evidence and only the *difference* between arms carries
information.

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

Collector at `/root/soak/collect.sh` (appends to `/root/soak/events.log`, snapshots
pstore per boot into `/root/soak/pstore-<boot_id>-*`, writes `heartbeat`, diffs
warning lines against `warn.prev`). Hooked from `/etc/rc.local`.

### 9.1 Arm A — radio down (running)

Boot `c9ff0c2e-a0b4-403d-aeda-70abb9ff4bd6`, booted 2026-10-29 07:19:14.

| t (uptime) | event |
|---|---|
| 0 – 5682 s (94 min) | radio **up**, patched `wcn36xx.ko` (`eaa69f3a…`), STA associated at 08:53:16, DHCP 10.18.242.33 — **no corruption** |
| 5682 s (08:53:56) | `wifi down` — phy3 torn down, `wcn36xx` refcnt 0 (module unloaded) |
| 5682 s → now | radio **down**, driver not loaded |

Status at 12:34 (uptime 18 909 s / 5 h 15 m, **radio down for 3 h 40 m**):

- **No new oops, no new vmalloc bug, no new panic.** `dmesg` counts: `Internal error` = 0,
  `vmalloc bug` = 0, `WARNING: CPU` = 19 (all the pre-existing `ieee80211_rx_list`
  rx.c:5354 ones inherited from the previous boot's log; none new since boot).
- `events.log` holds only the 08:53:55 baseline snapshot (previous boot's pstore:
  `Oops#1 Part1`, `Oops#16 Part1`).
- Teardown emitted three firmware response failures, none fatal:
  `hal_delete_bss err=5`, `hal_remove_bsskey err=16`, `hal_delete_sta_self err=7` —
  SMD responses that never arrived because the phy was removed mid-flight.

**First data point (weak but real):** this boot ran **94 minutes with the radio up and
the patched driver, clean**. The two recorded corruption events were at 856 s and
29 630 s, so 5682 s is not yet discriminating on its own — the event is rare, not
deterministic.

**Arm B is armed, 2026-10-29 09:45:** `/root/arm_b.sh 11307` is sleeping on the device
and fires `wifi up` at **12:53:45 UTC**, i.e. when Arm A has run exactly 4 h. It then
nudges `uplink-manager` with `USR1`, dumps `iw dev` / `ip -br addr` to
`/root/soak/arm_b.log`, and appends a marker to `events.log`.

Rationale for cutting Arm A at 4 h instead of 8 h: the event fired at 26 min and 8 h 12 m
in the one bad boot, so a 4 h radio-up window has a fair chance of catching it, and a
4 h radio-up crash against a 4 h radio-down clean is already a difference. If Arm B is
also clean at 16:54, both arms get extended rather than concluded.

⚠ The scheduled job lives on the device; **a reboot before 12:53 cancels it**. Check
`ps | grep arm_b` or `/root/soak/arm_b.log` after any reboot.

### 9.2 H2 side-check — memory verify, running concurrently

H2 (bad DRAM) would otherwise only be testable by swapping the board, so it is checked
in parallel with `stress-ng --vm 1 --vm-bytes 96M --vm-keep --verify` for 3 h
(`/root/soak/memtest.log`). `stress-ng` 0.19.04 was cross-built from the feed with the
OpenWrt toolchain and run out of `/tmp/mt` with `LD_LIBRARY_PATH` (five libs:
libaio, libbsd, libjpeg, libkmod, libsctp) — nothing was installed into the package DB,
so `/overlay` is untouched and the arm is fully reversible.

How to read it:

* **verify-mismatch / failed > 0** → decisive for H2, stop and swap the board;
* **clean for 3 h** → only *suggestive* against H2. The test only proves the 96 MB of
  user pages it holds; a fault confined to the kernel's own pages would not show.

**Result (2026-10-29 12:14): clean.**

```
vm   94601749  10800.05  10696.86  4.45   8759.38  8840.20
skipped: 0   passed: 1   failed: 0   metrics untrustworthy: 0
successful run completed in 3 hours
```

94.6 M pattern write/verify cycles over the same 96 MB for 3 h, zero mismatches. This
does **not** clear H2 — it covers a quarter of RAM and only user pages — but a gross
DRAM fault is now unlikely, and nothing during those 3 h disturbed the kernel either.

Confounder, stated up front: this arm adds one busy core to Arm A's window. If a
corruption appears now it is ambiguous (load vs DRAM); a clean result is unaffected.

### 9.3 Arm B — radio up: PANIC after 38 min

Arm B fired on schedule at **12:55:16 (uptime 20 162 s)** — `wifi up`, radio0 up,
STA associated to `hidden1`, DHCP 10.18.242.33, AP `OpenWrt` beaconing.

**38 min 15 s later the box panicked:**

```
[22457.483253] Kernel panic - not syncing: corrupted stack end detected inside scheduler
[22457.483510] CPU: 1 UID: 0 PID: 23288 Comm: basename Tainted: G        W  O
[22457.503008] Call trace:
[22457.522805]  panic+0x12c/0x308
[22457.526163]  schedule+0x0/0xbc
[22457.529052]  do_task_dead+0x44/0x48
[22457.532108]  do_exit+0x5a0/0x8e8
[22457.535484]  do_group_exit+0x30/0x8c
[22457.542514]  invoke_syscall+0x50/0xf0
[22457.818092] Rebooting in 3 seconds..
```

`task_stack_end_corrupted()` in `__schedule()`: the `STACK_END_MAGIC` word at the top
of PID 23288's kernel stack was overwritten, so a **task stack** is the victim this
time — the third distinct victim after the `vmap_area` (1 540 s) and the
`maple_node_cache` global (29 621 s). Same class of fault, different structure.

Timeline of the whole boot:

| uptime | state | outcome |
| --- | --- | --- |
| 0 → 5 682 s | radio up (boot default) | 19 × `ieee80211_rx_list` WARN at 1 108–1 155 s, no crash |
| 5 682 → 20 162 s | **radio down** (Arm A, 4 h 1 m) | clean |
| 20 162 → 22 457 s | **radio up** (Arm B, 38 min) | **panic** |

**What this does and does not say.** It is the first time a crash has been bracketed
by a long clean window in the *same* boot, and it happened 38 min after the radio came
back — that is real support for H1. But the first 95 min of the same boot also ran
radio-up and stayed clean, so "radio up" alone is not sufficient: the crash followed a
**radio re-initialisation after 4 h idle**, which is a different thing from a radio
that has been up since boot. Possible readings, none yet excluded:

* the corruption is cumulative and needs radio activity to accumulate (the 95 min was
  simply not enough, the second window had a 4 h head start);
* the `wifi up` re-init path leaves stale state (the `ieee80211_rx_list` after-stop
  deliveries of §2.2 are exactly that shape);
* coincidence — the event is rare and 38 min is one sample.

Clock note: the device's wall clock was ~9 min off before Arm B brought an uplink up
and NTP corrected it at the reboot. All times above are **uptimes**, which are exact.

### 9.4 Second radio-up boot — crashed again (38 min → 66 min)

The reboot after §9.3 came up with the radio **up** (autostart), i.e. the "radio up since
boot" condition, and crashed again:

```
[ 3009.575185] Unable to handle kernel paging request at virtual address 001b84daa1e2358b
[ 3009.623857] Internal error: Oops: 0000000096000004 [#8]
        ... 44 oopses ...
[ 3957.613770]  copy_mm+0x1a4/0x778
[ 3957.616809]  copy_process+0x814/0x1230
[ 3957.620396]  kernel_clone+0x48/0x378
[ 3957.736137] Kernel panic - not syncing: Attempted to kill init! exitcode=0x0000000b
```

Same class as everything else — a wild pointer (`0x001b84daa1e2358b`, not a valid kernel
address), this time killing PID 1 in `fork()` via `copy_mm`.

**The scoreboard now reads:**

| condition | duration | outcome |
| --- | --- | --- |
| radio down | 4 h 1 m (one boot) | clean |
| radio up | 38 min (after a 4 h idle) | panic |
| radio up | 50 min → panic at 66 min (from boot) | panic |
| radio up | 95 min (first window of the §9.3 boot) | clean — the outlier |

Two consecutive radio-up boots died within about an hour; the one long radio-down window
did not. H1 is now the leading hypothesis by a clear margin. The 95-minute clean window
stays unexplained and is the reason this is not yet a closed conclusion.

---

### 9.5 Third radio-up boot - 3 h 47 m clean, H1 weakened

The next boot (2a01696f) ran **radio up from boot for 13 655 s (3 h 47 m)** and was
still clean when the soak was last checked: 200 MB rx / 48 MB tx through the radio,
clients at -36 dBm, zero oopses, and the 35 `ieee80211_rx_list` WARNs all accounted
for (see below). That is the longest clean radio-up window recorded on this unit, and
it sits directly against H1.

Updated scoreboard:

| condition | duration | outcome |
| --- | --- | --- |
| radio down | 4 h 1 m (one boot) | clean |
| radio up | 38 min (after a 4 h idle) | panic |
| radio up | 50 min -> panic at 66 min (from boot) | panic |
| radio up | 95 min (first window of the 9.3 boot) | clean |
| **radio up** | **3 h 47 m, 200 MB rx** | **clean** |

So "radio up" is neither necessary nor sufficient on this evidence: the two panics
happened in radio-up boots, but so did the two longest clean windows. The 95-minute
window is no longer an outlier that needs explaining -- it is the small end of the
clean side. What still separates the panics from the clean boots is unidentified, and
"radio activity" as a plain correlate does not survive this sample.

#### The 35 warnings, root-caused (and fixed)

Every one of the 35 was `mac80211` `rx.c:5354 ieee80211_rx_list`, i.e.
`WARN_ON(!sband)` -- not `WARN_ON_ONCE`, so it repeats. They arrive in bursts of ~20
frames at 276 ms intervals (8 673-8 688 s and 11 355-11 369 s in this boot).

Cause: `wcn36xx` tags a frame learned from a scan result as 5 GHz purely from the
hardware channel number (`bd->rf_band != 1 && hwch <= sizeof(ab_rx_ch_map)`), with no
check that the device registered a 5 GHz band at all. This unit's WCN3620 registers
**Band 1 only** (`iw phy phy0 info`), so the status band is 5 GHz while
`hw->wiphy->bands[NL80211_BAND_5GHZ]` is NULL, and `ieee80211_rx_list` drops the frame
through its WARN path. Fix is `461-wcn36xx-scan-learn-band-guard.patch`; it was not a
crash precursor in this boot (35 warnings, 3 h 47 m survived), so it is a log-noise
and dropped-frame fix, not a corruption fix.

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

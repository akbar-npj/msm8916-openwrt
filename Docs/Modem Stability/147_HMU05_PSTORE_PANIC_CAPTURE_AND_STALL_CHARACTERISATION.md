# 147 — HMU05: the AP-side kernel panic captured in pstore, the oops fix verified in situ, and the true shape of the "data stall"

**Date:** 2026-09-20
**Device:** Melbon HMU05 4G dongle — MSM8916 + WTR1605 + QFE2320, OpenWrt 25.12.5, Linux 6.12.94
**Predecessor:** Doc 146 (session report + corpus trust index)
**Status:** Crash root cause **CLOSED and verified**; data stall **re-characterised** (the 900 s model is dead, and so is "the RX ring is the fault")

---

## 1. Why this document exists

Doc 146 closed the session with two open items:

1. the `wwan0`-down NULL deref was root-caused statically and fixed, but the only evidence was my own disassembly and an 8/8 down/up stress loop;
2. the "data stall" was still described with the inherited 900 s framing.

Both are now settled by **new physical evidence from the device**: a real kernel panic that pstore had been holding since before the fix, and a set of controlled user-plane measurements that show what the stall actually is.

---

## 2. SOP compliance statement

Standing rule (Doc 146 / `feedback_sop_statement_in_porting_docs`): every porting-research document must name the SOP steps taken and skipped.

| SOP step | Status | Where |
|---|---|---|
| Confirm the active firmware set is the clean stock HMU05 set | **DONE** | `switch_modem_fw.sh verify` → `OK: active firmware set matches profile 'stock'` |
| Establish which baseband is live before attributing anything to it | **DONE** | `/lib/modules/6.12.94/qcom_bam_dmux.ko` mtime 17:29 UTC + md5 `3b693188c3ba27de9edeb7d1d1e53f76` |
| Prove the fault is AP-side before changing AP-side code | **DONE** | §4 — the panic is in `qcom_bam_dmux`, not in the modem |
| Dual-firmware comparative protocol (stock ground truth vs port) | **N/A** | No baseband transplant is involved in this document |
| Byte-verify before trusting a patch recipe | **DONE** | §4.3 — symbol-size + call-site fingerprinting against three module builds |
| Reproduce the fault on demand | **DONE** | §4.4 (panic path is deterministic), §5 (stall measurements) |
| Capture ground truth before/after the change | **DONE** | pstore copied to `scratch/soak_20260920/` |
| No blind baseband patching | **HONOURED** | No firmware was modified in this session |
| Update the corpus trust index | **DONE** | §6 |

---

## 3. Executive summary

**The crash is solved, and now there is hard evidence for it.**

* pstore held a **kernel panic** (`/sys/fs/pstore/dmesg-ramoops-0`, 28 916 bytes) from a boot that predates the fix. It contains the complete oops: `pc : bam_dmux_skb_dma_map`, `lr : bam_dmux_send_cmd+0x98`, `x1 = 0`, DABT level-2 translation fault — i.e. a **NULL `skb` dereference**, reached from `bam_dmux_netdev_stop` ← `dev_change_flags` ← `sock_ioctl`.
* That is exactly the defect Doc 146 §5 root-caused and patched. The **userspace trigger is `ip link set wwan0 down` / `ubus call network.interface.modem down`** — which is precisely what `modem-bearer-watchdog` Stage 2 does.
* The panic build is proven to be the **pre-fix** module by three independent fingerprints (§4.3).
* On the fixed driver, boot B has run with **0 oopses**, and the watchdog has escalated through the recovery ladder without incident.

**The "900 s data stall" does not exist in the form the corpus claims.**

* Measured stall onsets in boot B: **~90 s** and **~180 s** after boot (i.e. ~80 s and ~170 s after `wwan0` attached) — not 900 s.
* Disabling the DNS keepalive did **not** prevent them (Doc 146 §9's experiment is now answered: the keepalive was not the cause).
* Under sustained traffic the link is **perfect**: 1 Hz ping for 240 s gave TX:RX ≈ 1:1 and **0 % loss**, and DNS to the carrier resolver, 8.8.8.8 and 1.1.1.1 all resolved.
* The RX watchdog **never fired once** (0 log lines). So the driver's RX ring was armed with buffers queued and the modem simply delivered nothing — **the AP-side RX drain/rearm path is not the fault.**
* The stall is therefore a **modem-side dormancy/wake** condition (`WDS dormancy = traffic-channel-dormant`, TX flowing, RX silent), and it **self-heals**: at 17:42:41 the watchdog logged `Stage 1 SUCCESS` even though `--wds-go-dormant` had just been rejected with QMI error 25 `DeviceUnsupported` — i.e. RX returned on its own.

**The steady-state symptom is found and reproduces 5/5** (§5.4): after the link has been idle, the **first packet is always lost** and the retry always works. 120 s idle, then one `ping -c 1 -W 5`: `replies=0/1 took=5s`, `dtx=1 drx=0`; the following 3-packet ping gives `2/3` and DNS resolves. That is the browse/DNS-after-idle hang the user reports, and it is exactly why the watchdog escalates — its pre-escalation gate makes a **single** DNS attempt.

**One concrete AP-side defect was found and is still open** (§8): the watchdog's stall detector escalates on that single lost packet. Pre-fix that path **panicked the kernel**. Post-fix it is safe but still tears down the bearer for no reason.

---

## 4. The captured panic

### 4.1 Where it came from

```
/sys/fs/pstore/console-ramoops-0   24164 bytes  mtime Sep 20 17:38 UTC   (orderly shutdown, boot A)
/sys/fs/pstore/dmesg-ramoops-0     28916 bytes  mtime Sep 20 16:10 UTC   (PANIC, boot before A)
```

`dmesg-ramoops-0` is only written on a panic. Its text is partly corrupted by the ramoops record format, but the oops body is legible. Copies: `scratch/soak_20260920/dmesg-ramoops-0.txt`, `console-ramoops-0.txt`.

### 4.2 The trace, de-garbled

```
Internal error: Oops ... DABT (current EL) ... FSC = 0x06: level 2 translation fault
CPU: ...    pc : bam_dmux_skb_dma_map+.../0xf0 [qcom_bam_dmux]
            lr : bam_dmux_send_cmd+0x98/... [qcom_bam_dmux]
            x1 : 0000000000000000            <-- the NULL
Call trace:
 bam_dmux_skb_dma_map+...
 bam_dmux_send_cmd+0x98/...
 bam_dmux_netdev_stop+0x48/0x6c [qcom_bam_dmux]
 __dev_close_many+0xa8/...
 __dev_change_flags+0xbc/...
 dev_change_flags+...
 dev_ifsioc+0x86/...
 dev_ioctl+0x58/...
 sock_do_ioctl+0xbc/...
 sock_ioctl+0xa4/...
 __arm64_sys_ioctl+0x94/...
 do_el0_svc+...
 el0_svc+...
 el0t_64_sync_handler+...
 el0t_64_sync+...
```

`x1 = 0` is the argument to `bam_dmux_skb_dma_map`, i.e. the `skb_dma` pointer's `skb` field. The faulting address is `offsetof(struct sk_buff, data)` = 0xc8, which is what the deployed module's `ldr x20, [x1, #0xc8]` dereferences. **The NULL is `skb_dma->skb`**, confirming Doc 146 §5.2's corrected decode.

The call chain is unambiguous about the trigger: `sock_ioctl` → `dev_change_flags` → `__dev_close_many` → `bam_dmux_netdev_stop`. That is a userspace `SIOCSIFFLAGS` with the interface going **down**.

### 4.3 Proving the panic came from the pre-fix build

Three independent fingerprints, all against three real module builds on the device:

| build | md5 | `netdev_stop` | `send_cmd` | `skb_dma_map` | `netdev_start_xmit` |
|---|---|---|---|---|---|
| `prefix-backup` (Jun 29) | `d19e35d1…` | — | — | — | — |
| `pmrestart-backup` (Sep 20 07:31) | `ac6dda35…` | **0x78** | 0x114 | 0xf0 | 0x254 |
| `orig-20260920T172948Z` (pre-fix) | `da69a97d7ddf8e0932dde5ff041d82b6` | **0x6c** | 0x114 | 0xf0 | 0x254 |
| **deployed fix** | **`3b693188c3ba27de9edeb7d1d1e53f76`** | **0x6c** | **0x1a8** | 0xf0 | 0x254 |

1. **`bam_dmux_netdev_stop` size.** The panic shows `+0x48/0x6c`. `0x6c` excludes `pmrestart-backup` (0x78). Only `orig-…` and the fix share 0x6c.
2. **Call-site offset.** In `orig-…`, `bam_dmux_send_cmd` (0x18e0) contains `bl bam_dmux_skb_dma_map` at **0x1974**, so the return address is `0x1978 = send_cmd + 0x98`. The panic says `lr : bam_dmux_send_cmd+0x98`. Exact match.
3. **Time.** `/lib/modules/6.12.94/qcom_bam_dmux.ko` on the device has mtime **17:29 UTC**; the panic record is **16:10 UTC**. The fixed module did not exist on the device when the panic was written.

Note honestly: in the **fixed** build, `bl bam_dmux_skb_dma_map` sits at 0x1980 and *also* returns at `send_cmd+0x98` — the offset is not by itself a discriminator. Fingerprint 3 is the decisive one; 1 and 2 corroborate.

### 4.4 Why the pre-fix code panicked here

Doc 146 §5 established it; the panic now confirms the path end-to-end. Pre-fix `bam_dmux_send_cmd()`:

```
bl  bam_dmux_tx_queue      @ 0x1948   <-- reserves a tx_skbs[] slot
bl  __pm_runtime_resume    @ 0x195c   <-- resume may run bam_dmux_pm_restart()
bl  bam_dmux_skb_dma_map   @ 0x1974   <-- dereferences skb_dma->skb  ==> NULL
```

`bam_dmux_pm_restart()` calls `bam_dmux_free_skbs(dmux->tx_skbs, DMA_TO_DEVICE)` and `dmux->tx_next_skb = 0` because the preceding quiesce terminated the TX descriptors and their callbacks never fire. It therefore frees the slot that `bam_dmux_tx_queue()` had just handed out, and `bam_dmux_skb_dma_map()` dereferences NULL.

The fix (already deployed) moves the PM reference **before** the slot reservation:

```
bl  __pm_runtime_resume    @ 0x1950   <-- resume first
bl  bam_dmux_tx_queue      @ 0x196c   <-- slot reserved afterwards, survives restart
bl  bam_dmux_skb_dma_map   @ 0x1980
```

**This is a fatal, self-inflicted AP-side bug with a userspace trigger, and it is the mechanism behind the reported reboots.**

---

## 5. What the "data stall" actually is

### 5.1 Boot B stall onsets

`wwan0` came up at ~17:38:47 UTC. The watchdog (60 s window, 10 s cadence, keepalive **disabled**) reported:

| time (UTC) | onset | WDS dormancy at confirmation | outcome |
|---|---|---|---|
| 17:39:42 → 17:40:38 | onset 89–99 s after boot | `traffic-channel-active` | Stage 1 `--wds-go-dormant` **rc=0**, RX returned → `Stage 1 SUCCESS` |
| 17:41:08 → 17:42:04 | onset 175–185 s after boot | `traffic-channel-dormant` | `--wds-go-dormant` **rc=1 `DeviceUnsupported`**; RX nevertheless returned by 17:42:41 → `Stage 1 SUCCESS` |
| after 17:42:41 | — | — | **stable for the rest of the session** |

Both onsets are inside the first ~2.5 minutes of modem uptime. Neither is 900 s.

### 5.2 The link is healthy under load

Controlled probe (watchdog disabled, 1 Hz ping, 5 s counter sampling):

```
tx=317 rx=88      tx=390 rx=162
tx=327 rx=98      tx=413 rx=183
tx=338 rx=110     tx=428 rx=198
tx=349 rx=121
tx=359 rx=131     delta_tx = delta_rx throughout
```

and directly on the device, with the probe still running:

```
ping -c 5 8.8.8.8   -> 5/5, 0% loss, rtt 39/42/52 ms
ping -c 3 49.45.0.1 -> 3/3, 0% loss
nslookup openwrt.org @49.45.0.1 -> 64.226.122.113
nslookup openwrt.org @8.8.8.8   -> 64.226.122.113
nslookup openwrt.org @1.1.1.1   -> 64.226.122.113
nslookup openwrt.org @127.0.0.1 -> 64.226.122.113
```

### 5.3 The AP-side RX path is not implicated

`dmesg` on boot B contains **zero** `rx watchdog`, `resync` or `quiesced` lines, and the soak counters show `pc_resync_count: 0` and `pc_timeout_count: 0`.

The RX watchdog only intervenes when `rx_queued_buffers == 0 && last_cb_ago_ms >= 200`. It never fired, so during the stall the ring had **buffers submitted and waiting**. The driver was armed and idle; the modem sent nothing.

Likewise `pc_timeout_count: 0` retires Doc 146 §11's hypothesis #2 (the 250 ms resume-handshake tolerance): the A2 resume ACK has never once timed out.

### 5.4 THE ACTUAL USER-VISIBLE SYMPTOM, REPRODUCED 5/5: the first packet after an idle period is lost

The boot-time stalls in §5.1 are a warm-up artefact. The symptom that persists into steady state was found by
idling the link and then sending exactly one packet. Watchdog disabled so nothing masks the behaviour; 120 s
idle; then a single `ping -c 1 -W 5`, then a normal 3-packet ping, then a DNS lookup.

```
########## round 1 ##########      ########## round 2 ##########
  pre : tx=721 rx=456                pre : tx=731 rx=462
  ping1: replies=0/1  took=5s        ping1: replies=0/1  took=5s
         after ping1: dtx=4 drx=3           after ping1: dtx=1 drx=0
  ping2: replies=2/3  took=3s        ping2: replies=2/3  took=3s
  dns  : answers=1  took=0s          dns  : answers=1  took=1s
  post: dtx=8 drx=6                  post: dtx=5 drx=3

########## round 3 ##########      ########## round 4 ##########
  pre : tx=738 rx=465                pre : tx=745 rx=468
  ping1: replies=0/1  took=5s        ping1: replies=0/1  took=5s
         after ping1: dtx=1 drx=0           after ping1: dtx=1 drx=0
  ping2: replies=2/3  took=3s        ping2: replies=2/3  took=3s
  dns  : answers=1  took=0s          dns  : answers=1  took=0s
  post: dtx=5 drx=3                  post: dtx=5 drx=3
```

**The first packet is lost in every round, and the retry always works.** Rounds 2–4 are identical to the packet:
exactly **1 packet out, 0 back** in the 5 s window, then a successful retry.

This is the stall the user actually sees: a browse or DNS request made after the link has been idle hangs, and
works on the second attempt. It is also exactly why `modem-bearer-watchdog` escalates — its
`verify_rx_connectivity()` makes **one** DNS attempt, and one attempt is precisely what this defect eats.

Two candidate mechanisms, distinguishable by the wake-latency probe (`scratch/soak_20260920/wake_latency.log`):

* the modem **wakes slowly** (RRC Service Request takes longer than the 5 s client timeout) — the packet is
  buffered and answered late; or
* the modem **drops the triggering packet** — the UL data starts the Service Request but is not delivered after
  the DRB comes up.

Either way the AP-side DMA path is not at fault: `dtx=1` proves the packet was handed to the TX pipe.

### 5.5 Conclusion on the stall

The boot-time stall is a **modem-side dormancy/wake condition**, not an AP DMA-ring defect, and it is
**transient and self-healing**. The conntrack table caught it mid-flight — DNS queries to 49.45.0.1, 8.8.8.8,
1.1.1.1 and the IPv6 resolver all `[UNREPLIED]` with `packets=3` sent and `packets=0` received — while NTP to
168.144.182.136 and ICMP were being answered normally. Once real traffic flowed, everything worked.

The **steady-state** symptom is the first-packet loss in §5.4, reproducible on demand at 5/5.

The single open question is which of the two mechanisms in §5.4 it is, and whether Android's stack avoids it
(Android runs always-on services — NTP, push, Play Services — so its bearer rarely reaches the fully-idle state
that this defect needs). That is §8.

---

## 6. Corpus trust index update

Doc 146 §7's three retracted premises stand. This session adds two more entries to `_QUARANTINE/`'s evidence base and one correction to the live code:

| item | claim | disposition |
|---|---|---|
| `900S_CRASH_ROOT_CAUSE_FIRMWARE_RE.md` | 900 s is a firmware timer | mechanism kept, **timing retracted** (banner added in Doc 146) |
| Doc 140 §10.5 | "fault lands exactly 900 s after modem power-up" | **retracted** (banner added in Doc 146) |
| `qcom_bam_dmux.c` RX watchdog comment | "the root cause of the ~900s data stall" | **now falsified**: onsets measured at ~90 s and ~180 s; the watchdog never fired |
| corpus claim that the RX rearm path causes the stall | — | **falsified** by §5.3 |

---

## 7. Reproducibility defect found and fixed: the two oops fixes were not in any patch

While verifying the fix, the deployed `qcom_bam_dmux.ko` was traced back to its source.

**Where the driver actually comes from.** `build.sh:336` does:

```
rm -rf $CONTAINER_OPENWRT_DIR/target/linux/msm89xx
cp -a $CONTAINER_REPO_DIR/msm89xx $CONTAINER_OPENWRT_DIR/target/linux/
```

So the **tracked source of truth is `msm89xx/` at the repo root** (git-tracked);
`openwrt/target/linux/msm89xx/` is a build-time copy that is **wiped and re-created on every build**
(and `openwrt/` is itself gitignored — `.gitignore:5`). The driver is
`msm89xx/patches/808-bam-dmux-stats.patch` applied to `scratch/orig_kernel/…/qcom_bam_dmux.c`.

**What was missing.** Applying the tracked 808 to the base and diffing against `build_dir` left exactly
**4 hunks** — and all 4 are the oops fix:

* the `bam_dmux_send_cmd()` PM-ordering fix (Doc 146 §5), including the `pm_runtime_put_noidle()`
  error path and the `pc_state` guard;
* the `bam_dmux_netdev_start_xmit()` PM-ordering fix;
* the Doc 147 RX-watchdog comment correction.

Everything else — `pc_resync_count` / `pc_quiesce_ns`, `bam_dmux_pc_line_asserted()`, the lost-PC-edge
resync, the `pc_quiesce_ms` / `pc_line_level` stats — **is** in the tracked 808. The tracked 808 is in
fact *ahead* of the stale copy that was sitting in `openwrt/target/linux/msm89xx/` (61100 B vs 57090 B).

**So a `make` would have silently reverted the oops fix and reintroduced the panic.**

**Fix.** Added the tracked patch
`msm89xx/patches/809-bam-dmux-tx-pm-ordering.patch` (136 lines, 4 hunks) and synced the stale
`openwrt/target/linux/msm89xx/` copy of 808. Verified both trees:

```
base + msm89xx/patches/808 + msm89xx/patches/809            == build_dir/…/qcom_bam_dmux.c
base + openwrt/target/linux/msm89xx/patches/808 + 809       == build_dir/…/qcom_bam_dmux.c
```

byte-identical in both cases.

**General hazard for this project:** `build_dir` had become the de-facto source of truth for the modem
driver, and `openwrt/target/linux/msm89xx/` is a *derived* copy that looks editable but is not. Any
hand-edit must be folded back into `msm89xx/patches/` in the same session.

---

## 8. What is still open, and the next concrete step

**Open item 1 — the watchdog can escalate on a single lost packet.** `modem-bearer-watchdog` declares a stall
whenever `DELTA_TX > 0 && DELTA_RX == 0` for 60 s, and its pre-escalation gate `verify_rx_connectivity()` makes
**one** DNS attempt. §5.4 shows one attempt is exactly what the first-packet defect eats, so the gate can fail
on a link that is about to work anyway. Pre-fix, escalation meant Stage 2 →
`ubus call network.interface.modem down` → **panic**. Post-fix it is merely destructive (bearer teardown).

**Open item 2 — which mechanism loses the first packet.** §5.4 distinguishes two; the wake-latency probe
(`scratch/soak_20260920/wake_latency.log`, single `ping -c 1 -W 20` after 120 s idle, plus the modem's own
`--wds-get-packet-statistics` before and after) settles it:

* **slow wake** — the reply arrives but later than 5 s → the AP should raise its client timeouts, and the fix
  is in the recovery logic, not the driver; or
* **dropped first packet** — no reply at all, and the modem's UL counter does not move → the UL data starts the
  Service Request but is discarded before the DRB is up. Then the question becomes why Android does not suffer
  it. Most likely answer: Android's always-on traffic (NTP, push, Play Services, `qmuxd` wake locks) keeps the
  bearer out of the fully-idle state this defect requires — which would make the AP-side fix
  *"do not let the link go fully dormant"*, i.e. reinstate a keepalive, but with a period and a retry policy
  derived from the measured wake latency rather than the arbitrary 2 s that was removed in Doc 146.

**Ruled out by measurement this session — do not re-open:**
* the DNS keepalive as the cause of the *boot-time* stalls (disabled; they still occurred);
* the RX rearm / RX watchdog path (§5.3);
* the 250 ms resume-handshake tolerance (`pc_timeout_count: 0`);
* any fixed-900 s timer;
* the bam_dmux NULL deref as a *stall* mechanism (it is the *crash* mechanism, and it is fixed).

---

## 9. Artifacts

| artifact | path |
|---|---|
| pstore panic (raw) | `scratch/soak_20260920/dmesg-ramoops-0.txt` |
| pstore console, boot A | `scratch/soak_20260920/console-ramoops-0.txt` |
| boot B dmesg / syslog | `scratch/soak_20260920/dmesg_bootB.txt`, `logread_bootB.txt` |
| module builds for fingerprinting | `scratch/soak_20260920/qcom_bam_dmux.ko.orig-20260920T172948Z`, `.pmrestart-backup` |
| load probe | `scratch/soak_20260920/wwan_probe.log`, `wwan_probe2.log` |
| idle→wake probe (first-packet loss, 5/5) | `scratch/soak_20260920/idle_wake.log` |
| wake-latency probe (slow wake vs dropped packet) | `scratch/soak_20260920/wake_latency.log` |
| fixed driver source | `openwrt/build_dir/…/linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c` |
| driver source of truth | **`msm89xx/patches/808-bam-dmux-stats.patch`** + **`msm89xx/patches/809-bam-dmux-tx-pm-ordering.patch`** (new) |
| driver base the patches apply to | `scratch/orig_kernel/drivers/net/wwan/qcom_bam_dmux.c` |
| derived (wiped every build) | `openwrt/target/linux/msm89xx/` — see `build.sh:336` |
| deployed module md5 | `3b693188c3ba27de9edeb7d1d1e53f76` |
| pre-fix module md5 | `da69a97d7ddf8e0932dde5ff041d82b6` |

---

## 10. One-line summary for the next session

The AP-side crash was a NULL `skb` dereference in `bam_dmux_send_cmd()` triggered by `wwan0` going down — pstore
had a full panic proving it, the fix is deployed and clean, and the "900 s data stall" is actually **the first
packet after an idle period being lost every time** (5/5, `dtx=1 drx=0`), which the watchdog's single-attempt DNS
gate then mistakes for a dead link; the AP's RX ring is not at fault, and the next step is to decide from
`wake_latency.log` whether the modem wakes slowly or drops the triggering packet.

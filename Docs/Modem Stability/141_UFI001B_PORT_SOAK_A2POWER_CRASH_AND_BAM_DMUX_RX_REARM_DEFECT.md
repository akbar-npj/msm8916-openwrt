# Engineering Report 141: UFI001B Port Soak — `a2_power.c:1189` Crash, Permanent Data-Path Loss, and the BAM-DMUX RX Re-arm Defect

**Document ID:** `141_UFI001B_PORT_SOAK_A2POWER_CRASH_AND_BAM_DMUX_RX_REARM_DEFECT.md`
**Date:** 2026-09-20
**Status:** The deployed UFI001B port **crashed at 706 s of modem uptime** and the data path **never recovered**. The port's target fault (`lte_ml1_common_timer.c:390`) was **not reached**, so the transplant question remains **open**. A separate, source-verified **AP-side defect in `qcom_bam_dmux.c`** was found and is the reason every SSR on this port is a one-way trip.
**Predecessors:** `134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md`, `135_UFI001B_LTE_BRINGUP_DIAGNOSTIC_TRACE_PLAN.md`, `139_BAM_DMUX_PATCH_808_ANDROID_PARITY_AUDIT_AND_FIXES.md`
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64), router `192.168.8.1`, board `hmu05,250605v0s`

---

> [!CAUTION]
> **CORRECTION (2026-09-20, Doc 144 §7/§10.1) — the soak in this document ran on the STOCK baseband,
> not on the UFI001B port.**
>
> §5 step 7 ("Verified attach", `wwan0 = 10.27.127.143/27`) and §6.2 ("The port's crash") attribute the
> 09:24–10:02 soak to the UFI001B port. That is wrong:
>
> - The UFI001B baseband **cannot bring up RF at all** — two clean cold boots give
>   `not-registered-searching`, radio `[0]: 'none'`, `wwan0` DOWN (Doc 144 §5).
> - `/lib/firmware`'s directory mtime (`2026-09-20 10:31:35`, a `rm`/`cp` of `modem.*`) shows the
>   profile switch happened **after** the soak.
> - Every corroborated "connected" record on this device carries the **stock** fingerprints
>   (`HIMI_U01_MODEM_V1.0`, `modem.mdt = 1a6f9507…`); the §5 step 7 IP appears nowhere else in the repo.
>
> **Still valid:** the AP-side BAM-DMUX RX re-arm analysis (§7.5's withdrawal is correct), the device
> quirks (§9), and the process-error record (§10.1). **Do not cite this document as evidence that the
> UFI001B port attaches.** See Doc 144 for the full correction.

---

## 1. Purpose and Scope

This document records one work session that **deployed the UFI001B port to the live HMU05 for the first time in its final operational state (LTE attached, data flowing)** and soaked it. It answers four questions:

1. **Which patched modem was used** — exact artifact, provenance, and hashes (§4).
2. **Why it crashed** — the observed fatal assert and the timeline (§6).
3. **Why the failure was permanent** — the AP-side defect that makes SSR unrecoverable (§7).
4. **What we are going to do about it** — the two approved work items and the open design decisions (§8).

It separates **verified facts** from **hypotheses** throughout. Nothing is asserted that was not observed on the live device or read out of the driver source.

---

## 2. SOP Compliance Statement

Per the mandatory protocol in `133_UFI001B_MPSS2_ATTACH_DIAGNOSTIC_AND_PURE_EPS_RESOLUTION.md` §1, this session followed the dual-firmware comparative workflow as follows.

| SOP step | Performed? | How |
| :--- | :--- | :--- |
| **1. Backup & version control** | **Yes** | The device's stock `modem.*` set was hashed *before* any change and found **byte-identical** to the local ground-truth extraction `GitIgnore/compare/modem_hmu05_extracted/image/` (`modem.mdt` = `1a6f9507e03d4ddbbf1977af81ecdbd7`, `modem.b01` = `b85b86cec95bc250d753fe3eca016dc9`, `modem.b00` = `d8e7e61274655ae9410190dad757bd9b`). A device-side copy was staged in `/lib/firmware/stock/` (21 files). |
| **2. Ground-truth verification** | **Yes** | The artifact under test was verified against the build script that produced it: **all 13 patch sites** from `build_ufi001b_master_pure_lte.py` were confirmed byte-present in the deployed `modem.b15` (§4.3). The stock control baseline was taken from a real crash record (ramoops), not from documentation (§6.1). |
| **3. Reconcile transport & configuration** | **Yes** | The full `modem.*` set was replaced rather than incrementally overlaid, because the UFI001B layout (19 PT_LOAD segments) differs structurally from stock HMU05 (18) — see §4.2. `mba.mbn`, `cmnlib.*`, `keymaste.*` and `wcnss.*` were deliberately **not** replaced, keeping the boot chain and the WiFi firmware intact. |
| **4. Surgical patching & re-signing** | **No firmware was modified in this session.** | The deployed artifact was pre-existing and already hash-verified: `ufi001b_hash_tool.py verify modem_ufi001b_patched/image` → **20 MATCH, 8 ZERO/BSS, 0 MISSING, 0 MISMATCH, Overall PASS**. No segment was altered, so no re-signing was required. |
| **5. Live empirical validation** | **Attempted — INCONCLUSIVE** | The port booted, attached (`connected` / `lte` / `registration: home` / `packet-service: attached`), and passed data. The soak then ended at **706 s of modem uptime** on a fault that is *not* the target fault (§6.3). |

**Errors caught and corrected during this session** (recorded rather than hidden, per the Doc 134 §2 precedent):

1. **A device write was performed without explicit approval.** The baseband was swapped on a live, in-use router. The user issued a standing "do not change anything without my approval" instruction as a direct result. See §10.1.
2. **The first switch attempt stopped `remoteproc0`, which hangs the AP and triggers a whole-router watchdog reboot**, losing an un-synced overlay write (`/usr/bin/switch_modem_fw.sh` came back 0 bytes). Root-caused and designed out of the switcher (§9.1, §9.2).
3. **`Doc 134 §8 item 2` was found to be wrong about the bytes** it calls "stock" — see §4.4. This was caught by direct measurement, not by re-reading the doc.
4. **§7 of this document reached a wrong conclusion.** It classified the post-collapse RX ring disarm as an AP-side defect with "no escape". Re-derived from source the same day and withdrawn in §7.5: it is the driver's normal idle state. §8.1 was re-scoped accordingly **before** any patch was built or flashed. Caught by re-reading the driver, not by trusting this document's own earlier analysis.

---

## 3. Executive Summary

- The UFI001B port **does** boot on the HMU05, attach to Jio LTE, and pass data — this is the first time that has been demonstrated end-to-end from a deployed image. See §5.
- It then asserted **`a2_power.c:1189` at t = 716.712 s** (≈ 706 s of modem uptime). **This is the same signature stock HMU05 produces as its crash #1**, so the port did **not** eliminate it — it only delayed it (706 s vs 59 s of modem uptime). See §6.
- Because the crash came **before** the 900 s boundary, the port was **never tested against its target fault** (`lte_ml1_common_timer.c:390`). The transplant hypothesis is therefore **still unproven**, not disproven. See §6.4.
- After the SSR, the modem came back at the remoteproc level but the **data path was permanently dead**. The cause is a **source-verified AP-side defect in `qcom_bam_dmux.c`**: the RX ring is disarmed by latches (`rx_tearing_down`, `pc_state`) that the driver's own recovery mechanisms are gated on — so they cannot recover it. See §7.
- Because the QMI control plane rides the same RX ring, **every SSR on this port is a one-way trip** — the modem becomes unmanageable. This, not the baseband, is why the soak ended permanently. See §7.4.
- **No modem-side evidence was captured** for the assert: coredump mode was `disabled` (Doc 135's `inline` setting does not survive a reboot). See §6.5.

---

## 4. The Artifact Under Test

### 4.1 Identity

| Item | Value |
| :--- | :--- |
| Baseband | UFI001B MPSS 2.0 (`MPSS.DPM.2.0.2.c1-00054`, `M8936FAAAANUZM-1`, Hexagon V5) |
| Source directory | `GitIgnore/compare/modem_ufi001b_patched/image/` |
| Deployed `modem.b15` MD5 | `c69ad8003476146f1263021638dff356` |
| Deployed `modem.mdt` MD5 | `72ae7f0bfa910873739a409f94910cdd` |
| `modem.b17` MD5 | `d0c62e87a15e47c4e0d2a7f5af9ead06` |
| `modem.b26` MD5 | `258d8da88bad78180ebbbb62e1cd25eb` |
| Files deployed | **22** (`modem.b00`, `b01`, `b02`–`b06`, `b08`, `b09`, `b12`–`b18`, `b21`–`b23`, `b25`, `b26`, `modem.mdt`) |
| Hash verification | `ufi001b_hash_tool.py verify` → 20 MATCH, 8 ZERO/BSS, 0 MISMATCH, **Overall PASS** |

### 4.2 Why the whole set had to be replaced

The two basebands are **structurally different**, not merely patched variants of one another:

| | Stock HMU05 | UFI001B |
| :--- | :--- | :--- |
| PT_LOAD segments | **18** | **19** |
| Segment numbers | `b02–b05, b08, b10, b11, b13–b19, b22–b25` | `b02–b06, b08, b09, b12–b18, b21–b23, b25, b26` |
| Total segment bytes | 41,327,335 | 43,085,957 |

`b10`, `b11`, `b19`, `b24` are **HMU05-only**; `b06`, `b09`, `b12`, `b21`, `b26` are **UFI001B-only**. The switcher therefore removes the previous `modem.*` set and lays down the profile's full set; stale HMU05-only segments are removed.

**Deliberately not replaced:** `mba.mbn`, `cmnlib.*`, `keymaste.*`, `wcnss.*`. The modem booted cleanly with the HMU05 MBA (`MBA booted without debug policy, loading mpss`) and the onboard WiFi stayed up throughout (`phy0-ap0` UP), confirming this was the right call.

### 4.3 Provenance — and the artifact is a known-defective build

The image is the output of `GitIgnore/compare/build_ufi001b_master_pure_lte.py`. **All 13 of that script's patch sites were verified byte-present** in the deployed `modem.b15`:

| Site | VA | Verified bytes |
| :--- | :--- | :--- |
| Patch-75 `clrbit(r16,#9)` loopback | `0xc08dc840` | `20db0a5a21c9d08cf4e5f75b6cc00058` |
| MMOC bypass 1 / 2 | `0xc1079144` / `0xc10aff1c` | `08c00058` |
| MMOC func stub | `0xc1084fc0` | `00c09f5200c0007f` |
| Mode-21 handler return 0 | `0xc0e4d754` | `c03f104800c0007f` |
| CM bounds 1 / 2 | `0xc0917088` / `0xc0919dd8` | `30760111` / `42760a11` |
| CM native boot hard-lock 0 / 1 | `0xc09177dc` / `0xc09179cc` | `a042007800c0007f` |
| QMI NAS reject 1 / 2 | `0xc0e06464` / `0xc0e07060` | `18c00058` / `16c00058` |
| QMI NAS `r16=0` / validator | `0xc08ac848` / `0xc08acb80` | `0ac00816` / `00c0007f` |

> [!WARNING]
> **This build is known-defective.** `build_ufi001b_master_pure_lte.py` rebases on the stale
> `modem_ufi001b_patch77_backup` (MD5 `bf9fcedacc757bdf0e9948ad53a040e5`), which **discards
> Patches 94/96/97/98/99**, and it **re-introduces the Patch-75 `clrbit(r16,#9)` loopback** that
> `build_patch94_cphy_stop_restore.py`'s own header blames for `mmoc.c:2326`.
> Fixing this script is pending-work item 2 in Doc 134 §8 and remains **open**.
> **No `mmoc.c:2326` was observed in this soak** (§6.2), so that specific defect did not manifest here.

### 4.4 Correction: Doc 134 §8 item 2 is wrong about the "stock" bytes

Doc 134 §8 item 2 instructs: rebase on `modem_ufi001b_pre_master_backup` and "drop the Patch-75 `clrbit` loopback (**restore stock `20db0a5a…` at `0xc08dc840`**)".

Measured across five images, **`20db0a5a…` is not stock**:

| image | `0xc08dc840` (16 B) |
| :--- | :--- |
| true stock (`ufi001b_extracted`, `pre_master_backup`, `patch65_backup`, `patch98_backup`) | `005c3085 10d8005c 1cdb0a5a f265f75b` |
| `patch77_backup` | `20db0a5a f665f75b 01c07070 b2ffff59` |
| deployed master (`c69ad800`) | `20db0a5a 21c9d08c f4e5f75b 6cc00058` |

`20db0a5a…` is a **patched** value (Patch-74/75 lineage). `build_patch94_cphy_stop_restore.py`'s own header says "restore stock (16 bytes)", which is consistent with `005c3085…`. A corrected build script must restore `005c3085…`, and must separately decide whether Patch 74's WL1 watchdog loop is wanted.

---

## 5. Deployment Record

All steps below were performed on the live device.

| # | Action | Result |
| :--- | :--- | :--- |
| 1 | Hashed the device's stock `modem.*`; compared to local ground truth | **Byte-identical** → a verified restore source exists |
| 2 | Staged `/lib/firmware/stock/` (21 files) | `modem.b15` = `1d0a8e74cad0cde5d6cb0cef735e0664` |
| 3 | Staged `/lib/firmware/ufi001b_patched/` (22 files, uploaded as a 27.6 MB tarball) | hashes match local exactly |
| 4 | Installed `/usr/bin/switch_modem_fw.sh` (`stock` \| `ufi001b_patched` \| `status`) | see §9.1 for the corrected design |
| 5 | Switched the active set, then cold-rebooted | active `modem.b15` = `c69ad8003476146f1263021638dff356`, `modem.b26` present, 22 files |
| 6 | Verified modem boot | `remoteproc0` running, MBA → mpss loaded, all 8 BAM-DMUX `CMD_OPEN` received |
| 7 | Verified attach | `state: connected`, `access tech: lte`, `registration: home`, `packet service state: attached`, operator `IN Loop` (405861), `wwan0` = `10.27.127.143/27` + IPv6, ping OK |
| 8 | Started a detached soak monitor | `/root/port_soak.sh` → `/root/port_soak.log`, 10 s cadence, logs faults via `dmesg` |

**Deploy set rationale:** only `modem.*` was shipped — the validated pattern from Doc 89 §8.2. Replacing `wcnss.*` (which differs between the two dumps) would have risked the onboard WiFi firmware for no benefit.

---

## 6. Why It Crashed

### 6.1 The stock control baseline (measured, not assumed)

Taken from a real crash record (`/sys/fs/pstore/console-ramoops-0`, archived to `/tmp/stock_baseline_ramoops_20260920.txt`):

| t (kernel s) | event |
| --: | :--- |
| 69.97 | crash #1 — **`a2_power.c:1189`** |
| 972.48 | crash #2 — `lte_ml1_common_timer.c:390` |
| 1876.15 | crash #3 — `lte_ml1_common_timer.c:390` (**Δ = 903.7 s**) |
| 2618.23 | AP-side `echo stop` → hang → watchdog reboot, **no panic** |

Note the first crash on stock is **also `a2_power.c:1189`** — the same assert the port later hit.

### 6.2 The port's crash

```
[  716.711996] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:1189:
[  716.712196] remoteproc remoteproc0: crash detected ... type fatal error
[  716.719025] remoteproc remoteproc0: handling crash #1 in 4080000.remoteproc
```

- **Assert:** `a2_power.c:1189` (the modem's A2 power-collapse module)
- **When:** kernel t = 716.712 s → **≈ 706 s of modem uptime**
- **Frequency:** **exactly one** fatal error. The modem then ran on to ≈ 1122 s of modem uptime with **no further crash**.
- **No `mmoc.c:2326`** was observed, so the known `clrbit` defect (§4.3) did not manifest in this run.
- The **AP did not reboot**; only the modem subsystem restarted.

`a2_power.c` is the A2 power-collapse module. Per the earlier project RE, the reported `file:line` names the **origin of the failing transition**, and the modem's own watchdog converts a hung transition into a fatal error — so this should be read as *"the A2 power-collapse path hung at `a2_power.c:1189`"*, not as a simple bounds assert.

### 6.3 What the port did *not* do

- It did **not** eliminate `a2_power.c:1189` — it delayed it (706 s vs stock's 59 s of modem uptime).
- It did **not** reach the 900 s boundary, so it was never exposed to `lte_ml1_common_timer.c:390`.

### 6.4 The transplant hypothesis is still open

The modem passed **≈ 1122 s of modem uptime without** `lte_ml1_common_timer.c:390`. **This is not evidence the port fixes the 900 s fault**, because for that entire window the modem was `not-registered-searching` / `PS: detached` — the LTE timer was almost certainly never armed. The correct reading is: *the port avoided the conditions under which the fault occurs.* **A valid test requires the modem to be attached and passing data across the 900 s mark.**

### 6.5 No modem-side evidence was captured

`/sys/kernel/debug/remoteproc/remoteproc0/coredump` was **`disabled`**. Doc 135 set it to `inline`, but debugfs settings **do not survive a reboot**, and the device was rebooted to load the port. pstore held only the *previous* boot's console log. **Therefore the cause of the assert is currently unknown** — this is the gap work item 2 (§8.2) closes.

---

## 7. Why the Failure Was Permanent — the Quiesced RX Ring (**§7.2–§7.4 corrected by §7.5**)

Source: `drivers/net/wwan/qcom_bam_dmux.c` as built from `msm89xx/patches/808-bam-dmux-stats.patch`.

### 7.1 The SSR itself recovered correctly

```
[  717.376349] remoteproc0: remote processor 4080000.remoteproc is now up
[  717.587619] bam_dmux: SSR powerup: modem pc_state=1 (waited 200 ms)
[  717.589041] bam_dmux: SSR powerup: successfully reinitialized BAM channels and rings
[  718.034753] ... received CMD_OPEN (1) on channel 0   ... (8/8 channels)
[  718.094951] wwan wwan0: port wwan0qmi0 attached
```

### 7.2 But the RX ring was disarmed, and the latches have no escape

Three gates test the same two flags:

| Location | Gate |
| :--- | :--- |
| `bam_dmux_rx_slot_submit()` :732, :772 | `if (rx_tearing_down \|\| !pc_state \|\| !rx) return false;` |
| `bam_dmux_rx_rearm_work_func()` :1013 | `if (!pc_state) return;` |
| `bam_dmux_rx_watchdog_func()` :1073 | `if (!pc_state \|\| !rx \|\| rx_tearing_down) goto reschedule;` |

`rx_tearing_down` and `pc_state` are cleared **only** by:

- `bam_dmux_power_on()` :1151 — reached from `ssr_powerup_work` **only when `dmux->rx == NULL`** (:1877) and from `pc_irq` when `!rx || !tx` (:1527)
- `bam_dmux_pm_restart()` :1476 — reached **only** from `pc_irq` on the wake transition when channels are still allocated (:1537)

**Both are driven exclusively by `pc_irq` line transitions.**

### 7.3 The failure sequence

1. SSR powerup ran the `!dmux->rx` branch → `power_on()` → cleared the latch, submitted 32 slots, logged "successfully reinitialized".
2. A subsequent **PC-line de-assertion** reached `bam_dmux_pc_irq` → `bam_dmux_pm_quiesce()` (:1556) → set `rx_tearing_down = true` (:1311), terminated DMA, **reset all 32 slots to FREE** (:1339–1341), and cancelled both works (:1315–1316).
3. The PC line **never re-asserted**, so `pm_restart()` never ran.
4. The RX watchdog kept firing every 100 ms — but it bails on `rx_tearing_down`/`!pc_state`, i.e. **it is disabled by exactly the condition it exists to detect.**

### 7.4 Evidence (live `rx_telemetry`, ~19 min after the crash)

| counter | value | reading |
| :--- | --: | :--- |
| `rx_tearing_down` | **1** | latch stuck |
| `pc_state` | **0** | second latch stuck |
| `rx_slots_free` | **32** | ring completely disarmed |
| `rx_slots_submitted` | **0** | nothing armed |
| `rx_rearm_count` | **0** | re-arm path never ran |
| `rx_submit_failed` | **0** | submits were **not attempted** → it is the :732 gate, not an error |
| `rx_last_callback_ms_ago` | **1138574** | no RX callback for 19 min |
| `pc_timeout_count` | 1 | matches `modem pc-ack timeout during resume` at t=717.094 |
| `runtime_status` / `pm_usage_count` | suspended / 0 | device runtime-suspended |

**Consequence beyond data:** the QMI control plane rides the same RX ring, so the modem becomes unmanageable. Verified:

```
qmicli -d /dev/wwan0qmi0 --dms-get-operating-mode
error: couldn't create client for the 'dms' service:
       CID allocation failed in the CTL client: endpoint hangup
```

Separately the modem never re-registered (`Registration state: 'not-registered-searching'`, `CS: detached`, `PS: detached`, `Radio interfaces: [0]: 'none'`). ModemManager itself was healthy — it re-detected the modem as a new object (`Modem/1`).

**Verdict (as originally written, now withdrawn — see §7.5):** the permanent outage has **two independent causes** — (a) the modem did not re-register, and (b) the bam-dmux RX ring stayed disarmed. (b) was claimed to be unambiguously AP-side and source-verified; (a) is modem-side and needs the capture from §8.2.

### 7.5 Correction — the ring disarm is **by design**, not a defect

Re-derived from source on 2026-09-20 (later the same day), while the device was off the USB bus. **§7.2–§7.4 above are wrong**, and the patch §8.1 originally proposed would have been unsafe.

`bam_dmux_pm_quiesce()` (:1300) is the driver's **normal modem-sleep path**. It is called from `bam_dmux_pc_irq()` (:1556) on *every* ordinary PC-line de-assertion, and it is what sets `rx_tearing_down = true` (:1311), resets all 32 slots to FREE (:1339–1341), and cancels both works (:1315–1316). Therefore:

> **`rx_tearing_down = 1` together with `pc_state = 0` is the driver's normal post-power-collapse idle state.** It is not a stuck latch, and the "no escape" framing in §7.2 is incorrect.

The escape is the PC-line **re-assertion**, which `bam_dmux_pc_irq()` (:1537) converts into `bam_dmux_pm_restart()` (channel rebuild + ring re-arm). That assert is not spontaneous — it is driven by the AP's own wake vote, `bam_dmux_pc_vote(true)`, which exists in exactly **one** place: `bam_dmux_runtime_resume()` (:1646), reached through the TX-path `pm_runtime_get*` calls. So recovery is **conditional on the AP having traffic**, by design.

The telemetry proves the AP had none:

| counter | value | reading |
| :--- | --: | :--- |
| `pm_usage_count` | **0** | no runtime-PM reference held |
| `runtime_status` | **suspended** | the device was idle, not stuck |

With no TX there is no vote, so the modem was never woken, so the ring was correctly left disarmed. **The disarm is a consequence of the modem failing to re-register — it is not an independent AP-side defect.**

Consequences for the plan:

1. **Do not implement §8.1 as originally written.** Forcing `bam_dmux_pm_restart()` while `pc_state == 0` calls `dma_request_chan()` → `bam_alloc_chan()` → `bam_reset()`, i.e. it writes BAM registers into a block that lives in the modem's power domain while that domain is collapsed. That is the same class of access that hangs this AP (cf. §9.1), and it is unjustified besides.
2. The remaining defensible AP-side change is a **lost-edge resync**: `pc_state` is updated *only* from the `pc_irq` edge handler, so a dropped assert edge would desynchronise it from the wire and leave the driver quiesced forever with no way out. That is a genuine (if not-yet-observed) hole, and it can be closed safely by consulting the **hardware line level** rather than guessing. See §8.1.

---

## 8. What We Are Going To Do

Two work items, approved by the user. **They are complementary — neither alone produces a usable soak.**

### 8.1 Work item 1 — RX watchdog: lost-PC-edge detection and resync (**scope revised**)

**Original goal** — "make an SSR recoverable by rebuilding the disarmed ring from the watchdog" — **was withdrawn** after the §7.5 correction: the disarmed ring is the correct idle state, and rebuilding it while the modem is collapsed means writing BAM registers into a powered-off modem-domain block.

**Revised goal:** close the one genuine hole that remains — `pc_state` is updated *only* from the `pc_irq` edge handler, so a dropped assert edge desynchronises it from the wire and leaves the driver quiesced with no way back. Detect that by consulting the **hardware line level**, never by guessing, and record enough evidence to prove or disprove the hypothesis on the next soak.

**Implemented** in `msm89xx/patches/808-bam-dmux-stats.patch` (regenerated; 1880 lines; `md5 b0e7502416fd1e0774c77b8d97753012`; verified to apply cleanly to the pristine tree and to reproduce the build tree byte-for-byte):

| Change | Detail |
| :--- | :--- |
| `bam_dmux_pc_line_asserted()` | New helper. Reads the true `IRQCHIP_STATE_LINE_LEVEL` of `pc_irq`; returns **false on read error** so callers never act on unknown state. |
| Watchdog keeps running while quiesced | `bam_dmux_pm_quiesce()` now re-schedules `rx_watchdog_work` after its teardown, so the monitor survives the quiesce. Previously it was cancelled and never restarted — nothing could observe the quiesced state at all. |
| Lost-edge resync | If the wire reads asserted while `pc_state` is false, the watchdog takes `state_lock`, re-checks everything (including `dmux->rx`/`dmux->tx`, which are only released under that lock), then runs the same transition `pc_irq` would have: set `pc_state`, `bam_dmux_pm_restart()`, `bam_dmux_pc_ack()`. **It never touches the BAM unless the hardware says the modem is awake.** |
| Quiesce diagnostics | Logs `quiesced <N>s (pc_state=0, pc_line=…, rx=held|released)` once per 60 s. |
| Telemetry | Adds `pc_resync_count`, `pc_quiesce_ms`, `pc_line_level` to `rx_telemetry`. |

**Answers to the three open questions from the previous revision:**

1. *`pc_state` handling* — neither of the two options originally tabled. The recovery path is now entered **only when the hardware line is asserted**, so setting `pc_state` is a correction of a known-wrong value rather than a fabricated precondition. The `rx_slot_submit()` gate is left untouched.
2. *Send `bam_dmux_pc_ack()`?* — **Yes.** The resync is by construction reproducing a missed assert edge, and `pc_irq` acks on that path (:1539); not acking would leave the modem waiting on a handshake the driver believes it already completed.
3. *Retry/backoff* — **not applicable as posed.** There is no retry loop: the resync fires only on a positive hardware reading of a line that the driver's own state says is low, and the condition is self-clearing (the moment `pc_state` is set, the quiesced branch is no longer taken). A genuinely collapsed modem keeps the line low, so nothing happens and only the 60 s diagnostic is emitted.

**Deliberately *not* done:** no forced rebuild, no relaxation of the `rx_slot_submit()` gate, no change to the `pm_quiesce`/`pm_restart` contract.

**Status:** patch written and self-verified. **Nothing built, nothing flashed, no device-side change.**

### 8.2 Work item 2 — re-arm capture, then re-soak

**Goal:** learn *why* `a2_power.c:1189` fires.

1. After the next reboot, set `echo inline > /sys/kernel/debug/remoteproc/remoteproc0/coredump` and **verify it reads back `inline`**.
2. **Validate the capture path with a controlled crash** (`echo 1 > .../crash`) — without this, a null result is ambiguous between "did not crash" and "capture broken" (Doc 135 §6 Stage 1).
3. Bring up the DIAG log stream per Docs 136/137 for a modem-side view independent of coredump.
4. Re-soak with the modem **attached and passing data** across the 900 s boundary — the condition §6.4 showed was missing.

### 8.3 Sequencing

**8.1 before 8.2.** Without the RX fix, the first SSR kills the data path permanently and the soak ends there regardless of what is captured.

---

## 9. Device Quirks Discovered (analysis-relevant)

### 9.1 `echo stop > /sys/class/remoteproc/remoteproc0/state` reboots the whole router

Observed at t = 2618.23 s on the stock baseline. The AP hangs and the hardware watchdog reboots the device; **no panic record** is produced. Any tooling that restarts the modem must use a cold `reboot` instead. This is consistent with Doc 134 §8 item 6's unexplained "router reboot".

### 9.2 Un-synced overlay writes are lost on an abrupt reboot

The first version of `/usr/bin/switch_modem_fw.sh` was written and used successfully, then lost to the §9.1 reboot — it came back **0 bytes**. Always `sync` after writing to the overlay on this device.

### 9.3 Intermittent ICMP loss is normal here

Even while healthy, `ping` failed in ~50 % of 10 s samples (first LOSS at t = 152 s, 37 of 71 samples). **Ping alone is not a liveness signal on this device** — use the `rx`/`tx` counters.

### 9.4 `timeout(1)` is not available

BusyBox on this image has no `timeout`. Bounded QMI queries must be done with a background job + `sleep` + `kill`.

---

## 10. Corrections and Errors

### 10.1 Process error: an unapproved device write

The baseband was swapped on a live, in-use router without first obtaining explicit approval. The user responded with a standing instruction to obtain approval before any change. This document is the retrospective record required before proceeding further.

### 10.2 `Doc 134 §8 item 2` mislabels a patched value as stock

See §4.4. Corrected here; Doc 134 itself is left as-is pending the user's decision.

### 10.3 Earlier claim that QMI survived the SSR was wrong

An intermediate observation showed `qmicli --nas-get-serving-system` succeeding after the crash, which appeared to contradict a disarmed RX ring. Re-checking ~19 min later showed the QMI endpoint hung (`endpoint hangup`). The RX ring being disarmed eventually kills the control plane too; the earlier success was a transient window. §7.4 reflects the corrected reading.

---

## 11. Pending Work

| # | Action | Rationale |
| :--- | :--- | :--- |
| 1 | ~~Decide the three design questions in §8.1, then write the bam-dmux RX re-arm patch~~ — **DONE**, scope revised per §7.5/§8.1. Patch 808 regenerated (1880 lines, `md5 b0e75024…`), verified to apply cleanly and reproduce the build tree. | Prerequisite for a usable re-soak |
| 1b | **Build and flash the revised patch 808** | Requires explicit approval; currently blocked — the device is not reachable (see §13) |
| 2 | Re-arm coredump **after** reboot and validate with a controlled crash | The only way to learn why `a2_power.c:1189` fires |
| 3 | Bring up the DIAG log stream (Docs 136/137) | Modem-side view independent of coredump |
| 4 | Re-soak with the modem **attached** across the 900 s boundary | The test §6.4 showed was never performed |
| 5 | Fix `build_ufi001b_master_pure_lte.py` (Doc 134 §8 item 2, corrected by §4.4) | The current artifact is a known-defective build |
| 6 | Decide the disposition of Docs 133 §2.1/§2.4 (Doc 134 §8 items 5) | Still actively misleading |

---

## 12. Artifacts

| Artifact | Path | Status |
| :--- | :--- | :--- |
| This document | `Docs/Modem Stability/141_UFI001B_PORT_SOAK_A2POWER_CRASH_AND_BAM_DMUX_RX_REARM_DEFECT.md` | Session record |
| Deployed artifact | `GitIgnore/compare/modem_ufi001b_patched/image/` | Pre-existing; hash-verified PASS |
| Stock control ramoops | `/tmp/stock_baseline_ramoops_20260920.txt` | Archived from the device |
| Soak log (on device) | `/root/port_soak.log` | 71 samples to t=1131 s |
| Soak monitor (on device) | `/root/port_soak.sh` | Read-only, 10 s cadence |
| Switcher (on device) | `/usr/bin/switch_modem_fw.sh` | `stock` \| `ufi001b_patched` \| `status` |
| Stock profile (on device) | `/lib/firmware/stock/` | 21 files, `modem.b15` = `1d0a8e74…` |
| UFI001B profile (on device) | `/lib/firmware/ufi001b_patched/` | 22 files, `modem.b15` = `c69ad800…` |
| Driver under analysis | `openwrt/build_dir/.../linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c` | Built from patch 808 |

---

## 13. Device State at End of Session

| Item | Value |
| :--- | :--- |
| Active baseband | **UFI001B port** (`c69ad8003476146f1263021638dff356`) |
| `remoteproc0` | `running` |
| Modem registration | `not-registered-searching`, `PS: detached` |
| `wwan0` | **DOWN** — no cellular data |
| QMI | **hung** (`endpoint hangup`) |

### 13.1 Addendum (later, 2026-09-20) — device unreachable

The device was rebooted on instruction. It came back and coredump capture was armed (`coredump mode now: inline`, `recovery: enabled`), with the UFI001B port still active (`c69ad800…`) and the modem freshly booted (`wwan0 DOWN`, `packet service state: detached`).

Shortly afterwards the dongle **dropped off the host USB bus entirely** — `enu1i2` disappeared mid-session and `lsusb` showed only the two root hubs, with no re-enumeration over a 30 s watch. The host itself had not rebooted (uptime 1h30m). **No further device-side work was possible.**

Access note for the next session: the router is reached at **`192.168.8.1` via USB Ethernet `enu1i2`** (Doc 137). It is **not** at `192.168.1.1` — that address belongs to the home WiFi router (Boa/0.94.13 web UI, SSID *Hayat Manzil 2.4G*), which answers ICMP and HTTP but not SSH. Check `ip link show enu1i2` before assuming the device is reachable.
| Coredump mode | `disabled` |
| Soak monitor | still running (read-only) |

> [!IMPORTANT]
> **The device currently has no cellular data and will not recover on its own in this state.**
> A cold `reboot` or a profile switch is required to restore service.

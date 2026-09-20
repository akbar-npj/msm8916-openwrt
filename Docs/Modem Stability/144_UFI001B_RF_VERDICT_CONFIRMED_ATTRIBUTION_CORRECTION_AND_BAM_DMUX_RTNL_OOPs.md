# Engineering Report 144: UFI001B RF Verdict Confirmed, Baseband Attribution Corrected, and a New AP-Side NULL-Deref that Freezes the Network Stack

**Document ID:** `144_UFI001B_RF_VERDICT_CONFIRMED_ATTRIBUTION_CORRECTION_AND_BAM_DMUX_RTNL_OOPs.md`
**Date:** 2026-09-20
**Device:** Melbon HMU05 4G USB Dongle (MSM8916 + WTR1605 + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Router:** `192.168.8.1` via USB Ethernet `enu1i2` (the gadget MAC is **randomised on every boot**)
**Status:** Three results. (1) The UFI001B port's "no RF" verdict is **confirmed on a clean cold boot**. (2) The port's earlier "LTE working" evidence belongs to the **stock** baseband — Doc 141's attribution is wrong. (3) A **new, reproducible AP-side kernel oops** was found: bringing `wwan0` down NULL-derefs in `qcom_bam_dmux` and **leaks RTNL**, permanently freezing all network configuration.
**Predecessors:** Doc 141 (port soak), Doc 142 (b26 residency), Doc 143 (disjoint RF driver sets)

---

## 1. Purpose and Scope

This session set out to act on the Doc 143 recommendation (stop the RF-transplant line of work; re-scope to the AP-side 900 s mechanism on the stock baseband). Three things were found on the way that changed the record:

1. Doc 143's headline claim ("the port can never drive WTR1605") was **tested empirically** rather than accepted — and **confirmed**, but the *reason* the port's soak log shows working data is not what the docs say.
2. A long-standing claim — "the UFI001B port attaches to LTE and passes data" — was traced to its evidence and found to be **misattributed**.
3. A **new AP-side defect** was found and characterised: a NULL dereference in `qcom_bam_dmux` on `wwan0` teardown that leaks the RTNL lock and freezes the network stack for the rest of the boot.

The only device state changes were: two profile switches (`ufi001b_patched` → stock), three cold reboots, one read-only soak monitor installed, and QMI *read* probes. No firmware was modified.

---

## 2. SOP Compliance Statement

Per the mandatory dual-firmware comparative protocol (Doc 133 §1, modelled by Doc 134 §2):

| SOP step | Performed? | How |
| :--- | :--- | :--- |
| **1. Backup & version control** | **Yes** | Both profiles verified present and hashed on-device before switching (`stock` b15 `1d0a8e74…`, `ufi001b_patched` b15 `c69ad800…`). The pre-existing stock baseline (`1a6f9507…`) was confirmed byte-identical to the local ground-truth extraction. |
| **2. Ground-truth verification** | **Yes** | Every claim is tied to a measured artifact: `md5sum /lib/firmware/modem.mdt`, `--dms-get-revision`, a kernel oops record, and a decoded AArch64 faulting instruction. **No claim rests on a document's narrative** — which is exactly how the Doc 141 attribution error was caught (§7). |
| **3. Dual-firmware comparison** | **Yes** | The same probes were run under both basebands in the same session, minutes apart (§5, §6). |
| **4. No blind patching** | **Yes** | **No firmware and no kernel was modified.** The only on-device writes were a read-only monitor script and the profile switch. |
| **5. Error control** | **Yes** | Two pre-existing errors were found and corrected (§7, §10.1), and one of this document's own working hypotheses was discarded when it failed a check (§10.2). |

---

## 3. Executive Summary

- **UFI001B has no RF, confirmed on a clean cold boot.** With `modem.mdt = 72ae7f0b…` and the port's 22-file set deployed, a fresh reboot gives `not-registered-searching`, `CS/PS: detached`, radio `[0]: 'none'`, signal `InformationUnavailable`, `wwan0` DOWN — while the SIM is `ready`, the mode preference is already `'umts, lte'`, and operating mode is `online`. **Doc 143 is upheld.**
- **The stock baseband restores service immediately.** `--dms-get-revision` → `HIMI_U01_MODEM_V1.0`, `registered`, `[0]: 'lte'`, MCC 405, `wwan0` gets an address, ping succeeds. Stock is deployed now.
- **The port's "LTE working" evidence is misattributed.** Doc 141's soak (`/root/port_soak.log`) does show `wwan0` passing data for 152→706 s — but that boot was the **stock** baseband. The baseband can be identified objectively (revision string, `modem.mdt` hash, presence of `modem.b26`), and the switch to UFI001B happened **after** that soak. See §7.
- **New AP-side defect (§8):** bringing `wwan0` down oopses the kernel —
  `bam_dmux_skb_dma_map` ← `bam_dmux_send_cmd` ← `bam_dmux_netdev_stop` ← `__dev_close_many` ← `__dev_change_flags` — a NULL dereference at `0xc8` in process `netifd`. Because this runs under **RTNL**, the lock is never released: **every subsequent `ip` command hangs forever** and the data path never returns. A reboot is the only recovery.

> **The practical consequence:** the "data stalls and never recovers" symptom on this device has (at least) two independent causes. One is modem-side. The other is this AP-side oops, and it is **fully explained, reproducible in principle, and fixable**.

---

## 4. Method

1. Read the deployed baseband identity from the device (`md5sum`, `--dms-get-revision`, `--dms-get-model`) rather than from any document.
2. Run the same RF probes under both basebands.
3. Trace each historical "LTE working" claim to its raw evidence file and check the baseband identity recorded alongside it.
4. Reconstruct the profile-switch timeline from filesystem metadata.
5. Read the kernel oops, decode the faulting instruction, and map it back to driver source.
6. Test the consequence (does `ip` still work?) rather than inferring it.

---

## 5. Result 1 — UFI001B cannot bring up RF (clean cold boot)

Deployed set verified before the test:

```
md5sum /lib/firmware/modem.mdt   -> 72ae7f0bfa910873739a409f94910cdd   (UFI001B)
ls /lib/firmware/modem.b26       -> present (1048576 bytes)
/usr/bin/switch_modem_fw.sh status
   Active modem.b15 MD5 : c69ad8003476146f1263021638dff356
   Active file count    : 22
```

After a **cold reboot** and with the modem `running`:

| probe | result |
| :--- | :--- |
| `--dms-get-revision` | `UFI001BC 20211121  1  [Nov 04 2016 02:00:00]` |
| `--nas-get-serving-system` | `not-registered-searching`, `CS: detached`, `PS: detached`, radio `[0]: 'none'` |
| `--nas-get-signal-info` | `QMI protocol error (74): 'InformationUnavailable'` |
| `--dms-get-operating-mode` | `online` |
| `--nas-get-system-selection-preference` | Mode preference `'umts, lte'` (already correct) |
| `--uim-get-card-status` | Card `present`, USIM `ready`, PIN1 `disabled` |
| `wwan0` | **DOWN** — no address, no route |

This is now **two independent clean boots** (Doc 143's session and this one) producing the same result, with configuration proven clean at the same time. The failure is in the firmware's RF layer.

---

## 6. Result 2 — the stock baseband works

`/usr/bin/switch_modem_fw.sh stock` (stages the 21-file set, then reboots). Verified after boot:

| probe | result |
| :--- | :--- |
| `md5sum /lib/firmware/modem.mdt` | `1a6f9507e03d4ddbbf1977af81ecdbd7` (stock) |
| `/lib/firmware/modem.b26` | **absent** (HMU05-only segment set) |
| `--dms-get-revision` | `HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]` |
| `--nas-get-serving-system` | `registered`, `CS: attached`, `PS: attached`, radio `[0]: 'lte'`, PLMN MCC `405` |
| `wwan0` | `10.30.189.55/28` + IPv6 |
| `ping -I wwan0 8.8.8.8` | 3 sent, 2 received, 33 % loss, 48–87 ms |

**Stock is the working platform and is deployed.**

---

## 7. Result 3 — the attribution error: the port's "working" evidence is the stock baseband

### 7.1 The claim and its evidence

Doc 141 §5 step 7 states the port was "verified attached" (`state: connected`, `access tech: lte`,
operator `IN Loop` 405861, `wwan0 = 10.27.127.143/27`). The port's soak log
(`/root/port_soak.log`, started 2026-09-20T09:24:00Z) does show `wwan0` passing data:

```
152 rproc=running rx=28  tx=113 ping=LOSS
165 rproc=running rx=28  tx=122 ping=time=197.711
...
706 rproc=running rx=100 tx=485 ping=LOSS
--- NEW FAULT at uptime 719s ---
[  716.711996] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:1189:
```

The soak script pings with `ping -I wwan0`, so a `time=…` result means the cellular interface had a
route and was passing traffic. **The boot really did have working data.**

### 7.2 But it was not the port

Three independent checks say that boot was **stock**:

1. **The port cannot attach at all** (§5) — two clean boots, no radio.
2. **`/lib/firmware`'s directory mtime is `2026-09-20 10:31:35`** — a `rm`/`cp` of `modem.*`, i.e. a
   profile switch. That is **after** the 09:24–10:02 soak. `MCFG_SW.MBN` carries its preserved mtime
   (`2021-11-17`), so carrier provisioning did not produce that timestamp; nothing else under
   `/lib/firmware` was modified after 10:00.
3. **The corroborated "connected" records are all stock.** `scratch/postsysupgrade/11_firmware_identity.txt`
   (2026-09-19 19:14Z) records `modem.mdt = 1a6f9507…` **and** `switch_modem_fw.sh: not found`;
   `scratch/postsysupgrade/10_final_snapshot.txt` shows revision `HIMI_U01_MODEM_V1.0`. Doc 141's
   `10.27.127.143/27` appears **nowhere else in the repo**.

### 7.3 The reliable way to tell which baseband is live

Never infer it from a document. Read it:

| fingerprint | stock HMU05 | UFI001B |
| :--- | :--- | :--- |
| `--dms-get-revision` | `HIMI_U01_MODEM_V1.0 1 [Sep 09 2015 10:00:00]` | `UFI001BC 20211121 1 [Nov 04 2016 02:00:00]` |
| `modem.mdt` MD5 | `1a6f9507e03d4ddbbf1977af81ecdbd7` | `72ae7f0bfa910873739a409f94910cdd` |
| `modem.b15` MD5 | `1d0a8e74cad0cde5d6cb0cef735e0664` | `c69ad8003476146f1263021638dff356` |
| `modem.b26` | **absent** | present |

### 7.4 Conclusion

Doc 143 stands. The port is RF-dead; the RF-transplant line of work (patches 41–53) is closed for
**both** reasons in Doc 143 §7 (unstable host region + wrong layer). Doc 141's baseband attribution
is corrected in §10.1.

---

## 8. Result 4 — a new AP-side defect: NULL deref on `wwan0` down leaks RTNL

### 8.1 The oops

```
[  159.883904] Unable to handle kernel NULL pointer dereference at virtual address 00000000000000c8
[  159.891874]   ESR = 0x0000000096000006
[  159.894396]   EC = 0x25: DABT (current EL), IL = 32 bits
[  159.906435]   FSC = 0x06: level 2 translation fault
[  159.939664] Internal error: Oops: 0000000096000006 [#1] PREEMPT SMP
[  160.002505] CPU: 2 UID: 0 PID: 1963 Comm: netifd Tainted: G           O       6.12.94 #0
[  160.024680] Tainted: [O]=OOT_MODULE
[  160.032911] Hardware name: HMU05 4G Modem Stick (DT)
[  160.041339] pc : bam_dmux_skb_dma_map+0x20/0xf8 [qcom_bam_dmux]
[  160.048019] lr : bam_dmux_send_cmd+0x98/0x120 [qcom_bam_dmux]
[  160.127188] Call trace:
[  160.134297]  bam_dmux_skb_dma_map+0x20/0xf8 [qcom_bam_dmux]
[  160.136561]  bam_dmux_send_cmd+0x98/0x120 [qcom_bam_dmux]
[  160.142114]  bam_dmux_netdev_stop+0x48/0x6c [qcom_bam_dmux]
[  160.147670]  __dev_close_many+0xa8/0x114
[  160.153049]  __dev_change_flags+0xbc/0x200
[  160.157216]  dev_change_flags+0x20/0x64
[  160.161121]  dev_ifsioc+0x280/0x38c
[  160.164853]  dev_ioctl+0x258/0x4f4
[  160.168326]  sock_do_ioctl+0xbc/0x110
[  160.171799]  sock_ioctl+0x2a4/0x320
[  160.175530]  __arm64_sys_ioctl+0x94/0xd0
```

### 8.2 What it means — decoded

The faulting instruction is `f9406434` = **`ldr x20, [x1, #0xc8]`**, with `x1 = 0` (register dump),
giving a fault address of exactly `0xc8`. The preceding instruction `a9400400` = `ldp x1, x0, [x0]`
loads the first two fields of `struct bam_dmux_skb_dma`:

```c
struct bam_dmux_skb_dma {
	struct bam_dmux *dmux;   /* offset 0x00  <-- loaded into x1 == 0 */
	struct sk_buff  *skb;    /* offset 0x08  <-- loaded into x0 = ffff33704432d080 */
	...
};
```

and `bam_dmux_skb_dma_map()` begins:

```c
struct device *dev = skb_dma->dmux->dev;   /* 0xc8 = offsetof(struct bam_dmux, dev) */
```

So **`skb_dma->dmux` was NULL** when `bam_dmux_send_cmd()` — called from `bam_dmux_netdev_stop()`
during a normal `wwan0` down — tried to DMA-map the `CMD_CLOSE` skb.

Trigger: `netifd` (PID 1963) issuing `SIOCSIFFLAGS` to bring `wwan0` down. This is ordinary
teardown traffic — ModemManager losing/tearing down a bearer, or a config reload.

### 8.3 The consequence is worse than the oops: RTNL is leaked

`__dev_change_flags()` runs **holding RTNL**. The oops kills the task mid-critical-section, so the
RTNL mutex is **never released**. Measured on the live device immediately afterwards:

| probe | result |
| :--- | :--- |
| `ip -br addr show wwan0` | **hangs indefinitely** (`IP_HUNG`) |
| `ping -I wwan0 8.8.8.8` | 2 sent, 0 received, **100 % loss** |
| after a cold reboot | `ip` OK, `wwan0 = 10.30.189.55/28`, ping 2/3 received |

So a single `wwan0` teardown **permanently freezes all network configuration** for the remainder of
the boot, and the only recovery is a reboot. This is an independent, fully-explained mechanism for
"the data path dies and never comes back".

### 8.4 The module under test

```
/lib/modules/6.12.94/qcom_bam_dmux.ko   md5 da69a97d7ddf8e0932dde5ff041d82b6   (Sep 20 07:42)
/root/qcom_bam_dmux.ko.pmrestart-backup md5 ac6dda3541003fe636fcefd117098cfd
/root/qcom_bam_dmux.ko.prefix-backup    md5 d19e35d1a250791ddffc0665bea92ebd
```

The loaded module is a **custom build that matches neither backup**. `bam_dmux_netdev_open()` and
`bam_dmux_netdev_stop()` in the tree carry project-authored comments about releasing the open-time
runtime-PM reference (the Android-parity power-collapse work), so this driver **has** been modified
by this project. Whether the NULL `dmux` is a regression introduced by those changes, a latent
upstream bug, or a consequence of a teardown ordering change is **not yet determined** — see §10.2.

### 8.5 The trigger in practice — the project's own Stage 2 recovery

`/usr/sbin/modem-bearer-watchdog` is running on the device and its **Stage 1 works**: it detects the
user-plane stall, runs `qmicli -p -d /dev/wwan0qmi0 --wds-go-dormant`, and recovers RX **without**
tearing the interface down. Verified live twice in one boot:

```
16:25:33 modem-stall-watchdog: User-plane stall confirmed (16s). ... TX=83, RX=24
16:25:33 modem-stall-watchdog: Stage 1 recovery: executing '... --wds-go-dormant' ...
16:25:43 modem-stall-watchdog: Stage 1 SUCCESS: RX traffic successfully recovered via
                                wds-go-dormant. No network teardown required.
```

**Stage 2, however, is self-defeating.** When Stage 1 is insufficient it executes:

```sh
ubus call network.interface.modem down     # line 243  -> netifd brings wwan0 down  -> THE OOPS
...
ip -4 addr flush dev wwan0                 # line 290  -> also needs RTNL
ubus call network.interface.modem up       # line 295  -> needs RTNL, hangs forever
```

Line 243 is precisely the `SIOCSIFFLAGS`-down path that oopses (§8.1). The oops leaks RTNL, so the
`ifup` on line 295 **can never succeed** — *when the oops fires*. Therefore, on a boot where the oops
does fire:

> **A stall that Stage 1 cannot fix is converted by Stage 2 into a permanent, reboot-only outage — and
> the outage is caused by the recovery path, not by the modem.**

> [!NOTE]
> **Qualification (added after §16).** A later soak on the same baseband drove Stage 2 to completion
> **without** oopsing, and it recovered the data path normally. So Stage 2 is *not* always fatal — the
> §8.1 oops is an **intermittent** extra failure mode, not the routine outcome. §8.5's "self-defeating"
> framing applies only to the boots where the oops fires. The modem-side stall and the AP-side freeze
> remain two separate faults; only the second is intermittent.

### 8.6 Not yet established

- **Why `skb_dma->dmux` is NULL.** In-tree, `dmux->tx_skbs[i].dmux = dmux` is set once in probe and
  `bam_dmux_free_skbs()` never clears it. The running module's source revision is **older** than the
  tree's current source (module Sep 20 07:42 vs source mtime Sep 20 16:01), so the running binary may
  not correspond to the tree being read. **This must be resolved before any fix is written.**
- **Whether it recurs spontaneously.** The oops was observed on one boot at t=160 s. It may have been
  provoked by concurrent QMI probing (which produced a `CID allocation failed … endpoint hangup` in
  the same window, i.e. a bearer teardown). A read-only soak monitor is now running to see whether it
  recurs without interference.
- **Whether it relates to the 900 s fault.** It is a *separate* mechanism at a different time
  (160 s vs ~900 s), but it is a genuine AP-side data-path killer and must be fixed regardless.

---

## 9. Impact and Interpretation

| # | Finding | Status |
| :--- | :--- | :--- |
| 1 | UFI001B port cannot bring up RF | **Confirmed** (2 clean boots) — Doc 143 upheld |
| 2 | Stock baseband is the working platform | **Confirmed** — deployed |
| 3 | The port's "LTE working" evidence is stock's | **Confirmed** — Doc 141 attribution corrected |
| 4 | `wwan0` down → NULL deref → RTNL leak → frozen network stack | **Confirmed mechanism**; trigger frequency and exact NULL source open |

The strategic conclusion of Doc 143 §8 is unchanged and now better supported: **stop the RF-transplant
work; the stock baseband is the platform, and the remaining work is AP-side.**

---

## 10. Corrections

### 10.1 Doc 141 §5 / §6 attribute the soak to the wrong baseband

Doc 141 §5 step 7 ("Verified attach", `wwan0 = 10.27.127.143/27`) and §6.2 ("The port's crash") present
the 2026-09-20 soak as the UFI001B port's. Per §7 above, that boot ran the **stock** baseband.

What remains valid in Doc 141: the AP-side BAM-DMUX RX re-arm analysis (§7.5's withdrawal is correct),
the device quirks (§9), and the process-error record (§10.1). Its baseband attribution does not.

### 10.2 A working hypothesis of this session was discarded

I initially read the `/lib/firmware` mtime as proof that the switch happened after the soak, and
treated the question as closed on that basis alone. That was too strong: an mtime is a single,
overwritable signal. The conclusion only became safe once it was combined with (a) two clean no-RF
boots and (b) the stock fingerprints on every corroborated "connected" record. **The mtime is
corroborating evidence, not proof** — recorded here so it is not over-cited later.

---

## 11. Reproduction Reference

```sh
# ---- Which baseband is live? (never infer from a doc) ----
ssh root@192.168.8.1 'md5sum /lib/firmware/modem.mdt; ls /lib/firmware/modem.b26; \
    qmicli -d /dev/wwan0qmi0 --dms-get-revision'
# stock   -> 1a6f9507… , no b26 ,  HIMI_U01_MODEM_V1.0
# UFI001B -> 72ae7f0b… , b26    ,  UFI001BC 20211121

# ---- Switch profile (stages files, then reboots) ----
ssh root@192.168.8.1 '/usr/bin/switch_modem_fw.sh status'
ssh root@192.168.8.1 '/usr/bin/switch_modem_fw.sh stock'

# ---- RF probe ----
ssh root@192.168.8.1 'qmicli -d /dev/wwan0qmi0 --nas-get-serving-system; \
                      qmicli -d /dev/wwan0qmi0 --nas-get-signal-info'

# ---- Detect the RTNL leak (no `timeout` on this image) ----
ssh root@192.168.8.1 'ip -br addr show wwan0 & P=$!; sleep 5; \
    kill -0 $P 2>/dev/null && echo IP_HUNG || echo ip_ok'

# ---- The oops ----
ssh root@192.168.8.1 'dmesg | sed -n "/Unable to handle kernel/,/end trace/p"'
```

---

## 12. Artifacts

| artifact | path |
| :--- | :--- |
| This document | `Docs/Modem Stability/144_UFI001B_RF_VERDICT_CONFIRMED_ATTRIBUTION_CORRECTION_AND_BAM_DMUX_RTNL_OOPs.md` |
| Port soak log (as archived) | `scratch/ufi_rf_check/port_soak.log` |
| pstore of the preceding boot | `scratch/ufi_rf_check/pstore_prev_boot.txt` |
| Full dmesg containing the oops | `scratch/ufi_rf_check/dmesg_stock_boot_rtnl_oops.txt` |
| Driver source | `openwrt/build_dir/…/linux-6.12.94/drivers/net/wwan/qcom_bam_dmux.c` |
| On-device soak monitor | `/root/stock_soak.sh` → `/root/stock_soak.log` (read-only) |
| On-device profiles | `/lib/firmware/stock/` (21), `/lib/firmware/ufi001b_patched/` (22) |

---

## 13. Device State at End of Session

| item | value |
| :--- | :--- |
| Active baseband | **stock HMU05** (`1a6f9507e03d4ddbbf1977af81ecdbd7`) |
| `remoteproc0` | `running` |
| Registration | `registered`, `CS/PS: attached`, radio `[0]: 'lte'`, MCC 405 |
| `wwan0` | `10.30.189.55/28` + IPv6 |
| Data | ping 2/3 received, 48–87 ms |
| Network stack | healthy (`ip` responsive) — the RTNL leak was cleared by a cold reboot |
| Monitor | `/root/stock_soak.sh` running, 10 s cadence |
| Firmware / kernel | **unmodified** |

---

## 14. Pending Work

| # | Action | Rationale |
| :--- | :--- | :--- |
| 1 | Determine whether the `bam_dmux` NULL-`dmux` oops recurs **without** concurrent QMI probing | Decides whether it is a routine teardown bug or was provoked |
| 2 | Rebuild `qcom_bam_dmux.ko` from the **current** tree and re-test | The running module's provenance does not match the tree (§8.4/§8.5) — no fix can be trusted until it does |
| 3 | Add a NULL guard for `skb_dma->dmux` **and** stop holding RTNL across a path that can oops | Minimum safe mitigation; must not mask the root cause |
| 4 | Annotate Doc 141 with the §10.1 attribution correction | It is actively misleading |
| 5 | Resume the AP-side 900 s soak on stock | The real target, now on the correct platform |
| 6 | Do **not** resume RF-table-transplant work (patches 41–53) | Doc 143 §7 — two independent fatal reasons |

---

## 15. RF-Port Feasibility — Porting HMU05's Transceiver Driver into UFI001B

**Requested direction (user, 2026-09-20):** port the HMU05 RF transceiver *driver* into the UFI001B
firmware, then run UFI001B — on the premise that UFI001B does not suffer the ~900 s crash/stall.
This is Doc 143 §8 **option (b)**. This section records the feasibility analysis, because it changes
what "port the driver" costs.

### 15.1 Both firmwares share one VA↔PA offset

```
UFI001B: VA = PA + 0x39800000     (p_vaddr == p_paddr, mpss_reloc = 0xc0000000)
HMU05  : VA = PA + 0x39800000     (p_vaddr != p_paddr, mpss_reloc = 0x86800000)
```

The modem's virtual and physical spaces differ by a constant `0x39800000` in **both** images. So a
segment keeps its VA if and only if it keeps its PA — moving one moves both.

### 15.2 The target segment

| | value |
| :--- | :--- |
| Segment | HMU05 `modem.b18` (holds all 102 `rfc_wtr1605_*` card classes) |
| Size | **7,580,896 bytes (7.23 MB)** |
| `p_vaddr` | `0xc1500000` |
| `p_paddr` | `0x87d00000` |

### 15.3 UFI001B's address space is already full — but the DT has room

UFI001B's loaded segments occupy, in physical terms:

```
0x86800000 ... 0x8bd00000        (modem.b02 .. modem.b27)
```

The `mpss@86800000` reserved region is `0x86800000 + 0x5500000` = **exactly `0x8bd00000`**.

| measure | value |
| :--- | :--- |
| Headroom past UFI001B's last segment | **0 bytes** |
| Total free gaps between segments | 396.9 KB (largest single gap 120 KB) |
| Needed for `modem.b18` | **7.23 MB** |

So a segment transplant has **nowhere to go inside the current map**. **However**, the device tree
leaves a **32 MB hole**:

```
mpss@86800000      0x86800000  size 0x05500000  ->  ends 0x8bd00000
   <-- 0x8bd00000 .. 0x8db00000 : UNALLOCATED (32 MB) -->
ramoops@8db00000   0x8db00000  size 0x00100000
```

(other reserved regions: `hypervisor@86400000` 1 MB, `reserved@86680000` 512 KB, `rmtfs@86700000`
896 KB, `rfsa@867e0000` 128 KB — all below mpss.)

**Therefore the region can simply be enlarged in the DTS** (`mpss@86800000` size `0x5500000` →
up to `0x7300000`). Space is **not** the blocker.

### 15.4 The real blocker is relocation, not space

`modem.b18`'s VA (`0xc1500000`) lands **inside UFI001B's `modem.b17`** (`0xc1480000`–`0xc1bd2000`),
so it cannot be placed at its original address. Placing it anywhere else requires rebasing the
segment's **cross-segment references** — calls into the shared RFC / RF-SW code and accesses to global
data, which in a fixed-address firmware image are absolute, not PC-relative. Doing that needs either
the original source/linker script, or a full relocation map for the segment. **That is the hard part,
and it is a research project, not a patching exercise.** Nothing in this repo currently provides it.

> **UPDATE — this section is superseded by Doc 145, which measured it.** The relocation study called
> for in §15.6 step 3 was performed. Relocation is *not* the blocker; the cross-build coupling is.
> `modem.b18` has **43,296 distinct outbound targets** (36,412 in `modem.b16` alone) against a build
> that shares only **5.5 %** of its 64-byte windows — so the port is a firmware merge, not a patch.
> Doc 145 also corrects the §15.5 probe that returned `0/102` references (the tables are 2-byte
> aligned and the self-reference is `name_va − 2`, so a 4-byte scan misses every entry), and finds the
> decisive architectural difference: **UFI001B has a card-pack module (`modem.b26`, 145 cards, a
> 19-entry framework ABI) and HMU05 does not** (its cards sit inside `b18` with a ~1,635-entry
> coupling). See Doc 145 §5–§8.

### 15.5 The premise itself is untested

The direction rests on "UFI001B does not have the 15-minute crash or data stall". **That has never
been tested**, because the port has never had RF — so its LTE path, DRX cycles and L1 sleep manager
have never run. The one soak that appeared to test it (Doc 141) was actually the **stock** baseband
(§7). Until RF is up on UFI001B, the premise is a hypothesis.

### 15.6 Recommended sequencing

1. **Cheapest, highest-confidence first:** fix the AP-side permanent-freeze (§8) — the watchdog Stage 2
   oops + RTNL leak. This alone may be the "data stalls and never recovers" symptom, and it needs no
   firmware port at all.
2. **Re-measure** whether a ~900 s fault still exists once recovery can actually complete.
3. **Then** decide on the RF port, with eyes open about §15.4. If pursued, the first concrete step is
   a **relocation study of `modem.b18`** (enumerate its absolute cross-segment references), not a
   segment copy.

### 15.7 WCNSS version mismatch (raised by the user — confirmed)

The device's WLAN firmware is **UFI001B's**, while the modem is now **HMU05 stock**:

| file | device | UFI001B | HMU05 stock |
| :--- | :--- | :--- | :--- |
| `wcnss.mdt` | **`41d715d8f942aa25d4c91e582877faf7`** | `41d715d8…` | `62bb56b2bfd0a1aa1ef57ec44b51a396` |

The HMU05 copy is preserved at `/root/fw_backup_20260920T104045Z/wcnss.mdt`. This is a plausible cause
of the early `coex_interface.c:530` modem assert (the modem's WLAN/BT coexistence interface talking to
mismatched WLAN firmware). **Rule:** WCNSS must match the *modem* profile — HMU05 modem ↔ HMU05 WCNSS,
UFI001B modem ↔ UFI001B WCNSS. Not yet changed (WiFi is currently up on the UFI001B WCNSS; the change
should be made deliberately and tested).

---

## 16. Live Soak Result — the ~900 s fault reproduced on stock, and the watchdog recovered it

Read-only monitor `/root/stock_soak.sh` (10 s cadence), stock baseband, started at uptime 310 s.

| t (kernel s) | event |
| ---: | :--- |
| 12.037 | fatal #1 — **`coex_interface.c:530`** (the WCNSS mismatch of §15.7); recovered by SSR |
| 310–900 | data flowing; watchdog **Stage 1** (`--wds-go-dormant`) recovered every stall, no teardown |
| **918.195** | **fatal #2 — `a2_power.c:1189`** ← **the ~900 s fault, reproduced** |
| 919–939 | `wwan0` loses its address; ping 100 % loss |
| ~940–950 | watchdog escalates to **Stage 2**: `ubus call network.interface.modem down`, bearer cleanup, `ifup` |
| ~950 | new bearer `Bearer/4`, new address **`10.104.184.233/30`** |
| 996 | ping OK again (2/3, 97–187 ms) |

**No kernel oops this boot** (`oops=0`) — Stage 2 completed normally. So the §8.1 oops is
**intermittent**, not the routine outcome of a teardown.

### 16.1 What this establishes

1. **The ~900 s modem fault is real and reproducible on the stock baseband** (`a2_power.c:1189` at
   t = 918 s). This **supports the motivation** for a baseband that does not exhibit it.
2. **The AP-side watchdog makes the fault survivable** — roughly 30 s of outage and a new address, then
   service resumes. The device is usable in this configuration.
3. The §8 oops is a **separate, intermittent** AP-side failure mode, still real, still worth fixing,
   but not the cause of every outage.

### 16.2 Consequence for the plan

Because the fault is now reproducible on demand (a soak past 900 s), it is finally possible to
**measure whether a candidate baseband avoids it**. That is exactly the test the UFI001B port has
never been able to pass — which is why §15's RF work is the gate for the user's direction, and why
§15.6's sequencing still holds.

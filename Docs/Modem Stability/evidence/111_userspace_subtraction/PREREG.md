# PRE-REGISTRATION — Task #111, the OpenWrt userspace subtraction

**Written:** 2026-09-22T12:10Z (host), device uptime **14576 s**, boot `eafa19f6-5a01-4ea8-b783-52e9f16bf862`
**Written BEFORE any manipulation.** The device had not been touched when this file was created.

---

## 1. The question

**Is OpenWrt's modem-facing userspace *necessary* for the ~903.675 s / 400-DRX-cycle modem fatal?**

This is the subtractive counterpart to the Android-vs-OpenWrt differential. If the fatal survives with
the userspace removed, the fault is localised to the kernel/driver/modem state machine and every
userspace hypothesis dies at once — cheaply, in one boot.

## 2. Baseline (in-boot control, measured before the manipulation)

| quantity | value |
| :-- | :-- |
| fatals | **16** |
| intervals between fatals | **15** |
| intervals that produced a fatal | **15 of 15 — one per interval, unbroken** |
| last fatal | `lte_ml1_common_timer.c:390` at **14169.427048 s** |
| signature split | `lte_ml1_common_timer.c:390` 12 · `a2_power.c:1189` 4 |
| `pc_timeout_count` | 4 (all four on `a2_power.c:1189`) |
| `pc_vote_tx_count` / `pc_unvote_tx_count` | 267 / 266 |
| `pm_resume_attempts` / `pm_suspend_attempts` | **267 / 266** |
| `pc_ack_irq_count` | 525 → deficit `267+266−525 = 8 = 2 × 4` timeouts |
| `pc_irq_count` (modem's pc-line edges) | 597 |
| `pc_resync_count` / `lost edge` | 0 / 0 |
| `pc_state` / `pc_line_level` | 1 / 1 |

The equality `pc_vote_tx_count == pm_resume_attempts` and `pc_unvote_tx_count == pm_suspend_attempts`
is the **proof that the vote is driven only by runtime-PM transitions** (step 1, verified in the driver:
`bam_dmux_pc_vote()` has exactly two callers, 2035 and 2062).

**Predicted epoch boundaries** (last fatal + 902 … 963 s, the observed interval range):

```
epoch 1:  15071.4 .. 15132.4 s
epoch 2:  15973.4 .. 16095.4 s
epoch 3:  16875.4 .. 17058.4 s
```

## 3. The manipulation

Remove the modem-facing userspace **while keeping `netifd` and `usb0` up**, because `usb0` is the SSH
path (`root@192.168.8.1`) and stopping `netifd` would cut the observer.

```
ifdown modem                      # network.interface.modem, l3_device wwan0, proto modemmanager
/etc/init.d/ModemManager stop
/etc/init.d/qcom-time-daemon stop # running -r 0, i.e. inert, stopped for completeness
```

`network.interface.modem` is the modem bearer; `network.interface.lan` owns `usb0` and is left alone.

## 4. The assertion that the manipulation TOOK

A control that silently did not take is worse than none. **The objective evidence is that the AP→modem
PM interface goes quiet:**

* **primary:** `pm_resume_attempts` / `pm_suspend_attempts` stop advancing (they were +1 per ~52 s),
  so `pc_vote_tx_count` stops advancing too;
* `ps` shows `ModemManager` and `qcom-time-daemon` gone;
* `ubus call network.interface.modem status` reports `"up": false`.

If the counters keep advancing, **the manipulation did not take and the run is void** — it is not a
null result.

## 5. Predictions

* **P-OW1 — the fatal persists.** ≥1 fatal inside the 3-epoch window, same signatures, landing in the
  predicted epoch bands. This is the *expected* outcome and it is the informative one.
* **P-OW2 — the vote goes flat.** `pm_resume_attempts` advances by ≪ 1 per epoch (baseline ≈ 17 per
  903.675 s epoch).
* **P-OW3 — the modem keeps running its own clock.** `pc_irq_count` (the modem's own pc-line edges)
  continues to advance even with the AP userspace gone, because the modem's 400-DRX-cycle epoch is its
  own.

## 6. Falsifier of H0 ("userspace is not necessary")

**H0 is falsified by:** ≥3 consecutive epochs with NO fatal, **with §4's assertion verified present**.

**Honest power statement — this direction is weak.** The baseline is 15/15 intervals, whose
Clopper-Pearson 95 % lower bound is p ≥ 0.782. Under p = 0.782, three clean epochs have probability
**0.475** — i.e. *not* significant. Rejecting p ≥ 0.782 at α = 0.05 needs **13 clean epochs ≈ 3.3 h**.

So, pre-registered:

* a fatal inside the window → **H0 confirmed decisively and the run ends**;
* 3 clean epochs → **suggestive only**; extend to **13 epochs** before claiming any effect;
* and the restore leg (§7) is a **positive control**, not a substitute for the epoch count.

## 7. The restore leg (A2)

Restart the three services and observe **2 epochs**:

* fatal returns → the manipulation is causally implicated (strong, because it is the same boot);
* fatal does not return → the clean window was time-drift, not the manipulation.

## 8. What this experiment cannot establish

* It says nothing about the ~903.675 s clock or the latched offset (Doc 179) — only about whether the
  fatal needs userspace.
* It does not reach the leading *structural* candidate (AP-side boot firmware, `hyp`/`tz`), which is
  not a process and cannot be stopped.
* It cannot separate "the fatal needs the data plane" from "the fatal needs any AP→modem PM activity":
  `ifdown modem` removes both. If the fatal persists, that is fine — the null is what is being tested.
* One boot. Boot-to-boot reproducibility is not claimed.
* It will not fix the vote-vs-wake architectural differential found in step 1; at best it shows whether
  that differential is load-bearing.

## 9. Observer

Host-side 10 s sampler (the device's own `/tmp` is tmpfs and dies with a reboot), writing to the repo
tree, capturing: uptime, fatal count, `pc_timeout_count`, `pc_resync_count`, `pc_vote_tx_count`,
`pc_unvote_tx_count`, `pc_ack_irq_count`, `pc_irq_count`, `pm_resume_attempts`, `pm_suspend_attempts`,
`pc_state`, `pc_line_level`, `runtime_status`, plus the manipulation marker.

---

## ADDENDUM A — manipulation executed (appended AFTER, predictions NOT edited)

**Executed at device uptime 14660.08 – 14682.95 s.** `/etc/init.d/ModemManager` does not exist; the
script is lowercase **`modemmanager`**. The full stop set, all `rc=0` except `sms_manager` (137, i.e.
already gone):

```
ifdown modem
/etc/init.d/{modemmanager,modem-bearer-watchdog,modem-keepalive,modem-led-monitor,
              hmu05-modem-pm,qcom-carrier-autocfg,sms_manager,msm-firmware-dumper,qcom-time-daemon} stop
```

Kept deliberately (the modem cannot function without them, and their removal would confound the null
with a "the modem cannot work" fatal): **`rmtfs`**, **`netifd`**, **`dropbear`**.

### A.1 The manipulation TOOK — §4's assertion is satisfied

| uptime | `pm_resume_attempts` | `pc_vote_tx_count` | `pc_irq_count` | `mm_alive` | `iface_up` |
| --: | --: | --: | --: | --: | --: |
| 14626.64 | 268 | 268 | 606 | 4 | 1 |
| 14657.36 | 272 | 272 | 615 | 4 | 1 |
| 14667.61 | 273 | 273 | 616 | 4 | **0** |
| 14688.04 | **273** | **273** | **616** | **0** | 0 |
| 14769.93 | **273** | **273** | **616** | 0 | 0 |

Every counter is **frozen for the 80 s after the stop**, `pc_state: 0`, `runtime_status: suspended`.
The AP→modem PM interface is silent. **The run is valid.**

### A.2 P-OW3 is FALSIFIED, and it corrects step 1's framing

**P-OW3 predicted `pc_irq_count` (the modem's own pc-line edges) would keep advancing.** It does not —
it freezes at 616 with everything else.

So the `pc` line is **not** an independent modem-initiated wake request: it toggles as part of an
**AP-initiated** handshake. With no AP votes there are no edges. **Step 1's "the modem asks for wakeups
the AP does not answer" framing is therefore too strong** — the verified differential is narrower and
still real: **Android's vote is driven by `ul_wakeup()` (uplink events), OpenWrt's only by
runtime-PM transitions**, and `bam_dmux_pc_irq()` acks the modem's line without re-arming the vote.
Whether the modem can *initiate* is not established by this run and needs its own test.

**Consequence for the null:** the AP is now not merely "without userspace" but **completely quiesced
with respect to the modem** — no votes, no resumes, no edges, the data plane down, the modem collapsed.
That makes the test *stronger*, not weaker: if the fatal still fires, no AP→modem activity is required
to produce it.

**One confound to carry:** with the AP never voting power-on, the modem sits permanently collapsed. If
the fatal does *not* fire, that could be because the 400-DRX-cycle evaluation does not run in the
collapsed state, rather than because userspace was removed. That reading is not excluded here.

---

## ADDENDUM B — OUTCOME: H0 CONFIRMED on epoch 1

**The fatal fired at 15073.101717 s — inside the predicted epoch-1 band (15071.4 … 15132.4), and
0.3 ms from the beat prediction `14169.427048 + 903.675 = 15073.102`.**

```
[15073.101717] qcom-q6v5-mss 4080000.remoteproc: fatal error received: lte_ml1_common_timer.c:390:
[15073.101925] remoteproc remoteproc0: crash detected ... handling crash #17
[15073.125014] bam_dmux: SSR before shutdown: scheduling teardown work
[15073.131006 .. 15073.212410]  SSR teardown T0 .. T9   (all present)
[15073.235928 .. 15073.308423]  q6v5-trace 01 .. 10
[15073.300282] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
```

**The counters at the moment of the fatal — and at uptime 15302.87, i.e. 230 s later:**

| counter | at T0 (14657) | at fatal (15073) | at 15302.87 |
| :-- | --: | --: | --: |
| `pc_vote_tx_count` | 273 | **273** | **273** |
| `pc_unvote_tx_count` | 273 | **273** | **273** |
| `pm_resume_attempts` | 273 | **273** | **273** |
| `pm_suspend_attempts` | 273 | **273** | **273** |
| `pc_ack_irq_count` | 538 | **538** | **538** |
| `pc_irq_count` | 616 | 618 | 618 |
| `pc_timeout_count` | 4 | 4 | 4 |

**So the fatal fired, and ran its entire SSR recovery, with ZERO power-collapse votes and ZERO runtime-PM
resumes.** The AP never interacted with the modem's power path, and the modem asserted on the beat
anyway.

**⇒ H0 is CONFIRMED: OpenWrt's modem-facing userspace is NOT necessary for the fatal.** Per §6 the run
ends here. All nine services stopped, the bearer down, the data plane gone, the AP quiesced — and the
fatal is unchanged in timing and signature.

### B.1 This also weakens step 1's lead

`pc_vote_tx_count` did not move across the fatal. So the vote-vs-wake architectural differential found in
step 1 (`ul_wakeup()`-driven voting on Android vs runtime-PM-driven on OpenWrt) is **not necessary for
the fatal** — the fatal fires with the vote path completely idle. The differential remains a real,
verified driver difference; it is **not** the cause of this fault.

### B.2 What the fatal DOES still need

It needs the **AP's kernel** — the SSR teardown, `q6v5-trace`, and the TrustZone `SCM mba_perm reclaim`
all ran, and the modem reloaded. So the remaining candidates are the kernel/driver state, the AP-side
boot firmware (`hyp`/`tz`), and the modem's own state machine with the RPM. Userspace is out.

---

## ADDENDUM C — EPOCH 2 IS CLEAN, AND THE RPM COLLAPSED 30× AFTER FATAL #17

**This is the result the pre-registration did not anticipate, and it is not evidence against H0.**

### C.1 Epoch 2

Beat prediction `15073.101717 + 903.675 = 15976.777`, band 15973.4 … 16095.4.

```
uptime 16174.18   fatal count STILL 17   (197 s past the beat, past the whole band)
```

**No fatal.** The counters remained frozen at `pc_vote_tx_count` 273 / `pm_resume_attempts` 273.

### C.2 The RPM ring collapsed immediately after fatal #17 — measured, not inferred

`rpmring -i 20` capture, frozen at uptime 16183.7 s, binned per 60 s (`changed_words` summed):

| window (s) | changed words /min | non-zero samples /min | |
| --: | --: | --: | :-- |
| 14880 – 15060 | 31,472 / 31,612 / 32,931 | 98 / 102 / 98 | pre-fatal baseline |
| **15060 – 15120** | **34,997** | **200** | **fatal #17 @ 15073** |
| 15120 – 15180 | 1,681 | 6 | ← collapse |
| 15180 – 15300 | 1,099 / 1,066 / 942 | 4 / 2 / 3 | |
| 15300 – 15900 | 941 … 1,199 | 4 / 4 / 2 | steady low |
| **15960 – 16020** | **945 / 945** | **4 / 4** | **epoch-2 beat @ 15976.8 — flat, no burst, no fatal** |
| 16080 – 16140 | 927 / 976 | 3 / 2 | |

**A ~30× sustained collapse, from ~32,000 changed words/min to ~1,000, beginning at fatal #17 and never
recovering.** The epoch-2 beat passed with the ring completely flat.

### C.3 The reading — and why the clean epoch does NOT falsify H0

The two readings are:

* **(a)** the RPM activity is *necessary* for the fatal — consistent with Docs 178/179, where the
  `a2_power` fatal's precursor is the RPM's power-collapse re-evaluation stopping;
* **(b)** both are consequences of a third thing — **after the 15073 reload the modem came back with no
  data plane and no userspace to provision it, so it never re-entered its active (registered / DRX)
  regime**, and neither the 400-DRX-cycle epoch nor the RPM activity runs in a quiescent modem.

**(b) is the better-supported reading**, because it explains the *delay*: epoch 1 fired at 15073 because
the modem was still in the regime established **before** the manipulation (its last reload was fatal #16
at 14169, with userspace present); it is only the reload *after* the manipulation that returns the modem
to a state the userspace is not there to restore.

**So the honest synthesis is narrower than "userspace is irrelevant":**

> Userspace is **not the proximate cause** of the fatal — epoch 1 fired with every modem-facing service
> stopped and zero votes/resumes. But userspace **maintains the modem's active regime**, and the fatal
> is a property of that regime plus the RPM. Remove userspace and the fatal stops *after one epoch* —
> not because the cause was removed, but because the modem fell out of the regime in which the cause
> operates.

**The PREREG falsifier (§6) is not met and does not apply:** it required a clean window *from the start
of the manipulation* with the assertion present. Here the assertion **is** present, and the first epoch
produced a fatal — H0 confirmed. The later clean epoch is a **regime change**, not a null result.

### C.4 This reframes the whole differential

The question is no longer "what does OpenWrt's userspace do to the modem". It is:

> **What is different about the modem's *active regime* on OpenWrt?** The fatal is a property of that
> regime, and Android runs the same regime for 2723 s without ever asserting.

Which puts the AP-side boot firmware (`hyp`/`tz`) and the kernel/driver state back in front, and makes
the next experiment the **restore leg**: bring the userspace back and see whether the fatal returns. If
it does, that is a causal A/B/A inside one boot.

---

## ADDENDUM D — EPOCH 3 CLEAN, AND THE CLEAN EPOCHS ARE **CONFOUNDED**

### D.1 Epoch 3

Beat prediction `15073.101717 + 2 × 903.675 = 16880.452`, band 16875.4 … 17058.4.

```
uptime 17071.45   fatal count STILL 17   (past the whole band)
```

**No fatal.** So the sequence is:

| epoch | beat (s) | outcome |
| --: | --: | :-- |
| 1 | 15073.102 | **FATAL at 15073.101717 — 0.3 ms from the beat** |
| 2 | 15976.777 | clean |
| 3 | 16880.452 | clean |

### D.2 The confound: the modem's AP-facing path never recovered after fatal #17

Checked at uptime 17230 – 17240 s, i.e. **~2150 s after the fatal and ~150 s after the restore**:

```
ifstatus        "up": false, "autostart": false, errors: [{ subsystem: "modemmanager",
                                                            code: "MM_CONNECT_FAILED" }]
wwan0           DOWN
pc_state        0        pc_line_level  0
runtime_status  suspended
pm_resume_attempts 273   (frozen — unchanged since 14657)
pc_vote_tx_count   273   (frozen)
rx_tearing_down 1        rx_rearm_count 0
dmesg           "RX watchdog: quiesced 2160s (pc_state=0, pc_line=0, rx=held, ring disarmed)"
```

The modem is **running** (`rproc=running`) but **unreachable**: its A2 power-control line is low, it does
not answer QMI, and `ModemManager` reports `MM_CONNECT_FAILED`.

**⇒ The clean epochs 2 and 3 CANNOT be attributed to the manipulation.** They coincide with the modem
being out of the regime entirely — unreachable, collapsed, and with the RX ring never re-armed. This is
the same failure class as the patch-814 "SSR powerup gave up" defect, and `pc_line_level: 0` means it is
**not** §8's `pc_line_level: 1 / pc_state: 0` tell either. It needs its own investigation.

### D.3 The restore leg is VOID

`ifup modem` returned `rc=0` but the interface did not come up (`up: false`, `MM_CONNECT_FAILED`), so the
modem never re-entered the regime and the A2 leg of §7 could not be run. **This is not a null result and
not a positive control — it is an untested leg.** It must be repeated after a fresh boot.

### D.4 A new, signature-linked recovery difference

`SSR powerup: successfully reinitialized BAM channels and rings` appears **exactly four times** in the
whole boot — at **525.323701, 5950.318477, 8720.129599, 12363.367529** — i.e. **after the four
`a2_power.c:1189` fatals and after NONE of the thirteen `lte_ml1_common_timer.c:390` fatals.** The two
signatures recover by different paths. Recorded, not explained; it is a clean lead and it is orthogonal
to this experiment.

### D.5 What step 2 actually established

**Established:**

* **The fatal does not need OpenWrt's modem-facing userspace.** Epoch 1 fired at 15073.101717 s — 0.3 ms
  from the beat — with nine services stopped, the bearer down, and `pc_vote_tx_count` and
  `pm_resume_attempts` frozen at 273 throughout, including across the fatal's own SSR. **H0 confirmed.**
* **The fatal does not need the vote path either.** The step-1 differential (Android's `ul_wakeup()`-driven
  voting vs OpenWrt's runtime-PM-driven voting) is a real, verified driver difference, but the fatal fires
  with zero votes. **It is not the cause of this fault.**
* The fatal still needs the **AP's kernel**: the SSR teardown T0–T9, the `q6v5-trace` sequence and the
  TrustZone `SCM mba_perm reclaim` all ran, and the modem reloaded.

**NOT established:**

* Whether the fatal needs the modem's *active regime*. Epochs 2 and 3 are clean but confounded (§D.2), so
  the regime reading of §C.3 is **untested**, not supported.
* The restore leg (VOID, §D.3).
* Anything about the ~903.675 s clock or the latched offset (Doc 179).
* Boot-to-boot reproducibility: one boot.

### D.6 Next

1. **Re-run the restore leg on a fresh boot** — the confound in §D.2 makes the current boot unusable for
   it.
2. **Investigate §D.4** — why only the `a2_power` fatals reinitialize the BAM channels. This is cheap,
   already measured, and points straight at the `a2_power` path.
3. **The AP-side boot-firmware swap** (`hyp`/`tz`) remains the leading structural candidate and is the one
   thing this experiment could not reach.





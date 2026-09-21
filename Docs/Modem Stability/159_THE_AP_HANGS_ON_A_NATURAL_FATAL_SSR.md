# 159 — THE AP HANGS ON A *NATURAL* FATAL-TRIGGERED SSR, at a new site (after `port failed halt`)

**Date:** 2026-09-21. Times are **AP `/proc/uptime` seconds** unless stated. The device RTC is
unreliable; the conversion to kernel time is stated where both exist.

**Status:** **observed, controlled, and localised to a 43–47 ms window — not yet root-caused.** A boot
that survived **20** fatal-triggered SSRs hung on the **21st** and was reset by the hardware watchdog.
The console stops dead between `port failed halt` and `MBA booted without debug policy, loading mpss`.
`port failed halt` itself is **normal** (21/21 in that boot, and the same message precedes 20
successful recoveries), so the message is the last thing printed, not the cause. The hang lands at a
**different site** from the Doc 151 §5 `echo stop` hang, which is the important new fact: the hang
class is **not** an artefact of `echo stop`, it is reachable from the ordinary crash-recovery path.

The AP-side driver was **not changed** for this doc. The deployed module is the patch-814 production
build, md5 `eca269f10a685a83a8b679bc7be38d1d`.

---

## 1. Why this doc exists

Everything the corpus knows about this hang comes from **`echo stop`**. Doc 151 §5 recorded it once and
explicitly declined to claim reproducibility; Doc 157 §8.2 recorded it a second time (n=2) and drew the
practical conclusion — *"an AP-initiated modem teardown is not available as a test route"*. Both
observations share a hidden premise: **the hang was only ever seen when a human asked the AP to stop
the modem.** If that premise were true, the hang would be a testing hazard and nothing more.

This boot falsifies the premise. The hang happened during the **crash-recovery path the modem triggers
by itself**, with nobody touching `remoteproc0/state`. That promotes it from "don't do that" to a real
field failure: on this boot, the device was up for **4.45 hours**, took **21** modem fatals, recovered
from 20 of them, and then lost the whole AP — data, ssh, everything — on the 21st.

It is also, and this is the uncomfortable part, **the failure mode the user actually reports**: the
device does not come back until it is power-cycled.

## 2. SOP compliance

| SOP element | this session |
| :-- | :-- |
| Dual-firmware comparative protocol | **N/A** — no firmware built, patched or transplanted. This is a post-mortem of the AP console; the deployed baseband was not touched. |
| Ground truth from source, not narrative | **Applied.** The corpus (memory quirk #11, Doc 151 §5, Doc 157 §8.2) had the *symptom* and a *site*. The site was re-derived from `qcom_q6v5_mss.c` rather than inherited — and it turns out the inherited site is a different one (§4). |
| Read the definition, not the name | **Applied.** `"port failed halt"` reads like the cause; `q6v5proc_halt_axi_port()` (`:953-978`) shows it is an `dev_err()` on a *timeout-then-still-not-idle* check, printed **21/21** in this boot, including on the 20 recoveries. A message is not a cause until its rate is known. |
| Check the call sites, not just the body | **Applied.** The window is not one function. It spans the **tail of `q6v5_mba_reclaim()`** and the **head of `q6v5_mba_load()`**, so the enumeration in §3 is by call order, not by function boundary. |
| Measure on the live device, before and after | **Applied, and it is the whole basis of this doc.** 21 crashes, 21 `port failed halt`, 21 `MBA booted` expected and 20 seen; and a same-signature control (crash #19) that survived in 47 ms. Nothing here is inferred from a single event. |
| Minimal, reversible change | **N/A** — no change was made. The only device-side write was rebooting it, which the watchdog had already done. |
| Record what was done, the result, and what is next | §3 the window, §4 the site, §5 the control, §6 candidates, §7 consequences. |
| Never classify a fault by its `file:line` | **Applied, twice over.** Crash #19 and crash #21 carry the **same** signature (`a2_power.c:2949`) and one survived while the other hung; and the fatal record is a mutable global (Doc 152). See §6.1. |

## 3. What was observed

The boot in question ran from AP uptime 0 to **16030.9 s** (4 h 27 min) and took **21** modem fatals.
The console of that boot is preserved in pstore and is saved verbatim as
`evidence/159_natural_hang/console-ramoops-0.hang_boot.txt` (927 lines). Its final 12 lines are:

```
[15983.189475] bam-dmux ...: bam_dmux: SSR powerup: modem pc_state=1 (waited 200 ms)
[15983.189546] bam-dmux ...: bam_dmux: SSR powerup: channels already active
[16029.959308] bam-dmux ...: bam_dmux: RX watchdog: PC line asserted while pc_state=0 (lost edge), resyncing
[16030.033664] qcom-q6v5-mss 4080000.remoteproc: fatal error received: a2_power.c:2949:
[16030.033741] remoteproc remoteproc0: crash detected in 4080000.remoteproc: type fatal error
[16030.040638] remoteproc remoteproc0: handling crash #21 in 4080000.remoteproc
[16030.048652] remoteproc remoteproc0: recovering 4080000.remoteproc
[16030.055846] bam-dmux ...: bam_dmux: SSR before shutdown: scheduling teardown work
[16030.062482] bam-dmux ...: bam_dmux: executing serialized asynchronous SSR teardown
[16030.069956] wwan wwan0: port wwan0at0 disconnected
[16030.081169] wwan wwan0: port wwan0at1 disconnected
[16030.086196] wwan wwan0: port wwan0qmi0 disconnected
[16030.094840] remoteproc remoteproc0: stopped remote processor 4080000.remoteproc
[16030.803033] qcom-q6v5-mss 4080000.remoteproc: port failed halt
<nothing further>
```

and then the device is gone: the ssh session died, and it came back at uptime ~32 s after the hardware
watchdog reset it.

Three things are worth stating precisely.

* **No panic.** `kernel.panic = 3` is set, and a panic prints `Kernel panic - not syncing:` to the
  console. There is no such line, no `BUG:`, no `Unable to handle`, no `Internal error`, no
  `SMP: stopping secondary CPUs`, and no `reboot: Restarting system`. The console simply **stops**,
  which is what a hard hang in kernel context looks like when the hardware watchdog finally resets the
  SoC. (The two `dmesg-ramoops-*.enc.z` files in pstore are stale records from an earlier boot; this
  boot wrote none.)
* **The teardown ran to completion.** The AP got *past* `executing serialized asynchronous SSR
  teardown`, past the three port detaches, and past `stopped remote processor`. So the bam_dmux side
  finished; this is not the Doc 157 §8.2 site.
* **The last line is `port failed halt`, 708 ms after `stopped remote processor`.**

## 4. `port failed halt` is NORMAL — the control that makes this readable

The obvious reading of §3 is "the AXI halt port failed, and that is what hung the AP". That reading is
wrong, and the boot itself refutes it:

```
crashes (handling crash #N)       : 21
"port failed halt"                : 21
"MBA booted without debug policy" : 21     (1 of which is the initial cold boot)
```

So **all 21 teardowns printed `port failed halt`**, and 20 of them went on to print `MBA booted`
43–47 ms later and recover normally. The message is a `dev_err()` inside
`q6v5proc_halt_axi_port()` (`qcom_q6v5_mss.c:953-978`):

```c
	regmap_read_poll_timeout(halt_map, offset + AXI_HALTACK_REG, val,
				 val, 1000, HALT_ACK_TIMEOUT_US);   /* 100 ms */

	ret = regmap_read(halt_map, offset + AXI_IDLE_REG, &val);
	if (ret || !val)
		dev_err(qproc->dev, "port failed halt\n");
```

It means *the port did not report idle within 100 ms*. On this device, on this SoC, that is the normal
outcome of a modem that has just crashed — the port is not idle because the modem died holding it. The
kernel clears the halt request and carries on (`:977`).

**Consequence: `port failed halt` is the last line *before* the hang, not the cause of it.** It is a
landmark. It is useful precisely because it is reliable: 21/21.

## 5. Where the hang actually is — a 43–47 ms window

Because `port failed halt` prints 21/21 and the next print normally follows in 43–47 ms, the hang is
**bounded by two adjacent printk calls**, and the window between them is short enough to enumerate
completely from source. In normal operation the sequence is:

| # | call | where |
| :-- | :-- | :-- |
| — | `dev_err("port failed halt")` | `q6v5proc_halt_axi_port()` `:974` (end of the 4th halt) |
| 1 | `q6v5proc_disable_qchannel()` ×3 | `q6v5_mba_reclaim()` `:1278-1280` |
| 2 | `q6v5_reset_assert()` | `:1282` — `reset_control_assert` on the MSS resets |
| 3 | `q6v5_clk_disable()` ×2, `q6v5_regulator_disable()` | `:1284-1289` |
| 4 | **`q6v5_xfer_mem_ownership(mba_perm, …)` → `qcom_scm_assign_mem()`** | `:1294` — **a synchronous SMC into TrustZone** |
| 5 | `qcom_q6v5_unprepare()` | `:1299` — tears down SMP2P / the mbox |
| 6 | `qcom_q6v5_prepare()` | `q6v5_mba_load()` `:1081` |
| 7 | `q6v5_pds_enable()`, `q6v5_regulator_enable()` ×3, `q6v5_clk_enable()` ×2 | `:1085-1122` |
| 8 | `q6v5_reset_deassert()` | `:1126` |
| 9 | `q6v5_clk_enable(active_clks)` | `:1132` |
| 10 | `q6v5proc_enable_qchannel(qaccept_axi)` | `:1139` |
| 11 | **`q6v5_xfer_mem_ownership(mpss_perm, …)` → `qcom_scm_assign_mem()`** | `:1149` — **TrustZone, the mpss region** |
| 12 | **`q6v5_xfer_mem_ownership(mba_perm, …)` → `qcom_scm_assign_mem()`** | `:1157` — **TrustZone** |
| 13 | `q6v5proc_reset()` → `dev_info("MBA booted without debug policy, loading mpss")` | `:1174` and on |

That is the whole window: **three TrustZone ownership transfers, one reset assert/deassert pair, four
regulator operations, four clock operations, six TCSR regmap writes, and one SMP2P/mbox
teardown+setup.** Nothing in it is expected to take 40 ms, and none of it has a timeout that would let
the kernel recover — items 1–13 are all called from a context where a stall is fatal to the AP.

## 6. This is a DIFFERENT site from the Doc 151 §5 hang

The two must not be conflated, because they will need different fixes.

| | Doc 151 §5 / Doc 157 §8.2 hang | this hang |
| :-- | :-- | :-- |
| trigger | `echo stop` on `remoteproc0` (n=2, human) | **a modem fatal, crash #21 (n=1, spontaneous)** |
| last console line | `SSR before shutdown: scheduling teardown work` / `port wwan0at0 disconnected` | `port failed halt` |
| how far it got | **before** `executing serialized asynchronous SSR teardown` — the bam_dmux teardown had not started | **past** the teardown, past `stopped remote processor` |
| site | the remoteproc stop path, or the teardown work's leading `cancel_*` calls | the `q6v5_mba_reclaim` → `q6v5_mba_load` handover (§5) |
| AP side in common | both are in the modem stop/restart path, and both hang hard enough that only the hardware watchdog recovers the device | same |

So the corpus now has **two** distinct hard hangs in the modem restart path. The `echo stop` one is a
test-route hazard; **this one is a production failure**, and it is the more serious of the two.

## 6.1 The crash that hung

```
crash #1   at    920.654 s     crash #12  at   9141.433 s
crash #2   at   1822.754 s     crash #13  at  10043.516 s
crash #3   at   2726.108 s     crash #14  at  10945.587 s
crash #4   at   2905.614 s     crash #15  at  11847.656 s
crash #5   at   3809.623 s     crash #16  at  12751.969 s
crash #6   at   3887.021 s     crash #17  at  13654.684 s
crash #7   at   4791.380 s     crash #8   at   5694.093 s
crash #9   at   6617.013 s     crash #19  at  15078.987 s
crash #10  at   7194.275 s     crash #20  at  15981.084 s
crash #11  at   8237.592 s     crash #21  at  16030.041 s   <-- HUNG
```

Two things stand out.

* **Crashes #1–#20 are the ~902 s timer** (Doc 156), spaced 902.1–902.2 s apart. Crash #21 is **49.0 s**
  after crash #20 — an *activity-correlated* fatal, i.e. the modem was only 48 s into its life when it
  died again. The hang happened on the crash that came while the system was still settling from the
  previous recovery, not on a quiet 902 s boundary. **n=1, so this is a hypothesis, not a finding**, but
  it is the obvious thing to test: does the hang need a *second* fatal inside the recovery window?
* **Crash #19 has the same signature as crash #21** (`a2_power.c:2949`) and recovered normally in
  47 ms. So the signature does not select the hang, and neither does the fatal's `file:line` — which is
  the third independent confirmation of the corpus rule "never classify a fatal by its `file:line`"
  (the record is a single mutable global; Doc 152).

## 7. Candidates, and what is NOT established

**Not established:** the root cause. Nothing below is proven; they are ranked by how well they fit a
hard hang inside a 43–47 ms window.

1. **`qcom_scm_assign_mem()` — the three TrustZone ownership transfers (§5 items 4, 11, 12).** This is
   the leading candidate, for three reasons. (a) It is a **synchronous SMC** — the AP enters EL3 and
   waits; if TrustZone does not return, the AP stops, and the console stops with it, exactly as
   observed. (b) The modem is the *other* VMID owner of the very memory being handed back
   (`QCOM_SCM_VMID_MSS_MSA`; Doc 158 §5), and the modem has **just crashed** — so this is precisely the
   moment at which the handoff is most likely to be in an unexpected state. (c) It is the only
   operation in the window with no timeout and no error path the kernel could take.
2. **The SMP2P / mailbox teardown+setup (`qcom_q6v5_unprepare()` → `qcom_q6v5_prepare()`).** Two
   existing patches (815, 816) already touch the mbox/SMSM request path, so this area has a history.
   `prepare()` is where a `mbox_request_channel`-class call could block.
3. **Regulator enable/disable** — an SPMI/I2C bus transaction that never completes would hang the same
   way. Weaker, because the same path runs on all 21 crashes.
4. **`q6v5_reset_assert/deassert`** — reset-controller operations, which may sleep.

**A test that would discriminate cheaply:** the console is the only instrument that survives a hang
(this is why the pstore console record is so valuable), so add `dev_info()` traces at items 1–13 in a
**temporary** build, run the soak, and read the last line after the next hang. That is a one-line-per-
step change, needs no new tooling, and would pin the site to a single call. **This is the recommended
next step** (§8.2 item 1) and it is why this doc stops at "localised", not "root-caused".

**One thing that is established and should not be lost:** the hang is **not** caused by, and is not
fixed by, patch 814. Patch 814 does not touch `q6v5_mba_reclaim()` or `q6v5_mba_load()`; it is entirely
inside `qcom_bam_dmux`. This boot is the strongest possible evidence of that — it ran the patch-814
module for 4.45 hours and 20 successful recoveries before hanging.

## 8. Consequences and next

1. **`a2_power.c:2949` has now been observed.** The decode reference recorded it as *not observed*.
   It appears at crash #19 and crash #21 in this boot. The reference memory should be corrected.
2. **Next steps, in order:**
   1. **Instrument the §5 window** with `dev_info()` at items 1–13 in a temporary build, deploy, soak,
      and read the last console line after the next hang. This is the cheapest path to the root cause.
   2. **Test the "second fatal inside the recovery window" hypothesis** (§6.1) by recording, for every
      crash, the time since the *previous* crash — the soak harness does not currently capture this.
   3. Only then consider a fix. A hang with no timeout in the SCM handoff may not be fixable in the
      driver at all (it may need a TrustZone-side or ordering change), so **do not patch blind.**
3. **Do not "fix" this by returning an error early or skipping the ownership handoff.** The handoff is
   what protects mpss (Doc 158); skipping it would be a security-relevant change and would very likely
   make the modem fail to load instead.

## 9. Artifacts

| file | what |
| :-- | :-- |
| `console-ramoops-0.hang_boot.txt` | the console of the boot that hung, verbatim (927 lines) |
| `run6_dmesg_final.txt`, `run6_soak814.csv`, `run6_soak814.log` | the same boot's soak data (in `evidence/157_ssr_powerup_giveup/`) — 21 crashes, 538 samples |

## 10. One line

**A modem fatal on this device can hang the whole AP in the ~45 ms between `port failed halt` and
`MBA booted` — the message is normal (21/21), the window contains three untimed TrustZone ownership
transfers, and the hang is a production failure, not the `echo stop` testing hazard the corpus
previously recorded.**

## 11. Provenance

* Observed: 2026-09-21, from the pstore console of the boot that ended at AP uptime 16030.9 s.
* Device: HMU05, module `qcom_bam_dmux.ko` md5 `eca269f10a685a83a8b679bc7be38d1d` (patch-814
  production build), baseband verified clean stock HMU05.
* Source line numbers: `qcom_q6v5_mss.c` in
  `openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/`.
* Related: Doc 151 §5 and Doc 157 §8.2 (the `echo stop` hang — a *different* site); Doc 158 (mpss is
  TrustZone-assigned, which is why candidate 1 in §7 is credible); Doc 156 (the 902 s timer, which
  accounts for crashes #1–#20).

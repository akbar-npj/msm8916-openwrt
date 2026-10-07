# Patch scope audit — universal vs modem-revision-specific

**Question:** the msm89xx target builds five devices off one kernel patch tree
(`msm89xx/patches/` → `openwrt/target/linux/msm89xx/patches/`). Which patches are
**universal** (AP-side/SoC, no modem coupling) and which are **coupled to a modem
firmware revision** and must be gated?

**Trigger:** the HMU05 modem is `HIMI_U01_MODEM_V1.0` (2015, `MPSS.DPM.1.0.C7-00193`)
while UFI001B is `HIMI_U01_MODEM_V2.0` (`MPSS.DPM.2.0.2`). A patch tuned to one must
not silently ship on the other.

**Ground truth (decompiled modems, `Docs/Modem Stability/Modem RE/`):**

| board | decompiled | revision string | size | md5 |
|---|---|---|---|---|
| HMU05 | `hmu05/modem_full_decompiled.c` | `HIMI_U01_MODEM_V1.0` / MPSS.DPM.1.0.C7-00193 | 105 121 709 | `9e11084a5416ce0a36cc10a088da8c8c` |
| UFI001B | `ufi001b/modem_full_decompiled.c` | `MPSS.DPM.2.0.2` | 106 000 424 | `d5933376a0bf33721ab718aa807dad4f` |
| UZ801 | `uz801/modem_full_decompiled.c` | (no banner matched) | 110 552 970 | `76cb4cea7ee0aed056e9cdeabf49ed08` |

**Devices sharing the tree:** `yiming-uz801v3`, `generic-uf02`, `generic-ufi001b`,
`generic-hmu05`, `generic-mf800b`.

---

## Key finding — the A2 quiesce CODE is IDENTICAL in V1.0 and V2.0

The A2 power-up quiesce (`a2_power.c`) that the `a2_power.c:1189` fatal lives in is
**structurally identical** in both firmwares. Verified by reading the decompiled
function bodies (not just counting address references — the `0xec320bXX` block is a
fixed peripheral window, so an address grep alone proves nothing):

| A2 piece | HMU05 (V1.0) | UFI001B (V2.0) | same? |
|---|---|---|---|
| power-up quiesce (five client words `{0xec320ba4,ba8,bac,bd8,be0} & 7`, five spin loops) | `FUN_c0504fc8` @862621 | `FUN_c056c5a0` @904735 | **yes** |
| assert helper (`spin++`, `% 0x32`, `900 < budget` ⇒ assert) | `FUN_c05042c8` @862146 | `FUN_c056b5fc` | **yes** |
| budget reset (exactly one site, inside the collapse) | `DAT_c28602fc = 0` @862803 | `DAT_c200f830 = 0` @904917 | **yes** |

⇒ The A2 quiesce asymmetry (`A2_QUIESCE_ASYMMETRY.md`) is **not** an HMU05-only quirk;
V2.0 carries the **same latent code defect**.

**But code-identity is necessary, not sufficient, for exposure.** The fatal is a
runtime condition, not a code property — see *Correction (2026-10-07)* below. This
distinction is the whole point: the AP-side patches are not coupled to a modem
behaviour that *differs* by revision, but neither does identical code imply the fatal
actually fires on the other board (it does not — UFI001B ran 12 h+ with a bearer).

---

## Classification

### A. Universal — keep target-wide

These fix **AP-side or SoC-level** defects. The AP driver and the MSM8916 platform are
identical on every board, so the defect (and its fix) does not depend on the modem
firmware.

| patch | touches | why universal |
|---|---|---|
| 801–807 | board DTS (`*-hmu05/ufi001b/mf800b/uf02.dts`, Makefile) | per-device hardware description |
| 808 | bam-dmux driver infra (counters, `rx_telemetry`, teardown/powerup works, `pc_line_asserted`) | AP-side structure |
| 809 | `bam_dmux_send_cmd`/`start_xmit` PM ordering | AP-side UAF (observed `bam_dmux_skb_dma_map` NULL deref) |
| 810 | TX-sweep race guards | AP-side race |
| 811 | deferred-TX telemetry | AP-side diagnostics |
| 812 | preserve deferred TX slots | AP-side correctness |
| 813 | reboot-to-EDL (`qcom_scm`, `msm-poweroff`, DTS) | SoC |
| 814 | SSR powerup retry | AP-side recovery (polls the pc wire) |
| 815 | `qcom_sysmon` ignore wcnss/modem SSR | platform |
| 816 | `qcom_smsm` validate mbox before request | platform |
| 818 | pm8916 L13 voltage range (DTS) | platform |
| 819 | `rpmsg_char` NULL-eptdev guard | platform |
| 820* | SSR teardown non-blocking cancel | AP-side lock-order (ABBA) fix |
| 821* | SSR teardown flush before power-down | AP-side ordering fix |
| 822 | `qcom_smd` drain channel pollers before free | platform |
| 823 | `rpmsg_wwan_ctrl` don't register pollers | platform |
| 824 | rpm-master-stats (DTS) | platform/diagnostic |
| 825 | pc-state reconcile against the wire | AP-side (trusts hardware, protocol-correct) |
| 826 | `q6v5_mss` async `msm_subsys` restart node | platform |
| 827 | `q6v5_mss` GFMUX clk src switch | platform |
| 828 | ack the stale deassert | AP-side (adds the missing SMSM ack — protocol-correct) |
| 831* | defer DMA release to a powered modem | AP-side (BAM power-domain hang) |
| 832 | TX `tx_bytes` UAF | AP-side UAF |
| 999 | tsens eprobe-defer | platform |

### B. Modem-revision-coupled — gate

| patch | why coupled | action |
|---|---|---|
| 833 | adds an A2 **down-ack wait** in `bam_dmux_runtime_resume()`; only validated against the HMU05 modem, and on a modem that does not complete the down-ack it would add a 2 s stall to every resume | **GATED to HMU05** via `of_machine_is_compatible("hmu05,250605v0s")` (2026-10-07) |

**833 is the only patch whose behaviour is coupled to a specific modem's A2
handshake.** It was gated on 2026-10-07; the counters stay unconditional so the
telemetry remains comparable across boards. (`msm89xx/patches/833-…` md5
`29be455856e486bcb0528d22debf0123`, synced to the live tree.)

### C. Investigation artifacts — drop / strip

Debug probes left in the tree from the AP-hang investigation. They are **not**
modem-revision-specific, but they should not ship on any board: they are `dev_err`
(console/log spam on every teardown), and 821's own comment notes the probes cost
~40 ms of console time and made the teardown **lose the power-down race 2/2 times** —
i.e. they are actively harmful, not merely noisy.

| patch | artifact | action |
|---|---|---|
| 830 | `PS0`–`PS3b` probes (pure probe patch — nothing else) | **DROPPED 2026-10-07** (file removed) |
| 820* | `T0`–`T4` probes embedded in a real fix | **STRIPPED 2026-10-07** (fix kept) |
| 821* | `T5`–`T9` probes embedded in a real fix | **STRIPPED 2026-10-07** (fix kept) |
| 831* | 1 probe (`PS3b` text) embedded in a real fix | **STRIPPED 2026-10-07** (fix kept) |

(`*` = real AP-side fix with debug probes attached.)

**Cleanup (2026-10-07):** the three affected patches were regenerated from a
reconstructed clean upstream (`qcom_bam_dmux.c`, 910 lines) rather than hand-edited,
and the whole bam-dmux series (808→833, 830 dropped) was replayed from clean and
verified to reproduce the edited build tree byte-for-byte. The module rebuilds
(`648e178c77b82935b85b0e915ebc62ef`, 244 960 B, down from 246 768 B) with **no**
`teardown T`/`bam-dmux T`/`bam-dmux PS` strings and the `hmu05,250605v0s` gate
present.

---

## Recommendation

1. **833** — done: gated to HMU05.
2. **830** — done: dropped (pure debug probe; no functional content).
3. **820 / 821 / 831** — done: the embedded `T*`/`PS*` `dev_err` probes are stripped;
   the fixes are kept.
4. **Everything else** — leave target-wide; the AP-side and SoC fixes are
   modem-revision-agnostic.

### Correction (2026-10-07) — UFI001B does NOT manifest the fatal (strong negative)

An earlier draft of this section said UFI001B is **"likely exposed"** to the same
`a2_power.c:1189` fatal as HMU05 once it carries a data bearer. **That was an
over-claim** — it inferred exposure from code-identity alone.

Exposure is a **runtime condition**, not a code property. The fatal requires *all* of:

1. the A2 to **collapse** — the AP must runtime-suspend bam-dmux and vote the modem
   down (`bam_dmux_runtime_suspend()` is the only writer of `bam_dmux_pc_vote(false)`);
2. a client quiesce word to still be `& 7 != 0` at the next power-up;
3. the shared budget `DAT_c28602fc` to exceed 900.

The HMU05 rate (≈8 %/cycle; 5 fatals / 61 cycles / 488 s) was measured with a **data
bearer UP and `control=auto`**. Without traffic the A2 does not cycle and the fatal
does not appear — which is exactly why the §112.170 "`a2_pin=0` is safe" verdict was
drawn without data and later retracted.

**The empirical evidence is a decisive negative.** UFI001B ran **12 h+ with a data
bearer up and traffic** — the exact condition that kills HMU05 — and did **not** crash.
At HMU05's rate (≈1 fatal / 98 s) a 12 h run predicts **hundreds** of fatals; **zero**
were observed. So UFI001B's manifestation rate is < ~0.2 % of HMU05's, i.e. effectively
zero *as tested*.

**Residual confound:** that run was on a build **without the recent bam-dmux patches**.
If any of those changed the A2 runtime-suspend behaviour (e.g. made the device suspend
where it previously stayed active), the comparison is not apples-to-apples. The
definitive test would be the **current** image on UFI001B (bearer up, `control=auto`,
with `pc_vote`/`a2_power` telemetry) — but the practical call does not wait on it.

**Recommendation: do NOT extend `modem-a2-hold` to UFI001B.** The clean 12 h run is
strong evidence it does not need it. Read the revision difference as a **runtime
behavioural** one — V2.0 does not hit the race despite the identical A2 code — not as a
missing-workaround.

---

## Verification

```bash
# A2 quiesce reference count (a WEAK proxy: the 0xec320bXX block is a fixed
# peripheral window; expect 15 for each, but this alone proves nothing).
for d in hmu05 ufi001b uz801; do
  f="Docs/Modem Stability/Modem RE/$d/modem_full_decompiled.c"
  echo -n "$d: "; grep -c "ec320ba4\|ec320ba8\|ec320bac\|ec320bd8\|ec320be0" "$f"
done

# the STRONG check: the quiesce function body + assert helper are the same shape
grep -n "FUN_c0504fc8\|FUN_c05042c8" "Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c"
grep -n "FUN_c056c5a0\|FUN_c056b5fc" "Docs/Modem Stability/Modem RE/ufi001b/modem_full_decompiled.c"
# budget reset: exactly one site each, inside the collapse
grep -n "DAT_c28602fc = 0" "Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c"
grep -n "DAT_c200f830 = 0" "Docs/Modem Stability/Modem RE/ufi001b/modem_full_decompiled.c"

# 833 is gated (expect the compatible string)
grep -n "of_machine_is_compatible" msm89xx/patches/833-*.patch

# the debug probes still in the tree
grep -lE '^\+.*dev_err.*"bam-dmux (T[0-9]|PS)' msm89xx/patches/*.patch
```

**SOP statement.** Read-only audit + one gated patch (833). All claims are from the
patch contents and the three decompiled modems; the classification is by *what the
patch depends on* (AP/SoC vs modem handshake), not by its title. The 833 gate was
verified: the patch applies clean to the 832-state, reproduces the edited build tree,
and the module compiles (gate string `hmu05,250605v0s` present in the `.ko`). The V2.0
A2-identical finding is by reading the decompiled function bodies (quiesce, assert
helper, budget reset), **not** by the address grep alone. The exposure correction is
from the user's UFI001B soak observation plus the runtime-condition analysis in
`project_a2_power_fatal_needs_control_on.md`; UFI001B was **not** instrumented here.

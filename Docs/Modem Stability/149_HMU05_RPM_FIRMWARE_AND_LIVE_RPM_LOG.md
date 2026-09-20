# 149 — HMU05: the RPM firmware is readable, and the RPM's live log is readable from the AP over `/dev/mem`

**Status:** capability established and exercised; one new measurement channel opened; the
E1 traffic experiment closed with a **failed prediction that is itself the result**.
**Depends on:** `148_HMU05_FATAL_PERIODICITY_AND_SIGNATURE_TAXONOMY.md` (§3 is corrected here),
`Modem RE/hmu05/900S_CRASH_LPR_FRAMEWORK_RE.md` (§6.4/§6.6 — the `rpm.sync` stall).
**Corrects:** Doc 148 §3/§3.0/§3.0.1 (the "903.674 s timer" framing and the "the AP selects
which timer fires" inference), and Doc 148 §6.1's "≥10 distinct signatures" framing.
**Corrected by:** `150_HMU05_RPM_LOG_IS_LIVE_AND_ITS_CLIENT_IS_THE_MODEM.md` §3 — §5.2's read
method and §5.3's histogram/census are retracted, `[2]` is not constant, `+0x38` is the write
counter, and §7 experiment 1 has now been run (§8 there: **the RPM is alive through the fatal**).

---

## 1. Why this document exists

Doc 148 established that the fatal is periodic but framed the period as **903.674 s of AP
uptime**. The modem RE (`900S_CRASH_LPR_FRAMEWORK_RE.md` §3.3) had independently derived
**400 × 2.256 s = 902.4 s**. Those two numbers looked like a contradiction and the corpus
never reconciled them.

They are not a contradiction: **903.674 s of AP uptime = ~902.3 s of *modem* uptime + ~1.4 s
of SSR downtime.** §2 shows the decomposition from `dmesg` alone.

The second reason is bigger. The RE doc's §6.6 says the root cause is either Q6-side or
**RPM-side**, and argues that RPM-side is the only thing consistent with the AP-side
difference (the modem firmware is byte-identical Android vs OpenWrt). Until now the RPM was
a black box — only the modem ELF had been reversed. Two things changed that:

* `rpm.bin` is available as a real partition dump and **is a plain ARM ELF**;
* the RPM's **live log ring and message RAM are readable from the AP with `/dev/mem`**.

So the side of the `rpm.sync` stall that was previously unobservable is now observable.

---

## 2. The period decomposes: AP interval = modem uptime + SSR downtime

`dmesg` anchors, AP uptime seconds (deployed boot, 2026-09-20):

| # | modem boot (`...is now up`) | fatal | **modem uptime** | AP interval | signature |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | 12.092460 | 914.769287 | 902.676827 | — | `lte_ml1_common_timer.c:390` |
| 2 | 916.175824 | 1818.443149 | **902.267325** | 903.673862 | `lte_ml1_common_timer.c:390` |
| 3 | 1819.807165 | 2722.117987 | **902.310822** | 903.674838 | `lte_ml1_common_timer.c:390` |
| 4 | 2723.511313 | 3625.792806 | **902.281493** | 903.674819 | `lte_ml1_common_timer.c:390` |
| 5 | 3627.257122 | 4529.467002 | **902.209880** | 903.674196 | `lte_ml1_common_timer.c:390` |
| 6 | 4530.841763 | 5426.331445 | 895.489682 | 896.864443 | **`a2_power.c:1189`** |
| 7 | 5427.699310 | 6360.554391 | 932.855081 | 934.222946 | **`a2_power.c:1189`** |
| 8 | 6361.955365 | 7262.819515 | **900.864150** | 902.265124 | **`lte_ml1_sleepmgr_stm.c:4054`** |

* Modem-uptime period, fatals 2–5: mean **902.267380 s**, spread **0.100942 s = 112 ppm**.
* Fatal 1 is +0.41 s high because on the *first* boot the "is now up" anchor is later relative
  to the modem's timer start (full MBA + firmware bring-up); it is not a different period.
* **902.267 s is within 0.015 % of the RE doc's `400 × 2.256 s = 902.4 s`.** The model and the
  measurement agree; Doc 148's "903.674 s" was the AP-observed interval, which includes the
  ~1.407 s the modem spends down (`fatal → stopped` is only ~0.06 s; `fatal → is now up` is
  ~1.35–1.41 s).

**Correction to Doc 148 §3/§3.0:** the timer is **~902.3 s of modem uptime**, and the
"predictive" test in §3.0 was passed because the *sum* (modem period + SSR) is equally
predictable. The prediction was real; the mechanism description was one layer off.

---

## 3. E1 — the traffic experiment, and the prediction that FAILED

A 1 Hz ping to `8.8.8.8` was run from AP ~5310 s. Three phases:

| phase | AP window | signature | modem-uptime period |
| :-- | :-- | :-- | :-- |
| **A** idle | 12 → ~5310 | `lte_ml1_common_timer.c:390` ×5 | 902.267 s (112 ppm) |
| **B** 1 Hz ping | ~5310 → ~6401 | `a2_power.c:1189` ×2 | **895.490 s, then 932.855 s** |
| **A′** idle again | ~6401 → 7262 | `lte_ml1_sleepmgr_stm.c:4054` ×1 | **900.864 s** |

The pre-registered prediction (Doc 148 §3.0.1) for fatal #7 was 6330.006 s (idle cadence
resumed) or 6323.196 s (traffic cadence persists). **It landed at 6360.554391 s — 30.6 s and
37.4 s late respectively. Both models were wrong.** That is the result:

1. **Traffic suppresses the deterministic idle fatal.** In phase A the fatal is exact to
   112 ppm; in phase B it is not a fixed period at all (895.5 s, then 932.9 s — a 37 s spread).
2. **A different failure substitutes.** Under traffic the deterministic timer does not fire;
   `a2_power.c:1189` does, at a variable time. Phase A′ then returns to a ~900 s period.
3. **Therefore Doc 148 §3.0.1's inference is wrong.** It read the single phase-A→B transition
   as "the AP's activity pattern selects which of several ~900 s timers expires first". The
   A/B/A reversal shows something narrower: the *idle* watchdog is reset by activity, and a
   *separate, non-deterministic* failure appears under traffic.

### 3.1 The reported assert site is NOT a discriminator

Three different signatures fired in this one boot, all at ~900 s of modem uptime, and two of
them (`common_timer` in phase A, `sleepmgr_stm` in phase A′) fired in the **same idle
condition** on different boots.

This is direct evidence for `900S_CRASH_LPR_FRAMEWORK_RE.md` §5.2 (which said the two
signatures are one root event at different assert sites) and against Doc 148 §6.1's
"≥10 distinct fatal signatures" framing. It is also consistent with the coredump finding
already recorded: the ERR_FATAL descriptor at `0xC35B1290` is a **single mutable global
"last fatal" record**, not a per-assert-site object — its payload words vary at runtime.

**Consequence for all future work: never classify a fatal by its `file:line` string.** The
root event is the MCPM `system_sleep_check` Q6-PC-voting failure; the string is whatever the
global descriptor held at that instant.

---

## 4. The AP's A2 vote cadence does NOT set the period

Doc 148 §5 treated the AP's power-collapse cadence as a candidate lever. It is not, for the
*timing*:

| between fatals | AP `pc_vote_tx_count` delta | modem-uptime period |
| :-- | :-- | :-- |
| #1→#2 | ~29 | 902.267 s |
| #2→#3 | ~27 | 902.311 s |
| #3→#4 | ~114 | 902.281 s |
| #4→#5 | ~158 | 902.210 s |
| #5→#6 | ~131 | 895.490 s |

The AP's vote count varies **5.9×** while the modem-uptime period stays flat to 112 ppm (and
the boot even contains a 240 s stretch with **zero** votes — `pc_vote_tx_count` pinned at 46
from AP 345.71 s to 586.36 s). So the modem's timer is internal and is not paced by the AP's
A2 handshake. (This is consistent with the earlier "fault time INVARIANT to AP voting"
observation.)

---

## 5. NEW CHANNEL — the RPM firmware and the live RPM log

### 5.1 `rpm.bin` is a normal ARM ELF

```
$ file rpm.bin
ELF 32-bit LSB executable, ARM, EABI5 version 1 (SYSV), statically linked, no section header
$ md5sum rpm.bin
b8c7275042ebce4bde3a201423529e22
```

Source: `/home/shaanair/Projects/Extracted_Firmware/melbon/white/MelbonWhiteStock_Dump/rpm.bin`
(512 KB, from the Android `rpm` partition at GPT offset `0x8300000`). `rpmbak.bin` is present too.

Program headers (there are no sections — disassemble by segment, as with `modem.elf`):

| type | file offset | phys | filesz | flags |
| :-- | :-- | :-- | :-- | :-- |
| LOAD | `0x003000` | `0x00200000` | `0x1c094` | R E |
| LOAD | `0x01f094` | `0x00290000` | `0x056a0` | RW |

The RPM core is an ARM Cortex-M3, so **Thumb-2**. Working recipe:

```bash
dd if=rpm.bin of=rpm_code.bin bs=1 skip=$((0x3000)) count=$((0x1c094)) status=none
arm-none-eabi-objdump -D -b binary -m arm -M force-thumb --adjust-vma=0x200000 rpm_code.bin > rpm_code.asm
```

`arm-none-eabi-objdump` is installed; `llvm-objdump` also has an `arm` target.

Recovered strings confirm the RPM has its own sleep framework and the two low-power modes the
AP already sees:

| file off | VA | string |
| :-- | :-- | :-- |
| `0x195c0` | `0x2165c0` | `xosd` + `vmin` (adjacent — the two `qcom_stats` records) |
| `0x16584`/`0x16ad8`/`0x194e0`/`0x19730` | `0x213584` … `0x216730` | `/sleep/uber` |
| `0x1e6bd` | `0x21b6bd` | `/node/sleep/uber` |

Also recovered, and worth knowing because they are the RPM's own failure modes:
`Heap memory corrupted`, `invalid message` (`0x8ec4`), `invalid header` (`0x19418`),
`SPMI Fatal: Timeout while executing SPMI transaction` (`0xa9fc`).

### 5.2 The RPM's log ring is readable from the AP with `devmem`

> [!WARNING]
> **CORRECTED 2026-09-21 (Doc 150 §3).** The *addresses* below are right, but **`devmem` is the
> wrong way to read this ring and everything read that way is retracted.**
>
> Measured: a 2048-word `devmem` walk takes **5.94 s**, while the RPM overwrites all 256 ring
> slots in **0.5–3 s** (it writes in bursts of 100–200 records). So a `devmem` dump is not a
> snapshot — by the time the walk reaches index *i*, that slot has usually been overwritten.
> Proof: two dumps taken back-to-back shared **0 of 256 records**; two taken 36 s apart also
> shared 0.
>
> Also corrected: `+0x38` is **not** "—", it is the ring's **byte write counter**
> (`write_pos = (counter>>5)&0xFF`), which is what makes an ordered read possible; `+0x08` reads
> `"RPM External Log"` — this doc's "Exitrnal" is a misread of `0x65747845` = `"Exte"`.
>
> Use `rpmring` (`evidence/150_rpm_track/rpmring.c`), which mmaps and copies the ring in
> ~500 µs, or copies only the new records in ~25–140 µs. See Doc 150 §3.1.

The AP DT (`msm8916.dtsi`) gives the addresses:

```
rpm_msg_ram: sram@60000   { compatible = "qcom,rpm-msg-ram";        reg = <0x00060000 0x8000>; };
             sram@290000  { compatible = "qcom,msm8916-rpm-stats";  reg = <0x00290000 0x10000>; };
```

The RPM log structure is at **AP phys `0x29dc00`** (the downstream `qcom,rpm-log` node's
address). Header words read live:

| offset | value | meaning |
| :-- | :-- | :-- |
| `+0x04` | `0x00001000` | version |
| `+0x08` | `"RPM Exitrnal Log"` | magic/name (sic — the RPM firmware's own typo) |
| `+0x24` | `0x0009DC58` | log buffer address **in RPM space** (`+0x200000` = AP phys `0x29dc58`) |
| `+0x28` | `0x00002000` | log length (8 KB) |
| `+0x2c` | `0x00001FFF` | ring mask |
| `+0x38` | byte counter | **ring write counter** — `(counter>>5)&0xFF` = next record slot (Doc 150 §4.2) |
| `+0x3c` | byte counter − `0x2020` | tracks `+0x38` |

**The log ring itself is at AP phys `0x29dc58`, 8 KB, and it decodes as 256 fixed 8-word
(32-byte) records:**

| word | meaning |
| :-- | :-- |
| `[0]` | constant `0x00200000` (record marker / module base) |
| `[1]` | RPM timestamp |
| `[2]` | ~~constant `0x00000020`~~ — **CORRECTED: not constant**; takes a small set of values that change rarely (`0x24`→`0x25`→`0x26`, two changes in 314 s). Not decoded. Doc 150 §6.3 |
| `[3]` | **event id** |
| `[4..7]` | arguments — frequently 4-char ASCII resource names |

Reading it (there is no mainline driver; `/dev/mem` `read()` is blocked for this range but
`devmem`'s mmap path works, so it must be looped one word at a time):

```sh
# header
for i in $(seq 0 23); do devmem $((0x29dc00 + i*4)); done
# ring (2048 words)
i=0; while [ $i -lt 2048 ]; do devmem $((0x29dc58 + i*4)); i=$((i+1)); done
```

`dd if=/dev/mem bs=4096 skip=670 …` returns only ~112 bytes — **`/dev/mem` `read()` does not
work for this range; `devmem` (mmap) does.** Do not conclude the region is unmapped.

### 5.3 What the first dump already shows

> [!WARNING]
> **RETRACTED 2026-09-21 (Doc 150 §3).** The histogram and the ASCII census below come from a
> `devmem` dump, which is not a snapshot (§5.2 banner), so **the counts are not real**. They are
> additionally inflated by a name-detection bug: any word whose four bytes were printable was
> accepted as a resource name, which turned the sequence number `0x00003148` into `"H1.."` and
> the small integers `0x72`/`0x65`/`0x4d` into `"r..."`/`"e..."`/`"M..."` — roughly 40 % of the
> entries below are noise.
>
> The corrected census (147 s, lossless, `is_name` fixed) is in Doc 150 §6.2 and gives exactly
> the same *qualitative* conclusion: `ldoa`, `bslv`, `bmas`, `smpa`, `clk0/1/2`, `clka`, and no
> `vmin`/`xosd`/`cxo` anywhere. The qualitative finding survives; these numbers do not.

Event-id histogram over the 256-record ring, and the ASCII arguments recovered:

```
0x00cb x31  0x00cd x17  0x00d0 x18  0x00d1 x31  0x00d2 x32  0x00d4 x32  0x00d5 x33
0x0143 x17  0x0144 x17  0x0041..0x0044 x7/7/4/4  0x00d9..0x00e3 x1 each  0x0245 x1

ascii args:  "req." x32   "ldoa" x29   "bslv" x21   "clk1" x15   "bmas" x12   "clk2" x9   "smpa" x6   "clka" x6
```

The structure is a request transaction: `0x00d2` with arg `"req."` opens a request, `0x00d1`
carries `(enable, resource_name, value)`, `0x00d4`/`0x00d5` echo it, `0x00cb`/`0x00cd` bracket.
Example (records 237–239, verbatim):

```
237 id=0x00d1 ts=0x70641175 args=[0x1, 'ldoa', 0x12]
238 id=0x00d4 ts=0x706411c0 args=['ldoa', 0x12]
239 id=0x00d5 ts=0x70641258 args=['ldoa', 0x12]
```

**The important negative: there is no `vmin`, `xosd`, `cxo` or `misc` request anywhere in the
ring.** The resources the RPM is asked to service are `ldoa`, `clk1`, `clk2`, `clka`, `smpa`,
`bslv`, `bmas` — regulators and clocks only.

### 5.4 The RPM has never entered either of its low-power modes

```
$ cat /sys/kernel/debug/qcom_stats/vmin
Count: 0 / Last Entered At: 0 / Last Exited At: 0 / Accumulated Duration: 0 / Client Votes: 0x0
$ cat /sys/kernel/debug/qcom_stats/xosd
Count: 0 / Last Entered At: 0 / Last Exited At: 0 / Accumulated Duration: 0 / Client Votes: 0x5070507
```

Verified unchanged after **8 SSR cycles** and ~2 h of uptime. `qcom_stats` ioremaps
`sram@290000` and reads the RPM's own sleep counters (`drivers/soc/qcom/qcom_stats.c`), so this
is the RPM's own bookkeeping, not an AP-side counter.

This is significant because the modem's LPR chain contains a **`cxo`** LPR with a `shutdown`
step, and `FUN_c0ce7fe0` (the crash function) reads **`rpmvmin`** on the way to the fatal
decision (`900S_CRASH_LPR_FRAMEWORK_RE.md` §6.7). The RPM's VMIN/XOSD states are therefore
directly implicated, and neither has ever been reached on OpenWrt.

### 5.5 The AP never votes VMIN — Android does

| | Android (`GitIgnore/android_kernel_zte_msm8916`) | OpenWrt (deployed) |
| :-- | :-- | :-- |
| CPR VMIN method | `qcom,vdd-mx-vmin-method = <4>` on `apc_vreg_corner` | **absent** — `drivers/regulator/qcom-cpr.c` contains no `vmin` code at all |
| CPU sleep status | `qcom,cpu-sleep-status@b088008`, mask `0x40000` | **absent** (no node, no driver) |
| SAW/SPM | 4 × `saw2` nodes with full `qcom,saw2-spm-cmd-spc` command sequences | SAW2 nodes present, `qcom,saw` phandles present — **but unused**, see below |
| CPU idle path | SAW2/SPM programmed per idle entry | PSCI only |

The OpenWrt DT has everything `cpuidle-qcom-spm` needs **except the compatible**: the SAW2
nodes (`qcom,msm8916-saw2-v3.0-cpu`) and the `qcom,saw = <&cpuN_saw>` phandles are all present
in `msm8916.dtsi`, but `CPU_SLEEP_0` declares

```dts
CPU_SLEEP_0: cpu-sleep-0 {
    compatible = "arm,idle-state";     /* cpuidle-qcom-spm matches "qcom,idle-state-spc" */
```

so `spm_set_low_power_mode(drv, PM_SLEEP_MODE_SPC)` is never called
(`drivers/cpuidle/cpuidle-qcom-spm.c:47`). The CPUs *do* enter SPC — `cpuidle/state1`
("standalone-power-collapse") has 872 929 entries on cpu0 — but via PSCI, with the SPM never
programmed.

**This is a hypothesis, not a proven cause.** §6 says how to test it. It is recorded because it
is the only AP-side gap found so far that lands on exactly the resource the RPM reports as
never-entered.

---

## 6. What is established vs what is not

**Established (measured, this session):**
1. The fatal period is ~**902.3 s of modem uptime** (112 ppm over 4 samples); the 903.674 s AP
   figure is modem period + SSR downtime.
2. The AP's A2 vote cadence does not set it (5.9× vote variation, flat period).
3. Traffic suppresses the deterministic idle fatal and substitutes a variable one (E1 A/B/A).
4. The reported `file:line` is not a discriminator — 3 signatures at ~900 s, 2 of them in the
   same idle condition.
5. `rpm.bin` is a disassemblable ARM ELF; the RPM's live log ring and message RAM are readable
   from the AP with `devmem`.
6. The RPM has never entered VMIN or XOSD (8 SSR cycles, 2 h).
7. The AP never votes VMIN; Android does (CPR method 4).

**Not established (do not assume):**
* That the VMIN/XOSD gap *causes* the `rpm.sync` stall. §5.5 is a hypothesis.
* What the RPM log looked like *during* a stall. The ring covers far more time than one
  collapse, and no collapse-window capture has been taken yet.
* The RPM timestamp tick rate (needed to place log records on a wall-clock axis).
* Whether `cpuidle-qcom-spm` binding would change any RPM vote. §5.5 argues by mechanism only.

---

## 7. Next experiments, in priority order

1. **Capture the RPM log around a collapse.** Poll the ring at ~1 Hz for the ~30 s spanning a
   predicted fatal (the schedule is now predictable to <1 s), and diff. If the RPM logs
   `churning`-equivalent retries or stops logging for the modem, that separates Q6-side from
   RPM-side conclusively.
2. **Establish the RPM tick rate** by sampling the newest record's timestamp against AP uptime.
3. **Test the VMIN hypothesis cheaply and reversibly.** Add `"qcom,idle-state-spc",` to
   `CPU_SLEEP_0`'s compatible (one line, `msm89xx/patches/`), rebuild, and read
   `qcom_stats/vmin` — if the count becomes non-zero, the AP's SPM path is the missing vote and
   the fatal schedule should change. This is the smallest change that tests §5.5.
4. **Disassemble `/node/sleep/uber`** in `rpm.bin` to find what the RPM requires before it will
   enter `vmin`/`xosd`, and whether a client vote can block it.
5. Read `sram@60000` (RPM message RAM) during a stall to see whether the modem's request is
   present and unretired.

---

## 8. Artifacts

| what | where |
| :-- | :-- |
| RPM firmware | `/home/shaanair/Projects/Extracted_Firmware/melbon/white/MelbonWhiteStock_Dump/rpm.bin` (md5 `b8c7275042ebce4bde3a201423529e22`) |
| RPM code segment + disassembly | `scratch/rpm_re/rpm_code.bin`, `rpm_code.asm` (44 975 lines) |
| RPM strings | `scratch/rpm_re/rpm_strings.txt` |
| Raw RPM log ring dump | `scratch/rpm_re/rpm_log_dump.txt` (2048 words) |
| E1 traffic log (phases A/B) | `scratch/e1_traffic/e1_traffic.log` |
| Fatal #8 watcher log | `scratch/e1_traffic/fatal8_watch.log` |

---

## 9. One line

The fatal is a **~902.3 s modem-internal timer** (not 903.674 s, not AP-paced); traffic
suppresses it and substitutes a variable failure; the `file:line` string is a mutable global
and must never be used to classify a fatal; and the RPM — whose `rpm.sync` is where the chain
stalls — is now **readable both as firmware and as a live log from the AP**, and it reports
that it has never once entered VMIN or XOSD while the AP never votes VMIN at all.

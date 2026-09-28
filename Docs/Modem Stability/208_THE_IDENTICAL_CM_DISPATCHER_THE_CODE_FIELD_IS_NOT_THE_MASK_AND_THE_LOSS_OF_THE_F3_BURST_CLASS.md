# 208 — The CM dispatcher is identical, the F3 `code` field is not the mask, and the F3 burst class is gone

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed as **v19** — `modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt` md5 `9f39ce579114bcf1383bbdce62a8d284`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10 1 [Sep 07 2015]`. Guard label `doc208-class-test`.
**Status:** RESULTS — one new two-arm code measurement (the CM dispatcher), two instrument-model corrections (`code` semantics; the stock arm's instrument identity), **one falsified instrument hypothesis of my own**, and **one new blocker**: the F3 burst-capture class has not occurred for 20 consecutive boots and its cause is not the sweep, the enable speed, the firmware bytes, or the modem's persistent partitions.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`; its §3/§7/§8 are updated in the same session as this doc.
**Predecessors:** `200` (the online gate / the `dcc` break), `202` (the CM boot-path divergence), **`203`** (the capture-class confound — **§3.2 of which this doc qualifies**), `204` (the `u=1` race), `205` (the descriptor table / the op-mode refusal), `206` (`FUN_c06928a8` is `cmcc.c`), **`207`** (the corrected descriptor model / the death of the `cmcc.c` axis).

---

## 1. SOP compliance statement

| SOP step | Taken? | Detail |
| :-- | :-- | :-- |
| Verify against stock HMU05 ground truth | **Yes** | Every differential is two-arm. §3 diffs UZ801 `FUN_c067cd2c` against the stock function that carries the *same switch constants* (`FUN_c0659ef0`, located in the stock decompile by grepping for `0x100006a`, not by an address heuristic). §5's mask-identity claim is read from the archived **stock instrument log** `scratch/hmu05_stock_boot.log`, not inferred. §6's falsifications are all live-device measurements against the same device. |
| Byte-level / hash verification before attributing | **Yes** | The firmware on the device was md5-verified (`c71d5346…`/`9f39ce57…`) **before and after** every reboot block, and the guard's own manifest was read (`/overlay/modem_guard/deployed`, label `v19 (0x39e8 caller-fix c71d5346)`). Every capture's identity is its `out_md5` from the on-device `diagboot.status`, and §6.3's partition hashes are raw `md5sum` of `/dev/mmcblk0p{4,5,2,1}` taken across a boot. |
| Backup / reversibility | **Yes** | The sweep-64 instrument was copied to the host **before** it was changed (`scratch/ref_sweep64/diagboot_run.sh.sweep64`, md5 `d385dad0…`), and the original `cntl-enable` behaviour is preserved byte-for-byte in the new tool (`span == 1` reproduces `ssid s..s`). The firmware was **redeployed from the verified `scratch/uz801_patched` set** through the guard-aware deployer, and the persistent partitions were proven byte-stable (§6.3) so nothing was silently mutated. |
| No blind patching of the baseband | **Yes** | **No baseband byte was changed** — the one deploy in this session wrote the *identical* v19 bytes (every md5 in `/overlay/modem_guard/deployed` matches `scratch/uz801_patched`). The only new binary is a **host-side AP tool** (`diag_logtool_range`), which is an instrument, not firmware. |
| Reboot discipline / instrument-not-perturbing | **Yes, with a declared cost.** This session performed **20 device reboots** — 5 for the sweep-256 arm, 5 for the range-256 arm, 4 for the sweep-64 control, plus the deploy reboot and the partition-stability pair. Each was a clean `reboot` (the guard logged `clean shutdown`), the guard stayed armed throughout (`boot_count` = 0, no `BOOTLOOP_REVERT.txt`), and **no capture is attributed to a class without its `mmocdbg.c` count being read from the artifact itself**. The instrument (`diagboot`, `START=11`, procd, asynchronous) is unchanged from Doc 196 and did not perturb rcS. |
| Mandatory ledger update in the same session | **Yes** | Doc 197 §3/§7/§8. |

**Errors caught and corrected in this session — recorded rather than hidden:**

1. **My own hypothesis "a wider mask blinds the capture" was FALSIFIED by its own control** (§6.1). I had 5/5
   blind at sweep 256 *and* 5/5 blind at range-256, and nearly published "the sweep width drives the class".
   Restoring the sweep to 64 produced **4/4 blind as well** — the class was already gone. The control is the
   only reason a wrong finding did not enter the corpus.
2. **Doc 203 §3.2's evidence for retiring the sweep dimension was inadequate** (§8.1) — it compared two
   captures that were *both* blind, and a blind-vs-blind comparison cannot detect a sweep effect on the
   burst class. The *conclusion* happens to survive (§6.1 shows the sweep is not the cause) but for a
   different reason than the one recorded.
3. **The F3 record's `code` field has been read as the AP-selected SSID by this campaign.** It is not (§4).
   Doc 203 §3.1's `code`-bearing table and Doc 202 §6.1's `tsk` argument both inherit the misreading; the
   `tsk` conclusion is unaffected (it is read from the `cmdbg.c` text, not from `code`).

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Doc 207 §9 item 1 — take the `cmmsc_auto.c` question to the runtime via the CM dispatcher | a runtime instrument | **PARTIAL — the static half is done and it is a null.** The dispatcher is **structurally identical** between the builds (§3), so the `dcc` command's drop is *not* a dispatcher-code difference. The runtime half is **blocked** by §6: the admissible capture class no longer exists on this device | **PARTIAL** |
| Close "the CM code differs" once and for all | a verdict | **MET.** `FUN_c067cd2c` (UZ801, 1096 normalised lines) vs `FUN_c0659ef0` (stock, 1096): raw `difflib` similarity **83.49 %**, 115 differing blocks — but **every one of those blocks is a `unaff_GP + 0x…` global-layout offset**. After normalising GP offsets, **2 blocks remain, both the same single-line `int *piVar;` declaration move** (§3.1) | **MET** |
| Establish whether the stock arm's F3 mask matches the UZ801 arm's | a yes/no | **MET — it is the IDENTICAL instrument.** `scratch/hmu05_stock_boot.log` shows `--- cntl-enable 64 SSIDs at uptime=12.98 ---` and 64 `F3_MASK` packets with `ssid_first=ssid_last=0…63`, produced by the same `/root/diagboot_run.sh` at the same uptime. **⇒ the campaign's file-set differentials are mask-controlled** (§5) | **MET** |
| Determine what the F3 `code` field is | a semantics | **MET.** `code` is **not** the AP-selected SSID. `cntl-enable N` provably sweeps SSIDs `0..N-1` (§4.1), yet `code` is **4772 for N = 1, 4, 64 and 256** on one boot, and changes only across **boots** (`4756, 4764, 4765, 4766, 4769, 4771, 4772, 40240, 41261, 42100, 42140, 0`). It is a **modem-assigned per-boot value** (§4.2/§4.3) | **MET** |
| Re-confirm the campaign's load-bearing negative (`cmss.c`/`cmmsc_auto.c` never run on UZ801) | a verdict | **MET — and it is strengthened, not weakened.** §5.1 compares the two **boot chunks** under the *same mask*: stock's boot chunk contains `cmss.c` 18, `cmmsc_auto.c` 3, `mmoc.c` 4, `cmlog.c` 6, `cmregprx.c` 7, `sdss.c` 6, `sdcmd.c` 5; UZ801's burst boot chunk contains **zero** of them while carrying the *same* adjacent layers (`mmocdbg.c` 60, `cmdbg.c` 14, `a2_power.c` 8). The mask cannot be the explanation | **MET** |
| Restore the admissible capture class | a working instrument | **NOT MET — this is the new blocker.** 20 consecutive boots are in Doc 203's **blind** class, across three masks and a fresh deploy; the sweep, the enable *speed*, the firmware bytes and the modem's persistent partitions are each falsified as the cause (§6) | **NOT MET** |
| Not make the ~900 s fatal worse | no new fatal mechanism | no fatal, no SSR; 20 clean reboots; the modem stayed `offline` throughout, so the LTE-gated clock was never armed | **MET** |

**The one-line truth:** the CM layer's code is now proven identical between the two builds and the campaign's
capture instrument is proven *matched* across the two arms — but the device has lost the F3 **burst** class
that every CM-layer negative must be scored on, and nothing in the instrument, the firmware or the modem's
persistent state explains the loss.

---

## 3. The CM dispatcher is structurally identical between the two builds

### 3.1 Method and result

Doc 207 §9 item 1 proposed instrumenting the CM command dispatcher. Before instrumenting it, the prior
question is whether it is the *same code*. It is.

`FUN_c067cd2c` is a **REX message handler**: it takes no arguments, calls `prolog_save_regs_c0030000()` to
obtain the message pointer, and switches on `*(uint *)(iVar8 + 8)` — the command id at offset 8 of the CM
command object. Its stock counterpart was located **by its switch constants, not by an address guess**:

```
$ grep -n "0x100006a" "Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c"
1116884:      if (uVar21 == 0x100006a) {
2040481:      if (uVar10 == 0x100006a) {          # a different, unrelated function
$ awk 'NR<=1116884 && /\/\* ---- Function: /{last=$0; ln=NR} END{print ln": "last}' …
1116844: /* ---- Function: FUN_c0659ef0 @ c0659ef0 ---- */
```

| | UZ801 | stock HMU05 |
| :-- | :-- | :-- |
| function | `FUN_c067cd2c` @ `0xc067cd2c` (decompile line 1134568) | `FUN_c0659ef0` @ `0xc0659ef0` (decompile line 1116844) |
| normalised lines | 1096 | 1096 |
| raw similarity (`fdiff.py`) | **83.49 %**, 115 differing blocks, 181 lines each | |
| after GP-offset normalisation | **2 blocks** — and both are the *same* single-line `int *piVar;` declaration move | |

`fdiff.py` already collapses `FUN_*`, `DAT_*`, `thunk_*`, `LAB_*`, literal addresses and Ghidra's temporary
register names. What it does **not** collapse is `unaff_GP + 0x…` — the **global-variable layout**, which
necessarily differs between two builds. Re-normalising that away:

```
total blocks 60 structural after GP-normalisation 2
=== insert uz[0/1]      + int *piVAR;
=== delete uz[1/0]      - int *piVAR;
```

**⇒ the two dispatchers are the same program.** Every one of the 115 raw differing blocks is a global slot
renumbering (`unaff_GP + 0x32fc` vs `+0x3278`, `+0x130c` vs `+0x12bc`, `+0x4a18` vs `+0x4968`, …), and the
`0x1930` / `0x1cf4` per-subscription record strides are identical on both sides.

### 3.2 What this closes

* It is **not** the case that UZ801's CM dispatcher lacks the case the `dcc` command hits, or maps that id to a
  different handler. Same code ⇒ same id→case mapping.
* Combined with Doc 207 §5–§6 (`cmcc.c` site-for-site identical, `FUN_c06928a8` ≡ `FUN_c066fa30`), the whole
  **CM dispatch surface** is now measured rather than assumed.
* Therefore the `dcc` command's 0.66 ms drop (Doc 200 §4, Doc 204 §7) must be a **command-content** or
  **CM-state** difference — a *runtime* question. That is exactly what §6 now blocks.

---

## 4. The F3 `code` field is not the AP-selected SSID

### 4.1 What `cntl-enable N` actually does (source- and wire-verified)

`GitIgnore/compare/diag_logtool.c:cmd_cntl_enable_n()` builds one `DIAG_CTRL_MSG_F3_MASK` packet per `s` in
`0 … nssid-1` with

```c
pkt[11] = s & 0xff; pkt[12] = 0;      /* ssid_first */
pkt[13] = s & 0xff; pkt[14] = 0;      /* ssid_last  */
```

i.e. **`cntl-enable N` enables SSIDs `0..N-1`, one single-SSID range per packet.** The wire confirms it — the
stock instrument log's packet sequence is `ssid_first=0x0000 last=0x0000`, `0x0001/0x0001`, … and the
sweep-256 log ends at `0x00fe/0x00fe`, `0x00ff/0x00ff`.

### 4.2 `code` is invariant to the sweep — measured on one boot

Four captures on the **same modem boot**, back to back, with only the sweep changed:

| sweep | n | distinct `code` values | files |
| --: | --: | :-- | --: |
| 1 | 1 619 | `{4772: 1619}` | 10 |
| 4 | 274 | `{4772: 274}` | 5 |
| 64 | 284 | `{4772: 284}` | 5 |
| 256 | 63 196 | `{4772: 63196}` | 14 |

**⇒ `code` does not track the enabled SSID range at all.** A field that is the same for a 1-SSID mask and a
256-SSID mask cannot be the AP's mask parameter.

### 4.3 `code` is a modem-assigned per-boot value

Across the archived captures, `code` takes the values
`0, 4756, 4764, 4765, 4766, 4769, 4771, 4772, 40240, 41261, 42100, 42140` — and within one capture it is
*almost* constant (`uz801_boot.bin` carries two, `0` and `4756`, with the `0` bucket holding 107
steady-state messages). The values move in a narrow band (`4756…4772`) across boots, and each capture's
dominant value is its own.

**⇒ read `code` as "the logging-subsystem id this modem boot handed to the stream".** It is a *capture*
attribute, not a *mask* attribute.

### 4.4 Consequences for the corpus

* Doc 203 §3.1's table carries a `code`-derived column; it must not be read as mask information.
* Doc 202 §6.1's argument is unaffected — its `tsk`/`u=` evidence comes from the **`cmdbg.c` message text**,
  not from `code`.
* The practical rule: **a capture's mask is known only from its own `diagboot.log`**, never from the F3
  record. This is now how §5 establishes mask identity.

---

## 5. The stock arm used the IDENTICAL instrument — so the mask is matched

This is the session's most load-bearing measurement, because §4 removed the only *in-band* way to read a
capture's mask.

`scratch/hmu05_stock_boot.log` — the archived instrument log for the 94 623 391 B stock capture — begins:

```
=== diagboot_run start uptime=9.92 ===
DIAG dev present after 3s (uptime=12.93)
--- binding at uptime=12.94 ---
DIAG=/dev/rpmsg0
CNTL=/dev/rpmsg1
--- cntl-enable 64 SSIDs at uptime=12.98 ---
CNTL TX FEATURE        (14 bytes): 08 00 00 00 06 00 00 00 02 00 00 00 15 02
CNTL TX F3_MASK        (23 bytes): 0b 00 00 00 0f 00 00 00 01 02 00 00 00 00 00 01 00 00 00 ff ff ff ff
CNTL TX F3_MASK        (23 bytes): 0b 00 00 00 0f 00 00 00 01 02 00 01 00 01 00 01 00 00 00 ff ff ff ff
…
```

**Same script, same `cntl-enable 64`, same `ssid_first=ssid_last=s` sweep, same bind uptime (12.9 s).** The
UZ801 arm runs `/root/diagboot_run.sh` with `SWEEP=${DIAGBOOT_SWEEP:-64}` and logs
`--- cntl-enable 64 SSIDs at uptime=13.18 ---`.

**⇒ the two arms' masks are identical.** Every stock-vs-UZ801 file-set differential in this campaign is
therefore **mask-controlled**, and Doc 203 §3.1's load-bearing negative is admissible.

### 5.1 The boot-chunk differential, restated with the mask held fixed

Stock's boot chunk is exactly delimited in its own trace: `Prot_state 0(NULL)` at ts 375 868 through
`New transaction : 0(NULL)` at ts 389 300, i.e. **ts < 600 000** — 324 messages, 28 files. UZ801's burst-class
captures carry the same phase (Doc 203 §3.1 verified it by emission index, not by `ts`, because the F3 `ts`
is a free-running clock that is **not** modem uptime — a UZ801 boot capture's first message can sit at
`ts` ≈ 9.6 × 10⁸).

| file | stock boot chunk | UZ801 burst (`v19_boot.bin`) | UZ801 burst (`diag_boot_v16.bin`) |
| :-- | --: | --: | --: |
| `mmocdbg.c` | 51 | **60** | **60** |
| `cmdbg.c` | 4 | **14** | **14** |
| `a2_power.c` | 8 | **8** | **8** |
| `mmocmmgsdi.c` | 11 | **61** | **62** |
| `wms.c` | 53 | **60** | **60** |
| `mmgsdiutil.c` | 30 | **62** | **59** |
| `cmss.c` | **18** | **0** | **0** |
| `cmmsc_auto.c` | **3** | **0** | **0** |
| `mmoc.c` | **4** | **0** | **0** |
| `cmlog.c` | **6** | **0** | **0** |
| `cmregprx.c` | **7** | **0** | **0** |
| `sdss.c` | **6** | **0** | **0** |
| `sdcmd.c` | **5** | **0** | **0** |
| `policyman_serving_system.c` | **1** | **0** | **0** |

**The negative is not a coverage artifact.** The UZ801 arm demonstrably *does* deliver the boot-phase CM/MMOC
layers on the same mask (`mmocdbg.c` 60, `cmdbg.c` 14, `mmocmmgsdi.c` 62) — and delivers **nothing** from the
serving-system set, all of whose stock members fire **inside the same boot chunk** (`cmss.c` first at
ts 381 296, `cmmsc_auto.c` at 375 844, `mmoc.c` at 375 904, `cmlog.c` at 381 964, `cmregprx.c` at 376 456,
`sdss.c` at 381 036). **The CM serving-system layer genuinely does not run on UZ801 at boot.**

> **Scope.** This is a statement about the **boot chunk**. `cmcc.c`, `cmcall.c`, `cmsds.c`, `cmsoa.c`,
> `mcpm.c`, `qm_util.c`, `trm_config_handler.c` first fire in stock only *after* the online transition
> (ts ≥ 3.04 × 10⁹) and are therefore **not** part of this differential — they are LTE-stack-gated (Doc 200
> §3a) and their absence on an offline modem carries no information.

---

## 6. The F3 burst class is gone, and it is not instrument-controlled

Doc 203 §3.2 defines the admissibility rule: **a negative scored from a UZ801 capture is admissible only if
that capture is burst-caught**, i.e. `mmocdbg.c` > 0. As of this session that class **no longer occurs on this
device**, and I falsified four candidate causes before accepting that.

### 6.1 Hypothesis 1 — "a wider mask blinds the capture" → **FALSIFIED by its own control**

The reasoning looked strong: `cntl-enable N` sends one packet per SSID, so a 256 sweep is 256 round trips and
could lose the modem's boot-time F3 history to the steady-state stream.

| arm | mask | boots | burst (`mmocdbg.c`>0) |
| :-- | :-- | --: | --: |
| single-SSID sweep | 0…255 | 5 | **0** |
| one-packet range | 0…255 | 5 | **0** |
| **control: single-SSID sweep** | **0…63** | **4** | **0** |

I built and deployed `cntl-enable-range` (§7) specifically to remove the *latency* while keeping the *width* —
and it blinded just as reliably. Then restoring the **original sweep-64 script, byte-for-byte**
(`md5 d385dad0905f33637e0f6cfa2cc1a594`) blinded **4/4 as well**.

**⇒ the sweep width is not the cause, and the "wide mask blinds" finding — which I was one control away from
publishing — is wrong.** The class had already been lost before any of these tests.

### 6.2 Hypothesis 2 — "a fresh deploy restores the class" → **FALSIFIED**

The class flip has a suggestive shape: burst captures cluster *immediately after* a firmware deploy
(`reboot_postpatch.bin` 2026-09-25 23:23; `v19_boot.bin` 2026-09-26 17:38, i.e. 2 min after v19 was deployed
at 11:51:22Z), and the class is blind ~45 min later.

I redeployed the **identical** v19 set through the guard-aware deployer
(`--label doc208-class-test --reboot`; all 22 md5s in `/overlay/modem_guard/deployed` match
`scratch/uz801_patched`). Result: `mmocdbg.c = 0`, `cmdbg.c = 4`, 25 files — **still blind**.

### 6.3 Hypothesis 3 — "the modem mutates persistent state across boots" → **FALSIFIED**

If the modem wrote its EFS/NV at boot, a cumulative drift could change its bring-up order. It does not:

| partition | before boot | after boot |
| :-- | :-- | :-- |
| `modemst1` (`/dev/mmcblk0p4`) | `92eddef96de5d8ab4a452dccb292cf67` | `92eddef96de5d8ab4a452dccb292cf67` |
| `modemst2` (`/dev/mmcblk0p5`) | `f0b30c2b7bf8ab2d0169d309a6b2cb7f` | `f0b30c2b7bf8ab2d0169d309a6b2cb7f` |
| `fsg` (`/dev/mmcblk0p2`) | `4201f7f39557578e1c8904e11c5529a4` | `4201f7f39557578e1c8904e11c5529a4` |
| `fsc` (`/dev/mmcblk0p1`) | `0f343b0931126a20f133d67c2b018a3b` | `0f343b0931126a20f133d67c2b018a3b` |

**Byte-identical across a boot.** The modem's persistent state is not the variable.

### 6.4 What *is* known: the flip is a step, and the streak is long

| when (host mtime) | capture | class |
| :-- | :-- | :-- |
| 2026-09-25 10:37 / 13:04 / 13:50 / 15:17 | `patched_boot`, `boot_hwid48`, `diag_boot_patch1`, `reboot_devcfg_boot` | blind |
| 2026-09-25 23:23 | `reboot_postpatch` | **burst** |
| 2026-09-26 03:07 / 03:21 / 08:55 / 15:42 / 17:38 | `diag_boot_v7`, `v8`, `v13`, `v16`, `v19_boot` | **burst** |
| **2026-09-26 18:23** | `boot_256_prep_64sweep` | **blind — the flip** |
| 18:30 / 18:41 | `boot_sweep256`, `boot_after_user_reboot` | blind |
| 22:47 → 23:5x (this session) | `boot_sweep256_good`, `boot_sweep256_try1`, `boot_burst256_try1…5`, `boot_range256_try1…5`, `boot_ctrl64_try1…4`, `boot_after_redeploy` | blind ×16 |

**The class flips in *blocks*, not randomly** — 5 burst in a row, then 20 blind in a row. A per-boot race at
p ≈ 0.5 would give 0.5²⁰ ≈ 1 × 10⁻⁶ for the current streak. The guard's `events.log` shows **no deploy, no
revert and no manual reset anywhere near 2026-09-26 12:38 UTC** (the only non-routine entries are the
2026-09-26 10:28:28 revert and a handful of `manual reset`s).

### 6.5 What the blind class is missing, precisely

| file | burst (`v19_boot.bin`) | blind (`boot_after_redeploy.bin`) |
| :-- | --: | --: |
| `mmocdbg.c` | 60 | **0** |
| `mmocmmgsdi.c` | 61 | 17 |
| `cmph.c` | 22 | 8 |
| `cmdbg.c` | 14 | 4 |
| `a2_sio.c` | 5 | 3 |
| `estk_bip.c` | 7 | **0** |
| `gstk_proactive_cmd.c` | 3 | **0** |
| `gstklib.c` | 2 | **0** |
| `gstk_s_term_profile_rsp_wait.c` | 1 | **0** |
| `nvruim.c` | 2 | 0–1 |
| `mcfg_uim.c` | 2 | **0** |
| `mmgsdi_session.c` | 1 | **0** |

**The missing set is the UIM/SIM-Toolkit bring-up and everything that hangs off it** — exactly Doc 203 §6's
"the burst class also carries `gstk` (SIM Toolkit) activity that the blind class lacks", and exactly Doc 202's
`tsk=mmgsdi_1` vs `tsk=cm` allocation-order distinction.

The card is **not** the explanation as far as the AP can see: `--uim-get-card-status` reports
`Card state: 'present'`, `Application [1]: usim (2)`, `Application state: 'ready'`, `PIN1 state: 'disabled'`,
`Personalization state: 'ready'`.

**⇒ the trigger is inside the modem's own UIM bring-up ordering, and it has latched for 20 boots.** It is not
visible from the AP, not carried in any partition, and not affected by the instrument.

### 6.6 Consequence — this blocks the campaign's F3-scored work

Every CM-layer negative in the campaign is scored on a burst-class capture. **With the class unavailable, no
new CM-layer negative can be scored, and no existing one can be re-tested.** Concretely, Doc 207 §9 item 1
(instrument the dispatcher's `u=1` handler chain and compare the two builds) **cannot be executed** until the
class returns, because the handler sequence lives in the boot chunk that only the burst class delivers.

**Named next step:** find the burst-class trigger. The leading hypothesis is now an **AP-side timing window** —
the class is a race between the modem's UIM bring-up and CM's first command, and the AP controls the EFS
window through `rmtfs`. A first probe: vary the `rmtfs` start relative to the modem's probe (Doc 196 measured
`rmtfs` at 10.66 s and the modem powering up at 10.94 s) and watch the `tsk` of the first `CMD alloc` in a
burst-vs-blind pair.

---

## 7. New tool: `cntl-enable-range`

`GitIgnore/compare/diag_logtool.c` gained `cmd_cntl_enable_n(path, nssid, span)` and a subcommand:

```
diag_logtool cntl-enable-range [dev] [nssid] [span]     # default nssid=span=256
```

`span` is how many SSIDs one `F3_MASK` packet covers; `span == 1` reproduces the original `ssid s..s` packet
byte-for-byte, so the existing `cntl-enable` behaviour is unchanged. With `span = 256` the whole `0..255` space
is enabled by **one** packet:

```
$ diag_logtool_range cntl-enable-range /dev/rpmsg2 256
CNTL TX F3_MASK        (23 bytes): 0b 00 00 00 0f 00 00 00 01 02 00 00 00 ff 00 01
00 00 00 ff ff ff ff
```

(`ssid_first = 0x0000`, `ssid_last = 0x00ff` — one packet instead of 256.) Built with the repo's own OpenWrt
toolchain, `openwrt/staging_dir/toolchain-aarch64_generic_gcc-14.3.0_musl/bin/aarch64-openwrt-linux-musl-gcc
-static -Os -fno-stack-protector`, md5 `470df474ed9425aa2b62386a8ef0eab4`, deployed to `/root/diag_logtool_range`.

It is retained because it is the right way to widen a mask, and because §6.1 needed it to falsify the latency
hypothesis. **It does not by itself restore the burst class.**

---

## 8. Method corrections

### 8.1 Doc 203 §3.2's evidence for retiring the sweep dimension was inadequate

Doc 203 concluded "the sweep setting has no measurable effect on the delivered file set" from a 64-sweep vs a
256-sweep capture — **both of which were blind**. A blind-vs-blind comparison can only show that the sweep
does not change *which subset of the already-missing boot chunk arrives*; it cannot show that the sweep does
not affect the **burst class itself**. §6.1 now shows the sweep is indeed not the cause, so the *conclusion*
survives — but it survives on this session's control, not on Doc 203 §3.2's comparison. The rule to keep:
**a dimension can only be retired by varying it inside the class you intend to score.**

### 8.2 `code` was mislabelled as the AP-selected SSID

See §4. It is a modem-assigned per-boot subsystem id. The fix is procedural: a capture's mask is read from its
`diagboot.log`, never from the F3 record.

### 8.3 A GP-offset-blind diff can hide a perfect match

`fdiff.py` reported `similarity 83.49%` on two **identical** programs, because `unaff_GP + 0x…` is the global
layout and two builds necessarily lay globals out differently. Any future `fdiff` verdict must be read with
GP offsets normalised, or a genuine match will be mistaken for divergence — and, symmetrically, a real
divergence hidden among GP noise.

---

## 9. Named next step

1. **Restore the F3 burst class** (§6.6). Nothing else in the campaign's F3-scored line can proceed without
   it. The probe is AP-side timing: vary the `rmtfs`/modem-probe window and watch the first `CMD alloc`'s
   `tsk`.
2. **Then execute Doc 207 §9 item 1** — instrument the dispatcher's `u=1` handler chain and compare the two
   builds' handler sequences. The static half is done (§3): the dispatcher is identical, so the comparison is
   about **which case is reached**, which is a runtime fact.
3. **Doc 207 §9 item 2 remains open** (descriptor hashes as the cross-build statement key); §5's mask identity
   now removes the last doubt about the *capture* side of that work.
4. **Do NOT re-open:** the `cmcc.c`/GROUP 11 axis (Doc 207), the `0x456`/`0x422` search (Doc 206 §5), the
   `u=1` race (Doc 204), the `evt=0` patch (Doc 204), the `0x1000001` whitelist (Doc 201), the
   `cm_state+0x39e8` byte (Doc 200/201), and now **"the CM code differs"** — §3 measures it identical.
   **Do not** read a capture's mask off the F3 `code` field. **Do not** score a CM-layer negative on a
   blind-class capture.

---

## 10. Evidence inventory (all under `scratch/`, gitignored)

| artifact | what it is |
| :-- | :-- |
| `dispatcher_fdiff.txt` | `fdiff.py FUN_c067cd2c FUN_c0659ef0 --full` — the 115 raw blocks |
| `dispatcher_stock_c0659ef0.txt` | the stock dispatcher extracted by function header |
| `code_test_1.bin`, `code_test_4.bin`, `code_test_64.bin` | the same-boot sweep-invariance captures (§4.2) |
| `wide_mask_test.bin` | live sweep-256 capture, md5 `f0ce06b45eb6716c1ee57128c82c96ae` |
| `boot_sweep256_try1…5.bin` | the 5 sweep-256 boots, all blind |
| `boot_range256_try1…5.bin` | the 5 one-packet-range-256 boots, all blind |
| `boot_ctrl64_try1…4.bin` | the **control**: sweep restored to 64, all blind |
| `boot_after_redeploy.bin` | after redeploying identical v19, blind |
| `ref_sweep64/diagboot_run.sh.sweep64` | the pre-change instrument, md5 `d385dad0905f33637e0f6cfa2cc1a594` |
| `ref_sweep64/diagboot_run.sh.sweep256`, `diagboot_run.sh.range256` | the two tested variants |
| `diag_logtool_range` | the new tool, md5 `470df474ed9425aa2b62386a8ef0eab4` |
| `hmu05_stock_boot.log` | **the stock arm's instrument log — the mask-identity proof (§5)** |
| `hmu05_stock_boot.bin` | the stock capture, 94 623 391 B |

---

## 11. Reproduce

```sh
cd /home/shaanair/Projects/msm8916-openwrt-clean

# §3 — the dispatcher is identical (read the "after GP-offset normalisation" count)
python3 scratch/fdiff.py FUN_c067cd2c FUN_c0659ef0 --full > scratch/dispatcher_fdiff.txt
python3 - <<'PY'
import re
blocks=[]; cur=None
for l in open('scratch/dispatcher_fdiff.txt'):
    m=re.match(r"--- (\w+) uz\[(\d+):(\d+)\] hm\[(\d+):(\d+)\]", l)
    if m: cur={'u':[],'h':[]}; blocks.append(cur); continue
    if cur is None: continue
    if l.startswith("   - "): cur['u'].append(l[5:])
    elif l.startswith("   + "): cur['h'].append(l[5:])
def norm(ls):
    return [re.sub(r"\b[A-Za-z_]{1,3}(?:VAR)\b","V",
            re.sub(r"0x[0-9a-f]{4,8}","0xA",
            re.sub(r"unaff_GP \+ 0x[0-9a-f]+","GP",x))).strip() for x in ls]
print(sum(1 for b in blocks if norm(b['u'])!=norm(b['h'])), "structural blocks")
PY

# §4 — `code` is invariant to the sweep
for N in 1 4 64; do
  ssh root@192.168.8.1 "B=\$(/root/diag_bind.sh); C=\$(echo \"\$B\"|sed -n 's/^CNTL=//p');
    /root/diag_logtool cntl-enable \"\$C\" $N >/dev/null 2>&1;
    /root/diag_logtool capture 12 /root/code_test_$N.bin >/dev/null 2>&1"
  scp -q root@192.168.8.1:/root/code_test_$N.bin scratch/code_test_$N.bin
done
python3 -c "
import sys; sys.path.insert(0,'scratch')
from f3parse import parse
from collections import Counter
for N in (1,4,64):
    d,ms=parse('scratch/code_test_%d.bin'%N)
    print(N, dict(Counter(m['code'] for m in ms)))"

# §5 — the stock arm's mask, read from its own log
grep -m1 "cntl-enable" scratch/hmu05_stock_boot.log

# §6.3 — the persistent partitions are stable across a boot
for p in 4 5 2 1; do ssh root@192.168.8.1 "md5sum /dev/mmcblk0p$p"; done
ssh root@192.168.8.1 'sync; reboot'; sleep 60
for p in 4 5 2 1; do ssh root@192.168.8.1 "md5sum /dev/mmcblk0p$p"; done
```

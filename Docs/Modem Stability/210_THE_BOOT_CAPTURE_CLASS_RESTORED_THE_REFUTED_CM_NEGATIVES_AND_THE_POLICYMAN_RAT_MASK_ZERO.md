# 210 — The F3 boot-capture class is restored: the campaign's CM-layer negatives are REFUTED, the policyman RAT mask is 0, and the missing-NV pattern

**Date:** 2026-09-27
**Device:** HMU05 4G modem stick, OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, patched `modem.b16` — **v19**
(`c71d53464a1e88f2072cbbb6f7f87475`); `modem.mdt` `9f39ce579114bcf1383bbdce62a8d284`.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`.
**Status:** RESULTS — the Doc 208 §9-item-0 gating blocker is **cleared**, a **deeper capture class is
found**, **five load-bearing campaign negatives are refuted**, and a new root-cause lead (the policyman
EFS item set) is established with a reproducible measurement.
**Companions:** `194` (the boot-log differential), `197` (the ledger + SOP), `200` (the `dcc` break),
`202` (the boot gate retracted), `203` (the capture-class confound), `205` (the op-mode refusal),
`207` (the descriptor model), `208` (the identical CM dispatcher + the lost burst class).

---

## 1. SOP compliance

Per the five-step protocol in `133 §1` (model: `194 §1`):

| SOP step | Status |
| :-- | :-- |
| **Backup / version control** | **Yes.** No firmware was modified this session. The deployed set was verified against the host tree before any work: `modem.mdt` / `b16` / `b18` / `b25` / `mba.mbn` on the device are **byte-identical** to `scratch/uz801_patched/` (§9). Both new captures are archived with md5 (§9). The bootloop guard was clean throughout (`boot_count` = 0, no `BOOTLOOP_REVERT.txt`). |
| **Ground-truth verification against stock HMU05** | **Yes.** The stock arm is the archived `scratch/hmu05_stock_boot.bin` (33 743 messages) plus the stock image `GitIgnore/MelbonWhiteStock_Dump/…/modem.elf` for string-level checks. **§4.4 declares the stock arm's coverage limit** — it is the reason the stock comparison is *not* used to settle the policyman question. |
| **Reconcile transport/config** | **Yes.** All captures parsed with the Doc-194 F3 parser; **`f3clean.py` is NOT applied to the load-bearing counts** (§3.3 — it is verified *not* to be the cause here, but the raw parse is used throughout). The instrument md5 is recorded per capture (`/root/diagboot.status`). **★ A defect in `f3parse.py` was found and fixed while re-deriving the counts (§3.4)** — it silently dropped 0.5–2.1 % of records; all counts in this doc are restated from the fixed parser. |
| **Surgical Hexagon patching + re-signing** | **NOT APPLIED — nothing was patched this session.** No hypothesis reached the patch stage. |
| **Live empirical validation** | **Yes.** Two live/boot captures taken on the running device this session (§5, §7); the new capture class is confirmed reproducible on **four** consecutive boots (§3.2). |

---

## 2. Headline

1. **The Doc 208 §9-item-0 gating blocker is CLEARED.** The F3 burst-capture class was lost to a
   **1-second-granularity DIAG-device poll** in `diagboot_run.sh` / `diag_bind.sh`. Replacing the polls
   with `sleep 0.05` fires `cntl-enable` at **~12.3–13.1 s** instead of ~13.2 s. Result: **4/4 boots
   capture the modem's cold boot** (§3.1). The class is a **race on the enable time**, not on the sweep
   width (which Doc 208 had already falsified).
2. **The restored class is DEEPER than the campaign's "burst" class, and the difference is decisive.**
   The campaign's burst-caught captures carry **`rcinit_init.c` = 0**; the new class carries
   **`rcinit_init.c` = 76–171** (§3.2). The campaign's class caught the *middle* of the boot; the new
   class catches the boot **from `rcinit` group 4**.
3. **Five load-bearing campaign negatives are REFUTED** (§4). On the restored class,
   `cmss.c` / `cmmsc_auto.c` / `cmregprx.c` / `sdss.c` / `sdcmd.c` / `mmoc.c` are **all NON-ZERO** —
   the ledger's "the CM serving-system layer genuinely never runs" is an artifact of a capture that
   never reached it.
4. **★ NEW ROOT-CAUSE LEAD: the UZ801 firmware's policyman cannot populate its RAT / RF / UE-mode items
   from the HMU05 EFS.** `policyman_rat_capability.c:726 … status 2, filesize 0` for **subs 0/1/2**,
   then **`Filtered RAT mask 0 based on HW capabilities 544`** — the modem ends the boot with **no RAT
   enabled at all**. The write back fails too (`policyman_efs.c:334`). (§5.)
5. **The EFS write path itself is FINE** — an independent QMI write (`--wds-create-profile`) created a
   profile that **persisted** (§6). So the policyman failure is **item-specific**, not a dead EFS.
6. **A missing-NV pattern** accompanies it, **reproduced in both** restored-class captures:
   `uim.c:7136 UIM_3: Read to NV active slot configuration unsuccessful` (arg `3`, decoded from the
   record) and `cmph.c:26759 =CM=  Can't read ue_based_cw`. A third candidate,
   `txlm_hal.c:821 Mismatch in values read from nv : 0x%x …` ×27, appears in **one** restored capture and
   **not** the other — it is recorded as **not reproducible** and must not be cited without its capture
   (§5.4).
7. **★ A concrete CM divergence the campaign's counting method could not see:** `cmmsc_auto.c`
   `MSC_AUTO: 0x200 & 0x%x = 0x200` — stock's mask is **`0x220`**, UZ801's is **`0xebe`**. And `0x220`
   = **544**, the exact value the UZ801 policyman reports as its **HW capabilities** (§8).
8. **The `dcc`-task break is confirmed on a fresh capture** — the AP's
   `--dms-set-operating-mode=online` produces a `dcc` CM command allocated and freed in **0.35 ms**
   with **no** downstream CM work (§7). But Doc 200 §4(b)'s supporting claim (`cmmsc_auto` never runs)
   is **refuted**.

---

## 3. The instrument fix and the restored class

### 3.1 The fix

`diagboot_run.sh` polled for the DIAG and DIAG_CNTL rpmsg devices with `sleep 1`:

```sh
while [ ! -d "$DEV" ] && [ "$i" -lt 600 ]; do sleep 1; i=$((i+1)); done
```

That 1-second granularity delayed `cntl-enable` to **~13.18 s** (measured:
`cntl-enable 64 SSIDs at uptime=12.98` in the stock arm's own log). The variant deployed here polls at
**0.05 s**:

```sh
while [ ! -d "$DEV" ] && [ "$i" -lt 6000 ]; do sleep 0.05; i=$((i+1)); done
echo "DIAG dev present after $((i/20))s (uptime=$(up)) [poll=0.05s]"
while [ ! -d "$CNTLDEV" ] && [ "$i" -lt 1200 ]; do sleep 0.05; i=$((i+1)); done
```

`diag_bind.sh`'s node-poll loop was changed the same way (`while [ "$i" -lt 10 ] … sleep 1` →
`while [ "$i" -lt 200 ] … sleep 0.05`). `cntl-enable` now fires at **12.34 / 12.47 / 12.86 s**
(boot-cycle log). The `diag_logtool.c` comment — *"the enable is a RACE against the modem's boot-time
F3 history"* — is confirmed.

**Verified 4/4 burst** after the change (three-boot cycle + the canonical boot), versus the campaign's
**20/20 blind**.

### 3.2 The new class is DEEPER — this is the load-bearing measurement

Every count below is from the **raw** parse (no `f3clean`).

| capture | bytes | msgs | distinct files | `rcinit_init.c` | `cmdbg.c` | `mmocdbg.c` |
| :-- | --: | --: | --: | --: | --: | --: |
| `scratch/v19_boot.bin` (campaign, "burst") | 956 379 | 845 | 31 | **0** | 14 | 60 |
| `scratch/diag_boot_v16.bin` (campaign) | 969 308 | 837 | 32 | **0** | 14 | 60 |
| `scratch/diag_boot_v13.bin` (campaign) | 1 173 733 | 1 925 | 32 | **0** | 26 | 60 |
| `scratch/g14_test/post_v18_group14_boot.bin` (campaign) | 982 460 | 843 | 28 | **0** | 16 | 60 |
| `scratch/doc209/boot_early_try1.bin` (**new**) | 1 244 197 | 1 917 | 74 | **171** | 281 | 131 |
| `scratch/doc210/boot_210_canon.bin` (**new, canonical**) | 1 163 362 | 1 718 | 73 | **76** | 273 | 144 |

*(Message counts are from the **fixed** parser of §3.4. With the pre-fix parser they read 841 / 829 / 1 900 /
809 / 1 887 / 1 683 — the fix adds 0.5–2.1 %. The **file counts and the `rcinit_init.c` column are
unchanged** by the fix, because no `rcinit_init.c` or campaign-class record carries the extra byte. The
campaign captures' `cmdbg.c` counts are the raw file counts and were not re-taken per-file.)*

**`rcinit_init.c` = 0 in every campaign capture and 76–171 in every new capture.** The `rcinit_init.c`
records are the firmware's own cold-boot group-4/5 bring-up:

```
rcinit_init.c:95   function enter group 4 func_name mc_init
rcinit_rex.c:141   task begins group 4 task_hash a363181 task_name gsm_mac1
rcinit_init.c:95   function enter group 5 func_name reg_init
rcinit_init.c:95   function enter group 5 func_name mmoc_init
```

⇒ **the campaign's captures started *after* the boot bring-up had already run.** Every negative scored
on them about the boot bring-up is therefore inadmissible.

### 3.3 `f3clean.py` is NOT the cause here (checked, not assumed)

Doc 202 §3.2 established that `f3clean.py`'s ±40e6-tick median window can delete real clusters. That is
**not** what is happening in the new class — checked explicitly:

| | raw | after `f3clean` |
| :-- | --: | --: |
| messages | 1 917 | 1 796 |
| `cmss.c` | 2 | 2 |
| `cmmsc_auto.c` | 4 | 4 |
| `cmregprx.c` | 5 | 5 |
| `mmoc.c` | 7 | 7 |
| `rcinit_init.c` | 171 | 171 |

The filter drops 121 of 1 917 messages and **none** of the load-bearing files. The difference between
the two classes is the capture, not the filter.

### 3.4 ★ A parser defect found and fixed while re-deriving the counts (2026-09-27, this session)

The counts in §3.2/§3.3/§4.2 were first taken with `scratch/f3parse.py` as it stood, and did not agree with
the device's own `/root/diagboot.status` (which counts by `grep -o`). Chasing that disagreement found **two
independent counting defects**, one in each direction:

1. **`f3parse.py` silently DROPPED records.** The F3 record's `fmt` was assumed to start at exactly
   `k+20+4*num_args`. A minority of records carry **one extra `0x00`** between the fixed `u32` field and
   the args, pushing the `fmt` start one byte later. Those records failed the `fmt`/`file` validation and
   were skipped (`pos=k+2`), so **every count taken with the old parser is a lower bound**. Measured
   extra-byte histogram (offset 0 = the only case the old parser handled):

   | capture | extra 0 | extra 1 | extra 2 | extra 3 | total | dropped |
   | :-- | --: | --: | --: | --: | --: | --: |
   | `scratch/doc210/boot_210_canon.bin` | 1 683 | 35 | 0 | 0 | **1 718** | **2.0 %** |
   | `scratch/doc209/boot_early_try1.bin` | 1 887 | 30 | 0 | 0 | **1 917** | **1.6 %** |
   | `scratch/v19_boot.bin` | 841 | 4 | 0 | 0 | **845** | **0.5 %** |
   | `scratch/hmu05_stock_boot.bin` | 33 039 | 679 | 24 | 1 | **33 743** | **2.1 %** |

   **It is a drop, not a mis-decode** — the `args` stay at `k+20+4*j` (the extra byte sits *before* the
   args field), so every arg value the old parser reported is still correct. **No verdict in this doc or
   in the campaign changes**; only the magnitudes. Fixed in `scratch/f3parse.py` (md5
   `59ccb3db69d084eae415dd940d7cdf1a`); the old file is kept as `scratch/f3parse.py.pre210`
   (`7da71c3cfc4c308fd38fd114d0bfefdc`).

   **Verified against a raw dump** (not assumed): the record carrying
   `policyman_rat_capability.c:74 'Filtered RAT mask %d based on HW capabilities %d'` decodes
   `args=[0, 544]` = `[mask, hw_caps]` — which is exactly §5.1's claim, read off the bytes.

2. **The device's `grep -o` counts are inflated**, because `diagboot_run.sh` greps the bare filename and
   the `.` is a **regex wildcard**. `grep -o "cmss.c"` matches `cmss` + any byte + `c`, so it reported
   `cmss.c=2` for a capture that contains the literal `cmss.c` **once**. ⇒ **never take a count from
   `diagboot.status`'s `count` lines as the number of records**; use the parser. (The status file is still
   the right instrument for *detecting* a blind capture — a zero is a zero either way. §7 of the ledger
   item 15 records the instrument change that adds `rcinit_init.c`.)

**Method rule (new).** Every F3 count in this corpus predating this fix is a **lower bound by ≤ 2.1 %**. No
verdict depends on a ≤ 2.1 % margin, so no verdict is affected — but the numbers are restated here from the
fixed parser, and the campaign's counts should be re-taken with it before any new claim that *does* turn on
a small margin.

---

## 4. The refuted campaign negatives

### 4.1 The ledger's claim

`197 §3` (Achieved vs Expected) carries this row, marked **MET (strengthened)**:

> *"Re-score the UZ801 CM-layer negative under that control — on the six burst-caught captures,
> `cmss.c` / `cmlog.c` / `cmmsc_auto.c` / `cmregprx.c` / `sdss.c` / `sdcmd.c` / `mmoc.c` are **all still
> 0** ⇒ the CM serving-system layer genuinely never runs (Doc 202 §5.3)"*

### 4.2 The measurement on the restored class

| file | campaign class (`v19_boot.bin`) | **new class (`boot_210_canon.bin`)** | **new class (`boot_early_try1.bin`)** | verdict |
| :-- | --: | --: | --: | :-- |
| `cmss.c` | 0 | **1** | **2** | **REFUTED** |
| `cmmsc_auto.c` | 0 | **4** | **4** | **REFUTED** |
| `cmregprx.c` | 0 | **5** | **5** | **REFUTED** |
| `sdss.c` | 0 | **2** | **2** | **REFUTED** |
| `sdcmd.c` | 0 | **1** | **1** | **REFUTED** |
| `mmoc.c` | 0 | **7** | **7** | **REFUTED** |
| `cmlog.c` | 0 | 0 | 0 | not observed (not claimed refuted) |

**The CM serving-system layer DOES run.** The campaign's zeroes were a coverage artifact.

*(Counts from the fixed parser of §3.4. The other three campaign captures — `diag_boot_v16.bin`,
`diag_boot_v13.bin`, `post_v18_group14_boot.bin` — are **also all 0** for every row above, so the campaign
class is 0 on **four** captures, not one. `cmlog.c` is the one campaign file that stays 0 in the restored
class as well: it is emitted on a later path, and Doc 204's "vs 590 in stock" figure is a **file count from
the stock arm**, which per §4.4 does not cover stock's boot either.)*

### 4.3 `cmmsc_auto` — Doc 200 §4(b) / Doc 202 §5.3, refuted directly

Doc 200 §4's surviving claim was that `cmmsc_auto.c` = 0 in *both* UZ801 captures while stock has 36
(**re-measured with the fixed parser: stock `cmmsc_auto.c` = 40** over its 33 743 messages).
The restored class contains **exactly the stock sequence**, in the same order:

| | STOCK (idx 58–60) | UZ801 v19 (new class, idx **514–516**) |
| :-- | :-- | :-- |
| 1 | `=CM= MSC_AUTO: 0x200 & 0x220 = 0x200` | `=CM= MSC_AUTO: 0x200 & **0xebe** = 0x200` |
| 2 | `=CM= CMMSC_AUTO:is_ue_mode_csfb=0 hybr_pref 1 is_cdma_subsc_avail 0` | identical |
| 3 | `=CM= CMMSC_AUTO: updating op_mode, cdma sub 0 hybr1_allowed 1 hybr2_allowed 0` | identical |

*(Both builds also emit a fourth `cmmsc_auto.c` record immediately after — `old_op_mode:%d
new_op_mode:%d ue_mode:%d`, stock `args=[2816,2816,0]` vs UZ801 `args=[2560,2816,0]` — so it is **not**
UZ801-only either. All four records are decoded from `args[]`; see §8 for the mask operand.)*

⇒ **`cmmsc_auto` runs, and its third statement — `updating op_mode`, the exact stock-only event
Doc 201 §5.3 named — is present.** What differs is the **mask argument** (§8), not the presence of the
module. This is precisely the class of divergence a presence/absence census cannot see.

### 4.4 What is NOT refuted, and the stock arm's declared limit

* The `dcc`-task timing break (Doc 200 §3/§4) **stands** — reproduced independently this session at
  **0.35 ms** (§7).
* The stock arm's **coverage limit is declared, and now measured**: `scratch/hmu05_stock_boot.bin`'s own
  boot chunk is only its **first 324 messages** (idx 0–323, `ts` 237 980–576 164) and carries **no**
  `rcinit_init.c`; **and the whole 33 743-message capture carries `rcinit_init.c` = 0 and `policyman_*` =
  0 for every policyman file** (`policyman_rat_capability.c` / `_rf.c` / `_ue_mode.c` / `_efs.c` all 0).
  Stock's capture therefore starts *after* both `rcinit` **and** the policyman init.
  **Consequence: the stock capture cannot answer whether stock's own policyman boot-time EFS read
  succeeds — it never reaches it.** §5 is stated as a UZ801-only measurement, not as a differential. This
  is now a **measured** limit, not an inference from the 324-message chunk.

---

## 5. ★ The policyman finding

### 5.1 The boot-time failure, verbatim

From `boot_210_canon.bin`, at boot indices 34–64 (present identically in `boot_early_try1.bin` at
223–251, and in every boot in the three-boot cycle):

**Every value below is decoded from the record's own `args[]` field, not read off the format string**
(§3.4): `…:726` carries `[subs, status, filesize]` = `[0, 2, 0]` / `[1, 2, 0]` / `[2, 2, 0]`;
`…:74` carries `[mask, hw_caps]` = `[0, 544]`; `…:907` carries `[subs, status]` = `[0, 2]`;
`policyman_phone_events.c:595` carries `[nSim]` = `[1]`; `policyman_ue_mode.c:217` carries
`[subs, ue_mode]` = `[0, 0]`.

```
policyman_rat_capability.c:726  subs 0: policyman_retrieve_rats_bands returned status 2, filesize 0
policyman_rat_capability.c:98   subs 0: can't populate RAT item from EFS
policyman_rat_capability.c:726  subs 0: policyman_retrieve_rats_bands returned status 2, filesize 0
policyman_rf.c:592              subs 0: can't populate RF item from EFS
policyman_ue_mode.c:122         subs 0: can't populate UE mode Item from EFS
   … repeated for subs 1 and subs 2 …
policyman_cfgitem.c:361         ========== Ensure device config
policyman_device_config.c:65    returning Feature mode SYS_SUBS_FEATURE_MODE_SVLTE
policyman_device_config.c:517   Default device configuration for subs 0 created
policyman_phone_events.c:595    nSim = 1, not determining multimode SUBS
policyman_rat_capability.c:74   Filtered RAT mask 0 based on HW capabilities 544      ← ★★
policyman_rat_capability.c:74   Filtered RAT mask 0 based on HW capabilities 544
policyman_cfgitem.c:226         subs 0: items id 4 doesn't need update
policyman_efs.c:334             Error writing file in policyman_efs_put_file()
policyman_ue_mode.c:217         subs 0: written ue_mode 0 into efs
policyman_rat_capability.c:907  subs 0: efs rat & band write status = 2
```

### 5.2 What it means

`Filtered RAT mask 0` is the end state of the boot's policyman evaluation: the device configuration was
created as a **default**, its RAT mask was filtered against the hardware capability mask (**544 =
0x220**) and came out **0**. **A modem with a RAT mask of 0 has no radio access technology enabled** —
it has nothing to scan, nothing to register on, and no serving system to report. This is a
candidate mechanism for the campaign's central symptom (the modem never leaves `Prot_state 7(OFFLINE)`)
that is **not on the campaign's closed list**.

The write-back also fails (`policyman_efs.c:334`, then `efs rat & band write status = 2`), so the
failure is **not self-healing on a later boot**: the item is never created.

### 5.3 The read failure is item-specific, not a dead EFS — proven

Two independent controls on the same running device:

| control | result | meaning |
| :-- | :-- | :-- |
| `qmicli --wds-create-profile="3gpp,name=efstest,apn=efstest.apn"` | **`New profile created: Profile index: '3'`**, and the profile is present in a subsequent `--wds-get-profile-list=3gpp` | **the modem CAN write and persist to its EFS** |
| `qmi_nasi_generate_get_preferred_nw_resp, file_enum 220, status:0` + `:PLMNWACT, read_data_len169,size:5` + a populated `plmn(0..15), mcc:405, mnc8xx` list | **status 0** | **NAS reads EFS items fine** |

⇒ the EFS is neither read-only nor unwritable. The policyman RAT/RF/UE-mode items specifically are
absent or unusable, and policyman specifically cannot create them.

### 5.4 The missing-NV pattern

The policyman failure is not isolated. Two further UZ801-only NV/EFS read failures appear in **both**
restored-class captures (args decoded from the raw records, not inferred from the format string):

| site | message | args | meaning |
| :-- | :-- | :-- | :-- |
| `uim.c:7136` | `UIM_%d: Read to NV active slot configuration unsuccessful` | `[3,0,0,0]` ⇒ **`UIM_3`** | a UIM NV read fails |
| `cmph.c:26759` | `=CM=  Can't read ue_based_cw ` | `[]` | a CM NV read fails |

A **third** candidate does **not** reproduce, and is recorded as such:

| site | message | `boot_early_try1.bin` | `boot_210_canon.bin` | verdict |
| :-- | :-- | --: | --: | :-- |
| `txlm_hal.c:821` | `Mismatch in values read from nv : 0x%x and regarray : 0x%x at index : %d` | **27** | **0** | **not reproducible** |

⇒ the `txlm_hal` NV-mismatch cluster is a **per-boot** event, not a stable property of the UZ801 boot, and
must **not** be cited as part of the missing-NV cluster without saying which capture it came from. (The
policyman messages themselves **do** reproduce exactly — `policyman_rat_capability.c` 12, `_rf.c` 3,
`_ue_mode.c` 4, `_efs.c` 1 in **both** captures — so the lead does not depend on it.)

The stock capture's window contains **none** of these strings — but per §4.4 that is not decisive,
because the stock window does not cover stock's boot.

### 5.5 What this does and does not establish

**Establishes:** on the HMU05, the UZ801 v19 firmware boots with a **0 RAT mask**, a **default** device
configuration, and a **cluster of failed NV reads** across policyman, UIM, CM and the TX linearity
manager.

**Does not establish:** that this is *the* differentiator. The policyman code is **string-identical**
between stock and UZ801 (56 vs 57 messages; the only delta is
`policyman_uim.c:updating PLMN for Sub %d`), so the same code reading the same EFS ought to behave the
same — unless the *item set* the code looks for is firmware-version-dependent, or the items are created
by a **different** module that fails on UZ801 (an RF bring-up that fails would never write the RF/RAT
capability the policyman then reads — but note that the one RF-side candidate actually measured, the
`txlm_hal.c:821` NV mismatch, **does not reproduce** across the two restored-class captures, §5.4, so it
cannot carry this on its own). **Settling this requires a stock boot capture on the restored instrument** —
which the campaign has never had (§4.4: the archived stock capture reaches neither `rcinit` nor policyman).

---

## 6. The EFS write path works (control)

Already stated in §5.3; recorded here as a standalone instrument result because it **bounds** the
policyman hypothesis: any explanation that requires a globally dead or read-only EFS is **falsified**.

---

## 7. The live op-mode capture (the `dcc` break, re-measured)

`scratch/doc210/live_opmode_cap.sh` (66 lines, device md5 `0f7f38fe42f40d12d77dbc7f9d934d9e`) binds the
DIAG bridge, `cntl-enable 64`, starts a 45 s capture, and issues the AP levers mid-capture.

| step | result |
| :-- | :-- |
| baseline | `Mode: 'offline'` |
| `--dms-set-operating-mode=online` | **`Operating mode set successfully`** |
| `--nas-set-system-selection-preference=lte` | **`QMI protocol error (3): 'Internal'`** |
| second `--dms-set-operating-mode=online` | **`couldn't create client for the 'dms' service: CID allocation failed in the CTL client: endpoint hangup`** |
| after | `Mode: 'offline'` |

The capture (`scratch/doc210/live_cap.bin`, 3 397 120 B, 17 865 parsed records) contains **no `ds`-task
CM command at all**. The only CM commands are:

```
ts=540864328  =CM= CMD alloc u=131, tsk=dcc
ts=540864400  =CM= CMD free  u=131, tsk=dcc, ftsk=cm      ← 72 ticks = 0.35 ms
ts=541492880  =CM= CMD alloc u=134, tsk=qmi_mmode
ts=541493040  =CM= CMD free  u=134, tsk=qmi_mmode, ftsk=cm
ts=680698432  =CM= CMD alloc u=814, tsk=dcc
ts=680698504  =CM= CMD free  u=814, tsk=dcc, ftsk=cm      ← 0.35 ms
ts=683998756  =CM= CMD alloc u=830, tsk=qmi_mmode
```

⇒ **Doc 200 §3/§4's `dcc` break is reproduced independently** (0.35 ms vs stock's 11.5–13.8 ms), and
`u` again tracks the modem's uptime in seconds (§Doc 201 §3.1). The DMS QMI endpoint hangup is a
transport-level observation, recorded not root-caused.

**Caveat declared:** the live capture's mask is narrower than the boot capture's — it carries
`cmdbg.c` (10) but **no** `mmocdbg.c`/`cmph.c`/`cmss.c`. Its CM-command evidence is therefore
admissible (that layer *is* enabled) but it cannot be used for an MMOC negative.

---

## 8. ★ The `MSC_AUTO` mask divergence (`0x220` vs `0xebe`)

`cmmsc_auto.c`'s first statement is a mask comparison, and its second argument is **not** the same on
the two builds. The record is `=CM= MSC_AUTO: 0x%x & 0x%x = 0x%x`, so all three values are read directly
from its `args[]`:

| build | `args[]` | rendered | mask |
| :-- | :-- | :-- | --: |
| STOCK HMU05 | `[512, 544, 512]` | `=CM= MSC_AUTO: 0x200 & 0x220 = 0x200` | **0x220** |
| UZ801 v19 | `[512, 3774, 512]` | `=CM= MSC_AUTO: 0x200 & 0xebe = 0x200` | **0xebe** |

**`0x220` = 544** — the exact value the UZ801 firmware's policyman reports as its **HW capabilities**
(§5.1). Stock's runtime mask equals the value UZ801's policyman *calls* its hardware capability; UZ801's
runtime mask is a much broader set (`0x220 ⊂ 0xebe`). The first operand (`0x200`) and the result
(`0x200`) are identical in both builds, so the divergence is in the *second* operand — a runtime value,
not a firmware constant. The two builds emit this statement at **different source lines**
(UZ801 `cmmsc_auto.c:3250` vs stock `:3241`), consistent with Doc 205 §8's uniform **+9** revision shift.

**Stated as a measurement.** The interpretation (that this operand is the RAT-capability mask that
policyman failed to populate, and that its value differs because the EFS item is absent) is a
**hypothesis**, and it is falsifiable by the same stock-boot capture §5.5 calls for.

---

## 9. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Restore the F3 burst-capture class (Doc 208 §9 item 0 — the gating blocker) | burst boots again | **4/4 burst** after replacing the 1-second DIAG-device poll with `sleep 0.05` | **MET** |
| Identify the true cause of the lost class | a mechanism | the enable is a **race on `cntl-enable` timing** (12.3–13.1 s vs 13.18 s), not sweep width / enable speed / firmware / partitions | **MET** |
| Determine whether the campaign's "burst" captures covered the boot | a yes/no | **no** — `rcinit_init.c` = **0** in all four checked; the new class has **76–171** | **MET** |
| Re-score the CM-layer negative on the restored class | a valid verdict | **REFUTED** — `cmss.c` **1/2**, `cmmsc_auto.c` 4/4, `cmregprx.c` 5/5, `sdss.c` 2/2, `sdcmd.c` 1/1, `mmoc.c` **7/7** (canon/try1; all were 0) | **MET** |
| Re-derive the counts with a parser that does not drop records | the true counts | **MET, and it found a defect** — `f3parse.py` silently dropped **0.5–2.1 %** of records (an extra byte before the `fmt`); fixed, and every count in this doc restated (§3.4) | **MET** (UNEXPECTED) |
| Test Doc 200 §4(b)'s "`cmmsc_auto` never runs" | a yes/no | **REFUTED** — `MSC_AUTO` / `is_ue_mode_csfb` / **`updating op_mode`** all present at boot | **MET** |
| Explain *why* the policyman boot messages were never seen by the campaign | a mechanism | the policyman init is interleaved with **`rcinit` group 5**, which the campaign's captures do not reach | **MET** |
| Name the UZ801 boot's policyman end state | a value | **`Filtered RAT mask 0 based on HW capabilities 544`** for the default device config — **args decoded from the record** (`[0, 544]`), not from the format string | **MET** |
| Establish whether the missing-NV cluster is reproducible | a yes/no | **PARTIAL** — `uim.c:7136` (`UIM_3`) and `cmph.c:26759` reproduce in **both** restored captures; `txlm_hal.c:821` ×27 is in **one only** ⇒ **not reproducible**, and demoted | **PARTIAL** |
| Prove the EFS is writable | a yes/no | **yes** — `--wds-create-profile` created index 3 and it **persisted** | **MET** |
| Prove the EFS is readable by the modem | a yes/no | **yes** — NAS `PLMNWACT` `status:0`, PLMN list populated | **MET** |
| Decide whether the policyman failure is THE differentiator | a verdict | **NOT DECIDED** — the stock capture reaches neither `rcinit` nor policyman (**`rcinit_init.c` = 0 and `policyman_*` = 0 across all 33 743 stock messages**, §4.4), and the policyman code is string-identical across the builds | **NOT MET** (needs a stock boot capture) |
| Bring the modem to `online` | `Mode: 'online'` | `Mode: 'offline'` | **NOT MET** |
| Reproduce the `dcc` break | the timing | **0.35 ms** alloc→free, no downstream CM work, no `ds` task | **MET** |

---

## 10. Evidence inventory

| artifact | md5 / identity | what it is |
| :-- | :-- | :-- |
| `scratch/doc210/boot_210_canon.bin` | `b693faebb2bab65ca5c9eb948285658b` (1 163 362 B) | **the canonical restored-class boot capture**; equals the device's `/root/diag_boot.bin` and its `diagboot.status` `out_md5` |
| `scratch/doc209/boot_early_try1.bin` | `bd3d79d93a590af50fcdf53161e0b084` (1 244 197 B) | the first restored-class capture |
| `scratch/doc210/live_cap.bin` | `6a00a0d35c638ab1f1389bae16647871` (3 397 120 B) | the live 45 s op-mode-battery capture |
| `scratch/doc210/live_opmode_cap.sh` | device md5 `0f7f38fe42f40d12d77dbc7f9d934d9e` | the device-side live-capture driver |
| `scratch/doc209/diagboot_run.sh.early` | — | the sub-second-poll instrument (deployed as `59fb8e77961175ca4addd4161cdb63eb`) |
| `scratch/doc209/diag_bind.sh.early` | — | the sub-second-poll bind helper (deployed as `4a13dfb86f9ea60e38b98a6883c9eb9e`) |
| device `/root/inst_backup/*.pre209` | `d385dad0905f33637e0f6cfa2cc1a594` / `4f84d28dc2162c512dd36d09860988d9` | the pre-change instrument backups |
| device `/root/diagboot.status` | `boot_id=de2e796b…`, `script_md5=59fb8e77…`, `sweep=64`, `out_md5=b693faeb…` | the per-boot capture-health record for the canonical capture |
| `scratch/doc209/boot_cycle_1.log` | — | the three-boot cycle proving the class is reproducible |
| **`scratch/f3parse.py`** | **`59ccb3db69d084eae415dd940d7cdf1a`** | the F3 parser **with the extra-byte fix of §3.4** — counts `extra ∈ {0,1,2,3}` for the `fmt` start and records which was used as `m['extra']` |
| `scratch/f3parse.py.pre210` | `7da71c3cfc4c308fd38fd114d0bfefdc` | the **pre-fix** parser, kept for provenance — its counts are lower bounds by ≤ 2.1 % |
| `scratch/hmu05_stock_boot.bin` (re-measured) | 33 743 msgs (was 33 039) | the stock arm — **`rcinit_init.c` = 0 and `policyman_*` = 0 across the whole capture**, which is why it cannot settle §5 |

---

## 11. What this changes, and the next step

**Changes:**

1. **Doc 208 §9 item 0 is closed.** The gating blocker is gone; every CM-layer negative must now be
   re-scored on the restored class. The ledger row *"the CM serving-system layer genuinely never runs"*
   must be **retracted**.
2. **Doc 200 §4(b) and Doc 202 §5.3 are corrected** — `cmmsc_auto` runs. Their *timing* evidence
   (`dcc` 0.35–0.66 ms vs 11.5–13.8 ms) is untouched and stands.
3. **A new, non-closed lead is open:** the policyman RAT mask is **0**, with a coherent cluster of
   failed NV reads behind it, and a measured CM-side mask divergence (`0x220` vs `0xebe`) that no
   presence/absence census could have found.
4. **`f3parse.py` dropped 0.5–2.1 % of records and is now fixed (§3.4).** Every count in this corpus
   predating the fix is a **lower bound**. No verdict moves — the smallest margin any verdict rests on is
   far larger than 2.1 % — but the counts in this doc are restated from the fixed parser, and
   `scratch/f3parse.py.pre210` is kept so the discrepancy is auditable. **The device's
   `/root/diagboot.status` `count` lines are NOT record counts**: `grep -o "cmss.c"` treats `.` as a regex
   wildcard and over-reports.
5. **The missing-NV cluster is demoted from "three" to "two".** `uim.c:7136` and `cmph.c:26759` reproduce
   in both restored-class captures; `txlm_hal.c:821` ×27 does **not** (§5.4). A candidate that appears in
   one boot and not the next cannot be load-bearing for a boot-path mechanism.

**Next step (single, decisive):** capture a **stock HMU05 boot on the restored instrument**. It is the
only arm that can separate "shared with stock ⇒ benign" from "UZ801-only ⇒ causal" for the policyman
finding, and the campaign has never had a stock boot capture that reaches `rcinit` — the archived stock
capture is now **measured** to reach neither `rcinit` nor policyman (`rcinit_init.c` = 0 and `policyman_*`
= 0 across all 33 743 of its messages, §4.4). Everything needed is already in place: the sub-second
instrument is installed and runs on every boot, the stock set is at `/overlay/fwbackup/hmu05_stock/` on the
device, the v19 set is verified byte-identical to `scratch/uz801_patched/` so it can be restored, and the
bootloop guard is clean.

**Do NOT** re-open: `cmcc.c`/GROUP 11, `0x456`/`0x422`, the `u=1` race, the `evt=0` patch, `0x1000001`,
`cm_state+0x39e8`, "the CM code differs". **Do NOT** score a CM-layer negative on a capture with
`rcinit_init.c` = 0. **Do NOT** order F3 messages by `ts` (use file/delivery order).

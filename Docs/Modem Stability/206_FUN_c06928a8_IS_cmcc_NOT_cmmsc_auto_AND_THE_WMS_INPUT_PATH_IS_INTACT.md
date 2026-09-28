# 206 — `FUN_c06928a8` / `FUN_c06917f0` are `cmcc.c`, NOT `cmmsc_auto.c` — the identification the whole campaign rested on is wrong

**Date:** 2026-09-26
**Device:** HMU05 4G modem stick (`Melbon HMU05`), OpenWrt 25.12.5, kernel 6.12.94 aarch64, `192.168.8.1`, SSH root.
**Firmware under test:** UZ801 v3.2 "-21" baseband, deployed as **v19** — `modem.b16` md5 `c71d53464a1e88f2072cbbb6f7f87475`, `modem.mdt` md5 `9f39ce579114bcf1383bbdce62a8d284`; `--dms-get-revision` = `UZ801_V3.0_21_V01R01B10 1 [Sep 07 2015]`.
**Status:** RESULTS + **RETRACTION of a corpus-wide misidentification**. No firmware was built, patched or deployed; **no reboot**.
**Ledger:** see `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md`; its §3/§7/§8 are updated in the same session as this doc.
**Predecessors:** `201` (the `0x1000001` gate is dead / the pivot is `cmmsc_auto`), `202` (the boot gate retracted / the CM boot-path divergence), `203` (the capture-class confound), `204` (the `u=1` race), **`205`** (the F3 descriptor table / the complete op-mode refusal).

---

## 1. SOP compliance statement

| SOP step | Taken? | Detail |
| :-- | :-- | :-- |
| Verify against stock HMU05 ground truth | **Yes** | Every claim is cross-checked against **three** stock arms in the same session: the **stock ELF** (`GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf`, 50 049 024 B), the **stock boot capture** `scratch/hmu05_stock_boot.bin` (30 958 parsed F3 records), and — for the headline — **stock's own runtime records**, which independently confirm the descriptor line numbers (stock's `cmcc.c` L3062/L3096 = UZ801's L3081/L3115 minus the revision shift). |
| Byte-level / hash verification before attributing | **Yes** | All static reads are from `scratch/uz801_fw/modem.elf` (the rejoined UZ801-21 image) and the stock ELF above; no device state was changed. The capture comparisons use the archived `scratch/v19_boot.bin` and `scratch/hmu05_stock_boot.bin`. |
| Backup / reversibility | **Yes** | **Nothing was written to the device.** The only writes are host-side: `scratch/desc_table.py` (a new tool). No firmware, NV, guard or partition was touched; the guard was not consulted because no reboot occurred. |
| No blind patching of the baseband | **Yes** | **Nothing was patched.** The doc *retracts* the rationale of two already-deployed patches (§4) and marks any new patch **UNBUILT**. |
| Reboot discipline / instrument-not-perturbing | **Yes** | **No reboot.** All new measurements are offline (ELF + archived captures). |
| Mandatory ledger update in the same session | **Yes** | Doc 197 §3/§7/§8. |

---

## 2. Achieved vs Expected

| Intent | Expected | Achieved | Status |
| :-- | :-- | :-- | :-- |
| Execute Doc 205 §11 item 1 — cross-reference the external message IDs `0x456`/`0x422` | a peer, or a negative | **MET (negative).** Neither ID appears anywhere else in the image as a message ID; `0x456` is one member of a **computed family** of IDs all registered with the **same handler**. The static route stays closed (§5) | **MET** (negative) |
| Identify the function that `0x456`'s handler belongs to | a file name | **MET — and it is not what the corpus says.** `FUN_c06928a8`'s descriptors resolve to **`cmcc.c`** lines 3027–3180, and its own embedded symbols are `s_cmcc_service_available_cb` / `s_cmcc_call_control_processing_lte` (§3) | **MET** (UNEXPECTED) |
| Confirm the misidentification independently | a second method | **MET.** Stock's runtime capture emits the *same* formats at stock lines **3062 / 3096**; the UZ801 descriptors carry **3081 / 3115** — a uniform **+19** revision shift, i.e. the identical statements (§3.3) | **MET** |
| Decide whether "`FUN_c06928a8` has no static caller" supports "it is never invoked in UZ801" | a yes/no | **MET — it does NOT.** The negative is **specific** (9 `immext` targets exist in the same code cluster, none in the `FUN_c06917f0`/`FUN_c06928a8` range), but it **cannot explain a build-to-build difference**: stock **runs `cmcc.c`** (9 records incl. the statements `FUN_c06928a8` emits), and the only measured UZ801-vs-stock difference at that site is a newer source revision. `205 §7`'s "both builds" claim was never calibrated on the stock arm ⇒ **the inference is unsupported, and the stock-side census is now a named open item (§9)** | **MET** (inference unsupported) |
| Bound where the UZ801-vs-stock divergence actually is | a window | **MET (new, tight).** The whole **WMS/MMGSDI input path is intact**: `wmssim.c L4322 wms_sim_mmgsdi_response_cb_proc` runs **14×** in *both* builds with an **identical `cnf` sequence** (51,26,26,26,0,0,0,0,2,0,2,2,2,0) and identical `slot`; `wms.c` puts **and** processes 625/626/627/628/629/631 in both (§6) | **MET** |
| Establish whether the "0 code references" census can decide code presence | a check | **MET — it cannot.** `wmssim.c` emits 14 records at runtime yet has **0** literal `immext` references — exactly like `cmmsc_auto.c`. The census is uniform across both builds for these files (§7.1) | **NOT MET** (method retired) |
| Correct the descriptor-table model where it was over-stated | a correction | **MET.** The table is **not globally sorted** (it contains several sorted runs); `desc.py`'s `{u32; u16; u16}` model is **wrong** and produces nonsense on real entries (§7.2, §7.3) | **MET** |
| Not make the ~900 s fatal worse | no new fatal mechanism | unchanged; **no reboot, no SSR, no fatal** during the session | **MET** |

**The one-line truth:** the function the campaign has called `cmmsc_auto` for four documents
(`201 §5.3`, `202 §7`, `204 §6`, `205 §7`) is **`cmcc.c`** — CM Call Control's MMGSDI-EPS / service-available
handler — and **the deployed "cmmsc_auto" patches (v14/v19 GROUP 11) patch a `cmcc.c` function**; the
"no static caller" negative that was read as "`cmmsc_auto` is runtime-registered and never invoked" is
**falsified by stock's own behaviour**, because stock runs that same `cmcc.c` code with no static caller in
the image. Separately, the WMS/MMGSDI **input** path is byte-for-byte equivalent in both builds, so the
UZ801-vs-stock divergence is confined to the CM layer itself.

---

## 3. ★ Result 1 — `FUN_c06928a8` and `FUN_c06917f0` are `cmcc.c` functions

### 3.1 The corpus claim being tested

Four documents and the ledger identify `FUN_c06928a8` as `cmmsc_auto`:

* `201 §5.3` — "`cmmsc_auto` (`FUN_c06928a8`) has **no direct caller** in the decompiled image".
* `202 §7` — "`FUN_c06928a8` (`cmmsc_auto`) … still none … its `MSC_AUTO: …` string is its **early-return** path".
* `204 §6` — "the gate is class-invariant: `cmmsc_auto.c` = `cmss.c` = 0".
* `205 §7` — "Result 3 — `FUN_c06928a8` (`cmmsc_auto`) has no static caller, and the method is now trustworthy".
* Ledger §8 — "`FUN_c06928a8` (`cmmsc_auto`)".

### 3.2 The evidence that it is `cmcc.c`

The Doc 205 descriptor table turns "which file does this function log as" into a direct measurement: a log
call passes the **descriptor entry VA**, and the entry's `strptr` names the file.

**`FUN_c06928a8`'s log calls** (every `FUN_c091d940` / `FUN_c091d8c0` / `FUN_c091d9d0` first argument in its
body) resolve as follows:

| descriptor VA | `packed>>16` (line) | string |
| :-- | --: | :-- |
| `0xc165c398` | 3027 | *(not retained — hash)* |
| `0xc165c3a0` | 3057 | *(hash)* |
| `0xc165c3a8` | 3066 | *(hash)* |
| `0xc165c3b0` | 3070 | *(hash)* |
| **`0xc165c3b8`** | **3081** | **`cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d`** |
| `0xc165c3c0` | 3085 | *(hash)* |
| `0xc165c3c8` | 3099 | *(hash)* |
| **`0xc165c3d0`** | **3115** | **`cmcc.c:=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d`** |
| `0xc165c3d8` | 3130 | *(hash)* |
| `0xc165c3e0` | 3135 | *(hash)* |
| `0xc165c3e8` | 3159 | *(hash)* |
| `0xc165c3f0` | 3164 | *(hash)* |
| `0xc165c3f8` | 3180 | *(hash)* |

**`FUN_c06917f0`'s log calls** (the function `apply_v14_patches.py` GROUP 11 patches):

| descriptor VA | line | string |
| :-- | --: | :-- |
| `0xc165c220` | 1158 | *(hash)* |
| `0xc165c228` | 1163 | *(hash)* |
| **`0xc165c250`** | **1266** | **`cmcc.c:=CM= CC: app_type=%d`** |
| `0xc165c2a0` | 1416 | *(hash)* |

**Independent confirmation from the function's own embedded symbol strings.** `FUN_c06928a8` calls
`FUN_c06952fc(…, s_cm_mmgsdi_srv_available_cnf_ptr_c18f875a, s_cmcc_call_control_processing_lte_c18f87f0, 0xc26)`
and `FUN_c0691d9c` calls
`FUN_c0695218(0x38, s_sizeof_mmgsdi_srv_available_cnf__c18f87a0, s_cmcc_service_available_cb_c18f87c6, 0x923)`.
Both symbols literally contain **`cmcc`**.

⇒ **`FUN_c06928a8` and `FUN_c06917f0` are `cmcc.c` (CM Call Control), not `cmmsc_auto.c`.**

### 3.3 The independent runtime cross-check (this is what makes it airtight)

The stock capture's own records confirm the descriptor lines, with the expected revision shift:

| format | UZ801 descriptor line | stock **runtime** line | Δ |
| :-- | --: | --: | --: |
| `=CM= Service Available callback, status=%d, cnf=%d` | 2325 | **2313** | +12 |
| `=CM= cmcc_service_available_cb(), srv_available = %d` | 2353 | **2341** | +12 |
| `=CM= ACL Operation callback, status=%d, cnf=%d` | 2858 | **2839** | +19 |
| `=CM= MMGSDI EPS mmgsdi_status=%d` | **3081** | **3062** | **+19** |
| `=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d` | **3115** | **3096** | **+19** |

The two formats the UZ801 descriptor table attributes to `cmcc.c` at lines **3081 / 3115** are emitted by
**stock at runtime** at lines **3062 / 3096** — the same statements, +19 lines, exactly the shift the UZ801
descriptor table reports. **The descriptor model, the file attribution, and the revision direction all agree
against the corpus.**

The complete `cmcc.c` differential is a clean **staircase** (insertions, not rewrites):

| format (`cmcc.c`) | UZ801 | stock | Δ |
| :-- | --: | --: | --: |
| `= CM CC object allocated for call id %d` | 264 | 264 | 0 |
| `= CC_after_sim_cap: call_id=%d, cc call_type=%d` | 1078 | 1078 | 0 |
| `= CC: app_type=%d` | 1266 | 1266 | 0 |
| `= sim_cc_required=%d` | 1490 | 1490 | 0 |
| `= CC Sim cap callback, …` | 1620 | 1616 | **+4** |
| `= Send_cc_cmd: call_id=%d  call_type=%d` | 1820 | 1813 | **+7** |
| `= Service Available callback, status=%d, cnf=%d` | 2325 | 2313 | **+12** |
| `= cmcc_service_available_cb(), srv_available = %d` | 2353 | 2341 | **+12** |
| `= ACL Operation callback, status=%d, cnf=%d` | 2858 | 2839 | **+19** |
| `= MMGSDI EPS mmgsdi_status=%d` | 3081 | 3062 | **+19** |
| `= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d` | 3115 | 3096 | **+19** |
| *(UZ801-only)* `= Voip CC sys_mode = %d` | 2535 | — | new |

Four insertion points (+4, +7, +12, +19) and one new statement — the UZ801 `cmcc.c` is a **newer, larger**
revision, exactly as `205 §8` found for `cmmsc_auto.c`. **Neither file is stripped.**

### 3.4 Where the label came from, and why it stuck

The label is almost certainly a **name collision**: `FUN_c06928a8` is the function that emits the
`CMMSC_AUTO:`-prefixed strings *and* the `MSC_AUTO:` string, and `205 §8`'s own table shows those strings
belong to `cmmsc_auto.c` (lines 2651 / 3250). But the descriptors `FUN_c06928a8` **actually passes** are the
`cmcc.c` ones above — the `cmmsc_auto.c` formats are never passed by it. The two files' descriptor blocks sit
**in the same segment, ~0x3A00 apart** (`cmcc.c` at `0xc165c1e8–0xc165c460`, `cmmsc_auto.c` at
`0xc165fd50–0xc165ff00`), which is exactly the distance a hand-written VA can slip by.

---

## 4. Result 2 — the consequences of the misidentification

### 4.1 The deployed "cmmsc_auto" patches patch a `cmcc.c` function

`scratch/apply_v14_patches.py` GROUP 11 (carried unchanged into `apply_v15/v16/v17/v19_patches.py`) is
labelled **"GROUP 11: cmmsc_auto.c mode gate bypass (FUN_c06917f0)"** and patches **VA `0xc0691950`**:

```
pristine 0xc069194c:  80 53 30 93   r0 = memub(r16+#0x29c)
                      0e e9 42 24   if (!cmp.eq(r0.new,#0x9)) jump:t 0xc0691968
v14 patch (0xc0691950): 0ee94224 -> 0cc00058   (unconditional jump 0xc0691968)
v19 patch (0xc0691950): 0ee94224 -> 12c00058   (unconditional jump 0xc0691974)
```

`0xc0691950` is inside **`FUN_c06917f0`** (`0xc06917f0 … 0xc0691d9c`), which §3.2 shows is **`cmcc.c`**.
The decompiled body confirms the patched instruction is the `if (psVar4[0x29c] != 9)` test.

⇒ **v14's "THE KEY FIX" and v19's GROUP 11 have never touched `cmmsc_auto.c`.** Their stated rationale
("forces `cmmsc_auto.c` to always proceed to the ONLINE path") is **wrong**. This does not by itself make the
patch harmful — `cmcc.c` is *also* dead in UZ801 (§6) — but it means the campaign's central intervention was
aimed at a function it had misnamed, and the patch's inertness carries **no** information about
`cmmsc_auto`.

### 4.2 "No static caller ⇒ not invoked" is **unsupported** — and the negative is *specific*, not a cluster artifact

`205 §7` calibrated the `immext` scan and concluded that `FUN_c06928a8` "has no reference of any kind". That
measurement stands, and this session adds a **specificity control** for it: scanning UZ801's `cmcc.c` code
cluster (`0xc0690000–0xc069b000`) for `immext` targets finds **9** into the cluster, **none** of them in the
`FUN_c06917f0` / `FUN_c06928a8` range (`0xc06917f0–0xc0692b00`). So the two functions really are the only
unreferenced ones in their own neighbourhood — the zero is not a cluster-wide artifact.

What does **not** stand is the *inference* the corpus drew from it — that the function is therefore "reached
through a runtime-registered external service" and hence **not invoked** in the failing build:

* stock **emits `cmcc.c` records at runtime** — 9 of them, including
  `=CM= MMGSDI EPS mmgsdi_status=%d` (stock L3062) and
  `=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d` (stock L3096) — i.e. **the same statements
  whose UZ801 counterparts (L3081/L3115) `FUN_c06928a8` emits**;
* the only measured difference between the two builds at that site is a **newer source revision** (§3.3), not
  a change in the calling mechanism.

⇒ **A no-caller property cannot explain a build-to-build difference unless it is shown to differ between the
builds.** `205 §7` asserted the property holds "for *either* firmware", but only the **UZ801 arm was
calibrated**; the stock-side reference census is a **named open item (§9 item 2), not a measured fact**.
Until it is run, "`FUN_c06928a8` has no static caller" must **not** be read as "it is never invoked in
UZ801", and the reasoning of `201 §5.3`, `202 §7` and `205 §7` on that point is **unsupported**.

---

## 5. Result 3 — the `0x456` / `0x422` cross-reference (Doc 205 §11 item 1): DONE, negative

Doc 205 §11 named "find which peer sends `0x456`/`0x422`" as the one static lead left open. It is now closed.

**5.1 Neither ID appears elsewhere in the image as a message ID.** A full-image search for the literals
`0x456` (31 hits) and `0x422` (131 hits) finds only:

* `0xc06928a8+…` — the known registrations in `FUN_c06928a8`;
* `FUN_c0bc546c(0x456, 4, &DAT_c1ec76f8)` in **`FUN_c0bc5234`** — a **CRMBuilds / `readConfigXmlData`
  config-XML item ID**, not a message ID (its neighbours are `FUN_c072fc38(…, 0x451/0x462/0x467, …)`);
* `uVar5 = 0x456` in the `readConfigXmlData` error path (`FUN_c0fac870(4, 0x17bd, …, 0x456, …)`) — an
  **F3 line number**;
* everything else is a struct offset (`+0x456`).

⇒ **There is no second user of either ID as a message ID anywhere in the image.**

**5.2 `0x456` is a member of a *computed* family, and the family shares one handler.** The other caller of the
registration thunk is `FUN_c06917f0`, which registers the **same** handler `FUN_c0691d9c` under an ID chosen at
runtime:

```c
if (psVar4[0xe] == 2) { uVar12 = 0x435; … }
else if (uVar13 < 2)  { uVar12 = 0x21b; … }
else if (uVar13 == 4) { uVar12 = 0x80c; }
else if (uVar13 == 3) { uVar12 = 0x41d; }
else                  { uVar12 = 0x11b; }
…
thunk_EXT_FUN_d0587538(local_40, uVar12, FUN_c0691d9c, psVar4);
```

with `uVar13` coming from `thunk_EXT_FUN_d001d338(local_48)`. So `{0x11b, 0x21b, 0x41d, 0x435, 0x80c}` —
and `0x456` from `FUN_c06928a8`, and `0x422` — are **not per-peer constants**: they are keys into one
external service, selected by a **service handle** obtained from `thunk_EXT_FUN_d001d2fc(sel, &handle)`.

**5.3 The handler is the CMCC service-available callback.** `FUN_c0691d9c` gates on `uVar5 == 0x1a` and
allocates `sizeof_mmgsdi_srv_available_cnf` for `cmcc_service_available_cb`. Stock's runtime confirms the
chain (`=CM= Service Available callback, status=%d, cnf=%d` with `cnf=26`, then
`cmcc_service_available_cb(), srv_available = %d`).

**⇒ Conclusion.** The "peer" is not identifiable by message-ID cross-reference because the IDs are
service-relative and computed. The static route to it is closed for good; only a behavioural probe can
advance this thread. **Do not spend further sessions on it.**

---

## 6. ★ Result 4 — the WMS/MMGSDI **input** path is intact; the divergence is inside the CM layer

The campaign's framing since `202 §6` has been "UZ801's CM serving-system layer never runs". This session
localises the *input* side of that boundary.

### 6.1 The SIM MMGSDI response path is equivalent in both builds

`wmssim.c L4322` (`wms_sim_mmgsdi_response_cb_proc(): handle cnf = %d slot 0x%x, session_id 0x%x`) fires
**14 times in both builds**, with an **identical `cnf` sequence**:

| | cnf sequence (14 records) | slot |
| :-- | :-- | :-- |
| **stock** | 51, 26, 26, 26, 0, 0, 0, 0, 2, 0, 2, 2, 2, 0 | 1 (all) |
| **UZ801 v19** | 51, 26, 26, 26, 0, 0, 0, 0, 2, 0, 2, 2, 2, 0 | 1 (all) |

Only `session_id` differs (`1037820239` vs `1035789919`) — both well-formed non-zero session IDs.

### 6.2 The WMS command queue drains in both builds

| WMS command | stock put/proc | UZ801 put/proc |
| :-- | --: | --: |
| 617 `WMS_CMD_SS_CHANGE_INFO` | 2 / 2 | **0 / 0** |
| 625 `WMS_CMD_MMGSDI_EVENT_CB` | 0 / 1 | 3 / 3 |
| 626 `WMS_CMD_MMGSDI_RESPONSE_CB` | 13 / 13 | 14 / 14 |
| 627 `WMS_CMD_MMGSDI_SEEK_CB` | 3 / 3 | 3 / 3 |
| 628 `WMS_CMD_CM_PH_EVENT_CB` | 6 / 7 | 3 / 3 |
| 629 `WMS_CMD_CM_SUBS_EVENT_CB` | 3 / 3 | 1 / 1 |
| 631 `WMS_CMD_CLIENT_ACTIVATE` | 1 / 1 | 1 / 1 |

**Nothing is put-but-never-processed in either build** — the queue drains. The one asymmetry is that UZ801
never sees `WMS_CMD_SS_CHANGE_INFO` (617) at all; that is consistent with §6.3 (the serving-system layer is
what is missing) and is **not** a queue stall.

(An earlier read of this session appeared to show `Processing 625` missing in UZ801; that was a **diff
artifact** — the records differ by the `wms.c` line shift (stock L1881 / UZ801 L1890), so a
`(file, line, fmt)`-keyed diff listed them as two separate sets. The corrected census above is keyed on the
command number parsed out of the format string.)

### 6.3 The file census, and the resulting bound

| file | stock | UZ801 v19 |
| :-- | --: | --: |
| `wms.c` | 83 | 60 |
| `wmssim.c` | 16 | **16** |
| `wmsmsg.c` | 3 | **3** |
| `qmi_nas_mmgsdi.c` | 1 | **1** |
| `mmocdbg.c` | 316 | 57 |
| **`cmmsc_auto.c`** | **35** | **0** |
| **`cmcc.c`** | **9** | **0** |
| **`cmss.c`** | **590** | **0** |

`wmssim.c` / `wmsmsg.c` / `qmi_nas_mmgsdi.c` match **exactly**, so the CM-layer zeros are **not** a
capture-length or coverage artifact — the two captures caught the same SIM/MMGSDI work. (Both are
burst-caught: `mmocdbg.c` = 316 / 57 > 0, satisfying the Doc 203 §3.2 admissibility rule.)

**⇒ The divergence is strictly inside the CM layer**: the MMGSDI responses arrive and are processed
identically, and then `cmmsc_auto.c` / `cmcc.c` / `cmss.c` never run.

### 6.4 What a healthy stock CM entry looks like (reference values)

The first stock `cmmsc_auto.c` burst (ts 375844, immediately before MMOC `Recvd command 0(SUBSCRIPTION_CHGD)`):

```
L3241  =CM= MSC_AUTO: 0x%x & 0x%x = 0x%x            args=[512, 544, 512]  (0x200 & 0x220 = 0x200)
L2849  =CM= CMMSC_AUTO:is_ue_mode_csfb=%d hybr_pref %d is_cdma_subsc_avail %d   args=[0,1,0]
L2651  =CM= CMMSC_AUTO: updating op_mode, cdma sub %d hybr1_allowed %d hybr2_allowed %d  args=[0,1,0]
L1872  =CM= MSC: HICPS: cm_mode_pref: %d, ph obj mode_pref: %d, ph_ptr mode_pref: %d    args=[39,38,38]
```

and the stock `cmcc.c` service-available chain (ts 3040143192):

```
L2313  =CM= Service Available callback, status=%d, cnf=%d        args=[0, 26]
L2341  =CM= cmcc_service_available_cb(), srv_available = %d      args=[0]
L3062  =CM= MMGSDI EPS mmgsdi_status=%d                          args=[0]
L3096  =CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d  args=[0, 0]
```

`cm_mode_pref` = 39 (`0x27`) with `ph`/`ph_ptr` mode_pref = 38 (`0x26`) is consistent with `202 §8`'s
"`mode_pref` 0x26 in BOTH stands".

---

## 7. Result 5 — three methodology corrections

### 7.1 The literal-reference census cannot decide whether code exists — RETIRED

Doc 205 §3 argued that the F3 strings are unreferenced "by construction". That is right, but §7 then used a
**function-address** reference census, and the corpus has repeatedly leaned on descriptor census counts.
This session shows the descriptor census is **not a proxy for code presence**:

| file | descriptor entries | literal `immext` references (UZ801) | literal refs (stock) | emitted at runtime? |
| :-- | --: | --: | --: | :-- |
| `cmcc.c` | 16 / 15 | **12 bases** | **12 bases** | yes |
| `cmss.c` | 38 / 39 | 3 bases | 6 bases | yes |
| `cmlog.c` | 9 / 13 | 1 base | 1 base | yes |
| **`cmmsc_auto.c`** | 23 / 20 | **0** | **0** | **yes (stock)** |
| **`wmssim.c`** | 3 | **0** | — | **yes (both)** |

`wmssim.c` is the decisive counter-example: it emits **14 records** in *both* builds and has **0** literal
references. ⇒ **"0 references" carries no information.** `205 §3`'s headline ("segment-25 strings are
unreferenced by construction") remains true; any *further* inference from a zero does not.

### 7.2 The descriptor table is **not globally sorted**

`205 §3` inferred "sorted by `packed` ⇒ binary search at log time". The table contains **several sorted
runs**, not one:

* at `0xc165c400` the `packed` sequence jumps **4054 → 408** (`0x0fd6000b` → `0x0198000b`) — a new run;
* in the `cmmsc_auto.c` block, `0xc165ff00` carries line **3250** *after* `0xc165fef0`'s **5087**.

⇒ The table is sorted **within runs** (most plausibly per source region), and any lookup keyed on it is a
**per-run** search, not one global binary search. The line/level decode itself is unaffected and remains
correct (§3.3 proves it against runtime).

### 7.3 `scratch/desc.py` is wrong for this table

`desc.py` (cited in `200 §9` and ledger §8) models an 8-byte entry as `{u32 ptr_or_hash; u16 file_id; u16 line}`.
On a real entry it produces nonsense:

```
$ python3 scratch/desc.py 0xc165fdf0 0xc165fe00
0xc165fdf0  hash 0x09fe000b  file_id=15620  line?=50262
0xc165fe00  hash 0x0a5b000b  file_id=15744  line?=50262
```

The correct model is `{u32 packed; u32 strptr}` with `line = packed>>16` (which gives **2558** and **2651**,
matching `205 §8`'s table). `desc.py` is **superseded by `scratch/desc_table.py`** and should not be cited.

---

## 8. What this changes

1. **A corpus-wide misidentification is retracted.** `201 §5.3`, `202 §7`, `204 §6`, `205 §7` and ledger §8
   label `FUN_c06928a8` as `cmmsc_auto`; it is **`cmcc.c`** (§3). Same for `FUN_c06917f0`.
2. **The deployed v14/v19 GROUP 11 patch is re-attributed.** VA `0xc0691950` is inside `cmcc.c`'s
   `FUN_c06917f0`, not `cmmsc_auto.c` (§4.1). The patch's rationale is void; its inertness proves nothing
   about `cmmsc_auto`.
3. **The "no static caller ⇒ not invoked" inference is unsupported, and the negative is specific** (§4.2).
   Stock runs `cmcc.c`; the UZ801 no-caller result is the only measured difference and is not shown to
   differ between builds.
4. **The `0x456`/`0x422` lead is closed** (§5). The IDs are service-relative and computed; there is no
   peer to find statically.
5. **The divergence is now bounded on the input side too** (§6). The WMS/MMGSDI path is equivalent; the
   break is inside the CM layer.
6. **Three method corrections are on the record** (§7): the literal census is retired as a presence test;
   the table is not globally sorted; `desc.py` is wrong.
7. **A new reference is available**: the stock CM entry values of §6.4, which any future patch can be
   scored against.

---

## 9. Named next step

**Re-derive the real `cmmsc_auto.c` entry point, and re-score the two deployed patches' target.**

The real `cmmsc_auto.c` code is **unlocated**, and the previous "we found it and it has no caller" conclusion
was about the wrong function. In order of cost:

1. **★ Locate the emitter of `cmmsc_auto.c` L2651 by record adjacency, not by reference census.** The
   reference census is retired (§7.1). The stock capture gives the *behavioural* neighbourhood: the L3241 →
   L2849 → L2651 burst at ts 375844 sits immediately after `qmi_mmode_task.c L363` and immediately before
   `mmocdbg.c Recvd command 0(SUBSCRIPTION_CHGD)`. The emitting function is therefore the one that, at that
   point, posts `SUBSCRIPTION_CHGD` to MMOC. Work backwards from the **MMOC command poster** — that is a
   `cmcc.c`/`cmss.c` function with a *literal* descriptor reference (12 bases exist in the `cmcc.c` block),
   so it **is** findable.
2. **★ Re-score GROUP 11's target with the corrected attribution.** `0xc0691950` is in `cmcc.c`. Since
   `cmcc.c` is **also** dead in UZ801 (0 records vs stock's 9), the question "is `cmcc.c` reachable at all
   before the patch can matter?" is now answerable and is a **precondition** the campaign never checked.
   If `cmcc.c` is never entered, every GROUP 11 variant is inert *by construction*, and the correct target
   is whatever should have entered it.
3. **Do not** re-open: the `0x456`/`0x422` cross-reference (§5, closed); the `u=1` race (`204`); the
   `evt=0` patch (`204`); the `0x1000001` whitelist (`201`); the `cm_state+0x39e8` byte (`200`/`201`).
   **Also do not** cite `FUN_c06928a8` as `cmmsc_auto` again, and do not cite `desc.py`.

**Explicitly NOT the next step:** any further static search for a *caller* of any specific function on this
platform. §4.2 shows the invocation mechanism is invisible to static references.

---

## 10. Evidence inventory

| artifact | md5 / value | what it is |
| :-- | :-- | :-- |
| **`scratch/desc_table.py`** | (new, this session) | the descriptor-table instrument: `entries` (dump `{packed,strptr}` in a VA range), `file` (dump a file's block), `refs` (guarded `immext` scan), `u32` (raw scan). Supersedes `desc.py` |
| `scratch/uz801_fw/modem.elf` | (rejoined UZ801-21, 50 912 492 B) | the UZ801 ELF; `cmcc.c` descriptors at `0xc165c1e8–0xc165c460`, `cmmsc_auto.c` at `0xc165fd50–0xc165ff00` |
| `GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf` | 50 049 024 B | the **stock HMU05 ELF**; `cmcc.c` descriptors at `0xc155bc40–0xc155beb0`, `cmmsc_auto.c` at `0xc155f720–0xc155f8b8` |
| `scratch/hmu05_stock_boot.bin` | 94 623 391 B, 30 958 records | the stock boot capture — source of every "stock runtime line" in §3.3 and §6 |
| `scratch/v19_boot.bin` | 956 379 B, 771 records | the UZ801 v19 boot capture (`mmocdbg.c` = 57 ⇒ burst-caught) |
| `scratch/race_boot_4.bin` | `a7f55a973108e325f6e5a20312b7f6e1` | a BLIND-class UZ801 boot capture, used as the §6 cross-check |
| `Docs/Modem Stability/Modem RE/uz801/modem_full_decompiled.c` | 81 832 functions | the Ghidra decompile — `FUN_c06928a8` (line 1144714), `FUN_c06917f0` (1143588), `FUN_c0691d9c` (1143908) |
| `scratch/apply_v14_patches.py` | — | GROUP 11 = the `0xc0691950` patch, labelled "cmmsc_auto.c" — the misattribution |
| `scratch/apply_v19_patches.py` | — | GROUP 11 = the same VA `0xc0691950` (`0x12c00058`), same label |
| `scratch/f3parse2.py` | — | the delimiter-aware F3 parser used for every capture census |
| VA `0xc165c250` (UZ801) | line 1266 | `cmcc.c:=CM= CC: app_type=%d` — the descriptor that proves `FUN_c06917f0` is `cmcc.c` |
| VA `0xc165c3b8` / `0xc165c3d0` (UZ801) | lines 3081 / 3115 | `cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d` / `… srv_available=%d, cm_mmgsdi_acl_availability=%d` — the descriptors that prove `FUN_c06928a8` is `cmcc.c` |

---

## 11. Reproduce

**Attribute a function to a source file (the §3 method):**

```python
import sys, struct
sys.path.insert(0, 'scratch')
import desc_table as dt
d, segs = dt.load('scratch/uz801_fw/modem.elf')
for va in [0xc165c250, 0xc165c3b8, 0xc165c3d0]:        # from the decompiled body
    o = dt.va2off(segs, va)
    w0, w1 = struct.unpack_from('<II', d, o)
    o2 = dt.va2off(segs, w1)
    s = d[o2:d.find(b'\x00', o2)].decode('latin1') if o2 else '(hash)'
    print('%#x  line=%d  %s' % (va, w0 >> 16, s))
# 0xc165c250  line=1266  cmcc.c:=CM= CC: app_type=%d
# 0xc165c3b8  line=3081  cmcc.c:=CM= MMGSDI EPS mmgsdi_status=%d
# 0xc165c3d0  line=3115  cmcc.c:=CM= MMGSDI EPS srv_available=%d, cm_mmgsdi_acl_availability=%d
```

**The whole `cmcc.c` source-line differential (§3.3):**

```python
import sys; sys.path.insert(0, 'scratch')
import desc_table as dt
def lines(path, prefix):
    d, segs = dt.load(path); i = 0; vas = []
    while True:
        i = d.find((prefix + ':').encode(), i)
        if i < 0: break
        va = dt.off2va(segs, i)
        if va and va > 0xc4000000: vas.append(va)
        i += 1
    return {e[3]: e[1] >> 16
            for e in dt.entries(path, min(vas) - 0x400, max(vas) + 0x400)
            if e[3].startswith(prefix)}
U = lines('scratch/uz801_fw/modem.elf', 'cmcc.c')
S = lines('GitIgnore/MelbonWhiteStock_Dump/modem_extracted/image/modem.elf', 'cmcc.c')
for k in sorted(set(U) & set(S), key=lambda x: S[x]):
    print('%+4d  %-64s UZ801=%d stock=%d' % (U[k] - S[k], k[10:74], U[k], S[k]))
```

**The WMS/MMGSDI equivalence (§6):**

```python
import sys, re; sys.path.insert(0, 'scratch')
import f3parse2 as f3
pat = re.compile(r'^(Putting|Processing) (\d+) (WMS_CMD_\w+)')
for label, path in [('stock', 'scratch/hmu05_stock_boot.bin'), ('uz801', 'scratch/v19_boot.bin')]:
    d, ms = f3.parse(path)
    put = {}; proc = {}
    for m in ms:
        if m['file'] != 'wms.c': continue
        g = pat.match(m['fmt'])
        if not g: continue
        (put if g.group(1) == 'Putting' else proc)[int(g.group(2))] = \
            (put if g.group(1) == 'Putting' else proc).get(int(g.group(2)), 0) + 1
    print(label, {c: (put.get(c, 0), proc.get(c, 0)) for c in sorted(set(put) | set(proc))})
```

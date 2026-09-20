# Engineering Report 143: The UFI001B → HMU05 RF Blocker — Disjoint Transceiver Driver Sets

**Device:** Melbon HMU05 (MSM8916 + **WTR1605** + QFE2320), OpenWrt 25.12.5 / Linux 6.12.94
**Router:** `192.168.8.1` (USB Ethernet `enu1i2`)
**Date:** 2026-09-20
**Subject:** Why the UFI001B port never brings up RF — and why ~100 patches could not have fixed it
**Companion:** Doc 142 (b26 transplant residency root cause)

---

## 1. Purpose and Scope

Doc 142 closed the question *"is the `modem.b26` table transplant resident?"* — answer: **no**.

This report answers the question that actually matters: **why does the RF front-end never come up on
the UFI001B port, and can it be made to?**

The investigation was read-only against the two firmware images plus live measurement of the running
modem. The only state changes were: one router reboot, one QMI mode-preference set (both reverted
below or documented), and binding the DIAG channels for a trace.

---

## 2. SOP Compliance Statement

Per the mandatory dual-firmware comparative protocol (Doc 133 §1, modelled by Doc 134 §2):

- **Backup / version control** — both images were identified by MD5 and the live device matched to
  `modem_ufi001b_patched/image/` before any analysis.
- **Ground-truth verification** — every claim is a **static property of the firmware images**, not an
  inference from a runtime artifact. The decisive evidence (§5) is byte counts in the shipped images.
- **Dual-firmware comparison** — the UFI001B image is compared against the **stock HMU05 image
  throughout**, as required. This is what made the disjoint driver sets visible.
- **No blind patching** — no firmware was modified. This report explicitly recommends **against**
  the patch class that the previous ~100 patches belong to.
- **Error control** — one intermediate hypothesis (ModemManager causing the QMI hang) was raised,
  tested, and **withdrawn** when it failed to reproduce (§10.1). It is recorded rather than hidden.

---

## 3. Executive Summary

> **The UFI001B firmware contains no WTR1605 RF card driver, and the HMU05 firmware contains no
> WTR4905 RF card driver. The two transceiver driver sets are completely disjoint.**
>
> The UFI001B firmware can therefore **never** drive the HMU05's WTR1605 RF front-end, no matter how
> many patches are applied to it. This is a *static* property of the shipped images — it does not
> depend on runtime behaviour, residency, or configuration.
>
> Consequently, patches 41–53 ("transplant HMU05 RF tables into UFI001B") failed for **two
> independent reasons**: the host region is not runtime-stable (Doc 142), **and** the transplant
> addressed the wrong layer — RF **card tables** are not RF **card drivers**.

Measured live, the modem activates **no radio at all** (LTE or WCDMA), reports no signal, and network
scans never complete — exactly what a modem with no driver for its transceiver should do. Everything
else (SIM, carrier MBN, APN, mode preference, QMI, AT) is verified correct.

---

## 4. Method

1. Extract the `rfc_*` **RTTI type-name strings** from every segment of both images. These are the
   C++ class names of the RF card drivers the firmware ships.
2. Count raw transceiver-model substrings (`wtr1605`, `wtr4905`, `wtr2605`, `wtr3925`) in each image.
3. For each UFI001B card class, test whether its type-name string is present in the modem's runtime
   memory, using the eight archived coredumps and the loader's address model from Doc 142.
4. Control: repeat (3) on the stock HMU05 working-state dump.
5. Corroborate with live measurement of the running modem.

---

## 5. Evidence — The Driver Sets Are Disjoint

### 5.1 Transceiver model strings (raw substring counts, all segments)

| string | UFI001B | HMU05 |
| :--- | ---: | ---: |
| `wtr1605` | **10** | 227 |
| `wtr4905` | **296** | **0** |
| `wtr2605` | 5 | 38 |
| `wtr3925` | 0 | 0 |

- **HMU05 has zero `wtr4905` references.** It cannot drive a WTR4905.
- **UFI001B's 10 `wtr1605` hits are all in `modem.b17`** and are the *generic* voltage-regulator
  manager classes `rfc_vreg_mgr_wtr1605_sv` / `rfc_vreg_mgr_wtr1605_nsv` — shared infrastructure, **not**
  card drivers. UFI001B ships **no WTR1605 card**.

### 5.2 Card driver classes (RTTI names)

| | UFI001B | HMU05 |
| :--- | :--- | :--- |
| distinct `rfc_wtr4905_*` classes | **145** | 0 |
| distinct `rfc_wtr1605_*` classes | **0** | **102** |
| intersection | **0** | |

Location of the classes in each image:

- UFI001B: all 145 in **`modem.b26`** (file offsets `0x1f2` … `0x53022`).
- HMU05: all 102 in **`modem.b18`** (e.g. `rfc_wtr1605_chile_rf360_lte_ag` at `b18+0x712922`).

**`modem.b26` is the UFI001B RF card-driver segment.** That is why transplanting "RF tables" into it
was the wrong lever: the segment holds *driver class code and vtables*, not tunable table data.

### 5.3 Runtime residency of the UFI001B card classes

Applying the Doc 142 address model to coredump `p53`:

| result | count | detail |
| :--- | ---: | :--- |
| total UFI001B card classes | 145 | |
| present in runtime memory | **36** | 25 at `0xc4764000 + file_offset` (the relocated first 40 KB), 11 `*_cmn_ag` common classes at `0xc3df4cdc…0xc3df6088` |
| **absent from runtime memory** | **109** | every per-RAT class beyond `b26[0xA000]` — `*_lte_ag`, `*_gsm_ag`, `*_wcdma_ag`, `*_tdscdma_ag`, `*_gnss_ag`, `*_cdma_ag` |

Spot checks (`b26` file offset → runtime VA):

| class | file offset | predicted VA (`0xc4764000+off`) | found |
| :--- | ---: | :--- | :--- |
| `rfc_wtr4905_china_cmn_ag` | `0x1f2` | `0xc47641f2` | **`0xc47641f2` ✓** |
| `rfc_wtr4905_china_lte_ag` | `0x3a92` | `0xc4767a92` | **`0xc4767a92` ✓** |
| `rfc_wtr4905_na2_lte_ag` | > `0xA000` | — | **NONE** |
| `rfc_wtr4905_jp_lte_ag` | > `0xA000` | — | **NONE** |
| `rfc_wtr4905_chile_asdiv_prx_lte_ag` | > `0xA000` | — | **NONE** |
| `rfc_wtr4905_msm8929_cmcc_3m_lte_ag` | > `0xA000` | — | **NONE** |

**Control (stock HMU05, working state):** `rfc_wtr1605_chile_rf360_lte_ag` is at `b18+0x712922`,
declared VA `0x88412922`, and is **found live at exactly `0x88412922`**. The working firmware's card
drivers are resident where declared; the broken port's are not.

---

## 6. Live Corroboration

Measured on the running modem (UFI001B set deployed, MD5-verified):

| probe | result | meaning |
| :--- | :--- | :--- |
| `qmicli --dms-get-model` | `4094` | UFI001B firmware is running |
| `qmicli --dms-get-revision` | `UFI001BC 20211121 1 [Nov 04 2016 02:00:00]` | MPSS 2.0 |
| `--nas-get-serving-system` | `not-registered-searching`, radio interfaces `[0]: 'none'` | **no RAT ever activates** |
| `--nas-get-signal-info` | `QMI protocol error (74) InformationUnavailable` | no signal |
| `--nas-network-scan` | never completes (killed at 150 s, zero results) | **receiver cannot scan** |
| `AT+CSQ` | `+CSQ: 99,99` | invalid / no signal |
| `AT+COPS?` | `+COPS: 0` | no operator |
| `AT+CGATT?` | `+CGATT: 0` | not PS-attached |
| `AT+CREG?` / `+CGREG?` / `+CEREG?` | `0,6` (all three) | the project's known "SMS-only" state |

### 6.1 Everything else is verified correct

These were all checked and are **not** the problem:

| item | state |
| :--- | :--- |
| SIM | present, USIM `ready`, PIN1 disabled, ISIM detected |
| Carrier MBN | `/lib/firmware/modem_pr/mcfg/configs/mcfg_sw/generic/apac/reliance/commerci/mcfg_sw.mbn` — "Active Carrier MBN already matches" |
| APN | `jionet`, IP type `ipv4v6` |
| Carrier autocfg | "Boot-time carrier provisioning completed successfully. No reboot required." (10:47:02) |
| Radio cache | "fully matches SIM requirements for 'Reliance Jio'. No cache flush needed." |
| Mode preference | `'umts, lte'` accepted (QMI `SET SSP mode_pref=0x0018: SUCCESS`) |
| QMI transport | stable (`/dev/wwan0qmi0`) |
| AT transport | stable (`/dev/wwan0at0`) |

**The configuration layer is clean. The failure is in the transceiver driver layer.**

---

## 7. Why Patches 41–53 Could Not Have Worked — Two Independent Reasons

1. **Residency (Doc 142).** The transplant host, `modem.b26`'s declared region (VA `0xc3e0f000`), is
   runtime-owned BSS/data. Only `b26[0:0xA000]` survives, at `0xc4764000`; the transplanted tables at
   `b26+0xe0000` are destroyed before use.
2. **Wrong layer (this report).** Even with a perfect, stable host, the transplant copied HMU05 RF
   **card tables** into a segment that holds UFI001B RF **card driver classes**. Tables parameterise a
   driver; they do not substitute for one. UFI001B has **no WTR1605 driver to parameterise**.

Reason 2 alone is fatal and is independent of any runtime behaviour.

---

## 8. Strategic Implication

The port's premise was *"UFI001B MPSS 2.0's restructured L1 sleep manager removes the 900 s fault;
transplant it onto HMU05."* That premise is sound in principle, but the execution path is blocked at
the RF layer:

- To make UFI001B firmware drive WTR1605, one would have to port **102 WTR1605 card driver classes**
  (and their vtables, dependencies, and the card-factory selection logic) from HMU05's `modem.b18`
  into UFI001B — into a segment that is **not runtime-stable**. This is a very large reverse-engineering
  effort, not a patching exercise.
- The alternative — keep the **stock HMU05 firmware**, which drives WTR1605 correctly and is already
  resident and working — and fix the 900 s fault at its true layer. Prior work in this repo
  (`project_android_differential_900s`, `project_modem_firmware_identical_proof`) established that the
  modem firmware is byte-identical between the working Android device and the failing OpenWrt device,
  and that the 900 s failure is **AP-side**. That is a far smaller and better-evidenced target.

**Recommendation:** stop the RF-table-transplant line of work; re-scope to (a) the AP-side 900 s
mechanism on the **stock** baseband, or (b) an explicit, scoped decision that porting 102 driver
classes is in-budget. Do not spend further effort on patches of the 41–53 class.

---

## 9. Pending Work

| # | Action |
| :--- | :--- |
| 1 | **Stop** producing RF-table-transplant patches into `modem.b26` / VA `0xc3eef000`. |
| 2 | Decide between §8 option (a) and option (b) — this is a project-direction call, not a technical one. |
| 3 | If option (a): re-deploy the stock HMU05 baseband (`/usr/bin/switch_modem_fw.sh stock`) and re-open the AP-side 900 s investigation. |
| 4 | Correct any doc that presents the UFI001B port as "RF-blocked pending patches" — it is blocked pending a driver port. |
| 5 | `Docs/Modem Stability/133_*` and `134_*` should be annotated with this finding. |

---

## 10. Corrections

### 10.1 Withdrawn hypothesis — ModemManager causing the QMI hang

During the session, a QMI NAS client allocation failure (`CID allocation failed in the CTL client:
endpoint hangup`) reproduced the known Doc 134 §6.2 hang. Stopping ModemManager + `qmi-proxy`
**immediately** restored QMI access, suggesting ModemManager as the cause.

**This was tested and withdrawn.** On restarting ModemManager, QMI remained fully stable across six
consecutive polls with mode `'umts, lte'`. The earlier recovery was therefore **coincidental** (the
hang was transient and self-cleared), not causal. ModemManager is **not** established as the cause of
the QMI hang and must not be reported as such. The hang's true trigger remains unresolved — but it is
**not** the blocker this report is about (§5–§7 are static facts and independent of it).

### 10.2 AT-port behaviour with ModemManager running

With ModemManager running, `/dev/wwan0at0` returned only the echoed commands and no modem responses;
with it stopped, the port answered normally (`OK`, `+CSQ: 99,99`, `+CGATT: 0`, …). This is a
**tooling observation** for future sessions — ModemManager holds the AT port — not a diagnosis.

---

## 11. Reproduction Reference

```sh
# ---- Static: transceiver model strings in each image ----
python3 - <<'EOF'
import os, glob
for label, d in [('UFI001B','GitIgnore/compare/modem_ufi001b_extracted/image'),
                 ('HMU05',  'GitIgnore/compare/modem_hmu05_extracted/image')]:
    tot={b'wtr1605':0, b'wtr4905':0, b'wtr2605':0}
    for f in glob.glob(os.path.join(d,'modem.b*')):
        bn=os.path.basename(f)
        if any(x in bn for x in ('.bak','.pre_','.p18_','pre_patch')): continue
        data=open(f,'rb').read()
        for k in tot: tot[k]+=data.count(k)
    print(label, {k.decode():v for k,v in tot.items()})
EOF
# expect: UFI001B {'wtr1605': 10, 'wtr4905': 296, 'wtr2605': 5}
#         HMU05   {'wtr1605': 227, 'wtr4905': 0,  'wtr2605': 38}

# ---- Static: card driver classes ----
python3 - <<'EOF'
import os, glob, re
for label, d, pat in [('UFI001B','GitIgnore/compare/modem_ufi001b_extracted/image', rb'rfc_wtr4905_[a-z0-9_]+'),
                      ('HMU05',  'GitIgnore/compare/modem_hmu05_extracted/image',  rb'rfc_wtr1605_[a-z0-9_]+')]:
    s=set()
    for f in glob.glob(os.path.join(d,'modem.b*')):
        bn=os.path.basename(f)
        if any(x in bn for x in ('.bak','.pre_','.p18_','pre_patch')): continue
        s |= set(m.group().decode() for m in re.finditer(pat, open(f,'rb').read()))
    print(label, len(s), 'distinct classes')
EOF
# expect: UFI001B 145 ; HMU05 102 ; intersection 0

# ---- Residency of UFI001B card classes (uses scratch/firmware/mdt.py) ----
python3 - <<'EOF'
import sys, re; sys.path.insert(0,'scratch/firmware')
from mdt import DumpView, search
b26=open('GitIgnore/compare/modem_ufi001b_patched/image/modem.b26','rb').read()
dump=DumpView('GitIgnore/compare/modem_coredump_p53.elf')
names=sorted(set(m.group().decode() for m in re.finditer(rb'rfc_wtr4905_[a-z0-9_]+', b26)))
present=[n for n in names if search(dump, n.encode(), limit=1)]
print(f"{len(present)}/{len(names)} resident")
EOF
# expect: 36/145

# ---- Live: modem state ----
qmicli -d /dev/wwan0qmi0 --dms-get-model
qmicli -d /dev/wwan0qmi0 --nas-get-serving-system      # radio [0]: 'none'
qmicli -d /dev/wwan0qmi0 --nas-get-signal-info         # InformationUnavailable
qmicli -d /dev/wwan0qmi0 --nas-get-system-selection-preference   # 'umts, lte'
```

---

## 12. Artifacts

| artifact | role |
| :--- | :--- |
| `scratch/firmware/mdt.py` | MDT/coredump inspection library (from Doc 142) |
| `scratch/lte_trace/lte_bringup.diag` | DIAG capture spanning a `mode_pref = 0x0018` change (627 msgs, 121 LOG_F) |
| `GitIgnore/compare/modem_ufi001b_extracted/image/` | UFI001B pristine image |
| `GitIgnore/compare/modem_hmu05_extracted/image/` | stock HMU05 image (control) |
| `GitIgnore/compare/modem_hmu05_live_connected.elf` | stock HMU05 working-state dump (control) |
| `GitIgnore/compare/modem_coredump_p{27,28r,29,49,49b,53,55,56}.elf` | the eight UFI001B dumps |

### 12.1 DIAG trace note

The capture contained equipment IDs **402, 61, 64 only** — VADC (`DalVAdc.c`), GPS/TPC (`tle_log.c`),
and CM/MMOC (`cmdbg.c`, `mmocdbg.c`, `cmlog.c`, `sdss.c`, `policyman_serving_system.c`). **No RF or
ML1 equipment IDs appeared at all.** After `mode_pref = 0x0018` the modem logs `=CM= CMD alloc
u=94, tsk=qmi_mmode` → `=MMOC= Recvd command 1(PROT_GEN_CMD)` → `=MMOC= Recvd report 3(PROT_GEN_CMD_CNF)`
→ `ssscr_int_srv_lost_mode_pref_none` → `ssscr_int_srv_lost_gw_pwrup`, and then nothing but VADC/GPS
spam. This is consistent with §5–§7 (no transceiver driver ⇒ no RF bring-up), though it is
corroborating rather than decisive, since RF equipment IDs may simply not be in the log mask.

---

## 13. Device State at End of Session

- Router up; modem `running`; **UFI001B patched set deployed** and MD5-verified.
- `mode preference` = **`'umts, lte'`**; QMI and AT both responsive; ModemManager running.
- DIAG channels `DIAG` and `DIAG_CNTL` are **bound** (`/dev/rpmsg0`, `/dev/rpmsg1`) — unbind before
  the next reboot if the rpmsg-char NULL-deref (Doc 137, patch 819) is a concern.
- `/root/diag_logtool` and `/root/set_umts_lte` deployed and synced.
- **No firmware was modified.**

---

## 14. Post-Publication Confirmation and One Correction (2026-09-20, Doc 144)

**Confirmed.** A fresh **cold reboot** with the UFI001B set deployed (`modem.mdt = 72ae7f0b…`,
`modem.b26` present) reproduces §6 exactly: `not-registered-searching`, `CS/PS: detached`, radio
`[0]: 'none'`, `--nas-get-signal-info` → `(74) InformationUnavailable`, `wwan0` DOWN — while
`--dms-get-operating-mode` is `online`, mode preference is already `'umts, lte'`, and the SIM is
`ready`/PIN1 `disabled`. That is now **two independent clean boots**. Switching to stock in the same
session immediately yields `registered` / `[0]: 'lte'` / MCC 405 / an address on `wwan0`. **§5–§7 of
this report stand.**

**Correction to a claim that *appeared* to contradict this report.** Doc 141's soak
(`/root/port_soak.log`) shows `wwan0` passing data for 152→706 s, which would suggest the port has RF
after all. It does not: that boot ran the **stock** baseband. See **Doc 144 §7** for the three
independent proofs (clean-boot no-RF; `/lib/firmware` mtime `10:31:35` = a profile switch *after* the
soak; every corroborated "connected" record carrying stock fingerprints). Doc 141 §5/§6 have been
annotated with a correction banner.

**How to apply:** do not treat the soak log as counter-evidence. Read the baseband identity directly
(`--dms-get-revision`, `md5sum /lib/firmware/modem.mdt`, presence of `modem.b26`) before attributing
any runtime result to a baseband.

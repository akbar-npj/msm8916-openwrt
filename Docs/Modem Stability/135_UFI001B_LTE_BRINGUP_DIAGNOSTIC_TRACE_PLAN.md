# Engineering Report 135: UFI001B LTE Bring-Up Hang — Diagnostic Trace Plan

**Document ID:** `135_UFI001B_LTE_BRINGUP_DIAGNOSTIC_TRACE_PLAN.md`
**Date:** September 19, 2026
**Status:** **PLAN — NOT YET EXECUTED.** This is the execution plan for pending-work item 1 in Doc 134 §8.
**Predecessors:** `134_UFI001B_RF_FRONTEND_BRINGUP_SESSION_RECORD_AND_PENDING_WORK.md`, `133_UFI001B_MPSS2_ATTACH_DIAGNOSTIC_AND_PURE_EPS_RESOLUTION.md`
**Target Hardware:** Melbon HMU05 4G USB Dongle (Qualcomm MSM8916 + WTR1605 Transceiver + QFE2320 Front-End)
**Host Environment:** OpenWrt 25.12.5 (Linux 6.12.94 aarch64), router `192.168.8.1`

---

## 1. Purpose

Doc 134 §6 established that **LTE reception is not achieved** on the UFI001B port: with mode preference `'umts, lte'`, the modem never activates a radio interface, network scans never complete, and the QMI stack reproducibly hangs. A modem SSR was observed.

Doc 134 §8 item 1 therefore requires a **diagnostic** step before any further patching:

> Trace the LTE bring-up hang — **DIAGNOSTIC, NOT CORRECTIVE**.

This document is that trace plan. Its single objective is:

> **Determine precisely where in the LTE bring-up path the port fails, and whether the failure is an assert (crash) or a hang (no crash).**

No firmware patching is performed by this plan except in Stage 6, which is explicitly conditional and only after Stages 1–5 have produced a suspect.

---

## 2. Problem Statement (verified input facts)

From Doc 134, all verified on the live device:

| Fact | Evidence |
| :--- | :--- |
| Mode preference can be set to `'umts, lte'` | QMI NAS `0x0033` with `mode_pref = 0x0018` → `SUCCESS` |
| The LTE radio interface **never activates** | `Radio interfaces: '1'`, `[0]: 'none'` — invariant during polling |
| Network scan never completes | Cancelled after 120 s, zero networks |
| No signal / no operator | `AT+CSQ` → `99,99`; `AT+COPS?` → `0` |
| QMI stack hangs | `CID allocation failed in the CTL client: endpoint hangup` |
| **AT path still works while QMI hangs** | `AT+CSQ` returned normally while `qmicli` was hung |
| A modem SSR occurred during LTE testing | `bam-dmux … SSR powerup: modem pc_state=1` at `t ≈ 3291 s` |
| All transceiver patches are present | `0xc0dac83c`=`c03f1048`, `0xc0dacb54`, `0xc0325c38` etc. all byte-verified |
| `--nas-get-signal-strength` is unreliable | `IO: -106 dBm` is a static default (identical across modes) |

**The unexplained gap:** we do not know whether the LTE bring-up **asserts** (modem crash → SSR) or **hangs** (modem alive but stuck). These have completely different diagnostics and must be discriminated first.

---

## 3. Diagnostic Goal and Success Criteria

**Primary goal:** obtain a crash signature (assert `file:line`) or a hang localisation for the LTE bring-up path.

**Success criteria — the plan succeeds if ANY of the following is obtained:**

1. A modem coredump captured at the moment of LTE bring-up failure, with an extractable assert `file:line` and QuRT exception frame; **or**
2. Positive evidence that the modem does **not** crash, with the hang localised to a specific stage (RF init / RRC / NAS / QMI transport); **or**
3. Proof that the failure is outside the modem (qmi-proxy / ModemManager), i.e. the modem is healthy but the host-side QMI path is broken.

**Explicit non-goal:** fixing the failure. That is a separate, later document.

---

## 4. Hypotheses to Discriminate

| ID | Hypothesis | Discriminator |
| :--- | :--- | :--- |
| **H1** | LTE RF bring-up asserts in the transceiver/PRX/DRX path (WTR1605 programming) | Coredump assert signature in the PRX/DRX/WTR area; SSR at the moment of failure |
| **H2** | LTE RF bring-up asserts in the RFC card / band-table selection path | Coredump assert signature in the RFC/card path |
| **H3** | LTE L1/ML1 fails to start (`wl1m.c` / `lte_ml1_*`) | Coredump assert signature in the L1/ML1 area |
| **H4** | The modem does **not** crash; it hangs (no assert) | **No SSR** in dmesg at the moment of failure; remoteproc stays `running` |
| **H5** | The failure is **host-side** (qmi-proxy / ModemManager), not the modem | AT path healthy and modem responsive via AT while QMI hangs |

> [!IMPORTANT]
> **H5 must be tested first** because Doc 134 observed that `AT+CSQ` continued to work while `qmicli` was hung. If H5 holds, most of the "LTE bring-up failure" symptom is a host-side artefact and the modem-side investigation is misdirected.

---

## 5. Instrumentation Inventory (verified present on this platform)

| Facility | Path | Status | Notes |
| :--- | :--- | :--- | :--- |
| Remoteproc coredump mode | `/sys/kernel/debug/remoteproc/remoteproc0/coredump` | **Verified writable.** Was `disabled`; **now set to `inline`** during plan preparation | `disabled` is why the LTE crash produced no dump |
| Remoteproc crash trigger | `/sys/kernel/debug/remoteproc/remoteproc0/crash` | Present (write-only) | Write `1` to force a crash — used to **validate** the capture path |
| Remoteproc recovery | `/sys/kernel/debug/remoteproc/remoteproc0/recovery` | `enabled` | Modem auto-recovers after a crash |
| devcoredump device | `/sys/class/devcoredump/devcd*/data` | Present; `disabled` = `0` (enabled) | Dump appears here after a crash |
| Carveout memories | `/sys/kernel/debug/remoteproc/remoteproc0/carveout_memories` | Present (currently empty) | Populated after a dump |
| Kernel log | `dmesg` | Available | Shows SSR / remoteproc transitions |
| ModemManager log | `/var/log/mm.log` | Available, DEBUG level | QMI message traces |
| pstore / ramoops | `/sys/fs/pstore` | Mounted, currently **empty** | AP-side panics only; 1 MB @ `0x8db00000` |
| AT path | `/dev/wwan0at1` via `mmcli -m <id> --command=` | Works when QMI is hung | **Independent** of QMI — key for H5 |
| Modem DIAG port | `/dev/rpmsg0` (bridged) | **AVAILABLE AND VERIFIED — see Doc 136** | No `/dev/diag` (`CONFIG_DIAG_CHAR` unbuilt), but the SMD `DIAG` channel is bridged via `rpmsg_chrdev` and the modem answers DIAG commands. Raw framing, no HDLC |
| Serial console | `ttyMSM0` @ 115200 | **Configured** (`console=ttyMSM0,115200`) | Physical UART pad access still unconfirmed — Doc 136 §3 |

**Reference artifacts (already captured by previous sessions):**

| Artifact | Path | Use |
| :--- | :--- | :--- |
| Stock HMU05 live connected dump | `GitIgnore/compare/modem_hmu05_live_connected.elf` (85 MB) | **Working-state ground truth** for differential comparison |
| Prior crash dumps | `GitIgnore/compare/modem_coredump_p27/p28r/p29/p49/p49b/p53/p55/p56.elf` | Assert-signature extraction templates |
| Assert filename table helper | `GitIgnore/compare/dump_fn.py` | Maps assert file index → source filename (table lives in `modem.b17`) |

---

## 6. The Trace Plan

### Stage 0 — Prerequisites and safety

| Step | Action | Rationale |
| :--- | :--- | :--- |
| 0.1 | Record `md5sum /lib/firmware/modem.b15` (expect `c69ad8003476146f1263021638dff356`) | Establish that the image is unchanged before/after |
| 0.2 | Confirm `/usr/bin/switch_modem_fw.sh status` and that `stock` profile is available | Recovery path |
| 0.3 | Confirm `recovery` = `enabled` | Modem must auto-recover from the induced crash |
| 0.4 | **Confirm serial console availability** (§12) | Strongly recommended; a whole-router reboot has been observed |
| 0.5 | Ensure the host has ≥200 MB free for dump transfer | Dumps are ~85 MB |

### Stage 1 — Enable and validate capture

| Step | Action |
| :--- | :--- |
| 1.1 | `echo inline > /sys/kernel/debug/remoteproc/remoteproc0/coredump` — **already done**; verify it reads `inline` |
| 1.2 | **Validate the capture path with a controlled crash**: `echo 1 > /sys/kernel/debug/remoteproc/remoteproc0/crash` |
| 1.3 | Confirm a `devcdN` device appears and the modem recovers (`remoteproc0` → `running`) |
| 1.4 | Stream the validation dump to the host and confirm it is a parseable ELF |

> [!IMPORTANT]
> **Step 1.2 must not be skipped.** Validating the capture path with a *known* crash is the only way to distinguish "the modem did not crash" from "the capture path is broken". Without it, a null result in Stage 3 is ambiguous.

### Stage 2 — Establish the working baseline (control)

| Step | Action |
| :--- | :--- |
| 2.1 | With mode preference `'umts'` (current state), confirm QMI is responsive and capture `dmesg` |
| 2.2 | Re-use `modem_hmu05_live_connected.elf` as the stock working-state reference (already available; no re-capture needed) |
| 2.3 | If practical, capture a fresh **stock HMU05** dump while attached to Jio — this is the strongest control, but requires a firmware switch and is optional |

### Stage 3 — Reproduce the failure under capture

| Step | Action |
| :--- | :--- |
| 3.1 | Start a timestamped `dmesg` capture in the background (`dmesg -w > /root/lte_trace.log`) |
| 3.2 | Start a ModemManager log marker (note the current `mm.log` size/time) |
| 3.3 | Apply LTE mode: `/tmp/set_umts_lte` (confirm `SUCCESS`) |
| 3.4 | **Immediately** begin polling the **AT path** (not QMI) every 2 s: `mmcli -m <id> --command="AT+CSQ"`, `AT+CEREG?`, `AT+COPS?` |
| 3.5 | Watch for the discriminator: does dmesg show `SSR before shutdown` / `stopped remote processor`? |
| 3.6 | If a `devcdN` device appears, **stream it to the host immediately** (it can be dismissed/overwritten) |
| 3.7 | Continue observation for at least 5 minutes, or until the fault is captured |

> [!WARNING]
> Poll the **AT** path, not QMI. QMI hangs during this scenario (Doc 134 §6.2) and will stall the capture. The AT path was proven to keep working.

### Stage 4 — Extract the failure signature

| Step | Action |
| :--- | :--- |
| 4.1 | Parse the dump ELF header and memory map |
| 4.2 | Locate the **SMEM crash structure** (previously found near VA `0xc2e71718`) |
| 4.3 | Extract the **QuRT exception frame** (PC, register set, stack) |
| 4.4 | Extract the **assert `file:line`** identifier |
| 4.5 | Map the file index to a filename using `dump_fn.py` against `modem.b17` |
| 4.6 | Disassemble the faulting PC with `llvm-mc -disassemble -triple=hexagon` |

### Stage 5 — AP-side correlation

| Step | Action |
| :--- | :--- |
| 5.1 | Build a timeline from `dmesg` (SSR, bam-dmux transitions) |
| 5.2 | Correlate with the ModemManager log (QMI failures, AT responses) |
| 5.3 | **Test H5 explicitly:** while QMI is hung, verify the AT path still responds; restart `qmi-proxy` alone and see whether QMI recovers without touching the modem |
| 5.4 | Decide: crash (H1–H3) vs hang (H4) vs host-side (H5) |

### Stage 6 — Targeted instrumentation (**CONDITIONAL — only if Stages 1–5 are inconclusive**)

Only permitted if the failure point is still unknown. Any patching here is **diagnostic instrumentation**, not a fix.

| Step | Action |
| :--- | :--- |
| 6.1 | Choose the minimum set of instrumentation points indicated by Stage 4/5 |
| 6.2 | Insert a **non-intrusive marker** (e.g. a write of a constant to a reserved scratch address) — never alter control flow |
| 6.3 | Recompute SHA-256 segment hashes and rebuild `modem.mdt` via `ufi001b_hash_tool.py` (require `Overall: PASS`) |
| 6.4 | Re-run Stage 3 and read the markers to bisect the failure point |
| 6.5 | **Revert instrumentation** once the failure point is known |

---

## 7. Command Reference

### 7.1 Enable capture

```sh
echo inline > /sys/kernel/debug/remoteproc/remoteproc0/coredump
cat /sys/kernel/debug/remoteproc/remoteproc0/coredump      # expect: inline
```

### 7.2 Validate the capture path (controlled crash)

```sh
echo 1 > /sys/kernel/debug/remoteproc/remoteproc0/crash
sleep 10
ls /sys/class/devcoredump/                                  # expect: devcdN
cat /sys/class/devcoredump/devcd1/data > /tmp/validation.elf
echo 1 > /sys/class/devcoredump/devcd1/dismiss
cat /sys/class/remoteproc/remoteproc0/state                 # expect: running
```

### 7.3 Reproduce with capture

```sh
dmesg -w > /root/lte_trace.log &
/tmp/set_umts_lte
qmicli -d /dev/wwan0qmi0 --nas-get-system-selection-preference | grep -i "mode preference"
# then poll the AT path:
MID=$(mmcli -L | grep -oE '[0-9]+' | head -1)
mmcli -m $MID --command="AT+CSQ"
mmcli -m $MID --command="AT+CEREG?"
```

### 7.4 Stream a dump to the host

```sh
# on the router
ls /sys/class/devcoredump/
cat /sys/class/devcoredump/devcd1/data > /root/lte_bringup_crash.elf
echo 1 > /sys/class/devcoredump/devcd1/dismiss
# from the host
scp root@192.168.8.1:/root/lte_bringup_crash.elf GitIgnore/compare/
```

### 7.5 Recovery

```sh
echo stop  > /sys/class/remoteproc/remoteproc0/state
echo start > /sys/class/remoteproc/remoteproc0/state
qmicli -d /dev/wwan0qmi0 --nas-set-system-selection-preference=umts
```

---

## 8. Analysis Method

1. **Dump integrity** — confirm the ELF parses and the memory map is sane before drawing conclusions.
2. **Assert signature** — the modem reports failures as `<file>:<line>`. Extract it; this alone usually identifies the subsystem.
3. **QuRT exception frame** — read PC and registers; disassemble at the PC.
4. **Differential against stock** — compare the same VA/function between the failing port dump and `modem_hmu05_live_connected.elf`. This is the SOP-mandated step and is where the WTR4905 → WTR1605 divergence should become visible.
5. **Cross-check with decompiled sources** — `Docs/Modem Stability/Modem RE/ufi001b/modem_full_decompiled.c` and `.../hmu05/modem_full_decompiled.c`.

**Known assert signatures seen previously (for pattern recognition):**

| Signature | Subsystem |
| :--- | :--- |
| `mmoc.c:2326` | MMOC deactivation watchdog |
| `wl1m.c:8670` | WCDMA L1 firmware activation |
| `lte_ml1_common_timer.c:390` | LTE ML1 periodic timer |
| `wl1trm.c:1073`, `wl1trm.c:1585` | WCDMA TRM |
| `rxdiv.c:613` | RX diversity |

---

## 9. Decision Tree

```text
Stage 1: does a controlled crash produce a valid dump?
├── NO  → capture path is broken. Fix that first. Do not proceed.
└── YES → Stage 3
        │
        Stage 3: does the modem SSR when LTE mode is applied?
        ├── NO SSR  → H4 (hang, no crash)
        │            ├── Is the AT path still responsive?
        │            │   ├── YES → H5: host-side fault. Test qmi-proxy restart.
        │            │   └── NO  → H4 confirmed: modem hung. Need Stage 6 instrumentation.
        │            └── (QMI hang alone is NOT evidence of a modem fault — H5)
        └── SSR     → H1/H2/H3 (crash). Stage 4 extracts the signature.
                      ├── signature in PRX/DRX/WTR area  → H1
                      ├── signature in RFC/card area     → H2
                      └── signature in L1/ML1 area       → H3
```

---

## 10. Abort and Recovery Criteria

**Abort the run immediately if:**

- The whole router reboots (observed once before; see §11).
- The modem fails to recover (`remoteproc0` not `running` after ~60 s).
- More than 3 uncontrolled crashes occur in one run.
- SSH becomes unreachable and serial console is not available.

**Recovery:** §7.5, then confirm mode preference is `'umts'` and QMI is responsive.

---

## 11. Risk and Mitigation

| Risk | Likelihood | Mitigation |
| :--- | :--- | :--- |
| Whole-router reboot during LTE bring-up | **Observed once** | Serial console (§12); do not run unattended; keep the run short |
| Modem fails to auto-recover | Low (`recovery` = `enabled`) | Manual `stop`/`start`; worst case re-flash via `switch_modem_fw.sh` |
| Dump overwritten before capture | Medium | Stream immediately on `devcdN` appearance (§7.4) |
| ~85 MB transfer over SSH fails | Medium | Stream directly with `scp`; verify ELF header after transfer |
| QMI hang stalls the capture loop | High | Poll the **AT** path, not QMI |
| Capture path silently broken | Medium | Stage 1.2 controlled-crash validation — **mandatory** |

---

## 12. Resolved: Serial Console and DIAG Availability

> **Status: ANSWERED — see Doc 136** (`136_HMU05_DIAG_PORT_AND_SERIAL_CONSOLE_DISCOVERY.md`).

**Serial console:** the console *is* configured — `console=tty0 console=ttyMSM0,115200`, with `/dev/ttyMSM0` present (MSM UART @ MMIO `0x78b0000`).

> **Correction to an earlier assumption in this plan:** the serial console does **not** carry modem-side output. The MSM8916 modem is a separate processor and its output does not reach the AP's `ttyMSM0`. The console's real value is **AP-side**: preserving kernel messages across a whole-router reboot (one was observed — Doc 134 §8) and capturing output if the kernel hangs.

**DIAG port: AVAILABLE AND VERIFIED.** The SMD `DIAG` channel can be bridged to `/dev/rpmsg0` via `rpmsg_chrdev`, and the modem answers DIAG commands (`VERNO`, `ESN`, `EXT_BUILD_ID`). See Doc 136 for the enable procedure and protocol details.

**Impact on this plan:** Stage 6 (blind firmware instrumentation) is now a **last resort**, because the DIAG port gives a live modem-side view — which is precisely what is needed to resolve **H4 (hang, no crash)**, where a coredump cannot help.

**Still outstanding for the user:** are the UART test pads physically exposed on the dongle PCB? This cannot be determined remotely (Doc 136 §3.3).

---

## 13. Deliverables

| # | Deliverable | Format |
| :--- | :--- | :--- |
| 1 | Validation dump (proves capture works) | `GitIgnore/compare/modem_coredump_validation.elf` |
| 2 | LTE bring-up failure dump (if it crashes) | `GitIgnore/compare/modem_coredump_lte_bringup.elf` |
| 3 | AP-side timeline | `dmesg` + `mm.log` excerpt |
| 4 | Extracted assert signature and QuRT frame | In the follow-up document |
| 5 | Verdict on H1–H5 | Follow-up document (proposed: Doc 136) |

---

## 14. Appendix A — Reference Artifacts

| Artifact | Path |
| :--- | :--- |
| Stock HMU05 working dump | `GitIgnore/compare/modem_hmu05_live_connected.elf` |
| Prior crash dumps | `GitIgnore/compare/modem_coredump_p{27,28r,29,49,49b,53,55,56}.elf` |
| Assert filename table helper | `GitIgnore/compare/dump_fn.py` |
| UFI001B decompiled source | `Docs/Modem Stability/Modem RE/ufi001b/modem_full_decompiled.c` |
| HMU05 decompiled source | `Docs/Modem Stability/Modem RE/hmu05/modem_full_decompiled.c` |
| Hash / re-sign tool | `GitIgnore/compare/ufi001b_hash_tool.py` |
| Firmware switcher | `/usr/bin/switch_modem_fw.sh` |

## 15. Appendix B — Verified Device State at Plan Authoring

| Item | Value |
| :--- | :--- |
| Firmware MD5 | `c69ad8003476146f1263021638dff356` (unchanged) |
| Mode preference | `'umts'` |
| `remoteproc0` | `running` |
| `recovery` | `enabled` |
| Coredump mode | **`inline`** (changed from `disabled` during plan preparation) |
| QMI | Responsive |
| `set_umts_lte` | Present at `/root/` and `/tmp/` |

> [!NOTE]
> The only device change made while authoring this plan was setting the coredump mode from `disabled` to `inline` (§5). This is a non-invasive, reversible diagnostic setting and is the desired state for executing this plan. It should remain `inline` until the trace is complete.

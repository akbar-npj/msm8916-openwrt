# MSM8916 Modem Stability — Documentation Index

> ⚠️ **CORRECTION NOTICE (2026-10-07).** Most documents in this directory date from
> September 2026 and are built on a **RETRACTED** premise: that the ~900 s modem fatal is
> a 900 s SCLK/DRX maintenance timer that must be neutralised by a **baseband binary
> patch** (`hmu05-patch-modem`, patching `modem.b16` and re-signing `modem.mdt` /
> `modem.b01`). The real cause is the modem RF task's `memcmp` of NV item 2500
> (`NV_FACTORY_DATA_4_I`) against the literal `"HiMI_OK"`; the fix is **AP-side**
> (`himi-ok-guard` — rewrite `HiMI_OK` after every modem boot, and the deadline never
> arms). **The `hmu05-patch-modem` patcher has been removed from the tree** (`57024f2`);
> no baseband patching is performed or required. **Do not follow any `modem.b16` /
> `modem.mdt` / `modem.b01` instruction in this directory.** For the current, verified
> state see [`Modem RE/hmu05/`](Modem%20RE/hmu05/) and the ledger
> `197_UZ801_PORT_LEDGER_AND_MANDATORY_SOP.md` §112.165–§112.171.

## Overview

This directory contains reverse-engineering reports, QMI protocol notes, and engineering
reports addressing the Qualcomm Hexagon QDSP6 v5 modem 15-minute crash and data-stall
issues on OpenWrt Linux 6.12. **Read the correction notice above before citing anything
here** — the September-era reports are retained for history and are largely superseded.

## Master Documentation Index

- [**HMU05 ~900 s Fatal — User Guide: Choose Your Fix**](HMU05_900S_FATAL_FIX_USER_GUIDE.md): ✅ **CURRENT** — user-facing guide: Path A (default `himi-ok-guard`) or Path B (opt-in baseband binary patch, do-it-yourself recipe). No patcher code is shipped.
- [**HMU05 ~900 s Fatal — Fix Options and the Decision**](HMU05_900S_FATAL_FIX_OPTIONS_AND_DECISION.md): ✅ **CURRENT — read this first.** The two ways to fix the ~900 s fatal (modem-firmware binary patch vs the pure-software `himi-ok-guard`), what we chose, and why.
- [**Final HMU05 Modem Stability Resolution Report**](FINAL_HMU05_MODEM_STABILITY_RESOLUTION_REPORT.md): ⚠️ **SUPERSEDED** — the "4-pillar" architecture, including the No-Sleep firmware patch (Pillar 1), predates the 2026-10-06 root-cause finding.
- [**MSM8916 Modem Stability Complete Engineering Report**](MSM8916_Modem_Stability_Complete_Engineering_Report.md): ⚠️ Ghidra decompilation of `LTE_ML1_SLEEPMGR_STM` and the **retracted** 900 s SCLK-drift theory.
- [**Modem Firmware No-Sleep Patching Guide**](MODEM_FIRMWARE_NO_SLEEP_PATCH_GUIDE.md): ⛔ **RETRACTED — do not follow** (baseband `modem.b16` patch opcodes for a false premise).
- [**Qualcomm QMI Time Service Reverse Engineering Report**](QUALCOMM_MSM8916_MODEM_TIME_SERVICE_REPORT.md): Analysis of stock Android `time_daemon`, QMI Service 22 IDL specification, and QRTR packet framing.
- [**Modem 15-Minute Crash & Stall Resolution Summary**](MSM8916_Modem_15Minute_Crash_and_Stall_Resolution.md): ⚠️ Compact September-era summary of BAM-DMUX runtime PM and diagnostic commands.
- [**Patch scope audit — universal vs modem-revision-specific**](PATCH_MODEM_REVISION_SCOPE_AUDIT.md): ✅ **CURRENT** — every `msm89xx/patches/` patch classified; only patch 833 is modem-coupled (gated to HMU05).

---

## Quick Reference: Diagnostic Commands

```bash
# Check modem uptime and verify absence of fatal errors
uptime
dmesg | grep -i -E "fatal error|crash detected|lte_ml1|remoteproc"

# Check cellular network bearer state
mmcli -m 0
ifconfig wwan0

# Check persistent crash logs across reboots
ls -la /sys/fs/pstore/
```

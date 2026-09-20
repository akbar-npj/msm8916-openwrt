# MSM8916 Modem Stability: Gap Analysis & Action Plan

## Current Implementation Status vs. 5-Tier Fix Architecture

Based on a thorough review of all 6 documents in `Docs/Modem Stability/` and the current `main` branch codebase.

---

## Tier-by-Tier Audit

### ✅ Tier 1: Modem Firmware No-Sleep Patch — IMPLEMENTED
- `hmu05-patch-modem` script exists on `hmu05-modem-fix` branch
- `msm-firmware-dumper` has the hook to call it (line 133-135)
- `99-msm89xx-firstboot` has the safety-net call

> [!WARNING]
> **GAP:** The `hmu05-patch-modem` script is **NOT on `main` branch** (`msm89xx/base-files/usr/sbin/hmu05-patch-modem` is MISSING on `main`). The dumper hook calls it but it doesn't exist on main. Must be cherry-picked.

---

### ⚠️ Tier 2: BAM-DMUX & Remoteproc PM Keep-Alive — PARTIALLY IMPLEMENTED

**What's done:**
- Kernel patch `808-bam-dmux-stats.patch` holds `pm_runtime_resume_and_get()` in `netdev_open` and `pm_runtime_put()` in `netdev_stop` — this is the **in-kernel** PM lock.

**What's missing from the docs' recommendation:**
- The engineering report (Section 3.2) recommends **also** setting sysfs runtime PM controls at boot:
  ```sh
  # For remoteproc and BAM-DMUX subsystem
  for f in $(find /sys/devices/platform/soc@0/4080000.remoteproc/ -name "control"); do
      echo on > "$f" 2>/dev/null || true
  done
  for f in $(find /sys/devices/platform/soc@0/4080000.remoteproc/ -name "autosuspend_delay_ms"); do
      echo -1 > "$f" 2>/dev/null || true
  done
  ```
- This sysfs configuration is **NOT present** in `99-msm89xx-firstboot` on `main`.

> [!IMPORTANT]
> The kernel patch alone handles netdev-level PM lock, but the **remoteproc parent device** still has its own autosuspend. The docs recommend disabling it at the sysfs level as a belt-and-suspenders approach.

---

### ❌ Tier 3: Host QMI Time Synchronization — NOT IMPLEMENTED

The QMI Time Service report documents Service 22 (Node 0, Port 11) and the `QMI_TIME_GENOFF_SET_REQ` (0x0020) / `QMI_TIME_REG_IND_REQ` (0x0025) protocol.

> [!NOTE]
> The research report (Document 3) proved that **pure QMI time sync alone cannot prevent the 912s crash** on stock firmware. However, with the No-Sleep patch applied (Tier 1), the time sync daemon serves as an **additional safety layer** keeping SCLK aligned, especially useful if a future firmware update changes sleep behavior.
>
> **Verdict:** Low priority. The No-Sleep firmware patch makes this redundant for now. Can be added later as defense-in-depth.

---

### ✅ Tier 4: Kernel Sysmon SSR Isolation — IMPLEMENTED
- `msm89xx/patches/815-qcom-sysmon-ignore-wcnss-modem-ssr.patch` exists on both `main` and `hmu05-modem-fix`.
- Verified correct: skips sending modem SSR events to WCNSS.

---

### ✅ Tier 5: Read-Write RMTFS — IMPLEMENTED
- `packages/rmtfs/files/rmtfs.init` runs with `-P -s` (no `-r` flag).
- Has explicit comment: *"Do NOT use -r (read-only mode). The modem firmware runs a periodic EFS sync timer every 900 seconds..."*
- Includes `populate_disk_labels()` for partition symlinks.
- Includes `stop_service()` with sync and partition flush.

---

## Critical Gaps to Fix

### Gap 1: `hmu05-patch-modem` Missing on `main`
**Priority:** 🔴 Critical
**Issue:** The firmware patcher script exists on `hmu05-modem-fix` but NOT on `main`. The `msm-firmware-dumper` hook on `main` calls `/usr/sbin/hmu05-patch-modem` which doesn't exist, so the patch silently fails.
**Fix:** Cherry-pick/create `msm89xx/base-files/usr/sbin/hmu05-patch-modem` on `main`.

### Gap 2: Remoteproc/BAM-DMUX Sysfs PM Configuration Missing
**Priority:** 🟡 Medium
**Issue:** The engineering report recommends setting `control=on` and `autosuspend_delay_ms=-1` at the sysfs level for the remoteproc subsystem. This is not in `99-msm89xx-firstboot`.
**Fix:** Add sysfs PM configuration to firstboot or an init script.

### Gap 3: EFS Factory Reset Documentation
**Priority:** 🟢 Low (operational procedure)
**Issue:** If a device has dirty `modemst1`/`modemst2` from prior crashes, it needs EDL erase + clean `fsg.bin` flash. This is documented but not automated.
**Fix:** Document the EDL recovery commands clearly. Optionally add an EFS integrity check to firstboot.

---

## Recommended Action Plan

1. **Immediate:** Cherry-pick `hmu05-patch-modem` to `main` branch so firmware patching works on fresh builds
2. **Immediate:** Add remoteproc sysfs PM keep-alive configuration to `99-msm89xx-firstboot`
3. **Future:** Consider implementing QMI Time Service 22 daemon as defense-in-depth
4. **Future:** Add automated EFS integrity check on first boot

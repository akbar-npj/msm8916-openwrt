# EDL Ground Truth: lk2nd Source vs Linux Patch

> All findings below are drawn from the **actual lk2nd source** at
> `openwrt/dl/msm8916-firmware/lk2nd/` — the same build confirmed to reach USB 9008.

---

## 1. lk2nd complete EDL call chain (verified from source)

```
fastboot "oem reboot-edl"
  └─ lk2nd/fastboot/misc.c: reboot_device(EMERGENCY_DLOAD)
       └─ platform/msm_shared/reboot.c: reboot_device()
            │
            ├─ 1. set_download_mode(EMERGENCY_DLOAD)
            │       └─ target/msm8916/init.c: set_download_mode()
            │               ├─ scm_dload_mode(EMERGENCY_DLOAD)   ← SCM call
            │               └─ pm8x41_clear_pmic_watchdog()      ← clears WD
            │
            ├─ 2. [NO restart-reason write for EMERGENCY_DLOAD]
            │      (writel(reboot_reason, RESTART_REASON_ADDR) is SKIPPED
            │       because of the if() guard on line 127 of reboot.c)
            │
            ├─ 3. reset_type = PON_PSHOLD_WARM_RESET  (0x01)
            │
            ├─ 4. pmic_reset_configure(PON_PSHOLD_WARM_RESET)
            │       └─ target/msm8916/init.c: pm8x41_reset_configure(0x01)
            │               ├─ REG_WRITE(PON_PS_HOLD_RESET_CTL2, 0x0)   ← disable
            │               ├─ udelay(300)
            │               ├─ REG_WRITE(PON_PS_HOLD_RESET_CTL, 0x01)   ← warm reset
            │               └─ REG_WRITE(PON_PS_HOLD_RESET_CTL2, BIT(7)) ← re-enable
            │
            ├─ 5. scm_halt_pmic_arbiter()   ← SCM SVC=0x9 CMD=0x1/0x2
            │      (only called if SCM call is available)
            │
            └─ 6. writel(0x00, MPM2_MPM_PS_HOLD)   ← drop PS_HOLD → warm reset
```

---

## 2. lk2nd register-level detail

### 2a. EDL cookies (`dload_util.c`)

```c
writel(0x322A4F99, target_dload_mode_addr);       // +0x00
writel(0xC67E4350, target_dload_mode_addr + 4);   // +0x04
writel(0x77777777, target_dload_mode_addr + 8);   // +0x08
dsb();
```

### 2b. SCM dload mode (`scm.c` — `scm_dload_mode`)

For `EMERGENCY_DLOAD`, lk2nd sets `dload_type = SCM_EDLOAD_MODE = **0x01**`.

It then writes this to **`TCSR_BOOT_MISC_DETECT = 0x193D100`** via one of:
- `scm_call2_atomic(SCM_SVC_BOOT=0x1, SCM_DLOAD_CMD=0x10, 0x01, 0)`, or
- Direct IO write: `scm_io_write(0x193D100, 0x01)`

**lk2nd writes raw `0x01` to bits [0] of `0x193D100`.**

### 2c. PM8916 PMIC reset configure (`pm8x41.c` — `pm8x41_reset_configure`)

```c
void pm8x41_reset_configure(uint8_t reset_type)   // called with 0x01
{
    REG_WRITE(PON_PS_HOLD_RESET_CTL2, 0x0);        // addr 0x85B — disable reset
    udelay(300);
    REG_WRITE(PON_PS_HOLD_RESET_CTL,  reset_type); // addr 0x85A — set warm reset (0x01)
    REG_WRITE(PON_PS_HOLD_RESET_CTL2, BIT(7));     // addr 0x85B — re-enable (S2_RESET_EN)
}
```

Registers (SPMI bus, PM8916 slave 0):
- `PON_PS_HOLD_RESET_CTL`  = `0x85A`
- `PON_PS_HOLD_RESET_CTL2` = `0x85B`
- `S2_RESET_EN_BIT` = 7 → `BIT(7) = 0x80`

**No watchdog disable is done inside `pm8x41_reset_configure`.** The watchdog disable is `pm8x41_clear_pmic_watchdog()` = `REG_WRITE(0x857, 0x0)`, called in `set_download_mode()`.

### 2d. PMIC arbiter halt (`scm.c` — `scm_halt_pmic_arbiter`)

```c
// ARMv8 path (MSM8916 uses this):
scm_arg.x0 = MAKE_SIP_SCM_CMD(SCM_SVC_PWR=0x9, SCM_IO_DISABLE_PMIC_ARBITER=0x1);
scm_arg.x1 = MAKE_SCM_ARGS(0x1);
scm_arg.x2 = 0;
scm_arg.atomic = true;
scm_call2(&scm_arg, NULL);
// Retries with SCM_IO_DISABLE_PMIC_ARBITER1=0x2 if first fails
```

This is a **TrustZone atomic SMC call** into the secure world: **SVC 0x9, CMD 0x1**.

---

## 3. Critical differences found vs current Linux patch

### 3a. TCSR value mismatch (IMPORTANT)

| | Physical address | Value written | Bits affected |
|---|---|---|---|
| lk2nd EDL | `0x193D100` | `0x01` | bit 0 |
| Linux `QCOM_DLOAD_FULLDUMP` | `0x193D100` | `0x10` (via `FIELD_PREP(GENMASK(5,4), 1)`) | bits 5:4 |
| lk2nd Normal DLOAD | `0x193D100` | `0x10` | bit 4 |

> [!CAUTION]
> **`QCOM_DLOAD_FULLDUMP` (Linux) writes the same value as lk2nd's `SCM_DLOAD_MODE` (Normal DLOAD), not `SCM_EDLOAD_MODE`.**
>
> The Linux kernel's `QCOM_DLOAD_MASK = GENMASK(5,4)` places the value in bits [5:4].
> lk2nd's `SCM_EDLOAD_MODE = 0x01` targets bit [0].
> These are **entirely different bit positions** in `TCSR_BOOT_MISC_DETECT`.

**Conclusion:** Our `qcom_scm_set_edload_mode()` calling `QCOM_DLOAD_FULLDUMP` does NOT reproduce lk2nd's EDL TCSR state. It may be writing the wrong bits entirely.

**Fix options:**
1. Use `qcom_scm_io_writel(0x193D100, 0x01)` directly (matches lk2nd exactly).
2. Drop the SCM TCSR write entirely and rely only on the IMEM cookies + PON warm reset (test first).

### 3b. Restart-reason write — must be removed for EDL

lk2nd skips `writel(reboot_reason, RESTART_REASON_ADDR)` for `EMERGENCY_DLOAD`.
Our patch writes `0x00000001` to `0x860065C` before the cookies. **Remove this.**

### 3c. PMIC reset configure — our reconstruction is correct but incomplete

Our `pm8916_pon_configure_warm_reset()` does:
1. Read PON revision → select `RST_CTL` or `RST_CTL2`
2. Clear `RESET_EN`
3. `udelay(500)` ← lk2nd uses `udelay(300)`, difference is minor
4. Set warm reset mode on `PS_HOLD_RST_CTL`
5. Re-enable `RESET_EN`
6. Disable PON watchdog (`WD_RST_S2_CTL2`)

lk2nd `pm8x41_reset_configure(0x01)` does:
1. `REG_WRITE(PON_PS_HOLD_RESET_CTL2=0x85B, 0x0)` — disable
2. `udelay(300)`
3. `REG_WRITE(PON_PS_HOLD_RESET_CTL=0x85A,  0x01)` — warm reset
4. `REG_WRITE(PON_PS_HOLD_RESET_CTL2=0x85B, 0x80)` — re-enable

**Key differences:**
- lk2nd uses **`CTL2` (0x85B) for enable/disable**, **`CTL` (0x85A) for mode**.
- Our code has a revision check; for rev2 it uses `CTL2` for the enable toggle, `CTL` always for mode. This is consistent with lk2nd behavior for PM8916.
- lk2nd does **not** disable the PON watchdog inside `pm8x41_reset_configure`. It does it separately in `set_download_mode()` via `pm8x41_clear_pmic_watchdog()` → `REG_WRITE(0x857, 0x0)`.
- Our `pm8916_pon_configure_warm_reset()` puts the watchdog disable **inside the PON configure function** (step 6). This is functionally equivalent since it still happens before PS_HOLD drop.

**Assessment:** Our PON configure is functionally correct for the warm-reset goal. Minor timing difference (300 us vs 500 us) is unlikely to matter.

### 3d. PMIC arbiter halt — missing and non-trivial to add

lk2nd calls `scm_halt_pmic_arbiter()` → **SMC: SVC=0x09, CMD=0x01** (atomic).

The mainline Linux 6.12 `qcom_scm` driver has **no public API** for this call.
The service `QCOM_SCM_SVC_PWR = 0x09` is entirely absent from the Linux internal `qcom_scm.h`.

**To implement this in Linux**, we need to add a new function directly in `qcom_scm.c`:

```c
/* In qcom_scm.c — new function */
static void qcom_scm_halt_pmic_arbiter(void)
{
    struct qcom_scm_desc desc = {
        .svc   = 0x09,  /* SCM_SVC_PWR */
        .cmd   = 0x01,  /* SCM_IO_DISABLE_PMIC_ARBITER */
        .owner = ARM_SMCCC_OWNER_SIP,
    };
    int ret;

    ret = qcom_scm_call_atomic(__scm->dev, &desc, NULL);
    if (ret) {
        desc.cmd = 0x02; /* SCM_IO_DISABLE_PMIC_ARBITER1 */
        qcom_scm_call_atomic(__scm->dev, &desc, NULL);
    }
}
```

And export it or call it inline from `qcom_scm_set_edload_mode()`.

### 3e. `flush_cache_all()` + `mdelay(50)` — remove

lk2nd does neither. These are Linux-specific additions. For a faithful port, remove them.

### 3f. Memory barrier — `mb()` vs `dsb()`

On arm64, `mb()` = `dsb(sy)` which is a full system barrier — **strictly stronger than lk2nd's `dsb()`** which is the same instruction. This is fine to keep.

---

## 4. Corrected Linux sequence

```
reboot("edl")
    |
    v
[skip restart-reason write]
    |
    v
write IMEM cookies at 0x08600FE0 (3 × u32, values match lk2nd exactly)
    |
    v
mb()  [= dsb(sy), acceptable]
    |
    v
qcom_scm_io_writel(0x193D100, 0x01)  ← lk2nd SCM_EDLOAD_MODE=0x01 directly
    |
    v
pm8916_pon_configure_warm_reset()    ← already correct functionally
    |
    v
qcom_scm_halt_pmic_arbiter()         ← add this: SVC=0x09, CMD=0x01 (atomic)
    |
    v
writel(0, msm_ps_hold)               ← drop PS_HOLD
    |
    v
[no flush_cache_all, no mdelay(50)]
    |
    v
warm reset → PBL → USB 9008
```

---

## 5. Summary of required patch changes

| # | Change | File | Priority |
|---|---|---|---|
| 1 | Remove restart-reason write from EDL path | `msm-poweroff.c` | **High** |
| 2 | Replace `QCOM_DLOAD_FULLDUMP` with direct `qcom_scm_io_writel(0x193D100, 0x01)` | `qcom_scm.c` | **Critical** |
| 3 | Add `qcom_scm_halt_pmic_arbiter()` (SVC=0x9, CMD=0x1, atomic) and call it | `qcom_scm.c` + `msm-poweroff.c` | **High** |
| 4 | Remove `flush_cache_all()` and `mdelay(50)` | `msm-poweroff.c` | Medium |
| 5 | Verify PM8916 revision path selects `CTL2` for enable/disable (matches lk2nd) | `qcom-pon.c` | Low (looks correct) |

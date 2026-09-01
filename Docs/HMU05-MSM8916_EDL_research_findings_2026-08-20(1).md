# MSM8916 EDL / Emergency DLOAD Research Findings

**Project:** UFI001B MSM8916 OpenWrt port  
**Reference kernel:** ZTE MSM8916 Android kernel  
**Purpose:** Reconstruct the Android software EDL (Emergency DLOAD / EDLOAD) path and reproduce it in OpenWrt.

---

## 1. Current objective

The device has two known EDL entry mechanisms:

1. **Hardware EDL** — the physical button at the back is pressed during power-on. This reliably enters Qualcomm Sahara/Firehose EDL.
2. **Software EDL** — Android has a software reboot-to-EDL path, but the current OpenWrt `reboot-edl` implementation does not enter EDL.

The goal is therefore to reconstruct the **exact Android software Emergency DLOAD sequence** and reproduce it in OpenWrt.

The current investigation is focused on determining exactly what happens between:

    Android software reboot request
            ↓
    restart subsystem
            ↓
    EDL / DLOAD preparation
            ↓
    Qualcomm SCM / IMEM / PMIC operations
            ↓
    reset
            ↓
    Qualcomm Boot ROM
            ↓
    Sahara / EDL

---

## 2. Important source-code situation

We **do not have the original kernel source for UFI001B**.

We do have the complete ZTE MSM8916 Android kernel source:

    ~/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916

This ZTE kernel is being used as a **reference kernel**.

Therefore:

- The ZTE kernel is not assumed to be identical to UFI001B.
- ZTE kernel code is being used to identify MSM8916-era Qualcomm EDL/DLOAD mechanisms.
- The complete Android DTS for UFI001B is available separately.
- The UFI001B DTS can be used to identify device-specific addresses and configuration.
- Runtime behaviour on UFI001B remains the final authority.
- Reference-kernel findings must be distinguished from confirmed UFI001B behaviour.
- We should not blindly copy ZTE-specific hardware addresses into OpenWrt.

The final OpenWrt implementation should reproduce only mechanisms demonstrated to be required by UFI001B.

---

## 3. Hardware EDL is confirmed

The physical EDL button path produces Qualcomm Sahara information including:

    HWID:       0x007050e100000000
    MSM_ID:     0x007050e1
    CPU:        MSM8916
    PK_HASH:    0xcc3153a80293939b90d02d3bf8b23e0292e452fef662c74998421adad42a380f
    Serial:     0x039b7f7f

A Firehose loader can be uploaded and the eMMC GPT can be read.

Therefore:

- Qualcomm hardware EDL works.
- MSM8916 enters Sahara correctly.
- Firehose communication works.
- eMMC access through EDL works.
- Qualcomm Boot ROM EDL functionality is confirmed.
- The remaining problem is specifically reproducing the software-triggered Emergency DLOAD transition.

---

## 4. Original Android firmware

Extracted firmware directory:

    ~/Projects/Extracted_Firmware/melbon/Black/orig_fw_with_esim_soldered/dumps/system/system

A relevant artifact was found:

    bin/dload.bin

Properties:

    Size:   approximately 2.3 KiB
    SHA256: f93e6307c92f917bdadc4807c2c02dbf6cf530ea90b56a4cb4a048c45099b40b
    Type:   raw data

It is not an ELF:

    readelf: Error: Not an ELF file
    objdump: file format not recognized

However, the contents look like a small ARM/Thumb executable image, with a vector table and Thumb instructions.

It also contains:

    TERMINATE

The exact role of `dload.bin` has not yet been proven.

It remains a promising later reverse-engineering target.

---

## 5. Android reference kernel

Reference source:

    ~/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916

Important file:

    drivers/power/reset/msm-poweroff.c

Important header:

    include/soc/qcom/restart.h

The reference kernel contains:

    #define RESTART_NORMAL 0x0
    #define RESTART_DLOAD  0x1

    void msm_set_restart_mode(int mode);
    void msm_set_download_mode(int mode);

This establishes that the Android restart subsystem explicitly supports a DLOAD restart mode.

The reference kernel also contains:

    void msm_set_restart_mode(int mode)
    {
            restart_mode = mode;
    }
    EXPORT_SYMBOL(msm_set_restart_mode);

and:

    void msm_set_download_mode(int mode)
    {
            download_mode = mode;
    }
    EXPORT_SYMBOL(msm_set_download_mode);

---

## 6. Android DLOAD definitions

The ZTE Android kernel contains:

    #define SCM_DLOAD_MODE   0X10
    #define SCM_EDLOAD_MODE  0X01
    #define SCM_DLOAD_CMD    0x10

Therefore:

    SCM_DLOAD_CMD   = 0x10
    SCM_DLOAD_MODE  = 0x10
    SCM_EDLOAD_MODE = 0x01

This gives an important distinction.

### Normal DLOAD

    SCM service = 0x01
    SCM command = 0x10
    argument 0  = 0x10
    argument 1  = 0x00

### Emergency EDLOAD

    SCM service = 0x01
    SCM command = 0x10
    argument 0  = 0x01
    argument 1  = 0x00

Therefore:

    DLOAD_MODE != EDLOAD_MODE

even though both use:

    SCM_DLOAD_CMD = 0x10

This is one of the most important findings in the entire investigation.

---

## 7. Android normal DLOAD implementation

The reference kernel contains:

    static void set_dload_mode(int on)
    {
            int ret;

            if (dload_mode_addr) {
                    __raw_writel(on ? 0xE47B337D : 0, dload_mode_addr);
                    __raw_writel(on ? 0xCE14091A : 0,
                                 dload_mode_addr + sizeof(unsigned int));
                    mb();
            }

            if (scm_dload_supported) {
                    ret = scm_call_atomic2(SCM_SVC_BOOT,
                                    SCM_DLOAD_CMD,
                                    on ? SCM_DLOAD_MODE : 0,
                                    0);
                    if (ret)
                            pr_err("Failed to set DLOAD mode: %d\n", ret);
            }

            dload_mode_enabled = on;
    }

For normal DLOAD:

    service = SCM_SVC_BOOT
    command = SCM_DLOAD_CMD = 0x10
    arg0    = SCM_DLOAD_MODE = 0x10
    arg1    = 0x00

The normal DLOAD mechanism may therefore involve:

    IMEM download-mode structure
            +
    SCM DLOAD command

---

## 8. Android Emergency DLOAD implementation

The most important discovery in the reference kernel is:

    static void enable_emergency_dload_mode(void)
    {
            int ret;

            if (emergency_dload_mode_addr) {
                    __raw_writel(EMERGENCY_DLOAD_MAGIC1,
                                 emergency_dload_mode_addr);

                    __raw_writel(EMERGENCY_DLOAD_MAGIC2,
                                 emergency_dload_mode_addr +
                                 sizeof(unsigned int));

                    __raw_writel(EMERGENCY_DLOAD_MAGIC3,
                                 emergency_dload_mode_addr +
                                 (2 * sizeof(unsigned int)));

                    qpnp_pon_wd_config(0);
                    mb();
            }

            if (scm_dload_supported) {
                    ret = scm_call_atomic2(SCM_SVC_BOOT,
                                    SCM_DLOAD_CMD,
                                    SCM_EDLOAD_MODE,
                                    0);

                    if (ret)
                            pr_err("Failed to set EDLOAD mode: %d\n", ret);
            }
    }

This gives us the Android Emergency DLOAD SCM call explicitly:

    scm_call_atomic2(SCM_SVC_BOOT,
                     SCM_DLOAD_CMD,
                     SCM_EDLOAD_MODE,
                     0);

Numerically:

    svc     = 0x01
    cmd     = 0x10
    arg0    = 0x01
    arg1    = 0x00

This is currently the strongest concrete reference for the Android software EDL mechanism.

---

## 9. Android Emergency DLOAD magic values

The reference kernel defines:

    #define EMERGENCY_DLOAD_MAGIC1   0x322A4F99
    #define EMERGENCY_DLOAD_MAGIC2   0xC67E4350
    #define EMERGENCY_DLOAD_MAGIC3   0x77777777

These are written as:

    offset +0 = 0x322A4F99
    offset +4 = 0xC67E4350
    offset +8 = 0x77777777

Therefore the Android Emergency DLOAD path has at least these components:

    1. Emergency DLOAD IMEM magic values
    2. Qualcomm SCM EDLOAD command
    3. PMIC watchdog configuration

The exact role of each component is still being established.

---

## 10. Android `reboot edl` handling

The reference kernel's `msm_restart_prepare()` contains:

    if (cmd != NULL) {
            if (!strncmp(cmd, "bootloader", 10)) {
                    __raw_writel(0x77665500, restart_reason);
            } else if (!strncmp(cmd, "recovery", 8)) {
                    __raw_writel(0x77665502, restart_reason);
            } else if (!strcmp(cmd, "rtc")) {
                    __raw_writel(0x77665503, restart_reason);
            } else if (!strncmp(cmd, "oem-", 4)) {
                    unsigned long code;
                    int ret;

                    ret = kstrtoul(cmd + 4, 16, &code);
                    if (!ret)
                            __raw_writel(0x6f656d00 | (code & 0xff),
                                          restart_reason);
            } else if (!strncmp(cmd, "edl", 3)) {
                    enable_emergency_dload_mode();
            } else {
                    __raw_writel(0x77665501, restart_reason);
            }
    }

Therefore the Android command:

    reboot edl

eventually reaches:

    enable_emergency_dload_mode();

This is a critical finding because it proves that the Android software EDL request is explicitly handled by the MSM restart driver.

---

## 11. Android restart preparation ordering

The reference kernel's `msm_restart_prepare()` performs DLOAD preparation before the final reset.

Relevant code:

    static void msm_restart_prepare(const char *cmd)
    {
    #ifdef CONFIG_MSM_DLOAD_MODE

            set_dload_mode(download_mode &&
                           (in_panic || restart_mode == RESTART_DLOAD));

    #endif

            if (get_dload_mode() || (cmd != NULL && cmd[0] != '\0'))
                    qpnp_pon_system_pwr_off(PON_POWER_OFF_WARM_RESET);
            else
                    qpnp_pon_system_pwr_off(PON_POWER_OFF_HARD_RESET);

            if (cmd != NULL) {
                    ...
                    else if (!strncmp(cmd, "edl", 3)) {
                            enable_emergency_dload_mode();
                    }
                    ...
            }

            flush_cache_all();

    #ifndef CONFIG_ARM64
            outer_flush_all();
    #endif
    }

Important observation:

The reference code does not simply perform:

    SCM EDLOAD call
    ↓
    reset

There are additional restart/PMIC operations around it.

The exact ordering must therefore be preserved during further reconstruction.

---

## 12. Android final reset path

The reference kernel contains:

    static void do_msm_restart(enum reboot_mode reboot_mode, const char *cmd)
    {
            int ret;

            pr_notice("Going down for restart now\n");

            msm_restart_prepare(cmd);

            ret = scm_call_atomic2(SCM_SVC_BOOT,
                                   SCM_WDOG_DEBUG_BOOT_PART,
                                   1, 0);

            if (ret)
                    pr_err("Failed to disable wdog debug: %d\n", ret);

            halt_spmi_pmic_arbiter();

            __raw_writel(0, msm_ps_hold);

            mdelay(10000);
    }

Therefore the overall Android restart path includes:

    msm_restart_prepare(cmd)
            ↓
    SCM watchdog/debug operation
            ↓
    halt SPMI PMIC arbiter
            ↓
    PS_HOLD = 0
            ↓
    reset/power transition

This is important because the SCM EDLOAD operation is not necessarily the final operation causing the physical reset.

---

## 13. Android PMIC watchdog operation

The Emergency DLOAD function contains:

    qpnp_pon_wd_config(0);

The comment in the Android source states:

    /* Need disable the pmic wdt, then the emergency dload mode
     * will not auto reset. */

This is highly significant.

The Android implementation intentionally disables the PMIC watchdog when preparing Emergency DLOAD.

Therefore:

    Emergency DLOAD
            ↓
    PMIC watchdog handling
            ↓
    SCM EDLOAD
            ↓
    reset/restart

is currently a strong working model.

However, the exact PMIC semantics on UFI001B must still be verified.

---

## 14. Android `RESTART_DLOAD` mechanism

The reference kernel contains:

    #define RESTART_NORMAL 0x0
    #define RESTART_DLOAD  0x1

and:

    void msm_set_restart_mode(int mode)
    {
            restart_mode = mode;
    }

The restart preparation code checks:

    restart_mode == RESTART_DLOAD

and then calls:

    set_dload_mode(...);

Specifically:

    set_dload_mode(download_mode &&
                   (in_panic || restart_mode == RESTART_DLOAD));

Therefore there are two related but distinct mechanisms:

### Normal DLOAD mode

Controlled by:

    restart_mode == RESTART_DLOAD

and:

    set_dload_mode()

### Emergency EDLOAD

Triggered by:

    cmd beginning with "edl"

and:

    enable_emergency_dload_mode()

This distinction must be preserved.

---

## 15. Android `msm_set_download_mode()` usage

Searching the reference kernel:

    grep -Rni --exclude-dir=.git \
        'msm_set_download_mode' \
        .

produced:

    ./drivers/char/diag/diagfwd.c:1624:
        msm_set_download_mode(1);

    ./drivers/power/reset/msm-poweroff.c:170:
        void msm_set_download_mode(int mode)

    ./drivers/power/reset/msm-poweroff.c:174:
        EXPORT_SYMBOL(msm_set_download_mode);

    ./include/soc/qcom/restart.h:21:
        void msm_set_download_mode(int mode);

This demonstrates that the DLOAD mode control is used by other Android subsystems as well.

---

## 16. Android SCM atomic call implementation

The reference Android kernel contains the older Qualcomm SCM implementation:

    s32 scm_call_atomic2(u32 svc, u32 cmd, u32 arg1, u32 arg2)
    {
            int context_id;
            register u32 r0 asm("r0") = SCM_ATOMIC(svc, cmd, 2);
            register u32 r1 asm("r1") = (uintptr_t)&context_id;
            register u32 r2 asm("r2") = arg1;
            register u32 r3 asm("r3") = arg2;

            asm volatile(
                    __asmeq("%0", R0_STR)
                    __asmeq("%1", R0_STR)
                    __asmeq("%2", R1_STR)
                    __asmeq("%3", R2_STR)
    #ifdef REQUIRES_SEC
                    ".arch_extension sec\n"
    #endif
                    "smc    #0\n"
                    : "=r" (r0)
                    : "r" (r0), "r" (r1), "r" (r2), "r" (r3));

            return r0;
    }

This means the Android reference kernel directly performs an ARM SMC call.

The relevant register mapping is:

    r0 = SCM atomic function identifier
    r1 = context pointer
    r2 = first SCM argument
    r3 = second SCM argument

For the Android EDLOAD call:

    svc  = SCM_SVC_BOOT
    cmd  = SCM_DLOAD_CMD
    arg1 = SCM_EDLOAD_MODE
    arg2 = 0

---

## 17. Modern OpenWrt Qualcomm SCM implementation

The OpenWrt kernel is:

    ~/Projects/msm8916-openwrt-clean/openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94

The modern Qualcomm SCM implementation is located at:

    drivers/firmware/qcom/qcom_scm.c
    drivers/firmware/qcom/qcom_scm-smc.c

Unlike the old Android reference implementation, modern Linux uses a descriptor:

    struct qcom_scm_desc {
            u32 svc;
            u32 cmd;
            u32 arginfo;
            u64 args[MAX_QCOM_SCM_ARGS];
            u32 owner;
    };

The SMC layer converts this descriptor into ARM SMCCC arguments.

---

## 18. Modern OpenWrt SCM call path

The OpenWrt `qcom_scm_call_atomic()` implementation selects the calling convention:

    static int qcom_scm_call_atomic(struct device *dev,
                                    const struct qcom_scm_desc *desc,
                                    struct qcom_scm_res *res)
    {
            switch (__get_convention()) {
            case SMC_CONVENTION_ARM_32:
            case SMC_CONVENTION_ARM_64:
                    return scm_smc_call(dev, desc, res, true);

            case SMC_CONVENTION_LEGACY:
                    return scm_legacy_call_atomic(dev, desc, res);

            default:
                    pr_err("Unknown current SCM calling convention.\n");
                    return -EINVAL;
            }
    }

Therefore the modern OpenWrt path may use either:

    ARM 32-bit SMC
    ARM 64-bit SMC
    legacy Qualcomm SCM

depending on the detected convention.

---

## 19. Modern OpenWrt SMC argument construction

In:

    drivers/firmware/qcom/qcom_scm-smc.c

the SMC descriptor is converted using:

    smc.args[0] = ARM_SMCCC_CALL_VAL(
            smccc_call_type,
            qcom_smccc_convention,
            desc->owner,
            SCM_SMC_FNID(desc->svc, desc->cmd));

    smc.args[1] = desc->arginfo;

    for (i = 0; i < SCM_SMC_N_REG_ARGS; i++)
            smc.args[i + SCM_SMC_FIRST_REG_IDX] = desc->args[i];

The function ID is:

    #define SCM_SMC_FNID(s, c) \
            ((((s) & 0xFF) << 8) | ((c) & 0xFF))

Therefore for:

    svc = 0x01
    cmd = 0x10

the SCM function identifier is:

    ((0x01 & 0xff) << 8) | (0x10 & 0xff)

which is:

    0x0110

The actual ARM SMCCC X0 additionally contains:

    ARM_SMCCC_CALL_VAL(
        call type,
        SMC convention,
        owner,
        function ID
    )

Therefore we must not compare the Android legacy `r0` value directly with modern Linux `X0` without decoding the calling convention.

---

## 20. Modern OpenWrt descriptor for Android EDLOAD

The current experiment introduced an Android-compatible helper:

    static int __qcom_scm_set_edload_mode_android(void)
    {
            struct qcom_scm_desc desc = {
                    .svc = QCOM_SCM_SVC_BOOT,
                    .cmd = QCOM_SCM_BOOT_SET_DLOAD_MODE,
                    .arginfo = QCOM_SCM_ARGS(2),
                    .args[0] = 0x01,
                    .args[1] = 0x00,
                    .owner = ARM_SMCCC_OWNER_SIP,
            };

            pr_emerg("MSM8916 EDL TEST: ANDROID EDLOAD SCM CALL\n");

            pr_emerg("MSM8916 EDL TEST: svc=0x%x cmd=0x%x arg0=0x%llx arg1=0x%llx\n",
                     desc.svc, desc.cmd, desc.args[0], desc.args[1]);

            return qcom_scm_call_atomic(__scm->dev, &desc, NULL);
    }

This was intentionally constructed to reproduce the Android reference call:

    scm_call_atomic2(SCM_SVC_BOOT,
                     SCM_DLOAD_CMD,
                     SCM_EDLOAD_MODE,
                     0);

Numerically:

    svc     = 0x01
    cmd     = 0x10
    arg0    = 0x01
    arg1    = 0x00

---

## 21. Important correction regarding `__qcom_scm_set_dload_mode()`

The existing modern OpenWrt function is:

    static int __qcom_scm_set_dload_mode(struct device *dev, bool enable)
    {
            struct qcom_scm_desc desc = {
                    .svc = QCOM_SCM_SVC_BOOT,
                    .cmd = QCOM_SCM_BOOT_SET_DLOAD_MODE,
                    .arginfo = QCOM_SCM_ARGS(2),
                    .args[0] = QCOM_SCM_BOOT_SET_DLOAD_MODE,
                    .owner = ARM_SMCCC_OWNER_SIP,
            };

            desc.args[1] =
                    enable ? QCOM_SCM_BOOT_SET_DLOAD_MODE : 0;

            return qcom_scm_call_atomic(__scm->dev, &desc, NULL);
    }

This is not identical to the Android reference EDLOAD call.

The existing modern function uses:

    args[0] = QCOM_SCM_BOOT_SET_DLOAD_MODE

which corresponds to:

    0x10

for the MSM8916 SCM DLOAD command.

Android Emergency DLOAD instead uses:

    args[0] = SCM_EDLOAD_MODE

which is:

    0x01

Therefore the experiment deliberately changed only the EDLOAD call to:

    args[0] = 0x01
    args[1] = 0x00

while leaving the normal DLOAD implementation intact.

---

## 22. Current OpenWrt EDL instrumentation

The current OpenWrt kernel contains instrumentation in:

    drivers/firmware/qcom/qcom_scm-smc.c

The instrumentation is placed after:

    smc.args[0] = ...
    smc.args[1] = ...
    for (...) ...
        smc.args[...] = desc->args[...]

and checks:

    if (desc->svc == QCOM_SCM_SVC_BOOT &&
        desc->cmd == QCOM_SCM_BOOT_SET_DLOAD_MODE)

It logs:

    MSM8916 EDL TEST: DLOAD SMC DETECTED

and:

    MSM8916 EDL TEST: svc=0x%x cmd=0x%x arginfo=0x%x owner=0x%x atomic=%d

and:

    MSM8916 EDL TEST: desc args[0]=0x%llx args[1]=0x%llx

and:

    MSM8916 EDL TEST: SMC X0=0x%lx X1=0x%lx X2=0x%lx X3=0x%lx X4=0x%lx X5=0x%lx

This instrumentation is intended to establish the exact SMC values reaching the ARM SMCCC layer.

---

## 23. Current OpenWrt RMW instrumentation

The OpenWrt `qcom_scm_io_rmw()` function has been instrumented.

Current logging includes:

    MSM8916 EDL TEST: RMW ENTERED addr=%pa mask=0x%08x val=0x%08x

    MSM8916 EDL TEST: RMW READ FAILED ret=%d

    MSM8916 EDL TEST: RMW READ old=0x%08x

    MSM8916 EDL TEST: RMW CALCULATED new=0x%08x

    MSM8916 EDL TEST: RMW WRITE returned %d

    MSM8916 EDL TEST: RMW READBACK FAILED ret=%d

    MSM8916 EDL TEST: RMW READBACK value=0x%08x

The purpose is to determine whether the modern Linux DLOAD MMIO path is actually touching the expected register and what values are read/written.

---

## 24. Boot-time SCM RMW observation

Normal OpenWrt boot produced:

    MSM8916 EDL TEST: EDL setting DLOAD FULLDUMP
    MSM8916 EDL TEST: RMW ENTERED addr=0x000000000193d100 mask=0x00000030 val=0x00000000
    MSM8916 EDL TEST: RMW READ old=0x00000000
    MSM8916 EDL TEST: RMW CALCULATED new=0x00000000
    MSM8916 EDL TEST: RMW WRITE returned 0
    MSM8916 EDL TEST: RMW READBACK value=0x00000000

This is a boot-time SCM DLOAD-related operation.

It should **not automatically be treated as the actual EDL trigger**.

The important point is that:

    dload_mode_addr = 0x193d100

was observed on the running UFI001B OpenWrt system.

The current read value was:

    0x00000000

and the mask was:

    0x00000030

The operation resulted in:

    old      = 0x00000000
    calculated new = 0x00000000
    write    = success
    readback = 0x00000000

This indicates that the current OpenWrt DLOAD MMIO mechanism is not obviously setting an active DLOAD value during this boot-time operation.

---

## 25. OpenWrt restart-driver instrumentation

The OpenWrt restart driver was instrumented.

Boot output showed:

    MSM8916 EDL TEST: msm_restart_probe() ENTERED
    MSM8916 EDL TEST: PS_HOLD ioremap OK
    MSM8916 EDL TEST: RESTART sys-off registration returned 0
    MSM8916 EDL TEST: POWER_OFF sys-off registration returned 0
    MSM8916 EDL TEST: msm_restart_probe() SUCCESS

This confirms that the MSM restart driver successfully probes and registers the restart/power-off handlers.

Therefore the problem is not simply:

    restart driver failed to probe

---

## 26. `reboot-edl` command

The OpenWrt userspace command was tested:

    reboot-edl

It prints:

    Calling LINUX_REBOOT_CMD_RESTART2 with command: edl

Therefore userspace is correctly issuing:

    LINUX_REBOOT_CMD_RESTART2

with:

    command = "edl"

The remaining question is whether the kernel restart framework reaches the MSM restart driver's EDL handling and whether that path correctly reproduces the Android sequence.

---

## 27. OpenWrt `qcom_scm_set_edload_mode()`

The modern OpenWrt tree contains:

    int qcom_scm_set_edload_mode(void)

The current implementation logs:

    MSM8916 EDL TEST: qcom_scm_set_edload_mode() ENTERED

It then logs:

    MSM8916 EDL TEST: DLOAD PATH dload_mode_addr=%pa

If an MMIO address exists:

    MSM8916 EDL TEST: DLOAD PATH using MMIO

and performs:

    qcom_scm_io_rmw(
        __scm->dload_mode_addr,
        QCOM_DLOAD_MASK,
        FIELD_PREP(QCOM_DLOAD_MASK,
                   QCOM_DLOAD_FULLDUMP)
    );

Otherwise it checks:

    __qcom_scm_is_call_available(
        __scm->dev,
        QCOM_SCM_SVC_BOOT,
        QCOM_SCM_BOOT_SET_DLOAD_MODE
    )

and can enter the SCM path.

---

## 28. Current Android EDLOAD experiment

A backup was first made before modifying the working kernel source.

Backup directory:

    ~/Projects/msm8916-openwrt-clean/kernel-modified-backup/Modified/EDL-probe-test-final-source

The current experimental source was saved as:

    qcom_scm-android-edload-call-test.c

SHA256:

    5fc7a9dc4b57cb75187324b76edb278677ce41ad5393744c707727f692abfc0d

The experimental helper was inserted into:

    drivers/firmware/qcom/qcom_scm.c

The helper is:

    static int __qcom_scm_set_edload_mode_android(void)
    {
            struct qcom_scm_desc desc = {
                    .svc = QCOM_SCM_SVC_BOOT,
                    .cmd = QCOM_SCM_BOOT_SET_DLOAD_MODE,
                    .arginfo = QCOM_SCM_ARGS(2),
                    .args[0] = 0x01,
                    .args[1] = 0x00,
                    .owner = ARM_SMCCC_OWNER_SIP,
            };

            pr_emerg("MSM8916 EDL TEST: ANDROID EDLOAD SCM CALL\n");
            pr_emerg("MSM8916 EDL TEST: svc=0x%x cmd=0x%x arg0=0x%llx arg1=0x%llx\n",
                     desc.svc, desc.cmd, desc.args[0], desc.args[1]);

            return qcom_scm_call_atomic(__scm->dev, &desc, NULL);
    }

The existing EDL call site was changed from:

    ret = __qcom_scm_set_dload_mode(__scm->dev, true);

to:

    ret = __qcom_scm_set_edload_mode_android();

This experiment is specifically designed to answer:

> Does the Android reference SCM EDLOAD command itself work when translated into the modern Linux Qualcomm SCM interface?

---

## 29. Current experimental source location

The active modified source is:

    ~/Projects/msm8916-openwrt-clean/openwrt/build_dir/target-aarch64_generic_musl/linux-msm89xx_msm8916/linux-6.12.94/drivers/firmware/qcom/qcom_scm.c

Backup:

    ~/Projects/msm8916-openwrt-clean/kernel-modified-backup/Modified/EDL-probe-test-final-source/qcom_scm-android-edload-call-test.c

The backup SHA256 is:

    5fc7a9dc4b57cb75187324b76edb278677ce41ad5393744c707727f692abfc0d

---

## 30. Important methodology: source tree is NOT being permanently modified

The working strategy is:

    Android / reference kernel research
            ↓
    temporarily modify unpacked OpenWrt kernel source
            ↓
    compile test kernel
            ↓
    boot UFI001B
            ↓
    observe logs / behaviour
            ↓
    save exact modified source in kernel-modified-backup/
            ↓
    identify successful changes
            ↓
    only after success:
    create clean BSP patches

We are therefore **not treating the build directory as the permanent source of truth**.

The BSP patch set will be created only after the correct mechanism has been experimentally established.

---

## 31. Current backup structure

Current backup tree:

    kernel-modified-backup/
    ├── Final-EDL-Test
    │   ├── Image-edl-test
    │   └── vmlinux-edl-test
    │
    ├── Modified
    │   ├── EDL-probe-test-base
    │   │   ├── msm-poweroff-EDL-probe-priority-130-final.c
    │   │   ├── msm-poweroff-after-priority-130.c
    │   │   ├── msm-poweroff-after-probe-instrumentation.c
    │   │   ├── msm-poweroff-before-priority-130.c
    │   │   ├── msm-poweroff-before-probe-instrumentation.c
    │   │   ├── psci-EDL-probe-final.c
    │   │   ├── psci-after-reset-instrumentation.c
    │   │   ├── psci-before-reset-instrumentation.c
    │   │   ├── qcom_scm-EDL-probe-final.c
    │   │   ├── qcom_scm-after-dload-path-instrumentation.c
    │   │   ├── qcom_scm-after-edl-entry-instrumentation.c
    │   │   ├── qcom_scm-after-rmw-instrumentation.c
    │   │   ├── qcom_scm-before-dload-path-instrumentation.c
    │   │   ├── qcom_scm-before-edload-entry-instrumentation.c
    │   │   ├── qcom_scm-before-probe-instrumentation.c
    │   │   ├── qcom_scm-before-probe-instrumentation.h
    │   │   ├── qcom_scm-before-rmw-instrumentation.c
    │   │   ├── qcom_scm-public-before-probe-instrumentation.h
    │   │   ├── reboot-EDL-probe-final.c
    │   │   ├── reboot-after-notifier-chain-instrumentation.c
    │   │   ├── reboot-before-notifier-chain-instrumentation.c
    │   │   └── reboot-before-probe-instrumentation.c
    │   │
    │   ├── EDL-probe-test-final-source
    │   │   ├── msm-poweroff.c
    │   │   ├── psci.c
    │   │   ├── qcom_scm-public.h
    │   │   ├── qcom_scm.c
    │   │   ├── qcom_scm.h
    │   │   └── reboot.c
    │   │
    │   ├── msm-poweroff-current-scm-download-test.c
    │   ├── msm-poweroff-current-scm-download.c
    │   ├── msm-poweroff-edl-scm-test-v2.c
    │   ├── msm-poweroff.c
    │   ├── qcom_scm-current-edl-test.c
    │   ├── qcom_scm-public.h-current-edl-test
    │   ├── qcom_scm.c
    │   ├── qcom_scm.h
    │   ├── qcom_scm.h-current-edl-test
    │   ├── reboot-current-edl-test.c
    │   └── reboot-edl-test-v2.c
    │
    └── Original
        ├── msm-poweroff-current.c
        ├── msm-poweroff.c
        ├── process.c
        ├── qcom_scm-current-before-edl.c
        ├── qcom_scm.c
        ├── qcom_scm.h
        ├── qcom_scm.h-current-before-edl
        ├── reboot-current.c
        └── reboot.c

The backup system is intentional because multiple experiments are being performed and we need to preserve the exact state of each test.

---

## 32. Latest source backup verification

The currently modified files were searched using:

    grep -Rnl "MSM8916 EDL TEST" \
        "$KDIR/drivers/firmware/qcom" \
        "$KDIR/drivers/power/reset" \
        "$KDIR/drivers/firmware/psci" \
        "$KDIR/kernel/reboot.c" \
        "$KDIR/include/linux/firmware/qcom" \
        2>/dev/null

The active instrumentation was found in:

    drivers/firmware/qcom/qcom_scm-smc.c
    drivers/firmware/qcom/qcom_scm.c
    drivers/power/reset/msm-poweroff.c
    drivers/firmware/psci/psci.c
    kernel/reboot.c

This confirms that the EDL investigation currently covers:

    reboot framework
    ↓
    PSCI
    ↓
    MSM restart driver
    ↓
    Qualcomm SCM
    ↓
    SMC layer

---

## 33. Original reference-kernel SCM search

The reference Android kernel was searched for:

    SCM_DLOAD_CMD
    SCM_EDLOAD_MODE
    SCM_DLOAD_MODE

Results:

    drivers/power/reset/msm-poweroff.c:40:
        #define SCM_DLOAD_MODE 0X10

    drivers/power/reset/msm-poweroff.c:41:
        #define SCM_EDLOAD_MODE 0X01

    drivers/power/reset/msm-poweroff.c:42:
        #define SCM_DLOAD_CMD 0x10

    drivers/power/reset/msm-poweroff.c:89:
        SCM_DLOAD_CMD,
        on ? SCM_DLOAD_MODE : 0, 0

    drivers/power/reset/msm-poweroff.c:124:
        SCM_DLOAD_CMD,
        SCM_EDLOAD_MODE, 0

    drivers/power/reset/msm-poweroff.c:291:
        scm_is_call_available(SCM_SVC_BOOT, SCM_DLOAD_CMD)

No other source-tree references to `SCM_EDLOAD_MODE` were found.

This strongly suggests that the reference implementation's Emergency DLOAD SCM call is concentrated in `msm-poweroff.c`.

---

## 34. Original reference-kernel `enable_emergency_dload_mode()` usage

Search:

    grep -Rni --exclude-dir=.git \
        'enable_emergency_dload_mode' \
        .

Results:

    ./drivers/power/reset/msm-poweroff.c:102:
        static void enable_emergency_dload_mode(void)

    ./drivers/power/reset/msm-poweroff.c:153:
        static void enable_emergency_dload_mode(void)

    ./drivers/power/reset/msm-poweroff.c:224:
        enable_emergency_dload_mode();

The two definitions are because one is under:

    #ifdef CONFIG_MSM_DLOAD_MODE

and the other is the fallback implementation when DLOAD support is disabled.

The actual `reboot edl` path reaches the implementation at line approximately 102 when:

    CONFIG_MSM_DLOAD_MODE

is enabled.

---

## 35. Original reference-kernel restart-mode search

Search:

    grep -Rni --exclude-dir=.git \
        'RESTART_DLOAD\|restart_mode\|RESTART_NORMAL' \
        drivers/power/reset/msm-poweroff.c \
        drivers/soc \
        include

Important results:

    drivers/power/reset/msm-poweroff.c:45:
        static int restart_mode;

    drivers/power/reset/msm-poweroff.c:164:
        void msm_set_restart_mode(int mode)

    drivers/power/reset/msm-poweroff.c:166:
        restart_mode = mode;

    drivers/power/reset/msm-poweroff.c:195:
        Write download mode flags if restart_mode says so

    drivers/power/reset/msm-poweroff.c:200:
        in_panic || restart_mode == RESTART_DLOAD

    include/soc/qcom/restart.h:

        #define RESTART_NORMAL 0x0
        #define RESTART_DLOAD  0x1

This confirms that DLOAD is part of the restart state machine.

---

## 36. Original reference-kernel DTS support

The reference kernel contains device-tree nodes using:

    qcom,msm-imem-download_mode

and:

    qcom,msm-imem-emergency_download_mode

Search results include:

    arch/arm/boot/dts/qcom/apq8084.dtsi
    arch/arm/boot/dts/qcom/fsm9900.dtsi
    arch/arm/boot/dts/qcom/mdm9630.dtsi
    arch/arm/boot/dts/qcom/mpq8092.dtsi
    arch/arm/boot/dts/qcom/msm8226.dtsi
    arch/arm/boot/dts/qcom/msm8610.dtsi
    arch/arm/boot/dts/qcom/msm8974-v1.dtsi
    arch/arm/boot/dts/qcom/msm8974-v2.dtsi
    arch/arm/boot/dts/qcom/msm8974pro.dtsi
    arch/arm/boot/dts/qcom/msmsamarium.dtsi

The same compatible strings also occur under:

    arch/arm64/boot/dts/qcom/

This establishes that Qualcomm Android kernels historically represented both:

    qcom,msm-imem-download_mode

and:

    qcom,msm-imem-emergency_download_mode

as separate device-tree mechanisms.

---

## 37. Important implication of the DTS discovery

The existence of two separate compatible strings is significant:

    qcom,msm-imem-download_mode
    qcom,msm-imem-emergency_download_mode

This reinforces the distinction between:

    normal DLOAD

and:

    Emergency DLOAD / EDLOAD

Therefore we should not assume that the modern OpenWrt:

    dload_mode_addr

is automatically equivalent to the Android:

    emergency_dload_mode_addr

They may represent two different IMEM regions.

This must be investigated using the complete UFI001B Android DTS.

---

## 38. UFI001B Android DTS

We have the full Android DTS for UFI001B.

This is especially valuable because the UFI001B kernel source itself is unavailable.

The DTS should be used to determine:

- IMEM base address.
- IMEM download-mode node.
- IMEM emergency-download-mode node.
- restart reason address.
- PS_HOLD address.
- PMIC PON configuration.
- watchdog configuration.
- reset configuration.
- SCM-related device-tree properties.
- memory regions relevant to reboot state.

The reference ZTE kernel tells us what to search for.

The UFI001B DTS tells us what the actual device declares.

---

## 39. Cookie experiment

Three registers were manually investigated:

    0x08600fe0
    0x08600fe4
    0x08600fe8

Original values:

    0x08600fe0 = 0xB6B11421
    0x08600fe4 = 0x08802522
    0x08600fe8 = 0xFDFC3C5E

They were manually changed to:

    0x08600fe0 = 0x322A4F99
    0x08600fe4 = 0xC67E4350
    0x08600fe8 = 0x77777777

Readback confirmed that the writes succeeded.

After:

    sync
    reboot-edl

the device rebooted/disconnected but did **not** enter EDL.

---

## 40. Cookie experiment interpretation

The values:

    0x322A4F99
    0xC67E4350
    0x77777777

are exactly the Android reference kernel's:

    EMERGENCY_DLOAD_MAGIC1
    EMERGENCY_DLOAD_MAGIC2
    EMERGENCY_DLOAD_MAGIC3

This is a major correlation.

However, writing these values manually did not produce EDL.

Therefore:

    Emergency DLOAD magic values alone
    ≠
    complete EDL trigger

Possible reasons include:

- The wrong physical IMEM address was used.
- The address is correct but additional state is required.
- The PMIC watchdog must be disabled/configured.
- The SCM EDLOAD command must also be executed.
- A specific reset type is required.
- The boot ROM expects a particular sequence.
- The values may need to be written immediately before reset.
- Cache/memory barriers may matter.
- Another Android-specific restart operation may be required.

The cookie experiment therefore remains useful but is not sufficient.

---

## 41. PMIC watchdog discovery

The Android reference Emergency DLOAD function contains:

    qpnp_pon_wd_config(0);

The surrounding comment says:

    Need disable the pmic wdt, then the emergency dload mode
    will not auto reset.

This is one of the most important remaining areas of investigation.

The next objective is to locate the complete implementation of:

    qpnp_pon_wd_config()

and determine:

- Which PMIC register is modified.
- What value is written.
- What `0` means.
- Whether the operation is required specifically for EDLOAD.
- Whether UFI001B has the same PMIC.
- Whether UFI001B DTS exposes the relevant PMIC configuration.
- Whether OpenWrt already has an equivalent PMIC operation.

---

## 42. Reference-kernel SCM path versus modern OpenWrt SCM path

The reference Android kernel uses:

    scm_call_atomic2()

which directly constructs an older Qualcomm SCM atomic SMC call.

The modern OpenWrt kernel uses:

    qcom_scm_call_atomic()

which eventually reaches:

    scm_smc_call()

and:

    __scm_smc_call()

or the legacy path depending on the detected convention.

Therefore the correct translation is not simply:

    old r0/r1/r2/r3
    =
    new X0/X1/X2/X3

Instead we must compare the **logical SCM descriptor**:

    service
    command
    argument count
    argument values
    owner
    calling convention

and then inspect the resulting ARM SMCCC registers.

---

## 43. Modern OpenWrt SMC construction

The important modern code is:

    smc.args[0] = ARM_SMCCC_CALL_VAL(
            smccc_call_type,
            qcom_smccc_convention,
            desc->owner,
            SCM_SMC_FNID(desc->svc, desc->cmd));

    smc.args[1] = desc->arginfo;

    for (i = 0; i < SCM_SMC_N_REG_ARGS; i++)
            smc.args[i + SCM_SMC_FIRST_REG_IDX] = desc->args[i];

For the Android EDLOAD logical descriptor:

    svc     = 0x01
    cmd     = 0x10
    arginfo = QCOM_SCM_ARGS(2)
    args[0] = 0x01
    args[1] = 0x00
    owner   = ARM_SMCCC_OWNER_SIP

the resulting SMC registers should be captured by the existing instrumentation.

This will allow us to determine whether the modern SCM layer is issuing the expected secure-world call.

---

## 44. Why the current SCM experiment is important

There are now three separate questions.

### Question 1

Does the OpenWrt restart path reach:

    qcom_scm_set_edload_mode()?

### Question 2

If it reaches the SCM path, does it issue:

    svc = 0x01
    cmd = 0x10
    arg0 = 0x01
    arg1 = 0x00

?

### Question 3

If the correct SCM call is issued, does the MSM8916 secure firmware actually enter Emergency DLOAD?

These questions must be answered separately.

We should not jump directly to changing reset code until the SCM experiment is understood.

---

## 45. Current experiment state

The current experimental modification changes only the SCM EDLOAD call.

It does NOT yet attempt to reproduce the entire Android Emergency DLOAD sequence.

Current logical sequence:

    qcom_scm_set_edload_mode()
            ↓
    if dload_mode_addr exists
            ↓
    MMIO path
            ↓
    otherwise SCM availability check
            ↓
    Android-compatible EDLOAD SCM descriptor
            ↓
    qcom_scm_call_atomic()

The current experiment therefore tests the SCM portion independently.

---

## 46. Important limitation of the current experiment

The Android reference implementation does:

    if (emergency_dload_mode_addr) {

            write MAGIC1
            write MAGIC2
            write MAGIC3

            qpnp_pon_wd_config(0)

            mb();
    }

    if (scm_dload_supported) {

            scm_call_atomic2(
                    SCM_SVC_BOOT,
                    SCM_DLOAD_CMD,
                    SCM_EDLOAD_MODE,
                    0
            );
    }

The current OpenWrt experiment does **not yet reproduce all of this**.

It currently focuses on the SCM EDLOAD call.

Therefore even if the SCM call succeeds, that does not automatically prove that the complete Android EDL sequence has been reproduced.

The remaining components must still be investigated.

---

## 47. Current research model

The strongest current model is:

    reboot-edl
        ↓
    Linux reboot framework
        ↓
    MSM restart driver
        ↓
    EDL preparation
        ├── Emergency DLOAD IMEM magic values
        ├── PMIC watchdog configuration
        └── SCM EDLOAD command
        ↓
    cache / memory barrier operations
        ↓
    PMIC / PS_HOLD reset sequence
        ↓
    Qualcomm Boot ROM
        ↓
    Emergency DLOAD
        ↓
    Sahara
        ↓
    Firehose

This is a working model.

It is **not yet proven end-to-end on UFI001B**.

---

## 48. Known-good hardware path versus software path

### Hardware path

    Physical EDL button
            ↓
    Qualcomm Boot ROM
            ↓
    Sahara
            ↓
    Firehose

Status:

    CONFIRMED

### Software path

    reboot-edl
            ↓
    Linux restart framework
            ↓
    MSM restart preparation
            ↓
    Emergency DLOAD state
            ↓
    reset
            ↓
    Qualcomm Boot ROM
            ↓
    Sahara

Status:

    NOT YET WORKING

---

## 49. Current confirmed findings

The following are confirmed from the reference source:

    SCM_DLOAD_CMD  = 0x10
    SCM_DLOAD_MODE = 0x10
    SCM_EDLOAD_MODE = 0x01

The Android Emergency DLOAD call is:

    scm_call_atomic2(
        SCM_SVC_BOOT,
        SCM_DLOAD_CMD,
        SCM_EDLOAD_MODE,
        0
    );

The Android Emergency DLOAD magic values are:

    0x322A4F99
    0xC67E4350
    0x77777777

The Android Emergency DLOAD path calls:

    qpnp_pon_wd_config(0);

The Android restart command checks:

    "edl"

and calls:

    enable_emergency_dload_mode();

The Android restart code later lowers:

    PS_HOLD

using:

    __raw_writel(0, msm_ps_hold);

---

## 50. Current UFI001B confirmed findings

Confirmed on UFI001B:

    Hardware EDL works.

Confirmed:

    Qualcomm Sahara works.

Confirmed:

    Firehose works.

Confirmed:

    eMMC GPT can be read through EDL.

Confirmed:

    OpenWrt restart driver probes successfully.

Confirmed:

    reboot-edl userspace command reaches the Linux reboot interface.

Confirmed:

    OpenWrt SCM RMW instrumentation is active.

Confirmed:

    OpenWrt boot-time DLOAD-related MMIO address:

        0x193d100

Confirmed from manual experimentation:

    0x322A4F99
    0xC67E4350
    0x77777777

can be written/read back at the investigated cookie location.

Not confirmed:

    The investigated cookie location is the actual UFI001B Emergency DLOAD IMEM address.

Not confirmed:

    The cookie values alone trigger EDL.

Not confirmed:

    The Android SCM EDLOAD command alone triggers EDL.

Not confirmed:

    The Android PMIC watchdog sequence is identical on UFI001B.

Not confirmed:

    The complete Android sequence has yet been reproduced in OpenWrt.

---

## 51. Important distinction: normal DLOAD versus Emergency DLOAD

This distinction must remain explicit throughout the investigation.

### Normal DLOAD

Reference code:

    set_dload_mode()

Uses:

    SCM_DLOAD_CMD
    SCM_DLOAD_MODE

Numerically:

    command = 0x10
    arg0    = 0x10

### Emergency DLOAD

Reference code:

    enable_emergency_dload_mode()

Uses:

    SCM_DLOAD_CMD
    SCM_EDLOAD_MODE

Numerically:

    command = 0x10
    arg0    = 0x01

Emergency DLOAD also uses:

    EMERGENCY_DLOAD_MAGIC1
    EMERGENCY_DLOAD_MAGIC2
    EMERGENCY_DLOAD_MAGIC3

and:

    qpnp_pon_wd_config(0)

Therefore normal DLOAD and Emergency DLOAD should not be treated as interchangeable.

---

## 52. Important distinction: `dload_mode_addr` versus `emergency_dload_mode_addr`

The Android reference kernel has:

    dload_mode_addr

and:

    emergency_dload_mode_addr

as separate variables.

The Android DTS also has separate compatible strings:

    qcom,msm-imem-download_mode

and:

    qcom,msm-imem-emergency_download_mode

This strongly suggests that the two mechanisms may use different IMEM locations.

The modern OpenWrt SCM code currently has:

    __scm->dload_mode_addr

This must not automatically be assumed to represent:

    emergency_dload_mode_addr

The complete UFI001B Android DTS should therefore be searched before selecting an address.

---

## 53. Current backup policy

Before every meaningful source modification:

1. Copy the current source into:

       kernel-modified-backup/

2. Give the backup a descriptive name.

3. Calculate SHA256.

4. Record the experiment in this research document.

5. Compile and test.

6. Preserve successful and unsuccessful experiments separately.

The objective is to maintain a reproducible history of every EDL experiment.

---

## 54. Current backup SHA256 values

The current final-source backup set includes:

    msm-poweroff.c
    SHA256:
    295dd61b5c61db0af088de86e72d9cba1cc3867123364d333cfd08b9b293bbf5

    psci.c
    SHA256:
    7de627b8ae8a20361f605738704eec47f3e052d25e9b5d59d742e192afd50da0

    qcom_scm-smc.c
    SHA256:
    e03f0517def4db43b7b2cf28547162486b38013d58710aaf58e68f185ead3cbd

    qcom_scm.c
    SHA256:
    6047d7c56802ed7cc48b8a8af0fd87ccfae3d004869970a361cff65e3de4a1f6

    reboot.c
    SHA256:
    02ab8d0e84f44678ec741fae4754f569192453f613a0279e00216c82fcbc9723

The Android EDLOAD call experiment was separately backed up as:

    qcom_scm-android-edload-call-test.c

SHA256:

    5fc7a9dc4b57cb75187324b76edb278677ce41ad5393744c707727f692abfc0d

---

## 55. What we should NOT do yet

Do not yet:

- Create the final BSP patch.
- Permanently modify the OpenWrt source tree.
- Assume the Android ZTE kernel is identical to UFI001B.
- Assume the investigated cookie address is correct.
- Assume the SCM call alone is sufficient.
- Replace the complete modern SCM implementation with the old Android SCM implementation.
- Blindly copy old 32-bit SMC register handling into the ARM64 OpenWrt kernel.
- Change PS_HOLD/reset behaviour without first understanding the Android sequence.
- Disable safety/recovery mechanisms without a reason supported by the reference sequence.

The current phase is still **research and controlled experimentation**.

---

## 56. Next research step #1 — locate `qpnp_pon_wd_config()`

In the ZTE reference kernel:

    cd ~/Projects/msm8916-openwrt-clean/GitIgnore/android_kernel_zte_msm8916

Run:

    grep -Rni \
        'qpnp_pon_wd_config' \
        . \
        --exclude-dir=.git

Then:

    grep -RniE \
        'pon_wd|wd_config|PON_WD|WATCHDOG' \
        drivers include arch/arm/mach-msm \
        2>/dev/null

The complete implementation needs to be inspected.

---

## 57. Next research step #2 — trace the PMIC watchdog implementation

Determine:

- Function implementation.
- PMIC register.
- Register address.
- Bit mask.
- Value written when argument is `0`.
- Whether the operation is synchronous.
- Whether any delay is required.
- Whether the operation depends on PMIC revision.
- Whether UFI001B uses the same PMIC.
- Whether the UFI001B DTS identifies the same PON block.

This will tell us whether the Android call can be reproduced safely in OpenWrt.

---

## 58. Next research step #3 — inspect UFI001B DTS

Search the complete UFI001B Android DTS for:

    qcom,msm-imem-download_mode

    qcom,msm-imem-emergency_download_mode

    emergency_download

    download_mode

    restart_reason

    ps_hold

    pon

    watchdog

    reboot

    reset

The goal is to identify the actual UFI001B device-specific addresses.

The ZTE kernel tells us what the properties mean.

The UFI001B DTS tells us what addresses/configuration the actual device uses.

---

## 59. Next research step #4 — determine the actual UFI001B Emergency DLOAD address

The Android reference code expects:

    emergency_dload_mode_addr

The exact address should be derived from:

    UFI001B Android DTS

rather than guessed from:

    ZTE kernel
    generic MSM8916 documentation
    manually discovered registers

Once identified, the address can be inspected from OpenWrt.

The first test should be read-only.

Only after confirming the region should controlled writes be attempted.

---

## 60. Next research step #5 — test the Android SCM EDLOAD call

Compile the current experiment only after the source backup has been verified.

The purpose of the experiment is:

    reboot-edl
        ↓
    qcom_scm_set_edload_mode()
        ↓
    Android-compatible descriptor
        ↓
    qcom_scm_call_atomic()
        ↓
    qcom_scm-smc.c
        ↓
    SMC instrumentation

The expected instrumentation should reveal:

    MSM8916 EDL TEST: ANDROID EDLOAD SCM CALL

followed by:

    svc=0x1
    cmd=0x10
    arg0=0x1
    arg1=0x0

and then the final ARM SMCCC register values.

---

## 61. Next research step #6 — determine SMC convention

The modern SCM code can select:

    SMC_CONVENTION_ARM_32
    SMC_CONVENTION_ARM_64
    SMC_CONVENTION_LEGACY

The current kernel should be instrumented if necessary to report:

    __get_convention()

and the selected convention.

This matters because the Android reference kernel uses an older SCM interface while the OpenWrt kernel uses modern ARM SMCCC infrastructure.

We need to know exactly which convention UFI001B is using.

---

## 62. Next research step #7 — compare logical SCM values

For every EDLOAD test, record:

    svc
    cmd
    arginfo
    arg0
    arg1
    owner
    atomic
    SMC convention
    resulting X0
    resulting X1
    resulting X2
    resulting X3

The key expected logical values from Android are:

    svc     = 0x01
    cmd     = 0x10
    arginfo = QCOM_SCM_ARGS(2)
    arg0    = 0x01
    arg1    = 0x00
    owner   = ARM_SMCCC_OWNER_SIP
    atomic  = true

---

## 63. Next research step #8 — determine whether SCM returns success

The SCM call return value must be recorded.

Important possibilities:

### Return 0

The secure-world SCM call accepted the request.

This does not yet prove EDL will occur.

### Negative return

The command failed or is unsupported.

This would indicate that the SCM interface/path needs further investigation.

### Device enters EDL

This would strongly confirm that the Android EDLOAD SCM command is usable on UFI001B.

### Device resets normally

This would show that the SCM call alone is insufficient or that another state must be prepared first.

---

## 64. Next research step #9 — compare reset ordering

Once the SCM path is understood, reconstruct:

    enable_emergency_dload_mode()
        ↓
    return
        ↓
    msm_restart_prepare()
        ↓
    watchdog/debug operation
        ↓
    PMIC arbiter handling
        ↓
    PS_HOLD
        ↓
    reset

The exact Android source order must be preserved.

Do not assume the operations are interchangeable.

---

## 65. Next research step #10 — investigate `dload.bin`

After the kernel path is understood, investigate:

    bin/dload.bin

Useful future work:

- Identify architecture.
- Identify vector table.
- Disassemble ARM/Thumb code.
- Determine reset/boot role.
- Search for magic values.
- Search for SCM identifiers.
- Search for PMIC-related constants.
- Search for strings.
- Compare with known Qualcomm DLOAD components.

This should be treated as a separate research track.

---

## 66. Current hypothesis

The strongest current hypothesis is:

    reboot-edl
        ↓
    MSM restart driver detects "edl"
        ↓
    enable_emergency_dload_mode()
        ↓
    write Emergency DLOAD magic values to IMEM
        ↓
    disable/configure PMIC watchdog
        ↓
    issue SCM_BOOT / DLOAD command
        ↓
    SCM command:
        svc  = 0x01
        cmd  = 0x10
        arg0 = 0x01
        arg1 = 0x00
        ↓
    memory barrier / cache handling
        ↓
    reset through PMIC / PS_HOLD
        ↓
    Qualcomm Boot ROM
        ↓
    Emergency DLOAD / Sahara

This is currently a **research hypothesis based on the ZTE reference kernel**.

It is not yet proven as the exact UFI001B sequence.

---

## 67. Most important finding so far

The investigation has moved beyond simply discovering:

    "Qualcomm has a DLOAD command."

We now have the exact Android reference Emergency DLOAD invocation:

    scm_call_atomic2(
        SCM_SVC_BOOT,
        SCM_DLOAD_CMD,
        SCM_EDLOAD_MODE,
        0
    );

with:

    SCM_SVC_BOOT   = 0x01
    SCM_DLOAD_CMD  = 0x10
    SCM_EDLOAD_MODE = 0x01

Therefore the logical SCM request is:

    Service: 0x01
    Command: 0x10
    Arg0:    0x01
    Arg1:    0x00

This is the exact target that the current modern OpenWrt SCM experiment is attempting to reproduce.

---

## 68. Most important remaining unknowns

The major unanswered questions are now:

1. What is the actual UFI001B `emergency_dload_mode_addr`?
2. Does UFI001B use the same Emergency DLOAD magic values?
3. Does UFI001B require `qpnp_pon_wd_config(0)`?
4. What exact PMIC register does `qpnp_pon_wd_config(0)` modify?
5. Does the modern OpenWrt SCM layer successfully issue the Android-equivalent SCM call?
6. Does the secure firmware accept `svc=0x01, cmd=0x10, arg0=0x01, arg1=0`?
7. What reset sequence is required after the EDLOAD preparation?
8. Does PS_HOLD need to be handled differently?
9. Is the Android EDLOAD mechanism dependent on a specific IMEM region that OpenWrt currently does not know about?
10. Is `dload.bin` involved in the final boot-ROM transition?

---

## 69. Current status table

| Component | Status |
|---|---|
| MSM8916 hardware EDL | **CONFIRMED WORKING** |
| Qualcomm Sahara | **CONFIRMED WORKING** |
| Firehose loader | **CONFIRMED WORKING** |
| eMMC access through EDL | **CONFIRMED WORKING** |
| ZTE Android reference kernel | **AVAILABLE** |
| UFI001B Android kernel source | **NOT AVAILABLE** |
| UFI001B complete Android DTS | **AVAILABLE** |
| Original Android firmware | **AVAILABLE** |
| `bin/dload.bin` | **FOUND; NOT FULLY REVERSED** |
| `RESTART_DLOAD` | **CONFIRMED IN REFERENCE KERNEL** |
| `SCM_DLOAD_CMD` | **0x10** |
| `SCM_DLOAD_MODE` | **0x10** |
| `SCM_EDLOAD_MODE` | **0x01** |
| Android EDLOAD SCM call | **CONFIRMED IN REFERENCE SOURCE** |
| Emergency DLOAD magic values | **CONFIRMED IN REFERENCE SOURCE** |
| PMIC watchdog call | **CONFIRMED IN REFERENCE SOURCE** |
| UFI001B cookie writes | **TESTED** |
| Cookie values alone trigger EDL | **NO** |
| OpenWrt restart driver probe | **CONFIRMED** |
| OpenWrt `reboot-edl` userspace request | **CONFIRMED** |
| OpenWrt SCM instrumentation | **IMPLEMENTED** |
| OpenWrt RMW instrumentation | **IMPLEMENTED** |
| Android-compatible SCM EDLOAD experiment | **IMPLEMENTED / TESTING** |
| Actual UFI001B Emergency DLOAD address | **NOT YET CONFIRMED** |
| `qpnp_pon_wd_config()` implementation | **NEXT TARGET** |
| Complete UFI001B software EDL sequence | **NOT YET RECONSTRUCTED** |
| Final BSP patch | **NOT YET CREATED** |

---

## 70. Final research direction

The investigation should now proceed in this order:

    1. Preserve current OpenWrt source backup.
    
    2. Inspect UFI001B Android DTS.
    
    3. Identify:
           emergency_dload_mode_addr
           dload_mode_addr
           restart_reason
           PS_HOLD
           PMIC/PON/watchdog information.
    
    4. Inspect the complete reference implementation of:
           qpnp_pon_wd_config()
    
    5. Compile and test the Android-compatible SCM EDLOAD experiment.
    
    6. Capture the complete SCM instrumentation.
    
    7. Determine whether the secure firmware accepts:
           svc  = 0x01
           cmd  = 0x10
           arg0 = 0x01
           arg1 = 0x00
    
    8. If SCM is accepted but EDL does not occur:
           reproduce the Android Emergency DLOAD IMEM writes.
    
    9. Reproduce PMIC watchdog handling.
    
    10. Reproduce the Android reset ordering.
    
    11. Test again.
    
    12. Only after a successful UFI001B software EDL transition:
           extract the minimal required changes.
    
    13. Create clean BSP patches.
    
    14. Remove temporary instrumentation from the production patch.

---

## 71. Working conclusion

The investigation has now established that the Android reference kernel contains a dedicated Emergency DLOAD path rather than simply using the normal DLOAD mode.

The critical Android sequence is:

    "edl"
        ↓
    enable_emergency_dload_mode()
        ↓
    Emergency DLOAD magic values
        ↓
    PMIC watchdog handling
        ↓
    SCM EDLOAD command
        ↓
    reset

The critical SCM operation is:

    SCM_SVC_BOOT
    SCM_DLOAD_CMD
    SCM_EDLOAD_MODE
    0

Numerically:

    0x01
    0x10
    0x01
    0x00

The modern OpenWrt kernel does not use the old Android `scm_call_atomic2()` implementation directly. Instead, it uses the modern Qualcomm SCM descriptor and ARM SMCCC infrastructure.

Therefore the current experiment is translating the **logical Android EDLOAD request** into the modern OpenWrt SCM architecture.

The hardware EDL path is already proven to work, so the remaining research is no longer about whether MSM8916 supports EDL.

The real question is:

    What exact state must the MSM8916 be placed into before reset
    so that the Qualcomm Boot ROM interprets the next boot as
    Emergency DLOAD?

The ZTE reference kernel gives us the strongest available blueprint:

    Emergency DLOAD IMEM state
            +
    PMIC watchdog state
            +
    SCM EDLOAD request
            +
    correct reset sequence

The UFI001B Android DTS and runtime experiments will now be used to determine which parts of this reference sequence are actually required by UFI001B.

**Next target: inspect `qpnp_pon_wd_config()` and the UFI001B Android DTS for `qcom,msm-imem-emergency_download_mode`.**

---

## 72. Research rule going forward

Every new experiment must answer one specific question.

Before modifying the OpenWrt kernel:

    1. Save the current source.
    2. Calculate SHA256.
    3. Record the experiment name.
    4. Make the smallest possible modification.
    5. Compile.
    6. Boot UFI001B.
    7. Capture dmesg.
    8. Record whether the device:
           - stayed running,
           - rebooted normally,
           - entered Sahara,
           - entered Firehose,
           - or failed to restart.
    9. Save the modified source.
    10. Only then proceed to the next experiment.

The final BSP patch must be generated only after the complete software EDL path has been demonstrated successfully.

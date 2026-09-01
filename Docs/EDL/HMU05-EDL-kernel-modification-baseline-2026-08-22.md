# HMU05 EDL Reboot Investigation — Kernel Modification Baseline

Date: 2026-08-22

## Objective

The goal is to make the **HMU05 4G Modem Stick** enter Qualcomm Emergency Download (EDL / 9008) mode when Linux is rebooted with:

```text
reboot-edl
```

The current test proves that the Linux kernel receives the `edl` reboot command and executes our Qualcomm download-mode software path successfully, but the device **does not actually enter EDL**.

Therefore, the next stage is to determine which part of the Qualcomm boot/restart path is still missing or behaves differently on HMU05.

---

## Current known-good software path

The HMU05 kernel was returned to a clean/pristine state and compiled successfully. The already-tested UFI001B modifications were then copied into their correct HMU05 kernel destinations **one change at a time**.

The current firmware successfully executes this sequence:

```text
reboot-edl
    |
    v
LINUX_REBOOT_CMD_RESTART2("edl")
    |
    v
kernel/reboot.c
    |
    v
msm-poweroff.c
    |
    v
qcom_scm_set_edload_mode()
    |
    v
DLOAD MMIO path
    |
    v
DLOAD register 0x193d100
    |
    v
write/readback 0x10
    |
    v
clear PS_HOLD
```

However, the device does **not** subsequently appear in Qualcomm EDL / 9008 mode.

---

# Modified kernel source files

## 1. Qualcomm SCM implementation

**Kernel destination:**

```text
$KDIR/drivers/firmware/qcom/qcom_scm.c
```

Modification:

- Added the dedicated `qcom_scm_set_edload_mode()` helper.
- The helper selects the Qualcomm download-mode path.
- On HMU05 it reaches the MMIO DLOAD register path.
- The helper performs the DLOAD register read-modify-write operation.
- The helper is exported for use by the restart driver.

Observed:

```text
MSM8916 EDL TEST: qcom_scm_set_edload_mode() ENTERED
MSM8916 EDL TEST: DLOAD PATH dload_mode_addr=0x000000000193d100
MSM8916 EDL TEST: DLOAD PATH using MMIO
MSM8916 EDL TEST: RMW ENTERED addr=0x000000000193d100 mask=0x00000030 val=0x00000010
MSM8916 EDL TEST: RMW READ old=0x00000000
MSM8916 EDL TEST: RMW CALCULATED new=0x00000010
MSM8916 EDL TEST: RMW WRITE returned 0
MSM8916 EDL TEST: RMW READBACK value=0x00000010
MSM8916 EDL TEST: DLOAD PATH result=0
MSM8916 EDL TEST: qcom_scm_set_edload_mode returned 0
```

The software therefore reports a successful DLOAD register operation.

---

## 2. Qualcomm SCM SMC layer

**Kernel destination:**

```text
$KDIR/drivers/firmware/qcom/qcom_scm-smc.c
```

Modification:

- Added instrumentation around the Qualcomm DLOAD SMC command.
- This was part of the UFI001B investigation to determine whether the DLOAD SMC path was reached and what arguments were being passed.

This file must remain tracked separately from `qcom_scm.c`.

---

## 3. MSM restart / poweroff driver

**Kernel destination:**

```text
$KDIR/drivers/power/reset/msm-poweroff.c
```

Modification:

- Detects restart command `edl`.
- Calls `qcom_scm_set_edload_mode()`.
- Waits briefly after the SCM operation.
- Clears PS_HOLD.
- Adds diagnostic logging.

Observed:

```text
MSM8916 EDL TEST: do_msm_poweroff mode=0 cmd=edl
MSM8916 EDL TEST: EDL command received
...
MSM8916 EDL TEST: qcom_scm_set_edload_mode returned 0
MSM8916 EDL TEST: clearing PS_HOLD
```

**Important:** The restart-handler priority is left at its normal value. We are **not** changing it from 128 to 130.

---

## 4. PSCI implementation

**Correct tested kernel destination:**

```text
$KDIR/drivers/firmware/psci/psci.c
```

This is important because the Linux source tree contains another `psci.c`:

```text
arch/arm64/kernel/psci.c
```

Earlier work accidentally caused duplicate definitions by putting the modified PSCI implementation into both locations. That produced linker errors such as:

```text
multiple definition of `psci_ops'
multiple definition of `psci_dt_init'
multiple definition of `psci_cpu_suspend_enter'
```

Therefore the tested modified PSCI source belongs at:

```text
drivers/firmware/psci/psci.c
```

and must **not** be copied to:

```text
arch/arm64/kernel/psci.c
```

---

## 5. Kernel reboot framework

**Kernel destination:**

```text
$KDIR/kernel/reboot.c
```

Modification:

- Added instrumentation around the kernel restart path.
- Confirmed that `LINUX_REBOOT_CMD_RESTART2` reaches the kernel.
- Confirmed that the command string `edl` is propagated into the restart path.

Observed:

```text
reboot: Restarting system with command 'edl'
reboot: MSM8916 EDL TEST: do_kernel_restart() CALLED cmd=edl
reboot: MSM8916 EDL TEST: BEFORE atomic_notifier_call_chain()
```

This confirms that userspace → kernel restart command propagation is working.

---

# Required public SCM declaration

**Kernel destination:**

```text
$KDIR/include/linux/firmware/qcom/qcom_scm.h
```

Declaration:

```c
int qcom_scm_set_edload_mode(void);
```

This declaration is required because `qcom_scm.c` provides the helper and `msm-poweroff.c` calls it.

---

# What has been confirmed on HMU05

The latest firmware demonstrated:

- `reboot-edl` executes.
- The kernel receives `cmd=edl`.
- `kernel/reboot.c` receives the restart request.
- `msm-poweroff.c` receives `cmd=edl`.
- `qcom_scm_set_edload_mode()` is called.
- DLOAD address is `0x193d100`.
- RMW writes `0x10`.
- Readback is `0x10`.
- The helper returns `0`.
- PS_HOLD is cleared.

The complete software path executes without a kernel error.

The test log shows the final sequence through PS_HOLD clearing. fileciteturn115file0L421-L443

---

# What has NOT been achieved

The device **does not enter Qualcomm EDL / 9008 mode**.

Therefore:

> A successful return from `qcom_scm_set_edload_mode()` and a successful MMIO readback of `0x10` do not yet prove that the Qualcomm boot ROM/boot chain will enter EDL.

The remaining investigation is to determine what is required after the DLOAD register is modified and PS_HOLD is cleared.

---

# Next investigation rule

The current firmware should be treated as the **known-good HMU05 software-path baseline**.

Future changes must be introduced **one at a time**.

For every experiment record:

1. Exact kernel source file changed.
2. Exact destination path.
3. Exact modification.
4. Build result.
5. `reboot-edl` result.
6. Fresh `/sys/fs/pstore/console-ramoops-0` after the resulting boot, if Linux returns.
7. Whether the host detects Qualcomm EDL / 9008.

This will allow us to identify exactly which change affects the HMU05 hardware behavior.

---

# Kernel source destination map

| Purpose | Correct kernel destination |
|---|---|
| Qualcomm SCM / EDL helper | `drivers/firmware/qcom/qcom_scm.c` |
| Qualcomm SCM SMC layer | `drivers/firmware/qcom/qcom_scm-smc.c` |
| Public SCM declaration | `include/linux/firmware/qcom/qcom_scm.h` |
| MSM restart/poweroff handler | `drivers/power/reset/msm-poweroff.c` |
| PSCI implementation | `drivers/firmware/psci/psci.c` |
| Kernel restart framework | `kernel/reboot.c` |

## Critical duplicate-source warning

Do not select a destination solely from the filename.

In particular, `psci.c` exists in multiple locations. The tested modified PSCI source belongs at:

```text
drivers/firmware/psci/psci.c
```

not:

```text
arch/arm64/kernel/psci.c
```

This destination map should be used for all future source-copy operations.

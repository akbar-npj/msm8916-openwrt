## Packaging Strategy

After reviewing the Linux Kconfig, the dependency graph is now clear.

`BT_QCA` is **not** MSM8916-specific. It is a generic Qualcomm Bluetooth helper module used by multiple Bluetooth transports.

The Linux Kconfig shows:

```text
BT_HCIUART_QCA
    select BT_QCA

BT_QCOMSMD
    select BT_QCA
```

This means `btqca.ko` is a shared support library and should not be bundled exclusively with the SMD transport.

### Proposed OpenWrt Package Layout

To mirror the Linux architecture and follow OpenWrt packaging practices, create two kernel packages:

```text
kmod-btqca
        |
        +-- btqca.ko
             ^
             |
      --------------------
      |                  |
kmod-btqcomsmd      kmod-hci-qca
```

### Package 1: `kmod-btqca`

Contains:

```text
btqca.ko
```

This package provides the common Qualcomm Bluetooth helper module that can be reused by multiple transport drivers.

### Package 2: `kmod-btqcomsmd`

Depends on:

```text
+kmod-btqca
+kmod-qcom-rproc-wcnss
+kmod-bluetooth
```

Contains:

```text
btqcomsmd.ko
```

This package provides the Qualcomm Shared Memory (SMD) Bluetooth HCI transport used by MSM8916 WCNSS devices.

## Why `bluetooth.mk` instead of `target/linux/msm89xx/modules.mk`?

Although `btqcomsmd` is primarily used on MSM8916 platforms, `btqca` is a generic Qualcomm Bluetooth helper shared by multiple transports.

Because of this, both packages belong in:

```text
package/kernel/linux/modules/bluetooth.mk
```

rather than:

```text
target/linux/msm89xx/modules.mk
```

The target-specific `modules.mk` should remain responsible for SoC infrastructure such as RemoteProc, WCNSS firmware loading, BAM-DMUX, and RPMSG drivers, while Bluetooth transport drivers remain grouped with the rest of the Bluetooth subsystem.

## Target Architecture

The resulting package layout should resemble:

```text
package/kernel/linux/modules/bluetooth.mk

├── kmod-bluetooth
├── kmod-hci-uart
├── kmod-btusb
├── kmod-btqca          <-- New
└── kmod-btqcomsmd      <-- New
```

## Next Steps

1. Add `kmod-btqca` to `package/kernel/linux/modules/bluetooth.mk`.
2. Add `kmod-btqcomsmd` with the appropriate dependencies.
3. Rebuild only the kernel packages.
4. Verify that `kmod-btqca` and `kmod-btqcomsmd` are generated as installable APKs.
5. Install the packages on the UFI001B and verify that the Bluetooth controller enumerates successfully.

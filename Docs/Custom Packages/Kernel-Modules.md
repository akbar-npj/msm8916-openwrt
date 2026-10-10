# In-Repo Kernel Modules

These `kmod-*` packages are defined in
[`msm89xx/modules.mk`](../../msm89xx/modules.mk) — they are the out-of-tree /
not-yet-upstream Qualcomm drivers the MSM8916 port needs. They are gated to
`@TARGET_msm89xx` and published to the signed APK feed like any other package.

| Package | Module(s) | Autoload | Purpose |
| :--- | :--- | :--- | :--- |
| `kmod-qcom-rproc` | `mdt_loader`, `qcom_common`, `qcom_pil_info` | ✅ | Qualcomm **remoteproc** support — loading firmware for the on-SoC remote processors. |
| `kmod-qcom-rproc-wcnss` | `qcom_wcnss_pil`, `wcnss_ctrl` | ✅ | **WCNSS** (Wi-Fi/BT Pronto core) remoteproc firmware loading and control. |
| `kmod-qcom-rproc-modem` | `qcom_q6v5`, `qcom_q6v5_mss` | ✅ | **Modem** (MPSS/Q6) remoteproc firmware loading and control. |
| `kmod-rpm-master-stats` | `rpm_master_stats` | ❌ manual | Per-subsystem **RPM sleep/wake counters** read from the RPM message RAM, exposed under `/sys/kernel/debug/qcom_rpm_master_stats/{APSS,MPSS,PRONTO}`. Ships no `MODULE_DEVICE_TABLE` (a debugging module), so load it with `modprobe rpm_master_stats`. Requires the `msm8916.dtsi` master-stats node (patch 824). |
| `kmod-rpmsg-wwan-ctrl` | `rpmsg_wwan_ctrl` | ✅ | **RPMSG WWAN Control** — exposes the modem control ports (AT, QMI) that use RPMSG. |
| `kmod-bam-dmux` | `qcom_bam_dmux` | ✅ | Qualcomm **BAM-DMUX** WWAN network driver — the `wwan0` data interface. This is the driver that carries the A2 pin and the many AP-side stability fixes (see [Docs/Modem Stability/](../Modem%20Stability/)). |
| `kmod-btqca` | `btqca` | ❌ dependency | Qualcomm **Bluetooth helper** routines (ROM patch / NVM download, BD address). Provides `qca_set_bdaddr_rome()`; no platform driver of its own, loaded as a dependency of `kmod-btqcomsmd`. |
| `kmod-btqcomsmd` | `btqcomsmd` | ✅ | **HCI over Qualcomm SMD** — bridges Bluetooth onto the WCNSS shared-memory channels. Binds to the `qcom,wcnss-bt` platform device created when the WCNSS remoteproc boots, so `wcnss.mdt` must be present. |

> **Autoload** uses OpenWrt's `AutoProbe`, which writes a `modules.d` alias so the
> listed modules load at boot. Modules marked ❌ are intentionally left
> manual/dependency-loaded.

## Related

- [**README.md**](README.md) — the userspace packages.
- [**Docs/Patches/README.md**](../Patches/README.md) — the kernel patches that
  add the DT nodes and driver changes these modules depend on.
- [**Docs/Bluetooth/**](../Bluetooth/) — Bluetooth bring-up (uses `btqca` /
  `btqcomsmd`).

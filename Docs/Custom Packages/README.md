# Custom Packages

Beyond the upstream OpenWrt tree, this project ships **18 packages** under
[`packages/`](../../packages/) — the tools, daemons and LuCI apps that make an
MSM8916 USB dongle usable as a headless router with a Qualcomm baseband.

They fall into two groups:

- **Authored in-repo (10)** — written for this project, specific to the MSM8916
  modem/DIAG/Bluetooth hardware.
- **Vendored (8)** — upstream or third-party packages carried in-tree (often with
  local patches) so the firmware build is self-contained.

> Kernel modules are **not** in `packages/`; they live in
> [`msm89xx/modules.mk`](../../msm89xx/modules.mk). See
> [**Kernel-Modules.md**](Kernel-Modules.md).

---

## Authored in-repo

| Package | What it does |
| :--- | :--- |
| [`ats-probe`](../../packages/ats-probe/) | Reads the modem's own **ATS_RTC uptime counter** over QMI TIME (service 22) and stamps each read with the AP's `CLOCK_BOOTTIME`, so a modem-side timestamp can be correlated with an AP-side one. Sends only the read-only `GENOFF_GET 0x0021` — it cannot write to the modem. Used to decide whether the ~902 s fatal is a counter wrap, an always-on clock, or a modem-local condition. |
| [`bt-spp`](../../packages/bt-spp/) | Bluetooth **Serial Port Profile** server and client. `bt-spp serve` bridges incoming SPP connections to a login shell (pty) or a TCP host:port; `bt-spp connect` bridges a remote SPP device to a local shell/TCP listener. Target selected in `/etc/config/bt-spp`. |
| [`bt-ssp-guard`](../../packages/bt-ssp-guard/) | Ensures the WCNSS Bluetooth controller **advertises Secure Simple Pairing**. The SSP LMP feature bit (NVM tag 6, byte 6, bit 3) is RAM-only, so this re-checks each boot and rewrites it when missing — without it, BR/EDR pairing falls back to legacy PIN pairing and fails. |
| [`diag-bind`](../../packages/diag-bind/) | Exposes the modem's SMD DIAG channels as character devices by binding `remoteproc0:smd-edge.DIAG` / `DIAG_CNTL` to the `rpmsg_chrdev` driver (`/dev/rpmsg0` data, `/dev/rpmsg1` control). Ships a one-shot helper **and** a procd service that re-binds whenever the modem subsystem restarts (SSR). |
| [`diag-efs`](../../packages/diag-efs/) | Reads/writes the modem **EFS** through the DIAG EFS2 subsystem (0x13) over `/dev/rpmsg0`. The modem decrypts its own EFS on the fly, exposing the live decrypted NV tree without the per-chip storage key. `list/read/stat/get/dump` (read-only) plus a guarded `put` that refuses calibration paths (`rfnv/`, `mcs/`) without `--force`. |
| [`diag-logtool`](../../packages/diag-logtool/) | Qualcomm **DIAG client** for the SMD DIAG channels (`/dev/rpmsg0`, `/dev/rpmsg1`). Round-trip/raw probes, decode/capture the DIAG stream, and control the log mask via `DIAG_LOG_CONFIG_F` (0x73). |
| [`msm-firmware-dumper`](../../packages/msm-firmware-dumper/) | Dumps the Qualcomm firmware partitions into `/lib/firmware` on first boot. |
| [`qcom-carrier-autocfg`](../../packages/qcom-carrier-autocfg/) | Automatic **SIM detection, carrier-profile matching, APN provisioning** and a dynamic connection manager for the MSM8916 Snapdragon modem. |
| [`qcom-time-daemon`](../../packages/qcom-time-daemon/) | **QMI Time synchronization** daemon: syncs host time with the modem's ATS/TOD and provides periodic keepalives to keep the modem DRX sleep state machine stable. |
| [`reboot-edl`](../../packages/reboot-edl/) | Safely quiesces background services, remounts filesystems read-only, and reboots the device into **EDL (9008), DLOAD (9006), bootloader or recovery**. |

## Vendored

| Package | What it does |
| :--- | :--- |
| [`cups`](../../packages/cups/) | **CUPS** print server (`cupsd`) with IPP, AirPrint, raw socket and web administration on port 631. |
| [`qrtr`](../../packages/qrtr/) | Qualcomm **IPC Router** userspace — `libqrtr` plus `qrtr-utils` (tools and nameservice daemon). |
| [`rmtfs`](../../packages/rmtfs/) | Qualcomm **Remote Filesystem service** daemon — serves the modem's remote-filesystem requests from the AP. |
| [`luci-app-cpu-perf`](../../packages/luci-app-cpu-perf/) | LuCI page for CPU performance information and management. |
| [`luci-app-sms-manager`](../../packages/luci-app-sms-manager/) | LuCI JS interface for **SMS / USSD / AT commands** via ModemManager. |
| [`luci-app-tailscale`](../../packages/luci-app-tailscale/) | LuCI interface for **Tailscale**. |
| [`luci-app-usb-gadget`](../../packages/luci-app-usb-gadget/) | Web interface for managing **USB gadget** functions (RNDIS, ECM, NCM, ACM, Mass Storage). |
| [`uci-usb-gadget`](../../packages/uci-usb-gadget/) | **UCI-based USB gadget manager** built on the kernel configfs interface. Configures the device as a USB peripheral and integrates the USB-Ethernet interface into the LAN bridge; auto-detects the UDC controller. |

---

## How they get into the image

The packages are selected per-board in each target's `diffconfig` / `modules.mk`
and built by [`build.sh`](../../build.sh). They are also published to the signed
APK feed (see the main [README → Official Package & Kernel Driver
Repository](../../README.md#-official-package--kernel-driver-repository)), so
they can be installed on-device with `apk add`.

## Related docs

- [**Kernel-Modules.md**](Kernel-Modules.md) — the in-repo `kmod-*` drivers.
- [**Docs/Patches/README.md**](../Patches/README.md) — every kernel patch,
  including the driver changes these packages rely on.
- [**Docs/Modem Stability/**](../Modem%20Stability/) — the modem/DIAG tooling in
  context.

# HMU05 Bluetooth — bring-up, pairing root cause, and packaging

Status: **Bluetooth works, including BR/EDR pairing.** Verified 2026-10-09 on the HMU05
(`hmu05,250605v0s`), OpenWrt 25.12.5 / kernel 6.12.94, bluez 5.83.

This supersedes the earlier conclusion recorded in memory that "pairing always fails; the WCNSS
DHKey crypto is broken". **That conclusion was wrong.** Pairing succeeds.

## 1. The stack

- BT core built into the kernel (`CONFIG_BT=y`).
- Transport **`btqcomsmd`** (OF `qcom,wcnss-bt`) + helper **`btqca`**.
- `openwrt/target/linux/msm89xx/config-6.12`: `CONFIG_BT_QCA=m`, `CONFIG_BT_QCOMSMD=m`.
- Platform device `a204000.remoteproc:smd-edge:wcnss:bluetooth`, created when `remoteproc1` boots
  `wcnss.mdt` (~9.5 s). rpmsg channels `…APPS_RIVA_BT_CMD/CTRL/DATA`.
- `hci0` — Bus SMD, BD Address `02:00:C2:B9:10:3D` (= Wi-Fi MAC `…:3c` + 1, injected by lk2nd via
  the `local-bd-address` property), Manufacturer Qualcomm (29), HCI 4.0, class `0x000100`.

## 2. Root cause of the pairing failure — the SSP LMP-feature bit

The controller advertises Secure Simple Pairing through the **LMP feature bit 51** = **byte 6, bit 3
(`0x08`)** of NVM tag 6. This bit gates SSP at the controller:

| NVM tag 6 byte 6 | SSP bit | `Read Simple Pairing Mode` (`0x03|0x0055`) |
|---|---|---|
| `0x5B` | set | `01 55 0C 00 01` — status `0x00`, **mode `0x01`** |
| `0x53` | cleared | `01 55 0C 11 FF` — **status `0x11` (Unsupported Feature)** |

With the bit cleared the controller reports SSP unsupported, so the device falls back to **legacy
PIN pairing, which also fails** (`PIN Code Request` → `Auth Complete status 0x06`, "PIN or Key
Missing"). With the bit set, SSP runs normally and pairing succeeds.

**`Write Simple Pairing Mode` (`0x03|0x0056`) works on this controller.**
- `mode = 0x01` → status `0x00` (success).
- `mode = 0x00` → status **`0x0C` (Command Disallowed)** — the controller *refuses to disable SSP*;
  readback stays `01`.

> Correction: an earlier note claimed `Write_Simple_Pairing_Mode` (0x0C56) returns "Unknown Command".
> That is **false** for this controller.

**Firmware defaults after a clean reboot** (verified): features `FF FE 8F FE D8 3F 5B 87` (SSP bit
set), SSP mode `01`. `bluetoothd` also writes `Write SSP=1` at its own init. ⇒ A stock boot is
already in the good state; **the fix is self-healing across a reboot and needs no init script.**

### What is NOT proven
The *original* failure — SSP active but `Simple Pairing Complete (0x36) status 0x05` — is **not
reproducible now**, and its trigger is **unknown**. The BD-address hypothesis (SSP's f1/f2/f3 bind
the BD_ADDR; bluez once reported a stale `00:90:4A:13:A0:72`) was checked against the fedora-side
capture and is **not supported** — the failing exchange used `02:00:C2:B9:10:3D` throughout. What is
proven is the feature-bit mechanism above and that a controller re-init (HCI reset / reboot)
restores a working state.

## 3. Verified pairing

A clean, fresh pair (both sides' bonds deleted) after a full device reboot:

```
[host] agent registered (NoInputNoOutput)
[host] pairing with 02:00:C2:B9:10:3D
[host] PAIR OK
```

Device side: `Paired: yes / Bonded: yes`, `LegacyPairing: no`. A full SDP session completes
(L2CAP / AVRCP / HFP / A2DP / OBEX records). This was reproduced with the OpenWrt-default
`NoInputNoOutput` agent (Just Works) and with `DisplayYesNo` (passkey confirmed).

### 3.1 The bond persists across a reboot (verified 2026-10-09)

Rebooted the device and re-checked. The bond **and its link key** survive:

| Check | Before reboot | After reboot |
|---|---|---|
| `bluetoothctl info C0:95:6D:45:9A:F3` | `Paired: yes / Bonded: yes` | `Paired: yes / Bonded: yes` |
| `…/C0:95:6D:45:9A:F3/info` md5 (holds `[LinkKey]`) | `f8975a1bdc3a5c4015d2961ed0b5c9b5` | **identical** |
| `…/02:00:C2:B9:10:3D/settings` md5 | `c166d0c91918551fb7efa4d32ae17c26` | **identical** |
| `hci0` | UP RUNNING, 0 errors | UP RUNNING, 0 errors |
| `btqca` / `btqcomsmd` loaded | yes | yes (auto-load) |

Root fs is `overlayfs` with `/overlay` on `/dev/mmcblk0p15` (ext4), so `/var/lib/bluetooth` is
on persistent storage. (A `sysupgrade -n` **would** wipe it — see the deployment trap.)

**The stored link key still authenticates** (this is the real test, not just the on-disk file):

- Device side: `hcitool cc C0:95:6D:45:9A:F3` → **`rc=0`**, and `hcitool con` shows
  `< ACL C0:95:6D:45:9A:F3 handle 2 state 1 lm PERIPHERAL` — a baseband ACL link established
  *using the stored key*. A missing/invalid key gives `Authentication Failed`, not a clean connect.
- Host side: `bluetoothctl connect 02:00:C2:B9:10:3D` → `ServicesResolved: yes` (SDP completed
  **over the authenticated link**) then `BREDR.ProfileUnavailable`.

> **Note on `br-connection-profile-unavailable`:** a plain `bluetoothctl connect` between two
> generic Linux hosts returns this. It is a **profile** error (neither side exposes a connectable
> service — no A2DP/HID/PAN listener), **not** an authentication error. Do not read it as "pairing
> broke". To prove the key works, use `hcitool cc` (link-level) or connect a real profile
> (e.g. an RFCOMM server, or a phone with A2DP/HFP).

## 4. Packaging — `kmod-btqca` + `kmod-btqcomsmd`

The modules are now proper kernel packages instead of hand-copied overlay files.

- `msm89xx/modules.mk` gained:
  - `KernelPackage/btqca` — `CONFIG_BT_QCA`, ships `btqca.ko`, not autoloaded (no platform driver).
  - `KernelPackage/btqcomsmd` — `CONFIG_BT_QCOMSMD`, ships `btqcomsmd.ko`,
    `AUTOLOAD = btqca btqcomsmd`, `DEPENDS = +kmod-bluetooth +kmod-btqca +kmod-qcom-rproc-wcnss`.
- `msm89xx/Makefile`: both added to `DEFAULT_PACKAGES`.
- `BLUETOOTH_MENU` is safe to use: `package/kernel/linux/Makefile:77` includes `modules/*.mk` before
  `:78` includes `$(SUBTARGET_MODULES)`.

Build (verified): produces `kmod-btqca-6.12.94-r1.apk` and `kmod-btqcomsmd-6.12.94-r1.apk`, both
listed in the `generic-hmu05` manifest, and the sysupgrade image rebuilt.

> **Trap (Doc 171):** a new `KernelPackage` in a target `modules.mk` is **silently dropped** by the
> stale `tmp/.config-package.in` cache — the `.ko` builds, the build succeeds, and no apk appears.
> Purge before building:
> ```
> rm -f openwrt/tmp/info/.packageinfo-* openwrt/tmp/info/.targetinfo-* openwrt/tmp/info/.files-* \
>       openwrt/tmp/.packageinfo openwrt/tmp/.config-package.in openwrt/tmp/.config-target.in \
>       openwrt/tmp/.packagedeps
> ```
> Then **verify the apk exists** rather than trusting the build's exit status.

Deployed to the device with `apk add --allow-untrusted`; after a reboot the package-provided
`/etc/modules.d/btqcomsmd` loads `btqca` + `btqcomsmd`, `hci0` comes up, and the fedora bond
persists. The redundant hand-made `/etc/modules.d/65-btqcomsmd` was removed.

## 5. Reusable traps

- The device's `bt-agent` is **interactive** — run it as `yes | bt-agent -c <cap>`.
- `bluetoothctl` auto-registers its own agent and makes it the default, overriding any other agent.
  Use a D-Bus agent that both registers and calls `Device1.Pair`.
- `bluetoothctl scan on` (LE+BR/EDR) repeatedly **misses** this BR/EDR-only device — use
  `bluetoothctl scan bredr` (or `hcitool scan`).
- **Any HCI reset clears inquiry-scan (ISCAN)** — re-run `hciconfig hci0 piscan` /
  `bluetoothctl discoverable on`. Not an RF problem.
- The device **cannot fetch HTTP from the host over the USB/br-lan link** (`EPERM`); use `scp`.

## 6. Evidence

`Docs/Bluetooth/evidence/`:
- `pairing_failure_dhkey_status05.log` — device-side HCI trace of the original failure
  (`User Confirmation Request` → `Simple Pairing Complete (0x36) status 0x05`).
- `phone_test_poco_m6_pro_5g.log` — the same failure against a POCO M6 Pro 5G.
- `pairing_failure_legacy_pin.log` — the legacy-PIN fallback (`PIN Code Request` → `Auth Complete
  0x06`) seen when the SSP feature bit is cleared.
- `pairing_success_ssp3.log` — a successful fedora-initiated pair (`DisplayYesNo`).
- `pairing_success_noio.log` — a successful pair with the OpenWrt-default `NoInputNoOutput` agent.

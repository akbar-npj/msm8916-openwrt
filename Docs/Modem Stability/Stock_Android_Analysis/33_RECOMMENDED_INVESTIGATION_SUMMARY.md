# Qualcomm MSM8916 Stock Android Ground-Truth Investigation Summary

**Target Device:** Qualcomm MSM8916 Snapdragon 410 (Generic HMU05 / UFI)  
**Baseband Firmware:** `HIMI_U01_MODEM_V1.0` (Model: 4094, Revision: Sep 09 2015)  
**Firmware OS:** Stock Android 4.4.4 (Kernel 3.10.28)  
**Capture Location:** `Docs/Modem Stability/Stock_Android_Analysis/`  

---

## 1. Watchdog Subsystem & Feeder Process

* **Hardware Watchdog Node:** `/sys/devices/soc.0/b017000.qcom,wdt` (Driver: `msm_watchdog`)
* **Watchdog Device Node in /dev:** There is **no `/dev/watchdog`** exposed to userspace.
* **Kernel Initialization:**
  ```text
  [    0.060760] msm_watchdog b017000.qcom,wdt: MSM Watchdog Initialized
  ```
* **Watchdog Feeder:** The MSM hardware watchdog is **managed entirely in-kernel** via timer workqueues (with `VosWDThread` for WCNSS Wi-Fi monitoring and `/sbin/healthd` monitoring Android framework binder transactions).
* **Watchdog Status:** `disable = 0` (Active). The kernel hardware watchdog timer has a default bark/bite window of 10s bark / 11s bite, fed continuously by the scheduler core.

---

## 2. Modem Memory Map (`mpss_mem` / CMA Regions)

From live kernel `dmesg`:

| Region Name | Base Address | Size | End Address | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`modem_adsp_mem` (`mpss_mem`)** | **`0x86800000`** | **85 MiB (`0x5500000`)** | **`0x8BD00000`** | **Hexagon QDSP6 Modem Firmware (MPSS)** |
| `external_image_mem` | `0x86000000` | 8 MiB (`0x800000`) | `0x86800000` | MBA (Modem Boot Authenticator) & TrustZone |
| `peripheral_mem` | `0x8BD00000` | 6 MiB (`0x600000`) | `0x8C300000` | WCNSS (Pronto Wireless Subsystem) |
| `venus_qseecom_mem` | `0x8F800000` | 8 MiB (`0x800000`) | `0x90000000` | Hardware Video Core & QSEE / TrustZone |

* **Remoteproc Loading Range**:
  ```text
  [    6.002955] pil-q6v5-mss 4080000.qcom,mss: modem: loading from 0x86800000 to 0x8ba00000
  [    6.048943] pil: MBA boot done
  [    6.677921] pil-q6v5-mss 4080000.qcom,mss: modem: Brought out of reset
  ```

---

## 3. Operational GPIO States When Modem is Active

From `/sys/kernel/debug/gpio` (Base 902 `msm_tlmm_v4_gpio`):

| Signal Name | Kernel GPIO ID | TLMM Physical GPIO | Direction | Operational State | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`esim1_en`** | `gpio-1021` | **GPIO 119** | `out` | **`HIGH` (255)** | **Primary SIM Card Power / Enable** |
| **`sim_hotplug`** | `gpio-1016` | **GPIO 114** | `out` | **`LOW` (0)** | **SIM Presence Detection Asserted** |
| **`4g_1`** | `gpio-973` | **GPIO 71** | `out` | **`HIGH` (1)** | **4G RF Power / LTE Status LED** |
| **`wifistatus`** | `gpio-975` | **GPIO 73** | `out` | **`HIGH` (1)** | **Wi-Fi Power / Status LED** |
| **`key_freset`** | `gpio-939` | **GPIO 37** | `in` | `LOW` (0) | Factory Reset Button |
| **`esim2_en`** | `gpio-916` | **GPIO 14** | `out` | `LOW` (0) | Secondary SIM Select (Disabled) |
| **`esim3_en`** | `gpio-914` | **GPIO 12** | `out` | `LOW` (0) | Tertiary SIM Select (Disabled) |
| **`disp_rst_n`** | `gpio-1019` | **GPIO 117** | `in` | `HIGH` (1) | Display Reset / Power |
| **`disp_dc`** | `gpio-1018` | **GPIO 116** | `in` | `HIGH` (1) | Display Data/Command Line |
| **`USB_ID_GPIO`**| `gpio-1012` | **GPIO 110** | `in` | `HIGH` (1) | USB OTG ID Detect |

---

## 4. AT Command & Interface Mode Analysis

* **AT Port**: `/dev/smd11` (Modem AT Channel)
* **Modem Identification**:
  ```text
  ATI
  Manufacturer: QUALCOMM INCORPORATED
  Model: 4094
  Revision: HIMI_U01_MODEM_V1.0  1  [Sep 09 2015 10:00:00]
  IMEI: 864293052253917
  +GCAP: +CGSM
  ```
* **Network Status**:
  ```text
  AT+CPIN? -> +CPIN: READY
  AT+COPS? -> +COPS: 0,0,"JIO 4G Jio",7 (LTE E-UTRAN Connected)
  AT+CSQ   -> +CSQ: 28,99 (~-57 dBm excellent signal)
  ```
* **USB/Data Mode Query (`AT+QCFG="usbnet"`)**:
  * Returns **`ERROR`** because `AT+QCFG` is a proprietary **Quectel** command syntax.
  * This is a **native Qualcomm Reference Design baseband (`Model: 4094`, Qualcomm Incorporated)**, which does not use USB-CDC emulation internally.
  * **Transport**: It uses **Native SoC BAM-DMUX DMA** (`/dev/bam_dmux8` / `rmnet0` via QMI WDS) for IP data transfer, rather than a USB CDC-ECM/NCM/QMI dongle interface.

# Stock Android Binary Reverse Engineering Map & Ghidra Analysis Targets

**Source OS:** Stock Android 4.4.4 (Kernel 3.10.28) on Qualcomm MSM8916  
**Artifact Path:** `Docs/Modem Stability/Stock_Android_Analysis/`  

---

## 1. Primary Binaries & Libraries Extracted

All files are stored in [`Docs/Modem Stability/Stock_Android_Analysis/binaries/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/binaries/) and [`vendor_libs/`](file:///home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem%20Stability/Stock_Android_Analysis/vendor_libs/).

### 1. `time_daemon` & `libtime_genoff.so`
* **Purpose**: QMI Service 22 (ATS / Time Service) host synchronization.
* **Key Functions in Ghidra**:
  * `genoff_boot_tod_init()`: Initial boot timebase synchronization between PMIC RTC (`/dev/rtc0`) and Modem ATS.
  * `tod_update_ind_cb()`: Handles asynchronous network NITZ / SIB16 broadcast indications from the modem.
  * `genoff_modem_qmi_init()`: Initializes QMI client over `AF_QIPCRTR` (Port 11, Service 22).
  * `time_genoff_operation()`: Low-level QMI message formatting (`QMI_TIME_GENOFF_SET_REQ` `0x0020`, `QMI_TIME_REG_IND_REQ` `0x0025`).

---

### 2. `libqmiservices.so` & `libqmi_cci.so`
* **Purpose**: Qualcomm QMI IDL type tables and Common Client Interface (CCI).
* **Key Symbol Tables in Ghidra**:
  * `time_service_get_service_object_v01()`: QMI Service 22 message structures and TLV layouts.
  * `wds_get_service_object_v01()`: Wireless Data Service (WDS) dormancy and bearer states.
  * `dms_get_service_object_v01()`: Device Management Service (DMS) power collapse and radio state.

---

### 3. `libril-qc-qmi-1.so` & `netmgrd`
* **Purpose**: RIL data call handling and network management.
* **Key Functions in Ghidra**:
  * `qmi_ril_data_call_list_changed()`: Dispatches `UNSOL_DATA_CALL_LIST_CHANGED` with `active=2` (`DORMANT`).
  * `netmgr_kif_cb()`: BAM-DMUX link state monitoring across channels `ch00`–`ch07`.

---

### 4. `rmt_storage` (`/system/bin/rmt_storage`)
* **Purpose**: Remote EFS2 storage server for Hexagon DSP.
* **Key Behaviors**:
  * Uses POSIX shared memory (`-P -s`) for direct DMA access to `/dev/block/bootdevice/by-name/` (`modemst1`, `modemst2`, `fsg`, `fsc`).
  * Handles the 900-second periodic radio calibration sync.

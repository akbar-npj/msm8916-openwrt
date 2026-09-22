# 176 — The FastDormancyService lead is DOWNGRADED: QMI WDS Go Dormant (0x0025) is `DeviceUnsupported` on this modem

**Date:** 2026-09-22
**Device:** HMUF02-v5 ("hmu05"), OpenWrt 6.12.94, stock HMU05 modem firmware; stock Android
dump in `GitIgnore/MelbonWhiteStock_Dump/`
**Answers:** Doc 170 PART 34 — "Android's `system_server` polls mobile data activity at 1 Hz
and exchanges messages with the modem; OpenWrt has no equivalent. Is that the differential?"
**Status:** **DOWNGRADED.** The whole chain was decompiled and string-traced to a single QMI
message, and that message is refused by this modem. Two supporting observations were also
misread and are corrected here.

---

## 1. SOP statement

| SOP step | Status |
| :-- | :-- |
| Stock-HMU05 comparative ground truth used | **Yes** — the Android side is the stock dump's own `fastdormancy.apk`, `qcrilhook.jar` and `libril-qc-qmi-1.so`; the modem side is the deployed HMU05 baseband |
| Measurement frozen before scoring | **Yes** — the logcat was re-read from the frozen `scratch/android_capture/` copy, not from a live writer |
| Comparability decided from KERNEL state | **n/a** — this is a static-analysis + single-command test, not a soak |
| Routing-path connectivity recorded | **n/a** |
| Physical/environmental context established first | **n/a for the decompile.** ⚠ The original logcat census was taken on a **MOVING BUS** (Doc 170 PART 35), which is why the "32 pairs in 29 min" figure must not be read as a stationary rate |
| Control asserted as present | **Yes** — the on-device negative control is the command itself: `--wds-get-dormancy-status` and `--wds-go-active` both succeed on the same device/port, so the failure is specific to 0x0025 and not to the QMI path |

---

## 2. The falsifier run (Doc 170 PART 36 → Doc 176)

**Method:** `strace` on `qmi-proxy` (PID 4638) for a 300 s idle window, plus ModemManager
debug logging.

**Result — NOT FALSIFIED.** OpenWrt is not silent:

* 102 `sendto` calls in 5 min, but the only **periodic** AP→modem send is
  **"Get Packet Statistics" (QMI WDS 0x0024) at 30 s intervals** — TX/RX byte counters,
  not data-activity monitoring.
* NAS messages in the window are **modem-initiated indications**, not AP polls.
* `qcom-time-daemon` is completely silent (0 operations in 30 s with `-r 0`).

So OpenWrt genuinely lacks the 1 Hz data-activity poll. That is what made the A/B look
warranted. The A/B is **not** warranted, for the reason in §4.

---

## 3. The chain, fully traced

| step | artefact | finding |
| :-- | :-- | :-- |
| 1 | `fastdormancy.apk` (stock dump, `system/app/`) | `com.qualcomm.fastdormancy.FastDormancyService` polls at 1 Hz, reschedules a 15000 ms alarm, and on expiry calls `mQcRilHook.qcRilGoDormant(interfaceName)` |
| 2 | `qcrilhook.jar` (stock dump, `system/framework/`) | `QcRilHook.qcRilGoDormant(String)` → `sendQcRilHookMsg(IQcRilHook.QCRILHOOK_GO_DORMANT, …)`; `QCRILHOOK_GO_DORMANT = QCRILHOOK_BASE + 3 = 0x80003`; wire header = `"QOEMHOOK"` (8 B ASCII) + request_id (LE u32) + payload_size (LE u32) + payload |
| 3 | `libril-qc-qmi-1.so` (stock dump, `system/vendor/lib/`) — `strings` | `qcril_data_process_qcrilhook_go_dormant` → **`qmi_wds_go_dormant_req`** |
| 4 | libqmi source, `qmi-wds.c` | `QMI_MESSAGE_WDS_GO_DORMANT = 0x0025` |
| 5 | **on device** | `qmicli -p -d /dev/wwan0qmi0 --wds-go-dormant` → **`QMI protocol error (25): 'DeviceUnsupported'`** |

Steps 1–4 are static; step 5 is the measurement. The RIL maps the OEM hook onto exactly the
QMI message `qmicli` sends, so **the FastDormancyService's dormancy command would fail even
if it fired.**

---

## 4. Two misreads corrected

**(a) `sent:N received:M` was not a modem exchange.** Doc 170 PART 34 read the logcat line as
a request/response pair with the modem. The decompiled source shows it is

```java
sent     = mDataUsage.tx - prev.tx;   // TrafficStats.getMobileTxPackets()
received = mDataUsage.rx - prev.rx;   // TrafficStats.getMobileRxPackets()
Log.d(TAG, "sent:" + sent + " received:" + received);
```

`TrafficStats.getMobileTxPackets()` reads the **local** `/proc/net/dev`. It sends nothing to
the modem. The "32 non-zero readings" are just seconds in which packets moved.

**(b) The service never triggered.** `isAlarmExpired: false` in **all 284** samples, and
`mFDset: 3` (`DATA_ACTIVE_MODE`) is constant — data was always active inside every 15 s
window, so the 15 s idle alarm never expired and `enterDormancy()` was never reached.

---

## 5. What this means

The FastDormancyService is **not** the Android/OpenWrt differential. At best it is a no-op
on this modem (the command it would send is refused); at worst it never ran during the
capture. The Doc 170 PART 34 lead is closed.

**Unexplored, recorded for completeness:** `strings` on `libril-qc-qmi-1.so` also shows
`qmi_ril_nwreg_enforce_data_dormancy_as_applicable_ncl` — a **network-registration-path**
dormancy enforcer, i.e. *not* the QMI WDS go-dormant message. It was not pursued and is not
claimed as a lead.

**Where the differential work stands after this:** the surviving AP-side candidates are the
ones that are *OpenWrt-AP-specific and HMU05-modem-specific* — the port's AP-side boot
firmware substitution (Doc 158/162-era, weakened to "not sufficient" by the UFI001B control)
and the bam_dmux path (pc-ack 2000 ms now deployed and **measured not to touch the fatal**).
The RPM/MCPM collapse-rate differential is **closed** (Doc 174 §3 / Doc 175): the modem
collapses at a comparable rate on both platforms. The period itself is now known to be
anchored to an always-on clock (Doc 177), which is where the next experiment points.

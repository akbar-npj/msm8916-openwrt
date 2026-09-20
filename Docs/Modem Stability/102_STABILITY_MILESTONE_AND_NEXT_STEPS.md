# Engineering Report 102: Stability Milestone & Jio LTE Search Analysis

## 1. Overview & Current Status

Following the deployment and verification of **Patch 29** and **Patch 30**:
- **System Stability**: 100% stable. Zero kernel crashes, zero QDSP6 exceptions, zero SSR events.
- **Modem Power & State**: Power is ON. ModemManager detects the modem cleanly:
  - Phone Number read from SIM: `918431225166` (Reliance Jio SIM)
  - Carrier Config: `ROW_Generic_3GPP`
  - Allowed Modes: `4G only`
  - Active LTE Bands: `E-UTRAN Band 3 (1800 MHz), E-UTRAN Band 5 (850 MHz)`
  - Active System Selection: `LTE only, automatic network selection`
- **Search Robustness**:
  - `qmicli --nas-force-network-search`: **SUCCESS** (clean execution, 0 crashes)
  - `mmcli --3gpp-register-home`: **SUCCESS** (initiates full search, times out gracefully with 0 crashes)
  - `qmicli --nas-get-cell-location-info`: Returns `QMI protocol error (13): NoNetworkFound` without crashing.

---

## 2. Technical Discoveries Across Patches 28–30

### 2.1 The `0x261d4` Card Init Failure
In stock HMU05 firmware, `0x261d4` initializes the WTR1605 RF front-end because the whole MPSS matches the hardware. In our port, `modem.b15` belongs to UFI001B. When `0x261d4` queries global structures initialized by `b15`, it returns `0`.
- **Resolution**: Patch 28/29/30 forces `r0 = 1` at `0x260b8` to ensure `card_init` takes the success branch to our hook at `0x260d4`.

### 2.2 Vtable Calling Conventions & Slot 7
Decompilation of Layer 1 RF driver function `FUN_c0d593f8` revealed:
- `vtable[4]` (`+0x10`): `get_device(dev, path)` returns PRX (path 0) and DRX (path 1) device pointers.
- `vtable[7]` (`+0x1c`): `get_tech_band_config(dev, tech, path, band)` is checked via `if (res1 == 0 || res2 == 0) fatal_error;`.
- **Resolution**: Slot 7 is assigned a clean duplex stub `RET1_STUB` (`{ r0 = #1 ; jumpr r31 }`, `0x48103fc0`), while unsupported methods safely return 0 via `RET0_STUB` (`0x48003fc0`).

### 2.3 Memory Mapping of `modem.b26`
- Segment 26 contains hardcoded internal pointers to `0xd234...` because Qualcomm compiled the binary for base `0xd2312000`, while the ELF header uses `p_vaddr = 0xc3e0f000`.
- In Patch 30, all card routing buffers, antenna script tables, and injected device structures were aligned so that memory accesses resolve within valid memory blocks.

---

## 3. Analysis: Why "NoNetworkFound" Occurs

The modem is actively searching, but reporting `NoNetworkFound`. There are two main reasons:

1. **Jio Band 40 (TDD 2300 MHz) vs Band 3/5**:
   - The UFI001B NV configuration currently enabled on the modem restricts LTE to Bands 1, 3, 5, 8.
   - Reliance Jio relies heavily on Band 40 (TDD 2300 MHz) in many Indian regions.
   - If the local cell tower transmits primary coverage on Band 40, the modem will not detect it until Band 40 is enabled in the NV band mask (`NV_LTE_BC_CONFIG_I`).

2. **WTR1605 RF Front-End Tuning Command Execution**:
   - While our vtable stubs successfully prevent Layer 1 from crashing when querying device capabilities, active RF tuning (programming the WTR1605 local oscillator / PLL to the exact downlink ARFCN frequencies) requires the actual transceiver commands to execute.
   - Next phase work will connect the transceiver tuning dispatch routines to the WTR1605 RF control functions in `modem.b26`.

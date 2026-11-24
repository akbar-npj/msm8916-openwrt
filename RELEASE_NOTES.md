> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

## v25.12.6-r10 (2026-11-24) — built from `main`

- 🆕 **New board: HM-MIFIB-V18** (`mifid`) — a battery MiFi with a 1.8" ST7735R LCD on SPI5,
  a PM8916 MPP backlight and SIM-rail GPIO hogs. It ships a framebuffer status dashboard
  (signal, battery, SIM/SMS/call tiles and popups) with a LuCI editor; the LuCI page is gated
  on the framebuffer, so boards without an LCD do not show it. The VoLTE stack is enabled on
  this image by default.

- 🔋 **Battery charge-limit logging.** The charge-limit monitor now logs every state transition
  (waiting / hold / charge / idle / unplugged) instead of only once at startup, and a stop or
  restart while holding restores the real charge current instead of capturing the 90 mA hold
  floor. The fix is in the shared `luci-app-battery` package, so it applies to every battery
  board, including **HM-MIFIB-V12** and HM-MIFIB-V18.

- 📶 **Wi-Fi station recovery.** A station that wedged in the 4-way handshake took nine minutes
  to come back; it is now re-armed in about 30 seconds, `DELETE_BSS` is retried and its handle
  dropped when it fails, and handshake-timeout reloads are counted so the recovery can tell a
  stubborn AP from a dead driver. A new default-on **probe-TX recovery** keeps the WCN3620 TX
  path alive with a periodic on-channel active scan — the only lever that clears the TX-path
  stall — and runs detached so a wedged scan cannot freeze the watchdog.

- 📞 **Calls were silent in both directions on a weak 4G signal.** The voice daemon chose the
  DSP voice session from the IMS registration state rather than the call's actual radio access.
  When a call fell back to 3G while IMS still reported registered, it was routed into a VoLTE
  session and no audio passed. The carrier RAT policy is now data-driven and configurable: the
  affected carrier is held on LTE, other carriers keep 3G/4G fallback, and a global UCI override
  lets you choose per device. Verified end-to-end with a real call — LTE throughout, audio both
  ways.

- 🎧 **One spurious error at the end of every call.** The DSP returns status `1` for a clean
  playback STOP, which the driver logged as `q6voice ... command 0x11239 failed with error 1`.
  The benign STOP status is now treated as success; call teardown is clean.

- 🌐 **IPv4 policy routing is enabled.** `IP_ADVANCED_ROUTER`, `IP_MULTIPLE_TABLES` and
  `IP_MROUTE_MULTIPLE_TABLES` are on, fixing builds that relied on multiple routing tables
  (#149).

- 🔀 **IPv6-only carriers get native IPv4 via CLAT (464XLAT).** Bearers that come up without an
  IPv4 address now get a NAT64/IPv4 translation path, so IPv4-only apps keep working.

- 📡 **The VoLTE/IMS stack ships on `jz0145` too**, alongside the main-stack improvements: the
  IMS service is published before LTE attach and IMS bearers are preserved across modem
  handoffs.

- 🗂️ **Carrier provisioning provisions the modem's default profile and refreshes the LTE attach
  context**, and activates the carrier MCFG so the modem runs its IMS stack.

- 🧰 **Also**: the publish-kmod workflow now resolves and bumps the package that actually owns
  each kmod (so published kmod versions no longer go stale); a stale modem index is re-resolved
  in `msm89xx-scripts`; the new-board generator produces correct DTS nodes and registration; a
  per-unit baseband identity recorder and manifests are added; and the shared LCD base-files
  changes carry board markers so the board-profile gate stays accurate.

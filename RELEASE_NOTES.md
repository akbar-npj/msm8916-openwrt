> 📖 **Full build & flashing guide →** <https://github.com/akbar-npj/msm8916-openwrt/wiki/Build-and-Flashing-Reference>

## v25.12.6-r9 — Feature highlights

- 🛡️ **A raw-IP redirect to an IFB could reboot the router.** With `tc` ingress `mirred` on
  `wwan0` (what SQM/CAKE does when it shapes the LTE interface through an IFB) and inbound
  traffic, the kernel panicked with `skb_under_panic` and the unit rebooted; disabling CAKE did
  not help, because `mirred` runs first. On an L3 device there is no L2 header, so `skb->mac_len`
  holds a buffer offset rather than a header length, and the push walked one byte past the
  headroom. `act_mirred` now asks the source device whether it has an L2 header at all, and drops
  a packet it cannot fit instead of panicking.

- 🎧 **The voice audio path is in the kernel now.** The q6voice stack (MVM, CVS, CVP) over APR,
  internal BT-SCO ports, in-call record taps and playback, as `kmod-sound-msm8916-voice` in the
  feed for every board, with the card enabled on `mifib-v12` and `ufi003-mb-v02`. This is the
  kernel half only — the userspace voice packages are not part of this release.

- 🔧 **A modem assertion during calls.** `bam_dmux_pc_irq()` compared the power-control line
  against `pc_state` before taking the lock; the RX watchdog can resync the same edge first, and
  the double ack left the A2 handshake one toggle off, which the modem answers with
  `a2_power.c:3017:A2 Assertion Failed`. The state is re-checked under the lock.

- 💡 **LED setup was rebuilt around roles.** Lamps are mapped by role (WAN, WLAN, battery) instead
  of by stock name, patterns are chosen from a dropdown instead of typed, the stock LED page is
  hidden, the battery roles are only offered on boards that have a battery, and the `hmuf02v5`
  colours are corrected (green is LTE, blue is Wi-Fi).

- 📶 **A Wi-Fi station that wedged in the 4-way handshake took nine minutes to come back.** The
  station interface is now re-armed in about 30 seconds, `DELETE_BSS` is retried and its handle
  dropped when it fails, and handshake-timeout reloads are counted so the recovery can tell a
  stubborn AP from a dead driver.

- 🎬 **Hardware H.264.** `kmod-video-qcom-venus` and the Venus 1.8 firmware ship on every board,
  so encode and decode run on the video core instead of the CPU. It is a module, not part of the
  default image payload, on every board including `jz0145`.

- 🆕 **New board `jz0145`** (`jz0145-v33`) — a UFI003-class stick, with the A2 power domain held
  active so its modem stays up.

- 💾 **Recovery images**: UZ801 is now buildable, the `dropbearkey` multicall symlink is included,
  and UFI003 image names keep their hyphens.

- ⚠️ **Reflash to move to r9 — do not mix it with r8 packages.** The kernel config changed, so the
  kernel's ABI tag changed with it. The LuCI Firmware Upgrade page is the normal path and handles
  this; what does not work is `apk add` of a kmod on an r8 unit after r9 is published, because the
  feed then carries only r9's kmods.

- 🧰 **Also**: filesystem, netfilter, QoS, storage, misc and sound kmods are enabled; Israeli APNs
  added; the firmware-upgrade page clears its hotfix banner after installing; one hotfix entry per
  package; CI moved off Node 20 onto `ubuntu-24.04` and keeps board-build images as a 7-day
  artifact; redundant BFQ settings dropped from the target config.

# Modem Bearer Watchdog & Mobility Profile

The `modem-bearer-watchdog` daemon keeps the LTE data bearer healthy on these
MSM8916 dongles. It detects genuine user-plane stalls (eNodeB DRB
de-scheduling / RF freeze) by watching `wwan0` RX counters, performs a
pre-emptive modem subsystem restart (SSR) when needed, and recovers a
"zombie" bearer/ModemManager without a cold reboot.

On top of that, it has a **mobility profile**: it automatically switches to a
handover-safe stall configuration while the device is moving, and back to the
stationary configuration when it stops. This document explains how that works
and how to tune it.

> Board status: the mobility profile was validated on **HMU05** over a ~20-minute
> city drive (zero spurious stalls). The other boards use the same code but
> have not been drive-tested — see the call for per-board maintainers.

---

## 1. How the mobility profile works

The watchdog resolves an **effective profile** every loop, in this precedence:

| Precedence | Condition | Profile | Stall / check |
| :--- | :--- | :--- | :--- |
| 1 | `mobile_optimized = 1` | **mobile-forced** | 30 s / 5 s (always) |
| 2 | `auto_mobile = 1` **and moving** | **mobile-detected** | 30 s / 5 s |
| 3 | `auto_mobile = 1` **and stationary** | **stationary-detected** | your configured values (60 s / 10 s) |
| 4 | both off | **config** | your configured values |

### `auto_mobile` (default **on**)

When enabled, the watchdog **boots in the stationary profile** and watches the
serving cell. On a genuine *forward* cell change it switches to the mobile
profile (30/5) and starts a grace timer. While you keep moving, each new cell
refreshes the timer, so it stays in the mobile profile for the whole journey.
When the cell stops changing for `mobile_grace_secs` (180 s) it reverts to
stationary.

**Why a "forward" change, not any change:** a device parked on a cell boundary
bounces between exactly two cells. That ping-pong must **not** toggle the mobile
profile, so a return to the previous cell is ignored — only a *third*, genuinely
different cell counts as forward movement. The very first cell transition after
startup is also suppressed, so re-acquiring the network at boot never trips it.

**How the cell is read (important):** the serving cell is read over
**ModemManager's D-Bus** via `mmcli --location-get` (MCC+MNC : area : cell-id).
This reuses ModemManager's own QMI client, so it never opens a second QMI
session. A `qmicli` poll loop, by contrast, previously **exhausted QMI
ClientIds** and wedged the modem (`ClientIdsExhausted`) — do not poll the modem
with `qmicli` from your own scripts while this is running.

### `mobile_optimized` (default **off**)

Manual force mode: always use the 30/5 profile regardless of movement. Use this
if you know the device will always be in a vehicle and don't want the
auto-detection. It is hidden in the LuCI UI unless `auto_mobile` is off.

---

## 2. Configuration

All options live in `/etc/config/modem-watchdog` (section `general`):

| Option | Default | Meaning |
| :--- | :--- | :--- |
| `enabled` | `1` | Master on/off for the watchdog. |
| `stall_timeout` | `60` | Seconds the bearer may be quiet before a stall is declared (stationary profile). |
| `check_interval` | `10` | How often RX counters are sampled (stationary profile). |
| `cooldown_seconds` | `60` | Minimum gap between recovery attempts. |
| `mobile_optimized` | `0` | Force the 30/5 profile always (manual mode). |
| `auto_mobile` | `1` | Automatic movement detection (the mobility profile). |
| `mobile_grace_secs` | `180` | How long to stay in the mobile profile after the last forward cell change. |
| `cell_poll_secs` | `15` | How often the serving cell is sampled for movement (throttled). |

A profile change resets the stall counter, so switching between profiles never
carries a half-accumulated count across the different thresholds.

### Examples

```bash
# Automatic (default): boot stationary, go mobile while driving, revert when parked
uci set modem-watchdog.general.auto_mobile='1'
uci commit modem-watchdog
/etc/init.d/modem-bearer-watchdog restart

# Manual force: always 30/5 (e.g. a car router that never sits still)
uci set modem-watchdog.general.auto_mobile='0'
uci set modem-watchdog.general.mobile_optimized='1'
uci commit modem-watchdog
/etc/init.d/modem-bearer-watchdog restart

# Tune the grace window / poll rate
uci set modem-watchdog.general.mobile_grace_secs='120'
uci set modem-watchdog.general.cell_poll_secs='10'
uci commit modem-watchdog
/etc/init.d/modem-bearer-watchdog restart
```

---

## 3. Web UI (LuCI)

Install `luci-app-modem-watchdog` (already selected in the five board
diffconfigs). It adds **Services → Modem Watchdog** with:

- a daemon status banner,
- enable/disable,
- the `auto_mobile` toggle + `mobile_grace_secs` / `cell_poll_secs` tuning,
- the manual `mobile_optimized` flag,
- `stall_timeout` / `check_interval`, keepalive, and recovery-escalation
  settings.

Changes take effect after saving and restarting the daemon.

---

## 4. Recovery ladder (context)

The mobility profile only changes *how patient* the watchdog is before it acts.
The actual recovery it triggers is unchanged:

1. **Stall detected** → verify with a retrying DNS probe (the first uplink
   packet after RRC-idle is routinely dropped, so a single probe would be a
   false stall).
2. **Bearer down & stuck** → re-arm the interface (`ifup modem`).  The
   stuck test is `autostart=true` (a down interface that is *supposed* to be
   up) **or** a recorded `MM_CONNECT_FAILED` error — a proto connect failure
   is never an intentional `ifdown` (a clean ifdown leaves `errors` empty),
   and netifd leaves autostart false in that state.  Live case 2026-10-25: the
   first LTE attach after a cold boot timed out (`Network timeout`), the
   interface went down with `autostart=false` while the modem itself
   re-registered and attached seconds later — the autostart-only gate then
   skipped it on every check and the bearer stayed down for over an hour with
   no default route.  The re-drive itself is `ubus call network.interface.modem
   up` + `notify_proto {action:5, available:true}`, cooldown-bounded at 60 s.
3. **Zombie modem** (no ModemManager object after a fatal) → a bounded
   `qmicli --dms-set-operating-mode=online` nudge.
4. **Last resort** → modem subsystem restart via the async `msm_subsys` node
   (Android-equivalent `subsystem_restart`), never the blocking remoteproc
   stop/start.

The 30/5 mobile profile simply means steps 1–2 fire faster during the brief RX
gaps that accompany a handover, so a user sees far less "no internet" time
while driving.

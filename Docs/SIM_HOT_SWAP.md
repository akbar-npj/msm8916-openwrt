# Physical SIM Hot-Swap

**Status:** implemented and live-verified on **HMUF02-V5 only**.
**Other boards:** **untested** — the hardware *may* expose a usable
`sim_hotplug` line, but nobody has verified it yet. See
[Testing on another board](#testing-on-another-board).
**Service:** `sim-hotplug` (`/etc/init.d/sim-hotplug`, `/usr/sbin/sim-hotplug`).

> **Board scope.** Hot-swap is **verified only on HMUF02-V5**, whose device tree
> exposes the `sim_hotplug` line (TLMM GPIO114) as a
> userspace-controllable `gpio-leds` node. The other boards' **current DTS
> in this repo do not define such a node** — but that does **not** mean the
> hardware lacks the line. It may be present and simply not exposed.
> **Device owners should verify it** with the procedure below before assuming
> hot-swap is unsupported on their board.

## Why this exists

These dongles have **no AP-side SIM card-detect line**. The baseband firmware
also ships with `feature_support_hotswap` **disabled**:

- It **notices a removal** — UIM starts answering `error: possibly-removed`.
- It **never notices an insertion** — a card can sit in the slot and the modem
  will not see it until the AP pulses `sim_hotplug`.

So a physical swap needs the AP to (a) notice the card is gone, (b) pulse the
line to make the baseband re-read the slot, and (c) make ModemManager reload the
SIM so a *different* carrier's IMSI/APN is visible to `qcom-carrier-autocfg`.

## What it does

| State | Behaviour |
| :--- | :--- |
| Card present + modem registered | one UIM check every **60 s** |
| Card present + modem searching | one UIM check every **5 s** (cadence only — `searching` alone never means "swap") |
| Card believed absent | pulse `sim_hotplug` → wait 4 s → re-check, every **6 s** |

On `absent → present` the service:

1. waits for the modem to register (up to 60 s);
2. forces ModemManager to re-read the SIM (`mmcli -d` + `mmcli -e`),
   which is what makes a **different** card's IMSI/APN visible;
3. brings the data bearer back up (`ifup modem`, under the bearer lock).

The pulse is only sent while the card is believed **absent**. Pulsing a card
that is already visible makes the modem bounce and can keep it in
`searching`.

## Measured latency

End to end on hardware: **~15 s** for a ~4 s swap.

```
16:33:20 SIM removed (UIM absent)
16:33:37 SIM detected after re-announce
16:33:42 modem registered
16:33:46 re-reading the SIM through ModemManager / bearer up
          → 10.152.146.4/29, ping 0% loss
```

## Design constraints (deliberate)

- **No modem-state detection.** `searching` is not a swap signal — a
  moving train or a cell reselection produces the same state. Detection is
  UIM-only, so those events trigger nothing.
- **No naive `qmicli` poll loop.** Every QMI client must be closed or
  the service hits `ClientIdsExhausted` (which caused a service
  disruption in an earlier attempt). The service uses
  `--device-open-proxy`, one short-lived client at a time, always under
  `timeout`, serialised with a `flock`.
- **No SSR / `mmcli -r`.** The re-read is a disable/enable
  (`-d`/`-e`), about 10 s, and never resets the modem.
- **Boot grace.** The loop waits 10 s before its first query so ModemManager
  can finish enumerating.

## Configuration

`/etc/config/sim-hotplug`:

| Option | Default | Meaning |
| :--- | :--- | :--- |
| `enabled` | `1` | run the service |
| `idle_interval` | `60` | UIM poll period when registered |
| `active_interval` | `5` | UIM poll period while out of service |
| `absent_interval` | `6` | re-announce period while the card is absent |
| `pulse_ms` | `300` | `sim_hotplug` pulse width |

## Manual operation

```sh
sim-hotplug --help
/etc/init.d/sim-hotplug status
/etc/init.d/sim-hotplug rescan      # force one re-announce now
logread -e sim-hotplug
```

## Known limitation

A swap **shorter than the modem's own removal debounce** is invisible: the modem
keeps its cached card, stays `connected`, and UIM keeps answering
`present`, so there is nothing for the AP to detect. This is harmless
when the same card goes back in. If a *different* card is inserted that quickly,
run `/etc/init.d/sim-hotplug rescan` — or let the bearer watchdog's SSR
re-initialise UIM when data fails.

## Board status

| Board | `sim_hotplug` node in current DTS | Hot-swap |
| :--- | :--- | :--- |
| `hmuf02v5` (HMUF02-V5) | **yes** — patch 805, `&tlmm 114` | **implemented + verified** |
| `uz801` | not exposed | **untested — verify** |
| `ufi001b` | not exposed | **untested — verify** |
| `ufi003` | not exposed | **untested — verify** |
| `uf02` | not exposed | **untested — verify** |
| `mf800b` | not exposed | **untested — verify** |

Only patch **805** currently defines `/sim-presence/sim_hotplug`
(`gpios = <&tlmm 114 GPIO_ACTIVE_HIGH>`). The service ships in the
shared `base-files` and is `enabled 1` on every board. On a board
with no writable `/sys/class/leds/sim_hotplug/brightness`,
`pulse_sim` fails, so the service keeps polling UIM but can never
re-announce an inserted card.

## Testing on another board

Please do this on real hardware. **Do not pulse a GPIO you have not identified**
— a pulse on an unrelated line can disturb other hardware.

1. **Look for an existing node.**

   ```sh
   ls -l /sys/class/leds/ | grep -i sim
   cat /sys/firmware/devicetree/base/sim-presence/sim_hotplug/gpios 2>/dev/null
   grep -ril 'sim' /sys/firmware/devicetree/base/ 2>/dev/null | head
   ```

   If `/sys/class/leds/sim_hotplug/brightness` exists, the line is
   already exposed — skip to step 3.

2. **Find the line from the board.** Check the schematic / vendor DTS for a
   GPIO wired to the baseband's SIM re-detect. Compare with the HMUF02-V5
   wiring (GPIO114, active-high). If the line exists but is not exposed, add a
   `gpio-leds` node like patch 805, or drive it temporarily with
   `gpioset` (from `libgpiod-tools`).

3. **Confirm the modem reacts.** With a card present:

   ```sh
   qmicli --device-open-proxy -d /dev/wwan0qmi0 --uim-get-card-status
   echo 255 > /sys/class/leds/sim_hotplug/brightness; sleep 0.3
   echo 0   > /sys/class/leds/sim_hotplug/brightness
   sleep 3
   qmicli --device-open-proxy -d /dev/wwan0qmi0 --uim-get-card-status
   ```

   Then the real case: remove the card (UIM should report
   `possibly-removed`), reinsert it, pulse, and check whether UIM
   reports `present` again.

4. **Run the service end to end.**

   ```sh
   uci set sim-hotplug.general.enabled='1'; uci commit sim-hotplug
   /etc/init.d/sim-hotplug restart
   logread -f -e sim-hotplug     # then physically swap the card
   ```

5. **Report back** with the board name, GPIO number, polarity, pulse width, and
   whether a physical swap recovered. That is what turns "untested" into
   "supported".

## Implementation

| File | Role |
| :--- | :--- |
| `msm89xx/base-files/usr/sbin/sim-hotplug` | the poll/pulse/recover logic |
| `msm89xx/base-files/etc/init.d/sim-hotplug` | procd service, `rescan` command |
| `msm89xx/base-files/etc/config/sim-hotplug` | defaults |
| `msm89xx/base-files/etc/rc.d/S98sim-hotplug` | enabled at boot (survives sysupgrade) |
| `msm89xx/patches/805-...hmuf02v5.patch` | GPIO114 `gpio-leds` node |

## Verification record

- Live card swap on HMUF02-V5: removal detected in 5–7 s, insertion in ≤10 s,
  re-read + bearer up by ~15 s, new IP, ping 0 % loss.
- Verified on the scp'd build twice and again on the flashed `sysupgrade -n`
  image.

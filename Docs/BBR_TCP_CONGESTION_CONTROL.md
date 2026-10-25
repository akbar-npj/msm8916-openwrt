# BBR as the default TCP congestion control

**Status:** shipped (default since 25.12.6) · **Files:** `msm89xx/base-files/etc/sysctl.d/10-bbr.conf`, `msm89xx/modules.mk`, `msm89xx/Makefile`, `msm89xx/config-6.12`

## TL;DR

OpenWrt ships **CUBIC** as the default TCP congestion control. On this port we
switch the default to **BBR**, because the primary WAN on every board in this
project is a **4G/LTE bearer**, and CUBIC's core assumption — *"packet loss means
the network is congested"* — is false on a radio link.

The switch is one sysctl at boot:

~~~conf
net.ipv4.tcp_congestion_control = bbr
~~~

No userspace daemon, no per-connection tuning, nothing to configure. See
[What we changed](#what-we-changed-in-the-tree) for the exact plumbing and
[Reverting](#reverting) to go back.

---

## 1. What a congestion control algorithm actually does

TCP has no idea how fast the path can carry data. Every TCP sender therefore
runs an algorithm that guesses the path's capacity and sets two numbers:

| Knob | Meaning |
| :--- | :--- |
| **cwnd** (congestion window) | how many bytes may be in flight before an ACK comes back |
| **pacing rate** | how fast those bytes are injected into the NIC |

Get it too low and the link is under-used. Get it too high and the excess
queues up in a router or base station — latency climbs, and eventually something
drops packets. The algorithm's whole job is to sit just below the point where
queues start to build.

The two families of algorithm differ in *what signal they use to find that
point*:

- **Loss-based** (Reno, CUBIC — OpenWrt's default): keep growing the window
  until a packet is lost, then cut the window hard. Loss *is* the congestion
  signal.
- **Model-based** (BBR): measure the **delivery rate** and the **minimum RTT**
  directly, and send at that rate. Loss is treated as noise, not as a signal.

---

## 2. Why CUBIC is a poor fit for an LTE link

CUBIC (RFC 9438) is an excellent algorithm for a wired path, where a loss
almost always means a full buffer somewhere. An LTE bearer breaks that
assumption in five separate ways.

### 2.1 Loss on LTE is mostly *not* congestion

A cellular link loses packets for reasons that have nothing to do with a full
queue:

- **RF fading and interference.** SINR moves continuously; a deep fade drops
  blocks.
- **Link adaptation runs at ~10 % BLER by design.** LTE's adaptive modulation
  and coding deliberately targets roughly a 10 % first-transmission block error
  rate — that is how it probes for the highest usable MCS. HARQ recovers most of
  it, but residual loss and, more importantly, **reordering and multi-hundred-ms
  delay spikes** leak through to TCP.
- **Handovers.** A cell change stalls the bearer for a moment and can reorder
  in-flight packets.
- **Scheduler grants.** The uplink is grant-based; a burst of TCP ACKs competes
  with data for grants, so the ACK stream itself is bursty and jittery.

CUBIC reads every one of these as "the path is congested" and cuts its window —
even when the path was nowhere near full. On a marginal signal this turns into
a permanent sawtooth at a fraction of the achievable rate.

### 2.2 The buffers are enormous (bufferbloat)

An eNodeB and the carrier's gateways buffer hundreds of milliseconds of traffic
per bearer. A loss-based sender fills that buffer *before* it ever sees the loss
that tells it to stop. That standing queue is the classic bufferbloat pattern,
and on LTE it is severe:

- idle RTT is typically **30–80 ms**
- under a CUBIC bulk download the same path can sit at **300–2000 ms**

Interactive traffic (SSH, DNS, LuCI, the modem's own QMI round-trips) then
shares that queue and becomes unusable *while a download runs*, even though the
download itself reports a healthy throughput number.

### 2.3 RTT is high and variable

CUBIC's window growth is a function of RTT, so it converges slowly on a
long-RTT path, and every latency spike (see 2.1) resets its state. LTE RTT also
moves for reasons unrelated to load — DRX sleep cycles, the modem's power state,
and bearer re-establishment all add tens to hundreds of milliseconds.

### 2.4 Capacity changes every few milliseconds

LTE re-picks the MCS on essentially every subframe, so the instantaneous
capacity of the bearer is a moving target. A loss-based algorithm cannot tell
"capacity just dropped because SINR dropped" from "capacity is unchanged and I
overran a buffer" — both look like loss, and both trigger the same backoff.

### 2.5 The uplink is usually the scarce direction

On many consumer LTE plans and cells the uplink is the bottleneck and is
scheduled in small grants. CUBIC's bursty, window-driven sending interacts badly
with a grant-based scheduler: bursts exceed the grant, queue in the modem, and
inflate the very RTT the algorithm is using to make decisions.

---

## 3. What BBR does instead

BBR (Bottleneck Bandwidth and Round-trip propagation time, Cardwell et al.,
ACM Queue 2017) maintains two running estimates:

| Estimate | How it is measured |
| :--- | :--- |
| **BtlBw** — bottleneck bandwidth | windowed **maximum** of the measured delivery rate (bytes ACKed / time), over roughly the last 10 round trips |
| **RTprop** — propagation RTT | windowed **minimum** RTT over the last 10 seconds |

It then sends at `pacing_gain × BtlBw` with `cwnd = cwnd_gain × BtlBw × RTprop`.
Because RTprop is the *minimum* RTT — the RTT with no queue — that window is
exactly the amount of data the path can hold with **no standing queue**. BBR
keeps the pipe full and the buffer empty at the same time.

Its state machine is four phases:

- **Startup** — exponential growth until the delivery rate stops increasing for
  three rounds (it detects the plateau directly, it does not wait for a loss).
- **Drain** — briefly send slower to empty the queue Startup created.
- **ProbeBW** — steady state: an eight-phase cycle (1.25 → 0.75 → 1 ×6) that
  probes upward for more bandwidth once per cycle and backs off to drain.
- **ProbeRTT** — every 10 s, if the minimum RTT has not been re-measured,
  briefly drain to 4 packets for 200 ms to re-sample the true propagation delay.

The practical consequences on a cellular link:

1. **Loss no longer causes a window collapse.** A fade or a handover no longer
   costs throughput; BBR simply keeps sending at the rate it measured.
2. **No standing queue, so latency stays near idle under load.** This is the
   headline benefit for a pocket router: a bulk download no longer makes SSH and
   the web UI unusable.
3. **Capacity changes are tracked directly.** A drop in delivered rate is
   observed and adapted to within a few round trips, instead of being inferred
   from loss after the buffer has already filled.
4. **Pacing smooths the sender.** BBR paces packets instead of dumping a whole
   window at once, which is a much better match for a grant-based LTE uplink
   scheduler and for the modem's internal queues.

### Honest limitations

- **BBRv1 fairness.** Linux 6.12 ships BBRv1. When a BBR flow shares a
  bottleneck with loss-based flows, BBRv1 can take more than its share. On this
  device that is not the situation: the bottleneck is the LTE bearer of a single
  subscriber, and the flows competing inside it are all ours. If you run this
  box as a busy multi-client hotspot behind a congested shared cell, evaluate
  before deploying.
- **It does not make a weak signal faster.** It makes latency predictable and
  keeps throughput near whatever the radio can actually deliver. If the SINR is
  bad, the link is still slow.
- **It does not affect UDP or QUIC.** Those protocols carry their own congestion
  control; the sysctl only changes TCP.
- **It does not change the modem's queues.** BBR paces the *AP's* TCP sender.
  The eNodeB's buffer and the modem firmware's own queues are untouched.

---

## 4. What we changed in the tree

| File | Change |
| :--- | :--- |
| `msm89xx/config-6.12` (the target kernel config, shared by all 7 boards) | `CONFIG_TCP_CONG_BBR=m` — builds `net/ipv4/tcp_bbr.ko` as a module |
| `msm89xx/modules.mk` | `KernelPackage/sched-bbr` → package `kmod-sched-bbr`, ships `tcp_bbr.ko`, `AUTOLOAD := $(call AutoLoad,30,tcp_bbr)` |
| `msm89xx/Makefile` | `kmod-sched-bbr` in `DEFAULT_PACKAGES` — preinstalled on every image |
| `msm89xx/base-files/etc/sysctl.d/10-bbr.conf` | `net.ipv4.tcp_congestion_control = bbr` — makes it the **default** |

### Why a sysctl and not a daemon

Autoloading a module only makes an algorithm *available*; the kernel keeps
whatever `net.ipv4.tcp_congestion_control` already says, and the kernel's
built-in default is `cubic`. One line in `/etc/sysctl.d/` is enough, because the
boot ordering already works out:

| Step | Init script | `START` | Effect |
| :--- | :--- | :--- | :--- |
| 1 | `/etc/init.d/boot` | **10** | runs `/sbin/kmodloader`, which loads `tcp_bbr` from the autoload stanza and registers the algorithm |
| 2 | `/etc/init.d/sysctl` | **11** | applies `/etc/sysctl.d/*.conf` and `/etc/sysctl.conf` |

So `bbr` is registered before the sysctl that selects it. No retry loop, no
`preinit` hack, no `sleep`. (If the ordering ever regressed, the kernel would
reject the write with `ENOENT` and the system would silently stay on CUBIC —
worth checking once per image, see below.)

### Why not `CONFIG_DEFAULT_TCP_CONG="bbr"`?

Because it cannot be done. `CONFIG_DEFAULT_TCP_CONG` is resolved at boot by the
kernel's built-in TCP stack, so it can only name an algorithm that is **built
into** `vmlinux`. `tcp_bbr` is `=m` here (and upstream has no Kconfig option to
build it in as the default), so the kernel falls back to `cubic` at boot and the
sysctl — which runs a second later, after the module is loaded — is the only
correct way to select it.

### Why the qdisc is left alone

BBR is often paired with the `fq` qdisc. That pairing dates from kernels before
4.20, when `fq` was what implemented BBR's pacing. Since 4.20 BBR paces
internally via the TCP stack's own high-resolution timer, so **any** qdisc works.
OpenWrt's default is `fq_codel`, which is a good choice for a shared Wi-Fi AP
(it also handles the non-TCP traffic), so we keep it:

~~~text
# tc qdisc show dev wwan0
qdisc fq_codel 0: root refcnt 2 limit 10240p flows 1024 quantum 1514 target 5ms interval 100ms ...
~~~

---

## 5. Verifying it on a live device

~~~sh
# 1. the module is loaded
lsmod | grep tcp_bbr

# 2. bbr is registered and is the active default
cat /proc/sys/net/ipv4/tcp_available_congestion_control   # cubic reno bbr
cat /proc/sys/net/ipv4/tcp_congestion_control             # bbr

# 3. a live connection is actually using it
ss -tin state established '( dport = :443 )' | head -20
~~~

`ss -tin` prints the algorithm in use plus BBR's own diagnostics
(`bbr (bw,rtt)`) on each socket, so you can confirm that new connections — not
just the default — are on BBR.

### Measuring the benefit

The claim to test is *"latency stays flat under load"*, not *"throughput goes
up"*. Saturate the downlink and watch the RTT:

~~~sh
# baseline RTT with the link idle
ping -c 20 1.1.1.1 | tail -2

# CUBIC
sysctl -w net.ipv4.tcp_congestion_control=cubic
wget -q -O /dev/null http://<a-large-file-on-the-internet> &
sleep 5; ping -c 40 -i 0.2 1.1.1.1 | tail -2; kill %1

# BBR
sysctl -w net.ipv4.tcp_congestion_control=bbr
wget -q -O /dev/null http://<a-large-file-on-the-internet> &
sleep 5; ping -c 40 -i 0.2 1.1.1.1 | tail -2; kill %1
~~~

With CUBIC the loaded RTT typically climbs to several hundred milliseconds or
more. With BBR it should stay close to the idle figure. Compare the reported
`wget` throughput too — on a clean signal the two should be similar, because the
win is latency, not raw bandwidth.

---

## 6. Reverting

Per boot (until the next reboot):

~~~sh
sysctl -w net.ipv4.tcp_congestion_control=cubic
~~~

Permanently — either delete the drop-in:

~~~sh
rm /etc/sysctl.d/10-bbr.conf
~~~

or keep the file and change its value to `cubic`. Per-destination overrides
work too, if your `iproute2` supports the route attribute:

~~~sh
ip route change default via 10.64.64.64 dev wwan0 congctl cubic
~~~

The `kmod-sched-bbr` package can stay installed in every case — it only makes
the algorithm available, it does not select it.

---

## 7. References

- **RFC 9438** — CUBIC for Fast and Long-Distance Networks
- **RFC 5681** — TCP Congestion Control
- Cardwell, Cheng, Gunn, Hassas Yeganeh, Jacobson — *BBR: Congestion-Based
  Congestion Control*, ACM Queue, 2017
- Linux `Documentation/networking/tcp_bbr.rst`

## Related

- [**Docs/Custom Packages/Kernel-Modules.md**](Custom%20Packages/Kernel-Modules.md)
  — the `kmod-sched-bbr` package row
- [**Docs/ARCHITECTURE.md**](ARCHITECTURE.md) — where `base-files`, `modules.mk`
  and `config-6.12` sit in the tree

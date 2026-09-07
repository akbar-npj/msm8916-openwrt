#!/usr/bin/env python3
import subprocess
import time
import sys
import os
import re

sys.stdout.reconfigure(line_buffering=True)

ROUTER_IP = "192.168.8.1"
SSH_CMD = ["ssh", "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "ConnectTimeout=3", f"root@{ROUTER_IP}"]
CSV_OUTPUT = "/home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem Stability/ab_test_qcom_time_r0_telemetry.csv"

def run_remote(cmd_str, timeout=6):
    try:
        res = subprocess.run(SSH_CMD + [cmd_str], capture_output=True, text=True, timeout=timeout)
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except Exception as e:
        return -1, "", str(e)

COLLECT_CMD = """
echo "===UPTIME==="; cat /proc/uptime 2>/dev/null || echo "0 0"
echo "===PING==="; ping -c 1 -W 1 1.1.1.1 >/dev/null 2>&1 && echo "PING_OK" || echo "PING_FAIL"
echo "===NETDEV==="; cat /sys/class/net/wwan0/statistics/rx_packets /sys/class/net/wwan0/statistics/tx_packets /sys/class/net/wwan0/statistics/rx_bytes /sys/class/net/wwan0/statistics/tx_bytes 2>/dev/null || echo "NETDEV_ABSENT"
echo "===INTERRUPTS==="; grep -E "bam_dma|bam-dmux" /proc/interrupts 2>/dev/null || echo "IRQ_ABSENT"
echo "===RX_TELEMETRY==="; cat /sys/devices/platform/soc@0/4080000.remoteproc/4080000.remoteproc:bam-dmux/rx_telemetry 2>/dev/null || echo "RX_TELEMETRY_ABSENT"
echo "===WDS_STATS==="; qmicli -d /dev/wwan0qmi0 -p --wds-get-packet-statistics 2>/dev/null || echo "WDS_ABSENT"
echo "===MM_STATE==="; mmcli -m any --output-keyvalue 2>/dev/null | grep -E "modem.generic.state|modem.3gpp.registration-state|modem.3gpp.packet-service-state|modem.generic.signal-quality.value" || echo "MM_ABSENT"
"""

def parse_telemetry(raw_output):
    data = {
        "uptime_s": 0.0,
        "ping_ok": False,
        "netdev_rx_pkt": None, "netdev_tx_pkt": None, "netdev_rx_bytes": None, "netdev_tx_bytes": None,
        "bam_dma_irq": None, "bam_dmux_irq": None,
        "rx_callbacks": None, "rx_queued_buffers": None, "rx_slots_submitted": None, "rx_slots_free": None,
        "rx_submit_failed": None, "rx_last_cb_ms": None, "pc_state": None, "runtime_status": None,
        "wds_rx_ok": None, "wds_tx_ok": None, "wds_rx_drop": None, "wds_tx_drop": None,
        "modem_state": None, "reg_state": None, "packet_state": None, "signal_quality": None,
        "rx_telemetry_present": True
    }
    
    current_sec = None
    sec_dict = {}
    for line in raw_output.splitlines():
        line = line.strip()
        m = re.match(r"^===([A-Z_]+)===$", line)
        if m:
            current_sec = m.group(1)
            sec_dict[current_sec] = []
        elif current_sec is not None:
            sec_dict[current_sec].append(line)

    # UPTIME
    up_lines = sec_dict.get("UPTIME", [])
    if up_lines:
        try:
            data["uptime_s"] = float(up_lines[0].split()[0])
        except:
            pass

    # PING
    ping_lines = sec_dict.get("PING", [])
    if ping_lines and "PING_OK" in ping_lines[0]:
        data["ping_ok"] = True

    # NETDEV
    net_lines = sec_dict.get("NETDEV", [])
    if net_lines and len(net_lines) >= 4 and "NETDEV_ABSENT" not in net_lines[0]:
        try:
            data["netdev_rx_pkt"] = int(net_lines[0])
            data["netdev_tx_pkt"] = int(net_lines[1])
            data["netdev_rx_bytes"] = int(net_lines[2])
            data["netdev_tx_bytes"] = int(net_lines[3])
        except:
            pass

    # INTERRUPTS
    irq_lines = sec_dict.get("INTERRUPTS", [])
    dma_sum = 0
    dmux_sum = 0
    for l in irq_lines:
        if "bam_dma" in l:
            parts = l.split()
            try:
                counts = [int(p) for p in parts[1:5] if p.isdigit()]
                dma_sum += sum(counts)
            except:
                pass
        elif "bam-dmux" in l:
            parts = l.split()
            try:
                counts = [int(p) for p in parts[1:5] if p.isdigit()]
                dmux_sum += sum(counts)
            except:
                pass
    data["bam_dma_irq"] = dma_sum
    data["bam_dmux_irq"] = dmux_sum

    # RX_TELEMETRY
    rx_lines = sec_dict.get("RX_TELEMETRY", [])
    if not rx_lines or "RX_TELEMETRY_ABSENT" in "".join(rx_lines):
        data["rx_telemetry_present"] = False
    else:
        for l in rx_lines:
            if ":" in l:
                k, v = [x.strip() for x in l.split(":", 1)]
                if k == "rx_callbacks" and v.isdigit(): data["rx_callbacks"] = int(v)
                elif k == "rx_last_callback_ms_ago" and v.isdigit(): data["rx_last_cb_ms"] = int(v)
                elif k == "rx_queued_buffers" and v.isdigit(): data["rx_queued_buffers"] = int(v)
                elif k == "rx_slots_submitted" and v.isdigit(): data["rx_slots_submitted"] = int(v)
                elif k == "rx_slots_free" and v.isdigit(): data["rx_slots_free"] = int(v)
                elif k == "rx_submit_failed" and v.isdigit(): data["rx_submit_failed"] = int(v)
                elif k == "pc_state": data["pc_state"] = v
                elif k == "runtime_status": data["runtime_status"] = v

    # WDS_STATS
    wds_lines = sec_dict.get("WDS_STATS", [])
    for l in wds_lines:
        if "TX packets OK:" in l:
            m = re.search(r'\d+', l)
            if m: data["wds_tx_ok"] = int(m.group(0))
        elif "RX packets OK:" in l:
            m = re.search(r'\d+', l)
            if m: data["wds_rx_ok"] = int(m.group(0))
        elif "TX packets dropped:" in l:
            m = re.search(r'\d+', l)
            if m: data["wds_tx_drop"] = int(m.group(0))
        elif "RX packets dropped:" in l:
            m = re.search(r'\d+', l)
            if m: data["wds_rx_drop"] = int(m.group(0))

    # MM_STATE
    mm_lines = sec_dict.get("MM_STATE", [])
    for l in mm_lines:
        if re.match(r"^modem\.generic\.state\s*:", l):
            data["modem_state"] = l.split(":", 1)[1].strip()
        elif re.match(r"^modem\.3gpp\.registration-state\s*:", l):
            data["reg_state"] = l.split(":", 1)[1].strip()
        elif re.match(r"^modem\.3gpp\.packet-service-state\s*:", l):
            data["packet_state"] = l.split(":", 1)[1].strip()
        elif re.match(r"^modem\.generic\.signal-quality\.value\s*:", l):
            data["signal_quality"] = l.split(":", 1)[1].strip()

    return data

def safe_append_csv(line):
    for _ in range(3):
        try:
            with open(CSV_OUTPUT, "a") as f:
                f.write(line)
            return
        except Exception as e:
            time.sleep(0.1)

print(f"[*] Connecting to router at {ROUTER_IP}...")
for _ in range(30):
    code, out, _ = run_remote("uptime")
    if code == 0:
        print(f"[+] Router reachable: {out}")
        break
    time.sleep(2)
else:
    print(f"[-] Cannot reach router at {ROUTER_IP}")
    sys.exit(1)

print(f"[*] Cold rebooting router cleanly for fresh A/B test baseline (qcom-time-daemon -r 0)...")
run_remote("reboot")
time.sleep(22)

for retry in range(40):
    code, out, _ = run_remote("uptime")
    if code == 0:
        print(f"[+] Router back up! Uptime: {out}")
        break
    time.sleep(3)
else:
    print("[-] Timeout waiting for reboot.")
    sys.exit(1)

# Check and verify qcom-time-daemon status and syslog verification
print("[*] Verifying qcom-time-daemon boot status and initial ATS sync...")
time.sleep(3)
code, ps_out, _ = run_remote("ps w | grep '[q]com-time-daemon'")
print(f"  Process: {ps_out.strip()}")
code, log_out, _ = run_remote("logread | grep 'QMI-TIME'")
print("--- QMI-TIME Boot Syslog Transactions ---")
print(log_out.strip())
print("-----------------------------------------")

print("[*] Checking bearer establishment and user plane ping (up to 180s)...")
bearer_ready = False
for wait_idx in range(90):
    code, out, _ = run_remote(COLLECT_CMD)
    tele = parse_telemetry(out)
    if tele["ping_ok"]:
        print(f"[+] Network UP and flowing at iteration {wait_idx} (Baseband Uptime: {tele['uptime_s']:.1f}s)!")
        bearer_ready = True
        break
    if wait_idx % 5 == 0:
        print(f"  [Wait {wait_idx}/90] Ping: {'OK' if tele['ping_ok'] else 'FAIL'}, Modem: {tele['modem_state']}, Uptime: {tele['uptime_s']:.1f}s")
    time.sleep(2)

if not bearer_ready:
    print("[-] Failed to establish user plane ping after reboot.")
    sys.exit(1)

t0 = time.time()
print(f"[*] Starting A/B test soak capture (qcom-time-daemon -r 0) at t0 = {time.ctime(t0)}...")

with open(CSV_OUTPUT, "w") as f:
    f.write("timestamp,elapsed_s,uptime_s,ping,netdev_rx_pkt,netdev_tx_pkt,netdev_rx_bytes,netdev_tx_bytes,"
            "bam_dma_irq,bam_dmux_irq,rx_callbacks,rx_queued_buffers,rx_slots_submitted,rx_slots_free,"
            "rx_submit_failed,rx_last_cb_ms,runtime_status,wds_rx_ok,wds_tx_ok,wds_rx_drop,wds_tx_drop,"
            "modem_state,reg_state,packet_state,signal_quality\n")

print(f"{'Elapsed':<10} | {'Uptime':<8} | {'Ping':<5} | {'WDS RX':<8} | {'wwan0 RX':<9} | {'BAM Callbacks':<13} | {'BAM DMA IRQ':<11} | {'Queued':<6} | {'SubFailed':<9} | {'PM Status'}")
print("-" * 105)

prev_wds_rx = None
prev_netdev_rx = None
prev_callbacks = None
prev_bam_irq = None
fail_count = 0
freeze_detected = False
passed_930_announced = False

while True:
    now = time.time()
    elapsed = int(now - t0)

    code, out, _ = run_remote(COLLECT_CMD)
    tele = parse_telemetry(out)

    if tele["uptime_s"] >= 930.0 and not passed_930_announced:
        passed_930_announced = True
        print("\n" + "*"*80)
        print(f"[***] MILESTONE PASSED: Baseband uptime reached {tele['uptime_s']:.1f}s (> 930s) with 0 crashes!")
        print(f"[***] Previous crashes occurred at 912.2s - 913.0s. The 15-minute crash did NOT occur!")
        print("*"*80 + "\n")

    d_wds = (tele["wds_rx_ok"] - prev_wds_rx) if (tele["wds_rx_ok"] is not None and prev_wds_rx is not None) else 0
    d_net = (tele["netdev_rx_pkt"] - prev_netdev_rx) if (tele["netdev_rx_pkt"] is not None and prev_netdev_rx is not None) else 0
    d_cb  = (tele["rx_callbacks"] - prev_callbacks) if (tele["rx_callbacks"] is not None and prev_callbacks is not None) else 0
    d_irq = (tele["bam_dma_irq"] - prev_bam_irq) if (tele["bam_dma_irq"] is not None and prev_bam_irq is not None) else 0

    prev_wds_rx = tele["wds_rx_ok"]
    prev_netdev_rx = tele["netdev_rx_pkt"]
    prev_callbacks = tele["rx_callbacks"]
    prev_bam_irq = tele["bam_dma_irq"]

    p_str = "OK" if tele["ping_ok"] else "FAIL"
    wds_s = f"{tele['wds_rx_ok'] or 0} (+{d_wds})"
    net_s = f"{tele['netdev_rx_pkt'] or 0} (+{d_net})"
    cb_s  = f"{tele['rx_callbacks'] or 0} (+{d_cb})"
    irq_s = f"{tele['bam_dma_irq'] or 0} (+{d_irq})"
    q_s   = f"{tele['rx_queued_buffers'] or 0}"
    sf_s  = f"{tele['rx_submit_failed'] or 0}"
    pm_s  = f"{tele['runtime_status'] or 'N/A'}"
    up_s  = f"{tele['uptime_s']:.0f}s"

    print(f"{elapsed:>4}s ({elapsed//60}m{elapsed%60:02d}s) | {up_s:<8} | {p_str:<5} | {wds_s:<8} | {net_s:<9} | {cb_s:<13} | {irq_s:<11} | {q_s:<6} | {sf_s:<9} | {pm_s}")
    sys.stdout.flush()

    safe_append_csv(f"{int(now)},{elapsed},{tele['uptime_s']:.1f},{1 if tele['ping_ok'] else 0},{tele['netdev_rx_pkt']},{tele['netdev_tx_pkt']},"
                    f"{tele['netdev_rx_bytes']},{tele['netdev_tx_bytes']},{tele['bam_dma_irq']},{tele['bam_dmux_irq']},"
                    f"{tele['rx_callbacks']},{tele['rx_queued_buffers']},{tele['rx_slots_submitted']},{tele['rx_slots_free']},"
                    f"{tele['rx_submit_failed']},{tele['rx_last_cb_ms']},{tele['runtime_status']},{tele['wds_rx_ok']},"
                    f"{tele['wds_tx_ok']},{tele['wds_rx_drop']},{tele['wds_tx_drop']},{tele['modem_state']},{tele['reg_state']},"
                    f"{tele['packet_state']},{tele['signal_quality']}\n")

    # Freeze detection: 3 consecutive fails after uptime >= 600s
    if not tele["ping_ok"]:
        fail_count += 1
        if fail_count >= 3 and tele["uptime_s"] >= 600 and not freeze_detected:
            freeze_detected = True
            print(f"\n" + "="*80)
            print(f"[!] DOWNLINK FREEZE DETECTED AT test_elapsed = {elapsed}s ({elapsed//60}m{elapsed%60:02d}s), baseband_uptime = {tele['uptime_s']:.1f}s!")
            print(f"[*] Preserving failed state without recovery commands.")
            print(f"[*] Starting 5-second high-resolution 1-second burst capture...")
            print("="*80)

            burst_data = []
            for b_idx in range(5):
                b_now = time.time()
                b_elapsed = int(b_now - t0)
                b_code, b_out, _ = run_remote(COLLECT_CMD, timeout=4)
                b_tele = parse_telemetry(b_out)
                burst_data.append((b_elapsed, b_tele))
                print(f"  [Burst {b_idx+1}/5 @ {b_elapsed}s (up:{b_tele['uptime_s']:.0f}s)] Ping: {'OK' if b_tele['ping_ok'] else 'FAIL'} | "
                      f"WDS_RX: {b_tele['wds_rx_ok']} | wwan0_RX: {b_tele['netdev_rx_pkt']} | "
                      f"BAM_Callbacks: {b_tele['rx_callbacks']} | BAM_DMA_IRQ: {b_tele['bam_dma_irq']} | "
                      f"Queued: {b_tele['rx_queued_buffers']} | SubFailed: {b_tele['rx_submit_failed']} | "
                      f"PM: {b_tele['runtime_status']}")
                safe_append_csv(f"{int(b_now)},{b_elapsed},{b_tele['uptime_s']:.1f},{1 if b_tele['ping_ok'] else 0},{b_tele['netdev_rx_pkt']},{b_tele['netdev_tx_pkt']},"
                                f"{b_tele['netdev_rx_bytes']},{b_tele['netdev_tx_bytes']},{b_tele['bam_dma_irq']},{b_tele['bam_dmux_irq']},"
                                f"{b_tele['rx_callbacks']},{b_tele['rx_queued_buffers']},{b_tele['rx_slots_submitted']},{b_tele['rx_slots_free']},"
                                f"{b_tele['rx_submit_failed']},{b_tele['rx_last_cb_ms']},{b_tele['runtime_status']},{b_tele['wds_rx_ok']},"
                                f"{b_tele['wds_tx_ok']},{b_tele['wds_rx_drop']},{b_tele['wds_tx_drop']},{b_tele['modem_state']},{b_tele['reg_state']},"
                                f"{b_tele['packet_state']},{b_tele['signal_quality']}\n")
                time.sleep(1)

            first_b = burst_data[0][1]
            last_b  = burst_data[-1][1]

            delta_wds_rx = (last_b["wds_rx_ok"] or 0) - (first_b["wds_rx_ok"] or 0)
            delta_wwan_rx = (last_b["netdev_rx_pkt"] or 0) - (first_b["netdev_rx_pkt"] or 0)
            delta_callbacks = (last_b["rx_callbacks"] or 0) - (first_b["rx_callbacks"] or 0)
            delta_irq = (last_b["bam_dma_irq"] or 0) - (first_b["bam_dma_irq"] or 0)

            print("\n" + "="*80)
            print("[*] DECISIVE BURST EVALUATION RESULTS:")
            print(f"  - Delta QMI WDS RX Packets:      {delta_wds_rx}  (Initial: {first_b['wds_rx_ok']}, Final: {last_b['wds_rx_ok']})")
            print(f"  - Delta wwan0 Netdev RX Packets: {delta_wwan_rx} (Initial: {first_b['netdev_rx_pkt']}, Final: {last_b['netdev_rx_pkt']})")
            print(f"  - Delta BAM RX Callbacks:        {delta_callbacks} (Initial: {first_b['rx_callbacks']}, Final: {last_b['rx_callbacks']})")
            print(f"  - Delta BAM DMA IRQ:             {delta_irq} (Initial: {first_b['bam_dma_irq']}, Final: {last_b['bam_dma_irq']})")
            print(f"  - BAM Queued Buffers:            {last_b['rx_queued_buffers']}")
            print(f"  - BAM Submit Failed:             {last_b['rx_submit_failed']}")
            print(f"  - BAM Runtime PM Status:         {last_b['runtime_status']}")
            print(f"  - Modem 3GPP State:              {last_b['modem_state']} / {last_b['reg_state']} / {last_b['packet_state']}")
            print("-" * 80)

            if not last_b["rx_telemetry_present"]:
                print("[!] NOTICE: rx_telemetry node was ABSENT on target.")

            if (last_b["rx_submit_failed"] or 0) > 0:
                print("[***] DIAGNOSIS: Driver/DMA descriptor allocation exhaustion (rx_submit_failed > 0).")
            elif (last_b["rx_queued_buffers"] or 0) == 0 and last_b["rx_telemetry_present"]:
                print("[***] DIAGNOSIS: Driver/DMA buffer depletion (rx_queued_buffers == 0).")
            elif delta_wds_rx > 0 and delta_callbacks > 0 and delta_wwan_rx == 0:
                print("[***] DIAGNOSIS: Host demux / netdev path failure (Modem received packets and BAM delivered descriptors, but wwan0 dropped them).")
            elif delta_wds_rx > 0 and delta_callbacks == 0:
                print("[***] DIAGNOSIS: Modem-to-BAM or DMA path failure (Modem received packets over radio, but BAM DMA engine failed to deliver descriptors to AP).")
            elif delta_wds_rx == 0 and delta_callbacks == 0 and delta_wwan_rx == 0:
                print("[***] DIAGNOSIS: Modem L1/DRB or carrier-side receive path failure (Modem radio stack stopped receiving packets from cellular network).")
            else:
                print(f"[***] DIAGNOSIS: Mixed state: WDS_RX_delta={delta_wds_rx}, Callbacks_delta={delta_callbacks}, Netdev_delta={delta_wwan_rx}.")
            print("="*80 + "\n")

            print("[*] Monitoring post-freeze state for 60 seconds (no recovery commands issued)...")
            for post_idx in range(12):
                time.sleep(5)
                p_now = time.time()
                p_el = int(p_now - t0)
                _, p_out, _ = run_remote(COLLECT_CMD, timeout=4)
                p_tele = parse_telemetry(p_out)
                print(f"  [Post-Freeze {p_el:>4}s (up:{p_tele['uptime_s']:.0f}s)] Ping: {'OK' if p_tele['ping_ok'] else 'FAIL'} | "
                      f"WDS_RX: {p_tele['wds_rx_ok']} | wwan0_RX: {p_tele['netdev_rx_pkt']} | "
                      f"BAM_Callbacks: {p_tele['rx_callbacks']} | BAM_DMA_IRQ: {p_tele['bam_dma_irq']}")
            break
    else:
        fail_count = 0

    if elapsed >= 1100: # 18.3 min max
        print("\n[+] Soak test completed without freeze!")
        break

    time.sleep(5)

print("\n[*] Multi-tier soak test finished. Data logged to:", CSV_OUTPUT)

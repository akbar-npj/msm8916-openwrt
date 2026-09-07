#!/usr/bin/env python3
import subprocess
import time
import sys
import os
import re

ROUTER_IP = "192.168.8.1"
SSH_CMD = ["ssh", "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "ConnectTimeout=3", f"root@{ROUTER_IP}"]
CSV_OUTPUT = "/home/shaanair/Projects/msm8916-openwrt-clean/Docs/Modem Stability/wds_dormancy_test_results.csv"

def run_remote(cmd_str):
    try:
        res = subprocess.run(SSH_CMD + [cmd_str], capture_output=True, text=True, timeout=5)
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except Exception as e:
        return -1, "", str(e)

print(f"[*] Checking initial connectivity to {ROUTER_IP}...")
code, out, _ = run_remote("uptime")
if code != 0:
    print(f"[-] Cannot reach router at {ROUTER_IP}")
    sys.exit(1)
print(f"[+] Router is up: {out}")

print("[*] Checking qcom-time-daemon status...")
code, out, _ = run_remote("ps | grep '[q]com-time-daemon'")
if out:
    print(f"[!] Warning: qcom-time-daemon is running: {out}. Stopping it now...")
    run_remote("/etc/init.d/qcom-time-daemon stop")
else:
    print("[+] Confirmed: qcom-time-daemon is NOT running.")

print("[*] Checking ModemManager status and current bearer...")
code, out, _ = run_remote("mmcli -m any --output-keyvalue")
print(f"[+] Modem info summary:\n" + "\n".join([line for line in out.splitlines() if any(k in line for k in ["modem.generic.state", "modem.generic.access-technologies", "modem.generic.signal-quality.value"])]))

print("[*] Rebooting router cleanly to begin t=0 test with fresh baseband...")
run_remote("reboot")

print("[*] Waiting for router to reboot...")
time.sleep(20)

for retry in range(40):
    code, out, _ = run_remote("uptime")
    if code == 0:
        print(f"[+] Router is back up! Uptime: {out}")
        break
    time.sleep(3)
else:
    print("[-] Timeout waiting for router to reboot")
    sys.exit(1)

print("[*] Waiting for ModemManager to detect modem and connect bearer...")
bearer_ready = False
for wait_idx in range(30):
    code, out, _ = run_remote("ip -br addr show dev wwan0")
    if "10." in out:
        print(f"[+] Bearer established! IP: {out}")
        bearer_ready = True
        break
    time.sleep(2)

if not bearer_ready:
    print("[-] Bearer was not automatically established within 60s, checking mmcli...")
    run_remote("ifup modem")
    time.sleep(10)

# Verify qcom-time-daemon did not start
code, out, _ = run_remote("ps | grep '[q]com-time-daemon'")
if out:
    print(f"[!] Stopping auto-started qcom-time-daemon: {out}")
    run_remote("/etc/init.d/qcom-time-daemon stop")

print("[*] Waiting for first successful ping to 1.1.1.1...")
for wait_idx in range(30):
    code, _, _ = run_remote("ping -c 1 -W 1 1.1.1.1")
    if code == 0:
        print("[+] User-plane data flowing! Ping to 1.1.1.1 SUCCEEDED.")
        break
    time.sleep(2)
else:
    print("[-] Ping failed after boot.")
    sys.exit(1)

t0 = time.time()
print(f"[*] Starting 15-minute soak test at t0 = {time.ctime(t0)}...")

prev_rx_pkt = 0
prev_tx_pkt = 0
fail_count = 0
last_mm_log_line = 0

# Get initial line count of mm.log
code, out, _ = run_remote("wc -l < /var/log/mm.log")
try:
    last_mm_log_line = int(out.strip())
except:
    last_mm_log_line = 0

with open(CSV_OUTPUT, "w") as csv_f:
    csv_f.write("timestamp,elapsed_sec,ping_status,rx_packets,tx_packets,rx_bytes,tx_bytes,irq17_bam,last_wds_events\n")

print(f"{'Elapsed':<10} | {'Ping':<5} | {'RX Pkts':<10} | {'TX Pkts':<10} | {'IRQ17':<7} | {'WDS Events'}")
print("-" * 80)

freeze_detected = False
last_known_dormancy = "UNKNOWN"
freeze_elapsed = 0

while True:
    now = time.time()
    elapsed = int(now - t0)

    # 1. Ping test
    code, _, _ = run_remote("ping -c 1 -W 1 1.1.1.1")
    ping_ok = (code == 0)

    # 2. Kernel stats
    code, stats_out, _ = run_remote("cat /sys/class/net/wwan0/statistics/rx_packets /sys/class/net/wwan0/statistics/tx_packets /sys/class/net/wwan0/statistics/rx_bytes /sys/class/net/wwan0/statistics/tx_bytes")
    try:
        parts = stats_out.split()
        rx_pkt, tx_pkt, rx_bytes, tx_bytes = [int(p) for p in parts[:4]]
    except:
        rx_pkt, tx_pkt, rx_bytes, tx_bytes = 0, 0, 0, 0

    # 3. IRQ 17
    code, irq_out, _ = run_remote("grep -w '17:' /proc/interrupts")
    irq17 = irq_out.split()[1] if irq_out else "0"

    # 4. New WDS events from mm.log
    code, new_lines_out, _ = run_remote(f"tail -n +{last_mm_log_line + 1} /var/log/mm.log | grep '\\[WDS-EVENT\\]'")
    wds_events = []
    if new_lines_out:
        for l in new_lines_out.splitlines():
            m = re.search(r'\[WDS-EVENT\].*', l)
            if m:
                ev = m.group(0)
                wds_events.append(ev)
                if "dormancy=traffic-channel-dormant" in ev:
                    last_known_dormancy = "DORMANT"
                elif "dormancy=traffic-channel-active" in ev:
                    last_known_dormancy = "ACTIVE"

    # Update line count
    code, out, _ = run_remote("wc -l < /var/log/mm.log")
    try:
        last_mm_log_line = int(out.strip())
    except:
        pass

    ev_str = " | ".join(wds_events) if wds_events else ""

    rx_delta = rx_pkt - prev_rx_pkt if prev_rx_pkt else 0
    tx_delta = tx_pkt - prev_tx_pkt if prev_tx_pkt else 0
    prev_rx_pkt = rx_pkt
    prev_tx_pkt = tx_pkt

    print(f"{elapsed:>4}s ({elapsed//60}m{elapsed%60:02d}s) | {'OK' if ping_ok else 'FAIL':<5} | {rx_pkt:>7} (+{rx_delta:>3}) | {tx_pkt:>7} (+{tx_delta:>3}) | {irq17:>7} | {ev_str}")
    sys.stdout.flush()

    with open(CSV_OUTPUT, "a") as csv_f:
        clean_ev = ev_str.replace(",", ";")
        csv_f.write(f"{int(now)},{elapsed},{1 if ping_ok else 0},{rx_pkt},{tx_pkt},{rx_bytes},{tx_bytes},{irq17},{clean_ev}\n")

    if not ping_ok:
        fail_count += 1
        if fail_count >= 3 and elapsed >= 600 and not freeze_detected:
            freeze_detected = True
            freeze_elapsed = elapsed
            print(f"\n[!] FREEZE DETECTED at t = {elapsed}s ({elapsed//60}m{elapsed%60:02d}s)!")
            print(f"[!] Last known WDS dormancy state: {last_known_dormancy}")

            if last_known_dormancy == "DORMANT":
                print("[!] Condition A: Modem reported DORMANT at freeze point.")
                print("[*] Testing recovery via single manual 'qmicli -d /dev/wwan0qmi0 -p --wds-go-active'...")
                code, act_out, act_err = run_remote("qmicli -d /dev/wwan0qmi0 -p --wds-go-active")
                print(f"[+] GO_ACTIVE response: {act_out} {act_err}")
                time.sleep(2)
                code, _, _ = run_remote("ping -c 2 -W 1 1.1.1.1")
                if code == 0:
                    print("[***] RESULT: PING UNFROZE! Dormancy was the root cause!")
                else:
                    print("[***] RESULT: PING STILL FAILED after GO_ACTIVE.")
            elif last_known_dormancy == "ACTIVE":
                print("[***] RESULT: Condition B: Modem reported ACTIVE throughout freeze.")
                print("[***] CONCLUSION: Fast-dormancy is 100% RULED OUT as the root cause!")
                print("[***] The stall is downstream in BAM DMA descriptor ring / Hexagon ML1 sleepmgr.")
            else:
                print("[***] RESULT: Condition C: No WDS dormancy event reported.")
                print("[***] CONCLUSION: Freeze occurs below WDS indication layer.")
            
            # Continue monitoring for 60 more seconds to observe post-freeze behavior
            print("\n[*] Monitoring for 60 more seconds to capture post-freeze telemetry...")
            for post_idx in range(12):
                time.sleep(5)
                now = time.time()
                elapsed = int(now - t0)
                code, stats_out, _ = run_remote("cat /sys/class/net/wwan0/statistics/rx_packets /sys/class/net/wwan0/statistics/tx_packets")
                print(f"{elapsed:>4}s (post-freeze) | Netdev: {stats_out.strip()}")
            break
    else:
        fail_count = 0

    if elapsed >= 1020: # 17 minutes max
        print(f"\n[+] Soak test completed 17 minutes without freeze!")
        break

    time.sleep(5)

print("\n[*] Soak test finished. Results saved to:", CSV_OUTPUT)

# Qualcomm MSM8916 Stock Android SSH & Debugging Toolkit

This toolkit provides an automated deployment system and a comprehensive utility suite for Qualcomm Snapdragon 410 (MSM8916) 4G LTE USB dongles and portable Wi-Fi routers (e.g., **HM-MIFIB-V12**, **HMU05**, **Melbon**, **UFI001B / UFI003**, **FXD 4G LTE**, etc.) running stock 32-bit Android (4.4.4 KitKat / userdebug).

It provides:
- **Permanent OpenSSH Server (`sshd`)** on port 22 with full root access.
- **Dual Authentication**: Passwordless login (via SSH key and/or empty password) and password-protected login (via standard `passwd`).
- **Full Debugging Tool Suite (426 CLI tools)** in `/system/xbin` including `bash`, `htop`, `strace`, `gdb`, `gdbserver`, `curl`, `git`, `tcpdump`, `iperf`, `socat`, `vim`, `nano`, `wg` (WireGuard), `zramctl`, and all BusyBox applets.
- **EDL Mode Flexibility**:
  - Uses native `reboot edl` command by default.
  - Includes an **optional payload** (`reboot-edl` and `edl_tcsr.ko`) that can be installed on devices where stock `reboot edl` is missing or non-functional.
- **Convenient System Commands**: Standalone `rw` and `ro` commands to quickly unlock and lock the `/system` partition.
- **Full Terminal Emulation**: Deployed terminfo database for `xterm`, `xterm-256color`, `linux`, and `vt100`.

---

## Directory Structure

```
device_debug_toolkit/
├── install.sh                  # Automated installer script (supports --with-edl / --without-edl)
├── README.md                   # This guide and documentation
├── payload/                    # Base payload deployed to /system
    ├── xbin/                   # 87 standalone static ARM binaries & helper scripts
    │   ├── bash, sshd, ssh, scp, sftp, sftp-server, ssh-keygen, ssh-add...
    │   ├── curl, git, htop, iftop, nano, vim, strace, gdb, gdbserver...
    │   ├── lsof, socat, tcpdump, iperf, iperf3, rsync, ncdu, netcat, nethogs...
    │   ├── iw, wg, inotifywait, inotifywatch, zramctl, ntfs-3g, mount.exfat-fuse...
    │   ├── useradd, usermod, userdel, groupadd, chsh, passwd...
    │   ├── rw                  # Standalone helper: unlocks /system as RW
    │   └── ro                  # Standalone helper: locks /system as RO
    ├── etc/
    │   ├── ssh/                # OpenSSH server & client configuration + host keys
    │   │   ├── sshd_config     # Configured for Android (PrivilegeSeparation disabled)
    │   │   ├── ssh_config
    │   │   ├── moduli
    │   │   └── ssh_host_*      # Pre-generated RSA, ECDSA, ED25519 host keys
    │   ├── passwd              # Root account (shell: /system/xbin/bash, home: /data/local)
    │   ├── shadow              # Default empty root password (ready for custom 'passwd')
    │   ├── group               # Root and sshd user group definitions
    │   ├── gshadow
    │   ├── profile             # Sources /system/etc/bashrc on login
    │   ├── bashrc              # Colorized prompt, aliases (rw, ro, ll, ports, mem), PATH
    │   └── nanorc, virc, inputrc, mkshrc, login.defs
    └── usr/
        └── share/
            └── terminfo/       # Curses terminal definitions (xterm, xterm-256color, etc.)
└── payload_optional/
    └── edl/                    # Optional EDL payload for devices lacking native 'reboot edl'
        ├── bin/
        │   └── reboot-edl      # Userspace script that arms TCSR latch and reboots
        └── lib/
            └── modules/
                └── edl_tcsr.ko # Kernel module issuing SCM SMC write to Qualcomm TCSR
```

---

## 1. Quick Start: Automated Installation

Connect your device via USB to your host computer, then run:

### Standard Installation (Default: Native `reboot edl`)
```bash
cd /home/shaanair/Projects/msm8916-openwrt-clean/device_debug_toolkit
./install.sh
```
*If running interactively, the script will ask if you wish to install the optional `reboot-edl` tool.*

### Installation Options
```bash
# Explicitly skip the optional EDL payload (recommended for standard devices):
./install.sh --without-edl

# Install WITH the optional 'reboot-edl' script & 'edl_tcsr.ko' module:
./install.sh --with-edl

# Target a specific device serial (if multiple ADB devices are attached):
./install.sh --without-edl -s 10d200c3
```

---

## 2. Connecting to the Device

### SSH Terminal Connection
```bash
# Connect to the device (passwordless with key or empty password prompt):
ssh root@192.168.100.1
```

> **Note on Host Key Warning**: If you have previously connected to a different device on the same IP, clear the stale key from known_hosts with:
> `ssh-keygen -R 192.168.100.1`

### SFTP File Transfer Connection
```bash
sftp root@192.168.100.1
```
You can browse, upload, and download files directly through SFTP or with graphical clients like FileZilla or VS Code SFTP extensions.

---

## 3. Managing Authentication (Password vs. Passwordless)

### Option A: Passwordless Access (Default)
The toolkit is pre-configured with two passwordless login mechanisms:
1. **Public Key Authentication**: If your host computer has `~/.ssh/id_rsa.pub` or `~/.ssh/id_ed25519.pub`, `install.sh` automatically copies it into `/data/local/.ssh/authorized_keys`. When connecting from this computer, SSH connects immediately without prompting for credentials.
2. **Empty Password Authentication**: In `/system/etc/shadow`, root has an empty password field, and `sshd_config` permits empty passwords (`PermitEmptyPasswords yes`). If connecting from a computer or phone without keys, simply press Enter when prompted.

### Option B: Securing Root with a Custom Password
If you wish to enforce password-protected login:
1. Connect to the device via SSH:
   ```bash
   ssh root@192.168.100.1
   ```
2. Run `passwd`:
   ```bash
   passwd
   ```
   > **Note on Automatic Partition Handling**: `/system/xbin/passwd` includes an automatic wrapper that checks if `/system` is mounted read-only (`ro`), unlocks it to `rw` temporarily to write to `/system/etc/shadow`, and locks it back to `ro` upon completion. You can also run `rw`, `passwd`, and `ro` manually if preferred.

3. Enter your new password (minimum 5 characters, mixture of uppercase, lowercase, and numbers).
4. The next time a device connects without an authorized SSH key, SSH will prompt for and enforce this password.

### Option C: Reverting Back to Passwordless Login
If you previously set a password and want to remove it so login is passwordless again:
```bash
passwd -d root
```
*(The `-d` option deletes the password hash for root in `/etc/shadow`, restoring empty-password passwordless login).*

---

## 4. Useful Commands Inside the Device

| Command | Description |
|---|---|
| `passwd` | Change root password (automatically handles `/system` remounting). |
| `passwd -d root` | Delete root password to make SSH login passwordless again. |
| `rw` | Manually unlocks the `/system` partition (mounts `ext4` as read-write). |
| `ro` | Manually locks the `/system` partition (mounts `ext4` as read-only). |
| `reboot edl` | Reboots directly into Qualcomm Emergency Download (9008) mode (uses native Android `/system/bin/reboot`). |
| `reboot bootloader` | Reboots into Qualcomm Fastboot mode. |
| `reboot recovery` | Reboots into Recovery partition. |
| `reboot` | Performs clean device reboot. |
| `reboot-edl` | *(Optional package only)* Arms Qualcomm TCSR register via `edl_tcsr.ko` and enters EDL mode. |
| `htop` | Interactive process viewer and CPU/RAM monitor. |
| `tcpdump -i any` | Packet capture directly on cellular/bridge interfaces. |
| `iperf -s` | Launch iperf bandwidth test server. |
| `strace <cmd>` | Trace system calls and signals. |
| `ports` | Alias to list all listening network sockets (`netstat`). |
| `mem` | View current kernel memory summary. |

> **Note on `reboot` commands**: `/system/xbin/reboot` is explicitly linked to the native Android binary (`/system/bin/reboot`) rather than BusyBox `reboot`. Standard BusyBox `reboot` does not recognize Android arguments like `edl` or `bootloader`, whereas the native binary signals Android init (`powerctl`) to switch modes properly.

---

## 5. Technical Details & Manual Step-by-Step Reference

If you ever need to perform this setup manually or understand the underlying Android mechanics:

### Step 1: Elevating ADB Shell to Root
On Qualcomm stock Android userdebug builds, temporary root can be acquired via:
```bash
adb shell "setprop service.adb.root 1; busybox killall adbd"
# Wait 2-3 seconds for adbd to restart, then verify:
adb shell id
# Output should show: uid=0(root) gid=0(root)
```

### Step 2: Remounting `/system`
```bash
adb shell "mount -o remount,rw /system"
```

### Step 3: Deploying Binaries and Permissions
All binaries in `/system/xbin` are 32-bit static ARM executables compiled with musl/glibc:
```bash
adb shell "chmod 755 /system/xbin/*"
adb shell "chmod 6755 /system/xbin/tcpdump"
adb shell "busybox chown -R 0:0 /system/xbin /system/etc/ssh"
adb shell "/system/bin/busybox --install -s /system/xbin"
# Ensure reboot points to Android native reboot (for 'reboot edl', 'reboot bootloader', etc.):
adb shell "ln -sf /system/bin/reboot /system/xbin/reboot"
```

### Step 4: OpenSSH Server Android Adaptation
Standard OpenSSH expects `/var/empty` and system user separation. On Android, the following settings in `/system/etc/ssh/sshd_config` are required:
```sshconfig
Port 22
PermitRootLogin yes
PubkeyAuthentication yes
PasswordAuthentication yes
PermitEmptyPasswords yes
UseDNS no
UsePrivilegeSeparation no
PidFile /data/local/tmp/sshd.pid
Subsystem sftp /system/xbin/sftp-server
AuthorizedKeysFile /data/local/.ssh/authorized_keys .ssh/authorized_keys
```
And the privilege separation runtime directory must be created in `/data`:
```bash
mkdir -p /data/var/empty
chmod 700 /data/var/empty
busybox chown 0:0 /data/var/empty
```

### Step 5: Persistent Autostart on Boot
Qualcomm Android triggers `/system/etc/init.qcom.post_boot.sh` on every boot via Android init. Adding this block ensures SSH starts reliably:
```sh
# Master Boot Trigger: Unconditional SSH Lifecycle
(
    sleep 15
    setenforce 0
    mkdir -p /data/var/empty /data/local/tmp
    chmod 700 /data/var/empty
    /system/xbin/sshd -p 22
) &
```

### Step 6: Locking the System
```bash
adb shell "sync; sleep 1; mount -o remount,ro /system"
```

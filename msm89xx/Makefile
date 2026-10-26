# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

BOARD:=msm89xx
BOARDNAME:=Boards based on Snapdragon 210/410/615 chipsets
FEATURES:=ext4 usb usbgadget gpio fpu rootfs-part boot-part squashfs
SUBTARGETS:=msm8916

# Kernel version map: OpenWrt major -> kernel patchver
#KERNEL_FOR_24 := 6.6
KERNEL_FOR_25 := 6.12
KERNEL_DEFAULT := 6.12

OPENWRT_MAJOR := $(shell git -C $(TOPDIR) describe --tags --abbrev=0 2>/dev/null | cut -d. -f1 | tr -d v)
KERNEL_PATCHVER := $(or $(KERNEL_FOR_$(OPENWRT_MAJOR)),$(KERNEL_DEFAULT))


define Target/Description
	Build firmware images for various Snapdragon 210/410/615 based devices
endef

include $(INCLUDE_DIR)/target.mk

DEFAULT_PACKAGES += partx-utils e2fsprogs mkf2fs
DEFAULT_PACKAGES += kmod-usb-chipidea kmod-usb-gadget-eth
DEFAULT_PACKAGES += kmod-wcn36xx kmod-qcom-rproc-wcnss kmod-qcom-rproc
DEFAULT_PACKAGES += kmod-rpmsg-wwan-ctrl kmod-bam-dmux kmod-qcom-rproc-modem
# Bluetooth over the WCNSS (Pronto/RIVA) core via the SMD transport.
DEFAULT_PACKAGES += kmod-btqcomsmd kmod-btqca
# BlueZ userspace so Bluetooth is usable out of the box: bluetoothd, the
# bluetoothctl/hcitool/hciconfig/btmgmt tools (bluez-utils), and rfcomm/sdptool/
# bt-agent (bluez-tools).  The kernel side (rfcomm/bnep/hidp over BT) is already
# pulled in by kmod-bluetooth via kmod-btqcomsmd; without this the tools only
# exist as ad-hoc overlay installs and are lost on a clean flash.
DEFAULT_PACKAGES += bluez-daemon bluez-utils bluez-tools
# Boot-time guard that keeps the WCNSS BT controller's Secure Simple Pairing
# LMP-feature bit set (byte 6 bit 3 of NVM tag 6).  The BT NVM is RAM-only and
# a stock boot already defaults to the good state, so this is a silent no-op
# in the normal case; it exists so a cleared feature bit cannot leave the
# device stuck in failing legacy PIN pairing.  Also provides the
# `bt-ssp-guard status|repair` CLI.
DEFAULT_PACKAGES += bt-ssp-guard
# Bluetooth Serial Port Profile (SPP): a server (default-on) and an on-demand
# client, with a uci-selectable bridge (a login shell on a pty, or TCP).  Needs
# bluetoothd --compat for the local SDP socket (see the openwrt-overlay
# bluetoothd.init override).
DEFAULT_PACKAGES += bt-spp
# qcom-time-daemon is HMUF02-V5-only: its init gates on the board name (returns 0 on
# any non-HMUF02-V5 board) and it only exists to anchor the HMUF02-V5 modem's ATS state.
# It is pulled in by the generic-hmuf02v5 DEVICE_PACKAGES, not as a default.
DEFAULT_PACKAGES += rmtfs qcom-carrier-autocfg
# Interactive/debug helpers wanted on every board: `stty` (terminal control,
# e.g. for the serial console) and `xxd` (hex dumps).
DEFAULT_PACKAGES += coreutils-stty xxd
# nohup/timeout: same story -- wanted on every board.  They used to sit only in
# the hmuf02v5 and mifib-v12 diffconfigs.
DEFAULT_PACKAGES += coreutils-nohup coreutils-timeout
# BBR TCP congestion control.  Google's BBR is model-based (it paces on
# measured bandwidth and RTT) where the default CUBIC is loss-based, and on
# LTE links that matters: the bursty loss CUBIC treats as congestion drags
# throughput down.  Installing the module only makes BBR *available* -- the
# active control is BBR by default: /etc/sysctl.d/10-bbr.conf sets
# net.ipv4.tcp_congestion_control=bbr at boot (tcp_bbr is autoloaded).
# It was hand-copied into every diffconfig as =m before; the kernel symbol
# stays =m so it is a preinstalled, autoloaded external module, NOT compiled
# into vmlinux.
DEFAULT_PACKAGES += kmod-sched-bbr
# wpa_cli is the station-mode event monitor: it attaches to the STA's
# wpa_supplicant ctrl socket and runs wifi-sta-wpa-event, which flags a
# rejected password for the LuCI page (wifi-sta-wpa-event).
DEFAULT_PACKAGES += wpa-cli
# LuCI apps maintained in this repo.  They used to be hand-copied into every
# diffconfig, which is how ufi003 silently came to miss
# luci-app-modem-watchdog; living here they ship on every board (and on any
# future board) by default.
DEFAULT_PACKAGES += luci-app-cpu-perf luci-app-modem-watchdog luci-app-sms-manager \
	luci-app-tailscale luci-app-usb-gadget luci-app-wifi-sta
# The LuCI theme this repo maintains (packages/luci-theme-feelgood).  Its
# uci-defaults takes luci.main.mediaurlbase over from the stock bootstrap
# default, so a fresh image - and a config-preserving upgrade from an older
# build - both come up in Feelgood, while a deliberate choice of another theme
# is left alone.  luci-theme-bootstrap stays installed: LuCI falls back to it
# if the Feelgood templates ever fail to compile.
DEFAULT_PACKAGES += luci-theme-feelgood
# Upstream userspace packages that every board used to select by hand: 112
# CONFIG_PACKAGE lines byte-identical in all seven diffconfigs.  They live
# here now so the diffconfigs hold only genuinely per-board choices; an
# eighth board gets them automatically.  (Busybox/kernel/package option
# symbols and the =m kernel-module block stay in the diffconfigs -
# DEFAULT_PACKAGES cannot express those.)
DEFAULT_PACKAGES += \
	avahi-dbus-daemon bash blkid cgi-io coreutils-od dbus \
	etherwake glib2 ip-tiny kmod-crypto-acompress kmod-crypto-kpp \
	kmod-crypto-lib-chacha20 kmod-crypto-lib-chacha20poly1305 \
	kmod-crypto-lib-curve25519 kmod-crypto-lib-poly1305 \
	kmod-lib-lzo kmod-tun kmod-udptunnel4 kmod-udptunnel6 \
	kmod-wireguard kmod-zram libatomic libattr \
	libavahi-dbus-support libdaemon libdbus libexpat libfdisk \
	libffi libiwinfo libiwinfo-data libltdl liblua liblucihttp \
	liblucihttp-lua liblucihttp-ucode libmbim libminiupnpc \
	libmount libnatpmp libncurses libpcre2 libqmi libqrtr-glib \
	libreadline librrd1 libsensors libstdcpp libsysfs \
	libtraceevent libtracefs libubus-lua lm-sensors lsblk lua luci \
	luci-app-filemanager luci-app-firewall \
	luci-app-package-manager luci-app-wol luci-base luci-lib-base \
	luci-lib-ip luci-lib-json luci-lib-jsonc luci-lib-nixio \
	luci-lib-uqr luci-light luci-lua-runtime luci-mod-admin-full \
	luci-mod-network luci-mod-rpc luci-mod-status luci-mod-system \
	luci-proto-ipv6 luci-proto-modemmanager luci-proto-ppp \
	luci-proto-wireguard luci-theme-bootstrap modemmanager \
	mount-utils mtools nano openssh-sftp-avahi-service \
	openssh-sftp-client openssh-sftp-server px5g-mbedtls qmi-utils \
	resize2fs resolveip rpcd rpcd-mod-file rpcd-mod-iwinfo \
	rpcd-mod-luci rpcd-mod-rrdns rpcd-mod-ucode rrdtool1 sfdisk \
	strace sysfsutils terminfo trace-cmd tune2fs ucode-mod-html \
	ucode-mod-log ucode-mod-lua ucode-mod-math uhttpd \
	uhttpd-mod-ubus wireguard-tools zerotier zram-swap
# Physical reset button bridge.  These dongles have no Ethernet and no
# accessible UART, so the on-board Restart button (a `gpio-keys` node in the
# DTS) is the only way to reach failsafe mode.  kmod-gpio-button-hotplug binds
# that node and emits SUBSYSTEM=button uevents, which make procd's preinit
# hotplug daemon run /etc/rc.button/failsafe (writing /tmp/failsafe_button) and
# the post-boot /etc/rc.button/reset handler.  Without it the button is inert.
DEFAULT_PACKAGES += kmod-gpio-button-hotplug

KERNELNAME:=Image

$(eval $(call BuildTarget))

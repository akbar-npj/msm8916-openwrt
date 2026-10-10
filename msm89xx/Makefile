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
# qcom-time-daemon is HMU05-only: its init gates on the board name (returns 0 on
# any non-HMU05 board) and it only exists to anchor the HMU05 modem's ATS state.
# It is pulled in by the generic-hmu05 DEVICE_PACKAGES, not as a default.
DEFAULT_PACKAGES += rmtfs qcom-carrier-autocfg
# Interactive/debug helpers wanted on every board: `stty` (terminal control,
# e.g. for the serial console) and `xxd` (hex dumps).
DEFAULT_PACKAGES += coreutils-stty xxd
# Physical reset button bridge.  These dongles have no Ethernet and no
# accessible UART, so the on-board Restart button (a `gpio-keys` node in the
# DTS) is the only way to reach failsafe mode.  kmod-gpio-button-hotplug binds
# that node and emits SUBSYSTEM=button uevents, which make procd's preinit
# hotplug daemon run /etc/rc.button/failsafe (writing /tmp/failsafe_button) and
# the post-boot /etc/rc.button/reset handler.  Without it the button is inert.
DEFAULT_PACKAGES += kmod-gpio-button-hotplug

KERNELNAME:=Image

$(eval $(call BuildTarget))

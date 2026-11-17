# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=uci-usb-gadget
PKG_VERSION:=1.0.0
PKG_RELEASE:=3

PKG_LICENSE:=GPL-2.0-only
PKG_MAINTAINER:=Miguel Fuertes <hkfuertes@gmail.com>

include $(INCLUDE_DIR)/package.mk

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
endef

define Build/Compile
endef

define Package/$(PKG_NAME)
	SECTION:=utils
	CATEGORY:=Utilities
	TITLE:=UCI-based USB gadget manager
	DEPENDS:=@USB_GADGET_SUPPORT \
		+kmod-fs-configfs \
		+kmod-usb-gadget \
		+kmod-usb-lib-composite \
		+kmod-usb-gadget-serial \
		+kmod-usb-gadget-mass-storage \
		+kmod-usb-gadget-ncm \
		+coreutils-truncate \
		+coreutils-shuf \
		+coreutils-dd
	PKGARCH:=all
endef

define Package/$(PKG_NAME)/description
	UCI-based USB gadget manager using the kernel configfs interface.

	Configures OpenWrt devices as USB peripherals (RNDIS, ECM, NCM, ACM,
	Mass Storage) when connected to a host. Hardware-agnostic: auto-detects
	the UDC controller and integrates USB ethernet interfaces into the LAN bridge.
endef

define Package/$(PKG_NAME)/conffiles
/etc/config/usbgadget
endef

define Package/$(PKG_NAME)/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./files/usb-gadget.sh $(1)/usr/sbin/usb-gadget
	$(INSTALL_BIN) ./files/usb-gadget-probe $(1)/usr/sbin/usb-gadget-probe
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/usb-gadget.init $(1)/etc/init.d/usb-gadget
	$(INSTALL_BIN) ./files/usb-gadget-probe.init $(1)/etc/init.d/usb-gadget-probe
	$(INSTALL_DIR) $(1)/etc/rc.d
	$(LN) ../init.d/usb-gadget $(1)/etc/rc.d/S99usb-gadget
	$(LN) ../init.d/usb-gadget-probe $(1)/etc/rc.d/S99usb-gadget-probe
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/usbgadget.config $(1)/etc/config/usbgadget
endef

define Package/$(PKG_NAME)/postinst
#!/bin/sh
[ -n "$${IPKG_INSTROOT}" ] || {
	/etc/init.d/usb-gadget enable
	/etc/init.d/usb-gadget-probe enable

	# A unit that predates net_mode keeps the per-function booleans -- NCM
	# only, which is exactly the behaviour the probe exists to fix.  Adopt
	# it into auto so the fix reaches the units that already have the
	# package; a fresh install gets auto from the shipped config anyway.
	# A unit with RNDIS or ECM selected is left alone: that was a choice,
	# not the default.
	if [ -z "$$(uci -q get usbgadget.usb.net_mode)" ] &&
	   [ "$$(uci -q get usbgadget.ncm.enabled)" = "1" ] &&
	   [ "$$(uci -q get usbgadget.rndis.enabled)" != "1" ] &&
	   [ "$$(uci -q get usbgadget.ecm.enabled)" != "1" ]; then
		uci set usbgadget.usb.net_mode=auto
		uci commit usbgadget
	fi

	# An upgrade leaves the old gadget running the old script, so without
	# this the package installs and changes nothing until the next reboot.
	# Rebuilding drops the USB link for a few seconds; that is unavoidable
	# when the gadget is the thing being replaced, and it is why this runs
	# here rather than being left to the user.
	name=$$(uci -q get usbgadget.usb.gadget_name)
	[ -n "$$name" ] || name=g1
	[ -d "/sys/kernel/config/usb_gadget/$$name" ] && /etc/init.d/usb-gadget restart
	/etc/init.d/usb-gadget-probe restart
}
exit 0
endef

define Package/$(PKG_NAME)/prerm
#!/bin/sh
[ -n "$${IPKG_INSTROOT}" ] || {
	/etc/init.d/usb-gadget-probe stop
	/etc/init.d/usb-gadget-probe disable
	/etc/init.d/usb-gadget disable

	# The gadget itself is deliberately left running.  Stopping it here
	# would drop the USB link -- and on an upgrade there would be nothing
	# to bring it back until a reboot, because this script runs before the
	# new files are unpacked.  postinst rebuilds it once they are; on a real
	# removal it keeps working until the next reboot, which is harmless.
}
exit 0
endef

$(eval $(call BuildPackage,$(PKG_NAME)))

# SPDX-License-Identifier: BSD-3-Clause

include $(TOPDIR)/rules.mk

PKG_NAME:=bt-spp
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_LICENSE:=BSD-3-Clause
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(INCLUDE_DIR)/package.mk

define Package/bt-spp
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Bluetooth Serial Port Profile (SPP) server/client
  DEPENDS:=+bluez-utils
endef

define Package/bt-spp/description
Bluetooth Serial Port Profile (SPP) server and client for OpenWrt.

`bt-spp serve` binds an RFCOMM channel and bridges incoming SPP connections to
a login shell (on a pty) or to a TCP host:port; `bt-spp connect` bridges a
remote SPP device to a local shell or TCP listener.  The bridge target is
selected in /etc/config/bt-spp.

The SDP "Serial Port" record is registered with sdptool, which requires
bluetoothd to run with --compat so it exposes the local SDP socket.
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-O2 -Wall -Wextra \
		-o $(PKG_BUILD_DIR)/bt-spp \
		$(PKG_BUILD_DIR)/bt-spp.c
endef

define Package/bt-spp/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/bt-spp $(1)/usr/sbin/bt-spp
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/bt-spp.init $(1)/etc/init.d/bt-spp
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/bt-spp.config $(1)/etc/config/bt-spp
endef

$(eval $(call BuildPackage,bt-spp))

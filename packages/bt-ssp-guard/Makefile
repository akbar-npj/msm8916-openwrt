# SPDX-License-Identifier: BSD-3-Clause

include $(TOPDIR)/rules.mk

PKG_NAME:=bt-ssp-guard
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_LICENSE:=BSD-3-Clause
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(INCLUDE_DIR)/package.mk

define Package/bt-ssp-guard
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=WCNSS Bluetooth Secure Simple Pairing feature-bit guard
  DEPENDS:=+bluez-utils
endef

define Package/bt-ssp-guard/description
Ensures the WCNSS (WCN36xx) Bluetooth controller advertises Secure Simple
Pairing. The SSP LMP feature bit is byte 6, bit 3 of NVM tag 6; when it is
clear the controller answers "Read Simple Pairing Mode" with status 0x11
(Unsupported Feature) and BR/EDR pairing falls back to legacy PIN pairing,
which fails. The BT NVM is RAM-only, so this re-checks each boot and rewrites
tag 6 when the bit is missing. On a healthy boot (the firmware default) it is
a silent no-op. Also installs a bt-ssp-guard CLI (status / repair).
endef

define Package/bt-ssp-guard/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./files/bt-ssp-guard $(1)/usr/sbin/bt-ssp-guard
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/bt-ssp-guard.init $(1)/etc/init.d/bt-ssp-guard
endef

# File-only package: nothing to compile.
define Build/Compile
endef

$(eval $(call BuildPackage,bt-ssp-guard))

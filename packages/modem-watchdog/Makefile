# SPDX-License-Identifier: BSD-3-Clause

include $(TOPDIR)/rules.mk

PKG_NAME:=modem-watchdog
PKG_VERSION:=1.0
PKG_RELEASE:=1

PKG_LICENSE:=BSD-3-Clause
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(INCLUDE_DIR)/package.mk

define Package/$(PKG_NAME)
	SECTION:=net
	CATEGORY:=Network
	TITLE:=Qualcomm MSM8916 Modem Link Watchdog & Self-Healing Supervisor
endef

define Package/$(PKG_NAME)/description
	Monitors MSM8916 modem health, BAM-DMUX DMA state, and cellular data flow.
	Provides automated multi-stage progressive self-healing on data stalls or carrier disconnects.
endef

define Package/$(PKG_NAME)/conffiles
/etc/config/modem-watchdog
endef

define Build/Compile
endef

define Package/$(PKG_NAME)/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./files/modem-watchdog.sh $(1)/usr/sbin/modem-watchdog
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/modem-watchdog.init $(1)/etc/init.d/modem-watchdog
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/modem-watchdog.config $(1)/etc/config/modem-watchdog
endef

$(eval $(call BuildPackage,$(PKG_NAME)))

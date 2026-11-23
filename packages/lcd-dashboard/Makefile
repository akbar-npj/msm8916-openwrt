# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=lcd-dashboard
PKG_VERSION:=1.0
PKG_RELEASE:=2

PKG_LICENSE:=GPL-2.0-only
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(INCLUDE_DIR)/package.mk

define Package/lcd-dashboard
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Colourful LuCI-editable status dashboard for the ST7735R LCD
  DEPENDS:=+luci-base
endef


define Package/lcd-dashboard/description
  Draws an Android-style three-band status dashboard directly on the 128x128
  ST7735R SPI framebuffer: signal/SIM, Wi-Fi mode and client count, battery,
  data traffic, SMS/missed-call counters, SSID/key, IP and custom text.  Which
  source each zone shows, all colours and the custom lines are editable from
  LuCI (luci-app-lcd-dashboard) or /etc/config/lcd-dashboard.  Coexists with
  lcd-control: it never touches the backlight and skips drawing while blanked.
endef

define Package/lcd-dashboard/conffiles
/etc/config/lcd-dashboard
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-O2 -Wall -Wextra \
		-o $(PKG_BUILD_DIR)/lcd-dashboard \
		$(PKG_BUILD_DIR)/lcd-dashboard.c
endef

define Package/lcd-dashboard/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/lcd-dashboard $(1)/usr/sbin/lcd-dashboard
	$(INSTALL_BIN) ./files/lcd-dashboard-collect $(1)/usr/sbin/lcd-dashboard-collect
	$(INSTALL_BIN) ./files/lcd-dashboardd $(1)/usr/sbin/lcd-dashboardd
	$(INSTALL_BIN) ./files/lcd-dashboard-ctl $(1)/usr/sbin/lcd-dashboard-ctl
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/lcd-dashboard.init $(1)/etc/init.d/lcd-dashboard
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/lcd-dashboard.config $(1)/etc/config/lcd-dashboard
	$(INSTALL_DIR) $(1)/etc/uci-defaults
	$(INSTALL_BIN) ./files/95-lcd-dashboard $(1)/etc/uci-defaults/95-lcd-dashboard
	$(INSTALL_DIR) $(1)/etc/rc.d
	$(LN) ../init.d/lcd-dashboard $(1)/etc/rc.d/S96lcd-dashboard
endef

$(eval $(call BuildPackage,lcd-dashboard))

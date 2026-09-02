# SPDX-License-Identifier: BSD-3-Clause

include $(TOPDIR)/rules.mk

PKG_NAME:=qcom-time-daemon
PKG_VERSION:=1.0
PKG_RELEASE:=1

PKG_LICENSE:=BSD-3-Clause
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(INCLUDE_DIR)/package.mk

define Package/qcom-time-daemon
	SECTION:=utils
	CATEGORY:=Utilities
	TITLE:=Qualcomm QMI Time Synchronization Daemon
	DEPENDS:=+libqrtr +qrtr-utils
endef

define Package/qcom-time-daemon/description
Qualcomm QMI Time Synchronization Daemon for MSM8916 platforms.
Synchronizes host system time with modem ATS/TOD and provides periodic
keepalives to keep modem DRX sleep state machine stable.
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-Wall -Wextra \
		-o $(PKG_BUILD_DIR)/qcom-time-daemon \
		$(PKG_BUILD_DIR)/qcom-time-daemon.c \
		$(PKG_BUILD_DIR)/qmi_time.c \
		-lqrtr
endef

define Package/qcom-time-daemon/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/qcom-time-daemon $(1)/usr/sbin/
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/qcom-time-daemon.init $(1)/etc/init.d/qcom-time-daemon
endef

$(eval $(call BuildPackage,qcom-time-daemon))

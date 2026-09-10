include $(TOPDIR)/rules.mk

PKG_NAME:=qcom-time-daemon
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_SOURCE:=qcom-time-daemon.c
PKG_SOURCE_URL:=file://$(TOPDIR)/../packages/qcom-time-daemon/
PKG_SOURCE_PROTO:=none

PKG_BUILD_DEPENDS:=libqrtr
PKG_BUILD_PARALLEL:=1

PKG_LICENSE:=BSD-3-Clause
PKG_MAINTAINER:=Shaan Air <shaanair@example.com>

include $(INCLUDE_DIR)/package.mk

define Package/qcom-time-daemon
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Qualcomm QMI time‑synchronisation daemon
  DEPENDS:=+libqrtr
endef

define Package/qcom-time-daemon/description
  Daemon that synchronises the AP’s system clock with the modem using QRTR/QMI ATS messages.
  Includes a post‑SSR guard (30 s) to prevent the lte_ml1_common_timer.c:390 crash.
endef

define Build/Compile
	$(CC) $(TARGET_CFLAGS) -O2 -Wall -o $(PKG_BUILD_DIR)/qcom-time-daemon $(PKG_BUILD_DIR)/qcom-time-daemon.c -lqrtr -lrt -lpthread
endef

define Package/qcom-time-daemon/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/qcom-time-daemon $(1)/usr/sbin/
endef

# Ensure the OpenWrt 25.12 build system emits an APK
PKG_BUILD_FLAGS := no-check

$(eval $(call BuildPackage,qcom-time-daemon))

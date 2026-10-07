# SPDX-License-Identifier: BSD-3-Clause

include $(TOPDIR)/rules.mk

PKG_NAME:=ats-probe
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_LICENSE:=BSD-3-Clause
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(INCLUDE_DIR)/package.mk

define Package/ats-probe
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=QMI TIME ATS_RTC uptime probe (read-only)
  DEPENDS:=+libqrtr
endef

define Package/ats-probe/description
Reads the modem's own ATS_RTC uptime counter over QMI TIME (service 22) and
stamps every read with the AP's CLOCK_BOOTTIME, so a modem-side timestamp can
be correlated with an AP-side one. Sends only the 14-byte GENOFF_GET 0x0021;
it cannot write to the modem. Used to decide whether the ~902.7 s fatal is a
counter wrap, an always-on clock, or a condition met on a modem-local counter.
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-O2 -Wall -Wextra \
		-o $(PKG_BUILD_DIR)/ats-probe \
		$(PKG_BUILD_DIR)/ats-probe.c \
		$(PKG_BUILD_DIR)/qmi_time.c \
		-lqrtr -lrt
endef

define Package/ats-probe/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/ats-probe $(1)/usr/sbin/
endef

$(eval $(call BuildPackage,ats-probe))

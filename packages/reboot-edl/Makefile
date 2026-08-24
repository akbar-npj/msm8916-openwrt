# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=reboot-edl
PKG_RELEASE:=1

PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Build/Prepare
	$(CP) ./src/. $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/reboot-edl $(PKG_BUILD_DIR)/reboot-edl.c
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/reboot-dload $(PKG_BUILD_DIR)/reboot-dload.c
endef

define Package/reboot-edl
	SECTION:=utils
	CATEGORY:=Utilities
	TITLE:=Reboot a Qualcomm MSM device into EDL mode
endef

define Package/reboot-edl/description
Request RESTART2("edl") or RESTART2("dload") reboot, for targets whose kernel supports EDL/download mode.
endef

define Package/reboot-edl/install
	$(INSTALL_DIR) $(1)/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/reboot-edl $(1)/sbin/reboot-edl
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/reboot-dload $(1)/sbin/reboot-dload
endef

$(eval $(call BuildPackage,reboot-edl))

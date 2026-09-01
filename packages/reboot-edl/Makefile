# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=reboot-edl
PKG_RELEASE:=2

PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/reboot-mode-raw $(PKG_BUILD_DIR)/reboot-mode-raw.c
endef

define Package/reboot-edl
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Safely reboot Qualcomm MSM device into EDL / DLOAD / Fastboot modes
  DEPENDS:=+busybox
endef

define Package/reboot-edl/description
Safely quiesces background services, unmounts/remounts filesystems read-only,
and reboots Qualcomm MSM8916 devices into EDL (9008), DLOAD (9006), bootloader,
or recovery modes.
endef

define Package/reboot-edl/install
	$(INSTALL_DIR) $(1)/sbin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/reboot-mode-raw $(1)/sbin/reboot-mode-raw
	$(INSTALL_BIN) ./files/reboot-mode.sh $(1)/sbin/reboot-mode
	ln -sf reboot-mode $(1)/sbin/reboot-edl
	ln -sf reboot-mode $(1)/sbin/reboot-dload
	ln -sf reboot-mode $(1)/sbin/reboot-bootloader
	ln -sf reboot-mode $(1)/sbin/reboot-fastboot
	ln -sf reboot-mode $(1)/sbin/reboot-recovery
endef

$(eval $(call BuildPackage,reboot-edl))

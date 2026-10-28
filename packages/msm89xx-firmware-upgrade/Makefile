# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=msm89xx-firmware-upgrade
PKG_VERSION:=1.0
PKG_RELEASE:=4

PKG_LICENSE:=GPL-2.0-only
PKG_MAINTAINER:=akbar-npj

include $(INCLUDE_DIR)/package.mk

define Package/$(PKG_NAME)
	SECTION:=utils
	CATEGORY:=Utilities
	TITLE:=Check for and apply published firmware upgrades
	DEPENDS:=+jsonfilter
	PKGARCH:=all
endef

define Package/$(PKG_NAME)/description
	Consults the firmware.json manifest the release pipeline publishes next to the
	images, reports when a newer image exists for this board, and applies it via
	sysupgrade (config preserved).  Companion to luci-app-update-notify, which
	surfaces the result; the LuCI half is added separately.

	The backend (firmware-upgrade) is intentionally CLI-only and testable without
	the UI: 'firmware-upgrade check' prints availability as JSON, 'download' fetches
	and sha256-verifies the image, and 'upgrade' applies it.
endef

# Files-only package: nothing to compile.  Build/Compile must be stubbed or the
# build falls through to a default 'make' in an empty build dir and fails.
define Build/Compile
endef

define Package/$(PKG_NAME)/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./root/usr/sbin/firmware-upgrade $(1)/usr/sbin/firmware-upgrade
	$(INSTALL_DIR) $(1)/usr/share/firmware-upgrade
	$(INSTALL_DATA) ./root/usr/share/firmware-upgrade/firmware.schema.json $(1)/usr/share/firmware-upgrade/firmware.schema.json
	$(INSTALL_DATA) ./root/usr/share/firmware-upgrade/firmware.example.json $(1)/usr/share/firmware-upgrade/firmware.example.json
endef

$(eval $(call BuildPackage,$(PKG_NAME)))

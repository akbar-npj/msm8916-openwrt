# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=msm89xx-scripts
PKG_VERSION:=1.0
PKG_RELEASE:=7

PKG_LICENSE:=GPL-2.0-only
PKG_MAINTAINER:=akbar-npj

include $(INCLUDE_DIR)/package.mk

define Package/$(PKG_NAME)
	SECTION:=utils
	CATEGORY:=Utilities
	TITLE:=LED monitors and station-mode helpers for the msm89xx boards
	DEPENDS:=+procd
	PKGARCH:=all
endef

define Package/$(PKG_NAME)/description
	The userspace half of the msm89xx board support: the modem and Wi-Fi LED
	monitors with their procd services, the boot blink, and the Wi-Fi station
	(repeater) helpers that luci-app-wifi-sta drives.

	These used to be shipped inside the target's base-files, which made them
	unreachable without a full firmware flash.  As a package they can be
	rebuilt and pushed through the signed package feed (PKG_RELEASE bump), so
	an LED or station fix no longer costs a sysupgrade.

	Boot-critical pieces deliberately stay in base-files: /etc/diag.sh, the
	/lib/preinit hooks and /lib/batteryless.sh, because they must exist before
	any package could be installed and are owned by the stock base-files.
endef

# Files-only package: nothing to configure or compile.  Build/Compile must be
# stubbed or the build system falls through to a default `make -C
# $(PKG_BUILD_DIR)`, which fails with "No targets specified and no makefile
# found" on a clean build.  (Build/Prepare is left at its default, which only
# creates the build directory.)
define Build/Compile
endef

define Package/$(PKG_NAME)/install
	$(INSTALL_DIR) $(1)
	$(CP) ./root/* $(1)/
endef

$(eval $(call BuildPackage,$(PKG_NAME)))

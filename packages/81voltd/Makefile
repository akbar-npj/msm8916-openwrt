# SPDX-License-Identifier: GPL-2.0-or-later
include $(TOPDIR)/rules.mk

PKG_NAME:=81voltd
PKG_VERSION:=1.2.0
PKG_RELEASE:=1
PKG_SOURCE_PROTO:=git
PKG_SOURCE_URL:=https://gitlab.nura.eco/modem/81voltd.git
PKG_SOURCE_VERSION:=a7794dd6c8ac216a97dc5a931edab2dfc46eca2a
PKG_MIRROR_HASH:=2b82069f840019574003f20d502d84091147ab71623974ba1316382cb3cc83ea
PKG_LICENSE:=GPL-2.0-or-later
PKG_LICENSE_FILES:=LICENSE
PKG_BUILD_PARALLEL:=1

include $(INCLUDE_DIR)/package.mk
include $(INCLUDE_DIR)/meson.mk

define Package/81voltd
  SECTION:=net
  CATEGORY:=Network
  TITLE:=Qualcomm IMS data service
  DEPENDS:=+modemmanager +libqmi +libqrtr +glib2
endef

define Package/81voltd/description
  Handles modem IMS data connection requests over QRTR using ModemManager.
  Includes support for the MSM8916 qcom-soc ModemManager plugin.
  Does not provide carrier provisioning or voice audio routing.
endef

define Package/81voltd/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/81voltd $(1)/usr/sbin/
endef

$(eval $(call BuildPackage,81voltd))

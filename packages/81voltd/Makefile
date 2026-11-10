# SPDX-License-Identifier: GPL-2.0-or-later
#
# 81voltd -- QMI IMS Data service (IMSD, service 770) over QRTR.
#
# In "hybrid" IMS mode (ims_hybrid_enable=1) the modem does NOT register IMS
# on its own. It asks the AP, over QMI service 770, to bring up the `ims` PDN
# (IMSD request 0x20). 81voltd creates that bearer through ModemManager and
# reports the assigned IP back to the modem (IMSD indication 0x20), after which
# the modem's own IMS stack can SIP-REGISTER for VoLTE.
#
# Upstream: https://gitlab.nura.eco/modem/81voltd  (postmarketOS "modem" group;
#           previously gitlab.postmarketos.org/modem/81voltd and the mirror
#           https://gitlab.com/flamingradian/81voltd, which is v1.0.0 and
#           simpler -- no libqmi, uses find_library('qrtr') instead of a .pc).

include $(TOPDIR)/rules.mk

PKG_NAME:=81voltd
PKG_VERSION:=1.2.0
PKG_RELEASE:=1

PKG_SOURCE_PROTO:=git
PKG_SOURCE_URL:=https://gitlab.nura.eco/modem/81voltd.git
PKG_SOURCE_VERSION:=a7794dd6c8ac216a97dc5a931edab2dfc46eca2a
PKG_MIRROR_HASH:=skip

PKG_LICENSE:=GPL-2.0-or-later
PKG_LICENSE_FILES:=LICENSE
PKG_MAINTAINER:=OpenWrt Community

PKG_BUILD_PARALLEL:=1
PKG_INSTALL:=1

include $(INCLUDE_DIR)/package.mk
include $(INCLUDE_DIR)/meson.mk

define Package/81voltd
	SECTION:=net
	CATEGORY:=Network
	SUBMENU:=WWAN
	TITLE:=QMI IMS Data service (IMSD/770) for VoLTE
	DEPENDS:=+libqrtr +libqmi +modemmanager +glib2
endef

define Package/81voltd/description
 81voltd implements the QMI IMS Data service (service 770) over QRTR.
 In hybrid IMS mode the modem asks the AP to bring up the "ims" PDN; 81voltd
 creates the bearer through ModemManager and reports the assigned IP address
 back to the modem, which then registers with the IMS core for VoLTE.
endef

define Package/81voltd/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/81voltd $(1)/usr/bin/81voltd
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/81voltd.init $(1)/etc/init.d/81voltd
endef

$(eval $(call BuildPackage,81voltd))

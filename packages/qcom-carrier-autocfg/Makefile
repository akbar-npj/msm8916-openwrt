include $(TOPDIR)/rules.mk

PKG_NAME:=qcom-carrier-autocfg
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_MAINTAINER:=OpenWrt Community
PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Package/qcom-carrier-autocfg
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=WWAN
  TITLE:=Qualcomm MSM8916 SIM Carrier Auto-Provisioning Engine
  DEPENDS:=+modemmanager +libqmi-utils
  PKGARCH:=all
endef

define Package/qcom-carrier-autocfg/description
  Automatic SIM detection, carrier profile matching, APN provisioning,
  and dynamic connection manager for Qualcomm MSM8916 Snapdragon modem devices.
endef

define Build/Compile
endef

define Package/qcom-carrier-autocfg/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./files/qcom-carrier-autocfg.sh $(1)/usr/sbin/qcom-carrier-autocfg
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/qcom-carrier-autocfg.init $(1)/etc/init.d/qcom-carrier-autocfg
	$(INSTALL_DIR) $(1)/usr/share/qcom-carrier-autocfg/mcfg
	$(CP) ./files/mcfg/* $(1)/usr/share/qcom-carrier-autocfg/mcfg/
endef

$(eval $(call BuildPackage,qcom-carrier-autocfg))

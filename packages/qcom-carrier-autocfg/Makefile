include $(TOPDIR)/rules.mk

PKG_NAME:=qcom-carrier-autocfg
PKG_VERSION:=1.0.0
PKG_RELEASE:=13

PKG_MAINTAINER:=OpenWrt Community
PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

# MCFG blobs have ELF headers but must reach the modem byte for byte.
RSTRIP:=:

define Package/qcom-carrier-autocfg
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=WWAN
  TITLE:=Qualcomm MSM8916 SIM Carrier Auto-Provisioning Engine
  DEPENDS:=+modemmanager +libqmi +qmi-utils +coreutils-sha1sum
  PKGARCH:=all
endef

define Package/qcom-carrier-autocfg/description
  Automatic SIM detection, carrier profile matching, APN provisioning,
  and dynamic connection manager for Qualcomm MSM8916 Snapdragon modem devices.
endef

define Package/qcom-carrier-autocfg/conffiles
/etc/config/qcom-carrier-autocfg
/etc/qcom-carrier-autocfg/custom-apns.tsv
endef

define Build/Compile
endef

define Package/qcom-carrier-autocfg/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./files/qcom-carrier-autocfg.sh $(1)/usr/sbin/qcom-carrier-autocfg
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/qcom-carrier-autocfg.init $(1)/etc/init.d/qcom-carrier-autocfg
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/qcom-carrier-autocfg.config $(1)/etc/config/qcom-carrier-autocfg
	$(INSTALL_DIR) $(1)/etc/qcom-carrier-autocfg
	$(INSTALL_DATA) ./files/custom-apns.tsv $(1)/etc/qcom-carrier-autocfg/custom-apns.tsv
	$(INSTALL_DIR) $(1)/usr/share/qcom-carrier-autocfg
	$(INSTALL_DATA) ./files/apns.tsv $(1)/usr/share/qcom-carrier-autocfg/apns.tsv
	$(INSTALL_DIR) $(1)/usr/share/qcom-carrier-autocfg/mcfg
	$(CP) ./files/mcfg/* $(1)/usr/share/qcom-carrier-autocfg/mcfg/
	# Keep the optional IPv4-only IMS profile outside the carrier MCFG tree.
	$(INSTALL_DIR) $(1)/usr/share/qcom-carrier-autocfg/mcfg-ipv4
	$(INSTALL_DATA) ./files/mcfg-ipv4/row-ims-ipv4.mbn $(1)/usr/share/qcom-carrier-autocfg/mcfg-ipv4/
endef

$(eval $(call BuildPackage,qcom-carrier-autocfg))

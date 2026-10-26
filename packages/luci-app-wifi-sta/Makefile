include $(TOPDIR)/rules.mk

LUCI_TITLE:=LuCI support for Station Mode (concurrent AP+STA)
LUCI_DESCRIPTION:=Configure the late-brought-up station interface of the wcn36xx radio without touching the wireless config
LUCI_DEPENDS:=+luci-base
LUCI_PKGARCH:=all

PKG_VERSION:=1.0
PKG_RELEASE:=4
PKG_LICENSE:=GPL-2.0

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

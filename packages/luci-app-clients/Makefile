include $(TOPDIR)/rules.mk

LUCI_TITLE:=LuCI connected clients
LUCI_DESCRIPTION:=Read-only list of associated Wi-Fi clients with signal, tx/rx rate, connected time and power-save state.
LUCI_PKGARCH:=all
LUCI_DEPENDS:=+iw

PKG_VERSION:=1.0
PKG_RELEASE:=1
PKG_MAINTAINER:=akbar-npj
PKG_LICENSE:=GPL-2.0

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

include $(TOPDIR)/rules.mk

LUCI_TITLE:=Dashboard notice for available package updates
LUCI_DESCRIPTION:=Runs `apk update` in the background and puts a banner on the dashboard when packages can be upgraded, so a device on the released feed does not silently fall behind. The banner renders nothing when everything is current.
LUCI_DEPENDS:=+luci-base +rpcd-mod-file
LUCI_PKGARCH:=all

PKG_VERSION:=1.0
PKG_RELEASE:=3
PKG_MAINTAINER:=akbar-npj
PKG_LICENSE:=GPL-2.0-only

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

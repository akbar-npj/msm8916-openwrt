# SPDX-License-Identifier: GPL-2.0-only
include $(TOPDIR)/rules.mk

LUCI_TITLE:=LuCI support for firmware upgrade
LUCI_DESCRIPTION:=Check for and apply published firmware upgrades from the LuCI web UI
LUCI_DEPENDS:=+luci-base +rpcd-mod-file +msm89xx-firmware-upgrade
LUCI_PKGARCH:=all

PKG_VERSION:=1.0
PKG_RELEASE:=2
PKG_LICENSE:=GPL-2.0
PKG_MAINTAINER:=akbar-npj

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

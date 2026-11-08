# SPDX-License-Identifier: GPL-2.0-only
include $(TOPDIR)/rules.mk

LUCI_TITLE:=LuCI LED role and pattern setup
LUCI_DESCRIPTION:=Choose which physical lamp is Wi-Fi, LTE, fault or battery, blink a lamp to identify it, and tune the per-scenario blink patterns.
LUCI_DEPENDS:=+luci-base +rpcd-mod-file +msm89xx-scripts
LUCI_PKGARCH:=all

PKG_VERSION:=1.0
PKG_RELEASE:=4
PKG_LICENSE:=GPL-2.0-only
PKG_MAINTAINER:=akbar-npj

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

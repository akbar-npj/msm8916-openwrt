# SPDX-License-Identifier: GPL-2.0-only
include $(TOPDIR)/rules.mk

LUCI_TITLE:=LuCI LCD dashboard editor
LUCI_DESCRIPTION:=Customise the colourful ST7735R LCD dashboard: pick the source for each band, every colour, custom text and notification popups.
LUCI_DEPENDS:=+luci-base +lcd-dashboard
LUCI_PKGARCH:=all

PKG_VERSION:=1.0
PKG_RELEASE:=1
PKG_LICENSE:=GPL-2.0-only
PKG_MAINTAINER:=OpenWrt MSM8916 Team

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

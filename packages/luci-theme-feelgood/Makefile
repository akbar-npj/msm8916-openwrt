#
# Copyright (C) 2008-2026 The LuCI Team <luci@lists.subsignal.org>
#
# This is free software, licensed under the Apache License, Version 2.0 .
#

include $(TOPDIR)/rules.mk

LUCI_TITLE:=Feelgood Theme
LUCI_DEPENDS:=+luci-base
PKG_LICENSE:=Apache-2.0

CONFIG_LUCI_UTMIN:=
LUCI_MINIFY_UT:=0

define Package/luci-theme-feelgood/postrm
#!/bin/sh
[ -n "$${IPKG_INSTROOT}" ] || {
	uci -q delete luci.themes.Feelgood
	uci -q delete luci.themes.FeelgoodHighContrast
	uci -q delete luci.theme_feelgood
	uci commit luci
}
endef

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

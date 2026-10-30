#
# Copyright (C) 2008-2026 The LuCI Team <luci@lists.subsignal.org>
#
# This is free software, licensed under the Apache License, Version 2.0 .
#

include $(TOPDIR)/rules.mk

LUCI_TITLE:=Feelgood Theme
LUCI_DEPENDS:=+luci-base
PKG_VERSION:=1
PKG_RELEASE:=25
PKG_LICENSE:=Apache-2.0

CONFIG_LUCI_UTMIN:=
LUCI_MINIFY_UT:=0
# csstidy mangles the login-page cascade (it drops/mis-orders the
# `body.login .main-left{display:none}` and `.main-left .brand-logo{28px}`
# rules, leaving the sidebar LoginIcon.png at its natural 1016px and
# covering the login form). Ship the unminified CSS so the cascade is exact.
LUCI_MINIFY_CSS:=0

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

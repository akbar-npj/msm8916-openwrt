#
# Battery status, charge limit and the battery LED pair.
#
# Only the boards that actually carry a pack install this: the MF800B and the
# HM-MIFIB-V12 both define a 'simple-battery' node wired to pm8916_bms /
# pm8916_lbc in their device tree.  The other boards have no battery at all, so
# shipping the scripts there only put a dead LuCI page in the menu.
#

include $(TOPDIR)/rules.mk

LUCI_TITLE:=Battery status, charge limit and battery LED
LUCI_DESCRIPTION:=LuCI page and services for the PM8916 BMS/LBC: capacity, temperature and charge status, a configurable charge limit, and the battery LED monitor.
LUCI_PKGARCH:=all
LUCI_DEPENDS:=+luci-lua-runtime

PKG_VERSION:=1.0
PKG_RELEASE:=2
PKG_LICENSE:=GPL-2.0-only

define Package/luci-app-battery/conffiles
/etc/config/battery
endef

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

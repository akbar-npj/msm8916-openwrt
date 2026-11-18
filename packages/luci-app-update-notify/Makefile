include $(TOPDIR)/rules.mk

LUCI_TITLE:=Dashboard notice for available package and firmware updates
LUCI_DESCRIPTION:=Runs `apk update` in the background and puts a banner on the dashboard when packages can be upgraded, so a device on the released feed does not silently fall behind. Also shows a firmware-update banner (when msm89xx-firmware-upgrade is installed) with a one-click upgrade button. Banners render nothing when everything is current.
LUCI_DEPENDS:=+luci-base +rpcd-mod-file
LUCI_PKGARCH:=all

PKG_VERSION:=1.0
PKG_RELEASE:=16
PKG_MAINTAINER:=akbar-npj
PKG_LICENSE:=GPL-2.0-only

# The dismissal lives here; without this an upgrade would ship the default
# dismissed_count=0 over it and the banner would come back.
define Package/luci-app-update-notify/conffiles
/etc/config/update-notify
endef

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature

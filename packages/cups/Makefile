# SPDX-License-Identifier: GPL-2.0-only
#
# OpenWrt Package Makefile for CUPS Network Print Server
#

include $(TOPDIR)/rules.mk

PKG_NAME:=cups
PKG_VERSION:=2.2.12
PKG_RELEASE:=2

PKG_SOURCE:=$(PKG_NAME)-$(PKG_VERSION)-source.tar.gz
PKG_SOURCE_URL:=https://github.com/apple/cups/releases/download/v$(PKG_VERSION)/
PKG_HASH:=0f61ab449e4748a24c6ab355b481ff7691247a140d327b2b7526fce34b7f9aa8
PKG_MAINTAINER:=Luiz Angelo Daros de Luca <luizluca@gmail.com>
PKG_LICENSE:=GPL-2.0
PKG_LICENSE_FILES:=LICENSE.txt
PKG_CPE_ID:=cpe:/a:apple:cups

PKG_INSTALL:=1
PKG_BUILD_PARALLEL:=1
PKG_BUILD_DEPENDS:=zlib libpng libjpeg-turbo libusb

include $(INCLUDE_DIR)/package.mk

define Package/cups/Default
  URL:=http://www.cups.org/
  SUBMENU:=Printing
endef

define Package/cups-daemon
$(call Package/cups/Default)
  SECTION:=net
  CATEGORY:=Network
  DEPENDS:=+libcups +libpthread +zlib +libpng +libjpeg +libusb-1.0 +kmod-usb-printer
  TITLE:=Common UNIX Printing System - Server Daemon (cupsd)
endef

define Package/cups-daemon/description
  Common UNIX Printing System - Full Network Print Server Daemon (cupsd)
  with IPP, AirPrint, raw socket, and web administration on port 631.
endef

define Package/cups-daemon/conffiles
/etc/cups/cupsd.conf
/etc/cups/printers.conf
endef

define Package/cups-client
$(call Package/cups/Default)
  SECTION:=utils
  CATEGORY:=Utilities
  DEPENDS:=+libcups
  TITLE:=Common UNIX Printing System - Client Utilities (lp, lpadmin, lpstat)
endef

define Package/libcups
$(call Package/cups/Default)
  SECTION:=libs
  CATEGORY:=Libraries
  DEPENDS:=+zlib +libpthread +libpng +libjpeg +libusb-1.0
  TITLE:=Common UNIX Printing System - Core library
endef

CONFIGURE_ARGS+= \
	--with-cups-user="nobody" \
	--with-cups-group="nogroup" \
	--with-components="all" \
	--with-pdftops="none" \
	--without-perl \
	--without-python \
	--without-php \
	--enable-shared \
	--enable-image \
	--enable-libusb \
	--disable-avahi \
	--disable-dnssd \
	--disable-acl \
	--disable-dbus \
	--disable-launchd \
	--disable-ldap \
	--disable-pam \
	--disable-slp \
	--disable-gnutls \
	--disable-openssl \
	--disable-cdsassl \
	--disable-ssl \
	--disable-gssapi \
	--disable-tiff \
	UNAME="Linux" \
	LIBS="$(TARGET_LDFLAGS) -lz -lpng -ljpeg"

MAKE_INSTALL_FLAGS += \
	DSTROOT="$(PKG_INSTALL_DIR)" \
	BUILDROOT="$(PKG_INSTALL_DIR)" \
	INSTALL_USER="" \
	INSTALL_GROUP=""

define Build/InstallDev
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/cups-config $(1)/usr/bin/
	$(INSTALL_DIR) $(2)/bin
	$(LN) $(STAGING_DIR)/usr/bin/cups-config $(2)/bin
	$(INSTALL_DIR) $(1)/usr/include
	$(CP) $(PKG_INSTALL_DIR)/usr/include/cups $(1)/usr/include/
	$(INSTALL_DIR) $(1)/usr/lib
	$(CP) $(PKG_INSTALL_DIR)/usr/lib*/libcups*.so* $(1)/usr/lib/
endef

define Package/libcups/install
	$(INSTALL_DIR) $(1)/usr/lib
	$(CP) $(PKG_INSTALL_DIR)/usr/lib*/libcups*.so* $(1)/usr/lib/
endef

define Package/cups-daemon/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/sbin/cupsd $(1)/usr/sbin/
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/sbin/cupsfilter $(1)/usr/sbin/
	$(INSTALL_DIR) $(1)/usr/lib/cups/backend
	$(CP) $(PKG_INSTALL_DIR)/usr/lib/cups/backend/* $(1)/usr/lib/cups/backend/ 2>/dev/null || true
	$(INSTALL_DIR) $(1)/usr/lib/cups/filter
	$(CP) $(PKG_INSTALL_DIR)/usr/lib/cups/filter/* $(1)/usr/lib/cups/filter/ 2>/dev/null || true
	$(INSTALL_DIR) $(1)/usr/share/cups/templates
	$(CP) $(PKG_INSTALL_DIR)/usr/share/cups/templates/* $(1)/usr/share/cups/templates/ 2>/dev/null || true
	$(INSTALL_DIR) $(1)/usr/share/cups/mime
	$(CP) $(PKG_INSTALL_DIR)/usr/share/cups/mime/* $(1)/usr/share/cups/mime/ 2>/dev/null || true
	$(INSTALL_DIR) $(1)/etc/cups
	$(CP) $(PKG_INSTALL_DIR)/etc/cups/* $(1)/etc/cups/ 2>/dev/null || true
	$(INSTALL_CONF) ./files/cupsd.conf $(1)/etc/cups/cupsd.conf
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/cupsd.init $(1)/etc/init.d/cupsd
endef

define Package/cups-client/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/lp* $(1)/usr/bin/ 2>/dev/null || true
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/cancel $(1)/usr/bin/ 2>/dev/null || true
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/sbin/lp* $(1)/usr/sbin/ 2>/dev/null || true
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/sbin/cupsenable $(1)/usr/sbin/ 2>/dev/null || true
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/sbin/cupsdisable $(1)/usr/sbin/ 2>/dev/null || true
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/sbin/cupstestppd $(1)/usr/sbin/ 2>/dev/null || true
endef

$(eval $(call BuildPackage,libcups))
$(eval $(call BuildPackage,cups-daemon))
$(eval $(call BuildPackage,cups-client))

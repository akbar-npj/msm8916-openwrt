# SPDX-License-Identifier: GPL-2.0-or-later
#
# qmi-raw -- send an arbitrary QMI request and print the decoded response.
#
# Development tool.  Stock libqmi only implements the messages it knows about;
# the OpenIMSd (postmarketOS) VoLTE stack relies on messages that libqmi 1.36
# does not have -- most importantly IMS (0x12) 0x008f "Set IMS Services Enabled
# Setting", which is what actually turns VoLTE on in the modem.  qmi-raw builds
# the QMI payload by hand and lets libqmi do the QMUX framing, so any message
# can be tried without patching libqmi.

include $(TOPDIR)/rules.mk

PKG_NAME:=qmi-raw
PKG_VERSION:=1.0
PKG_RELEASE:=4

PKG_LICENSE:=GPL-2.0-or-later
PKG_MAINTAINER:=OpenWrt Community

include $(INCLUDE_DIR)/package.mk

define Package/qmi-raw
	SECTION:=utils
	CATEGORY:=Utilities
	TITLE:=Send arbitrary raw QMI requests (development tool)
	DEPENDS:=+libqmi +glib2
endef

define Package/qmi-raw/description
 qmi-raw sends an arbitrary QMI request -- service id, message id and a list
 of raw TLVs -- to a QMI device and prints the response.  It exists because
 several interesting messages (IMS 0x008f Set IMS Services Enabled Setting,
 IMSDCM 0x0020 PDP Activate Request) are not implemented in libqmi 1.36.
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CPPFLAGS) $(TARGET_CFLAGS) \
		-I$(STAGING_DIR)/usr/include/libqmi-glib \
		-I$(STAGING_DIR)/usr/include/libqrtr-glib \
		-I$(STAGING_DIR)/usr/include/glib-2.0 \
		-I$(STAGING_DIR)/usr/lib/glib-2.0/include \
		-o $(PKG_BUILD_DIR)/qmi-raw $(PKG_BUILD_DIR)/qmi-raw.c \
		$(TARGET_LDFLAGS) -L$(STAGING_DIR)/usr/lib \
		-lqmi-glib -lqrtr-glib -lgio-2.0 -lgobject-2.0 -lglib-2.0
endef

define Package/qmi-raw/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/qmi-raw $(1)/usr/bin/qmi-raw
endef

$(eval $(call BuildPackage,qmi-raw))

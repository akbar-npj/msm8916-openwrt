# SPDX-License-Identifier: BSD-3-Clause
include $(TOPDIR)/rules.mk

PKG_NAME:=qcom-ims
PKG_VERSION:=0.1.0
PKG_RELEASE:=4
PKG_LICENSE:=BSD-3-Clause

include $(INCLUDE_DIR)/package.mk

define Package/qcom-ims
  SECTION:=net
  CATEGORY:=Network
  TITLE:=Experimental MSM8916 IMS registration support
  DEPENDS:=+81voltd +libqrtr +libqmi +qmi-utils
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) -Wall -Wextra $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/qcom-ims-settings \
		$(PKG_BUILD_DIR)/qcom-ims-settings.c -lqrtr
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_CPPFLAGS) -Wall -Wextra \
		`$(PKG_CONFIG) --cflags qmi-glib` $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/qcom-ims-ipv4-pco \
		$(PKG_BUILD_DIR)/qcom-ims-ipv4-pco.c \
		`$(PKG_CONFIG) --libs qmi-glib`
endef

define Package/qcom-ims/conffiles
/etc/config/qcom-ims
endef

define Package/qcom-ims/install
	$(INSTALL_DIR) $(1)/usr/sbin $(1)/etc/config $(1)/etc/init.d
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/qcom-ims-settings $(1)/usr/sbin/
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/qcom-ims-ipv4-pco $(1)/usr/sbin/
	$(INSTALL_CONF) ./files/qcom-ims.config $(1)/etc/config/qcom-ims
	$(INSTALL_BIN) ./files/qcom-ims.init $(1)/etc/init.d/qcom-ims
endef

$(eval $(call BuildPackage,qcom-ims))

# SPDX-License-Identifier: BSD-3-Clause
include $(TOPDIR)/rules.mk

PKG_NAME:=auto-answer
PKG_VERSION:=0.1.0
PKG_RELEASE:=6
PKG_LICENSE:=BSD-3-Clause

include $(INCLUDE_DIR)/package.mk

define Package/auto-answer
  SECTION:=net
  CATEGORY:=Network
  TITLE:=Automatic VoLTE call answering for JZ0145
  CONFLICTS:=voice-greeting
  DEPENDS:=@TARGET_msm89xx +qcom-ims +alsa-utils +alsa-lib +opus-tools +kmod-sound-msm8916-voice +coreutils-timeout
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) -Wall -Wextra $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/auto-answer-hold \
		$(PKG_BUILD_DIR)/auto-answer-hold.c -lasound
endef

define Package/auto-answer/conffiles
/etc/config/auto-answer
endef

define Package/auto-answer/install
	$(INSTALL_DIR) $(1)/usr/sbin $(1)/usr/libexec $(1)/etc/config $(1)/etc/init.d $(1)/etc/auto-answer
	$(INSTALL_BIN) ./files/auto-answer $(1)/usr/sbin/
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/auto-answer-hold $(1)/usr/libexec/
	$(INSTALL_CONF) ./files/auto-answer.config $(1)/etc/config/auto-answer
	$(INSTALL_BIN) ./files/auto-answer.init $(1)/etc/init.d/auto-answer
	$(INSTALL_DATA) ./files/greeting.opus $(1)/etc/auto-answer/
endef

$(eval $(call BuildPackage,auto-answer))

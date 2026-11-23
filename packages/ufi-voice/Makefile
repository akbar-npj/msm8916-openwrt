# SPDX-License-Identifier: GPL-2.0-only
#
# ufi-voice: VoLTE (IMS data service for the modem) and call audio for the
# UFI003: Bluetooth headset (HFP AG + internal BT SCO) and local Asterisk
# phones (AudioSocket bridge).
#
# Provenance: this package and the kernel voice patch it depends on
# (msm89xx/patches/873-*) come from @isprogrammer's voice_calls_new branch,
# https://github.com/isprogrammer/msm8916-openwrt/tree/voice_calls_new --
# imsdcm is his file unchanged, ufi-voiced is his with our call-handling
# additions. The q6voice driver under it is Stephan Gerhold's, from
# msm8916-mainline. File-by-file record: Docs/Modem Stability/221_VOLTE_GUIDE.md §17.

include $(TOPDIR)/rules.mk

PKG_NAME:=ufi-voice
PKG_VERSION:=1.0
PKG_RELEASE:=26
PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Package/ufi-voice
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=Telephony
  TITLE:=VoLTE and call audio for MSM8916 sticks
  DEPENDS:=@TARGET_msm89xx +kmod-sound-msm8916-voice +kmod-sound-core +alsa-lib +alsa-utils \
	+libdbus +python3-light +qmi-utils +modemmanager +diag-efs +diag-logtool \
	+libopenssl
endef

define Package/ufi-voice/description
 imsdcm: the AP-side IMS data connection service the modem's IMS stack uses
 in hybrid mode (brings up the IMS PDN through ModemManager).
 ufi-voiced: connects modem calls to a Bluetooth headset (hfp-ag) and/or
 local SIP phones through Asterisk (ufi-voice-asterisk).
 voicectl / hostless: DSP voice path routing. voicebridge: low-latency
 in-call audio for the Asterisk phones. volte-setup: modem EFS items.
 ims-profile-setup / ims-profile-watch: create the "ims" WDS profile at every
 modem boot, AP boot and runtime restart alike.
endef

define Package/ufi-voice/conffiles
/etc/config/ufivoice
endef

define Package/ufi-voice-asterisk
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=Telephony
  TITLE:=Asterisk setup for ufi-voice (SIP phones <-> mobile calls)
  DEPENDS:=+ufi-voice +asterisk +asterisk-pjsip +asterisk-res-rtp-asterisk \
	+asterisk-bridge-simple +asterisk-codec-ulaw +asterisk-codec-alaw \
	+asterisk-app-audiosocket +asterisk-res-audiosocket +asterisk-func-shell \
	+asterisk-res-timing-timerfd
endef

define Package/ufi-voice-asterisk/description
 Writes an Asterisk configuration once (SIP phones 100/101, outgoing calls
 through the mobile network, incoming calls ring the phones) and enables it.
endef

TARGET_CFLAGS += -Wall -Wextra

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) -o $(PKG_BUILD_DIR)/hostless \
		./src/hostless.c -lasound
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) -o $(PKG_BUILD_DIR)/voicebridge \
		./src/voicebridge.c -lasound -lm
	$(TARGET_CC) $(TARGET_CFLAGS) \
		-I$(STAGING_DIR)/usr/include/dbus-1.0 \
		-I$(STAGING_DIR)/usr/lib/dbus-1.0/include \
		$(TARGET_LDFLAGS) -o $(PKG_BUILD_DIR)/hfp-ag ./src/hfp-ag.c -ldbus-1
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) -o $(PKG_BUILD_DIR)/ntfy-jwe \
		./src/ntfy-jwe.c -lcrypto
endef

define Package/ufi-voice/install
	$(INSTALL_DIR) $(1)/usr/bin $(1)/usr/sbin $(1)/etc/init.d $(1)/etc/config
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/hostless $(PKG_BUILD_DIR)/voicebridge $(1)/usr/bin/
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/ntfy-jwe $(1)/usr/bin/
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/hfp-ag $(1)/usr/sbin/
	$(INSTALL_BIN) ./files/imsdcm ./files/ufi-voiced ./files/voicectl \
		./files/volte-setup ./files/ufi-voice-dial ./files/ufi-voice-apply \
		./files/ufi-voice-status ./files/ims-profile-setup \
		./files/ims-profile-watch ./files/ufi-voice-notify $(1)/usr/sbin/
	$(INSTALL_BIN) ./files/imsdcm.init $(1)/etc/init.d/imsdcm
	$(INSTALL_BIN) ./files/ims-profile.init $(1)/etc/init.d/ims-profile
	$(INSTALL_BIN) ./files/ims-profile-watch.init $(1)/etc/init.d/ims-profile-watch
	$(INSTALL_BIN) ./files/hfp-ag.init $(1)/etc/init.d/hfp-ag
	$(INSTALL_BIN) ./files/ufi-voiced.init $(1)/etc/init.d/ufi-voiced
	$(INSTALL_DIR) $(1)/etc/rc.d
	$(LN) ../init.d/imsdcm $(1)/etc/rc.d/S61imsdcm
	$(LN) ../init.d/ims-profile $(1)/etc/rc.d/S94ims-profile
	$(LN) ../init.d/ims-profile-watch $(1)/etc/rc.d/S97ims-profile-watch
	# Call manager + BT audio gateway: the init scripts gate themselves on
	# ufivoice.main.mode, so enabling them here is safe on every board.
	$(LN) ../init.d/hfp-ag $(1)/etc/rc.d/S97hfp-ag
	$(LN) ../init.d/ufi-voiced $(1)/etc/rc.d/S99ufi-voiced
	$(INSTALL_DIR) $(1)/etc/hotplug.d/wwan
	$(INSTALL_BIN) ./files/hotplug.d/wwan/20-ims-profile \
		$(1)/etc/hotplug.d/wwan/20-ims-profile
	$(INSTALL_CONF) ./files/ufivoice.config $(1)/etc/config/ufivoice
	$(INSTALL_DIR) $(1)/etc/ModemManager/connection.d
	$(INSTALL_BIN) ./files/10-report-down $(1)/etc/ModemManager/connection.d/
	$(INSTALL_DIR) $(1)/etc/uci-defaults
	$(INSTALL_BIN) ./files/90-ufi-voice-services $(1)/etc/uci-defaults/
endef

define Package/ufi-voice-asterisk/conffiles
/etc/asterisk/ufi-voice-phones.txt
/etc/asterisk/.ufi-voice
endef

define Package/ufi-voice-asterisk/install
	$(INSTALL_DIR) $(1)/etc/uci-defaults $(1)/usr/sbin
	$(INSTALL_BIN) ./files/91-ufi-voice-asterisk $(1)/etc/uci-defaults/
	$(INSTALL_BIN) ./files/ufi-voice-asterisk-apply $(1)/usr/sbin/
endef

$(eval $(call BuildPackage,ufi-voice))
$(eval $(call BuildPackage,ufi-voice-asterisk))

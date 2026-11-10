# SPDX-License-Identifier: GPL-2.0-only
#
# ufi-voice: VoLTE (IMS data service for the modem) and call audio for the
# UFI003: Bluetooth headset (HFP AG + internal BT SCO) and local Asterisk
# phones (AudioSocket bridge).

include $(TOPDIR)/rules.mk

PKG_NAME:=ufi-voice
PKG_VERSION:=1.0
PKG_RELEASE:=8
PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Package/ufi-voice
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=Telephony
  TITLE:=VoLTE and call audio for MSM8916 sticks
  DEPENDS:=@TARGET_msm89xx +kmod-sound-msm8916-voice +alsa-lib +alsa-utils \
	+libdbus +python3-light +qmi-utils +modemmanager +diag-efs +diag-logtool
endef

define Package/ufi-voice/description
 imsdcm: the AP-side IMS data connection service the modem's IMS stack uses
 in hybrid mode (brings up the IMS PDN through ModemManager).
 ufi-voiced: connects modem calls to a Bluetooth headset (hfp-ag) and/or
 local SIP phones through Asterisk (ufi-voice-asterisk).
 voicectl / hostless: DSP voice path routing. voicebridge: low-latency
 in-call audio for the Asterisk phones. volte-setup: modem EFS items.
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
endef

define Package/ufi-voice/install
	$(INSTALL_DIR) $(1)/usr/bin $(1)/usr/sbin $(1)/etc/init.d $(1)/etc/config
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/hostless $(PKG_BUILD_DIR)/voicebridge $(1)/usr/bin/
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/hfp-ag $(1)/usr/sbin/
	$(INSTALL_BIN) ./files/imsdcm ./files/ufi-voiced ./files/voicectl \
		./files/volte-setup ./files/ufi-voice-dial $(1)/usr/sbin/
	$(INSTALL_BIN) ./files/imsdcm.init $(1)/etc/init.d/imsdcm
	$(INSTALL_BIN) ./files/hfp-ag.init $(1)/etc/init.d/hfp-ag
	$(INSTALL_BIN) ./files/ufi-voiced.init $(1)/etc/init.d/ufi-voiced
	$(INSTALL_CONF) ./files/ufivoice.config $(1)/etc/config/ufivoice
	$(INSTALL_DIR) $(1)/etc/ModemManager/connection.d
	$(INSTALL_BIN) ./files/10-report-down $(1)/etc/ModemManager/connection.d/
endef

define Package/ufi-voice-asterisk/conffiles
/etc/asterisk/ufi-voice-phones.txt
/etc/asterisk/.ufi-voice
endef

define Package/ufi-voice-asterisk/install
	$(INSTALL_DIR) $(1)/etc/uci-defaults
	$(INSTALL_BIN) ./files/91-ufi-voice-asterisk $(1)/etc/uci-defaults/
endef

$(eval $(call BuildPackage,ufi-voice))
$(eval $(call BuildPackage,ufi-voice-asterisk))

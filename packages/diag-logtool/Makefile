# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=diag-logtool
PKG_VERSION:=1.0
PKG_RELEASE:=1

PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
	$(CP) ./src/* $(PKG_BUILD_DIR)/
endef

define Build/Compile
	$(TARGET_CC) $(TARGET_CFLAGS) $(TARGET_LDFLAGS) \
		-o $(PKG_BUILD_DIR)/diag_logtool $(PKG_BUILD_DIR)/diag_logtool.c
endef

define Package/diag-logtool
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Qualcomm DIAG client: F3 log masks, capture and listen over SMD DIAG
  DEPENDS:=+diag-bind
endef

define Package/diag-logtool/description
Qualcomm DIAG client for the SMD DIAG channels bridged to /dev/rpmsg0 (data)
and /dev/rpmsg1 (control).

  selftest / raw              -- round-trip and raw-payload probes
  listen / capture            -- decode or capture the DIAG stream
  log-config / set-mask / get-mask / log-enable / log-disable
                              -- DIAG_LOG_CONFIG_F (0x73) log-mask control
  cntl-enable / cntl-enable-range / cntl-disable / cntl-dump
                              -- control-channel F3/log/event mask delivery

This is the instrument behind the modem's F3 boot log (Docs 196/197/203/205/208).
Requires the SMD DIAG <-> rpmsg bridge: run `diag-bind`, or install the
diag-bind service which keeps the bridge alive across modem SSRs.
endef

define Package/diag-logtool/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/diag_logtool $(1)/usr/bin/diag_logtool
endef

$(eval $(call BuildPackage,diag-logtool))

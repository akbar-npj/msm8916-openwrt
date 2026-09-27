# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=diag-efs
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
		-o $(PKG_BUILD_DIR)/diag_efs $(PKG_BUILD_DIR)/diag_efs.c
endef

define Package/diag-efs
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Read/write the modem's EFS (decrypted on the fly) over the SMD DIAG channel
  DEPENDS:=+diag-bind
endef

define Package/diag-efs/description
Reads the Qualcomm modem's EFS through the DIAG EFS2 subsystem (0x13) over
/dev/rpmsg0. The modem decrypts its own EFS on the fly, so this exposes the
live, decrypted NV tree without needing the (per-chip) storage key.

  list / read / stat / get / dump  -- read-only inspection
  put <local> <efs-path>           -- guarded write: refuses calibration paths
                                      (rfnv/, mcs/) without --force, backs up an
                                      existing target, and always verifies by
                                      read-back (MATCH/MISMATCH)

Requires the SMD DIAG <-> rpmsg bridge: run `diag-bind`, or install the
diag-bind service which keeps the bridge alive across modem SSRs.
endef

define Package/diag-efs/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/diag_efs $(1)/usr/bin/diag_efs
endef

$(eval $(call BuildPackage,diag-efs))

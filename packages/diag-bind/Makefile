# SPDX-License-Identifier: GPL-2.0-only

include $(TOPDIR)/rules.mk

PKG_NAME:=diag-bind
PKG_VERSION:=1.0
PKG_RELEASE:=1

PKG_LICENSE:=GPL-2.0-only

include $(INCLUDE_DIR)/package.mk

define Build/Compile
endef

define Package/diag-bind
  SECTION:=utils
  CATEGORY:=Utilities
  TITLE:=Bind the SMD DIAG endpoints to rpmsg_chrdev (creates /dev/rpmsg0, /dev/rpmsg1)
  DEPENDS:=+kmod-qcom-rproc-modem
endef

define Package/diag-bind/description
Exposes the Qualcomm modem's SMD DIAG channels as character devices by binding
remoteproc0:smd-edge.DIAG / DIAG_CNTL to the rpmsg_chrdev driver, creating
/dev/rpmsg0 (data) and /dev/rpmsg1 (control).

Ships both the one-shot helper (/usr/sbin/diag-bind) and a procd service that
keeps the bridge alive: it binds at boot and re-binds whenever the modem
subsystem restarts (SSR), which destroys and re-creates the rpmsg device the
binding lived on. Without the service, a capture goes silent after the first
modem restart -- the modem on this platform restarts roughly every 900 s.

Required by diag-efs and diag-logtool.
endef

define Package/diag-bind/install
	$(INSTALL_DIR) $(1)/usr/sbin
	$(INSTALL_BIN) ./files/diag-bind $(1)/usr/sbin/diag-bind
	$(INSTALL_BIN) ./files/diag-bind-watch $(1)/usr/sbin/diag-bind-watch
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/diag-bind.init $(1)/etc/init.d/diag-bind
	$(INSTALL_DIR) $(1)/etc/uci-defaults
	$(INSTALL_BIN) ./files/diag-bind.defaults $(1)/etc/uci-defaults/95-diag-bind
endef

$(eval $(call BuildPackage,diag-bind))

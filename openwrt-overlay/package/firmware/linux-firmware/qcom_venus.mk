# SPDX-License-Identifier: GPL-2.0-only

Package/qcom-venus-firmware = $(call Package/firmware-default,Qualcomm Venus 1.8 firmware,,LICENSE.qcom qcom/NOTICE.txt,LicenseRef-LICENSE.qcom)

define Package/qcom-venus-firmware/description
  Firmware for the Qualcomm Venus 1.8 video encoder/decoder on MSM8916.
endef

define Package/qcom-venus-firmware/install
	$(INSTALL_DIR) $(1)/lib/firmware/qcom/venus-1.8
	$(INSTALL_DATA) $(PKG_BUILD_DIR)/qcom/venus-1.8/venus.mbn $(1)/lib/firmware/qcom/venus-1.8/
	$(INSTALL_DIR) $(1)/usr/share/licenses/qcom-venus-firmware
	$(INSTALL_DATA) $(PKG_BUILD_DIR)/LICENSE.qcom $(1)/usr/share/licenses/qcom-venus-firmware/LICENSE.qcom.txt
	$(INSTALL_DATA) $(PKG_BUILD_DIR)/qcom/NOTICE.txt $(1)/usr/share/licenses/qcom-venus-firmware/
endef

$(eval $(call BuildPackage,qcom-venus-firmware))

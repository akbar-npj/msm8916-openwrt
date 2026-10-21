# SPDX-License-Identifier: GPL-2.0-only

ifeq ($(SUBTARGET),msm8916)

define Build/generate-squashfs-gpt
	chmod +x $(TOPDIR)/target/linux/$(BOARD)/image/generate_squashfs_gpt.sh
	TOT_SECTORS="$(GPT_TOT_SECTORS)" \
	$(TOPDIR)/target/linux/$(BOARD)/image/generate_squashfs_gpt.sh $@
endef

define Build/install-flasher
	$(CP) $(TOPDIR)/target/linux/$(BOARD)/image/flash.sh $@
	chmod +x $@
endef

define Build/generate-firmware
	chmod +x $(TOPDIR)/target/linux/$(BOARD)/image/generate_firmware.sh
	TOPDIR="$(TOPDIR)" \
	$(TOPDIR)/target/linux/$(BOARD)/image/generate_firmware.sh $@
endef

define Device/msm8916
	SOC := msm8916
	# 4 GB eMMC fitted to the modem sticks; override per device when it differs.
	GPT_TOT_SECTORS := 7569408
	CMDLINE := "earlycon console=tty0 console=ttyMSM0,115200 root=/dev/mmcblk0p14 rootfstype=squashfs rootwait"
	FEATURES := squashfs
	IMAGE/system.img := append-rootfs | append-metadata
	ARTIFACTS := squashfs-gpt_both0.bin flash.sh firmware.zip
	ARTIFACT/squashfs-gpt_both0.bin := generate-squashfs-gpt
	ARTIFACT/flash.sh := install-flasher
	ARTIFACT/firmware.zip := generate-firmware
endef

define Device/yiming-uz801v3
	$(Device/msm8916)
	DEVICE_VENDOR := YiMing
	DEVICE_MODEL := uz801v3
	SUPPORTED_DEVICES := yiming,uz801-v3
	FILESYSTEMS := squashfs
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += yiming-uz801v3

define Device/generic-uf02
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := UF02 (250605 V0S)
	SUPPORTED_DEVICES := uf02,250605v0s
	FILESYSTEMS := squashfs
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-uf02

define Device/generic-ufi001b
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := UFI001B
	DEVICE_DTS := msm8916-generic-ufi001b
	SUPPORTED_DEVICES := ufi001b,250605v0s
	FILESYSTEMS := squashfs
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-ufi001b

define Device/generic-ufi003-mb-v02
	$(Device/msm8916)
	DEVICE_VENDOR := UFI
	DEVICE_MODEL := UFI003 MB V02
	DEVICE_DTS := msm8916-generic-ufi003_mb_v02
	SUPPORTED_DEVICES := ufi003,mb-v02
	FILESYSTEMS := squashfs
	GPT_TOT_SECTORS := 7634944
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-ufi003-mb-v02

define Device/generic-hmuf02v5
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := HMUF02-V5
	DEVICE_DTS := msm8916-generic-hmuf02v5
	SUPPORTED_DEVICES := hmuf02v5,250605v0s hmu05,250605v0s
	FILESYSTEMS := squashfs
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg qcom-time-daemon \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-hmuf02v5

define Device/generic-mf800b
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := MF800B
	DEVICE_DTS := msm8916-generic-mf800b
	SUPPORTED_DEVICES := mf800b,generic
	FILESYSTEMS := squashfs
	# Slightly larger eMMC than the sticks (cat /sys/block/mmcblk0/size).
	GPT_TOT_SECTORS := 7634944
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-mf800b

define Device/generic-mifib-v12
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := HM-MIFIB-V12
	DEVICE_DTS := msm8916-generic-mifib-v12
	SUPPORTED_DEVICES := mifib-v12,generic
	FILESYSTEMS := squashfs
	# Same eMMC size as the mf800b (0x748000 sectors).
	GPT_TOT_SECTORS := 7634944
	DEVICE_PACKAGES := wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool
endef
TARGET_DEVICES += generic-mifib-v12

endif

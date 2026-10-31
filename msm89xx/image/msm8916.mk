# SPDX-License-Identifier: GPL-2.0-only

ifeq ($(SUBTARGET),msm8916)

# Custom per-device variables MUST be declared here.  `Device/Export` turns each
# one into a target-specific variable, but it runs after every device has been
# evaluated - so an undeclared variable is just a plain global and every device
# ends up with the value of whichever device assigned one LAST.  That is how
# hmuf02v5 silently got the mf800b/mifib-v12 `GPT_RECOVERY=1` layout.
DEVICE_VARS += GPT_TOT_SECTORS GPT_RECOVERY

define Build/generate-squashfs-gpt
	chmod +x $(TOPDIR)/target/linux/$(BOARD)/image/generate_squashfs_gpt.sh
	TOT_SECTORS="$(GPT_TOT_SECTORS)" RECOVERY="$(GPT_RECOVERY)" \
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
	# Reset per device.  GPT_RECOVERY is NOT in OpenWrt's DEVICE_VARS, so it
	# is a plain global: without this default it leaks from the first device
	# that sets it to 1 into every device evaluated afterwards (observed:
	# hmuf02v5 silently got misc+recovery even though it never asks for them).
	GPT_RECOVERY := 0
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
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify
endef
TARGET_DEVICES += yiming-uz801v3

define Device/generic-uf02
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := UF02 (250605 V0S)
	SUPPORTED_DEVICES := uf02,250605v0s
	FILESYSTEMS := squashfs
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify
endef
TARGET_DEVICES += generic-uf02

define Device/generic-ufi001b
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := UFI001B
	DEVICE_DTS := msm8916-generic-ufi001b
	SUPPORTED_DEVICES := ufi001b,250605v0s
	FILESYSTEMS := squashfs
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify
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
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify
endef
TARGET_DEVICES += generic-ufi003-mb-v02

define Device/generic-hmuf02v5
	$(Device/msm8916)
	DEVICE_VENDOR := Generic
	DEVICE_MODEL := HMUF02-V5
	DEVICE_DTS := msm8916-generic-hmuf02v5
	SUPPORTED_DEVICES := hmuf02v5,250605v0s hmu05,250605v0s
	FILESYSTEMS := squashfs
	# Real eMMC confirmed from the stock Android GPT: 0x748000 sectors
	# (userdata fills to 0xE8FFBE00 of 0xE9000000). The 7569408 default
	# leaves 32 MiB unused and puts the backup GPT header off the disk end.
	GPT_TOT_SECTORS := 7634944
	# Recovery partition enabled for testing on the safe board (EDL push
	# button + batteryless, so a bad failover is always recoverable).  The
	# button paths are unchanged: power-on -> EDL, 7 s window -> failsafe,
	# 5 s hold -> factory reset.  Remove this line to go back to 15
	# partitions and reclaim ~17 MB of rootfs_data.
	GPT_RECOVERY := 1
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg qcom-time-daemon \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify
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
	# No EDL push-button: needs the recovery + misc partitions.
	GPT_RECOVERY := 1
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify luci-app-battery
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
	# No EDL push-button: needs the recovery + misc partitions.
	GPT_RECOVERY := 1
	DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
		block-mount f2fs-tools tar \
		msm-firmware-dumper reboot-edl qcom-carrier-autocfg \
		diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify luci-app-battery
endef
TARGET_DEVICES += generic-mifib-v12


define Device/generic-uf896
    $(Device/msm8916)
    DEVICE_VENDOR := Generic
    DEVICE_MODEL  := UF896 (v1.1)
    DEVICE_DTS    := msm8916-generic-uf896
    SUPPORTED_DEVICES := uf896,v1.1
    FILESYSTEMS   := squashfs
    # Batteryless UFI-family stick (same 4 GB eMMC as ufi003); the build
    # default GPT_TOT_SECTORS is used.  flash.sh reads the real size at
    # flash time, so a mismatch only leaves a little unused space.
    # qmi-utils provides qmicli, needed by etc/init.d/uf896-modem-online
    # to switch the modem out of factory-test mode at boot.
    DEVICE_PACKAGES := msm89xx-scripts wpad-basic-wolfssl rmtfs uci-usb-gadget \
        block-mount f2fs-tools tar \
        msm-firmware-dumper reboot-edl qcom-carrier-autocfg qmi-utils \
        diag-efs diag-logtool msm89xx-firmware-upgrade luci-app-update-notify
endef
TARGET_DEVICES += generic-uf896
endif

#
# SPDX-FileCopyrightText: 2025 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

COMMON_PATH := device/samsung/exynos7885-common

BOARD_VENDOR := samsung

# SLSI Linaro
include hardware/samsung_slsi-linaro/config/BoardConfig7885.mk

BOARD_HWJPEG_ANDROID_VERSION := 11

# Architecture
TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_ABI2 :=
TARGET_CPU_VARIANT := cortex-a53

TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv8-a
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi
TARGET_2ND_CPU_VARIANT := cortex-a53

# Build system
BUILD_BROKEN_DUP_RULES := true
BUILD_BROKEN_ELF_PREBUILT_PRODUCT_COPY_FILES := true

# Camera
ifneq ($(TARGET_DEVICE),a10)
$(call soong_config_set,exynos7885CameraVars,extra_ids,50)
endif

# Display
BOARD_MINIMUM_DISPLAY_BRIGHTNESS := 1

# DTBO
BOARD_KERNEL_SEPARATED_DTBO := true
BOARD_DTBO_CFG := $(COMMON_PATH)/configs/kernel/$(TARGET_DEVICE).cfg

# Init
$(call soong_config_set,libinit,vendor_init_lib,//$(COMMON_PATH):libinit_exynos7885)

# Kernel
BOARD_KERNEL_IMAGE_NAME := Image
BOARD_KERNEL_BASE := 0x10000000
BOARD_KERNEL_OFFSET := 0x00008000
BOARD_KERNEL_PAGESIZE := 2048
BOARD_RAMDISK_OFFSET := 0x01000000
BOARD_TAGS_OFFSET := 0x00000100
BOARD_BOOTIMG_HEADER_VERSION := 1

BOARD_MKBOOTIMG_ARGS := --base $(BOARD_KERNEL_BASE)
BOARD_MKBOOTIMG_ARGS += --pagesize $(BOARD_KERNEL_PAGESIZE)
BOARD_MKBOOTIMG_ARGS += --kernel_offset $(BOARD_KERNEL_OFFSET)
BOARD_MKBOOTIMG_ARGS += --ramdisk_offset $(BOARD_RAMDISK_OFFSET)
BOARD_MKBOOTIMG_ARGS += --tags_offset $(BOARD_TAGS_OFFSET)
BOARD_MKBOOTIMG_ARGS += --header_version $(BOARD_BOOTIMG_HEADER_VERSION)

TARGET_KERNEL_SOURCE := kernel/samsung/exynos7885
TARGET_KERNEL_NO_GCC := true

# Keymaster
$(call soong_config_set,samsungVars,target_keymaster4_library,//vendor/samsung/exynos7885-common:libskeymaster4device)

# Manifest
DEVICE_MANIFEST_FILE := $(COMMON_PATH)/manifest.xml
DEVICE_MATRIX_FILE := $(COMMON_PATH)/compatibility_matrix.xml
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE := \
    hardware/samsung/vintf/samsung_framework_compatibility_matrix.xml

# Touch HAL: it registers every feature the touch driver lists in
# /sys/class/sec/tsp/cmd_list (glove_mode -> IGloveMode, hover_enable ->
# IStylusMode, singletap_enable -> ITouchscreenGesture) and aborts when one is
# not declared here, so each device declares exactly what its driver lists.
ifneq ($(filter a30,$(TARGET_DEVICE)),)
# melfas_mss100
DEVICE_MANIFEST_FILE += $(COMMON_PATH)/manifest_touch_tsgesture.xml
else ifneq ($(filter a30s,$(TARGET_DEVICE)),)
# imagis ist40xx
DEVICE_MANIFEST_FILE += $(COMMON_PATH)/manifest_touch_stylus_tsgesture.xml
else ifneq ($(filter a40,$(TARGET_DEVICE)),)
# imagis ist30xxh
DEVICE_MANIFEST_FILE += $(COMMON_PATH)/manifest_touch_stylus.xml
else
# a10 sec_incell_ts, a20 zinitix, a20e himax
DEVICE_MANIFEST_FILE += $(COMMON_PATH)/manifest_touch.xml
endif

# NFC
ODM_MANIFEST_SKUS += NFC
ODM_MANIFEST_NFC_FILES := $(COMMON_PATH)/manifest_nfc.xml

# Partitions
BOARD_BOOTIMAGE_PARTITION_SIZE := 37748736
BOARD_DTBIMG_PARTITION_SIZE := 8388608
BOARD_DTBOIMG_PARTITION_SIZE := 8388608
BOARD_SYSTEMIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_FLASH_BLOCK_SIZE := 131072
TARGET_COPY_OUT_VENDOR := vendor

BOARD_USES_METADATA_PARTITION := true

# Platform
TARGET_BOARD_PLATFORM := exynos7885
TARGET_BOOTLOADER_BOARD_NAME := $(TARGET_SOC)

# Properties
TARGET_VENDOR_PROP += $(COMMON_PATH)/vendor.prop

# Recovery
TARGET_RECOVERY_FSTAB := $(COMMON_PATH)/rootdir/etc/fstab.exynos7885
TARGET_RECOVERY_PIXEL_FORMAT := ABGR_8888
BOARD_INCLUDE_RECOVERY_DTBO := true

# Releasetools
TARGET_RELEASETOOLS_EXTENSIONS := $(COMMON_PATH)/releasetools

# Rootdir
BOARD_ROOT_EXTRA_FOLDERS := factory
BOARD_ROOT_EXTRA_SYMLINKS := /factory:/efs

# RIL
ENABLE_VENDOR_RIL_SERVICE := true

# Sepolicy
BOARD_SEPOLICY_TEE_FLAVOR := teegris
include device/lineage/sepolicy/exynos/sepolicy.mk
include device/samsung_slsi/sepolicy/sepolicy.mk
include hardware/samsung-ext/interfaces/sepolicy/SEPolicy.mk
BOARD_VENDOR_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/vendor
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/public
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/private

# UFFD GC
OVERRIDE_ENABLE_UFFD_GC := false

# Vibrator
$(call soong_config_set_bool,samsungVibratorVars,duration_amplitude,true)

# Wifi
BOARD_WLAN_DEVICE                := slsi
WPA_SUPPLICANT_VERSION           := VER_0_8_X
BOARD_WPA_SUPPLICANT_DRIVER      := NL80211
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
BOARD_HOSTAPD_DRIVER             := NL80211
BOARD_HOSTAPD_PRIVATE_LIB        := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
WIFI_HIDL_FEATURE_DUAL_INTERFACE := true
WIFI_HIDL_UNIFIED_SUPPLICANT_SERVICE_RC_ENTRY := true

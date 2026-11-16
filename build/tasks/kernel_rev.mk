#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# vendor/lineage's kernel.mk only builds the kernel and dtbo.img when they are missing or
# the defconfig changed: neither rule depends on the kernel sources. So after syncing new
# kernel commits, an existing out/ kept the old Image and dtbo.img.
#
# Record the kernel's commit in a file and make both depend on it. Kati re-runs the git
# command on every build; when the commit changes, the stamp rule's command changes, ninja
# rewrites the stamp, and both images get rebuilt (kbuild then only recompiles what changed).

ifneq ($(filter exynos7884B exynos7904,$(TARGET_SOC)),)
KERNEL_SRC_REV := $(shell git -C $(TARGET_KERNEL_SOURCE) rev-parse HEAD 2>/dev/null)
KERNEL_SRC_REV_STAMP := $(TARGET_OUT_INTERMEDIATES)/KERNEL_SRC_REV

$(KERNEL_SRC_REV_STAMP):
	$(hide) mkdir -p $(dir $@)
	$(hide) echo "$(KERNEL_SRC_REV)" > $@

$(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/arch/$(KERNEL_ARCH)/boot/$(BOARD_KERNEL_IMAGE_NAME): $(KERNEL_SRC_REV_STAMP)
ifneq ($(BOARD_PREBUILT_DTBOIMAGE),)
$(BOARD_PREBUILT_DTBOIMAGE): $(KERNEL_SRC_REV_STAMP)
endif
endif

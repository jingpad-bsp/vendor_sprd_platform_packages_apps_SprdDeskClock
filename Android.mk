LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, res)

LOCAL_CERTIFICATE := platform
LOCAL_MODULE_TAGS := optional
#LOCAL_SDK_VERSION := current
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_PACKAGE_NAME := SprdDeskClock
LOCAL_OVERRIDES_PACKAGES := AlarmClock  DeskClock

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_ANDROID_LIBRARIES := \
        $(ANDROID_SUPPORT_DESIGN_TARGETS) \
        android-support-percent \
        android-support-transition \
        android-support-compat \
        android-support-core-ui \
        android-support-media-compat \
        android-support-v13 \
        android-support-v14-preference \
        android-support-v7-appcompat \
        android-support-v7-gridlayout \
        android-support-v7-preference \
        android-support-v7-recyclerview

LOCAL_USE_AAPT2 := true
# Bug 584154 temporary modifications in order to solve "clock stops running" when Click the "add worldclock" button
LOCAL_PROGUARD_FLAG_FILES += proguard.flags

include $(BUILD_PACKAGE)

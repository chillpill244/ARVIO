package com.arflix.tv.shared.util

import androidx.compose.runtime.compositionLocalOf

enum class DeviceType {
    TV,
    TABLET,
    PHONE;

    fun isTouchDevice(): Boolean = this == PHONE || this == TABLET

    fun isMobile(): Boolean = isTouchDevice()
}

val LocalDeviceType = compositionLocalOf { DeviceType.TV }

/** True if the physical device has a touchscreen. Use this to decide navigation style. */
val LocalHasTouchScreen = compositionLocalOf { true }

package com.websmithing.gpstracker2.service

import android.os.Build

internal enum class TrackingRestartAlarmMode {
    ExactAllowWhileIdle,
    AllowWhileIdle,
}

internal fun chooseTrackingRestartAlarmMode(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
): TrackingRestartAlarmMode {
    return if (sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
        TrackingRestartAlarmMode.AllowWhileIdle
    } else {
        TrackingRestartAlarmMode.ExactAllowWhileIdle
    }
}

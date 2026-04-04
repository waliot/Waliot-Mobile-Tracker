package com.websmithing.gpstracker2.service

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingRestartAlarmModeTest {

    @Test
    fun `uses exact allow while idle before Android 12`() {
        assertEquals(
            TrackingRestartAlarmMode.ExactAllowWhileIdle,
            chooseTrackingRestartAlarmMode(
                sdkInt = Build.VERSION_CODES.R,
                canScheduleExactAlarms = false,
            )
        )
    }

    @Test
    fun `uses exact allow while idle on Android 12 plus when exact alarms are allowed`() {
        assertEquals(
            TrackingRestartAlarmMode.ExactAllowWhileIdle,
            chooseTrackingRestartAlarmMode(
                sdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = true,
            )
        )
    }

    @Test
    fun `falls back to allow while idle on Android 12 plus when exact alarms are unavailable`() {
        assertEquals(
            TrackingRestartAlarmMode.AllowWhileIdle,
            chooseTrackingRestartAlarmMode(
                sdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = false,
            )
        )
    }
}

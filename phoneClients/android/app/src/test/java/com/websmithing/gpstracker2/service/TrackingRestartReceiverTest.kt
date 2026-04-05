package com.websmithing.gpstracker2.service

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRestartReceiverTest {

    @Test
    fun `boot completed action is treated as recovery trigger`() {
        assertTrue(shouldRestoreTrackingForAction(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `package replaced action is treated as recovery trigger`() {
        assertTrue(shouldRestoreTrackingForAction(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `other actions do not trigger recovery`() {
        assertFalse(shouldRestoreTrackingForAction(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }

    @Test
    fun `null action does not trigger recovery`() {
        assertFalse(shouldRestoreTrackingForAction(null))
    }
}

package com.websmithing.gpstracker2.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPermissionCheckerTest {

    @Test
    fun `canStartTrackingService returns false when foreground location permission is missing`() {
        val checker = TrackingPermissionChecker(
            sdkInt = 35,
            hasForegroundLocationPermission = { false },
            hasBackgroundLocationPermission = { true },
        )

        assertFalse(checker.canStartTrackingService())
    }

    @Test
    fun `canRestoreTrackingFromBackground requires background permission on Android Q and above`() {
        val checker = TrackingPermissionChecker(
            sdkInt = 35,
            hasForegroundLocationPermission = { true },
            hasBackgroundLocationPermission = { false },
        )

        assertFalse(checker.canRestoreTrackingFromBackground())
    }

    @Test
    fun `canRestoreTrackingFromBackground allows foreground permission only below Android Q`() {
        val checker = TrackingPermissionChecker(
            sdkInt = 28,
            hasForegroundLocationPermission = { true },
            hasBackgroundLocationPermission = { false },
        )

        assertTrue(checker.canRestoreTrackingFromBackground())
    }

    @Test
    fun `canRestoreTrackingFromBackground allows recovery when both permissions are granted`() {
        val checker = TrackingPermissionChecker(
            sdkInt = 35,
            hasForegroundLocationPermission = { true },
            hasBackgroundLocationPermission = { true },
        )

        assertTrue(checker.canRestoreTrackingFromBackground())
    }
}

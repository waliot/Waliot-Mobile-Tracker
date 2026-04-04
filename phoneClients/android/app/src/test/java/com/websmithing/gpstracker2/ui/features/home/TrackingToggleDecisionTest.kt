package com.websmithing.gpstracker2.ui.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingToggleDecisionTest {

    @Test
    fun `stopping tracking is always allowed even when permissions were revoked`() {
        val decision = decideTrackingToggle(
            isTracking = true,
            canRunTracking = true,
            requiresBackgroundLocationPermission = true,
            hasBackgroundLocationPermission = false,
            isIgnoringBatteryOptimizations = false,
            hasShownBatteryOptimizationWarning = false,
        )

        assertTrue(decision.shouldProceed)
        assertEquals(null, decision.blocker)
        assertFalse(decision.showBatteryOptimizationWarning)
    }

    @Test
    fun `starting tracking is blocked when configuration is missing`() {
        val decision = decideTrackingToggle(
            isTracking = false,
            canRunTracking = false,
            requiresBackgroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            isIgnoringBatteryOptimizations = true,
            hasShownBatteryOptimizationWarning = false,
        )

        assertFalse(decision.shouldProceed)
        assertEquals(TrackingToggleBlocker.MissingConfiguration, decision.blocker)
    }

    @Test
    fun `starting tracking is blocked when background permission is missing`() {
        val decision = decideTrackingToggle(
            isTracking = false,
            canRunTracking = true,
            requiresBackgroundLocationPermission = true,
            hasBackgroundLocationPermission = false,
            isIgnoringBatteryOptimizations = true,
            hasShownBatteryOptimizationWarning = false,
        )

        assertFalse(decision.shouldProceed)
        assertEquals(TrackingToggleBlocker.MissingBackgroundLocationPermission, decision.blocker)
    }

    @Test
    fun `starting tracking warns when battery optimization is still enabled`() {
        val decision = decideTrackingToggle(
            isTracking = false,
            canRunTracking = true,
            requiresBackgroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            isIgnoringBatteryOptimizations = false,
            hasShownBatteryOptimizationWarning = false,
        )

        assertTrue(decision.shouldProceed)
        assertTrue(decision.showBatteryOptimizationWarning)
    }

    @Test
    fun `starting tracking below Android Q does not require background permission`() {
        val decision = decideTrackingToggle(
            isTracking = false,
            canRunTracking = true,
            requiresBackgroundLocationPermission = false,
            hasBackgroundLocationPermission = false,
            isIgnoringBatteryOptimizations = true,
            hasShownBatteryOptimizationWarning = false,
        )

        assertTrue(decision.shouldProceed)
        assertEquals(null, decision.blocker)
    }

    @Test
    fun `starting tracking does not repeat battery optimization warning after first notice`() {
        val decision = decideTrackingToggle(
            isTracking = false,
            canRunTracking = true,
            requiresBackgroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            isIgnoringBatteryOptimizations = false,
            hasShownBatteryOptimizationWarning = true,
        )

        assertTrue(decision.shouldProceed)
        assertFalse(decision.showBatteryOptimizationWarning)
    }
}

package com.websmithing.gpstracker2.ui.features.home.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionDecisionTest {

    @Test
    fun `foreground decision allows when fine location is granted and background is not required`() {
        val decision = decideForegroundPermissionResult(
            fineLocationGranted = true,
            coarseLocationGranted = false,
            isBackgroundLocationRequired = false,
            hasBackgroundLocationPermission = false,
            anyShouldShowRationale = false,
        )

        assertEquals(ForegroundPermissionDecision.Allow, decision)
    }

    @Test
    fun `foreground decision requests background permission when foreground is granted but background is missing`() {
        val decision = decideForegroundPermissionResult(
            fineLocationGranted = false,
            coarseLocationGranted = true,
            isBackgroundLocationRequired = true,
            hasBackgroundLocationPermission = false,
            anyShouldShowRationale = false,
        )

        assertEquals(ForegroundPermissionDecision.RequestBackgroundPermission, decision)
    }

    @Test
    fun `foreground decision allows when background permission is already granted`() {
        val decision = decideForegroundPermissionResult(
            fineLocationGranted = true,
            coarseLocationGranted = false,
            isBackgroundLocationRequired = true,
            hasBackgroundLocationPermission = true,
            anyShouldShowRationale = false,
        )

        assertEquals(ForegroundPermissionDecision.Allow, decision)
    }

    @Test
    fun `foreground decision shows rationale when permissions are denied but rationale is available`() {
        val decision = decideForegroundPermissionResult(
            fineLocationGranted = false,
            coarseLocationGranted = false,
            isBackgroundLocationRequired = true,
            hasBackgroundLocationPermission = false,
            anyShouldShowRationale = true,
        )

        assertEquals(ForegroundPermissionDecision.ShowRationale, decision)
    }

    @Test
    fun `foreground decision denies when permissions are denied without rationale`() {
        val decision = decideForegroundPermissionResult(
            fineLocationGranted = false,
            coarseLocationGranted = false,
            isBackgroundLocationRequired = true,
            hasBackgroundLocationPermission = false,
            anyShouldShowRationale = false,
        )

        assertEquals(ForegroundPermissionDecision.Deny, decision)
    }

    @Test
    fun `background decision allows when permission is granted`() {
        assertEquals(
            BackgroundPermissionDecision.Allow,
            decideBackgroundPermissionResult(granted = true)
        )
    }

    @Test
    fun `background decision denies when permission is rejected`() {
        assertEquals(
            BackgroundPermissionDecision.Deny,
            decideBackgroundPermissionResult(granted = false)
        )
    }
}

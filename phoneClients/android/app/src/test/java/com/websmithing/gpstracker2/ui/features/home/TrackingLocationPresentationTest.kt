package com.websmithing.gpstracker2.ui.features.home

import android.location.Location
import com.websmithing.gpstracker2.repository.location.LocationFixIssue
import com.websmithing.gpstracker2.repository.location.LocationFixQuality
import com.websmithing.gpstracker2.repository.location.LocationFixStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TrackingLocationPresentationTest {

    @Test
    fun `trusted gps fix becomes stale after five minutes`() {
        val trustedLocation = mockLocation(
            provider = "gps",
            time = 1_700_000_000_000L,
            accuracy = 5f,
        )

        val presentation = presentLocationFixStatus(
            fixStatus = LocationFixStatus(
                quality = LocationFixQuality.TrustedGps,
                trustedLocation = trustedLocation,
                provider = "gps",
                accuracyMeters = 5f,
                observedAtMillis = trustedLocation.time,
            ),
            nowMillis = trustedLocation.time + STALE_FIX_TIMEOUT_MILLIS + 1L,
        )

        assertEquals(TrackingLocationUiState.StaleGps, presentation.state)
    }

    @Test
    fun `network provider is presented as degraded fix`() {
        val trustedLocation = mockLocation(
            provider = "network",
            time = 1_700_000_000_000L,
            accuracy = 80f,
        )

        val presentation = presentLocationFixStatus(
            fixStatus = LocationFixStatus(
                quality = LocationFixQuality.TrustedDegraded,
                trustedLocation = trustedLocation,
                provider = "network",
                accuracyMeters = 80f,
                observedAtMillis = trustedLocation.time,
            ),
            nowMillis = trustedLocation.time + 10_000L,
        )

        assertEquals(TrackingLocationUiState.FreshDegraded, presentation.state)
    }

    @Test
    fun `no fix preserves degraded state of the last trusted location`() {
        val trustedLocation = mockLocation(
            provider = "fused",
            time = 1_700_000_000_000L,
            accuracy = 25f,
        )

        val presentation = presentLocationFixStatus(
            fixStatus = LocationFixStatus(
                quality = LocationFixQuality.NoFix,
                trustedLocation = trustedLocation,
                trustedFixDegraded = true,
                issue = LocationFixIssue.NoProviders,
                observedAtMillis = trustedLocation.time + 30_000L,
            ),
            nowMillis = trustedLocation.time + 60_000L,
        )

        assertEquals(TrackingLocationUiState.FreshDegraded, presentation.state)
    }

    @Test
    fun `suspect fix remains suspect while preserving last trusted location`() {
        val trustedLocation = mockLocation(
            provider = "gps",
            time = 1_700_000_000_000L,
            accuracy = 5f,
        )

        val presentation = presentLocationFixStatus(
            fixStatus = LocationFixStatus(
                quality = LocationFixQuality.Suspect,
                trustedLocation = trustedLocation,
                issue = LocationFixIssue.MockFix,
                provider = "gps",
                accuracyMeters = 5f,
                observedAtMillis = trustedLocation.time + 30_000L,
            ),
            nowMillis = trustedLocation.time + 60_000L,
        )

        assertEquals(TrackingLocationUiState.Suspect, presentation.state)
        assertEquals(LocationFixIssue.MockFix, presentation.issue)
        assertEquals(trustedLocation, presentation.trustedLocation)
    }

    @Test
    fun `no trusted location remains no fix`() {
        val presentation = presentLocationFixStatus(
            fixStatus = LocationFixStatus(
                quality = LocationFixQuality.NoFix,
                trustedLocation = null,
                issue = LocationFixIssue.NoProviders,
                observedAtMillis = 1_700_000_000_000L,
            ),
            nowMillis = 1_700_000_100_000L,
        )

        assertEquals(TrackingLocationUiState.NoFix, presentation.state)
    }

    private fun mockLocation(
        provider: String,
        time: Long,
        accuracy: Float,
    ): Location {
        return mock<Location>().also { location ->
            whenever(location.provider).thenReturn(provider)
            whenever(location.time).thenReturn(time)
            whenever(location.hasAccuracy()).thenReturn(true)
            whenever(location.accuracy).thenReturn(accuracy)
        }
    }
}

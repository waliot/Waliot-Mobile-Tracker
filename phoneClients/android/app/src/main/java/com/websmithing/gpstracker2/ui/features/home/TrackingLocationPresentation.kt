package com.websmithing.gpstracker2.ui.features.home

import android.location.Location
import com.websmithing.gpstracker2.repository.location.LocationFixIssue
import com.websmithing.gpstracker2.repository.location.LocationFixQuality
import com.websmithing.gpstracker2.repository.location.LocationFixStatus

internal const val STALE_FIX_TIMEOUT_MILLIS = 5 * 60 * 1000L

enum class TrackingLocationUiState {
    NoFix,
    FreshGps,
    FreshDegraded,
    StaleGps,
    StaleDegraded,
    Suspect,
}

data class TrackingLocationPresentation(
    val state: TrackingLocationUiState = TrackingLocationUiState.NoFix,
    val trustedLocation: Location? = null,
    val issue: LocationFixIssue? = null,
    val provider: String? = null,
    val accuracyMeters: Float? = null,
    val fixAgeMillis: Long? = null,
)

internal fun presentLocationFixStatus(
    fixStatus: LocationFixStatus,
    nowMillis: Long,
): TrackingLocationPresentation {
    if (fixStatus.quality == LocationFixQuality.Suspect) {
        val trustedLocation = fixStatus.trustedLocation
        return TrackingLocationPresentation(
            state = TrackingLocationUiState.Suspect,
            trustedLocation = trustedLocation,
            issue = fixStatus.issue,
            provider = fixStatus.provider ?: trustedLocation?.provider,
            accuracyMeters = fixStatus.accuracyMeters,
            fixAgeMillis = trustedLocation?.time
                ?.takeIf { it > 0L }
                ?.let { (nowMillis - it).coerceAtLeast(0L) },
        )
    }

    val trustedLocation = fixStatus.trustedLocation
    if (trustedLocation == null) {
        return TrackingLocationPresentation(
            state = TrackingLocationUiState.NoFix,
            trustedLocation = null,
            issue = fixStatus.issue,
            provider = fixStatus.provider,
            accuracyMeters = fixStatus.accuracyMeters,
            fixAgeMillis = null,
        )
    }

    val fixAgeMillis = (nowMillis - trustedLocation.time).coerceAtLeast(0L)
    val degraded = when (fixStatus.quality) {
        LocationFixQuality.TrustedDegraded -> true
        LocationFixQuality.TrustedGps -> false
        LocationFixQuality.NoFix -> fixStatus.trustedFixDegraded
        LocationFixQuality.Suspect -> false
    }

    val state = if (fixAgeMillis > STALE_FIX_TIMEOUT_MILLIS) {
        if (degraded) TrackingLocationUiState.StaleDegraded else TrackingLocationUiState.StaleGps
    } else {
        if (degraded) TrackingLocationUiState.FreshDegraded else TrackingLocationUiState.FreshGps
    }

    return TrackingLocationPresentation(
        state = state,
        trustedLocation = trustedLocation,
        issue = fixStatus.issue,
        provider = fixStatus.provider ?: trustedLocation.provider,
        accuracyMeters = fixStatus.accuracyMeters.takeIf { it != null } ?: trustedLocation.accuracy.takeIf { trustedLocation.hasAccuracy() },
        fixAgeMillis = fixAgeMillis,
    )
}

package com.websmithing.gpstracker2.repository.location

import android.location.Location

enum class LocationFixQuality {
    NoFix,
    TrustedGps,
    TrustedDegraded,
    Suspect,
}

enum class LocationFixIssue {
    StaleFix,
    MockFix,
    ImpossibleJump,
    TimestampRegression,
    NoProviders,
}

data class LocationFixStatus(
    val quality: LocationFixQuality = LocationFixQuality.NoFix,
    val trustedLocation: Location? = null,
    val trustedFixDegraded: Boolean = false,
    val issue: LocationFixIssue? = null,
    val provider: String? = null,
    val accuracyMeters: Float? = null,
    val observedAtMillis: Long = 0L,
)

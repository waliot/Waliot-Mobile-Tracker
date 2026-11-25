package com.websmithing.gpstracker2.ui.features.home

internal enum class TrackingToggleBlocker {
    MissingConfiguration,
    MissingForegroundLocationPermission,
    MissingBackgroundLocationPermission,
}

internal data class TrackingToggleDecision(
    val shouldProceed: Boolean,
    val blocker: TrackingToggleBlocker? = null,
    val showBatteryOptimizationWarning: Boolean = false,
)

internal fun decideTrackingToggle(
    isTracking: Boolean,
    canRunTracking: Boolean,
    hasForegroundLocationPermission: Boolean,
    requiresBackgroundLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    hasShownBatteryOptimizationWarning: Boolean,
): TrackingToggleDecision {
    if (isTracking) {
        return TrackingToggleDecision(shouldProceed = true)
    }

    if (!canRunTracking) {
        return TrackingToggleDecision(
            shouldProceed = false,
            blocker = TrackingToggleBlocker.MissingConfiguration
        )
    }

    if (!hasForegroundLocationPermission) {
        return TrackingToggleDecision(
            shouldProceed = false,
            blocker = TrackingToggleBlocker.MissingForegroundLocationPermission
        )
    }

    if (requiresBackgroundLocationPermission && !hasBackgroundLocationPermission) {
        return TrackingToggleDecision(
            shouldProceed = false,
            blocker = TrackingToggleBlocker.MissingBackgroundLocationPermission
        )
    }

    return TrackingToggleDecision(
        shouldProceed = true,
        showBatteryOptimizationWarning = !isIgnoringBatteryOptimizations && !hasShownBatteryOptimizationWarning
    )
}

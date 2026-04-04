package com.websmithing.gpstracker2.ui.features.home.components

internal enum class ForegroundPermissionDecision {
    Allow,
    RequestBackgroundPermission,
    ShowRationale,
    Deny,
}

internal fun decideForegroundPermissionResult(
    fineLocationGranted: Boolean,
    coarseLocationGranted: Boolean,
    isBackgroundLocationRequired: Boolean,
    hasBackgroundLocationPermission: Boolean,
    anyShouldShowRationale: Boolean,
): ForegroundPermissionDecision {
    val anyForegroundLocationGranted = fineLocationGranted || coarseLocationGranted
    if (anyForegroundLocationGranted) {
        if (!isBackgroundLocationRequired || hasBackgroundLocationPermission) {
            return ForegroundPermissionDecision.Allow
        }

        return ForegroundPermissionDecision.RequestBackgroundPermission
    }

    return if (anyShouldShowRationale) {
        ForegroundPermissionDecision.ShowRationale
    } else {
        ForegroundPermissionDecision.Deny
    }
}

internal enum class BackgroundPermissionDecision {
    Allow,
    Deny,
}

internal fun decideBackgroundPermissionResult(granted: Boolean): BackgroundPermissionDecision {
    return if (granted) {
        BackgroundPermissionDecision.Allow
    } else {
        BackgroundPermissionDecision.Deny
    }
}

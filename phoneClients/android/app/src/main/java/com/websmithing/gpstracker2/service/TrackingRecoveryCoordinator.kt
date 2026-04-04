package com.websmithing.gpstracker2.service

import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRecoveryCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val trackingServiceController: TrackingServiceController,
    private val trackingPermissionChecker: TrackingPermissionChecker,
    ) {

    fun restoreTrackingIfRequired() {
        if (!settingsRepository.peekTrackingState()) {
            return
        }

        if (!trackingPermissionChecker.canRestoreTrackingFromBackground()) {
            return
        }

        trackingServiceController.startTracking()
    }
}

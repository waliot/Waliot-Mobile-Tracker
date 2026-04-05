package com.websmithing.gpstracker2.service

import com.websmithing.gpstracker2.testing.FakeSettingsRepository
import com.websmithing.gpstracker2.testing.FakeTrackingServiceController
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingRecoveryCoordinatorTest {

    @Test
    fun `restoreTrackingIfRequired starts tracking when persisted state is enabled and permissions allow background recovery`() {
        val settingsRepository = FakeSettingsRepository(trackingState = true)
        val trackingServiceController = FakeTrackingServiceController()
        val coordinator = TrackingRecoveryCoordinator(
            settingsRepository = settingsRepository,
            trackingServiceController = trackingServiceController,
            trackingPermissionChecker = TrackingPermissionChecker(
                sdkInt = 35,
                hasForegroundLocationPermission = { true },
                hasBackgroundLocationPermission = { true },
            ),
        )

        coordinator.restoreTrackingIfRequired()

        assertEquals(listOf("start"), trackingServiceController.actions)
    }

    @Test
    fun `restoreTrackingIfRequired does nothing when persisted state is disabled`() {
        val settingsRepository = FakeSettingsRepository(trackingState = false)
        val trackingServiceController = FakeTrackingServiceController()
        val coordinator = TrackingRecoveryCoordinator(
            settingsRepository = settingsRepository,
            trackingServiceController = trackingServiceController,
            trackingPermissionChecker = TrackingPermissionChecker(
                sdkInt = 35,
                hasForegroundLocationPermission = { true },
                hasBackgroundLocationPermission = { true },
            ),
        )

        coordinator.restoreTrackingIfRequired()

        assertEquals(emptyList<String>(), trackingServiceController.actions)
    }

    @Test
    fun `restoreTrackingIfRequired skips recovery when background permission is missing on modern Android`() {
        val settingsRepository = FakeSettingsRepository(trackingState = true)
        val trackingServiceController = FakeTrackingServiceController()
        val coordinator = TrackingRecoveryCoordinator(
            settingsRepository = settingsRepository,
            trackingServiceController = trackingServiceController,
            trackingPermissionChecker = TrackingPermissionChecker(
                sdkInt = 35,
                hasForegroundLocationPermission = { true },
                hasBackgroundLocationPermission = { false },
            ),
        )

        coordinator.restoreTrackingIfRequired()

        assertEquals(emptyList<String>(), trackingServiceController.actions)
    }

    @Test
    fun `restoreTrackingIfRequired allows pre Android 10 recovery without background permission`() {
        val settingsRepository = FakeSettingsRepository(trackingState = true)
        val trackingServiceController = FakeTrackingServiceController()
        val coordinator = TrackingRecoveryCoordinator(
            settingsRepository = settingsRepository,
            trackingServiceController = trackingServiceController,
            trackingPermissionChecker = TrackingPermissionChecker(
                sdkInt = 28,
                hasForegroundLocationPermission = { true },
                hasBackgroundLocationPermission = { false },
            ),
        )

        coordinator.restoreTrackingIfRequired()

        assertEquals(listOf("start"), trackingServiceController.actions)
    }
}

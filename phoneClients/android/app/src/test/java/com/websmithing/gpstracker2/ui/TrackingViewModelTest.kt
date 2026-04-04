package com.websmithing.gpstracker2.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.websmithing.gpstracker2.repository.location.LocationConsumer
import com.websmithing.gpstracker2.testing.FakeLocationRepository
import com.websmithing.gpstracker2.testing.FakeSettingsRepository
import com.websmithing.gpstracker2.testing.FakeTrackingServiceController
import com.websmithing.gpstracker2.testing.FakeUploadRepository
import com.websmithing.gpstracker2.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init loads settings and restores tracking service when persisted tracking is enabled`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            trackingState = true,
            trackerIdentifier = "12345",
            uploadServer = "127.0.0.1:30032",
            uploadTimeInterval = 7,
            bufferTimeInterval = 3,
            bufferDistanceInterval = 250,
            language = "en",
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )

        advanceUntilIdle()

        assertTrue(viewModel.isTracking.value == true)
        assertEquals("12345", viewModel.trackerIdentifier.value)
        assertEquals("127.0.0.1:30032", viewModel.uploadServer.value)
        assertEquals(7, viewModel.uploadTimeInterval.value)
        assertEquals(3, viewModel.bufferTimeInterval.value)
        assertEquals(250, viewModel.bufferDistanceInterval.value)
        assertEquals("en", viewModel.language.value)
        assertEquals(listOf("ensure"), trackingServiceController.actions)
    }

    @Test
    fun `startTracking persists state resets upload status and starts service`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.startTracking()
        advanceUntilIdle()

        assertTrue(viewModel.isTracking.value == true)
        assertTrue(settingsRepository.getTrackingState())
        assertEquals(1, uploadRepository.resetCalls)
        assertEquals(listOf("start"), trackingServiceController.actions)
    }

    @Test
    fun `stopTracking persists state and sends explicit stop command to service`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(trackingState = true)
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.stopTracking()
        advanceUntilIdle()

        assertFalse(viewModel.isTracking.value == true)
        assertFalse(settingsRepository.getTrackingState())
        assertEquals(listOf("ensure", "stop"), trackingServiceController.actions)
    }

    @Test
    fun `changing upload interval while tracking refreshes service instead of restarting tracking state`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(trackingState = true, uploadTimeInterval = 5)
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.onUploadTimeIntervalChanged("10")
        advanceUntilIdle()

        assertEquals(10, settingsRepository.getUploadTimeInterval())
        assertEquals(listOf("ensure", "refresh"), trackingServiceController.actions)
    }

    @Test
    fun `changing buffer distance interval while tracking is disabled does not touch service control flow`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(trackingState = false, bufferDistanceInterval = 100)
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.onBufferDistanceIntervalChanged("250")
        advanceUntilIdle()

        assertEquals(250, settingsRepository.getBufferDistanceInterval())
        assertTrue(trackingServiceController.actions.isEmpty())
    }

    @Test
    fun `changing buffer time interval while tracking is active updates settings without refreshing service`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(trackingState = true, bufferTimeInterval = 5)
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.onBufferTimeIntervalChanged("2")
        advanceUntilIdle()

        assertEquals(2, settingsRepository.getBufferTimeInterval())
        assertEquals(listOf("ensure"), trackingServiceController.actions)
    }

    @Test
    fun `changing tracker identifier while tracking is active updates settings without restarting service`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            trackingState = true,
            trackerIdentifier = "12345",
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.onTrackerIdentifierChanged("67890")
        advanceUntilIdle()

        assertEquals("67890", settingsRepository.getTrackerIdentifier())
        assertEquals("67890", viewModel.trackerIdentifier.value)
        assertEquals(listOf("ensure"), trackingServiceController.actions)
    }

    @Test
    fun `foreground location controls use the foreground consumer only`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingServiceController = FakeTrackingServiceController()

        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingServiceController = trackingServiceController,
        )
        advanceUntilIdle()

        viewModel.startForegroundLocation()
        viewModel.stopForegroundLocation()

        assertEquals(listOf(LocationConsumer.ForegroundUi), locationRepository.startedConsumers)
        assertEquals(listOf(LocationConsumer.ForegroundUi), locationRepository.stoppedConsumers)
        assertTrue(locationRepository.activeConsumers.isEmpty())
    }

    @Test
    fun `battery optimization warning flag is restored and can be marked as shown`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(batteryOptimizationWarningShown = false)
        val viewModel = TrackingViewModel(
            settingsRepository = settingsRepository,
            locationRepository = FakeLocationRepository(),
            uploadRepository = FakeUploadRepository(),
            trackingServiceController = FakeTrackingServiceController(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.batteryOptimizationWarningShown.value == true)

        viewModel.markBatteryOptimizationWarningShown()
        advanceUntilIdle()

        assertTrue(viewModel.batteryOptimizationWarningShown.value == true)
        assertTrue(settingsRepository.getBatteryOptimizationWarningShown())
    }
}

package com.websmithing.gpstracker2.service

import android.location.Location
import com.websmithing.gpstracker2.testing.FakeLocationRepository
import com.websmithing.gpstracker2.testing.FakeSettingsRepository
import com.websmithing.gpstracker2.testing.FakeTrackingBufferStore
import com.websmithing.gpstracker2.testing.FakeUploadRepository
import com.websmithing.gpstracker2.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingRuntimeControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `start is idempotent and starts location updates only once`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
        )

        try {
            assertTrue(controller.start())
            assertFalse(controller.start())

            advanceUntilIdle()

            assertTrue(controller.isTrackingActive)
            assertEquals(1, locationRepository.startCalls)
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `refresh while active restarts workers without clearing buffered points`() = runTest(mainDispatcherRule.dispatcher) {
        val bufferSnapshots = mutableListOf<Int>()
        val settingsRepository = FakeSettingsRepository()
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            onBufferCountChanged = { bufferSnapshots += it },
        )
        val location = mockLocation(distanceTo = 0f)

        try {
            assertTrue(controller.start())
            locationRepository.emit(location)
            advanceUntilIdle()

            assertTrue(controller.refresh())
            advanceUntilIdle()

            assertEquals(2, locationRepository.startCalls)
            assertTrue(controller.isTrackingActive)
            assertEquals(listOf(1), bufferSnapshots.filter { it > 0 }.distinct())
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `stop preserves queued points, resets collection anchor, and stops location updates`() = runTest(mainDispatcherRule.dispatcher) {
        val bufferSnapshots = mutableListOf<Int>()
        val settingsRepository = FakeSettingsRepository()
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            onBufferCountChanged = { bufferSnapshots += it },
        )

        try {
            controller.start()
            locationRepository.emit(mockLocation(distanceTo = 0f))
            advanceUntilIdle()

            assertTrue(controller.stop())
            advanceUntilIdle()

            assertFalse(controller.isTrackingActive)
            assertEquals(1, locationRepository.stopCalls)
            assertEquals(1, bufferSnapshots.last())
            assertEquals(1, trackingBufferStore.snapshot.bufferedLocations.size)
            assertEquals(0, trackingBufferStore.clearCalls)
            assertTrue(trackingBufferStore.snapshot.lastBufferedLocation == null)
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `locations emitted after stop are not appended to the buffer`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
        )
        val first = mockLocation(distanceToOther = 150f)
        val second = mockLocation(distanceToOther = 150f)

        try {
            controller.start()
            locationRepository.emit(first)
            advanceUntilIdle()

            assertTrue(controller.stop())
            advanceUntilIdle()

            locationRepository.emit(second)
            advanceUntilIdle()

            assertFalse(controller.isTrackingActive)
            assertEquals(1, trackingBufferStore.snapshot.bufferedLocations.size)
            assertSame(first, trackingBufferStore.snapshot.bufferedLocations.single())
            assertEquals(1, trackingBufferStore.appendCalls)
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `stop keeps upload loop active until buffered points are drained`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "drain-after-stop",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )
        val location = mockLocation(distanceToOther = 150f)

        try {
            controller.start()
            locationRepository.emit(location)
            advanceUntilIdle()

            assertTrue(controller.stop())
            advanceUntilIdle()

            gate.tick()
            advanceUntilIdle()

            assertFalse(controller.isTrackingActive)
            assertEquals(listOf("drain-after-stop"), uploadRepository.uploadedTrackerIds)
            assertSame(location, uploadRepository.uploadedLocations.single())
            assertTrue(trackingBufferStore.snapshot.bufferedLocations.isEmpty())
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `failed upload after stop is retried on the next interval without restarting tracking`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "retry-after-stop",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository().apply {
            enqueueUploadResults(false, true)
        }
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )
        val location = mockLocation(distanceToOther = 150f)

        try {
            controller.start()
            locationRepository.emit(location)
            advanceUntilIdle()

            assertTrue(controller.stop())
            advanceUntilIdle()

            gate.tick()
            advanceUntilIdle()

            assertFalse(controller.isTrackingActive)
            assertEquals(1, trackingBufferStore.snapshot.bufferedLocations.size)

            gate.tick()
            advanceUntilIdle()

            assertEquals(listOf("retry-after-stop", "retry-after-stop"), uploadRepository.uploadedTrackerIds)
            assertSame(location, uploadRepository.uploadedLocations[0])
            assertSame(location, uploadRepository.uploadedLocations[1])
            assertTrue(trackingBufferStore.snapshot.bufferedLocations.isEmpty())
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `upload loop uses the latest tracker identifier on each cycle`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "old-id",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )

        try {
            controller.start()
            locationRepository.emit(mockLocation(distanceTo = 0f))
            advanceUntilIdle()

            settingsRepository.setTrackingIdentifier("new-id")
            gate.tick()
            advanceUntilIdle()

            assertEquals(listOf("new-id"), uploadRepository.uploadedTrackerIds)
            assertTrue(gate.requestedIntervals.isNotEmpty())
            assertTrue(gate.requestedIntervals.all { it == 5L })
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `destroy preserves buffered points and restores them on next start`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "restored-id",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val restoredBufferCounts = mutableListOf<Int>()
        val persistedLocation = mockLocation(distanceTo = 0f)

        val firstController = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )

        firstController.start()
        locationRepository.emit(persistedLocation)
        advanceUntilIdle()
        firstController.destroy()
        advanceUntilIdle()

        assertEquals(1, trackingBufferStore.snapshot.bufferedLocations.size)

        val secondController = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            onBufferCountChanged = { restoredBufferCounts += it },
            awaitNextUpload = gate::await,
        )

        try {
            assertTrue(secondController.start())
            advanceUntilIdle()

            assertEquals(listOf(1), restoredBufferCounts.filter { it > 0 }.distinct())

            gate.tick()
            advanceUntilIdle()

            assertEquals(listOf("restored-id"), uploadRepository.uploadedTrackerIds)
            assertTrue(trackingBufferStore.snapshot.bufferedLocations.isEmpty())
        } finally {
            secondController.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `failed upload stays persisted and is retried after restart`() = runTest(mainDispatcherRule.dispatcher) {
        val firstGate = UploadGate()
        val secondGate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "retry-id",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository().apply {
            nextUploadResult = false
        }
        val trackingBufferStore = FakeTrackingBufferStore()
        val location = mockLocation(distanceTo = 0f)

        val firstController = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = firstGate::await,
        )

        firstController.start()
        locationRepository.emit(location)
        advanceUntilIdle()

        firstGate.tick()
        advanceUntilIdle()

        assertEquals(1, trackingBufferStore.snapshot.bufferedLocations.size)
        firstController.destroy()
        advanceUntilIdle()

        uploadRepository.nextUploadResult = true

        val secondController = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = secondGate::await,
        )

        try {
            secondController.start()
            advanceUntilIdle()

            secondGate.tick()
            advanceUntilIdle()

            assertEquals(listOf("retry-id", "retry-id"), uploadRepository.uploadedTrackerIds)
            assertTrue(trackingBufferStore.snapshot.bufferedLocations.isEmpty())
        } finally {
            secondController.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `failed drain keeps remaining backlog order and retries from failed point`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "retry-id",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository().apply {
            enqueueUploadResults(true, false, true, true)
        }
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )
        val first = mockLocation(distanceToOther = 150f)
        val second = mockLocation(distanceToOther = 150f)
        val third = mockLocation(distanceTo = 150f)

        try {
            controller.start()
            locationRepository.emit(first)
            advanceUntilIdle()
            locationRepository.emit(second)
            advanceUntilIdle()
            locationRepository.emit(third)
            advanceUntilIdle()

            assertEquals(3, trackingBufferStore.snapshot.bufferedLocations.size)

            gate.tick()
            advanceUntilIdle()

            assertEquals(listOf("retry-id", "retry-id"), uploadRepository.uploadedTrackerIds)
            assertEquals(2, uploadRepository.uploadedLocations.size)
            assertSame(first, uploadRepository.uploadedLocations[0])
            assertSame(second, uploadRepository.uploadedLocations[1])
            assertEquals(2, trackingBufferStore.snapshot.bufferedLocations.size)
            assertSame(second, trackingBufferStore.snapshot.bufferedLocations[0])
            assertSame(third, trackingBufferStore.snapshot.bufferedLocations[1])

            gate.tick()
            advanceUntilIdle()

            assertEquals(
                listOf("retry-id", "retry-id", "retry-id", "retry-id"),
                uploadRepository.uploadedTrackerIds
            )
            assertEquals(4, uploadRepository.uploadedLocations.size)
            assertSame(second, uploadRepository.uploadedLocations[2])
            assertSame(third, uploadRepository.uploadedLocations[3])
            assertTrue(trackingBufferStore.snapshot.bufferedLocations.isEmpty())
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `regular add and drain path uses incremental store operations instead of full snapshot rewrites`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "incremental-id",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )
        val first = mockLocation(distanceToOther = 150f)
        val second = mockLocation(distanceToOther = 150f)

        try {
            controller.start()
            locationRepository.emit(first)
            advanceUntilIdle()
            locationRepository.emit(second)
            advanceUntilIdle()

            assertEquals(2, trackingBufferStore.appendCalls)
            assertEquals(0, trackingBufferStore.saveCalls)

            gate.tick()
            advanceUntilIdle()

            assertEquals(2, trackingBufferStore.removeOldestCalls)
            assertEquals(0, trackingBufferStore.saveCalls)
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `buffer keeps only latest 1000 points by dropping the oldest entries`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            bufferDistanceInterval = 100,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
        )
        val emittedLocations = List(1_001) { mockLocation(distanceToOther = 150f) }

        try {
            controller.start()

            emittedLocations.forEach { location ->
                locationRepository.emit(location)
                advanceUntilIdle()
            }

            assertEquals(1_000, trackingBufferStore.snapshot.bufferedLocations.size)
            assertSame(emittedLocations[1], trackingBufferStore.snapshot.bufferedLocations.first())
            assertSame(emittedLocations.last(), trackingBufferStore.snapshot.bufferedLocations.last())
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `buffering time threshold is independent from upload interval`() = runTest(mainDispatcherRule.dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            uploadTimeInterval = 60,
            bufferTimeInterval = 5,
            bufferDistanceInterval = 1_000,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
        )
        val firstTimestamp = 1_000L
        val first = mockLocation(time = firstTimestamp, distanceToOther = 0f)
        val second = mockLocation(
            time = firstTimestamp + java.util.concurrent.TimeUnit.MINUTES.toMillis(6),
            distanceToOther = 0f,
            distanceToSelf = 0f,
        )

        try {
            controller.start()
            locationRepository.emit(first)
            advanceUntilIdle()
            locationRepository.emit(second)
            advanceUntilIdle()

            assertEquals(2, trackingBufferStore.snapshot.bufferedLocations.size)
            assertSame(first, trackingBufferStore.snapshot.bufferedLocations[0])
            assertSame(second, trackingBufferStore.snapshot.bufferedLocations[1])
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    @Test
    fun `refresh keeps backlog and applies updated settings on next upload cycle`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = UploadGate()
        val settingsRepository = FakeSettingsRepository(
            trackerIdentifier = "old-id",
            uploadTimeInterval = 5,
        )
        val locationRepository = FakeLocationRepository()
        val uploadRepository = FakeUploadRepository()
        val trackingBufferStore = FakeTrackingBufferStore()
        val controller = createController(
            scope = this,
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            awaitNextUpload = gate::await,
        )
        val first = mockLocation(distanceToOther = 150f)
        val second = mockLocation(distanceToOther = 150f, distanceToSelf = 0f)

        try {
            controller.start()
            locationRepository.emit(first)
            advanceUntilIdle()
            locationRepository.emit(second)
            advanceUntilIdle()

            assertEquals(2, trackingBufferStore.snapshot.bufferedLocations.size)
            assertEquals(listOf(5L), gate.requestedIntervals)

            settingsRepository.setTrackingIdentifier("new-id")
            settingsRepository.setUploadTimeInterval(10)

            assertTrue(controller.refresh())
            advanceUntilIdle()

            assertEquals(2, locationRepository.startCalls)
            assertEquals(2, trackingBufferStore.snapshot.bufferedLocations.size)
            assertEquals(listOf(5L, 10L), gate.requestedIntervals.take(2))

            gate.tick()
            advanceUntilIdle()

            assertEquals(listOf("new-id", "new-id"), uploadRepository.uploadedTrackerIds)
            assertSame(first, uploadRepository.uploadedLocations[0])
            assertSame(second, uploadRepository.uploadedLocations[1])
            assertTrue(trackingBufferStore.snapshot.bufferedLocations.isEmpty())
        } finally {
            controller.destroy()
            advanceUntilIdle()
        }
    }

    private fun createController(
        scope: CoroutineScope,
        settingsRepository: FakeSettingsRepository,
        locationRepository: FakeLocationRepository,
        uploadRepository: FakeUploadRepository,
        trackingBufferStore: FakeTrackingBufferStore = FakeTrackingBufferStore(),
        onBufferCountChanged: (Int) -> Unit = {},
        awaitNextUpload: suspend (Long) -> Unit = { CompletableDeferred<Unit>().await() },
    ): TrackingRuntimeController {
        return TrackingRuntimeController(
            settingsRepository = settingsRepository,
            locationRepository = locationRepository,
            uploadRepository = uploadRepository,
            trackingBufferStore = trackingBufferStore,
            serviceScope = scope,
            onBufferCountChanged = onBufferCountChanged,
            awaitNextUpload = awaitNextUpload,
        )
    }

    private fun mockLocation(distanceTo: Float): Location {
        return mockLocation(distanceToOther = distanceTo)
    }

    private fun mockLocation(
        distanceToOther: Float,
        distanceToSelf: Float = distanceToOther,
        time: Long = System.currentTimeMillis(),
    ): Location {
        val location = mock<Location>()
        whenever(location.time).thenReturn(time)
        whenever(location.distanceTo(any())).thenAnswer { invocation ->
            val target = invocation.arguments.firstOrNull()
            if (target === location) distanceToSelf else distanceToOther
        }
        return location
    }

    private class UploadGate {
        val requestedIntervals = mutableListOf<Long>()
        private var nextTick = CompletableDeferred<Unit>()

        suspend fun await(intervalMinutes: Long) {
            requestedIntervals += intervalMinutes
            nextTick.await()
            nextTick = CompletableDeferred()
        }

        fun tick() {
            nextTick.complete(Unit)
        }
    }
}

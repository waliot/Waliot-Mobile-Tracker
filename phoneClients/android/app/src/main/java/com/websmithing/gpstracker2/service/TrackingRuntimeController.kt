package com.websmithing.gpstracker2.service

import android.location.Location
import com.websmithing.gpstracker2.repository.location.LocationConsumer
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

internal class TrackingRuntimeController(
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val uploadRepository: UploadRepository,
    private val trackingBufferStore: TrackingBufferStore,
    private val serviceScope: CoroutineScope,
    private val onBufferCountChanged: (Int) -> Unit = {},
    private val onUploadBacklogDrained: () -> Unit = {},
    private val awaitNextUpload: suspend (Long) -> Unit = { intervalMinutes ->
        delay(TimeUnit.MINUTES.toMillis(intervalMinutes))
    },
) {

    private companion object {
        const val MAX_BUFFERED_LOCATIONS = 1_000
    }

    private val bufferMutex = Mutex()
    private val uploadMutex = Mutex()

    private val locationBuffer = mutableListOf<Location>()
    private var lastBufferLocation: Location? = null
    private var locationCollectionJob: Job? = null
    private var uploadJob: Job? = null
    private var currentBufferCount: Int = 0
    private var isInitialized: Boolean = false

    var isTrackingActive: Boolean = false
        private set

    fun start(): Boolean {
        if (isTrackingActive) return false

        restoreBufferStateIfNeeded()
        isTrackingActive = true
        startLocationCollectionWorker()
        reconcileUploadWorker()
        return true
    }

    fun stop(): Boolean {
        if (!isTrackingActive) return false

        isTrackingActive = false
        stopLocationCollectionWorker()
        persistStoppedBufferState()
        locationRepository.stop(LocationConsumer.TrackingService)
        reconcileUploadWorker()
        return true
    }

    fun refresh(): Boolean {
        restoreBufferStateIfNeeded()
        if (!shouldKeepServiceRunning()) return false

        if (isTrackingActive) {
            startLocationCollectionWorker()
        }
        reconcileUploadWorker(forceRestart = true)
        return true
    }

    fun destroy() {
        stopLocationCollectionWorker()
        stopUploadWorker()
        isTrackingActive = false
        isInitialized = false
        locationRepository.stop(LocationConsumer.TrackingService)
    }

    fun hasPendingBufferedLocations(): Boolean = currentBufferCount > 0

    fun shouldKeepServiceRunning(): Boolean = isTrackingActive || hasPendingBufferedLocations()

    private fun startLocationCollectionWorker() {
        stopLocationCollectionWorker()
        locationRepository.start(LocationConsumer.TrackingService)

        locationCollectionJob = serviceScope.launch {
            locationRepository.currentLocation.collect { location ->
                if (location != null) {
                    val bufferTimeInterval = settingsRepository.getBufferTimeInterval().toLong()
                    val bufferDistanceInterval = settingsRepository.getBufferDistanceInterval().toLong()

                    if (shouldAddToBuffer(location, bufferTimeInterval, bufferDistanceInterval)) {
                        addToBuffer(location)
                    }
                }
            }
        }
    }

    private fun stopLocationCollectionWorker() {
        locationCollectionJob?.cancel()
        locationCollectionJob = null
    }

    private fun stopUploadWorker() {
        uploadJob?.cancel()
        uploadJob = null
    }

    private fun reconcileUploadWorker(forceRestart: Boolean = false) {
        if (forceRestart) {
            stopUploadWorker()
        }

        if (!shouldKeepServiceRunning()) {
            stopUploadWorker()
            return
        }

        if (uploadJob?.isActive == true) {
            return
        }

        lateinit var job: Job
        job = serviceScope.launch {
            var backlogDrainedWhileUploadOnly = false

            try {
                while (shouldKeepServiceRunning()) {
                    val uploadTimeInterval = settingsRepository.getUploadTimeInterval().toLong()
                    awaitNextUpload(uploadTimeInterval)

                    val trackerId = settingsRepository.getTrackerIdentifier()
                    uploadBuffer(trackerId)
                }

                backlogDrainedWhileUploadOnly = !isTrackingActive && !hasPendingBufferedLocations()
            } finally {
                if (uploadJob === job) {
                    uploadJob = null
                }
                if (backlogDrainedWhileUploadOnly) {
                    onUploadBacklogDrained()
                }
            }
        }

        uploadJob = job
    }

    private fun shouldAddToBuffer(location: Location, bufferTimeInterval: Long, bufferDistanceInterval: Long): Boolean {
        val lastLoc = lastBufferLocation ?: return true

        val timeElapsed = (resolveTimestamp(location) - resolveTimestamp(lastLoc)).coerceAtLeast(0L)
        val distance = location.distanceTo(lastLoc)

        return timeElapsed >= TimeUnit.MINUTES.toMillis(bufferTimeInterval)
            || distance >= bufferDistanceInterval
    }

    private suspend fun uploadBuffer(trackerId: String) {
        uploadMutex.withLock {
            if (bufferMutex.withLock { locationBuffer.isEmpty() }) {
                return
            }

            while (true) {
                val next: Location = bufferMutex.withLock {
                    locationBuffer.firstOrNull() ?: return
                }

                val success = uploadRepository.uploadData(trackerId, next)

                if (!success) {
                    return
                }

                bufferMutex.withLock {
                    if (locationBuffer.isNotEmpty()) {
                        locationBuffer.removeAt(0)
                        trackingBufferStore.removeOldestBufferedLocation()
                        updateBufferCount(locationBuffer.size)
                    }
                }
            }
        }
    }

    private suspend fun addToBuffer(location: Location) {
        bufferMutex.withLock {
            lastBufferLocation = location
            locationBuffer.add(location)
            while (locationBuffer.size > MAX_BUFFERED_LOCATIONS) {
                locationBuffer.removeAt(0)
            }
            trackingBufferStore.appendBufferedLocation(
                location = location,
                maxSize = MAX_BUFFERED_LOCATIONS,
                lastBufferedLocation = lastBufferLocation,
            )
            updateBufferCount(locationBuffer.size)
        }
    }

    private fun restoreBufferStateIfNeeded() {
        if (isInitialized) return

        val restoredState = trackingBufferStore.loadState()
        val trimmedBuffer = restoredState.bufferedLocations.takeLast(MAX_BUFFERED_LOCATIONS)
        locationBuffer.clear()
        locationBuffer.addAll(trimmedBuffer)
        lastBufferLocation = restoredState.lastBufferedLocation
        updateBufferCount(locationBuffer.size)
        isInitialized = true

        if (trimmedBuffer.size != restoredState.bufferedLocations.size) {
            trackingBufferStore.saveState(locationBuffer.toList(), lastBufferLocation)
        }
    }

    private fun persistStoppedBufferState() {
        lastBufferLocation = null
        trackingBufferStore.replaceLastBufferedLocation(lastBufferLocation)
        updateBufferCount(locationBuffer.size)
    }

    private fun updateBufferCount(size: Int) {
        currentBufferCount = size
        onBufferCountChanged(size)
    }

    private fun resolveTimestamp(location: Location): Long {
        return location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
    }
}

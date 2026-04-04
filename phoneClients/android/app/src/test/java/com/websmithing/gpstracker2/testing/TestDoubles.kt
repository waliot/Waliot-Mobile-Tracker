package com.websmithing.gpstracker2.testing

import android.location.Location
import com.websmithing.gpstracker2.repository.location.LocationConsumer
import com.websmithing.gpstracker2.repository.location.LocationFixQuality
import com.websmithing.gpstracker2.repository.location.LocationFixStatus
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.location.isDegradedLocation
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.service.TrackingBufferSnapshot
import com.websmithing.gpstracker2.service.TrackingBufferStore
import com.websmithing.gpstracker2.service.TrackingServiceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSettingsRepository(
    private var trackingState: Boolean = false,
    private var trackerIdentifier: String = "",
    private var uploadServer: String = "device.waliot.com:30032",
    private var uploadTimeInterval: Int = 5,
    private var bufferTimeInterval: Int = 5,
    private var bufferDistanceInterval: Int = 100,
    private var language: String = "ru",
    private var batteryOptimizationWarningShown: Boolean = false,
) : SettingsRepository {

    var setTrackingStateCalls = 0
        private set

    override fun peekTrackingState(): Boolean = trackingState

    override suspend fun getTrackingState(): Boolean = trackingState

    override suspend fun setTrackingState(isTracking: Boolean) {
        setTrackingStateCalls += 1
        trackingState = isTracking
    }

    override suspend fun getTrackerIdentifier(): String = trackerIdentifier

    override suspend fun setTrackingIdentifier(trackerIdentifier: String) {
        this.trackerIdentifier = trackerIdentifier
    }

    override suspend fun getUploadServer(): String = uploadServer

    override suspend fun setUploadServer(serverAddress: String) {
        uploadServer = serverAddress
    }

    override suspend fun getUploadTimeInterval(): Int = uploadTimeInterval

    override suspend fun setUploadTimeInterval(intervalMinutes: Int) {
        uploadTimeInterval = intervalMinutes
    }

    override suspend fun getBufferTimeInterval(): Int = bufferTimeInterval

    override suspend fun setBufferTimeInterval(intervalMinutes: Int) {
        bufferTimeInterval = intervalMinutes
    }

    override suspend fun getBufferDistanceInterval(): Int = bufferDistanceInterval

    override suspend fun setBufferDistanceInterval(intervalMeters: Int) {
        bufferDistanceInterval = intervalMeters
    }

    override fun peekLanguage(): String = language

    override suspend fun getLanguage(): String = language

    override suspend fun setLanguage(language: String) {
        this.language = language
    }

    override fun peekBatteryOptimizationWarningShown(): Boolean = batteryOptimizationWarningShown

    override suspend fun getBatteryOptimizationWarningShown(): Boolean = batteryOptimizationWarningShown

    override suspend fun setBatteryOptimizationWarningShown(shown: Boolean) {
        batteryOptimizationWarningShown = shown
    }
}

class FakeLocationRepository : LocationRepository {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation
    private val _locationFixStatus = MutableStateFlow(LocationFixStatus())
    override val locationFixStatus: StateFlow<LocationFixStatus> = _locationFixStatus

    var startCalls = 0
        private set
    var stopCalls = 0
        private set
    val startedConsumers = mutableListOf<LocationConsumer>()
    val stoppedConsumers = mutableListOf<LocationConsumer>()
    val activeConsumers = linkedSetOf<LocationConsumer>()

    override fun start(consumer: LocationConsumer) {
        startCalls += 1
        startedConsumers += consumer
        activeConsumers += consumer
    }

    override fun stop(consumer: LocationConsumer) {
        stopCalls += 1
        stoppedConsumers += consumer
        activeConsumers.remove(consumer)
    }

    fun emit(location: Location?) {
        _currentLocation.value = location
        _locationFixStatus.value = if (location == null) {
            LocationFixStatus()
        } else {
            LocationFixStatus(
                quality = if (isDegradedLocation(location)) {
                    LocationFixQuality.TrustedDegraded
                } else {
                    LocationFixQuality.TrustedGps
                },
                trustedLocation = location,
                trustedFixDegraded = isDegradedLocation(location),
                provider = location.provider,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                observedAtMillis = location.time,
            )
        }
    }
}

class FakeUploadRepository : UploadRepository {
    private val _lastUploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    override val lastUploadStatus: StateFlow<UploadStatus> = _lastUploadStatus

    var resetCalls = 0
        private set
    val uploadedTrackerIds = mutableListOf<String>()
    val uploadedLocations = mutableListOf<Location>()
    var nextUploadResult: Boolean = true
    var nextOfflineResult: Boolean = false
    var uploadStatusTimestampMillis: Long = 1_700_000_000_000L
    private val scriptedUploadResults = ArrayDeque<Boolean>()

    override suspend fun resetUploadStatus() {
        resetCalls += 1
        _lastUploadStatus.value = UploadStatus.Idle
    }

    override suspend fun uploadData(trackerIdentifier: String, location: Location): Boolean {
        uploadedTrackerIds += trackerIdentifier
        uploadedLocations += location
        if (nextOfflineResult) {
            _lastUploadStatus.value = UploadStatus.Offline
            return false
        }
        val result = scriptedUploadResults.removeFirstOrNull() ?: nextUploadResult
        _lastUploadStatus.value = if (result) {
            UploadStatus.Success(uploadStatusTimestampMillis)
        } else {
            UploadStatus.Failure("upload failed")
        }
        return result
    }

    fun enqueueUploadResults(vararg results: Boolean) {
        scriptedUploadResults.addAll(results.toList())
    }
}

class FakeTrackingServiceController : TrackingServiceController {
    val actions = mutableListOf<String>()

    override fun startTracking() {
        actions += "start"
    }

    override fun stopTracking() {
        actions += "stop"
    }

    override fun refreshTracking() {
        actions += "refresh"
    }

    override fun ensureTrackingRunning() {
        actions += "ensure"
    }
}

class FakeTrackingBufferStore(
    initialState: TrackingBufferSnapshot = TrackingBufferSnapshot()
) : TrackingBufferStore {

    var snapshot: TrackingBufferSnapshot = initialState
        private set
    var saveCalls = 0
        private set
    var appendCalls = 0
        private set
    var removeOldestCalls = 0
        private set
    var replaceLastBufferedLocationCalls = 0
        private set
    var clearCalls = 0
        private set

    override fun loadState(): TrackingBufferSnapshot = snapshot.copy(
        bufferedLocations = snapshot.bufferedLocations.toList()
    )

    override fun saveState(bufferedLocations: List<Location>, lastBufferedLocation: Location?) {
        saveCalls += 1
        snapshot = TrackingBufferSnapshot(
            bufferedLocations = bufferedLocations.toList(),
            lastBufferedLocation = lastBufferedLocation
        )
    }

    override fun appendBufferedLocation(location: Location, maxSize: Int, lastBufferedLocation: Location?) {
        appendCalls += 1
        val nextBufferedLocations = (snapshot.bufferedLocations + location).takeLast(maxSize)
        snapshot = TrackingBufferSnapshot(
            bufferedLocations = nextBufferedLocations,
            lastBufferedLocation = lastBufferedLocation,
        )
    }

    override fun removeOldestBufferedLocation() {
        removeOldestCalls += 1
        snapshot = TrackingBufferSnapshot(
            bufferedLocations = snapshot.bufferedLocations.drop(1),
            lastBufferedLocation = snapshot.lastBufferedLocation,
        )
    }

    override fun replaceLastBufferedLocation(location: Location?) {
        replaceLastBufferedLocationCalls += 1
        snapshot = TrackingBufferSnapshot(
            bufferedLocations = snapshot.bufferedLocations,
            lastBufferedLocation = location,
        )
    }

    override fun clear() {
        clearCalls += 1
        snapshot = TrackingBufferSnapshot()
    }
}

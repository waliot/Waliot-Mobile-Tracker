package com.websmithing.gpstracker2.ui

import android.location.Location
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websmithing.gpstracker2.repository.location.LocationConsumer
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.service.TrackingService
import com.websmithing.gpstracker2.service.TrackingServiceController
import com.websmithing.gpstracker2.ui.features.home.TrackingLocationPresentation
import com.websmithing.gpstracker2.ui.features.home.presentLocationFixStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val uploadRepository: UploadRepository,
    private val trackingServiceController: TrackingServiceController,
) : ViewModel() {

    private val locationPresentationTicker = flow {
        emit(System.currentTimeMillis())
        while (true) {
            delay(30_000)
            emit(System.currentTimeMillis())
        }
    }

    private val _isTracking = MutableLiveData<Boolean>()
    val isTracking: LiveData<Boolean> = _isTracking

    private val _trackerIdentifier = MutableLiveData<String>()
    val trackerIdentifier: LiveData<String> = _trackerIdentifier

    private val _uploadServer = MutableLiveData<String>()
    val uploadServer: LiveData<String> = _uploadServer

    private val _uploadTimeInterval = MutableLiveData<Int>()
    val uploadTimeInterval: LiveData<Int> = _uploadTimeInterval

    private val _bufferTimeInterval = MutableLiveData<Int>()
    val bufferTimeInterval: LiveData<Int> = _bufferTimeInterval

    private val _bufferDistanceInterval = MutableLiveData<Int>()
    val bufferDistanceInterval: LiveData<Int> = _bufferDistanceInterval

    private val _language = MutableLiveData<String>()
    val language: LiveData<String> = _language

    private val _batteryOptimizationWarningShown = MutableLiveData<Boolean>()
    val batteryOptimizationWarningShown: LiveData<Boolean> = _batteryOptimizationWarningShown

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    val latestLocation: StateFlow<Location?> = locationRepository.currentLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val locationPresentation: StateFlow<TrackingLocationPresentation> = combine(
        locationRepository.locationFixStatus,
        locationPresentationTicker,
    ) { fixStatus, nowMillis ->
        presentLocationFixStatus(fixStatus, nowMillis)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TrackingLocationPresentation(),
    )

    val lastUploadStatus: StateFlow<UploadStatus> = uploadRepository.lastUploadStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadStatus.Idle)

    val bufferCount: StateFlow<Int> = TrackingService.bufferCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        _batteryOptimizationWarningShown.value = settingsRepository.peekBatteryOptimizationWarningShown()

        viewModelScope.launch {
            _isTracking.value = settingsRepository.getTrackingState()
            _trackerIdentifier.value = settingsRepository.getTrackerIdentifier()
            _uploadServer.value = settingsRepository.getUploadServer()
            _uploadTimeInterval.value = settingsRepository.getUploadTimeInterval()
            _bufferTimeInterval.value = settingsRepository.getBufferTimeInterval()
            _bufferDistanceInterval.value = settingsRepository.getBufferDistanceInterval()
            _language.value = settingsRepository.getLanguage()
            _batteryOptimizationWarningShown.value = settingsRepository.getBatteryOptimizationWarningShown()

            ensureTrackingServiceRunningIfRequired()
        }
    }

    //region SETTING CHANGES

    fun onTrackerIdentifierChanged(newValue: String) {
        val newTrackerIdentifier = newValue.trim()
        if (newTrackerIdentifier != _trackerIdentifier.value) {
            _trackerIdentifier.value = newTrackerIdentifier
            viewModelScope.launch {
                settingsRepository.setTrackingIdentifier(newTrackerIdentifier)
            }
        }
    }

    fun onUploadServerChanged(newValue: String) {
        val newServerAddress = newValue.trim()
        if (newServerAddress != _uploadServer.value) {
            _uploadServer.value = newServerAddress
            viewModelScope.launch {
                settingsRepository.setUploadServer(newServerAddress)
            }
        }
    }

    fun onUploadTimeIntervalChanged(newValue: String) {
        val newTimeInterval = newValue.toIntOrNull() ?: return
        if (newTimeInterval == _uploadTimeInterval.value) return

        _uploadTimeInterval.value = newTimeInterval
        viewModelScope.launch {
            settingsRepository.setUploadTimeInterval(newTimeInterval)
            refreshTrackingServiceIfRequired()
        }
    }

    fun onBufferTimeIntervalChanged(newValue: String) {
        val newTimeInterval = newValue.toIntOrNull() ?: return
        if (newTimeInterval == _bufferTimeInterval.value) return

        _bufferTimeInterval.value = newTimeInterval
        viewModelScope.launch {
            settingsRepository.setBufferTimeInterval(newTimeInterval)
        }
    }

    fun onBufferDistanceIntervalChanged(newValue: String) {
        val newDistanceInterval = newValue.toIntOrNull() ?: return
        if (newDistanceInterval == _bufferDistanceInterval.value) return

        _bufferDistanceInterval.value = newDistanceInterval
        viewModelScope.launch {
            settingsRepository.setBufferDistanceInterval(newDistanceInterval)
        }
    }

    fun onLanguageChanged(language: String) {
        if (language != _language.value) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language)
            )
            viewModelScope.launch {
                settingsRepository.setLanguage(language)
                _language.value = language
            }
        }
    }

    //endregion SETTING CHANGES

    //region MAP LOCATION UPDATE

    fun startForegroundLocation() = locationRepository.start(LocationConsumer.ForegroundUi)

    fun stopForegroundLocation() = locationRepository.stop(LocationConsumer.ForegroundUi)

    fun markBatteryOptimizationWarningShown() {
        if (_batteryOptimizationWarningShown.value == true) return

        _batteryOptimizationWarningShown.value = true
        viewModelScope.launch {
            settingsRepository.setBatteryOptimizationWarningShown(true)
        }
    }

    //endregion MAP LOCATION UPDATE

    //region UPLOADING CONTROL

    fun startTracking() {
        updateTrackingState(true)
    }

    fun stopTracking() {
        updateTrackingState(false)
    }

    fun switchTrackingState() {
        if (isTracking.value == true) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private fun updateTrackingState(shouldTrack: Boolean): Job {
        if (_isTracking.value == shouldTrack) return Job().apply { complete() }

        _isTracking.value = shouldTrack

        return viewModelScope.launch {
            settingsRepository.setTrackingState(shouldTrack)

            if (shouldTrack) {
                uploadRepository.resetUploadStatus()
                trackingServiceController.startTracking()
            } else {
                trackingServiceController.stopTracking()
            }
        }
    }

    private fun ensureTrackingServiceRunningIfRequired() {
        if (_isTracking.value != true) return

        trackingServiceController.ensureTrackingRunning()
    }

    private fun refreshTrackingServiceIfRequired() {
        if (_isTracking.value != true) return

        trackingServiceController.refreshTracking()
    }

    //endregion UPLOADING CONTROL
}

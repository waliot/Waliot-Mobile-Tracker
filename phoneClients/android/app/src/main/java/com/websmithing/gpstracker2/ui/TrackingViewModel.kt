package com.websmithing.gpstracker2.ui

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.upload.UploadRepository
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val uploadRepository: UploadRepository
) : ViewModel() {

    private val _isTracking = MutableLiveData<Boolean>()
    val isTracking: LiveData<Boolean> = _isTracking

    private val _trackerIdentifier = MutableLiveData<String>()
    val trackerIdentifier: LiveData<String> = _trackerIdentifier

    private val _uploadServer = MutableLiveData<String>()
    val uploadServer: LiveData<String> = _uploadServer

    private val _uploadTimeInterval = MutableLiveData<Int>()
    val uploadTimeInterval: LiveData<Int> = _uploadTimeInterval

    private val _uploadDistanceInterval = MutableLiveData<Int>()
    val uploadDistanceInterval: LiveData<Int> = _uploadDistanceInterval

    private val _language = MutableLiveData<String>()
    val language: LiveData<String> = _language

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    val latestLocation: StateFlow<Location?> = locationRepository.currentLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastUploadStatus: StateFlow<UploadStatus> = uploadRepository.lastUploadStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadStatus.Idle)

    internal val initJob: Job = viewModelScope.launch {
        _isTracking.value = settingsRepository.getTrackingState()
        _trackerIdentifier.value = settingsRepository.getTrackerIdentifier()
        _uploadServer.value = settingsRepository.getUploadServer()
        _uploadTimeInterval.value = settingsRepository.getUploadTimeInterval()
        _uploadDistanceInterval.value = settingsRepository.getUploadDistanceInterval()
        _language.value = settingsRepository.getLanguage()
        Timber.d("ViewModel initialized. Tracking: ${isTracking.value}")

        if (settingsRepository.isFirstTimeLoading()) {
            Timber.d("First time loading detected, generating App ID.")
            settingsRepository.generateAndSaveAppId()
            settingsRepository.setFirstTimeLoading(false)
        }
    }

    //region SETTING CHANGES

    fun onTrackerIdentifierChanged(newValue: String) {
        val newTrackerIdentifier = newValue.trim()
        if (newTrackerIdentifier != _trackerIdentifier.value) {
            _trackerIdentifier.value = newTrackerIdentifier
            viewModelScope.launch {
                settingsRepository.setTrackingIdentifier(newTrackerIdentifier)
                Timber.d("Tracker identifier saved: $newTrackerIdentifier")
            }
        }
    }

    fun onUploadServerChanged(newValue: String) {
        val newServerAddress = newValue.trim()
        if (newServerAddress != _uploadServer.value) {
            _uploadServer.value = newServerAddress
            viewModelScope.launch {
                settingsRepository.setUploadServer(newServerAddress)
                Timber.d("Upload server saved: $newServerAddress")
            }
        }
    }

    fun onTimeIntervalChanged(newValue: String) {
        val newTimeInterval = newValue.toIntOrNull() ?: return
        if (newTimeInterval == _uploadTimeInterval.value) return

        _uploadTimeInterval.value = newTimeInterval
        viewModelScope.launch {
            settingsRepository.setUploadTimeInterval(newTimeInterval)
            Timber.d("Interval changed to: $newTimeInterval minutes")

            restartForegroundServiceIfRequired()
        }
    }

    fun onDistanceIntervalChanged(newValue: String) {
        val newDistanceInterval = newValue.toIntOrNull() ?: return
        if (newDistanceInterval == _uploadDistanceInterval.value) return

        _uploadDistanceInterval.value = newDistanceInterval
        viewModelScope.launch {
            settingsRepository.setUploadDistanceInterval(newDistanceInterval)
            Timber.d("Interval changed to: $newDistanceInterval meters")

            restartForegroundServiceIfRequired()
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

    fun startForegroundLocation() = locationRepository.start()

    fun stopForegroundLocation() = locationRepository.stop()

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
                Intent(context, TrackingService::class.java).also { intent ->
                    intent.action = TrackingService.ACTION_START_SERVICE
                    context.startForegroundService(intent)
                }
            } else {
                Intent(context, TrackingService::class.java).also { intent ->
                    intent.action = TrackingService.ACTION_STOP_SERVICE
                    context.stopService(intent)
                }
            }
        }
    }

    private fun restartForegroundServiceIfRequired() {
        if (_isTracking.value != true) return

        stopTracking()
        startTracking()
    }

    //endregion UPLOADING CONTROL
}

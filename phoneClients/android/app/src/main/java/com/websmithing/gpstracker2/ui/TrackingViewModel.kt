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
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.location.ForegroundLocationRepository
import com.websmithing.gpstracker2.repository.location.LocationRepository
import com.websmithing.gpstracker2.repository.location.UploadStatus
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val foregroundLocationRepository: ForegroundLocationRepository
) : ViewModel() {

    private val _isTracking = MutableLiveData<Boolean>()
    val isTracking: LiveData<Boolean> = _isTracking

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    private val _trackingInterval = MutableLiveData<Int>()
    val trackingInterval: LiveData<Int> = _trackingInterval

    private val _websiteUrl = MutableLiveData<String>()
    val websiteUrl: LiveData<String> = _websiteUrl

    private val _language = MutableLiveData<String>()
    val language: LiveData<String> = _language

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    val latestLocation: StateFlow<Location?> = locationRepository.latestLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestForegroundLocation: StateFlow<Location?> =
        foregroundLocationRepository.currentLocation
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalDistance: StateFlow<Float> = locationRepository.totalDistance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val lastUploadStatus: StateFlow<UploadStatus> = locationRepository.lastUploadStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadStatus.Idle)

    internal val initJob: Job

    init {
        initJob = viewModelScope.launch {
            _isTracking.value = settingsRepository.getCurrentTrackingState()
            _userName.value = settingsRepository.getCurrentUsername()
            _trackingInterval.value = settingsRepository.getCurrentTrackingInterval()
            _websiteUrl.value = settingsRepository.getCurrentWebsiteUrl()
            _language.value = settingsRepository.getCurrentLanguage()
            Timber.d("ViewModel initialized. Tracking: ${isTracking.value}")

            if (settingsRepository.isFirstTimeLoading()) {
                Timber.d("First time loading detected, generating App ID.")
                settingsRepository.generateAndSaveAppId()
                settingsRepository.setFirstTimeLoading(false) // Mark as no longer first time
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundLocation()
    }

    fun startTracking() {
        Timber.d("startTracking called in ViewModel")
        updateTrackingState(true)
    }

    fun stopTracking() {
        Timber.d("stopTracking called in ViewModel")
        updateTrackingState(false)
    }

    fun forceStopTracking() {
        Timber.d("forceStopTracking called in ViewModel")
        if (_isTracking.value == true) {
            updateTrackingState(false)
        }
    }

    fun switchTrackingState() {
        if (isTracking.value == true) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    fun startForegroundLocation() = foregroundLocationRepository.start()

    fun stopForegroundLocation() = foregroundLocationRepository.stop()

    fun onIntervalChanged(newInterval: Int) {
        if (newInterval != _trackingInterval.value) {
            Timber.d("Interval changed to: $newInterval minutes")
            _trackingInterval.value = newInterval
            viewModelScope.launch {
                settingsRepository.saveTrackingInterval(newInterval)
                // If currently tracking, stop and restart the service to apply the new interval
                if (_isTracking.value == true) {
                    _snackbarMessage.value = context.getString(R.string.interval_updated)
                    // Stop the service
                    Intent(context, TrackingService::class.java).also { intent ->
                        intent.action = TrackingService.ACTION_STOP_SERVICE
                        context.stopService(intent)
                    }
                    // Start the service again (it will read the new interval)
                    Intent(context, TrackingService::class.java).also { intent ->
                        intent.action = TrackingService.ACTION_START_SERVICE
                        context.startForegroundService(intent)
                    }
                }
            }
        }
    }

    fun onUserNameChanged(newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName != _userName.value) {
            _userName.value = trimmedName
            viewModelScope.launch {
                settingsRepository.saveUsername(trimmedName)
                Timber.d("Username saved: $trimmedName")
            }
        }
    }

    fun onWebsiteUrlChanged(newUrl: String) {
        val trimmedUrl = newUrl.trim()
        if (trimmedUrl != _websiteUrl.value && trimmedUrl.isNotEmpty()) {
            _websiteUrl.value = trimmedUrl
            viewModelScope.launch {
                settingsRepository.saveWebsiteUrl(trimmedUrl)
                Timber.d("Website URL saved: $trimmedUrl")
            }
        }
    }

    fun onLanguageChanged(language: String) {
        if (language != _language.value) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language)
            )
            viewModelScope.launch {
                settingsRepository.saveLanguage(language)
                _language.value = language
            }
        }
    }

    fun onSnackbarMessageShown() {
        _snackbarMessage.value = null
    }

    private fun updateTrackingState(shouldTrack: Boolean): Job {
        if (_isTracking.value == shouldTrack) return Job().apply { complete() }

        _isTracking.value = shouldTrack
        return viewModelScope.launch {
            settingsRepository.setTrackingState(shouldTrack)
            if (shouldTrack) {
                val newSessionId = UUID.randomUUID().toString()
                settingsRepository.saveSessionId(newSessionId)
                locationRepository.resetLocationState() // Reset location repo state
                // Start the foreground service
                Intent(context, TrackingService::class.java).also { intent ->
                    intent.action = TrackingService.ACTION_START_SERVICE
                    context.startForegroundService(intent)
                    Timber.i("Tracking started via ViewModel. Session: $newSessionId. Service started.")
                }
            } else {
                settingsRepository.clearSessionId()
                // Stop the foreground service
                Intent(context, TrackingService::class.java).also { intent ->
                    intent.action = TrackingService.ACTION_STOP_SERVICE
                    context.stopService(intent) // Use stopService for foreground services
                    Timber.i("Tracking stopped via ViewModel. Service stopped.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "TrackingViewModel"
    }
}

package com.websmithing.gpstracker2.repository.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LOCATION_UPDATE_DISTANCE_METERS
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundLocationRepositoryImpl @Inject constructor(
    private val provider: FusedLocationProviderClient,
) : ForegroundLocationRepository {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation

    private var locationCallback: LocationCallback? = null
    private var initialFixReceived = false

    private companion object {
        const val TAG = "ForegroundLocationRepository"
    }

    override fun start() {
        if (locationCallback != null) return

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    updateLocationState(loc, "Callback fix")
                }
            }
        }

        requestInitialFix()
        startLocationUpdates()
    }

    override fun stop() {
        locationCallback?.let {
            provider.removeLocationUpdates(it)
            Timber.tag(TAG).d("Location updates stopped")
        }
        locationCallback = null
    }

    private fun updateLocationState(location: Location, source: String) {
        initialFixReceived = true
        _currentLocation.value = location
        Timber.tag(TAG).d("$source: ${location.latitude}, ${location.longitude}")
    }

    @SuppressLint("MissingPermission")
    private fun requestInitialFix() {
        provider.lastLocation.addOnSuccessListener { loc ->
            loc?.let { updateLocationState(it, "Initial fix from lastLocation") }
        }

        provider.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (!initialFixReceived && loc != null) {
                    updateLocationState(loc, "Initial fix from getCurrentLocation")
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = createLocationRequest()
        locationCallback?.let { cb ->
            provider.requestLocationUpdates(request, cb, Looper.getMainLooper())
            Timber.tag(TAG).d("Location updates started")
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS))
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS / 2))
            .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS * 2))
            .setMinUpdateDistanceMeters(DEFAULT_LOCATION_UPDATE_DISTANCE_METERS)
            .setWaitForAccurateLocation(true)
            .build()
    }
}

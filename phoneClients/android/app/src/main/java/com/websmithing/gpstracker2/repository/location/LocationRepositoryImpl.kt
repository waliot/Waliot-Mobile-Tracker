package com.websmithing.gpstracker2.repository.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val provider: FusedLocationProviderClient,
) : LocationRepository {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation

    private var locationCallback: LocationCallback? = null
    private var initialFixReceived = false

    private companion object {
        const val TAG = "LocationRepository"
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
        Timber.tag(TAG).i("$source: ${location.latitude}, ${location.longitude}")
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
        val interval = 10_000L
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval)
            .setMaxUpdateDelayMillis(interval)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()
    }
}

package com.websmithing.gpstracker2.data.repository

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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundLocationRepositoryImpl @Inject constructor(
    private val provider: FusedLocationProviderClient,
) : ForegroundLocationRepository {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation

    private var callback: LocationCallback? = null
    private var initialFixReceived = false

    @SuppressLint("MissingPermission")
    override fun start() {
        if (callback != null) {
            return
        }

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                initialFixReceived = true
                _currentLocation.value = loc
                Timber.d("Callback fix: ${loc.latitude}, ${loc.longitude}")
            }
        }

        requestInitialFix()
        startLocationUpdates()
    }

    override fun stop() {
        callback?.let {
            provider.removeLocationUpdates(it)
            Timber.d("Location updates stopped")
        }
        callback = null
    }

    @SuppressLint("MissingPermission")
    private fun requestInitialFix() {
        provider.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                initialFixReceived = true
                _currentLocation.value = loc
                Timber.d("Initial fix from lastLocation")
            }
        }

        provider.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (!initialFixReceived && loc != null) {
                    initialFixReceived = true
                    _currentLocation.value = loc
                    Timber.d("Initial fix from getCurrentLocation")
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(60)
        )
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(30))
            .setMaxUpdateDelayMillis(TimeUnit.MINUTES.toMillis(2))
            .build()

        provider.requestLocationUpdates(
            request,
            callback!!,
            Looper.getMainLooper()
        )
        Timber.d("Location updates started")
    }
}

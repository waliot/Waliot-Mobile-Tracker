package com.websmithing.gpstracker2.ui.features.home.components

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import timber.log.Timber
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
@Composable
fun getUserLocation(): Location? {
    val context = LocalContext.current
    val locationProvider = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var currentLocation by rememberSaveable(stateSaver = locationSaver) {
        mutableStateOf<Location?>(null)
    }
    var initialFixReceived by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(false) }

    val callback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation
                if (last != null) {
                    currentLocation = last
                    initialFixReceived = true
                    Timber.d("Callback fix: ${last.latitude}, ${last.longitude}")
                }
            }
        }
    }

    LaunchedEffect(start) {
        if (start && !initialFixReceived) {
            locationProvider.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLocation = loc
                        initialFixReceived = true
                        Timber.d("Initial fix from lastLocation")
                    } else {
                        Timber.d("lastLocation = null")
                    }
                }

            val current = locationProvider.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )

            current.addOnSuccessListener { loc ->
                if (!initialFixReceived && loc != null) {
                    currentLocation = loc
                    initialFixReceived = true
                    Timber.d("Initial fix from getCurrentLocation")
                }
            }
        }
    }

    LaunchedEffect(start) {
        if (start) {
            val request = LocationRequest.create().apply {
                interval = TimeUnit.SECONDS.toMillis(60)
                fastestInterval = TimeUnit.SECONDS.toMillis(30)
                maxWaitTime = TimeUnit.MINUTES.toMillis(2)
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationProvider.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
            Timber.d("Location updates started")
        } else {
            locationProvider.removeLocationUpdates(callback)
            Timber.d("Location updates stopped")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationProvider.removeLocationUpdates(callback)
            Timber.d("Location callback disposed")
        }
    }

    LocationPermissionFlow(
        onAllow = { start = true },
        onDeny = { start = false }
    )

    return currentLocation
}

private val locationSaver = Saver<Location?, List<Double>>(
    save = { loc ->
        if (loc == null) emptyList()
        else listOf(loc.latitude, loc.longitude)
    },
    restore = { list ->
        if (list.isEmpty()) null
        else Location("").apply {
            latitude = list[0]
            longitude = list[1]
        }
    }
)
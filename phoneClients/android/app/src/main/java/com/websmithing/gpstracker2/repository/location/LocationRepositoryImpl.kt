package com.websmithing.gpstracker2.repository.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LocationRepositoryImpl private constructor(
    private val fusedProvider: FusedLocationProviderClient,
    private val platformLocationManager: LocationManager,
    private val isGooglePlayServicesAvailable: () -> Boolean,
    private val currentTimeMillis: () -> Long,
) : LocationRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        fusedProvider: FusedLocationProviderClient,
        platformLocationManager: LocationManager,
    ) : this(
        fusedProvider = fusedProvider,
        platformLocationManager = platformLocationManager,
        isGooglePlayServicesAvailable = {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        },
        currentTimeMillis = System::currentTimeMillis,
    )

    internal constructor(
        fusedProvider: FusedLocationProviderClient,
        platformLocationManager: LocationManager,
        googlePlayServicesAvailable: Boolean,
        currentTimeMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        fusedProvider = fusedProvider,
        platformLocationManager = platformLocationManager,
        isGooglePlayServicesAvailable = { googlePlayServicesAvailable },
        currentTimeMillis = currentTimeMillis,
    )

    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation
    private val _locationFixStatus = MutableStateFlow(LocationFixStatus())
    override val locationFixStatus: StateFlow<LocationFixStatus> = _locationFixStatus

    private val activeConsumers = linkedSetOf<LocationConsumer>()
    private var fusedLocationCallback: LocationCallback? = null
    private var platformLocationListener: LocationListener? = null
    private var initialFixReceived = false
    private var activeBackend: LocationBackend? = null

    private companion object {
        const val TAG = "LocationRepository"
        const val LOCATION_UPDATE_INTERVAL_MILLIS = 10_000L
        const val MAX_ACCEPTABLE_FIX_AGE_MILLIS = 5 * 60 * 1000L
        const val MAX_ACCEPTABLE_TRAVEL_SPEED_KMH = 180.0
        val PLATFORM_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }

    override fun start(consumer: LocationConsumer) {
        val added = activeConsumers.add(consumer)
        if (!added && activeBackend != null) return
        if (activeBackend != null) return

        initialFixReceived = false
        startPreferredBackend()
    }

    override fun stop(consumer: LocationConsumer) {
        if (!activeConsumers.remove(consumer)) return
        if (activeConsumers.isNotEmpty()) return

        stopActiveBackend()
        initialFixReceived = false
    }

    private fun startPreferredBackend() {
        if (isGooglePlayServicesAvailable() && startFusedBackend()) {
            return
        }
        startPlatformBackend()
    }

    @SuppressLint("MissingPermission")
    private fun startFusedBackend(): Boolean {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    handleLocationCandidate(loc, "Callback fix")
                }
            }
        }

        fusedLocationCallback = callback
        activeBackend = LocationBackend.Fused
        Timber.tag(TAG).d("Using fused location backend")
        requestInitialFixFromFused()

        val request = createLocationRequest()
        return runCatching {
            fusedProvider.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    fallbackToPlatform(error, "Fused location updates request failed")
                }
            Timber.tag(TAG).d("Fused location updates started")
            true
        }.getOrElse { error ->
            fallbackToPlatform(error, "Unable to start fused location updates")
            false
        }
    }

    private fun updateLocationState(location: Location, source: String, quality: LocationFixQuality) {
        initialFixReceived = true
        _currentLocation.value = location
        _locationFixStatus.value = LocationFixStatus(
            quality = quality,
            trustedLocation = location,
            trustedFixDegraded = quality == LocationFixQuality.TrustedDegraded,
            issue = null,
            provider = location.provider,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            observedAtMillis = resolveTimestamp(location),
        )
        Timber.tag(TAG).i(formatLocationLogMessage(source, location))
    }

    private fun handleLocationCandidate(location: Location, source: String) {
        when (val evaluation = evaluateLocationCandidate(location)) {
            is LocationEvaluation.Accept -> {
                updateLocationState(
                    location = location,
                    source = source,
                    quality = if (evaluation.degraded) {
                        LocationFixQuality.TrustedDegraded
                    } else {
                        LocationFixQuality.TrustedGps
                    },
                )
            }

            is LocationEvaluation.Reject -> {
                when (evaluation.issue) {
                    LocationFixIssue.MockFix,
                    LocationFixIssue.ImpossibleJump,
                    LocationFixIssue.TimestampRegression -> {
                        _locationFixStatus.value = LocationFixStatus(
                            quality = LocationFixQuality.Suspect,
                            trustedLocation = _currentLocation.value,
                            trustedFixDegraded = currentTrustedFixIsDegraded(),
                            issue = evaluation.issue,
                            provider = location.provider,
                            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                            observedAtMillis = currentTimeMillis(),
                        )
                    }

                    LocationFixIssue.StaleFix -> {
                        if (_currentLocation.value == null) {
                            _locationFixStatus.value = LocationFixStatus(
                                quality = LocationFixQuality.NoFix,
                                trustedLocation = null,
                                trustedFixDegraded = false,
                                issue = evaluation.issue,
                                provider = location.provider,
                                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                                observedAtMillis = currentTimeMillis(),
                            )
                        }
                    }

                    LocationFixIssue.NoProviders -> {
                        publishNoFix(LocationFixIssue.NoProviders)
                    }
                }

                Timber.tag(TAG).w(
                    "Rejected location candidate from %s: issue=%s",
                    source,
                    evaluation.issue.name
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestInitialFixFromFused() {
        runCatching {
            fusedProvider.lastLocation
                .addOnSuccessListener { loc ->
                    loc?.let { handleLocationCandidate(it, "Initial fix from lastLocation") }
                }
                .addOnFailureListener { error ->
                    fallbackToPlatform(error, "Failed to obtain fused last known location")
                }
        }.onFailure { error ->
            fallbackToPlatform(error, "Unable to request fused last known location")
        }

        runCatching {
            fusedProvider.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (!initialFixReceived && loc != null) {
                        handleLocationCandidate(loc, "Initial fix from getCurrentLocation")
                    }
                }
                .addOnFailureListener { error ->
                    fallbackToPlatform(error, "Failed to obtain fused current location")
                }
        }.onFailure { error ->
            fallbackToPlatform(error, "Unable to request fused current location")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformBackend(): Boolean {
        val enabledProviders = runCatching { platformLocationManager.getProviders(true) }
            .getOrElse { error ->
                Timber.tag(TAG).w(error, "Unable to query enabled platform providers")
                emptyList()
            }
            .filter { it in PLATFORM_PROVIDERS }

        if (enabledProviders.isEmpty()) {
            activeBackend = null
            platformLocationListener = null
            publishNoFix(LocationFixIssue.NoProviders)
            Timber.tag(TAG).w("No enabled platform location providers are available")
            return false
        }

        requestInitialFixFromPlatform(enabledProviders)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationCandidate(location, "Platform callback fix")
            }
        }
        platformLocationListener = listener
        activeBackend = LocationBackend.Platform

        return runCatching {
            enabledProviders.forEach { providerName ->
                platformLocationManager.requestLocationUpdates(
                    providerName,
                    LOCATION_UPDATE_INTERVAL_MILLIS,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            Timber.tag(TAG).d("Platform location updates started using providers: $enabledProviders")
            true
        }.getOrElse { error ->
            platformLocationListener = null
            activeBackend = null
            Timber.tag(TAG).w(error, "Unable to start platform location updates")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestInitialFixFromPlatform(enabledProviders: List<String>) {
        val bestLastKnownLocation = enabledProviders.mapNotNull { providerName ->
            runCatching { platformLocationManager.getLastKnownLocation(providerName) }
                .onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to obtain last known location from provider: $providerName")
                }
                .getOrNull()
        }.maxByOrNull(Location::getTime)

        bestLastKnownLocation?.let {
            handleLocationCandidate(it, "Initial fix from platform lastKnownLocation")
        }
    }

    private fun publishNoFix(issue: LocationFixIssue) {
        _locationFixStatus.value = LocationFixStatus(
            quality = LocationFixQuality.NoFix,
            trustedLocation = _currentLocation.value,
            trustedFixDegraded = currentTrustedFixIsDegraded(),
            issue = issue,
            provider = null,
            accuracyMeters = null,
            observedAtMillis = currentTimeMillis(),
        )
    }

    private fun evaluateLocationCandidate(location: Location): LocationEvaluation {
        val timestamp = resolveTimestamp(location)
        val ageMillis = (currentTimeMillis() - timestamp).coerceAtLeast(0L)
        if (ageMillis > MAX_ACCEPTABLE_FIX_AGE_MILLIS) {
            return LocationEvaluation.Reject(LocationFixIssue.StaleFix)
        }

        if (LocationCompat.isMock(location)) {
            return LocationEvaluation.Reject(LocationFixIssue.MockFix)
        }

        val previousTrustedLocation = _currentLocation.value
        if (previousTrustedLocation != null) {
            val previousTimestamp = resolveTimestamp(previousTrustedLocation)
            if (timestamp < previousTimestamp) {
                return LocationEvaluation.Reject(LocationFixIssue.TimestampRegression)
            }

            val deltaMillis = timestamp - previousTimestamp
            if (deltaMillis > 0L) {
                val distanceMeters = location.distanceTo(previousTrustedLocation).toDouble()
                val speedKmh = distanceMeters / (deltaMillis / 1000.0) * 3.6
                if (speedKmh > MAX_ACCEPTABLE_TRAVEL_SPEED_KMH) {
                    return LocationEvaluation.Reject(LocationFixIssue.ImpossibleJump)
                }
            }
        }

        return LocationEvaluation.Accept(
            degraded = isDegradedLocation(location)
        )
    }

    private fun currentTrustedFixIsDegraded(): Boolean {
        val currentStatus = _locationFixStatus.value
        return when (currentStatus.quality) {
            LocationFixQuality.TrustedDegraded -> true
            LocationFixQuality.TrustedGps -> false
            LocationFixQuality.NoFix,
            LocationFixQuality.Suspect -> currentStatus.trustedFixDegraded
        }
    }

    private fun resolveTimestamp(location: Location): Long {
        return location.time.takeIf { it > 0L } ?: currentTimeMillis()
    }

    private fun fallbackToPlatform(error: Throwable, message: String) {
        if (activeBackend != LocationBackend.Fused) {
            Timber.tag(TAG).w(error, message)
            return
        }

        Timber.tag(TAG).w(error, "%s; switching to platform location backend", message)
        stopFusedBackend()
        activeBackend = null
        startPlatformBackend()
    }

    private fun stopActiveBackend() {
        when (activeBackend) {
            LocationBackend.Fused -> stopFusedBackend()
            LocationBackend.Platform -> stopPlatformBackend()
            null -> Unit
        }
        activeBackend = null
    }

    private fun stopFusedBackend() {
        fusedLocationCallback?.let {
            runCatching { fusedProvider.removeLocationUpdates(it) }
                .onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to stop fused location updates")
                }
            Timber.tag(TAG).d("Fused location updates stopped")
        }
        fusedLocationCallback = null
    }

    private fun stopPlatformBackend() {
        platformLocationListener?.let {
            runCatching { platformLocationManager.removeUpdates(it) }
                .onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to stop platform location updates")
                }
            Timber.tag(TAG).d("Platform location updates stopped")
        }
        platformLocationListener = null
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL_MILLIS)
            .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()
    }

    private enum class LocationBackend {
        Fused,
        Platform,
    }

    private sealed interface LocationEvaluation {
        data class Accept(val degraded: Boolean) : LocationEvaluation
        data class Reject(val issue: LocationFixIssue) : LocationEvaluation
    }
}

internal fun formatLocationLogMessage(source: String, location: Location): String {
    val providerLabel = location.provider?.takeIf { it.isNotBlank() } ?: "unknown"
    val accuracyLabel = if (location.hasAccuracy()) {
        "${location.accuracy.roundToInt()}m"
    } else {
        "unknown"
    }
    return "$source: provider=$providerLabel accuracy=$accuracyLabel coordinates=redacted"
}

internal fun isDegradedProvider(provider: String?): Boolean {
    return when (provider?.lowercase()) {
        LocationManager.NETWORK_PROVIDER.lowercase() -> true
        LocationManager.PASSIVE_PROVIDER.lowercase() -> true
        else -> false
    }
}

internal fun isDegradedLocation(location: Location): Boolean {
    if (isDegradedProvider(location.provider)) {
        return true
    }

    return when (location.provider?.lowercase()) {
        "fused" -> (location.extras?.getInt("satellites") ?: 0) <= 0
        else -> false
    }
}

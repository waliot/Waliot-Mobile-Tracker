package com.websmithing.gpstracker2.repository.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

enum class LocationConsumer {
    ForegroundUi,
    TrackingService,
}

interface LocationRepository {
    val currentLocation: Flow<Location?>
    val locationFixStatus: Flow<LocationFixStatus>
    fun start(consumer: LocationConsumer)
    fun stop(consumer: LocationConsumer)
}

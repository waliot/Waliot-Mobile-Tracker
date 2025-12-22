package com.websmithing.gpstracker2.repository.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface ForegroundLocationRepository {
    val currentLocation: Flow<Location?>
    fun start()
    fun stop()
}

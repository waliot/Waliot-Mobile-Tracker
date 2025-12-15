package com.websmithing.gpstracker2.data.repository

import android.location.Location
import kotlinx.coroutines.flow.Flow

/**
 * Provides a most recent location data in foreground mode and is not related to the logic of
 * sending tracking data over the network
 */
interface ForegroundLocationRepository {
    val currentLocation: Flow<Location?>

    fun start()

    fun stop()
}
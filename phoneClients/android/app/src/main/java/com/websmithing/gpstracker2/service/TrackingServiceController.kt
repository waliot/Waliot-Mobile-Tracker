package com.websmithing.gpstracker2.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface TrackingServiceController {
    fun startTracking()
    fun stopTracking()
    fun refreshTracking()
    fun ensureTrackingRunning()
}

@Singleton
class DefaultTrackingServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TrackingServiceController {

    override fun startTracking() {
        Intent(context, TrackingService::class.java).also { intent ->
            intent.action = TrackingService.ACTION_START_SERVICE
            context.startForegroundService(intent)
        }
    }

    override fun stopTracking() {
        Intent(context, TrackingService::class.java).also { intent ->
            intent.action = TrackingService.ACTION_STOP_SERVICE
            context.startService(intent)
        }
    }

    override fun refreshTracking() {
        Intent(context, TrackingService::class.java).also { intent ->
            intent.action = TrackingService.ACTION_REFRESH_SERVICE
            context.startService(intent)
        }
    }

    override fun ensureTrackingRunning() {
        startTracking()
    }
}

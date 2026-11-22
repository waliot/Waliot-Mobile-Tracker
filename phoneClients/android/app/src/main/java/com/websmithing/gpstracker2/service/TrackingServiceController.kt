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
    @param:ApplicationContext private val context: Context,
    private val trackingPermissionChecker: TrackingPermissionChecker,
) : TrackingServiceController {

    override fun startTracking() {
        // Refuse before requesting an FGS: stopping it from onStartCommand is too late
        // on devices that enforce the foreground-promotion timeout after permission loss.
        if (!trackingPermissionChecker.canStartTrackingService()) return

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

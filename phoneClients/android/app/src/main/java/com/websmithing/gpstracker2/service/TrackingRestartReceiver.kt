package com.websmithing.gpstracker2.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TrackingRestartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var trackingRecoveryCoordinator: TrackingRecoveryCoordinator

    override fun onReceive(context: Context?, intent: Intent?) {
        if (shouldRestoreTrackingForAction(intent?.action)) {
            Timber.d("Restoring tracking after system restart action=${intent?.action}")
            trackingRecoveryCoordinator.restoreTrackingIfRequired()
        }
    }
}

internal fun shouldRestoreTrackingForAction(action: String?): Boolean {
    return action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}

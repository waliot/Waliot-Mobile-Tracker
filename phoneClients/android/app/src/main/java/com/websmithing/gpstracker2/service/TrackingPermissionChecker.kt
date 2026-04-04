package com.websmithing.gpstracker2.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingPermissionChecker private constructor(
    private val permissionState: TrackingPermissionState
) {

    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        permissionState = AndroidTrackingPermissionState(context)
    )

    internal constructor(
        sdkInt: Int,
        hasForegroundLocationPermission: () -> Boolean,
        hasBackgroundLocationPermission: () -> Boolean,
    ) : this(
        permissionState = TestTrackingPermissionState(
            sdkInt = sdkInt,
            hasForegroundLocationPermission = hasForegroundLocationPermission,
            hasBackgroundLocationPermission = hasBackgroundLocationPermission,
        )
    )

    fun canStartTrackingService(): Boolean {
        return permissionState.hasForegroundLocationPermission()
    }

    fun canRestoreTrackingFromBackground(): Boolean {
        if (!permissionState.hasForegroundLocationPermission()) {
            return false
        }

        return if (permissionState.sdkInt >= Build.VERSION_CODES.Q) {
            permissionState.hasBackgroundLocationPermission()
        } else {
            true
        }
    }
}

private interface TrackingPermissionState {
    val sdkInt: Int
    fun hasForegroundLocationPermission(): Boolean
    fun hasBackgroundLocationPermission(): Boolean
}

private class AndroidTrackingPermissionState(
    private val context: Context
) : TrackingPermissionState {

    override val sdkInt: Int
        get() = Build.VERSION.SDK_INT

    override fun hasForegroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasBackgroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

private class TestTrackingPermissionState(
    override val sdkInt: Int,
    private val hasForegroundLocationPermission: () -> Boolean,
    private val hasBackgroundLocationPermission: () -> Boolean,
) : TrackingPermissionState {

    override fun hasForegroundLocationPermission(): Boolean = hasForegroundLocationPermission.invoke()

    override fun hasBackgroundLocationPermission(): Boolean = hasBackgroundLocationPermission.invoke()
}

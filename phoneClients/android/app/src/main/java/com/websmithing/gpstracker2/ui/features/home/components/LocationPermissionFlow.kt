package com.websmithing.gpstracker2.ui.features.home.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.ui.isBackgroundLocationPermissionGranted
import com.websmithing.gpstracker2.ui.isForegroundLocationPermissionGranted

private const val INITIAL_LOCATION_DISCLOSURE_SHOWN = "initial_location_disclosure_shown"

private val foregroundLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private enum class LocationDisclosure(@get:StringRes val title: Int, @get:StringRes val message: Int) {
    Foreground(
        R.string.permission_rationale_foreground_location_title,
        R.string.permission_rationale_foreground_location_message,
    ),
    Background(
        R.string.permission_rationale_background_location_title,
        R.string.permission_rationale_background_location_pre_request,
    ),
}

@Composable
internal fun locationPermissionFlow(
    onForegroundAllowed: () -> Unit,
    onForegroundDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val requiresBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var disclosure by rememberSaveable { mutableStateOf<LocationDisclosure?>(null) }
    var foregroundSettingsRequired by rememberSaveable { mutableStateOf(false) }
    var backgroundSettingsRequired by rememberSaveable { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val decision = decideForegroundPermissionResult(
            fineLocationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            coarseLocationGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
            isBackgroundLocationRequired = requiresBackground,
            hasBackgroundLocationPermission = isBackgroundLocationPermissionGranted(context),
            anyShouldShowRationale = result.entries.any { (permission, granted) ->
                !granted && ActivityCompat.shouldShowRequestPermissionRationale(context.findActivity(), permission)
            },
        )
        when (decision) {
            ForegroundPermissionDecision.Allow -> onForegroundAllowed()
            ForegroundPermissionDecision.RequestBackgroundPermission -> {
                onForegroundAllowed()
                disclosure = LocationDisclosure.Background
            }
            ForegroundPermissionDecision.ShowRationale -> onForegroundDenied()
            ForegroundPermissionDecision.Deny -> {
                foregroundSettingsRequired = result.isNotEmpty()
                onForegroundDenied()
            }
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when (decideBackgroundPermissionResult(granted)) {
            BackgroundPermissionDecision.Allow -> Unit
            BackgroundPermissionDecision.Deny -> backgroundSettingsRequired = true
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (isForegroundLocationPermissionGranted(context)) {
            onForegroundAllowed()
        } else {
            onForegroundDenied()
        }
    }

    fun requestMissingLocationPermission() {
        disclosure = when {
            !isForegroundLocationPermissionGranted(context) -> LocationDisclosure.Foreground
            requiresBackground && !isBackgroundLocationPermissionGranted(context) -> LocationDisclosure.Background
            else -> null
        }
    }

    LaunchedEffect(Unit) {
        if (isForegroundLocationPermissionGranted(context)) onForegroundAllowed()
        val preferences = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(INITIAL_LOCATION_DISCLOSURE_SHOWN, false)) {
            preferences.edit().putBoolean(INITIAL_LOCATION_DISCLOSURE_SHOWN, true).apply()
            requestMissingLocationPermission()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        when (disclosure) {
            LocationDisclosure.Foreground -> {
                if (isForegroundLocationPermissionGranted(context)) disclosure = null
            }
            LocationDisclosure.Background -> {
                if (!isForegroundLocationPermissionGranted(context) ||
                    isBackgroundLocationPermissionGranted(context)
                ) {
                    disclosure = null
                }
            }
            null -> Unit
        }
    }

    disclosure?.let { step ->
        val openSettings = when (step) {
            LocationDisclosure.Foreground -> foregroundSettingsRequired
            LocationDisclosure.Background -> backgroundSettingsRequired
        }

        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(stringResource(step.title)) },
            text = { Text(stringResource(step.message)) },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = null
                    if (openSettings) {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    } else {
                        when (step) {
                            LocationDisclosure.Foreground -> foregroundLauncher.launch(foregroundLocationPermissions)
                            LocationDisclosure.Background -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }) {
                    Text(stringResource(if (openSettings) R.string.permission_button_settings else R.string.permission_button_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { disclosure = null }) {
                    Text(stringResource(R.string.permission_button_deny))
                }
            },
        )
    }

    return ::requestMissingLocationPermission
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Context is not an Activity")
}

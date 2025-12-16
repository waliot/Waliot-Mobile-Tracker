package com.websmithing.gpstracker2.ui.features.home.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.components.PermissionDeniedDialog
import com.websmithing.gpstracker2.ui.isBackgroundLocationPermissionGranted

private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
} else {
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

@Composable
fun LocationPermissionFlow(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    var showPreBackgroundDialog by remember { mutableStateOf(false) }

    ForegroundLocation(
        onAllow = onAllow,
        onDeny = onDeny,
        onShowBackgroundDialog = { showPreBackgroundDialog = true }
    )

    BackgroundLocation(
        onAllow = onAllow,
        onDeny = onDeny,
        show = showPreBackgroundDialog,
    ) {
        showPreBackgroundDialog = false
    }
}

@Composable
fun ForegroundLocation(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onShowBackgroundDialog: () -> Unit,
) {
    val context = LocalContext.current
    val isBackgroundLocationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    var permissionsRequest by remember { mutableStateOf(false) }

    var showForegroundDeniedDialog by remember { mutableStateOf(false) }
    var showForegroundRationaleDialog by remember { mutableStateOf(false) }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val anyGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (anyGranted) {
                if (isBackgroundLocationRequired) {
                    if (isBackgroundLocationPermissionGranted(context)) {
                        onAllow()
                    } else {
                        onShowBackgroundDialog()
                    }

                } else {
                    onAllow()
                }
            } else {
                val anyShouldShow = result.entries.any { (permission, granted) ->
                    !granted && shouldShowRationale(context, permission)
                }

                if (anyShouldShow) {
                    showForegroundRationaleDialog = true
                } else {
                    onDeny()
                    showForegroundDeniedDialog = true
                }
            }
        }

    LaunchedEffect(Unit) {
        if (!permissionsRequest) {
            permissionsRequest = true
            permissionsLauncher.launch(permissions)
        }
    }

    if (showForegroundRationaleDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(context.getString(R.string.permission_rationale_foreground_location_title)) },
            text = { Text(context.getString(R.string.permission_rationale_foreground_location_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showForegroundRationaleDialog = false
                    permissionsLauncher.launch(permissions)
                }) {
                    Text(context.getString(R.string.permission_button_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForegroundRationaleDialog = false }) {
                    Text(context.getString(R.string.permission_button_deny))
                }
            }
        )
    }

    if (showForegroundDeniedDialog) {
        PermissionDeniedDialog(
            text = context.getString(R.string.permission_denied_foreground_location),
            onDismissRequest = { showForegroundDeniedDialog = false }
        )
    }
}

@Composable
private fun BackgroundLocation(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    var showBackgroundDeniedDialog by remember { mutableStateOf(false) }

    val backgroundLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                onAllow()
            } else {
                val shouldShow = shouldShowRationale(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )

                onDeny()
                showBackgroundDeniedDialog = true
            }
        }

    if (show) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(context.getString(R.string.permission_rationale_background_location_title)) },
            text = { Text(context.getString(R.string.permission_rationale_background_location_pre_request)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRequest()
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) {
                    Text(context.getString(R.string.permission_button_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDismissRequest()
                }) {
                    Text(context.getString(R.string.permission_button_cancel))
                }
            }
        )
    }

    if (showBackgroundDeniedDialog) {
        PermissionDeniedDialog(
            text = context.getString(R.string.permission_denied_background_location),
            onDismissRequest = { showBackgroundDeniedDialog = false }
        )
    }
}

private fun shouldShowRationale(context: android.content.Context, permission: String): Boolean {
    return ActivityCompat.shouldShowRequestPermissionRationale(
        context.findActivity(),
        permission
    )
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity {
    return when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> error("Context is not an Activity")
    }
}
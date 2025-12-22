package com.websmithing.gpstracker2.ui.features.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.location.UploadStatus
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.components.CustomSnackbar
import com.websmithing.gpstracker2.ui.components.CustomSnackbarType
import com.websmithing.gpstracker2.ui.components.PermissionDeniedDialog
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarker
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerSize
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerState
import com.websmithing.gpstracker2.ui.features.home.components.LocationPermissionFlow
import com.websmithing.gpstracker2.ui.features.home.components.MapView
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButton
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButtonState
import com.websmithing.gpstracker2.ui.features.home.components.TrackingInfoSheet
import com.websmithing.gpstracker2.ui.isBackgroundLocationPermissionGranted
import com.websmithing.gpstracker2.ui.router.AppDestination
import com.websmithing.gpstracker2.ui.toPosition
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import kotlin.time.Duration.Companion.milliseconds

private const val defaultZoom = 15.0

@Composable
fun HomePage(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: TrackingViewModel = activityHiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraState = rememberCameraState()
    val latestLocation by viewModel.latestForegroundLocation.collectAsStateWithLifecycle()
    val markerPosition by remember(cameraState.position, latestLocation) {
        derivedStateOf {
            latestLocation?.let { location ->
                cameraState.projection?.screenLocationFromPosition(location.toPosition())
            } ?: DpOffset.Unspecified
        }
    }

    val isTracking by viewModel.isTracking.observeAsState(false)
    val userName by viewModel.userName.observeAsState()
    val websiteUrl by viewModel.websiteUrl.observeAsState()
    val canRunTracking by remember(userName, websiteUrl) {
        derivedStateOf {
            !websiteUrl.isNullOrEmpty() && !userName.isNullOrEmpty()
        }
    }
    val lastUploadStatus by viewModel.lastUploadStatus.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.observeAsState()

    var showTrackingInfoSheet by remember { mutableStateOf(false) }
    var showBackgroundDeniedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(latestLocation) {
        val oldZoom = cameraState.position.zoom
        latestLocation?.let {
            cameraState.animateTo(
                CameraPosition(
                    target = it.toPosition(),
                    zoom = if (oldZoom == 1.0) defaultZoom else oldZoom,
                ),
                duration = 500.milliseconds,
            )
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    it,
                    actionLabel = CustomSnackbarType.success.name,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    LaunchedEffect(lastUploadStatus) {
        when (val status = lastUploadStatus) {
            is UploadStatus.Failure -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.error_format,
                        status.errorMessage ?: context.getString(R.string.unknown_error)
                    ),
                    actionLabel = CustomSnackbarType.warning.name,
                    duration = SnackbarDuration.Short
                )
            }

            else -> {}
        }
    }

    LaunchedEffect(isTracking) {
        if (isTracking) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.tracking_enabled),
                    actionLabel = CustomSnackbarType.success.name,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    DisposableEffect(true) {
        onDispose {
            viewModel.stopForegroundLocation()
        }
    }

    LocationPermissionFlow(
        onAllow = { viewModel.startForegroundLocation() },
        onDeny = { viewModel.stopForegroundLocation() }
    )

    fun switchTracking() {
        if (!canRunTracking) {
            return
        }
        if (!isBackgroundLocationPermissionGranted(context)) {
            showBackgroundDeniedDialog = true
            return
        }
        try {
            viewModel.switchTrackingState()
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.error_format, e.message ?: e.toString()),
                    actionLabel = CustomSnackbarType.warning.name,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            TrackingButton(
                state = if (!canRunTracking) {
                    TrackingButtonState.Stop
                } else if (isTracking) {
                    TrackingButtonState.Pause
                } else {
                    TrackingButtonState.Play
                }
            ) {
                switchTracking()
            }
        },
        modifier = modifier,
    ) { paddingValues ->
        @Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
        @SuppressLint("UnusedBoxWithConstraintsScope")
        BoxWithConstraints(
            modifier = Modifier
                .padding(paddingValues)
                .clipToBounds()
        ) {
            MapView(cameraState = cameraState)

            CustomFloatingButton(
                onClick = { navController.navigate(AppDestination.Settings) },
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    painterResource(R.drawable.ic_settings_24),
                    contentDescription = stringResource(R.string.permission_button_settings),
                    modifier = Modifier.requiredSize(24.dp)
                )
            }

            if (markerPosition.isSpecified) {
                LocationMarker(
                    onClick = { showTrackingInfoSheet = true },
                    state = when {
                        !showTrackingInfoSheet -> LocationMarkerState.Inactive
                        userName.isNullOrEmpty() -> LocationMarkerState.Error
                        else -> LocationMarkerState.Active
                    },
                    modifier = Modifier.offset(
                        x = markerPosition.x - LocationMarkerSize / 2,
                        y = markerPosition.y - LocationMarkerSize / 2
                    )
                )
            }
        }
    }

    if (showTrackingInfoSheet) {
        TrackingInfoSheet(
            onDismissRequest = { showTrackingInfoSheet = false },
            userName = userName,
            location = latestLocation,
            totalDistance = viewModel.totalDistance,
            lastUploadStatus = lastUploadStatus
        )
    }

    if (showBackgroundDeniedDialog) {
        PermissionDeniedDialog(
            text = context.getString(R.string.permission_denied_background_location),
            onDismissRequest = { showBackgroundDeniedDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { data ->
                CustomSnackbar(
                    type = CustomSnackbarType.valueOf(data.visuals.actionLabel!!),
                    message = data.visuals.message,
                    onClick = { data.dismiss() }
                )
            }
        )
    }
}

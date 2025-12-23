package com.websmithing.gpstracker2.ui.features.home

import android.annotation.SuppressLint
import android.location.Location
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
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.components.CustomPermissionDeniedDialog
import com.websmithing.gpstracker2.ui.components.CustomSnackbar
import com.websmithing.gpstracker2.ui.components.CustomSnackbarType
import com.websmithing.gpstracker2.ui.features.home.components.DEFAULT_MAP_ZOOM
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
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds

private fun Location.toPosition() = Position(longitude = longitude, latitude = latitude, altitude = altitude)

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
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val markerPosition by remember(cameraState.position, latestLocation) {
        derivedStateOf {
            latestLocation?.let { location ->
                cameraState.projection?.screenLocationFromPosition(location.toPosition())
            } ?: DpOffset.Unspecified
        }
    }

    val isTracking by viewModel.isTracking.observeAsState(false)
    val trackerIdentifier by viewModel.trackerIdentifier.observeAsState()
    val uploadServer by viewModel.uploadServer.observeAsState()
    val canRunTracking by remember(trackerIdentifier, uploadServer) {
        derivedStateOf {
            !uploadServer.isNullOrBlank() && !trackerIdentifier.isNullOrBlank()
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
                    zoom = if (oldZoom == 1.0) DEFAULT_MAP_ZOOM else oldZoom,
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
                    actionLabel = CustomSnackbarType.SUCCESS.name,
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
                    actionLabel = CustomSnackbarType.WARNING.name,
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
                    actionLabel = CustomSnackbarType.SUCCESS.name,
                    duration = SnackbarDuration.Short
                )
            }
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
                    context.getString(R.string.error_format, e.localizedMessage ?: R.string.unknown_error),
                    actionLabel = CustomSnackbarType.WARNING.name,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            TrackingButton(
                state = if (!canRunTracking) {
                    TrackingButtonState.STOP
                } else if (isTracking) {
                    TrackingButtonState.PAUSE
                } else {
                    TrackingButtonState.PLAY
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
                        !showTrackingInfoSheet -> LocationMarkerState.INACTIVE
                        trackerIdentifier.isNullOrEmpty() -> LocationMarkerState.ERROR
                        else -> LocationMarkerState.ACTIVE
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
            trackerIdentifier = trackerIdentifier,
            location = latestLocation,
            lastUploadStatus = lastUploadStatus
        )
    }

    if (showBackgroundDeniedDialog) {
        CustomPermissionDeniedDialog(
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

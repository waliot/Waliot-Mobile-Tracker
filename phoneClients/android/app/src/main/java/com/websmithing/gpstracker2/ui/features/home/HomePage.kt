package com.websmithing.gpstracker2.ui.features.home

import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.repository.settings.canRunTrackingConfiguration
import com.websmithing.gpstracker2.repository.upload.UploadStatus
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.components.CustomPermissionDeniedDialog
import com.websmithing.gpstracker2.ui.components.CustomSnackbar
import com.websmithing.gpstracker2.ui.components.CustomSnackbarType
import com.websmithing.gpstracker2.ui.isIgnoringBatteryOptimizations
import com.websmithing.gpstracker2.ui.isForegroundLocationPermissionGranted
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarker
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerSize
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerState
import com.websmithing.gpstracker2.ui.features.home.components.LocationPermissionFlow
import com.websmithing.gpstracker2.ui.features.home.components.MapView
import com.websmithing.gpstracker2.ui.features.home.components.INITIAL_TRACKING_MAP_ZOOM
import com.websmithing.gpstracker2.ui.features.home.components.buildTrackingCameraPosition
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButton
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButtonState
import com.websmithing.gpstracker2.ui.features.home.components.TrackingInfoSheet
import com.websmithing.gpstracker2.ui.isBackgroundLocationPermissionGranted
import com.websmithing.gpstracker2.ui.openBatteryOptimizationSettings
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showBackgroundDeniedDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var hasCenteredInitialCamera by remember { mutableStateOf(false) }

    val cameraState = rememberCameraState()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val locationPresentation by viewModel.locationPresentation.collectAsStateWithLifecycle()
    val markerPosition by remember(cameraState.position, latestLocation) {
        derivedStateOf {
            latestLocation?.let { location ->
                cameraState.projection?.screenLocationFromPosition(location.toPosition())
            } ?: DpOffset.Unspecified
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.observeAsState()

    val isTracking by viewModel.isTracking.observeAsState(false)
    val batteryOptimizationWarningShown by viewModel.batteryOptimizationWarningShown.observeAsState(false)
    val lastUploadStatus by viewModel.lastUploadStatus.collectAsStateWithLifecycle()
    val bufferCount by viewModel.bufferCount.collectAsStateWithLifecycle()
    var showTrackingInfoSheet by remember { mutableStateOf(false) }

    val trackerIdentifier by viewModel.trackerIdentifier.observeAsState()
    val uploadServer by viewModel.uploadServer.observeAsState()
    val canRunTracking by remember(trackerIdentifier, uploadServer) {
        derivedStateOf {
            canRunTrackingConfiguration(
                trackerIdentifier = trackerIdentifier,
                uploadServer = uploadServer,
            )
        }
    }

    LocationPermissionFlow(
        onAllow = { viewModel.startForegroundLocation() },
        onDeny = { viewModel.stopForegroundLocation() }
    )

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (isForegroundLocationPermissionGranted(context)) {
                        viewModel.startForegroundLocation()
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    viewModel.stopForegroundLocation()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopForegroundLocation()
        }
    }

    LaunchedEffect(latestLocation) {
        latestLocation?.let {
            cameraState.animateTo(
                buildTrackingCameraPosition(
                    location = it,
                    currentPosition = cameraState.position,
                    preferredZoom = if (hasCenteredInitialCamera) null else INITIAL_TRACKING_MAP_ZOOM,
                ),
                duration = 500.milliseconds,
            )
            hasCenteredInitialCamera = true
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

    LaunchedEffect(lastUploadStatus) {
        when (val status = lastUploadStatus) {
            is UploadStatus.Offline -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.upload_status_offline_snackbar),
                    actionLabel = CustomSnackbarType.WARNING.name,
                    duration = SnackbarDuration.Short
                )
            }

            is UploadStatus.Failure -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.upload_status_failure_snackbar),
                    actionLabel = CustomSnackbarType.WARNING.name,
                    duration = SnackbarDuration.Short
                )
            }

            else -> {}
        }
    }

    fun switchTracking() {
        val decision = decideTrackingToggle(
            isTracking = isTracking,
            canRunTracking = canRunTracking,
            requiresBackgroundLocationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            hasBackgroundLocationPermission = isBackgroundLocationPermissionGranted(context),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
            hasShownBatteryOptimizationWarning = batteryOptimizationWarningShown,
        )

        if (!decision.shouldProceed) {
            if (decision.blocker == TrackingToggleBlocker.MissingBackgroundLocationPermission) {
                showBackgroundDeniedDialog = true
            }
            return
        }

        if (decision.showBatteryOptimizationWarning) {
            viewModel.markBatteryOptimizationWarningShown()
            showBatteryOptimizationDialog = true
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
                        locationPresentation.state == TrackingLocationUiState.Suspect -> LocationMarkerState.ERROR
                        locationPresentation.state == TrackingLocationUiState.FreshGps -> LocationMarkerState.ACTIVE
                        locationPresentation.state == TrackingLocationUiState.FreshDegraded ||
                            locationPresentation.state == TrackingLocationUiState.StaleGps ||
                            locationPresentation.state == TrackingLocationUiState.StaleDegraded -> LocationMarkerState.WARNING
                        else -> LocationMarkerState.INACTIVE
                    },
                    rotation = -45f,
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
            locationPresentation = locationPresentation,
            lastUploadStatus = lastUploadStatus,
            bufferCount = bufferCount
        )
    }

    if (showBackgroundDeniedDialog) {
        CustomPermissionDeniedDialog(
            text = context.getString(R.string.permission_denied_background_location),
            onDismissRequest = { showBackgroundDeniedDialog = false }
        )
    }

    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            title = { Text(context.getString(R.string.battery_optimization_warning_title)) },
            text = { Text(context.getString(R.string.battery_optimization_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        openBatteryOptimizationSettings(context)
                    }
                ) {
                    Text(context.getString(R.string.permission_button_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        viewModel.startTracking()
                    }
                ) {
                    Text(context.getString(R.string.permission_button_continue))
                }
            }
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

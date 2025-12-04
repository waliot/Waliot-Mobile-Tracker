package com.websmithing.gpstracker2.ui.features.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.activityHiltViewModel
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarker
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerSize
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerState
import com.websmithing.gpstracker2.ui.features.home.components.MapView
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButton
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButtonState
import com.websmithing.gpstracker2.ui.features.home.components.TrackingInfoSheet
import com.websmithing.gpstracker2.ui.features.home.components.getUserLocation
import com.websmithing.gpstracker2.ui.router.AppDestination
import com.websmithing.gpstracker2.ui.toPosition
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
    val cameraState = rememberCameraState()
    val latestLocation = getUserLocation()
    val markerPosition by remember(cameraState.position, latestLocation) {
        derivedStateOf {
            latestLocation?.let { location ->
                cameraState.projection?.screenLocationFromPosition(location.toPosition())
            } ?: DpOffset.Zero
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

    var showTrackingInfoSheet by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier,
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
                if (isTracking) {
                    viewModel.stopTracking()
                } else {
                    viewModel.startTracking()
                }
            }
        }
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

            LocationMarker(
                onClick = { showTrackingInfoSheet = true },
                state = if (showTrackingInfoSheet) {
                    if (userName == null || userName!!.isEmpty()) {
                        LocationMarkerState.Error
                    } else {
                        LocationMarkerState.Active
                    }
                } else {
                    LocationMarkerState.Inactive
                },
                modifier = Modifier.offset(
                    x = markerPosition.x - LocationMarkerSize,
                    y = markerPosition.y + LocationMarkerSize
                )
            )
        }
    }

    if (showTrackingInfoSheet) {
        TrackingInfoSheet(
            onDismissRequest = { showTrackingInfoSheet = false },
            userName = userName,
            location = latestLocation,
            totalDistance = viewModel.totalDistance
        )
    }
}
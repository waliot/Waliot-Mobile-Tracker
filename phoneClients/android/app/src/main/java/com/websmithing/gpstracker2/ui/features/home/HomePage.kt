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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.websmithing.gpstracker2.R
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.components.CustomFloatingButton
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarker
import com.websmithing.gpstracker2.ui.features.home.components.LocationMarkerSize
import com.websmithing.gpstracker2.ui.features.home.components.LocationPermissionFlow
import com.websmithing.gpstracker2.ui.features.home.components.MapView
import com.websmithing.gpstracker2.ui.features.home.components.TrackingButton
import com.websmithing.gpstracker2.ui.toPosition
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import kotlin.time.Duration.Companion.milliseconds

private const val defaultZoom = 15.0

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomePage(
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val cameraState = rememberCameraState()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val markerPosition by remember(cameraState.position, latestLocation) {
        derivedStateOf {
            latestLocation?.let { location ->
                cameraState.projection?.screenLocationFromPosition(location.toPosition())
            } ?: DpOffset.Zero
        }
    }

    LaunchedEffect(true) {
        viewModel.latestLocation.collect { location ->
            val oldZoom = cameraState.position.zoom
            location?.let {
                cameraState.animateTo(
                    CameraPosition(
                        target = it.toPosition(),
                        zoom = if (oldZoom == 1.0) defaultZoom else oldZoom,
                    ),
                    duration = 500.milliseconds,
                )
            }
        }
    }

    LocationPermissionFlow(
        onStartBackgroundService = { viewModel.startTracking() },
        onStopBackgroundService = { viewModel.forceStopTracking() }
    )

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            TrackingButton { }
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
                onClick = {},
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
                onClick = {},
                modifier = Modifier.offset(
                    x = markerPosition.x - LocationMarkerSize,
                    y = markerPosition.y + LocationMarkerSize
                )
            )
        }
    }
}
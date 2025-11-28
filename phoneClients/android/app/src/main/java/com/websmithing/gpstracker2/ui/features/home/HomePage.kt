package com.websmithing.gpstracker2.ui.features.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.websmithing.gpstracker2.ui.TrackingViewModel
import com.websmithing.gpstracker2.ui.features.components.MapView
import com.websmithing.gpstracker2.ui.toPosition
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import kotlin.time.Duration.Companion.milliseconds

private const val defaultZoom = 15.0

@Composable
fun HomePage(
    viewModel: TrackingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val cameraState = rememberCameraState()

    LaunchedEffect(true) {
        viewModel.startTracking()
    }

    LaunchedEffect(viewModel.latestLocation) {
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

    Scaffold(modifier = modifier) { paddingValues ->
        MapView(
            cameraState = cameraState,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
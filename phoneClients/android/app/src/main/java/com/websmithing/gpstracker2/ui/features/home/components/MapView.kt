package com.websmithing.gpstracker2.ui.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle

private const val mapStyleUri = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapView(modifier: Modifier = Modifier, cameraState: CameraState) {
    MaplibreMap(
        baseStyle = BaseStyle.Uri(mapStyleUri),
        cameraState = cameraState,
        options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
        modifier = modifier,
    )
}

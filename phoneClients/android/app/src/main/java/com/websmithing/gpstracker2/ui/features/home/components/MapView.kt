package com.websmithing.gpstracker2.ui.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle

const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
const val DEFAULT_MAP_ZOOM = 15.0

@Composable
fun MapView(modifier: Modifier = Modifier, cameraState: CameraState) {
    MaplibreMap(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
        cameraState = cameraState,
        options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
        modifier = modifier
    )
}

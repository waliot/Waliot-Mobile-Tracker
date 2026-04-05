package com.websmithing.gpstracker2.ui.features.home.components

import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

internal const val TRACKING_MAP_TILT = 45.0
internal const val INITIAL_TRACKING_MAP_ZOOM = 16.0
private val TRACKING_MAP_PADDING = PaddingValues(bottom = 120.dp)

internal fun buildTrackingCameraPosition(
    location: Location,
    currentPosition: CameraPosition,
    preferredZoom: Double? = null,
): CameraPosition {
    val zoom = preferredZoom ?: if (currentPosition.zoom == 1.0) DEFAULT_MAP_ZOOM else currentPosition.zoom
    val bearing = if (location.hasBearing()) {
        location.bearing.toDouble()
    } else {
        currentPosition.bearing
    }

    return CameraPosition(
        target = Position(longitude = location.longitude, latitude = location.latitude, altitude = location.altitude),
        zoom = zoom,
        bearing = bearing,
        tilt = TRACKING_MAP_TILT,
        padding = TRACKING_MAP_PADDING,
    )
}

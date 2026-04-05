package com.websmithing.gpstracker2.ui.features.home.components

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TrackingCameraTest {

    @Test
    fun `tracking camera follows location with tilt and bearing`() {
        val location = mock<Location>()
        whenever(location.latitude).thenReturn(55.75)
        whenever(location.longitude).thenReturn(37.61)
        whenever(location.altitude).thenReturn(120.0)
        whenever(location.hasBearing()).thenReturn(true)
        whenever(location.bearing).thenReturn(135f)

        val position = buildTrackingCameraPosition(
            location = location,
            currentPosition = CameraPosition(
                target = Position(0.0, 0.0),
                zoom = 15.0,
                bearing = 0.0,
                tilt = 0.0,
            ),
        )

        assertEquals(37.61, position.target.longitude, 0.0)
        assertEquals(55.75, position.target.latitude, 0.0)
        assertEquals(120.0, position.target.altitude ?: 0.0, 0.0)
        assertEquals(15.0, position.zoom, 0.0)
        assertEquals(135.0, position.bearing, 0.0)
        assertEquals(TRACKING_MAP_TILT, position.tilt, 0.0)
    }

    @Test
    fun `tracking camera keeps previous bearing when location has no heading`() {
        val location = mock<Location>()
        whenever(location.latitude).thenReturn(55.75)
        whenever(location.longitude).thenReturn(37.61)
        whenever(location.altitude).thenReturn(120.0)
        whenever(location.hasBearing()).thenReturn(false)

        val position = buildTrackingCameraPosition(
            location = location,
            currentPosition = CameraPosition(
                target = Position(0.0, 0.0),
                zoom = DEFAULT_MAP_ZOOM,
                bearing = 42.0,
                tilt = 0.0,
            ),
        )

        assertEquals(42.0, position.bearing, 0.0)
        assertEquals(TRACKING_MAP_TILT, position.tilt, 0.0)
    }

    @Test
    fun `tracking camera applies preferred zoom for initial focus`() {
        val location = mock<Location>()
        whenever(location.latitude).thenReturn(55.75)
        whenever(location.longitude).thenReturn(37.61)
        whenever(location.altitude).thenReturn(120.0)
        whenever(location.hasBearing()).thenReturn(false)

        val position = buildTrackingCameraPosition(
            location = location,
            currentPosition = CameraPosition(
                target = Position(0.0, 0.0),
                zoom = DEFAULT_MAP_ZOOM,
                bearing = 0.0,
                tilt = 0.0,
            ),
            preferredZoom = INITIAL_TRACKING_MAP_ZOOM,
        )

        assertEquals(INITIAL_TRACKING_MAP_ZOOM, position.zoom, 0.0)
        assertEquals(TRACKING_MAP_TILT, position.tilt, 0.0)
    }
}

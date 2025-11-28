package com.websmithing.gpstracker2.ui

import android.location.Location
import org.maplibre.spatialk.geojson.Position

fun Location.toPosition() =
    Position(longitude = longitude, latitude = latitude, altitude = altitude)
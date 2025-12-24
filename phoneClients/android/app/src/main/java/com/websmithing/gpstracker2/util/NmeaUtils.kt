package com.websmithing.gpstracker2.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object NmeaUtils {

    private const val MIN_LATITUDE_VALUE: Double = -90.0
    private const val MAX_LATITUDE_VALUE: Double = 90.0
    private const val MIN_LONGITUDE_VALUE: Double = -180.0
    private const val MAX_LONGITUDE_VALUE: Double = 180.0

    private const val NORTH: Char = 'N'
    private const val SOUTH: Char = 'S'
    private const val WEST: Char = 'W'
    private const val EAST: Char = 'E'

    private const val NMEA_MINUTES_DIVIDER: Double = 1e4
    private const val NMEA_MINUTES_MAX_VALUE: Double = 60.0

    fun latitudeToDdm(latitude: Double, separator: String): String {
        require(latitude in MIN_LATITUDE_VALUE..MAX_LATITUDE_VALUE) {
            "Latitude value $latitude is out of range"
        }
        require(separator.isNotEmpty()) { "Separator cannot be null or empty" }

        val (degrees, wholeMinutes, fracMinutes) = buildDdm(latitude)
        return String.format(
            Locale.US, "%02d%02d.%04d%s%c",
            degrees, wholeMinutes, fracMinutes, separator, if (latitude >= 0) NORTH else SOUTH
        )
    }

    fun longitudeToDdm(longitude: Double, separator: String): String {
        require(longitude in MIN_LONGITUDE_VALUE..MAX_LONGITUDE_VALUE) {
            "Longitude value $longitude is out of range"
        }
        require(separator.isNotEmpty()) { "Separator cannot be null or empty" }

        val (degrees, wholeMinutes, fracMinutes) = buildDdm(longitude)
        return String.format(
            Locale.US, "%03d%02d.%04d%s%c",
            degrees, wholeMinutes, fracMinutes, separator, if (longitude >= 0) EAST else WEST
        )
    }

    private fun buildDdm(value: Double): IntArray {
        val absValue = abs(value)

        val degrees = floor(absValue).toInt()
        var minutes = (absValue - degrees) * NMEA_MINUTES_MAX_VALUE

        minutes = (minutes * NMEA_MINUTES_DIVIDER).roundToInt() / NMEA_MINUTES_DIVIDER
        if (minutes >= NMEA_MINUTES_MAX_VALUE) {
            minutes = 0.0
        }

        var wholeMinutes = minutes.toInt()
        var fracMinutes = ((minutes - wholeMinutes) * NMEA_MINUTES_DIVIDER).roundToInt()
        if (fracMinutes == NMEA_MINUTES_DIVIDER.toInt()) {
            fracMinutes = 0
            wholeMinutes += 1
        }

        return intArrayOf(degrees, wholeMinutes, fracMinutes)
    }
}

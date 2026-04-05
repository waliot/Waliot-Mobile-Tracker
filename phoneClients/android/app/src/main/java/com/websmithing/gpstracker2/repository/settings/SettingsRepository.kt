package com.websmithing.gpstracker2.repository.settings

interface SettingsRepository {

    companion object {
        const val PREFS_NAME = "com.waliot.tracker"

        const val KEY_TRACKING_STATE = "trackingState"
        const val DEFAULT_TRACKING_STATE = false

        const val KEY_TRACKER_IDENTIFIER = "trackerIdentifier"
        const val DEFAULT_TRACKER_IDENTIFIER = ""

        const val KEY_UPLOAD_SERVER = "uploadServer"
        const val DEFAULT_UPLOAD_SERVER = "device.waliot.com:30032"

        const val KEY_UPLOAD_TIME_INTERVAL = "uploadTimeInterval"
        const val DEFAULT_UPLOAD_TIME_INTERVAL = 5

        const val KEY_BUFFER_TIME_INTERVAL = "bufferTimeInterval"
        const val DEFAULT_BUFFER_TIME_INTERVAL = 1

        const val KEY_BUFFER_DISTANCE_INTERVAL = "bufferDistanceInterval"
        const val DEFAULT_BUFFER_DISTANCE_INTERVAL = 100

        const val KEY_LANGUAGE = "language"
        const val DEFAULT_LANGUAGE = "ru"

        const val KEY_BATTERY_OPTIMIZATION_WARNING_SHOWN = "batteryOptimizationWarningShown"
        const val DEFAULT_BATTERY_OPTIMIZATION_WARNING_SHOWN = false
    }

    fun peekTrackingState(): Boolean
    suspend fun getTrackingState(): Boolean
    suspend fun setTrackingState(isTracking: Boolean)

    suspend fun getTrackerIdentifier(): String
    suspend fun setTrackingIdentifier(trackerIdentifier: String)

    suspend fun getUploadServer(): String
    suspend fun setUploadServer(serverAddress: String)

    suspend fun getUploadTimeInterval(): Int
    suspend fun setUploadTimeInterval(intervalMinutes: Int)

    suspend fun getBufferTimeInterval(): Int
    suspend fun setBufferTimeInterval(intervalMinutes: Int)

    suspend fun getBufferDistanceInterval(): Int
    suspend fun setBufferDistanceInterval(intervalMeters: Int)

    fun peekLanguage(): String
    suspend fun getLanguage(): String
    suspend fun setLanguage(language: String)

    fun peekBatteryOptimizationWarningShown(): Boolean
    suspend fun getBatteryOptimizationWarningShown(): Boolean
    suspend fun setBatteryOptimizationWarningShown(shown: Boolean)
}

package com.websmithing.gpstracker2.repository.settings

interface SettingsRepository {

    companion object {
        const val PREFS_NAME = "com.waliot.tracker"

        const val KEY_APP_ID = "appID"

        const val KEY_TRACKING_STATE = "trackingState"
        const val DEFAULT_TRACKING_STATE = false

        const val KEY_TRACKER_IDENTIFIER = "trackerIdentifier"
        const val DEFAULT_TRACKER_IDENTIFIER = ""

        const val KEY_UPLOAD_SERVER = "uploadServer"
        const val DEFAULT_UPLOAD_SERVER = "device.waliot.com:30032"

        const val KEY_UPLOAD_TIME_INTERVAL = "uploadTimeInterval"
        const val DEFAULT_UPLOAD_TIME_INTERVAL = 1

        const val KEY_UPLOAD_DISTANCE_INTERVAL = "uploadDistanceInterval"
        const val DEFAULT_UPLOAD_DISTANCE_INTERVAL = 100

        const val KEY_LANGUAGE = "language"
        const val DEFAULT_LANGUAGE = "ru"
    }

    suspend fun getAppId(): String
    suspend fun generateAndSaveAppId(): String

    suspend fun getTrackingState(): Boolean
    suspend fun setTrackingState(isTracking: Boolean)

    suspend fun getTrackerIdentifier(): String
    suspend fun setTrackingIdentifier(trackerIdentifier: String)

    suspend fun getUploadServer(): String
    suspend fun setUploadServer(serverAddress: String)

    suspend fun getUploadTimeInterval(): Int
    suspend fun setUploadTimeInterval(intervalMinutes: Int)

    suspend fun getUploadDistanceInterval(): Int
    suspend fun setUploadDistanceInterval(intervalMeters: Int)

    suspend fun getLanguage(): String
    suspend fun setLanguage(language: String)

    suspend fun isFirstTimeLoading(): Boolean
    suspend fun setFirstTimeLoading(isFirst: Boolean)
}

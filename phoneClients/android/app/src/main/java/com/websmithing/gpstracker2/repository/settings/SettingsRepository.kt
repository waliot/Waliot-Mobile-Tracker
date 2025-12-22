package com.websmithing.gpstracker2.repository.settings

interface SettingsRepository {

    companion object {
        const val PREFS_NAME = "com.websmithing.gpstracker2.prefs"

        const val KEY_APP_ID = "appID"
        const val KEY_SESSION_ID = "sessionID"
        const val KEY_CURRENTLY_TRACKING = "currentlyTracking"

        const val KEY_USER_NAME = "userName"
        const val KEY_WEBSITE_URL = "defaultUploadWebsite"
        const val KEY_INTERVAL_MINUTES = "intervalInMinutes"
        const val KEY_LANGUAGE = "language"

        const val DEFAULT_WEBSITE_URL = "device.waliot.com:30032"
        const val DEFAULT_TRACKING_INTERVAL = 5
        const val DEFAULT_LANGUAGE = "ru"

        const val DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS = 10L
        const val DEFAULT_LOCATION_UPDATE_DISTANCE_METERS = 10f

        const val DEFAULT_MAP_ZOOM = 15.0
    }

    suspend fun isFirstTimeLoading(): Boolean
    suspend fun setFirstTimeLoading(isFirst: Boolean)

    suspend fun getAppId(): String
    suspend fun generateAndSaveAppId(): String

    suspend fun clearSessionId()
    suspend fun getCurrentSessionId(): String
    suspend fun saveSessionId(sessionId: String)

    suspend fun getCurrentTrackingState(): Boolean
    suspend fun setTrackingState(isTracking: Boolean)

    suspend fun getCurrentUsername(): String
    suspend fun saveUsername(username: String)

    suspend fun getCurrentWebsiteUrl(): String
    suspend fun saveWebsiteUrl(url: String)

    suspend fun getCurrentTrackingInterval(): Int
    suspend fun saveTrackingInterval(intervalMinutes: Int)

    suspend fun getCurrentLanguage(): String
    suspend fun saveLanguage(language: String)
}

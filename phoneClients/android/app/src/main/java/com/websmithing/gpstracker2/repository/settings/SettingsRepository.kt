package com.websmithing.gpstracker2.repository.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    suspend fun isFirstTimeLoading(): Boolean
    suspend fun setFirstTimeLoading(isFirst: Boolean)

    suspend fun isFirstTimeGettingPosition(): Boolean

    suspend fun getAppId(): String
    suspend fun generateAndSaveAppId(): String

    suspend fun clearSessionId()
    suspend fun getCurrentSessionId(): String
    suspend fun saveSessionId(sessionId: String)

    fun getUsername(): Flow<String>
    suspend fun getCurrentUsername(): String
    suspend fun saveUsername(username: String)

    fun getWebsiteUrl(): Flow<String>
    suspend fun getCurrentWebsiteUrl(): String
    suspend fun saveWebsiteUrl(url: String)

    fun getTrackingInterval(): Flow<Int>
    suspend fun getCurrentTrackingInterval(): Int
    suspend fun saveTrackingInterval(intervalMinutes: Int)

    fun isTracking(): Flow<Boolean>
    suspend fun getCurrentTrackingState(): Boolean
    suspend fun setTrackingState(isTracking: Boolean)

    suspend fun getTotalDistance(): Float

    suspend fun resetLocationStateForNewSession()
    suspend fun saveDistanceAndPositionFlags(totalDistance: Float, firstTime: Boolean)

    fun getCurrentLanguage(): String
    fun saveLanguage(language: String)
}

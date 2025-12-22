package com.websmithing.gpstracker2.repository.settings

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : SettingsRepository {

    companion object {
        private const val KEY_FIRST_TIME_GETTING_POSITION = "firstTimeGettingPosition"

        private const val KEY_APP_ID = "appID"
        private const val KEY_SESSION_ID = "sessionID"

        private const val KEY_USER_NAME = "userName"
        private const val KEY_WEBSITE_URL = "defaultUploadWebsite"
        private const val KEY_INTERVAL_MINUTES = "intervalInMinutes"

        private const val KEY_CURRENTLY_TRACKING = "currentlyTracking"
        private const val KEY_TOTAL_DISTANCE = "totalDistanceInMeters"
        private const val KEY_PREVIOUS_LATITUDE = "previousLatitude"
        private const val KEY_PREVIOUS_LONGITUDE = "previousLongitude"

        private const val KEY_LANGUAGE = "language"
    }

    override suspend fun isFirstTimeLoading(): Boolean {
        return withContext(Dispatchers.IO) {
            !sharedPreferences.contains(KEY_APP_ID)
        }
    }

    override suspend fun setFirstTimeLoading(isFirst: Boolean) {
        withContext(Dispatchers.IO) {
            if (!isFirst) {
                getAppId()
            }
        }
    }

    override suspend fun isFirstTimeGettingPosition(): Boolean {
        return withContext(Dispatchers.IO) {
            sharedPreferences.getBoolean(KEY_FIRST_TIME_GETTING_POSITION, true)
        }
    }

    override suspend fun getAppId(): String {
        return withContext(Dispatchers.IO) {
            var appId = sharedPreferences.getString(KEY_APP_ID, null)
            if (appId == null) {
                appId = generateAndSaveAppId()
            }
            appId
        }
    }

    override suspend fun generateAndSaveAppId(): String {
        return withContext(Dispatchers.IO) {
            val newId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(KEY_APP_ID, newId).apply()
            newId
        }
    }

    override suspend fun clearSessionId() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().remove(KEY_SESSION_ID).apply()
        }
    }

    override suspend fun getCurrentSessionId(): String {
        return withContext(Dispatchers.IO) {
            val storedSessionId = sharedPreferences.getString(KEY_SESSION_ID, "") ?: ""
            if (storedSessionId.isBlank()) {
                val defaultSessionId = UUID.randomUUID().toString()
                sharedPreferences.edit().putString(KEY_SESSION_ID, defaultSessionId).apply()
                return@withContext defaultSessionId
            }
            storedSessionId
        }
    }

    override suspend fun saveSessionId(sessionId: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putString(KEY_SESSION_ID, sessionId.trim()).apply()
        }
    }

    override fun getUsername(): Flow<String> = flow {
        emit(getCurrentUsername())
    }

    override suspend fun getCurrentUsername(): String {
        return withContext(Dispatchers.IO) {
            val storedUsername = sharedPreferences.getString(KEY_USER_NAME, "") ?: ""
            storedUsername
        }
    }

    override suspend fun saveUsername(username: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putString(KEY_USER_NAME, username.trim()).apply()
        }
    }

    override fun getWebsiteUrl(): Flow<String> = flow {
        emit(getCurrentWebsiteUrl())
    }

    override suspend fun getCurrentWebsiteUrl(): String {
        return withContext(Dispatchers.IO) {
            sharedPreferences.getString(KEY_WEBSITE_URL, "device.waliot.com:30032")
                ?: "device.waliot.com:30032"
        }
    }

    override suspend fun saveWebsiteUrl(url: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putString(KEY_WEBSITE_URL, url.trim()).apply()
        }
    }

    override fun getTrackingInterval(): Flow<Int> = flow {
        emit(getCurrentTrackingInterval())
    }

    override suspend fun getCurrentTrackingInterval(): Int {
        return withContext(Dispatchers.IO) {
            sharedPreferences.getInt(KEY_INTERVAL_MINUTES, 1)
        }
    }

    override suspend fun saveTrackingInterval(intervalMinutes: Int) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putInt(KEY_INTERVAL_MINUTES, intervalMinutes).apply()
        }
    }

    override fun isTracking(): Flow<Boolean> = flow {
        emit(getCurrentTrackingState())
    }

    override suspend fun getCurrentTrackingState(): Boolean {
        return withContext(Dispatchers.IO) {
            sharedPreferences.getBoolean(KEY_CURRENTLY_TRACKING, false)
        }
    }

    override suspend fun setTrackingState(isTracking: Boolean) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putBoolean(KEY_CURRENTLY_TRACKING, isTracking).apply()
        }
    }

    override suspend fun getTotalDistance(): Float {
        return withContext(Dispatchers.IO) {
            sharedPreferences.getFloat(KEY_TOTAL_DISTANCE, 0f)
        }
    }

    override suspend fun resetLocationStateForNewSession() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().apply {
                putFloat(KEY_TOTAL_DISTANCE, 0f)
                putBoolean(KEY_FIRST_TIME_GETTING_POSITION, true)
                remove(KEY_PREVIOUS_LATITUDE)
                remove(KEY_PREVIOUS_LONGITUDE)
                apply()
            }
        }
    }

    override suspend fun saveDistanceAndPositionFlags(totalDistance: Float, firstTime: Boolean) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().apply {
                putFloat(KEY_TOTAL_DISTANCE, totalDistance)
                putBoolean(KEY_FIRST_TIME_GETTING_POSITION, firstTime)
                apply()
            }
        }
    }

    override fun getCurrentLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, "ru") ?: "ru"
    }

    override fun saveLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language.trim()).apply()

    }
}

package com.websmithing.gpstracker2.repository.settings

import android.content.SharedPreferences
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKING_STATE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_APP_ID
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_TRACKING_STATE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_UPLOAD_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_UPLOAD_TIME_INTERVAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : SettingsRepository {

    override suspend fun getAppId(): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_APP_ID, null) ?: generateAndSaveAppId()
    }

    override suspend fun generateAndSaveAppId(): String = withContext(Dispatchers.IO) {
        UUID.randomUUID().toString().also { newId ->
            updateSettings { putString(KEY_APP_ID, newId) }
        }
    }

    override suspend fun getTrackingState(): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.getBoolean(KEY_TRACKING_STATE, DEFAULT_TRACKING_STATE)
    }

    override suspend fun setTrackingState(isTracking: Boolean) = updateSettings {
        putBoolean(KEY_TRACKING_STATE, isTracking)
    }

    override suspend fun getTrackerIdentifier(): String = getStringOrDefault(KEY_TRACKER_IDENTIFIER, DEFAULT_TRACKER_IDENTIFIER)

    override suspend fun setTrackingIdentifier(trackerIdentifier: String) = updateSettings {
        putString(KEY_TRACKER_IDENTIFIER, trackerIdentifier.trim())
    }

    override suspend fun getUploadServer(): String = getStringOrDefault(KEY_UPLOAD_SERVER, DEFAULT_UPLOAD_SERVER)

    override suspend fun setUploadServer(serverAddress: String) = updateSettings {
        putString(KEY_UPLOAD_SERVER, serverAddress.trim())
    }

    override suspend fun getUploadTimeInterval(): Int = withContext(Dispatchers.IO) {
        sharedPreferences.getInt(KEY_UPLOAD_TIME_INTERVAL, DEFAULT_UPLOAD_TIME_INTERVAL)
    }

    override suspend fun setUploadTimeInterval(intervalMinutes: Int) = updateSettings {
        putInt(KEY_UPLOAD_TIME_INTERVAL, intervalMinutes)
    }

    override suspend fun getUploadDistanceInterval(): Int = withContext(Dispatchers.IO) {
        sharedPreferences.getInt(KEY_UPLOAD_DISTANCE_INTERVAL, DEFAULT_UPLOAD_DISTANCE_INTERVAL)
    }

    override suspend fun setUploadDistanceInterval(intervalMeters: Int) = updateSettings {
        putInt(KEY_UPLOAD_DISTANCE_INTERVAL, intervalMeters)
    }

    override suspend fun getLanguage(): String = getStringOrDefault(KEY_LANGUAGE, DEFAULT_LANGUAGE)

    override suspend fun setLanguage(language: String) = updateSettings {
        putString(KEY_LANGUAGE, language.trim())
    }

    override suspend fun isFirstTimeLoading(): Boolean = withContext(Dispatchers.IO) {
        !sharedPreferences.contains(KEY_APP_ID)
    }

    override suspend fun setFirstTimeLoading(isFirst: Boolean) {
        if (!isFirst) getAppId()
    }

    private suspend fun getStringOrDefault(key: String, default: String): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(key, default) ?: default
    }

    private suspend fun updateSettings(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().apply(block).apply()
        }
    }
}

package com.websmithing.gpstracker2.repository.settings

import android.content.SharedPreferences
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKING_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_WEBSITE_URL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_APP_ID
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_CURRENTLY_TRACKING
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_INTERVAL_MINUTES
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_SESSION_ID
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_USER_NAME
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_WEBSITE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : SettingsRepository {

    override suspend fun isFirstTimeLoading(): Boolean = withContext(Dispatchers.IO) {
        !sharedPreferences.contains(KEY_APP_ID)
    }

    override suspend fun setFirstTimeLoading(isFirst: Boolean) {
        if (!isFirst) getAppId()
    }

    override suspend fun getAppId(): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_APP_ID, null) ?: generateAndSaveAppId()
    }

    override suspend fun generateAndSaveAppId(): String = withContext(Dispatchers.IO) {
        UUID.randomUUID().toString().also { newId ->
            updateSettings { putString(KEY_APP_ID, newId) }
        }
    }

    override suspend fun clearSessionId() = updateSettings {
        remove(KEY_SESSION_ID)
    }

    override suspend fun getCurrentSessionId(): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_SESSION_ID, "").let { id ->
            if (id.isNullOrBlank()) {
                UUID.randomUUID().toString().also { newId ->
                    updateSettings { putString(KEY_SESSION_ID, newId) }
                }
            } else id
        }
    }

    override suspend fun saveSessionId(sessionId: String) = updateSettings {
        putString(KEY_SESSION_ID, sessionId.trim())
    }

    override suspend fun getCurrentTrackingState(): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.getBoolean(KEY_CURRENTLY_TRACKING, false)
    }

    override suspend fun setTrackingState(isTracking: Boolean) = updateSettings {
        putBoolean(KEY_CURRENTLY_TRACKING, isTracking)
    }

    override suspend fun getCurrentUsername(): String = getStringOrDefault(KEY_USER_NAME, "")

    override suspend fun saveUsername(username: String) = updateSettings {
        putString(KEY_USER_NAME, username.trim())
    }

    override suspend fun getCurrentWebsiteUrl(): String = getStringOrDefault(KEY_WEBSITE_URL, DEFAULT_WEBSITE_URL)

    override suspend fun saveWebsiteUrl(url: String) = updateSettings {
        putString(KEY_WEBSITE_URL, url.trim())
    }

    override suspend fun getCurrentTrackingInterval(): Int = withContext(Dispatchers.IO) {
        sharedPreferences.getInt(KEY_INTERVAL_MINUTES, DEFAULT_TRACKING_INTERVAL)
    }

    override suspend fun saveTrackingInterval(intervalMinutes: Int) = updateSettings {
        putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
    }

    override suspend fun getCurrentLanguage(): String = getStringOrDefault(KEY_LANGUAGE, DEFAULT_LANGUAGE)

    override suspend fun saveLanguage(language: String) = updateSettings {
        putString(KEY_LANGUAGE, language.trim())
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

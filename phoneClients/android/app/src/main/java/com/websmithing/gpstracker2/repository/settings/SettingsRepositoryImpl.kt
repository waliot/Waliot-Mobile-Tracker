package com.websmithing.gpstracker2.repository.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BUFFER_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BUFFER_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BATTERY_OPTIMIZATION_WARNING_SHOWN
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKING_STATE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_BUFFER_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_BUFFER_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_BATTERY_OPTIMIZATION_WARNING_SHOWN
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_TRACKING_STATE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.KEY_UPLOAD_TIME_INTERVAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : SettingsRepository {

    private companion object {
        const val LEGACY_KEY_UPLOAD_DISTANCE_INTERVAL = "uploadDistanceInterval"
    }

    override fun peekTrackingState(): Boolean {
        return sharedPreferences.getBoolean(KEY_TRACKING_STATE, DEFAULT_TRACKING_STATE)
    }

    override suspend fun getTrackingState(): Boolean = withContext(Dispatchers.IO) {
        peekTrackingState()
    }

    override suspend fun setTrackingState(isTracking: Boolean) = updateSettings {
        putBoolean(KEY_TRACKING_STATE, isTracking)
    }

    override suspend fun getTrackerIdentifier(): String = getValidatedStringSetting(
        key = KEY_TRACKER_IDENTIFIER,
        default = DEFAULT_TRACKER_IDENTIFIER,
        sanitize = ::sanitizeTrackerIdentifierSetting,
    )

    override suspend fun setTrackingIdentifier(trackerIdentifier: String) = updateSettings {
        putString(KEY_TRACKER_IDENTIFIER, trackerIdentifier.trim())
    }

    override suspend fun getUploadServer(): String = getValidatedStringSetting(
        key = KEY_UPLOAD_SERVER,
        default = DEFAULT_UPLOAD_SERVER,
        sanitize = ::sanitizeUploadServerSetting,
    )

    override suspend fun setUploadServer(serverAddress: String) = updateSettings {
        putString(KEY_UPLOAD_SERVER, serverAddress.trim())
    }

    override suspend fun getUploadTimeInterval(): Int = getValidatedIntSetting(
        key = KEY_UPLOAD_TIME_INTERVAL,
        defaultValue = DEFAULT_UPLOAD_TIME_INTERVAL,
    )

    override suspend fun setUploadTimeInterval(intervalMinutes: Int) = updateSettings {
        putInt(KEY_UPLOAD_TIME_INTERVAL, intervalMinutes)
    }

    override suspend fun getBufferTimeInterval(): Int = migrateValidatedIntSettingIfMissing(
        targetKey = KEY_BUFFER_TIME_INTERVAL,
        defaultValue = DEFAULT_BUFFER_TIME_INTERVAL,
        legacyKey = KEY_UPLOAD_TIME_INTERVAL,
        removeLegacyKey = false,
    )

    override suspend fun setBufferTimeInterval(intervalMinutes: Int) = updateSettings {
        putInt(KEY_BUFFER_TIME_INTERVAL, intervalMinutes)
    }

    override suspend fun getBufferDistanceInterval(): Int = migrateValidatedIntSettingIfMissing(
        targetKey = KEY_BUFFER_DISTANCE_INTERVAL,
        defaultValue = DEFAULT_BUFFER_DISTANCE_INTERVAL,
        legacyKey = LEGACY_KEY_UPLOAD_DISTANCE_INTERVAL,
        removeLegacyKey = true,
    )

    override suspend fun setBufferDistanceInterval(intervalMeters: Int) = updateSettings {
        putInt(KEY_BUFFER_DISTANCE_INTERVAL, intervalMeters)
        remove(LEGACY_KEY_UPLOAD_DISTANCE_INTERVAL)
    }

    override fun peekLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    override suspend fun getLanguage(): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    override suspend fun setLanguage(language: String) = updateSettings {
        putString(KEY_LANGUAGE, language.trim())
    }

    override fun peekBatteryOptimizationWarningShown(): Boolean {
        return sharedPreferences.getBoolean(
            KEY_BATTERY_OPTIMIZATION_WARNING_SHOWN,
            DEFAULT_BATTERY_OPTIMIZATION_WARNING_SHOWN
        )
    }

    override suspend fun getBatteryOptimizationWarningShown(): Boolean = withContext(Dispatchers.IO) {
        peekBatteryOptimizationWarningShown()
    }

    override suspend fun setBatteryOptimizationWarningShown(shown: Boolean) = updateSettings {
        putBoolean(KEY_BATTERY_OPTIMIZATION_WARNING_SHOWN, shown)
    }

    private suspend fun getValidatedStringSetting(
        key: String,
        default: String,
        sanitize: (String) -> String,
    ): String = withContext(Dispatchers.IO) {
        val rawValue = sharedPreferences.getString(key, null)
        val sanitizedValue = sanitize(rawValue ?: default)
        if (rawValue != null && rawValue != sanitizedValue) {
            sharedPreferences.edit {
                putString(key, sanitizedValue)
            }
        }
        sanitizedValue
    }

    private suspend fun getValidatedIntSetting(
        key: String,
        defaultValue: Int,
    ): Int = withContext(Dispatchers.IO) {
        val storedValue = sharedPreferences.getInt(key, defaultValue)
        val sanitizedValue = sanitizePositiveIntervalSetting(storedValue, defaultValue)
        if (storedValue != sanitizedValue) {
            sharedPreferences.edit {
                putInt(key, sanitizedValue)
            }
        }
        sanitizedValue
    }

    private suspend fun migrateValidatedIntSettingIfMissing(
        targetKey: String,
        defaultValue: Int,
        legacyKey: String,
        removeLegacyKey: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        if (sharedPreferences.contains(targetKey)) {
            return@withContext getValidatedIntSetting(targetKey, defaultValue)
        }

        val migratedValue = if (sharedPreferences.contains(legacyKey)) {
            sharedPreferences.getInt(legacyKey, defaultValue)
        } else {
            defaultValue
        }
        val sanitizedValue = sanitizePositiveIntervalSetting(migratedValue, defaultValue)

        sharedPreferences.edit {
            putInt(targetKey, sanitizedValue)
            if (removeLegacyKey) {
                remove(legacyKey)
            }
        }

        sanitizedValue
    }

    private suspend fun updateSettings(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().apply(block).apply()
        }
    }
}

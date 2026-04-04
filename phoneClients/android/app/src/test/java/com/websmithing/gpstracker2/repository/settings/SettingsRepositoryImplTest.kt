package com.websmithing.gpstracker2.repository.settings

import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    @Test
    fun `defaults are returned when preferences are empty`() = runTest {
        val repository = SettingsRepositoryImpl(InMemorySharedPreferences())

        assertFalse(repository.peekTrackingState())
        assertFalse(repository.getTrackingState())
        assertEquals(SettingsRepository.DEFAULT_TRACKER_IDENTIFIER, repository.getTrackerIdentifier())
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_SERVER, repository.getUploadServer())
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_TIME_INTERVAL, repository.getUploadTimeInterval())
        assertEquals(SettingsRepository.DEFAULT_BUFFER_TIME_INTERVAL, repository.getBufferTimeInterval())
        assertEquals(SettingsRepository.DEFAULT_BUFFER_DISTANCE_INTERVAL, repository.getBufferDistanceInterval())
        assertEquals(SettingsRepository.DEFAULT_LANGUAGE, repository.peekLanguage())
        assertEquals(SettingsRepository.DEFAULT_LANGUAGE, repository.getLanguage())
        assertFalse(repository.peekBatteryOptimizationWarningShown())
        assertFalse(repository.getBatteryOptimizationWarningShown())
    }

    @Test
    fun `setters persist trimmed string values and numeric intervals`() = runTest {
        val repository = SettingsRepositoryImpl(InMemorySharedPreferences())

        repository.setTrackingState(true)
        repository.setTrackingIdentifier(" waliot-01 ")
        repository.setUploadServer(" device.waliot.com:30032 ")
        repository.setUploadTimeInterval(7)
        repository.setBufferTimeInterval(3)
        repository.setBufferDistanceInterval(250)
        repository.setLanguage(" en ")
        repository.setBatteryOptimizationWarningShown(true)

        assertTrue(repository.peekTrackingState())
        assertTrue(repository.getTrackingState())
        assertEquals("waliot-01", repository.getTrackerIdentifier())
        assertEquals("device.waliot.com:30032", repository.getUploadServer())
        assertEquals(7, repository.getUploadTimeInterval())
        assertEquals(3, repository.getBufferTimeInterval())
        assertEquals(250, repository.getBufferDistanceInterval())
        assertEquals("en", repository.peekLanguage())
        assertEquals("en", repository.getLanguage())
        assertTrue(repository.peekBatteryOptimizationWarningShown())
        assertTrue(repository.getBatteryOptimizationWarningShown())
    }

    @Test
    fun `new buffering settings migrate from legacy keys and become independent`() = runTest {
        val preferences = InMemorySharedPreferences(
            mutableMapOf(
                SettingsRepository.KEY_UPLOAD_TIME_INTERVAL to 11,
                "uploadDistanceInterval" to 333,
            )
        )
        val repository = SettingsRepositoryImpl(preferences)

        assertEquals(11, repository.getBufferTimeInterval())
        assertEquals(333, repository.getBufferDistanceInterval())
        assertEquals(11, preferences.getInt(SettingsRepository.KEY_BUFFER_TIME_INTERVAL, -1))
        assertEquals(333, preferences.getInt(SettingsRepository.KEY_BUFFER_DISTANCE_INTERVAL, -1))
        assertFalse(preferences.contains("uploadDistanceInterval"))

        repository.setUploadTimeInterval(20)

        assertEquals(11, repository.getBufferTimeInterval())
        assertEquals(20, repository.getUploadTimeInterval())
    }

    @Test
    fun `null strings fall back to defaults`() = runTest {
        val preferences = InMemorySharedPreferences(
            mutableMapOf(
                SettingsRepository.KEY_TRACKER_IDENTIFIER to null,
                SettingsRepository.KEY_UPLOAD_SERVER to null,
                SettingsRepository.KEY_LANGUAGE to null,
            )
        )
        val repository = SettingsRepositoryImpl(preferences)

        assertEquals(SettingsRepository.DEFAULT_TRACKER_IDENTIFIER, repository.getTrackerIdentifier())
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_SERVER, repository.getUploadServer())
        assertEquals(SettingsRepository.DEFAULT_LANGUAGE, repository.getLanguage())
        assertEquals(SettingsRepository.DEFAULT_LANGUAGE, repository.peekLanguage())
    }

    @Test
    fun `invalid persisted runtime settings are normalized before use`() = runTest {
        val preferences = InMemorySharedPreferences(
            mutableMapOf(
                SettingsRepository.KEY_TRACKER_IDENTIFIER to " bad id ",
                SettingsRepository.KEY_UPLOAD_SERVER to "bad server value",
                SettingsRepository.KEY_UPLOAD_TIME_INTERVAL to 0,
                SettingsRepository.KEY_BUFFER_TIME_INTERVAL to -1,
                SettingsRepository.KEY_BUFFER_DISTANCE_INTERVAL to 0,
            )
        )
        val repository = SettingsRepositoryImpl(preferences)

        assertEquals(SettingsRepository.DEFAULT_TRACKER_IDENTIFIER, repository.getTrackerIdentifier())
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_SERVER, repository.getUploadServer())
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_TIME_INTERVAL, repository.getUploadTimeInterval())
        assertEquals(SettingsRepository.DEFAULT_BUFFER_TIME_INTERVAL, repository.getBufferTimeInterval())
        assertEquals(SettingsRepository.DEFAULT_BUFFER_DISTANCE_INTERVAL, repository.getBufferDistanceInterval())

        assertEquals(SettingsRepository.DEFAULT_TRACKER_IDENTIFIER, preferences.getString(SettingsRepository.KEY_TRACKER_IDENTIFIER, null))
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_SERVER, preferences.getString(SettingsRepository.KEY_UPLOAD_SERVER, null))
        assertEquals(SettingsRepository.DEFAULT_UPLOAD_TIME_INTERVAL, preferences.getInt(SettingsRepository.KEY_UPLOAD_TIME_INTERVAL, -1))
        assertEquals(SettingsRepository.DEFAULT_BUFFER_TIME_INTERVAL, preferences.getInt(SettingsRepository.KEY_BUFFER_TIME_INTERVAL, -1))
        assertEquals(SettingsRepository.DEFAULT_BUFFER_DISTANCE_INTERVAL, preferences.getInt(SettingsRepository.KEY_BUFFER_DISTANCE_INTERVAL, -1))
    }
}

private class InMemorySharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        return values[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return values[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return values[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return values[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = values
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = Removed
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                values.clear()
            }
            pending.forEach { (key, value) ->
                if (value === Removed) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            pending.clear()
            clearRequested = false
        }
    }

    private companion object {
        val Removed = Any()
    }
}

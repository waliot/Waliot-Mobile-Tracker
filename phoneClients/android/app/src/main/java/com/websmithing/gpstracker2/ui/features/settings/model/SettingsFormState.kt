package com.websmithing.gpstracker2.ui.features.settings.model

import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BUFFER_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_BUFFER_TIME_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_TIME_INTERVAL

data class SettingsFormState(
    val trackerIdentifier: String = DEFAULT_TRACKER_IDENTIFIER,
    val uploadServer: String = DEFAULT_UPLOAD_SERVER,
    val uploadTimeInterval: String = DEFAULT_UPLOAD_TIME_INTERVAL.toString(),
    val bufferTimeInterval: String = DEFAULT_BUFFER_TIME_INTERVAL.toString(),
    val bufferDistanceInterval: String = DEFAULT_BUFFER_DISTANCE_INTERVAL.toString(),
    val languageCode: String = DEFAULT_LANGUAGE,

    val trackerIdentifierError: String? = null,
    val uploadServerError: String? = null,
    val uploadTimeIntervalError: String? = null,
    val bufferTimeIntervalError: String? = null,
    val bufferDistanceIntervalError: String? = null,
) {
    val isValid: Boolean
        get() = trackerIdentifierError == null &&
            uploadServerError == null &&
            uploadTimeIntervalError == null &&
            bufferTimeIntervalError == null &&
            bufferDistanceIntervalError == null

    fun hasSameInputs(other: SettingsFormState): Boolean {
        return trackerIdentifier == other.trackerIdentifier &&
            uploadServer == other.uploadServer &&
            uploadTimeInterval == other.uploadTimeInterval &&
            bufferTimeInterval == other.bufferTimeInterval &&
            bufferDistanceInterval == other.bufferDistanceInterval &&
            languageCode == other.languageCode
    }
}

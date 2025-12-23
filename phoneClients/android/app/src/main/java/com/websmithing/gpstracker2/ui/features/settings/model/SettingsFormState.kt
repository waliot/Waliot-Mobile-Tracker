package com.websmithing.gpstracker2.ui.features.settings.model

import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_DISTANCE_INTERVAL
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_UPLOAD_TIME_INTERVAL

data class SettingsFormState(
    val trackerIdentifier: String = DEFAULT_TRACKER_IDENTIFIER,
    val uploadServer: String = DEFAULT_UPLOAD_SERVER,
    val uploadTimeInterval: String = DEFAULT_UPLOAD_TIME_INTERVAL.toString(),
    val uploadDistanceInterval: String = DEFAULT_UPLOAD_DISTANCE_INTERVAL.toString(),
    val languageCode: String = DEFAULT_LANGUAGE,

    val trackerIdentifierError: String? = null,
    val uploadServerError: String? = null,
    val uploadTimeIntervalError: String? = null,
    val uploadDistanceIntervalError: String? = null,
)

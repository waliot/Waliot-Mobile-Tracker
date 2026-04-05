package com.websmithing.gpstracker2.repository.settings

import com.websmithing.gpstracker2.repository.upload.isUploadServerValid

internal fun isTrackerIdentifierSettingValid(identifier: String): Boolean =
    identifier.isNotBlank() &&
        identifier.none(Char::isWhitespace) &&
        ';' !in identifier &&
        '\r' !in identifier &&
        '\n' !in identifier

internal fun sanitizeTrackerIdentifierSetting(
    identifier: String,
    defaultValue: String = SettingsRepository.Companion.DEFAULT_TRACKER_IDENTIFIER,
): String {
    val trimmed = identifier.trim()
    return trimmed.takeIf(::isTrackerIdentifierSettingValid) ?: defaultValue
}

internal fun isUploadServerSettingValid(serverAddress: String): Boolean =
    isUploadServerValid(serverAddress)

internal fun sanitizeUploadServerSetting(
    serverAddress: String,
    defaultValue: String = SettingsRepository.Companion.DEFAULT_UPLOAD_SERVER,
): String {
    val trimmed = serverAddress.trim()
    return trimmed.takeIf(::isUploadServerSettingValid) ?: defaultValue
}

internal fun sanitizePositiveIntervalSetting(
    interval: Int,
    defaultValue: Int,
): Int {
    return interval.takeIf { it > 0 } ?: defaultValue
}

internal fun canRunTrackingConfiguration(
    trackerIdentifier: String?,
    uploadServer: String?,
): Boolean {
    return !trackerIdentifier.isNullOrBlank() &&
        !uploadServer.isNullOrBlank() &&
        isTrackerIdentifierSettingValid(trackerIdentifier.trim()) &&
        isUploadServerSettingValid(uploadServer.trim())
}

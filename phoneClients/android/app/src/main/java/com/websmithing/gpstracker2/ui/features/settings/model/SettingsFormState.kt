package com.websmithing.gpstracker2.ui.features.settings.model

data class SettingsFormState(
    val userName: String = "",
    val websiteUrl: String = "",
    val intervalTime: String = "5",
    val intervalDistance: String = "100",
    val languageCode: String = "",

    val userNameError: String? = null,
    val websiteUrlError: String? = null,
    val intervalTimeError: String? = null,
    val intervalDistanceError: String? = null,
)

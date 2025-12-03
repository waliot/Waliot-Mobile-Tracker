package com.websmithing.gpstracker2.ui.features.settings.model

data class SettingsFormState(
    val userName: String = "",
    val websiteUrl: String = "",
    val interval: Int = 1,
    val languageCode: String = "",
    val userNameError: String? = null,
    val websiteUrlError: String? = null,
)
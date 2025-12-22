package com.websmithing.gpstracker2.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import java.util.Locale

object LocaleHelper {

    fun onAttach(context: Context, settingsRepository: SettingsRepository): Context {
        val currentLanguage = settingsRepository.getCurrentLanguage()
        return setAppLocale(context, currentLanguage)
    }

    private fun setAppLocale(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val resources: Resources = context.resources
        val config: Configuration = resources.configuration
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}

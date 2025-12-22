package com.websmithing.gpstracker2.helper

import android.content.Context
import android.content.res.Configuration
import com.websmithing.gpstracker2.repository.settings.SettingsRepository
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import java.util.Locale

object LocaleHelper {

    suspend fun wrapContext(ctx: Context, settingsRepository: SettingsRepository): Context {
        val languageCode = try {
            settingsRepository.getCurrentLanguage()
        } catch (_: Exception) {
            DEFAULT_LANGUAGE
        }

        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(ctx.resources.configuration).apply {
            setLocale(locale)
        }

        return ctx.createConfigurationContext(config)
    }
}

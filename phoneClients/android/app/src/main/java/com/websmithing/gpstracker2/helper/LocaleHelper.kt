package com.websmithing.gpstracker2.helper

import android.content.Context
import android.content.res.Configuration
import com.websmithing.gpstracker2.repository.settings.SettingsRepository.Companion.DEFAULT_LANGUAGE
import java.util.Locale

object LocaleHelper {

    fun wrapContext(ctx: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(ctx.resources.configuration).apply {
            setLocale(locale)
        }

        return ctx.createConfigurationContext(config)
    }

    fun wrapContextFromSettings(ctx: Context, languageCode: String?): Context {
        return wrapContext(
            ctx = ctx,
            languageCode = languageCode?.takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE,
        )
    }
}

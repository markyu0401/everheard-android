package io.github.markyu0401.everheard.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import io.github.markyu0401.everheard.data.settings.AppLanguage
import io.github.markyu0401.everheard.data.settings.appSettingsStore
import java.util.Locale

/**
 * Applies the persisted in-app language before resources are resolved. Android
 * has no per-app locale API below API 33, so a configuration-wrapped base
 * context is the portable path. Every activity that shows user-facing text
 * extends this class.
 */
open class LocalizedActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.appSettingsStore().current.language
        super.attachBaseContext(newBase.withLanguage(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}

fun Context.withLanguage(language: AppLanguage): Context {
    val tag = language.tag ?: return this
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

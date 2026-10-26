package io.github.markyu0401.everheard.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    CHINESE("zh-CN")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM
)

/**
 * Small synchronous UI preferences store. It is deliberately SharedPreferences
 * rather than DataStore because [LocalizedActivity.attachBaseContext] must know
 * the language before the first resource lookup. The StateFlow gives Compose
 * immediate updates when the user changes a setting.
 */
class AppSettingsStore private constructor(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "everheard_ui",
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings
        get() = _settings.value

    fun setTheme(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
        _settings.value = _settings.value.copy(language = language)
    }

    private fun read(): AppSettings {
        val theme = preferences.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val language = preferences.getString(KEY_LANGUAGE, null)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: AppLanguage.SYSTEM
        return AppSettings(themeMode = theme, language = language)
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language"

        @Volatile
        private var instance: AppSettingsStore? = null

        fun get(context: Context): AppSettingsStore =
            instance ?: synchronized(this) {
                instance ?: AppSettingsStore(context).also { instance = it }
            }
    }
}

fun Context.appSettingsStore(): AppSettingsStore = AppSettingsStore.get(this)

package io.github.markyu0401.everheard.data.settings

import android.content.Context

/**
 * Package names whose audio playback should pause capture. The list is read by
 * the capture service on every evaluation, so a change in Settings takes effect
 * without a restart.
 */
class ExcludedAppsStore private constructor(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): Set<String> =
        preferences.getStringSet(KEY_PACKAGES, emptySet())?.toSet().orEmpty()

    fun save(packages: Set<String>) {
        preferences.edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    companion object {
        private const val PREFS_NAME = "everheard_excluded_apps"
        const val KEY_PACKAGES = "excluded_packages"

        @Volatile
        private var instance: ExcludedAppsStore? = null

        fun get(context: Context): ExcludedAppsStore =
            instance ?: synchronized(this) {
                instance ?: ExcludedAppsStore(context).also { instance = it }
            }
    }
}

fun Context.excludedAppsStore(): ExcludedAppsStore = ExcludedAppsStore.get(this)

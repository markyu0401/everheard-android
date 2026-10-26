package io.github.markyu0401.everheard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.data.settings.appSettingsStore

/**
 * Reads the persisted theme + language preferences and applies them to the
 * current activity. Language is applied earlier in LocalizedActivity; this
 * wrapper owns the live theme switch and the system-bar icon contrast.
 */
@Composable
fun EverheardAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { context.appSettingsStore() }
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = store.current)
    val darkTheme = settings.themeMode.resolve(isSystemInDarkTheme())

    EverheardTheme(darkTheme = darkTheme) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val activity = view.context as? Activity ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
        content()
    }
}

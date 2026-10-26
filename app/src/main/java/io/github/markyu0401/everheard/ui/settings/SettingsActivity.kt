package io.github.markyu0401.everheard.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.settings.AppLanguage
import io.github.markyu0401.everheard.data.settings.ThemeMode
import io.github.markyu0401.everheard.data.settings.appSettingsStore
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.components.SelectionRow
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme

class SettingsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardAppTheme {
                SettingsScreen(
                    onBack = { finish() },
                    onLanguageSelected = { language ->
                        val store = appSettingsStore()
                        if (store.current.language != language) {
                            store.setLanguage(language)
                            restartApp()
                        }
                    }
                )
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { context.appSettingsStore() }
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = store.current)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)

        LargeTitle(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle)
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader(stringResource(R.string.settings_appearance))
        GroupedCard {
            ThemeMode.values().forEachIndexed { index, mode ->
                SelectionRow(
                    title = themeLabel(mode),
                    selected = settings.themeMode == mode,
                    showDivider = index != ThemeMode.values().lastIndex,
                    onClick = { store.setTheme(mode) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader(stringResource(R.string.settings_language))
        GroupedCard {
            AppLanguage.values().forEachIndexed { index, language ->
                SelectionRow(
                    title = languageLabel(language),
                    selected = settings.language == language,
                    showDivider = index != AppLanguage.values().lastIndex,
                    onClick = { onLanguageSelected(language) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.settings_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    AppLanguage.CHINESE -> stringResource(R.string.language_chinese)
}

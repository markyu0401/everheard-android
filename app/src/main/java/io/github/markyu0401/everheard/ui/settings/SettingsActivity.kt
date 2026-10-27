package io.github.markyu0401.everheard.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.settings.AppLanguage
import io.github.markyu0401.everheard.data.settings.ThemeMode
import io.github.markyu0401.everheard.data.settings.appSettingsStore
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.speaker.VoiceProfile
import io.github.markyu0401.everheard.speaker.VoiceProfileStore
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.components.SelectionRow
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.voice.VoiceEnrollmentActivity
import java.io.File
import kotlinx.coroutines.launch

class SettingsActivity : LocalizedActivity() {

    private var refreshToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardAppTheme {
                SettingsScreen(
                    refreshToken = refreshToken,
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

    override fun onResume() {
        super.onResume()
        refreshToken++
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
    refreshToken: Int,
    onBack: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { context.appSettingsStore() }
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = store.current)
    val transcriptStore = remember(context) {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val voiceStore = remember(context) {
        VoiceProfileStore(File(context.filesDir, "voice_profile.bin"))
    }
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<VoiceProfile?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        profile = voiceStore.reload()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { profile = voiceStore.reload() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

        Spacer(Modifier.height(24.dp))

        SectionHeader(stringResource(R.string.settings_voice))
        GroupedCard {
            SettingsRow(
                icon = Icons.Filled.Face,
                title = stringResource(R.string.settings_voice_title),
                subtitle = profile?.let {
                    stringResource(
                        R.string.settings_voice_enrolled,
                        it.samples / 16_000,
                        it.allEmbeddings().size
                    )
                } ?: stringResource(R.string.settings_voice_not_enrolled),
                showDivider = profile != null,
                onClick = {
                    context.startActivity(
                        Intent(context, VoiceEnrollmentActivity::class.java)
                    )
                }
            )
            if (profile != null) {
                SettingsRow(
                    icon = Icons.Filled.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.settings_voice_delete),
                    showDivider = false,
                    onClick = { showDeleteVoiceDialog = true }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader(stringResource(R.string.settings_data))
        GroupedCard {
            SettingsRow(
                icon = Icons.Filled.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.settings_clear_title),
                subtitle = stringResource(R.string.settings_clear_subtitle),
                showDivider = false,
                onClick = { showClearDialog = true }
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.settings_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            transcriptStore.clear()
                            Toast.makeText(
                                context,
                                R.string.settings_clear_done,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            }
        )
    }

    if (showDeleteVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteVoiceDialog = false },
            title = { Text(stringResource(R.string.settings_voice_delete)) },
            text = { Text(stringResource(R.string.settings_voice_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteVoiceDialog = false
                        scope.launch {
                            voiceStore.clear()
                            profile = null
                            Toast.makeText(
                                context,
                                R.string.settings_voice_delete_done,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.schedule_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVoiceDialog = false }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            }
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

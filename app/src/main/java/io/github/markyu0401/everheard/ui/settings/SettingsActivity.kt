package io.github.markyu0401.everheard.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.BuildConfig
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.provider.ProviderConfig
import io.github.markyu0401.everheard.data.provider.ProviderConfigStore
import io.github.markyu0401.everheard.data.settings.AppLanguage
import io.github.markyu0401.everheard.data.settings.ThemeMode
import io.github.markyu0401.everheard.data.settings.appSettingsStore
import io.github.markyu0401.everheard.data.settings.excludedAppsStore
import io.github.markyu0401.everheard.data.settings.scheduleStore
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.mcp.McpConfig
import io.github.markyu0401.everheard.mcp.McpConfigStore
import io.github.markyu0401.everheard.speaker.VoiceProfile
import io.github.markyu0401.everheard.speaker.VoiceProfileStore
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.components.BrandBar
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.EverheardPill
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.PrivacyWell
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.memory.MemoryActivity
import io.github.markyu0401.everheard.ui.person.PeopleActivity
import io.github.markyu0401.everheard.ui.schedule.ScheduleActivity
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity
import io.github.markyu0401.everheard.ui.tutorial.TutorialActivity
import io.github.markyu0401.everheard.ui.voice.VoiceEnrollmentActivity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
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
    val memoryStore = remember(context) {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val voiceStore = remember(context) {
        VoiceProfileStore(File(context.filesDir, "voice_profile.bin"))
    }
    val providerStore = remember(context) {
        ProviderConfigStore.inDirectory(context.filesDir)
    }
    val mcpStore = remember(context) {
        McpConfigStore.inDirectory(context.filesDir)
    }
    val scheduleStore = remember(context) { context.scheduleStore() }
    val excludedStore = remember(context) { context.excludedAppsStore() }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<VoiceProfile?>(null) }
    var provider by remember { mutableStateOf<ProviderConfig?>(null) }
    var mcp by remember { mutableStateOf<McpConfig?>(null) }
    var rules by remember { mutableStateOf<List<ScheduleRule>>(emptyList()) }
    var excludedCount by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    suspend fun reload() {
        profile = voiceStore.reload()
        provider = providerStore.reload()
        mcp = mcpStore.reload()
        rules = scheduleStore.rules.first()
        excludedCount = excludedStore.load().size
    }

    LaunchedEffect(refreshToken) {
        reload()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reload() }
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
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            BrandBar(
                trailing = {
                    EverheardPill(
                        text = "v${BuildConfig.VERSION_NAME}",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle_v2)
            )
            Spacer(Modifier.height(18.dp))

            SettingsSection(title = stringResource(R.string.settings_experience)) {
                DropdownSettingRow(
                    title = stringResource(R.string.settings_appearance),
                    value = themeLabel(settings.themeMode),
                    expanded = appearanceExpanded,
                    onExpand = { appearanceExpanded = true },
                    onDismiss = { appearanceExpanded = false },
                    options = ThemeMode.entries.map { mode ->
                        DropdownOption(
                            label = themeLabel(mode),
                            selected = settings.themeMode == mode,
                            onClick = {
                                store.setTheme(mode)
                                appearanceExpanded = false
                            }
                        )
                    },
                    showDivider = true
                )
                DropdownSettingRow(
                    title = stringResource(R.string.settings_language),
                    value = languageLabel(settings.language),
                    expanded = languageExpanded,
                    onExpand = { languageExpanded = true },
                    onDismiss = { languageExpanded = false },
                    options = AppLanguage.entries.map { language ->
                        DropdownOption(
                            label = languageLabel(language),
                            selected = settings.language == language,
                            onClick = {
                                languageExpanded = false
                                onLanguageSelected(language)
                            }
                        )
                    },
                    showDivider = false
                )
            }

            Spacer(Modifier.height(18.dp))
            SettingsSection(title = stringResource(R.string.settings_voice_people)) {
                SettingsRow(
                    icon = Icons.Outlined.GraphicEq,
                    title = stringResource(R.string.settings_voice_title),
                    subtitle = voiceSubtitle(profile),
                    showDivider = profile != null,
                    onClick = {
                        context.startActivity(Intent(context, VoiceEnrollmentActivity::class.java))
                    }
                )
                if (profile != null) {
                    SettingsRow(
                        title = stringResource(R.string.settings_voice_delete),
                        titleColor = MaterialTheme.colorScheme.error,
                        showDivider = true,
                        onClick = { showDeleteVoiceDialog = true }
                    )
                }
                SettingsRow(
                    icon = Icons.Outlined.People,
                    title = stringResource(R.string.people_title),
                    subtitle = stringResource(R.string.people_subtitle),
                    showDivider = false,
                    onClick = {
                        context.startActivity(Intent(context, PeopleActivity::class.java))
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            SettingsSection(title = stringResource(R.string.settings_intelligence)) {
                SettingsRow(
                    icon = Icons.Outlined.Hub,
                    title = stringResource(R.string.provider_title),
                    subtitle = provider?.llmModel?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.settings_provider_subtitle),
                    trailingContent = {
                        ProviderPill(configured = provider?.llmConfigured == true)
                    },
                    showDivider = true,
                    onClick = {
                        context.startActivity(Intent(context, ProviderSettingsActivity::class.java))
                    }
                )
                SettingsRow(
                    icon = Icons.Outlined.Terminal,
                    title = stringResource(R.string.mcp_title),
                    subtitle = stringResource(R.string.settings_mcp_subtitle),
                    trailingContent = {
                        McpPill(enabled = mcp?.enabled == true)
                    },
                    showDivider = false,
                    onClick = {
                        context.startActivity(Intent(context, McpSettingsActivity::class.java))
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            SettingsSection(title = stringResource(R.string.settings_capture)) {
                SettingsRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.schedule_open),
                    subtitle = scheduleSummary(rules),
                    showDivider = true,
                    onClick = {
                        context.startActivity(Intent(context, ScheduleActivity::class.java))
                    }
                )
                SettingsRow(
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.settings_excluded_apps),
                    subtitle = if (excludedCount == 0) {
                        stringResource(R.string.settings_excluded_apps_none)
                    } else {
                        stringResource(R.string.settings_excluded_apps_count, excludedCount)
                    },
                    showDivider = false,
                    onClick = {
                        context.startActivity(Intent(context, ExcludedAppsActivity::class.java))
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            SettingsSection(title = stringResource(R.string.settings_help)) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = stringResource(R.string.settings_tutorial_title),
                    subtitle = stringResource(R.string.settings_tutorial_subtitle),
                    showDivider = false,
                    onClick = {
                        context.startActivity(Intent(context, TutorialActivity::class.java))
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.settings_privacy))
            PrivacyWell(
                rows = listOf(
                    stringResource(R.string.privacy_audio) to stringResource(R.string.privacy_audio_value),
                    stringResource(R.string.privacy_transcripts) to stringResource(R.string.privacy_transcripts_value),
                    stringResource(R.string.privacy_backup) to stringResource(R.string.privacy_backup_value)
                )
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.settings_data))
            EverheardCard {
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.settings_clear_title),
                    subtitle = stringResource(R.string.settings_clear_subtitle),
                    titleColor = MaterialTheme.colorScheme.error,
                    showDivider = false,
                    onClick = { showClearDialog = true }
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        BottomBar(
            selected = MainTab.SETTINGS,
            onSelect = { tab ->
                when (tab) {
                    MainTab.TODAY -> context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    )
                    MainTab.LIBRARY -> context.startActivity(
                        Intent(context, TranscriptListActivity::class.java)
                    )
                    MainTab.MEMORY -> context.startActivity(
                        Intent(context, MemoryActivity::class.java)
                    )
                    MainTab.SETTINGS -> Unit
                    else -> Unit
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            transcriptStore.clear()
                            memoryStore.clear()
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
            shape = RoundedCornerShape(24.dp),
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
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionLabel(title)
    EverheardCard {
        content()
    }
}

private data class DropdownOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun DropdownSettingRow(
    title: String,
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    options: List<DropdownOption>,
    showDivider: Boolean
) {
    Box {
        SettingsRow(
            title = title,
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            showDivider = showDivider,
            showChevron = false,
            onClick = onExpand
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(220.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (option.selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = option.onClick
                )
            }
        }
    }
}

@Composable
private fun ProviderPill(configured: Boolean) {
    EverheardPill(
        text = if (configured) {
            stringResource(R.string.settings_connected)
        } else {
            stringResource(R.string.settings_not_configured)
        },
        containerColor = if (configured) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (configured) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun McpPill(enabled: Boolean) {
    EverheardPill(
        text = if (enabled) {
            stringResource(R.string.settings_on)
        } else {
            stringResource(R.string.settings_off)
        },
        containerColor = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun voiceSubtitle(profile: VoiceProfile?): String {
    if (profile == null) return stringResource(R.string.settings_voice_not_enrolled)
    val languages = profile.languages
        .map { code -> if (code == "zh") "中文" else "English" }
        .distinct()
    val seconds = profile.samples / 16_000
    return buildString {
        if (languages.isNotEmpty()) {
            append(languages.joinToString(" · "))
            append(" · ")
        }
        append("${seconds}s")
    }
}

@Composable
private fun scheduleSummary(rules: List<ScheduleRule>): String {
    if (rules.isEmpty()) return stringResource(R.string.schedule_all_day)
    val first = rules.first()
    val time = String.format(
        Locale.US,
        "%02d:%02d–%02d:%02d",
        first.startMinute / 60,
        first.startMinute % 60,
        first.endMinute / 60,
        first.endMinute % 60
    )
    return if (rules.size == 1) {
        time
    } else {
        "$time · ${stringResource(R.string.settings_rules_count, rules.size)}"
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

package io.github.markyu0401.everheard.ui.settings

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.backup.BackupListResult
import io.github.markyu0401.everheard.data.backup.BackupManager
import io.github.markyu0401.everheard.data.backup.BackupResult
import io.github.markyu0401.everheard.data.backup.ConnectionResult
import io.github.markyu0401.everheard.data.backup.NasBackupConfig
import io.github.markyu0401.everheard.data.backup.NasConfigStore
import io.github.markyu0401.everheard.data.backup.RestoreResult
import io.github.markyu0401.everheard.data.backup.WebDavEntry
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.settings.excludedAppsStore
import io.github.markyu0401.everheard.data.settings.scheduleStore
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.EverheardPill
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.components.TextAction
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NasBackupActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                NasBackupScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun NasBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val scope = rememberCoroutineScope()
    val configStore = remember { NasConfigStore.inDirectory(context.filesDir) }
    val manager = remember {
        BackupManager(
            configStore = configStore,
            transcriptStore = EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin")),
            memoryStore = EncryptedMemoryStore(File(context.filesDir, "memory.bin")),
            loadSchedule = { context.scheduleStore().rules.first() },
            saveSchedule = { context.scheduleStore().save(it) },
            loadExcluded = { context.excludedAppsStore().load() },
            saveExcluded = { context.excludedAppsStore().save(it) }
        )
    }

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(NasBackupConfig.DEFAULT_FOLDER) }
    var passphrase by remember { mutableStateOf("") }
    var trustSelfSigned by remember { mutableStateOf(true) }
    var lastBackupAt by remember { mutableStateOf(0L) }
    var lastBackupName by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    var listLoaded by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<WebDavEntry?>(null) }

    val savedMessage = stringResource(R.string.nas_saved)
    val testOkMessage = stringResource(R.string.nas_test_ok)
    val backupDoneFormat = stringResource(R.string.nas_backup_done)
    val restoreDoneFormat = stringResource(R.string.nas_restore_done)
    val restoreFailedFormat = stringResource(R.string.nas_error_restore)
    val noBackupsMessage = stringResource(R.string.nas_no_backups)
    val restoreLabel = stringResource(R.string.nas_restore)
    val refreshLabel = stringResource(R.string.nas_refresh)
    val testLabel = stringResource(R.string.nas_test)
    val saveLabel = stringResource(R.string.nas_save)
    val backupLabel = stringResource(R.string.nas_backup)

    LaunchedEffect(Unit) {
        val loaded = configStore.load()
        serverUrl = loaded.serverUrl
        username = loaded.username
        password = loaded.password
        folder = loaded.folder
        passphrase = loaded.passphrase
        trustSelfSigned = loaded.trustSelfSigned
        lastBackupAt = loaded.lastBackupAt
        lastBackupName = loaded.lastBackupName
    }

    fun currentConfig(): NasBackupConfig = NasBackupConfig(
        serverUrl = serverUrl,
        username = username,
        password = password,
        folder = folder,
        passphrase = passphrase,
        trustSelfSigned = trustSelfSigned,
        lastBackupAt = lastBackupAt,
        lastBackupName = lastBackupName
    )

    suspend fun saveCurrent(): NasBackupConfig {
        val normalized = currentConfig().normalized()
        configStore.save(normalized)
        serverUrl = normalized.serverUrl
        folder = normalized.folder
        return normalized
    }

    fun formatDate(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(millis))

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.nas_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.nas_title),
                subtitle = stringResource(R.string.nas_subtitle)
            )
            Spacer(Modifier.height(18.dp))

            SectionLabel(stringResource(R.string.nas_connection))
            EverheardCard {
                BackupField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = stringResource(R.string.nas_server_url),
                    placeholder = stringResource(R.string.nas_server_hint)
                )
                BackupField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.nas_username)
                )
                BackupField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.nas_password),
                    secret = true
                )
                BackupField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = stringResource(R.string.nas_folder),
                    placeholder = stringResource(R.string.nas_folder_hint)
                )
                TrustSelfSignedRow(
                    checked = trustSelfSigned,
                    onCheckedChange = { trustSelfSigned = it }
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.nas_encryption))
            EverheardCard {
                BackupField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.nas_passphrase),
                    secret = true,
                    showDivider = false
                )
                Text(
                    text = stringResource(R.string.nas_passphrase_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = saveLabel,
                enabled = !busy,
                onClick = {
                    if (busy) return@PrimaryButton
                    busy = true
                    status = null
                    scope.launch {
                        saveCurrent()
                        busy = false
                        status = savedMessage
                        statusIsError = false
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            TextAction(
                text = testLabel,
                showArrow = false,
                onClick = {
                    if (busy) return@TextAction
                    busy = true
                    status = null
                    scope.launch {
                        saveCurrent()
                        val result = withContext(Dispatchers.IO) { manager.testConnection() }
                        busy = false
                        when (result) {
                            is ConnectionResult.Success -> {
                                status = testOkMessage
                                statusIsError = false
                            }
                            is ConnectionResult.Failure -> {
                                status = result.message
                                statusIsError = true
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.nas_backup_section))
            PrimaryButton(
                text = backupLabel,
                enabled = !busy,
                onClick = {
                    if (busy) return@PrimaryButton
                    busy = true
                    status = null
                    scope.launch {
                        saveCurrent()
                        val result = withContext(Dispatchers.IO) { manager.backupNow() }
                        busy = false
                        when (result) {
                            is BackupResult.Success -> {
                                lastBackupAt = System.currentTimeMillis()
                                lastBackupName = result.fileName
                                status = String.format(
                                    locale,
                                    backupDoneFormat,
                                    result.fileName,
                                    result.sessions,
                                    result.memories
                                )
                                statusIsError = false
                            }
                            is BackupResult.Failure -> {
                                status = result.message
                                statusIsError = true
                            }
                        }
                    }
                }
            )
            if (lastBackupAt > 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.settings_nas_backup_last,
                        formatDate(lastBackupAt)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.nas_restore))
            TextAction(
                text = refreshLabel,
                showArrow = false,
                onClick = {
                    if (busy) return@TextAction
                    busy = true
                    status = null
                    scope.launch {
                        saveCurrent()
                        val result = withContext(Dispatchers.IO) { manager.listBackups() }
                        busy = false
                        listLoaded = true
                        when (result) {
                            is BackupListResult.Success -> {
                                backups = result.entries
                                statusIsError = false
                            }
                            is BackupListResult.Failure -> {
                                backups = emptyList()
                                status = result.message
                                statusIsError = true
                            }
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            if (backups.isNotEmpty()) {
                EverheardCard {
                    backups.forEachIndexed { index, entry ->
                        SettingsRow(
                            title = entry.name,
                            subtitle = stringResource(
                                R.string.nas_file_meta,
                                formatDate(entry.lastModified),
                                formatSize(entry.size)
                            ),
                            showDivider = index < backups.lastIndex,
                            showChevron = false,
                            trailingContent = {
                                TextAction(
                                    text = restoreLabel,
                                    showArrow = false,
                                    onClick = { restoreTarget = entry }
                                )
                            }
                        )
                    }
                }
            } else if (listLoaded) {
                Text(
                    text = noBackupsMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            status?.let { message ->
                Spacer(Modifier.height(14.dp))
                EverheardPill(
                    text = message,
                    containerColor = if (statusIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (statusIsError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    restoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.nas_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.nas_restore_confirm_body,
                        target.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreTarget = null
                        busy = true
                        status = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { manager.restore(target) }
                            busy = false
                            when (result) {
                                is RestoreResult.Success -> {
                                    status = String.format(
                                        locale,
                                        restoreDoneFormat,
                                        result.sessions,
                                        result.memories
                                    )
                                    statusIsError = false
                                }
                                is RestoreResult.Failure -> {
                                    status = String.format(
                                        locale,
                                        restoreFailedFormat,
                                        result.message
                                    )
                                    statusIsError = true
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.nas_restore),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            }
        )
    }
}

@Composable
private fun BackupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    secret: Boolean = false,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = if (placeholder != null) {
                { Text(text = placeholder) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 14.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun TrustSelfSignedRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.nas_trust_self_signed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.nas_trust_self_signed_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

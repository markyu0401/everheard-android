package io.github.markyu0401.everheard.ui.transcript

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.CommitmentRow
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.InspirationRow
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.MemoryEventRow
import io.github.markyu0401.everheard.ui.components.MemorySummaryCard
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class SessionDetailActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        setContent {
            EverheardAppTheme {
                SessionDetailScreen(
                    sessionId = sessionId,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun SessionDetailScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val store = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val analyzer = remember { RuleBasedMemoryAnalyzer() }
    val scope = rememberCoroutineScope()
    val markColor = LocalSemanticColors.current.commitment
    var session by remember { mutableStateOf<TranscriptSession?>(null) }
    var memory by remember { mutableStateOf<SessionMemory?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun analyzeCurrent() {
        val target = session ?: return
        if (target.utterances.isEmpty() || analyzing) return
        scope.launch {
            analyzing = true
            val generated = analyzer.analyze(target)
            memoryStore.save(generated)
            memory = generated
            analyzing = false
        }
    }

    LaunchedEffect(sessionId) {
        val loadedSession = store.session(sessionId)
        session = loadedSession
        memory = memoryStore.get(sessionId)
        loaded = true
        if (memory == null && loadedSession?.utterances?.isNotEmpty() == true) {
            analyzing = true
            val generated = analyzer.analyze(loadedSession)
            memoryStore.save(generated)
            memory = generated
            analyzing = false
        }
    }

    val current = session
    val markedCount = current?.utterances?.count { it.marked } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { current?.let { shareSession(context, it, locale) } },
                enabled = current != null
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.transcript_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { showDeleteDialog = true },
                enabled = current != null
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.transcript_delete_title),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (!loaded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            return@Column
        }
        if (current == null) {
            Text(
                text = stringResource(R.string.transcript_detail_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        LargeTitle(
            title = current.title.ifBlank {
                stringResource(R.string.transcript_session_fallback)
            },
            subtitle = buildString {
                append(formatSessionDate(current.startedAt, locale))
                if (markedCount > 0) {
                    append(" · ")
                    append(stringResource(R.string.transcript_marked_count, markedCount))
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        if (current.utterances.isEmpty()) {
            Text(
                text = stringResource(R.string.transcript_detail_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PrimaryButton(
                        text = when {
                            analyzing -> stringResource(R.string.memory_analyzing)
                            memory == null -> stringResource(R.string.memory_analyze)
                            else -> stringResource(R.string.memory_refresh)
                        },
                        enabled = !analyzing,
                        onClick = { analyzeCurrent() }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.memory_local_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                val currentMemory = memory
                if (currentMemory != null) {
                    if (currentMemory.summary.isNotBlank()) {
                        item { MemorySummaryCard(currentMemory) }
                    }
                    item {
                        SectionHeader(stringResource(R.string.memory_timeline))
                        GroupedCard {
                            if (currentMemory.events.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.memory_no_events),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                currentMemory.events.forEachIndexed { index, event ->
                                    MemoryEventRow(
                                        event = event,
                                        showDivider = index != currentMemory.events.lastIndex
                                    )
                                }
                            }
                        }
                    }
                    if (currentMemory.commitments.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.memory_commitments))
                            GroupedCard {
                                currentMemory.commitments.forEachIndexed { index, commitment ->
                                    CommitmentRow(
                                        commitment = commitment,
                                        showDivider = index != currentMemory.commitments.lastIndex
                                    )
                                }
                            }
                        }
                    }
                    if (currentMemory.inspirations.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.memory_inspirations))
                            GroupedCard {
                                currentMemory.inspirations.forEachIndexed { index, inspiration ->
                                    InspirationRow(
                                        inspiration = inspiration,
                                        showDivider = index != currentMemory.inspirations.lastIndex
                                    )
                                }
                            }
                        }
                    }
                } else if (!analyzing) {
                    item {
                        Text(
                            text = stringResource(R.string.memory_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                item {
                    SectionHeader(stringResource(R.string.transcript_title))
                    GroupedCard {
                        current.utterances.forEachIndexed { index, utterance ->
                            UtteranceRow(
                                utterance = utterance,
                                markColor = markColor,
                                onToggleMark = {
                                    scope.launch {
                                        store.setMarked(
                                            sessionId = sessionId,
                                            utteranceId = utterance.id,
                                            marked = !utterance.marked
                                        )
                                        session = store.session(sessionId)
                                    }
                                }
                            )
                            if (index != current.utterances.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.transcript_delete_title)) },
            text = { Text(stringResource(R.string.transcript_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            memoryStore.delete(sessionId)
                            store.deleteSession(sessionId)
                            Toast.makeText(
                                context,
                                R.string.transcript_delete_done,
                                Toast.LENGTH_SHORT
                            ).show()
                            onBack()
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            }
        )
    }
}

@Composable
private fun UtteranceRow(
    utterance: TranscriptUtterance,
    markColor: Color,
    onToggleMark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (utterance.marked) markColor.copy(alpha = 0.10f) else Color.Transparent
            )
            .clickable(onClick = onToggleMark)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val speakerText = when (utterance.speaker) {
                "me" -> stringResource(R.string.speaker_me)
                null -> null
                else -> utterance.speaker
            }
            if (speakerText != null) {
                Text(
                    text = speakerText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(utterance.startMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(
                    if (utterance.marked) R.string.transcript_unmark else R.string.transcript_mark
                ),
                tint = if (utterance.marked) {
                    markColor
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = utterance.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareSession(context: android.content.Context, session: TranscriptSession, locale: Locale) {
    val body = buildString {
        appendLine(session.title.ifBlank { context.getString(R.string.transcript_session_fallback) })
        appendLine(formatSessionDate(session.startedAt, locale))
        session.utterances.forEach { utterance ->
            append(formatTime(utterance.startMs))
            append("  ")
            if (utterance.marked) append("★ ")
            appendLine(utterance.text)
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, session.title)
        putExtra(Intent.EXTRA_TEXT, body.trim())
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.transcript_share)))
}

private fun formatSessionDate(timestamp: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        locale
    ).format(Date(timestamp))

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

package io.github.markyu0401.everheard.ui.project

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.project.ProjectBuilder
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.CommitmentRow
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.MetricStrip
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.components.TimelineMoment
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ProjectDetailActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        setContent {
            EverheardAppTheme {
                ProjectDetailScreen(
                    projectId = projectId,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ProjectDetailScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val transcriptStore = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val projectBuilder = remember { ProjectBuilder() }
    var sessions by remember { mutableStateOf<List<TranscriptSession>>(emptyList()) }
    var project by remember { mutableStateOf<Project?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        sessions = transcriptStore.reload()
        val memories = memoryStore.reload()
        project = projectBuilder.build(sessions, memories).firstOrNull { it.id == projectId }
        loaded = true
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
                text = stringResource(R.string.projects_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        val current = project
        if (!loaded) {
            Text(
                text = stringResource(R.string.memory_analyzing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }
        if (current == null) {
            Text(
                text = stringResource(R.string.project_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            item {
                ScreenHeader(
                    title = current.name,
                    subtitle = current.keywords.joinToString(" · ")
                )
                Spacer(Modifier.height(14.dp))
                MetricStrip(
                    metrics = listOf(
                        current.sessionCount.toString() to stringResource(R.string.memory_stat_sessions),
                        current.openCommitments.size.toString() to stringResource(R.string.memory_stat_open),
                        current.events.size.toString() to stringResource(R.string.memory_observations_count)
                    )
                )
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel(
                    text = stringResource(R.string.project_timeline),
                    trailing = current.events.size.toString()
                )
                if (current.events.isEmpty()) {
                    Text(
                        text = stringResource(R.string.memory_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    current.events.forEach { projectEvent ->
                        val session = sessions.firstOrNull { it.id == projectEvent.sessionId }
                        TimelineMoment(
                            time = formatClock(
                                (session?.startedAt ?: 0L) + projectEvent.event.evidence.startMs
                            ),
                            title = projectEvent.event.text,
                            typeLabel = memoryTypeLabel(projectEvent.event.type),
                            typeColor = memoryTypeColor(projectEvent.event.type),
                            meta = projectEvent.sessionTitle
                        )
                    }
                }
            }

            if (current.openCommitments.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(
                        text = stringResource(R.string.project_commitments),
                        trailing = current.openCommitments.size.toString()
                    )
                    EverheardCard {
                        current.openCommitments.forEachIndexed { index, commitment ->
                            CommitmentRow(
                                commitment = commitment,
                                showDivider = index != current.openCommitments.lastIndex
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel(
                    text = stringResource(R.string.project_sessions),
                    trailing = current.sessionCount.toString()
                )
                EverheardCard {
                    val projectSessions = sessions.filter { it.id in current.sessionIds }
                    projectSessions.forEachIndexed { index, session ->
                        SettingsRow(
                            icon = Icons.Filled.DateRange,
                            title = session.title.ifBlank {
                                stringResource(R.string.transcript_session_fallback)
                            },
                            subtitle = formatProjectDate(session.startedAt),
                            showDivider = index != projectSessions.lastIndex,
                            onClick = {
                                context.startActivity(
                                    Intent(context, SessionDetailActivity::class.java)
                                        .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.id)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun memoryTypeLabel(type: MemoryEventType): String = stringResource(
    when (type) {
        MemoryEventType.PROGRESS -> R.string.memory_event_progress
        MemoryEventType.DECISION -> R.string.memory_event_decision
        MemoryEventType.COMMITMENT -> R.string.memory_event_commitment
        MemoryEventType.RISK -> R.string.memory_event_risk
        MemoryEventType.INSPIRATION -> R.string.memory_event_inspiration
    }
)

@Composable
private fun memoryTypeColor(type: MemoryEventType): Color {
    val semantic = LocalSemanticColors.current
    return when (type) {
        MemoryEventType.PROGRESS -> MaterialTheme.colorScheme.primary
        MemoryEventType.DECISION -> semantic.decision
        MemoryEventType.COMMITMENT -> semantic.commitment
        MemoryEventType.RISK -> semantic.risk
        MemoryEventType.INSPIRATION -> semantic.insight
    }
}

private fun formatClock(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

private fun formatProjectDate(timestamp: Long): String =
    DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

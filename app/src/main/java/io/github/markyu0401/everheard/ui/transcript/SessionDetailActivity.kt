package io.github.markyu0401.everheard.ui.transcript

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.calendar.CommitmentCalendar
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.Inspiration
import io.github.markyu0401.everheard.data.memory.MemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.provider.LlmMemoryAnalyzer
import io.github.markyu0401.everheard.data.provider.ProviderConfigStore
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.EverheardPill
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.TextAction
import io.github.markyu0401.everheard.ui.components.TimelineMoment
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.EverheardSerif
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    val store = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val providerStore = remember {
        ProviderConfigStore.inDirectory(context.filesDir)
    }
    var analyzer by remember { mutableStateOf<MemoryAnalyzer>(RuleBasedMemoryAnalyzer()) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<TranscriptSession?>(null) }
    var memory by remember { mutableStateOf<SessionMemory?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    fun analyzeCurrent() {
        val target = session ?: return
        if (target.utterances.isEmpty() || analyzing) return
        scope.launch {
            analyzing = true
            try {
                val generated = withContext(Dispatchers.IO) { analyzer.analyze(target) }
                memoryStore.save(generated)
                memory = generated
            } finally {
                analyzing = false
            }
        }
    }

    LaunchedEffect(sessionId) {
        val loadedSession = store.session(sessionId)
        session = loadedSession
        memory = memoryStore.get(sessionId)
        val providerConfig = providerStore.load()
        analyzer = if (providerConfig.llmConfigured) {
            LlmMemoryAnalyzer(providerConfig)
        } else {
            RuleBasedMemoryAnalyzer()
        }
        loaded = true
        if (memory == null && loadedSession?.utterances?.isNotEmpty() == true) {
            analyzing = true
            try {
                val generated = withContext(Dispatchers.IO) { analyzer.analyze(loadedSession) }
                memoryStore.save(generated)
                memory = generated
            } finally {
                analyzing = false
            }
        }
    }

    val current = session
    val semantic = LocalSemanticColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp)
        ) {
            BackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                text = stringResource(R.string.session_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { showShareMenu = true },
                        enabled = current != null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.transcript_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transcript_share_text)) },
                            onClick = {
                                showShareMenu = false
                                current?.let { shareSession(context, it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transcript_share_markdown)) },
                            onClick = {
                                showShareMenu = false
                                current?.let { shareSessionMarkdown(context, it, memory) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transcript_share_json)) },
                            onClick = {
                                showShareMenu = false
                                current?.let { shareSessionJson(context, it, memory) }
                            }
                        )
                    }
                }
                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = current != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.transcript_delete_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        if (!loaded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
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
                Spacer(Modifier.height(6.dp))
                val displayTitle = current.title.ifBlank {
                    stringResource(R.string.transcript_session_fallback)
                }
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = io.github.markyu0401.everheard.ui.theme.displayFontFor(
                            displayTitle
                        )
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = sessionMeta(context, current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val speakers = speakerLabels(current)
                if (speakers.isNotEmpty()) {
                    Spacer(Modifier.height(11.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        speakers.take(4).forEachIndexed { index, speaker ->
                            EverheardPill(
                                text = speaker,
                                containerColor = if (index == 0) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (index == 0) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SummarySection(
                    memory = memory,
                    analyzing = analyzing,
                    onRefresh = { analyzeCurrent() }
                )
            }

            if (memory != null && memory!!.events.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(
                        text = stringResource(R.string.session_timeline),
                        trailing = memory!!.events.size.toString()
                    )
                    memory!!.events.forEach { event ->
                        TimelineMoment(
                            time = formatClock(current.startedAt + event.evidence.startMs),
                            title = event.text,
                            body = null,
                            typeLabel = memoryTypeLabel(event.type),
                            typeColor = memoryTypeColor(event.type, semantic),
                            meta = event.evidence.speaker?.let { speakerLabel(context, it) }
                        )
                    }
                }
            }

            if (memory != null && memory!!.commitments.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(
                        text = stringResource(R.string.session_open_loops),
                        trailing = memory!!.commitments.size.toString()
                    )
                    EverheardCard {
                        memory!!.commitments.forEachIndexed { index, commitment ->
                            CommitmentDetailRow(
                                commitment = commitment,
                                showDivider = index != memory!!.commitments.lastIndex,
                                onAddToCalendar = {
                                    val intent = CommitmentCalendar.intent(
                                        context,
                                        commitment,
                                        current.startedAt
                                    )
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.memory_no_due_date,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (memory != null && memory!!.inspirations.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(
                        text = stringResource(R.string.session_ideas),
                        trailing = memory!!.inspirations.size.toString()
                    )
                    EverheardCard {
                        memory!!.inspirations.forEachIndexed { index, inspiration ->
                            InspirationDetailRow(
                                inspiration = inspiration,
                                showDivider = index != memory!!.inspirations.lastIndex
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(22.dp))
                SectionLabel(
                    text = stringResource(R.string.session_transcript),
                    trailing = stringResource(R.string.session_lines, current.utterances.size)
                )
            }
            if (current.utterances.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.transcript_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            } else {
                items(current.utterances, key = { it.id }) { utterance ->
                    UtteranceRow(
                        utterance = utterance,
                        markColor = semantic.commitment,
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
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(24.dp),
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
private fun SummarySection(
    memory: SessionMemory?,
    analyzing: Boolean,
    onRefresh: () -> Unit
) {
    EverheardCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.session_summary),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextAction(
                    text = when {
                        analyzing -> stringResource(R.string.memory_analyzing)
                        memory == null -> stringResource(R.string.memory_analyze)
                        else -> stringResource(R.string.session_refresh)
                    },
                    onClick = onRefresh,
                    showArrow = false
                )
            }
            if (memory?.summary?.isNotBlank() == true) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = memory.summary,
                    style = TextStyle(
                        fontFamily = EverheardSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                memory.summaryEvidence?.let { evidence ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "\u201C${evidence.quote}\u201D  ${formatTime(evidence.startMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (analyzing) {
                        stringResource(R.string.memory_analyzing)
                    } else {
                        stringResource(R.string.memory_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommitmentDetailRow(
    commitment: Commitment,
    showDivider: Boolean,
    onAddToCalendar: () -> Unit
) {
    val context = LocalContext.current
    Column {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(17.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
                Text(
                    text = commitment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                commitment.dueHint?.let { due ->
                    Text(
                        text = due,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = LocalSemanticColors.current.commitment
                    )
                }
            }
            val ownerLabel = if (commitment.owner != null) {
                speakerLabel(context, commitment.owner)
            } else {
                null
            }
            val dueLabel = if (commitment.dueHint != null) {
                stringResource(R.string.memory_due, commitment.dueHint)
            } else {
                null
            }
            val meta = buildString {
                if (ownerLabel != null) append(ownerLabel)
                if (dueLabel != null) {
                    if (isNotEmpty()) append(" · ")
                    append(dueLabel)
                }
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 27.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            TextAction(
                text = stringResource(R.string.memory_add_calendar),
                onClick = onAddToCalendar,
                showArrow = false,
                modifier = Modifier.padding(start = 27.dp)
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
}

@Composable
private fun InspirationDetailRow(
    inspiration: Inspiration,
    showDivider: Boolean
) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = inspiration.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(inspiration.evidence.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 14.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun UtteranceRow(
    utterance: TranscriptUtterance,
    markColor: Color,
    onToggleMark: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (utterance.marked) markColor.copy(alpha = 0.10f) else Color.Transparent
            )
            .clickable(onClick = onToggleMark)
            .padding(vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val speakerText = utterance.speaker?.let { speakerLabel(context, it) }
            if (speakerText != null) {
                Text(
                    text = speakerText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (utterance.speaker == "me") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalSemanticColors.current.commitment
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(utterance.startMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(7.dp))
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
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = utterance.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 9.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun sessionMeta(context: Context, session: TranscriptSession): String {
    val date = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val time = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    val end = session.endedAt
        ?: session.utterances.lastOrNull()?.let { session.startedAt + it.endMs }
        ?: session.startedAt
    val minutes = ((end - session.startedAt) / 60_000L).coerceAtLeast(0L).toInt()
    val locale = LocalConfiguration.current.locales[0]
    val datePattern = if (locale.language == "zh") "M月d日 · EEE" else "EEE, d MMM"
    val dateLabel = date.format(DateTimeFormatter.ofPattern(datePattern, locale))
    val durationLabel = if (minutes > 0) {
        context.getString(R.string.duration_m, minutes)
    } else {
        context.getString(R.string.duration_under_minute)
    }
    return "$dateLabel · $time · $durationLabel"
}

@Composable
private fun speakerLabels(session: TranscriptSession): List<String> {
    val context = LocalContext.current
    val result = mutableListOf<String>()
    for (speaker in session.utterances.mapNotNull { it.speaker }.distinct()) {
        result += speakerLabel(context, speaker)
    }
    return result
}

private fun speakerLabel(context: Context, speaker: String): String =
    if (speaker == "me") context.getString(R.string.speaker_me) else speaker

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
private fun memoryTypeColor(type: MemoryEventType, semantic: io.github.markyu0401.everheard.ui.theme.SemanticColors): Color =
    when (type) {
        MemoryEventType.PROGRESS -> MaterialTheme.colorScheme.primary
        MemoryEventType.DECISION -> semantic.decision
        MemoryEventType.COMMITMENT -> semantic.commitment
        MemoryEventType.RISK -> semantic.risk
        MemoryEventType.INSPIRATION -> semantic.insight
    }

private fun formatClock(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1_000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun shareSession(context: android.content.Context, session: TranscriptSession) {
    val body = buildString {
        appendLine(session.title.ifBlank { context.getString(R.string.transcript_session_fallback) })
        appendLine(formatSessionDate(session.startedAt))
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

private fun shareSessionMarkdown(
    context: android.content.Context,
    session: TranscriptSession,
    memory: SessionMemory?
) {
    val body = buildString {
        appendLine("# ${session.title.ifBlank { context.getString(R.string.transcript_session_fallback) }}")
        appendLine()
        appendLine(formatSessionDate(session.startedAt))
        if (memory?.summary?.isNotBlank() == true) {
            appendLine()
            appendLine("## Summary")
            appendLine(memory.summary)
        }
        if (!memory?.events.isNullOrEmpty()) {
            appendLine()
            appendLine("## Timeline")
            memory!!.events.forEach { event ->
                appendLine("- ${event.type.name.lowercase()}: ${event.text}")
                appendLine("  > ${event.evidence.quote} (${formatTime(event.evidence.startMs)})")
            }
        }
        if (!memory?.commitments.isNullOrEmpty()) {
            appendLine()
            appendLine("## Commitments")
            memory!!.commitments.forEach { commitment ->
                appendLine("- ${commitment.text} (${commitment.status})")
            }
        }
        if (!memory?.inspirations.isNullOrEmpty()) {
            appendLine()
            appendLine("## Ideas")
            memory!!.inspirations.forEach { inspiration ->
                appendLine("- ${inspiration.text}")
            }
        }
        appendLine()
        appendLine("## Transcript")
        session.utterances.forEach { utterance ->
            append(formatTime(utterance.startMs))
            append("  ")
            if (utterance.marked) append("★ ")
            appendLine(utterance.text)
        }
    }
    shareText(context, session.title, body.trim(), "text/markdown")
}

private fun shareSessionJson(
    context: android.content.Context,
    session: TranscriptSession,
    memory: SessionMemory?
) {
    val root = JSONObject().apply {
        put("id", session.id)
        put("title", session.title)
        put("startedAt", session.startedAt)
        session.endedAt?.let { put("endedAt", it) }
        put("utterances", JSONArray().apply {
            session.utterances.forEach { utterance ->
                put(JSONObject().apply {
                    put("id", utterance.id)
                    put("startMs", utterance.startMs)
                    put("endMs", utterance.endMs)
                    put("text", utterance.text)
                    utterance.speaker?.let { put("speaker", it) }
                    put("marked", utterance.marked)
                })
            }
        })
        memory?.let { memoryValue ->
            put("memory", JSONObject().apply {
                put("summary", memoryValue.summary)
                put("generatedAt", memoryValue.generatedAt)
                put("analyzer", memoryValue.analyzer)
                put("events", JSONArray().apply {
                    memoryValue.events.forEach { event ->
                        put(JSONObject().apply {
                            put("type", event.type.name.lowercase())
                            put("text", event.text)
                            put("quote", event.evidence.quote)
                            put("startMs", event.evidence.startMs)
                            put("utteranceId", event.evidence.utteranceId)
                        })
                    }
                })
                put("commitments", JSONArray().apply {
                    memoryValue.commitments.forEach { commitment ->
                        put(JSONObject().apply {
                            put("text", commitment.text)
                            put("owner", commitment.owner)
                            put("dueHint", commitment.dueHint)
                            put("status", commitment.status)
                            put("quote", commitment.evidence.quote)
                            put("utteranceId", commitment.evidence.utteranceId)
                        })
                    }
                })
                put("inspirations", JSONArray().apply {
                    memoryValue.inspirations.forEach { inspiration ->
                        put(JSONObject().apply {
                            put("text", inspiration.text)
                            put("quote", inspiration.evidence.quote)
                            put("utteranceId", inspiration.evidence.utteranceId)
                        })
                    }
                })
            })
        }
    }
    shareText(context, session.title, root.toString(2), "application/json")
}

private fun shareText(
    context: android.content.Context,
    subject: String,
    body: String,
    mimeType: String
) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.transcript_share))
    )
}

private fun formatSessionDate(timestamp: Long): String =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

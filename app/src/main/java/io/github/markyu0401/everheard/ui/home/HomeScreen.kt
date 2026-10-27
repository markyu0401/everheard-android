package io.github.markyu0401.everheard.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.CaptureController
import io.github.markyu0401.everheard.capture.CaptureState
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.components.ArcMark
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.components.BrandBar
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.EverheardPill
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.PrivacyNote
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.StatusDot
import io.github.markyu0401.everheard.ui.components.TextAction
import io.github.markyu0401.everheard.ui.components.TimelineMoment
import io.github.markyu0401.everheard.ui.memory.MemoryActivity
import io.github.markyu0401.everheard.ui.settings.SettingsActivity
import io.github.markyu0401.everheard.ui.theme.EverheardSans
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Today answers one question: what did I hear today, and what is still open?
 * The capture hero is the biggest block; the thread is the signature spine.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val serviceState by CaptureController.state.collectAsStateWithLifecycle()
    val store = remember(context) {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember(context) {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val analyzer = remember { RuleBasedMemoryAnalyzer() }
    var sessions by remember { mutableStateOf<List<TranscriptSession>>(emptyList()) }
    var memories by remember { mutableStateOf<List<SessionMemory>>(emptyList()) }

    LaunchedEffect(Unit) {
        sessions = store.reload()
        memories = memoryStore.reload()
        analyzeMissingToday(sessions, memories, memoryStore, analyzer)
        memories = memoryStore.reload()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    sessions = store.reload()
                    memories = memoryStore.reload()
                    analyzeMissingToday(sessions, memories, memoryStore, analyzer)
                    memories = memoryStore.reload()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todaySessions = sessions.filter { isToday(it.startedAt) }
    val todayIds = todaySessions.map { it.id }.toSet()
    val todayMemories = memories.filter { it.sessionId in todayIds }
    val sessionsById = todaySessions.associateBy { it.id }
    val totalMinutes = todaySessions.sumOf { sessionMinutes(it) }
    val openCommitments = todayMemories
        .flatMap { it.commitments }
        .filter { it.status == Commitment.STATUS_OPEN }
    val ideaCount = todayMemories.sumOf { it.inspirations.size }
    val threadItems = buildThreadItems(todaySessions, todayMemories, sessionsById, locale)
    val threadTitle = if (threadItems.isNotEmpty() && todaySessions.isNotEmpty()) {
        stringResource(R.string.home_thread)
    } else {
        stringResource(R.string.home_recent)
    }

    Column(
        modifier = modifier
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
                onSearch = {
                    context.startActivity(Intent(context, TranscriptListActivity::class.java))
                }
            )
            ScreenHeader(
                title = stringResource(R.string.home_today),
                sideText = formatHeaderDate(todaySessions.firstOrNull()?.startedAt, locale)
            )
            Spacer(Modifier.height(16.dp))

            CaptureHero(
                state = serviceState,
                totalMinutes = totalMinutes,
                conversations = todaySessions.size,
                openLoops = openCommitments.size,
                ideas = ideaCount,
                onLive = {
                    context.startActivity(Intent(context, io.github.markyu0401.everheard.ui.live.LiveCaptureActivity::class.java))
                }
            )

            if (threadItems.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel(
                    text = threadTitle,
                    trailing = stringResource(R.string.home_thread_meta, threadItems.size)
                )
                threadItems.forEach { item ->
                    TimelineMoment(
                        time = item.time,
                        title = item.title,
                        body = item.body,
                        typeLabel = item.typeLabel,
                        typeColor = item.typeColor,
                        meta = item.meta,
                        onClick = {
                            context.startActivity(
                                Intent(context, SessionDetailActivity::class.java)
                                    .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, item.sessionId)
                            )
                        }
                    )
                }
            } else {
                Spacer(Modifier.height(22.dp))
                SectionLabel(text = threadTitle)
                EverheardCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.home_nothing_yet),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_nothing_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (openCommitments.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel(
                    text = stringResource(R.string.home_still_open),
                    trailing = stringResource(R.string.home_commitments_count, openCommitments.size)
                )
                EverheardCard {
                    openCommitments.take(3).forEachIndexed { index, commitment ->
                        val sessionId = todayMemories
                            .firstOrNull { it.commitments.any { c -> c.id == commitment.id } }
                            ?.sessionId
                        OpenLoopRow(
                            text = commitment.text,
                            due = commitment.dueHint,
                            showDivider = index != openCommitments.take(3).lastIndex,
                            onClick = sessionId?.let { id ->
                                {
                                    context.startActivity(
                                        Intent(context, SessionDetailActivity::class.java)
                                            .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, id)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            PrivacyNote(text = stringResource(R.string.home_footer))
        }

        BottomBar(
            selected = MainTab.TODAY,
            onSelect = { tab ->
                when (tab) {
                    MainTab.LIBRARY -> context.startActivity(
                        Intent(context, TranscriptListActivity::class.java)
                    )
                    MainTab.MEMORY -> context.startActivity(
                        Intent(context, MemoryActivity::class.java)
                    )
                    MainTab.SETTINGS -> context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                    )
                    MainTab.TODAY -> Unit
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun CaptureHero(
    state: CaptureState,
    totalMinutes: Int,
    conversations: Int,
    openLoops: Int,
    ideas: Int,
    onLive: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val recording = state == CaptureState.RECORDING
    val running = state.isServiceAlive
    val statusColor = when {
        recording -> semantic.live
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (state) {
        CaptureState.RECORDING -> stringResource(R.string.home_service_recording)
        CaptureState.ARMED -> stringResource(R.string.home_service_armed)
        CaptureState.STOPPED -> stringResource(R.string.home_service_stopped)
        CaptureState.PAUSED_SCHEDULE,
        CaptureState.PAUSED_BATTERY,
        CaptureState.PAUSED_THERMAL,
        CaptureState.PAUSED_SILENCE,
        CaptureState.PAUSED_USER -> stringResource(R.string.home_service_paused)
        CaptureState.ERROR -> stringResource(R.string.home_service_stopped)
    }

    EverheardCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(color = statusColor, text = statusText)
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_chip_local),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatMinutes(totalMinutes),
                        style = TextStyle(
                            fontFamily = EverheardSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 34.sp,
                            lineHeight = 34.sp,
                            letterSpacing = (-1.2).sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_heard_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                HeroStat(
                    value = conversations.toString(),
                    label = stringResource(R.string.home_metric_conversations),
                    modifier = Modifier.weight(1f)
                )
                HeroDivider()
                HeroStat(
                    value = openLoops.toString(),
                    label = stringResource(R.string.home_metric_open_loops),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                HeroDivider()
                HeroStat(
                    value = ideas.toString(),
                    label = stringResource(R.string.home_metric_ideas),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextAction(
                    text = stringResource(R.string.home_open_live),
                    onClick = onLive
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = TextStyle(
                fontFamily = EverheardSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun OpenLoopRow(
    text: String,
    due: String?,
    showDivider: Boolean,
    onClick: (() -> Unit)?
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickableRow(onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 11.dp),
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
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!due.isNullOrBlank()) {
                Text(
                    text = due,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = LocalSemanticColors.current.commitment
                )
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 14.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

private data class ThreadItem(
    val sessionId: String,
    val time: String,
    val title: String,
    val body: String,
    val typeLabel: String?,
    val typeColor: Color,
    val meta: String
)

@Composable
private fun buildThreadItems(
    todaySessions: List<TranscriptSession>,
    todayMemories: List<SessionMemory>,
    sessionsById: Map<String, TranscriptSession>,
    locale: Locale
): List<ThreadItem> {
    val semantic = LocalSemanticColors.current
    val events = todayMemories.flatMap { memory ->
        memory.events.map { memory.sessionId to it }
    }.take(3)
    if (events.isNotEmpty()) {
        return events.map { (sessionId, event) ->
            val session = sessionsById[sessionId]
            val speakerMeta = if (event.evidence.speaker == null) {
                ""
            } else if (event.evidence.speaker == "me") {
                stringResource(R.string.speaker_me)
            } else {
                event.evidence.speaker
            }
            ThreadItem(
                sessionId = sessionId,
                time = formatClock((session?.startedAt ?: 0L) + event.evidence.startMs),
                title = session?.title?.ifBlank {
                    stringResource(R.string.transcript_session_fallback)
                } ?: stringResource(R.string.transcript_session_fallback),
                body = event.text,
                typeLabel = memoryTypeLabel(event.type),
                typeColor = memoryTypeColor(event.type, semantic),
                meta = speakerMeta
            )
        }
    }
    return todaySessions.take(3).map { session ->
        ThreadItem(
            sessionId = session.id,
            time = formatClock(session.startedAt),
            title = session.title.ifBlank {
                stringResource(R.string.transcript_session_fallback)
            },
            body = session.utterances.firstOrNull()?.text.orEmpty(),
            typeLabel = null,
            typeColor = MaterialTheme.colorScheme.primary,
            meta = formatDurationShort(session)
        )
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
private fun memoryTypeColor(type: MemoryEventType, semantic: io.github.markyu0401.everheard.ui.theme.SemanticColors): Color =
    when (type) {
        MemoryEventType.PROGRESS -> MaterialTheme.colorScheme.primary
        MemoryEventType.DECISION -> semantic.decision
        MemoryEventType.COMMITMENT -> semantic.commitment
        MemoryEventType.RISK -> semantic.risk
        MemoryEventType.INSPIRATION -> semantic.insight
    }

private suspend fun analyzeMissingToday(
    sessions: List<TranscriptSession>,
    memories: List<SessionMemory>,
    memoryStore: EncryptedMemoryStore,
    analyzer: RuleBasedMemoryAnalyzer
) {
    val today = sessions.filter { isToday(it.startedAt) }
    val missing = today.filter { session ->
        session.utterances.isNotEmpty() && memories.none { it.sessionId == session.id }
    }
    missing.take(5).forEach { session ->
        runCatching { memoryStore.save(analyzer.analyze(session)) }
    }
}

private fun isToday(timestamp: Long): Boolean {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return date == LocalDate.now()
}

@Composable
private fun formatHeaderDate(timestamp: Long?, locale: Locale): String {
    val date = if (timestamp != null) {
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    } else {
        LocalDate.now()
    }
    val pattern = if (locale.language == "zh") "M月d日 · EEE" else "EEE, d MMM"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun formatClock(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

@Composable
private fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 -> stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.duration_m, minutes)
}

@Composable
private fun formatDurationShort(session: TranscriptSession): String {
    val end = session.endedAt
        ?: session.utterances.lastOrNull()?.let { session.startedAt + it.endMs }
        ?: session.startedAt
    val seconds = ((end - session.startedAt) / 1_000L).coerceAtLeast(0L).toInt()
    return if (seconds >= 60) {
        stringResource(R.string.duration_m, seconds / 60)
    } else {
        stringResource(R.string.duration_seconds, seconds)
    }
}

private fun sessionMinutes(session: TranscriptSession): Int {
    val end = session.endedAt
        ?: session.utterances.lastOrNull()?.let { session.startedAt + it.endMs }
        ?: session.startedAt
    return ((end - session.startedAt) / 60_000L).coerceAtLeast(0L).toInt()
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    )

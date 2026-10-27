package io.github.markyu0401.everheard.ui.home

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.CaptureController
import io.github.markyu0401.everheard.capture.CaptureState
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.MemoryEventRow
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.components.StatusDot
import io.github.markyu0401.everheard.ui.components.TranscriptSessionRow
import io.github.markyu0401.everheard.ui.live.LiveCaptureActivity
import io.github.markyu0401.everheard.ui.settings.SettingsActivity
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * A calm, grouped home: one capture card, one library list, and no developer
 * instrumentation on the main surface.
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
        val today = sessions.filter { isToday(it.startedAt) }
        val missing = today.filter { session ->
            session.utterances.isNotEmpty() &&
                memories.none { it.sessionId == session.id }
        }
        if (missing.isNotEmpty()) {
            missing.take(5).forEach { session ->
                runCatching {
                    memoryStore.save(analyzer.analyze(session))
                }
            }
            memories = memoryStore.reload()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    sessions = store.reload()
                    memories = memoryStore.reload()
                    val missing = sessions
                        .filter { isToday(it.startedAt) && it.utterances.isNotEmpty() }
                        .filter { session -> memories.none { it.sessionId == session.id } }
                    if (missing.isNotEmpty()) {
                        missing.take(5).forEach { session ->
                            runCatching {
                                memoryStore.save(analyzer.analyze(session))
                            }
                        }
                        memories = memoryStore.reload()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todaySessions = sessions.filter { isToday(it.startedAt) }
    val timelineSessions =
        if (todaySessions.isNotEmpty()) todaySessions.take(5) else sessions.take(3)
    val timelineTitle =
        if (todaySessions.isNotEmpty()) R.string.home_today else R.string.home_recent
    val todayIds = todaySessions.map { it.id }.toSet()
    val highlights = memories
        .filter { it.sessionId in todayIds }
        .flatMap { memory -> memory.events.map { memory.sessionId to it } }
        .take(3)

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
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            LargeTitle(
                title = stringResource(R.string.home_title),
                subtitle = stringResource(R.string.home_body)
            )

            Spacer(Modifier.height(24.dp))

            CaptureCard(
                state = serviceState,
                onStart = { CaptureController.start(context) },
                onStop = { CaptureController.stop(context) }
            )

            if (highlights.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionHeader(stringResource(R.string.memory_highlights))
                GroupedCard {
                    highlights.forEachIndexed { index, (sessionId, event) ->
                        MemoryEventRow(
                            event = event,
                            showDivider = index != highlights.lastIndex,
                            onClick = {
                                context.startActivity(
                                    Intent(context, SessionDetailActivity::class.java)
                                        .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, sessionId)
                                )
                            }
                        )
                    }
                }
            }

            if (timelineSessions.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionHeader(stringResource(timelineTitle))
                GroupedCard {
                    timelineSessions.forEachIndexed { index, session ->
                        val titleText = session.title.ifBlank {
                            stringResource(R.string.transcript_session_fallback)
                        }
                        val preview = session.utterances
                            .drop(1)
                            .firstOrNull()
                            ?.text
                            .orEmpty()
                        val markedCount = session.utterances.count { it.marked }
                        val meta = buildString {
                            append(sessionMeta(session, locale))
                            if (markedCount > 0) {
                                append(" · ")
                                append(
                                    stringResource(
                                        R.string.transcript_marked_count,
                                        markedCount
                                    )
                                )
                            }
                        }
                        TranscriptSessionRow(
                            title = titleText,
                            meta = meta,
                            snippet = preview,
                            showDivider = index != timelineSessions.lastIndex,
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

            Spacer(Modifier.height(28.dp))

            SectionHeader(stringResource(R.string.home_quick_actions))
            GroupedCard {
                SettingsRow(
                    iconPainter = painterResource(R.drawable.ic_stat_everheard),
                    title = stringResource(R.string.live_open),
                    subtitle = stringResource(R.string.home_live_subtitle),
                    showDivider = false,
                    onClick = {
                        context.startActivity(Intent(context, LiveCaptureActivity::class.java))
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.home_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(8.dp))
        }

        BottomBar(
            selected = MainTab.TODAY,
            onSelect = { tab ->
                when (tab) {
                    MainTab.LIBRARY -> context.startActivity(
                        Intent(context, TranscriptListActivity::class.java)
                    )
                    MainTab.SETTINGS -> context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                    )
                    MainTab.TODAY -> Unit
                }
            }
        )
    }
}

private fun isToday(timestamp: Long): Boolean {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return date == LocalDate.now()
}

@Composable
private fun sessionMeta(session: TranscriptSession, locale: Locale): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
        .format(Date(session.startedAt))
    val end = session.endedAt
        ?: session.utterances.lastOrNull()?.let { session.startedAt + it.endMs }
        ?: session.startedAt
    val seconds = ((end - session.startedAt) / 1_000L).coerceAtLeast(0L).toInt()
    val duration = if (seconds >= 60) {
        stringResource(R.string.duration_minutes, seconds / 60, seconds % 60)
    } else {
        stringResource(R.string.duration_seconds, seconds)
    }
    return "$time · $duration"
}

@Composable
private fun CaptureCard(
    state: CaptureState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val running = state.isServiceAlive
    val recording = state == CaptureState.RECORDING
    val statusColor = when {
        recording -> semantic.risk
        running -> semantic.decision
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (state) {
        CaptureState.RECORDING -> stringResource(R.string.home_service_recording)
        CaptureState.ARMED -> stringResource(R.string.home_service_armed)
        CaptureState.STOPPED -> stringResource(R.string.home_service_stopped)
        else -> stringResource(R.string.home_service_running)
    }
    val body = when (state) {
        CaptureState.RECORDING -> stringResource(R.string.home_capture_body_recording)
        CaptureState.ARMED -> stringResource(R.string.home_capture_body_armed)
        CaptureState.PAUSED_SCHEDULE -> stringResource(R.string.home_capture_body_schedule)
        CaptureState.PAUSED_USER -> stringResource(R.string.home_capture_body_user)
        CaptureState.PAUSED_BATTERY -> stringResource(R.string.home_capture_body_battery)
        CaptureState.PAUSED_THERMAL -> stringResource(R.string.home_capture_body_thermal)
        CaptureState.PAUSED_SILENCE -> stringResource(R.string.home_capture_body_silence)
        CaptureState.ERROR -> stringResource(R.string.home_capture_body_error)
        CaptureState.STOPPED -> stringResource(R.string.home_capture_body_stopped)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            StatusDot(color = statusColor, text = statusText)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (recording) {
                        semantic.risk.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stat_everheard),
                            contentDescription = null,
                            tint = if (recording) semantic.risk else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_capture_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (running) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_service_stop),
                        style = MaterialTheme.typography.titleMedium,
                        color = semantic.risk
                    )
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.home_service_start),
                    onClick = onStart
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomePreview() {
    EverheardTheme(darkTheme = false) {
        HomeScreen()
    }
}

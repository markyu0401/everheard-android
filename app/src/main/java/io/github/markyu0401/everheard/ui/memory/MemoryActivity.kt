package io.github.markyu0401.everheard.ui.memory

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.insight.InsightBuilder
import io.github.markyu0401.everheard.data.insight.InsightCitation
import io.github.markyu0401.everheard.data.insight.InsightObservation
import io.github.markyu0401.everheard.data.insight.InsightReport
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.project.ProjectBuilder
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.components.BrandBar
import io.github.markyu0401.everheard.ui.components.EmptyState
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.MetricStrip
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.SegmentedControl
import io.github.markyu0401.everheard.ui.components.WeeklyBars
import io.github.markyu0401.everheard.ui.project.ProjectDetailActivity
import io.github.markyu0401.everheard.ui.settings.SettingsActivity
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.EverheardSans
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class MemoryActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                MemoryScreen()
            }
        }
    }
}

@Composable
private fun MemoryScreen() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val transcriptStore = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val analyzer = remember { RuleBasedMemoryAnalyzer() }
    val projectBuilder = remember { ProjectBuilder() }
    val insightBuilder = remember { InsightBuilder() }

    var selected by remember { mutableIntStateOf(0) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var report by remember { mutableStateOf<InsightReport?>(null) }
    var sessions by remember { mutableStateOf<List<TranscriptSession>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        val loadedSessions = transcriptStore.reload()
        var memories = memoryStore.reload()
        val missing = loadedSessions.filter { session ->
            session.utterances.isNotEmpty() && memories.none { it.sessionId == session.id }
        }
        missing.take(20).forEach { session ->
            runCatching { memoryStore.save(analyzer.analyze(session)) }
        }
        if (missing.isNotEmpty()) memories = memoryStore.reload()
        sessions = loadedSessions
        projects = projectBuilder.build(loadedSessions, memories)
        report = insightBuilder.build(loadedSessions, memories, projects)
        loaded = true
    }

    LaunchedEffect(Unit) { load() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { load() }
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
            BrandBar()
            ScreenHeader(
                title = stringResource(R.string.tab_memory),
                sideText = stringResource(R.string.memory_last_7_days)
            )
            Spacer(Modifier.height(16.dp))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.memory_this_week),
                    stringResource(R.string.memory_projects)
                ),
                selectedIndex = selected,
                onSelect = { selected = it }
            )
            Spacer(Modifier.height(18.dp))

            if (!loaded) {
                Text(
                    text = stringResource(R.string.memory_analyzing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (selected == 0) {
                WeekView(
                    report = report,
                    sessions = sessions,
                    locale = locale,
                    onOpenSession = { sessionId ->
                        context.startActivity(
                            Intent(context, SessionDetailActivity::class.java)
                                .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, sessionId)
                        )
                    },
                    onProjects = { selected = 1 }
                )
            } else {
                ProjectsView(
                    projects = projects,
                    sessions = sessions,
                    locale = locale,
                    onOpen = { projectId ->
                        context.startActivity(
                            Intent(context, ProjectDetailActivity::class.java)
                                .putExtra(ProjectDetailActivity.EXTRA_PROJECT_ID, projectId)
                        )
                    }
                )
            }
        }

        BottomBar(
            selected = MainTab.MEMORY,
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
                    MainTab.SETTINGS -> context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                    )
                    MainTab.MEMORY -> Unit
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun ProjectsView(
    projects: List<Project>,
    sessions: List<TranscriptSession>,
    locale: Locale,
    onOpen: (String) -> Unit
) {
    if (projects.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.projects_title),
            body = stringResource(R.string.projects_empty)
        )
        return
    }
    SectionLabel(
        text = stringResource(R.string.memory_projects),
        trailing = stringResource(R.string.memory_active_projects, projects.size)
    )
    EverheardCard {
        projects.forEachIndexed { index, project ->
            val days = projectDays(project, sessions)
            val meta = buildString {
                append(stringResource(R.string.projects_session_count, project.sessionCount))
                append(" · ")
                append(formatRelativeDate(project.lastAt, locale))
            }
            ProjectRow(
                project = project,
                meta = meta,
                days = days,
                showDivider = index != projects.lastIndex,
                onClick = { onOpen(project.id) }
            )
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    meta: String,
    days: List<Int>,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (project.keywords.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = project.keywords.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                MiniBars(values = days)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (project.openCommitments.isNotEmpty()) {
                        stringResource(R.string.memory_project_open, project.openCommitments.size)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun MiniBars(values: List<Int>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.height(18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        values.forEach { value ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (value <= 0) 0.dp else (18f * value / max).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

@Composable
private fun WeekView(
    report: InsightReport?,
    sessions: List<TranscriptSession>,
    locale: Locale,
    onOpenSession: (String) -> Unit,
    onProjects: () -> Unit
) {
    if (report == null) {
        EmptyState(
            title = stringResource(R.string.insights_title),
            body = stringResource(R.string.insights_empty)
        )
        return
    }
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { day -> minutesOn(day, sessions) }
    val labels = days.map { it.format(DateTimeFormatter.ofPattern("EEE", locale)) }
    val previousWeekStart = today.minusDays(13)
    val previousWeekEnd = today.minusDays(7)
    val previousMinutes = sessions
        .filter {
            val date = dateOf(it.startedAt)
            date in previousWeekStart..previousWeekEnd
        }
        .sumOf { minutesOf(it) }
    val delta = report.minutes - previousMinutes

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatMinutes(report.minutes),
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
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.memory_heard_week),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            if (previousMinutes > 0 && delta != 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (delta > 0) {
                        stringResource(R.string.memory_delta, delta)
                    } else {
                        stringResource(R.string.memory_delta_down, -delta)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (delta > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    EverheardCard {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(R.string.memory_daily),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.memory_range),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            WeeklyBars(values = values, labels = labels)
        }
    }
    Spacer(Modifier.height(12.dp))
    MetricStrip(
        metrics = listOf(
            report.sessionCount.toString() to stringResource(R.string.memory_stat_sessions),
            report.decisionCount.toString() to stringResource(R.string.memory_stat_decisions),
            report.openCommitmentCount.toString() to stringResource(R.string.memory_stat_open),
            report.riskCount.toString() to stringResource(R.string.memory_stat_risks)
        )
    )

    if (report.observations.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        SectionLabel(
            text = stringResource(R.string.memory_observations),
            trailing = stringResource(R.string.memory_observations_count, report.observations.size)
        )
        EverheardCard {
            report.observations.take(3).forEachIndexed { index, observation ->
                ObservationRow(
                    observation = observation,
                    showDivider = index != report.observations.take(3).lastIndex,
                    onOpenSession = onOpenSession
                )
            }
        }
    }

    if (report.projectCount > 0) {
        Spacer(Modifier.height(14.dp))
        EverheardCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProjects)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.memory_active_projects, report.projectCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "\u2192",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ObservationRow(
    observation: InsightObservation,
    showDivider: Boolean,
    onOpenSession: (String) -> Unit
) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = observation.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = observation.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            observation.action?.let { action ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            observation.citations.take(2).forEach { citation ->
                CitationRow(citation = citation, onOpen = { onOpenSession(citation.sessionId) })
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

@Composable
private fun CitationRow(citation: InsightCitation, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "\u201C${citation.quote}\u201D",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatTime(citation.startMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 -> stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.duration_m, minutes)
}

private fun dateOf(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

private fun minutesOf(session: TranscriptSession): Int {
    val end = session.endedAt
        ?: session.utterances.lastOrNull()?.let { session.startedAt + it.endMs }
        ?: session.startedAt
    return ((end - session.startedAt) / 60_000L).coerceAtLeast(0L).toInt()
}

private fun minutesOn(day: LocalDate, sessions: List<TranscriptSession>): Int =
    sessions.filter { dateOf(it.startedAt) == day }.sumOf { minutesOf(it) }

private fun projectDays(project: Project, sessions: List<TranscriptSession>): List<Int> {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val ids = project.sessionIds.toSet()
    return days.map { day ->
        sessions.count { it.id in ids && dateOf(it.startedAt) == day }
    }
}

private fun formatRelativeDate(timestamp: Long, locale: Locale): String {
    val date = dateOf(timestamp)
    val today = LocalDate.now()
    return when {
        date == today -> if (locale.language == "zh") "今天" else "Today"
        date == today.minusDays(1) -> if (locale.language == "zh") "昨天" else "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", locale))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1_000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

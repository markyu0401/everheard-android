package io.github.markyu0401.everheard.ui.insight

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import io.github.markyu0401.everheard.data.project.ProjectBuilder
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.memory.MemoryActivity
import io.github.markyu0401.everheard.ui.project.ProjectsActivity
import io.github.markyu0401.everheard.ui.settings.SettingsActivity
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class InsightActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                InsightScreen()
            }
        }
    }
}

@Composable
private fun InsightScreen() {
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
    var sessions by remember { mutableStateOf<List<TranscriptSession>>(emptyList()) }
    var report by remember { mutableStateOf<InsightReport?>(null) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        val loadedSessions = transcriptStore.reload()
        var memories = memoryStore.reload()
        val missing = loadedSessions.filter { session ->
            session.utterances.isNotEmpty() &&
                memories.none { it.sessionId == session.id }
        }
        if (missing.isNotEmpty()) {
            missing.forEach { session ->
                runCatching { memoryStore.save(analyzer.analyze(session)) }
            }
            memories = memoryStore.reload()
        }
        sessions = loadedSessions
        val projects = projectBuilder.build(loadedSessions, memories)
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            LargeTitle(
                title = stringResource(R.string.insights_title),
                subtitle = stringResource(R.string.insights_subtitle)
            )
            Spacer(Modifier.height(24.dp))

            val current = report
            if (!loaded || current == null) {
                Text(
                    text = stringResource(R.string.memory_analyzing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GroupedCard {
                    MetricRow(
                        label = stringResource(R.string.insights_sessions),
                        value = current.sessionCount.toString()
                    )
                    MetricRow(
                        label = stringResource(R.string.insights_minutes),
                        value = current.minutes.toString()
                    )
                    MetricRow(
                        label = stringResource(R.string.insights_projects),
                        value = current.projectCount.toString()
                    )
                    MetricRow(
                        label = stringResource(R.string.insights_decisions),
                        value = current.decisionCount.toString()
                    )
                    MetricRow(
                        label = stringResource(R.string.insights_commitments),
                        value = "${current.commitmentCount} · ${current.openCommitmentCount} " +
                            stringResource(R.string.insights_open)
                    )
                    MetricRow(
                        label = stringResource(R.string.insights_risks),
                        value = current.riskCount.toString(),
                        showDivider = false
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (current.observations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.insights_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SectionHeader(stringResource(R.string.memory_section))
                    current.observations.forEach { observation ->
                        InsightObservationCard(
                            observation = observation,
                            onOpenSession = { sessionId ->
                                context.startActivity(
                                    Intent(context, SessionDetailActivity::class.java)
                                        .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, sessionId)
                                )
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatPeriod(current, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        BottomBar(
            selected = MainTab.INSIGHTS,
            onSelect = { tab ->
                when (tab) {
                    MainTab.TODAY -> context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    )
                    MainTab.LIBRARY -> context.startActivity(
                        Intent(context, TranscriptListActivity::class.java)
                    )
                    MainTab.PROJECTS -> context.startActivity(
                        Intent(context, ProjectsActivity::class.java)
                    )
                    MainTab.INSIGHTS -> Unit
                    MainTab.MEMORY -> context.startActivity(
                        Intent(context, MemoryActivity::class.java)
                    )
                    MainTab.SETTINGS -> context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                    )
                }
            }
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun InsightObservationCard(
    observation: InsightObservation,
    onOpenSession: (String) -> Unit
) {
    GroupedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = observation.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = observation.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            observation.action?.let { action ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.insights_action)}: $action",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (observation.citations.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.insights_sources),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                observation.citations.forEach { citation ->
                    CitationRow(citation, onOpenSession)
                }
            }
        }
    }
}

@Composable
private fun CitationRow(citation: InsightCitation, onOpenSession: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSession(citation.sessionId) }
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "\u201C${citation.quote}\u201D",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatCitationTime(citation.startMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCitationTime(ms: Long): String {
    val totalSeconds = ms / 1_000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun formatPeriod(report: InsightReport, locale: Locale): String {
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    return "${formatter.format(Date(report.periodStart))} – " +
        formatter.format(Date(report.periodEnd))
}

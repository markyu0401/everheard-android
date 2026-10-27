package io.github.markyu0401.everheard.ui.project

import android.content.Intent
import android.view.WindowManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.project.ProjectBuilder
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.memory.MemoryActivity
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.insight.InsightActivity
import io.github.markyu0401.everheard.ui.settings.SettingsActivity
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class ProjectsActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                ProjectsScreen(
                    onOpen = { id ->
                        startActivity(
                            Intent(this, ProjectDetailActivity::class.java)
                                .putExtra(ProjectDetailActivity.EXTRA_PROJECT_ID, id)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProjectsScreen(onOpen: (String) -> Unit) {
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
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        val sessions = transcriptStore.reload()
        var memories = memoryStore.reload()
        val missing = sessions.filter { session ->
            session.utterances.isNotEmpty() &&
                memories.none { it.sessionId == session.id }
        }
        if (missing.isNotEmpty()) {
            missing.forEach { session ->
                runCatching { memoryStore.save(analyzer.analyze(session)) }
            }
            memories = memoryStore.reload()
        }
        projects = projectBuilder.build(sessions, memories)
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
                title = stringResource(R.string.projects_title),
                subtitle = stringResource(R.string.projects_subtitle)
            )
            Spacer(Modifier.height(24.dp))

            if (!loaded) {
                Text(
                    text = stringResource(R.string.memory_analyzing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (projects.isEmpty()) {
                GroupedCard {
                    Text(
                        text = stringResource(R.string.projects_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                GroupedCard {
                    projects.forEachIndexed { index, project ->
                        val meta = buildString {
                            append(
                                stringResource(
                                    R.string.projects_session_count,
                                    project.sessionCount
                                )
                            )
                            append(" · ")
                            append(formatProjectDate(project.lastAt, locale))
                            if (project.openCommitments.isNotEmpty()) {
                                append(" · ")
                                append(
                                    stringResource(
                                        R.string.projects_open_count,
                                        project.openCommitments.size
                                    )
                                )
                            }
                        }
                        SettingsRow(
                            icon = Icons.Filled.Star,
                            title = project.name,
                            subtitle = meta,
                            trailingText = project.keywords.take(2).joinToString(" · "),
                            showDivider = index != projects.lastIndex,
                            onClick = { onOpen(project.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        BottomBar(
            selected = MainTab.PROJECTS,
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
                    MainTab.PROJECTS -> Unit
                    MainTab.MEMORY -> context.startActivity(
                        Intent(context, MemoryActivity::class.java)
                    )
                    MainTab.INSIGHTS -> context.startActivity(
                        Intent(context, InsightActivity::class.java)
                    )
                    MainTab.SETTINGS -> context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                    )
                }
            }
        )
    }
}

private fun formatProjectDate(timestamp: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date(timestamp))

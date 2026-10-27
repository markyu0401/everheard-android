package io.github.markyu0401.everheard.ui.transcript

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptHit
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BottomBar
import io.github.markyu0401.everheard.ui.components.BrandBar
import io.github.markyu0401.everheard.ui.components.EmptyState
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.MainTab
import io.github.markyu0401.everheard.ui.components.PrivacyNote
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SearchField
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.memory.MemoryActivity
import io.github.markyu0401.everheard.ui.settings.SettingsActivity
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class TranscriptListActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                TranscriptListScreen(
                    onOpen = { id ->
                        startActivity(
                            Intent(this, SessionDetailActivity::class.java)
                                .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, id)
                        )
                    },
                    onOpenLive = {
                        startActivity(Intent(this, io.github.markyu0401.everheard.ui.live.LiveCaptureActivity::class.java))
                    }
                )
            }
        }
    }
}

private enum class LibraryFilter { ALL, TODAY, WEEK, STARRED }

@Composable
fun TranscriptListScreen(
    onOpen: (String) -> Unit,
    onOpenLive: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val store = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    var sessions by remember { mutableStateOf<List<TranscriptSession>>(emptyList()) }
    var hits by remember { mutableStateOf<List<TranscriptHit>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        sessions = store.reload()
        loaded = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    sessions = store.reload()
                    if (query.isNotBlank()) hits = store.search(query)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(query) {
        hits = if (query.isBlank()) emptyList() else store.search(query)
    }

    val today = LocalDate.now()
    val filtered = remember(sessions, filter) {
        when (LibraryFilter.entries[filter]) {
            LibraryFilter.ALL -> sessions
            LibraryFilter.TODAY -> sessions.filter { dateOf(it.startedAt) == today }
            LibraryFilter.WEEK -> sessions.filter { dateOf(it.startedAt) >= today.minusDays(6) }
            LibraryFilter.STARRED -> sessions.filter { it.utterances.any { u -> u.marked } }
        }
    }
    val groups = remember(filtered) {
        filtered.groupBy { dateOf(it.startedAt) }
            .toSortedMap(compareByDescending { it })
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
                .padding(horizontal = 20.dp)
        ) {
            BrandBar()
            ScreenHeader(
                title = stringResource(R.string.tab_library),
                sideText = stringResource(R.string.library_sessions, sessions.size)
            )
            Spacer(Modifier.height(14.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.library_search_hint)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LibraryChip(
                    text = stringResource(R.string.library_all),
                    selected = filter == 0,
                    onClick = { filter = 0 }
                )
                LibraryChip(
                    text = stringResource(R.string.library_today),
                    selected = filter == 1,
                    onClick = { filter = 1 }
                )
                LibraryChip(
                    text = stringResource(R.string.library_week),
                    selected = filter == 2,
                    onClick = { filter = 2 }
                )
                LibraryChip(
                    text = stringResource(R.string.library_starred),
                    selected = filter == 3,
                    onClick = { filter = 3 }
                )
            }
            Spacer(Modifier.height(8.dp))

            if (query.isNotBlank()) {
                SearchResults(hits = hits, onOpen = onOpen)
            } else if (loaded && filtered.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    actionText = stringResource(R.string.live_open),
                    onAction = onOpenLive,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groups.forEach { (date, daySessions) ->
                        item(key = "header-$date") {
                            SectionLabel(
                                text = dateLabel(date, today, LocalConfiguration.current.locales[0]),
                                trailing = daySessions.size.toString(),
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        items(daySessions, key = { it.id }) { session ->
                            SessionRow(
                                session = session,
                                onClick = { onOpen(session.id) }
                            )
                        }
                    }
                    item(key = "privacy") {
                        Spacer(Modifier.height(12.dp))
                        PrivacyNote(text = stringResource(R.string.home_footer))
                    }
                }
            }
        }

        BottomBar(
            selected = MainTab.LIBRARY,
            onSelect = { tab ->
                when (tab) {
                    MainTab.TODAY -> context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    )
                    MainTab.MEMORY -> context.startActivity(
                        Intent(context, MemoryActivity::class.java)
                    )
                    MainTab.SETTINGS -> context.startActivity(
                        Intent(context, SettingsActivity::class.java)
                    )
                    MainTab.LIBRARY -> Unit
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun SearchResults(hits: List<TranscriptHit>, onOpen: (String) -> Unit) {
    if (hits.isEmpty()) {
        Text(
            text = stringResource(R.string.transcript_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp)
        )
        return
    }
    EverheardCard(modifier = Modifier.padding(top = 10.dp)) {
        hits.forEachIndexed { index, hit ->
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(hit.sessionId) }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = hit.sessionTitle.ifBlank {
                            stringResource(R.string.transcript_session_fallback)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = hit.utterance.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formatTime(hit.utterance.startMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index != hits.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 14.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: TranscriptSession, onClick: () -> Unit) {
    val marked = session.utterances.any { it.marked }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = formatClock(session.startedAt),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(40.dp)
                    .padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                val titleText = session.title.ifBlank {
                    stringResource(R.string.transcript_session_fallback)
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val snippet = session.utterances.firstOrNull()?.text.orEmpty()
                if (snippet.isNotBlank() && snippet != titleText) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp, top = 1.dp)
            ) {
                Text(
                    text = formatDuration(session),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (marked) {
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = LocalSemanticColors.current.commitment,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun LibraryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .then(
                if (selected) Modifier
                else Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(999.dp)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Medium
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun dateOf(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dateLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when {
    date == today -> if (locale.language == "zh") "今天" else "Today"
    date == today.minusDays(1) -> if (locale.language == "zh") "昨天" else "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("MMM d", locale))
}

private fun formatClock(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

@Composable
private fun formatDuration(session: TranscriptSession): String {
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

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1_000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

package io.github.markyu0401.everheard.ui.person

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.person.PersonBuilder
import io.github.markyu0401.everheard.data.person.PersonProfile
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
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.EverheardSerif
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PersonDetailActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val personId = intent.getStringExtra(EXTRA_PERSON_ID).orEmpty()
        setContent {
            EverheardAppTheme {
                PersonDetailScreen(
                    personId = personId,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun PersonDetailScreen(personId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val transcriptStore = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val builder = remember { PersonBuilder() }
    var sessions by remember { mutableStateOf<List<TranscriptSession>>(emptyList()) }
    var person by remember { mutableStateOf<PersonProfile?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(personId) {
        sessions = transcriptStore.reload()
        person = builder.build(sessions, memoryStore.reload())
            .firstOrNull { it.id == personId }
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
                text = stringResource(R.string.people_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        val current = person
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
                    title = if (current.name == "me") {
                        stringResource(R.string.speaker_me)
                    } else {
                        current.name
                    },
                    subtitle = formatPersonDate(current.lastTalkedAt)
                )
                Spacer(Modifier.height(14.dp))
                MetricStrip(
                    metrics = listOf(
                        current.sessionIds.size.toString() to stringResource(R.string.memory_stat_sessions),
                        current.openCommitments.size.toString() to stringResource(R.string.memory_stat_open)
                    )
                )
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
                    trailing = current.sessionIds.size.toString()
                )
                EverheardCard {
                    val personSessions = sessions.filter { it.id in current.sessionIds }
                    personSessions.forEachIndexed { index, session ->
                        SettingsRow(
                            icon = Icons.Filled.DateRange,
                            title = session.title.ifBlank {
                                stringResource(R.string.transcript_session_fallback)
                            },
                            subtitle = formatPersonDate(session.startedAt),
                            showDivider = index != personSessions.lastIndex,
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

            if (current.quotes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(
                        text = stringResource(R.string.person_quotes),
                        trailing = current.quotes.size.toString()
                    )
                    EverheardCard {
                        current.quotes.forEachIndexed { index, quote ->
                            Text(
                                text = "\u201C$quote\u201D",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = EverheardSerif,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 18.sp,
                                    lineHeight = 23.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                            if (index != current.quotes.lastIndex) {
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
        }
    }
}

private fun formatPersonDate(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

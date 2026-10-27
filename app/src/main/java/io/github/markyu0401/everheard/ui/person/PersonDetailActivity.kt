package io.github.markyu0401.everheard.ui.person

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.person.PersonBuilder
import io.github.markyu0401.everheard.data.person.PersonProfile
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.CommitmentRow
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.transcript.SessionDetailActivity
import java.io.File
import java.text.DateFormat
import java.util.Date
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
    val locale = LocalConfiguration.current.locales[0]
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
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)

        val current = person
        if (!loaded) {
            Text(
                text = stringResource(R.string.memory_analyzing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (current == null) {
            Text(
                text = stringResource(R.string.project_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        LargeTitle(
            title = if (current.name == "me") {
                stringResource(R.string.speaker_me)
            } else {
                current.name
            },
            subtitle = formatPersonDate(current.lastTalkedAt, locale)
        )

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (current.openCommitments.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.project_commitments))
                    GroupedCard {
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
                SectionHeader(stringResource(R.string.project_sessions))
                GroupedCard {
                    val personSessions = sessions.filter { it.id in current.sessionIds }
                    personSessions.forEachIndexed { index, session ->
                        SettingsRow(
                            icon = Icons.Filled.DateRange,
                            title = session.title.ifBlank {
                                stringResource(R.string.transcript_session_fallback)
                            },
                            subtitle = formatPersonDate(session.startedAt, locale),
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
                    SectionHeader(stringResource(R.string.person_quotes))
                    GroupedCard {
                        current.quotes.forEachIndexed { index, quote ->
                            Text(
                                text = "\u201C$quote\u201D",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                            if (index != current.quotes.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
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
}

private fun formatPersonDate(timestamp: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date(timestamp))

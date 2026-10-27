package io.github.markyu0401.everheard.ui.person

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
import androidx.compose.material.icons.filled.Face
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
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class PeopleActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                PeopleScreen(
                    onBack = { finish() },
                    onOpen = { id ->
                        startActivity(
                            Intent(this, PersonDetailActivity::class.java)
                                .putExtra(PersonDetailActivity.EXTRA_PERSON_ID, id)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PeopleScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val transcriptStore = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }
    val memoryStore = remember {
        EncryptedMemoryStore(File(context.filesDir, "memory.bin"))
    }
    val builder = remember { PersonBuilder() }
    var people by remember { mutableStateOf<List<PersonProfile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessions = transcriptStore.reload()
        val memories = memoryStore.reload()
        people = builder.build(sessions, memories)
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)
        LargeTitle(
            title = stringResource(R.string.people_title),
            subtitle = stringResource(R.string.people_subtitle)
        )
        Spacer(Modifier.height(20.dp))

        if (!loaded) {
            Text(
                text = stringResource(R.string.memory_analyzing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (people.isEmpty()) {
            GroupedCard {
                Text(
                    text = stringResource(R.string.people_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            GroupedCard {
                people.forEachIndexed { index, person ->
                    val displayName = if (person.name == "me") {
                        stringResource(R.string.speaker_me)
                    } else {
                        person.name
                    }
                    val meta = buildString {
                        append(
                            stringResource(
                                R.string.projects_session_count,
                                person.sessionIds.size
                            )
                        )
                        append(" · ")
                        append(formatPersonDate(person.lastTalkedAt, locale))
                        if (person.openCommitments.isNotEmpty()) {
                            append(" · ")
                            append(
                                stringResource(
                                    R.string.projects_open_count,
                                    person.openCommitments.size
                                )
                            )
                        }
                    }
                    SettingsRow(
                        icon = Icons.Filled.Face,
                        title = displayName,
                        subtitle = meta,
                        showDivider = index != people.lastIndex,
                        onClick = { onOpen(person.id) }
                    )
                }
            }
        }
    }
}

private fun formatPersonDate(timestamp: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date(timestamp))

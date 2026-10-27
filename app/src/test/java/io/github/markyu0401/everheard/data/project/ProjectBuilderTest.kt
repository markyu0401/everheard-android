package io.github.markyu0401.everheard.data.project

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.junit.Test

class ProjectBuilderTest {

    private val builder = ProjectBuilder()
    private val analyzer = RuleBasedMemoryAnalyzer(now = { 1_700_000_000_000L })

    @Test
    fun groups_sessions_that_share_topic_keywords() {
        val launchOne = session(
            id = "s1",
            title = "Launch timeline",
            texts = listOf(
                "We decided to move the launch to Friday.",
                "The API is not ready for launch."
            )
        )
        val launchTwo = session(
            id = "s2",
            title = "Launch review",
            texts = listOf(
                "We finished the launch checklist.",
                "The launch risk is the API."
            )
        )
        val groceries = session(
            id = "s3",
            title = "Grocery list",
            texts = listOf("Buy milk and eggs.")
        )
        val sessions = listOf(launchOne, launchTwo, groceries)
        val memories = sessions.map(analyzer::analyze)

        val projects = builder.build(sessions, memories)

        assertThat(projects).hasSize(2)
        val launch = projects.first { it.sessionIds.contains("s1") }
        assertThat(launch.sessionIds).containsExactly("s1", "s2")
        assertThat(launch.keywords).contains("launch")
        assertThat(launch.events).isNotEmpty()
        assertThat(launch.events.all { it.event.evidence.quote.isNotBlank() }).isTrue()
        val other = projects.first { it.sessionIds.contains("s3") }
        assertThat(other.sessionIds).containsExactly("s3")
    }

    @Test
    fun keeps_open_commitments_with_their_project() {
        val one = session(
            id = "s1",
            title = "Timeline review",
            texts = listOf(
                "I will send the timeline by tomorrow.",
                "The timeline risk is the API."
            )
        )
        val two = session(
            id = "s2",
            title = "Timeline follow-up",
            texts = listOf(
                "I will update the timeline by Friday.",
                "The timeline API is still a risk."
            )
        )
        val sessions = listOf(one, two)
        val memories = sessions.map(analyzer::analyze)

        val projects = builder.build(sessions, memories)

        assertThat(projects).hasSize(1)
        assertThat(projects.first().openCommitments).hasSize(2)
        assertThat(projects.first().openCommitments.all {
            it.evidence.quote.isNotBlank()
        }).isTrue()
    }

    private fun session(id: String, title: String, texts: List<String>) =
        TranscriptSession(
            id = id,
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_030_000L,
            title = title,
            utterances = texts.mapIndexed { index, text ->
                TranscriptUtterance(
                    id = "$id-u$index",
                    sessionId = id,
                    startMs = index * 2_000L,
                    endMs = index * 2_000L + 1_500L,
                    text = text
                )
            }
        )
}

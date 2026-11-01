package io.github.markyu0401.everheard.data.insight

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.project.ProjectBuilder
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import java.util.concurrent.TimeUnit
import org.junit.Test

class InsightBuilderTest {

    private val now = 1_700_000_000_000L
    private val analyzer = RuleBasedMemoryAnalyzer(now = { now })
    private val projectBuilder = ProjectBuilder()
    private val insightBuilder = InsightBuilder(now = { now })

    @Test
    fun computes_weekly_metrics_and_cited_observations() {
        val sessions = listOf(
            session(
                id = "s1",
                title = "Timeline review",
                texts = listOf(
                    "I will send the timeline by tomorrow.",
                    "The timeline risk is the API.",
                    "What if we use a shared timeline?"
                )
            ),
            session(
                id = "s2",
                title = "Timeline follow-up",
                texts = listOf(
                    "I will update the timeline by Friday.",
                    "The timeline API is still a risk.",
                    "We decided to use the new timeline."
                )
            )
        )
        val memories = sessions.map(analyzer::analyze)
        val projects = projectBuilder.build(sessions, memories)

        val report = insightBuilder.build(sessions, memories, projects)

        assertThat(report.sessionCount).isEqualTo(2)
        assertThat(report.minutes).isEqualTo(2)
        assertThat(report.projectCount).isEqualTo(1)
        assertThat(report.commitmentCount).isEqualTo(2)
        assertThat(report.openCommitmentCount).isEqualTo(2)
        assertThat(report.riskCount).isEqualTo(2)
        assertThat(report.observations).isNotEmpty()
        report.observations.forEach { observation ->
            assertThat(observation.citations.size).isAtLeast(2)
            observation.citations.forEach { citation ->
                assertThat(citation.quote).isNotEmpty()
                assertThat(citation.sessionId).isNotEmpty()
            }
        }
    }

    @Test
    fun ignores_sessions_outside_the_window() {
        val old = session(
            id = "old",
            title = "Old meeting",
            startedAt = now - TimeUnit.DAYS.toMillis(30),
            texts = listOf("I will do something.")
        )
        val sessions = listOf(old)
        val memories = sessions.map(analyzer::analyze)
        val projects = projectBuilder.build(sessions, memories)

        val report = insightBuilder.build(sessions, memories, projects)

        assertThat(report.sessionCount).isEqualTo(0)
        assertThat(report.observations).isEmpty()
    }

    @Test
    fun counts_speech_time_not_the_capture_window() {
        // Capture ran for three hours; only one minute of it was speech.
        val long = session(
            id = "long",
            title = "Open all afternoon",
            startedAt = now - TimeUnit.HOURS.toMillis(3),
            endedAt = now,
            texts = listOf("One.", "Two.", "Three.")
        )
        val memories = listOf(long).map(analyzer::analyze)
        val report = insightBuilder.build(
            listOf(long),
            memories,
            projectBuilder.build(listOf(long), memories)
        )

        assertThat(report.minutes).isEqualTo(1)
    }

    @Test
    fun background_media_is_not_quoted_as_evidence() {
        val talk = session(
            id = "talk",
            title = "Timeline review",
            texts = listOf(
                "I will send the timeline by tomorrow.",
                "The timeline risk is the API.",
                "We decided to ship Friday."
            )
        ).let { it.copy(voiceProfileActive = true, utterances = it.utterances.map { u -> u.copy(speaker = "me") }) }
        val followUp = session(
            id = "follow",
            title = "Timeline follow-up",
            texts = listOf(
                "I will update the timeline by Friday.",
                "The timeline API is still a risk.",
                "We decided to keep the timeline."
            )
        ).let { it.copy(voiceProfileActive = true, utterances = it.utterances.map { u -> u.copy(speaker = "me") }) }
        val tv = session(
            id = "tv",
            title = "Drama",
            texts = listOf("本王决定今晚出兵。", "殿下饶命，我明天再来。", "明天再议。")
        ).let { it.copy(voiceProfileActive = true, utterances = it.utterances.map { u -> u.copy(speaker = "other") }) }

        val sessions = listOf(talk, followUp, tv)
        val memories = sessions.map(analyzer::analyze)
        val report = insightBuilder.build(sessions, memories, projectBuilder.build(sessions, memories))

        assertThat(memories.last().events).isEmpty()
        assertThat(report.observations).isNotEmpty()
        report.observations.forEach { observation ->
            observation.citations.forEach { citation ->
                assertThat(citation.sessionId).isNotEqualTo("tv")
            }
        }
        assertThat(report.minutes).isEqualTo(3)
    }

    private fun session(
        id: String,
        title: String,
        startedAt: Long = now - 60_000L,
        endedAt: Long = startedAt + 90_000L,
        texts: List<String>
    ) = TranscriptSession(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        title = title,
        utterances = texts.mapIndexed { index, text ->
            TranscriptUtterance(
                id = "$id-u$index",
                sessionId = id,
                startMs = index * 25_000L,
                endMs = index * 25_000L + 20_000L,
                text = text
            )
        }
    )
}

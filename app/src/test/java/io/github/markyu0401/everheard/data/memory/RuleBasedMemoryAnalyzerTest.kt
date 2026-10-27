package io.github.markyu0401.everheard.data.memory

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.junit.Test

class RuleBasedMemoryAnalyzerTest {

    private val analyzer = RuleBasedMemoryAnalyzer(now = { 1_700_000_000_000L })

    @Test
    fun extracts_evidence_backed_events_commitments_and_ideas() {
        val session = session(
            "We decided to move the launch to Friday.",
            "I will send the draft by tomorrow.",
            "The risk is that the API is not ready.",
            "We finished the onboarding flow.",
            "What if we use a timeline?"
        ).copy(
            utterances = session(
                "We decided to move the launch to Friday.",
                "I will send the draft by tomorrow.",
                "The risk is that the API is not ready.",
                "We finished the onboarding flow.",
                "What if we use a timeline?"
            ).utterances.mapIndexed { index, utterance ->
                if (index == 4) utterance.copy(marked = true) else utterance
            }
        )

        val memory = analyzer.analyze(session)

        assertThat(memory.summary).isEqualTo(
            "We decided to move the launch to Friday. I will send the draft by tomorrow."
        )
        assertThat(memory.summaryEvidence?.quote)
            .isEqualTo("We decided to move the launch to Friday.")
        assertThat(memory.events.map { it.type }).containsExactly(
            MemoryEventType.DECISION,
            MemoryEventType.COMMITMENT,
            MemoryEventType.RISK,
            MemoryEventType.PROGRESS,
            MemoryEventType.INSPIRATION
        )
        assertThat(memory.commitments).hasSize(1)
        assertThat(memory.commitments.first().dueHint).isEqualTo("tomorrow")
        assertThat(memory.commitments.first().owner).isEqualTo("me")
        assertThat(memory.inspirations).hasSize(1)
        memory.events.forEach { event ->
            assertThat(event.evidence.quote).isEqualTo(event.text)
            assertThat(event.evidence.utteranceId).isNotEmpty()
        }
        assertThat(memory.generatedAt).isEqualTo(1_700_000_000_000L)
        assertThat(memory.analyzer).isEqualTo(RuleBasedMemoryAnalyzer.NAME)
    }

    @Test
    fun extracts_chinese_decisions_commitments_risks_and_ideas() {
        val session = session(
            "我明天会把方案发给你。",
            "我们决定先做时间线。",
            "这个风险要提前处理。",
            "灵感：把项目串起来。"
        )

        val memory = analyzer.analyze(session)

        assertThat(memory.commitments).hasSize(1)
        assertThat(memory.commitments.first().dueHint).isEqualTo("明天")
        assertThat(memory.commitments.first().owner).isEqualTo("me")
        assertThat(memory.events.map { it.type }).containsExactly(
            MemoryEventType.COMMITMENT,
            MemoryEventType.DECISION,
            MemoryEventType.RISK,
            MemoryEventType.INSPIRATION
        )
        assertThat(memory.inspirations.first().text).isEqualTo("灵感：把项目串起来。")
        memory.events.forEach { event ->
            assertThat(event.evidence.quote).isEqualTo(event.text)
        }
    }

    @Test
    fun marked_line_is_an_idea_even_without_a_keyword() {
        val session = session("We could ship it on Monday.").copy(
            utterances = listOf(
                utterance(
                    id = "u1",
                    text = "We could ship it on Monday.",
                    marked = true
                )
            )
        )

        val memory = analyzer.analyze(session)

        assertThat(memory.events).hasSize(1)
        assertThat(memory.events.first().type).isEqualTo(MemoryEventType.INSPIRATION)
        assertThat(memory.inspirations).hasSize(1)
    }

    @Test
    fun empty_session_produces_no_fabricated_items() {
        val memory = analyzer.analyze(TranscriptSession(id = "empty", startedAt = 1L))

        assertThat(memory.summary).isEmpty()
        assertThat(memory.summaryEvidence).isNull()
        assertThat(memory.events).isEmpty()
        assertThat(memory.commitments).isEmpty()
        assertThat(memory.inspirations).isEmpty()
    }

    @Test
    fun caps_the_number_of_items() {
        val utterances = (1..20).map { index ->
            utterance(
                id = "u$index",
                text = "I will do item $index.",
                startMs = index * 1_000L
            )
        }
        val memory = analyzer.analyze(
            TranscriptSession(id = "many", startedAt = 1L, utterances = utterances)
        )

        assertThat(memory.events).hasSize(8)
        assertThat(memory.commitments).hasSize(5)
    }

    private fun session(vararg texts: String): TranscriptSession =
        TranscriptSession(
            id = "s1",
            startedAt = 1_700_000_000_000L,
            utterances = texts.mapIndexed { index, text ->
                utterance(id = "u${index + 1}", text = text, startMs = index * 2_000L)
            }
        )

    private fun utterance(
        id: String,
        text: String,
        startMs: Long = 0L,
        marked: Boolean = false
    ) = TranscriptUtterance(
        id = id,
        sessionId = "s1",
        startMs = startMs,
        endMs = startMs + 1_500L,
        text = text,
        marked = marked
    )
}

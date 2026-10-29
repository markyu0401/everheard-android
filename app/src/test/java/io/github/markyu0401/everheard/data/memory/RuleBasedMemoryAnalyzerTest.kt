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

    @Test
    fun a_session_without_a_matching_voice_produces_no_memory() {
        // TV drama: every row is somebody else. It must stay transcript-only.
        val utterances = listOf(
            utterance("u1", "狮子殿想是这样的，本王决定今晚出兵。", speaker = "other"),
            utterance("u2", "殿下饶命，我明天再来。", speaker = "other")
        )
        val memory = analyzer.analyze(
            TranscriptSession(
                id = "tv",
                startedAt = 1L,
                utterances = utterances,
                voiceProfileActive = true
            )
        )

        assertThat(memory.summary).isEmpty()
        assertThat(memory.events).isEmpty()
        assertThat(memory.commitments).isEmpty()
        assertThat(memory.inspirations).isEmpty()
    }

    @Test
    fun with_attribution_only_confirmed_rows_become_memory() {
        val utterances = listOf(
            utterance("u1", "I will send the draft by tomorrow.", speaker = "me"),
            utterance("u2", "本王决定今晚出兵，明天再议。", speaker = "other")
        )
        val memory = analyzer.analyze(
            TranscriptSession(
                id = "mixed",
                startedAt = 1L,
                utterances = utterances,
                voiceProfileActive = true
            )
        )

        assertThat(memory.commitments).hasSize(1)
        assertThat(memory.commitments.first().text)
            .isEqualTo("I will send the draft by tomorrow.")
        assertThat(memory.commitments.first().owner).isEqualTo("me")
        assertThat(memory.events).hasSize(1)
        assertThat(memory.events.first().text)
            .isEqualTo("I will send the draft by tomorrow.")
    }

    @Test
    fun an_unattributed_session_keeps_the_text_heuristic() {
        val memory = analyzer.analyze(
            TranscriptSession(
                id = "no-profile",
                startedAt = 1L,
                utterances = listOf(
                    utterance("u1", "我明天会把方案发给你。", speaker = null)
                ),
                voiceProfileActive = false
            )
        )

        assertThat(memory.commitments).hasSize(1)
        assertThat(memory.commitments.first().owner).isEqualTo("me")
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
        marked: Boolean = false,
        speaker: String? = null
    ) = TranscriptUtterance(
        id = id,
        sessionId = "s1",
        startMs = startMs,
        endMs = startMs + 1_500L,
        text = text,
        speaker = speaker,
        marked = marked
    )
}

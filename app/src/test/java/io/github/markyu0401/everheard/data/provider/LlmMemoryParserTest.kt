package io.github.markyu0401.everheard.data.provider

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.junit.Test

class LlmMemoryParserTest {

    private val session = TranscriptSession(
        id = "s1",
        startedAt = 0L,
        title = "Planning",
        utterances = listOf(
            TranscriptUtterance(
                id = "u1",
                sessionId = "s1",
                startMs = 0L,
                endMs = 1_000L,
                text = "We decided to ship on Friday.",
                speaker = "me"
            ),
            TranscriptUtterance(
                id = "u2",
                sessionId = "s1",
                startMs = 2_000L,
                endMs = 3_000L,
                text = "I will send the draft by tomorrow.",
                speaker = "me"
            ),
            TranscriptUtterance(
                id = "u3",
                sessionId = "s1",
                startMs = 4_000L,
                endMs = 5_000L,
                text = "What if we add a timeline?",
                speaker = null
            )
        )
    )

    @Test
    fun parses_valid_items_and_maps_evidence_from_the_transcript() {
        val content = """
            {
              "summary": "The team will ship Friday and send the draft tomorrow.",
              "events": [
                {"type": "decision", "text": "Ship on Friday", "quote": "We decided to ship on Friday.", "utteranceId": "u1", "startMs": 9999, "speaker": "someone"},
                {"type": "commitment", "text": "Send the draft", "quote": "I will send the draft by tomorrow.", "utteranceId": "u2", "startMs": 2000}
              ],
              "commitments": [
                {"text": "Send the draft", "owner": "me", "dueHint": "tomorrow", "quote": "I will send the draft by tomorrow.", "utteranceId": "u2", "startMs": 2000}
              ],
              "inspirations": [
                {"text": "Add a timeline", "quote": "What if we add a timeline?", "utteranceId": "u3", "startMs": 4000}
              ]
            }
        """.trimIndent()

        val memory = success(LlmMemoryParser.parse(content, session, generatedAt = 123L))

        assertThat(memory.sessionId).isEqualTo("s1")
        assertThat(memory.analyzer).isEqualTo(LlmMemoryAnalyzer.NAME)
        assertThat(memory.generatedAt).isEqualTo(123L)
        assertThat(memory.summary)
            .isEqualTo("The team will ship Friday and send the draft tomorrow.")
        assertThat(memory.summaryEvidence).isNull()

        assertThat(memory.events).hasSize(2)
        assertThat(memory.events[0].type).isEqualTo(MemoryEventType.DECISION)
        assertThat(memory.events[0].text).isEqualTo("Ship on Friday")
        assertThat(memory.events[0].evidence.utteranceId).isEqualTo("u1")
        assertThat(memory.events[0].evidence.quote)
            .isEqualTo("We decided to ship on Friday.")
        // startMs is taken from the transcript line, not the model's echo.
        assertThat(memory.events[0].evidence.startMs).isEqualTo(0L)
        // The transcript speaker wins over the model's guess.
        assertThat(memory.events[0].evidence.speaker).isEqualTo("me")

        assertThat(memory.commitments).hasSize(1)
        assertThat(memory.commitments[0].owner).isEqualTo("me")
        assertThat(memory.commitments[0].dueHint).isEqualTo("tomorrow")
        assertThat(memory.commitments[0].evidence.startMs).isEqualTo(2_000L)

        assertThat(memory.inspirations).hasSize(1)
        assertThat(memory.inspirations[0].evidence.utteranceId).isEqualTo("u3")
        assertThat(memory.inspirations[0].evidence.speaker).isNull()
    }

    @Test
    fun drops_items_whose_quote_is_not_a_substring_of_the_named_utterance() {
        val content = """
            {
              "summary": "Summary stays.",
              "events": [
                {"type": "decision", "text": "Ship Monday", "quote": "We decided to ship on Monday.", "utteranceId": "u1", "startMs": 0},
                {"type": "commitment", "text": "Cross-wired quote", "quote": "We decided to ship on Friday.", "utteranceId": "u2", "startMs": 2000}
              ],
              "commitments": [
                {"text": "Invented", "quote": "I will send the deck tonight.", "utteranceId": "u2", "startMs": 2000}
              ],
              "inspirations": [
                {"text": "Invented idea", "quote": "Let's rewrite the whole app.", "utteranceId": "u3", "startMs": 4000}
              ]
            }
        """.trimIndent()

        val memory = success(LlmMemoryParser.parse(content, session))

        assertThat(memory.summary).isEqualTo("Summary stays.")
        assertThat(memory.events).isEmpty()
        assertThat(memory.commitments).isEmpty()
        assertThat(memory.inspirations).isEmpty()
    }

    @Test
    fun drops_items_with_unknown_or_missing_utterance_ids() {
        val content = """
            {
              "summary": "",
              "events": [
                {"type": "progress", "text": "Ghost", "quote": "We decided to ship on Friday.", "utteranceId": "u99", "startMs": 0},
                {"type": "progress", "text": "No id", "quote": "We decided to ship on Friday.", "startMs": 0}
              ],
              "commitments": [],
              "inspirations": []
            }
        """.trimIndent()

        val memory = success(LlmMemoryParser.parse(content, session))

        assertThat(memory.events).isEmpty()
    }

    @Test
    fun drops_unknown_event_types_but_keeps_known_ones() {
        val content = """
            {
              "summary": "",
              "events": [
                {"type": "gossip", "text": "Unknown", "quote": "We decided to ship on Friday.", "utteranceId": "u1", "startMs": 0},
                {"type": "", "text": "Blank", "quote": "We decided to ship on Friday.", "utteranceId": "u1", "startMs": 0},
                {"type": "DECISION", "text": "Known", "quote": "We decided to ship on Friday.", "utteranceId": "u1", "startMs": 0},
                {"type": "risk", "text": "Known risk", "quote": "I will send the draft by tomorrow.", "utteranceId": "u2", "startMs": 2000}
              ]
            }
        """.trimIndent()

        val memory = success(LlmMemoryParser.parse(content, session))

        assertThat(memory.events).hasSize(2)
        assertThat(memory.events.map { it.type })
            .containsExactly(MemoryEventType.DECISION, MemoryEventType.RISK)
    }

    @Test
    fun malformed_json_returns_a_failure() {
        assertThat(failure(LlmMemoryParser.parse("not json", session)).reason)
            .contains("malformed")
        assertThat(failure(LlmMemoryParser.parse("", session)).reason)
            .contains("malformed")
        assertThat(failure(LlmMemoryParser.parse("""{"summary": "x", "events": [}""", session)).reason)
            .contains("malformed")
    }

    @Test
    fun accepts_fenced_json_and_json_embedded_in_prose() {
        val body = """
            {
              "summary": "Fenced",
              "events": [
                {"type": "progress", "text": "Done", "quote": "What if we add a timeline?", "utteranceId": "u3", "startMs": 4000}
              ]
            }
        """.trimIndent()

        val fenced = success(LlmMemoryParser.parse("```json\n$body\n```", session))
        assertThat(fenced.summary).isEqualTo("Fenced")
        assertThat(fenced.events).hasSize(1)

        val prose = success(LlmMemoryParser.parse("Here is the result:\n$body\nThanks!", session))
        assertThat(prose.summary).isEqualTo("Fenced")
        assertThat(prose.events).hasSize(1)
    }

    @Test
    fun quote_matching_normalizes_whitespace_and_case() {
        val content = """
            {
              "summary": "",
              "events": [
                {"type": "decision", "text": "Ship", "quote": "we decided to ship on friday.", "utteranceId": "u1", "startMs": 0},
                {"type": "progress", "text": "Draft", "quote": "I will send  the draft by tomorrow.", "utteranceId": "u2", "startMs": 2000}
              ]
            }
        """.trimIndent()

        val memory = success(LlmMemoryParser.parse(content, session))

        assertThat(memory.events).hasSize(2)
    }

    @Test
    fun blank_text_falls_back_to_the_validated_quote() {
        val content = """
            {
              "summary": "",
              "events": [
                {"type": "decision", "text": "", "quote": "We decided to ship on Friday.", "utteranceId": "u1", "startMs": 0}
              ]
            }
        """.trimIndent()

        val memory = success(LlmMemoryParser.parse(content, session))

        assertThat(memory.events).hasSize(1)
        assertThat(memory.events[0].text).isEqualTo("We decided to ship on Friday.")
    }

    private fun success(result: LlmAnalysisResult): SessionMemory {
        assertThat(result).isInstanceOf(LlmAnalysisResult.Success::class.java)
        return (result as LlmAnalysisResult.Success).memory
    }

    private fun failure(result: LlmAnalysisResult): LlmAnalysisResult.Failure {
        assertThat(result).isInstanceOf(LlmAnalysisResult.Failure::class.java)
        return result as LlmAnalysisResult.Failure
    }
}

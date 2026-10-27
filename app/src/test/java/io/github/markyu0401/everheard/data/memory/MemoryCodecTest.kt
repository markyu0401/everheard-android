package io.github.markyu0401.everheard.data.memory

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryCodecTest {

    @Test
    fun round_trips_memory_with_evidence() {
        val memory = SessionMemory(
            sessionId = "s1",
            summary = "We decided to ship on Friday.",
            summaryEvidence = MemoryEvidence(
                utteranceId = "u1",
                quote = "We decided to ship on Friday.",
                startMs = 0L,
                speaker = "me"
            ),
            events = listOf(
                MemoryEvent(
                    id = "e1",
                    type = MemoryEventType.DECISION,
                    text = "We decided to ship on Friday.",
                    evidence = MemoryEvidence("u1", "We decided to ship on Friday.", 0L, "me")
                ),
                MemoryEvent(
                    id = "e2",
                    type = MemoryEventType.RISK,
                    text = "The API is not ready.",
                    evidence = MemoryEvidence("u2", "The API is not ready.", 4_000L, null)
                )
            ),
            commitments = listOf(
                Commitment(
                    id = "c1",
                    text = "I will send the draft by tomorrow.",
                    owner = "me",
                    dueHint = "tomorrow",
                    evidence = MemoryEvidence("u2", "I will send the draft by tomorrow.", 4_000L, "me")
                )
            ),
            inspirations = listOf(
                Inspiration(
                    id = "i1",
                    text = "What if we use a timeline?",
                    evidence = MemoryEvidence("u3", "What if we use a timeline?", 8_000L, null)
                )
            ),
            generatedAt = 1_700_000_000_000L,
            analyzer = "rule-v1"
        )

        val decoded = MemoryCodec.decode(MemoryCodec.encode(listOf(memory)))

        assertThat(decoded).hasSize(1)
        assertThat(decoded.first()).isEqualTo(memory)
    }

    @Test
    fun drops_items_without_evidence_or_unknown_types() {
        val raw = """
            {
              "version": 1,
              "memories": [
                {
                  "sessionId": "s1",
                  "summary": "summary",
                  "events": [
                    {"id": "e1", "type": "UNKNOWN", "text": "unknown", "evidence": {"quote": "unknown", "startMs": 0}},
                    {"id": "e2", "type": "DECISION", "text": "decision", "evidence": {"quote": "decision", "startMs": 1}}
                  ],
                  "commitments": [
                    {"id": "c1", "text": "no evidence"}
                  ],
                  "inspirations": [
                    {"id": "i1", "text": "idea", "evidence": {"quote": "idea", "startMs": 2}}
                  ]
                }
              ]
            }
        """.trimIndent()

        val decoded = MemoryCodec.decode(raw)

        assertThat(decoded).hasSize(1)
        assertThat(decoded.first().events).hasSize(1)
        assertThat(decoded.first().events.first().type).isEqualTo(MemoryEventType.DECISION)
        assertThat(decoded.first().commitments).isEmpty()
        assertThat(decoded.first().inspirations).hasSize(1)
    }

    @Test
    fun blank_or_malformed_json_is_empty() {
        assertThat(MemoryCodec.decode("")).isEmpty()
        assertThat(MemoryCodec.decode("not json")).isEmpty()
        assertThat(MemoryCodec.decode("""{"version":1}""")).isEmpty()
    }
}

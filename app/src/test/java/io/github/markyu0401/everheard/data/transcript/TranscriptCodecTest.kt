package io.github.markyu0401.everheard.data.transcript

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptCodecTest {

    @Test
    fun round_trips_sessions_and_utterances() {
        val session = TranscriptSession(
            id = "s1",
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_060_000L,
            title = "Design review",
            utterances = listOf(
                TranscriptUtterance(
                    id = "u1",
                    sessionId = "s1",
                    startMs = 0,
                    endMs = 4_000,
                    text = "We should ship the capture core first.",
                    speaker = "me",
                    marked = true
                ),
                TranscriptUtterance(
                    id = "u2",
                    sessionId = "s1",
                    startMs = 4_000,
                    endMs = 9_000,
                    text = "Agreed.",
                    speaker = null
                )
            )
        )

        val decoded = TranscriptCodec.decode(TranscriptCodec.encode(listOf(session)))

        assertThat(decoded).isEqualTo(listOf(session))
    }

    @Test
    fun old_rows_default_to_not_marked() {
        val raw = """{"version":1,"sessions":[{"id":"s","startedAt":1,"utterances":[{"id":"u","sessionId":"s","startMs":0,"endMs":1,"text":"hello"}]}]}"""

        val decoded = TranscriptCodec.decode(raw)

        assertThat(decoded[0].utterances[0].marked).isFalse()
    }

    @Test
    fun malformed_input_returns_empty_list() {
        assertThat(TranscriptCodec.decode("not json")).isEmpty()
        assertThat(TranscriptCodec.decode("")).isEmpty()
        assertThat(TranscriptCodec.decode("{\"version\":1}")).isEmpty()
    }

    @Test
    fun skips_sessions_without_ids_and_empty_utterances() {
        val raw = """
            {"version":1,"sessions":[
              {"id":"","startedAt":1,"utterances":[]},
              {"id":"s2","startedAt":2,"utterances":[{"id":"u","sessionId":"s2","startMs":0,"endMs":1,"text":" "}]}
            ]}
        """.trimIndent()

        val decoded = TranscriptCodec.decode(raw)

        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].id).isEqualTo("s2")
        assertThat(decoded[0].utterances).isEmpty()
    }
}

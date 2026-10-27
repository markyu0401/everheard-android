package io.github.markyu0401.everheard.data.person

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.junit.Test

class PersonBuilderTest {

    @Test
    fun builds_people_only_from_explicit_speaker_labels() {
        val session = TranscriptSession(
            id = "s1",
            startedAt = 1_700_000_000_000L,
            title = "Sync",
            utterances = listOf(
                TranscriptUtterance(
                    id = "u1",
                    sessionId = "s1",
                    startMs = 0L,
                    endMs = 1_000L,
                    text = "I will send the draft.",
                    speaker = "me"
                ),
                TranscriptUtterance(
                    id = "u2",
                    sessionId = "s1",
                    startMs = 1_000L,
                    endMs = 2_000L,
                    text = "Thanks.",
                    speaker = null
                ),
                TranscriptUtterance(
                    id = "u3",
                    sessionId = "s1",
                    startMs = 2_000L,
                    endMs = 3_000L,
                    text = "I will review it.",
                    speaker = "Alex"
                )
            )
        )
        val memory = RuleBasedMemoryAnalyzer(now = { 1_700_000_000_000L }).analyze(session)

        val people = PersonBuilder().build(listOf(session), listOf(memory))

        assertThat(people.map { it.name }).containsExactly("me", "Alex")
        assertThat(people.first { it.name == "me" }.sessionIds).containsExactly("s1")
        assertThat(people.first { it.name == "Alex" }.quotes).containsExactly("I will review it.")
        assertThat(people.first { it.name == "Alex" }.openCommitments).hasSize(1)
    }
}

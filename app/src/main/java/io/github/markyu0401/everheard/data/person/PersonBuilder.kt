package io.github.markyu0401.everheard.data.person

import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession

/**
 * Person profiles come only from explicit speaker labels. Unlabelled segments
 * are not assigned to a person; this avoids inventing attendees.
 */
class PersonBuilder {

    fun build(
        sessions: List<TranscriptSession>,
        memories: List<SessionMemory>
    ): List<PersonProfile> {
        val bySpeaker = mutableMapOf<String, MutablePerson>()
        sessions.forEach { session ->
            session.utterances.forEach { utterance ->
                val speaker = utterance.speaker?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@forEach
                val person = bySpeaker.getOrPut(speaker) { MutablePerson(speaker) }
                person.sessionIds += session.id
                person.lastTalkedAt = maxOf(person.lastTalkedAt, session.startedAt)
                person.quotes += utterance.text
            }
        }

        return bySpeaker.values.map { person ->
            val commitments = memories
                .filter { it.sessionId in person.sessionIds }
                .flatMap { it.commitments }
                .filter { commitment ->
                    commitment.status == Commitment.STATUS_OPEN &&
                        (commitment.evidence.speaker == person.name ||
                            commitment.owner == person.name)
                }
            PersonProfile(
                id = Integer.toHexString(person.name.hashCode()),
                name = person.name,
                sessionIds = person.sessionIds.distinct(),
                lastTalkedAt = person.lastTalkedAt,
                openCommitments = commitments,
                quotes = person.quotes.take(5)
            )
        }.sortedByDescending { it.lastTalkedAt }
    }

    private data class MutablePerson(
        val name: String,
        val sessionIds: MutableSet<String> = linkedSetOf(),
        var lastTalkedAt: Long = 0L,
        val quotes: MutableList<String> = mutableListOf()
    )
}

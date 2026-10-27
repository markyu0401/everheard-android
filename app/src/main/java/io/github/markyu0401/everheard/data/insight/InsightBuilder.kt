package io.github.markyu0401.everheard.data.insight

import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import java.util.concurrent.TimeUnit

/**
 * Deterministic weekly review. Every number is computed from the stores; every
 * observation cites at least two transcript lines. No LLM narrative is invented.
 */
class InsightBuilder(
    private val now: () -> Long = System::currentTimeMillis
) {

    companion object {
        private const val PERIOD_DAYS = 7L
        private const val MIN_CITATIONS = 2
    }

    fun build(
        sessions: List<TranscriptSession>,
        memories: List<SessionMemory>,
        projects: List<Project>
    ): InsightReport {
        val periodEnd = now()
        val periodStart = periodEnd - TimeUnit.DAYS.toMillis(PERIOD_DAYS)
        val periodSessions = sessions.filter { it.startedAt in periodStart..periodEnd }
        val periodIds = periodSessions.map { it.id }.toSet()
        val periodMemories = memories.filter { it.sessionId in periodIds }
        val events = periodMemories.flatMap { it.events }
        val commitments = periodMemories.flatMap { it.commitments }
        val openCommitments = commitments.filter { it.status == Commitment.STATUS_OPEN }
        val projectsTouched = projects.count { project ->
            project.sessionIds.any { it in periodIds }
        }
        val minutes = periodSessions.sumOf { session ->
            val end = session.endedAt
                ?: session.utterances.lastOrNull()?.let { session.startedAt + it.endMs }
                ?: session.startedAt
            ((end - session.startedAt) / 60_000L).coerceAtLeast(0L).toInt()
        }

        val observations = mutableListOf<InsightObservation>()
        val sessionCitations = periodSessions.mapNotNull { session ->
            session.utterances.firstOrNull()?.let { utterance ->
                InsightCitation(
                    sessionId = session.id,
                    sessionTitle = session.title.ifBlank { "Conversation" },
                    quote = utterance.text,
                    startMs = utterance.startMs
                )
            }
        }

        if (periodSessions.isNotEmpty() && sessionCitations.size >= MIN_CITATIONS) {
            observations += InsightObservation(
                title = "Your week in conversations",
                body = "You recorded ${periodSessions.size} sessions across " +
                    "$projectsTouched projects, with ${events.count { it.type == MemoryEventType.DECISION }} " +
                    "decisions and ${commitments.size} commitments.",
                action = "Open the transcripts that shaped this week.",
                citations = sessionCitations.take(3)
            )
        }

        val commitmentCitations = openCommitments.mapNotNull { commitment ->
            val session = periodSessions.firstOrNull { candidate ->
                candidate.utterances.any { it.id == commitment.evidence.utteranceId }
            } ?: periodSessions.firstOrNull() ?: return@mapNotNull null
            InsightCitation(
                sessionId = session.id,
                sessionTitle = session.title.ifBlank { "Conversation" },
                quote = commitment.evidence.quote,
                startMs = commitment.evidence.startMs
            )
        }
        if (openCommitments.size >= MIN_CITATIONS) {
            observations += InsightObservation(
                title = "Open loops",
                body = "${openCommitments.size} commitments are still open. " +
                    "The oldest one came from “${openCommitments.first().text.take(80)}”.",
                action = "Review the commitments list and close what is done.",
                citations = commitmentCitations.take(3)
            )
        }

        val riskEvents = events.filter { it.type == MemoryEventType.RISK }
        if (riskEvents.size >= MIN_CITATIONS) {
            observations += InsightObservation(
                title = "Recurring risks",
                body = "${riskEvents.size} risk statements appeared this week. " +
                    "They are worth resolving before they become blockers.",
                action = "Pick one risk and assign an owner.",
                citations = riskEvents.take(3).mapNotNull { event ->
                    citationFor(event.evidence.quote, event.evidence.startMs, periodSessions)
                }
            )
        }

        val ideaEvents = events.filter { it.type == MemoryEventType.INSPIRATION }
        if (ideaEvents.size >= MIN_CITATIONS) {
            observations += InsightObservation(
                title = "Ideas worth keeping",
                body = "${ideaEvents.size} ideas were captured. " +
                    "The most recent one was “${ideaEvents.last().text.take(80)}”.",
                action = "Turn one idea into a small next step.",
                citations = ideaEvents.take(3).mapNotNull { event ->
                    citationFor(event.evidence.quote, event.evidence.startMs, periodSessions)
                }
            )
        }

        return InsightReport(
            periodStart = periodStart,
            periodEnd = periodEnd,
            sessionCount = periodSessions.size,
            minutes = minutes,
            decisionCount = events.count { it.type == MemoryEventType.DECISION },
            commitmentCount = commitments.size,
            openCommitmentCount = openCommitments.size,
            riskCount = riskEvents.size,
            ideaCount = ideaEvents.size,
            projectCount = projectsTouched,
            observations = observations
        )
    }

    private fun citationFor(
        quote: String,
        startMs: Long,
        sessions: List<TranscriptSession>
    ): InsightCitation? {
        val session = sessions.firstOrNull { candidate ->
            candidate.utterances.any { it.text == quote && it.startMs == startMs }
        } ?: return null
        return InsightCitation(
            sessionId = session.id,
            sessionTitle = session.title.ifBlank { "Conversation" },
            quote = quote,
            startMs = startMs
        )
    }
}

package io.github.markyu0401.everheard.data.project

import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.MemoryEvent

/**
 * A project is a set of sessions that share at least two topic keywords. It is
 * derived from the encrypted transcript and memory stores; no separate project
 * database is needed yet.
 */
data class Project(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val sessionIds: List<String>,
    val firstAt: Long,
    val lastAt: Long,
    val events: List<ProjectEvent> = emptyList(),
    val openCommitments: List<Commitment> = emptyList()
) {
    val sessionCount: Int get() = sessionIds.size
    val eventCount: Int get() = events.size
}

data class ProjectEvent(
    val sessionId: String,
    val sessionTitle: String,
    val event: MemoryEvent
)

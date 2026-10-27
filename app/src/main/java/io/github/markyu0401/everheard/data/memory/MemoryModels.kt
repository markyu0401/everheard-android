package io.github.markyu0401.everheard.data.memory

/**
 * Structured memory extracted from one transcript session. Every item carries
 * an evidence quote; an item without evidence is not stored.
 */
data class SessionMemory(
    val sessionId: String,
    val summary: String,
    val summaryEvidence: MemoryEvidence? = null,
    val events: List<MemoryEvent> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val inspirations: List<Inspiration> = emptyList(),
    val generatedAt: Long = 0L,
    val analyzer: String = RuleBasedMemoryAnalyzer.NAME
)

data class MemoryEvidence(
    val utteranceId: String,
    val quote: String,
    val startMs: Long,
    val speaker: String? = null
)

enum class MemoryEventType {
    PROGRESS,
    DECISION,
    COMMITMENT,
    RISK,
    INSPIRATION
}

data class MemoryEvent(
    val id: String,
    val type: MemoryEventType,
    val text: String,
    val evidence: MemoryEvidence
)

data class Commitment(
    val id: String,
    val text: String,
    val owner: String? = null,
    val dueHint: String? = null,
    val status: String = STATUS_OPEN,
    val evidence: MemoryEvidence
) {
    companion object {
        const val STATUS_OPEN = "open"
    }
}

data class Inspiration(
    val id: String,
    val text: String,
    val evidence: MemoryEvidence
)

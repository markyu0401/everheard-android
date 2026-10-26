package io.github.markyu0401.everheard.data.transcript

/**
 * A transcript session is the unit the UI lists and the MCP server exports.
 * Utterances keep timestamps relative to the session start.
 */
data class TranscriptSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val title: String = "",
    val utterances: List<TranscriptUtterance> = emptyList()
)

data class TranscriptUtterance(
    val id: String,
    val sessionId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speaker: String? = null
)

data class TranscriptHit(
    val sessionId: String,
    val sessionTitle: String,
    val utterance: TranscriptUtterance
)

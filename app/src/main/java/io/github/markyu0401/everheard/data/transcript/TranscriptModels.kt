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
    val utterances: List<TranscriptUtterance> = emptyList(),
    /**
     * True when the voice profile was enrolled and speaker attribution actually
     * ran for this session. Memory extraction uses this to decide whether an
     * utterance must be voiceprint-confirmed ("me") before it can become the
     * user's commitment; without a profile there is nothing to gate on.
     */
    val voiceProfileActive: Boolean = false
)

data class TranscriptUtterance(
    val id: String,
    val sessionId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speaker: String? = null,
    val marked: Boolean = false
)

data class TranscriptHit(
    val sessionId: String,
    val sessionTitle: String,
    val utterance: TranscriptUtterance
)

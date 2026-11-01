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

/**
 * Conversation bounds. [TranscriptSession.startedAt] is when capture resumed,
 * which can be minutes before anyone speaks; the conversation itself starts at
 * the first kept utterance and ends at the last one. Use these for anything a
 * person reads: list clocks, durations, daily grouping and the insight report.
 */
val TranscriptSession.firstSpeechAt: Long
    get() = utterances.firstOrNull()?.let { startedAt + it.startMs } ?: startedAt

val TranscriptSession.lastSpeechAt: Long
    get() = utterances.lastOrNull()?.let { startedAt + it.endMs } ?: (endedAt ?: startedAt)

/**
 * Speech time only. Capture pauses and the silence between segments are
 * excluded, so a session that sat open for three hours but contains ten minutes
 * of speech reports ten minutes.
 */
val TranscriptSession.speechDurationMs: Long
    get() = utterances.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }


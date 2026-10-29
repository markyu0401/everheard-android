package io.github.markyu0401.everheard.asr

import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance

/**
 * A capture session is only persisted once it contains something worth
 * remembering. Without this gate, a fan noise burst at 03:00 becomes a session
 * with the title "." and pollutes Today, Library and Memory.
 */
object SessionQuality {

    /** Two utterances plus eight seconds of speech is the normal threshold. */
    const val MIN_UTTERANCES = 2
    const val MIN_TOTAL_SPEECH_MS = 8_000L

    /** A single, long, substantive sentence is also worth keeping. */
    const val MIN_SINGLE_MS = 5_000L
    const val MIN_SINGLE_CHARS = 12

    fun qualifies(utterances: List<TranscriptUtterance>): Boolean {
        if (utterances.isEmpty()) return false
        val totalSpeech = utterances.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        if (utterances.size >= MIN_UTTERANCES && totalSpeech >= MIN_TOTAL_SPEECH_MS) return true
        if (utterances.size == 1) {
            val only = utterances.first()
            return only.endMs - only.startMs >= MIN_SINGLE_MS &&
                only.text.trim().length >= MIN_SINGLE_CHARS
        }
        return false
    }

    /**
     * Titles used to be the first utterance, which is exactly the row the model
     * hallucinates first ("Yeah.", ".", "B."). Pick the most substantive
     * utterance instead, preferring voiceprint-confirmed speech.
     */
    fun titleFor(utterances: List<TranscriptUtterance>): String {
        val best = utterances
            .filter { it.text.isNotBlank() }
            .maxByOrNull { utterance ->
                utterance.text.count { it.isLetter() } +
                    if (utterance.speaker == "me") 4 else 0
            } ?: return ""
        return best.text.trim().take(40)
    }
}

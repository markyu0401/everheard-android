package io.github.markyu0401.everheard.asr

import kotlin.math.ceil

/**
 * Post-ASR gate. SenseVoice is non-autoregressive and will happily emit "。",
 * "嗯。" or "B." when handed noise; Silero passes fan, breath and TV hiss at the
 * default threshold. Everything that reaches the transcript must survive this
 * filter, otherwise junk becomes memory.
 *
 * The rules are deliberately conservative: dropping a short filler costs
 * nothing, while keeping one turns into a fabricated commitment downstream.
 */
object TranscriptQuality {

    /** Anything shorter than this cannot be a useful sentence. */
    const val MIN_DURATION_MS = 700L

    /** VAD-confirmed speech must occupy at least this share of the segment. */
    const val MIN_SPEECH_RATIO = 0.35f

    /** Below this character rate the "text" is a stray token, not speech. */
    private const val MIN_CHARS_PER_SECOND = 0.5f

    private const val SHORT_TEXT_MS = 3_000L
    private const val SHORT_TEXT_CHARS = 2

    /**
     * Model hallucination vocabulary. Compared after trimming punctuation and
     * lowercasing; only rejected while the segment is short, so a real
     * "Okay, let's start the meeting" survives.
     */
    private val fillers = setOf(
        "嗯", "嗯嗯", "嗯嘿", "嗯呵", "嗯哼", "嘿嘿", "呵", "呵呵",
        "啊", "哦", "呃", "唉", "呀", "诶", "哎", "嗷",
        "yeah", "yep", "okay", "ok", "b", "i", "the", "a", "oh", "um", "uh",
        "hmm", "mm", "huh", "empty", "you"
    )

    fun isAcceptable(text: String, durationMs: Long, speechRatio: Float = 1f): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (durationMs < MIN_DURATION_MS) return false
        if (speechRatio < MIN_SPEECH_RATIO) return false

        val letters = trimmed.count { it.isLetter() }
        if (letters == 0) return false
        // "空。" / "Q空。" / "B。" style fragments.
        if (letters <= SHORT_TEXT_CHARS && durationMs < SHORT_TEXT_MS) return false
        // Two characters stretched over ten seconds is noise, not speech.
        val expected = ceil(durationMs / 1000f * MIN_CHARS_PER_SECOND)
            .toInt()
            .coerceAtLeast(1)
        if (letters < expected) return false

        val normalized = trimmed.lowercase().trim { it in " .。,，!！?？~～…、；;：:" }
        // A pure filler is never worth a row, however long the segment was.
        if (normalized in fillers) return false
        return true
    }
}

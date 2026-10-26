package io.github.markyu0401.everheard.asr

import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance

/**
 * Joins adjacent VAD segments when the pause between them is short. Without
 * this, a poem or a single sentence with a breath pause becomes several rows in
 * the transcript even when the words are correct.
 */
object TranscriptMerger {

    const val DEFAULT_GAP_MS = 1_200L

    fun shouldMerge(
        previous: TranscriptUtterance?,
        nextStartMs: Long,
        gapMs: Long = DEFAULT_GAP_MS
    ): Boolean {
        if (previous == null) return false
        val gap = nextStartMs - previous.endMs
        return gap <= gapMs
    }

    fun merge(previous: TranscriptUtterance, next: TranscriptUtterance): TranscriptUtterance =
        previous.copy(
            endMs = maxOf(previous.endMs, next.endMs),
            text = joinText(previous.text, next.text)
        )

    fun joinText(left: String, right: String): String {
        val a = left.trim()
        val b = right.trim()
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val separator = if (containsCjk(a) || containsCjk(b)) "" else " "
        return a + separator + b
    }

    private fun containsCjk(text: String): Boolean =
        text.any { character -> character in '\u4E00'..'\u9FFF' }
}

package io.github.markyu0401.everheard.speaker

import kotlin.math.sqrt

enum class VoiceIssue {
    TOO_SHORT,
    TOO_LONG,
    TOO_QUIET,
    NOT_ENOUGH_SPEECH
}

data class VoiceAssessment(
    val durationSeconds: Float,
    val rms: Float,
    val speechRatio: Float,
    val issue: VoiceIssue?
) {
    val ok: Boolean get() = issue == null
}

/**
 * Quality gate for an enrollment recording. A clean, close, continuous sample
 * is more important than a long one.
 */
object VoiceEnrollment {

    const val MIN_SECONDS = 5f
    const val MAX_SECONDS = 30f
    const val MIN_RMS = 0.005f
    const val MIN_SPEECH_RATIO = 0.20f

    fun assess(
        pcm16: ShortArray,
        speechSamples: Int,
        sampleRate: Int = 16_000
    ): VoiceAssessment {
        val duration = pcm16.size.toFloat() / sampleRate
        var sumSquares = 0.0
        for (sample in pcm16) {
            val value = sample / 32768.0
            sumSquares += value * value
        }
        val rms = if (pcm16.isEmpty()) 0f else sqrt(sumSquares / pcm16.size).toFloat()
        val ratio = if (pcm16.isEmpty()) 0f else speechSamples.toFloat() / pcm16.size

        val issue = when {
            duration < MIN_SECONDS -> VoiceIssue.TOO_SHORT
            duration > MAX_SECONDS -> VoiceIssue.TOO_LONG
            rms < MIN_RMS -> VoiceIssue.TOO_QUIET
            ratio < MIN_SPEECH_RATIO -> VoiceIssue.NOT_ENOUGH_SPEECH
            else -> null
        }
        return VoiceAssessment(duration, rms, ratio, issue)
    }
}

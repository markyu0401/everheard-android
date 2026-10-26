package io.github.markyu0401.everheard.asr

/**
 * Turns VAD decisions into speech segments. A segment starts on the first
 * speech frame, includes a short tail of silence, and is emitted after
 * [endSilenceMs] of silence or at [maxSegmentMs]. Segments shorter than
 * [minSpeechMs] are dropped as noise.
 */
class SpeechSegmenter(
    private val sampleRate: Int = 16_000,
    private val minSpeechMs: Long = 800,
    private val endSilenceMs: Long = 600,
    private val maxSegmentMs: Long = 30_000
) {

    data class Segment(
        val pcm16: ShortArray,
        val startMs: Long,
        val endMs: Long
    )

    private val maxSamples = (maxSegmentMs * sampleRate / 1000).toInt()
    private val buffer = ShortArray(maxSamples)
    private var size = 0
    private var speechMs = 0L
    private var silenceMs = 0L
    private var active = false
    private var elapsedMs = 0L
    private var segmentStartMs = 0L

    fun accept(frame: ShortArray, isSpeech: Boolean): List<Segment> {
        if (frame.isEmpty()) return emptyList()
        val frameMs = frame.size * 1000L / sampleRate
        val emitted = mutableListOf<Segment>()

        if (isSpeech) {
            if (!active) {
                active = true
                size = 0
                speechMs = 0L
                silenceMs = 0L
                segmentStartMs = elapsedMs
            }
            append(frame)
            speechMs += frameMs
            silenceMs = 0L
        } else if (active) {
            append(frame)
            silenceMs += frameMs
            if (silenceMs >= endSilenceMs) {
                emitIfLongEnough(emitted)
                reset()
            }
        }

        elapsedMs += frameMs

        if (active && size >= maxSamples) {
            emitIfLongEnough(emitted)
            reset()
        }
        return emitted
    }

    fun flush(): Segment? {
        if (!active) return null
        val emitted = mutableListOf<Segment>()
        emitIfLongEnough(emitted)
        reset()
        return emitted.firstOrNull()
    }

    private fun append(frame: ShortArray) {
        val room = maxSamples - size
        if (room <= 0) return
        val count = minOf(room, frame.size)
        frame.copyInto(buffer, size, 0, count)
        size += count
    }

    private fun emitIfLongEnough(emitted: MutableList<Segment>) {
        if (speechMs < minSpeechMs || size == 0) return
        val endMs = elapsedMs + (size * 1000L / sampleRate) - speechMs
        emitted += Segment(
            pcm16 = buffer.copyOf(size),
            startMs = segmentStartMs,
            endMs = maxOf(endMs, segmentStartMs)
        )
    }

    private fun reset() {
        active = false
        size = 0
        speechMs = 0L
        silenceMs = 0L
    }
}

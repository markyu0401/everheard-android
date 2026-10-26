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
    private val maxSegmentMs: Long = 30_000,
    private val preRollMs: Long = 500
) {

    data class Segment(
        val pcm16: ShortArray,
        val startMs: Long,
        val endMs: Long
    )

    private val maxSamples = (maxSegmentMs * sampleRate / 1000).toInt()
    private val preRollSamples = (preRollMs * sampleRate / 1000).toInt()
    private val buffer = ShortArray(maxSamples)
    private val preRoll = ShortArray(preRollSamples)
    private var preRollCount = 0
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

        if (!active) {
            appendPreRoll(frame)
            if (isSpeech) {
                active = true
                size = 0
                speechMs = frameMs
                silenceMs = 0L
                // Keep the frames just before the VAD fired so the first
                // syllable is not clipped. The current frame is already in
                // the pre-roll buffer.
                preRoll.copyInto(buffer, 0, 0, preRollCount)
                size = preRollCount
                segmentStartMs = maxOf(0L, elapsedMs - preRollCount * 1000L / sampleRate)
            }
        } else if (isSpeech) {
            append(frame)
            speechMs += frameMs
            silenceMs = 0L
        } else {
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

    private fun appendPreRoll(frame: ShortArray) {
        if (preRollSamples == 0) return
        if (frame.size >= preRollSamples) {
            frame.copyInto(preRoll, 0, frame.size - preRollSamples, frame.size)
            preRollCount = preRollSamples
            return
        }
        val overflow = preRollCount + frame.size - preRollSamples
        if (overflow > 0) {
            preRoll.copyInto(preRoll, 0, overflow, preRollCount)
            preRollCount -= overflow
        }
        frame.copyInto(preRoll, preRollCount)
        preRollCount += frame.size
    }

    private fun emitIfLongEnough(emitted: MutableList<Segment>) {
        if (speechMs < minSpeechMs || size == 0) return
        emitted += Segment(
            pcm16 = buffer.copyOf(size),
            startMs = segmentStartMs,
            endMs = segmentStartMs + size * 1000L / sampleRate
        )
    }

    private fun reset() {
        active = false
        size = 0
        speechMs = 0L
        silenceMs = 0L
        preRollCount = 0
    }
}

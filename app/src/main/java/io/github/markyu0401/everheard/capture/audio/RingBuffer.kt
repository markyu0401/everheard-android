package io.github.markyu0401.everheard.capture.audio

/**
 * A fixed-size circular buffer of 16-bit PCM samples.
 *
 * The audio thread writes 20 ms frames; the UI thread may snapshot the last
 * N seconds for a "mark that" action. All public methods synchronize on the
 * buffer, which is cheap at 50 writes per second.
 */
class RingBuffer(val capacitySamples: Int) {

    init {
        require(capacitySamples > 0) { "capacitySamples must be positive" }
    }

    private val buffer = ShortArray(capacitySamples)
    private var writeIndex = 0
    private var filled = 0

    @Synchronized
    fun write(frame: ShortArray) {
        if (frame.isEmpty()) return

        if (frame.size >= capacitySamples) {
            frame.copyInto(
                destination = buffer,
                destinationOffset = 0,
                startIndex = frame.size - capacitySamples,
                endIndex = frame.size
            )
            writeIndex = 0
            filled = capacitySamples
            return
        }

        val firstChunk = minOf(frame.size, capacitySamples - writeIndex)
        frame.copyInto(
            destination = buffer,
            destinationOffset = writeIndex,
            startIndex = 0,
            endIndex = firstChunk
        )

        val remaining = frame.size - firstChunk
        if (remaining > 0) {
            frame.copyInto(
                destination = buffer,
                destinationOffset = 0,
                startIndex = firstChunk,
                endIndex = frame.size
            )
        }

        writeIndex = (writeIndex + frame.size) % capacitySamples
        filled = minOf(capacitySamples, filled + frame.size)
    }

    /**
     * Returns the newest [seconds] of audio in chronological order. If fewer
     * samples are buffered, returns everything buffered.
     */
    @Synchronized
    fun snapshotLast(seconds: Int, sampleRate: Int = 16_000): ShortArray {
        require(seconds >= 0) { "seconds must not be negative" }
        require(sampleRate > 0) { "sampleRate must be positive" }

        val requested = minOf(filled, seconds * sampleRate)
        if (requested == 0) return ShortArray(0)

        val start = (writeIndex - requested + capacitySamples) % capacitySamples
        val result = ShortArray(requested)
        val firstChunk = minOf(requested, capacitySamples - start)
        buffer.copyInto(
            destination = result,
            destinationOffset = 0,
            startIndex = start,
            endIndex = start + firstChunk
        )
        if (requested > firstChunk) {
            buffer.copyInto(
                destination = result,
                destinationOffset = firstChunk,
                startIndex = 0,
                endIndex = requested - firstChunk
            )
        }
        return result
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        filled = 0
    }

    @Synchronized
    fun size(): Int = filled
}

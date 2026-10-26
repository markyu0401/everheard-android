package io.github.markyu0401.everheard.capture.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RingBufferTest {

    @Test
    fun snapshot_returns_last_second_in_order() {
        val ring = RingBuffer(capacitySamples = 16_000 * 3)
        ring.write(ShortArray(16_000) { 1 })
        ring.write(ShortArray(16_000) { 2 })
        ring.write(ShortArray(16_000) { 3 })

        val last = ring.snapshotLast(seconds = 1)

        assertThat(last.size).isEqualTo(16_000)
        assertThat(last.all { it == 3.toShort() }).isTrue()
    }

    @Test
    fun wrap_around_keeps_the_newest_samples() {
        val ring = RingBuffer(capacitySamples = 4)
        ring.write(shortArrayOf(1, 2, 3, 4))
        ring.write(shortArrayOf(5, 6))

        val last = ring.snapshotLast(seconds = 4, sampleRate = 1)

        assertThat(last.toList()).containsExactly(3.toShort(), 4.toShort(), 5.toShort(), 6.toShort()).inOrder()
    }

    @Test
    fun oversized_frame_keeps_only_the_tail() {
        val ring = RingBuffer(capacitySamples = 3)
        ring.write(shortArrayOf(1, 2, 3, 4, 5))

        assertThat(ring.snapshotLast(seconds = 3, sampleRate = 1).toList())
            .containsExactly(3.toShort(), 4.toShort(), 5.toShort()).inOrder()
    }

    @Test
    fun empty_buffer_returns_empty_snapshot() {
        val ring = RingBuffer(capacitySamples = 10)

        assertThat(ring.snapshotLast(seconds = 1).size).isEqualTo(0)
        assertThat(ring.size()).isEqualTo(0)
    }

    @Test
    fun clear_drops_everything() {
        val ring = RingBuffer(capacitySamples = 10)
        ring.write(shortArrayOf(1, 2, 3))
        ring.clear()

        assertThat(ring.size()).isEqualTo(0)
        assertThat(ring.snapshotLast(seconds = 1).size).isEqualTo(0)
    }
}

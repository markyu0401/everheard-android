package io.github.markyu0401.everheard.capture.audio

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavWriterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writes_a_canonical_16khz_mono_header() {
        val file = File(temporaryFolder.root, "spike.wav")
        val pcm = ShortArray(1_000) { it.toShort() }

        WavWriter.write(file, pcm)

        assertThat(file.length()).isEqualTo(WavWriter.HEADER_SIZE + pcm.size * 2L)

        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(String(bytes, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(header.getInt(4)).isEqualTo(36 + pcm.size * 2)
        assertThat(String(bytes, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(String(bytes, 12, 4, Charsets.US_ASCII)).isEqualTo("fmt ")
        assertThat(header.getInt(16)).isEqualTo(16)
        assertThat(header.getShort(20).toInt()).isEqualTo(1)
        assertThat(header.getShort(22).toInt()).isEqualTo(1)
        assertThat(header.getInt(24)).isEqualTo(16_000)
        assertThat(header.getInt(28)).isEqualTo(32_000)
        assertThat(header.getShort(32).toInt()).isEqualTo(2)
        assertThat(header.getShort(34).toInt()).isEqualTo(16)
        assertThat(String(bytes, 36, 4, Charsets.US_ASCII)).isEqualTo("data")
        assertThat(header.getInt(40)).isEqualTo(pcm.size * 2)
        assertThat(header.getShort(44).toInt()).isEqualTo(0)
        assertThat(header.getShort(46).toInt()).isEqualTo(1)
    }

    @Test
    fun creates_parent_directories() {
        val file = File(temporaryFolder.root, "nested/spike.wav")

        WavWriter.write(file, shortArrayOf(1, 2, 3))

        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isEqualTo(WavWriter.HEADER_SIZE + 6L)
    }
}

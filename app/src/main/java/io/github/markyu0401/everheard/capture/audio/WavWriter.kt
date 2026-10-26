package io.github.markyu0401.everheard.capture.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a canonical 16-bit PCM WAV file. Used by the M0 spike and, later, by
 * the optional retained-audio path (before it is deleted after transcription).
 */
object WavWriter {

    const val HEADER_SIZE = 44

    fun write(
        file: File,
        pcm: ShortArray,
        sampleRate: Int = 16_000,
        channels: Int = 1
    ) {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels in 1..2) { "channels must be 1 or 2" }

        val dataSize = pcm.size * 2
        val blockAlign = channels * 2
        val byteRate = sampleRate * blockAlign

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                 // PCM fmt chunk size
            putShort(1)                // audio format: PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16)               // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }

        file.parentFile?.mkdirs()

        FileOutputStream(file).use { fileOut ->
            BufferedOutputStream(fileOut, 32 * 1024).use { out ->
                out.write(header.array())

                val chunk = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
                var index = 0
                while (index < pcm.size) {
                    chunk.clear()
                    val count = minOf(pcm.size - index, chunk.capacity() / 2)
                    for (offset in 0 until count) {
                        chunk.putShort(pcm[index + offset])
                    }
                    out.write(chunk.array(), 0, count * 2)
                    index += count
                }
            }
        }
    }
}

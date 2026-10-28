package io.github.markyu0401.everheard.capture.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * 16 kHz mono PCM16 capture from the phone microphone.
 *
 * VOICE_RECOGNITION is deliberate: MIC applies AGC and noise suppression that
 * degrade ASR. Frames are exactly [frameSamples] long; a short read is filled
 * before the callback fires.
 */
class AudioRecordSource(
    private val sampleRate: Int = 16_000,
    private val frameSamples: Int = 320
) {

    private companion object {
        const val TAG = "AudioRecordSource"
    }

    private var audioRecord: AudioRecord? = null
    private var readThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var sessionId: Int = 0
        private set

    val isRecording: Boolean
        get() = running

    /**
     * True when the framework is delivering silence to this client because a
     * higher-priority app took the microphone. This is the authoritative
     * own-client signal; it does not depend on session-id matching.
     */
    val isSilenced: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            val record = audioRecord ?: return false
            return runCatching {
                record.activeRecordingConfiguration?.isClientSilenced == true
            }.getOrDefault(false)
        }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onFrame: (ShortArray) -> Unit) {
        check(!running) { "AudioRecordSource is already recording" }
        require(frameSamples > 0) { "frameSamples must be positive" }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferSize > 0) { "AudioRecord does not support 16 kHz mono PCM16" }

        val bufferSizeBytes = maxOf(minBufferSize, frameSamples * 2 * 8)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes
        )

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }

        audioRecord = record
        running = true
        // AudioRecord assigns its session id during construction on most
        // devices, but some OEMs only expose it after startRecording(). Read it
        // on both sides so the recording monitor can recognize our own client.
        sessionId = record.audioSessionId
        Log.i(TAG, "start before sessionId=$sessionId source=${hashCode()}")
        record.startRecording()
        sessionId = record.audioSessionId
        Log.i(TAG, "start after sessionId=$sessionId source=${hashCode()}")

        readThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val frame = ShortArray(frameSamples)

            while (running) {
                var filled = 0
                while (running && filled < frame.size) {
                    val read = record.read(frame, filled, frame.size - filled)
                    if (read > 0) {
                        filled += read
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord.read failed: $read")
                        running = false
                        break
                    }
                }
                if (filled == frame.size) {
                    onFrame(frame.copyOf())
                }
            }
        }, "everheard-audio").also { it.start() }
    }

    fun stop() {
        Log.i(TAG, "stop source=${hashCode()} sessionId=$sessionId running=$running")
        running = false
        sessionId = 0

        audioRecord?.let { record ->
            try {
                record.stop()
            } catch (error: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop failed", error)
            }
            record.release()
        }
        audioRecord = null

        readThread?.join(500)
        readThread = null
    }
}

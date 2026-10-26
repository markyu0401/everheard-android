package io.github.markyu0401.everheard.asr

import android.content.Context
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns one capture session: creates the transcript session, lazily loads the
 * on-device ASR model, transcribes speech segments in order, and appends the
 * text to the encrypted store. Audio is never persisted.
 *
 * Segments go through an unlimited channel so the audio thread never blocks.
 * [stop] closes the channel; the consumer drains queued segments, writes the
 * session end time, and then releases the scope.
 */
class CaptureTranscriber(
    context: Context,
    private val store: TranscriptStore,
    private val sessionId: String = UUID.randomUUID().toString(),
    private val startedAt: Long = System.currentTimeMillis(),
    private val asrFactory: () -> AsrEngine = { SherpaAsr(context.applicationContext.assets) }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<SpeechSegmenter.Segment>(Channel.UNLIMITED)
    private val engineMutex = Mutex()
    private var engine: AsrEngine? = null
    private var consumerJob: Job? = null
    private var stopped = false

    fun start() {
        if (consumerJob != null) return
        consumerJob = scope.launch {
            var sessionCreated = false
            for (segment in queue) {
                if (segment.pcm16.isEmpty()) continue
                try {
                    val text = engineMutex.withLock {
                        val asr = engine ?: asrFactory().also { engine = it }
                        asr.transcribe(AsrRequest(pcm16 = segment.pcm16)).text.trim()
                    }
                    if (text.isNotEmpty()) {
                        if (!sessionCreated) {
                            store.createSession(
                                TranscriptSession(
                                    id = sessionId,
                                    startedAt = startedAt,
                                    title = ""
                                )
                            )
                            sessionCreated = true
                        }
                        store.appendUtterance(
                            sessionId,
                            TranscriptUtterance(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                startMs = segment.startMs,
                                endMs = segment.endMs,
                                text = text
                            )
                        )
                    }
                } catch (_: Exception) {
                    // A missing model or a native failure must not kill capture.
                }
            }
            if (sessionCreated) {
                val existing = store.session(sessionId)
                if (existing != null) {
                    store.createSession(existing.copy(endedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    fun submit(segment: SpeechSegmenter.Segment) {
        if (stopped || segment.pcm16.isEmpty()) return
        queue.trySend(segment)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        queue.close()
        val job = consumerJob
        scope.launch {
            job?.join()
            scope.cancel()
        }
    }
}

package io.github.markyu0401.everheard.asr

import android.content.Context
import android.util.Log
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import io.github.markyu0401.everheard.speaker.SherpaSpeakerEmbedder
import io.github.markyu0401.everheard.speaker.SpeakerEmbedder
import io.github.markyu0401.everheard.speaker.VoiceMatcher
import io.github.markyu0401.everheard.speaker.VoiceProfile
import io.github.markyu0401.everheard.speaker.VoiceProfileStore
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
    private val appContext: Context = context.applicationContext,
    private val asrFactory: () -> AsrEngine = {
        try {
            SenseVoiceAsr(appContext.assets)
        } catch (error: Throwable) {
            Log.e(TAG, "SenseVoice unavailable; falling back to streaming ASR", error)
            SherpaAsr(appContext.assets)
        }
    },
    private val voiceProfileStore: VoiceProfileStore? = null,
    private val speakerFactory: () -> SpeakerEmbedder = {
        SherpaSpeakerEmbedder(appContext.assets)
    }
) {

    private companion object {
        const val TAG = "CaptureTranscriber"
    }

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
            var lastUtterance: TranscriptUtterance? = null
            var speakerInitialized = false
            var voiceProfile: VoiceProfile? = null
            var speakerEmbedder: SpeakerEmbedder? = null
            for (segment in queue) {
                if (segment.pcm16.isEmpty()) continue
                try {
                    val text = engineMutex.withLock {
                        val asr = engine ?: asrFactory().also {
                            engine = it
                            Log.i(TAG, "ASR model loaded: ${it.id}")
                        }
                        asr.transcribe(AsrRequest(pcm16 = segment.pcm16)).text.trim()
                    }
                    Log.i(
                        TAG,
                        "segment ${segment.startMs}-${segment.endMs} " +
                            "samples=${segment.pcm16.size} textLength=${text.length}"
                    )
                    if (text.isNotEmpty()) {
                        if (!speakerInitialized) {
                            speakerInitialized = true
                            voiceProfile = voiceProfileStore?.load()
                            if (voiceProfile != null) {
                                try {
                                    speakerEmbedder = speakerFactory()
                                    Log.i(TAG, "speaker model loaded: ${speakerEmbedder?.id}")
                                } catch (error: Throwable) {
                                    Log.e(TAG, "Speaker model unavailable", error)
                                }
                            }
                        }
                        val speaker = if (
                            voiceProfile != null &&
                            segment.pcm16.size >= VoiceMatcher.MIN_SPEAKER_SAMPLES &&
                            speakerEmbedder != null
                        ) {
                            try {
                                val embedding = speakerEmbedder!!.embed(segment.pcm16)
                                if (VoiceMatcher.isMe(embedding, voiceProfile!!)) "me" else null
                            } catch (error: Throwable) {
                                Log.e(TAG, "Speaker attribution failed", error)
                                null
                            }
                        } else {
                            null
                        }
                        if (!sessionCreated) {
                            store.createSession(
                                TranscriptSession(
                                    id = sessionId,
                                    startedAt = startedAt,
                                    title = text.take(40)
                                )
                            )
                            sessionCreated = true
                        }
                        val utterance = TranscriptUtterance(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            startMs = segment.startMs,
                            endMs = segment.endMs,
                            text = text,
                            speaker = speaker
                        )
                        val previous = lastUtterance
                        if (previous != null &&
                            TranscriptMerger.shouldMerge(
                                previous,
                                segment.startMs,
                                nextSpeaker = speaker
                            )
                        ) {
                            val merged = TranscriptMerger.merge(previous, utterance)
                            val existing = store.session(sessionId)
                            if (existing != null) {
                                store.createSession(
                                    existing.copy(
                                        utterances = existing.utterances.dropLast(1) + merged
                                    )
                                )
                                lastUtterance = merged
                                Log.i(TAG, "merged segment into previous utterance")
                                continue
                            }
                        }
                        store.appendUtterance(sessionId, utterance)
                        lastUtterance = utterance
                    }
                } catch (error: Exception) {
                    // A missing model or a native failure must not kill capture,
                    // but it must be visible in logcat while diagnosing.
                    Log.e(TAG, "Segment transcription failed", error)
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

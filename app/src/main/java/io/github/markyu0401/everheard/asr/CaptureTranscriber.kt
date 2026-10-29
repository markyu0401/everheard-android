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
 * Three gates sit between ASR and storage, because raw output is not memory:
 * 1. [TranscriptQuality] drops hallucinated fragments ("嗯。", ".", "B.").
 * 2. [TranscriptMerger] joins a short pause but never past [TranscriptMerger.MAX_MERGED_MS].
 * 3. [SessionQuality] holds utterances in memory until there is enough speech
 *    to be worth a session, so a noise burst does not create one.
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
            var speakerInitialized = false
            var attributionActive = false
            var voiceProfile: VoiceProfile? = null
            var speakerEmbedder: SpeakerEmbedder? = null
            var lastUtterance: TranscriptUtterance? = null
            val pending = mutableListOf<TranscriptUtterance>()

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
                    val durationMs = segment.endMs - segment.startMs
                    Log.i(
                        TAG,
                        "segment ${segment.startMs}-${segment.endMs} " +
                            "samples=${segment.pcm16.size} textLength=${text.length} " +
                            "speechRatio=${"%.2f".format(segment.speechRatio)}"
                    )
                    if (!TranscriptQuality.isAcceptable(text, durationMs, segment.speechRatio)) {
                        Log.i(TAG, "segment rejected by quality gate (hallucination or noise)")
                        continue
                    }

                    if (!speakerInitialized) {
                        speakerInitialized = true
                        voiceProfile = voiceProfileStore?.load()
                        if (voiceProfile != null) {
                            try {
                                speakerEmbedder = speakerFactory()
                                attributionActive = true
                                Log.i(TAG, "speaker model loaded: ${speakerEmbedder?.id}")
                            } catch (error: Throwable) {
                                Log.e(TAG, "Speaker model unavailable", error)
                            }
                        }
                    }

                    val speaker = if (
                        attributionActive &&
                        segment.pcm16.size >= VoiceMatcher.MIN_SPEAKER_SAMPLES &&
                        speakerEmbedder != null
                    ) {
                        try {
                            val embedding = speakerEmbedder!!.embed(segment.pcm16)
                            val score = VoiceMatcher.bestSimilarity(embedding, voiceProfile!!)
                            val label = VoiceMatcher.classify(embedding, voiceProfile!!)
                            Log.i(
                                TAG,
                                "speaker score=${"%.3f".format(score)} " +
                                    "label=${label ?: "unknown"}"
                            )
                            label
                        } catch (error: Throwable) {
                            Log.e(TAG, "Speaker attribution failed", error)
                            null
                        }
                    } else {
                        null
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
                            utterance.startMs,
                            utterance.endMs,
                            nextSpeaker = speaker
                        )
                    ) {
                        val merged = TranscriptMerger.merge(previous, utterance)
                        if (sessionCreated) {
                            val existing = store.session(sessionId)
                            if (existing != null) {
                                store.createSession(
                                    existing.copy(
                                        utterances = existing.utterances.dropLast(1) + merged
                                    )
                                )
                            }
                        } else if (pending.isNotEmpty()) {
                            pending[pending.lastIndex] = merged
                        }
                        lastUtterance = merged
                        Log.i(TAG, "merged segment into previous utterance")
                        continue
                    }

                    if (!sessionCreated) {
                        pending += utterance
                        lastUtterance = utterance
                        if (SessionQuality.qualifies(pending)) {
                            store.createSession(
                                TranscriptSession(
                                    id = sessionId,
                                    startedAt = startedAt,
                                    title = SessionQuality.titleFor(pending),
                                    utterances = pending.toList(),
                                    voiceProfileActive = attributionActive
                                )
                            )
                            sessionCreated = true
                            Log.i(TAG, "session created with ${pending.size} utterances")
                        }
                    } else {
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
            } else {
                Log.i(TAG, "session discarded: ${pending.size} pending utterances")
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

package io.github.markyu0401.everheard.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureTranscriberTest {

    private class FakeAsr(private val text: String) : AsrEngine {
        override val id: String = "fake"
        override suspend fun transcribe(request: AsrRequest): AsrResult =
            AsrResult(text = text, segments = emptyList(), model = id, latencyMs = 0L)
    }

    private class SequenceAsr(private val texts: List<String>) : AsrEngine {
        override val id: String = "sequence"
        private var index = 0

        override suspend fun transcribe(request: AsrRequest): AsrResult {
            val text = texts.getOrElse(index) { "" }
            index += 1
            return AsrResult(text = text, segments = emptyList(), model = id, latencyMs = 0L)
        }
    }

    @Test
    fun merges_adjacent_segments_with_a_short_pause() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "transcriber-test-${System.nanoTime()}.bin")
            val store = EncryptedTranscriptStore(file)
            val transcriber = CaptureTranscriber(
                context = context,
                store = store,
                asrFactory = { SequenceAsr(listOf("春眠不觉晓，", "处处闻啼鸟。")) }
            )

            transcriber.start()
            transcriber.submit(
                SpeechSegmenter.Segment(
                    pcm16 = ShortArray(1_600) { 1_000 },
                    startMs = 0,
                    endMs = 2_000
                )
            )
            transcriber.submit(
                SpeechSegmenter.Segment(
                    pcm16 = ShortArray(1_600) { 1_000 },
                    startMs = 2_800,
                    endMs = 4_500
                )
            )
            delay(600)
            transcriber.stop()
            delay(600)

            val sessions = store.sessions()
            assertThat(sessions).hasSize(1)
            assertThat(sessions[0].utterances).hasSize(1)
            assertThat(sessions[0].utterances[0].text).isEqualTo("春眠不觉晓，处处闻啼鸟。")
            assertThat(sessions[0].utterances[0].endMs).isEqualTo(4_500)
            file.delete()
        }
    }

    @Test
    fun does_not_merge_segments_with_a_long_pause() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "transcriber-test-${System.nanoTime()}.bin")
            val store = EncryptedTranscriptStore(file)
            val transcriber = CaptureTranscriber(
                context = context,
                store = store,
                asrFactory = { SequenceAsr(listOf("First sentence.", "Second sentence.")) }
            )

            transcriber.start()
            transcriber.submit(
                SpeechSegmenter.Segment(
                    pcm16 = ShortArray(1_600) { 1_000 },
                    startMs = 0,
                    endMs = 2_000
                )
            )
            transcriber.submit(
                SpeechSegmenter.Segment(
                    pcm16 = ShortArray(1_600) { 1_000 },
                    startMs = 4_000,
                    endMs = 5_000
                )
            )
            delay(600)
            transcriber.stop()
            delay(600)

            val sessions = store.sessions()
            assertThat(sessions).hasSize(1)
            assertThat(sessions[0].utterances).hasSize(2)
            file.delete()
        }
    }

    @Test
    fun does_not_create_an_empty_session() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "transcriber-test-${System.nanoTime()}.bin")
            val store = EncryptedTranscriptStore(file)
            val transcriber = CaptureTranscriber(context = context, store = store)

            transcriber.start()
            delay(200)
            transcriber.stop()
            delay(300)

            assertThat(store.sessions()).isEmpty()
            file.delete()
        }
    }

    @Test
    fun creates_a_session_and_utterance_from_a_segment() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "transcriber-test-${System.nanoTime()}.bin")
            val store = EncryptedTranscriptStore(file)
            val transcriber = CaptureTranscriber(
                context = context,
                store = store,
                asrFactory = { FakeAsr("Send the draft by Friday.") }
            )

            transcriber.start()
            transcriber.submit(
                SpeechSegmenter.Segment(
                    pcm16 = ShortArray(1_600) { 1_000 },
                    startMs = 0,
                    endMs = 100
                )
            )
            delay(400)
            transcriber.stop()
            delay(400)

            val sessions = store.sessions()
            assertThat(sessions).hasSize(1)
            assertThat(sessions[0].endedAt).isNotNull()
            assertThat(sessions[0].utterances).hasSize(1)
            assertThat(sessions[0].utterances[0].text).isEqualTo("Send the draft by Friday.")
            file.delete()
        }
    }
}

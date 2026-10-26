package io.github.markyu0401.everheard.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaAsrTest {

    @Test
    fun model_loads_when_present_and_handles_silence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelPresent = try {
            context.assets.open("models/asr-small/encoder.int8.onnx").use { }
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue("ASR model not fetched; run scripts/fetch_models.sh", modelPresent)

        val asr = SherpaAsr(context.assets)
        // Silence is a valid input; it must not crash the recognizer.
        val result = kotlinx.coroutines.runBlocking {
            asr.transcribe(AsrRequest(pcm16 = ShortArray(16_000)))
        }
        assert(result.model == SherpaAsr.MODEL_ID)
    }
}

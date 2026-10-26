package io.github.markyu0401.everheard.speaker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaSpeakerEmbedderTest {

    @Test
    fun produces_a_non_empty_embedding_when_model_is_present() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelPresent = try {
            context.assets.open("models/speaker/campplus_zh_en.onnx").use { }
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue("Speaker model not fetched; run scripts/fetch_models.sh", modelPresent)

        val embedder = SherpaSpeakerEmbedder(context.assets)
        val embedding = runBlocking {
            embedder.embed(ShortArray(16_000))
        }

        assertTrue(embedding.isNotEmpty())
        assertTrue(embedding.size == embedder.dimension())
    }
}

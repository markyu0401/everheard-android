package io.github.markyu0401.everheard.speaker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceProfileStoreTest {

    @Test
    fun saves_encrypts_loads_and_clears_a_profile() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "voice-test-${System.nanoTime()}.bin")
            val store = VoiceProfileStore(file)
            val embedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
            val alternate = floatArrayOf(0.4f, 0.3f, 0.2f, 0.1f)

            val saved = store.save(
                embeddings = listOf(embedding, alternate),
                samples = 80_000
            )

            assertThat(file.exists()).isTrue()
            assertThat(file.readBytes().toString(Charsets.UTF_8)).doesNotContain("embedding")
            assertThat(saved.samples).isEqualTo(80_000)
            assertThat(saved.alternates).hasSize(1)

            val reopened = VoiceProfileStore(file)
            val loaded = reopened.load()
            assertThat(loaded).isNotNull()
            assertThat(loaded!!.embedding.size).isEqualTo(4)
            assertThat(loaded.alternates).hasSize(1)
            assertThat(loaded.alternates[0].size).isEqualTo(4)
            assertThat(loaded.samples).isEqualTo(80_000)

            reopened.clear()
            assertThat(reopened.load()).isNull()
            assertThat(file.exists()).isFalse()
        }
    }

    @Test
    fun a_missing_file_loads_as_null() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = VoiceProfileStore(File(context.filesDir, "missing-${System.nanoTime()}.bin"))
            assertThat(store.load()).isNull()
        }
    }
}

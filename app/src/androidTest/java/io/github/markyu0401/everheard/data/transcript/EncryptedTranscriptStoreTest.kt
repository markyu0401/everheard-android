package io.github.markyu0401.everheard.data.transcript

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedTranscriptStoreTest {

    @Test
    fun encrypts_on_disk_and_round_trips_through_a_new_instance() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "transcript-test-${System.nanoTime()}.bin")
            val store = EncryptedTranscriptStore(file)
            val session = TranscriptSession(
                id = "s1",
                startedAt = 1_700_000_000_000L,
                title = "Weekly sync",
                utterances = listOf(
                    TranscriptUtterance(
                        id = "u1",
                        sessionId = "s1",
                        startMs = 0,
                        endMs = 2_000,
                        text = "Send the draft by Friday."
                    )
                )
            )

            store.createSession(session)

            assertThat(file.exists()).isTrue()
            assertThat(file.readBytes().toString(Charsets.UTF_8)).doesNotContain("Send the draft")
            assertThat(store.session("s1")).isEqualTo(session)

            val reopened = EncryptedTranscriptStore(file)
            assertThat(reopened.session("s1")).isEqualTo(session)
            assertThat(reopened.search("friday")).hasSize(1)

            file.delete()
        }
    }
}

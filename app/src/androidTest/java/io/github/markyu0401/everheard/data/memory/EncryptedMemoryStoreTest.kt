package io.github.markyu0401.everheard.data.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedMemoryStoreTest {

    @Test
    fun encrypts_on_disk_and_round_trips_through_a_new_instance() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "memory-test-${System.nanoTime()}.bin")
            val store = EncryptedMemoryStore(file)
            val memory = SessionMemory(
                sessionId = "s1",
                summary = "Send the draft by Friday.",
                summaryEvidence = MemoryEvidence("u1", "Send the draft by Friday.", 0L, "me"),
                events = listOf(
                    MemoryEvent(
                        id = "e1",
                        type = MemoryEventType.COMMITMENT,
                        text = "Send the draft by Friday.",
                        evidence = MemoryEvidence("u1", "Send the draft by Friday.", 0L, "me")
                    )
                ),
                commitments = listOf(
                    Commitment(
                        id = "c1",
                        text = "Send the draft by Friday.",
                        owner = "me",
                        dueHint = "Friday",
                        evidence = MemoryEvidence("u1", "Send the draft by Friday.", 0L, "me")
                    )
                ),
                generatedAt = 1_700_000_000_000L
            )

            store.save(memory)

            assertThat(file.exists()).isTrue()
            assertThat(file.readBytes().toString(Charsets.UTF_8)).doesNotContain("Send the draft")
            assertThat(store.get("s1")).isEqualTo(memory)

            val reopened = EncryptedMemoryStore(file)
            assertThat(reopened.get("s1")).isEqualTo(memory)

            file.delete()
        }
    }

    @Test
    fun deletes_one_session_and_clears_all_memory() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "memory-test-${System.nanoTime()}.bin")
            val store = EncryptedMemoryStore(file)
            store.save(SessionMemory(sessionId = "s1", summary = "one"))
            store.save(SessionMemory(sessionId = "s2", summary = "two"))
            assertThat(store.all()).hasSize(2)

            store.delete("s1")
            assertThat(store.get("s1")).isNull()
            assertThat(store.get("s2")).isNotNull()

            store.clear()
            assertThat(store.all()).isEmpty()
            assertThat(file.exists()).isFalse()
        }
    }
}

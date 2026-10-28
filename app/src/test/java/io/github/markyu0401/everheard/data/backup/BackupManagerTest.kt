package io.github.markyu0401.everheard.data.backup

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.capture.schedule.ScheduleMode
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.data.memory.MemoryStore
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptHit
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = NasBackupConfig(
        serverUrl = "https://nas.example:5006",
        username = "user",
        password = "pass",
        folder = "/home/EverheardBackups",
        passphrase = "test-passphrase"
    )

    private val session = TranscriptSession(
        id = "s1",
        startedAt = 1_000L,
        endedAt = 2_000L,
        title = "组会",
        utterances = listOf(
            TranscriptUtterance(
                id = "u1",
                sessionId = "s1",
                startMs = 0,
                endMs = 1_000,
                text = "下午三点开会",
                speaker = "me"
            )
        )
    )

    private val otherSession = session.copy(id = "s2", title = "另一段")

    private val memory = SessionMemory(sessionId = "s1", summary = "摘要")
    private val otherMemory = SessionMemory(sessionId = "s2", summary = "另一段摘要")

    private val rule = ScheduleRule(
        id = 1,
        startMinute = 23 * 60 + 30,
        endMinute = 7 * 60,
        mode = ScheduleMode.BLOCK
    )

    private val otherRule = ScheduleRule(
        id = 2,
        startMinute = 12 * 60,
        endMinute = 13 * 60,
        mode = ScheduleMode.ALLOW
    )

    private inner class Harness(private val config: NasBackupConfig = this@BackupManagerTest.config) {
        val transcripts = FakeTranscripts()
        val memories = FakeMemory()
        val gateway = FakeGateway()
        val store = NasConfigStore(File(temp.root, "nas.bin"), PlainCipher())
        var rules: List<ScheduleRule> = listOf(rule)
        var excluded: Set<String> = setOf("com.google.android.youtube")
        var savedRules: List<ScheduleRule> = emptyList()
        var savedExcluded: Set<String> = emptySet()

        val manager = BackupManager(
            configStore = store,
            transcriptStore = transcripts,
            memoryStore = memories,
            loadSchedule = { rules },
            saveSchedule = { savedRules = it },
            loadExcluded = { excluded },
            saveExcluded = { savedExcluded = it },
            clientFactory = { gateway }
        )

        suspend fun saveConfig() = store.save(config)
    }

    @Test
    fun backup_uploads_an_encrypted_payload_and_updates_the_marker() = runTest {
        val harness = Harness()
        harness.saveConfig()
        harness.transcripts.data = listOf(session)
        harness.memories.data = listOf(memory)

        val result = harness.manager.backupNow()

        assertThat(result).isInstanceOf(BackupResult.Success::class.java)
        val success = result as BackupResult.Success
        assertThat(success.sessions).isEqualTo(1)
        assertThat(success.memories).isEqualTo(1)
        assertThat(harness.gateway.folderEnsured).isTrue()
        val bytes = harness.gateway.files[success.fileName]
        assertThat(bytes).isNotNull()
        val payload = BackupCodec.decodePayload(
            BackupCodec.decrypt(bytes!!, config.passphrase)
        )
        assertThat(payload.transcripts).hasSize(1)
        assertThat(payload.memories).hasSize(1)
        assertThat(payload.scheduleRules).hasSize(1)
        assertThat(payload.excludedApps).containsExactly("com.google.android.youtube")

        val saved = harness.store.reload()
        assertThat(saved.lastBackupAt).isGreaterThan(0L)
        assertThat(saved.lastBackupName).isEqualTo(success.fileName)
    }

    @Test
    fun restore_replaces_local_data_after_a_successful_decrypt() = runTest {
        val harness = Harness()
        harness.saveConfig()
        harness.transcripts.data = listOf(session)
        val payload = BackupPayload(
            createdAt = 1L,
            transcripts = listOf(otherSession),
            memories = listOf(otherMemory),
            scheduleRules = listOf(otherRule),
            excludedApps = listOf("com.other")
        )
        val name = "everheard-20261028-020000.ehb"
        harness.gateway.files[name] = BackupCodec.encrypt(
            BackupCodec.encodePayload(payload),
            config.passphrase
        )

        val result = harness.manager.restore(WebDavEntry(name, 100L, 0L))

        assertThat(result).isInstanceOf(RestoreResult.Success::class.java)
        assertThat(harness.transcripts.data).isEqualTo(payload.transcripts)
        assertThat(harness.memories.data).isEqualTo(payload.memories)
        assertThat(harness.savedRules).isEqualTo(payload.scheduleRules)
        assertThat(harness.savedExcluded).isEqualTo(setOf("com.other"))
    }

    @Test
    fun a_wrong_passphrase_leaves_local_data_untouched() = runTest {
        val harness = Harness(config.copy(passphrase = "wrong"))
        harness.saveConfig()
        harness.transcripts.data = listOf(session)
        val payload = BackupPayload(createdAt = 1L, transcripts = listOf(otherSession))
        val name = "everheard-20261028-030000.ehb"
        harness.gateway.files[name] = BackupCodec.encrypt(
            BackupCodec.encodePayload(payload),
            "right"
        )

        val result = harness.manager.restore(WebDavEntry(name, 100L, 0L))

        assertThat(result).isInstanceOf(RestoreResult.Failure::class.java)
        assertThat(harness.transcripts.data).isEqualTo(listOf(session))
        assertThat(harness.savedRules).isEmpty()
    }

    @Test
    fun test_connection_reports_authentication_failure() = runTest {
        val harness = Harness()
        harness.saveConfig()
        harness.gateway.testError = WebDavException(401, "Unauthorized")

        val result = harness.manager.testConnection()

        assertThat(result).isInstanceOf(ConnectionResult.Failure::class.java)
        assertThat((result as ConnectionResult.Failure).message)
            .contains("Authentication failed")
    }

    @Test
    fun list_backups_returns_remote_entries() = runTest {
        val harness = Harness()
        harness.saveConfig()
        harness.gateway.entries = listOf(WebDavEntry("a.ehb", 10L, 100L))

        val result = harness.manager.listBackups()

        assertThat(result).isInstanceOf(BackupListResult.Success::class.java)
        assertThat((result as BackupListResult.Success).entries).hasSize(1)
    }

    @Test
    fun restore_rejects_a_path_traversal_name() = runTest {
        val harness = Harness()
        harness.saveConfig()

        val result = harness.manager.restore(WebDavEntry("../evil.ehb", 1L, 0L))

        assertThat(result).isInstanceOf(RestoreResult.Failure::class.java)
        assertThat(harness.transcripts.data).isEmpty()
    }

    private class PlainCipher : NasPayloadCipher {
        override fun encrypt(plain: ByteArray): ByteArray = plain
        override fun decrypt(payload: ByteArray): ByteArray = payload
    }

    private class FakeGateway : WebDavGateway {
        val files = mutableMapOf<String, ByteArray>()
        var entries: List<WebDavEntry> = emptyList()
        var testError: Exception? = null
        var folderEnsured = false

        override fun test() {
            testError?.let { throw it }
        }

        override fun ensureFolder() {
            folderEnsured = true
        }

        override fun put(name: String, bytes: ByteArray) {
            files[name] = bytes
        }

        override fun list(): List<WebDavEntry> = entries

        override fun get(name: String): ByteArray =
            files[name] ?: throw WebDavException(404, "Not Found")
    }

    private class FakeTranscripts(
        var data: List<TranscriptSession> = emptyList()
    ) : TranscriptStore {
        override suspend fun sessions(): List<TranscriptSession> = data
        override suspend fun reload(): List<TranscriptSession> = data
        override suspend fun session(id: String): TranscriptSession? =
            data.firstOrNull { it.id == id }

        override suspend fun createSession(session: TranscriptSession) {
            data = data.filterNot { it.id == session.id } + session
        }

        override suspend fun appendUtterance(sessionId: String, utterance: TranscriptUtterance) = Unit

        override suspend fun setMarked(
            sessionId: String,
            utteranceId: String,
            marked: Boolean
        ) = Unit

        override suspend fun deleteSession(sessionId: String) {
            data = data.filterNot { it.id == sessionId }
        }

        override suspend fun search(query: String): List<TranscriptHit> = emptyList()

        override suspend fun replaceAll(sessions: List<TranscriptSession>) {
            data = sessions
        }

        override suspend fun clear() {
            data = emptyList()
        }
    }

    private class FakeMemory(
        var data: List<SessionMemory> = emptyList()
    ) : MemoryStore {
        override suspend fun all(): List<SessionMemory> = data
        override suspend fun reload(): List<SessionMemory> = data
        override suspend fun get(sessionId: String): SessionMemory? =
            data.firstOrNull { it.sessionId == sessionId }

        override suspend fun save(memory: SessionMemory) {
            data = data.filterNot { it.sessionId == memory.sessionId } + memory
        }

        override suspend fun delete(sessionId: String) {
            data = data.filterNot { it.sessionId == sessionId }
        }

        override suspend fun replaceAll(memories: List<SessionMemory>) {
            data = memories
        }

        override suspend fun clear() {
            data = emptyList()
        }
    }
}

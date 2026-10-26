package io.github.markyu0401.everheard.data.transcript

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface TranscriptStore {
    suspend fun sessions(): List<TranscriptSession>
    suspend fun session(id: String): TranscriptSession?
    suspend fun createSession(session: TranscriptSession)
    suspend fun appendUtterance(sessionId: String, utterance: TranscriptUtterance)
    suspend fun search(query: String): List<TranscriptHit>
}

/**
 * One encrypted JSON document for the whole personal corpus. It is intentionally
 * simple: transcripts are text, the corpus is small, and M4 replaces this with
 * Room + SQLCipher when commitments and projects arrive.
 */
class EncryptedTranscriptStore(
    private val file: File,
    private val cipher: KeystoreCipher = KeystoreCipher()
) : TranscriptStore {

    private val mutex = Mutex()
    private var cache: List<TranscriptSession>? = null

    override suspend fun sessions(): List<TranscriptSession> = mutex.withLock {
        load()
    }

    override suspend fun session(id: String): TranscriptSession? = mutex.withLock {
        load().firstOrNull { it.id == id }
    }

    override suspend fun createSession(session: TranscriptSession) = mutex.withLock {
        val list = load().toMutableList()
        list.removeAll { it.id == session.id }
        list += session
        persist(list)
        cache = list
    }

    override suspend fun appendUtterance(
        sessionId: String,
        utterance: TranscriptUtterance
    ) = mutex.withLock {
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.id == sessionId }
        if (index >= 0) {
            val session = list[index]
            list[index] = session.copy(utterances = session.utterances + utterance)
            persist(list)
            cache = list
        }
    }

    override suspend fun search(query: String): List<TranscriptHit> = mutex.withLock {
        val needle = query.trim()
        if (needle.isEmpty()) return@withLock emptyList()
        load().flatMap { session ->
            session.utterances
                .filter { it.text.contains(needle, ignoreCase = true) }
                .map { TranscriptHit(session.id, session.title, it) }
        }
    }

    private suspend fun load(): List<TranscriptSession> {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching {
                val plain = cipher.decrypt(file.readBytes())
                TranscriptCodec.decode(String(plain, Charsets.UTF_8))
            }.getOrElse { emptyList() }
        }
        cache = loaded
        return loaded
    }

    private suspend fun persist(sessions: List<TranscriptSession>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val plain = TranscriptCodec.encode(sessions).toByteArray(Charsets.UTF_8)
            file.writeBytes(cipher.encrypt(plain))
        }
    }
}

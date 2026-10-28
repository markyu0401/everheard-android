package io.github.markyu0401.everheard.data.memory

import io.github.markyu0401.everheard.data.transcript.KeystoreCipher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface MemoryStore {
    suspend fun all(): List<SessionMemory>
    suspend fun reload(): List<SessionMemory>
    suspend fun get(sessionId: String): SessionMemory?
    suspend fun save(memory: SessionMemory)
    suspend fun delete(sessionId: String)
    suspend fun replaceAll(memories: List<SessionMemory>)
    suspend fun clear()
}

/**
 * One encrypted document, same atomic temp-file pattern as the transcript
 * store. This is the pre-Room memory layer: it is enough for a personal corpus
 * and keeps the JSON versioned for a later SQLCipher migration.
 */
class EncryptedMemoryStore(
    private val file: File,
    private val cipher: KeystoreCipher = KeystoreCipher("everheard-memory")
) : MemoryStore {

    private val mutex = Mutex()
    private var cache: List<SessionMemory>? = null

    override suspend fun all(): List<SessionMemory> = mutex.withLock { load() }

    override suspend fun reload(): List<SessionMemory> = mutex.withLock {
        cache = null
        load()
    }

    override suspend fun get(sessionId: String): SessionMemory? = mutex.withLock {
        load().firstOrNull { it.sessionId == sessionId }
    }

    override suspend fun save(memory: SessionMemory) = mutex.withLock {
        val list = load().toMutableList()
        list.removeAll { it.sessionId == memory.sessionId }
        list += memory
        persist(list)
        cache = list
    }

    override suspend fun delete(sessionId: String) {
        mutex.withLock {
            val list = load().filterNot { it.sessionId == sessionId }
            persist(list)
            cache = list
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            cache = emptyList()
            withContext(Dispatchers.IO) {
                file.delete()
                File(file.parentFile, "${file.name}.tmp").delete()
            }
        }
    }

    override suspend fun replaceAll(memories: List<SessionMemory>) {
        mutex.withLock {
            persist(memories)
            cache = memories
        }
    }

    private suspend fun load(): List<SessionMemory> {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching {
                val plain = cipher.decrypt(file.readBytes())
                MemoryCodec.decode(String(plain, Charsets.UTF_8))
            }.getOrElse { emptyList() }
        }
        cache = loaded
        return loaded
    }

    private suspend fun persist(memories: List<SessionMemory>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val plain = MemoryCodec.encode(memories).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.encrypt(plain)
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(encrypted)
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "unable to replace memory file" }
            }
        }
    }
}

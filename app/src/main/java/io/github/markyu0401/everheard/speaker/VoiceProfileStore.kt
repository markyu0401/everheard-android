package io.github.markyu0401.everheard.speaker

import io.github.markyu0401.everheard.data.transcript.KeystoreCipher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One encrypted voice profile. The file contains a normalised embedding and
 * metadata only; enrollment audio is never written.
 */
class VoiceProfileStore(
    private val file: File,
    private val cipher: KeystoreCipher = KeystoreCipher("everheard-voice-profile")
) {

    private companion object {
        const val VERSION = 1
    }

    private val mutex = Mutex()
    private var cache: VoiceProfile? = null
    private var loaded = false

    suspend fun load(): VoiceProfile? = mutex.withLock {
        if (loaded) return@withLock cache
        cache = withContext(Dispatchers.IO) { readProfile() }
        loaded = true
        cache
    }

    suspend fun save(embedding: FloatArray, samples: Int): VoiceProfile = mutex.withLock {
        val normalized = VoiceMatcher.normalize(embedding)
            ?: error("voice embedding is empty")
        val previous = if (loaded) cache else withContext(Dispatchers.IO) { readProfile() }
        val now = System.currentTimeMillis()
        val profile = VoiceProfile(
            embedding = normalized,
            samples = samples,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now
        )
        withContext(Dispatchers.IO) { writeProfile(profile) }
        cache = profile
        loaded = true
        profile
    }

    suspend fun clear() {
        mutex.withLock {
            cache = null
            loaded = true
            withContext(Dispatchers.IO) {
                file.delete()
                File(file.parentFile, "${file.name}.tmp").delete()
            }
        }
    }

    private fun readProfile(): VoiceProfile? {
        if (!file.exists()) return null
        return runCatching {
            val plain = cipher.decrypt(file.readBytes())
            val root = JSONObject(String(plain, Charsets.UTF_8))
            val array = root.optJSONArray("embedding") ?: return null
            if (array.length() == 0) return null
            val embedding = FloatArray(array.length()) { index ->
                array.optDouble(index, 0.0).toFloat()
            }
            VoiceProfile(
                embedding = embedding,
                samples = root.optInt("samples", 0),
                createdAt = root.optLong("createdAt", 0L),
                updatedAt = root.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    private fun writeProfile(profile: VoiceProfile) {
        file.parentFile?.mkdirs()
        val root = JSONObject().apply {
            put("version", VERSION)
            put("embedding", JSONArray().apply {
                profile.embedding.forEach { put(it.toDouble()) }
            })
            put("samples", profile.samples)
            put("createdAt", profile.createdAt)
            put("updatedAt", profile.updatedAt)
        }
        val encrypted = cipher.encrypt(root.toString().toByteArray(Charsets.UTF_8))
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(encrypted)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "unable to replace voice profile" }
        }
    }
}

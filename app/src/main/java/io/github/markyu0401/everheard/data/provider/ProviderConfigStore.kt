package io.github.markyu0401.everheard.data.provider

import io.github.markyu0401.everheard.data.transcript.KeystoreCipher
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Small abstraction over the Keystore cipher so the store can be unit-tested
 * without Android Keystore. Production code uses [KeystoreProviderCipher].
 */
interface ProviderPayloadCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(payload: ByteArray): ByteArray
}

/** AES-256-GCM backed by Android Keystore under the `everheard-providers` alias. */
class KeystoreProviderCipher(
    private val delegate: KeystoreCipher = KeystoreCipher(ProviderConfigStore.KEYSTORE_ALIAS)
) : ProviderPayloadCipher {

    override fun encrypt(plain: ByteArray): ByteArray = delegate.encrypt(plain)

    override fun decrypt(payload: ByteArray): ByteArray = delegate.decrypt(payload)
}

/**
 * Encrypted, versioned JSON file for BYO provider settings.
 *
 * The document is written to `providers.bin` through a sibling `.tmp` file and
 * atomically renamed, so a crash mid-write cannot leave a half-written config.
 * Plaintext (including API keys) only exists in memory; the bytes on disk are
 * always the Keystore ciphertext. Corrupt or missing files load as an
 * unconfigured [ProviderConfig].
 */
class ProviderConfigStore(
    private val file: File,
    private val cipher: ProviderPayloadCipher = KeystoreProviderCipher()
) {

    companion object {
        const val FILE_NAME = "providers.bin"
        const val KEYSTORE_ALIAS = "everheard-providers"

        private const val TEMP_SUFFIX = ".tmp"

        /** The conventional location under `Context.filesDir`. */
        fun inDirectory(directory: File): ProviderConfigStore =
            ProviderConfigStore(File(directory, FILE_NAME))
    }

    private val mutex = Mutex()
    private var cache: ProviderConfig? = null

    suspend fun load(): ProviderConfig = mutex.withLock { loadLocked() }

    /** Drops the in-memory cache and re-reads the file. */
    suspend fun reload(): ProviderConfig = mutex.withLock {
        cache = null
        loadLocked()
    }

    suspend fun save(config: ProviderConfig) {
        mutex.withLock {
            persist(config)
            cache = config
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cache = ProviderConfig()
            withContext(Dispatchers.IO) {
                file.delete()
                tempFile().delete()
            }
        }
    }

    suspend fun exists(): Boolean = withContext(Dispatchers.IO) { file.exists() }

    private suspend fun loadLocked(): ProviderConfig {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext ProviderConfig()
            runCatching {
                val plain = cipher.decrypt(file.readBytes())
                ProviderConfigCodec.decode(String(plain, Charsets.UTF_8))
            }.getOrElse { ProviderConfig() }
        }
        cache = loaded
        return loaded
    }

    private suspend fun persist(config: ProviderConfig) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val plaintext = ProviderConfigCodec.encode(config).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.encrypt(plaintext)
            val temp = tempFile()
            FileOutputStream(temp).use { output ->
                output.write(encrypted)
                output.flush()
                output.fd.sync()
            }
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "unable to replace provider config file" }
            }
        }
    }

    private fun tempFile(): File = File(file.parentFile, "${file.name}$TEMP_SUFFIX")
}

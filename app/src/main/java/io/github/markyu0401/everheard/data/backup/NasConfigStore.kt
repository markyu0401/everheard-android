package io.github.markyu0401.everheard.data.backup

import io.github.markyu0401.everheard.data.transcript.KeystoreCipher
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface NasPayloadCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(payload: ByteArray): ByteArray
}

/** AES-256-GCM under the dedicated `everheard-nas` Keystore alias. */
class KeystoreNasCipher(
    private val delegate: KeystoreCipher = KeystoreCipher(NasConfigStore.KEYSTORE_ALIAS)
) : NasPayloadCipher {

    override fun encrypt(plain: ByteArray): ByteArray = delegate.encrypt(plain)

    override fun decrypt(payload: ByteArray): ByteArray = delegate.decrypt(payload)
}

/**
 * Encrypted `nas.bin`: WebDAV URL, account, folder, backup passphrase and the
 * last successful backup marker. The same atomic temp-file pattern as the
 * transcript and provider stores.
 */
class NasConfigStore(
    private val file: File,
    private val cipher: NasPayloadCipher = KeystoreNasCipher()
) {

    companion object {
        const val FILE_NAME = "nas.bin"
        const val KEYSTORE_ALIAS = "everheard-nas"
        private const val TEMP_SUFFIX = ".tmp"

        fun inDirectory(directory: File): NasConfigStore =
            NasConfigStore(File(directory, FILE_NAME))
    }

    private val mutex = Mutex()
    private var cache: NasBackupConfig? = null

    suspend fun load(): NasBackupConfig = mutex.withLock { loadLocked() }

    suspend fun reload(): NasBackupConfig = mutex.withLock {
        cache = null
        loadLocked()
    }

    suspend fun save(config: NasBackupConfig) {
        mutex.withLock {
            persist(config)
            cache = config
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cache = NasBackupConfig()
            withContext(Dispatchers.IO) {
                file.delete()
                tempFile().delete()
            }
        }
    }

    suspend fun exists(): Boolean = withContext(Dispatchers.IO) { file.exists() }

    private suspend fun loadLocked(): NasBackupConfig {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext NasBackupConfig()
            runCatching {
                val plain = cipher.decrypt(file.readBytes())
                NasConfigCodec.decode(String(plain, Charsets.UTF_8))
            }.getOrElse { NasBackupConfig() }
        }
        cache = loaded
        return loaded
    }

    private suspend fun persist(config: NasBackupConfig) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val plaintext = NasConfigCodec.encode(config).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.encrypt(plaintext)
            val temp = tempFile()
            FileOutputStream(temp).use { output ->
                output.write(encrypted)
                output.flush()
                output.fd.sync()
            }
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "unable to replace NAS config file" }
            }
        }
    }

    private fun tempFile(): File = File(file.parentFile, "${file.name}$TEMP_SUFFIX")
}

package io.github.markyu0401.everheard.mcp

import io.github.markyu0401.everheard.data.transcript.KeystoreCipher
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class McpConfig(
    val enabled: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val token: String = ""
) {
    companion object {
        const val DEFAULT_PORT = 8765
    }
}

interface McpPayloadCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(payload: ByteArray): ByteArray
}

class KeystoreMcpCipher(
    private val delegate: KeystoreCipher = KeystoreCipher(McpConfigStore.KEYSTORE_ALIAS)
) : McpPayloadCipher {
    override fun encrypt(plain: ByteArray): ByteArray = delegate.encrypt(plain)
    override fun decrypt(payload: ByteArray): ByteArray = delegate.decrypt(payload)
}

object McpConfigCodec {
    private const val VERSION = 1

    fun encode(config: McpConfig): String = JSONObject().apply {
        put("version", VERSION)
        put("enabled", config.enabled)
        put("port", config.port)
        put("token", config.token)
    }.toString()

    fun decode(raw: String): McpConfig {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return McpConfig()
        val port = root.optInt("port", McpConfig.DEFAULT_PORT)
            .takeIf { it in 1024..65535 } ?: McpConfig.DEFAULT_PORT
        return McpConfig(
            enabled = root.optBoolean("enabled", false),
            port = port,
            token = root.optString("token")
        )
    }
}

class McpConfigStore(
    private val file: File,
    private val cipher: McpPayloadCipher = KeystoreMcpCipher()
) {

    companion object {
        const val FILE_NAME = "mcp.bin"
        const val KEYSTORE_ALIAS = "everheard-mcp"
        private const val TEMP_SUFFIX = ".tmp"
        private val secureRandom = SecureRandom()

        fun inDirectory(directory: File): McpConfigStore =
            McpConfigStore(File(directory, FILE_NAME))

        fun newToken(): String {
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    private val mutex = Mutex()
    private var cache: McpConfig? = null

    suspend fun load(): McpConfig = mutex.withLock { loadLocked() }

    suspend fun reload(): McpConfig = mutex.withLock {
        cache = null
        loadLocked()
    }

    suspend fun save(config: McpConfig) {
        mutex.withLock {
            persist(config)
            cache = config
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cache = McpConfig()
            withContext(Dispatchers.IO) {
                file.delete()
                tempFile().delete()
            }
        }
    }

    private suspend fun loadLocked(): McpConfig {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext McpConfig()
            runCatching {
                McpConfigCodec.decode(
                    String(cipher.decrypt(file.readBytes()), Charsets.UTF_8)
                )
            }.getOrElse { McpConfig() }
        }
        cache = loaded
        return loaded
    }

    private suspend fun persist(config: McpConfig) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val plain = McpConfigCodec.encode(config).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.encrypt(plain)
            val temp = tempFile()
            FileOutputStream(temp).use { output ->
                output.write(encrypted)
                output.flush()
                output.fd.sync()
            }
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "unable to replace MCP config file" }
            }
        }
    }

    private fun tempFile(): File = File(file.parentFile, "${file.name}$TEMP_SUFFIX")
}

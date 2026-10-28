package io.github.markyu0401.everheard.data.backup

import org.json.JSONObject

/** Versioned plaintext JSON for the encrypted `nas.bin` document. */
object NasConfigCodec {

    const val VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_SERVER_URL = "serverUrl"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_FOLDER = "folder"
    private const val KEY_PASSPHRASE = "passphrase"
    private const val KEY_TRUST_SELF_SIGNED = "trustSelfSigned"
    private const val KEY_LAST_BACKUP_AT = "lastBackupAt"
    private const val KEY_LAST_BACKUP_NAME = "lastBackupName"

    fun encode(config: NasBackupConfig): String {
        val root = JSONObject().apply {
            put(KEY_VERSION, VERSION)
            put(KEY_SERVER_URL, config.serverUrl)
            put(KEY_USERNAME, config.username)
            put(KEY_PASSWORD, config.password)
            put(KEY_FOLDER, config.folder)
            put(KEY_PASSPHRASE, config.passphrase)
            put(KEY_TRUST_SELF_SIGNED, config.trustSelfSigned)
            put(KEY_LAST_BACKUP_AT, config.lastBackupAt)
            put(KEY_LAST_BACKUP_NAME, config.lastBackupName)
        }
        return root.toString()
    }

    fun decode(raw: String): NasBackupConfig {
        if (raw.isBlank()) return NasBackupConfig()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return NasBackupConfig()
        return NasBackupConfig(
            serverUrl = root.optString(KEY_SERVER_URL),
            username = root.optString(KEY_USERNAME),
            password = root.optString(KEY_PASSWORD),
            folder = root.optString(KEY_FOLDER, NasBackupConfig.DEFAULT_FOLDER),
            passphrase = root.optString(KEY_PASSPHRASE),
            trustSelfSigned = root.optBoolean(KEY_TRUST_SELF_SIGNED, true),
            lastBackupAt = root.optLong(KEY_LAST_BACKUP_AT, 0L),
            lastBackupName = root.optString(KEY_LAST_BACKUP_NAME)
        )
    }
}

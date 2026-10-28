package io.github.markyu0401.everheard.data.backup

/**
 * WebDAV endpoint for NAS backups. The default folder is the Synology user's
 * home; the WebDAV root on DSM exposes it as `/home`.
 */
data class NasBackupConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val folder: String = DEFAULT_FOLDER,
    val passphrase: String = "",
    val trustSelfSigned: Boolean = true,
    val lastBackupAt: Long = 0L,
    val lastBackupName: String = ""
) {

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            folder.isNotBlank() &&
            passphrase.isNotBlank()

    fun normalized(): NasBackupConfig = copy(
        serverUrl = serverUrl.trim().trimEnd('/'),
        username = username.trim(),
        folder = normalizeFolder(folder)
    )

    companion object {
        const val DEFAULT_FOLDER = "/home/EverheardBackups"

        /** Always starts with `/` and never ends with one, except for `/`. */
        fun normalizeFolder(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return DEFAULT_FOLDER
            val withLeadingSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
            return withLeadingSlash.trimEnd('/').ifEmpty { "/" }
        }
    }
}

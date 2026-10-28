package io.github.markyu0401.everheard.data.backup

import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.data.memory.MemoryStore
import io.github.markyu0401.everheard.data.transcript.TranscriptStore
import java.io.IOException

sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(val message: String) : ConnectionResult
}

sealed interface BackupResult {
    data class Success(
        val fileName: String,
        val bytes: Int,
        val sessions: Int,
        val memories: Int
    ) : BackupResult

    data class Failure(val message: String) : BackupResult
}

sealed interface BackupListResult {
    data class Success(val entries: List<WebDavEntry>) : BackupListResult
    data class Failure(val message: String) : BackupListResult
}

sealed interface RestoreResult {
    data class Success(val sessions: Int, val memories: Int) : RestoreResult
    data class Failure(val message: String) : RestoreResult
}

/**
 * Orchestrates a portable backup: gather transcripts/memory/schedule/excluded
 * apps, encode versioned JSON, encrypt with the user passphrase, upload over
 * WebDAV. Restore does the reverse and replaces local data only after the file
 * has been decrypted and validated, so a wrong passphrase cannot wipe anything.
 */
class BackupManager(
    private val configStore: NasConfigStore,
    private val transcriptStore: TranscriptStore,
    private val memoryStore: MemoryStore,
    private val loadSchedule: suspend () -> List<ScheduleRule>,
    private val saveSchedule: suspend (List<ScheduleRule>) -> Unit,
    private val loadExcluded: () -> Set<String>,
    private val saveExcluded: (Set<String>) -> Unit,
    private val clientFactory: (NasBackupConfig) -> WebDavGateway = { WebDavClient(it) }
) {

    suspend fun testConnection(): ConnectionResult {
        val config = configStore.load().normalized()
        if (!config.hasEndpoint()) {
            return ConnectionResult.Failure("Fill in the server, account and folder first.")
        }
        return try {
            val gateway = clientFactory(config)
            gateway.test()
            gateway.ensureFolder()
            ConnectionResult.Success
        } catch (error: WebDavException) {
            ConnectionResult.Failure(mapWebDav(error))
        } catch (error: IOException) {
            ConnectionResult.Failure(networkMessage(error))
        } catch (error: Exception) {
            ConnectionResult.Failure("Connection failed: ${safeMessage(error)}")
        }
    }

    suspend fun backupNow(): BackupResult {
        val config = configStore.load().normalized()
        if (!config.isConfigured) {
            return BackupResult.Failure(
                "Fill in the server, account, folder and backup passphrase first."
            )
        }
        return try {
            val now = System.currentTimeMillis()
            val transcripts = transcriptStore.sessions()
            val memories = memoryStore.all()
            val rules = loadSchedule()
            val excluded = loadExcluded().sorted()
            val payload = BackupPayload(
                createdAt = now,
                transcripts = transcripts,
                memories = memories,
                scheduleRules = rules,
                excludedApps = excluded
            )
            val plain = BackupCodec.encodePayload(payload)
            val encrypted = BackupCodec.encrypt(plain, config.passphrase)
            val name = BackupCodec.fileName(now)
            val gateway = clientFactory(config)
            gateway.ensureFolder()
            gateway.put(name, encrypted)
            configStore.save(
                config.copy(lastBackupAt = now, lastBackupName = name)
            )
            BackupResult.Success(
                fileName = name,
                bytes = encrypted.size,
                sessions = transcripts.size,
                memories = memories.size
            )
        } catch (error: WebDavException) {
            BackupResult.Failure(mapWebDav(error))
        } catch (error: IOException) {
            BackupResult.Failure(networkMessage(error))
        } catch (error: BackupFormatException) {
            BackupResult.Failure(error.message ?: "Invalid backup data.")
        } catch (error: Exception) {
            BackupResult.Failure("Backup failed: ${safeMessage(error)}")
        }
    }

    suspend fun listBackups(): BackupListResult {
        val config = configStore.load().normalized()
        if (!config.hasEndpoint()) {
            return BackupListResult.Failure("Configure the NAS endpoint first.")
        }
        return try {
            BackupListResult.Success(clientFactory(config).list())
        } catch (error: WebDavException) {
            BackupListResult.Failure(mapWebDav(error))
        } catch (error: IOException) {
            BackupListResult.Failure(networkMessage(error))
        } catch (error: Exception) {
            BackupListResult.Failure("Cannot list backups: ${safeMessage(error)}")
        }
    }

    suspend fun restore(entry: WebDavEntry): RestoreResult {
        val config = configStore.load().normalized()
        if (!config.isConfigured) {
            return RestoreResult.Failure(
                "Fill in the server, account, folder and backup passphrase first."
            )
        }
        if (!SAFE_FILE_NAME.matches(entry.name)) {
            return RestoreResult.Failure("Invalid backup file name.")
        }
        return try {
            val gateway = clientFactory(config)
            val encrypted = gateway.get(entry.name)
            val plain = BackupCodec.decrypt(encrypted, config.passphrase)
            val payload = BackupCodec.decodePayload(plain)
            transcriptStore.replaceAll(payload.transcripts)
            memoryStore.replaceAll(payload.memories)
            saveSchedule(payload.scheduleRules)
            saveExcluded(payload.excludedApps.toSet())
            RestoreResult.Success(
                sessions = payload.transcripts.size,
                memories = payload.memories.size
            )
        } catch (error: WebDavException) {
            RestoreResult.Failure(mapWebDav(error))
        } catch (error: IOException) {
            RestoreResult.Failure(networkMessage(error))
        } catch (error: BackupDecryptException) {
            RestoreResult.Failure(error.message ?: "Wrong passphrase.")
        } catch (error: BackupFormatException) {
            RestoreResult.Failure(error.message ?: "Invalid backup file.")
        } catch (error: Exception) {
            RestoreResult.Failure("Restore failed: ${safeMessage(error)}")
        }
    }

    private fun NasBackupConfig.hasEndpoint(): Boolean =
        serverUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            folder.isNotBlank()

    private fun mapWebDav(error: WebDavException): String = when (error.statusCode) {
        401 -> "Authentication failed. Check the username and password."
        403 -> "Permission denied. This account cannot write to the folder."
        404 -> "Folder not found. Check the folder path."
        405 -> "The server does not allow this operation in that folder."
        409 -> "The parent folder does not exist."
        507 -> "The NAS has no free space for this backup."
        else -> "NAS error ${error.statusCode}: ${error.message}"
    }

    private fun networkMessage(error: IOException): String {
        val detail = error.message?.take(120)?.takeIf { it.isNotBlank() }
        return if (detail == null) {
            "Cannot reach the NAS. Check the server URL and network."
        } else {
            "Cannot reach the NAS: $detail"
        }
    }

    private fun safeMessage(error: Exception): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private companion object {
        val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]+\\.${BackupCodec.FILE_EXTENSION}")
    }
}

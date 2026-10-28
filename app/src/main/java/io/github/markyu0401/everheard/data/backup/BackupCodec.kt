package io.github.markyu0401.everheard.data.backup

import io.github.markyu0401.everheard.capture.schedule.ScheduleCodec
import io.github.markyu0401.everheard.data.memory.MemoryCodec
import io.github.markyu0401.everheard.data.transcript.TranscriptCodec
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

class BackupFormatException(message: String) : Exception(message)

class BackupDecryptException(message: String) : Exception(message)

/**
 * Portable, passphrase-encrypted backup file.
 *
 * Container: `EHB1 | version(1) | saltLen(1) | salt | ivLen(1) | iv | ciphertext`.
 * The payload is versioned JSON; transcripts and memory reuse the on-device
 * codecs, and the schedule uses [ScheduleCodec]. AES-256-GCM with
 * PBKDF2-HMAC-SHA256 (120k iterations) means the NAS only ever stores
 * ciphertext and the file can be restored on a different phone without the
 * Android Keystore key.
 */
object BackupCodec {

    const val FORMAT_VERSION = 1
    const val FILE_EXTENSION = "ehb"

    private val MAGIC = byteArrayOf(
        'E'.code.toByte(),
        'H'.code.toByte(),
        'B'.code.toByte(),
        '1'.code.toByte()
    )
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + 1 + SALT_BYTES + 1 + IV_BYTES

    fun encodePayload(payload: BackupPayload): ByteArray {
        val root = JSONObject().apply {
            put("version", payload.version)
            put("createdAt", payload.createdAt)
            put("transcripts", JSONObject(TranscriptCodec.encode(payload.transcripts)))
            put("memories", JSONObject(MemoryCodec.encode(payload.memories)))
            put("schedule", ScheduleCodec.encode(payload.scheduleRules))
            put("excludedApps", JSONArray(payload.excludedApps))
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodePayload(plain: ByteArray): BackupPayload {
        val root = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }
            .getOrElse { throw BackupFormatException("The file is not a JSON backup.") }
        val version = root.optInt("version", 0)
        if (version !in 1..FORMAT_VERSION) {
            throw BackupFormatException("Unsupported backup version: $version.")
        }
        val transcripts = root.optJSONObject("transcripts")
            ?.let { runCatching { TranscriptCodec.decode(it.toString()) }.getOrElse { emptyList() } }
            .orEmpty()
        val memories = root.optJSONObject("memories")
            ?.let { runCatching { MemoryCodec.decode(it.toString()) }.getOrElse { emptyList() } }
            .orEmpty()
        val excluded = root.optJSONArray("excludedApps")
            ?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }
            .orEmpty()
        return BackupPayload(
            version = version,
            createdAt = root.optLong("createdAt", 0L),
            transcripts = transcripts,
            memories = memories,
            scheduleRules = ScheduleCodec.decode(root.optString("schedule")),
            excludedApps = excluded
        )
    }

    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return ByteBuffer.allocate(4 + 1 + 1 + salt.size + 1 + iv.size + ciphertext.size)
            .put(MAGIC)
            .put(FORMAT_VERSION.toByte())
            .put(salt.size.toByte())
            .put(salt)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(payload: ByteArray, passphrase: String): ByteArray {
        if (payload.size < HEADER_BYTES) {
            throw BackupFormatException("The backup file is too short.")
        }
        if (!payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupFormatException("This is not an Everheard backup file.")
        }
        val version = payload[MAGIC.size].toInt() and 0xFF
        if (version !in 1..FORMAT_VERSION) {
            throw BackupFormatException("Unsupported backup version: $version.")
        }
        var offset = MAGIC.size + 1
        val saltLength = payload[offset++].toInt() and 0xFF
        if (saltLength <= 0 || offset + saltLength > payload.size) {
            throw BackupFormatException("Invalid salt in the backup file.")
        }
        val salt = payload.copyOfRange(offset, offset + saltLength)
        offset += saltLength
        val ivLength = payload[offset++].toInt() and 0xFF
        if (ivLength <= 0 || offset + ivLength > payload.size) {
            throw BackupFormatException("Invalid IV in the backup file.")
        }
        val iv = payload.copyOfRange(offset, offset + ivLength)
        offset += ivLength
        val ciphertext = payload.copyOfRange(offset, payload.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw BackupDecryptException("Wrong passphrase or corrupted backup.")
        } catch (_: GeneralSecurityException) {
            throw BackupDecryptException("Wrong passphrase or corrupted backup.")
        }
    }

    fun fileName(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
            .format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return "everheard-$stamp.$FILE_EXTENSION"
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}

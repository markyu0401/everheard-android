package io.github.markyu0401.everheard.data.transcript

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that never leaves Android Keystore. The payload is
 * `EH01 | ivLength (1 byte) | iv | ciphertext`; the IV is generated per write.
 */
class KeystoreCipher(private val alias: String = "everheard-transcripts") {

    companion object {
        private val MAGIC = byteArrayOf('E'.code.toByte(), 'H'.code.toByte(), '0'.code.toByte(), '1'.code.toByte())
        private const val IV_LENGTH_BYTE = 1
        private const val TAG_BITS = 128
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + IV_LENGTH_BYTE + iv.size + ciphertext.size)
            .put(MAGIC)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > MAGIC.size + IV_LENGTH_BYTE) { "payload too short" }
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "bad magic" }
        val ivLength = payload[MAGIC.size].toInt() and 0xFF
        val ivStart = MAGIC.size + IV_LENGTH_BYTE
        val cipherStart = ivStart + ivLength
        require(cipherStart <= payload.size) { "bad iv length" }
        val iv = payload.copyOfRange(ivStart, cipherStart)
        val ciphertext = payload.copyOfRange(cipherStart, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
        return keyStore.getKey(alias, null) as SecretKey
    }
}

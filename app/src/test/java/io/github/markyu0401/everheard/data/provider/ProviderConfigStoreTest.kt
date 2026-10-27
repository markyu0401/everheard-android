package io.github.markyu0401.everheard.data.provider

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProviderConfigStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Deterministic stand-in for KeystoreCipher; never touches Android. */
    private class XorCipher : ProviderPayloadCipher {
        override fun encrypt(plain: ByteArray): ByteArray =
            ByteArray(plain.size) { index -> (plain[index].toInt() xor 0x5A).toByte() }

        override fun decrypt(payload: ByteArray): ByteArray = encrypt(payload)
    }

    private fun newStore(file: File = File(temporaryFolder.root, ProviderConfigStore.FILE_NAME)) =
        ProviderConfigStore(file, XorCipher())

    @Test
    fun save_load_round_trip_encrypts_the_document_and_removes_temp_file() = runTest {
        val file = File(temporaryFolder.root, ProviderConfigStore.FILE_NAME)
        val store = newStore(file)
        val config = ProviderConfig(
            llmBaseUrl = "https://llm.example.test/v1",
            llmModel = "gpt-test",
            llmApiKey = "sk-llm-secret",
            asrBaseUrl = "https://asr.example.test/v1",
            asrModel = "whisper-test",
            asrApiKey = "sk-asr-secret",
            embeddingBaseUrl = "https://embed.example.test/v1",
            embeddingModel = "embed-test",
            embeddingApiKey = "sk-embed-secret",
            connectTimeoutMs = 7_000,
            readTimeoutMs = 45_000
        )

        store.save(config)

        assertThat(file.exists()).isTrue()
        val diskBytes = file.readBytes()
        val diskText = String(diskBytes, Charsets.ISO_8859_1)
        assertThat(diskText).doesNotContain("sk-llm-secret")
        assertThat(diskText).doesNotContain("sk-asr-secret")
        assertThat(diskText).doesNotContain("sk-embed-secret")
        assertThat(diskText).doesNotContain("llm.example.test")
        assertThat(File(temporaryFolder.root, "${ProviderConfigStore.FILE_NAME}.tmp").exists())
            .isFalse()

        assertThat(store.load()).isEqualTo(config)
    }

    @Test
    fun reload_reads_the_file_again() = runTest {
        val file = File(temporaryFolder.root, ProviderConfigStore.FILE_NAME)
        val first = newStore(file)
        val second = newStore(file)
        val config = ProviderConfig(llmBaseUrl = "https://llm.example.test/v1", llmModel = "m")

        first.save(config)

        assertThat(second.reload()).isEqualTo(config)
    }

    @Test
    fun clear_deletes_file_and_returns_defaults() = runTest {
        val file = File(temporaryFolder.root, ProviderConfigStore.FILE_NAME)
        val store = newStore(file)
        store.save(ProviderConfig(llmApiKey = "sk-secret"))

        store.clear()

        assertThat(file.exists()).isFalse()
        assertThat(store.load()).isEqualTo(ProviderConfig())
        assertThat(store.exists()).isFalse()
    }

    @Test
    fun corrupt_file_loads_as_unconfigured_config() = runTest {
        val file = File(temporaryFolder.root, ProviderConfigStore.FILE_NAME)
        file.writeBytes(XorCipher().encrypt("this is not json".toByteArray()))

        val loaded = newStore(file).load()

        assertThat(loaded).isEqualTo(ProviderConfig())
    }

    @Test
    fun missing_file_loads_as_unconfigured_config() = runTest {
        val loaded = newStore().load()

        assertThat(loaded).isEqualTo(ProviderConfig())
        assertThat(loaded.llmConfigured).isFalse()
    }
}

package io.github.markyu0401.everheard.data.provider

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class ProviderConfigCodecTest {

    @Test
    fun round_trips_every_provider_field() {
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
            connectTimeoutMs = 5_000,
            readTimeoutMs = 90_000
        )

        val encoded = ProviderConfigCodec.encode(config)
        val decoded = ProviderConfigCodec.decode(encoded)

        assertThat(decoded).isEqualTo(config)
    }

    @Test
    fun encoded_document_is_versioned() {
        val encoded = ProviderConfigCodec.encode(ProviderConfig())

        val root = JSONObject(encoded)

        assertThat(root.getInt("version")).isEqualTo(ProviderConfigCodec.VERSION)
        assertThat(root.getJSONObject("llm").getString("baseUrl")).isEmpty()
        assertThat(root.getJSONObject("timeouts").getInt("connectMs"))
            .isEqualTo(ProviderConfig.DEFAULT_CONNECT_TIMEOUT_MS)
    }

    @Test
    fun blank_or_malformed_json_returns_defaults() {
        assertThat(ProviderConfigCodec.decode("")).isEqualTo(ProviderConfig())
        assertThat(ProviderConfigCodec.decode("not json")).isEqualTo(ProviderConfig())
        assertThat(ProviderConfigCodec.decode("""{"version":1}""")).isEqualTo(ProviderConfig())
        assertThat(ProviderConfigCodec.decode("""{"llm":"wrong shape"}"""))
            .isEqualTo(ProviderConfig())
    }

    @Test
    fun missing_fields_default_to_empty_strings() {
        val decoded = ProviderConfigCodec.decode(
            """
            {
              "version": 1,
              "llm": {"baseUrl": "https://llm.example.test/v1"}
            }
            """.trimIndent()
        )

        assertThat(decoded.llmBaseUrl).isEqualTo("https://llm.example.test/v1")
        assertThat(decoded.llmModel).isEmpty()
        assertThat(decoded.llmApiKey).isEmpty()
        assertThat(decoded.asrBaseUrl).isEmpty()
        assertThat(decoded.embeddingApiKey).isEmpty()
        assertThat(decoded.connectTimeoutMs)
            .isEqualTo(ProviderConfig.DEFAULT_CONNECT_TIMEOUT_MS)
        assertThat(decoded.readTimeoutMs)
            .isEqualTo(ProviderConfig.DEFAULT_READ_TIMEOUT_MS)
    }

    @Test
    fun null_values_are_treated_as_empty_strings() {
        val decoded = ProviderConfigCodec.decode(
            """
            {
              "version": 1,
              "llm": {"baseUrl": null, "model": null, "apiKey": null}
            }
            """.trimIndent()
        )

        assertThat(decoded.llmBaseUrl).isEmpty()
        assertThat(decoded.llmModel).isEmpty()
        assertThat(decoded.llmApiKey).isEmpty()
    }

    @Test
    fun toString_masks_api_keys() {
        val config = ProviderConfig(
            llmApiKey = "sk-llm-secret",
            asrApiKey = "sk-asr-secret",
            embeddingApiKey = "sk-embed-secret"
        )

        val rendered = config.toString()

        assertThat(rendered).doesNotContain("sk-llm-secret")
        assertThat(rendered).doesNotContain("sk-asr-secret")
        assertThat(rendered).doesNotContain("sk-embed-secret")
        assertThat(rendered).contains("llmApiKey=***")
        assertThat(config.withoutSecrets().llmApiKey).isEmpty()
    }
}

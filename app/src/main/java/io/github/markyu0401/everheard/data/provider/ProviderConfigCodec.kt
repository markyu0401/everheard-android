package io.github.markyu0401.everheard.data.provider

import org.json.JSONObject

/**
 * Versioned plaintext JSON shape for [ProviderConfig].
 *
 * This codec only owns the in-memory document shape; encryption at rest is the
 * responsibility of [ProviderConfigStore]. A missing, blank, or corrupt document
 * decodes to an unconfigured [ProviderConfig] rather than throwing, so a broken
 * settings file cannot crash app startup.
 */
object ProviderConfigCodec {

    const val VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_LLM = "llm"
    private const val KEY_ASR = "asr"
    private const val KEY_EMBEDDING = "embedding"
    private const val KEY_TIMEOUTS = "timeouts"
    private const val KEY_BASE_URL = "baseUrl"
    private const val KEY_MODEL = "model"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_CONNECT_MS = "connectMs"
    private const val KEY_READ_MS = "readMs"

    fun encode(config: ProviderConfig): String {
        val root = JSONObject().apply {
            put(KEY_VERSION, VERSION)
            put(
                KEY_LLM,
                providerObject(config.llmBaseUrl, config.llmModel, config.llmApiKey)
            )
            put(
                KEY_ASR,
                providerObject(config.asrBaseUrl, config.asrModel, config.asrApiKey)
            )
            put(
                KEY_EMBEDDING,
                providerObject(
                    config.embeddingBaseUrl,
                    config.embeddingModel,
                    config.embeddingApiKey
                )
            )
            put(KEY_TIMEOUTS, JSONObject().apply {
                put(KEY_CONNECT_MS, config.connectTimeoutMs)
                put(KEY_READ_MS, config.readTimeoutMs)
            })
        }
        return root.toString()
    }

    fun decode(raw: String): ProviderConfig {
        if (raw.isBlank()) return ProviderConfig()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return ProviderConfig()
        val llm = root.optJSONObject(KEY_LLM)
        val asr = root.optJSONObject(KEY_ASR)
        val embedding = root.optJSONObject(KEY_EMBEDDING)
        val timeouts = root.optJSONObject(KEY_TIMEOUTS)
        return ProviderConfig(
            llmBaseUrl = llm.string(KEY_BASE_URL),
            llmModel = llm.string(KEY_MODEL),
            llmApiKey = llm.string(KEY_API_KEY),
            asrBaseUrl = asr.string(KEY_BASE_URL),
            asrModel = asr.string(KEY_MODEL),
            asrApiKey = asr.string(KEY_API_KEY),
            embeddingBaseUrl = embedding.string(KEY_BASE_URL),
            embeddingModel = embedding.string(KEY_MODEL),
            embeddingApiKey = embedding.string(KEY_API_KEY),
            connectTimeoutMs = timeouts.intOr(
                KEY_CONNECT_MS,
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT_MS
            ),
            readTimeoutMs = timeouts.intOr(
                KEY_READ_MS,
                ProviderConfig.DEFAULT_READ_TIMEOUT_MS
            )
        )
    }

    private fun providerObject(baseUrl: String, model: String, apiKey: String): JSONObject =
        JSONObject().apply {
            put(KEY_BASE_URL, baseUrl)
            put(KEY_MODEL, model)
            put(KEY_API_KEY, apiKey)
        }

    private fun JSONObject?.string(key: String): String {
        if (this == null || !has(key) || isNull(key)) return ""
        return optString(key)
    }

    private fun JSONObject?.intOr(key: String, fallback: Int): Int {
        if (this == null || !has(key) || isNull(key)) return fallback
        return optInt(key, fallback)
    }
}

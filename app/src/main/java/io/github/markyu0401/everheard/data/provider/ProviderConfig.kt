package io.github.markyu0401.everheard.data.provider

/**
 * Bring-your-own OpenAI-compatible provider settings.
 *
 * The API keys are secrets. Never log this object directly ([toString] masks
 * them), never put them in SharedPreferences plaintext, and only persist it
 * through [ProviderConfigStore], which encrypts the JSON document with a key
 * held by Android Keystore.
 *
 * The ASR and embedding fields are configuration-only for now; the LLM fields
 * drive [LlmMemoryAnalyzer].
 */
data class ProviderConfig(
    val llmBaseUrl: String = "",
    val llmModel: String = "",
    val llmApiKey: String = "",
    val asrBaseUrl: String = "",
    val asrModel: String = "",
    val asrApiKey: String = "",
    val embeddingBaseUrl: String = "",
    val embeddingModel: String = "",
    val embeddingApiKey: String = "",
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
) {

    /** True when a remote LLM can be called (a base URL and model are set). */
    val llmConfigured: Boolean
        get() = llmBaseUrl.isNotBlank() && llmModel.isNotBlank()

    val asrConfigured: Boolean
        get() = asrBaseUrl.isNotBlank() && asrModel.isNotBlank()

    val embeddingConfigured: Boolean
        get() = embeddingBaseUrl.isNotBlank() && embeddingModel.isNotBlank()

    /** A copy safe to show in diagnostics: every secret is removed. */
    fun withoutSecrets(): ProviderConfig = copy(
        llmApiKey = "",
        asrApiKey = "",
        embeddingApiKey = ""
    )

    /**
     * Redacts secrets so an accidental log line cannot leak an API key. The
     * data class' generated toString would otherwise print every field.
     */
    override fun toString(): String = buildString {
        append("ProviderConfig(")
        append("llmBaseUrl=").append(llmBaseUrl)
        append(", llmModel=").append(llmModel)
        append(", llmApiKey=").append(mask(llmApiKey))
        append(", asrBaseUrl=").append(asrBaseUrl)
        append(", asrModel=").append(asrModel)
        append(", asrApiKey=").append(mask(asrApiKey))
        append(", embeddingBaseUrl=").append(embeddingBaseUrl)
        append(", embeddingModel=").append(embeddingModel)
        append(", embeddingApiKey=").append(mask(embeddingApiKey))
        append(", connectTimeoutMs=").append(connectTimeoutMs)
        append(", readTimeoutMs=").append(readTimeoutMs)
        append(")")
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_MS = 60_000

        private const val MASKED = "***"
        private const val UNSET = "<unset>"

        private fun mask(secret: String): String =
            if (secret.isBlank()) UNSET else MASKED
    }
}

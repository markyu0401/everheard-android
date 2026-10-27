package io.github.markyu0401.everheard.data.provider

import java.net.HttpURLConnection
import java.net.URL

data class ProviderTestResult(
    val ok: Boolean,
    val message: String
)

/**
 * Minimal connection check for an OpenAI-compatible endpoint. It calls
 * `GET {baseUrl}/models` and never logs the API key.
 */
object ProviderTester {

    fun testLlm(config: ProviderConfig): ProviderTestResult {
        val baseUrl = config.llmBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return ProviderTestResult(false, "Set the LLM base URL first.")
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return ProviderTestResult(false, "Base URL must start with http:// or https://.")
        }
        return try {
            val connection = (URL("$baseUrl/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = config.connectTimeoutMs.coerceAtLeast(1_000)
                readTimeout = config.readTimeoutMs.coerceAtLeast(1_000)
                setRequestProperty("Accept", "application/json")
                if (config.llmApiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.llmApiKey.trim()}")
                }
            }
            try {
                val status = connection.responseCode
                if (status in 200..299) {
                    ProviderTestResult(true, "Connected (HTTP $status).")
                } else {
                    ProviderTestResult(false, "HTTP $status from the provider.")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            ProviderTestResult(false, "Connection failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

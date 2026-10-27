package io.github.markyu0401.everheard.data.provider

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * One JSON POST to an OpenAI-compatible endpoint. Kept as a data class so tests
 * can inspect the exact request the analyzer builds without opening a socket.
 */
data class ProviderHttpRequest(
    val url: String,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val connectTimeoutMs: Int = ProviderConfig.DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = ProviderConfig.DEFAULT_READ_TIMEOUT_MS
) {
    /**
     * Never print the bearer token or the transcript body. A data class'
     * generated toString would leak both into an accidental log line.
     */
    override fun toString(): String {
        val safeHeaders = headers.mapValues { (name, value) ->
            if (name.equals("Authorization", ignoreCase = true)) "***" else value
        }
        return "ProviderHttpRequest(url=$url, headers=$safeHeaders, " +
            "connectTimeoutMs=$connectTimeoutMs, readTimeoutMs=$readTimeoutMs, " +
            "body=<${body.length} chars>)"
    }
}

data class ProviderHttpResponse(
    val statusCode: Int,
    val body: String
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299

    /** Avoid dumping provider/transcript content into logs. */
    override fun toString(): String =
        "ProviderHttpResponse(statusCode=$statusCode, body=<${body.length} chars>)"
}

/**
 * Minimal injectable transport. Production uses [HttpUrlConnectionTransport];
 * tests inject a fake so no real API or secret is ever touched.
 */
interface ProviderTransport {
    @Throws(IOException::class)
    fun post(request: ProviderHttpRequest): ProviderHttpResponse
}

/**
 * `java.net.HttpURLConnection` implementation (no OkHttp dependency). Timeouts
 * come from [ProviderHttpRequest]; non-2xx responses are returned, not thrown,
 * so the analyzer can produce a precise failure message.
 */
class HttpUrlConnectionTransport : ProviderTransport {

    override fun post(request: ProviderHttpRequest): ProviderHttpResponse {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = request.connectTimeoutMs.coerceAtLeast(1_000)
            readTimeout = request.readTimeoutMs.coerceAtLeast(1_000)
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            request.headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
        }
        try {
            connection.outputStream.use { output ->
                output.write(request.body.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return ProviderHttpResponse(statusCode = status, body = body)
        } finally {
            connection.disconnect()
        }
    }
}

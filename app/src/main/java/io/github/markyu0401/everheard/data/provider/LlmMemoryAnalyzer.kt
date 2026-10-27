package io.github.markyu0401.everheard.data.provider

import io.github.markyu0401.everheard.data.memory.MemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional remote memory analyzer for any OpenAI-compatible chat endpoint.
 *
 * The request is a plain `POST {baseUrl}/chat/completions` with `temperature: 0`
 * and `response_format: {"type":"json_object"}`. If the server rejects
 * `response_format`, one retry without it is attempted.
 *
 * [analyzeResult] never fabricates a memory: transport, timeout, HTTP, and JSON
 * problems come back as [LlmAnalysisResult.Failure]. [analyze] is the
 * drop-in [MemoryAnalyzer] adapter and falls back to [RuleBasedMemoryAnalyzer]
 * on any failure, which keeps the remote analyzer optional.
 *
 * All methods are blocking and must be called off the main thread.
 */
class LlmMemoryAnalyzer(
    private val configProvider: () -> ProviderConfig,
    private val transport: ProviderTransport = HttpUrlConnectionTransport(),
    private val fallback: MemoryAnalyzer = RuleBasedMemoryAnalyzer(),
    private val now: () -> Long = System::currentTimeMillis
) : MemoryAnalyzer {

    constructor(
        config: ProviderConfig,
        transport: ProviderTransport = HttpUrlConnectionTransport(),
        fallback: MemoryAnalyzer = RuleBasedMemoryAnalyzer(),
        now: () -> Long = System::currentTimeMillis
    ) : this({ config }, transport, fallback, now)

    companion object {
        const val NAME = "llm-v1"

        private const val MIN_TIMEOUT_MS = 1_000
        private const val MAX_CONNECT_TIMEOUT_MS = 120_000
        private const val MAX_READ_TIMEOUT_MS = 300_000
        private const val MAX_PROMPT_CHARS = 24_000
        private const val MAX_ERROR_BODY_CHARS = 200

        private val SYSTEM_PROMPT = """
            You extract structured memory from one meeting transcript.
            Return exactly one JSON object and nothing else: no markdown fences, no prose.

            JSON schema:
            {
              "summary": "one short factual paragraph in the transcript language",
              "events": [
                {"type": "progress|decision|commitment|risk|inspiration", "text": "...", "quote": "...", "utteranceId": "...", "startMs": 0, "speaker": "..."}
              ],
              "commitments": [
                {"text": "...", "owner": "...", "dueHint": "...", "quote": "...", "utteranceId": "...", "startMs": 0}
              ],
              "inspirations": [
                {"text": "...", "quote": "...", "utteranceId": "...", "startMs": 0}
              ]
            }

            Rules:
            - Every event, commitment, and inspiration must include "quote": an exact substring copied from the transcript line named by "utteranceId".
            - Never paraphrase, translate, or invent a quote. If no line supports an item, omit the item.
            - Allowed event types are progress, decision, commitment, risk, inspiration. Never use another type.
            - "utteranceId" must be an id present in the transcript. "startMs" must be that line's start time.
            - Use empty arrays when nothing applies. Keep the summary factual and short.
        """.trimIndent()
    }

    /** True when [configProvider] currently returns a usable LLM endpoint. */
    fun isConfigured(): Boolean = configProvider().llmConfigured

    /**
     * Runs the remote request and returns an explicit success or failure.
     * A non-2xx status, timeout, connection error, or malformed model output is
     * a [LlmAnalysisResult.Failure]; no placeholder memory is produced.
     */
    fun analyzeResult(session: TranscriptSession): LlmAnalysisResult {
        val config = try {
            configProvider()
        } catch (e: Exception) {
            return LlmAnalysisResult.Failure(
                "Unable to read provider config: ${e.message ?: e.javaClass.simpleName}",
                e
            )
        }
        if (!config.llmConfigured) {
            return LlmAnalysisResult.Failure(
                "LLM provider is not configured: set llmBaseUrl and llmModel"
            )
        }
        val baseUrl = config.llmBaseUrl.trim()
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return LlmAnalysisResult.Failure(
                "LLM base URL must start with http:// or https://"
            )
        }
        val utterances = session.utterances.filter { it.text.isNotBlank() }
        if (utterances.isEmpty()) {
            return LlmAnalysisResult.Failure("Transcript has no utterances to analyze")
        }

        val url = chatCompletionsUrl(baseUrl)
        val headers = buildMap {
            put("Content-Type", "application/json; charset=utf-8")
            put("Accept", "application/json")
            if (config.llmApiKey.isNotBlank()) {
                put("Authorization", "Bearer ${config.llmApiKey.trim()}")
            }
        }
        val connectTimeoutMs = config.connectTimeoutMs
            .coerceIn(MIN_TIMEOUT_MS, MAX_CONNECT_TIMEOUT_MS)
        val readTimeoutMs = config.readTimeoutMs
            .coerceIn(MIN_TIMEOUT_MS, MAX_READ_TIMEOUT_MS)

        val response = try {
            val first = transport.post(
                ProviderHttpRequest(
                    url = url,
                    body = buildRequestBody(config, session, includeResponseFormat = true),
                    headers = headers,
                    connectTimeoutMs = connectTimeoutMs,
                    readTimeoutMs = readTimeoutMs
                )
            )
            if (shouldRetryWithoutResponseFormat(first)) {
                transport.post(
                    ProviderHttpRequest(
                        url = url,
                        body = buildRequestBody(config, session, includeResponseFormat = false),
                        headers = headers,
                        connectTimeoutMs = connectTimeoutMs,
                        readTimeoutMs = readTimeoutMs
                    )
                )
            } else {
                first
            }
        } catch (e: Exception) {
            return LlmAnalysisResult.Failure(
                "LLM request failed: ${e.message ?: e.javaClass.simpleName}",
                e
            )
        }

        if (!response.isSuccessful) {
            return LlmAnalysisResult.Failure(
                "LLM request failed with HTTP ${response.statusCode}${bodySnippet(response.body)}"
            )
        }

        val content = extractContent(response.body)
            ?: return LlmAnalysisResult.Failure(
                "LLM response did not include choices[0].message.content"
            )
        if (content.isBlank()) {
            return LlmAnalysisResult.Failure("LLM returned empty content")
        }
        return try {
            LlmMemoryParser.parse(content, session, now())
        } catch (e: Exception) {
            LlmAnalysisResult.Failure(
                "Failed to parse LLM response: ${e.message ?: e.javaClass.simpleName}",
                e
            )
        }
    }

    /**
     * Drop-in [MemoryAnalyzer] behavior: remote first, deterministic rule
     * analyzer on any failure. Use [analyzeResult] when the caller needs to
     * surface the reason instead of silently falling back.
     */
    fun analyzeOrFallback(session: TranscriptSession): SessionMemory =
        when (val result = analyzeResult(session)) {
            is LlmAnalysisResult.Success -> result.memory
            is LlmAnalysisResult.Failure -> fallback.analyze(session)
        }

    override fun analyze(session: TranscriptSession): SessionMemory =
        analyzeOrFallback(session)

    private fun buildRequestBody(
        config: ProviderConfig,
        session: TranscriptSession,
        includeResponseFormat: Boolean
    ): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", buildTranscriptPrompt(session))
            })
        }
        return JSONObject().apply {
            put("model", config.llmModel.trim())
            put("messages", messages)
            put("temperature", 0)
            if (includeResponseFormat) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }.toString()
    }

    private fun buildTranscriptPrompt(session: TranscriptSession): String {
        val builder = StringBuilder()
        builder.append("Session id: ").append(session.id).append('\n')
        if (session.title.isNotBlank()) {
            builder.append("Title: ").append(session.title.trim()).append('\n')
        }
        builder.append("Transcript lines:\n")
        var truncated = false
        for (utterance in session.utterances) {
            if (utterance.text.isBlank()) continue
            val speaker = utterance.speaker
                ?.takeIf { it.isNotBlank() }
                ?.let { " speaker=$it" }
                .orEmpty()
            val line = "[${utterance.id}] ${utterance.startMs}ms$speaker: " +
                "${utterance.text.trim()}\n"
            if (builder.length + line.length > MAX_PROMPT_CHARS) {
                truncated = true
                break
            }
            builder.append(line)
        }
        if (truncated) {
            builder.append("[transcript truncated]\n")
        }
        return builder.toString()
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    /**
     * Some OpenAI-compatible servers do not implement `response_format`. Retry
     * once without it only when the error clearly points at that field.
     */
    private fun shouldRetryWithoutResponseFormat(response: ProviderHttpResponse): Boolean {
        if (response.statusCode != 400 && response.statusCode != 422) return false
        val body = response.body.lowercase()
        return body.contains("response_format") ||
            body.contains("json_object") ||
            body.contains("unsupported")
    }

    private fun extractContent(raw: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val choices = root.optJSONArray("choices") ?: return null
        val choice = choices.optJSONObject(0) ?: return null
        val message = choice.optJSONObject("message")
        val content = message?.opt("content")
        if (content == null || content == JSONObject.NULL) {
            return choice.optString("text").takeIf { it.isNotBlank() }
        }
        return when (content) {
            is String -> content
            is JSONObject -> content.toString()
            is JSONArray -> {
                val parts = (0 until content.length()).mapNotNull { index ->
                    when (val part = content.opt(index)) {
                        is String -> part.takeIf { it.isNotBlank() }
                        is JSONObject -> part.optString("text").takeIf { it.isNotBlank() }
                        else -> null
                    }
                }
                parts.joinToString("\n").takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun bodySnippet(body: String): String {
        val trimmed = body.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return ""
        return ": " + trimmed.take(MAX_ERROR_BODY_CHARS)
    }
}

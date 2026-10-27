package io.github.markyu0401.everheard.data.provider

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class LlmMemoryAnalyzerTest {

    private val session = TranscriptSession(
        id = "s1",
        startedAt = 0L,
        utterances = listOf(
            TranscriptUtterance("u1", "s1", 0L, 1_000L, "We decided to ship on Friday.", "me"),
            TranscriptUtterance("u2", "s1", 2_000L, 3_000L, "I will send the draft by tomorrow.", "me"),
            TranscriptUtterance("u3", "s1", 4_000L, 5_000L, "What if we add a timeline?", null)
        )
    )

    private val config = ProviderConfig(
        llmBaseUrl = "https://llm.example.test/v1",
        llmModel = "gpt-test",
        llmApiKey = "sk-test-key",
        connectTimeoutMs = 4_000,
        readTimeoutMs = 30_000
    )

    private val validContent = """
        {
          "summary": "The team will ship Friday and send the draft tomorrow.",
          "events": [
            {"type": "decision", "text": "Ship on Friday", "quote": "We decided to ship on Friday.", "utteranceId": "u1", "startMs": 0, "speaker": "me"},
            {"type": "commitment", "text": "Send the draft", "quote": "I will send the draft by tomorrow.", "utteranceId": "u2", "startMs": 2000}
          ],
          "commitments": [
            {"text": "Send the draft", "owner": "me", "dueHint": "tomorrow", "quote": "I will send the draft by tomorrow.", "utteranceId": "u2", "startMs": 2000}
          ],
          "inspirations": [
            {"text": "Add a timeline", "quote": "What if we add a timeline?", "utteranceId": "u3", "startMs": 4000}
          ]
        }
    """.trimIndent()

    @Test
    fun remote_success_maps_to_llm_v1_memory() {
        val transport = FakeTransport(chatResponse(validContent))
        val analyzer = LlmMemoryAnalyzer(config = config, transport = transport, now = { 42L })

        val memory = success(analyzer.analyzeResult(session))

        assertThat(memory.analyzer).isEqualTo(LlmMemoryAnalyzer.NAME)
        assertThat(memory.analyzer).isEqualTo("llm-v1")
        assertThat(memory.generatedAt).isEqualTo(42L)
        assertThat(memory.events).hasSize(2)
        assertThat(memory.events[0].type).isEqualTo(MemoryEventType.DECISION)
        assertThat(memory.events[1].type).isEqualTo(MemoryEventType.COMMITMENT)
        assertThat(memory.commitments).hasSize(1)
        assertThat(memory.inspirations).hasSize(1)
        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun request_targets_chat_completions_with_required_fields() {
        val transport = FakeTransport(chatResponse(validContent))
        val analyzer = LlmMemoryAnalyzer(config, transport)

        analyzer.analyzeResult(session)

        val request = transport.requests.single()
        assertThat(request.url)
            .isEqualTo("https://llm.example.test/v1/chat/completions")
        assertThat(request.connectTimeoutMs).isEqualTo(4_000)
        assertThat(request.readTimeoutMs).isEqualTo(30_000)
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer sk-test-key")
        assertThat(request.headers["Content-Type"]).contains("application/json")

        val body = JSONObject(request.body)
        assertThat(body.getString("model")).isEqualTo("gpt-test")
        assertThat(body.getInt("temperature")).isEqualTo(0)
        assertThat(body.getJSONObject("response_format").getString("type"))
            .isEqualTo("json_object")
        val messages = body.getJSONArray("messages")
        assertThat(messages.length()).isEqualTo(2)
        assertThat(messages.getJSONObject(0).getString("role")).isEqualTo("system")
        assertThat(messages.getJSONObject(1).getString("role")).isEqualTo("user")
        assertThat(messages.getJSONObject(1).getString("content")).contains("[u1] 0ms")
        assertThat(messages.getJSONObject(1).getString("content"))
            .contains("We decided to ship on Friday.")
    }

    @Test
    fun blank_api_key_omits_the_authorization_header() {
        val transport = FakeTransport(chatResponse(validContent))
        val analyzer = LlmMemoryAnalyzer(
            config.copy(llmApiKey = ""),
            transport
        )

        analyzer.analyzeResult(session)

        assertThat(transport.requests.single().headers)
            .doesNotContainKey("Authorization")
    }

    @Test
    fun invalid_timeouts_are_clamped_to_sane_minimums() {
        val transport = FakeTransport(chatResponse(validContent))
        val analyzer = LlmMemoryAnalyzer(
            config.copy(connectTimeoutMs = 0, readTimeoutMs = -1),
            transport
        )

        analyzer.analyzeResult(session)

        val request = transport.requests.single()
        assertThat(request.connectTimeoutMs).isEqualTo(1_000)
        assertThat(request.readTimeoutMs).isEqualTo(1_000)
    }

    @Test
    fun response_content_array_is_joined_from_text_parts() {
        val response = ProviderHttpResponse(
            statusCode = 200,
            body = JSONObject().apply {
                put("choices", JSONArray().apply {
                    put(JSONObject().apply {
                        put("message", JSONObject().put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", validContent)
                            })
                        }))
                    })
                })
            }.toString()
        )
        val transport = FakeTransport(response)

        val memory = success(LlmMemoryAnalyzer(config, transport).analyzeResult(session))

        assertThat(memory.events).hasSize(2)
        assertThat(memory.analyzer).isEqualTo(LlmMemoryAnalyzer.NAME)
    }

    @Test
    fun request_to_string_does_not_leak_the_api_key_or_transcript() {
        val transport = FakeTransport(chatResponse(validContent))

        LlmMemoryAnalyzer(config, transport).analyzeResult(session)

        val rendered = transport.requests.single().toString()
        assertThat(rendered).doesNotContain("sk-test-key")
        assertThat(rendered).doesNotContain("We decided to ship on Friday.")
        assertThat(rendered).contains("Authorization=***")
    }

    @Test
    fun http_error_returns_a_failure_and_analyze_falls_back_to_rules() {
        val transport = FakeTransport(
            ProviderHttpResponse(500, """{"error":{"message":"upstream exploded"}}""")
        )
        val analyzer = LlmMemoryAnalyzer(config, transport)

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("HTTP 500")
        assertThat(failure.reason).contains("upstream exploded")

        val fallback = analyzer.analyze(session)
        assertThat(fallback.analyzer).isEqualTo(RuleBasedMemoryAnalyzer.NAME)
        assertThat(fallback.events).isNotEmpty()
        assertThat(fallback.commitments).isNotEmpty()
    }

    @Test
    fun transport_exception_returns_a_failure_and_analyze_falls_back_to_rules() {
        val transport = FakeTransport()
        transport.error = IOException("connection reset")
        val analyzer = LlmMemoryAnalyzer(config, transport)

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("LLM request failed")
        assertThat(failure.cause).isInstanceOf(IOException::class.java)

        val fallback = analyzer.analyze(session)
        assertThat(fallback.analyzer).isEqualTo(RuleBasedMemoryAnalyzer.NAME)
        assertThat(fallback.sessionId).isEqualTo("s1")
    }

    @Test
    fun malformed_model_json_is_a_failure_not_a_memory() {
        val transport = FakeTransport(chatResponse("this is not json"))
        val analyzer = LlmMemoryAnalyzer(config, transport)

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("malformed")
    }

    @Test
    fun response_without_message_content_is_a_failure() {
        val transport = FakeTransport(
            ProviderHttpResponse(200, """{"choices":[]}""")
        )
        val analyzer = LlmMemoryAnalyzer(config, transport)

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("choices[0].message.content")
    }

    @Test
    fun unconfigured_provider_is_a_failure_without_network_call() {
        val transport = FakeTransport()
        val analyzer = LlmMemoryAnalyzer(ProviderConfig(), transport)

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("not configured")
        assertThat(transport.requests).isEmpty()

        val fallback = analyzer.analyze(session)
        assertThat(fallback.analyzer).isEqualTo(RuleBasedMemoryAnalyzer.NAME)
    }

    @Test
    fun non_http_base_url_is_a_failure_without_network_call() {
        val transport = FakeTransport()
        val analyzer = LlmMemoryAnalyzer(
            config.copy(llmBaseUrl = "ftp://llm.example.test"),
            transport
        )

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("http:// or https://")
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun empty_transcript_is_a_failure_without_network_call() {
        val transport = FakeTransport()
        val analyzer = LlmMemoryAnalyzer(config, transport)
        val empty = TranscriptSession(id = "empty", startedAt = 0L)

        val failure = failure(analyzer.analyzeResult(empty))

        assertThat(failure.reason).contains("no utterances")
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun retries_once_without_response_format_when_the_server_rejects_it() {
        val transport = FakeTransport(
            ProviderHttpResponse(
                400,
                """{"error":{"message":"response_format is not supported"}}"""
            ),
            chatResponse(validContent)
        )
        val analyzer = LlmMemoryAnalyzer(config, transport)

        val memory = success(analyzer.analyzeResult(session))

        assertThat(memory.events).hasSize(2)
        assertThat(transport.requests).hasSize(2)
        assertThat(JSONObject(transport.requests[0].body).has("response_format")).isTrue()
        assertThat(JSONObject(transport.requests[1].body).has("response_format")).isFalse()
    }

    @Test
    fun does_not_retry_authentication_errors() {
        val transport = FakeTransport(
            ProviderHttpResponse(401, """{"error":{"message":"bad key"}}""")
        )
        val analyzer = LlmMemoryAnalyzer(config, transport)

        val failure = failure(analyzer.analyzeResult(session))

        assertThat(failure.reason).contains("HTTP 401")
        assertThat(transport.requests).hasSize(1)
    }

    private fun chatResponse(content: String, statusCode: Int = 200): ProviderHttpResponse =
        ProviderHttpResponse(
            statusCode = statusCode,
            body = JSONObject().apply {
                put("choices", JSONArray().apply {
                    put(JSONObject().apply {
                        put("message", JSONObject().put("content", content))
                    })
                })
            }.toString()
        )

    private fun success(result: LlmAnalysisResult): SessionMemory {
        assertThat(result).isInstanceOf(LlmAnalysisResult.Success::class.java)
        return (result as LlmAnalysisResult.Success).memory
    }

    private fun failure(result: LlmAnalysisResult): LlmAnalysisResult.Failure {
        assertThat(result).isInstanceOf(LlmAnalysisResult.Failure::class.java)
        return result as LlmAnalysisResult.Failure
    }

    private class FakeTransport(
        private val responses: MutableList<ProviderHttpResponse>
    ) : ProviderTransport {

        constructor(vararg responses: ProviderHttpResponse) : this(responses.toMutableList())

        val requests = mutableListOf<ProviderHttpRequest>()
        var error: Exception? = null

        override fun post(request: ProviderHttpRequest): ProviderHttpResponse {
            requests += request
            error?.let { throw it }
            check(responses.isNotEmpty()) { "no fake response queued" }
            return responses.removeAt(0)
        }
    }
}

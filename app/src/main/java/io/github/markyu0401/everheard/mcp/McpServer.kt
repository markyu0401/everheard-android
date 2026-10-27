package io.github.markyu0401.everheard.mcp

import android.content.Context
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.person.PersonProfile
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * A deliberately small, read-only MCP server bound to 127.0.0.1.
 *
 * It implements the JSON-RPC subset needed by Claude/Cursor: initialize,
 * tools/list, tools/call, resources/list, resources/read, and ping. It is not a
 * general-purpose HTTP server; requests are one connection at a time and the
 * response is JSON (not SSE). All data comes from [McpRepository]; no write
 * tools exist.
 */
class McpServer(
    private val repository: McpRepository,
    private val token: String,
    private val requestedPort: Int = McpConfig.DEFAULT_PORT
) {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    var port: Int = requestedPort
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val socket = ServerSocket(
            requestedPort,
            50,
            InetAddress.getByName("127.0.0.1")
        )
        serverSocket = socket
        port = socket.localPort
        acceptThread = Thread({
            while (running.get()) {
                try {
                    val client = socket.accept()
                    Thread { handle(client) }.apply {
                        isDaemon = true
                        start()
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }, "everheard-mcp-accept").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            try {
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    writeResponse(output, 400, errorBody("bad request"))
                    return
                }
                val method = parts[0]
                val path = parts[1]
                val headers = readHeaders(input)
                val body = readBody(input, headers["content-length"]?.toIntOrNull() ?: 0)

                if (method == "GET" && path == "/health") {
                    writeResponse(output, 200, """{"status":"ok"}""")
                    return
                }
                if (method != "POST" || path != "/mcp") {
                    writeResponse(output, 404, errorBody("not found"))
                    return
                }
                if (!authorized(headers["authorization"])) {
                    writeResponse(output, 401, errorBody("unauthorized"))
                    return
                }
                val response = runCatching {
                    dispatch(body)
                }.getOrElse { throwable ->
                    rpcError(null, -32603, throwable.message ?: "internal error")
                }
                if (response == null) {
                    writeResponse(output, 202, "")
                } else {
                    writeResponse(output, 200, response.toString())
                }
            } catch (_: SocketTimeoutException) {
                writeResponse(output, 408, errorBody("timeout"))
            } catch (_: Exception) {
                runCatching { writeResponse(output, 500, errorBody("server error")) }
            }
        }
    }

    private fun authorized(header: String?): Boolean {
        if (token.isBlank() || header == null) return false
        val expected = "Bearer $token"
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            header.toByteArray(Charsets.UTF_8)
        )
    }

    private fun dispatch(raw: String): JSONObject? {
        val request = runCatching { JSONObject(raw) }.getOrNull()
            ?: return rpcError(null, -32700, "parse error")
        val id = if (request.has("id")) request.opt("id") else null
        val method = request.optString("method")
        val params = request.optJSONObject("params") ?: JSONObject()
        return when (method) {
            "initialize" -> rpcResult(
                id,
                JSONObject().apply {
                    put("protocolVersion", "2025-06-18")
                    put("serverInfo", JSONObject().apply {
                        put("name", "everheard")
                        put("version", "0.1.0")
                    })
                    put("capabilities", JSONObject().apply {
                        put("tools", JSONObject())
                        put("resources", JSONObject())
                    })
                }
            )
            "notifications/initialized" -> null
            "ping" -> rpcResult(id, JSONObject())
            "tools/list" -> rpcResult(
                id,
                JSONObject().put("tools", JSONArray().apply {
                    tools().forEach { put(it) }
                })
            )
            "tools/call" -> {
                val name = params.optString("name")
                val arguments = params.optJSONObject("arguments") ?: JSONObject()
                when (val result = callTool(name, arguments)) {
                    is ToolResult.Success -> rpcResult(
                        id,
                        JSONObject().apply {
                            put("content", JSONArray().put(JSONObject().apply {
                                put("type", "text")
                                put("text", result.text)
                            }))
                            put("isError", false)
                        }
                    )
                    is ToolResult.Failure -> rpcError(id, -32602, result.message)
                }
            }
            "resources/list" -> rpcResult(
                id,
                JSONObject().put("resources", JSONArray().apply {
                    resources().forEach { put(it) }
                })
            )
            "resources/read" -> {
                val uri = params.optString("uri")
                when (val result = readResource(uri)) {
                    is ResourceResult.Success -> rpcResult(
                        id,
                        JSONObject().put("contents", JSONArray().put(JSONObject().apply {
                            put("uri", uri)
                            put("mimeType", result.mimeType)
                            put("text", result.text)
                        }))
                    )
                    is ResourceResult.Failure -> rpcError(id, -32602, result.message)
                }
            }
            else -> rpcError(id, -32601, "method not found: $method")
        }
    }

    private fun tools(): List<JSONObject> = listOf(
        tool(
            "search_memory",
            "Search transcript utterances. Returns evidence quotes with session ids.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                    })
                    put("limit", JSONObject().apply {
                        put("type", "integer")
                        put("default", 10)
                    })
                })
                put("required", JSONArray().put("query"))
            }
        ),
        tool(
            "get_daily_timeline",
            "Return semantic memory events for one day (YYYY-MM-DD).",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("date", JSONObject().apply {
                    put("type", "string")
                }))
                put("required", JSONArray().put("date"))
            }
        ),
        tool(
            "list_commitments",
            "List commitments, optionally filtered by status.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("status", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("open").put("all"))
                }))
            }
        ),
        tool(
            "get_project",
            "Return a project timeline and open commitments.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("id", JSONObject().apply {
                    put("type", "string")
                }))
                put("required", JSONArray().put("id"))
            }
        ),
        tool(
            "get_session",
            "Return one session transcript and its structured memory.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("id", JSONObject().apply {
                    put("type", "string")
                }))
                put("required", JSONArray().put("id"))
            }
        ),
        tool(
            "list_inspirations",
            "List saved or extracted ideas with source quotes.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
        ),
        tool(
            "get_person",
            "Return a person profile with sessions, commitments and quotes.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("id", JSONObject().apply {
                    put("type", "string")
                }))
                put("required", JSONArray().put("id"))
            }
        ),
        tool(
            "get_insight",
            "Return the weekly insight report.",
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().put("id", JSONObject().apply {
                    put("type", "string")
                }))
            }
        )
    )

    private fun callTool(name: String, arguments: JSONObject): ToolResult = when (name) {
        "search_memory" -> searchMemory(
            arguments.optString("query"),
            arguments.optInt("limit", 10).coerceIn(1, 50)
        )
        "get_daily_timeline" -> dailyTimeline(arguments.optString("date"))
        "list_commitments" -> listCommitments(arguments.optString("status", "open"))
        "get_project" -> getProject(arguments.optString("id"))
        "get_session" -> getSession(arguments.optString("id"))
        "list_inspirations" -> listInspirations()
        "get_person" -> getPerson(arguments.optString("id"))
        "get_insight" -> getInsight(arguments.optString("id"))
        else -> ToolResult.Failure("unknown tool: $name")
    }

    private fun searchMemory(query: String, limit: Int): ToolResult {
        val needle = query.trim()
        if (needle.isEmpty()) return ToolResult.Failure("query is required")
        val sessions = repository.sessions().associateBy { it.id }
        val result = JSONArray()
        var count = 0
        for (session in sessions.values) {
            for (utterance in session.utterances) {
                if (count >= limit) break
                if (!utterance.text.contains(needle, ignoreCase = true)) continue
                result.put(JSONObject().apply {
                    put("sessionId", session.id)
                    put("sessionTitle", session.title)
                    put("utteranceId", utterance.id)
                    put("startMs", utterance.startMs)
                    put("quote", utterance.text)
                    utterance.speaker?.let { put("speaker", it) }
                })
                count++
            }
            if (count >= limit) break
        }
        return ToolResult.Success(result.toString())
    }

    private fun dailyTimeline(date: String): ToolResult {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
            ?: return ToolResult.Failure("date must be YYYY-MM-DD")
        val sessions = repository.sessions()
        val memories = repository.memories().associateBy { it.sessionId }
        val events = JSONArray()
        sessions.forEach { session ->
            val sessionDate = java.time.Instant.ofEpochMilli(session.startedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            if (sessionDate != parsed) return@forEach
            val memory = memories[session.id] ?: return@forEach
            memory.events.forEach { event ->
                events.put(eventJson(event, session))
            }
        }
        return ToolResult.Success(events.toString())
    }

    private fun listCommitments(status: String): ToolResult {
        val includeAll = status.equals("all", ignoreCase = true)
        val result = JSONArray()
        repository.memories().forEach { memory ->
            val session = repository.sessions().firstOrNull { it.id == memory.sessionId }
            memory.commitments.forEach { commitment ->
                if (!includeAll && commitment.status != Commitment.STATUS_OPEN) return@forEach
                result.put(JSONObject().apply {
                    put("sessionId", memory.sessionId)
                    put("sessionTitle", session?.title.orEmpty())
                    put("text", commitment.text)
                    commitment.owner?.let { put("owner", it) }
                    commitment.dueHint?.let { put("dueHint", it) }
                    put("status", commitment.status)
                    put("quote", commitment.evidence.quote)
                    put("utteranceId", commitment.evidence.utteranceId)
                    put("startMs", commitment.evidence.startMs)
                })
            }
        }
        return ToolResult.Success(result.toString())
    }

    private fun getProject(id: String): ToolResult {
        val project = repository.projects().firstOrNull { it.id == id }
            ?: return ToolResult.Failure("project not found: $id")
        return ToolResult.Success(projectJson(project).toString())
    }

    private fun getSession(id: String): ToolResult {
        val session = repository.sessions().firstOrNull { it.id == id }
            ?: return ToolResult.Failure("session not found: $id")
        val memory = repository.memories().firstOrNull { it.sessionId == id }
        return ToolResult.Success(sessionJson(session, memory).toString())
    }

    private fun listInspirations(): ToolResult {
        val result = JSONArray()
        repository.memories().forEach { memory ->
            val session = repository.sessions().firstOrNull { it.id == memory.sessionId }
            memory.inspirations.forEach { inspiration ->
                result.put(JSONObject().apply {
                    put("sessionId", memory.sessionId)
                    put("sessionTitle", session?.title.orEmpty())
                    put("text", inspiration.text)
                    put("quote", inspiration.evidence.quote)
                    put("utteranceId", inspiration.evidence.utteranceId)
                    put("startMs", inspiration.evidence.startMs)
                })
            }
        }
        return ToolResult.Success(result.toString())
    }

    private fun getPerson(id: String): ToolResult {
        val person = repository.people().firstOrNull { it.id == id }
            ?: return ToolResult.Failure("person not found: $id")
        return ToolResult.Success(personJson(person).toString())
    }

    private fun getInsight(id: String): ToolResult {
        val reports = repository.insights()
        if (reports.isEmpty()) return ToolResult.Failure("no insight report available")
        val report = reports.firstOrNull { it.periodEnd.toString() == id } ?: reports.first()
        return ToolResult.Success(insightJson(report).toString())
    }

    private fun resources(): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        repository.sessions().forEach { session ->
            result += resource(
                "everheard://session/${session.id}/summary",
                "Summary: ${session.title.ifBlank { session.id }}"
            )
            result += resource(
                "everheard://session/${session.id}/transcript",
                "Transcript: ${session.title.ifBlank { session.id }}"
            )
        }
        repository.memories().flatMap { it.inspirations }.forEach { inspiration ->
            result += resource(
                "everheard://inspiration/${inspiration.id}",
                inspiration.text.take(60)
            )
        }
        repository.projects().forEach { project ->
            result += resource(
                "everheard://project/${project.id}/timeline",
                "Project: ${project.name}"
            )
        }
        repository.people().forEach { person ->
            result += resource(
                "everheard://person/${person.id}",
                "Person: ${person.name}"
            )
        }
        return result
    }

    private fun readResource(uri: String): ResourceResult {
        if (!uri.startsWith("everheard://")) {
            return ResourceResult.Failure("unsupported resource URI")
        }
        val path = uri.removePrefix("everheard://")
        val parts = path.split('/')
        return when {
            parts.size == 3 && parts[0] == "session" && parts[2] == "summary" -> {
                val session = repository.sessions().firstOrNull { it.id == parts[1] }
                    ?: return ResourceResult.Failure("session not found")
                val memory = repository.memories().firstOrNull { it.sessionId == parts[1] }
                ResourceResult.Success(
                    "application/json",
                    sessionSummaryJson(session, memory).toString()
                )
            }
            parts.size == 3 && parts[0] == "session" && parts[2] == "transcript" -> {
                val session = repository.sessions().firstOrNull { it.id == parts[1] }
                    ?: return ResourceResult.Failure("session not found")
                ResourceResult.Success("text/plain", transcriptText(session))
            }
            parts.size == 2 && parts[0] == "inspiration" -> {
                val inspiration = repository.memories()
                    .flatMap { it.inspirations }
                    .firstOrNull { it.id == parts[1] }
                    ?: return ResourceResult.Failure("inspiration not found")
                ResourceResult.Success(
                    "application/json",
                    JSONObject().apply {
                        put("id", inspiration.id)
                        put("text", inspiration.text)
                        put("quote", inspiration.evidence.quote)
                        put("startMs", inspiration.evidence.startMs)
                    }.toString()
                )
            }
            parts.size == 3 && parts[0] == "project" && parts[2] == "timeline" -> {
                val project = repository.projects().firstOrNull { it.id == parts[1] }
                    ?: return ResourceResult.Failure("project not found")
                ResourceResult.Success("application/json", projectJson(project).toString())
            }
            parts.size == 2 && parts[0] == "person" -> {
                val person = repository.people().firstOrNull { it.id == parts[1] }
                    ?: return ResourceResult.Failure("person not found")
                ResourceResult.Success("application/json", personJson(person).toString())
            }
            else -> ResourceResult.Failure("unsupported resource URI")
        }
    }

    private fun sessionJson(session: TranscriptSession, memory: SessionMemory?): JSONObject =
        JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("startedAt", session.startedAt)
            session.endedAt?.let { put("endedAt", it) }
            put("utterances", JSONArray().apply {
                session.utterances.forEach { utterance ->
                    put(JSONObject().apply {
                        put("id", utterance.id)
                        put("startMs", utterance.startMs)
                        put("endMs", utterance.endMs)
                        put("text", utterance.text)
                        utterance.speaker?.let { put("speaker", it) }
                        put("marked", utterance.marked)
                    })
                }
            })
            memory?.let { put("memory", memoryJson(it)) }
        }

    private fun sessionSummaryJson(
        session: TranscriptSession,
        memory: SessionMemory?
    ): JSONObject = JSONObject().apply {
        put("sessionId", session.id)
        put("title", session.title)
        put("summary", memory?.summary.orEmpty())
        put("events", JSONArray().apply {
            memory?.events.orEmpty().forEach { put(eventJson(it, session)) }
        })
        put("commitments", JSONArray().apply {
            memory?.commitments.orEmpty().forEach { commitment ->
                put(commitmentJson(commitment, session.id))
            }
        })
    }

    private fun memoryJson(memory: SessionMemory): JSONObject = JSONObject().apply {
        put("summary", memory.summary)
        put("generatedAt", memory.generatedAt)
        put("analyzer", memory.analyzer)
        put("events", JSONArray().apply {
            memory.events.forEach { event ->
                put(JSONObject().apply {
                    put("type", event.type.name.lowercase())
                    put("text", event.text)
                    put("quote", event.evidence.quote)
                    put("utteranceId", event.evidence.utteranceId)
                    put("startMs", event.evidence.startMs)
                    event.evidence.speaker?.let { put("speaker", it) }
                })
            }
        })
        put("commitments", JSONArray().apply {
            memory.commitments.forEach { put(commitmentJson(it, memory.sessionId)) }
        })
        put("inspirations", JSONArray().apply {
            memory.inspirations.forEach { inspiration ->
                put(JSONObject().apply {
                    put("id", inspiration.id)
                    put("text", inspiration.text)
                    put("quote", inspiration.evidence.quote)
                    put("startMs", inspiration.evidence.startMs)
                })
            }
        })
    }

    private fun eventJson(event: MemoryEvent, session: TranscriptSession): JSONObject =
        JSONObject().apply {
            put("sessionId", session.id)
            put("sessionTitle", session.title)
            put("type", event.type.name.lowercase())
            put("text", event.text)
            put("quote", event.evidence.quote)
            put("utteranceId", event.evidence.utteranceId)
            put("startMs", event.evidence.startMs)
            event.evidence.speaker?.let { put("speaker", it) }
        }

    private fun commitmentJson(commitment: Commitment, sessionId: String): JSONObject =
        JSONObject().apply {
            put("sessionId", sessionId)
            put("text", commitment.text)
            commitment.owner?.let { put("owner", it) }
            commitment.dueHint?.let { put("dueHint", it) }
            put("status", commitment.status)
            put("quote", commitment.evidence.quote)
            put("utteranceId", commitment.evidence.utteranceId)
            put("startMs", commitment.evidence.startMs)
        }

    private fun projectJson(project: Project): JSONObject = JSONObject().apply {
        put("id", project.id)
        put("name", project.name)
        put("keywords", JSONArray().apply { project.keywords.forEach { put(it) } })
        put("sessionIds", JSONArray().apply { project.sessionIds.forEach { put(it) } })
        put("firstAt", project.firstAt)
        put("lastAt", project.lastAt)
        put("timeline", JSONArray().apply {
            project.events.forEach { projectEvent ->
                put(JSONObject().apply {
                    put("sessionId", projectEvent.sessionId)
                    put("sessionTitle", projectEvent.sessionTitle)
                    put("type", projectEvent.event.type.name.lowercase())
                    put("text", projectEvent.event.text)
                    put("quote", projectEvent.event.evidence.quote)
                    put("startMs", projectEvent.event.evidence.startMs)
                })
            }
        })
        put("openCommitments", JSONArray().apply {
            project.openCommitments.forEach { put(commitmentJson(it, "")) }
        })
    }

    private fun personJson(person: PersonProfile): JSONObject = JSONObject().apply {
        put("id", person.id)
        put("name", person.name)
        put("sessionIds", JSONArray().apply { person.sessionIds.forEach { put(it) } })
        put("lastTalkedAt", person.lastTalkedAt)
        put("openCommitments", JSONArray().apply {
            person.openCommitments.forEach { put(commitmentJson(it, "")) }
        })
        put("quotes", JSONArray().apply { person.quotes.forEach { put(it) } })
    }

    private fun insightJson(report: io.github.markyu0401.everheard.data.insight.InsightReport): JSONObject =
        JSONObject().apply {
            put("periodStart", report.periodStart)
            put("periodEnd", report.periodEnd)
            put("sessionCount", report.sessionCount)
            put("minutes", report.minutes)
            put("decisionCount", report.decisionCount)
            put("commitmentCount", report.commitmentCount)
            put("openCommitmentCount", report.openCommitmentCount)
            put("riskCount", report.riskCount)
            put("ideaCount", report.ideaCount)
            put("projectCount", report.projectCount)
            put("observations", JSONArray().apply {
                report.observations.forEach { observation ->
                    put(JSONObject().apply {
                        put("title", observation.title)
                        put("body", observation.body)
                        observation.action?.let { put("action", it) }
                        put("citations", JSONArray().apply {
                            observation.citations.forEach { citation ->
                                put(JSONObject().apply {
                                    put("sessionId", citation.sessionId)
                                    put("sessionTitle", citation.sessionTitle)
                                    put("quote", citation.quote)
                                    put("startMs", citation.startMs)
                                })
                            }
                        })
                    })
                }
            })
        }

    private fun transcriptText(session: TranscriptSession): String = buildString {
        appendLine(session.title.ifBlank { session.id })
        session.utterances.forEach { utterance ->
            append("[${utterance.startMs}ms]")
            utterance.speaker?.let { append(" $it") }
            append(": ${utterance.text}")
            appendLine()
        }
    }

    private fun tool(name: String, description: String, inputSchema: JSONObject): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", description)
            put("inputSchema", inputSchema)
        }

    private fun resource(uri: String, name: String): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("name", name)
        put("mimeType", "application/json")
    }

    private fun rpcResult(id: Any?, result: JSONObject): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id ?: JSONObject.NULL)
        put("result", result)
    }

    private fun rpcError(id: Any?, code: Int, message: String): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id ?: JSONObject.NULL)
        put("error", JSONObject().apply {
            put("code", code)
            put("message", message)
        })
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = mutableListOf<Byte>()
        var previous = -1
        while (true) {
            val current = input.read()
            if (current < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.UTF_8)
            if (current == '\n'.code && previous == '\r'.code) {
                bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray().toString(Charsets.UTF_8)
            }
            bytes += current.toByte()
            previous = current
        }
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isBlank()) break
            val index = line.indexOf(':')
            if (index <= 0) continue
            headers[line.substring(0, index).trim().lowercase()] =
                line.substring(index + 1).trim()
        }
        return headers
    }

    private fun readBody(input: BufferedInputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return bytes.copyOf(offset).toString(Charsets.UTF_8)
    }

    private fun writeResponse(output: BufferedOutputStream, status: Int, body: String) {
        val statusText = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            408 -> "Request Timeout"
            else -> "Internal Server Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private fun errorBody(message: String): String =
        JSONObject().put("error", message).toString()

    private sealed interface ToolResult {
        data class Success(val text: String) : ToolResult
        data class Failure(val message: String) : ToolResult
    }

    private sealed interface ResourceResult {
        data class Success(val mimeType: String, val text: String) : ResourceResult
        data class Failure(val message: String) : ResourceResult
    }
}

object McpServerManager {

    @Volatile
    private var server: McpServer? = null

    fun start(context: Context, config: McpConfig): Result<Int> {
        stop()
        if (config.token.isBlank()) {
            return Result.failure(IllegalStateException("MCP token is empty"))
        }
        return runCatching {
            val instance = McpServer(
                repository = DefaultMcpRepository(context.applicationContext),
                token = config.token,
                requestedPort = config.port
            )
            instance.start()
            server = instance
            instance.port
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }

    fun isRunning(): Boolean = server != null

    fun port(): Int? = server?.port
}

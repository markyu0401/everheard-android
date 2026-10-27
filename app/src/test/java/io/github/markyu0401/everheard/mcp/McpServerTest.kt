package io.github.markyu0401.everheard.mcp

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.insight.InsightBuilder
import io.github.markyu0401.everheard.data.insight.InsightReport
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.MemoryEvidence
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.person.PersonProfile
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import org.json.JSONObject
import org.junit.Test

class McpServerTest {

    private val session = TranscriptSession(
        id = "s1",
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_060_000L,
        title = "Timeline review",
        utterances = listOf(
            TranscriptUtterance(
                id = "u1",
                sessionId = "s1",
                startMs = 0L,
                endMs = 1_500L,
                text = "I will send the timeline by tomorrow.",
                speaker = "me"
            )
        )
    )
    private val memory = SessionMemory(
        sessionId = "s1",
        summary = "Send the timeline.",
        summaryEvidence = MemoryEvidence("u1", "I will send the timeline by tomorrow.", 0L, "me"),
        events = listOf(
            MemoryEvent(
                id = "e1",
                type = MemoryEventType.COMMITMENT,
                text = "Send the timeline",
                evidence = MemoryEvidence("u1", "I will send the timeline by tomorrow.", 0L, "me")
            )
        ),
        commitments = listOf(
            Commitment(
                id = "c1",
                text = "Send the timeline",
                owner = "me",
                dueHint = "tomorrow",
                evidence = MemoryEvidence("u1", "I will send the timeline by tomorrow.", 0L, "me")
            )
        )
    )
    private val project = Project(
        id = "p1",
        name = "Timeline review",
        keywords = listOf("timeline"),
        sessionIds = listOf("s1"),
        firstAt = session.startedAt,
        lastAt = session.startedAt
    )
    private val person = PersonProfile(
        id = "person1",
        name = "me",
        sessionIds = listOf("s1"),
        lastTalkedAt = session.startedAt
    )

    private class FakeRepository(
        private val sessions: List<TranscriptSession>,
        private val memories: List<SessionMemory>,
        private val projects: List<Project>,
        private val people: List<PersonProfile>
    ) : McpRepository {
        override fun sessions() = sessions
        override fun memories() = memories
        override fun projects() = projects
        override fun insights(): List<InsightReport> =
            listOf(InsightBuilder().build(sessions, memories, projects))
        override fun people() = people
    }

    @Test
    fun rejects_missing_token_and_serves_initialize() {
        val server = McpServer(
            repository = FakeRepository(listOf(session), listOf(memory), listOf(project), listOf(person)),
            token = "secret-token",
            requestedPort = 0
        )
        server.start()
        try {
            val unauthorized = post(server.port, """{"jsonrpc":"2.0","id":1,"method":"ping"}""")
            assertThat(unauthorized.first).isEqualTo(401)

            val response = post(
                server.port,
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
                token = "secret-token"
            )
            assertThat(response.first).isEqualTo(200)
            val json = JSONObject(response.second)
            assertThat(json.getJSONObject("result").getString("protocolVersion"))
                .isEqualTo("2025-06-18")
        } finally {
            server.stop()
        }
    }

    @Test
    fun lists_tools_and_calls_get_session() {
        val server = McpServer(
            repository = FakeRepository(listOf(session), listOf(memory), listOf(project), listOf(person)),
            token = "secret-token",
            requestedPort = 0
        )
        server.start()
        try {
            val list = post(
                server.port,
                """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",
                token = "secret-token"
            )
            assertThat(list.second).contains("search_memory")
            assertThat(list.second).contains("get_session")

            val call = post(
                server.port,
                """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_session","arguments":{"id":"s1"}}}""",
                token = "secret-token"
            )
            assertThat(call.first).isEqualTo(200)
            assertThat(call.second).contains("Timeline review")
            assertThat(call.second).contains("I will send the timeline by tomorrow.")
        } finally {
            server.stop()
        }
    }

    @Test
    fun unknown_tool_returns_jsonrpc_error() {
        val server = McpServer(
            repository = FakeRepository(listOf(session), listOf(memory), listOf(project), listOf(person)),
            token = "secret-token",
            requestedPort = 0
        )
        server.start()
        try {
            val response = post(
                server.port,
                """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"write_everything","arguments":{}}}""",
                token = "secret-token"
            )
            val json = JSONObject(response.second)
            assertThat(json.getJSONObject("error").getInt("code")).isEqualTo(-32602)
        } finally {
            server.stop()
        }
    }

    private fun post(port: Int, body: String, token: String? = null): Pair<Int, String> {
        Socket("127.0.0.1", port).use { socket ->
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.print("POST /mcp HTTP/1.1\r\n")
            writer.print("Host: 127.0.0.1\r\n")
            writer.print("Content-Type: application/json\r\n")
            writer.print("Content-Length: ${body.toByteArray().size}\r\n")
            if (token != null) writer.print("Authorization: Bearer $token\r\n")
            writer.print("\r\n")
            writer.print(body)
            writer.flush()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val statusLine = reader.readLine().orEmpty()
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val chars = CharArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = reader.read(chars, offset, contentLength - offset)
                if (read < 0) break
                offset += read
            }
            return status to String(chars, 0, offset)
        }
    }
}

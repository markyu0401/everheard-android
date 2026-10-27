package io.github.markyu0401.everheard.mcp

import io.github.markyu0401.everheard.data.insight.InsightBuilder
import io.github.markyu0401.everheard.data.insight.InsightReport
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.person.PersonBuilder
import io.github.markyu0401.everheard.data.person.PersonProfile
import io.github.markyu0401.everheard.data.project.Project
import io.github.markyu0401.everheard.data.project.ProjectBuilder
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Read-only data source for the local MCP server. The server never writes.
 */
interface McpRepository {
    fun sessions(): List<TranscriptSession>
    fun memories(): List<SessionMemory>
    fun projects(): List<Project>
    fun insights(): List<InsightReport>
    fun people(): List<PersonProfile>
}

class DefaultMcpRepository(context: Context) : McpRepository {

    private val transcriptStore = EncryptedTranscriptStore(
        File(context.filesDir, "transcripts.bin")
    )
    private val memoryStore = EncryptedMemoryStore(
        File(context.filesDir, "memory.bin")
    )
    private val projectBuilder = ProjectBuilder()
    private val insightBuilder = InsightBuilder()
    private val personBuilder = PersonBuilder()

    override fun sessions(): List<TranscriptSession> = runBlocking {
        transcriptStore.reload()
    }

    override fun memories(): List<SessionMemory> = runBlocking {
        memoryStore.reload()
    }

    override fun projects(): List<Project> =
        projectBuilder.build(sessions(), memories())

    override fun insights(): List<InsightReport> =
        listOf(insightBuilder.build(sessions(), memories(), projects()))

    override fun people(): List<PersonProfile> =
        personBuilder.build(sessions(), memories())
}

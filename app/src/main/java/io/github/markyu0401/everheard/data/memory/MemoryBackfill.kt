package io.github.markyu0401.everheard.data.memory

import io.github.markyu0401.everheard.data.provider.LlmMemoryAnalyzer
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.firstSpeechAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills in missing memories, and re-analyzes memories left by an older analyzer
 * (rule-v1 → rule-v2, or anything → llm-v1 once a provider is configured). Runs on [Dispatchers.IO] because the LLM analyzer is
 * a blocking HTTP call, and stops after two consecutive fallbacks so a broken
 * endpoint is not hammered on every screen load.
 *
 * @return how many memories were written.
 */
suspend fun backfillMemories(
    sessions: List<TranscriptSession>,
    memories: List<SessionMemory>,
    store: EncryptedMemoryStore,
    analyzer: MemoryAnalyzer,
    limit: Int
): Int = withContext(Dispatchers.IO) {
    if (limit <= 0) return@withContext 0
    val byId = memories.associateBy { it.sessionId }
    val candidates = sessions
        .filter { it.utterances.isNotEmpty() }
        .filter { session ->
            val existing = byId[session.id]
            existing == null || MemoryAnalyzerProvider.needsUpgrade(analyzer, existing)
        }
        .sortedByDescending { it.firstSpeechAt }
    val llm = analyzer is LlmMemoryAnalyzer
    var written = 0
    var consecutiveFallbacks = 0
    for (session in candidates.take(limit)) {
        val memory = runCatching { analyzer.analyze(session) }.getOrNull() ?: continue
        if (llm && memory.analyzer != LlmMemoryAnalyzer.NAME) {
            consecutiveFallbacks++
            if (consecutiveFallbacks >= 2) break
        } else {
            consecutiveFallbacks = 0
        }
        runCatching { store.save(memory) }
        written++
    }
    written
}

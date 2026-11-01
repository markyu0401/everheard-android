package io.github.markyu0401.everheard.data.memory

import android.content.Context
import io.github.markyu0401.everheard.data.provider.LlmMemoryAnalyzer
import io.github.markyu0401.everheard.data.provider.ProviderConfigStore

/**
 * The one place that decides which memory analyzer a screen should use.
 *
 * Every screen used to construct [RuleBasedMemoryAnalyzer] directly, so a
 * configured OpenAI-compatible endpoint was only consulted when a session was
 * opened by hand. With this provider, backfills and refreshes use llm-v1 as
 * soon as the provider settings are complete, and fall back to the offline
 * rule baseline otherwise.
 */
object MemoryAnalyzerProvider {

    suspend fun create(context: Context): MemoryAnalyzer {
        val config = runCatching {
            ProviderConfigStore.inDirectory(context.filesDir).load()
        }.getOrNull()
        return if (config != null && config.llmConfigured) {
            LlmMemoryAnalyzer(config)
        } else {
            RuleBasedMemoryAnalyzer()
        }
    }

    /** True when [memory] was produced by an older analyzer than [analyzer]. */
    fun needsUpgrade(analyzer: MemoryAnalyzer, memory: SessionMemory): Boolean =
        memory.analyzer != nameOf(analyzer)

    fun nameOf(analyzer: MemoryAnalyzer): String = when (analyzer) {
        is LlmMemoryAnalyzer -> LlmMemoryAnalyzer.NAME
        else -> RuleBasedMemoryAnalyzer.NAME
    }
}

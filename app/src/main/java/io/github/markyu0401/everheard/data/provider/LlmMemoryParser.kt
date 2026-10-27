package io.github.markyu0401.everheard.data.provider

import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.Inspiration
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.MemoryEvidence
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.json.JSONObject

/**
 * Explicit outcome of a remote analysis. [Failure] is returned instead of
 * inventing a memory; callers that want the offline baseline can use
 * [LlmMemoryAnalyzer.analyzeOrFallback].
 */
sealed interface LlmAnalysisResult {
    data class Success(val memory: SessionMemory) : LlmAnalysisResult
    data class Failure(val reason: String, val cause: Throwable? = null) : LlmAnalysisResult
}

/**
 * Parses and validates the JSON object returned by the LLM.
 *
 * Validation is evidence-first: every event, commitment, and inspiration must
 * name an utterance that exists in [session] and its `quote` must be a substring
 * of that utterance's text (whitespace-normalized, case-insensitive). Items that
 * fail are dropped; event types outside the allowed set are dropped. Unknown
 * fields are ignored. The result is never padded with invented content.
 */
object LlmMemoryParser {

    const val MAX_EVENTS = 32
    const val MAX_COMMITMENTS = 16
    const val MAX_INSPIRATIONS = 16

    private const val KEY_SUMMARY = "summary"
    private const val KEY_EVENTS = "events"
    private const val KEY_COMMITMENTS = "commitments"
    private const val KEY_INSPIRATIONS = "inspirations"
    private const val KEY_TYPE = "type"
    private const val KEY_TEXT = "text"
    private const val KEY_QUOTE = "quote"
    private const val KEY_UTTERANCE_ID = "utteranceId"
    private const val KEY_SPEAKER = "speaker"
    private const val KEY_OWNER = "owner"
    private const val KEY_DUE_HINT = "dueHint"

    private val WHITESPACE = Regex("\\s+")

    fun parse(
        content: String,
        session: TranscriptSession,
        generatedAt: Long = 0L
    ): LlmAnalysisResult {
        val root = parseRoot(content)
            ?: return LlmAnalysisResult.Failure("LLM returned malformed JSON")
        val utterances = session.utterances
            .filter { it.text.isNotBlank() }
            .associateBy { it.id }

        val events = mutableListOf<MemoryEvent>()
        root.optJSONArray(KEY_EVENTS)?.let { array ->
            for (index in 0 until array.length()) {
                if (events.size >= MAX_EVENTS) break
                val item = array.optJSONObject(index) ?: continue
                val type = eventType(item.string(KEY_TYPE)) ?: continue
                val evidence = evidenceFor(item, utterances) ?: continue
                val text = item.string(KEY_TEXT).trim().ifBlank { evidence.quote }
                events += MemoryEvent(
                    id = "${session.id}:llm:event:${events.size}",
                    type = type,
                    text = text,
                    evidence = evidence
                )
            }
        }

        val commitments = mutableListOf<Commitment>()
        root.optJSONArray(KEY_COMMITMENTS)?.let { array ->
            for (index in 0 until array.length()) {
                if (commitments.size >= MAX_COMMITMENTS) break
                val item = array.optJSONObject(index) ?: continue
                val evidence = evidenceFor(item, utterances) ?: continue
                val text = item.string(KEY_TEXT).trim().ifBlank { evidence.quote }
                commitments += Commitment(
                    id = "${session.id}:llm:commitment:${commitments.size}",
                    text = text,
                    owner = item.string(KEY_OWNER).trim().takeIf { it.isNotEmpty() },
                    dueHint = item.string(KEY_DUE_HINT).trim().takeIf { it.isNotEmpty() },
                    status = Commitment.STATUS_OPEN,
                    evidence = evidence
                )
            }
        }

        val inspirations = mutableListOf<Inspiration>()
        root.optJSONArray(KEY_INSPIRATIONS)?.let { array ->
            for (index in 0 until array.length()) {
                if (inspirations.size >= MAX_INSPIRATIONS) break
                val item = array.optJSONObject(index) ?: continue
                val evidence = evidenceFor(item, utterances) ?: continue
                val text = item.string(KEY_TEXT).trim().ifBlank { evidence.quote }
                inspirations += Inspiration(
                    id = "${session.id}:llm:inspiration:${inspirations.size}",
                    text = text,
                    evidence = evidence
                )
            }
        }

        return LlmAnalysisResult.Success(
            SessionMemory(
                sessionId = session.id,
                summary = root.string(KEY_SUMMARY).trim(),
                summaryEvidence = null,
                events = events,
                commitments = commitments,
                inspirations = inspirations,
                generatedAt = generatedAt,
                analyzer = LlmMemoryAnalyzer.NAME
            )
        )
    }

    /**
     * Resolves the evidence for one item. A blank/unknown utterance id or a
     * quote that is not a substring of that utterance's text rejects the item.
     */
    private fun evidenceFor(
        item: JSONObject,
        utterancesById: Map<String, TranscriptUtterance>
    ): MemoryEvidence? {
        val quote = item.string(KEY_QUOTE).trim()
        if (quote.isEmpty()) return null
        val utteranceId = item.string(KEY_UTTERANCE_ID).trim()
        if (utteranceId.isEmpty()) return null
        val utterance = utterancesById[utteranceId] ?: return null
        if (!quoteMatches(utterance.text, quote)) return null
        val speaker = utterance.speaker?.takeIf { it.isNotBlank() }
        return MemoryEvidence(
            utteranceId = utterance.id,
            quote = quote,
            startMs = utterance.startMs,
            speaker = speaker
        )
    }

    /** Substring check after whitespace normalization; case is ignored. */
    private fun quoteMatches(utteranceText: String, quote: String): Boolean {
        val normalizedQuote = normalizeWhitespace(quote)
        if (normalizedQuote.isEmpty()) return false
        return normalizeWhitespace(utteranceText).contains(normalizedQuote, ignoreCase = true)
    }

    private fun normalizeWhitespace(value: String): String =
        value.trim().replace(WHITESPACE, " ")

    private fun eventType(raw: String): MemoryEventType? =
        when (raw.trim().lowercase()) {
            "progress" -> MemoryEventType.PROGRESS
            "decision" -> MemoryEventType.DECISION
            "commitment" -> MemoryEventType.COMMITMENT
            "risk" -> MemoryEventType.RISK
            "inspiration" -> MemoryEventType.INSPIRATION
            else -> null
        }

    private fun parseRoot(raw: String): JSONObject? {
        val text = stripCodeFence(raw)
        if (text.isBlank()) return null
        runCatching { JSONObject(text) }.getOrNull()?.let { return it }
        // Some OpenAI-compatible servers wrap JSON in prose despite instructions.
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private fun stripCodeFence(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("```")) return text
        val firstNewline = text.indexOf('\n')
        if (firstNewline >= 0) {
            text = text.substring(firstNewline + 1)
        } else {
            text = text.removePrefix("```").removePrefix("json")
        }
        if (text.endsWith("```")) {
            text = text.dropLast(3)
        }
        return text.trim()
    }

    private fun JSONObject.string(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return optString(key)
    }
}

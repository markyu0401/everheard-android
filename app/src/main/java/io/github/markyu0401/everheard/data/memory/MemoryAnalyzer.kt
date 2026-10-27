package io.github.markyu0401.everheard.data.memory

import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance

/**
 * The first memory analyzer is deliberately deterministic: it never invents a
 * sentence, and every item is a quote from the transcript. It is a real,
 * offline baseline; a BYO LLM analyzer can replace it behind the same interface.
 */
interface MemoryAnalyzer {
    fun analyze(session: TranscriptSession): SessionMemory
}

class RuleBasedMemoryAnalyzer(
    private val now: () -> Long = System::currentTimeMillis
) : MemoryAnalyzer {

    companion object {
        const val NAME = "rule-v1"
        private const val MAX_EVENTS = 8
        private const val MAX_COMMITMENTS = 5
        private const val MAX_INSPIRATIONS = 3
        private const val MAX_SUMMARY_CHARS = 220

        private val decisionPatterns = listOf(
            Regex("(?i)\\b(we\\s+)?(decided|agreed|settled|concluded)\\b"),
            Regex("(?i)\\b(let's|lets)\\b"),
            Regex("决定|定了|确定|同意|就按|就这么|结论")
        )
        private val commitmentPatterns = listOf(
            Regex("(?i)\\b(i|we)\\s+(will|'ll|am going to|are going to|need to|have to|promise)\\b"),
            Regex("(?i)\\b(by|before)\\s+(tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next week|the end of)\\b"),
            Regex("我会|我来|我负责|我得|我要|需要我|答应|记得|明天|下周|周五|月底|之前")
        )
        private val riskPatterns = listOf(
            Regex("(?i)\\b(risk|risks|concern|worried|blocker|blocked|issue|problem|tight|danger)\\b"),
            Regex("风险|担心|问题|来不及|卡住|隐患|阻塞")
        )
        private val progressPatterns = listOf(
            Regex("(?i)\\b(done|finished|completed|shipped|updated|progress|working on)\\b"),
            Regex("完成|做完|搞定|已经|进展|上线|更新了")
        )
        private val inspirationPatterns = listOf(
            Regex("(?i)\\b(idea|what if|maybe we could|we could try)\\b"),
            Regex("灵感|想法|或许可以|可以试试|不如")
        )
        private val duePatterns = listOf(
            Regex("(?i)\\b(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next week|this week|end of (the )?(day|week|month))\\b"),
            Regex("今天|明天|后天|下周|这周|本周|周五|月底|周末")
        )
    }

    override fun analyze(session: TranscriptSession): SessionMemory {
        val utterances = session.utterances.filter { it.text.isNotBlank() }
        if (utterances.isEmpty()) {
            return SessionMemory(
                sessionId = session.id,
                summary = "",
                generatedAt = now()
            )
        }

        val summaryEvidence = evidenceFor(utterances.first())
        val summary = extractiveSummary(utterances)
        val events = mutableListOf<MemoryEvent>()
        val commitments = mutableListOf<Commitment>()
        val inspirations = mutableListOf<Inspiration>()
        val seenTexts = mutableSetOf<String>()

        utterances.forEachIndexed { index, utterance ->
            val text = utterance.text.trim()
            val normalized = text.lowercase()
            val evidence = evidenceFor(utterance)
            val type = classify(text, utterance.marked)
            val commitment = if (matches(commitmentPatterns, normalized)) {
                Commitment(
                    id = "${session.id}:commitment:$index",
                    text = text,
                    owner = ownerFor(utterance),
                    dueHint = dueHint(text),
                    evidence = evidence
                )
            } else {
                null
            }
            if (commitment != null && commitments.size < MAX_COMMITMENTS &&
                seenTexts.add("commitment:$normalized")
            ) {
                commitments += commitment
            }

            if (utterance.marked || type == MemoryEventType.INSPIRATION) {
                if (inspirations.size < MAX_INSPIRATIONS &&
                    seenTexts.add("inspiration:$normalized")
                ) {
                    inspirations += Inspiration(
                        id = "${session.id}:inspiration:$index",
                        text = text,
                        evidence = evidence
                    )
                }
            }

            val eventType = when {
                commitment != null -> MemoryEventType.COMMITMENT
                type != null -> type
                utterance.marked -> MemoryEventType.INSPIRATION
                else -> null
            }
            if (eventType != null && events.size < MAX_EVENTS &&
                seenTexts.add("event:$normalized")
            ) {
                events += MemoryEvent(
                    id = "${session.id}:event:$index",
                    type = eventType,
                    text = text,
                    evidence = evidence
                )
            }
        }

        return SessionMemory(
            sessionId = session.id,
            summary = summary,
            summaryEvidence = summaryEvidence,
            events = events,
            commitments = commitments,
            inspirations = inspirations,
            generatedAt = now()
        )
    }

    private fun extractiveSummary(utterances: List<TranscriptUtterance>): String {
        val first = utterances.first().text.trim()
        if (utterances.size == 1) return first.take(MAX_SUMMARY_CHARS)
        val second = utterances[1].text.trim()
        val combined = "$first $second"
        return if (combined.length <= MAX_SUMMARY_CHARS) {
            combined
        } else {
            combined.take(MAX_SUMMARY_CHARS).trimEnd() + "…"
        }
    }

    private fun classify(text: String, marked: Boolean): MemoryEventType? {
        val normalized = text.lowercase()
        return when {
            matches(riskPatterns, normalized) -> MemoryEventType.RISK
            matches(decisionPatterns, normalized) -> MemoryEventType.DECISION
            matches(commitmentPatterns, normalized) -> MemoryEventType.COMMITMENT
            matches(progressPatterns, normalized) -> MemoryEventType.PROGRESS
            marked || matches(inspirationPatterns, normalized) -> MemoryEventType.INSPIRATION
            else -> null
        }
    }

    private fun matches(patterns: List<Regex>, text: String): Boolean =
        patterns.any { it.containsMatchIn(text) }

    private fun dueHint(text: String): String? =
        duePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.value
        }

    private fun ownerFor(utterance: TranscriptUtterance): String? =
        utterance.speaker ?: if (
            Regex("(?i)\\b(i|we)\\b|我").containsMatchIn(utterance.text)
        ) {
            "me"
        } else {
            null
        }

    private fun evidenceFor(utterance: TranscriptUtterance): MemoryEvidence =
        MemoryEvidence(
            utteranceId = utterance.id,
            quote = utterance.text,
            startMs = utterance.startMs,
            speaker = utterance.speaker
        )
}

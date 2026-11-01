package io.github.markyu0401.everheard.data.memory

import io.github.markyu0401.everheard.asr.TranscriptQuality
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance

/**
 * The first memory analyzer is deliberately deterministic: it never invents a
 * sentence, and every item is a quote from the transcript. It is a real,
 * offline baseline; a BYO LLM analyzer can replace it behind the same interface.
 *
 * Attribution rule: when a voice profile was active for the session, only
 * voiceprint-confirmed ("me") utterances may become events or commitments, and
 * a session with no "me" utterance is background media — it produces no memory
 * at all. Without a profile there is nothing to gate on, so the older
 * text-heuristic behavior applies.
 */
interface MemoryAnalyzer {
    fun analyze(session: TranscriptSession): SessionMemory
}

class RuleBasedMemoryAnalyzer(
    private val now: () -> Long = System::currentTimeMillis
) : MemoryAnalyzer {

    companion object {
        const val NAME = "rule-v5"
        private const val MAX_EVENTS = 8
        private const val MAX_COMMITMENTS = 5
        private const val MAX_INSPIRATIONS = 3
        private const val MAX_SUMMARY_CHARS = 220

        private val decisionPatterns = listOf(
            Regex("(?i)\\b(we\\s+)?(decided|agreed|settled|concluded|chose)\\b"),
            Regex("(?i)\\b(let's|lets)\\s+(go with|use|do|ship|pick)\\b"),
            Regex("决定|定了|确定|同意|就按|按这个|按你说的|就这么|采用|结论")
        )
        // A commitment needs a first-person subject and an action verb close to
        // it. The old list matched bare "记得", "之前" and "明天", which turned
        // recollections and TV dialogue into open commitments.
        private val commitmentPatterns = listOf(
            Regex("(?i)\\b(i)\\s+(will|'ll|am going to|need to|have to|promise)\\b"),
            Regex("(?i)\\b(we)\\s+(will|'ll|are going to)\\b"),
            // "我要/我得/我来" must be adjacent; "我要是" is a conditional and
            // "我要不要" is a question, not a promise.
            Regex("(?<![你他她帮请让叫])我((?<!不)(?<![体学机])会|(?<!不)要(?!是|不|干|嘛|什)|(?<!记)得|(?<![起回出])来|负责|帮|安排|处理|改|修|发(?!现)|确认|补|答应|保证|准备|提交|上线)"),
            // A time word may sit between the subject and a strong action verb
            // ("我明天会把方案发给你"), but only verbs that are unambiguous in
            // that position; 发/改/修/帮 are too often part of 发现/修改/帮忙.
            Regex("(?<![你他她帮请让叫])我[^，。！？；]{1,6}((?<![体学机])会|负责|安排|处理|确认|准备|提交|上线|答应|保证|补)")
        )
        private val negationPatterns = listOf(
            Regex("(?i)\\b(not|never|won't|don't|doesn't)\\b"),
            Regex("不用|不需要|不必|没打算|不准备|不会")
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
            Regex("(?i)\\b(idea|what if|maybe we could|we could try|new direction)\\b"),
            Regex("灵感|想法|新方向|设想|点子|我想做|我想买|想做|有没有可能|如果能|要是能|我们可以做|不如")
        )
        private val duePatterns = listOf(
            Regex("(?i)\\b(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next week|this week|end of (the )?(day|week|month))\\b"),
            Regex("今天|明天|后天|下周|这周|本周|周五|月底|周末")
        )
        private val firstPerson = Regex("(?i)\\b(i|we)\\b|我")
        private const val MAX_COMMITMENT_CHARS = 240
    }

    override fun analyze(session: TranscriptSession): SessionMemory {
        val utterances = session.utterances.filter { utterance ->
            utterance.text.isNotBlank() &&
                TranscriptQuality.isAcceptable(
                    utterance.text,
                    utterance.endMs - utterance.startMs
                )
        }
        if (utterances.isEmpty()) return emptyMemory(session)

        val attributed = session.voiceProfileActive
        val mine = if (attributed) utterances.filter { isUserLine(it, attributed) } else utterances
        // A session where nobody matched the enrolled voice is TV, a video or a
        // room full of other people. It is transcript, not memory.
        if (attributed && mine.isEmpty()) return emptyMemory(session)

        val summaryPool = if (attributed) mine else utterances
        val summaryEvidence = evidenceFor(summaryPool.first())
        val summary = extractiveSummary(summaryPool)
        val events = mutableListOf<MemoryEvent>()
        val commitments = mutableListOf<Commitment>()
        val inspirations = mutableListOf<Inspiration>()
        val seenTexts = mutableSetOf<String>()

        utterances.forEachIndexed { index, utterance ->
            val text = utterance.text.trim()
            val normalized = text.lowercase()
            val evidence = evidenceFor(utterance)
            val isMine = isUserLine(utterance, attributed)
            val userAuthored = utterance.marked
            // Someone else's line may be marked by the user as evidence, but it
            // must never become the user's own commitment or event.
            if (!isMine && !userAuthored) return@forEachIndexed

            val type = if (isMine) classify(text, utterance.marked) else null
            val commitment = if (isMine && isCommitment(text)) {
                Commitment(
                    id = "${session.id}:commitment:$index",
                    text = text,
                    owner = ownerFor(utterance, attributed),
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

            if ((isMine && (utterance.marked || type == MemoryEventType.INSPIRATION)) ||
                (!isMine && userAuthored)
            ) {
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
                userAuthored -> MemoryEventType.INSPIRATION
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

    private fun emptyMemory(session: TranscriptSession): SessionMemory =
        SessionMemory(
            sessionId = session.id,
            summary = "",
            generatedAt = now()
        )

    /**
     * Picks the opening line plus the most informative other line instead of
     * blindly concatenating the first two. Still extractive: no invented text.
     */
    private fun extractiveSummary(utterances: List<TranscriptUtterance>): String {
        val first = utterances.first()
        val best = utterances.drop(1)
            .maxByOrNull { summaryScore(it.text) }
            ?.takeIf { summaryScore(it.text) >= summaryScore(first.text) }
        val picked = listOfNotNull(first, best).sortedBy { utterances.indexOf(it) }
        val combined = picked.joinToString(" ") { it.text.trim() }
        return if (combined.length <= MAX_SUMMARY_CHARS) {
            combined
        } else {
            combined.take(MAX_SUMMARY_CHARS).trimEnd() + "…"
        }
    }

    private fun summaryScore(text: String): Int {
        var score = 0
        if (matches(decisionPatterns, text)) score += 3
        if (isCommitment(text)) score += 3
        if (matches(riskPatterns, text)) score += 2
        if (matches(progressPatterns, text)) score += 2
        if (matches(inspirationPatterns, text)) score += 3
        if (text.any(Char::isDigit)) score += 1
        if (Regex("[A-Za-z]{3,}").containsMatchIn(text)) score += 1
        score += (text.length / 60).coerceAtMost(2)
        return score
    }

    private fun classify(text: String, marked: Boolean): MemoryEventType? {
        val normalized = text.lowercase()
        return when {
            matches(riskPatterns, normalized) -> MemoryEventType.RISK
            matches(decisionPatterns, normalized) -> MemoryEventType.DECISION
            isCommitment(text) -> MemoryEventType.COMMITMENT
            matches(progressPatterns, normalized) -> MemoryEventType.PROGRESS
            marked || matches(inspirationPatterns, normalized) -> MemoryEventType.INSPIRATION
            else -> null
        }
    }

    /**
     * A commitment is a first-person promise or a concrete action verb. Long
     * merged rows (lectures, TV dialogue) and negated sentences are rejected.
     */
    private fun isCommitment(text: String): Boolean {
        if (text.length > MAX_COMMITMENT_CHARS) return false
        if (matches(negationPatterns, text)) return false
        return matches(commitmentPatterns, text)
    }

    /**
     * The voice matcher leaves scores in the 0.30-0.58 band unlabelled. Those
     * rows used to be dropped from memory even when the user said them; a
     * first-person line is safe to keep, while media speech is labelled
     * "other" and stays excluded.
     */
    private fun isUserLine(utterance: TranscriptUtterance, attributed: Boolean): Boolean {
        if (!attributed) return true
        if (utterance.speaker == "me") return true
        if (utterance.speaker != null) return false
        // Unlabelled rows sit in the matcher's ambiguous 0.30-0.58 band. Only a
        // decision or an idea is distinctive enough to keep without a
        // voiceprint match; a bare "我..." line could be the user or the room.
        return matches(decisionPatterns, utterance.text) ||
            matches(inspirationPatterns, utterance.text)
    }

    private fun matches(patterns: List<Regex>, text: String): Boolean =
        patterns.any { it.containsMatchIn(text) }

    private fun dueHint(text: String): String? =
        duePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.value
        }

    /**
     * With attribution active, only a "me" utterance reaches this method, so the
     * owner is always the user. Without a profile, the old text heuristic is
     * the best available signal — but it must never be applied to unattributed
     * media speech, which is what produced fake commitments in the field.
     */
    private fun ownerFor(utterance: TranscriptUtterance, attributed: Boolean): String? {
        if (attributed) return utterance.speaker
        return utterance.speaker ?: if (firstPerson.containsMatchIn(utterance.text)) {
            "me"
        } else {
            null
        }
    }

    private fun evidenceFor(utterance: TranscriptUtterance): MemoryEvidence =
        MemoryEvidence(
            utteranceId = utterance.id,
            quote = utterance.text,
            startMs = utterance.startMs,
            speaker = utterance.speaker
        )
}

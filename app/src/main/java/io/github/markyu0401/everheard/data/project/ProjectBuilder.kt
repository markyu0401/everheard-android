package io.github.markyu0401.everheard.data.project

import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import java.util.Locale

/**
 * Deterministic topic clustering for the pre-embedding M5 slice.
 *
 * A session is represented by its title, transcript text, and extracted event
 * text. Two sessions are linked when they share at least two non-stopword
 * keywords; connected components become projects. This is deliberately
 * conservative: unrelated conversations should not be merged just because both
 * contain a common word.
 */
class ProjectBuilder(
    private val stopwords: Set<String> = DEFAULT_STOPWORDS
) {

    companion object {
        private const val MAX_KEYWORDS = 8
        private const val MIN_SHARED_KEYWORDS = 2
        private const val MAX_PROJECTS = 30

        private val DEFAULT_STOPWORDS = setOf(
            "the", "and", "for", "with", "that", "this", "from", "have", "will",
            "about", "into", "your", "our", "you", "are", "was", "were", "been",
            "they", "them", "their", "there", "here", "what", "when", "where",
            "why", "how", "can", "could", "would", "should", "just", "like",
            "today", "tomorrow", "yesterday", "now", "then", "also", "very",
            "really", "okay", "ok", "yes", "no", "not", "but", "all", "any",
            "some", "one", "two", "three", "get", "got", "make", "made", "go",
            "going", "went", "think", "know", "need", "want", "say", "said",
            "project", "meeting", "call", "thing", "things", "stuff", "work",
            "time", "day", "week", "month", "year", "new", "next", "last",
            "今天", "明天", "昨天", "现在", "然后", "就是", "这个", "那个",
            "我们", "你们", "他们", "什么", "怎么", "可以", "需要", "一个",
            "一下", "时候", "问题", "东西", "事情", "工作", "时间", "项目",
            "会议", "电话", "好的", "对", "嗯", "啊", "吧", "呢", "了"
        )

        private val ENGLISH_WORD = Regex("[A-Za-z][A-Za-z0-9_-]{2,}")
        private val CJK_RUN = Regex("[\\u4e00-\\u9fff]{2,}")
        private val CJK_BIGRAM = Regex("[\\u4e00-\\u9fff]{2}")
    }

    fun build(
        sessions: List<TranscriptSession>,
        memories: List<SessionMemory>
    ): List<Project> {
        val memoryBySession = memories.associateBy { it.sessionId }
        val keywordsBySession = sessions.associate { session ->
            session.id to keywordsFor(session, memoryBySession[session.id])
        }
        val linked = sessions.associate { it.id to mutableSetOf<String>() }
        sessions.forEachIndexed { index, first ->
            val firstKeywords = keywordsBySession[first.id].orEmpty().toSet()
            if (firstKeywords.isEmpty()) return@forEachIndexed
            for (otherIndex in index + 1 until sessions.size) {
                val other = sessions[otherIndex]
                val otherKeywords = keywordsBySession[other.id].orEmpty().toSet()
                if (otherKeywords.isEmpty()) continue
                if (firstKeywords.intersect(otherKeywords).size >= MIN_SHARED_KEYWORDS) {
                    linked[first.id]?.add(other.id)
                    linked[other.id]?.add(first.id)
                }
            }
        }

        val components = mutableListOf<MutableSet<String>>()
        val seen = mutableSetOf<String>()
        sessions.forEach { session ->
            if (!seen.add(session.id)) return@forEach
            val component = mutableSetOf(session.id)
            val queue = ArrayDeque<String>()
            queue.add(session.id)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                linked[current].orEmpty().forEach { neighbour ->
                    if (seen.add(neighbour)) {
                        component += neighbour
                        queue.add(neighbour)
                    }
                }
            }
            components += component
        }

        val projects = components.mapNotNull { component ->
            val componentSessions = sessions
                .filter { it.id in component }
                .sortedBy { it.startedAt }
            if (componentSessions.isEmpty()) return@mapNotNull null
            val keywordCounts = mutableMapOf<String, Int>()
            componentSessions.forEach { session ->
                keywordsBySession[session.id].orEmpty().forEach { keyword ->
                    keywordCounts[keyword] = (keywordCounts[keyword] ?: 0) + 1
                }
            }
            val keywords = keywordCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key })
                .take(MAX_KEYWORDS)
                .map { it.key }
            val name = if (componentSessions.size == 1) {
                componentSessions.first().title.ifBlank { "Conversation" }
            } else {
                keywords.firstOrNull()?.let(::displayName)
                    ?: componentSessions.first().title.ifBlank { "Conversation" }
            }
            val events = componentSessions.flatMap { session ->
                memoryBySession[session.id]?.events.orEmpty().map { event ->
                    ProjectEvent(
                        sessionId = session.id,
                        sessionTitle = session.title.ifBlank { "Conversation" },
                        event = event
                    )
                }
            }.sortedWith(compareBy<ProjectEvent> { eventSessionStart(it, componentSessions) }
                .thenBy { it.event.evidence.startMs })
            val commitments = componentSessions.flatMap { session ->
                memoryBySession[session.id]?.commitments.orEmpty()
                    .filter { it.status == Commitment.STATUS_OPEN }
            }
            Project(
                id = projectId(keywords, componentSessions),
                name = name,
                keywords = keywords,
                sessionIds = componentSessions.map { it.id },
                firstAt = componentSessions.first().startedAt,
                lastAt = componentSessions.last().startedAt,
                events = events,
                openCommitments = commitments
            )
        }
        return projects
            .sortedByDescending { it.lastAt }
            .take(MAX_PROJECTS)
    }

    private fun eventSessionStart(event: ProjectEvent, sessions: List<TranscriptSession>): Long =
        sessions.firstOrNull { it.id == event.sessionId }?.startedAt ?: 0L

    private fun projectId(keywords: List<String>, sessions: List<TranscriptSession>): String {
        val seed = keywords.joinToString("|") + "#" + sessions.first().id
        return Integer.toHexString(seed.hashCode())
    }

    private fun keywordsFor(
        session: TranscriptSession,
        memory: SessionMemory?
    ): List<String> {
        val text = buildString {
            append(session.title).append(' ')
            session.utterances.take(12).forEach { append(it.text).append(' ') }
            memory?.events.orEmpty().forEach { append(it.text).append(' ') }
        }
        val counts = mutableMapOf<String, Int>()
        ENGLISH_WORD.findAll(text).forEach { match ->
            val word = match.value.lowercase(Locale.US)
            if (word !in stopwords && word.length >= 3) {
                counts[word] = (counts[word] ?: 0) + 1
            }
        }
        CJK_RUN.findAll(text).forEach { match ->
            val run = match.value
            for (index in 0..run.length - 2) {
                val bigram = run.substring(index, index + 2)
                if (bigram !in stopwords && CJK_BIGRAM.matches(bigram)) {
                    counts[bigram] = (counts[bigram] ?: 0) + 1
                }
            }
        }
        return counts.entries
            .filter { it.value >= 1 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key })
            .take(MAX_KEYWORDS)
            .map { it.key }
    }

    private fun displayName(keyword: String): String =
        keyword.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

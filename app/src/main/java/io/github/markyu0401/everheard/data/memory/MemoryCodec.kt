package io.github.markyu0401.everheard.data.memory

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON for the encrypted memory document. The file is encrypted by
 * [KeystoreCipher]; this codec only owns the plaintext shape.
 */
object MemoryCodec {

    private const val VERSION = 1

    fun encode(memories: List<SessionMemory>): String {
        val root = JSONObject().apply {
            put("version", VERSION)
            put("memories", JSONArray().apply {
                memories.forEach { memory -> put(encodeMemory(memory)) }
            })
        }
        return root.toString()
    }

    fun decode(raw: String): List<SessionMemory> {
        if (raw.isBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("memories") ?: return emptyList()
        val memories = mutableListOf<SessionMemory>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val sessionId = item.optString("sessionId").takeIf { it.isNotBlank() } ?: continue
            memories += SessionMemory(
                sessionId = sessionId,
                summary = item.optString("summary"),
                summaryEvidence = item.optJSONObject("summaryEvidence")?.let(::decodeEvidence),
                events = decodeArray(item.optJSONArray("events")) { decodeEvent(it) },
                commitments = decodeArray(item.optJSONArray("commitments")) { decodeCommitment(it) },
                inspirations = decodeArray(item.optJSONArray("inspirations")) { decodeInspiration(it) },
                generatedAt = item.optLong("generatedAt", 0L),
                analyzer = item.optString("analyzer", RuleBasedMemoryAnalyzer.NAME)
            )
        }
        return memories
    }

    private fun encodeMemory(memory: SessionMemory): JSONObject = JSONObject().apply {
        put("sessionId", memory.sessionId)
        put("summary", memory.summary)
        memory.summaryEvidence?.let { put("summaryEvidence", encodeEvidence(it)) }
        put("events", JSONArray().apply { memory.events.forEach { put(encodeEvent(it)) } })
        put("commitments", JSONArray().apply { memory.commitments.forEach { put(encodeCommitment(it)) } })
        put("inspirations", JSONArray().apply { memory.inspirations.forEach { put(encodeInspiration(it)) } })
        put("generatedAt", memory.generatedAt)
        put("analyzer", memory.analyzer)
    }

    private fun encodeEvidence(evidence: MemoryEvidence): JSONObject = JSONObject().apply {
        put("utteranceId", evidence.utteranceId)
        put("quote", evidence.quote)
        put("startMs", evidence.startMs)
        evidence.speaker?.let { put("speaker", it) }
    }

    private fun encodeEvent(event: MemoryEvent): JSONObject = JSONObject().apply {
        put("id", event.id)
        put("type", event.type.name)
        put("text", event.text)
        put("evidence", encodeEvidence(event.evidence))
    }

    private fun encodeCommitment(commitment: Commitment): JSONObject = JSONObject().apply {
        put("id", commitment.id)
        put("text", commitment.text)
        commitment.owner?.let { put("owner", it) }
        commitment.dueHint?.let { put("dueHint", it) }
        put("status", commitment.status)
        put("evidence", encodeEvidence(commitment.evidence))
    }

    private fun encodeInspiration(inspiration: Inspiration): JSONObject = JSONObject().apply {
        put("id", inspiration.id)
        put("text", inspiration.text)
        put("evidence", encodeEvidence(inspiration.evidence))
    }

    private fun decodeEvidence(item: JSONObject): MemoryEvidence? {
        val quote = item.optString("quote")
        if (quote.isBlank()) return null
        return MemoryEvidence(
            utteranceId = item.optString("utteranceId"),
            quote = quote,
            startMs = item.optLong("startMs", 0L),
            speaker = if (item.has("speaker") && !item.isNull("speaker")) {
                item.optString("speaker")
            } else {
                null
            }
        )
    }

    private fun decodeEvent(item: JSONObject): MemoryEvent? {
        val evidence = item.optJSONObject("evidence")?.let(::decodeEvidence) ?: return null
        val text = item.optString("text")
        if (text.isBlank()) return null
        val type = runCatching {
            MemoryEventType.valueOf(item.optString("type"))
        }.getOrNull() ?: return null
        return MemoryEvent(
            id = item.optString("id"),
            type = type,
            text = text,
            evidence = evidence
        )
    }

    private fun decodeCommitment(item: JSONObject): Commitment? {
        val evidence = item.optJSONObject("evidence")?.let(::decodeEvidence) ?: return null
        val text = item.optString("text")
        if (text.isBlank()) return null
        return Commitment(
            id = item.optString("id"),
            text = text,
            owner = item.optString("owner").takeIf { it.isNotBlank() },
            dueHint = item.optString("dueHint").takeIf { it.isNotBlank() },
            status = item.optString("status", Commitment.STATUS_OPEN),
            evidence = evidence
        )
    }

    private fun decodeInspiration(item: JSONObject): Inspiration? {
        val evidence = item.optJSONObject("evidence")?.let(::decodeEvidence) ?: return null
        val text = item.optString("text")
        if (text.isBlank()) return null
        return Inspiration(
            id = item.optString("id"),
            text = text,
            evidence = evidence
        )
    }

    private fun <T> decodeArray(array: JSONArray?, decode: (JSONObject) -> T?): List<T> {
        if (array == null) return emptyList()
        val result = mutableListOf<T>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            decode(item)?.let { result += it }
        }
        return result
    }
}

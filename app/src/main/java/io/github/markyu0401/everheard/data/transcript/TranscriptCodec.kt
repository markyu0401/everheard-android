package io.github.markyu0401.everheard.data.transcript

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON is deliberately hand-written so the on-disk format stays stable and
 * readable for tests. The file itself is encrypted by [KeystoreCipher].
 */
object TranscriptCodec {

    private const val VERSION = 1

    fun encode(sessions: List<TranscriptSession>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("startedAt", session.startedAt)
                    session.endedAt?.let { put("endedAt", it) }
                    put("title", session.title)
                    put("utterances", JSONArray().apply {
                        session.utterances.forEach { utterance ->
                            put(
                                JSONObject().apply {
                                    put("id", utterance.id)
                                    put("sessionId", utterance.sessionId)
                                    put("startMs", utterance.startMs)
                                    put("endMs", utterance.endMs)
                                    put("text", utterance.text)
                                    utterance.speaker?.let { put("speaker", it) }
                                }
                            )
                        }
                    })
                }
            )
        }
        root.put("sessions", array)
        return root.toString()
    }

    fun decode(raw: String): List<TranscriptSession> {
        if (raw.isBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("sessions") ?: return emptyList()
        val sessions = mutableListOf<TranscriptSession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val startedAt = item.optLong("startedAt", 0L)
            val endedAt = if (item.has("endedAt") && !item.isNull("endedAt")) {
                item.optLong("endedAt", 0L)
            } else {
                null
            }
            val title = item.optString("title")
            val utterances = mutableListOf<TranscriptUtterance>()
            val utteranceArray = item.optJSONArray("utterances") ?: JSONArray()
            for (u in 0 until utteranceArray.length()) {
                val utterance = utteranceArray.optJSONObject(u) ?: continue
                val text = utterance.optString("text")
                if (text.isBlank()) continue
                utterances += TranscriptUtterance(
                    id = utterance.optString("id"),
                    sessionId = utterance.optString("sessionId", id),
                    startMs = utterance.optLong("startMs", 0L),
                    endMs = utterance.optLong("endMs", 0L),
                    text = text,
                    speaker = if (utterance.has("speaker") && !utterance.isNull("speaker")) {
                        utterance.optString("speaker")
                    } else {
                        null
                    }
                )
            }
            sessions += TranscriptSession(
                id = id,
                startedAt = startedAt,
                endedAt = endedAt,
                title = title,
                utterances = utterances
            )
        }
        return sessions
    }
}

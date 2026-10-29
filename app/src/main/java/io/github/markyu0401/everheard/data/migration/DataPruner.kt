package io.github.markyu0401.everheard.data.migration

import android.content.Context
import io.github.markyu0401.everheard.asr.SessionQuality
import io.github.markyu0401.everheard.asr.TranscriptQuality
import io.github.markyu0401.everheard.data.memory.EncryptedMemoryStore
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.speaker.VoiceProfileStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time cleanup for corpora written before the quality gates existed.
 *
 * A full day of capture produced 633 utterances, a quarter of which were pure
 * hallucination (".", "嗯。", "B."), and every commitment was extracted from
 * unattributed TV audio. This migration drops hallucinated rows, deletes
 * sessions that no longer meet [SessionQuality], rewrites titles from the most
 * substantive row, marks the remaining sessions as voiceprint-gated (the user
 * is enrolled), and recomputes memory so fabricated commitments disappear.
 *
 * It runs once, from [io.github.markyu0401.everheard.EverheardApp.onCreate],
 * before any capture service can start in this process — a stale store cache in
 * a running transcriber would otherwise resurrect pruned rows.
 */
object DataPruner {

    private const val PREFS = "everheard_migration"
    private const val KEY_PRUNE_V1 = "prune_v1"

    data class Result(
        val removedUtterances: Int,
        val removedSessions: Int,
        val remainingSessions: Int
    )

    suspend fun runOnce(context: Context): Result? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PRUNE_V1, false)) return@withContext null

        val files = context.filesDir
        val transcriptFile = File(files, "transcripts.bin")
        if (!transcriptFile.exists()) {
            prefs.edit().putBoolean(KEY_PRUNE_V1, true).apply()
            return@withContext null
        }

        val transcriptStore = EncryptedTranscriptStore(transcriptFile)
        val memoryStore = EncryptedMemoryStore(File(files, "memory.bin"))
        val profileStore = VoiceProfileStore(File(files, "voice_profile.bin"))
        val attributed = profileStore.load() != null

        val sessions = transcriptStore.reload()
        var removedUtterances = 0
        var removedSessions = 0
        val kept = sessions.mapNotNull { session ->
            val clean = session.utterances.filter { utterance ->
                TranscriptQuality.isAcceptable(
                    utterance.text,
                    utterance.endMs - utterance.startMs
                )
            }
            removedUtterances += session.utterances.size - clean.size
            if (!SessionQuality.qualifies(clean)) {
                removedSessions++
                null
            } else {
                session.copy(
                    utterances = clean,
                    title = SessionQuality.titleFor(clean),
                    voiceProfileActive = attributed
                )
            }
        }

        transcriptStore.replaceAll(kept)
        val analyzer = RuleBasedMemoryAnalyzer()
        memoryStore.replaceAll(kept.map { analyzer.analyze(it) })

        prefs.edit().putBoolean(KEY_PRUNE_V1, true).apply()
        Result(
            removedUtterances = removedUtterances,
            removedSessions = removedSessions,
            remainingSessions = kept.size
        )
    }
}

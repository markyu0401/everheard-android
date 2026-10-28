package io.github.markyu0401.everheard.data.backup

import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession

/**
 * The portable content of one backup. Provider API keys, the voiceprint and UI
 * preferences are deliberately excluded.
 */
data class BackupPayload(
    val createdAt: Long,
    val transcripts: List<TranscriptSession> = emptyList(),
    val memories: List<SessionMemory> = emptyList(),
    val scheduleRules: List<ScheduleRule> = emptyList(),
    val excludedApps: List<String> = emptyList(),
    val version: Int = BackupCodec.FORMAT_VERSION
)

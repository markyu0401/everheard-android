package io.github.markyu0401.everheard.capture.schedule

import java.time.DayOfWeek

/**
 * Line-based schedule format shared by [ScheduleStore] and the backup codec:
 * `id|day,day|startMinute|endMinute|MODE`.
 */
object ScheduleCodec {

    fun encode(rules: List<ScheduleRule>): String = rules.joinToString("\n") { rule ->
        val days = rule.days.joinToString(",") { it.value.toString() }
        "${rule.id}|$days|${rule.startMinute}|${rule.endMinute}|${rule.mode.name}"
    }

    fun decode(value: String): List<ScheduleRule> {
        if (value.isBlank()) return emptyList()
        return value.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 5) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                val days = if (parts[1].isBlank()) {
                    emptySet()
                } else {
                    parts[1].split(",").mapNotNull { token ->
                        token.toIntOrNull()?.let { runCatching { DayOfWeek.of(it) }.getOrNull() }
                    }.toSet()
                }
                val start = parts[2].toIntOrNull() ?: return@mapNotNull null
                val end = parts[3].toIntOrNull() ?: return@mapNotNull null
                val mode = runCatching { ScheduleMode.valueOf(parts[4]) }.getOrNull()
                    ?: return@mapNotNull null
                ScheduleRule(
                    id = id,
                    days = days,
                    startMinute = start,
                    endMinute = end,
                    mode = mode
                )
            }
            .toList()
    }
}

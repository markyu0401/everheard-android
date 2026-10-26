package io.github.markyu0401.everheard.capture.schedule

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

enum class ScheduleMode { ALLOW, BLOCK }

/**
 * A weekly schedule rule in local time. [days] empty means every day.
 * A rule whose start equals its end covers the whole day.
 */
data class ScheduleRule(
    val id: Long = 0,
    val days: Set<DayOfWeek> = emptySet(),
    val startMinute: Int,
    val endMinute: Int,
    val mode: ScheduleMode
) {
    init {
        require(startMinute in 0..1439) { "startMinute must be 0..1439" }
        require(endMinute in 0..1439) { "endMinute must be 0..1439" }
    }

    fun isActiveAt(now: LocalDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val day = now.dayOfWeek

        if (startMinute == endMinute) {
            return days.isEmpty() || day in days
        }

        if (startMinute < endMinute) {
            return (days.isEmpty() || day in days) &&
                minute in startMinute until endMinute
        }

        // Window crosses midnight: the start day owns the rule.
        return if (minute >= startMinute) {
            days.isEmpty() || day in days
        } else if (minute < endMinute) {
            val previousDay = day.minus(1)
            days.isEmpty() || previousDay in days
        } else {
            false
        }
    }
}

object ScheduleEvaluator {

    /**
     * Block wins. If there are no ALLOW rules, everything outside BLOCK windows
     * is allowed. If there is at least one ALLOW rule, only its windows are
     * allowed (minus any BLOCK overlap).
     */
    fun isAllowed(rules: List<ScheduleRule>, now: LocalDateTime): Boolean {
        val active = rules.filter { it.isActiveAt(now) }
        if (active.any { it.mode == ScheduleMode.BLOCK }) return false

        val hasAllowRules = rules.any { it.mode == ScheduleMode.ALLOW }
        if (!hasAllowRules) return true

        return active.any { it.mode == ScheduleMode.ALLOW }
    }

    fun defaultRules(): List<ScheduleRule> = listOf(
        ScheduleRule(
            id = 1,
            startMinute = LocalTime.of(23, 30).toSecondOfDay() / 60,
            endMinute = LocalTime.of(7, 0).toSecondOfDay() / 60,
            mode = ScheduleMode.BLOCK
        )
    )
}

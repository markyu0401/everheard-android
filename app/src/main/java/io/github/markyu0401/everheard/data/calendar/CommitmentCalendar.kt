package io.github.markyu0401.everheard.data.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import io.github.markyu0401.everheard.data.memory.Commitment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Creates a calendar-insert intent for a commitment with a recognizable due
 * hint. This does not write silently; the user confirms in their calendar app.
 */
object CommitmentCalendar {

    fun intent(
        context: Context,
        commitment: Commitment,
        sessionStartedAt: Long
    ): Intent? {
        val date = dueDate(commitment.dueHint, sessionStartedAt) ?: return null
        val begin = date.atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val end = begin + 30 * 60_000L
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, commitment.text)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "From Everheard: ${commitment.evidence.quote}"
            )
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
    }

    fun dueDate(dueHint: String?, sessionStartedAt: Long): LocalDate? {
        val hint = dueHint?.trim()?.lowercase(Locale.US).orEmpty()
        if (hint.isBlank()) return null
        val base = Instant.ofEpochMilli(sessionStartedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return when {
            hint.contains("today") || hint.contains("今天") -> base
            hint.contains("tomorrow") || hint.contains("明天") -> base.plusDays(1)
            hint.contains("next week") || hint.contains("下周") -> base.plusWeeks(1)
            hint.contains("weekend") || hint.contains("周末") ->
                base.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            hint.contains("month") || hint.contains("月底") ->
                base.with(TemporalAdjusters.lastDayOfMonth())
            else -> weekday(hint, base)
        }
    }

    private fun weekday(hint: String, base: LocalDate): LocalDate? {
        val days = listOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY,
            "周一" to DayOfWeek.MONDAY,
            "周二" to DayOfWeek.TUESDAY,
            "周三" to DayOfWeek.WEDNESDAY,
            "周四" to DayOfWeek.THURSDAY,
            "周五" to DayOfWeek.FRIDAY,
            "周六" to DayOfWeek.SATURDAY,
            "周日" to DayOfWeek.SUNDAY
        )
        val match = days.firstOrNull { hint.contains(it.first) } ?: return null
        return base.with(TemporalAdjusters.nextOrSame(match.second))
    }
}

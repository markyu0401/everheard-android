package io.github.markyu0401.everheard.data.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class CommitmentCalendarTest {

    private val sessionStart = Instant.parse("2026-10-26T15:00:00Z").toEpochMilli()

    @Test
    fun parses_english_and_chinese_relative_dates() {
        val base = Instant.ofEpochMilli(sessionStart)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        assertThat(CommitmentCalendar.dueDate("tomorrow", sessionStart))
            .isEqualTo(base.plusDays(1))
        assertThat(CommitmentCalendar.dueDate("明天", sessionStart))
            .isEqualTo(base.plusDays(1))
        assertThat(CommitmentCalendar.dueDate("next week", sessionStart))
            .isEqualTo(base.plusWeeks(1))
        assertThat(CommitmentCalendar.dueDate("周五", sessionStart))
            .isEqualTo(base.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY)))
    }

    @Test
    fun unknown_due_hint_is_null() {
        assertThat(CommitmentCalendar.dueDate("soon", sessionStart)).isNull()
        assertThat(CommitmentCalendar.dueDate(null, sessionStart)).isNull()
    }
}

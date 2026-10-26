package io.github.markyu0401.everheard.capture.schedule

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Test

class ScheduleEvaluatorTest {

    private fun at(day: DayOfWeek, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 10, 26, hour, minute).with(
            java.time.temporal.TemporalAdjusters.nextOrSame(day)
        )

    @Test
    fun default_rule_blocks_sleep_and_allows_the_day() {
        val rules = ScheduleEvaluator.defaultRules()

        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.MONDAY, 23, 45))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.TUESDAY, 2))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.TUESDAY, 7))).isTrue()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.TUESDAY, 12))).isTrue()
    }

    @Test
    fun block_wins_over_allow() {
        val rules = listOf(
            ScheduleRule(
                days = emptySet(),
                startMinute = 0,
                endMinute = 1439,
                mode = ScheduleMode.ALLOW
            ),
            ScheduleRule(
                days = emptySet(),
                startMinute = 12 * 60,
                endMinute = 13 * 60,
                mode = ScheduleMode.BLOCK
            )
        )

        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.MONDAY, 12, 30))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.MONDAY, 11, 30))).isTrue()
    }

    @Test
    fun allow_only_rules_limit_the_window() {
        val rules = listOf(
            ScheduleRule(
                days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                startMinute = 9 * 60,
                endMinute = 17 * 60,
                mode = ScheduleMode.ALLOW
            )
        )

        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.FRIDAY, 10))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.SATURDAY, 10))).isTrue()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.SATURDAY, 18))).isFalse()
    }

    @Test
    fun a_window_that_crosses_midnight_belongs_to_its_start_day() {
        val rules = listOf(
            ScheduleRule(
                days = setOf(DayOfWeek.FRIDAY),
                startMinute = 23 * 60,
                endMinute = 2 * 60,
                mode = ScheduleMode.BLOCK
            )
        )

        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.FRIDAY, 23, 30))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.SATURDAY, 1))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.SATURDAY, 2))).isTrue()
    }

    @Test
    fun no_allow_rules_means_everything_outside_blocks_is_allowed() {
        val rules = listOf(
            ScheduleRule(
                days = emptySet(),
                startMinute = 1 * 60,
                endMinute = 2 * 60,
                mode = ScheduleMode.BLOCK
            )
        )

        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.MONDAY, 1, 30))).isFalse()
        assertThat(ScheduleEvaluator.isAllowed(rules, at(DayOfWeek.MONDAY, 3))).isTrue()
    }

    @Test
    fun equal_start_and_end_means_the_whole_day() {
        val rule = ScheduleRule(
            days = setOf(DayOfWeek.MONDAY),
            startMinute = 0,
            endMinute = 0,
            mode = ScheduleMode.BLOCK
        )

        assertThat(rule.isActiveAt(at(DayOfWeek.MONDAY, 0))).isTrue()
        assertThat(rule.isActiveAt(at(DayOfWeek.MONDAY, 12))).isTrue()
        assertThat(rule.isActiveAt(at(DayOfWeek.TUESDAY, 12))).isFalse()
    }
}

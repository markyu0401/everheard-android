package io.github.markyu0401.everheard.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.capture.schedule.ScheduleEvaluator
import io.github.markyu0401.everheard.capture.schedule.ScheduleMode
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(scope: kotlinx.coroutines.CoroutineScope): ScheduleStore {
        val file = File(temporaryFolder.root, "schedule.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return ScheduleStore(dataStore)
    }

    @Test
    fun missing_value_returns_the_default_sleep_block() = runTest {
        val store = store(backgroundScope)
        val rules = store.rules.first()

        assertThat(rules).isEqualTo(ScheduleEvaluator.defaultRules())
    }

    @Test
    fun saved_rules_round_trip() = runTest {
        val store = store(backgroundScope)
        val rules = listOf(
            ScheduleRule(
                id = 7,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startMinute = 9 * 60,
                endMinute = 17 * 60,
                mode = ScheduleMode.ALLOW
            ),
            ScheduleRule(
                id = 8,
                days = emptySet(),
                startMinute = 23 * 60 + 30,
                endMinute = 7 * 60,
                mode = ScheduleMode.BLOCK
            )
        )

        store.save(rules)

        assertThat(store.rules.first()).isEqualTo(rules)
    }

    @Test
    fun encode_decode_handles_empty_days_and_wrap_windows() = runTest {
        val store = store(backgroundScope)
        val rule = ScheduleRule(
            id = 1,
            days = emptySet(),
            startMinute = 1410,
            endMinute = 420,
            mode = ScheduleMode.BLOCK
        )

        val encoded = store.encode(listOf(rule))
        val decoded = store.decode(encoded)

        assertThat(decoded).isEqualTo(listOf(rule))
    }
}

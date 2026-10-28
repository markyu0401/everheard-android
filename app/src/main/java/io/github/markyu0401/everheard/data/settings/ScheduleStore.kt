package io.github.markyu0401.everheard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.markyu0401.everheard.capture.schedule.ScheduleCodec
import io.github.markyu0401.everheard.capture.schedule.ScheduleEvaluator
import io.github.markyu0401.everheard.capture.schedule.ScheduleMode
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.scheduleDataStore by preferencesDataStore(name = "everheard_schedule")

private val RULES_KEY = stringPreferencesKey("schedule_rules")

/**
 * Persists the weekly schedule as a small line-based format:
 * `id|day,day|startMinute|endMinute|MODE`. Missing key means the default
 * 23:30–07:00 block; an empty string means "no rules" (allow all day).
 */
class ScheduleStore(private val dataStore: DataStore<Preferences>) {

    val rules: Flow<List<ScheduleRule>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[RULES_KEY]?.let(::decode) ?: ScheduleEvaluator.defaultRules()
        }

    suspend fun save(rules: List<ScheduleRule>) {
        dataStore.edit { preferences ->
            preferences[RULES_KEY] = encode(rules)
        }
    }

    internal fun encode(rules: List<ScheduleRule>): String = ScheduleCodec.encode(rules)

    internal fun decode(value: String): List<ScheduleRule> = ScheduleCodec.decode(value)
}

fun Context.scheduleStore(): ScheduleStore = ScheduleStore(scheduleDataStore)

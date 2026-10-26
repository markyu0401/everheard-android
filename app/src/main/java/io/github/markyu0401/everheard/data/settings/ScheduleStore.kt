package io.github.markyu0401.everheard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.markyu0401.everheard.capture.schedule.ScheduleEvaluator
import io.github.markyu0401.everheard.capture.schedule.ScheduleMode
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import java.io.IOException
import java.time.DayOfWeek
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

    internal fun encode(rules: List<ScheduleRule>): String = rules.joinToString("\n") { rule ->
        val days = rule.days.joinToString(",") { it.value.toString() }
        "${rule.id}|$days|${rule.startMinute}|${rule.endMinute}|${rule.mode.name}"
    }

    internal fun decode(value: String): List<ScheduleRule> {
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

fun Context.scheduleStore(): ScheduleStore = ScheduleStore(scheduleDataStore)

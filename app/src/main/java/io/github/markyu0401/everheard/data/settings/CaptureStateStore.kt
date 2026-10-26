package io.github.markyu0401.everheard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.captureStateDataStore by preferencesDataStore(name = "everheard_capture_state")

private val PAUSE_UNTIL = longPreferencesKey("pause_until_millis")
private val LAST_STATE = stringPreferencesKey("last_state")

/**
 * Persists the user pause across process death and the last known state for
 * diagnostics. It deliberately does not persist "recording": a restarted
 * service starts armed and re-evaluates the schedule instead of claiming that
 * capture continued while the process was dead.
 */
class CaptureStateStore(private val dataStore: DataStore<Preferences>) {

    val pauseUntilMillis: Flow<Long> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[PAUSE_UNTIL] ?: 0L }

    val lastState: Flow<String?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[LAST_STATE] }

    suspend fun setPauseUntil(millis: Long) {
        dataStore.edit { preferences -> preferences[PAUSE_UNTIL] = millis }
    }

    suspend fun clearPauseUntil() {
        dataStore.edit { preferences -> preferences.remove(PAUSE_UNTIL) }
    }

    suspend fun setLastState(state: String) {
        dataStore.edit { preferences -> preferences[LAST_STATE] = state }
    }
}

fun Context.captureStateStore(): CaptureStateStore = CaptureStateStore(captureStateDataStore)

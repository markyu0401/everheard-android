package io.github.markyu0401.everheard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.everheardSettings by preferencesDataStore(name = "everheard_settings")

private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

/**
 * Persists the one-time legal/onboarding acknowledgement.
 *
 * The constructor takes a [DataStore] rather than a Context so it can be tested
 * on the JVM with a temporary file.
 */
class OnboardingStore(private val dataStore: DataStore<Preferences>) {

    val isComplete: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }

    suspend fun markComplete() {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = true
        }
    }
}

fun Context.onboardingStore(): OnboardingStore = OnboardingStore(everheardSettings)

package io.github.markyu0401.everheard.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureStateStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pause_round_trips_and_can_be_cleared() = runTest {
        val file = File(temporaryFolder.root, "capture_state.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val store = CaptureStateStore(dataStore)

        assertThat(store.pauseUntilMillis.first()).isEqualTo(0L)

        store.setPauseUntil(123_456L)
        assertThat(store.pauseUntilMillis.first()).isEqualTo(123_456L)

        store.clearPauseUntil()
        assertThat(store.pauseUntilMillis.first()).isEqualTo(0L)
    }

    @Test
    fun last_state_is_persisted() = runTest {
        val file = File(temporaryFolder.root, "capture_state.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val store = CaptureStateStore(dataStore)

        store.setLastState("PAUSED_SCHEDULE")

        assertThat(store.lastState.first()).isEqualTo("PAUSED_SCHEDULE")
    }
}

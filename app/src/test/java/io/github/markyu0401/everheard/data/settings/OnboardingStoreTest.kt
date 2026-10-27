package io.github.markyu0401.everheard.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaults_to_incomplete_and_persists_completion() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val store = OnboardingStore(dataStore)

        assertThat(store.isComplete.first()).isFalse()

        store.markComplete()

        assertThat(store.isComplete.first()).isTrue()
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun tutorial_defaults_to_unseen_and_persists() = runTest {
        val file = File(temporaryFolder.root, "settings-tutorial.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val store = OnboardingStore(dataStore)

        assertThat(store.tutorialSeen.first()).isFalse()

        store.markTutorialSeen()

        assertThat(store.tutorialSeen.first()).isTrue()
    }
}

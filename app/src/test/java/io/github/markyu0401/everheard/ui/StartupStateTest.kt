package io.github.markyu0401.everheard.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartupStateTest {

    @Test
    fun onboarding_wins_even_when_permissions_are_granted() {
        val state = startupStateFor(
            onboardingComplete = false,
            permissions = PermissionStatus(microphoneGranted = true, notificationsGranted = true)
        )
        assertThat(state).isEqualTo(StartupState.Onboarding)
    }

    @Test
    fun microphone_is_requested_before_notifications() {
        val state = startupStateFor(
            onboardingComplete = true,
            permissions = PermissionStatus(microphoneGranted = false, notificationsGranted = false)
        )
        assertThat(state).isEqualTo(StartupState.NeedsMicrophone)
    }

    @Test
    fun notifications_are_requested_after_microphone() {
        val state = startupStateFor(
            onboardingComplete = true,
            permissions = PermissionStatus(microphoneGranted = true, notificationsGranted = false)
        )
        assertThat(state).isEqualTo(StartupState.NeedsNotifications)
    }

    @Test
    fun ready_when_onboarding_and_both_permissions_are_complete() {
        val state = startupStateFor(
            onboardingComplete = true,
            permissions = PermissionStatus(microphoneGranted = true, notificationsGranted = true)
        )
        assertThat(state).isEqualTo(StartupState.Ready)
    }
}

package io.github.markyu0401.everheard.ui

/**
 * The startup gate. Permission order matters: onboarding first (legal consent),
 * then microphone, then notifications. Notifications are only a runtime permission
 * on Android 13+; on older versions the status is treated as granted.
 */
sealed interface StartupState {
    data object Loading : StartupState
    data object Onboarding : StartupState
    data object NeedsMicrophone : StartupState
    data object NeedsNotifications : StartupState
    data object Ready : StartupState
}

data class PermissionStatus(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean
)

fun startupStateFor(
    onboardingComplete: Boolean,
    permissions: PermissionStatus
): StartupState = when {
    !onboardingComplete -> StartupState.Onboarding
    !permissions.microphoneGranted -> StartupState.NeedsMicrophone
    !permissions.notificationsGranted -> StartupState.NeedsNotifications
    else -> StartupState.Ready
}

package io.github.markyu0401.everheard.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.markyu0401.everheard.data.settings.onboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val onboardingStore = application.onboardingStore()

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    private var onboardingComplete = false
    private var tutorialSeen = false

    init {
        viewModelScope.launch {
            onboardingComplete = onboardingStore.isComplete.first()
            tutorialSeen = onboardingStore.tutorialSeen.first()
            refreshPermissions()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            onboardingStore.markComplete()
            onboardingComplete = true
            refreshPermissions()
        }
    }

    fun completeTutorial() {
        viewModelScope.launch {
            onboardingStore.markTutorialSeen()
            tutorialSeen = true
            refreshPermissions()
        }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()

        val microphoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        _state.value = startupStateFor(
            onboardingComplete = onboardingComplete,
            tutorialSeen = tutorialSeen,
            permissions = PermissionStatus(
                microphoneGranted = microphoneGranted,
                notificationsGranted = notificationsGranted
            )
        )
    }
}

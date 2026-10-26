package io.github.markyu0401.everheard.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Start/stop entry points. Start must be called from a visible screen or a
 * notification action; a background caller is refused by the platform.
 */
object CaptureController {

    private val _state = MutableStateFlow(CaptureState.STOPPED)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun start(context: Context) {
        val intent = Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, CaptureService::class.java))
    }

    fun isRunning(): Boolean = state.value.isServiceAlive

    internal fun updateState(state: CaptureState) {
        _state.value = state
    }
}

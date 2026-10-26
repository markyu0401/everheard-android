package io.github.markyu0401.everheard.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Start/stop entry points. Start must be called from a visible screen or a
 * notification action; a background caller is refused by the platform.
 */
object CaptureController {

    fun start(context: Context) {
        val intent = Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, CaptureService::class.java))
    }

    fun isRunning(): Boolean = CaptureService.isRunning
}

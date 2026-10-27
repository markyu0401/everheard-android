package io.github.markyu0401.everheard.capture.guards

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Resolves the package that most recently came to the foreground. This needs
 * the user-granted Usage access special permission; when it is absent,
 * [currentPackage] returns null and app-specific playback exclusion stays off.
 */
class ForegroundAppMonitor(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun currentPackage(): String? {
        if (!hasUsageAccess()) return null
        return runCatching {
            val usage = context.getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val events = usage.queryEvents(end - LOOKBACK_MS, end)
            val event = UsageEvents.Event()
            var lastPackage: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (resumed) lastPackage = event.packageName
            }
            lastPackage
        }.getOrNull()
    }

    companion object {
        private const val LOOKBACK_MS = 10 * 60_000L
    }
}

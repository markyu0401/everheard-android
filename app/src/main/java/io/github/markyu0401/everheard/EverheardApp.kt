package io.github.markyu0401.everheard

import android.app.Application
import android.util.Log
import io.github.markyu0401.everheard.data.migration.DataPruner
import io.github.markyu0401.everheard.mcp.McpConfigStore
import io.github.markyu0401.everheard.mcp.McpServerManager
import kotlinx.coroutines.runBlocking

class EverheardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // One-time corpus cleanup. It must finish before the sticky capture
        // service starts in this process, otherwise the service's own store
        // cache would rewrite the pre-migration rows on its next append.
        runCatching {
            runBlocking {
                val result = DataPruner.runOnce(this@EverheardApp)
                if (result != null) {
                    Log.i(
                        "EverheardApp",
                        "prune_v1: removed ${result.removedUtterances} utterances, " +
                            "${result.removedSessions} sessions; " +
                            "${result.remainingSessions} remain"
                    )
                }
            }
        }.onFailure { error ->
            Log.e("EverheardApp", "prune_v1 failed; will retry on next launch", error)
        }

        // The MCP server used to start only from the Settings toggle, so it
        // vanished on every process death. Honor the stored config at boot.
        runCatching {
            runBlocking {
                val config = McpConfigStore.inDirectory(filesDir).load()
                if (config.enabled && config.token.isNotBlank()) {
                    val started = McpServerManager.start(this@EverheardApp, config)
                    started.onSuccess { port ->
                        Log.i("EverheardApp", "MCP listening on 127.0.0.1:$port")
                    }.onFailure { error ->
                        Log.e("EverheardApp", "MCP autostart failed", error)
                    }
                }
            }
        }
    }
}

package io.github.markyu0401.everheard.capture

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R

/**
 * Quick-settings tile. A tile tap is user interaction, so the microphone
 * foreground service may start from it; if the platform refuses, open the app
 * instead of crashing.
 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (CaptureService.isRunning) {
            CaptureController.stop(this)
        } else {
            try {
                CaptureController.start(this)
            } catch (_: Exception) {
                val openApp = Intent(this, MainActivity::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pending = PendingIntent.getActivity(
                        this,
                        6,
                        openApp,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pending)
                } else {
                    openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(openApp)
                }
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (CaptureService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.quick_tile_label)
        tile.updateTile()
    }
}

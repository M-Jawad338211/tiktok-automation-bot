package dev.malik.phonebot.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.malik.phonebot.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** FR-5: Quick Settings tile mirroring the TikTok flow switch. */
class BotTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        scope.launch { render(app.settings.current().tiktokEnabled) }
    }

    override fun onClick() {
        scope.launch {
            val on = !app.settings.current().tiktokEnabled
            app.settings.setTiktokEnabled(on)
            render(on)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (on) "On" else "Off"
        tile.updateTile()
    }

    companion object {
        /** Call after the switch changes in the app so the tile catches up. */
        fun refresh(context: Context) =
            requestListeningState(context, ComponentName(context, BotTileService::class.java))
    }
}

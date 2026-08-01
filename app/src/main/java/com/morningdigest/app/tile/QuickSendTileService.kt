package com.morningdigest.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.morningdigest.app.worker.WorkScheduler

class QuickSendTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Notify Now"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
        WorkScheduler.runNow(applicationContext, sendNotification = true)
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

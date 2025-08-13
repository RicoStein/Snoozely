// app/src/main/java/.../SleepTimerTileService.kt
package com.tigonic.snoozely.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tigonic.snoozely.MainActivity // oder dein Entry-Activity

class SleepTimerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // Kachelzustand updaten (aktiv/inaktiv)
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.label = "Snoozely"
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        // App öffnen:
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivityAndCollapse(launch) // schließt Quick Settings und öffnet Activity

        // ODER: direkt eine Aktion triggern (z. B. Timer starten/stoppen)
        // val intent = Intent(this, TimerEngineService::class.java)
        //     .setAction(TimerContracts.ACTION_TOGGLE) // oder ACTION_START/STOP/EXTEND
        // startForegroundService(intent)
    }
}

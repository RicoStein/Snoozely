package com.tigonic.snoozely.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.tigonic.snoozely.MainActivity

class SleepTimerTileService : TileService() {

    override fun onClick() {
        super.onClick()

        // Wenn du die App beim Tippen öffnen willst:
        openAppSafely()
        // Wenn du stattdessen nur etwas toggeln/starten willst (Service), lass openAppSafely() weg
        // und sende hier deinen Start/Stop-Intent an den TimerEngineService.
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppSafely() {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            // Passe Flags nach Bedarf an
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Ab Android 14+ die PendingIntent-Variante verwenden
        val pending = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (isLocked) {
            // Gerät entsperren und dann starten
            unlockAndRun {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(pending)
                } else {
                    // Fallback für ältere Systeme
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(activityIntent)
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(activityIntent)
            }
        }
    }
}

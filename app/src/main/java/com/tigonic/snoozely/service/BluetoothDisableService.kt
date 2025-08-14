package com.tigonic.snoozely.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Deaktiviert Bluetooth auf Geräten, auf denen das programmatisch erlaubt ist.
 * - Erlaubt bis Android 12 (API 31/32), ab Android 13 (API 33) nicht mehr möglich.
 * - Läuft kurz als Foreground-Service (O+ Restriktionen) und beendet sich danach.
 */
class BluetoothDisableService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                val requested = SettingsPreferenceHelper.getBluetoothDisableRequested(applicationContext).first()

                if (allowed && requested) {
                    // Auf O+ als Foreground-Service starten
                    startAsForegroundIfRequired()

                    // Bluetooth deaktivieren (sofern Adapter vorhanden)
                    runCatching {
                        BluetoothAdapter.getDefaultAdapter()?.disable()
                    }
                }
            } finally {
                stopForegroundSafely()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForegroundIfRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "bt_control"
            val channelName = getString(R.string.notif_channel_bt_title)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.notif_channel_bt_desc)
                    }
                )
            }
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_timer) // Fallback-Icon in mipmap/drawable
                .setContentTitle(getString(R.string.notif_bt_disabling_title))
                .setContentText(getString(R.string.notif_bt_disabling_text))
                .setSilent(true)
                .build()

            // ab Android 14 (API 34) nutzt das System Foreground-Service-Typ Connected-Device
            startForeground(9042, notification)
        }
    }

    private fun stopForegroundSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startIfAllowed(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val i = Intent(ctx, BluetoothDisableService::class.java)
                // O+: als Foreground starten, sonst normal
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            }
        }
    }
}

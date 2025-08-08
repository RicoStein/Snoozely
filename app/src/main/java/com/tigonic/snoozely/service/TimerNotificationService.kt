package com.tigonic.snoozely.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R

class TimerNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_notification_channel"
        const val NOTIFICATION_ID = 42

        const val EXTRA_REMAINING_MS = "EXTRA_REMAINING_MS"
        const val EXTRA_TOTAL_MS = "EXTRA_TOTAL_MS"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PLUS_5_MIN = "ACTION_PLUS_5_MIN"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_UPDATE -> {
                val remainingMs = intent.getLongExtra(EXTRA_REMAINING_MS, 0)
                val totalMs = intent.getLongExtra(EXTRA_TOTAL_MS, 0)
                showNotification(remainingMs, totalMs)
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
            // TODO: Handle ACTION_PLUS_5_MIN (Logik im Timer!)
        }
        return START_STICKY
    }

    private fun showNotification(remainingMs: Long, totalMs: Long) {
        val minutes = (remainingMs / 1000) / 60
        val seconds = (remainingMs / 1000) % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)

        val progress = if (totalMs > 0) (((totalMs - remainingMs) * 100 / totalMs).toInt()).coerceIn(0, 100) else 0


        // Stopp-Action (optional)
        val stopIntent = Intent(this, TimerNotificationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1001, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable())

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText(getString(R.string.notification_remaining_time, timeText))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_lock_idle_alarm, getString(R.string.timer_stop), stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}

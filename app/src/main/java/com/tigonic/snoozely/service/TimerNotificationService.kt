package com.tigonic.snoozely.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TimerNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_notification_channel"
        const val NOTIFICATION_ID = 42

        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_EXTEND = "ACTION_EXTEND"
    }

    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showInitializingNotification()
        when (intent?.action) {
            ACTION_UPDATE -> startOrUpdateTicker()
            ACTION_STOP -> {
                stopTickerAndNotification()
                // *** NUR BEIM EXPLIZITEN STOP DEN TIMER AUCH IM DATASTORE STOPPEN! ***
                CoroutineScope(Dispatchers.Default).launch {
                    TimerPreferenceHelper.stopTimer(
                        applicationContext,
                        TimerPreferenceHelper.getTimer(applicationContext).first()
                    )
                }
            }
            ACTION_EXTEND -> extendTimerMinutes()
        }
        return START_STICKY
    }

    private fun showInitializingNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText("Timer wird gestartet...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startOrUpdateTicker() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            val context = applicationContext
            // Main-Loop: Solange Timer läuft und Notification aktiv ist, Notification updaten
            while (true) {
                val notificationEnabled = SettingsPreferenceHelper.getNotificationEnabled(context).first()
                val timerRunning = TimerPreferenceHelper.getTimerRunning(context).first()
                val timerMinutes = TimerPreferenceHelper.getTimer(context).first()
                val timerStartTime = TimerPreferenceHelper.getTimerStartTime(context).first()

                if (!notificationEnabled || !timerRunning || timerMinutes < 1 || timerStartTime == 0L) {
                    break
                }

                val totalMs = timerMinutes * 60_000L
                val now = System.currentTimeMillis()
                val elapsedMs = now - timerStartTime
                val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)

                showNotification(remainingMs, totalMs)

                if (remainingMs <= 0) {
                    break
                }
                delay(1000)
                // Werte nochmal prüfen
                if (!notificationEnabled || !timerRunning || timerMinutes < 1) {
                    break
                }
            }
            stopTickerAndNotification()
        }
    }

    private fun stopTickerAndNotification() {
        timerJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun extendTimerMinutes() {
        CoroutineScope(Dispatchers.Default).launch {
            val context = applicationContext
            val timerRunning = TimerPreferenceHelper.getTimerRunning(context).first()
            val timerMinutes = TimerPreferenceHelper.getTimer(context).first()
            val timerStartTime = TimerPreferenceHelper.getTimerStartTime(context).first()
            val extendMinutes = SettingsPreferenceHelper.getProgressExtendMinutes(context).first()

            if (!timerRunning || timerStartTime == 0L) return@launch

            val now = System.currentTimeMillis()
            val elapsedMs = now - timerStartTime
            val totalMs = timerMinutes * 60_000L
            val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)

            val newTotalMs = remainingMs + (extendMinutes * 60_000L)
            val newMinutes = ((newTotalMs + elapsedMs) / 60_000L).toInt().coerceAtLeast(1)

            TimerPreferenceHelper.setTimer(applicationContext, newMinutes)
            // StartTime bleibt gleich!
            startOrUpdateTicker()
        }
    }

    private fun showNotification(remainingMs: Long, totalMs: Long) {
        val minutes = (remainingMs / 1000) / 60
        val seconds = (remainingMs / 1000) % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        val progress = if (totalMs > 0) (((totalMs - remainingMs) * 100 / totalMs).toInt()).coerceIn(0, 100) else 0

        val stopIntent = Intent(this, TimerNotificationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1001, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable())

        // Dynamischer Minutenwert
        val extendMinutes = runBlocking {
            SettingsPreferenceHelper.getProgressExtendMinutes(this@TimerNotificationService).first()
        }
        val extendIntent = Intent(this, TimerNotificationService::class.java).apply { action = ACTION_EXTEND }
        val extendPendingIntent = PendingIntent.getService(this, 1002, extendIntent, PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable())

        val extendButtonText = getString(R.string.timer_plus_x, extendMinutes) // z.B. "+ 7 min"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_timer_running))
            .setContentText(getString(R.string.notification_remaining_time, timeText))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_input_add, extendButtonText, extendPendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, getString(R.string.timer_stop), stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
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

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }
}

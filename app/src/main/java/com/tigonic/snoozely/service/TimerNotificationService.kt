package com.tigonic.snoozely.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.dialog.ReminderDialogActivity
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TimerNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_notification_channel"           // FG (LOW)
        const val NOTIFICATION_ID = 42

        const val REMINDER_CHANNEL_ID = "timer_reminder_channel"      // HUN (HIGH)
        const val REMINDER_NOTIFICATION_ID = 43

        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_EXTEND = "ACTION_EXTEND"
    }

    private var reminderShownForTimerStart: Long = -1
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createForegroundChannel()
        createReminderChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showInitializingNotification()
        when (intent?.action) {
            ACTION_UPDATE -> startOrUpdateTicker()
            ACTION_STOP -> {
                stopTickerAndNotification()
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

            while (true) {
                val notificationEnabled = SettingsPreferenceHelper.getNotificationEnabled(context).first()
                val timerRunning = TimerPreferenceHelper.getTimerRunning(context).first()
                val timerMinutes = TimerPreferenceHelper.getTimer(context).first()
                val timerStartTime = TimerPreferenceHelper.getTimerStartTime(context).first()
                val showReminderPopup = SettingsPreferenceHelper.getShowReminderPopup(context).first()
                val reminderMinutes = SettingsPreferenceHelper.getReminderMinutes(context).first()

                if (!notificationEnabled || !timerRunning || timerMinutes < 1 || timerStartTime == 0L) {
                    Log.d("TimerService", "Abbruch: notif=$notificationEnabled, running=$timerRunning, minutes=$timerMinutes, start=$timerStartTime")
                    break
                }

                val totalMs = timerMinutes * 60_000L
                val now = System.currentTimeMillis()
                val elapsedMs = now - timerStartTime
                val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0)
                val remainingSec = (remainingMs / 1000).toInt()

                if (showReminderPopup &&
                    remainingSec == reminderMinutes * 60 &&
                    reminderShownForTimerStart != timerStartTime
                ) {
                    reminderShownForTimerStart = timerStartTime
                    Log.d("TimerService", "Heads-Up Reminder ausgelöst (t−$reminderMinutes min)")
                    showHeadsUpReminder(reminderMinutes)
                }

                showNotification(remainingMs, totalMs)

                if (remainingMs <= 0) {
                    Log.d("TimerService", "Timer abgelaufen.")
                    break
                }
                delay(1000)
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

        val extendMinutes = runBlocking { SettingsPreferenceHelper.getProgressExtendMinutes(this@TimerNotificationService).first() }
        val extendIntent = Intent(this, TimerNotificationService::class.java).apply { action = ACTION_EXTEND }
        val extendPendingIntent = PendingIntent.getService(this, 1002, extendIntent, PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable())

        val extendButtonText = getString(R.string.timer_plus_x, extendMinutes)

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

    // ---------- Channels & Heads-Up ----------

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Timer",
                    NotificationManager.IMPORTANCE_LOW   // Foreground → LOW
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun createReminderChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "Timer-Reminder",
                    NotificationManager.IMPORTANCE_HIGH  // Heads-Up
                ).apply {
                    description = "Hinweis kurz vor Timerende"
                    enableVibration(true)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun showHeadsUpReminder(remainingMin: Int) {
        // Verlängerungsschritt aus Settings holen
        val extendMinutes = runBlocking {
            SettingsPreferenceHelper.getProgressExtendMinutes(this@TimerNotificationService).first()
        }

        // kurzer, dynamischer Text: "Timer läuft in X Min ab – +Y Min oder beenden."
        val body = getString(R.string.reminder_popup_message, remainingMin, extendMinutes)
        // Falls du lieber den längeren Text möchtest:
        // val body = getString(R.string.reminder_popup_hint, remainingMin, extendMinutes)

        // Tippen auf Banner -> optionaler Dialog
        val dialogIntent = Intent(this, ReminderDialogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val dialogPi = PendingIntent.getActivity(
            this, 2001, dialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable()
        )

        // Action: +x min
        val extendIntent = Intent(this, TimerNotificationService::class.java).apply {
            action = ACTION_EXTEND
        }
        val extendPi = PendingIntent.getService(
            this, 2002, extendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable()
        )

        // Action: Stoppen
        val stopIntent = Intent(this, TimerNotificationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 2003, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable()
        )

        val builder = NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.reminder_popup_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // mehr Text im HUN sichtbar
            .setCategory(NotificationCompat.CATEGORY_ALARM)            // aggressiver als REMINDER
            .setPriority(NotificationCompat.PRIORITY_HIGH)             // < API 26
            .setDefaults(Notification.DEFAULT_ALL)                     // Ton/Vibration für Heads-Up
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(dialogPi)
            .addAction(
                android.R.drawable.ic_input_add,
                getString(R.string.timer_plus_x, extendMinutes),
                extendPi
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                getString(R.string.timer_stop),
                stopPi
            )
            .setTimeoutAfter(10_000)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(REMINDER_NOTIFICATION_ID, builder.build())
    }



    private fun flagImmutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }
}
